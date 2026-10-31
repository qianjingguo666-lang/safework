# 承包商管理自测：准入审核状态机、人员登记校验、作业票进场校验（培训、持证、资质保险、待审核）、违章扣分自动拉黑、权限、租户隔离
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\test-contractor.ps1

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
# 登录要先取验证码：开发环境（dev）后端会把答案放在 debugAnswer 里返回，脚本直接用它
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
function Fmt($d) { $d.ToString('yyyy-MM-dd HH:mm:ss') }
function Day($d) { $d.ToString('yyyy-MM-dd') }
function MsgsOf($token, $bizId) { ,@((Call GET '/message/page?size=100' $null $token).data.records | Where-Object { "$($_.bizId)" -eq "$bizId" }) }
function Detail($id, $token) { (Call GET "/safety/contractor/$id" $null $token).data }
# 随机统一社会信用代码（18 位，不含 I O S V Z）
function NewCode() { $chars = '0123456789ABCDEFGHJKLMNPQRTUWXY'; '91340100MA' + (-join (1..8 | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })) }
function HotWork($title, $contractorId, $workerIds, $hours = 4) {
    $now = Get-Date
    return @{ ticketType = 'HOT_WORK'; title = $title; location = '合成车间二楼'; workContent = '管道焊接'; safetyMeasures = '清理周边可燃物，配备灭火器'
              planStartTime = (Fmt $now); planEndTime = (Fmt $now.AddHours($hours)); guardianId = 104
              gasOxygen = 20.9; gasLel = 0.5; gasH2s = 0; gasCheckTime = (Fmt $now)
              contractorId = $contractorId; contractorWorkerIds = @($workerIds) }
}
$failed = 0
$today = Get-Date
$suffix = Get-Random -Minimum 1000 -Maximum 9999

$tZhang = Login 'demo' 'zhangsan'   # 安全员：管理承包商
$tLisi  = Login 'demo' 'lisi'       # 作业负责人：查看承包商，申请作业票
$tZhao  = Login 'demo' 'zhaoliu'    # 监护人：没有承包商菜单
$tTest  = Login 'test' 'admin'

Title '1. 演示数据和权限'
$ov = (Call GET '/safety/contractor/overview' $null $tZhang).data
Check "合格 $($ov.approved)、待审核 $($ov.pending)、黑名单 $($ov.blacklisted)、资质保险快到期 $($ov.expiring)、人员培训快到期 $($ov.workerWarnings)" ($ov.approved -ge 2 -and $ov.pending -ge 1 -and $ov.blacklisted -ge 1 -and $ov.workerWarnings -ge 2)
$all = @((Call GET '/safety/contractor/page?size=50' $null $tZhang).data.records)
$anda = $all | Where-Object { $_.creditCode -eq '91340100MA2N0A1B2C' }
$hengtong = $all | Where-Object { $_.creditCode -eq '91340100MA2P3C4D5E' }
$xinyuan = $all | Where-Object { $_.creditCode -eq '91340100MA2R6F7G8H' }
$suda = $all | Where-Object { $_.creditCode -eq '91340100MA2T9J0K1L' }
Check "安全评分按近 12 个月违章扣分：安达 $($anda.score)、恒通 $($hengtong.score)、速达 $($suda.score)" ($anda.score -eq 95 -and $hengtong.score -eq 80 -and $suda.score -eq 55)
Check "恒通的责任险快到期有提示：$($hengtong.warnings -join '、')" ((@($hengtong.warnings) -match '责任险').Count -ge 1)
$d = Detail $hengtong.id $tZhang
$chen = @($d.workers | Where-Object { $_.name -eq '陈刚' })[0]
Check "人员培训过期有提示：陈刚 $($chen.problems -join '、')" ((@($chen.problems) -match '已过期').Count -ge 1)
$r = Call GET '/safety/contractor/page' $null $tLisi
Check '作业负责人可以查看承包商' ($r.code -eq 200)
$r = Call POST '/safety/contractor' @{ name = 'x' } $tLisi; Show $r
Check '作业负责人不能登记承包商' ($r.code -eq 403)
$r = Call GET '/safety/contractor/page' $null $tZhao; Show $r
Check '监护人没有承包商菜单' ($r.code -eq 403)
Check '作业负责人在承包商详情里看不到审核按钮' (@((Detail $anda.id $tLisi).actions).Count -eq 0)

