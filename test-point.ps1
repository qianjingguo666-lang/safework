# 隐患随手拍积分自测：权限、演示数据、隐患确认和按期整改自动加分、手工调整校验、兑换 / 退回 / 发放（记入安全投入）、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-point.ps1



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
$tWang  = Login 'test' 'wangwu'
$wangId = (Call GET '/auth/me' $null $tWang).data.user.id
function Mine($token) { return (Call GET '/safety/point/mine' $null $token).data }

Title '1. 权限'
$r = Call GET '/safety/point/overview' $null $tLisi; Show $r
Check '作业负责人不能看积分管理' ($r.code -ne 200)
$r = Call POST '/safety/point/adjust' @{ userId = 103; points = 10; reason = '越权' } $tLisi; Show $r
Check '作业负责人不能调整积分' ($r.code -ne 200)
$m = Mine $tLisi
Check "员工能看自己的积分：李四余额 $($m.balance)，今年第 $($m.yearRank) 名" ($null -ne $m -and $null -ne $m.balance)
Check '员工能看排行榜和奖品' ((Call GET '/safety/point/ranking?period=YEAR' $null $tLisi).code -eq 200 -and (Call GET '/safety/point/rewards' $null $tLisi).code -eq 200)

Title '2. 演示数据'
$rk = (Call GET '/safety/point/ranking?period=YEAR' $null $tZhang).data
foreach ($x in $rk.rows) { Write-Host "    第 $($x.rank) 名 $($x.userName) $($x.points) 分，确认隐患 $($x.hazards) 条" -ForegroundColor DarkGray }
Check "今年排行榜有 $(@($rk.rows).Count) 人，按得分从高到低" (@($rk.rows).Count -ge 3 -and [int]$rk.rows[0].points -ge [int]$rk.rows[-1].points)
$last = @($m.records)[0]
Check "李四余额 = 最新一条流水的变动后余额（$($last.balanceAfter)）" ([int]$m.balance -eq [int]$last.balanceAfter)

Title '3. 隐患确认、按期整改自动加分（测试企业）'
$a0 = (Mine $tTest).balance
$w0 = (Mine $tWang).balance
$hid = (Call POST '/safety/hazard' @{ title = "积分自测 $(Get-Random)"; category = '作业环境'; level = 'GENERAL'; location = '自测' } $tTest).data
Check '上报后还没确认，不加分' ([int](Mine $tTest).balance -eq [int]$a0)
$deadline = (Get-Date).AddDays(3).ToString('yyyy-MM-dd HH:mm:ss')
Call POST "/safety/hazard/$hid/assign" @{ rectifierId = $wangId; deadline = $deadline } $tTest | Out-Null
$a1 = (Mine $tTest).balance
Check "安全员确认（指派）后，上报人 +10（$a0 → $a1）" ([int]$a1 -eq [int]$a0 + 10)
Call POST "/safety/hazard/$hid/rectify" @{ rectifyMeasure = '积分自测：已整改' } $tWang | Out-Null
Call POST "/safety/hazard/$hid/verify-pass" @{ comment = '合格' } $tTest | Out-Null
$w1 = (Mine $tWang).balance
Check "按期整改、验收通过，整改人 +5（$w0 → $w1）" ([int]$w1 -eq [int]$w0 + 5)
$rec = @((Call GET "/safety/point/records?userId=$wangId&sourceType=HAZARD_RECTIFY" $null $tTest).data.records | Where-Object { $_.sourceId -eq $hid })
Check "流水里有这条：$($rec[0].reason)" ($rec.Count -eq 1)

Title '4. 手工调整'
$r = Call POST '/safety/point/adjust' @{ userId = $wangId; points = 0; reason = 'x' } $tTest; Show $r
Check '调整 0 分被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/point/adjust' @{ userId = $wangId; points = 300; reason = 'x' } $tTest; Show $r
Check '一次不能超过 200 分' ($r.code -ne 200)
$r = Call POST '/safety/point/adjust' @{ userId = $wangId; points = -199; reason = '扣成负数' } $tTest; Show $r
Check "余额 $w1 分时不能扣 199 分（不能扣成负数）" ([int]$w1 -ge 199 -or $r.code -ne 200)
$r = Call POST '/safety/point/adjust' @{ userId = $wangId; points = 20; reason = '积分自测：合理化建议' } $tTest
$w2 = (Mine $tWang).balance
Check "加 20 分（$w1 → $w2）" ($r.code -eq 200 -and [int]$w2 -eq [int]$w1 + 20)

Title '5. 兑换、退回、发放'
$need = [int]$w2
$rid = (Call POST '/safety/point/rewards' @{ name = "自测奖品 $(Get-Random)"; points = $need; price = 12.5; stock = 1 } $tTest).data
Check "新增奖品：$need 分、12.5 元、库存 1" ($rid)
$r = Call POST "/safety/point/rewards/$rid/redeem" $null $tWang; Show $r
$d1 = $r.data
Check "王五兑换成功，余额变成 $((Mine $tWang).balance)" ($r.code -eq 200 -and [int](Mine $tWang).balance -eq 0)
$r = Call POST "/safety/point/rewards/$rid/redeem" $null $tWang; Show $r
Check '库存没了 / 积分不够，不能再兑换' ($r.code -ne 200)
$r = Call PUT "/safety/point/redeems/$d1/handle" @{ issue = $false } $tTest; Show $r
Check '退回要写原因' ($r.code -ne 200)
$r = Call PUT "/safety/point/redeems/$d1/handle" @{ issue = $false; remark = '自测退回' } $tTest
$rw = @((Call GET '/safety/point/rewards/all' $null $tTest).data | Where-Object { $_.reward.id -eq $rid })[0]
Check "退回后积分（$((Mine $tWang).balance)）和库存（$($rw.reward.stock)）都还回去了" ($r.code -eq 200 -and [int](Mine $tWang).balance -eq $need -and [int]$rw.reward.stock -eq 1)
$d2 = (Call POST "/safety/point/rewards/$rid/redeem" $null $tWang).data
$r = Call PUT "/safety/point/redeems/$d2/handle" @{ issue = $true; remark = '自测发放' } $tTest
$red = @((Call GET '/safety/point/redeems' $null $tTest).data | Where-Object { $_.id -eq $d2 })[0]
Check "发放成功，记入安全投入（支出记录 $($red.costExpenseId)）" ($r.code -eq 200 -and $red.status -eq 'ISSUED' -and $red.costExpenseId)
$ex = (Call GET "/safety/cost/expense/$($red.costExpenseId)" $null $tTest).data
Check "安全投入里多了一笔：$($ex.title) $($ex.amount) 元（$($ex.category)）" ([double]$ex.amount -eq 12.5 -and $ex.category -eq 'TRAINING')
$r = Call PUT "/safety/point/redeems/$d2/handle" @{ issue = $true } $tTest; Show $r
Check '已处理的不能再处理' ($r.code -ne 200)
Call PUT "/safety/cost/expense/$($red.costExpenseId)/void" @{ reason = '积分自测数据' } $tTest | Out-Null
Call PUT "/safety/point/rewards/$rid/status?status=0" $null $tTest | Out-Null

Title '6. 租户隔离'
$names = @((Call GET '/safety/point/ranking?period=YEAR' $null $tTest).data.rows | ForEach-Object { $_.userName })
Check "测试企业的排行榜里没有演示企业的人（$($names -join '、')）" (@($names | Where-Object { $_ -like '*张三*' -or $_ -like '*李四*' -or $_ -like '*赵六*' }).Count -eq 0)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
