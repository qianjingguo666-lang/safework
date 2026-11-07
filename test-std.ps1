# 安全生产标准化自评自测：权限、考评标准、演示数据、自动取证、打分校验（0.5 分、空项不计分）、完成和等级、转隐患整改、租户隔离、导出
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-std.ps1


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
$tTest  = Login 'test' 'admin'
function Detail($sid, $token) { return (Call GET "/safety/std/$sid" $null $token).data }
function Deducted($d) { return @($d.items | Where-Object { -not [int]$_.item.na -and [double]$_.item.deduct -gt 0 }) }

Title '1. 权限和考评标准'
$r = Call GET '/safety/std/list' $null $tLisi; Show $r
Check '作业负责人不能看标准化自评' ($r.code -ne 200)
$r = Call POST '/safety/std' @{ title = '越权' } $tLisi; Show $r
Check '作业负责人不能新建自评' ($r.code -ne 200)
$std = (Call GET '/safety/std/standard' $null $tZhang).data
$sum = ($std.items | Measure-Object -Property score -Sum).Sum
Check "8 个一级要素、$(@($std.items).Count) 个考评项目，满分 $sum" (@($std.elements).Count -eq 8 -and @($std.items).Count -eq 27 -and [int]$sum -eq 100)

Title '2. 演示企业去年的自评'
$demo = @((Call GET '/safety/std/list' $null $tZhang).data | Where-Object { $_.assessment.status -eq 'DONE' } | Select-Object -Last 1)[0]
Check "有已完成的自评：$($demo.assessment.title) $($demo.assessment.score) 分 $($demo.assessment.grade)" ($null -ne $demo -and $demo.assessment.grade)
$dd = Detail $demo.assessment.id $tZhang
$ded = (Deducted $dd | ForEach-Object { [double]$_.item.deduct } | Measure-Object -Sum).Sum
Check "得分 = 100 - 扣分合计 $ded（没有空项）" ([math]::Abs([double]$dd.score - (100 - $ded)) -lt 0.05)
Check "2 个扣分项已转整改（$((@($dd.items | Where-Object { $_.item.hazardNo }) | ForEach-Object { $_.item.hazardNo }) -join '、')）" (@($dd.items | Where-Object { $_.item.hazardNo }).Count -eq 2)
Check '已完成的自评不能编辑' ($dd.canEdit -eq $false)
$resp = Invoke-WebRequest -Uri "$base/safety/std/$($demo.assessment.id)/export" -Headers @{ Authorization = "Bearer $tZhang" } -UseBasicParsing -TimeoutSec 60
Check "导出扣分汇总 Excel（$($resp.RawContentLength) 字节）" ($resp.StatusCode -eq 200 -and $resp.RawContentLength -gt 1000)

Title '3. 新建自评（测试企业）：自动取证'
foreach ($old in @((Call GET '/safety/std/list' $null $tTest).data | Where-Object { $_.assessment.status -eq 'DRAFT' })) { Call DELETE "/safety/std/$($old.assessment.id)" $null $tTest | Out-Null }
$r = Call POST '/safety/std' @{ title = '自测标准化自评'; members = '自测' } $tTest; Show $r
$sid = $r.data
Check '新建成功' ($r.code -eq 200 -and $sid)
$r2 = Call POST '/safety/std' @{ title = '第二次' } $tTest; Show $r2
Check '同时只能有一次进行中的自评' ($r2.code -ne 200)
$d = Detail $sid $tTest
$auto = @($d.items | Where-Object { [int]$_.item.auto -eq 1 })
$withEvidence = @($auto | Where-Object { @($_.evidence).Count -gt 0 -and $null -ne $_.item.suggested })
Check "27 项，自动取证 $($auto.Count) 项都有证据和建议扣分" (@($d.items).Count -eq 27 -and $withEvidence.Count -eq $auto.Count -and $auto.Count -ge 20)
$prefilled = @($auto | Where-Object { [double]$_.item.deduct -ne [double]$_.item.suggested })
Check '建议扣分已经预先填进扣分' ($prefilled.Count -eq 0)
foreach ($x in (Deducted $d | Select-Object -First 3)) { Write-Host "    $($x.item.code) $($x.item.title)：建议扣 $($x.item.suggested) 分 ← $(@($x.evidence)[0])" -ForegroundColor DarkGray }
Check "测试企业看不到演示企业的自评" (@((Call GET '/safety/std/list' $null $tTest).data | Where-Object { $_.assessment.id -eq $demo.assessment.id }).Count -eq 0)
Check "演示企业的自评 ID 在测试企业里打不开" ((Call GET "/safety/std/$($demo.assessment.id)" $null $tTest).code -ne 200)

