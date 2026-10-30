# 接口自测脚本（第一阶段 + RBAC 权限）
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-api.ps1
# 可以重复运行：每次新建的用户名都带时间戳，不依赖固定人数

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'

# 封装请求：强制按 UTF-8 解析返回内容，避免 Windows PowerShell 5.1 中文乱码
function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
    if ($body) {
        $params.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 5))
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
$failed = 0

Title '1. 健康检查'
$r = Call GET '/actuator/health'
Check "服务已启动（status = $($r.status)）" ($r.status -eq 'UP')

Title '2. 演示化工 admin 登录，查看自己的权限'
$tDemo = Login 'demo' 'admin'
$r = Call GET '/auth/me' $null $tDemo
Write-Host "角色 = $($r.data.roles -join ', ')   权限 = $($r.data.permissions -join ', ')"
Write-Host "菜单 = $((@($r.data.menus) | ForEach-Object { $_.menuName }) -join ', ')"
Check '企业管理员拥有套餐内全部权限（旗舰版含 AI）' ($r.data.permissions -contains 'system:user:add' -and $r.data.permissions -contains 'ai:chat')
Check '能看到 5 个顶级菜单（看板、作业、安全、AI、系统）' (@($r.data.menus).Count -eq 5)
Check '不返回密码' (-not $r.data.user.password)

Title '3. 演示化工 zhangsan（安全员）登录'
$tZhang = Login 'demo' 'zhangsan'
$r = Call GET '/auth/me' $null $tZhang
Write-Host "角色 = $($r.data.roles -join ', ')   权限 = $($r.data.permissions -join ', ')"
Write-Host "菜单 = $((@($r.data.menus) | ForEach-Object { $_.menuName }) -join ', ')"
Check '安全员有审批权限 ticket:approve' ($r.data.permissions -contains 'ticket:approve')
Check '安全员没有申请权限 ticket:add' (-not ($r.data.permissions -contains 'ticket:add'))
Check '看不到【系统管理】菜单' (-not (@($r.data.menus) | Where-Object { $_.menuName -eq '系统管理' }))

Title '4. 安全员访问用户管理接口（应返回 403）'
$r = Call GET '/system/user/page' $null $tZhang
Write-Host "code = $($r.code), msg = $($r.msg)"
Check '无权限被拦截' ($r.code -eq 403)

Title '5. 演示化工的角色列表'
$r = Call GET '/system/role/list' $null $tDemo
$r.data | ForEach-Object { Write-Host "  - [$($_.id)] $($_.roleCode)  $($_.roleName)" }
Check '有 4 个预置角色' (@($r.data).Count -ge 4)

Title '6. 新建用户并分配【作业负责人】角色，新用户登录后应有申请权限'
$newName = 'u' + (Get-Date -Format 'MMddHHmmss')
$r = Call POST '/system/user' @{ username = $newName; password = 'admin123'; nickname = "测试用户$newName" } $tDemo
Check "新建用户 $newName" ($r.code -eq 200)
$newId = $r.data
$r = Call PUT "/system/user/$newId/roles" @{ ids = @(13) } $tDemo
Check '分配角色成功' ($r.code -eq 200)
$tNew = Login 'demo' $newName
$r = Call GET '/auth/me' $null $tNew
Write-Host "角色 = $($r.data.roles -join ', ')   权限 = $($r.data.permissions -join ', ')"
Check '新用户拥有 ticket:add' ($r.data.permissions -contains 'ticket:add')

Title '7. 租户隔离：测试冶金搜不到演示化工的新用户'
$rDemo = Call GET "/system/user/page?keyword=$newName" $null $tDemo
$tTest = Login 'test' 'admin'
$rTest = Call GET "/system/user/page?keyword=$newName" $null $tTest
Check "演示化工能搜到（$($rDemo.data.total)），测试冶金搜不到（$($rTest.data.total)）" ($rDemo.data.total -eq 1 -and $rTest.data.total -eq 0)

Title '8. 越权测试：测试冶金管理员尝试使用演示化工的角色和用户'
$r = Call PUT '/system/user/202/roles' @{ ids = @(12) } $tTest
Write-Host "把演示化工的角色(12)分配给自己的用户：code = $($r.code), msg = $($r.msg)"
Check '不能使用别的企业的角色' ($r.code -eq 400)
$r = Call PUT '/system/user/103/roles' @{ ids = @(21) } $tTest
Write-Host "修改演示化工的用户(103)：code = $($r.code), msg = $($r.msg)"
Check '不能操作别的企业的用户' ($r.code -eq 404)

Title '9. 不能给企业管理员角色分配菜单'
$r = Call PUT '/system/role/11/menus' @{ ids = @(2) } $tDemo
Write-Host "code = $($r.code), msg = $($r.msg)"
Check '被拒绝' ($r.code -eq 400)

Title '10. 未登录 / 错误密码'
$r = Call GET '/system/user/page'
Check "未登录返回 401（$($r.msg)）" ($r.code -eq 401)
$r = LoginRaw 'demo' 'admin' 'wrong'
Check "错误密码被拒绝（$($r.msg)）" ($r.code -eq 400)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过，RBAC 权限完成！' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Yellow }

