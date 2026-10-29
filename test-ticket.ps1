# 作业票 + 审批流 自测脚本
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-ticket.ps1
# 可以重复运行

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'

function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
    if ($body) {
        $json = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 5 }
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    $resp = Invoke-WebRequest @params
    $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}
function Login($tenant, $user) {
    $r = Call POST '/auth/login' @{ tenantCode = $tenant; username = $user; password = 'admin123' }
    if ($r.code -ne 200) { throw "登录失败 $tenant/$user：$($r.msg)" }
    return $r.data.token
}
function Check($name, $ok) {
    if ($ok) { Write-Host "[通过] $name" -ForegroundColor Green }
    else     { Write-Host "[失败] $name" -ForegroundColor Red; $script:failed++ }
}
function Title($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Show($r) { Write-Host "  -> code = $($r.code), msg = $($r.msg)" -ForegroundColor DarkGray }
function Status($id, $token) { (Call GET "/ticket/$id" $null $token).data.ticket.status }
function Fmt($d) { $d.ToString('yyyy-MM-dd HH:mm:ss') }
$failed = 0

# 演示化工：lisi 作业负责人(申请)  zhangsan 安全员(审批)  zhaoliu 监护人(104)  admin 管理员
$tLisi  = Login 'demo' 'lisi'
$tZhang = Login 'demo' 'zhangsan'
$tZhao  = Login 'demo' 'zhaoliu'
$tAdmin = Login 'demo' 'admin'
$tTest  = Login 'test' 'admin'

$now = Get-Date
function NewTicket($type, $title, $guardian, $o2, $space = 1001) {
    $t = @{
        ticketType     = $type
        spaceId        = $(if ($type -eq 'CONFINED_SPACE') { $space } else { $null })
        title          = $title
        location       = '1# 储罐区 T-101'
        workContent    = '储罐内部检修、清理'
        safetyMeasures = '1. 切断物料并加盲板 2. 强制通风 3. 配备正压式呼吸器'
        planStartTime  = Fmt $now
        planEndTime    = Fmt $now.AddHours(4)
        guardianId     = $guardian
    }
    if ($o2) {
        $t.gasOxygen = $o2; $t.gasLel = 2.5; $t.gasH2s = 0.5; $t.gasCheckTime = Fmt $now
    }
    return $t
}

Title '1. 作业负责人 lisi 新建受限空间作业票'
$r = Call POST '/ticket' (NewTicket 'CONFINED_SPACE' '储罐清理' 104 20.9) $tLisi
Show $r
Check '创建成功' ($r.code -eq 200)
$id1 = $r.data
$d = (Call GET "/ticket/$id1" $null $tLisi).data
Write-Host "  编号 = $($d.ticket.ticketNo)  状态 = $($d.ticket.status)  监护人 = $($d.ticket.guardianName)"
Write-Host "  lisi 可执行的操作 = $($d.actions -join ', ')"
Check '编号以 SX 开头，状态为草稿' ($d.ticket.ticketNo -like 'SX*' -and $d.ticket.status -eq 'DRAFT')
Check '申请人可以修改、提交、取消' (($d.actions -contains 'EDIT') -and ($d.actions -contains 'SUBMIT') -and ($d.actions -contains 'CANCEL'))

Title '2. 监护人校验'
$r = Call POST '/ticket' (NewTicket 'CONFINED_SPACE' '测试' 102 20.9) $tLisi; Show $r
Check '选安全员 zhangsan 当监护人被拒绝' ($r.code -eq 400)
$r = Call POST '/ticket' (NewTicket 'CONFINED_SPACE' '测试' 202 20.9) $tLisi; Show $r
Check '选别的企业的人当监护人被拒绝（查不到）' ($r.code -eq 404)
$r = Call POST '/ticket' (NewTicket 'CONFINED_SPACE' '测试' 104 20.9) $tZhao; Show $r
Check '监护人没有申请权限（403）' ($r.code -eq 403)

$noSpace = NewTicket 'CONFINED_SPACE' '未选空间' 104 20.9
$noSpace.spaceId = $null
$idNoSpace = (Call POST '/ticket' $noSpace $tLisi).data
$r = Call POST "/ticket/$idNoSpace/submit" $null $tLisi; Show $r
Check '受限空间作业不选台账中的空间，不能提交' ($r.code -eq 400)
Call POST "/ticket/$idNoSpace/cancel" @{} $tLisi | Out-Null

Title '3. 提交审批；申请人不能自己审批'
$r = Call POST "/ticket/$id1/submit" $null $tLisi; Show $r
Check '提交成功，状态 = 待审批' ($r.code -eq 200 -and (Status $id1 $tLisi) -eq 'PENDING')
$r = Call POST "/ticket/$id1/approve" @{ comment = '同意' } $tLisi; Show $r
Check '作业负责人没有审批权限（403）' ($r.code -eq 403)

Title '4. 安全员 zhangsan 审批'
$d = (Call GET "/ticket/$id1" $null $tZhang).data
Write-Host "  zhangsan 可执行的操作 = $($d.actions -join ', ')"
Check '安全员可以批准、驳回' (($d.actions -contains 'APPROVE') -and ($d.actions -contains 'REJECT'))
$r = Call POST "/ticket/$id1/approve" @{ comment = '安全措施到位，同意作业' } $tZhang; Show $r
Check '批准成功，状态 = 已批准' ($r.code -eq 200 -and (Status $id1 $tZhang) -eq 'APPROVED')
$r = Call POST "/ticket/$id1/approve" @{ comment = '再批一次' } $tZhang; Show $r
Check '状态机：不能重复批准' ($r.code -eq 400)

Title '5. 开工 / 完工只能由指定监护人操作'
$r = Call POST "/ticket/$id1/start" @{} $tLisi; Show $r
Check '申请人不能开工（403）' ($r.code -eq 403)
$r = Call POST "/ticket/$id1/start" @{ gasOxygen = 20.8; gasLel = 1.0; gasH2s = 0.2; comment = '现场复测合格' } $tZhao; Show $r
Check '监护人复测气体后开工，状态 = 作业中' ($r.code -eq 200 -and (Status $id1 $tZhao) -eq 'IN_PROGRESS')
$r = Call POST "/ticket/$id1/complete" @{ comment = '作业完成，现场已清理' } $tZhao; Show $r
Check '完工，状态 = 已完成' ($r.code -eq 200 -and (Status $id1 $tZhao) -eq 'COMPLETED')

Title '6. 操作记录'
$d = (Call GET "/ticket/$id1" $null $tAdmin).data
$d.logs | ForEach-Object { Write-Host ("  {0}  {1,-8} {2,-12} -> {3,-12} {4}  {5}" -f $_.createTime, $_.action, $_.fromStatus, $_.toStatus, $_.operatorName, $_.comment) }
Check '完整记录 5 步：创建、提交、批准、开工、完工' (@($d.logs).Count -eq 5)
Check '审批人记录为安全员' ($d.ticket.approverId -eq '102')

Title '7. 气体检测不合格不能提交；驳回后修改重新提交'
$r = Call POST '/ticket' (NewTicket 'CONFINED_SPACE' '污水池清淤' 104 18.0) $tLisi
$id2 = $r.data
Check '草稿可以先保存（不校验气体）' ($r.code -eq 200)
$r = Call POST "/ticket/$id2/submit" $null $tLisi; Show $r
Check '氧含量 18% 不合格，提交被拒绝' ($r.code -eq 400)
$r = Call PUT "/ticket/$id2" (NewTicket 'CONFINED_SPACE' '污水池清淤' 104 20.9) $tLisi; Show $r
Check '修改气体数据' ($r.code -eq 200)
$r = Call POST "/ticket/$id2/submit" $null $tLisi
Check '重新提交成功' ($r.code -eq 200)
$r = Call POST "/ticket/$id2/reject" @{} $tZhang; Show $r
Check '驳回必须填写原因' ($r.code -eq 400)
$r = Call POST "/ticket/$id2/reject" @{ comment = '缺少盲板抽堵作业票' } $tZhang; Show $r
Check '驳回成功，状态 = 已驳回' ($r.code -eq 200 -and (Status $id2 $tLisi) -eq 'REJECTED')
$r = Call POST "/ticket/$id2/submit" $null $tLisi
Check '驳回后可以再次提交' ($r.code -eq 200 -and (Status $id2 $tLisi) -eq 'PENDING')

Title '8. 并发测试：同时发出两个审批请求，只能成功一个（乐观锁）'
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
function NewApproveReq {
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, "$base/ticket/$id2/approve")
    $req.Headers.Add('Authorization', "Bearer $tZhang")
    $req.Content = New-Object System.Net.Http.StringContent('{"comment":"concurrent"}', [Text.Encoding]::UTF8, 'application/json')
    return $req
}
$tasks = [System.Threading.Tasks.Task[]]@($client.SendAsync((NewApproveReq)), $client.SendAsync((NewApproveReq)))
[System.Threading.Tasks.Task]::WaitAll($tasks)
$results = $tasks | ForEach-Object {
    [Text.Encoding]::UTF8.GetString($_.Result.Content.ReadAsByteArrayAsync().Result) | ConvertFrom-Json
}
$results | ForEach-Object { Show $_ }
$okCount = @($results | Where-Object { $_.code -eq 200 }).Count
Check "两个请求只有 1 个成功（成功 $okCount 个）" ($okCount -eq 1)
$logs = (Call GET "/ticket/$id2" $null $tAdmin).data.logs | Where-Object { $_.action -eq 'APPROVE' }
Check '只产生 1 条审批记录' (@($logs).Count -eq 1)

