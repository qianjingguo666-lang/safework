# 外来人员与车辆入厂自测：权限、预约校验（手机、身份证校验码、车牌、日期）、身份证打码、签须知入厂和离厂、在厂人数、危化品车辆审批和证件核验、数据范围、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-visit.ps1



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

$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tZhao  = Login 'demo' 'zhaoliu'
$tTest  = Login 'test' 'admin'
$today = (Get-Date).ToString('yyyy-MM-dd')
$sig = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII='
$id = '11010519491231002X'
function V($extra) {
    $b = @{ visitType = 'VISITOR'; visitorName = '自测访客'; phone = '13900000001'; idCard = $id; company = "自测单位 $(Get-Random)"; purpose = '自测'; peopleCount = 2; planDate = $today }
    foreach ($k in $extra.Keys) { $b[$k] = $extra[$k] }
    return $b
}
function Hz($extra) {
    $b = V @{ visitType = 'HAZMAT'; plateNo = '皖A12345'; driverCertNo = 'D123'; driverCertExpire = (Get-Date).AddYears(1).ToString('yyyy-MM-dd');
        escortName = '自测押运员'; escortCertNo = 'E123'; escortCertExpire = (Get-Date).AddYears(1).ToString('yyyy-MM-dd');
        transportPermitNo = 'T123'; transportPermitExpire = (Get-Date).AddYears(1).ToString('yyyy-MM-dd'); cargoName = '甲醇'; cargoUnNo = 'UN1230'; cargoQty = 10 }
    foreach ($k in $extra.Keys) { $b[$k] = $extra[$k] }
    return $b
}
function D($vid, $token) { return (Call GET "/safety/visit/$vid" $null $token).data }

Title '1. 权限'
$r = Call GET '/safety/visit/gate' $null $tLisi; Show $r
Check '作业负责人不能做门岗登记' ($r.code -ne 200)
$r = Call GET '/safety/visit/gate' $null $tZhao
Check "监护人有门岗权限：今天 $(@($r.data).Count) 条（待入厂 + 在厂）" ($r.code -eq 200)

Title '2. 预约校验'
$r = Call POST '/safety/visit' (V @{ phone = '123' }) $tLisi; Show $r
Check '手机号不正确被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/visit' (V @{ idCard = '110105194912310021' }) $tLisi; Show $r
Check '身份证校验码不对被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/visit' (V @{ visitType = 'VEHICLE'; plateNo = '' }) $tLisi; Show $r
Check '车辆不填车牌被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/visit' (V @{ planDate = (Get-Date).AddDays(31).ToString('yyyy-MM-dd') }) $tLisi; Show $r
Check '最多提前 30 天预约' ($r.code -ne 200)

Title '3. 访客：预约 → 签须知入厂 → 离厂'
$vid = (Call POST '/safety/visit' (V @{}) $tLisi).data
$v = D $vid $tLisi
Check "作业负责人能预约，访客直接通过（$($v.pass.status)）" ($v.pass.status -eq 'APPROVED')
Check "身份证号只保存打码的：$($v.pass.idMasked)" ($v.pass.idMasked -eq '110***********002X')
$before = (Call GET '/safety/visit/overview' $null $tZhang).data.onsitePeople
$r = Call POST "/safety/visit/$vid/enter" @{ signImage = '' } $tZhao; Show $r
Check '不签安全须知不能入厂' ($r.code -ne 200)
$r = Call POST "/safety/visit/$vid/enter" @{ signImage = $sig } $tZhao; Show $r
$v = D $vid $tLisi
Check "签名后入厂：在厂，签名已保存（$($v.pass.enterGuard)）" ($r.code -eq 200 -and $v.pass.status -eq 'ENTERED' -and $v.pass.signImage -like 'data:image/png*')
$after = (Call GET '/safety/visit/overview' $null $tZhang).data.onsitePeople
Check "在厂人数 $before → $after（+2）" ([int]$after -eq [int]$before + 2)
Check '在厂清单里有这一条' (@((Call GET '/safety/visit/onsite' $null $tZhao).data | Where-Object { $_.id -eq $vid }).Count -eq 1)
$r = Call PUT "/safety/visit/$vid/cancel" $null $tLisi; Show $r
Check '已入厂不能取消' ($r.code -ne 200)
$r = Call PUT "/safety/visit/$vid/leave" $null $tZhao
Check '离厂登记' ($r.code -eq 200 -and (D $vid $tLisi).pass.status -eq 'LEFT')
$r = Call PUT "/safety/visit/$vid/leave" $null $tZhao; Show $r
Check '不能重复离厂' ($r.code -ne 200)

