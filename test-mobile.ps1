# 手机端自测：首页待办数按权限返回、隐患闭环时待办数实时变化、“只看我的”筛选
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-mobile.ps1

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
$zhangId = (Call GET '/auth/me' $null $tZhang).data.user.id
$lisiId  = (Call GET '/auth/me' $null $tLisi).data.user.id

Title '1. 首页待办数按权限返回'
$r = Call GET '/mobile/home' $null $null; Show $r
Check '未登录不能访问' ($r.code -eq 401)
$hz = (Call GET '/mobile/home' $null $tZhang).data
Write-Host "  安全员：待我审批 $($hz.approve)、我的作业 $($hz.working)、待我整改 $($hz.rectify)、待指派 $($hz.assign)、待验收 $($hz.verify)、今日巡检 $($hz.riskDue)、未读 $($hz.unread)" -ForegroundColor DarkGray
Check '安全员有审批、指派、验收待办数' ($null -ne $hz.approve -and $null -ne $hz.assign -and $null -ne $hz.verify)
$hl = (Call GET '/mobile/home' $null $tLisi).data
Write-Host "  作业负责人：待我审批 $($hl.approve)、我的作业 $($hl.working)、待我整改 $($hl.rectify)（逾期 $($hl.rectifyOverdue)）、待指派 $($hl.assign)、未读 $($hl.unread)" -ForegroundColor DarkGray
Check '作业负责人没有审批权限，待我审批返回空（手机上不显示这一项）' ($null -eq $hl.approve)
Check '作业负责人没有指派权限，待指派、待验收返回空' ($null -eq $hl.assign -and $null -eq $hl.verify)
Check '作业负责人有“我的作业”和“待我整改”' ($null -ne $hl.working -and $null -ne $hl.rectify)

Title '2. 隐患闭环时待办数实时变化'
$title = "手机端自测隐患$(Get-Random -Minimum 1000 -Maximum 9999)"
$hid = (Call POST '/safety/hazard' @{ title = $title; category = '作业环境'; level = 'GENERAL'; location = '罐区' } $tLisi).data
$h1 = (Call GET '/mobile/home' $null $tZhang).data
Check "作业负责人上报后，安全员的待指派 $($hz.assign) → $($h1.assign)" ([int]$h1.assign -eq [int]$hz.assign + 1)

$deadline = (Get-Date).AddDays(3).ToString('yyyy-MM-dd HH:mm:ss')
$r = Call POST "/safety/hazard/$hid/assign" @{ rectifierId = $lisiId; deadline = $deadline } $tZhang; Show $r
$l2 = (Call GET '/mobile/home' $null $tLisi).data
Check "指派给作业负责人后，他的待我整改 $($hl.rectify) → $($l2.rectify)" ([int]$l2.rectify -eq [int]$hl.rectify + 1)
$mine = (Call GET "/safety/hazard/page?mine=true&status=RECTIFYING&size=100" $null $tLisi).data
Check '作业负责人“待我整改”列表里有这条' ((@($mine.records | Where-Object { $_.id -eq $hid })).Count -eq 1)
$zm = (Call GET "/safety/hazard/page?mine=true&status=RECTIFYING&size=100" $null $tZhang).data
Check '安全员能看全部隐患，但“待我整改”里没有别人负责的这条' ((@($zm.records | Where-Object { $_.id -eq $hid })).Count -eq 0)
Check '“待我整改”里每一条的整改人都是自己' ((@($mine.records | Where-Object { $_.rectifierId -ne $lisiId })).Count -eq 0)

$r = Call POST "/safety/hazard/$hid/rectify" @{ rectifyMeasure = '已紧固' } $tLisi; Show $r
$l3 = (Call GET '/mobile/home' $null $tLisi).data
$h3 = (Call GET '/mobile/home' $null $tZhang).data
Check "提交整改后，待我整改 $($l2.rectify) → $($l3.rectify)" ([int]$l3.rectify -eq [int]$hl.rectify)
Check "安全员的待验收 $($hz.verify) → $($h3.verify)" ([int]$h3.verify -eq [int]$hz.verify + 1)
Call POST "/safety/hazard/$hid/verify-pass" @{ comment = '测试验收' } $tZhang | Out-Null
$h4 = (Call GET '/mobile/home' $null $tZhang).data
Check "验收通过后待验收恢复 $($h4.verify)" ([int]$h4.verify -eq [int]$hz.verify)

Title '3. 作业票“只看我的”'
$all = (Call GET '/ticket/page?size=100' $null $tZhang).data
$mt = (Call GET '/ticket/page?mine=true&size=100' $null $tZhang).data
Write-Host "  安全员：全部 $($all.total) 张，我申请 / 监护的 $($mt.total) 张" -ForegroundColor DarkGray
Check '“我的作业”不多于全部' ([int]$mt.total -le [int]$all.total)
Check '“我的作业”里每一张都是自己申请或监护的' ((@($mt.records | Where-Object { $_.applicantId -ne $zhangId -and $_.guardianId -ne $zhangId })).Count -eq 0)

Title '4. 租户隔离'
$r = Call GET '/mobile/home' $null $tTest
Check '测试冶金也能打开手机首页' ($r.code -eq 200)
$tp = (Call GET "/safety/hazard/page?size=100&keyword=$([uri]::EscapeDataString($title))" $null $tTest)
Check '测试冶金查不到演示企业的这条隐患' ($tp.code -ne 200 -or (@($tp.data.records)).Count -eq 0)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
