# 安全培训考试自测：题目校验、组卷规则、随机试卷不泄露答案、自动保存、阅卷、补考次数、权限、租户隔离、统计导出、AI 出题
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-training.ps1
# 最后一部分（AI 出题）需要配置大模型，没配置会跳过

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

$ts = Get-Date -Format 'MMddHHmmss'
$cat = "自测$ts"
function Fmt2($d) { $d.ToString('yyyy-MM-dd HH:mm:ss') }

$tAdmin = Login 'demo' 'admin'
$tZhao  = Login 'demo' 'zhaoliu'
$tLisi  = Login 'demo' 'lisi'
$tTest  = Login 'test' 'admin'

Title '1. 题目校验'
$opts = @("甲$ts", "乙$ts", "丙$ts", "丁$ts")
$r = Call POST '/training/question' @{ type = 'SINGLE'; category = $cat; content = "单选两个答案 $ts"; options = $opts; answer = 'AB' } $tAdmin
Show $r
Check '单选题不能有两个答案' ($r.code -eq 400)
$r = Call POST '/training/question' @{ type = 'MULTI'; category = $cat; content = "多选一个答案 $ts"; options = $opts; answer = 'A' } $tAdmin
Show $r
Check '多选题至少两个答案' ($r.code -eq 400)
$r = Call POST '/training/question' @{ type = 'SINGLE'; category = $cat; content = "答案超范围 $ts"; options = $opts; answer = 'E' } $tAdmin
Show $r
Check '答案字母超出选项范围被拒绝' ($r.code -eq 400)
$r = Call GET '/training/question/page' $null $tZhao
Check '监护人不能查看题库（403）' ($r.code -eq 403)

# 题库：3 道单选、1 道多选、2 道判断；记下每道题的正确选项内容，答题时用来找出打乱后的字母
$bank = @{}
function AddQ($type, $content, $options, $answer) {
    $body = @{ type = $type; category = $cat; content = $content; answer = $answer; analysis = "解析 $content" }
    if ($options) { $body.options = $options }
    $r = Call POST '/training/question' $body $tAdmin
    if ($r.code -ne 200) { throw "新增题目失败：$($r.msg)" }
    $script:bank[$content] = @{ type = $type; options = $options; answer = $answer }
}
$singleAnswers = @('B', 'C', 'D')
for ($i = 1; $i -le 3; $i++) {
    $ans = $singleAnswers[$i - 1]
    AddQ 'SINGLE' "单选题 $i（$ts）" @("单$i-A", "单$i-B", "单$i-C", "单$i-D") $ans
}
AddQ 'MULTI' "多选题（$ts）" @('多-A', '多-B', '多-C', '多-D') 'AC'
AddQ 'JUDGE' "判断题 1（$ts）" $null 'A'
AddQ 'JUDGE' "判断题 2（$ts）" $null 'B'
$count = (Call GET "/training/exam/question-count?category=$([Uri]::EscapeDataString($cat))" $null $tAdmin).data
Check "题库按题型统计：单选 $($count.SINGLE)、多选 $($count.MULTI)、判断 $($count.JUDGE)" ($count.SINGLE -eq 3 -and $count.MULTI -eq 1 -and $count.JUDGE -eq 2)

Title '2. 建考试、发布'
$now = Get-Date
$examBody = @{ title = "自测考试 $ts"; category = $cat; singleCount = 3; singleScore = 10; multiCount = 1; multiScore = 20; judgeCount = 2; judgeScore = 10
    passScore = 50; durationMinutes = 10; maxAttempts = 2; startTime = (Fmt2 $now.AddMinutes(-1)); endTime = (Fmt2 $now.AddHours(1)); showAnswer = $true }
