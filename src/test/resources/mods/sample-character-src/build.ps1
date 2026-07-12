param([string]$EngineJar,[string]$SdkJar,[string]$OutputDirectory)
Copy-Item -LiteralPath "$PSScriptRoot/project" -Destination $OutputDirectory -Recurse
$encoded = Join-Path $OutputDirectory "src/main/mod/runner.png.base64"
[IO.File]::WriteAllBytes((Join-Path $OutputDirectory "src/main/mod/runner.png"),
    [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $encoded).Trim()))
Remove-Item -LiteralPath $encoded
& mvn -q -Dmse=off -f "$OutputDirectory/pom.xml" package "-Dopenggf.engine.jar=$EngineJar" "-Dopenggf.sdk.jar=$SdkJar"
exit $LASTEXITCODE
