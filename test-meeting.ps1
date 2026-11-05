# 班前会自测：班组权限、开会校验（日期、要点、点名）、AI 生成安全要点、状态不好不能上岗、同一班次不能重复、成员确认、照片权限、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-meeting.ps1

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
$zhangId = (Call GET '/auth/me' $null $tZhang).data.user.id
$lisiId = (Call GET '/auth/me' $null $tLisi).data.user.id
$zhaoId = (Call GET '/auth/me' $null $tZhao).data.user.id
$tag = Get-Random -Minimum 1000 -Maximum 9999
$today = (Get-Date).ToString('yyyy-MM-dd')
$topics = "1. 自测：今天罐区动火作业，动火前可燃气体检测合格`n2. 正确佩戴劳动防护用品"
function Roll($lisiAttend, $lisiState, $zhaoAttend, $zhaoState) {
    return @(@{ userId = $lisiId; attend = $lisiAttend; bodyState = $lisiState }, @{ userId = $zhaoId; attend = $zhaoAttend; bodyState = $zhaoState })
}

Title '1. 班组'
$r = Call POST '/safety/meeting/team' @{ name = "自测班组 $tag"; leaderId = $lisiId; memberIds = @($zhaoId) } $tLisi; Show $r
Check '作业负责人不能新增班组' ($r.code -ne 200)
$r = Call POST '/safety/meeting/team' @{ name = "自测班组 $tag"; leaderId = $lisiId; memberIds = @($zhaoId) } $tZhang
$teamId = $r.data
$t = @((Call GET '/safety/meeting/team/list' $null $tLisi).data | Where-Object { $_.team.id -eq $teamId })[0]
Check "新增班组，班组长李四自动加进成员：$(@($t.members).Count) 人，李四能开会" ($r.code -eq 200 -and @($t.members).Count -eq 2 -and $t.canHold -eq $true -and $t.today -eq $false)

Title '2. 开会校验'
$ok = @{ teamId = $teamId; meetingDate = $today; shift = 'DAY'; topics = $topics; roll = (Roll 'PRESENT' 'GOOD' 'PRESENT' 'TIRED') }
$r = Call POST '/safety/meeting' $ok $tZhao; Show $r
Check '赵六不是班组长，不能开' ($r.code -ne 200)
$x = $ok.Clone(); $x.topics = '注意安全'
$r = Call POST '/safety/meeting' $x $tLisi; Show $r
Check '安全要点太短' ($r.code -ne 200)
$x = $ok.Clone(); $x.meetingDate = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$r = Call POST '/safety/meeting' $x $tLisi; Show $r
Check '不能登记以后的' ($r.code -ne 200)
$x = $ok.Clone(); $x.meetingDate = (Get-Date).AddDays(-4).ToString('yyyy-MM-dd')
$r = Call POST '/safety/meeting' $x $tLisi; Show $r
Check '只能补录 3 天内的' ($r.code -ne 200)
$x = $ok.Clone(); $x.shift = 'ALIEN'
$r = Call POST '/safety/meeting' $x $tLisi; Show $r
Check '班次不正确' ($r.code -ne 200)
$x = $ok.Clone(); $x.roll = @(@{ userId = $lisiId; attend = 'PRESENT'; bodyState = 'GOOD' })
$r = Call POST '/safety/meeting' $x $tLisi; Show $r
Check '有成员没点名' ($r.code -ne 200)
$x = $ok.Clone(); $x.roll = @(@{ userId = $lisiId; attend = 'PRESENT'; bodyState = 'GOOD' }, @{ userId = $zhangId; attend = 'PRESENT'; bodyState = 'GOOD' })
$r = Call POST '/safety/meeting' $x $tLisi; Show $r
Check '点名里有不是这个班组的人' ($r.code -ne 200)
$x = $ok.Clone(); $x.roll = (Roll 'PRESENT' 'GOOD' 'PRESENT' $null)
$r = Call POST '/safety/meeting' $x $tLisi; Show $r
Check '出席的人必须选身体精神状态' ($r.code -ne 200)

Title '3. AI 生成安全要点'
$r = Call POST "/ai/meeting/$teamId/tips" $null $tZhao; Show $r
Check '赵六不是班组长，不能生成' ($r.code -ne 200)
$r = Call POST "/ai/meeting/$teamId/tips" $null $tLisi
Check "李四生成 $(@($r.data.tips).Count) 条要点（$(if ($r.data.aiUsed) { '大模型' } else { '规则：' + $r.data.note })），今天作业票 $(@($r.data.works).Count) 张" ($r.code -eq 200 -and @($r.data.tips).Count -ge 2)