$lack = $examBody.Clone(); $lack.title = "题目不够 $ts"; $lack.singleCount = 4
$r = Call POST '/training/exam' $lack $tAdmin
$r = Call POST "/training/exam/$($r.data)/publish" $null $tAdmin
Show $r
Check '题库不够时不能发布，提示缺几道' ($r.code -eq 400 -and $r.msg -like '*需要 4 道*')
$bad = $examBody.Clone(); $bad.passScore = 80
$r = Call POST '/training/exam' $bad $tAdmin
Show $r
Check '及格分不能高于总分（70）' ($r.code -eq 400)
$r = Call POST '/training/exam' $examBody $tAdmin
$examId = $r.data
Check '新建考试（草稿）' ($r.code -eq 200)
$r = Call POST "/training/exam/$examId/publish" $null $tAdmin
Show $r
Check "发布成功，通知了 $($r.data) 人" ($r.code -eq 200 -and $r.data -ge 3)
$r = Call PUT "/training/exam/$examId" $examBody $tAdmin
Check '已发布的考试不能修改' ($r.code -eq 400)
$msg = @((Call GET '/message/page?size=50' $null $tZhao).data.records | Where-Object { $_.bizId -eq $examId })
Check '监护人收到【新考试】待办消息' ($msg.Count -ge 1 -and $msg[0].title -like '新考试*')

Title '3. 监护人答题'
$mine = @((Call GET '/training/my/exams' $null $tZhao).data | Where-Object { $_.examId -eq $examId })
Check '我的考试里能看到，状态是待参加' ($mine.Count -eq 1 -and $mine[0].state -eq 'TODO' -and $mine[0].canStart)
$r = Call POST "/training/my/exams/$examId/start" $null $tZhao
$paper = $r.data
Check "开始考试：随机组卷 $(@($paper.items).Count) 道题，满分 $($paper.totalScore)" (@($paper.items).Count -eq 6 -and $paper.totalScore -eq 70)
$leak = @($paper.items | Where-Object { $_.PSObject.Properties.Name -contains 'answer' -or $_.PSObject.Properties.Name -contains 'analysis' })
Check '试卷里没有正确答案和解析（防作弊）' ($leak.Count -eq 0)
$r2 = Call POST "/training/my/exams/$examId/start" $null $tZhao
Check '再点开始考试：继续原来那张卷，不会重新组卷' ($r2.data.recordId -eq $paper.recordId)

# 根据题库里的正确选项内容，找出打乱顺序后对应的字母
function CorrectOf($item) {
    $q = $bank[$item.content]
    if ($item.type -eq 'JUDGE') { return $q.answer }
    $texts = @($q.answer.ToCharArray() | ForEach-Object { $q.options[[int]$_ - 65] })
    $letters = ''
    for ($i = 0; $i -lt @($item.options).Count; $i++) {
        if ($texts -contains $item.options[$i]) { $letters += [char](65 + $i) }
    }
    return $letters
}
$shuffled = @($paper.items | Where-Object { $_.type -eq 'SINGLE' -and (CorrectOf $_) -ne $bank[$_.content].answer }).Count
Write-Host "  有 $shuffled 道单选题的正确答案字母和题库里不一样（选项被打乱了）" -ForegroundColor DarkGray
$answers = @{}
foreach ($item in $paper.items) { $answers["$($item.index)"] = CorrectOf $item }

$first = @{ '1' = $answers['1'] }
$r = Call PUT "/training/my/record/$($paper.recordId)/answers" @{ answers = $first } $tZhao
$again = (Call GET "/training/my/record/$($paper.recordId)/paper" $null $tZhao).data
Check '自动保存答案：重新打开试卷能看到已答的题' ($r.code -eq 200 -and $again.answers.'1' -eq $answers['1'])
$r = Call GET "/training/my/record/$($paper.recordId)/result" $null $tLisi
Check '别人的答卷看不到（404）' ($r.code -eq 404)

