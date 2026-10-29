# 安全管理自测：受限空间台账、扫码、隐患排查闭环
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-safety.ps1
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
$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tZhao  = Login 'demo' 'zhaoliu'
$tTest  = Login 'test' 'admin'
$ts = Get-Date -Format 'MMddHHmmss'

Title '1. 受限空间台账'
$page = (Call GET '/safety/space/page?size=50' $null $tLisi).data
Write-Host "  演示化工共 $($page.total) 个受限空间"
Check '作业负责人可以查看台账' ($page.total -ge 5)
Check '列表不返回二维码令牌' (-not ($page.records[0].PSObject.Properties.Name -contains 'qrToken'))
$r = Call POST '/safety/space' @{ spaceCode = "T-$ts"; spaceName = '测试'; spaceType = '储罐'; location = 'x'; riskLevel = 'LOW' } $tZhao; Show $r
Check '监护人没有新增权限（403）' ($r.code -eq 403)

$space = @{ spaceCode = "CS-T$ts"; spaceName = "测试地下室 $ts"; spaceType = '地下有限空间'; location = '综合楼负一层'
            deptId = 1105; responsibleId = 104; medium = '可能缺氧'; riskLevel = 'MEDIUM' }
$r = Call POST '/safety/space' $space $tAdmin; Show $r
Check '管理员新增受限空间' ($r.code -eq 200)
$spaceId = $r.data
$r = Call POST '/safety/space' $space $tAdmin; Show $r
Check '同一企业编号不能重复' ($r.code -eq 400)
$space2 = $space.Clone(); $space2.Remove('deptId'); $space2.Remove('responsibleId')
$r = Call POST '/safety/space' $space2 $tTest; Show $r
Check '别的企业可以用相同编号（唯一约束按企业）' ($r.code -eq 200)
$r = Call POST '/safety/space' @{ spaceCode = "X$ts"; spaceName = 'x'; spaceType = '储罐'; location = 'x'; riskLevel = 'SUPER' } $tAdmin; Show $r
Check '风险等级取值校验' ($r.code -eq 400)

Title '2. 扫码查看（不需要登录）'
$token = (Call GET "/safety/space/$spaceId/qr-token" $null $tAdmin).data
Write-Host "  二维码地址：http://localhost:5173/scan/$token"
$r = Call GET "/public/space/$token"; Show $r
Check '不带 token 也能查看空间信息' ($r.code -eq 200 -and $r.data.spaceName -like '测试地下室*')
Check '返回企业名称和部门' ($r.data.tenantName -eq '演示化工有限公司' -and $r.data.deptName -eq '设备部')
$r = Call GET '/public/space/0123456789abcdef0123456789abcdef'; Show $r
Check '无效的二维码返回 404' ($r.code -eq 404)
$r = Call GET "/safety/space/$spaceId/qr-token" $null $tLisi; Show $r
Check '没有修改权限的人拿不到二维码令牌' ($r.code -eq 403)

