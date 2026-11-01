# 安全数据大屏自测：权限、整屏数据完整、预警来自各模块、新上报的隐患实时出现、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-screen.ps1

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

$tAdmin = Login 'demo' 'admin'
$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tTest  = Login 'test' 'admin'

Title '1. 权限'
$r = Call GET '/screen/overview' $null $tZhang
Check '安全员可以看大屏' ($r.code -eq 200)
$r = Call GET '/screen/overview' $null $tAdmin
Check '企业管理员可以看大屏' ($r.code -eq 200)
$r = Call GET '/screen/overview' $null $tLisi; Show $r
Check '作业负责人没有大屏权限' ($r.code -eq 403)
$menus = (Call GET '/auth/me' $null $tZhang).data.menus
Check '安全员的菜单里有【安全大屏】' (@($menus | Where-Object { $_.path -eq '/screen' }).Count -eq 1)

Title '2. 整屏数据'
$d = (Call GET '/screen/overview' $null $tZhang).data
$k = $d.kpi
Check "KPI：作业中 $($k.ticketInProgress)、待审批 $($k.ticketPending)、未闭环隐患 $($k.hazardOpen)、重大 $($k.hazardMajorOpen)、整改逾期 $($k.hazardOverdue)、应急响应 $($k.emergencyResponding)" ($null -ne $k.hazardOpen -and $null -ne $k.ticketInProgress)
$colors = $d.riskColors
Check "风险四色：红 $($colors.MAJOR)、橙 $($colors.HIGH)、黄 $($colors.GENERAL)、蓝 $($colors.LOW)" ($null -ne $colors.MAJOR -and ($colors.MAJOR + $colors.HIGH + $colors.GENERAL + $colors.LOW) -ge 1)
Check "隐患趋势 $(@($d.hazardTrend.dates).Count) 天" (@($d.hazardTrend.dates).Count -eq 14 -and @($d.hazardTrend.reported).Count -eq 14)
$labels = @($d.warnings | ForEach-Object { $_.label })
Write-Host "  预警：$((@($d.warnings) | ForEach-Object { $_.label + ' ' + $_.count }) -join '、')" -ForegroundColor DarkGray
Check '有设备检验逾期预警（演示数据 PG-201 压力表）' ($labels -contains '设备检验逾期')
Check '有人员证书过期预警（演示数据赵六的电工证复审逾期）' ($labels -contains '人员证书已过期')
Check '有承包商黑名单预警（演示数据速达劳务）' ($labels -contains '承包商黑名单')
$first = @($d.warnings)[0]
Check '红色预警排在前面' ($first.level -eq 'danger')
Check "最近动态 $(@($d.feed).Count) 条" (@($d.feed).Count -ge 1)
if ($null -ne $d.safeDays) { Write-Host "  安全运行 $($d.safeDays) 天" -ForegroundColor DarkGray }

Title '3. 实时性'
$title = "大屏自测隐患$(Get-Random -Minimum 1000 -Maximum 9999)"
$r = Call POST '/safety/hazard' @{ title = $title; category = '作业环境'; level = 'MAJOR'; location = '罐区' } $tLisi
$hid = $r.data
$d2 = (Call GET '/screen/overview' $null $tZhang).data
Check "上报重大隐患后，未闭环 $($k.hazardOpen) → $($d2.kpi.hazardOpen)、重大 $($k.hazardMajorOpen) → $($d2.kpi.hazardMajorOpen)" ($d2.kpi.hazardOpen -eq $k.hazardOpen + 1 -and $d2.kpi.hazardMajorOpen -eq $k.hazardMajorOpen + 1)
Check '最近动态第一条就是这条隐患' (@($d2.feed)[0].text -like "*$title*")
$before = [int]((@($d.hazardTrend.reported) | Measure-Object -Sum).Sum)
$after = [int]((@($d2.hazardTrend.reported) | Measure-Object -Sum).Sum)
$lastDay = @($d2.hazardTrend.dates)[-1]
Check "近 14 天上报总数 $before  $after（今天 $lastDay：$(@($d.hazardTrend.reported)[-1])  $(@($d2.hazardTrend.reported)[-1])）" ($after -eq $before + 1)
Call POST "/safety/hazard/$hid/revoke" @{ comment = '测试清理' } $tZhang | Out-Null

Title '4. 租户隔离'
$r = Call GET '/screen/overview' $null $tTest
if ($r.code -eq 403) {
    Write-Host '  （跳过：测试冶金管理员没有大屏菜单）' -ForegroundColor DarkGray
} else {
    Check '测试冶金看不到演示企业的承包商黑名单' (-not (@($r.data.warnings) | Where-Object { $_.label -eq '承包商黑名单' }))
    Check '测试冶金的动态里没有演示企业的隐患' (-not (@($r.data.feed) | Where-Object { $_.text -like "*$title*" }))
}

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
