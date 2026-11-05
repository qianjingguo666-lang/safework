# 劳保用品自测：用品校验、岗位标准生成台账、库存不够一件都不发、入库后批量补发、重新发放旧记录变已更换、手机首页、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-ppe.ps1

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
$lisiId = (Call GET '/auth/me' $null $tLisi).data.user.id
$zhaoId = (Call GET '/auth/me' $null (Login 'demo' 'zhaoliu')).data.user.id
$tag = Get-Random -Minimum 1000 -Maximum 9999

Title '1. 权限和演示数据'
$ov = (Call GET '/safety/ppe/overview' $null $tZhang).data
Check "安全员看统计：岗位 $($ov.people) 人，已过期 $($ov.expired)，未发放 $($ov.missing)，30 天内到期 $($ov.dueSoon)，库存不足 $($ov.lowStock) 种" ([int]$ov.people -ge 1)
$r = Call GET '/safety/ppe/ledger?size=200' $null $tLisi
Check "作业负责人只能看到自己的台账（$($r.data.total) 项）" ($r.code -eq 200 -and (@($r.data.records | Where-Object { $_.userId -ne $lisiId })).Count -eq 0)
$r = Call POST '/safety/ppe/item' @{ name = "作业负责人建的$tag"; category = 'HAND'; unit = '副'; validMonths = 3; minStock = 0 } $tLisi; Show $r
Check '作业负责人不能新增用品（403）' ($r.code -eq 403)

Title '2. 用品和岗位标准'
$r = Call POST '/safety/ppe/item' @{ name = "自测手套$tag"; category = 'HAND'; unit = '副'; validMonths = 0; minStock = 5 } $tZhang; Show $r
Check '使用期限 0 个月被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/ppe/item' @{ name = "自测手套$tag"; category = 'HAND'; spec = '丁腈'; unit = '副'; validMonths = 3; stock = 2; minStock = 5 } $tZhang
$itemId = $r.data
Check '新增用品：使用期限 3 个月，初始库存 2' ($r.code -eq 200 -and $itemId)
$r = Call POST '/safety/ppe/post' @{ name = "自测岗位$tag"; userIds = @($lisiId, $zhaoId); standards = @(@{ itemId = $itemId; quantity = 2 }, @{ itemId = $itemId; quantity = 1 }) } $tZhang; Show $r
Check '同一种用品配两次被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/ppe/post' @{ name = "自测岗位$tag"; userIds = @($lisiId, $zhaoId); standards = @(@{ itemId = $itemId; quantity = 2 }) } $tZhang
$postId = $r.data
Check '新建岗位：李四、赵六，每人 2 副' ($r.code -eq 200 -and $postId)
$rows = @((Call GET "/safety/ppe/ledger?postId=$postId&size=50" $null $tZhang).data.records | Where-Object { $_.itemId -eq $itemId })
Check "台账立即出现 2 项“未发放”" ($rows.Count -eq 2 -and (@($rows | Where-Object { $_.state -eq 'MISSING' })).Count -eq 2)

Title '3. 库存不够一件都不发'
$r = Call POST "/safety/ppe/issue/batch?postId=$postId" $null $tZhang; Show $r
Check '需要 4 副、库存 2 副：拒绝并说明缺多少' ($r.code -ne 200 -and $r.msg -like '*需要 4*库存 2*')
$stock = (@((Call GET '/safety/ppe/item/list' $null $tZhang).data | Where-Object { $_.id -eq $itemId }))[0].stock
Check "库存没有变化（$stock）" ([int]$stock -eq 2)
$r = Call POST "/safety/ppe/item/$itemId/stock-in" @{ quantity = 10 } $tZhang
Check "入库 10 副，现在 $($r.data)" ([int]$r.data -eq 12)