Title '8.5 同一受限空间、时间重叠，不能同时有两张有效作业票'
$r = Call POST '/ticket' (NewTicket 'CONFINED_SPACE' '同空间第二张票' 104 20.9) $tLisi
$id4 = $r.data
Call POST "/ticket/$id4/submit" $null $tLisi | Out-Null
$r = Call POST "/ticket/$id4/approve" @{ comment = '同意' } $tZhang; Show $r
Check '时间冲突，批准被拒绝' ($r.code -eq 400)
# 清理：取消这两张票，保证脚本可以重复运行
Call POST "/ticket/$id2/cancel" @{ comment = '测试清理' } $tLisi | Out-Null
Call POST "/ticket/$id4/cancel" @{ comment = '测试清理' } $tLisi | Out-Null
Check '清理完成（已取消）' ((Status $id2 $tLisi) -eq 'CANCELLED' -and (Status $id4 $tLisi) -eq 'CANCELLED')

Title '9. 管理员有全部权限，但也不能审批自己的票（职责分离）'
$r = Call POST '/ticket' (NewTicket 'HEIGHT' '厂房屋顶检修' 104 $null) $tAdmin
$id3 = $r.data
Check '高处作业不需要气体检测，可以直接创建' ($r.code -eq 200)
Call POST "/ticket/$id3/submit" $null $tAdmin | Out-Null
$r = Call POST "/ticket/$id3/approve" @{ comment = '自己批' } $tAdmin; Show $r
Check '不能审批自己申请的作业票' ($r.code -eq 403)

