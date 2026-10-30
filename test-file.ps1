# 附件 + Excel 导入导出 + 数据看板自测
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-file.ps1
# 可以重复运行。后端必须用开发环境启动：java -jar safework-server\target\safework-server.jar --spring.profiles.active=dev

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

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
function LoginRaw($tenant, $user, $password) {
    $c = (Call GET '/auth/captcha').data
    if ($c.enabled -and -not $c.debugAnswer) { throw '后端没有返回验证码答案：请用 --spring.profiles.active=dev 启动后端' }
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

# multipart 上传：$fields 是表单字段，文件字段名是 file
function PostFile($path, $token, $fields, [byte[]]$bytes, $fileName) {
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromMinutes(2)
    $client.DefaultRequestHeaders.Add('Authorization', "Bearer $token")
    $form = New-Object System.Net.Http.MultipartFormDataContent
    foreach ($k in $fields.Keys) { $form.Add((New-Object System.Net.Http.StringContent([string]$fields[$k])), $k) }
    $form.Add((New-Object System.Net.Http.ByteArrayContent(,$bytes)), 'file', $fileName)
    $resp = $client.PostAsync("$base$path", $form).Result
    $text = [Text.Encoding]::UTF8.GetString($resp.Content.ReadAsByteArrayAsync().Result)
    $client.Dispose()
    return $text | ConvertFrom-Json
}
function Upload($token, $bizType, $bizId, $tag, [byte[]]$bytes, $fileName) {
    PostFile '/file/upload' $token @{ bizType = $bizType; bizId = $bizId; tag = $tag } $bytes $fileName
}
# 下载：成功返回 @{ ok; bytes; type; disposition }，失败返回后端的 JSON 错误
function Download($path, $token) {
    $resp = Invoke-WebRequest -Uri "$base$path" -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing
    $bytes = $resp.RawContentStream.ToArray()
    $type = "$($resp.Headers['Content-Type'])"
    if ($type -like '*json*') {
        $j = [Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json
        return @{ ok = $false; code = $j.code; msg = $j.msg }
    }
    return @{ ok = $true; bytes = $bytes; type = $type; disposition = [Uri]::UnescapeDataString("$($resp.Headers['Content-Disposition'])") }
}
function IsZip([byte[]]$b) { $b.Length -gt 4 -and $b[0] -eq 0x50 -and $b[1] -eq 0x4B }
# 造一个「图片」：PNG 文件头 + 随机内容（后端按文件头识别类型）
function FakePng() { [byte[]](@(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + (1..2000 | ForEach-Object { Get-Random -Maximum 256 })) }

$failed = 0
$ts = Get-Date -Format 'MMddHHmmss'
$now = Get-Date
$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tZhao  = Login 'demo' 'zhaoliu'
$tAdmin = Login 'demo' 'admin'
$tTest  = Login 'test' 'admin'

Title '1. 隐患现场照片：按文件内容识别类型'
$hid = (Call POST '/safety/hazard' @{ title = "附件测试：配电箱门未关 $ts"; category = '设备设施'; level = 'GENERAL'; location = '机修车间' } $tZhao).data
$png = FakePng
$r = Upload $tZhao 'HAZARD' $hid 'REPORT' $png 'site-photo.png'; Show $r
Check '上报人上传现场照片' ($r.code -eq 200 -and $r.data.ext -eq 'png')
$fileId = $r.data.id
Check '接口不返回存储路径' (-not $r.data.storageKey)
$fake = [Text.Encoding]::UTF8.GetBytes('<%@ page import="java.io.*" %> 我是伪装成图片的脚本')
$r = Upload $tZhao 'HAZARD' $hid 'REPORT' $fake 'photo.jpg'; Show $r
Check '把脚本改名成 .jpg 上传被识别出来拒绝' ($r.code -eq 400)
$pdf = [Text.Encoding]::ASCII.GetBytes('%PDF-1.4 test')
$r = Upload $tZhao 'HAZARD' $hid 'REPORT' $pdf 'report.pdf'; Show $r
Check '现场照片只能传图片' ($r.code -eq 400 -and $r.msg.Contains('图片'))
$r = Upload $tZhao 'HAZARD' $hid 'RECTIFY' $png 'x.png'; Show $r
Check '还没到整改环节，不能传整改照片（403）' ($r.code -eq 403)

Title '2. 附件权限跟随隐患'
$l = (Call GET "/file/list?bizType=HAZARD&bizId=$hid&tag=REPORT" $null $tZhang).data
Check '安全员能看到照片（能看隐患就能看照片）' (@($l.files).Count -eq 1 -and $l.editable)
$r = Call GET "/file/list?bizType=HAZARD&bizId=$hid&tag=REPORT" $null $tLisi; Show $r
Check '和这条隐患无关的人看不到（404）' ($r.code -eq 404)
$d = Download "/file/$fileId" $tZhang
Check "下载内容和上传的一致（$($d.bytes.Length) 字节，$($d.type)）" ($d.ok -and $d.bytes.Length -eq $png.Length -and $d.type -like 'image/png*')
$d = Download "/file/$fileId" $tLisi
Check '无关的人直接下载也不行' (-not $d.ok -and $d.code -eq 404)
$d = Download "/file/$fileId" $tTest
Check '别的企业更不行' (-not $d.ok -and $d.code -eq 404)
$r = Call DELETE "/file/$fileId" $null $tZhang; Show $r
Check '不能删除别人上传的' ($r.code -eq 403)

Title '3. 整改环节：整改人上传整改后照片'
Call POST "/safety/hazard/$hid/assign" @{ rectifierId = 103; deadline = (Fmt $now.AddDays(1)) } $tZhang | Out-Null
$r = Upload $tZhao 'HAZARD' $hid 'REPORT' $png 'late.png'; Show $r
Check '已经指派了，上报人不能再补传现场照片' ($r.code -eq 403)
$r = Upload $tLisi 'HAZARD' $hid 'RECTIFY' (FakePng) 'after.png'
Check '整改人上传整改后照片' ($r.code -eq 200)
$r = Upload $tZhang 'HAZARD' $hid 'RECTIFY' (FakePng) 'after2.png'
Check '安全员不能替整改人传整改照片' ($r.code -eq 403)
$r = Call DELETE "/file/$fileId" $null $tZhao
Check '过了这个环节，自己上传的也不能删了' ($r.code -eq 403)

Title '4. 作业票附件'
$ticket = @{ ticketType = 'HEIGHT'; title = "附件测试高处作业 $ts"; location = '3 号厂房屋面'; guardianId = 104
             safetyMeasures = '系安全带'; planStartTime = (Fmt $now); planEndTime = (Fmt $now.AddHours(3)) }
$tid = (Call POST '/ticket' $ticket $tLisi).data
$r = Upload $tLisi 'TICKET' $tid 'ATTACH' $pdf 'work-plan.pdf'
Check '申请人上传作业方案（PDF）' ($r.code -eq 200 -and $r.data.ext -eq 'pdf')
$r = Upload $tZhao 'TICKET' $tid 'ATTACH' (FakePng) 'gas-check.png'
Check '监护人上传气体检测照片' ($r.code -eq 200)
$l = (Call GET "/file/list?bizType=TICKET&bizId=$tid&tag=ATTACH" $null $tZhang).data
Check '审批人能看到 2 个附件，但不能上传' (@($l.files).Count -eq 2 -and -not $l.editable)
$exe = [byte[]](0x4D, 0x5A, 0x90, 0x00, 1, 2, 3, 4, 5, 6, 7, 8)
$r = Upload $tLisi 'TICKET' $tid 'ATTACH' $exe 'tool.exe'; Show $r
Check 'exe 等不在白名单的文件被拒绝' ($r.code -eq 400)

Title '5. Excel 导出（带数据权限）'
# 演示数据里的台账默认都是启用的，先临时停用 CS-005，验证「停用」状态也能导出、导入
$cs5 = (Call GET '/safety/space/page?keyword=CS-005&size=1' $null $tAdmin).data.records[0]
Call PUT "/safety/space/$($cs5.id)/status" @{ status = 0 } $tAdmin | Out-Null
# 只导出演示数据里 CS- 开头的台账（导入测试会把编号改成新前缀，可以重复运行）
$d = Download '/safety/space/export?keyword=CS-' $tAdmin
Write-Host "  $($d.disposition)" -ForegroundColor DarkGray
Check "导出受限空间台账（$($d.bytes.Length) 字节）" ($d.ok -and (IsZip $d.bytes) -and $d.disposition.Contains('受限空间台账'))
$exportFile = Join-Path $env:TEMP "spaces-$ts.xlsx"
[IO.File]::WriteAllBytes($exportFile, $d.bytes)
$d = Download '/safety/space/export' $tZhao
Check '监护人没有导入导出权限（403）' (-not $d.ok -and $d.code -eq 403)
$d = Download '/safety/space/import-template' $tZhang
Check '下载导入模板' ($d.ok -and (IsZip $d.bytes))
$d = Download '/safety/hazard/export' $tZhang
Check '安全员导出隐患台账' ($d.ok -and (IsZip $d.bytes))
$d = Download '/ticket/export?status=DRAFT' $tZhang
Check '安全员按状态导出作业票' ($d.ok -and (IsZip $d.bytes))
$d = Download '/ticket/export' $tLisi
Check '作业负责人没有导出权限（403）' (-not $d.ok -and $d.code -eq 403)

Title '6. Excel 导入：先全部校验，有错一行都不导'
$total = (Call GET '/safety/space/page?keyword=CS-&size=100' $null $tAdmin).data.total
$r = PostFile '/safety/space/import' $tAdmin @{} ([IO.File]::ReadAllBytes($exportFile)) 'spaces.xlsx'
Write-Host "  共 $($r.data.total) 行，错误 $(@($r.data.errors).Count) 行，例如：第 $($r.data.errors[0].row) 行 $($r.data.errors[0].message)" -ForegroundColor DarkGray
Check '把刚导出的文件原样导入：编号全部已存在，一行都没导' ($r.code -eq 200 -and $r.data.success -eq 0 -and @($r.data.errors).Count -eq $total)
Check '错误带 Excel 行号（表头是第 1 行，数据从第 2 行开始）' ($r.data.errors[0].row -eq 2)
# 把编号改掉再导入：直接改 xlsx 里的 XML（xlsx 本质是 zip）
$zip = [IO.Compression.ZipFile]::Open($exportFile, 'Update')
foreach ($entry in @($zip.Entries | Where-Object { $_.FullName -like 'xl/*.xml' -or $_.FullName -like 'xl/worksheets/*.xml' })) {
    $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8)
    $xml = $reader.ReadToEnd(); $reader.Close()
    if ($xml.Contains('CS-')) {
        $name = $entry.FullName; $entry.Delete()
        $writer = New-Object IO.StreamWriter($zip.CreateEntry($name).Open(), (New-Object Text.UTF8Encoding $false))
        $writer.Write($xml.Replace('CS-', "IM$ts-")); $writer.Close()
    }
}
$zip.Dispose()
$r = PostFile '/safety/space/import' $tAdmin @{} ([IO.File]::ReadAllBytes($exportFile)) 'spaces.xlsx'; Show $r
Check "改掉编号后导入成功（$($r.data.success) 条）" ($r.code -eq 200 -and $r.data.success -eq $total -and @($r.data.errors).Count -eq 0)
$imported = (Call GET "/safety/space/page?keyword=IM$ts&size=100" $null $tAdmin).data
Check '导入的台账能查到，部门、责任人、风险等级都对上了' ($imported.total -eq $total -and @($imported.records | Where-Object { $_.riskLevel -eq 'HIGH' }).Count -ge 1 -and @($imported.records | Where-Object { $_.responsibleName }).Count -ge 1)
Check '「停用」状态也导进来了' (@($imported.records | Where-Object { $_.status -eq 0 }).Count -ge 1)
$r = PostFile '/safety/space/import' $tAdmin @{} ([Text.Encoding]::UTF8.GetBytes('not excel')) 'spaces.txt'; Show $r
Check '不是 Excel 文件直接拒绝' ($r.code -eq 400)
Remove-Item $exportFile
Call PUT "/safety/space/$($cs5.id)/status" @{ status = 1 } $tAdmin | Out-Null

Title '7. 数据看板'
$d = (Call GET '/dashboard/overview?days=30' $null $tZhang).data
Write-Host "  30 天作业票 $($d.kpi.ticketTotal)，作业中 $($d.kpi.ticketInProgress)，未闭环隐患 $($d.kpi.hazardOpen)（逾期 $($d.kpi.hazardOverdue)），整改及时率 $($d.kpi.rectifyOnTimeRate)，受限空间 $($d.kpi.spaceTotal)" -ForegroundColor DarkGray
Check '日期轴 30 天，每种作业类型每天都有数据（没有的补 0）' (@($d.dates).Count -eq 30 -and @($d.ticketTrend.'受限空间作业').Count -eq 30)
Check '统计到了这次测试的作业票、隐患' ($d.kpi.ticketTotal -ge 1 -and $d.kpi.hazardOpen -ge 1)
$enabled = (Call GET '/safety/space/page?status=1&size=1' $null $tAdmin).data.total
Check "受限空间数和台账一致（$enabled 个启用）" ($d.kpi.spaceTotal -eq $enabled)
$d7 = (Call GET '/dashboard/overview?days=7' $null $tZhang).data
Check '切换成近 7 天' (@($d7.dates).Count -eq 7)
$r = Call GET '/dashboard/overview' $null $tLisi
Check '作业负责人没有看板权限（403）' ($r.code -eq 403)
$dt = (Call GET '/dashboard/overview' $null $tTest).data
$testSpaces = (Call GET '/safety/space/page?status=1&size=1' $null $tTest).data.total
Check "测试冶金只统计自己的数据（受限空间 $($dt.kpi.spaceTotal) 个）" ($dt.kpi.spaceTotal -eq $testSpaces)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过，附件 + Excel + 数据看板完成！' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Yellow }
