param(
    [string]$GameVersion = "26.3",
    [string]$ModsDir = "$env:APPDATA\.minecraft\mods"
)
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$headers = @{ "User-Agent" = "rttt-installer/1.0 (alex-home-mod)" }

$mods = [ordered]@{
    "lithium"        = "Lithium: оптимизация логики мира и физики"
    "ferrite-core"   = "FerriteCore: меньше расход памяти"
    "immediatelyfast"= "ImmediatelyFast: быстрее рисует интерфейс, текст и частицы"
    "modernfix"      = "ModernFix: быстрее запуск и меньше памяти"
}

New-Item -ItemType Directory -Force -Path $ModsDir | Out-Null
Write-Host "Папка модов: $ModsDir" -ForegroundColor Cyan
Write-Host "Версия игры: $GameVersion (Fabric)`n" -ForegroundColor Cyan

$ok = 0; $skip = 0
foreach ($slug in $mods.Keys) {
    Write-Host ("- " + $mods[$slug])
    try {
        $loaders = [uri]::EscapeDataString('["fabric"]')
        $versions = [uri]::EscapeDataString("[`"$GameVersion`"]")
        $url = "https://api.modrinth.com/v2/project/$slug/version?loaders=$loaders&game_versions=$versions"
        $list = Invoke-RestMethod -Uri $url -Headers $headers
        if (-not $list -or $list.Count -eq 0) {
            Write-Host "    нет версии под $GameVersion — пропускаю (это нормально, мод работает и без него)" -ForegroundColor Yellow
            $skip++; continue
        }
        $ver = $list | Where-Object { $_.version_type -eq "release" } | Select-Object -First 1
        if (-not $ver) { $ver = $list | Select-Object -First 1 }
        $file = $ver.files | Where-Object { $_.primary } | Select-Object -First 1
        if (-not $file) { $file = $ver.files | Select-Object -First 1 }
        $target = Join-Path $ModsDir $file.filename
        if (Test-Path $target) {
            Write-Host "    уже установлен: $($file.filename)" -ForegroundColor Green
            $ok++; continue
        }
        Get-ChildItem $ModsDir -Filter "*.jar" | Where-Object { $_.Name -like "$slug*" -and $_.Name -ne $file.filename } | ForEach-Object {
            Write-Host "    удаляю старую версию $($_.Name)"
            Remove-Item $_.FullName -Force
        }
        Invoke-WebRequest -Uri $file.url -Headers $headers -OutFile $target -UseBasicParsing
        Write-Host "    скачан $($file.filename) ($($ver.version_number))" -ForegroundColor Green
        $ok++
    } catch {
        Write-Host "    ошибка: $($_.Exception.Message)" -ForegroundColor Red
        $skip++
    }
}
Write-Host "`nГотово: установлено $ok, пропущено $skip." -ForegroundColor Cyan
Write-Host "EntityCulling не ставится: он несовместим с TLSkinCape из TLauncher."
Write-Host "Dynamic FPS специально НЕ ставится: он душит FPS, когда окно не в фокусе, а OBS захватывает именно такое окно."