Title '4. 危化品车辆：审批 → 门岗核对证件'
$hid = (Call POST '/safety/visit' (Hz @{}) $tLisi).data
Check '危化品车辆预约后待审批' ((D $hid $tLisi).pass.status -eq 'PENDING')
$r = Call POST "/safety/visit/$hid/enter" @{ signImage = $sig } $tZhao; Show $r
Check '没审批不能入厂' ($r.code -ne 200)
$r = Call PUT "/safety/visit/$hid/approve" $null $tLisi; Show $r
Check '作业负责人不能审批' ($r.code -ne 200)
$r = Call PUT "/safety/visit/$hid/approve" $null $tZhang
Check '安全员审批通过' ($r.code -eq 200 -and (D $hid $tLisi).pass.status -eq 'APPROVED')
$r = Call POST "/safety/visit/$hid/enter" @{ signImage = $sig } $tZhao
Check "证件齐全，入厂（提示：$($r.data.tip)）" ($r.code -eq 200 -and $r.data.tip)
Call PUT "/safety/visit/$hid/leave" $null $tZhao | Out-Null

$bad = (Call POST '/safety/visit' (Hz @{ driverCertExpire = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd'); escortName = '自测访客' }) $tLisi).data
Call PUT "/safety/visit/$bad/approve" $null $tZhang | Out-Null
$bd = D $bad $tZhao
Check "检查出 $(@($bd.problems).Count) 个问题：$($bd.problems -join '；')" (@($bd.problems).Count -eq 2)
$r = Call POST "/safety/visit/$bad/enter" @{ signImage = $sig } $tZhao; Show $r
Check '驾驶员资格证过期、驾驶员和押运员是同一人，门岗拦下' ($r.code -ne 200)
Call PUT "/safety/visit/$bad/cancel" $null $tLisi | Out-Null

$self = (Call POST '/safety/visit' (Hz @{}) $tZhang).data
$r = Call PUT "/safety/visit/$self/approve" $null $tZhang; Show $r
Check '不能审批自己登记的' ($r.code -ne 200)
$r = Call PUT "/safety/visit/$self/reject" @{ reason = '' } $tZhang; Show $r
Check '驳回要写原因' ($r.code -ne 200)
Call PUT "/safety/visit/$self/cancel" $null $tZhang | Out-Null

Title '5. 日期和数据范围'
$tm = (Call POST '/safety/visit' (V @{ planDate = (Get-Date).AddDays(1).ToString('yyyy-MM-dd') }) $tZhang).data
$r = Call POST "/safety/visit/$tm/enter" @{ signImage = $sig } $tZhao; Show $r
Check '明天的预约今天不能入厂' ($r.code -ne 200)
$r = Call GET "/safety/visit/$tm" $null $tLisi; Show $r
Check '作业负责人看不到别人登记、别人接待的预约' ($r.code -ne 200)
$mine = @((Call GET '/safety/visit/page?size=100' $null $tLisi).data.records | Where-Object { $_.pass.creatorName -notlike '*李四*' -and $_.pass.hostName -notlike '*李四*' })
Check '作业负责人的列表里只有自己登记或接待的' ($mine.Count -eq 0)
Call PUT "/safety/visit/$tm/cancel" $null $tZhang | Out-Null
$r = Call GET "/safety/visit/$vid" $null $tTest
Check '别的企业看不到' ($r.code -ne 200)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
