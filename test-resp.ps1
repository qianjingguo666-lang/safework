# 安全生产责任制自测：权限、岗位职责校验、责任书草稿和发布、本人手写签署、隐私、考核、手机首页、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-resp.ps1

Add-Type -AssemblyName System.Drawing
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

$tZhang = Login 'demo' 'zhangsan'
$tLisi  = Login 'demo' 'lisi'
$tZhao  = Login 'demo' 'zhaoliu'
$tTest  = Login 'test' 'admin'
$lisiId = (Call GET '/auth/me' $null $tLisi).data.user.id
$tag = Get-Random -Minimum 1000 -Maximum 9999
$year = (Get-Date).Year
$deadline = (Get-Date).AddDays(10).ToString('yyyy-MM-dd')
$postId = '9711'

Title '1. 权限'
$r = Call GET '/safety/resp/book/list' $null $tLisi; Show $r
Check '作业负责人不能管理责任书' ($r.code -ne 200)
$r = Call GET '/safety/resp/duty/list' $null $tLisi; Show $r
Check '作业负责人不能看岗位职责管理' ($r.code -ne 200)
$r = Call GET '/safety/resp/mine' $null $tLisi
Check "作业负责人能看自己的责任书（$(@($r.data).Count) 份）" ($r.code -eq 200)

Title '2. 岗位安全职责'
$duty = @((Call GET '/safety/resp/duty/list' $null $tZhang).data | Where-Object { $_.postId -eq $postId })[0]
Check "安全员看到岗位职责：$($duty.postName) $(@($duty.duties).Count) 条，$($duty.memberCount) 人" ($null -ne $duty)
$old = @($duty.duties)
$r = Call PUT "/safety/resp/duty/$postId" '{"duties":[]}' $tZhang; Show $r
Check '职责不能为空' ($r.code -ne 200)
$many = @(1..31 | ForEach-Object { "职责 $_" })
$r = Call PUT "/safety/resp/duty/$postId" @{ duties = $many } $tZhang; Show $r
Check '职责不能超过 30 条' ($r.code -ne 200)
$r = Call PUT "/safety/resp/duty/$postId" @{ duties = @($old + "1. 自测职责 $tag") } $tZhang
$now = @((Call GET '/safety/resp/duty/list' $null $tZhang).data | Where-Object { $_.postId -eq $postId })[0]
Check '保存职责：行首编号“1.”自动去掉' ($r.code -eq 200 -and (@($now.duties) -contains "自测职责 $tag"))

