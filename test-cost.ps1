# 安全投入台账自测：权限、应提金额（超额累退）、年度报表一致性、编制计划校验、登记支出与超预算提示、修改作废留痕、租户隔离、导出
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-cost.ps1


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
$today = (Get-Date).ToString('yyyy-MM-dd')
$tag = Get-Random -Maximum 99999
function CatRow($rep, $code) { return @($rep.categories | Where-Object { $_.code -eq $code })[0] }
function Report($token) { return (Call GET "/safety/cost/report?year=$year" $null $token).data }

Title '1. 权限'
$r = Call GET '/safety/cost/report' $null $tLisi; Show $r
Check '作业负责人不能看安全投入' ($r.code -ne 200)
$r = Call POST '/safety/cost/expense' @{ category = 'PPE'; title = '越权'; amount = 1; expenseDate = $today } $tLisi; Show $r
Check '作业负责人不能登记支出' ($r.code -ne 200)

Title '2. 应提金额（超额累退）'
$a = (Call GET '/safety/cost/accrue?revenue=800' $null $tZhang).data
Check "营业收入 800 万：800 × 4.5% = 36 万（实际 $($a.total)）" ([double]$a.total -eq 360000)
$a = (Call GET '/safety/cost/accrue?revenue=8000' $null $tZhang).data
Check "8000 万：1000 × 4.5% + 7000 × 2.25% = 202.5 万，分 2 档（实际 $($a.total)，$(@($a.lines).Count) 档）" ([double]$a.total -eq 2025000 -and @($a.lines).Count -eq 2)
$a = (Call GET '/safety/cost/accrue?revenue=200000' $null $tZhang).data
Check "20 亿：45 + 202.5 + 495 + 200 = 942.5 万，分 4 档（实际 $($a.total)）" ([double]$a.total -eq 9425000 -and @($a.lines).Count -eq 4)

Title '3. 演示企业的年度报表'
$rep = Report $tZhang
Write-Host "  可用 $($rep.available) = 结转 $($rep.plan.carryOver) + 应提 $($rep.plan.accrueAmount)；已用 $($rep.used)（$($rep.usageRate)%），余额 $($rep.balance)，时间进度 $($rep.timeProgress)%" -ForegroundColor DarkGray
foreach ($x in $rep.alerts) { Write-Host "    提醒：$x" -ForegroundColor DarkGray }
Check '已编制今年的计划' ($rep.hasPlan -eq $true)
Check '可用 = 上年结转 + 本年应提' ([math]::Abs([double]$rep.available - [double]$rep.plan.carryOver - [double]$rep.plan.accrueAmount) -lt 0.01)
Check '余额 = 可用 - 已用' ([math]::Abs([double]$rep.balance - ([double]$rep.available - [double]$rep.used)) -lt 0.01)
$catSum = ($rep.categories | Measure-Object -Property used -Sum).Sum
Check "10 个使用类别，合计 = 已用（$catSum）" (@($rep.categories).Count -eq 10 -and [math]::Abs([double]$catSum - [double]$rep.used) -lt 0.01)
Check '12 个月，未来的月份为空' (@($rep.months).Count -eq 12 -and ((Get-Date).Month -eq 12 -or $null -eq $rep.months[11].used))
$ppe = CatRow $rep 'PPE'
Check "劳动防护用品超预算（预算 $($ppe.budget)，已用 $($ppe.used)）" ($ppe.state -eq 'OVER' -and @($rep.alerts | Where-Object { $_ -like '*劳动防护用品*' }).Count -ge 1)
Check "有隐患整改支出（$($rep.hazardUsed) 元）" ([double]$rep.hazardUsed -gt 0)
$form = (Call GET "/safety/cost/plan?year=$year" $null $tZhang).data
Check "编制计划表单：建议的上年结转 = 去年余额（$($form.suggestedCarry)）" ([double]$form.suggestedCarry -eq 186400)

