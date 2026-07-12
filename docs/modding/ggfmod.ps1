param(
    [Parameter(Mandatory=$true)][string]$EngineJar,
    [Parameter(Mandatory=$true)][string]$SdkJar,
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$CommandArgs
)

& java -cp "$EngineJar$([IO.Path]::PathSeparator)$SdkJar" com.openggf.tools.modsdk.GgfModCli @CommandArgs
exit $LASTEXITCODE
