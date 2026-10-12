param(
    [Parameter(Mandatory=$true)][string]$EngineJar,
    [Parameter(Mandatory=$true)][string]$SdkJar,
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$CommandArgs
)

$javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/java" } else { "java" }
& $javaExe --enable-native-access=ALL-UNNAMED -cp "$EngineJar$([IO.Path]::PathSeparator)$SdkJar" com.openggf.tools.modsdk.GgfModCli @CommandArgs
exit $LASTEXITCODE
