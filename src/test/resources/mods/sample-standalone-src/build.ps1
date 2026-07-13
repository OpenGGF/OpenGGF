param([string]$EngineJar,[string]$SdkJar,[string]$OutputDirectory)
Copy-Item -LiteralPath "$PSScriptRoot/project" -Destination $OutputDirectory -Recurse
$levelSource = Join-Path $OutputDirectory "src/main/mod/level-source"
Get-Content -LiteralPath (Join-Path $levelSource "binary-assets.properties") | ForEach-Object {
    $parts = $_.Split('=', 2)
    [IO.File]::WriteAllBytes((Join-Path $levelSource $parts[0]), [Convert]::FromBase64String($parts[1]))
}
Remove-Item -LiteralPath (Join-Path $levelSource "binary-assets.properties")
[IO.File]::WriteAllBytes((Join-Path $OutputDirectory "src/main/mod/runner.png"),
    [Convert]::FromBase64String((Get-Content -Raw -LiteralPath (Join-Path $OutputDirectory "src/main/mod/runner.png.base64")).Trim()))
[IO.File]::WriteAllBytes((Join-Path $OutputDirectory "src/main/resources/audio/sample-tone.wav"),
    [Convert]::FromBase64String((Get-Content -Raw -LiteralPath (Join-Path $OutputDirectory "src/main/mod/sample-tone.wav.base64")).Trim()))
& mvn -q -f "$OutputDirectory/pom.xml" package "-Dopenggf.engine.jar=$EngineJar" "-Dopenggf.sdk.jar=$SdkJar"
exit $LASTEXITCODE
