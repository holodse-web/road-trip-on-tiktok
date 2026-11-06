param(
    [string]$SourceJar = (Join-Path $PSScriptRoot 'build/rttt-1.0.0-beta.1.jar'),
    [string]$DestinationJar = (Join-Path $env:APPDATA '.minecraft/mods/rttt-1.0.0-beta.1.jar'),
    [string]$StatusFile = (Join-Path $PSScriptRoot 'build/update-status.json'),
    [int]$TimeoutSeconds = 43200
)

$ErrorActionPreference = 'Stop'
$sourcePath = (Resolve-Path -LiteralPath $SourceJar).Path
$destinationPath = [IO.Path]::GetFullPath($DestinationJar)
if ($sourcePath -eq $destinationPath) { throw 'Source and destination must differ.' }
function Get-UpdateHash([string]$path) {
    $stream = [IO.File]::OpenRead($path)
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-', '') }
    finally { $stream.Dispose(); $hasher.Dispose() }
}
$sourceHash = Get-UpdateHash $sourcePath
$backupFolder = Join-Path $PSScriptRoot 'build/backups'
New-Item -ItemType Directory -Force -Path $backupFolder | Out-Null
$backupPath = Join-Path $backupFolder ('rttt-before-update.' + [guid]::NewGuid().ToString('N') + '.jar')
$deadline = [DateTime]::UtcNow.AddSeconds([Math]::Max(1, $TimeoutSeconds))
function Write-UpdateStatus([string]$state, [string]$message) {
    @{ state = $state; message = $message; source = $sourcePath; destination = $destinationPath; backup = $backupPath } |
        ConvertTo-Json | Set-Content -LiteralPath $StatusFile -Encoding UTF8
}
Write-UpdateStatus 'waiting' 'Waiting for Minecraft to release the mod file.'
while ([DateTime]::UtcNow -lt $deadline) {
    $destinationStream = $null
    try {
        # Holding the destination exclusively prevents a new game from reading a partial update.
        $destinationStream = [IO.File]::Open($destinationPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch [IO.IOException] {
        Start-Sleep -Seconds 2
        continue
    }
    try {
        $backupStream = [IO.File]::Open($backupPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
        try { $destinationStream.CopyTo($backupStream) } finally { $backupStream.Dispose() }
        try {
            if ((Get-UpdateHash $sourcePath) -ne $sourceHash) { throw 'Source changed while waiting.' }
            $sourceStream = [IO.File]::OpenRead($sourcePath)
            try {
                $destinationStream.Position = 0
                $destinationStream.SetLength(0)
                $sourceStream.CopyTo($destinationStream)
                $destinationStream.Flush($true)
            } finally { $sourceStream.Dispose() }
            $destinationStream.Position = 0
            $hasher = [Security.Cryptography.SHA256]::Create()
            try { $writtenHash = [BitConverter]::ToString($hasher.ComputeHash($destinationStream)).Replace('-', '') } finally { $hasher.Dispose() }
            if ($writtenHash -ne $sourceHash) { throw 'Installed file hash mismatch.' }
        } catch {
            # Restore the previous JAR before releasing the destination if installation failed.
            $backupStream = [IO.File]::OpenRead($backupPath)
            try { $destinationStream.Position = 0; $destinationStream.SetLength(0); $backupStream.CopyTo($destinationStream); $destinationStream.Flush($true) } finally { $backupStream.Dispose() }
            throw
        }
        Write-UpdateStatus 'installed' 'Updated mod installed and verified. Minecraft can be started again.'
        exit 0
    } catch {
        Write-UpdateStatus 'error' $_.Exception.Message
        exit 1
    } finally {
        if ($null -ne $destinationStream) { $destinationStream.Dispose() }
    }
}
Write-UpdateStatus 'timeout' 'Minecraft kept the file open. Close it and run install-update.ps1 again.'
exit 1
