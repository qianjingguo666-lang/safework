# 危险化学品自测：权限、CAS 校验、存储单元、重大危险源辨识与分级（GB 18218）、出入库（负责人权限、不能超出库存、超储提醒）、MSDS 权限、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-chem.ps1

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
$lisiId = (Call GET '/auth/me' $null $tLisi).data.user.id
$tag = Get-Random -Minimum 1000 -Maximum 9999

Title '1. 演示数据的重大危险源辨识'
$stores = @((Call GET '/safety/chem/store/list' $null $tZhang).data)
$tank = @($stores | Where-Object { $_.store.name -eq '罐区' })[0]
if ($tank) {
    Check "罐区：S = $($tank.design.sum)，α = $($tank.design.alpha)，R = $($tank.design.r)，等级 $($tank.design.level)（应为 12 / 2 / 24 / 三级）" ([double]$tank.design.sum -eq 12 -and [double]$tank.design.r -eq 24 -and [int]$tank.design.level -eq 3)
} else { Write-Host '  （没有演示罐区，跳过）' -ForegroundColor DarkGray }
$ware = @($stores | Where-Object { $_.store.name -eq '危化品仓库' })[0]
if ($ware) {
    Check "危化品仓库：S = $($ware.design.sum) 恰好等于 1 也构成重大危险源，R = $($ware.design.r)，四级" ($ware.design.major -eq $true -and [int]$ware.design.level -eq 4)
}
$ov = (Call GET '/safety/chem/overview' $null $tZhang).data
Check "统计：$($ov.chemicals) 种危化品，$($ov.stores) 个单元，重大危险源 $($ov.major) 处，超储 $($ov.overstock) 项，缺 MSDS $($ov.noMsds) 种" ([int]$ov.chemicals -ge 1)

Title '2. 权限'
$r = Call GET '/safety/chem/item/list' $null $tZhao
Check '监护人能查危化品目录' ($r.code -eq 200)
$r = Call POST '/safety/chem/store' @{ name = "自测单元 $tag"; storeType = 'WAREHOUSE'; location = '自测'; people = 0; managerId = $lisiId } $tLisi; Show $r
Check '作业负责人不能新增存储单元' ($r.code -ne 200)

Title '3. 目录校验'
$r = Call POST '/safety/chem/item' @{ name = '自测'; cas = '67-56-2'; category = 'FLAMMABLE_LIQUID' } $tZhang; Show $r
Check 'CAS 号校验位不对被拒绝' ($r.code -ne 200)
$r = Call POST '/safety/chem/item' @{ name = '自测'; cas = '67-56-1'; category = 'FLAMMABLE_LIQUID' } $tZhang; Show $r
Check 'CAS 号重复被拒绝（甲醇已经在目录里）' ($r.code -ne 200)
$r = Call POST '/safety/chem/item' @{ name = '自测'; cas = '64-17-5'; category = 'ALIEN' } $tZhang; Show $r
Check '危险类别不正确被拒绝' ($r.code -ne 200)
$methanol = @((Call GET '/safety/chem/item/list?keyword=67-56-1' $null $tZhang).data)[0].item
Check "找到甲醇：临界量 $($methanol.criticalQty) 吨" ($null -ne $methanol)

Title '4. 存储单元和辨识'
$r = Call POST '/safety/chem/store' @{ name = "自测单元 $tag"; storeType = 'WAREHOUSE'; location = '自测'; people = 0; managerId = $lisiId } $tZhang
$storeId = $r.data
Check "新增存储单元，负责人李四 id = $storeId" ($r.code -eq 200)
$r = Call POST "/safety/chem/store/$storeId/stock" @{ chemId = $methanol.id; maxQty = 0 } $tZhang; Show $r
Check '最大储量必须大于 0' ($r.code -ne 200)
$r = Call POST "/safety/chem/store/$storeId/stock" @{ chemId = $methanol.id; maxQty = 400 } $tZhang
$stockId = $r.data
$s = (Call GET "/safety/chem/store/$storeId" $null $tZhang).data
Check "甲醇最大储量 400 吨：S = $($s.design.sum) < 1，不构成重大危险源" ($r.code -eq 200 -and $s.design.major -eq $false -and [double]$s.design.sum -eq 0.8)
$r = Call POST "/safety/chem/store/$storeId/stock" @{ chemId = $methanol.id; maxQty = 600 } $tZhang
$s = (Call GET "/safety/chem/store/$storeId" $null $tZhang).data
Check "改成 600 吨：S = $($s.design.sum) 构成；500 m 内 0 人 α = $($s.design.alpha)，R = $($s.design.r)，四级" ($s.design.major -eq $true -and [double]$s.design.alpha -eq 0.5 -and [double]$s.design.r -eq 0.6 -and [int]$s.design.level -eq 4)
$r = Call PUT "/safety/chem/store/$storeId" @{ name = "自测单元 $tag"; storeType = 'WAREHOUSE'; location = '自测'; people = 150; managerId = $lisiId } $tZhang
$s = (Call GET "/safety/chem/store/$storeId" $null $tZhang).data
Check "500 m 内改成 150 人：α = $($s.design.alpha)，R = $($s.design.r)" ([double]$s.design.alpha -eq 2 -and [double]$s.design.r -eq 2.4)

