# JSA 作业安全分析自测：权限、AI 起草、步骤校验（步数、L/S 范围、重大风险至少 2 条措施）、编写人才能改、提交 / 驳回 / 审核发布（不能审自己的）、作业票引用（类型一致、只能引用已发布）、复制复审后旧版本自动停用、删除、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-jsa.ps1



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
function JsaStep($name, $l, $s, $controls) { return @{ step = $name; hazards = "自测危害：$name"; likelihood = $l; severity = $s; controls = $controls } }
function NewJsa($extra) {
    $b = @{ title = "自测屋顶高处作业 JSA $(Get-Random -Maximum 9999)"; ticketType = 'HEIGHT'; workContent = '自测：屋顶排风机检修'; location = '自测厂房屋顶'; ppe = '安全帽、双钩安全带'
            steps = @((JsaStep '作业前交底' 2 3 '安全交底'), (JsaStep '攀爬屋顶' 3 5 '双钩安全带全程系挂；爬梯底部有人扶梯'), (JsaStep '检修排风机' 3 4 '工具系防坠绳')) }
    foreach ($k in $extra.Keys) { $b[$k] = $extra[$k] }
    return $b
}
function JsaOf($jsaId, $token) { (Call GET "/ticket/jsa/$jsaId" $null $token).data }
function HeightTicket($type, $jsaId) {
    $now = Get-Date
    $t = @{ ticketType = $type; title = '自测引用 JSA 的作业票'; location = '自测厂房屋顶'; workContent = '自测'; safetyMeasures = '自测'
            planStartTime = (Fmt $now.AddHours(1)); planEndTime = (Fmt $now.AddHours(5)); guardianId = 104; jsaId = $jsaId }
    if ($type -eq 'HOT_WORK') { $t.gasOxygen = 20.9; $t.gasLel = 2.5; $t.gasH2s = 0.5; $t.gasCheckTime = (Fmt $now) }
    return $t
}

Title '1. 权限'
$r = Call POST '/ticket/jsa' (NewJsa @{}) $tZhao; Show $r
Check '监护人只能看，不能编写' ($r.code -ne 200)
$r = Call GET '/ticket/jsa/page' $null $tZhao
Check "监护人能看 JSA 列表（$($r.data.total) 份）" ($r.code -eq 200)

Title '2. AI 起草'
$r = Call POST '/ai/jsa/draft' @{ ticketType = 'HEIGHT'; title = ''; workContent = '' } $tLisi; Show $r
Check '没写名称和作业内容不能起草' ($r.code -ne 200)
$r = Call POST '/ai/jsa/draft' @{ ticketType = 'HEIGHT'; title = '屋顶排风机检修'; workContent = '1 号厂房屋顶排风机更换轴承'; location = '1 号厂房屋顶' } $tLisi
$how = if ($r.data.aiUsed) { '大模型' } else { '模板（没配置大模型或调用失败）' }
Check "起草了 $(@($r.data.steps).Count) 个步骤，来源：$how" ($r.code -eq 200 -and @($r.data.steps).Count -ge 2)
$r = Call POST '/ai/jsa/draft' @{ ticketType = 'HEIGHT'; title = 'x'; workContent = 'x' } $tZhao; Show $r
Check '没有编写权限不能用 AI 起草' ($r.code -ne 200)

Title '3. 步骤校验'
$r = Call POST '/ticket/jsa' (NewJsa @{ steps = @((JsaStep 'a' 1 1 'x')) }) $tLisi; Show $r
Check '至少 2 个步骤' ($r.code -ne 200)
$r = Call POST '/ticket/jsa' (NewJsa @{ steps = @((JsaStep 'a' 1 1 'x'), (JsaStep 'b' 6 1 'x')) }) $tLisi; Show $r
Check '可能性要在 1 ~ 5' ($r.code -ne 200)
$r = Call POST '/ticket/jsa' (NewJsa @{ steps = @((JsaStep 'a' 1 1 'x'), (JsaStep 'b' 4 4 '只有一条措施')) }) $tLisi; Show $r
Check '重大风险（16 分）的控制措施至少 2 条' ($r.code -ne 200)
$r = Call POST '/ticket/jsa' (NewJsa @{ ticketType = 'SWIM' }) $tLisi; Show $r
Check '作业类型不正确' ($r.code -ne 200)
$r = Call POST '/ticket/jsa' (NewJsa @{}) $tLisi
Check '李四编写 JSA（草稿）' ($r.code -eq 200)
$jid = $r.data
$j = JsaOf $jid $tLisi
Check "最高风险 $($j.jsa.maxRisk)（$($j.maxLevel)），每步算出风险值：$($j.steps[1].risk)" ($j.jsa.maxRisk -eq 15 -and $j.maxLevel -eq 'MAJOR' -and $j.steps[1].risk -eq 15)

