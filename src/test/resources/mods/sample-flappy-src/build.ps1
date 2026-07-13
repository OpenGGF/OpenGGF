param([string]$EngineJar,[string]$SdkJar,[string]$OutputDirectory)
Copy-Item -LiteralPath "$PSScriptRoot/project" -Destination $OutputDirectory -Recurse
$levelSource = Join-Path $OutputDirectory "src/main/mod/level-source"
$encodedAssets = Join-Path $levelSource "binary-assets.properties"
Get-Content -LiteralPath $encodedAssets | ForEach-Object {
    $parts = $_.Split('=', 2)
    [IO.File]::WriteAllBytes((Join-Path $levelSource $parts[0]), [Convert]::FromBase64String($parts[1]))
}
Remove-Item -LiteralPath $encodedAssets
& mvn -q -f "$OutputDirectory/pom.xml" package "-Dopenggf.engine.jar=$EngineJar" "-Dopenggf.sdk.jar=$SdkJar"
exit $LASTEXITCODE
