# 事故调查自测：权限、启动调查（期限按等级）、组长权限、整改措施和责任人、责任处理、警示教育、“四不放过”结案、写进 AI 知识库、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-accident.ps1

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
function Fmt($d) { $d.ToString('yyyy-MM-dd HH:mm:ss') }
$failed = 0

$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tZhao  = Login 'demo' 'zhaoliu'
$tTest  = Login 'test' 'admin'
$lisiId = (Call GET '/auth/me' $null $tLisi).data.user.id
$zhaoId = (Call GET '/auth/me' $null $tZhao).data.user.id
$tag = Get-Random -Minimum 1000 -Maximum 9999
$now = Get-Date
$day = { param($n) $now.AddDays($n).ToString('yyyy-MM-dd') }
$cause = '作业前未进行气体检测，盲目进入受限空间（自测）'

Title '0. 准备：上报一起事故'
$r = Call POST '/safety/emergency' @{ title = "自测事故 $tag"; accidentType = 'POISONING'; level = 'GENERAL'; location = '污水处理站'; description = '自测用，作业人员轻微不适'; minorInjured = 1; occurTime = (Fmt $now.AddHours(-1)) } $tLisi
$eventId = $r.data
Check "李四上报事故快报 id = $eventId" ($r.code -eq 200)

Title '1. 权限'
$r = Call GET '/safety/accident/page' $null $tZhao; Show $r
Check '监护人能看事故调查列表' ($r.code -eq 200)
$r = Call GET '/safety/accident/candidates' $null $tLisi; Show $r
Check '作业负责人不能启动调查' ($r.code -ne 200)
$r = Call POST '/safety/accident' @{ eventId = $eventId; leaderId = $lisiId } $tLisi; Show $r
Check '作业负责人不能启动调查（直接调接口）' ($r.code -ne 200)

Title '2. 启动调查'
$c = (Call GET '/safety/accident/candidates' $null $tZhang).data
Check '待调查列表里有这起事故' ((@($c | Where-Object { $_.eventId -eq $eventId })).Count -eq 1)
$r = Call POST '/safety/accident' @{ eventId = $eventId; leaderId = $lisiId; deadline = (& $day -1) } $tZhang; Show $r
Check '调查期限不能早于今天' ($r.code -ne 200)
$r = Call POST '/safety/accident' @{ eventId = $eventId; leaderId = $lisiId; members = '自测调查组' } $tZhang
$invId = $r.data
Check "启动调查，组长李四 id = $invId" ($r.code -eq 200)
$r = Call POST '/safety/accident' @{ eventId = $eventId; leaderId = $lisiId } $tZhang; Show $r
Check '同一起事故不能重复启动调查' ($r.code -ne 200)
$d = (Call GET "/safety/accident/$invId" $null $tZhang).data
Check "一般事故调查期限 = 事故日期 + 30 天：$($d.investigation.deadline)" ($d.investigation.deadline -eq $now.AddHours(-1).AddDays(30).ToString('yyyy-MM-dd'))
Check '刚启动：“四不放过”一项都没满足，不能结案' ((@($d.checks | Where-Object { $_.ok })).Count -eq 0 -and $d.canClose -eq $true)
$r = Call POST "/safety/accident/$invId/close" @{ toKnowledge = $false } $tZhang; Show $r
Check '“四不放过”没满足不能结案' ($r.code -ne 200)

Title '3. 组长填写调查内容'
$d = (Call GET "/safety/accident/$invId" $null $tLisi).data
Check '李四（组长）能填写，但不能结案' ($d.canEdit -eq $true -and $d.canClose -eq $false)
$r = Call PUT "/safety/accident/$invId/analysis" @{ directCause = $cause; managementCause = '审批不严'; economicLoss = 0.5 } $tLisi
$d = (Call GET "/safety/accident/$invId" $null $tLisi).data
Check '管理原因太短（少于 10 个字）不算查清' ($r.code -eq 200 -and $d.checks[0].ok -eq $false)
$r = Call PUT "/safety/accident/$invId/analysis" @{ directCause = $cause; indirectCause = '未设置固定式报警仪'; managementCause = '作业票审批时未核实气体检测记录，监护人兼顾其他工作'; economicLoss = 0.5; lessons = '先通风、再检测、后作业' } $tLisi
$d = (Call GET "/safety/accident/$invId" $null $tLisi).data
Check "保存原因分析：$($d.checks[0].hint)" ($r.code -eq 200 -and $d.checks[0].ok -eq $true)
$r = Call PUT "/safety/accident/$invId/analysis" @{ directCause = $cause; managementCause = $cause } $tZhao; Show $r
Check '赵六（不是组长）不能填写' ($r.code -ne 200)
$r = Call POST "/safety/accident/$invId/punish" @{ personName = '自测班长'; duty = 'ALIEN'; measure = '通报批评' } $tLisi; Show $r
Check '责任类别不正确被拒绝' ($r.code -ne 200)
$r = Call POST "/safety/accident/$invId/punish" @{ personName = '自测班长'; duty = 'DIRECT'; measure = '通报批评，停岗培训' } $tLisi
Check '登记责任处理' ($r.code -eq 200)

