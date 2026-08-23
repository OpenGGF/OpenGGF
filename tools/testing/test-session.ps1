$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& java --source 21 (Join-Path $scriptDir 'TestSessionCoordinator.java') @args
exit $LASTEXITCODE
