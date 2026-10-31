# 应急管理自测：事故快报（等级按伤亡自动调高）、通知安全员、启动响应 / 处置记录 / 结束的状态流转和权限、
# 预案匹配建议、预案增改和编号查重、演练完成后更新预案的演练时间、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-emergency.ps1

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'

function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 90 }
    if ($null -ne $body) {
        $json = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 5 }
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    $resp = Invoke-WebRequest @params
    $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}
# 登录要先取验证码：开发环境（dev）后端会把答案放在 debugAnswer 里返回，脚本直接用它
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
function MsgsOf($token, $bizId) { ,@((Call GET '/message/page?size=100' $null $token).data.records | Where-Object { "$($_.bizId)" -eq "$bizId" }) }
function Detail($id, $token) { (Call GET "/safety/emergency/$id" $null $token).data }
$failed = 0

$tAdmin = Login 'demo' 'admin'
$tZhang = Login 'demo' 'zhangsan'   # 安全员：处置应急事件、编辑预案
$tLisi  = Login 'demo' 'lisi'       # 作业负责人：只能上报和查看
$tZhao  = Login 'demo' 'zhaoliu'    # 监护人：预案 YA-XC-01 的应急小组成员
$tTest  = Login 'test' 'admin'

Title '1. 演示数据'
$plans = @((Call GET '/safety/emergency/plan/list' $null $tZhang).data)
$xc01 = $plans | Where-Object { $_.planCode -eq 'YA-XC-01' }
Check "演示企业有 $($plans.Count) 份预案（至少 4 份）" ($plans.Count -ge 4)
Check "受限空间中毒窒息方案上次演练 2026-04-15，现场处置方案半年一次，状态：$($xc01.drillState)" ($xc01 -and $xc01.drillState -eq 'OVERDUE')
$zh01 = $plans | Where-Object { $_.planCode -eq 'YA-ZH-01' }
Check "综合预案从未演练：$($zh01.drillState)" ($zh01.drillState -eq 'NEVER')
$ov = (Call GET '/safety/emergency/overview' $null $tZhang).data
Check "概览：响应中 $($ov.responding) 起，今年 $($ov.reportedThisYear) 起，演练预警 $($ov.drillWarnings) 份" ($ov.drillWarnings -ge 2)

Title '2. 事故快报'
$now = Get-Date
$r = Call POST '/safety/emergency' @{ title = '测试：未来时间'; accidentType = 'POISONING'; level = 'GENERAL'; location = '污水处理站'; occurTime = (Fmt $now.AddHours(2)) } $tLisi; Show $r
Check '事发时间不能晚于现在' ($r.code -ne 200)
$r = Call POST '/safety/emergency' @{ title = '测试：未知类型'; accidentType = 'ALIEN'; level = 'GENERAL'; location = '污水处理站'; occurTime = (Fmt $now) } $tLisi; Show $r
Check '事故类型必须在字典里' ($r.code -ne 200)
$ev = @{ title = '自测：污水池清淤有人晕倒'; accidentType = 'POISONING'; level = 'GENERAL'; location = '污水处理站调节池'
         description = '清淤作业时一名作业人员晕倒，监护人已呼救'; dead = 0; seriousInjured = 10; minorInjured = 2; occurTime = (Fmt $now.AddMinutes(-5)) }
$r = Call POST '/safety/emergency' $ev $tLisi
Check '作业负责人可以上报事故' ($r.code -eq 200)
$id = $r.data
$d = Detail $id $tLisi
Check "报一般事故但重伤 10 人，等级自动调为较大事故：$($d.event.level)" ($d.event.level -eq 'LARGER')
Check "编号 $($d.event.eventNo)，状态已上报" ($d.event.eventNo -like 'YJ*' -and $d.event.status -eq 'REPORTED')
Check '作业负责人看不到处置按钮' (@($d.actions).Count -eq 0)
Check '快报记录里写明了等级自动调高' (@($d.logs)[0].content -like '*自动调高*')
$m = MsgsOf $tZhang $id
Check "安全员立即收到预警消息：$(@($m)[0].title)" ($m.Count -ge 1 -and @($m)[0].category -eq 'WARN')

