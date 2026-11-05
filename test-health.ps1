# 职业健康自测：权限和隐私、岗位接害生成台账、周期取最短、体检校验、异常待处理和处理、禁忌证标记、手机首页、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-health.ps1

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
$tZhao  = Login 'demo' 'zhaoliu'
$tTest  = Login 'test' 'admin'
$lisiId = (Call GET '/auth/me' $null $tLisi).data.user.id
$zhaoId = (Call GET '/auth/me' $null $tZhao).data.user.id
$tag = Get-Random -Minimum 1000 -Maximum 9999
$today = (Get-Date).ToString('yyyy-MM-dd')

Title '1. 权限和隐私'
$ov = (Call GET '/safety/health/overview' $null $tZhang).data
Check "管理员看统计：接害 $($ov.exposed) 人，逾期 $($ov.overdue)，从未体检 $($ov.never)，30 天内到期 $($ov.dueSoon)，异常未处理 $($ov.abnormalOpen)，禁忌证在岗 $($ov.contraindication)" ([int]$ov.exposed -ge 1)
$r = Call GET '/safety/health/ledger?size=200' $null $tLisi
Check '作业负责人只能看到自己的台账' ($r.code -eq 200 -and (@($r.data.records | Where-Object { $_.userId -ne $lisiId })).Count -eq 0)
$r = Call GET "/safety/health/exam/page?size=100&userId=$zhaoId" $null $tLisi
Check '作业负责人查别人的体检记录：只返回自己的（体检结果是个人隐私）' ($r.code -eq 200 -and (@($r.data.records | Where-Object { $_.userId -ne $lisiId })).Count -eq 0)
$r = Call POST '/safety/health/exam' @{ userId = $lisiId; examType = 'ON'; examDate = $today; conclusion = 'NORMAL' } $tLisi; Show $r
Check '作业负责人不能登记体检（403）' ($r.code -eq 403)

Title '2. 危害因素和岗位接害'
$r = Call POST '/safety/health/factor' @{ name = "自测苯$tag"; category = 'CHEMICAL'; cycleMonths = 2 } $tZhang; Show $r
Check '体检周期 2 个月被拒绝（3 ~ 36）' ($r.code -ne 200)
$r = Call POST '/safety/health/factor' @{ name = "自测苯$tag"; category = 'CHEMICAL'; cycleMonths = 6; limitValue = 'PC-TWA 6 mg/m³'; contraindication = '造血系统疾病' } $tZhang
$fid = $r.data
Check '新增危害因素：体检周期 6 个月' ($r.code -eq 200 -and $fid)
$item = @((Call GET '/safety/ppe/item/list?enabled=true' $null $tZhang).data)[0]
$r = Call POST '/safety/ppe/post' @{ name = "自测接害岗位$tag"; userIds = @($lisiId, $zhaoId); standards = @(@{ itemId = $item.id; quantity = 1 }) } $tZhang
$postId = $r.data
Check '新建岗位（劳保模块）：李四、赵六' ($r.code -eq 200 -and $postId)
$r = Call PUT "/safety/health/post/$postId/factors" @{ factorIds = @($fid) } $tZhang
Check '设置岗位接触自测苯' ($r.code -eq 200)
$rows = @((Call GET "/safety/health/ledger?postId=$postId&size=50" $null $tZhang).data.records)
Check "台账出现这个岗位的 $($rows.Count) 人" ($rows.Count -eq 2)
$lisi = @($rows | Where-Object { $_.userId -eq $lisiId })[0]
Check "李四同时在罐区（12 个月）和自测岗位（6 个月），体检周期取最短：$($lisi.cycleMonths) 个月" ([int]$lisi.cycleMonths -eq 6)
$r = Call PUT "/safety/health/factor/$fid/status?status=0" $null $tZhang; Show $r
Check '有岗位接触时不能停用' ($r.code -ne 200)

Title '3. 体检校验'
$future = (Get-Date).AddDays(2).ToString('yyyy-MM-dd')
$r = Call POST '/safety/health/exam' @{ userId = $lisiId; examType = 'ON'; examDate = $future; conclusion = 'NORMAL' } $tZhang; Show $r
Check '体检日期晚于今天被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/health/exam' @{ userId = $lisiId; examType = 'ON'; examDate = $today; conclusion = 'RECHECK' } $tZhang; Show $r
Check '需要复查但没填复查日期被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/health/exam' @{ userId = $lisiId; examType = 'ON'; examDate = $today; conclusion = 'NORMAL'; recheckDate = $future } $tZhang; Show $r
Check '结论正常却填了复查日期被拒绝' ($r.code -ne 200)