Title '4. 批量补发'
$msg0 = (Call GET '/message/unread' $null $tLisi).data.total
$r = Call POST "/safety/ppe/issue/batch?postId=$postId" $null $tZhang
Check "补发成功：$($r.data.people) 人 $($r.data.records) 项" ($r.code -eq 200 -and [int]$r.data.people -eq 2 -and [int]$r.data.records -eq 2)
$stock = (@((Call GET '/safety/ppe/item/list' $null $tZhang).data | Where-Object { $_.id -eq $itemId }))[0].stock
Check "库存 12 → $stock" ([int]$stock -eq 8)
$rows = @((Call GET "/safety/ppe/ledger?postId=$postId&size=50" $null $tZhang).data.records | Where-Object { $_.itemId -eq $itemId })
$expect = (Get-Date).AddMonths(3).ToString('yyyy-MM-dd')
Check "台账变成“正常”，到期日期 $(@($rows)[0].expireDate)（3 个月后）" ((@($rows | Where-Object { $_.state -eq 'OK' })).Count -eq 2 -and @($rows)[0].expireDate -eq $expect)
$msg1 = (Call GET '/message/unread' $null $tLisi).data.total
Check "领用人收到通知（未读 $msg0 → $msg1）" ([int]$msg1 -ge [int]$msg0 + 1)
$r = Call POST "/safety/ppe/issue/batch?postId=$postId" $null $tZhang; Show $r
Check '再点一次补发：没有需要补发的' ($r.code -ne 200)

Title '5. 重新发放和异常情况'
$r = Call POST '/safety/ppe/issue' @{ userId = $lisiId; items = @(@{ itemId = $itemId; quantity = 2 }); remark = '手套破损更换' } $tZhang
Check '给李四重新发放 2 副' ($r.code -eq 200)
$recs = @((Call GET "/safety/ppe/issue/page?itemId=$itemId&size=20" $null $tZhang).data.records | Where-Object { $_.userId -eq $lisiId })
Check "李四这种手套有 $($recs.Count) 条记录：最新的“在用”，旧的变成“已更换”" ($recs.Count -eq 2 -and (@($recs | Where-Object { $_.status -eq 'IN_USE' })).Count -eq 1 -and (@($recs | Where-Object { $_.status -eq 'REPLACED' })).Count -eq 1)
$r = Call POST '/safety/ppe/issue' @{ userId = $zhaoId; items = @(@{ itemId = $itemId; quantity = 20 }) } $tZhang; Show $r
Check '库存 6 副发 20 副：库存不足' ($r.code -ne 200 -and $r.msg -like '*库存不足*')
$stock = (@((Call GET '/safety/ppe/item/list' $null $tZhang).data | Where-Object { $_.id -eq $itemId }))[0].stock
Check "失败没有扣库存（$stock）" ([int]$stock -eq 6)
$future = (Get-Date).AddDays(3).ToString('yyyy-MM-dd')
$r = Call POST '/safety/ppe/issue' @{ userId = $zhaoId; items = @(@{ itemId = $itemId; quantity = 1 }); issueDate = $future } $tZhang; Show $r
Check '发放日期晚于今天被拒绝' ($r.code -ne 200)
$r = Call PUT "/safety/ppe/item/$itemId/status?status=0" $null $tZhang; Show $r
Check '岗位标准里有它时不能停用' ($r.code -ne 200)

Title '6. 我的劳保和手机首页'
$mine = @((Call GET '/safety/ppe/mine' $null $tLisi).data)
Check "李四的“我的劳保”有 $($mine.Count) 项，包含这种手套" ((@($mine | Where-Object { $_.itemId -eq $itemId })).Count -eq 1)
$mhome = (Call GET '/mobile/home' $null $tLisi).data
Check "手机首页显示我的劳保：需要处理 $($mhome.ppe) 项，其中未领 / 过期 $($mhome.ppeExpired) 项" ($null -ne $mhome.ppe)

Title '7. 租户隔离'
$r = Call GET '/safety/ppe/item/list' $null $tTest
Check '别的企业看不到这个用品' ($r.code -ne 200 -or (@($r.data | Where-Object { $_.id -eq $itemId })).Count -eq 0)
$r = Call GET '/safety/ppe/ledger?size=200' $null $tTest
Check '别的企业的台账里没有演示企业的人' ($r.code -ne 200 -or (@($r.data.records | Where-Object { $_.userId -eq $lisiId })).Count -eq 0)

Title '8. 清理'
Call PUT "/safety/ppe/post/$postId/status?status=0" $null $tZhang | Out-Null
Write-Host '  已停用自测岗位（不再计入台账）' -ForegroundColor DarkGray

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
