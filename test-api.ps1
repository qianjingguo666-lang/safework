# 第一阶段接口自测脚本：登录、获取当前用户、分页查询、租户隔离、未登录拦截
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-api.ps1

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'

# 封装请求：强制按 UTF-8 解析返回内容，避免 Windows PowerShell 5.1 中文乱码
function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
    if ($body) {
        $params.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json))
        $params.ContentType = 'application/json; charset=utf-8'
    }
    $resp = Invoke-WebRequest @params
    $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}

function Check($name, $ok) {
    if ($ok) { Write-Host "[通过] $name" -ForegroundColor Green }
    else     { Write-Host "[失败] $name" -ForegroundColor Red; $script:failed++ }
}
$failed = 0

Write-Host "`n=== 1. 健康检查 ===" -ForegroundColor Cyan
$r = Call GET '/actuator/health'
Write-Host "status = $($r.status)"
Check '服务已启动' ($r.status -eq 'UP')

Write-Host "`n=== 2. 登录【演示化工】demo / admin ===" -ForegroundColor Cyan
$r = Call POST '/auth/login' @{ tenantCode = 'demo'; username = 'admin'; password = 'admin123' }
Write-Host "code = $($r.code), 企业 = $($r.data.tenantName), 昵称 = $($r.data.nickname)"
Check '登录成功并拿到 token' ($r.code -eq 200 -and $r.data.token)
$tokenDemo = $r.data.token

Write-Host "`n=== 3. 获取当前登录用户 /auth/me ===" -ForegroundColor Cyan
$r = Call GET '/auth/me' $null $tokenDemo
Write-Host "用户名 = $($r.data.username), 昵称 = $($r.data.nickname)"
Check '返回当前用户，且不返回密码' ($r.data.username -eq 'admin' -and -not $r.data.password)

Write-Host "`n=== 4. 演示化工的用户列表（应为 3 人） ===" -ForegroundColor Cyan
$r = Call GET '/system/user/page?current=1&size=10' $null $tokenDemo
$r.data.records | ForEach-Object { Write-Host "  - $($_.username)  $($_.nickname)" }
Check "只看到本企业用户（total = $($r.data.total)）" ($r.data.total -eq 3)

Write-Host "`n=== 5. 登录【测试冶金】test / admin，查看用户列表（应为 2 人） ===" -ForegroundColor Cyan
$r = Call POST '/auth/login' @{ tenantCode = 'test'; username = 'admin'; password = 'admin123' }
$tokenTest = $r.data.token
$r = Call GET '/system/user/page' $null $tokenTest
$r.data.records | ForEach-Object { Write-Host "  - $($_.username)  $($_.nickname)" }
Check "租户隔离生效（total = $($r.data.total)）" ($r.data.total -eq 2)

Write-Host "`n=== 6. 在演示化工新增用户 zhaoliu ===" -ForegroundColor Cyan
$r = Call POST '/system/user' @{ username = 'zhaoliu'; password = 'admin123'; nickname = 'zhaoliu' } $tokenDemo
if ($r.code -ne 200) { Write-Host "  提示：$($r.msg)（重复运行脚本时出现属于正常）" }
$r = Call GET '/system/user/page' $null $tokenDemo
$r2 = Call GET '/system/user/page' $null $tokenTest
Check "新用户只出现在演示化工（demo = $($r.data.total), test = $($r2.data.total)）" ($r.data.total -ge 4 -and $r2.data.total -eq 2)

Write-Host "`n=== 7. 不带 token 访问（应被拦截） ===" -ForegroundColor Cyan
$r = Call GET '/system/user/page'
Write-Host "code = $($r.code), msg = $($r.msg)"
Check '未登录返回 401' ($r.code -eq 401)

Write-Host "`n=== 8. 错误密码 ===" -ForegroundColor Cyan
$r = Call POST '/auth/login' @{ tenantCode = 'demo'; username = 'admin'; password = 'wrong' }
Write-Host "code = $($r.code), msg = $($r.msg)"
Check '密码错误被拒绝' ($r.code -eq 400)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过，第一阶段完成！' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Yellow }
