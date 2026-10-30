# 风险分级管控自测：LEC 只能取标准分值、自动定级、巡检周期约束、责任人巡检、措施失效转隐患、权限、租户隔离、导出、逾期提醒
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-risk.ps1

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
function Download($path, $token) {
    $resp = Invoke-WebRequest -Uri "$base$path" -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing
    $bytes = $resp.RawContentStream.ToArray()
    $type = "$($resp.Headers['Content-Type'])"
    if ($type -like '*json*') {
        $j = [Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json
        return @{ ok = $false; code = $j.code; msg = $j.msg }
    }
    return @{ ok = $true; bytes = $bytes; type = $type; disposition = [Uri]::UnescapeDataString("$($resp.Headers['Content-Disposition'])") }
}
function IsZip([byte[]]$b) { $b.Length -gt 4 -and $b[0] -eq 0x50 -and $b[1] -eq 0x4B }

$ts = Get-Date -Format 'MMddHHmmss'
function Point($l, $e, $c, $code, $responsibleId, $cycle) {
    $body = @{ pointCode = $code; pointName = "自测风险点 $ts"; pointType = '设备设施'; location = "自测区域 $ts"; responsibleId = $responsibleId
        hazardFactors = '测试用'; possibleAccidents = '火灾爆炸'; lecL = $l; lecE = $e; lecC = $c
        controlMeasures = "1. 报警器运行正常`n2. 安全阀铅封完好`n3. 静电接地可靠" }
    if ($cycle) { $body.checkCycleDays = $cycle }
    return $body
}

$tAdmin = Login 'demo' 'admin'
$tLisi  = Login 'demo' 'lisi'
$tZhao  = Login 'demo' 'zhaoliu'
$tZhang = Login 'demo' 'zhangsan'
$tTest  = Login 'test' 'admin'
$users = (Call GET '/system/user/options' $null $tAdmin).data
$lisiId = ($users | Where-Object { $_.nickname -like '李四*' } | Select-Object -First 1).id
$adminId = ($users | Where-Object { $_.nickname -like '*管理员*' } | Select-Object -First 1).id

Title '1. LEC 评估定级'
$opt = (Call GET '/safety/risk/lec-options' $null $tAdmin).data
Check "标准分值表：L $(@($opt.l).Count) 档、E $(@($opt.e).Count) 档、C $(@($opt.c).Count) 档" (@($opt.l).Count -eq 7 -and @($opt.e).Count -eq 6 -and @($opt.c).Count -eq 6)
$r = Call POST '/safety/risk' (Point 5 6 15 "TA$ts" $adminId $null) $tAdmin
Show $r
Check 'L 随便填（5）被拒绝，提示标准分值' ($r.code -eq 400 -and $r.msg -like '*标准分值*')
$r = Call POST '/safety/risk' (Point 6 6 15 "TA$ts" $adminId 7) $tAdmin
Show $r
Check 'D = 6×6×15 = 540 是重大风险，巡检周期不能设成 7 天（最长 1 天）' ($r.code -eq 400 -and $r.msg -like '*1 天*')
$r = Call POST '/safety/risk' (Point 6 6 15 "TA$ts" $adminId $null) $tAdmin
$idA = $r.data
$a = (Call GET "/safety/risk/$idA" $null $tAdmin).data.point
Check "新增成功：D = $($a.lecD)，$($a.riskLevel)，巡检周期默认 $($a.checkCycleDays) 天" ($a.lecD -eq 540 -and $a.riskLevel -eq 'MAJOR' -and $a.checkCycleDays -eq 1)
$r = Call POST '/safety/risk' (Point 1 6 15 "TA$ts" $adminId $null) $tAdmin
Check '编号不能重复' ($r.code -eq 400)
$r = Call PUT "/safety/risk/$idA" (Point 1 6 15 "TA$ts" $adminId $null) $tAdmin
$a = (Call GET "/safety/risk/$idA" $null $tAdmin).data.point
Check "重新评估：D = 1×6×15 = $($a.lecD)，降为一般风险，周期改为 $($a.checkCycleDays) 天" ($a.lecD -eq 90 -and $a.riskLevel -eq 'GENERAL' -and $a.checkCycleDays -eq 15)
$ov = (Call GET '/safety/risk/overview' $null $tAdmin).data
Check "四色统计：共 $($ov.total) 个，按位置分成 $(@($ov.groups).Count) 组" ($ov.total -ge 1 -and @($ov.groups | Where-Object { $_.location -eq "自测区域 $ts" }).Count -eq 1)

Title '2. 责任人巡检'
$r = Call POST '/safety/risk' (Point 3 6 15 "TB$ts" $lisiId $null) $tAdmin
$idB = $r.data
$b = (Call GET "/safety/risk/$idB" $null $tAdmin).data.point
Check "责任人李四的风险点：D = $($b.lecD)，较大风险，每 $($b.checkCycleDays) 天巡检" ($b.riskLevel -eq 'HIGH' -and $b.checkCycleDays -eq 7)
$lisiTasks = @((Call GET '/safety/risk/check/tasks' $null $tLisi).data | Where-Object { $_.pointId -eq $idB })
$zhaoTasks = @((Call GET '/safety/risk/check/tasks' $null $tZhao).data | Where-Object { $_.pointId -eq $idB })
Check '李四的巡检任务里有，赵六的没有（按责任人）' ($lisiTasks.Count -eq 1 -and $zhaoTasks.Count -eq 0 -and @($lisiTasks[0].measures).Count -eq 3)
$allOk = @(@{ ok = $true }, @{ ok = $true }, @{ ok = $true })
$r = Call POST "/safety/risk/$idB/check" @{ items = $allOk } $tZhao
Check '不是责任人不能巡检（403）' ($r.code -eq 403)
$r = Call POST "/safety/risk/$idB/check" @{ items = @(@{ ok = $true }) } $tLisi
Show $r
Check '必须逐条确认，少一条被拒绝' ($r.code -eq 400)
$bad = @(@{ ok = $true }, @{ ok = $false; note = '铅封断了' }, @{ ok = $true })
$r = Call POST "/safety/risk/$idB/check" @{ items = $bad; toHazard = $false } $tLisi
Show $r
Check '有措施失效又不上报隐患时，必须写备注说明处理情况' ($r.code -eq 400)
$r = Call POST "/safety/risk/$idB/check" @{ items = $allOk } $tLisi
$next = [datetime]::ParseExact("$($r.data.nextCheckTime)", 'yyyy-MM-dd HH:mm', $null)
Check "全部有效：结果正常，下次巡检排到 7 天后（$($r.data.nextCheckTime)）" ($r.data.result -eq 'NORMAL' -and [math]::Abs(($next - (Get-Date).AddDays(7)).TotalMinutes) -lt 10)

Title '3. 措施失效转隐患'
$r = Call POST "/safety/risk/$idB/check" @{ items = $bad; toHazard = $true } $tLisi
Show $r
Check "上报了隐患 $($r.data.hazardNo)" ($r.code -eq 200 -and $r.data.result -eq 'ABNORMAL' -and $r.data.hazardNo)
$h = (Call GET "/safety/hazard/$($r.data.hazardId)" $null $tAdmin).data.hazard
Write-Host "  隐患：$($h.title)" -ForegroundColor DarkGray
Check '较大风险点的措施失效，按重大隐患上报，状态待指派' ($h.level -eq 'MAJOR' -and $h.status -eq 'REPORTED' -and $h.location -eq "自测区域 $ts")
Check '隐患描述里写明了失效的措施' ($h.description -like '*安全阀铅封完好*铅封断了*')
$msg = @((Call GET '/message/page?size=50' $null $tZhang).data.records | Where-Object { $_.bizId -eq $h.id })
Check '和手动上报一样，安全员收到了待指派消息' ($msg.Count -ge 1)
$checks = (Call GET "/safety/risk/check/page?pointId=$idB" $null $tAdmin).data
Check '巡检记录：2 次（1 次正常、1 次转隐患）' ($checks.total -eq 2 -and @($checks.records | Where-Object { $_.hazardNo }).Count -eq 1)
$mine = (Call GET "/safety/risk/check/page?pointId=$idB" $null $tZhao).data
Check '赵六看不到李四的巡检记录' ($mine.total -eq 0)

Title '4. 权限、租户隔离、导出、停用'
$r = Call GET '/safety/risk/page' $null $tLisi
Check '作业负责人不能看风险清单（403），只能做自己的巡检' ($r.code -eq 403)
$r = Call GET "/safety/risk/$idB" $null $tTest
Check '测试冶金看不到演示化工的风险点' ($r.code -eq 404)
$d = Download "/safety/risk/export?keyword=$ts" $tAdmin
Check '导出风险分级管控清单 Excel' ($d.ok -and $d.bytes.Length -gt 1000 -and $d.bytes[0] -eq 0x50 -and $d.bytes[1] -eq 0x4B)
$r = Call PUT "/safety/risk/$idB/status?status=0" $null $tAdmin
$lisiTasks = @((Call GET '/safety/risk/check/tasks' $null $tLisi).data | Where-Object { $_.pointId -eq $idB })
Check '停用后不再出现在巡检任务里' ($r.code -eq 200 -and $lisiTasks.Count -eq 0)

Title '5. 巡检逾期提醒（定时任务）'
$tPlat = Login 'platform' 'admin'
$r = Call POST '/platform/job/risk-check/run' @{} $tPlat
Show $r
Write-Host "  $($r.data.result)" -ForegroundColor DarkGray
Check '平台手动执行【风险点巡检提醒】' ($r.code -eq 200 -and $r.data.status -eq 1)
$riskMsgs = @((Call GET '/message/page?size=100' $null $tLisi).data.records | Where-Object { $_.bizType -eq 'RISK' })
Check "李四收到了巡检提醒（演示数据里有逾期和今天到期的风险点）：$($riskMsgs.Count) 条" ($riskMsgs.Count -ge 1)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Red }
