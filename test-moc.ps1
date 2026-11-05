# 变更管理（MOC）自测：申请校验、草稿可见范围、风险评估（LEC）、分级审批（较大风险两人、不能审批自己、驳回后重新计数）、
# 实施、投用前安全检查（PSSR）、临时变更恢复、取消、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-moc.ps1

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
$tAdmin = Login 'demo' 'admin'
$tTest  = Login 'test' 'admin'
$tag = Get-Random -Minimum 1000 -Maximum 9999
$day = { param($n) (Get-Date).AddDays($n).ToString('yyyy-MM-dd') }
function Status($id) { (Call GET "/safety/moc/$id" $null $tZhang).data.change.status }
$ok7 = @($true, $true, $true, $true, $true, $true, $true)

Title '1. 申请校验'
$mform = @{ title = "自测变更 $tag"; changeType = 'PROCESS'; temporary = $false; location = '罐区'; reason = '自测原因'; content = '自测变更内容' }
$r = Call POST '/safety/moc' ($mform + @{}) $tLisi
$id = $r.data
Check "李四新建永久变更（草稿）id = $id" ($r.code -eq 200)
$bad = $mform.Clone(); $bad.changeType = 'ALIEN'
$r = Call POST '/safety/moc' $bad $tLisi; Show $r
Check '变更类型不正确被拒绝' ($r.code -ne 200)
$bad = $mform.Clone(); $bad.temporary = $true
$r = Call POST '/safety/moc' $bad $tLisi; Show $r
Check '临时变更必须填恢复日期' ($r.code -ne 200)
$bad.restoreDate = (& $day 200)
$r = Call POST '/safety/moc' $bad $tLisi; Show $r
Check '临时变更最长 180 天' ($r.code -ne 200)
$r = Call GET "/safety/moc/$id" $null $tZhao; Show $r
Check '别人看不到李四的草稿' ($r.code -ne 200)
$d = (Call GET "/safety/moc/$id" $null $tLisi).data
Check "草稿：李四能修改、提交（$($d.actions -join '、')）" (($d.actions -contains 'EDIT') -and ($d.actions -contains 'SUBMIT'))

Title '2. 提交和风险评估'
$r = Call POST "/safety/moc/$id/submit" $null $tLisi
Check '提交风险评估' ($r.code -eq 200 -and (Status $id) -eq 'ASSESSING')
$r = Call PUT "/safety/moc/$id" $mform $tLisi; Show $r
Check '提交后不能修改' ($r.code -ne 200)
$assess = @{ lecL = 6; lecE = 6; lecC = 15; riskAnalysis = '自测风险分析'; measures = '自测控制措施' }
$r = Call POST "/safety/moc/$id/assess" $assess $tZhao; Show $r
Check '监护人不能做风险评估' ($r.code -ne 200)
$r = Call POST "/safety/moc/$id/assess" (@{ lecL = 4; lecE = 6; lecC = 15; riskAnalysis = 'x'; measures = 'x' }) $tZhang; Show $r
Check 'L 只能取标准分值（4 不行）' ($r.code -ne 200)
$r = Call POST "/safety/moc/$id/assess" $assess $tZhang
$c = (Call GET "/safety/moc/$id" $null $tZhang).data.change
Check "评估 D = $($c.lecD)，$($c.riskLevel)，需要 $($c.approvalsNeeded) 人审批（应为 540 / MAJOR / 2）" ($r.code -eq 200 -and [double]$c.lecD -eq 540 -and $c.riskLevel -eq 'MAJOR' -and [int]$c.approvalsNeeded -eq 2)

Title '3. 分级审批'
$r = Call POST "/safety/moc/$id/approve" @{ comment = '' } $tLisi; Show $r
Check '申请人不能审批' ($r.code -ne 200)
$r = Call POST "/safety/moc/$id/approve" @{ comment = '同意（自测）' } $tZhang
Check '张三审批通过（1/2），还在待审批' ($r.code -eq 200 -and (Status $id) -eq 'APPROVING')
$r = Call POST "/safety/moc/$id/approve" @{ comment = '' } $tZhang; Show $r
Check '同一人不能审批两次' ($r.code -ne 200)
$r = Call POST "/safety/moc/$id/reject" @{ comment = '' } $tAdmin; Show $r
Check '驳回必须写原因' ($r.code -ne 200)
$r = Call POST "/safety/moc/$id/reject" @{ comment = '控制措施不够具体（自测）' } $tAdmin
Check '企业管理员驳回，回到风险评估' ($r.code -eq 200 -and (Status $id) -eq 'ASSESSING')
$assess.lecL = 3
$r = Call POST "/safety/moc/$id/assess" $assess $tZhang
$d = (Call GET "/safety/moc/$id" $null $tZhang).data
Check "重新评估 D = $($d.change.lecD)（较大风险），之前的审批不算：本轮 $($d.approved) 人已批" ($r.code -eq 200 -and [int]$d.approved -eq 0 -and [int]$d.change.approvalsNeeded -eq 2)
$r1 = Call POST "/safety/moc/$id/approve" @{ comment = '同意' } $tZhang
$r2 = Call POST "/safety/moc/$id/approve" @{ comment = '同意' } $tAdmin
Check '两人审批通过，进入实施' ($r1.code -eq 200 -and $r2.code -eq 200 -and (Status $id) -eq 'IMPLEMENTING')