Title '4. 整改措施'
$r = Call POST "/safety/accident/$invId/measure" @{ content = '自测措施'; ownerId = $zhaoId; deadline = (& $day -1) } $tLisi; Show $r
Check '整改期限不能早于今天' ($r.code -ne 200)
$r = Call POST "/safety/accident/$invId/measure" @{ content = '自测措施'; ownerId = $zhaoId; deadline = (& $day 181) } $tLisi; Show $r
Check '整改期限最长 180 天' ($r.code -ne 200)
$r = Call POST "/safety/accident/$invId/measure" @{ content = "安装固定式硫化氢报警仪（自测 $tag）"; ownerId = $zhaoId; deadline = (& $day 7) } $tLisi
$m1 = $r.data
$r2 = Call POST "/safety/accident/$invId/measure" @{ content = '临时措施（自测，稍后删除）'; ownerId = $zhaoId; deadline = (& $day 3) } $tLisi
$r3 = Call DELETE "/safety/accident/measure/$($r2.data)" $null $tLisi
Check '新增两项措施，删除一项' ($r.code -eq 200 -and $r2.code -eq 200 -and $r3.code -eq 200)
$mine = @((Call GET '/safety/accident/measure/mine' $null $tZhao).data | Where-Object { $_.id -eq $m1 })
Check '赵六的“我的整改措施”里有这项，带事故名称' ($mine.Count -eq 1 -and $mine[0].eventTitle -eq "自测事故 $tag" -and $mine[0].state -eq 'OPEN')
$mh = (Call GET '/mobile/home' $null $tZhao).data
Check "赵六手机首页显示事故整改 $($mh.accident) 项" ([int]$mh.accident -ge 1)
$r = Call POST "/safety/accident/measure/$m1/done" @{ note = '已完成' } $tLisi; Show $r
Check '李四（不是责任人）不能替赵六填写落实情况' ($r.code -ne 200)
$r = Call POST "/safety/accident/measure/$m1/done" @{ note = '' } $tZhao; Show $r
Check '落实情况不能为空' ($r.code -ne 200)
$r = Call POST "/safety/accident/measure/$m1/done" @{ note = '已安装 2 台报警仪并接入中控室（自测）' } $tZhao
Check '赵六填写落实情况，措施完成' ($r.code -eq 200)
$r = Call POST "/safety/accident/measure/$m1/done" @{ note = '再来一次' } $tZhao; Show $r
Check '不能重复完成' ($r.code -ne 200)
$r = Call DELETE "/safety/accident/measure/$m1" $null $tLisi; Show $r
Check '已完成的措施不能删除' ($r.code -ne 200)

Title '5. 警示教育和结案'
$r = Call PUT "/safety/accident/$invId/education" @{ educationDate = (& $day 1); educationCount = 20 } $tLisi; Show $r
Check '教育日期不能晚于今天' ($r.code -ne 200)
$r = Call PUT "/safety/accident/$invId/education" @{ educationDate = (& $day 0); educationCount = 0 } $tLisi; Show $r
Check '参加人数至少 1 人' ($r.code -ne 200)
$r = Call PUT "/safety/accident/$invId/education" @{ educationDate = (& $day 0); educationCount = 35; educationNote = '班组长以上召开警示会（自测）' } $tLisi
$d = (Call GET "/safety/accident/$invId" $null $tZhang).data
Check "登记警示教育后“四不放过”全部满足：$((@($d.checks | ForEach-Object { $_.hint })) -join '；')" ($r.code -eq 200 -and (@($d.checks | Where-Object { $_.ok })).Count -eq 4)
$r = Call POST "/safety/accident/$invId/close" @{ toKnowledge = $true } $tLisi; Show $r
Check '组长不能结案（要管理员）' ($r.code -ne 200)
$r = Call POST "/safety/accident/$invId/close" @{ toKnowledge = $true } $tZhang
Check '安全员结案' ($r.code -eq 200)
$d = (Call GET "/safety/accident/$invId" $null $tZhang).data
Check '结案后状态为已结案，不能再填写' ($d.investigation.status -eq 'CLOSED' -and $d.canEdit -eq $false -and $d.canClose -eq $false)
$r = Call PUT "/safety/accident/$invId/analysis" @{ directCause = $cause; managementCause = $cause } $tZhang; Show $r
Check '结案后不能修改' ($r.code -ne 200)
Start-Sleep -Seconds 2
$docs = @((Call GET "/ai/knowledge/page?size=50&keyword=$([uri]::EscapeDataString("自测事故 $tag"))" $null $tZhang).data.records)
Check "事故案例已写进 AI 知识库：$($docs[0].title)" ($docs.Count -ge 1)

Title '6. 租户隔离'
$r = Call GET "/safety/accident/$invId" $null $tTest; Show $r
Check '别的企业打不开这份事故调查' ($r.code -ne 200)
$r = Call GET '/safety/accident/candidates' $null $tTest
Check '别的企业的待调查列表里没有这起事故' ($r.code -ne 200 -or (@($r.data | Where-Object { $_.eventId -eq $eventId })).Count -eq 0)

Title '7. 清理'
foreach ($doc in $docs) { Call DELETE "/ai/knowledge/$($doc.id)" $null $tZhang | Out-Null }
Call POST "/safety/emergency/$eventId/close" @{ content = '自测事故，已完成调查' } $tZhang | Out-Null
Write-Host '  已删除知识库里的自测案例、结束自测事故快报（调查记录保留，demo-reset 后消失）' -ForegroundColor DarkGray

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
