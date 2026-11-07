# 工伤管理自测：权限、登记校验、30 日申请认定倒计时、状态流转、劳动能力鉴定和一次性伤残补助金、停工留薪期、费用台账、结案后锁定、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-injury.ps1



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
function DaysAgo($n) { return (Get-Date).Date.AddDays(-$n).AddHours(10).ToString('yyyy-MM-dd HH:mm:ss') }
function DateAgo($n) { return (Get-Date).Date.AddDays(-$n).ToString('yyyy-MM-dd') }
function NewInjury($extra) {
    $b = @{ employerType = 'CONTRACTOR'; injuredName = '自测伤员'; employerName = "自测承包商 $(Get-Random)"; injuryTime = (DaysAgo 2); location = '自测地点'; description = '自测经过'; injuryPart = '左手'; injuryNature = '割伤' }
    foreach ($k in $extra.Keys) { $b[$k] = $extra[$k] }
    return $b
}
function InjuryOf($cid) { return (Call GET "/safety/injury/$cid" $null $tZhang).data }

Title '1. 权限'
$r = Call GET '/safety/injury/page' $null $tLisi; Show $r
Check '作业负责人看不到工伤（涉及个人信息）' ($r.code -ne 200)
$r = Call POST '/safety/injury' (NewInjury @{}) $tLisi; Show $r
Check '作业负责人不能登记工伤' ($r.code -ne 200)
$ov = (Call GET '/safety/injury/overview' $null $tZhang).data
Check "概况：今年 $($ov.yearCases) 人，待申请认定 $($ov.pendingApply) 人" ($null -ne $ov)

Title '2. 登记校验'
$r = Call POST '/safety/injury' (NewInjury @{ injuryTime = (Get-Date).AddDays(1).ToString('yyyy-MM-dd HH:mm:ss') }) $tZhang; Show $r
Check '受伤时间不能晚于现在' ($r.code -ne 200)
$r = Call POST '/safety/injury' (NewInjury @{ employerName = '' }) $tZhang; Show $r
Check '承包商人员要填所在单位' ($r.code -ne 200)
$r = Call POST '/safety/injury' (NewInjury @{ employerType = 'OWN'; injuredUserId = $null }) $tZhang; Show $r
Check '本企业员工要选择账号' ($r.code -ne 200)
$r = Call POST '/safety/injury' (NewInjury @{ employerType = 'OWN'; injuredUserId = 104 }) $tZhang
$own = $r.data
$od = InjuryOf $own
Check "本企业员工自动带出姓名和部门：$($od.injury.injuredName) / $($od.injury.deptName)" ($od.injury.injuredName -like '*赵六*' -and $od.injury.employerName -eq '本企业')

Title '3. 30 日申请认定倒计时'
Check "2 天前受伤：$($od.deadline.dueDate) 截止，还剩 $($od.deadline.daysLeft) 天" ($od.deadline.daysLeft -eq 28 -and $od.deadline.state -eq 'OK')
$soon = (Call POST '/safety/injury' (NewInjury @{ injuryTime = (DaysAgo 27) }) $tZhang).data
$sd = InjuryOf $soon
Check "27 天前受伤：还剩 $($sd.deadline.daysLeft) 天，状态 SOON" ($sd.deadline.daysLeft -eq 3 -and $sd.deadline.state -eq 'SOON')
$late = (Call POST '/safety/injury' (NewInjury @{ injuryTime = (DaysAgo 40) }) $tZhang).data
$ld = InjuryOf $late
Check "40 天前受伤：超期 $(-$ld.deadline.daysLeft) 天" ($ld.deadline.daysLeft -eq -10 -and $ld.deadline.state -eq 'OVERDUE')
$ov2 = (Call GET '/safety/injury/overview' $null $tZhang).data
Check "概况里的超期数加 1（$($ov.overdueApply) -> $($ov2.overdueApply)）" ($ov2.overdueApply -eq $ov.overdueApply + 1)
$scr = (Call GET '/screen/overview' $null $tZhang).data
Check '大屏预警里有工伤认定申请超期' (@($scr.warnings | Where-Object { $_.label -like '工伤认定申请已超期*' }).Count -eq 1)

Title '4. 状态流转'
$r = Call POST "/safety/injury/$late/decide" @{ recognized = $true; decisionDate = (DateAgo 0) } $tZhang; Show $r
Check '还没申请认定，不能登记认定结果' ($r.code -ne 200)
$r = Call POST "/safety/injury/$late/apply" @{ applyDate = (DateAgo 41) } $tZhang; Show $r
Check '申请日期不能早于受伤日期' ($r.code -ne 200)
$r = Call POST "/safety/injury/$late/apply" @{ applyDate = (DateAgo 1); applyNo = 'ZC-001' } $tZhang
Check '登记已申请认定' ($r.code -eq 200)
$ld = InjuryOf $late
Check '申请后倒计时结束（DONE）' ($ld.deadline.state -eq 'DONE' -and $ld.injury.status -eq 'APPLIED')
$r = Call PUT "/safety/injury/$late" (NewInjury @{}) $tZhang; Show $r
Check '申请认定后不能再修改基本信息' ($r.code -ne 200)
$r = Call POST "/safety/injury/$late/decide" @{ recognized = $false; decisionDate = (DateAgo 0); note = '自测不予认定' } $tZhang
Check '不予认定' ($r.code -eq 200 -and (InjuryOf $late).injury.status -eq 'NOT_RECOGNIZED')
$r = Call POST "/safety/injury/$late/appraise" @{ grade = 9; appraiseDate = (DateAgo 0) } $tZhang; Show $r
Check '不予认定的不能做劳动能力鉴定' ($r.code -ne 200)
$r = Call POST "/safety/injury/$late/close" @{ note = '自测结案' } $tZhang
Check '不予认定的可以结案' ($r.code -eq 200)

