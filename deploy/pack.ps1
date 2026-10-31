# 在自己电脑上打发布包：编译后端 jar 和前端页面，和部署文件一起打成 safework-release.tar.gz
# 服务器只有 2G 内存时，在服务器上编译 Java、前端很容易内存不足，所以在本机编译好再上传
#
# 运行方式（在项目根目录）：powershell -ExecutionPolicy Bypass -File .\deploy\pack.ps1
# 已经编译过、只想重新打包：powershell -ExecutionPolicy Bypass -File .\deploy\pack.ps1 -SkipBuild
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root
# 发布包放在项目目录外面（D:\code\safework-release），不会被 git 提交
$out = Join-Path (Split-Path -Parent $root) 'safework-release'
$tarFile = Join-Path (Split-Path -Parent $root) 'safework-release.tar.gz'

if (-not $SkipBuild) {
    Write-Host '== 1. 编译后端（跳过测试，约 1 分钟）==' -ForegroundColor Cyan
    mvn -q clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw '后端编译失败，把上面的输出发给 Claude' }

    Write-Host '== 2. 编译前端 ==' -ForegroundColor Cyan
    Push-Location safework-web
    try {
        if (-not (Test-Path node_modules)) { npm install }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw '前端编译失败，把上面的输出发给 Claude' }
    } finally {
        Pop-Location
    }
}

$jar = Join-Path $root 'safework-server/target/safework-server.jar'
$dist = Join-Path $root 'safework-web/dist'
if (-not (Test-Path $jar)) { throw "找不到 $jar，请去掉 -SkipBuild 重新运行" }
if (-not (Test-Path (Join-Path $dist 'index.html'))) { throw "找不到 $dist\index.html，请去掉 -SkipBuild 重新运行" }

Write-Host '== 3. 组装发布包 ==' -ForegroundColor Cyan
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path "$out/backend", "$out/web" | Out-Null

# Linux 上运行的文件统一转成 LF 换行（Windows 换行的脚本在 Linux 上会报 $'\r': command not found）
$utf8 = New-Object Text.UTF8Encoding $false
function CopyLf($from, $to) {
    $text = [IO.File]::ReadAllText((Join-Path $root $from), [Text.Encoding]::UTF8).Replace("`r`n", "`n")
    [IO.File]::WriteAllText($to, $text, $utf8)
}
CopyLf 'deploy/release/docker-compose.yml' "$out/docker-compose.yml"
CopyLf 'deploy/release/start.sh' "$out/start.sh"
CopyLf 'deploy/release/backup.sh' "$out/backup.sh"
CopyLf 'deploy/release/server-init.sh' "$out/server-init.sh"
CopyLf 'deploy/release/demo-baseline.sh' "$out/demo-baseline.sh"
CopyLf 'deploy/release/demo-reset.sh' "$out/demo-reset.sh"
CopyLf 'deploy/release/backend/Dockerfile' "$out/backend/Dockerfile"
CopyLf 'deploy/release/web/Dockerfile' "$out/web/Dockerfile"
CopyLf 'safework-web/nginx.conf' "$out/web/nginx.conf"
# .env.example：2G 内存服务器把后端堆内存调小一点
$envText = [IO.File]::ReadAllText((Join-Path $root '.env.example'), [Text.Encoding]::UTF8).Replace("`r`n", "`n")
$envText = $envText -replace 'JAVA_OPTS=.*', 'JAVA_OPTS="-Xms256m -Xmx448m -XX:+UseG1GC -XX:MaxMetaspaceSize=192m"'
[IO.File]::WriteAllText("$out/.env.example", $envText, $utf8)
Copy-Item $jar "$out/backend/app.jar"
Copy-Item $dist "$out/web/dist" -Recurse

Write-Host '== 4. 打包 ==' -ForegroundColor Cyan
if (Test-Path $tarFile) { Remove-Item $tarFile -Force }
# Windows 10 / 11 自带 tar；-C 进入目录打包，解压时直接得到里面的文件
tar -czf $tarFile -C $out .
if ($LASTEXITCODE -ne 0) { throw '打包失败' }

$size = [math]::Round((Get-Item $tarFile).Length / 1MB, 1)
Write-Host ''
Write-Host "发布包：$tarFile（$size MB）" -ForegroundColor Green
Write-Host '下一步上传到服务器：' -ForegroundColor Green
Write-Host "  scp `"$tarFile`" root@服务器IP:/root/"
