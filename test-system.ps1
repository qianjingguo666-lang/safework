# 系统管理自测：部门、用户管理（修改/停用/重置密码/改密码）、操作日志
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-system.ps1
# 可以重复运行

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
function Status($id, $token) { (Call GET "/ticket/$id" $null $token).data.ticket.status }
function Fmt($d) { $d.ToString('yyyy-MM-dd HH:mm:ss') }
$failed = 0

$tAdmin = Login 'demo' 'admin'
$tZhao  = Login 'demo' 'zhaoliu'
$tTest  = Login 'test' 'admin'
$ts = Get-Date -Format 'MMddHHmmss'

Title '1. 部门树'
$tree = (Call GET '/system/dept/tree' $null $tAdmin).data
Write-Host "  演示化工顶级部门：$((@($tree) | ForEach-Object { $_.deptName }) -join '、')"
Check '演示化工有 3 个顶级部门' (@($tree).Count -eq 3)
Check '生产部下有 2 个车间' (@((@($tree) | Where-Object { $_.deptName -eq '生产部' }).children).Count -ge 2)
$treeTest = (Call GET '/system/dept/tree' $null $tTest).data
Check '测试冶金看到的是自己的部门' (-not (@($treeTest) | Where-Object { $_.deptName -eq '生产部' }))

Title '2. 新增部门、防止循环'
$r = Call POST '/system/dept' @{ parentId = 1101; deptName = "测试车间$ts"; sort = 9 } $tAdmin; Show $r
Check '在生产部下新增车间' ($r.code -eq 200)
$deptId = $r.data
$r = Call PUT '/system/dept/1101' @{ parentId = $deptId; deptName = '生产部' } $tAdmin; Show $r
Check '不能把部门移到自己的下级下面' ($r.code -eq 400)
$r = Call POST '/system/dept' @{ parentId = 2101; deptName = '跨企业' } $tAdmin; Show $r
Check '不能挂到别的企业的部门下（查不到）' ($r.code -eq 404)

Title '3. 新增用户（带部门和角色），按部门筛选包含下级'
$name = "t$ts"
$r = Call POST '/system/user' @{ username = $name; password = 'admin123'; nickname = "测试员$ts"; deptId = $deptId; roleIds = @(13) } $tAdmin; Show $r
Check '新增用户成功' ($r.code -eq 200)
$uid = $r.data
$u = (Call GET "/system/user/page?keyword=$name" $null $tAdmin).data.records[0]
Write-Host "  部门 = $($u.deptName)  角色 = $($u.roleNames -join '、')"
Check '列表显示部门和角色' ($u.deptName -eq "测试车间$ts" -and ($u.roleNames -contains '作业负责人'))
$p = (Call GET '/system/user/page?deptId=1101&size=100' $null $tAdmin).data
Check '按“生产部”筛选，能查到下级车间的用户' (@($p.records | Where-Object { $_.username -eq $name }).Count -eq 1)
$r = Call DELETE "/system/dept/$deptId" $null $tAdmin; Show $r
Check '部门下有用户时不能删除' ($r.code -eq 400)

Title '4. 修改用户、停用后立即踢下线'
$r = Call PUT "/system/user/$uid" @{ nickname = "测试员$ts（改）"; phone = '13800000000'; deptId = 1102 } $tAdmin
Check '修改用户信息' ($r.code -eq 200)
$tUser = Login 'demo' $name
$r = Call PUT "/system/user/$uid/status" @{ status = 0 } $tAdmin
Check '停用用户' ($r.code -eq 200)
$r = Call GET '/auth/me' $null $tUser; Show $r
Check '被停用的用户原来的登录立即失效（401）' ($r.code -eq 401)
$r = LoginRaw 'demo' $name 'admin123'; Show $r
Check '被停用的用户无法登录' ($r.code -eq 400)
Call PUT "/system/user/$uid/status" @{ status = 1 } $tAdmin | Out-Null
$r = Call PUT '/system/user/101/status' @{ status = 0 } $tAdmin; Show $r
Check '不能停用自己' ($r.code -eq 400)

Title '5. 重置密码、修改自己的密码'
$tUser = Login 'demo' $name
$r = Call PUT "/system/user/$uid/password" @{ password = 'reset123' } $tAdmin
Check '管理员重置密码' ($r.code -eq 200)
$r = Call GET '/auth/me' $null $tUser
Check '重置后原来的登录失效' ($r.code -eq 401)
$tUser = (LoginRaw 'demo' $name 'reset123').data.token
Check '用新密码登录' ([bool]$tUser)
$r = Call POST '/auth/password' @{ oldPassword = 'wrong'; newPassword = 'mine123' } $tUser; Show $r
Check '原密码错误不能修改' ($r.code -eq 400)
$r = Call POST '/auth/password' @{ oldPassword = 'reset123'; newPassword = 'mine123' } $tUser
$ok = (LoginRaw 'demo' $name 'mine123').code -eq 200
Check '修改自己的密码后用新密码登录' ($r.code -eq 200 -and $ok)

Title '6. 下拉选项、权限'
$opts = (Call GET '/system/user/options' $null $tZhao).data
Check "普通用户可以获取人员下拉（$(@($opts).Count) 人，不含密码、手机号）" (@($opts).Count -ge 4 -and -not ($opts[0].PSObject.Properties.Name -contains 'phone'))
$r = Call GET '/system/log/page' $null $tZhao; Show $r
Check '监护人不能查看操作日志（403）' ($r.code -eq 403)

Title '7. 操作日志（AOP 自动记录）'
$logs = (Call GET '/system/log/page?module=用户管理&size=50' $null $tAdmin).data
$logs.records | Select-Object -First 6 | ForEach-Object {
    Write-Host ("  {0}  {1,-8} {2,-10} {3}  {4}" -f $_.createTime, $_.action, $_.operatorName, $(if ($_.status -eq 1) { '成功' } else { '失败：' + $_.errorMsg }), $_.costMs) }
Check '用户管理的操作都被记录' ($logs.total -ge 5)
$reset = @($logs.records | Where-Object { $_.action -eq '重置密码' })[0]
Write-Host "  重置密码的参数：$($reset.requestParams)"
Check '日志里的密码已脱敏' ($reset.requestParams.Contains('******') -and -not $reset.requestParams.Contains('reset123'))
$fail = @($logs.records | Where-Object { $_.status -eq 0 })
Check '失败的操作也被记录（如停用自己）' ($fail.Count -ge 1)
$pwdLog = (Call GET '/system/log/page?module=个人中心&size=5' $null $tAdmin).data.records
Check '修改密码接口不记录参数' (@($pwdLog).Count -ge 1 -and -not $pwdLog[0].requestParams)
$logsTest = (Call GET '/system/log/page?module=用户管理&size=50' $null $tTest).data
Check '测试冶金看不到演示化工的日志' (@($logsTest.records | Where-Object { $_.operatorName -eq '演示企业管理员' }).Count -eq 0)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过，系统管理完成！' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Yellow }
