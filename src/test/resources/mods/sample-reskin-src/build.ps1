param([string]$EngineJar,[string]$SdkJar,[string]$OutputDirectory)
$cp="$EngineJar$([IO.Path]::PathSeparator)$SdkJar"
New-Item -ItemType Directory -Force "$OutputDirectory/art","$OutputDirectory/META-INF" | Out-Null
Copy-Item "$PSScriptRoot/META-INF/openggf-mod.yaml" "$OutputDirectory/META-INF/openggf-mod.yaml"
[IO.File]::WriteAllBytes("$OutputDirectory/reskin.png",[Convert]::FromBase64String((Get-Content "$PSScriptRoot/reskin.png.base64" -Raw).Trim()))
& java -cp $cp com.openggf.tools.modsdk.GgfModCli convert art --image "$OutputDirectory/reskin.png" --sheet "$PSScriptRoot/reskin-sheet.yaml" --out "$OutputDirectory/art/reskin.ggfs"
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& java -cp $cp com.openggf.tools.modsdk.GgfModCli package --input $OutputDirectory --out "$OutputDirectory/../phase2-reskin.jar"
exit $LASTEXITCODE
