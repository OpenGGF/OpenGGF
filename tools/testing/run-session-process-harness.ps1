$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir '../..')).Path
$harnessRoot = if ($env:OPENGGF_HARNESS_ROOT) {
    $env:OPENGGF_HARNESS_ROOT
} else {
    Join-Path ([System.IO.Path]::GetTempPath()) ('openggf-session-harness-' + [guid]::NewGuid().ToString('N'))
}
$classes = Join-Path ([System.IO.Path]::GetTempPath()) ('openggf-session-harness-classes-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $classes | Out-Null
try {
    javac --release 21 -d $classes (Join-Path $scriptDir 'TestSessionProcessHarness.java')
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
    java -ea -cp $classes TestSessionProcessHarness $projectRoot $harnessRoot
    if ($LASTEXITCODE -ne 0) { throw 'process harness failed' }
}
finally {
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $classes
}
