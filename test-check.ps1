# 安全检查自测：检查表校验、新建计划立即生成任务且不重复、只有检查人能提交、逐项校验、不合格项转隐患、统计和手机首页、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-check.ps1

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
$lisiId = (Call GET '/auth/me' $null $tLisi).data.user.id
$tag = Get-Random -Minimum 1000 -Maximum 9999

Title '1. 权限和演示数据'
$r = Call GET '/safety/check/task/page?size=50' $null $tZhang
Check "安全员能看到检查任务（$($r.data.total) 张）" ($r.code -eq 200 -and $r.data.total -ge 1)
$r = Call GET '/safety/check/task/page?size=50' $null $tLisi
Check '作业负责人只能看到分给自己的任务' ($r.code -eq 200 -and (@($r.data.records | Where-Object { $_.checkerId -ne $lisiId })).Count -eq 0)
$r = Call GET '/safety/check/template/list' $null $tZhang
Check "演示检查表 $(@($r.data).Count) 张（罐区日常巡回、受限空间专项、冬季防冻）" (@($r.data).Count -ge 3)
$r = Call POST '/safety/check/template' @{ name = "作业负责人建的表$tag"; checkType = 'DAILY'; items = @(@{ content = '检查项' }) } $tLisi; Show $r
Check '作业负责人不能新建检查表（403）' ($r.code -eq 403)

Title '2. 检查表校验'
$r = Call POST '/safety/check/template' @{ name = "自测检查表$tag"; checkType = 'DAILY'; items = @(@{ content = '消防通道畅通' }, @{ content = '消防通道畅通 ' }) } $tZhang; Show $r
Check '有重复的检查项时拒绝' ($r.code -ne 200)
$r = Call POST '/safety/check/template' @{ name = "自测检查表$tag"; checkType = 'DAILY'; items = @(@{ content = '消防通道畅通'; category = '外星类别' }) } $tZhang; Show $r
Check '隐患类别不正确时拒绝' ($r.code -ne 200)
$items = @(
    @{ content = '消防通道畅通'; standard = '无杂物堆放'; category = '作业环境' },
    @{ content = '灭火器压力正常'; standard = '指针在绿区'; category = '设备设施' },
    @{ content = '人员劳保用品佩戴齐全'; category = '人员行为' }
)
$r = Call POST '/safety/check/template' @{ name = "自测检查表$tag"; checkType = 'SPECIAL'; items = $items; remark = '自测' } $tZhang
$tplId = $r.data
Check '新建检查表（3 项）' ($r.code -eq 200 -and $tplId)

Title '3. 新建计划立即生成任务，同一周期不重复'
$today = (Get-Date).ToString('yyyy-MM-dd')
$msgBefore = (Call GET '/message/unread' $null $tLisi).data.total
$home0 = (Call GET '/mobile/home' $null $tLisi).data
$r = Call POST '/safety/check/plan' @{ name = "自测每日检查$tag"; templateId = $tplId; cycle = 'DAILY'; checkerId = $lisiId; location = '罐区东侧'; startDate = $today } $tZhang
$planId = $r.data
Check '新建每日检查计划（今天开始）' ($r.code -eq 200 -and $planId)
$tasks = (Call GET "/safety/check/task/page?planId=$planId&size=10" $null $tZhang).data
Check "立即生成了今天的任务（$($tasks.total) 张）" ($tasks.total -eq 1)
$task = @($tasks.records)[0]
Check "截止日期是今天 $($task.dueDate)，检查人是李四，共 $($task.itemCount) 项" ($task.dueDate -eq $today -and $task.checkerId -eq $lisiId -and [int]$task.itemCount -eq 3)
$msgAfter = (Call GET '/message/unread' $null $tLisi).data.total
Check "检查人收到待办（未读 $msgBefore → $msgAfter）" ([int]$msgAfter -ge [int]$msgBefore + 1)
$home1 = (Call GET '/mobile/home' $null $tLisi).data
Check "手机首页的安全检查 $($home0.check) → $($home1.check)" ([int]$home1.check -eq [int]$home0.check + 1)
Call PUT "/safety/check/plan/$planId/status?status=0" $null $tZhang | Out-Null
$r = Call PUT "/safety/check/plan/$planId/status?status=1" $null $tZhang
$tasks2 = (Call GET "/safety/check/task/page?planId=$planId&size=10" $null $tZhang).data
Check '停用再启用，同一天不会再生成第二张' ($r.code -eq 200 -and $tasks2.total -eq 1)
$r = Call PUT "/safety/check/template/$tplId/status?status=0" $null $tZhang; Show $r
Check '有启用的计划在用时，检查表不能停用' ($r.code -ne 200)

