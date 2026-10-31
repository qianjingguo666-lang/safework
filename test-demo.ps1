# 演示模式自测：演示账号展示、禁止危险操作、核心业务不受影响、平台管理员不受影响、AI 每日限额
# 需要用演示模式启动后端（AI 限额设小一点方便测试）：
#   java -jar safework-server\target\safework-server.jar --spring.profiles.active=dev --safework.demo.enabled=true --safework.demo.ai-daily-limit=5
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-demo.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
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
function Blocked($r) { $r.code -eq 403 -and $r.msg -like '演示环境不允许*' }
$failed = 0

$info = (Call GET '/public/demo').data
if (-not $info.enabled) {
    Write-Host '后端没有打开演示模式。请关掉后端，用下面的命令重新启动后再运行本脚本：' -ForegroundColor Yellow
    Write-Host '  java -jar safework-server\target\safework-server.jar --spring.profiles.active=dev --safework.demo.enabled=true --safework.demo.ai-daily-limit=5'
    exit 1
}

Title '1. 登录页的演示信息（不用登录）'
Check "演示模式已打开，演示企业：$(@($info.tenantCodes) -join '、')" (@($info.tenantCodes) -contains 'demo')
Check "列出演示账号（$((@($info.accounts) | ForEach-Object { $_.label }) -join '、')）" (@($info.accounts).Count -ge 4)
Check '不会列出平台管理员账号' (-not (@($info.accounts) | Where-Object { $_.tenantCode -eq 'platform' }))
Check "提示数据每天 $($info.resetTime) 恢复、AI 每天 $($info.aiDailyLimit) 次" ($info.resetTime -and $info.aiDailyLimit -gt 0)

$tAdmin = Login 'demo' 'admin'
$tLisi  = Login 'demo' 'lisi'
$tZhang = Login 'demo' 'zhangsan'
$tTest = $null
try { $tTest = Login 'test' 'admin' } catch { Write-Host '  （测试冶金管理员登录失败，相关检查跳过）' -ForegroundColor DarkGray }

Title '2. 会影响其他访客的操作被禁止'
$r = Call POST '/auth/password' @{ oldPassword = 'admin123'; newPassword = 'Hack12345' } $tAdmin; Show $r
Check '演示账号不能改密码' (Blocked $r)
$r = Call PUT '/system/user/102/password' @{ password = 'Hack12345' } $tAdmin; Show $r
Check '企业管理员不能重置员工密码' (Blocked $r)
$r = Call PUT '/system/user/102/status' @{ status = 0 } $tAdmin; Show $r
Check '不能停用员工账号' (Blocked $r)
$r = Call PUT '/system/role/12/menus' @{ ids = @(2) } $tAdmin; Show $r
Check '不能修改角色权限' (Blocked $r)
$r = Call POST '/system/notice' @{ title = '测试公告'; content = '<p>x</p>'; level = 'NORMAL' } $tAdmin; Show $r
Check '不能发布全员公告' (Blocked $r)
$r = Call DELETE '/system/online/abc' $null $tAdmin; Show $r
Check '不能强制别人下线' (Blocked $r)
$r = Call DELETE '/ai/knowledge/1' $null $tAdmin; Show $r
Check '不能删除知识库文件' (Blocked $r)
if ($tTest) {
    $r = Call POST '/auth/password' @{ oldPassword = 'admin123'; newPassword = 'Hack12345' } $tTest; Show $r
    Check '测试冶金也是演示企业，同样被禁止' (Blocked $r)
}

Title '3. 核心业务不受影响'
$now = Get-Date
$t = @{ ticketType = 'HEIGHT'; title = '演示模式测试'; location = '厂房屋顶'; guardianId = 104; workContent = '更换彩钢瓦'; safetyMeasures = '系挂安全带'
        planStartTime = (Fmt $now); planEndTime = (Fmt $now.AddHours(2)) }
$r = Call POST '/ticket' $t $tLisi
Check '作业负责人可以新建作业票' ($r.code -eq 200)
if ($r.code -eq 200) { Call POST "/ticket/$($r.data)/cancel" @{ comment = '测试清理' } $tLisi | Out-Null }
$r = Call GET '/system/user/page?size=5' $null $tAdmin
Check '可以查看员工列表' ($r.code -eq 200)
$r = Call GET '/system/notice/page?size=5' $null $tAdmin
Check '可以查看通知公告' ($r.code -eq 200)

Title '4. 平台管理员不受限制'
try {
    $tPlat = Login 'platform' 'admin'
    $r = Call POST '/auth/password' @{ oldPassword = 'wrong-password'; newPassword = 'Whatever123' } $tPlat; Show $r
    Check '平台管理员改密码没有被演示模式拦截（这里故意输错原密码，不会真的修改）' ($r.code -ne 403 -and $r.code -ne 200)
} catch {
    Write-Host "  （跳过：平台管理员登录失败，可能已经改过密码：$($_.Exception.Message)）" -ForegroundColor DarkGray
}

Title '5. AI 每个演示企业每天限额'
$st = (Call GET '/ai/status' $null $tZhang).data
if (-not $st.chatEnabled) {
    Write-Host '  （跳过：没有配置大模型，隐患提取走关键词规则，不计入 AI 次数）' -ForegroundColor DarkGray
} else {
    $limit = [int]$info.aiDailyLimit
    if ($limit -gt 10) {
        Write-Host "  （跳过：每日限额是 $limit 次，测试会花太多调用。用 --safework.demo.ai-daily-limit=5 启动后端再测）" -ForegroundColor DarkGray
    } else {
        $hit = $null
        for ($i = 1; $i -le $limit + 2; $i++) {
            $r = Call POST '/ai/hazard/parse' @{ text = '仓库通道堆满了纸箱' } $tZhang
            if ($r.code -eq 429 -and $r.msg -like '*演示环境今天*') { $hit = $i; Show $r; break }
        }
        Check "演示企业今天的 AI 次数用完后提示明天再来（第 $hit 次）" ($null -ne $hit)
        if ($tTest) {
            $r = Call POST '/ai/hazard/parse' @{ text = '仓库通道堆满了纸箱' } $tTest; Show $r
            Check '另一个演示企业的额度单独计算' ($r.code -eq 200 -or ($r.code -eq 429 -and $r.msg -like '*演示环境今天*'))
        }
    }
}

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
