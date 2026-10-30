# 消息通知 + 定时任务自测：实时推送（SSE）、作业票/隐患流转通知、已读、公告、定时任务、分布式锁、去重
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-message.ps1
# 可以重复运行。后端必须用开发环境启动：java -jar safework-server\target\safework-server.jar --spring.profiles.active=dev

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
Add-Type -AssemblyName System.Net.Http

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
function LoginRaw($tenant, $user, $password) {
    $c = (Call GET '/auth/captcha').data
    if ($c.enabled -and -not $c.debugAnswer) { throw '后端没有返回验证码答案：请用 --spring.profiles.active=dev 启动后端' }
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
# 某人关于某个单据的消息
function MsgsOf($token, $bizId) { ,@((Call GET '/message/page?size=100' $null $token).data.records | Where-Object { $_.bizId -eq $bizId }) }
function Unread($token) { [int](Call GET '/message/unread' $null $token).data.total }

# 打开消息推送长连接（和浏览器一样），返回读取器
function OpenStream($token) {
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromMinutes(5)
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, "$base/message/stream")
    $req.Headers.Add('Authorization', "Bearer $token")
    $resp = $client.SendAsync($req, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).Result
    $reader = New-Object System.IO.StreamReader($resp.Content.ReadAsStreamAsync().Result, [Text.Encoding]::UTF8)
    return @{ client = $client; reader = $reader; pending = $null }
}
# 读取下一个事件（event + data），超时返回 $null。以冒号开头的心跳行会被跳过
function ReadEvent($s, $seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    $event = ''; $data = ''
    while ($true) {
        $left = [int]($deadline - (Get-Date)).TotalMilliseconds
        if ($left -le 0) { return $null }
        if (-not $s.pending) { $s.pending = $s.reader.ReadLineAsync() }
        if (-not $s.pending.Wait($left)) { return $null }
        $line = $s.pending.Result; $s.pending = $null
        if ($null -eq $line) { return $null }
        if ($line -eq '') {
            if ($data) { return @{ event = $event; data = ($data | ConvertFrom-Json) } }
            $event = ''; $data = ''; continue
        }
        if ($line.StartsWith('event:')) { $event = $line.Substring(6).Trim() }
        elseif ($line.StartsWith('data:')) { $data += $line.Substring(5) }
    }
}
# 读到指定类型的事件为止（中间的未读数更新等事件跳过）
function ReadUntil($s, $name, $seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $e = ReadEvent $s ([Math]::Max(1, ($deadline - (Get-Date)).TotalSeconds))
        if ($null -eq $e) { return $null }
        if ($e.event -eq $name) { return $e }
    }
    return $null
}
$failed = 0
$now = Get-Date
$ts = Get-Date -Format 'MMddHHmmss'

$tLisi  = Login 'demo' 'lisi'
$tZhang = Login 'demo' 'zhangsan'
$tZhao  = Login 'demo' 'zhaoliu'
$tAdmin = Login 'demo' 'admin'
$tTest  = Login 'test' 'admin'
$tPlat  = Login 'platform' 'admin'

Title '1. 建立实时推送长连接（SSE）'
$s = OpenStream $tZhang
$e = ReadEvent $s 5
Check "连接后立即收到未读数（$($e.data.count)）" ($e.event -eq 'unread')

Title '2. 作业负责人提交作业票 → 审批人实时收到待办'
$ticket = @{ ticketType = 'HOT_WORK'; title = "消息测试动火 $ts"; location = '机修车间'; guardianId = 104
             safetyMeasures = '1. 清理周边可燃物 2. 配备灭火器'; planStartTime = (Fmt $now); planEndTime = (Fmt $now.AddHours(4))
             gasOxygen = 20.9; gasLel = 1.5; gasH2s = 0; gasCheckTime = (Fmt $now) }
