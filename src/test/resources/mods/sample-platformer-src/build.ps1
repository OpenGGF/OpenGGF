param([string]$EngineJar,[string]$SdkJar,[string]$OutputDirectory)
Copy-Item -LiteralPath "$PSScriptRoot/project" -Destination $OutputDirectory -Recurse
& mvn -q -f "$OutputDirectory/pom.xml" package "-Dopenggf.engine.jar=$EngineJar" "-Dopenggf.sdk.jar=$SdkJar"
exit $LASTEXITCODE
