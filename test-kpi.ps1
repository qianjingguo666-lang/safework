# 安全目标考核自测：权限、年度报告（8 个指标、计算一致性、亮灯、得分）、实际值随业务数据变化、目标设置校验、未来年度、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-kpi.ps1

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
$failed = 0

$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tTest  = Login 'test' 'admin'
$year = (Get-Date).Year
function Item($rep, $code) { return @($rep.items | Where-Object { $_.code -eq $code })[0] }

Title '1. 权限'
$r = Call GET '/safety/kpi/report' $null $tLisi; Show $r
Check '作业负责人不能看安全目标' ($r.code -ne 200)
$r = Call PUT '/safety/kpi/targets' @{ year = $year; items = @() } $tLisi; Show $r
Check '作业负责人不能设置目标' ($r.code -ne 200)

Title '2. 年度报告'
$rep = (Call GET "/safety/kpi/report?year=$year" $null $tZhang).data
Write-Host "  综合得分 $($rep.totalScore)，绿 $($rep.green) / 黄 $($rep.yellow) / 红 $($rep.red)，统计到 $($rep.asOf)" -ForegroundColor DarkGray
foreach ($i in $rep.items) { Write-Host ("    {0}：实际 {1}{2}，目标 {3}{2}，{4}，得分 {5}/{6}" -f $i.label, $i.actual, $i.unit, $i.target, $i.light, $i.score, $i.weight) -ForegroundColor DarkGray }
Check '8 个指标，权重合计 100' (@($rep.items).Count -eq 8 -and ([int](($rep.items | Measure-Object -Property weight -Sum).Sum)) -eq 100)
Check "统计到今天（$($rep.asOf)）" ($rep.asOf -eq (Get-Date).ToString('yyyy-MM-dd'))
$bad = @($rep.items | Where-Object { $null -ne $_.denominator -and [int]$_.denominator -gt 0 -and [math]::Abs([double]$_.actual - [math]::Round([double]$_.numerator * 100 / [double]$_.denominator, 1)) -gt 0.05 })
Check '比例类指标的实际值 = 分子 / 分母' ($bad.Count -eq 0)
$bad = @($rep.items | Where-Object { $null -ne $_.score -and [double]$_.score -gt [double]$_.weight })
Check '单项得分不超过权重' ($bad.Count -eq 0)
$acc = Item $rep 'ACCIDENT'
Check "伤亡事故起数：$($acc.actual) 起，目标不超过 $($acc.target)，$($acc.light)" (($acc.light -eq 'GREEN') -eq ([double]$acc.actual -le [double]$acc.target))
$eq = Item $rep 'EQUIP_ONTIME'
Check '设备检验及时率只有当前值，没有按月趋势' ($eq.snapshot -eq $true -and @($eq.months).Count -eq 0)
$hz = Item $rep 'HAZARD_CLOSE'
Check '有按月趋势：12 个月，未来的月份为空' (@($hz.months).Count -eq 12 -and ((Get-Date).Month -eq 12 -or $null -eq $hz.months[11]))

Title '3. 实际值随业务数据变化'
$before = (Item $rep 'HAZARD_CLOSE').denominator
$hid = (Call POST '/safety/hazard' @{ title = "安全目标自测 $(Get-Random)"; category = '安全管理'; level = 'GENERAL'; location = '办公楼' } $tZhang).data
$after = (Item (Call GET "/safety/kpi/report?year=$year" $null $tZhang).data 'HAZARD_CLOSE').denominator
Check "上报 1 条隐患，隐患闭环率的分母 $before → $after" ([int]$after -eq [int]$before + 1)
Call POST "/safety/hazard/$hid/revoke" @{ comment = '安全目标自测，撤销' } $tZhang | Out-Null
$back = (Item (Call GET "/safety/kpi/report?year=$year" $null $tZhang).data 'HAZARD_CLOSE').denominator
Check "撤销后不再统计，分母回到 $back" ([int]$back -eq [int]$before)

Title '4. 设置目标'
$old = @((Call GET "/safety/kpi/targets?year=$year" $null $tZhang).data)
$items = @($old | ForEach-Object { @{ code = $_.code; target = $_.target; weight = $_.weight } })
$items[0].weight = [int]$items[0].weight + 1
$r = Call PUT '/safety/kpi/targets' @{ year = $year; items = $items } $tZhang; Show $r
Check '权重合计不是 100 被拒绝' ($r.code -ne 200)
$items[0].weight = [int]$items[0].weight - 1
$items[0].target = 101
$r = Call PUT '/safety/kpi/targets' @{ year = $year; items = $items } $tZhang; Show $r
Check '比例类目标不能超过 100' ($r.code -ne 200)
$r = Call PUT '/safety/kpi/targets' @{ year = $year + 5; items = $items } $tZhang; Show $r
Check '只能设置去年、今年、明年' ($r.code -ne 200)
$items[0].target = 1
$r = Call PUT '/safety/kpi/targets' @{ year = $year; items = $items } $tZhang
$i0 = Item (Call GET "/safety/kpi/report?year=$year" $null $tZhang).data $items[0].code
Check "把“$($i0.label)”目标改成 1%：$(if ($null -eq $i0.actual) { '没有数据，不亮灯' } else { '亮 ' + $i0.light })" ($r.code -eq 200 -and ($null -eq $i0.actual -or $i0.light -eq 'GREEN'))
$restore = @($old | ForEach-Object { @{ code = $_.code; target = $_.target; weight = $_.weight } })
$r = Call PUT '/safety/kpi/targets' @{ year = $year; items = $restore } $tZhang
Check '恢复原来的目标' ($r.code -eq 200)

Title '5. 未来年度和租户隔离'
$next = (Call GET "/safety/kpi/report?year=$($year + 1)" $null $tZhang).data
Check '明年还没开始：都没有数据，没有总分' ($null -eq $next.totalScore -and @($next.items | Where-Object { $_.light -ne 'NO_DATA' }).Count -eq 0)
$t = Call GET "/safety/kpi/report?year=$year" $null $tTest
Check "别的企业看到自己的报告（没设置目标，用默认目标：$($t.data.defaultTargets)）" ($t.code -ne 200 -or $t.data.defaultTargets -eq $true)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