Title '4. 逐项检查'
$tid = $task.id
$d = (Call GET "/safety/check/task/$tid" $null $tLisi).data
Check "检查人打开任务：可以提交，$(@($d.items).Count) 个检查项" ($d.canSubmit -eq $true -and @($d.items).Count -eq 3)
$d2 = (Call GET "/safety/check/task/$tid" $null $tZhang).data
Check '安全员能看，但不能替检查人提交（canSubmit = false）' ($d2.canSubmit -eq $false)
$ok3 = @(@{ ok = $true }, @{ ok = $true }, @{ ok = $true })
$r = Call POST "/safety/check/task/$tid/submit" @{ items = $ok3 } $tZhang; Show $r
Check '安全员提交被拒绝（403）' ($r.code -eq 403)
$r = Call POST "/safety/check/task/$tid/submit" @{ items = @(@{ ok = $true }, @{ ok = $true }) } $tLisi; Show $r
Check '少检查一项被拒绝' ($r.code -ne 200)
$r = Call POST "/safety/check/task/$tid/submit" @{ items = @(@{ ok = $true }, @{ ok = $false; toHazard = $false }, @{ ok = $true }) } $tLisi; Show $r
Check '不合格又不转隐患、不写说明，被拒绝' ($r.code -ne 200)
$hzBefore = (Call GET '/safety/hazard/page?size=1' $null $tZhang).data.total
$r = Call POST "/safety/check/task/$tid/submit" @{ summary = '自测'; items = @(
    @{ ok = $true },
    @{ ok = $false; note = '2# 灭火器压力不足'; toHazard = $true; level = 'MAJOR' },
    @{ ok = $false; note = '1 人未戴安全帽，已当场纠正'; toHazard = $false }
) } $tLisi
Check "提交成功：$($r.data.failCount) 项不合格，转隐患 $(@($r.data.hazardNos) -join '、')" ($r.code -eq 200 -and [int]$r.data.failCount -eq 2 -and @($r.data.hazardNos).Count -eq 1)
$hzNo = @($r.data.hazardNos)[0]
$hz = (Call GET "/safety/hazard/page?size=5&keyword=$hzNo" $null $tZhang).data
$h = @($hz.records)[0]
Check "隐患台账里有这条：$($h.title)（$($h.level)、$($h.category)、待指派）" ($h.level -eq 'MAJOR' -and $h.category -eq '设备设施' -and $h.status -eq 'REPORTED')
$hzAfter = (Call GET '/safety/hazard/page?size=1' $null $tZhang).data.total
Check "隐患总数 $hzBefore → $hzAfter" ([int]$hzAfter -eq [int]$hzBefore + 1)
$d3 = (Call GET "/safety/check/task/$tid" $null $tLisi).data
$res = @($d3.items)
Check '提交后显示逐项结果：第 2 项带隐患编号，第 3 项带现场说明' ($d3.task.status -eq 'DONE' -and $res[1].hazardNo -eq $hzNo -and $res[2].note -like '*安全帽*')
$r = Call POST "/safety/check/task/$tid/submit" @{ items = $ok3 } $tLisi; Show $r
Check '不能重复提交' ($r.code -ne 200)
$home2 = (Call GET '/mobile/home' $null $tLisi).data
Check "提交后手机首页的安全检查恢复 $($home2.check)" ([int]$home2.check -eq [int]$home0.check)
$ov = (Call GET '/safety/check/task/overview' $null $tZhang).data
Check "统计：待检查 $($ov.pending)、逾期 $($ov.overdue)、本月完成 $($ov.monthDone)、不合格项 $($ov.monthFail)、转隐患 $($ov.monthHazard)、按时完成率 $($ov.onTimeRate)%" ([int]$ov.monthDone -ge 1 -and [int]$ov.monthHazard -ge 1)

Title '5. 租户隔离'
$r = Call GET "/safety/check/task/$tid" $null $tTest
Check '别的企业看不到这张任务' ($r.code -ne 200)
$r = Call GET '/safety/check/task/page?size=50' $null $tTest
Check '别的企业的列表里没有演示企业的任务' ($r.code -ne 200 -or (@($r.data.records | Where-Object { $_.id -eq $tid })).Count -eq 0)

Title '6. 清理'
Call PUT "/safety/check/plan/$planId/status?status=0" $null $tZhang | Out-Null
Call PUT "/safety/check/template/$tplId/status?status=0" $null $tZhang | Out-Null
Call POST "/safety/hazard/$($h.id)/revoke" @{ comment = '自测清理' } $tZhang | Out-Null
Write-Host '  已停用自测计划、检查表，撤销自测隐患' -ForegroundColor DarkGray

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