Title '4. 编制计划（测试企业）'
$r = Call PUT '/safety/cost/plan' @{ year = $year; revenue = 1200; carryOver = -1; budgets = @() } $tTest; Show $r
Check '上年结转不能是负数' ($r.code -ne 200)
$r = Call PUT '/safety/cost/plan' @{ year = $year; revenue = 1200; carryOver = 5000; budgets = @(@{ category = 'PPE'; amount = 1 }, @{ category = 'PPE'; amount = 2 }) } $tTest; Show $r
Check '同一个类别不能填两次' ($r.code -ne 200)
$r = Call PUT '/safety/cost/plan' @{ year = $year; revenue = 1200; carryOver = 5000; budgets = @(@{ category = 'PPE'; amount = 1000 }, @{ category = 'FACILITY'; amount = 9999999 }) } $tTest; Show $r
Check "预算超过可用额度：能保存，但有提示（$($r.data.warning)）" ($r.code -eq 200 -and $r.data.warning)
$r = Call PUT '/safety/cost/plan' @{ year = $year; revenue = 1200; carryOver = 5000; remark = '自测'; budgets = @(@{ category = 'PPE'; amount = 1000 }) } $tTest
Check '改成正常预算：没有提示' ($r.code -eq 200 -and -not $r.data.warning)
$t0 = Report $tTest
Check "应提 = 1000 × 4.5% + 200 × 2.25% = 49.5 万，可用 50 万（实际 $($t0.plan.accrueAmount) / $($t0.available)）" ([double]$t0.plan.accrueAmount -eq 495000 -and [double]$t0.available -eq 500000)
Check '测试企业看不到演示企业的支出' ([double]$t0.used -lt [double]$rep.used -or [double]$rep.used -eq 0)

Title '5. 登记支出'
$future = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$r = Call POST '/safety/cost/expense' @{ category = 'PPE'; title = '未来'; amount = 1; expenseDate = $future } $tTest; Show $r
Check '支出日期不能晚于今天' ($r.code -ne 200)
$r = Call POST '/safety/cost/expense' @{ category = 'XXX'; title = '类别'; amount = 1; expenseDate = $today } $tTest; Show $r
Check '使用类别不正确被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/cost/expense' @{ category = 'PPE'; title = '金额'; amount = 0; expenseDate = $today } $tTest; Show $r
Check '金额必须大于 0' ($r.code -ne 200)
$r = Call POST '/safety/cost/expense' @{ category = 'RISK_HAZARD'; title = '别人的隐患'; amount = 1; expenseDate = $today; hazardId = 2 } $tTest; Show $r
Check '不能关联别的企业的隐患' ($r.code -ne 200)

foreach ($old in @((Call GET "/safety/cost/expense/page?year=$year&status=1&size=100" $null $tTest).data.records)) { Call PUT "/safety/cost/expense/$($old.id)/void" @{ reason = 'cleanup' } $tTest | Out-Null }
$r1 = Call POST '/safety/cost/expense' @{ category = 'PPE'; title = "自测安全帽 $tag"; amount = 800; expenseDate = $today; payee = '自测劳保'; voucherNo = "ZC-$tag" } $tTest
Check "登记 800 元：没超预算，没有提示（编号 $($r1.data.id)）" ($r1.code -eq 200 -and -not $r1.data.warning)
$used0 = [double](CatRow (Report $tTest) 'PPE').used
$r2 = Call POST '/safety/cost/expense' @{ category = 'PPE'; title = "自测防护服 $tag"; amount = 300; expenseDate = $today } $tTest
Check "再登记 300 元：超预算提示（$($r2.data.warning)）" ($r2.code -eq 200 -and $r2.data.warning -like '*超过预算*')
$r3 = Call POST '/safety/cost/expense' @{ category = 'TRAINING'; title = "自测培训 $tag"; amount = 50; expenseDate = $today } $tTest
Check "没有预算的类别：提示没有安排预算" ($r3.code -eq 200 -and $r3.data.warning -like '*没有安排预算*')
$t1 = Report $tTest
Check "劳动防护用品状态变成超预算，已用增加 300（$used0 → $((CatRow $t1 'PPE').used)）" ((CatRow $t1 'PPE').state -eq 'OVER' -and [double](CatRow $t1 'PPE').used -eq $used0 + 300)
Check '安全教育培训：无预算支出' ((CatRow $t1 'TRAINING').state -eq 'UNPLANNED')