Title '2. 准入审核'
$code = NewCode
$info = @{ name = "自测检修有限公司$suffix"; creditCode = $code; contactName = '测试'; contactPhone = '13900000000'; businessScope = '管道焊接'
           licenseExpire = (Day $today.AddYears(2)); insuranceExpire = (Day $today.AddYears(1)) }
$r = Call POST '/safety/contractor' $info $tZhang
Check '登记承包商' ($r.code -eq 200)
$cid = $r.data
Check "新登记的状态是待审核：$((Detail $cid $tZhang).contractor.status)" ((Detail $cid $tZhang).contractor.status -eq 'PENDING')
$r = Call POST '/safety/contractor' $info $tZhang; Show $r
Check '名称、信用代码不能重复' ($r.code -ne 200)
$bad = $info.Clone(); $bad.name = "自测B$suffix"; $bad.creditCode = '12345'
$r = Call POST '/safety/contractor' $bad $tZhang; Show $r
Check '信用代码格式校验' ($r.code -ne 200)
$opts = @((Call GET '/safety/contractor/options' $null $tLisi).data)
Check '待审核的承包商不出现在作业票的选择列表里' (-not ($opts | Where-Object { "$($_.id)" -eq "$cid" }))
$r = Call POST "/safety/contractor/$cid/status/reject" @{} $tZhang; Show $r
Check '驳回必须写原因' ($r.code -ne 200)
$r = Call POST "/safety/contractor/$cid/status/reject" @{ comment = '缺少安全生产许可证复印件' } $tZhang
Check '驳回' ($r.code -eq 200 -and (Detail $cid $tZhang).contractor.status -eq 'REJECTED')
$r = Call POST "/safety/contractor/$cid/status/approve" $null $tZhang; Show $r
Check '已驳回的不能直接审核通过' ($r.code -ne 200)
Call POST "/safety/contractor/$cid/status/resubmit" $null $tZhang | Out-Null
$r = Call POST "/safety/contractor/$cid/status/approve" $null $tZhang
Check '补充材料后重新提交、审核通过' ($r.code -eq 200 -and (Detail $cid $tZhang).contractor.status -eq 'APPROVED')
$r = Call POST "/safety/contractor/$cid/status/fly" $null $tZhang; Show $r
Check '不支持的操作' ($r.code -ne 200)

Title '3. 登记人员'
$r = Call POST "/safety/contractor/$cid/worker" @{ name = '新来的'; jobType = '焊工' } $tZhang
$wNew = $r.data
$r = Call POST "/safety/contractor/$cid/worker" @{ name = '老焊工'; phone = '13900001111'; jobType = '焊工'; trainingDate = (Day $today.AddMonths(-2))
                                                   certType = 'WELDING'; certNo = "T$suffix"; certExpire = (Day $today.AddYears(3)) } $tZhang
Check '登记人员（入厂培训 + 焊工证）' ($r.code -eq 200)
$wOk = $r.data
$r = Call POST "/safety/contractor/$cid/worker" @{ name = 'x'; certType = 'WELDING' } $tZhang; Show $r
Check '登记特种作业证要同时填编号和有效期' ($r.code -ne 200)
$r = Call POST "/safety/contractor/$cid/worker" @{ name = 'x'; trainingDate = (Day $today.AddDays(3)) } $tZhang; Show $r
Check '培训日期不能晚于今天' ($r.code -ne 200)
$r = Call POST "/safety/contractor/$cid/worker" @{ name = 'x'; phone = '123' } $tZhang; Show $r
Check '手机号格式校验' ($r.code -ne 200)
$w = @((Detail $cid $tZhang).workers | Where-Object { "$($_.id)" -eq "$wNew" })[0]
Check "没培训的人员显示原因：$($w.problems -join '、')" ((@($w.problems) -match '没有参加').Count -ge 1)