Title '3. 预案匹配建议'
$r = Call POST "/ai/emergency/$id/advise" $null $tZhang
Check "返回建议（$(if ($r.data.aiUsed) { '大模型' } else { '规则摘取' })，耗时 $($r.data.costMs) ms）" ($r.code -eq 200)
if ($r.code -eq 200) {
    Check "建议启动：$($r.data.planCode) $($r.data.planTitle)" ($r.data.planCode -eq 'YA-XC-01')
    Check "首要处置步骤 $(@($r.data.steps).Count) 条" (@($r.data.steps).Count -ge 1)
    Check "候选预案只来自本企业：$((@($r.data.candidates) | ForEach-Object { $_.code }) -join '、')" (-not (@($r.data.candidates) | Where-Object { "$($_.id)" -eq '9201' }))
} else { Show $r }

Title '4. 状态流转和权限'
$r = Call POST "/safety/emergency/$id/start" @{ planId = $xc01.id } $tLisi; Show $r
Check '作业负责人不能启动应急响应' ($r.code -eq 403)
$r = Call POST "/safety/emergency/$id/log" @{ content = '还没启动就记录' } $tZhang; Show $r
Check '还没启动响应不能记录处置情况' ($r.code -ne 200)
$r = Call POST "/safety/emergency/$id/start" @{ content = '没选预案' } $tZhang; Show $r
Check '启动必须选择预案' ($r.code -ne 200)
$r = Call POST "/safety/emergency/$id/start" @{ planId = $xc01.id; content = '全员撤离到东门集合点' } $tZhang
Check '安全员启动应急响应' ($r.code -eq 200)
$d = Detail $id $tZhang
Check "状态变为响应中，现场指挥：$($d.event.commanderName)" ($d.event.status -eq 'RESPONDING' -and $d.event.commanderName)
Check "详情带出预案处置程序和应急小组（$(@($d.team) -join '、')）" ($d.plan.content -and @($d.team).Count -ge 1)
Check "安全员现在能做：$(@($d.actions) -join ' / ')" ((@($d.actions) -contains 'UPDATE') -and (@($d.actions) -contains 'CLOSE') -and -not (@($d.actions) -contains 'START'))
$m = MsgsOf $tZhao $id
Check "应急小组成员（监护人）收到响应通知：$(@($m)[0].title)" ($m.Count -ge 1)
$r = Call POST "/safety/emergency/$id/start" @{ planId = $xc01.id } $tZhang; Show $r
Check '不能重复启动' ($r.code -ne 200)
$r = Call POST "/safety/emergency/$id/log" @{ content = '救援人员佩戴空气呼吸器进入，伤员已救出送医' } $tZhang
Check '记录处置情况' ($r.code -eq 200)
$r = Call POST "/safety/emergency/$id/close" @{ content = '' } $tZhang; Show $r
Check '结束必须写事后总结' ($r.code -ne 200)
$r = Call POST "/safety/emergency/$id/close" @{ content = '伤员送医后脱离危险。原因：清淤前未检测硫化氢。整改：作业前必须三项气体检测' } $tZhang
Check '结束应急响应' ($r.code -eq 200)
$d = Detail $id $tZhang
Check "状态已结束，处置记录 $(@($d.logs).Count) 条（快报、启动、处置、结束）" ($d.event.status -eq 'CLOSED' -and @($d.logs).Count -eq 4)
$r = Call POST "/safety/emergency/$id/log" @{ content = '结束后补记' } $tZhang; Show $r
Check '已结束的事件不能再记录' ($r.code -ne 200)
$m = MsgsOf $tLisi $id
Check '上报人收到响应结束的通知' ($m.Count -ge 1)

# 误报：不启动响应直接结束
$r = Call POST '/safety/emergency' @{ title = '自测：误报'; accidentType = 'FIRE_EXPLOSION'; level = 'GENERAL'; location = '仓库'; occurTime = (Fmt $now) } $tLisi
$r = Call POST "/safety/emergency/$($r.data)/close" @{ content = '误报：是焊接作业的烟，不是着火' } $tZhang
Check '误报可以不启动响应直接结束' ($r.code -eq 200)

