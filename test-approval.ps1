# 可配置审批流自测：流程配置校验、多级审批（或签 → 会签）、轮到谁审批、待我审批、驳回重提、流程快照、消息
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-approval.ps1
# 脚本会临时修改【动火作业】的审批流程，结束时恢复默认（不影响 test-ticket / test-message）

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'

function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $body) {
        $json = if ($body -is [string]) { $body } else { ConvertTo-Json -InputObject $body -Depth 6 }
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    $resp = Invoke-WebRequest @params
    $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}
function LoginRaw($tenant, $user, $password) {
    $c = (Call GET '/auth/captcha').data
    if ($c.enabled -and -not $c.debugAnswer) { throw '后端没有返回验证码答案：请确认后端是用开发环境（dev）启动的' }
    return Call POST '/auth/login' @{ tenantCode = $tenant; username = $user; password = $password; captchaKey = $c.captchaKey; captchaCode = $c.debugAnswer }
}
function Login($tenant, $user, $password = 'admin123') {
    $r = LoginRaw $tenant $user $password
    if ($r.code -ne 200) { throw "登录失败 $tenant/$user：$($r.msg)" }
    return $r.data.token
}
function Check($name, $ok) {
    if ($ok) { Write-Host "[通过] $name" -ForegroundColor Green }
    else     { Write-Host "[失败] $name" -ForegroundColor Red; $script:failed++ }
}
function Title($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Show($r) { Write-Host "  -> code = $($r.code), msg = $($r.msg)" -ForegroundColor DarkGray }
function Fmt($d) { $d.ToString('yyyy-MM-dd HH:mm:ss') }
function Detail($id, $token) { (Call GET "/ticket/$id" $null $token).data }
function Todo($token) { ,@((Call GET '/ticket/page?size=100&todo=true' $null $token).data.records | ForEach-Object { "$($_.id)" }) }
function MsgsOf($token, $bizId) { ,@((Call GET '/message/page?size=100' $null $token).data.records | Where-Object { "$($_.bizId)" -eq "$bizId" }) }
function SaveFlow($nodes, $token) { Call PUT '/ticket/flow/HOT_WORK' (ConvertTo-Json -InputObject @($nodes) -Depth 6) $token }
function Node($name, $type, $role, $userIds, $mode) {
    return [ordered]@{ name = $name; approverType = $type; roleCode = $role; userIds = $userIds; mode = $mode }
}
$failed = 0
$ts = Get-Date -Format 'MMddHHmmss'
$now = Get-Date
function NewHotWork($title) {
    return @{ ticketType = 'HOT_WORK'; title = $title; location = '机修车间'; guardianId = 104
              workContent = '管道焊接'; safetyMeasures = '1. 清理周边 15 米可燃物 2. 配备 2 具灭火器'
              planStartTime = (Fmt $now); planEndTime = (Fmt $now.AddHours(4))
              gasOxygen = 20.9; gasLel = 1.5; gasH2s = 0; gasCheckTime = (Fmt $now) }
}

$tAdmin = Login 'demo' 'admin'
$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tTest  = Login 'test' 'admin'

# 脚本中途失败也要恢复默认流程
try {

Title '1. 查看流程配置'
$flows = (Call GET '/ticket/flow' $null $tAdmin).data
Check "三种作业类型都有流程（$(@($flows).Count) 种）" (@($flows).Count -eq 3)
$height = $flows | Where-Object { $_.ticketType -eq 'HEIGHT' }
Check "演示数据：高处作业配置了两级审批（$(($height.nodes | ForEach-Object { $_.name }) -join ' → ')）" ($height.configured -and @($height.nodes).Count -eq 2)
Call DELETE '/ticket/flow/HOT_WORK' $null $tAdmin | Out-Null
$hot = (Call GET '/ticket/flow' $null $tAdmin).data | Where-Object { $_.ticketType -eq 'HOT_WORK' }
Check '动火作业没有配置时用默认流程（单节点、或签）' (-not $hot.configured -and @($hot.nodes).Count -eq 1 -and $hot.nodes[0].mode -eq 'ANY')
$cands = (Call GET '/ticket/flow/candidates' $null $tAdmin).data
Check "可选审批人只有「有审批权限」的员工（$(($cands | ForEach-Object { $_.nickname }) -join '、')）" (@($cands).Count -ge 2 -and -not ($cands | Where-Object { $_.nickname -like '李四*' }))
$r = Call GET '/ticket/flow' $null $tLisi; Show $r
Check '作业负责人没有【审批流程】权限' ($r.code -eq 403)

Title '2. 保存时的校验'
$r = Call PUT '/ticket/flow/HOT_WORK' '[]' $tAdmin; Show $r
Check '至少 1 个节点' ($r.code -eq 400)
$r = SaveFlow @((Node '全员会签' 'ANY_APPROVER' $null $null 'ALL')) $tAdmin; Show $r
Check '会签不能用于「所有有审批权限的人」' ($r.code -eq 400 -and $r.msg -like '*会签*')
$r = SaveFlow @((Node '不存在的角色' 'ROLE' 'nope' $null 'ANY')) $tAdmin; Show $r
Check '角色必须存在' ($r.code -eq 400 -and $r.msg -like '*角色不存在*')
$r = SaveFlow @((Node '监护人审批' 'USER' $null @(104) 'ANY')) $tAdmin; Show $r
Check '监护人没有审批权限，不能设为审批人' ($r.code -eq 400 -and $r.msg -like '*没有人能审批*')
$six = 1..6 | ForEach-Object { Node "节点$_" 'ROLE' 'safety' $null 'ANY' }
$r = SaveFlow $six $tAdmin; Show $r
Check '最多 5 个节点' ($r.code -eq 400)
$r = SaveFlow @((Node '安全员审核' 'ROLE' 'safety' $null 'ANY')) $tLisi; Show $r
Check '作业负责人不能修改流程' ($r.code -eq 403)

$r = SaveFlow @((Node '安全员审核' 'ROLE' 'safety' $null 'ANY'), (Node '双人会签' 'USER' $null @(102, 101) 'ALL')) $tAdmin; Show $r
Check "保存两级流程：安全员审核（或签）→ 双人会签（$(($r.data.approvers[1]) -join '、')）" ($r.code -eq 200 -and $r.data.configured -and @($r.data.approvers[1]).Count -eq 2)
$r = Call GET '/ticket/flow' $null $tTest
$hotTest = $r.data | Where-Object { $_.ticketType -eq 'HOT_WORK' }
Check '租户隔离：测试冶金的动火作业还是默认流程' (-not $hotTest.configured)

Title '3. 提交：按流程激活第 1 个节点'
$tid = (Call POST '/ticket' (NewHotWork "多级审批测试 $ts") $tLisi).data
$r = Call POST "/ticket/$tid/submit" @{} $tLisi
Check '李四提交动火作业票' ($r.code -eq 200)
$d = Detail $tid $tLisi
Check "当前节点：$($d.ticket.currentStep)" ($d.ticket.status -eq 'PENDING' -and $d.ticket.currentStep -like '安全员审核*1/2*')
Check '审批进度：共 2 级、第 1 级审批中' ($d.approval.totalNodes -eq 2 -and $d.approval.currentNode -eq 0 -and $d.approval.nodes[0].status -eq 'ACTIVE' -and $d.approval.nodes[1].status -eq 'WAITING')
Check '张三的「待我审批」里有这张票' ((Todo $tZhang) -contains "$tid")
Check '管理员还不是当前节点的审批人，「待我审批」里没有' (-not ((Todo $tAdmin) -contains "$tid"))
Check '管理员看详情时没有同意 / 驳回按钮' (-not (@((Detail $tid $tAdmin).actions) -contains 'APPROVE'))
$r = Call POST "/ticket/$tid/approve" @{ comment = '抢先审批' } $tAdmin; Show $r
Check '没轮到管理员，审批被拒绝' ($r.code -eq 403)
Check '只有第 1 个节点的审批人收到待办消息（管理员没收到）' ((MsgsOf $tZhang $tid).Count -eq 1 -and (MsgsOf $tAdmin $tid).Count -eq 0)

Title '4. 第 1 级通过 → 进入会签节点'
$r = Call POST "/ticket/$tid/approve" @{ comment = '动火措施已落实' } $tZhang
Check '张三同意' ($r.code -eq 200)
$d = Detail $tid $tLisi
Check "作业票仍是待审批，当前节点：$($d.ticket.currentStep)" ($d.ticket.status -eq 'PENDING' -and $d.approval.currentNode -eq 1 -and $d.ticket.currentStep -like '双人会签*2/2*')
Check '会签节点给 2 个人都生成了待办' (@($d.approval.nodes[1].tasks).Count -eq 2)
Check '管理员现在收到了待办' ((MsgsOf $tAdmin $tid).Count -eq 1 -and (MsgsOf $tAdmin $tid)[0].title -like '*会签*')
Check '申请人收到审批进度通知' (@(MsgsOf $tLisi $tid | Where-Object { $_.title -like '审批进度*' }).Count -eq 1)
$log = @($d.logs | Where-Object { $_.action -eq 'APPROVE' })
Check "操作记录带节点名称（$($log[0].comment)）" ($log.Count -eq 1 -and $log[0].comment -like '【安全员审核*】动火措施已落实')

Title '5. 会签：所有人都同意才通过'
$r = Call POST "/ticket/$tid/approve" @{ comment = '同意' } $tAdmin
Check '管理员同意' ($r.code -eq 200)
$d = Detail $tid $tLisi
Check '还差张三，作业票仍是待审批' ($d.ticket.status -eq 'PENDING' -and $d.approval.nodes[1].status -eq 'ACTIVE')
$r = Call POST "/ticket/$tid/approve" @{ comment = '再点一次' } $tAdmin; Show $r
Check '同一个人不能重复审批' ($r.code -eq 403)
Check '管理员的「待我审批」里没有了，张三的还有' (-not ((Todo $tAdmin) -contains "$tid") -and ((Todo $tZhang) -contains "$tid"))
$r = Call POST "/ticket/$tid/approve" @{ comment = '会签同意' } $tZhang
Check '张三会签同意' ($r.code -eq 200)
$d = Detail $tid $tLisi
Check "全部通过，作业票已批准（审批人：$($d.ticket.approverName)）" ($d.ticket.status -eq 'APPROVED' -and $null -eq $d.approval.currentNode -and $d.ticket.approverName -like '张三*')
Check '两个节点都是已通过' ($d.approval.nodes[0].status -eq 'APPROVED' -and $d.approval.nodes[1].status -eq 'APPROVED')
Check '监护人收到待开工' (@(MsgsOf (Login 'demo' 'zhaoliu') $tid | Where-Object { $_.title -like '待开工*' }).Count -eq 1)

Title '6. 驳回、修改流程不影响已提交的票、重新提交'
$tid2 = (Call POST '/ticket' (NewHotWork "驳回测试 $ts") $tLisi).data
Call POST "/ticket/$tid2/submit" @{} $tLisi | Out-Null
Call POST "/ticket/$tid2/approve" @{ comment = '同意' } $tZhang | Out-Null
$r = Call DELETE '/ticket/flow/HOT_WORK' $null $tAdmin
Check '管理员把动火作业恢复为默认流程（单节点）' ($r.code -eq 200)
$d = Detail $tid2 $tLisi
Check '已提交的票按提交时的流程走：仍在第 2 级会签' ($d.approval.totalNodes -eq 2 -and $d.approval.currentNode -eq 1)
$r = Call POST "/ticket/$tid2/reject" @{ comment = '动火点下方地沟没有封堵' } $tAdmin
Check '管理员在会签节点驳回' ($r.code -eq 200)
$d = Detail $tid2 $tLisi
Check '作业票已驳回，第 2 级显示已驳回' ($d.ticket.status -eq 'REJECTED' -and $d.approval.nodes[1].status -eq 'REJECTED')
Check '张三在这个节点的待办被取消' (-not ((Todo $tZhang) -contains "$tid2"))
$r = Call POST "/ticket/$tid2/submit" @{} $tLisi
Check '李四修改后重新提交' ($r.code -eq 200)
$d = Detail $tid2 $tLisi
Check "第 2 次提交按新流程（单节点）：第 $($d.approval.round) 轮、共 $($d.approval.totalNodes) 级" ($d.approval.round -eq 2 -and $d.approval.totalNodes -eq 1)
$r = Call POST "/ticket/$tid2/approve" @{ comment = '已整改' } $tZhang
Check '单节点：张三同意后直接批准' ($r.code -eq 200 -and (Detail $tid2 $tLisi).ticket.status -eq 'APPROVED')

Title '7. 取消：待办一起取消'
$tid3 = (Call POST '/ticket' (NewHotWork "取消测试 $ts") $tLisi).data
Call POST "/ticket/$tid3/submit" @{} $tLisi | Out-Null
Check '提交后张三有待办' ((Todo $tZhang) -contains "$tid3")
Call POST "/ticket/$tid3/cancel" @{ comment = '计划变更' } $tLisi | Out-Null
Check '取消后待办消失' (-not ((Todo $tZhang) -contains "$tid3"))

# 清理：取消测试票，保证可以重复运行
Call POST "/ticket/$tid/cancel" @{ comment = '测试清理' } $tLisi | Out-Null
Call POST "/ticket/$tid2/cancel" @{ comment = '测试清理' } $tLisi | Out-Null

} finally {
    Call DELETE '/ticket/flow/HOT_WORK' $null $tAdmin | Out-Null
    Write-Host "`n（已恢复动火作业的默认审批流程）" -ForegroundColor DarkGray
}

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red }
