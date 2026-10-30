# 作业票手写签名 + 打印 + 扫码验真自测：签名校验、签名和操作同一事务、打印数据、验真码、匿名验真、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-print.ps1
# 可以重复运行

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$base = 'http://localhost:8080'

function CallRaw($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $body) {
        $json = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 5 }
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    $resp = Invoke-WebRequest @params
    return [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
}
function Call($method, $path, $body, $token) { (CallRaw $method $path $body $token) | ConvertFrom-Json }
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
function Detail($id, $token) { (Call GET "/ticket/$id" $null $token).data }
# 用 System.Drawing 画一个签名（和浏览器画布导出的一样是 PNG data URL）；$blank 为 true 时是空白画布
function Signature([bool]$blank = $false) {
    $bmp = New-Object System.Drawing.Bitmap 360, 120
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    if (-not $blank) {
        $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::Black), 4
        $g.DrawLines($pen, [System.Drawing.Point[]]@((New-Object System.Drawing.Point 20, 80), (New-Object System.Drawing.Point 80, 20),
            (New-Object System.Drawing.Point 140, 90), (New-Object System.Drawing.Point 200, 30), (New-Object System.Drawing.Point 260, 85), (New-Object System.Drawing.Point 330, 40)))
        $pen.Dispose()
    }
    $g.Dispose()
    $ms = New-Object IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    return 'data:image/png;base64,' + [Convert]::ToBase64String($ms.ToArray())
}
$failed = 0
$ts = Get-Date -Format 'MMddHHmmss'
$now = Get-Date

$tLisi  = Login 'demo' 'lisi'
$tZhang = Login 'demo' 'zhangsan'
$tZhao  = Login 'demo' 'zhaoliu'
$tTest  = Login 'test' 'admin'
$sign = Signature
$blank = Signature $true

$ticket = @{ ticketType = 'HOT_WORK'; title = "打印签名测试 $ts"; location = '机修车间'; guardianId = 104
             workContent = '管道焊接'; safetyMeasures = '1. 清理周边 15 米可燃物 2. 配备 2 具灭火器'
             planStartTime = (Fmt $now); planEndTime = (Fmt $now.AddHours(4))
             gasOxygen = 20.9; gasLel = 1.5; gasH2s = 0; gasCheckTime = (Fmt $now) }
$id = (Call POST '/ticket' $ticket $tLisi).data

Title '1. 签名校验：不合格的签名，整个操作都不执行'
$r = Call POST "/ticket/$id/submit" @{ signature = $blank } $tLisi; Show $r
Check '空白签名被拒绝' ($r.code -eq 400 -and $r.msg -like '*空白*')
$r = Call POST "/ticket/$id/submit" @{ signature = 'data:image/jpeg;base64,AAAA' } $tLisi; Show $r
Check '不是 PNG 被拒绝' ($r.code -eq 400 -and $r.msg -like '*PNG*')
$r = Call POST "/ticket/$id/submit" @{ signature = 'data:image/png;base64,' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('<script>alert(1)</script>')) } $tLisi; Show $r
Check '伪装成 PNG 的内容被拒绝' ($r.code -eq 400)
Check '签名不合格时作业票还是草稿' ((Detail $id $tLisi).ticket.status -eq 'DRAFT')

Title '2. 提交、审批、开工都带手写签名'
$r = Call POST "/ticket/$id/submit" @{ signature = $sign } $tLisi
Check '申请人签名提交' ($r.code -eq 200)
$r = Call POST "/ticket/$id/approve" @{ comment = '动火措施已落实'; signature = $sign } $tZhang
Check '安全员签名审批' ($r.code -eq 200)
$r = Call POST "/ticket/$id/start" @{ gasOxygen = 20.8; gasLel = 1.0; gasH2s = 0; comment = '现场复测合格'; signature = $sign } $tZhao
Check '监护人签名开工' ($r.code -eq 200)
$d = Detail $id $tLisi
$signed = @($d.logs | Where-Object { $_.signImage })
Check "操作记录里有 3 个签名（$((@($signed | ForEach-Object { $_.action })) -join '、')）" ($signed.Count -eq 3)
Check '签名就是提交的那张图片' ($signed[0].signImage -eq $sign)

Title '3. 打印数据'
$r = Call GET "/ticket/$id/print" $null $tLisi
$p = $r.data
Check '申请人可以打印' ($r.code -eq 200)
Check "验真码是 32 位（$($p.verifyToken.Substring(0, 8))…）" ($p.verifyToken -match '^[0-9a-f]{32}$')
Check "企业名称、打印人（$($p.tenantName)、$($p.printedBy)）" ($p.tenantName -and $p.printedBy -like '李四*')
Check "票面结论：$($p.verdict.text)" ($p.verdict.level -eq 'OK')
Check '打印数据带签名' (@($p.logs | Where-Object { $_.signImage }).Count -eq 3)
$p2 = (Call GET "/ticket/$id/print" $null $tZhao).data
Check '再次打印（监护人）验真码不变' ($p2.verifyToken -eq $p.verifyToken)
$r = Call GET "/ticket/$id/print" $null $tTest; Show $r
Check '别的企业打印不了（404）' ($r.code -eq 404)

Title '4. 扫码验真（不登录）'
$raw = CallRaw GET "/public/ticket/$($p.verifyToken)" $null $null
$v = ($raw | ConvertFrom-Json).data
Write-Host "  -> $($v.tenantName) $($v.ticketNo) $($v.statusLabel)：$($v.verdict.text)" -ForegroundColor DarkGray
Check '不登录也能验真，编号对得上' ($v.ticketNo -eq $d.ticket.ticketNo)
Check '作业中：有效' ($v.status -eq 'IN_PROGRESS' -and $v.verdict.level -eq 'OK')
Check "审批与确认记录（$(@($v.steps).Count) 步），每步标明是否手写签名" (@($v.steps).Count -eq 3 -and @($v.steps | Where-Object { $_.signed }).Count -eq 3)
Check '匿名接口不返回签名图片和审批意见' (-not $raw.Contains('data:image') -and -not $raw.Contains('动火措施已落实'))
$r = Call GET '/public/ticket/abc' $null $null; Show $r
Check '格式不对的验真码（404）' ($r.code -eq 404)
$r = Call GET ('/public/ticket/' + ('f' * 32)) $null $null; Show $r
Check '不存在的验真码（404）' ($r.code -eq 404)

Title '5. 完工后再扫码'
$r = Call POST "/ticket/$id/complete" @{ comment = '现场已清理'; signature = $sign } $tZhao
Check '监护人签名完工' ($r.code -eq 200)
$v = (Call GET "/public/ticket/$($p.verifyToken)" $null $null).data
Check "完工后扫码：$($v.verdict.text)" ($v.status -eq 'COMPLETED' -and $v.verdict.level -eq 'WARN')

Title '6. 兼容：不签名也能操作（脚本、旧版本客户端），打印时显示未签名'
$id2 = (Call POST '/ticket' ($ticket + @{}) $tLisi).data
$r = Call POST "/ticket/$id2/submit" $null $tLisi
Check '不带签名提交成功' ($r.code -eq 200)
$p3 = (Call GET "/ticket/$id2/print" $null $tLisi).data
Check '没签名的记录 signImage 为空' (-not (@($p3.logs | Where-Object { $_.action -eq 'SUBMIT' })[0].signImage))
Check "待审批的票面结论是不能作业（$($p3.verdict.text)）" ($p3.verdict.level -eq 'DANGER')
Call POST "/ticket/$id2/cancel" @{ comment = '测试清理' } $tLisi | Out-Null

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