Title '5. 劳动能力鉴定、停工留薪期、费用'
Call POST "/safety/injury/$own/apply" @{ applyDate = (DateAgo 1) } $tZhang | Out-Null
Call POST "/safety/injury/$own/decide" @{ recognized = $true; decisionDate = (DateAgo 0); decisionNo = 'ZC-RD-001' } $tZhang | Out-Null
$r = Call POST "/safety/injury/$own/appraise" @{ grade = 11; appraiseDate = (DateAgo 0) } $tZhang; Show $r
Check '伤残等级只能 0 ~ 10' ($r.code -ne 200)
$r = Call POST "/safety/injury/$own/appraise" @{ grade = 9; appraiseDate = (DateAgo 0); monthlyWage = 5000 } $tZhang
$od = InjuryOf $own
Check "九级伤残：$($od.lumpSumMonths) 个月 x 5000 = $($od.lumpSum) 元" ($od.gradeLabel -eq '九级伤残' -and $od.lumpSumMonths -eq 9 -and [double]$od.lumpSum -eq 45000)
$r = Call PUT "/safety/injury/$own/leave" @{ leaveStart = (DateAgo 2); leaveEnd = (Get-Date).AddMonths(13).ToString('yyyy-MM-dd') } $tZhang; Show $r
Check '停工留薪期一般不超过 12 个月' ($r.code -ne 200)
$r = Call PUT "/safety/injury/$own/leave" @{ leaveStart = (DateAgo 2); leaveEnd = (Get-Date).AddMonths(13).ToString('yyyy-MM-dd'); extended = $true } $tZhang
Check '经确认延长可以超过 12 个月' ($r.code -eq 200)
$r = Call PUT "/safety/injury/$own/leave" @{ leaveStart = (DateAgo 3); leaveEnd = (Get-Date).AddMonths(2).ToString('yyyy-MM-dd') } $tZhang; Show $r
Check '停工留薪期不能早于受伤日期' ($r.code -ne 200)
$r = Call POST "/safety/injury/$own/cost" @{ category = 'MEDICAL'; amount = 0; payer = 'FUND'; payDate = (DateAgo 0) } $tZhang; Show $r
Check '金额必须大于 0' ($r.code -ne 200)
$r = Call POST "/safety/injury/$own/cost" @{ category = 'MEDICAL'; amount = 100; payer = 'FUND'; payDate = (DateAgo 5) } $tZhang; Show $r
Check '支付日期不能早于受伤日期' ($r.code -ne 200)
$c1 = (Call POST "/safety/injury/$own/cost" @{ category = 'MEDICAL'; amount = 1234.5; payer = 'FUND'; payDate = (DateAgo 0) } $tZhang).data
$c2 = (Call POST "/safety/injury/$own/cost" @{ category = 'LEAVE_WAGE'; amount = 800; payer = 'COMPANY'; payDate = (DateAgo 0) } $tZhang).data
$c3 = (Call POST "/safety/injury/$own/cost" @{ category = 'OTHER'; amount = 1; payer = 'COMPANY'; payDate = (DateAgo 0) } $tZhang).data
Call DELETE "/safety/injury/cost/$c3" $null $tZhang | Out-Null
$od = InjuryOf $own
Check "费用：基金 $($od.fundTotal)、单位 $($od.companyTotal)（删除的那笔不算）" ([double]$od.fundTotal -eq 1234.5 -and [double]$od.companyTotal -eq 800 -and @($od.costs).Count -eq 2)

Title '6. 结案后锁定、租户隔离'
Call POST "/safety/injury/$own/close" @{ note = '自测结案' } $tZhang | Out-Null
$r = Call POST "/safety/injury/$own/cost" @{ category = 'OTHER'; amount = 1; payer = 'COMPANY'; payDate = (DateAgo 0) } $tZhang; Show $r
Check '结案后不能再登记费用' ($r.code -ne 200)
$r = Call DELETE "/safety/injury/cost/$c1" $null $tZhang; Show $r
Check '结案后不能删除费用' ($r.code -ne 200)
$r = Call GET "/safety/injury/$own" $null $tTest
Check '别的企业看不到' ($r.code -ne 200)
# 收尾：把 27 天前那条也走完，避免本地留下待申请的自测数据
Call POST "/safety/injury/$soon/apply" @{ applyDate = (DateAgo 0) } $tZhang | Out-Null
Call POST "/safety/injury/$soon/decide" @{ recognized = $false; decisionDate = (DateAgo 0) } $tZhang | Out-Null
Call POST "/safety/injury/$soon/close" @{ note = '自测结案' } $tZhang | Out-Null

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
