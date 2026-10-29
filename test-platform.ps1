# SaaS 平台端 + 登录安全自测：验证码、平台登录、开通企业、套餐限制、账号上限、登录锁定、在线用户、到期、停用
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-platform.ps1
# 可以重复运行：每次开通一个新企业（编码带时间戳）。后端必须是开发环境（dev）启动，否则拿不到验证码答案

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
# 登录要先取验证码：开发环境（dev）后端会把答案放在 debugAnswer 里返回，脚本直接用它
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
function MenuNames($me) { @($me.menus | ForEach-Object { $_.menuName }) }
$failed = 0
$ts = Get-Date -Format 'MMddHHmmss'

Title '1. 图形验证码'
$c = (Call GET '/auth/captcha').data
Check "返回验证码图片（SVG，$($c.image.Length) 字符）" ($c.enabled -and $c.image.StartsWith('data:image/svg+xml;base64,'))
$r = Call POST '/auth/login' @{ tenantCode = 'demo'; username = 'admin'; password = 'admin123' }; Show $r
Check '不带验证码不能登录' ($r.code -eq 400 -and $r.msg.Contains('验证码'))
$c = (Call GET '/auth/captcha').data
$r = Call POST '/auth/login' @{ tenantCode = 'demo'; username = 'admin'; password = 'admin123'; captchaKey = $c.captchaKey; captchaCode = '999' }; Show $r
Check '验证码错误被拒绝' ($r.code -eq 400 -and $r.msg -eq '验证码错误')
$r = Call POST '/auth/login' @{ tenantCode = 'demo'; username = 'admin'; password = 'admin123'; captchaKey = $c.captchaKey; captchaCode = $c.debugAnswer }; Show $r
Check '验证码只能用一次（上一次输错后就作废了）' ($r.code -eq 400 -and $r.msg.Contains('过期'))

Title '2. 平台管理员登录（企业编码 platform）'
$tPlat = Login 'platform' 'admin'
$me = (Call GET '/auth/me' $null $tPlat).data
Write-Host "  菜单 = $((MenuNames $me) -join ', ')   权限 = $($me.permissions -join ', ')" -ForegroundColor DarkGray
Check '识别为运营平台' ($me.tenant.platform -eq $true)
Check '只有【平台管理】菜单，看不到企业业务菜单' (((MenuNames $me) -join ',') -eq '平台管理')
Check '有开通企业权限，没有作业票权限' ($me.permissions -contains 'platform:tenant:add' -and -not ($me.permissions -contains 'ticket:list'))
$r = Call GET '/ticket/page' $null $tPlat
Check '平台管理员不能调用企业业务接口（403）' ($r.code -eq 403)

$tDemo = Login 'demo' 'admin'
$r = Call GET '/platform/tenant/page' $null $tDemo; Show $r
Check '企业管理员不能访问平台接口（403）' ($r.code -eq 403)

Title '3. 套餐'
$pkgs = (Call GET '/platform/package/list' $null $tPlat).data
$pkgs | ForEach-Object { Write-Host "  - [$($_.id)] $($_.packageName)：$($_.pageCount) 个页面，$($_.tenantCount) 家企业在用" -ForegroundColor DarkGray }
$basic = $pkgs | Where-Object { $_.packageName -eq '基础版' } | Select-Object -First 1
$ultimate = $pkgs | Where-Object { $_.packageName -eq '旗舰版' } | Select-Object -First 1
Check '有旗舰版、基础版' ($basic -and $ultimate)
$r = Call POST '/platform/package' @{ packageName = "坏套餐$ts"; menuIds = @(5, 500) } $tPlat; Show $r
Check '平台菜单不能放进套餐' ($r.code -eq 400)
$r = Call POST '/platform/package' @{ packageName = "坏套餐$ts"; menuIds = @(2001) } $tPlat; Show $r
Check '只勾按钮不勾上级页面被拒绝' ($r.code -eq 400)
$r = Call PUT "/platform/package/$($ultimate.id)/status" @{ status = 0 } $tPlat; Show $r
Check '有企业在用的套餐不能停用' ($r.code -eq 400)

Title '4. 开通企业（基础版：只有作业票，账号上限 2 个）'
$code = "auto$ts"
$tenant = @{ tenantCode = $code; tenantName = "自动测试企业$ts"; contactName = '测试'; packageId = $basic.id
             expireTime = (Fmt (Get-Date).AddYears(1)); accountLimit = 2; adminUsername = 'boss'; adminPassword = 'boss123' }
$bad = $tenant.Clone(); $bad.tenantCode = 'platform'
$r = Call POST '/platform/tenant' $bad $tPlat; Show $r
Check '保留编码 platform 不能开通' ($r.code -eq 400)
$r = Call POST '/platform/tenant' $tenant $tPlat; Show $r
Check "开通企业 $code" ($r.code -eq 200)
$tid = $r.data
$r = Call POST '/platform/tenant' $tenant $tPlat
Check '企业编码不能重复' ($r.code -eq 400)