Title '4. 登记体检'
$msg0 = (Call GET '/message/unread' $null $tLisi).data.total
$r = Call POST '/safety/health/exam' @{ userId = $lisiId; examType = 'ON'; examDate = $today; conclusion = 'NORMAL'; org = '自测体检中心' } $tZhang
Check '李四在岗体检正常' ($r.code -eq 200)
$lisi = @((Call GET "/safety/health/ledger?postId=$postId&size=50" $null $tZhang).data.records | Where-Object { $_.userId -eq $lisiId })[0]
$expect = (Get-Date).AddMonths(6).ToString('yyyy-MM-dd')
Check "李四状态正常，下次体检 $($lisi.nextExamDate)（6 个月后）" ($lisi.state -eq 'OK' -and $lisi.nextExamDate -eq $expect)
$msg1 = (Call GET '/message/unread' $null $tLisi).data.total
Check "本人收到体检结果通知（未读 $msg0 → $msg1）" ([int]$msg1 -ge [int]$msg0 + 1)
$exam = @((Call GET '/safety/health/exam/page?size=5' $null $tLisi).data.records)[0]
Check "体检记录保存了当时接触的危害因素：$($exam.factors)" ($exam.factors -like "*自测苯$tag*")

$open0 = (Call GET '/safety/health/overview' $null $tZhang).data.abnormalOpen
$r = Call POST '/safety/health/exam' @{ userId = $zhaoId; examType = 'ON'; examDate = $today; conclusion = 'CONTRAINDICATION'; detail = '血小板减少' } $tZhang
$eid = $r.data
Check '赵六在岗体检：职业禁忌证' ($r.code -eq 200 -and $eid)
$zhao = @((Call GET "/safety/health/ledger?postId=$postId&size=50" $null $tZhang).data.records | Where-Object { $_.userId -eq $zhaoId })[0]
Check '台账标记“禁忌证，应调岗”' ($zhao.contraindication -eq $true)
$ov2 = (Call GET '/safety/health/overview' $null $tZhang).data
Check "异常未处理 $open0 → $($ov2.abnormalOpen)，禁忌证仍在岗 $($ov2.contraindication)" ([int]$ov2.abnormalOpen -eq [int]$open0 + 1 -and [int]$ov2.contraindication -ge 1)
$r = Call POST "/safety/health/exam/$eid/handle" @{ measure = '' } $tZhang; Show $r
Check '处理措施不能为空' ($r.code -ne 200)
$r = Call POST "/safety/health/exam/$eid/handle" @{ measure = '已调离苯作业岗位，安排到仓库' } $tZhang
Check '登记处理措施' ($r.code -eq 200)
$r = Call POST "/safety/health/exam/$eid/handle" @{ measure = '重复处理' } $tZhang; Show $r
Check '不能重复处理' ($r.code -ne 200)
$ov3 = (Call GET '/safety/health/overview' $null $tZhang).data
Check "处理后异常未处理恢复 $($ov3.abnormalOpen)" ([int]$ov3.abnormalOpen -eq [int]$open0)

Title '5. 我的职业健康和手机首页'
$mine = (Call GET '/safety/health/mine' $null $tLisi).data
Check "李四的“我的职业健康”：接触 $($mine.ledger.factors)，体检记录 $(@($mine.exams).Count) 条" ($null -ne $mine.ledger -and @($mine.exams).Count -ge 1)
$mh = (Call GET '/mobile/home' $null $tLisi).data
Check "手机首页显示职业体检（状态 $($mh.healthState)）" ($null -ne $mh.health -and $mh.healthState -eq 'OK')

Title '6. 租户隔离'
$r = Call GET '/safety/health/factor/list' $null $tTest
Check '别的企业看不到这个危害因素' ($r.code -ne 200 -or (@($r.data | Where-Object { $_.id -eq $fid })).Count -eq 0)
$r = Call GET '/safety/health/exam/page?size=100' $null $tTest
Check '别的企业看不到演示企业的体检记录' ($r.code -ne 200 -or (@($r.data.records | Where-Object { $_.userId -eq $lisiId })).Count -eq 0)

Title '7. 清理'
Call PUT "/safety/health/post/$postId/factors" @{ factorIds = @() } $tZhang | Out-Null
Call PUT "/safety/ppe/post/$postId/status?status=0" $null $tZhang | Out-Null
Call PUT "/safety/health/factor/$fid/status?status=0" $null $tZhang | Out-Null
Write-Host '  已去掉自测岗位的危害因素、停用自测岗位和危害因素（体检记录保留，李四、赵六的演示数据状态会变化）' -ForegroundColor DarkGray

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