Title '10. 数据权限与租户隔离'
$r = Call GET "/ticket/$id3" $null $tLisi; Show $r
Check 'lisi 看不到别人申请、且自己不是监护人的票' ($r.code -eq 404)
$page = (Call GET '/ticket/page?size=100' $null $tLisi).data
$others = @($page.records | Where-Object { $_.applicantId -ne '103' -and $_.guardianId -ne '103' })
Check "lisi 的列表只包含与自己相关的票（共 $($page.total) 张）" ($others.Count -eq 0)
$pageAll = (Call GET '/ticket/page?size=1' $null $tZhang).data
Check "安全员能看到全部（$($pageAll.total) 张 >= lisi 的 $($page.total) 张）" ($pageAll.total -gt $page.total)
$r = Call GET "/ticket/$id1" $null $tTest; Show $r
Check '测试冶金查不到演示化工的作业票' ($r.code -eq 404)

Title '11. 参数校验'
$bad = '{"ticketType":"CONFINED_SPACE","title":"x","location":"x","planStartTime":"2026/10/28 09:00","planEndTime":"2026-10-28 18:00:00","guardianId":104}'
$r = Call POST '/ticket' $bad $tLisi; Show $r
Check '日期格式错误返回 400' ($r.code -eq 400)
$r = Call GET '/ticket/page?status=ABC' $null $tLisi; Show $r
Check '状态值错误返回 400' ($r.code -eq 400)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过，作业票审批流完成！' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Yellow }
