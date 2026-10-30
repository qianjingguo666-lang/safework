# 语音智能上报隐患自测：一句话提取（大模型 / 关键词兜底）、受限空间匹配、租户隔离、不自动提交、调用记录、语音识别接口
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-voice.ps1
# 没有配置大模型、讯飞语音也能跑：提取会走关键词规则，语音识别会返回「没有配置」的提示

$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 默认没有加载 HttpClient 所在的程序集
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
function ShowDraft($d) {
    Write-Host "  -> 标题：$($d.title)" -ForegroundColor DarkGray
    Write-Host "     类别：$($d.category)　等级：$($d.level)　位置：$($d.location)　受限空间：$($d.spaceName)" -ForegroundColor DarkGray
    Write-Host "     描述：$($d.description)" -ForegroundColor DarkGray
    Write-Host "     $(if ($d.aiUsed) { '大模型提取' } else { '关键词提取' })，$($d.costMs) ms；提示：$(@($d.notes) -join '；')" -ForegroundColor DarkGray
}
function Parse($text, $token) { Call POST '/ai/hazard/parse' @{ text = $text } $token }
# 上传录音（multipart，字段名 audio）
function PostAudio([byte[]]$bytes, $token) {
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromMinutes(1)
    $client.DefaultRequestHeaders.Add('Authorization', "Bearer $token")
    $form = New-Object System.Net.Http.MultipartFormDataContent
    $form.Add((New-Object System.Net.Http.ByteArrayContent(,$bytes)), 'audio', 'voice.pcm')
    $resp = $client.PostAsync("$base/ai/hazard/transcribe", $form).Result
    $text = [Text.Encoding]::UTF8.GetString($resp.Content.ReadAsByteArrayAsync().Result)
    $client.Dispose()
    return $text | ConvertFrom-Json
}
$failed = 0

$tLisi  = Login 'demo' 'lisi'
$tAdmin = Login 'demo' 'admin'
$tTest  = Login 'test' 'admin'
$tPlat  = Login 'platform' 'admin'

Title '1. AI 配置状态'
$st = (Call GET '/ai/status' $null $tLisi).data
Write-Host "  -> 大模型：$(if ($st.chatEnabled) { $st.chatModel } else { '未配置' })，讯飞语音听写：$(if ($st.asrEnabled) { '已配置' } else { '未配置' })" -ForegroundColor DarkGray
Check '状态接口返回了 asrEnabled 字段' ($null -ne $st.asrEnabled)

Title '2. 一句话提取：重大隐患 + 匹配受限空间'
$before = [int](Call GET '/safety/hazard/page?size=1' $null $tAdmin).data.total
$r = Parse '一号原料储罐旁边的可燃气体报警器不亮了，按测试按钮也不响' $tLisi
Check '提取成功' ($r.code -eq 200)
$d = $r.data; ShowDraft $d
Check '匹配到受限空间 CS-001（1# 原料储罐 T-101）' ("$($d.spaceId)" -eq '1001')
Check '可燃气体报警器失效判为重大隐患' ($d.level -eq 'MAJOR')
Check '类别是设备设施' ($d.category -eq '设备设施')
Check '标题、位置、描述都填好了' ($d.title -and $d.location -and $d.description)
Check '重大隐患有提醒确认' (@($d.notes | Where-Object { $_ -like '*重大隐患*' }).Count -eq 1)
Check '原话保留在结果里，方便核对' ($d.text -like '一号原料储罐*')

Title '3. 一句话提取：一般隐患'
$r = Parse '合成车间门口地上有一大摊油污，人走过去很滑' $tLisi
$d = $r.data; ShowDraft $d
Check '一般隐患' ($r.code -eq 200 -and $d.level -eq 'GENERAL')
Check '类别是作业环境' ($d.category -eq '作业环境')
Check '位置包含「合成车间」' ($d.location -like '*合成车间*')

Title '4. 只填表，不自动提交'
$after = [int](Call GET '/safety/hazard/page?size=1' $null $tAdmin).data.total
Check "隐患台账数量没有变化（$before → $after）：要上报人核对后自己提交" ($after -eq $before)

Title '5. 参数校验和权限'
$r = Parse '   ' $tLisi; Show $r
Check '空内容被拒绝' ($r.code -eq 400)
$r = Parse ('储罐区有问题' * 100) $tLisi; Show $r
Check '超过 500 字被拒绝' ($r.code -eq 400)
$r = Parse '储罐漏了' $tPlat; Show $r
Check '平台账号没有上报隐患权限（403）' ($r.code -eq 403)

Title '6. 租户隔离：只匹配本企业的受限空间'
$r = Parse '一号转炉烟道人孔附近一氧化碳报警器在响' $tTest
$d = $r.data; ShowDraft $d
Check '测试冶金匹配到自己的 1# 转炉烟道' ("$($d.spaceId)" -eq '2001')
$r = Parse '一号原料储罐旁边的报警器不亮了' $tTest
Check '测试冶金匹配不到演示化工的储罐' ($r.code -eq 200 -and "$($r.data.spaceId)" -ne '1001')

Title '7. 调用记录'
$logs = (Call GET '/ai/chat/logs?bizType=HAZARD&size=5' $null $tAdmin).data
Check "管理员在【问答记录】里能看到隐患提取记录（$($logs.total) 条）" ([int]$logs.total -ge 2 -and $logs.records[0].bizType -eq 'HAZARD')

Title '8. 语音识别接口'
$short = PostAudio (New-Object byte[] 200) $tLisi; Show $short
Check '录音太短或没有配置语音听写，返回明确提示' ($short.code -eq 400)
if ($st.asrEnabled) {
    # 2 秒的 440Hz 正弦波（不是人声），只验证和讯飞的连接、鉴权是否正常
    $n = 32000 * 2; $pcm = New-Object byte[] $n
    for ($i = 0; $i -lt $n / 2; $i++) {
        $v = [int](3000 * [Math]::Sin(2 * [Math]::PI * 440 * $i / 16000))
        $pcm[2 * $i] = [byte]($v -band 0xFF); $pcm[2 * $i + 1] = [byte](($v -shr 8) -band 0xFF)
    }
    $r = PostAudio $pcm $tLisi; Show $r
    Write-Host "  -> 识别结果：$($r.data.text)" -ForegroundColor DarkGray
    Check '连接讯飞成功（识别出文字，或者提示没听清）' ($r.code -eq 200 -or ($r.code -eq 400 -and $r.msg -like '*没有听清*'))
    Check '不是鉴权或开通问题' (-not ($r.msg -like '*鉴权*' -or $r.msg -like '*没有开通*' -or $r.msg -like '*不匹配*'))
} else {
    Check '没有配置讯飞语音听写：提示可以打字描述' ($short.msg -like '*没有配置讯飞*')
    Write-Host '  （配置了 config/secret.yml 的 safework.ai.asr 之后，会额外测试和讯飞的连接）' -ForegroundColor DarkGray
}

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
