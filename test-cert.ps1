# 人员资质自测：证书台账（数据权限、校验、长期有效）、开票持证要求、作业票提交时的持证拦截、到期提醒、智能体工具、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-cert.ps1

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
function Day($d) { $d.ToString('yyyy-MM-dd') }
function MsgsOf($token, $bizId) { ,@((Call GET '/message/page?size=100' $null $token).data.records | Where-Object { "$($_.bizId)" -eq "$bizId" }) }
function NewTicket($type, $title, $hours = 4) {
    $now = Get-Date
    return @{ ticketType = $type; title = $title; location = '厂房屋顶'; workContent = '更换彩钢瓦'; safetyMeasures = '系挂安全带，设置警戒区'
              planStartTime = (Fmt $now); planEndTime = (Fmt $now.AddHours($hours)); guardianId = 104 }
}
$failed = 0
$today = Get-Date
$suffix = Get-Random -Minimum 10000 -Maximum 99999

$tZhang = Login 'demo' 'zhangsan'   # 安全员：管理证书和持证要求
$tLisi  = Login 'demo' 'lisi'       # 作业负责人：只看自己的证书，申请作业票
$tZhao  = Login 'demo' 'zhaoliu'    # 监护人
$tTest  = Login 'test' 'admin'

Title '1. 演示数据和数据权限'
$ov = (Call GET '/safety/cert/overview' $null $tZhang).data
Check "安全员看到全部：$($ov.total) 张证书、$($ov.holders) 人，30 天内到期 $($ov.expiring)、已过期 $($ov.expired)" ($ov.total -ge 8 -and $ov.expiring -ge 1 -and $ov.expired -ge 1)
$exp = @((Call GET '/safety/cert/page?state=EXPIRED&size=50' $null $tZhang).data.records)
Check "按状态筛选已过期：$((@($exp) | ForEach-Object { $_.userName + ' ' + $_.certType }) -join '、')" (@($exp | Where-Object { $_.certType -eq 'ELECTRICIAN' }).Count -ge 1 -and -not ($exp | Where-Object { $_.state -ne 'EXPIRED' }))
$mine = @((Call GET '/safety/cert/page?size=50' $null $tLisi).data.records)
Check "作业负责人只看到自己的 $($mine.Count) 张证书" ($mine.Count -ge 3 -and -not ($mine | Where-Object { "$($_.userId)" -ne '103' }))
$zhaoCert = @((Call GET '/safety/cert/page?size=50' $null $tZhang).data.records | Where-Object { "$($_.userId)" -eq '104' })[0]
$r = Call GET "/safety/cert/$($zhaoCert.id)" $null $tLisi; Show $r
Check '看不到别人的证书详情' ($r.code -eq 404)
$r = Call POST '/safety/cert' @{ userId = 103; certType = 'OTHER'; certNo = "X$suffix"; issueDate = (Day $today) } $tLisi; Show $r
Check '作业负责人不能登记证书' ($r.code -eq 403)

Title '2. 登记证书'
$cert = @{ userId = 104; certType = 'HAZCHEM'; certNo = "T$suffix"; issuer = '安徽省应急管理厅'
           issueDate = (Day $today.AddYears(-1)); expireDate = (Day $today.AddYears(5)); reviewDate = (Day $today.AddYears(2)) }
