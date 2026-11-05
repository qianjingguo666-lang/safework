# 智能体新工具自测：危险化学品、事故调查、变更管理、班前会、安全生产责任书
# 需要配置大模型（支持 Function Calling，例如 DeepSeek）；每个问题要十几秒
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-agent-tools.ps1

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'

function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
    if ($body) {
        $json = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 5 }
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    $resp = Invoke-WebRequest @params
    $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}
# 登录要先取验证码：开发环境（dev）后端会把答案放在 debugAnswer 里返回，脚本直接用它
function LoginRaw($tenant, $user, $password) {
    $c = (Call GET '/auth/captcha').data
    if ($c.enabled -and -not $c.debugAnswer) { throw '后端没有返回验证码答案：请确认后端是用开发环境（dev）启动的' }
    return Call POST '/auth/login' @{ tenantCode = $tenant; username = $user; password = $password; captchaKey = $c.captchaKey; captchaCode = $c.debugAnswer }
}
function Login($tenant, $user, $password = 'admin123') {
    $r = LoginRaw $tenant $user $password
    if ($r.code -ne 200) { throw "登录失败 $tenant/$user：$($r.msg)" }
    return $r.data.token
}
function Check($name, $ok) {
    if ($ok) { Write-Host "[通过] $name" -ForegroundColor Green }
    else     { Write-Host "[失败] $name" -ForegroundColor Red; $script:failed++ }
}
function Title($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Show($r) { Write-Host "  -> code = $($r.code), msg = $($r.msg)" -ForegroundColor DarkGray }
function Status($id, $token) { (Call GET "/ticket/$id" $null $token).data.ticket.status }
function Fmt($d) { $d.ToString('yyyy-MM-dd HH:mm:ss') }
$failed = 0

# 调用智能体接口，解析 SSE 事件，返回 { starts; ends; answer; done; error; json }
function Agent($question, $token) {
    $body = @{ question = $question } | ConvertTo-Json
    $resp = Invoke-WebRequest -Uri "$base/ai/agent/chat" -Method POST -UseBasicParsing -TimeoutSec 180 `
        -Headers @{ Authorization = "Bearer $token"; Accept = 'text/event-stream, application/json' } `
        -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
    $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    $r = @{ starts = New-Object System.Collections.ArrayList; ends = New-Object System.Collections.ArrayList; cards = New-Object System.Collections.ArrayList; answer = ''; done = $null; error = $null; json = $null }
    if ($resp.Headers['Content-Type'] -like '*json*') { $r.json = $text | ConvertFrom-Json; return $r }
    foreach ($block in ($text -split "`r?`n`r?`n")) {
        $event = ''; $data = ''
        foreach ($line in ($block -split "`r?`n")) {
            if ($line.StartsWith('event:')) { $event = $line.Substring(6).Trim() }
            elseif ($line.StartsWith('data:')) { $data += $line.Substring(5) }
        }
        if (-not $data) { continue }
        $obj = $data | ConvertFrom-Json
        switch ($event) {
            'tool_start' { [void]$r.starts.Add($obj) }
            'tool_end'   { [void]$r.ends.Add($obj) }
            'confirm'    { [void]$r.cards.Add($obj) }
            'answer'     { $r.answer = $obj.t }
            'done'       { $r.done = $obj }
            'error'      { $r.error = $obj.msg }
        }
    }
    return $r
}
# 打印调用过程
function ShowSteps($r) {
    for ($i = 0; $i -lt $r.starts.Count; $i++) {
        $s = $r.starts[$i]
        $e = $r.ends | Where-Object { $_.id -eq $s.id } | Select-Object -First 1
        $argText = ($s.args | ConvertTo-Json -Compress)
        Write-Host "  调用 $($s.label) $argText → $($e.summary)" -ForegroundColor DarkGray
    }
    if ($r.answer) {
        $a = $r.answer -replace "`r?`n", ' '
        if ($a.Length -gt 120) { $a = $a.Substring(0, 120) + '…' }
        Write-Host "  回答：$a" -ForegroundColor DarkGray
    }
    if ($r.done) { Write-Host "  $($r.done.rounds) 轮，$($r.done.tokens) tokens，用时 $($r.done.costMs) ms" -ForegroundColor DarkGray }
    if ($r.error) { Write-Host "  错误：$($r.error)" -ForegroundColor Yellow }
}
# 工具结果摘要「共 N 条」里的 N
function TotalOf($summary) {
    if ($summary -match '共 (\d+)') { return [int]$Matches[1] }
    return -1
}
# 不带任何筛选条件的隐患查询（只有 limit），它的总数应该等于这个人在隐患列表页看到的总数
function UnfilteredHazardTotal($r) {
    foreach ($s in $r.starts) {
        if ($s.name -ne 'search_hazards') { continue }
        $names = @($s.args.PSObject.Properties | Where-Object { $_.Value -ne $null -and "$($_.Value)" -ne '' } | ForEach-Object { $_.Name }) | Where-Object { $_ -ne 'limit' }
        if (@($names).Count -eq 0) {
            $e = $r.ends | Where-Object { $_.id -eq $s.id } | Select-Object -First 1
            return TotalOf $e.summary
        }
    }
    return -1
}

$tAdmin = Login 'demo' 'admin'
$tLisi  = Login 'demo' 'lisi'
$tZhao  = Login 'demo' 'zhaoliu'
$newTools = @('search_chemicals', 'search_accidents', 'search_changes', 'search_shift_meetings', 'search_responsibility')
function Called($r, $name) { return $null -ne ($r.starts | Where-Object { $_.name -eq $name } | Select-Object -First 1) }

Title '1. 新工具按权限出现'
$names = @((Call GET '/ai/agent/tools' $null $tAdmin).data | ForEach-Object { $_.name })
$missing = @($newTools | Where-Object { $names -notcontains $_ })
Check "企业管理员有 5 个新工具（缺：$($missing -join '、')）" ($missing.Count -eq 0)
$names = @((Call GET '/ai/agent/tools' $null $tZhao).data | ForEach-Object { $_.name })
Check '监护人有危化品、事故、变更、班前会、责任书菜单，也就有这 5 个工具' ((@($newTools | Where-Object { $names -notcontains $_ })).Count -eq 0)

$st = (Call GET '/ai/status' $null $tAdmin).data
if (-not $st.chatEnabled) {
    Write-Host "`n  当前没有配置大模型，跳过问答测试。" -ForegroundColor Yellow
} else {
    Write-Host "  对话模型：$($st.chatModel)" -ForegroundColor DarkGray

    Title '2. 危险化学品'
    $tank = @((Call GET '/safety/chem/store/list' $null $tAdmin).data | Where-Object { $_.store.name -eq '罐区' })[0]
    $meth = if ($tank) { @($tank.stocks | Where-Object { $_.chemName -eq '甲醇' })[0] } else { $null }
    Write-Host "`n  问：罐区现在甲醇有多少吨？罐区是几级重大危险源？" -ForegroundColor White
    $r = Agent '罐区现在甲醇有多少吨？罐区是几级重大危险源？' $tAdmin
    ShowSteps $r
    Check '调用了【查询危险化学品】' (Called $r 'search_chemicals')
    if ($meth) {
        $q = ([double]$meth.qty).ToString()
        Check "回答里的甲醇库存和页面一致（$q 吨）" ($r.answer -match [regex]::Escape($q))
    }
    Check '回答里有重大危险源等级' ($r.answer -match '[一二三四]级')

    Title '3. 事故调查'
    Write-Host "`n  问：哪些事故整改措施已经逾期了？责任人是谁？" -ForegroundColor White
    $r = Agent '哪些事故整改措施已经逾期了？责任人是谁？' $tAdmin
    ShowSteps $r
    Check '调用了【查询事故调查】' (Called $r 'search_accidents')
    Check '给出了回答' ($r.answer.Length -gt 0 -and -not $r.error)

    Title '4. 变更管理'
    Write-Host "`n  问：我有哪些变更要处理？" -ForegroundColor White
    $r = Agent '我有哪些变更要处理？' $tAdmin
    ShowSteps $r
    $call = $r.starts | Where-Object { $_.name -eq 'search_changes' } | Select-Object -First 1
    Check '调用了【查询变更管理】，并且只查要我处理的（todo=true）' ($null -ne $call -and "$($call.args.todo)" -eq 'True')
    $before = @((Call GET '/safety/moc/page?size=50' $null $tAdmin).data.records | Where-Object { $_.change.status -eq 'APPROVING' }).Count
    Write-Host "`n  问：把所有待审批的变更都审批通过" -ForegroundColor White
    $r = Agent '把所有待审批的变更都审批通过' $tAdmin
    ShowSteps $r
    $after = @((Call GET '/safety/moc/page?size=50' $null $tAdmin).data.records | Where-Object { $_.change.status -eq 'APPROVING' }).Count
    Check "智能体不能代替审批：待审批前后都是 $before 项，没有生成确认卡片" ($before -eq $after -and $r.cards.Count -eq 0)

    Title '5. 班前会'
    Write-Host "`n  问：今天哪些班组还没开班前会？谁不能上岗？" -ForegroundColor White
    $r = Agent '今天哪些班组还没开班前会？谁不能上岗？' $tAdmin
    ShowSteps $r
    Check '调用了【查询班前会】' (Called $r 'search_shift_meetings')
    $notHeld = @((Call GET '/safety/meeting/team/list' $null $tAdmin).data | Where-Object { $_.team.status -eq 1 -and -not $_.today })
    if ($notHeld.Count -gt 0) {
        Check "回答里提到了今天没开的班组（$($notHeld[0].team.name)）" ($r.answer.Contains($notHeld[0].team.name))
    }

    Title '6. 安全生产责任书（作业负责人李四）'
    Write-Host "`n  问：我的安全生产责任书签了没有？" -ForegroundColor White
    $r = Agent '我的安全生产责任书签了没有？' $tLisi
    ShowSteps $r
    Check '调用了【查询安全生产责任书】' (Called $r 'search_responsibility')
    Check '给出了回答' ($r.answer.Length -gt 0 -and -not $r.error)
}

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '模型每次的回答不完全一样，偶尔一项失败可以再跑一次；一直失败就把输出发给 Claude' }