Title '5. 预案管理'
$code = 'TEST-' + (Get-Random -Minimum 1000 -Maximum 9999)
$plan = @{ planCode = $code; title = '自测触电现场处置方案'; planType = 'ONSITE'; accidentType = 'ELECTRIC'; scope = '配电室'
           content = "1. 立即切断电源`n2. 严禁直接用手拉触电者`n3. 心肺复苏，拨打 120"; teamUserIds = @(102, 104) }
$r = Call POST '/safety/emergency/plan' $plan $tLisi; Show $r
Check '作业负责人不能新增预案' ($r.code -eq 403)
$r = Call POST '/safety/emergency/plan' $plan $tZhang
Check "安全员新增预案 $code" ($r.code -eq 200)
$planId = $r.data
$r = Call POST '/safety/emergency/plan' $plan $tZhang; Show $r
Check '预案编号不能重复' ($r.code -ne 200)
$p = (Call GET "/safety/emergency/plan/$planId" $null $tZhang).data
Check "应急小组：$($p.teamNames)；新预案从未演练：$($p.drillState)" ($p.teamNames -like '*张三*' -and $p.drillState -eq 'NEVER')
$bad = $plan.Clone(); $bad.teamUserIds = @(999999)
$r = Call PUT "/safety/emergency/plan/$planId" $bad $tZhang; Show $r
Check '应急小组成员必须是本企业在职员工' ($r.code -ne 200)

Title '6. 演练'
$r = Call POST '/safety/emergency/drill' @{ planId = $planId; title = '自测触电救援桌面推演'; drillType = 'TABLETOP'; planTime = (Fmt $now.AddDays(3)); location = '配电室' } $tZhang
Check '安排演练' ($r.code -eq 200)
$did = $r.data
$m = MsgsOf $tZhao $did
Check '应急小组成员收到演练通知' ($m.Count -ge 1)
$r = Call POST "/safety/emergency/drill/$did/complete" @{ actualTime = (Fmt $now); participantCount = 0; summary = 'x' } $tZhang; Show $r
Check '参演人数至少 1 人' ($r.code -ne 200)
$r = Call POST "/safety/emergency/drill/$did/complete" @{ actualTime = (Fmt $now.AddMinutes(-30)); participantCount = 8; summary = '推演顺利'; problems = '绝缘手套过期 1 副' } $tZhang
Check '记录演练结果' ($r.code -eq 200)
$p = (Call GET "/safety/emergency/plan/$planId" $null $tZhang).data
Check "预案上次演练时间更新为 $($p.lastDrillTime)，状态：$($p.drillState)" ($p.lastDrillTime -and $p.drillState -eq 'OK')
$r = Call POST "/safety/emergency/drill/$did/cancel" $null $tZhang; Show $r
Check '已完成的演练不能取消' ($r.code -ne 200)

$r = Call PUT "/safety/emergency/plan/$planId/status" @{ status = 0 } $tZhang
Check '停用预案' ($r.code -eq 200)
$r = Call POST '/safety/emergency' @{ title = '自测：停用预案'; accidentType = 'ELECTRIC'; level = 'GENERAL'; location = '配电室'; occurTime = (Fmt $now) } $tLisi
$eid2 = $r.data
$r = Call POST "/safety/emergency/$eid2/start" @{ planId = $planId } $tZhang; Show $r
Check '已停用的预案不能启动' ($r.code -ne 200)
Call POST "/safety/emergency/$eid2/close" @{ content = '自测数据' } $tZhang | Out-Null

Title '7. 租户隔离'
$r = Call GET '/safety/emergency/plan/list' $null $tTest
if ($r.code -eq 403) {
    Write-Host '  （跳过：测试冶金管理员没有应急管理菜单）' -ForegroundColor DarkGray
} else {
    $codes = @($r.data | ForEach-Object { "$($_.id)" })
    Check "测试冶金只看到自己的预案（$($codes -join '、')）" (($codes -contains '9201') -and -not ($codes -contains "$($xc01.id)"))
    $r = Call GET "/safety/emergency/$id" $null $tTest; Show $r
    Check '看不到演示企业的事件' ($r.code -ne 200)
    $r = Call POST "/safety/emergency/$id/close" @{ content = 'x' } $tTest; Show $r
    Check '不能处置演示企业的事件' ($r.code -ne 200)
}

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
