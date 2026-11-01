# 设备设施自测：类型和检验周期校验、特种设备登记证、检验到期状态、检验合格排期、不合格自动停用并转隐患、启用条件、报废、到期提醒、权限、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-equipment.ps1

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'

function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 90 }
    if ($null -ne $body) {
        $json = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 6 }
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    $resp = Invoke-WebRequest @params
    $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}
# 登录要先取验证码：开发环境（dev）后端会把答案放在 debugAnswer 里返回，脚本直接用它
function Login($tenant, $user, $password = 'admin123') {
    $c = (Call GET '/auth/captcha').data
    if ($c.enabled -and -not $c.debugAnswer) { throw '后端没有返回验证码答案：请确认后端是用开发环境（dev）启动的' }
    $r = Call POST '/auth/login' @{ tenantCode = $tenant; username = $user; password = $password; captchaKey = $c.captchaKey; captchaCode = $c.debugAnswer }
    if ($r.code -ne 200) { throw "登录失败 $tenant/$user：$($r.msg)" }
    return $r.data.token
}
function Check($name, $ok) {
    if ($ok) { Write-Host "[通过] $name" -ForegroundColor Green }
    else     { Write-Host "[失败] $name" -ForegroundColor Red; $script:failed++ }
}
function Title($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Show($r) { Write-Host "  -> code = $($r.code), msg = $($r.msg)" -ForegroundColor DarkGray }
function Day($d) { $d.ToString('yyyy-MM-dd') }
function MsgsOf($token, $bizId) { ,@((Call GET '/message/page?size=100' $null $token).data.records | Where-Object { "$($_.bizId)" -eq "$bizId" }) }
function Eq($id, $token) { (Call GET "/safety/equipment/$id" $null $token).data }
$failed = 0
$today = Get-Date
$suffix = Get-Random -Minimum 1000 -Maximum 9999

$tZhang = Login 'demo' 'zhangsan'   # 安全员：管理台账、登记检验
$tLisi  = Login 'demo' 'lisi'       # 作业负责人：只能查看
$tTest  = Login 'test' 'admin'

Title '1. 演示数据和权限'
$ov = (Call GET '/safety/equipment/overview' $null $tZhang).data
Check "在用 $($ov.inUse)、检验逾期 $($ov.overdue)、30 天内到期 $($ov.dueSoon)、从未检验 $($ov.never)、停用 $($ov.stopped)" ($ov.inUse -ge 8 -and $ov.overdue -ge 1 -and $ov.dueSoon -ge 2 -and $ov.never -ge 1 -and $ov.stopped -ge 1)
$types = @((Call GET '/safety/equipment/types' $null $tZhang).data)
$gauge = $types | Where-Object { $_.value -eq 'PRESSURE_GAUGE' }
Check "设备类型 $($types.Count) 种；压力表默认 $($gauge.defaultMonths) 个月、最长 $($gauge.maxMonths) 个月" ($types.Count -ge 15 -and $gauge.maxMonths -eq 6)
$over = @((Call GET '/safety/equipment/page?inspectState=OVERDUE&size=50' $null $tZhang).data.records)
Check "检验逾期：$(($over | ForEach-Object { $_.equipCode }) -join '、')" (($over | Where-Object { $_.equipCode -eq 'PG-201' }) -and -not ($over | Where-Object { $_.inspectState -ne 'OVERDUE' }))
$stopped = @((Call GET '/safety/equipment/page?status=STOPPED&size=50' $null $tZhang).data.records | Where-Object { $_.equipCode -eq 'GD-301' })[0]
Check "检验不合格的报警器已停用：$($stopped.statusReason)" ($stopped.lastResult -eq 'FAIL')
$r = Call GET '/safety/equipment/page' $null $tLisi
Check '作业负责人可以查看设备' ($r.code -eq 200)
$r = Call POST '/safety/equipment' @{ equipCode = 'X'; name = 'x'; equipType = 'OTHER'; location = 'x' } $tLisi; Show $r
Check '作业负责人不能登记设备' ($r.code -eq 403)
$r = Call POST "/safety/equipment/$($stopped.id)/inspection" @{ inspectDate = (Day $today); result = 'PASS' } $tLisi; Show $r
Check '作业负责人不能登记检验结果' ($r.code -eq 403)

Title '2. 登记设备'
$last = $today.AddDays(20).AddMonths(-12)   # 上次检验：差不多一年前，20 天后到期
$valve = @{ equipCode = "SV-T$suffix"; name = '自测安全阀'; equipType = 'SAFETY_VALVE'; location = '自测装置区'; responsibleId = 103
            lastInspectDate = (Day $last) }
$r = Call POST '/safety/equipment' $valve $tZhang
Check "登记安全阀（录入上次检验 $(Day $last)）" ($r.code -eq 200)
$vid = $r.data
$e = (Eq $vid $tZhang).equipment
Check "按默认周期 $($e.cycleMonths) 个月排下次检验 $($e.nextInspectDate)：$($e.inspectState)" ($e.cycleMonths -eq 12 -and $e.nextInspectDate -eq (Day $last.AddMonths(12)) -and $e.inspectState -eq 'DUE_SOON')
$r = Call POST '/safety/equipment' $valve $tZhang; Show $r
Check '设备位号不能重复' ($r.code -ne 200)
$r = Call POST '/safety/equipment' @{ equipCode = "V-T$suffix"; name = '自测储罐'; equipType = 'PRESSURE_VESSEL'; location = 'x' } $tZhang; Show $r
Check '特种设备必须填写使用登记证编号' ($r.code -ne 200)
$r = Call POST '/safety/equipment' @{ equipCode = "PG-T$suffix"; name = '自测压力表'; equipType = 'PRESSURE_GAUGE'; location = 'x'; cycleMonths = 12 } $tZhang; Show $r
Check '压力表检验周期不能超过 6 个月' ($r.code -ne 200)
$r = Call POST '/safety/equipment' @{ equipCode = "U-T$suffix"; name = 'x'; equipType = 'SPACESHIP'; location = 'x' } $tZhang; Show $r
Check '设备类型必须正确' ($r.code -ne 200)
$r = Call POST '/safety/equipment' @{ equipCode = "CC-T$suffix"; name = '自测叉车'; equipType = 'FORKLIFT'; location = '仓库'; registerNo = "车11皖A$suffix" } $tZhang
$fid = $r.data
Check "新设备不填上次检验：$((Eq $fid $tZhang).equipment.inspectState)" ((Eq $fid $tZhang).equipment.inspectState -eq 'NEVER')

$valve.cycleMonths = 6
$r = Call PUT "/safety/equipment/$vid" $valve $tZhang
$e = (Eq $vid $tZhang).equipment
Check "检验周期改成 6 个月，按上次检验重新排期：$($e.nextInspectDate) $($e.inspectState)" ($r.code -eq 200 -and $e.nextInspectDate -eq (Day $last.AddMonths(6)) -and $e.inspectState -eq 'OVERDUE')

Title '3. 登记检验'
$r = Call POST "/safety/equipment/$vid/inspection" @{ inspectDate = (Day $today.AddDays(2)); result = 'PASS' } $tZhang; Show $r
Check '检验日期不能晚于今天' ($r.code -ne 200)
$r = Call POST "/safety/equipment/$vid/inspection" @{ inspectDate = (Day $today.AddYears(-2)); result = 'PASS' } $tZhang; Show $r
Check '检验日期不能早于上次检验' ($r.code -ne 200)
$r = Call POST "/safety/equipment/$vid/inspection" @{ inspectDate = (Day $today); result = 'PASS'; nextInspectDate = (Day $today.AddMonths(18)) } $tZhang; Show $r
Check '下次检验日期不能超过最长周期' ($r.code -ne 200)
$r = Call POST "/safety/equipment/$vid/inspection" @{ inspectDate = (Day $today); result = 'FAIL' } $tZhang; Show $r
Check '不合格必须写原因' ($r.code -ne 200)
$r = Call POST "/safety/equipment/$vid/inspection" @{ inspectDate = (Day $today); result = 'FAIL'; org = '合肥市特种设备检验所'; conclusion = '起跳压力偏高 0.2MPa，密封面泄漏'; toHazard = $true } $tZhang
Check "检验不合格：设备自动停用，转隐患 $($r.data.hazardNo)" ($r.code -eq 200 -and $r.data.stopped -and $r.data.hazardNo)
$h = (Call GET "/safety/hazard/$($r.data.hazardId)" $null $tZhang).data.hazard
Check "隐患：$($h.title)，安全阀不合格按重大隐患（$($h.level)），类别 $($h.category)" ($h.level -eq 'MAJOR' -and $h.category -eq '设备设施' -and $h.description -like '*密封面泄漏*')
$r = Call PUT "/safety/equipment/$vid/status" @{ status = 'IN_USE' } $tZhang; Show $r
Check '检验不合格的设备不能直接启用' ($r.code -ne 200 -and $r.msg -like '*不合格*')
$r = Call POST "/safety/equipment/$vid/inspection" @{ inspectDate = (Day $today); result = 'PASS'; conclusion = '更换阀芯后复检合格' } $tZhang
Check "复检合格，下次检验 $($r.data.nextInspectDate)（周期 6 个月）" ($r.code -eq 200 -and $r.data.nextInspectDate -eq (Day $today.AddMonths(6)))
$r = Call PUT "/safety/equipment/$vid/status" @{ status = 'IN_USE' } $tZhang
Check '复检合格后可以重新启用' ($r.code -eq 200 -and (Eq $vid $tZhang).equipment.status -eq 'IN_USE')
Check "检验记录 $(@((Eq $vid $tZhang).inspections).Count) 条" (@((Eq $vid $tZhang).inspections).Count -eq 2)

Title '4. 停用、报废'
$r = Call PUT "/safety/equipment/$vid/status" @{ status = 'STOPPED' } $tZhang; Show $r
Check '停用要写原因' ($r.code -ne 200)
$r = Call PUT "/safety/equipment/$fid/status" @{ status = 'SCRAPPED'; reason = '自测数据' } $tZhang
Check '报废' ($r.code -eq 200)
$r = Call POST "/safety/equipment/$fid/inspection" @{ inspectDate = (Day $today); result = 'PASS' } $tZhang; Show $r
Check '报废的设备不能再登记检验' ($r.code -ne 200)
$r = Call PUT "/safety/equipment/$fid/status" @{ status = 'IN_USE' } $tZhang; Show $r
Check '报废的设备不能恢复' ($r.code -ne 200)
$list = @((Call GET "/safety/equipment/page?keyword=CC-T$suffix" $null $tZhang).data.records)
Check '列表默认不显示报废的设备' ($list.Count -eq 0)

Title '5. 到期提醒'
$ext = @{ equipCode = "MH-T$suffix"; name = '自测灭火器'; equipType = 'EXTINGUISHER'; location = '自测区'; responsibleId = 104
          lastInspectDate = (Day $today.AddDays(7).AddMonths(-12)) }
$xid = (Call POST '/safety/equipment' $ext $tZhang).data
try {
    $tPlat = Login 'platform' 'admin'
    $r = Call POST '/platform/job/equipment-inspect/run' @{} $tPlat
    Check "执行设备检验到期提醒：$($r.data)" ($r.code -eq 200)
    $tZhao = Login 'demo' 'zhaoliu'
    $m = MsgsOf $tZhao $xid
    Check "责任人收到 7 天后到期的提醒：$(@($m)[0].title)" ($m.Count -ge 1)
    Check '安全员也收到' ((MsgsOf $tZhang $xid).Count -ge 1)
    Call POST '/platform/job/equipment-inspect/run' @{} $tPlat | Out-Null
    Check '同一天再执行不会重复提醒' ((MsgsOf $tZhao $xid).Count -eq $m.Count)
} catch {
    Write-Host "  （跳过：平台管理员登录失败，可能已经改过密码：$($_.Exception.Message)）" -ForegroundColor DarkGray
}

Title '6. 智能体工具、租户隔离、清理'
$names = @((Call GET '/ai/agent/tools' $null $tZhang).data | ForEach-Object { $_.name })
Check '智能体有【查询设备设施】工具 search_equipment' ($names -contains 'search_equipment')
$r = Call GET '/safety/equipment/page?size=50' $null $tTest
if ($r.code -eq 403) {
    Write-Host '  （跳过：测试冶金管理员没有设备设施菜单）' -ForegroundColor DarkGray
} else {
    Check "测试冶金只看到自己的设备（$((@($r.data.records) | ForEach-Object { $_.equipCode }) -join '、')）" (-not (@($r.data.records) | Where-Object { $_.equipCode -eq 'R-201' -or $_.equipCode -like '*-T*' }))
    $r = Call GET "/safety/equipment/$vid" $null $tTest; Show $r
    Check '看不到演示企业的设备' ($r.code -ne 200)
    $r = Call POST '/safety/equipment' @{ equipCode = "SV-T$suffix"; name = '测试冶金的同号设备'; equipType = 'OTHER'; location = 'x' } $tTest
    Check '不同企业的设备位号可以相同' ($r.code -eq 200)
    if ($r.code -eq 200) { Call PUT "/safety/equipment/$($r.data)/status" @{ status = 'SCRAPPED'; reason = '自测数据' } $tTest | Out-Null }
}
Call PUT "/safety/equipment/$vid/status" @{ status = 'SCRAPPED'; reason = '自测数据' } $tZhang | Out-Null
Call PUT "/safety/equipment/$xid/status" @{ status = 'SCRAPPED'; reason = '自测数据' } $tZhang | Out-Null

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
