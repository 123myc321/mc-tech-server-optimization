# 构建 SuperOpt 示例
#
# 用法：
#   .\build.ps1 -ServerDir "C:\path\to\server" -Version "0.1.0"
#
# 说明：
#   - 依赖 classpath 由 server.jar + libraries + .fabric/remappedJars 拼成。
#   - 不同加载器/版本的依赖布局可能不同；缺类时把对应 jar 加进 $jars。

param(
    [Parameter(Mandatory = $true)][string]$ServerDir,
    [string]$Version = "0.1.0",
    [string]$JavaHome = "",
    [int]$JavaRelease = 25
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$build = Join-Path $root "build"
$classes = Join-Path $build "classes"

Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $classes -Force | Out-Null

$javac = if ($JavaHome) { Join-Path $JavaHome "bin\javac.exe" } else { "javac" }
$jarTool = if ($JavaHome) { Join-Path $JavaHome "bin\jar.exe" } else { "jar" }

$jars = @()
$serverJar = Join-Path $ServerDir "server.jar"
if (Test-Path $serverJar) { $jars += $serverJar }

$libs = Join-Path $ServerDir "libraries"
if (Test-Path $libs) {
    $jars += Get-ChildItem $libs -Recurse -Filter *.jar | ForEach-Object { $_.FullName }
}

$remap = Join-Path $ServerDir ".fabric\remappedJars"
if (Test-Path $remap) {
    $jars += Get-ChildItem $remap -Recurse -Filter *.jar | ForEach-Object { $_.FullName }
}

if ($jars.Count -eq 0) { throw "No dependency jars found under $ServerDir" }
$cp = $jars -join ";"

$sources = Get-ChildItem (Join-Path $root "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }

& $javac -encoding UTF-8 -source $JavaRelease -target $JavaRelease -cp $cp -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item (Join-Path $root "src\main\resources\*") $classes -Recurse -Force

$fmj = Join-Path $classes "fabric.mod.json"
$text = (Get-Content $fmj -Raw).Replace('${version}', $Version)
[IO.File]::WriteAllText($fmj, $text, (New-Object Text.UTF8Encoding($false)))

$out = Join-Path $build "superopt-$Version.jar"
& $jarTool --create --file $out -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Write-Host "built: $out"