$tid = (Call POST '/ticket' $ticket $tLisi).data
$no = (Call GET "/ticket/$tid" $null $tLisi).data.ticket.ticketNo
$before = Unread $tZhang
$r = Call POST "/ticket/$tid/submit" @{} $tLisi
Check "提交作业票 $no" ($r.code -eq 200)
$e = ReadUntil $s 'message' 5
Write-Host "  长连接收到：event = $($e.event)，标题 = $($e.data.title)" -ForegroundColor DarkGray
Check '安全员不用刷新，实时收到推送' ($e.event -eq 'message' -and $e.data.title.Contains($no))
Check '消息分类是待办，带跳转地址' ($e.data.category -eq 'TODO' -and $e.data.link -eq "/ticket/list?detail=$tid")
Check '数据库里的未读数 +1' ((Unread $tZhang) -eq $before + 1)
Check '企业管理员也有审批权限，也收到了' ((MsgsOf $tAdmin $tid).Count -eq 1)
Check '提交人自己不会收到' ((MsgsOf $tLisi $tid).Count -eq 0)
Check '监护人此时还收不到' ((MsgsOf $tZhao $tid).Count -eq 0)

Title '3. 审批通过 → 申请人收到通知，监护人收到待开工'
$r = Call POST "/ticket/$tid/approve" @{ comment = '同意，注意动火监护' } $tZhang
Check '安全员批准' ($r.code -eq 200)
$m = MsgsOf $tLisi $tid
Check "申请人收到「已批准」（$($m[0].title)）" ($m.Count -eq 1 -and $m[0].category -eq 'NOTICE' -and $m[0].content.Contains('注意动火监护'))
$m = MsgsOf $tZhao $tid
Check "监护人收到「待开工」" ($m.Count -eq 1 -and $m[0].title.StartsWith('待开工'))
Check '审批人自己不会收到自己的审批通知' ((MsgsOf $tZhang $tid).Count -eq 1)

Title '4. 已读、越权'
$mine = (MsgsOf $tZhang $tid)[0]
$u1 = Unread $tZhang
$r = Call PUT "/message/$($mine.id)/read" @{} $tZhang
Check '标记已读，未读数 -1' ($r.code -eq 200 -and (Unread $tZhang) -eq $u1 - 1)
$adminMsg = (MsgsOf $tAdmin $tid)[0]
$r = Call PUT "/message/$($adminMsg.id)/read" @{} $tZhang; Show $r
Check '不能标记别人的消息（404）' ($r.code -eq 404)
$r = Call PUT "/message/$($adminMsg.id)/read" @{} $tTest
Check '别的企业更不行（404）' ($r.code -eq 404)
$r = Call PUT '/message/read-all' @{} $tZhang
Check "全部已读（$($r.data) 条），未读数归零" ($r.code -eq 200 -and (Unread $tZhang) -eq 0)

Title '5. 隐患闭环：每一步通知下一个处理人'
$hid = (Call POST '/safety/hazard' @{ title = "动火点附近油桶未清理 $ts"; category = '作业环境'; level = 'MAJOR'; location = '机修车间' } $tZhao).data
$m = MsgsOf $tZhang $hid
Check "重大隐患上报 → 安全员收到预警（$($m[0].title)）" ($m.Count -eq 1 -and $m[0].category -eq 'WARN' -and $m[0].title.Contains('重大隐患'))
$e = ReadUntil $s 'message' 5
Check '预警也是实时推送的' ($e.event -eq 'message' -and $e.data.bizId -eq $hid)
Call POST "/safety/hazard/$hid/assign" @{ rectifierId = 103; deadline = (Fmt $now.AddDays(1)); comment = '下班前清走' } $tZhang | Out-Null
$m = MsgsOf $tLisi $hid
Check '指派后整改人收到待整改' ($m.Count -eq 1 -and $m[0].title.StartsWith('待整改'))
Call POST "/safety/hazard/$hid/rectify" @{ rectifyMeasure = '油桶已移到危化品库' } $tLisi | Out-Null
$m = MsgsOf $tZhang $hid
Check '整改完成后安全员收到待验收' (@($m | Where-Object { $_.title.StartsWith('待验收') }).Count -eq 1)
Check '整改人自己不会收到待验收' (@(MsgsOf $tLisi $hid | Where-Object { $_.title.StartsWith('待验收') }).Count -eq 0)
Call POST "/safety/hazard/$hid/verify-pass" @{ comment = '现场复查合格' } $tZhang | Out-Null
Check '验收通过后上报人收到闭环通知' (@(MsgsOf $tZhao $hid | Where-Object { $_.title.StartsWith('隐患已闭环') }).Count -eq 1)

