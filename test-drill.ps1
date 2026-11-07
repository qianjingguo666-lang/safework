# 应急演练自测：安排（参演人员和角色校验）、签到时间窗、登记结果（实到人员）、评估打分（不适用折算、0.5 分、预案适宜性）、问题转隐患、年度演练计划、修改预案清除修订意见、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-drill.ps1



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
$tZhao  = Login 'demo' 'zhaoliu'
$tTest  = Login 'test' 'admin'
function Fmt($d) { $d.ToString('yyyy-MM-dd HH:mm:ss') }
function DrillOf($drillId, $token) { (Call GET "/safety/emergency/drill/$drillId" $null $token).data }
function MsgsAbout($token, $bizId) { ,@((Call GET '/message/page?size=100' $null $token).data.records | Where-Object { "$($_.bizId)" -eq "$bizId" }) }
function FullScores($extra) {
    $items = (Call GET '/safety/emergency/drill/options' $null $tZhang).data.scoreItems
    $list = @()
    foreach ($i in $items) {
        $s = $i.max
        if ($extra.ContainsKey($i.code)) { $s = $extra[$i.code] }
        $list += @{ code = $i.code; score = $s; remark = '' }
    }
    return ,$list
}
$now = Get-Date
$soon = $now.AddMinutes(30)
if ($soon.Date -ne $now.Date) { $soon = $now.Date.AddHours(23).AddMinutes(59) }

Title '1. 准备一份自测预案（现场处置方案，每年要演练 2 次）'
$code = "ZC-DR-$(Get-Random -Maximum 99999)"
$r = Call POST '/safety/emergency/plan' @{ planCode = $code; title = '自测有限空间救援现场处置方案'; planType = 'ONSITE'; accidentType = 'POISONING'; content = '1. 停止作业 2. 呼救 3. 佩戴空气呼吸器施救'; teamUserIds = @(102) } $tZhang
Check '新建预案' ($r.code -eq 200)
$planId = $r.data
$people = @(@{ userId = 102; role = 'COMMAND' }, @{ userId = 103; role = 'RESCUE' }, @{ userId = 104; role = 'EVACUATE' })
function NewDrill($extra) {
    $b = @{ planId = $planId; title = '自测救援实战演练'; drillType = 'LIVE'; planTime = (Fmt $soon); location = '污水站'; scenario = '自测情景'; participants = $people }
    foreach ($k in $extra.Keys) { $b[$k] = $extra[$k] }
    return $b
}

Title '2. 安排演练的校验'
$r = Call POST '/safety/emergency/drill' (NewDrill @{ participants = @(@{ userId = 103; role = 'BOSS' }) }) $tZhang; Show $r
Check '参演角色不正确' ($r.code -ne 200)
$r = Call POST '/safety/emergency/drill' (NewDrill @{ participants = @(@{ userId = 103; role = 'RESCUE' }, @{ userId = 103; role = 'COMMAND' }) }) $tZhang; Show $r
Check '参演人员不能重复' ($r.code -ne 200)
$r = Call POST '/safety/emergency/drill' (NewDrill @{ participants = @(@{ userId = 202; role = 'RESCUE' }) }) $tZhang; Show $r
Check '参演人员必须是本企业员工（不能选别的企业的人）' ($r.code -ne 200)
$r = Call POST '/safety/emergency/drill' (NewDrill @{ planTime = (Fmt $now.AddDays(-1)) }) $tZhang; Show $r
Check '计划时间不能早于今天' ($r.code -ne 200)
$r = Call POST '/safety/emergency/drill' (NewDrill @{}) $tLisi; Show $r
Check '作业负责人不能安排演练' ($r.code -ne 200)
$r = Call POST '/safety/emergency/drill' (NewDrill @{}) $tZhang
Check '安排演练（今天，3 名参演人员）' ($r.code -eq 200)
$did = $r.data
Check '参演人员收到通知' ((MsgsAbout $tZhao $did).Count -ge 1)
$later = (Call POST '/safety/emergency/drill' (NewDrill @{ title = '自测第二次演练'; planTime = (Fmt $now.Date.AddDays(3).AddHours(9)) }) $tZhang).data