$r = Call POST "/training/my/record/$($paper.recordId)/submit" @{ answers = $answers } $tZhao
$res = $r.data
Show $r
Check "交卷自动阅卷：$($res.score) 分，答对 $($res.correctCount) 题" ($res.score -eq 70 -and $res.passed -and $res.correctCount -eq 6)
Check '交卷后显示正确答案和解析' ($null -ne $res.items[0].answer -and $null -ne $res.items[0].analysis)
$r = Call PUT "/training/my/record/$($paper.recordId)/answers" @{ answers = $first } $tZhao
Check '交卷后不能再改答案（409）' ($r.code -eq 409)
$mine = @((Call GET '/training/my/exams' $null $tZhao).data | Where-Object { $_.examId -eq $examId })
Check '状态变成已通过，不能再考' ($mine[0].state -eq 'PASSED' -and -not $mine[0].canStart -and $mine[0].bestScore -eq 70)
$r = Call POST "/training/my/exams/$examId/start" $null $tZhao
Check '通过后再点开始被拒绝' ($r.code -eq 400)

Title '4. 补考次数'
$p1 = (Call POST "/training/my/exams/$examId/start" $null $tLisi).data
$r1 = (Call POST "/training/my/record/$($p1.recordId)/submit" @{ answers = @{} } $tLisi).data
Check '李四第 1 次交白卷：0 分，未通过' ($r1.score -eq 0 -and -not $r1.passed)
$mine = @((Call GET '/training/my/exams' $null $tLisi).data | Where-Object { $_.examId -eq $examId })
Check '状态：未通过，可补考' ($mine[0].state -eq 'RETRY' -and $mine[0].canStart)
$p2 = (Call POST "/training/my/exams/$examId/start" $null $tLisi).data
Check '第 2 次重新组卷（新答卷）' ($p2.attemptNo -eq 2 -and $p2.recordId -ne $p1.recordId)
$half = @{}
foreach ($item in $p2.items) { if ($item.type -eq 'SINGLE') { $half["$($item.index)"] = CorrectOf $item } }
$r2 = (Call POST "/training/my/record/$($p2.recordId)/submit" @{ answers = $half } $tLisi).data
Check "第 2 次只答对 3 道单选：$($r2.score) 分，没到 50 分及格线" ($r2.score -eq 30 -and -not $r2.passed)
$r = Call POST "/training/my/exams/$examId/start" $null $tLisi
Show $r
Check '次数用完后不能再考' ($r.code -eq 400 -and $r.msg -like '*次数已用完*')

Title '5. 成绩统计、导出'
$st = (Call GET "/training/exam/$examId/stats" $null $tAdmin).data
Write-Host "  应参加 $($st.targetUsers) 人，已交卷 $($st.submittedUsers) 人，通过 $($st.passedUsers) 人，通过率 $($st.passRate)%，平均分 $($st.avgScore)" -ForegroundColor DarkGray
Check '每人取最高分：2 人交卷，1 人通过，通过率 50%' ($st.submittedUsers -eq 2 -and $st.passedUsers -eq 1 -and $st.passRate -eq 50)
Check '每人取最高分算平均：（70 + 30）/ 2 = 50' ($st.avgScore -eq 50)
Check '列出了正确率最低的题' (@($st.hardest).Count -ge 1)
Check "未交卷名单 $(@($st.notTaken).Count) 人" (@($st.notTaken).Count -eq $st.targetUsers - 2)
$recs = (Call GET "/training/exam/$examId/records" $null $tAdmin).data
Check '答卷列表：3 份（李四考了 2 次）' ($recs.total -eq 3)
$d = Download "/training/exam/$examId/export" $tAdmin
Check '导出成绩 Excel' ($d.ok -and $d.bytes.Length -gt 1000 -and $d.bytes[0] -eq 0x50 -and $d.bytes[1] -eq 0x4B)
$r = Call GET "/training/exam/$examId/stats" $null $tZhao
Check '监护人不能看统计（403）' ($r.code -eq 403)