Title '4. 实施和投用前安全检查'
$r = Call POST "/safety/moc/$id/implemented" @{ comment = '完成' } $tZhao; Show $r
Check '不是申请人不能提交实施完成' ($r.code -ne 200)
$r = Call POST "/safety/moc/$id/implemented" @{ comment = '' } $tLisi; Show $r
Check '实施情况必须写' ($r.code -ne 200)
$r = Call POST "/safety/moc/$id/implemented" @{ comment = '已按方案实施（自测）' } $tLisi
Check '实施完成，进入投用前检查' ($r.code -eq 200 -and (Status $id) -eq 'PSSR')
$r = Call POST "/safety/moc/$id/pssr" @{ results = @($true, $true, $true) } $tZhang; Show $r
Check '检查项不全被拒绝' ($r.code -ne 200)
$res = @($true, $true, $true, $true, $false, $true, $true)
$r = Call POST "/safety/moc/$id/pssr" @{ results = $res; notes = @('', '', '', '', '', '', '') } $tZhang; Show $r
Check '不合格项必须写原因' ($r.code -ne 200)
$r = Call POST "/safety/moc/$id/pssr" @{ results = $res; notes = @('', '', '', '', 'P&ID 还没更新（自测）', '', '') } $tZhang
Check '有不合格项：退回实施' ($r.code -eq 200 -and (Status $id) -eq 'IMPLEMENTING')
Call POST "/safety/moc/$id/implemented" @{ comment = 'P&ID 已更新（自测）' } $tLisi | Out-Null
$r = Call POST "/safety/moc/$id/pssr" @{ results = $ok7; notes = @() } $tZhang
$d = (Call GET "/safety/moc/$id" $null $tLisi).data
Check "全部合格：已投用，记录 $(@($d.logs).Count) 条" ($r.code -eq 200 -and $d.change.status -eq 'CLOSED' -and (@($d.pssr | Where-Object { $_.ok })).Count -eq 7)
$r = Call POST "/safety/moc/$id/cancel" @{ comment = '' } $tLisi; Show $r
Check '已投用的不能取消' ($r.code -ne 200)

Title '5. 临时变更'
$t = $mform.Clone(); $t.title = "自测临时变更 $tag"; $t.changeType = 'EQUIPMENT'; $t.temporary = $true; $t.restoreDate = (& $day 10)
$tid = (Call POST '/safety/moc' $t $tZhao).data
Call POST "/safety/moc/$tid/submit" $null $tZhao | Out-Null
Call POST "/safety/moc/$tid/assess" @{ lecL = 1; lecE = 1; lecC = 1; riskAnalysis = '低风险（自测）'; measures = '巡检（自测）' } $tZhang | Out-Null
$c = (Call GET "/safety/moc/$tid" $null $tZhang).data.change
Check "低风险只要 1 人审批（D = $($c.lecD)）" ([int]$c.approvalsNeeded -eq 1)
Call POST "/safety/moc/$tid/approve" @{ comment = '' } $tAdmin | Out-Null
Call POST "/safety/moc/$tid/implemented" @{ comment = '已切换（自测）' } $tZhao | Out-Null
Call POST "/safety/moc/$tid/pssr" @{ results = $ok7 } $tZhang | Out-Null
$d = (Call GET "/safety/moc/$tid" $null $tZhao).data
Check '临时变更投用后，赵六可以登记恢复' ($d.change.status -eq 'CLOSED' -and ($d.actions -contains 'RESTORE'))
$r = Call POST "/safety/moc/$tid/restore" @{ comment = '已恢复' } $tLisi; Show $r
Check '别人不能替申请人登记恢复' ($r.code -ne 200)
$r = Call POST "/safety/moc/$tid/restore" @{ comment = '主设备已修好，恢复原状（自测）' } $tZhao
Check '登记已恢复原状' ($r.code -eq 200 -and [int](Call GET "/safety/moc/$tid" $null $tZhao).data.change.restored -eq 1)
$r = Call POST "/safety/moc/$tid/restore" @{ comment = '再来' } $tZhao; Show $r
Check '不能重复登记恢复' ($r.code -ne 200)

Title '6. 取消、待办、租户隔离'
$cid = (Call POST '/safety/moc' $mform $tLisi).data
$r = Call POST "/safety/moc/$cid/cancel" @{ comment = '自测取消' } $tLisi
Check '草稿可以取消' ($r.code -eq 200 -and (Status $cid) -eq 'CANCELLED')
$ov = (Call GET '/safety/moc/overview' $null $tZhang).data
Check "张三的统计：要我处理 $($ov.myTodo)，待审批 $($ov.approving)，临时变更未恢复 $($ov.temporaryOpen)" ($null -ne $ov.myTodo)
$mh = (Call GET '/mobile/home' $null $tZhang).data
Check "张三手机首页的变更待办 $($mh.moc)" ($null -ne $mh.moc)
$r = Call GET "/safety/moc/$id" $null $tTest; Show $r
Check '别的企业打不开这个变更' ($r.code -ne 200)

Write-Host ''
Write-Host '  自测产生的变更保留在列表里（标题带“自测”），demo-reset 后消失' -ForegroundColor DarkGray
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
