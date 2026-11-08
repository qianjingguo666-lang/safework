# 安全风险研判与承诺自测：权限、逐项研判校验（异常要写情况和措施、装置数对得上）、四个方面研判完才能承诺、手写签名、承诺后锁定、公告、历史和按时率、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-commit.ps1
# 注意：一天只能承诺一次。今天已经承诺过的话，研判和签字部分会跳过（明天再测，或者恢复数据库后再测）



$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'

function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 90 }
    if ($null -ne $body) {
        $json = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 6 }
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    $resp = Invoke-WebRequest @params
    $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}
function Login($tenant, $user, $password = 'admin123') {
    $c = (Call GET '/auth/captcha').data
    if ($c.enabled -and -not $c.debugAnswer) { throw '后端没有返回验证码答案：请确认后端是用开发环境（dev）启动的' }
    $r = Call POST '/auth/login' @{ tenantCode = $tenant; username = $user; password = $password; captchaKey = $c.captchaKey; captchaCode = $c.debugAnswer }
    if ($r.code -ne 200) { throw "登录失败 $tenant/$user：$($r.msg)" }
    return $r.data.token
}
function Check($name, $ok) {
    if ($ok) { Write-Host "[通过] $name" -ForegroundColor Green }
    else     { Write-Host "[失败] $name" -ForegroundColor Red; $script:failed++ }
}
function Title($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Show($r) { Write-Host "  -> code = $($r.code), msg = $($r.msg)" -ForegroundColor DarkGray }
$failed = 0

$tAdmin = Login 'demo' 'admin'
$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tZhao  = Login 'demo' 'zhaoliu'
$tTest  = Login 'test' 'admin'
$sig = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII='
function AllOk($aspect) {
    $items = (Call GET '/safety/commit/options' $null $tZhang).data.items | Where-Object { $_.aspect -eq $aspect }
    return ,@($items | ForEach-Object { @{ code = $_.code; ok = $true; note = ''; measure = '' } })
}
function JudgeBody($aspect, $extra) {
    $b = @{ aspect = $aspect; results = (AllOk $aspect) }
    if ($aspect -eq 'PRODUCTION') { $b.unitsTotal = 6; $b.unitsRunning = 5; $b.unitsStopped = 0; $b.unitsOverhaul = 1; $b.trialRun = $false; $b.startupShutdown = $false }
    foreach ($k in $extra.Keys) { $b[$k] = $extra[$k] }
    return $b
}

Title '1. 权限'
$r = Call POST '/safety/commit/judge' (JudgeBody 'SAFETY' @{}) $tZhao; Show $r
Check '监护人只能看，不能研判' ($r.code -ne 200)
$r = Call POST '/safety/commit/sign' @{ signImage = $sig } $tZhang; Show $r
Check '安全员不能代替主要负责人签字承诺' ($r.code -ne 200)
$t = (Call GET '/safety/commit/today' $null $tZhao).data
Check "监护人能看今天的情况：$($t.date)，还差 $(@($t.missing).Count) 个方面" ($null -ne $t)

Title '2. 研判校验'
$r = Call POST '/safety/commit/judge' (JudgeBody 'BOSS' @{ results = (AllOk 'SAFETY') }) $tZhang; Show $r
Check '研判方面不正确' ($r.code -ne 200)
$bad = AllOk 'SAFETY'; $bad = @($bad[0..2])
$r = Call POST '/safety/commit/judge' (JudgeBody 'SAFETY' @{ results = $bad }) $tZhang; Show $r
Check '少一项不行' ($r.code -ne 200)
$bad = AllOk 'SAFETY'; $bad[0].ok = $false; $bad[0].note = '有一张动火票气体检测超时'
$r = Call POST '/safety/commit/judge' (JudgeBody 'SAFETY' @{ results = $bad }) $tZhang; Show $r
Check '异常要写管控措施' ($r.code -ne 200)
$r = Call POST '/safety/commit/judge' (JudgeBody 'PRODUCTION' @{ unitsRunning = 4 }) $tLisi; Show $r
Check '运行 + 停产 + 检修要等于总数' ($r.code -ne 200)

$t = (Call GET '/safety/commit/today' $null $tZhang).data
if ($t.commitment -and $t.commitment.status -eq 'COMMITTED') {
    Write-Host "`n今天已经承诺过了，跳过研判和签字（明天再测或恢复数据库后再测）" -ForegroundColor Yellow
} else {
    Title '3. 研判'
    if (@($t.missing).Count -gt 0) {
        $r = Call POST '/safety/commit/sign' @{ signImage = $sig } $tAdmin; Show $r
        Check '还有方面没研判，不能承诺' ($r.code -ne 200)
    }
    $r = Call POST '/safety/commit/judge' (JudgeBody 'PRODUCTION' @{}) $tLisi
    Check '生产负责人研判（6 套装置：运行 5、检修 1）' ($r.code -eq 200)
    $eq = AllOk 'EQUIPMENT'; $eq[2].ok = $false; $eq[2].note = '自测：罐区 2# 可燃气体探头故障'; $eq[2].measure = '自测：修好前每 2 小时人工检测'
    $r = Call POST '/safety/commit/judge' (JudgeBody 'EQUIPMENT' @{ results = $eq }) $tZhang
    Check '设备负责人研判（1 项异常，写了情况和措施）' ($r.code -eq 200)
    $r = Call POST '/safety/commit/judge' (JudgeBody 'PROCESS' @{}) $tLisi
    Check '工艺负责人研判' ($r.code -eq 200)
    $r = Call POST '/safety/commit/judge' (JudgeBody 'SAFETY' @{}) $tZhang
    Check '安全负责人研判' ($r.code -eq 200)
    $r = Call POST '/safety/commit/judge' (JudgeBody 'SAFETY' @{}) $tZhang
    Check '承诺前可以重新研判（覆盖，不会多一条）' ($r.code -eq 200)
    $t = (Call GET '/safety/commit/today' $null $tZhang).data
    Check "四个方面都研判了，异常 $($t.commitment.abnormalCount) 项；安全员不能签：canSign = $($t.canSign)" (@($t.missing).Count -eq 0 -and $t.commitment.abnormalCount -eq 1 -and -not $t.canSign)
    $ta = (Call GET '/safety/commit/today' $null $tAdmin).data
    Check '主要负责人（企业管理员）可以签字承诺' ($ta.canSign)

    Title '4. 签字承诺'
    $r = Call POST '/safety/commit/sign' @{ signImage = 'data:image/png;base64,abc' } $tAdmin; Show $r
    Check '签名图片不正确不行' ($r.code -ne 200)
    $r = Call POST '/safety/commit/sign' @{ signImage = $sig } $tAdmin
    Check '签字承诺' ($r.code -eq 200)
    $t = (Call GET '/safety/commit/today' $null $tZhang).data
    Check "承诺内容：$($t.commitment.content.Substring(0, 30))..." ($t.commitment.status -eq 'COMMITTED' -and $t.commitment.content -like '*发现 1 项异常*')
    $r = Call POST '/safety/commit/judge' (JudgeBody 'SAFETY' @{}) $tZhang; Show $r
    Check '承诺后不能再改研判' ($r.code -ne 200)
    $r = Call POST '/safety/commit/sign' @{ signImage = $sig } $tAdmin; Show $r
    Check '不能重复承诺' ($r.code -ne 200)
}

Title '5. 公告和历史'
$b = (Call GET '/safety/commit/board' $null $tZhao).data
Check "最近一次公告：$($b.commitment.commitDate)，有签名，动火作业 $($b.stats.hotWork) 张、重大危险源 $($b.stats.majorSources) 个" ($b.commitment.status -eq 'COMMITTED' -and $b.signImage -like 'data:image/png;base64,*' -and $null -ne $b.stats)
$abn = @($b.aspects | ForEach-Object { $_.items } | Where-Object { $_.ok -eq $false })
Check "公告里列出异常项和措施（$($abn.Count) 项）" ($abn.Count -eq $b.commitment.abnormalCount)
$h = (Call GET '/safety/commit/history' $null $tZhang).data
Check "本月 $(@($h.rows).Count) 天记录，按时承诺 $($h.onTimeDays) / $($h.days) 天，按时率 $($h.rate)%" (@($h.rows).Count -eq (Get-Date).Day)
$r = Call GET '/safety/commit/history?month=2026-13' $null $tZhang; Show $r
Check '月份格式不正确' ($r.code -ne 200)
$r = Call GET "/safety/commit/board?date=$($b.commitment.commitDate)" $null $tTest; Show $r
Check '别的企业看不到这家的公告' ($r.code -ne 200 -or "$($r.data.commitment.id)" -ne "$($b.commitment.id)")

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