Title '4. 开班前会'
$r = Call POST '/safety/meeting' $ok $tLisi
$mid = $r.data
$d = (Call GET "/safety/meeting/$mid" $null $tLisi).data
Check "李四开白班班前会：出席 $($d.meeting.presentCount)/$($d.meeting.memberCount)，不能上岗 $($d.meeting.unfitCount) 人（赵六精神疲劳）" ($r.code -eq 200 -and [int]$d.meeting.unfitCount -eq 1)
$me = @($d.attendees | Where-Object { $_.userId -eq $lisiId })[0]
Check '主持人自己自动确认' ($null -ne $me.confirmTime)
$r = Call POST '/safety/meeting' $ok $tLisi; Show $r
Check '同一天同一班次不能重复开' ($r.code -ne 200)
$x = $ok.Clone(); $x.shift = 'NIGHT'; $x.roll = (Roll 'PRESENT' 'GOOD' 'LEAVE' $null)
$r = Call POST '/safety/meeting' $x $tLisi
$d2 = (Call GET "/safety/meeting/$($r.data)" $null $tLisi).data
Check '夜班可以再开一次；请假的人不算“不能上岗”' ($r.code -eq 200 -and [int]$d2.meeting.unfitCount -eq 0 -and [int]$d2.meeting.presentCount -eq 1)
$t = @((Call GET '/safety/meeting/team/list' $null $tLisi).data | Where-Object { $_.team.id -eq $teamId })[0]
Check "班组今天已开，最近 30 天开了 $($t.held30) 天（同一天两个班次算 1 天）" ($t.today -eq $true -and [int]$t.held30 -eq 1)

Title '5. 成员确认'
$p = @((Call GET '/safety/meeting/mine' $null $tZhao).data.pending | Where-Object { $_.meeting.id -eq $mid })
Check '赵六的手机上有这条待确认' ($p.Count -eq 1)
$r = Call POST "/safety/meeting/$mid/confirm" $null $tZhang; Show $r
Check '不在点名里的人不能确认' ($r.code -ne 200)
$r = Call POST "/safety/meeting/$mid/confirm" $null $tZhao
Check '赵六确认' ($r.code -eq 200)
$r = Call POST "/safety/meeting/$mid/confirm" $null $tZhao; Show $r
Check '不能重复确认' ($r.code -ne 200)
$nid = (@((Call GET "/safety/meeting/page?teamId=$teamId" $null $tZhang).data.records | Where-Object { $_.shift -eq 'NIGHT' }))[0].id
$r = Call POST "/safety/meeting/$nid/confirm" $null $tZhao; Show $r
Check '请假的人不需要确认' ($r.code -ne 200)
$d = (Call GET "/safety/meeting/$mid" $null $tZhang).data
Check "已确认 $($d.meeting.confirmedCount) 人" ([int]$d.meeting.confirmedCount -eq 2)
$r = Call GET "/safety/meeting/page?unfit=true&teamId=$teamId" $null $tZhang
Check '“只看有人不能上岗的”能筛出白班这次' ($r.code -eq 200 -and (@($r.data.records | Where-Object { $_.id -eq $mid })).Count -eq 1 -and [int]$r.data.total -eq 1)

Title '6. 照片权限、统计、隔离'
$r = Call GET "/file/list?bizType=MEETING&bizId=$mid&tag=PHOTO" $null $tLisi
Check '主持人能上传照片' ($r.code -eq 200 -and $r.data.editable -eq $true)
$r = Call GET "/file/list?bizType=MEETING&bizId=$mid&tag=PHOTO" $null $tZhao
Check '成员能看照片，不能上传' ($r.code -eq 200 -and $r.data.editable -eq $false)
$ov = (Call GET '/safety/meeting/overview' $null $tZhang).data
Check "统计：$($ov.teams) 个班组，今天开了 $($ov.heldToday) 个，不能上岗 $($ov.unfitToday) 人，30 天开展率 $($ov.rate30)%" ([int]$ov.unfitToday -ge 1)
$mh = (Call GET '/mobile/home' $null $tLisi).data
Check "李四手机首页的班前会待办 $($mh.meeting)" ($null -ne $mh.meeting)
$r = Call GET "/safety/meeting/$mid" $null $tTest; Show $r
Check '别的企业打不开这次班前会' ($r.code -ne 200)

Title '7. 清理'
$r = Call PUT "/safety/meeting/team/$teamId/status?status=0" $null $tZhang
Check '停用自测班组（班前会记录保留，demo-reset 后消失）' ($r.code -eq 200)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