$r = Call POST '/safety/cert' $cert $tZhang
Check '安全员登记证书' ($r.code -eq 200)
$cid = $r.data
$r = Call POST '/safety/cert' $cert $tZhang; Show $r
Check '同类证书编号不能重复' ($r.code -ne 200)
$bad = $cert.Clone(); $bad.certNo = "B$suffix"; $bad.expireDate = (Day $today.AddYears(-2))
$r = Call POST '/safety/cert' $bad $tZhang; Show $r
Check '有效期必须晚于发证日期' ($r.code -ne 200)
$bad = $cert.Clone(); $bad.certNo = "B$suffix"; $bad.issueDate = (Day $today.AddDays(3))
$r = Call POST '/safety/cert' $bad $tZhang; Show $r
Check '发证日期不能晚于今天' ($r.code -ne 200)
$bad = $cert.Clone(); $bad.certNo = "B$suffix"; $bad.certType = 'DRIVER'
$r = Call POST '/safety/cert' $bad $tZhang; Show $r
Check '证书类型必须在字典里' ($r.code -ne 200)
$bad = $cert.Clone(); $bad.certNo = "B$suffix"; $bad.userId = 202
$r = Call POST '/safety/cert' $bad $tZhang; Show $r
Check '不能给别的企业的人登记证书' ($r.code -ne 200)
$long = $cert.Clone(); $long.expireDate = $null; $long.reviewDate = $null
$r = Call PUT "/safety/cert/$cid" $long $tZhang
$c = (Call GET "/safety/cert/$cid" $null $tZhang).data
Check "改成长期有效（有效期、复审日期清空）：状态 $($c.state)" ($r.code -eq 200 -and -not $c.expireDate -and -not $c.validUntil -and $c.state -eq 'VALID')
$soon = $cert.Clone(); $soon.expireDate = (Day $today.AddDays(7)); $soon.reviewDate = $null
Call PUT "/safety/cert/$cid" $soon $tZhang | Out-Null
$c = (Call GET "/safety/cert/$cid" $null $tZhang).data
Check "改成 7 天后到期：$($c.state)，剩 $($c.daysLeft) 天" ($c.state -eq 'EXPIRING' -and $c.daysLeft -eq 7)
$c = (Call GET "/safety/cert/$cid" $null $tZhao).data
Check '持证人本人能看自己的证书' ($c.certNo -eq "T$suffix")

Title '3. 到期提醒'
try {
    $tPlat = Login 'platform' 'admin'
    $r = Call POST '/platform/job/cert-expiry/run' @{} $tPlat
    Check "执行证书到期提醒：$($r.data)" ($r.code -eq 200)
    $m = MsgsOf $tZhao $cid
    Check "持证人收到提醒：$(@($m)[0].title)" ($m.Count -ge 1)
    $m2 = MsgsOf $tZhang $cid
    Check '安全员也收到' ($m2.Count -ge 1)
    Call POST '/platform/job/cert-expiry/run' @{} $tPlat | Out-Null
    Check '同一天再执行不会重复提醒' ((MsgsOf $tZhao $cid).Count -eq $m.Count)
} catch {
    Write-Host "  （跳过：平台管理员登录失败，可能已经改过密码：$($_.Exception.Message)）" -ForegroundColor DarkGray
}

Title '4. 开票持证要求'
$orig = @((Call GET '/safety/cert/requirement' $null $tZhang).data | ForEach-Object { @{ ticketType = $_.ticketType; holderRole = $_.holderRole; certType = $_.certType } })
Check "演示化工已配置 $($orig.Count) 条要求" ($orig.Count -ge 6)
$r = Call PUT '/safety/cert/requirement' @{ items = $orig } $tLisi; Show $r
Check '作业负责人不能修改持证要求' ($r.code -eq 403)
$r = Call PUT '/safety/cert/requirement' @{ items = @(@{ ticketType = 'DIVING'; holderRole = 'APPLICANT'; certType = 'HEIGHT' }) } $tZhang; Show $r
Check '要求的作业类型必须正确' ($r.code -ne 200)

Title '5. 作业票提交时检查持证'
$t = NewTicket 'HEIGHT' '持证校验：要求合格'
$r = (Call GET "/safety/cert/ticket-check?ticketType=HEIGHT&guardianId=104&planEndTime=$([uri]::EscapeDataString($t.planEndTime))" $null $tLisi).data
Check '按演示要求，李四（高处作业证）和赵六（监护人证）都合格' (@($r).Count -eq 0)
$id = (Call POST '/ticket' $t $tLisi).data
$r = Call POST "/ticket/$id/submit" $null $tLisi
Check '持证合格可以提交审批' ($r.code -eq 200)
Call POST "/ticket/$id/cancel" @{ comment = '测试清理' } $tLisi | Out-Null