Title '3. 签到'
$r = Call POST "/safety/emergency/drill/$did/sign" @{} $tAdmin; Show $r
Check '不在参演名单里不能签到' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$later/sign" @{} $tLisi; Show $r
Check '3 天后的演练今天不能签到' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/sign" @{} $tLisi
Check "李四签到 $($r.data)" ($r.code -eq 200)
$r2 = Call POST "/safety/emergency/drill/$did/sign" @{} $tLisi
Check '重复签到返回原来的签到时间' ($r2.code -eq 200 -and "$($r2.data)" -eq "$($r.data)")
$mine = @((Call GET '/safety/emergency/drill/mine' $null $tLisi).data | Where-Object { "$($_.drill.id)" -eq "$did" })
Check "我的演练里能看到：角色 $($mine[0].role)，已签到" ($mine.Count -eq 1 -and $mine[0].signTime)
$d = DrillOf $did $tZhang
Check "详情：参演 $(@($d.participants).Count) 人，可以做 $($d.actions -join ',')" (@($d.participants).Count -eq 3 -and $d.actions -contains 'COMPLETE')

Title '4. 登记结果'
$r = Call POST "/safety/emergency/drill/$did/evaluate" @{ scores = (FullScores @{}); planSuitable = $true } $tZhang; Show $r
Check '没登记结果不能评估' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/complete" @{ actualTime = (Fmt $now); participantCount = 5; summary = '自测'; attendedUserIds = @(101) } $tZhang; Show $r
Check '确认参加的人必须在名单里' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/complete" @{ actualTime = (Fmt $now); participantCount = 1; summary = '自测'; attendedUserIds = @(102, 103) } $tZhang; Show $r
Check '参演人数不能少于确认参加的人数' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/complete" @{ actualTime = (Fmt $now.AddHours(1)); participantCount = 10; summary = '自测' } $tZhang; Show $r
Check '实际时间不能晚于现在' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/complete" @{ actualTime = (Fmt $now); participantCount = 10; summary = '自测演练顺利'; problems = '三脚架慢'; attendedUserIds = @(102, 103) } $tZhang
Check '登记结果' ($r.code -eq 200)
$d = DrillOf $did $tZhang
$zhao = @($d.participants | Where-Object { "$($_.userId)" -eq '104' })[0]
Check "状态变成待评估，赵六登记为缺席（attended = $($zhao.attended)）" ($d.drill.status -eq 'DONE' -and $zhao.attended -eq 0)
$r = Call POST "/safety/emergency/drill/$did/cancel" @{} $tZhang; Show $r
Check '已登记结果的不能取消' ($r.code -ne 200)

Title '5. 评估打分'
$r = Call POST "/safety/emergency/drill/$did/evaluate" @{ scores = @((FullScores @{})[0..6]); planSuitable = $true } $tZhang; Show $r
Check '少一个评估项目不行' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/evaluate" @{ scores = (FullScores @{ REPORT = 8.3 }); planSuitable = $true } $tZhang; Show $r
Check '分数最小单位 0.5' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/evaluate" @{ scores = (FullScores @{ REPORT = 11 }); planSuitable = $true } $tZhang; Show $r
Check '不能超过满分' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/evaluate" @{ scores = (FullScores @{ REPORT = $null; COMMAND = $null; COMM = $null; MEDICAL = $null; RECOVERY = $null }); planSuitable = $true } $tZhang; Show $r
Check '适用项目至少 4 个' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/evaluate" @{ scores = (FullScores @{}); planSuitable = $false } $tZhang; Show $r
Check '预案不适宜要写修订意见' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/evaluate" @{ scores = (FullScores @{}); planSuitable = $true; problems = @(@{ category = 'XX'; description = 'x' }) } $tZhang; Show $r
Check '问题类别不正确' ($r.code -ne 200)
# 医疗救护、后期处置不适用，现场处置 15 / 20 => 75 / 80 = 93.8 分
$ev = @{ scores = (FullScores @{ MEDICAL = $null; RECOVERY = $null; DISPOSAL = 15 }); planSuitable = $false; reviseNote = '自测：补充救援三脚架存放位置'
         problems = @(@{ category = 'EQUIPMENT'; description = '自测：三脚架展开慢' }, @{ category = 'TRAINING'; description = '自测：气瓶压力检查不熟练' }) }
