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

    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
}
"@

function Hide-ProcessWindows([System.Diagnostics.Process]$Process) {
    $targetPid = [uint32]$Process.Id
    $callback = [OpenggfWindowTools+EnumWindowsProc]{
        param([IntPtr]$hWnd, [IntPtr]$lParam)

        $windowPid = [uint32]0
        [void][OpenggfWindowTools]::GetWindowThreadProcessId($hWnd, [ref]$windowPid)
        if ($windowPid -eq $targetPid) {
            [void][OpenggfWindowTools]::ShowWindowAsync($hWnd, 0)
        }
        return $true
    }

    [void][OpenggfWindowTools]::EnumWindows($callback, [IntPtr]::Zero)
    $Process.Refresh()
    $handle = $Process.MainWindowHandle
    if ($null -ne $handle -and [IntPtr]$handle -ne [IntPtr]::Zero) {
        [void][OpenggfWindowTools]::ShowWindowAsync([IntPtr]$handle, 0)
    }
}

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
Hide-ProcessWindows $process

$fastHideDeadline = [DateTime]::UtcNow.AddSeconds(5)
while (-not $process.HasExited) {
    Hide-ProcessWindows $process
    $pollMs = if ([DateTime]::UtcNow -lt $fastHideDeadline) { 10 } else { 100 }
    if ($process.WaitForExit($pollMs)) {
        break
    }
}
exit $process.ExitCode