$tBoss = Login $code 'boss' 'boss123'
$me = (Call GET '/auth/me' $null $tBoss).data
Write-Host "  菜单 = $((MenuNames $me) -join ', ')   套餐 = $($me.tenant.packageName)" -ForegroundColor DarkGray
Check '新企业管理员能登录，套餐是基础版' ($me.tenant.packageName -eq '基础版')
$names = ((MenuNames $me) | Sort-Object) -join ','
Check '基础版只有【作业管理】【系统管理】' ($names -eq ((@('作业管理', '系统管理') | Sort-Object) -join ','))
Check '没有受限空间、AI 权限' (-not ($me.permissions -contains 'safety:space:list') -and -not ($me.permissions -contains 'ai:chat'))
$r = Call GET '/safety/space/page' $null $tBoss
Check '直接调用套餐外的接口也被拒绝（403）' ($r.code -eq 403)
$roles = (Call GET '/system/role/list' $null $tBoss).data
Write-Host "  预置角色 = $(($roles | ForEach-Object { $_.roleName }) -join ', ')" -ForegroundColor DarkGray
Check '自动创建了 4 个角色' (@($roles).Count -eq 4)
$safetyRole = $roles | Where-Object { $_.roleCode -eq 'safety' }
$menus = (Call GET "/system/role/$($safetyRole.id)/menus" $null $tBoss).data
Check "安全员角色只分配了套餐内的菜单（$($menus -join ',')）" (-not ($menus -contains '300') -and ($menus -contains '2002'))
$r = Call PUT "/system/role/$($safetyRole.id)/menus" @{ ids = @(3, 300) } $tBoss; Show $r
Check '不能给角色分配套餐外的功能' ($r.code -eq 400)
$depts = (Call GET '/system/dept/tree' $null $tBoss).data
Check '自动创建了根部门' (@($depts).Count -eq 1 -and $depts[0].deptName -eq "自动测试企业$ts")

Title '5. 账号上限'
$r = Call POST '/system/user' @{ username = 'worker'; password = 'work1234'; nickname = '工人甲'; roleIds = @($safetyRole.id) } $tBoss
Check '第 2 个账号可以创建' ($r.code -eq 200)
$workerId = $r.data
$r = Call POST '/system/user' @{ username = 'worker2'; password = 'work1234' } $tBoss; Show $r
Check '第 3 个账号超出上限' ($r.code -eq 400 -and $r.msg.Contains('上限'))

Title '6. 升级套餐：基础版 → 旗舰版，立即生效'
$r = Call PUT "/platform/tenant/$tid" @{ tenantName = "自动测试企业$ts"; contactName = '测试'; packageId = $ultimate.id; expireTime = (Fmt (Get-Date).AddYears(1)); accountLimit = 5 } $tPlat
Check '平台修改套餐' ($r.code -eq 200)
$me = (Call GET '/auth/me' $null $tBoss).data
Write-Host "  菜单 = $((MenuNames $me) -join ', ')" -ForegroundColor DarkGray
Check '不用重新登录，菜单多了安全管理和 AI 助手' ((MenuNames $me) -contains 'AI 助手' -and (MenuNames $me) -contains '安全管理')

Title '7. 连续输错密码锁定账号'
for ($i = 1; $i -le 4; $i++) { $r = LoginRaw $code 'worker' 'wrong'; Write-Host "  第 $i 次：$($r.msg)" -ForegroundColor DarkGray }
Check '提示剩余次数' ($r.msg.Contains('还可以尝试 1 次'))
$r = LoginRaw $code 'worker' 'wrong'; Show $r
Check '第 5 次输错后锁定' ($r.msg.Contains('锁定'))
$r = LoginRaw $code 'WORKER' 'work1234'; Show $r
Check '锁定期间密码正确也不能登录（用户名大小写不同也一样）' ($r.code -eq 400 -and $r.msg.Contains('锁定'))
$u = (Call GET '/system/user/page?keyword=worker' $null $tBoss).data.records | Where-Object { $_.username -eq 'worker' }
Check '用户列表显示已锁定' ($u.locked -eq $true)
$r = Call PUT "/system/user/$workerId/unlock" @{} $tBoss
Check '管理员解锁' ($r.code -eq 200)
$tWorker = Login $code 'worker' 'work1234'
Check '解锁后可以登录' ([bool]$tWorker)