try {
    # 高处作业：作业负责人还要有危化品作业证（李四没有），监护人还要有电工证（赵六的复审逾期）
    $strict = @($orig) + @(@{ ticketType = 'HEIGHT'; holderRole = 'APPLICANT'; certType = 'HAZCHEM' }, @{ ticketType = 'HEIGHT'; holderRole = 'GUARDIAN'; certType = 'ELECTRICIAN' })
    $r = Call PUT '/safety/cert/requirement' @{ items = $strict } $tZhang
    Check '安全员修改持证要求' ($r.code -eq 200)
    $chk = @((Call GET "/safety/cert/ticket-check?ticketType=HEIGHT&guardianId=104" $null $tLisi).data)
    Check "新建作业票时预检出 $($chk.Count) 人不合格：$(($chk | ForEach-Object { $_.roleName + '：' + ($_.problems -join '、') }) -join '；')" ($chk.Count -eq 2)
    $id = (Call POST '/ticket' (NewTicket 'HEIGHT' '持证校验：缺证') $tLisi).data
    Check '草稿可以保存（保存时不检查，提交审批时才检查）' ($null -ne $id)
    $r = Call POST "/ticket/$id/submit" $null $tLisi; Show $r
    Check '缺证不能提交审批' ($r.code -ne 200 -and $r.msg -like '*没有*危险化学品*' -and $r.msg -like '*复审已逾期*')

    # 给李四登记一张 2 天后到期的危化品证，给赵六登记有效的电工证：作业 3 天，证书撑不到作业结束
    $hz = Call POST '/safety/cert' @{ userId = 103; certType = 'HAZCHEM'; certNo = "H$suffix"; issueDate = (Day $today.AddYears(-1)); expireDate = (Day $today.AddDays(2)) } $tZhang
    $el = Call POST '/safety/cert' @{ userId = 104; certType = 'ELECTRICIAN'; certNo = "E$suffix"; issueDate = (Day $today.AddYears(-1)); expireDate = (Day $today.AddYears(5)); reviewDate = (Day $today.AddYears(2)) } $tZhang
    $id2 = (Call POST '/ticket' (NewTicket 'HEIGHT' '持证校验：作业期间到期' 72) $tLisi).data
    $r = Call POST "/ticket/$id2/submit" $null $tLisi; Show $r
    Check '证书在作业结束前到期也不能提交（换了新证的旧证不影响，取最晚到期的那张）' ($r.code -ne 200 -and $r.msg -like '*早于作业结束时间*')
    $r = Call POST "/ticket/$id/submit" $null $tLisi
    Check '4 小时的作业，证书够用，可以提交' ($r.code -eq 200)
    Call POST "/ticket/$id/cancel" @{ comment = '测试清理' } $tLisi | Out-Null
    Call POST "/ticket/$id2/cancel" @{ comment = '测试清理' } $tLisi | Out-Null
    Call DELETE "/safety/cert/$($hz.data)" $null $tZhang | Out-Null
    Call DELETE "/safety/cert/$($el.data)" $null $tZhang | Out-Null
} finally {
    $r = Call PUT '/safety/cert/requirement' @{ items = $orig } $tZhang
    Check '恢复原来的持证要求' ($r.code -eq 200)
}

Title '6. 智能体工具、租户隔离、清理'
$names = @((Call GET '/ai/agent/tools' $null $tZhang).data | ForEach-Object { $_.name })
Check '智能体有【查询人员资质】工具 search_certs' ($names -contains 'search_certs')
$r = Call GET '/safety/cert/page?size=50' $null $tTest
if ($r.code -eq 403) {
    Write-Host '  （跳过：测试冶金管理员没有人员资质菜单）' -ForegroundColor DarkGray
} else {
    Check "测试冶金只看到自己的证书（$(@($r.data.records).Count) 张）" (-not (@($r.data.records) | Where-Object { "$($_.userId)" -notin @('201', '202') }))
    $r = Call GET "/safety/cert/$cid" $null $tTest; Show $r
    Check '看不到演示企业的证书' ($r.code -ne 200)
    $reqs = @((Call GET '/safety/cert/requirement' $null $tTest).data)
    Check "测试冶金没有配置持证要求（$($reqs.Count) 条），开票不检查" ($reqs.Count -eq 0)
}
$r = Call DELETE "/safety/cert/$cid" $null $tZhang
Check '删除测试证书' ($r.code -eq 200)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
