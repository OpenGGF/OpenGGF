$ErrorActionPreference = 'Stop'

$translated = [System.Collections.Generic.List[string]]::new()
for ($index = 0; $index -lt $args.Count; $index++) {
    $argument = [string]$args[$index]
    switch -Regex ($argument) {
        '^(?i)-ExportFile$' {
            $translated.Add('--export-file')
            $index++
            $translated.Add([string]$args[$index])
        }
        '^(?i)-LockRoot$' {
            $translated.Add('--lock-root')
            $index++
            $translated.Add([string]$args[$index])
        }
        '^(?i)-AllowSystemTmp$' { $translated.Add('--allow-system-tmp') }
        '^(?i)-Reclaim$' {
            $translated.Add('--reclaim')
            $index++
            $translated.Add([string]$args[$index])
        }
        '^(?i)-Guard$' {
            $translated.Add('--guard')
            $index++
            $translated.Add([string]$args[$index])
        }
        default { $translated.Add($argument) }
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& java --source 21 (Join-Path $scriptDir 'TestSessionCoordinator.java') --reuse-stale $translated
exit $LASTEXITCODE