Title '8. 在线用户、强制下线'
$tWorker2 = Login $code 'worker' 'work1234'
$online = (Call GET '/system/online/list' $null $tBoss).data
$online | ForEach-Object { Write-Host "  - $($_.nickname)  $($_.ip)  $($_.browser) / $($_.os)  $($_.loginTime)$(if ($_.current) { '  ← 当前会话' })" -ForegroundColor DarkGray }
Check '能看到 3 个会话（管理员 1 + 工人甲 2）' (@($online).Count -eq 3)
Check '接口不返回 token' (-not ($online | Where-Object { $_.token }))
$me1 = $online | Where-Object { $_.current }
$r = Call DELETE "/system/online/$($me1.sessionId)" $null $tBoss; Show $r
Check '不能强制下线自己' ($r.code -eq 400)
$victim = $online | Where-Object { $_.username -eq 'worker' } | Select-Object -First 1
$r = Call DELETE "/system/online/$($victim.sessionId)" $null $tBoss
Check '强制下线工人甲的一个会话' ($r.code -eq 200)
$r1 = Call GET '/auth/me' $null $tWorker
$r2 = Call GET '/auth/me' $null $tWorker2
$kicked = @($r1, $r2) | Where-Object { $_.code -eq 401 }
Write-Host "  被踢的会话收到：$($kicked.msg)" -ForegroundColor DarkGray
Check '被踢的会话 401“已被管理员强制下线”，另一个会话不受影响' (@($kicked).Count -eq 1 -and $kicked.msg.Contains('强制下线'))
$r = Call GET '/system/online/list' $null $tDemo
Check '演示化工看不到这个企业的在线用户' (-not ($r.data | Where-Object { $_.username -eq 'worker' }))

Title '9. 登录日志'
$logs = (Call GET '/system/loginlog/page?size=50' $null $tBoss).data
Write-Host "  本企业登录日志 $($logs.total) 条，失败 $(@($logs.records | Where-Object { $_.status -eq 0 }).Count) 条" -ForegroundColor DarkGray
Check '记录了失败和成功的登录' (@($logs.records | Where-Object { $_.status -eq 0 }).Count -ge 6 -and @($logs.records | Where-Object { $_.status -eq 1 }).Count -ge 3)
Check '企业只能看到自己的登录日志' (-not ($logs.records | Where-Object { $_.tenantCode -ne $code }))
Check '解析出浏览器（PowerShell）' (($logs.records[0].browser) -like 'PowerShell*')
$plat = (Call GET "/platform/loginlog/page?tenantCode=$code" $null $tPlat).data
Check "平台能查到这个企业的登录日志（$($plat.total) 条）" ($plat.total -eq $logs.total)

Title '10. 服务到期：已登录的用户立即失效，不能再登录'
$r = Call PUT "/platform/tenant/$tid" @{ tenantName = "自动测试企业$ts"; packageId = $ultimate.id; expireTime = (Fmt (Get-Date).AddDays(-1)); accountLimit = 5 } $tPlat
$r = Call GET '/auth/me' $null $tBoss; Show $r
Check '到期后原来的登录 401' ($r.code -eq 401 -and $r.msg.Contains('到期'))
$r = LoginRaw $code 'boss' 'boss123'; Show $r
Check '到期后不能登录' ($r.code -eq 400 -and $r.msg.Contains('到期'))
$r = Call PUT "/platform/tenant/$tid" @{ tenantName = "自动测试企业$ts"; packageId = $ultimate.id; expireTime = $null; accountLimit = 5 } $tPlat
$tBoss = Login $code 'boss' 'boss123'
Check '续费（改成不限期）后恢复' ([bool]$tBoss)
$t = (Call GET "/platform/tenant/$tid" $null $tPlat).data
Check '到期时间确实改成了不限（NULL）' ($null -eq $t.expireTime)

Title '11. 停用企业：所有人立即下线'
$r = Call PUT "/platform/tenant/$tid/status" @{ status = 0 } $tPlat
Check '停用企业' ($r.code -eq 200)
$r = Call GET '/auth/me' $null $tBoss; Show $r
Check '已登录的管理员被踢下线' ($r.code -eq 401)
$r = LoginRaw $code 'boss' 'boss123'; Show $r
Check '停用后不能登录' ($r.code -eq 400 -and $r.msg.Contains('停用'))
Call PUT "/platform/tenant/$tid/status" @{ status = 1 } $tPlat | Out-Null
$r = Call PUT '/platform/tenant/0/status' @{ status = 0 } $tPlat; Show $r
Check '不能停用运营平台自己' ($r.code -eq 400)

Title '12. 平台重置企业管理员密码'
$r = Call PUT "/platform/tenant/$tid/admin-password" @{ password = 'newboss1' } $tPlat
Check "重置成功（管理员 $($r.data)）" ($r.code -eq 200 -and $r.data -eq 'boss')
Check '用新密码登录' ((LoginRaw $code 'boss' 'newboss1').code -eq 200)

Title '13. 平台首页统计'
$s = (Call GET '/platform/tenant/stats' $null $tPlat).data
Write-Host "  企业 $($s.tenantTotal) 家，账号 $($s.userTotal) 个，在线 $($s.onlineTotal)，今日登录成功 $($s.loginSuccessToday) / 失败 $($s.loginFailToday)" -ForegroundColor DarkGray
Check '统计数据正常' ($s.tenantTotal -ge 3 -and $s.loginFailToday -ge 6)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过，SaaS 平台端 + 登录安全完成！' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Yellow }
