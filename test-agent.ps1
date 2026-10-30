# AI 智能体自测：工具按权限过滤、Function Calling 调用过程、数据权限、租户隔离、只读
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-agent.ps1
# 第 1、2 部分不需要大模型；第 3 部分需要在 config/secret.yml 配置支持 Function Calling 的模型（例如 DeepSeek），没配置会跳过

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
    $r = @{ starts = New-Object System.Collections.ArrayList; ends = New-Object System.Collections.ArrayList; answer = ''; done = $null; error = $null; json = $null }
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
$tTest  = Login 'test' 'admin'

Title '1. 工具按权限过滤'
$names = @((Call GET '/ai/agent/tools' $null $tAdmin).data | ForEach-Object { $_.name })
Write-Host "  管理员可用：$($names -join ', ')" -ForegroundColor DarkGray
Check '管理员有 5 个工具' ($names.Count -eq 5)
$names = @((Call GET '/ai/agent/tools' $null $tLisi).data | ForEach-Object { $_.name })
Write-Host "  作业负责人可用：$($names -join ', ')" -ForegroundColor DarkGray
Check '作业负责人没有数据看板权限，也就没有【查询安全总览】工具' (-not ($names -contains 'get_safety_overview') -and $names -contains 'search_hazards')
$r = Call GET '/ai/agent/tools'
Check '未登录返回 401' ($r.code -eq 401)

Title '2. 参数校验'
$r = Call POST '/ai/agent/chat' @{ question = '' } $tAdmin
Show $r
Check '问题为空被拒绝（400）' ($r.code -eq 400)

Title '3. 智能体问答（需要大模型）'
$st = (Call GET '/ai/status' $null $tAdmin).data
if (-not $st.chatEnabled) {
    $r = Agent '有哪些隐患？' $tAdmin
    Show $r.json
    Check '没有配置大模型时提示去配置' ($r.json.code -eq 400 -and $r.json.msg -like '*大模型*')
    Write-Host "`n  当前没有配置大模型，跳过需要大模型的测试。" -ForegroundColor Yellow
} else {
    Write-Host "  对话模型：$($st.chatModel)（每个问题要请求大模型 2~3 次，需要十几秒）" -ForegroundColor DarkGray
    $logsBefore = (Call GET '/ai/chat/logs?bizType=AGENT&size=1' $null $tAdmin).data.total

    Write-Host "`n  问：现在有多少条整改中的隐患？分别是什么？" -ForegroundColor White
    $r = Agent '现在有多少条整改中的隐患？分别是什么？' $tAdmin
    ShowSteps $r
    $real = (Call GET '/safety/hazard/page?status=RECTIFYING&size=1' $null $tAdmin).data.total
    $call = $r.starts | Where-Object { $_.name -eq 'search_hazards' -and $_.args.status -eq 'RECTIFYING' } | Select-Object -First 1
    Check '模型自己选择了【查询隐患】工具，并把「整改中」转换成参数 status=RECTIFYING' ($null -ne $call)
    if ($call) {
        $end = $r.ends | Where-Object { $_.id -eq $call.id } | Select-Object -First 1
        Check "工具查到的数量和隐患列表页一致（$real 条）" ((TotalOf $end.summary) -eq $real)
    }
    Check '给出了回答，没有报错' ($r.answer.Length -gt 0 -and -not $r.error -and $r.done)
    # 回答里可能写阿拉伯数字，也可能写中文数字
    $cn = @('零', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十')
    $mentioned = ($r.answer -match "$real") -or ($real -le 10 -and $r.answer.Contains($cn[$real])) -or ($real -eq 2 -and $r.answer.Contains('两'))
    Check "回答里提到了正确的数量 $real" $mentioned

    Write-Host "`n  问（监护人赵六）：不加任何筛选条件，查一下隐患一共有几条" -ForegroundColor White
    $r = Agent '不加任何筛选条件，查一下隐患一共有几条' $tZhao
    ShowSteps $r
    $agentTotal = UnfilteredHazardTotal $r
    $pageTotal = (Call GET '/safety/hazard/page?size=1' $null $tZhao).data.total
    $adminTotal = (Call GET '/safety/hazard/page?size=1' $null $tAdmin).data.total
    if ($agentTotal -lt 0) {
        Write-Host '  [跳过] 模型这次带了筛选条件，无法比较总数，可以再运行一次' -ForegroundColor Yellow
    } else {
        Check "数据权限：智能体给监护人查到 $agentTotal 条，和他自己在列表页看到的一致（$pageTotal 条；管理员能看到 $adminTotal 条）" ($agentTotal -eq $pageTotal)
    }

    Write-Host "`n  问（测试冶金管理员）：不加任何筛选条件，查一下隐患一共有几条" -ForegroundColor White
    $r = Agent '不加任何筛选条件，查一下隐患一共有几条' $tTest
    ShowSteps $r
    $agentTotal = UnfilteredHazardTotal $r
    $pageTotal = (Call GET '/safety/hazard/page?size=1' $null $tTest).data.total
    if ($agentTotal -lt 0) {
        Write-Host '  [跳过] 模型这次带了筛选条件，无法比较总数，可以再运行一次' -ForegroundColor Yellow
    } else {
        Check "租户隔离：测试冶金查到 $agentTotal 条，是自己企业的数据（列表页 $pageTotal 条，演示化工 $adminTotal 条）" ($agentTotal -eq $pageTotal)
    }

    Write-Host "`n  问（作业负责人李四）：最近 30 天的安全总览数据怎么样？" -ForegroundColor White
    $r = Agent '最近 30 天的安全总览数据怎么样？' $tLisi
    ShowSteps $r
    Check '没有权限的工具不会被调用（没有调用【查询安全总览】）' (-not ($r.starts | Where-Object { $_.name -eq 'get_safety_overview' }))
    Check '仍然给出了回答' ($r.answer.Length -gt 0)

    Write-Host "`n  问：把所有整改中的隐患都改成已闭环" -ForegroundColor White
    $before = (Call GET '/safety/hazard/page?status=RECTIFYING&size=1' $null $tAdmin).data.total
    $r = Agent '把所有整改中的隐患都改成已闭环' $tAdmin
    ShowSteps $r
    $after = (Call GET '/safety/hazard/page?status=RECTIFYING&size=1' $null $tAdmin).data.total
    Check "只读：智能体不能修改数据（整改中的隐患前后都是 $before 条）" ($before -eq $after)

    $logsAfter = (Call GET '/ai/chat/logs?bizType=AGENT&size=1' $null $tAdmin).data.total
    Check '每次提问都记录在问答记录里（类型：智能体）' ($logsAfter -ge $logsBefore + 3)
}

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Red }