Title '4. 审核'
$r = Call PUT "/ticket/jsa/$jid" (NewJsa @{}) $tZhang; Show $r
Check '只有编写人能修改' ($r.code -ne 200)
$r = Call POST "/ticket/jsa/$jid/approve" @{} $tZhang; Show $r
Check '草稿不能直接审核' ($r.code -ne 200)
Call POST "/ticket/jsa/$jid/submit" @{} $tLisi | Out-Null
$r = Call PUT "/ticket/jsa/$jid" (NewJsa @{}) $tLisi; Show $r
Check '审核中不能修改' ($r.code -ne 200)
$r = Call POST "/ticket/jsa/$jid/approve" @{} $tLisi; Show $r
Check '作业负责人没有审核权限' ($r.code -ne 200)
$r = Call POST "/ticket/jsa/$jid/reject" @{ comment = '' } $tZhang; Show $r
Check '驳回要写原因' ($r.code -ne 200)
$r = Call POST "/ticket/jsa/$jid/reject" @{ comment = '自测：攀爬步骤要写清楚生命绳设置' } $tZhang
Check '张三驳回' ($r.code -eq 200 -and (JsaOf $jid $tLisi).jsa.status -eq 'REJECTED')
$fixed = NewJsa @{ title = (JsaOf $jid $tLisi).jsa.title; steps = @((JsaStep '作业前交底' 2 3 '安全交底'), (JsaStep '攀爬屋顶' 3 5 '设置生命绳；双钩安全带全程系挂'), (JsaStep '检修排风机' 3 4 '工具系防坠绳')) }
$r = Call PUT "/ticket/jsa/$jid" $fixed $tLisi
Check '被驳回的可以修改' ($r.code -eq 200)
Call POST "/ticket/jsa/$jid/submit" @{} $tLisi | Out-Null
$r = Call POST "/ticket/jsa/$jid/approve" @{} $tZhang
$j = JsaOf $jid $tLisi
Check "审核通过发布：$($j.jsa.status)，审核人 $($j.jsa.reviewerName)" ($r.code -eq 200 -and $j.jsa.status -eq 'PUBLISHED')
$own = (Call POST '/ticket/jsa' (NewJsa @{ ticketType = $null }) $tZhang).data
Call POST "/ticket/jsa/$own/submit" @{} $tZhang | Out-Null
$r = Call POST "/ticket/jsa/$own/approve" @{} $tZhang; Show $r
Check '不能审核自己编写的' ($r.code -ne 200)

Title '5. 作业票引用'
$use = @((Call GET '/ticket/jsa/usable?ticketType=HEIGHT' $null $tLisi).data | Where-Object { "$($_.id)" -eq "$jid" })
Check '高处作业票可以选这份 JSA' ($use.Count -eq 1)
$use = @((Call GET '/ticket/jsa/usable?ticketType=CONFINED_SPACE' $null $tLisi).data | Where-Object { "$($_.id)" -eq "$jid" })
Check '受限空间作业票选不到高处作业的 JSA' ($use.Count -eq 0)
$r = Call POST '/ticket' (HeightTicket 'HOT_WORK' $jid) $tLisi; Show $r
Check '动火作业票不能引用高处作业的 JSA' ($r.code -ne 200)
$r = Call POST '/ticket' (HeightTicket 'HEIGHT' $own) $tLisi; Show $r
Check '不能引用审核中的 JSA' ($r.code -ne 200)
$r = Call POST '/ticket' (HeightTicket 'HEIGHT' $jid) $tLisi
$tk = (Call GET "/ticket/$($r.data)" $null $tLisi).data.ticket
Check "作业票引用了 $($tk.jsaNo)" ($r.code -eq 200 -and "$($tk.jsaId)" -eq "$jid")
$j = JsaOf $jid $tZhang
Check "JSA 详情里能看到引用的作业票（$(@($j.tickets).Count) 张）" (@($j.tickets).Count -eq 1)

Title '6. 复制复审'
$r = Call POST "/ticket/jsa/$jid/copy" @{} $tZhang
$copyId = $r.data
$c = JsaOf $copyId $tZhang
Check "复制成草稿 $($c.jsa.jsaNo)，编写人是复制的人" ($c.jsa.status -eq 'DRAFT' -and $c.jsa.authorName -like '*张三*' -and "$($c.jsa.copiedFrom)" -eq "$jid")
Call POST "/ticket/jsa/$copyId/submit" @{} $tZhang | Out-Null
$r = Call POST "/ticket/jsa/$copyId/approve" @{} $tAdmin
Check '企业管理员审核通过新版本' ($r.code -eq 200)
Check '新版本发布后，旧版本自动停用' ((JsaOf $jid $tZhang).jsa.status -eq 'ARCHIVED')
$use = @((Call GET '/ticket/jsa/usable?ticketType=HEIGHT' $null $tLisi).data | Where-Object { "$($_.id)" -eq "$jid" })
Check '停用的不能再引用' ($use.Count -eq 0)

Title '7. 删除、隔离、大屏'
$d = (Call POST '/ticket/jsa' (NewJsa @{}) $tLisi).data
$r = Call DELETE "/ticket/jsa/$d" $null $tZhang; Show $r
Check '不能删别人的草稿' ($r.code -ne 200)
$r = Call DELETE "/ticket/jsa/$d" $null $tLisi
Check '编写人删除自己的草稿' ($r.code -eq 200)
$r = Call DELETE "/ticket/jsa/$copyId" $null $tZhang; Show $r
Check '已发布的不能删除' ($r.code -ne 200)
$r = Call GET "/ticket/jsa/$jid" $null $tTest
Check '别的企业看不到' ($r.code -ne 200)
$scr = (Call GET '/screen/overview' $null $tZhang).data
Check '大屏预警里有 JSA 待审核' (@($scr.warnings | Where-Object { $_.label -eq 'JSA 待审核' }).Count -eq 1)
# 收尾：撤回自测的审核中 JSA 不影响演示数据（驳回成被驳回状态，再删除）
Call POST "/ticket/jsa/$own/reject" @{ comment = '自测收尾' } $tAdmin | Out-Null
Call DELETE "/ticket/jsa/$own" $null $tZhang | Out-Null

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