Title '4. 打分'
$it = @($d.items | Where-Object { $_.item.code -eq '2.2' })[0]
$r = Call PUT "/safety/std/item/$($it.item.id)" @{ na = $false; deduct = 0.3 } $tTest; Show $r
Check '扣分要按 0.5 分为单位' ($r.code -ne 200)
$r = Call PUT "/safety/std/item/$($it.item.id)" @{ na = $false; deduct = 5 } $tTest; Show $r
Check '扣分不能超过标准分（4 分）' ($r.code -ne 200)
$r = Call PUT "/safety/std/item/$($it.item.id)" @{ na = $false; deduct = 1.5; note = '自测：缺少动火作业管理制度' } $tTest
$na = @($d.items | Where-Object { $_.item.code -eq '4.3' })[0]
Call PUT "/safety/std/item/$($na.item.id)" @{ na = $true; deduct = 0 } $tTest | Out-Null
$d = Detail $sid $tTest
$valid = @($d.items | Where-Object { -not [int]$_.item.na })
$full = ($valid | ForEach-Object { [int]$_.item.standardScore } | Measure-Object -Sum).Sum
$dsum = ($valid | ForEach-Object { [double]$_.item.deduct } | Measure-Object -Sum).Sum
$expect = [math]::Round(($full - $dsum) * 100 / $full, 1)
Check "4.3 设为空项后：得分 = （$full - $dsum）/ $full = $expect（实际 $($d.score)，$($d.grade)）" ([math]::Abs([double]$d.score - $expect) -lt 0.05 -and [int]$full -eq 96)

Title '5. 完成自评'
if ($d.noteMissing -gt 0) {
    $r = Call POST "/safety/std/$sid/finish" @{ summary = '自测' } $tTest; Show $r
    Check "扣了分没写说明不能完成（还差 $($d.noteMissing) 项）" ($r.code -ne 200)
}
foreach ($x in (Deducted $d | Where-Object { -not $_.item.note })) {
    Call PUT "/safety/std/item/$($x.item.id)" @{ na = $false; deduct = $x.item.deduct; note = '自测：系统取证发现的问题' } $tTest | Out-Null
}
$r = Call POST "/safety/std/$sid/finish" @{ summary = '自测结论' } $tTest; Show $r
$f = $r.data
Check "完成：$($f.score) 分，$($f.grade)" ($r.code -eq 200 -and $f.assessment.status -eq 'DONE' -and [math]::Abs([double]$f.score - [double]$d.score) -lt 0.05)
$g = if ($f.score -ge 90 -and $f.dead + $f.serious -eq 0) { '一级' } elseif ($f.score -ge 75 -and $f.dead -eq 0) { '二级' } elseif ($f.score -ge 60 -and $f.dead -le 1) { '三级' } else { '未达标' }
Check "等级按得分和安全绩效计算（$g）" ($f.grade -eq $g)
$r = Call PUT "/safety/std/item/$($it.item.id)" @{ na = $false; deduct = 0 } $tTest; Show $r
Check '完成后不能再改分' ($r.code -ne 200)
$r = Call POST "/safety/std/$sid/refresh" $null $tTest; Show $r
Check '完成后不能重新取数' ($r.code -ne 200)

Title '6. 扣分项转隐患整改'
$r = Call POST "/safety/std/item/$($it.item.id)/hazard" $null $tTest; Show $r
$hid = $r.data.hazardId
Check "生成隐患 $($r.data.hazardNo)" ($r.code -eq 200 -and $r.data.hazardNo)
$h = (Call GET "/safety/hazard/$hid" $null $tTest).data
Check "隐患标题带上自评项目：$($h.hazard.title)" ($h.hazard.title -like '*2.2*')
$r = Call POST "/safety/std/item/$($it.item.id)/hazard" $null $tTest; Show $r
Check '不能重复转整改' ($r.code -ne 200)
$noDeduct = @((Detail $sid $tTest).items | Where-Object { [double]$_.item.deduct -eq 0 -and -not [int]$_.item.na })[0]
$r = Call POST "/safety/std/item/$($noDeduct.item.id)/hazard" $null $tTest; Show $r
Check '没扣分的项目不能转整改' ($r.code -ne 200)
Call POST "/safety/hazard/$hid/revoke" @{ comment = '标准化自评自测，撤销' } $tTest | Out-Null

Title '7. 删除进行中的自评'
$r = Call POST '/safety/std' @{ title = '自测：建了马上删' } $tTest
$r2 = Call DELETE "/safety/std/$($r.data)" $null $tTest
Check '进行中的自评可以删除' ($r2.code -eq 200 -and (Call GET "/safety/std/$($r.data)" $null $tTest).code -ne 200)
$r = Call DELETE "/safety/std/$sid" $null $tTest; Show $r
Check '已完成的不能删除' ($r.code -ne 200)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