Title '5. 出入库'
$r = Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'IN'; qty = 10 } $tZhao; Show $r
Check '赵六（不是负责人）不能办理' ($r.code -ne 200)
$r = Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'IN'; qty = 0 } $tLisi; Show $r
Check '数量必须大于 0' ($r.code -ne 200)
$r = Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'IN'; qty = 10; purpose = '自测入库' } $tLisi
Check "李四（负责人）入库 10 吨，库存 $($r.data.balance)" ($r.code -eq 200 -and [double]$r.data.balance -eq 10)
$r = Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'OUT'; qty = 4 } $tLisi; Show $r
Check '出库必须写用途' ($r.code -ne 200)
$r = Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'OUT'; qty = 15; purpose = '自测' } $tLisi; Show $r
Check '出库不能超过库存' ($r.code -ne 200)
$r = Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'OUT'; qty = 4.25; purpose = '自测领用' } $tLisi
Check "出库 4.25 吨，库存 $($r.data.balance)" ($r.code -eq 200 -and [double]$r.data.balance -eq 5.75)
$r = Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'CHECK'; qty = 5.5; purpose = '自测盘点' } $tLisi
Check "盘点为 5.5 吨" ($r.code -eq 200 -and [double]$r.data.balance -eq 5.5)
$r = Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'IN'; qty = 600; purpose = '自测超储' } $tZhang
Check "入库后 $($r.data.balance) 吨超过最大储量 600：照样入库，但标出超储" ($r.code -eq 200 -and $r.data.overstock -eq $true)
$s = (Call GET "/safety/chem/store/$storeId" $null $tZhang).data
Check '单元显示 1 项超储' ([int]$s.overstock -eq 1)
$f = (Call GET "/safety/chem/flow/page?storeId=$storeId&size=50" $null $tZhang).data
$chk = @($f.records | Where-Object { $_.flowType -eq 'CHECK' })[0]
Check "流水 $($f.total) 条，盘点记录的是差值 $($chk.qty)" ([int]$f.total -eq 4 -and [double]$chk.qty -eq -0.25)
$r = Call PUT "/safety/chem/store/$storeId/status?status=0" $null $tZhang; Show $r
Check '有库存的单元不能停用' ($r.code -ne 200)
$r = Call DELETE "/safety/chem/stock/$stockId" $null $tZhang; Show $r
Check '有库存不能从单元去掉' ($r.code -ne 200)
$mine = @((Call GET '/safety/chem/store/mine' $null $tLisi).data | Where-Object { $_.store.id -eq $storeId })
Check '手机端“危化品出入库”里有李四负责的单元' ($mine.Count -eq 1 -and $mine[0].canFlow -eq $true)
$mh = (Call GET '/mobile/home' $null $tLisi).data
Check "李四手机首页显示超储 $($mh.chem) 项" ([int]$mh.chem -ge 1)

Title '6. MSDS 附件权限'
$r = Call GET "/file/list?bizType=CHEM&bizId=$($methanol.id)&tag=MSDS" $null $tZhao
Check '监护人能看 MSDS，但不能上传' ($r.code -eq 200 -and $r.data.editable -eq $false)
$r = Call GET "/file/list?bizType=CHEM&bizId=$($methanol.id)&tag=MSDS" $null $tZhang
Check '安全员能上传 MSDS' ($r.code -eq 200 -and $r.data.editable -eq $true)

Title '7. 租户隔离'
$r = Call GET '/safety/chem/store/list' $null $tTest
Check '别的企业看不到这个单元' ($r.code -ne 200 -or (@($r.data | Where-Object { $_.store.id -eq $storeId })).Count -eq 0)
$r = Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'OUT'; qty = 1; purpose = '越权' } $tTest; Show $r
Check '别的企业不能给这条库存出库' ($r.code -ne 200)

Title '8. 清理'
Call POST "/safety/chem/stock/$stockId/flow" @{ flowType = 'CHECK'; qty = 0; purpose = '自测清理' } $tZhang | Out-Null
$r1 = Call DELETE "/safety/chem/stock/$stockId" $null $tZhang
$r2 = Call PUT "/safety/chem/store/$storeId/status?status=0" $null $tZhang
Check '盘点为 0 后可以去掉危化品、停用单元' ($r1.code -eq 200 -and $r2.code -eq 200)

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