$p = (Call GET "/safety/cost/expense/page?year=$year&keyword=$tag&status=1" $null $tTest).data
Check "按关键词查到 3 笔，合计 1150（$($p.total) 笔，$($p.sum)）" ([int]$p.total -eq 3 -and [double]$p.sum -eq 1150)
$e = (Call GET "/safety/cost/expense/$($r1.data.id)" $null $tTest).data
Check "自动编号 AF 开头、登记人、年度（$($e.expenseNo)，$($e.recorderName)）" ($e.expenseNo -like 'AF*' -and $e.recorderName -and [int]$e.year -eq $year)
$r = Call GET "/safety/cost/expense/$($r1.data.id)" $null $tZhang
Check '演示企业看不到测试企业的支出' ($r.code -ne 200)

Title '6. 修改、作废'
$r = Call PUT "/safety/cost/expense/$($r2.data.id)" @{ category = 'PPE'; title = "自测防护服 $tag"; amount = 150; expenseDate = $today } $tTest
Check "改成 150 元后不超预算了" ($r.code -eq 200 -and -not $r.data.warning)
$f = (Call GET "/file/list?bizType=COST&bizId=$($r1.data.id)&tag=VOUCHER" $null $tTest).data
Check '有效的支出可以上传凭证' ($f.editable -eq $true)
$r = Call PUT "/safety/cost/expense/$($r1.data.id)/void" @{ reason = '' } $tTest; Show $r
Check '作废要填原因' ($r.code -ne 200)
foreach ($x in @($r1, $r2, $r3)) { Call PUT "/safety/cost/expense/$($x.data.id)/void" @{ reason = '自测数据，作废' } $tTest | Out-Null }
$e = (Call GET "/safety/cost/expense/$($r1.data.id)" $null $tTest).data
Check "作废留痕：状态 0，原因、作废人（$($e.voidReason)，$($e.voiderName)）" ([int]$e.status -eq 0 -and $e.voidReason -and $e.voiderName)
$r = Call PUT "/safety/cost/expense/$($r1.data.id)/void" @{ reason = '再作废' } $tTest; Show $r
Check '不能重复作废' ($r.code -ne 200)
$r = Call PUT "/safety/cost/expense/$($r1.data.id)" @{ category = 'PPE'; title = '改作废的'; amount = 1; expenseDate = $today } $tTest; Show $r
Check '作废的不能修改' ($r.code -ne 200)
$f = (Call GET "/file/list?bizType=COST&bizId=$($r1.data.id)&tag=VOUCHER" $null $tTest).data
Check '作废后不能再改凭证' ($f.editable -eq $false)
$t2 = Report $tTest
Check "作废的不计入已用（$($t2.used) = 自测前 $($t0.used)）" ([double]$t2.used -eq [double]$t0.used)
$p = (Call GET "/safety/cost/expense/page?year=$year&keyword=$tag" $null $tTest).data
Check '列表里还能看到作废的记录，合计只算有效的' ([int]$p.total -eq 3 -and [double]$p.sum -eq 0)

Title '7. 导出'
$resp = Invoke-WebRequest -Uri "$base/safety/cost/expense/export?year=$year" -Headers @{ Authorization = "Bearer $tZhang" } -UseBasicParsing -TimeoutSec 60
Check "导出 Excel（$($resp.RawContentLength) 字节）" ($resp.StatusCode -eq 200 -and $resp.RawContentLength -gt 1000 -and "$($resp.Headers['Content-Type'])" -like '*sheet*')

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