Title '6. 通知公告'
$r = Call POST '/system/notice' @{ title = "冬季防冻检查通知 $ts"; level = 'IMPORTANT'; content = "各车间：`n请于本周五前完成消防管线防冻检查。" } $tZhang; Show $r
Check '安全员没有发布权限（403）' ($r.code -eq 403)
$r = Call POST '/system/notice' @{ title = "冬季防冻检查通知 $ts"; level = 'IMPORTANT'; content = "各车间：`n请于本周五前完成消防管线防冻检查。" } $tAdmin
Check '企业管理员发布公告' ($r.code -eq 200)
$nid = $r.data
$m = MsgsOf $tLisi $nid
Check "全员收到公告消息（$($m[0].title)）" ($m.Count -eq 1 -and $m[0].category -eq 'ANNOUNCE' -and $m[0].title.StartsWith('【重要】'))
$n = (Call GET "/system/notice/page?keyword=$ts" $null $tAdmin).data.records[0]
Write-Host "  发送人数 = $($n.receiverCount)" -ForegroundColor DarkGray
Check '记录了发送人数' ($n.receiverCount -ge 4)
$r = Call GET "/message/notice/$nid" $null $tLisi
Check '收到的人可以查看全文' ($r.code -eq 200 -and $r.data.content.Contains('防冻'))
$r = Call GET "/message/notice/$nid" $null $tTest
Check '别的企业看不到（404）' ($r.code -eq 404)
$r = Call DELETE "/system/notice/$nid" $null $tAdmin
Check '撤回公告后，已发出的消息也删除' ($r.code -eq 200 -and (MsgsOf $tLisi $nid).Count -eq 0)

Title '7. 定时任务'
$jobs = (Call GET '/platform/job/list' $null $tPlat).data
$jobs | ForEach-Object { Write-Host "  - $($_.code)  $($_.cron)  下次 $($_.nextTime)  $($_.name)" -ForegroundColor DarkGray }
$expectJobs = @('hazard-deadline', 'ticket-overtime', 'tenant-expire', 'data-cleanup', 'exam-reminder', 'exam-auto-submit', 'risk-check')
$missing = @($expectJobs | Where-Object { @($jobs.code) -notcontains $_ })
Check "注册了 $(@($jobs).Count) 个任务（隐患、作业票、到期、清理、考试提醒、考试自动交卷、风险巡检）" ($missing.Count -eq 0 -and @($jobs).Count -eq $expectJobs.Count)
$r = Call GET '/platform/job/list' $null $tAdmin
Check '企业管理员不能看定时任务（403）' ($r.code -eq 403)
$r = Call POST '/platform/job/hazard-deadline/run' @{} $tPlat
Write-Host "  结果：$($r.data.result)（$($r.data.costMs) ms，$($r.data.server)）" -ForegroundColor DarkGray
Check '手动执行隐患期限检查成功' ($r.code -eq 200 -and $r.data.status -eq 1)
$warn = @(MsgsOf $tLisi '2' | Where-Object { $_.category -eq 'WARN' })
Check '演示数据里逾期的隐患（R-201 渗漏）整改人李四收到逾期预警' ($warn.Count -ge 1)
$r = Call POST '/platform/job/hazard-deadline/run' @{} $tPlat
$warn2 = @(MsgsOf $tLisi '2' | Where-Object { $_.category -eq 'WARN' })
Check '同一天再执行一次不会重复提醒（去重）' ($warn2.Count -eq $warn.Count)
foreach ($code in 'ticket-overtime', 'tenant-expire', 'data-cleanup') {
    $r = Call POST "/platform/job/$code/run" @{} $tPlat
    Check "$code：$($r.data.result)" ($r.code -eq 200 -and $r.data.status -eq 1)
}
$logs = (Call GET '/platform/job/log/page?size=20' $null $tPlat).data
Check "执行记录（共 $($logs.total) 条），记录了手动执行人" ($logs.total -ge 5 -and $logs.records[0].triggerType -eq 'MANUAL' -and $logs.records[0].operatorName)
$r = Call POST '/platform/job/not-exist/run' @{} $tPlat
Check '任务不存在返回 404' ($r.code -eq 404)

$s.client.Dispose()
Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过，消息通知 + 定时任务完成！' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Yellow }
