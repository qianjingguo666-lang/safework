# AI 模块自测：知识库上传、检索问答（SSE 流式）、拒答、作业票 AI 预审、限流
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-ai.ps1
# 不配置大模型 API Key 也能跑：会测试“只检索”的降级模式

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
function Login($tenant, $user) {
    $r = Call POST '/auth/login' @{ tenantCode = $tenant; username = $user; password = 'admin123' }
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

# 调用流式问答接口，返回 { events = 事件列表; answer = 拼好的回答; sources; error; json = 非流式错误 }
function Ask($question, $token, $history) {
    $body = @{ question = $question }
    if ($history) { $body.history = $history }
    $resp = Invoke-WebRequest -Uri "$base/ai/chat/stream" -Method POST -UseBasicParsing -Headers @{ Authorization = "Bearer $token"; Accept = 'text/event-stream, application/json' } `
        -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 5)))
    $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    $result = @{ answer = ''; sources = @(); error = $null; done = $null; json = $null }
    if ($resp.Headers['Content-Type'] -like '*json*') { $result.json = $text | ConvertFrom-Json; return $result }
    foreach ($block in ($text -split "`r?`n`r?`n")) {
        $event = ''; $data = ''
        foreach ($line in ($block -split "`r?`n")) {
            if ($line.StartsWith('event:')) { $event = $line.Substring(6).Trim() }
            elseif ($line.StartsWith('data:')) { $data += $line.Substring(5) }
        }
        if (-not $data) { continue }
        $obj = $data | ConvertFrom-Json
        switch ($event) {
            'sources' { $result.sources = @($obj) }
            'delta'   { $result.answer += $obj.t }
            'done'    { $result.done = $obj }
            'error'   { $result.error = $obj.msg }
        }
    }
    return $result
}

$tAdmin = Login 'demo' 'admin'
$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tTest  = Login 'test' 'admin'

Title '1. AI 配置状态'
$st = (Call GET '/ai/status' $null $tAdmin).data
Write-Host "  对话模型：$(if ($st.chatEnabled) { $st.chatModel } else { '未配置' })   向量模型：$(if ($st.embeddingEnabled) { $st.embeddingModel } else { '未配置' })"
Check '状态接口正常' ($null -ne $st)

Title '2. 上传示例制度文件'
$file = Get-ChildItem 'docs\knowledge\*.md' | Select-Object -First 1
$r = Call POST '/ai/knowledge/upload' $null $tLisi
Check '作业负责人没有上传权限（403）' ($r.code -eq 403)
# PowerShell 5.1 没有 -Form 参数，手动拼 multipart 请求体
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromMinutes(3)
$client.DefaultRequestHeaders.Add('Authorization', "Bearer $tZhang")
$form = New-Object System.Net.Http.MultipartFormDataContent
$content = New-Object System.Net.Http.ByteArrayContent(,[IO.File]::ReadAllBytes($file.FullName))
$content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('text/markdown')
$form.Add($content, 'file', 'sample.md')
$form.Add((New-Object System.Net.Http.StringContent("特殊作业安全管理制度（示例）", [Text.Encoding]::UTF8)), 'title')
$resp = $client.PostAsync("$base/ai/knowledge/upload", $form).Result
$r = [Text.Encoding]::UTF8.GetString($resp.Content.ReadAsByteArrayAsync().Result) | ConvertFrom-Json
Show $r
Check "安全员上传成功，切分为 $($r.data.chunkCount) 个片段" ($r.code -eq 200 -and $r.data.chunkCount -ge 5)
$docId = $r.data.id
$chunks = (Call GET "/ai/knowledge/$docId/chunks" $null $tZhang).data
Check '片段带章节标题（便于检索和理解）' (@($chunks | Where-Object { $_.content.StartsWith('【2.2 气体检测】') }).Count -ge 1)

Title '3. 问答：能检索到正确的条款'
$cases = @(
    @{ q = '氧含量多少才合格？'; expect = '19.5' },
    @{ q = '发生中毒窒息事故怎么救援？'; expect = '盲目施救' },
    @{ q = '动火作业要配备哪些消防器材？'; expect = '灭火器' },
    @{ q = '作业票有效期多长'; expect = '8 小时' }
)
foreach ($c in $cases) {
    $a = Ask $c.q $tLisi
    $top = if ($a.sources.Count) { $a.sources[0] } else { $null }
    Write-Host "  问：$($c.q)"
    Write-Host "    来源[1]：$(if ($top) { $top.content.Substring(0, [Math]::Min(40, $top.content.Length)) + '…（相关度 ' + $top.score + '）' } else { '无' })" -ForegroundColor DarkGray
    Write-Host "    答：$($a.answer.Substring(0, [Math]::Min(80, $a.answer.Length)))" -ForegroundColor DarkGray
    Check "检索到包含“$($c.expect)”的条款" (@($a.sources | Where-Object { $_.content.Contains($c.expect) }).Count -ge 1)
}

Title '4. 拒答：知识库里没有的问题不胡编'
$a = Ask '年假怎么申请？' $tLisi
Write-Host "  答：$($a.answer)" -ForegroundColor DarkGray
Check '没有检索到来源，回答“没有找到”' ($a.sources.Count -eq 0 -and $a.answer.Contains('没有找到'))

Title '5. 多轮追问：带上历史再检索'
$history = @(@{ role = 'user'; content = '受限空间作业气体检测有什么要求？' }, @{ role = 'assistant'; content = '……' })
$a = Ask '中断以后呢？' $tLisi $history
Check '追问“中断以后呢”能找到“中断超过 30 分钟重新检测”' (@($a.sources | Where-Object { $_.content.Contains('中断') }).Count -ge 1)

Title '6. 租户隔离：别的企业检索不到这份文档'
$a = Ask '氧含量多少才合格？' $tTest
Check '测试冶金没有上传文档，检索不到' ($a.sources.Count -eq 0)

Title '7. 作业票 AI 预审'
$now = Get-Date
$ticket = @{ ticketType = 'CONFINED_SPACE'; spaceId = 1004; title = '污水池清淤'; location = '污水处理站'; guardianId = 104
             safetyMeasures = '1. 强制通风'; planStartTime = $now.ToString('yyyy-MM-dd HH:mm:ss'); planEndTime = $now.AddHours(10).ToString('yyyy-MM-dd HH:mm:ss')
             gasOxygen = 19.8; gasLel = 6; gasH2s = 2; gasCheckTime = $now.ToString('yyyy-MM-dd HH:mm:ss') }
$tid = (Call POST '/ticket' $ticket $tLisi).data
$r = Call POST "/ai/ticket/$tid/review" $null $tLisi; Show $r
Check '作业负责人没有预审权限（403）' ($r.code -eq 403)
$r = Call POST "/ai/ticket/$tid/review" $null $tZhang
$v = $r.data
Write-Host "  综合风险：$($v.riskLevel)   规则发现 $(@($v.ruleFindings).Count) 个问题：" -ForegroundColor DarkGray
$v.ruleFindings | ForEach-Object { Write-Host "    [$($_.level)] $($_.problem)" -ForegroundColor DarkGray }
if ($v.ai) { Write-Host "  大模型结论：$($v.ai.conclusion)  $($v.ai.summary)" -ForegroundColor DarkGray }
if ($v.aiError) { Write-Host "  大模型失败：$($v.aiError)" -ForegroundColor Yellow }
Check '规则检查发现安全措施缺项（隔离、检测、防护、救援）' (@($v.ruleFindings | Where-Object { $_.item -eq '安全措施' }).Count -eq 4)
Check '发现可燃气体偏高、作业超过 8 小时' ((@($v.ruleFindings | Where-Object { $_.problem -like '*偏高*' }).Count -ge 1) -and (@($v.ruleFindings | Where-Object { $_.level -eq 'LOW' }).Count -ge 1))
Check '引用了企业制度' (@($v.sources).Count -ge 1)
Check '预审不会改变作业票状态' ((Call GET "/ticket/$tid" $null $tLisi).data.ticket.status -eq 'DRAFT')
Call POST "/ticket/$tid/cancel" @{} $tLisi | Out-Null

Title '8. 问答记录（含未命中的问题）'
$logs = (Call GET '/ai/chat/logs?hit=0&size=20' $null $tZhang).data
Check '未命中的问题被记录下来，用于补充知识库' (@($logs.records | Where-Object { $_.question -like '*年假*' }).Count -ge 1)

Title '9. 限流：每人每分钟最多 10 次'
$limited = $false
# 用一个知识库里没有的问题：不会调用大模型，跑得快，不浪费额度
for ($i = 0; $i -lt 25; $i++) {
    $a = Ask '年假怎么申请？' $tAdmin
    if ($a.json -and $a.json.code -eq 429) { $limited = $true; Write-Host "  第 $($i + 1) 次：$($a.json.msg)" -ForegroundColor DarkGray; break }
}
Check '超过次数返回 429' $limited

Title '10. 删除文档后不再引用'
$r = Call DELETE "/ai/knowledge/$docId" $null $tZhang
$a = Ask '氧含量多少才合格？' $tLisi
Check '删除成功，检索不到' ($r.code -eq 200 -and @($a.sources | Where-Object { $_.documentId -eq $docId }).Count -eq 0)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过，AI 模块完成！' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Yellow }