Title '3. 责任书草稿'
$r = Call POST '/safety/resp/book' @{ year = $year + 5; title = "自测 $tag"; content = '通用条款'; deadline = $deadline } $tZhang; Show $r
Check '年度只能是去年、今年、明年' ($r.code -ne 200)
$r = Call POST '/safety/resp/book' @{ year = $year; title = "自测 $tag"; content = '通用条款'; deadline = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd') } $tZhang; Show $r
Check '签署截止日期不能早于今天' ($r.code -ne 200)
$r = Call POST '/safety/resp/book' @{ year = $year; title = "自测删除 $tag"; content = '通用条款'; deadline = $deadline } $tZhang
$r2 = Call DELETE "/safety/resp/book/$($r.data)" $null $tZhang
Check '草稿可以删除' ($r.code -eq 200 -and $r2.code -eq 200)
$r = Call POST '/safety/resp/book' @{ year = $year; title = "自测责任书 $tag"; content = "一、自测通用条款`n二、第二条"; deadline = $deadline } $tZhang
$bookId = $r.data
Check "新建草稿 id = $bookId" ($r.code -eq 200)
$r = Call PUT "/safety/resp/book/$bookId" @{ year = $year; title = "自测责任书 $tag"; content = "一、自测通用条款（已修改）`n二、第二条"; deadline = $deadline } $tZhang
Check '草稿可以修改' ($r.code -eq 200)

Title '4. 发布'
$r = Call POST "/safety/resp/book/$bookId/publish" $null $tZhang; Show $r
Check "发布：$($r.data) 人待签署" ($r.code -eq 200 -and [int]$r.data -ge 2)
$r = Call PUT "/safety/resp/book/$bookId" @{ year = $year; title = '改标题'; content = 'x'; deadline = $deadline } $tZhang; Show $r
Check '发布后不能修改' ($r.code -ne 200)
$r = Call DELETE "/safety/resp/book/$bookId" $null $tZhang; Show $r
Check '发布后不能删除' ($r.code -ne 200)
$r = Call POST "/safety/resp/book/$bookId/publish" $null $tZhang; Show $r
Check '不能重复发布' ($r.code -ne 200)
$r = Call POST "/safety/resp/book/$bookId/sync" $null $tZhang
Check '同步人员：没有新人，补发 0 人' ($r.code -eq 200 -and [int]$r.data -eq 0)
$b = @((Call GET '/safety/resp/book/list' $null $tZhang).data | Where-Object { $_.book.id -eq $bookId })[0]
Check "列表显示进度 $($b.signed)/$($b.total)，状态 $($b.book.status)" ($b.book.status -eq 'PUBLISHED' -and [int]$b.signed -eq 0 -and [int]$b.total -ge 2)

Title '5. 本人签署'
$mine = @((Call GET '/safety/resp/mine' $null $tLisi).data | Where-Object { $_.book.id -eq $bookId })
Check '李四的“我的责任书”里有这份' ($mine.Count -eq 1 -and [int]$mine[0].signed -eq 0)
$signId = (Call GET "/safety/resp/mine/$bookId" $null $tLisi).data
$d = (Call GET "/safety/resp/sign/$signId" $null $tLisi).data
Check "李四打开责任书：可以签署，岗位 $($d.sign.posts)，包含刚加的职责" ($d.canSign -eq $true -and (@($d.duties) -contains "自测职责 $tag"))
$mh = (Call GET '/mobile/home' $null $tZhao).data
Check "赵六手机首页显示待签责任书 $($mh.resp) 份" ([int]$mh.resp -ge 1)
$r = Call GET "/safety/resp/sign/$signId" $null $tZhao; Show $r
Check '赵六看不到李四的责任书' ($r.code -ne 200)
$r = Call POST "/safety/resp/sign/$signId" @{ signature = (Signature) } $tZhao; Show $r
Check '赵六不能替李四签' ($r.code -ne 200)
$r = Call POST "/safety/resp/sign/$signId" @{ signature = (Signature $true) } $tLisi; Show $r
Check '空白签名被拒绝' ($r.code -ne 200)
$r = Call POST "/safety/resp/sign/$signId" @{ signature = 'data:image/jpeg;base64,AAAA' } $tLisi; Show $r
Check '不是 PNG 被拒绝' ($r.code -ne 200)
$r = Call POST "/safety/resp/sign/$signId" @{ signature = (Signature) } $tLisi; Show $r
Check '李四手写签署成功' ($r.code -eq 200)
$r = Call POST "/safety/resp/sign/$signId" @{ signature = (Signature) } $tLisi; Show $r
Check '不能重复签署' ($r.code -ne 200)
$d = (Call GET "/safety/resp/sign/$signId" $null $tLisi).data
Check '签署后：有签名图片、SHA-256、签署时间，不能再签' ($d.signImage -like 'data:image/png;base64,*' -and "$($d.sign.signHash)".Length -eq 64 -and $d.sign.signTime -and $d.canSign -eq $false)
$r = Call GET "/safety/resp/book/$bookId/signs?status=SIGNED" $null $tZhang
$row = @($r.data.records | Where-Object { $_.id -eq $signId })
Check '签署列表能筛出已签的李四，列表不返回签名图片' ($row.Count -eq 1 -and -not $row[0].signImage)
$r = Call GET "/safety/resp/sign/$signId" $null $tZhang
Check '安全员能查看李四签好的责任书' ($r.code -eq 200 -and $r.data.signImage)

Title '6. 年度考核'
$r = Call POST "/safety/resp/sign/$signId/assess" @{ score = 90 } $tLisi; Show $r
Check '李四不能给自己打分' ($r.code -ne 200)
$r = Call GET "/safety/resp/sign/$signId/reference" $null $tZhang
Check "考核参考：上报隐患 $($r.data.hazardsReported)，按期整改 $($r.data.rectifyOnTime)/$($r.data.rectifyTotal)，按时检查 $($r.data.checkOnTime)/$($r.data.checkTotal)，完成作业票 $($r.data.ticketsDone)" ($r.code -eq 200)
$r = Call POST "/safety/resp/sign/$signId/assess" @{ score = 101 } $tZhang; Show $r
Check '分数不能超过 100' ($r.code -ne 200)
$r = Call POST "/safety/resp/sign/$signId/assess" @{ score = 85; comment = '自测考核' } $tZhang
Check "85 分 = $($r.data.grade)" ($r.code -eq 200 -and $r.data.grade -eq '良好')
$r = Call POST "/safety/resp/sign/$signId/assess" @{ score = 92; comment = '自测考核（修改）' } $tZhang
Check "可以修改考核：92 分 = $($r.data.grade)" ($r.code -eq 200 -and $r.data.grade -eq '优秀')
$r = Call GET "/safety/resp/book/$bookId/signs?unassessed=true&size=100" $null $tZhang
Check '“只看没考核的”里没有李四' ($r.code -eq 200 -and (@($r.data.records | Where-Object { $_.id -eq $signId })).Count -eq 0)

Title '7. 结束签署'
$r = Call POST "/safety/resp/book/$bookId/close" $null $tZhang
Check '结束签署' ($r.code -eq 200)
$zSign = (Call GET "/safety/resp/mine/$bookId" $null $tZhao).data
$r = Call POST "/safety/resp/sign/$zSign" @{ signature = (Signature) } $tZhao; Show $r
Check '结束后赵六不能再签' ($r.code -ne 200)

Title '8. 租户隔离'
$r = Call GET '/safety/resp/book/list' $null $tTest
Check '别的企业看不到这份责任书' ($r.code -ne 200 -or (@($r.data | Where-Object { $_.book.id -eq $bookId })).Count -eq 0)
$r = Call GET "/safety/resp/sign/$signId" $null $tTest; Show $r
Check '别的企业打不开李四的责任书' ($r.code -ne 200)

Title '9. 清理'
Call PUT "/safety/resp/duty/$postId" @{ duties = $old } $tZhang | Out-Null
Write-Host '  已恢复罐区操作工的岗位职责；自测责任书已结束签署（保留，demo-reset 后消失）' -ForegroundColor DarkGray

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