Title '4. 作业票进场校验（动火作业）'
$chk = @((Call GET "/safety/cert/ticket-check?ticketType=HOT_WORK&guardianId=104&contractorId=$cid&workerIds=$wNew,$wOk" $null $tLisi).data)
Check "新建作业票时预检：$(($chk | ForEach-Object { $_.userName + '：' + ($_.problems -join '、') }) -join '；')" ($chk.Count -eq 1 -and $chk[0].userName -eq '新来的')
$tid = (Call POST '/ticket' (HotWork '承包商动火：有人没培训' $cid @($wNew, $wOk)) $tLisi).data
Check '草稿可以保存' ($null -ne $tid)
$t = (Call GET "/ticket/$tid" $null $tLisi).data.ticket
Check "作业票记录了承包商和人员：$($t.contractorName) / $($t.contractorWorkerNames)" ($t.contractorName -like '自测检修*' -and $t.contractorWorkerNames -like '*老焊工*')
$r = Call POST "/ticket/$tid/submit" $null $tLisi; Show $r
Check '有人没参加入厂培训，不能提交' ($r.code -ne 200 -and $r.msg -like '*新来的*没有参加入厂安全培训*')
# 新来的也要有焊工证：先补培训，看是不是还会因为没证被拦
Call PUT "/safety/contractor/$cid/worker/$wNew" @{ name = '新来的'; jobType = '焊工'; trainingDate = (Day $today) } $tZhang | Out-Null
$r = Call POST "/ticket/$tid/submit" $null $tLisi; Show $r
Check '培训了但没有焊工证（动火作业要求承包商作业人员持焊工证），还是不能提交' ($r.code -ne 200 -and $r.msg -like '*焊接*')
$upd = HotWork '承包商动火：只带持证焊工' $cid @($wOk)
Call PUT "/ticket/$tid" $upd $tLisi | Out-Null
$r = Call POST "/ticket/$tid/submit" $null $tLisi
Check '只带培训合格、持证的人员，可以提交' ($r.code -eq 200)
Call POST "/ticket/$tid/cancel" @{ comment = '测试清理' } $tLisi | Out-Null

$r = Call POST '/ticket' (HotWork '别家的人' $cid @(9811)) $tLisi; Show $r
Check '不能选别的承包商的人员' ($r.code -ne 200)
$r = Call POST '/ticket' (HotWork '没选人员' $cid @()) $tLisi; Show $r
Check '承包商作业必须选作业人员' ($r.code -ne 200)

# 保险明天到期，作业 3 天
$short = $info.Clone(); $short.insuranceExpire = (Day $today.AddDays(1))
Call PUT "/safety/contractor/$cid" $short $tZhang | Out-Null
$tid2 = (Call POST '/ticket' (HotWork '保险撑不到作业结束' $cid @($wOk) 72) $tLisi).data
$r = Call POST "/ticket/$tid2/submit" $null $tLisi; Show $r
Check '资质、保险要覆盖作业期间' ($r.code -ne 200 -and $r.msg -like '*安全生产责任险*早于作业结束时间*')
Call PUT "/safety/contractor/$cid" $info $tZhang | Out-Null
Call POST "/ticket/$tid2/cancel" @{ comment = '测试清理' } $tLisi | Out-Null

# 改回本企业作业：承包商信息要清空
$tid3 = (Call POST '/ticket' (HotWork '先选承包商再改回本企业' $cid @($wOk)) $tLisi).data
$own = HotWork '本企业作业' $null @()
Call PUT "/ticket/$tid3" $own $tLisi | Out-Null
$t = (Call GET "/ticket/$tid3" $null $tLisi).data.ticket
Check '改回本企业作业后，承包商信息清空' (-not $t.contractorId -and -not $t.contractorWorkerNames)
Call POST "/ticket/$tid3/cancel" @{ comment = '测试清理' } $tLisi | Out-Null