$r = Call POST "/safety/emergency/drill/$did/evaluate" $ev $tZhang
Check "评估：$($r.data.total) 分（$($r.data.grade)），按 80 分适用项目折算" ($r.code -eq 200 -and [double]$r.data.total -eq 93.8 -and $r.data.grade -eq '优秀')
$r = Call POST "/safety/emergency/drill/$did/evaluate" $ev $tZhang; Show $r
Check '不能重复评估' ($r.code -ne 200)
$p = (Call GET "/safety/emergency/plan/$planId" $null $tZhang).data
Check "预案标记为待修订：$($p.reviseNote)" ($p.reviseNote -like '*三脚架*')
Check '预案管理员收到修订提醒' ((MsgsAbout $tZhang $did | Where-Object { $_.title -like '预案需要修订*' }).Count -ge 1)

Title '6. 问题转隐患整改'
$d = DrillOf $did $tZhang
$problemId = $d.problems[0].problem.id
Check "评估后有 $(@($d.problems).Count) 个问题，可以转整改" (@($d.problems).Count -eq 2 -and $d.actions -contains 'TO_HAZARD')
$r = Call POST "/safety/emergency/drill/problem/$problemId/hazard" @{} $tZhang
Check '转隐患' ($r.code -eq 200)
$h = (Call GET "/safety/hazard/$($r.data)" $null $tZhang).data
Check "隐患 $($h.hazard.hazardNo)：$($h.hazard.title)（$($h.hazard.category)）" ($h.hazard.title -like '演练发现*' -and $h.hazard.category -eq '设备设施')
$r = Call POST "/safety/emergency/drill/problem/$problemId/hazard" @{} $tZhang; Show $r
Check '不能重复转' ($r.code -ne 200)
$d = DrillOf $did $tZhang
Check "详情里显示隐患状态：$($d.problems[0].hazardStatusLabel)" ($d.problems[0].problem.hazardNo -and $d.problems[0].hazardStatusLabel)

Title '7. 年度演练计划'
$row = @((Call GET '/safety/emergency/drill/annual' $null $tZhang).data.rows | Where-Object { "$($_.planId)" -eq "$planId" })[0]
Check "现场处置方案要求 $($row.required) 次，完成 $($row.done) 次、已安排 $($row.planned) 次：$($row.state)" ($row.required -eq 2 -and $row.done -eq 1 -and $row.planned -eq 1 -and $row.state -eq 'PLANNED')
Call POST "/safety/emergency/drill/$later/cancel" @{} $tZhang | Out-Null
$row = @((Call GET '/safety/emergency/drill/annual' $null $tZhang).data.rows | Where-Object { "$($_.planId)" -eq "$planId" })[0]
Check "取消第二次演练后变成 $($row.state)" ($row.state -eq 'MISSING')

Title '8. 修改预案后修订意见清除、租户隔离'
Call PUT "/safety/emergency/plan/$planId" @{ planCode = $code; title = '自测有限空间救援现场处置方案'; planType = 'ONSITE'; accidentType = 'POISONING'; content = '1. 停止作业 2. 呼救 3. 佩戴空气呼吸器施救 4. 三脚架放在池边'; teamUserIds = @(102) } $tZhang | Out-Null
$p = (Call GET "/safety/emergency/plan/$planId" $null $tZhang).data
Check '修改预案后不再显示待修订' (-not $p.reviseNote)
$r = Call GET "/safety/emergency/drill/$did" $null $tTest
Check '别的企业看不到' ($r.code -ne 200)
$r = Call GET '/safety/emergency/drill/list' $null $tTest
Check '别的企业的列表里没有' (@($r.data | Where-Object { "$($_.drill.id)" -eq "$did" }).Count -eq 0)
# 收尾：停用自测预案，不影响演示数据的年度计划
Call PUT "/safety/emergency/plan/$planId/status" @{ status = 0 } $tZhang | Out-Null

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