Title '3. 停用空间后：扫码失效、不能新建作业票'
Call PUT "/safety/space/$spaceId/status" @{ status = 0 } $tAdmin | Out-Null
$r = Call GET "/public/space/$token"; Show $r
Check '停用后扫码返回 404' ($r.code -eq 404)
$ticket = @{ ticketType = 'CONFINED_SPACE'; spaceId = $spaceId; title = 'x'; location = 'x'; guardianId = 104
             planStartTime = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'); planEndTime = (Get-Date).AddHours(2).ToString('yyyy-MM-dd HH:mm:ss') }
$r = Call POST '/ticket' $ticket $tLisi; Show $r
Check '停用的空间不能新建作业票' ($r.code -eq 400)
Call PUT "/safety/space/$spaceId/status" @{ status = 1 } $tAdmin | Out-Null
$r = Call POST "/safety/space/$spaceId/qr-token/reset" @{} $tAdmin
$r = Call GET "/public/space/$token"; Show $r
Check '重新生成二维码后，旧二维码失效' ($r.code -eq 404)

Title '4. 隐患闭环：上报 → 指派 → 整改 → 验收不通过 → 再整改 → 验收通过'
$r = Call POST '/safety/hazard' @{ title = "罐区围堰排水阀未关闭 $ts"; category = '设备设施'; level = 'GENERAL'; location = '罐区东侧'; spaceId = 1001 } $tZhao; Show $r
Check '监护人上报隐患' ($r.code -eq 200)
$hid = $r.data
$d = (Call GET "/safety/hazard/$hid" $null $tZhao).data
Check "编号以 YH 开头、状态为待指派（$($d.hazard.hazardNo)）" ($d.hazard.hazardNo -like 'YH*' -and $d.hazard.status -eq 'REPORTED')
Check '关联了受限空间' ($d.hazard.spaceName -eq '1# 原料储罐 T-101')
$deadline = (Get-Date).AddDays(3).ToString('yyyy-MM-dd HH:mm:ss')
$r = Call POST "/safety/hazard/$hid/assign" @{ rectifierId = 103; deadline = $deadline } $tLisi; Show $r
Check '作业负责人没有指派权限（403）' ($r.code -eq 403)
$r = Call POST "/safety/hazard/$hid/assign" @{ rectifierId = 103; deadline = '2020-01-01 00:00:00' } $tZhang; Show $r
Check '整改期限不能是过去的时间' ($r.code -eq 400)
$r = Call POST "/safety/hazard/$hid/assign" @{ rectifierId = 103; deadline = $deadline; comment = '今天下班前关闭' } $tZhang; Show $r
Check '安全员指派 lisi 整改' ($r.code -eq 200)
$r = Call POST "/safety/hazard/$hid/rectify" @{ rectifyMeasure = 'x' } $tZhao; Show $r
Check '不是整改人不能提交整改（403）' ($r.code -eq 403)
$r = Call POST "/safety/hazard/$hid/rectify" @{} $tLisi; Show $r
Check '整改措施必填' ($r.code -eq 400)
$r = Call POST "/safety/hazard/$hid/rectify" @{ rectifyMeasure = '已关闭排水阀' } $tLisi
Check '整改人提交整改' ($r.code -eq 200)
$r = Call POST "/safety/hazard/$hid/verify-fail" @{} $tZhang; Show $r
Check '验收不通过必须写原因' ($r.code -eq 400)
$r = Call POST "/safety/hazard/$hid/verify-fail" @{ comment = '阀门未挂「禁止操作」牌' } $tZhang
Check '验收不通过，退回整改中' ($r.code -eq 200 -and (Call GET "/safety/hazard/$hid" $null $tLisi).data.hazard.status -eq 'RECTIFYING')
Call POST "/safety/hazard/$hid/rectify" @{ rectifyMeasure = '已关闭排水阀并挂牌上锁' } $tLisi | Out-Null
$r = Call POST "/safety/hazard/$hid/verify-pass" @{ comment = '现场复查合格' } $tZhang
$d = (Call GET "/safety/hazard/$hid" $null $tAdmin).data
Check '验收通过，隐患闭环' ($r.code -eq 200 -and $d.hazard.status -eq 'CLOSED' -and $d.hazard.verifierName -eq '张三（安全员）')
$d.logs | ForEach-Object { Write-Host ("  {0}  {1,-12} {2}  {3}" -f $_.createTime, $_.action, $_.operatorName, $_.comment) }
Check '完整记录 6 步' (@($d.logs).Count -eq 6)

Title '5. 职责分离：自己整改的隐患不能自己验收'
$hid2 = (Call POST '/safety/hazard' @{ title = "安全员自查 $ts"; category = '安全管理'; level = 'GENERAL'; location = '办公楼' } $tZhang).data
Call POST "/safety/hazard/$hid2/assign" @{ rectifierId = 102; deadline = $deadline } $tZhang | Out-Null
Call POST "/safety/hazard/$hid2/rectify" @{ rectifyMeasure = '已处理' } $tZhang | Out-Null
$r = Call POST "/safety/hazard/$hid2/verify-pass" @{} $tZhang; Show $r
Check '整改人不能验收自己的整改（403）' ($r.code -eq 403)
$r = Call POST "/safety/hazard/$hid2/verify-pass" @{} $tAdmin
Check '由管理员验收通过' ($r.code -eq 200)

Title '6. 逾期、数据权限、租户隔离'
$od = (Call GET '/safety/hazard/page?overdue=true&size=50' $null $tZhang).data
Check "逾期筛选能查到演示数据中的逾期隐患（$($od.total) 条）" ($od.total -ge 1 -and (@($od.records | Where-Object { -not $_.overdue }).Count -eq 0))
$mine = (Call GET '/safety/hazard/page?size=100' $null $tZhao).data
$others = @($mine.records | Where-Object { $_.reporterId -ne '104' -and $_.rectifierId -ne '104' })
Check "监护人只能看到自己上报或负责整改的隐患（$($mine.total) 条）" ($others.Count -eq 0)
$r = Call GET '/safety/hazard/1' $null $tLisi; Show $r
Check 'lisi 看不到别人上报、且不归自己整改的隐患' ($r.code -eq 404)
$r = Call GET "/safety/hazard/$hid" $null $tTest; Show $r
Check '测试冶金看不到演示化工的隐患' ($r.code -eq 404)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过，安全管理模块完成！' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Yellow }