Title '5. 违章扣分和黑名单'
$r = Call POST "/safety/contractor/$cid/violation" @{ description = 'x'; points = 25; occurTime = (Fmt $today) } $tZhang; Show $r
Check '单次最多扣 20 分' ($r.code -ne 200)
$r = Call POST "/safety/contractor/$cid/violation" @{ workerId = $wOk; description = '动火作业未清理周边可燃物'; points = 20; occurTime = (Fmt $today.AddHours(-2)) } $tZhang
Check "记录违章，评分 $($r.data.score)" ($r.code -eq 200 -and $r.data.score -eq 80 -and -not $r.data.blacklisted)
Call POST "/safety/contractor/$cid/violation" @{ description = '未办理动火作业票即动火'; points = 20; occurTime = (Fmt $today.AddHours(-1)) } $tZhang | Out-Null
$r = Call POST "/safety/contractor/$cid/violation" @{ description = '作业现场吸烟'; points = 1; occurTime = (Fmt $today) } $tZhang
Check "评分 $($r.data.score) 低于 60 分，自动列入黑名单" ($r.data.score -eq 59 -and $r.data.blacklisted)
$c = (Detail $cid $tZhang).contractor
Check "状态：$($c.status)，原因：$($c.statusComment)" ($c.status -eq 'BLACKLISTED' -and $c.reviewerName -eq '系统')
$m = MsgsOf $tZhang $cid
Check "安全员收到通知：$(@($m)[0].title)" ($m.Count -ge 1)
$tid4 = (Call POST '/ticket' (HotWork '黑名单单位' $cid @($wOk)) $tLisi).data
$r = Call POST "/ticket/$tid4/submit" $null $tLisi; Show $r
Check '黑名单单位不能作业' ($r.code -ne 200 -and $r.msg -like '*黑名单*')
Call POST "/ticket/$tid4/cancel" @{ comment = '测试清理' } $tLisi | Out-Null
$r = Call POST "/safety/contractor/$cid/worker" @{ name = 'y' } $tZhang; Show $r
Check '黑名单单位不能再登记人员' ($r.code -ne 200)
$r = Call POST "/safety/contractor/$cid/status/lift" $null $tZhang; Show $r
Check '解除黑名单必须写原因' ($r.code -ne 200)
$r = Call POST "/safety/contractor/$cid/status/lift" @{ comment = '已全员重新培训，签订安全承诺书' } $tZhang
Check '解除后回到待审核（要重新审核，不能直接恢复合格）' ($r.code -eq 200 -and (Detail $cid $tZhang).contractor.status -eq 'PENDING')

# 待审核单位的人员
$tid5 = (Call POST '/ticket' (HotWork '待审核单位' $xinyuan.id @(9816)) $tLisi).data
$r = Call POST "/ticket/$tid5/submit" $null $tLisi; Show $r
Check '待审核单位不能作业' ($r.code -ne 200 -and $r.msg -like '*待审核*')
Call POST "/ticket/$tid5/cancel" @{ comment = '测试清理' } $tLisi | Out-Null

Title '6. 租户隔离'
$r = Call GET '/safety/contractor/page?size=50' $null $tTest
if ($r.code -eq 403) {
    Write-Host '  （跳过：测试冶金管理员没有承包商菜单）' -ForegroundColor DarkGray
} else {
    Check "测试冶金只看到自己的承包商（$((@($r.data.records) | ForEach-Object { $_.name }) -join '、')）" (-not (@($r.data.records) | Where-Object { $_.name -like '*安达*' -or $_.name -like '自测*' }))
    $r = Call GET "/safety/contractor/$($anda.id)" $null $tTest; Show $r
    Check '看不到演示企业的承包商' ($r.code -ne 200)
    $r = Call POST "/safety/contractor/$($anda.id)/violation" @{ description = 'x'; points = 20; occurTime = (Fmt $today) } $tTest; Show $r
    Check '不能给演示企业的承包商记违章' ($r.code -ne 200)
}

Write-Host ''
if ($failed -eq 0) { Write-Host '全部通过' -ForegroundColor Green } else { Write-Host "失败 $failed 项" -ForegroundColor Red; Write-Host '把上面的输出发给 Claude' }