Title '6. 租户隔离、结束考试'
$r = Call GET "/training/exam/$examId" $null $tTest
Check '测试冶金看不到演示化工的考试' ($r.code -eq 404)
$r = Call POST "/training/my/exams/$examId/start" $null $tTest
Check '测试冶金不能参加演示化工的考试' ($r.code -eq 404)
$r = Call POST "/training/exam/$examId/close" $null $tAdmin
Check '结束考试' ($r.code -eq 200)
$tZhang = Login 'demo' 'zhangsan'
$r = Call POST "/training/my/exams/$examId/start" $null $tZhang
Check '结束后不能再开始' ($r.code -eq 400)

Title '7. AI 出题（需要大模型）'
$status = (Call GET '/ai/status' $null $tAdmin).data
$docs = @((Call GET '/ai/knowledge/page?size=50' $null $tAdmin).data.records | Where-Object { $_.status -eq 'READY' })
if (-not $status.chatEnabled) {
    Write-Host '  没有配置大模型，跳过' -ForegroundColor Yellow
} elseif ($docs.Count -eq 0) {
    Write-Host '  知识库里没有文件，跳过（先运行 test-ai.ps1 或在页面上传制度文件）' -ForegroundColor Yellow
} else {
    $aiCat = "AI$ts"
    Write-Host "  根据《$($docs[0].title)》出题，大约需要 20~60 秒…" -ForegroundColor DarkGray
    $params = @{ Uri = "$base/ai/question/generate"; Method = 'POST'; UseBasicParsing = $true; TimeoutSec = 180
        Headers = @{ Authorization = "Bearer $tAdmin" }; ContentType = 'application/json; charset=utf-8'
        Body = [Text.Encoding]::UTF8.GetBytes((@{ documentId = $docs[0].id; category = $aiCat; singleCount = 2; multiCount = 1; judgeCount = 2 } | ConvertTo-Json)) }
    $r = [Text.Encoding]::UTF8.GetString((Invoke-WebRequest @params).RawContentStream.ToArray()) | ConvertFrom-Json
    Show $r
    if ($r.code -eq 200) {
        Write-Host "  生成 $($r.data.generated) 道，入库 $($r.data.saved) 道，用时 $([math]::Round($r.data.costMs / 1000, 1)) 秒" -ForegroundColor DarkGray
        foreach ($s in $r.data.skipped) { Write-Host "  跳过：$($s.content) → $($s.reason)" -ForegroundColor DarkGray }
    }
    Check 'AI 出题成功，至少入库 3 道' ($r.code -eq 200 -and $r.data.saved -ge 3)
    $pending = (Call GET "/training/question/page?size=50&status=0&category=$([Uri]::EscapeDataString($aiCat))" $null $tAdmin).data
    foreach ($q in @($pending.records)) { Write-Host "  [$($q.typeLabel)] $($q.content)  答案：$($q.answer)" -ForegroundColor DarkGray }
    Check 'AI 生成的题是【待审核】状态' ($pending.total -eq $r.data.saved)
    $cnt = (Call GET "/training/exam/question-count?category=$([Uri]::EscapeDataString($aiCat))" $null $tAdmin).data
    Check '审核前不能用于组卷（可用题目为 0）' ([int]$cnt.SINGLE + [int]$cnt.MULTI + [int]$cnt.JUDGE -eq 0)
    $ids = @($pending.records | ForEach-Object { $_.id })
    $r = Call POST '/training/question/approve' @{ ids = $ids } $tAdmin
    $cnt = (Call GET "/training/exam/question-count?category=$([Uri]::EscapeDataString($aiCat))" $null $tAdmin).data
    Check "审核通过 $($r.data) 道后可以用于组卷" ($r.data -eq $ids.Count -and ([int]$cnt.SINGLE + [int]$cnt.MULTI + [int]$cnt.JUDGE) -eq $ids.Count)
    $logs = (Call GET '/ai/chat/logs?bizType=QUESTION&size=1' $null $tAdmin).data
    Check '出题记录在问答记录里（类型：AI 出题）' ($logs.total -ge 1)
}

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green }
else { Write-Host "有 $failed 项失败，把上面的输出发给 Claude" -ForegroundColor Red }
