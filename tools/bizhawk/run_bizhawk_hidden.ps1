param(
    [Parameter(Mandatory = $true)]
    [string]$EmuHawkExe,

    [Parameter(Mandatory = $true)]
    [string]$LuaScript,

    [Parameter(Mandatory = $true)]
    [string]$MoviePath,

    [Parameter(Mandatory = $true)]
    [string]$RomPath,

    [string]$ConfigPath,

    [string]$ExtraArgs
)

$ErrorActionPreference = "Stop"

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class OpenggfWindowTools {
    [DllImport("user32.dll")]
    public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
}
"@

function Quote-WindowsArgument([string]$Arg) {
    if ($null -eq $Arg) {
        return '""'
    }
    if ($Arg.Length -gt 0 -and $Arg -notmatch '[\s"]') {
        return $Arg
    }

    $result = New-Object System.Text.StringBuilder
    [void]$result.Append('"')
    $backslashes = 0
    foreach ($ch in $Arg.ToCharArray()) {
        if ($ch -eq '\') {
            $backslashes++
            continue
        }
        if ($ch -eq '"') {
            [void]$result.Append('\' * ($backslashes * 2 + 1))
            [void]$result.Append('"')
            $backslashes = 0
            continue
        }
        if ($backslashes -gt 0) {
            [void]$result.Append('\' * $backslashes)
            $backslashes = 0
        }
        [void]$result.Append($ch)
    }
    if ($backslashes -gt 0) {
        [void]$result.Append('\' * ($backslashes * 2))
    }
    [void]$result.Append('"')
    return $result.ToString()
}

$args = New-Object System.Collections.Generic.List[string]
$args.Add("--audiosync")
$args.Add("false")
if ($ConfigPath) {
    $args.Add("--config")
    $args.Add($ConfigPath)
}
$args.Add("--chromeless")
$args.Add("--lua")
$args.Add($LuaScript)
$args.Add("--movie")
$args.Add($MoviePath)
$args.Add($RomPath)

$argumentLine = ($args | ForEach-Object { Quote-WindowsArgument $_ }) -join " "
if ($ExtraArgs) {
    $argumentLine = "$argumentLine $ExtraArgs"
}

$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = (Resolve-Path -LiteralPath $EmuHawkExe).Path
$psi.Arguments = $argumentLine
$psi.UseShellExecute = $false
$psi.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden

$process = [System.Diagnostics.Process]::Start($psi)
while (-not $process.WaitForExit(100)) {
    $process.Refresh()
    $handle = $process.MainWindowHandle
    if ($null -ne $handle -and [IntPtr]$handle -ne [IntPtr]::Zero) {
        [void][OpenggfWindowTools]::ShowWindowAsync([IntPtr]$handle, 0)
    }
}
exit $process.ExitCode
