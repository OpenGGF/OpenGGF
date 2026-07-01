param(
    [Parameter(Mandatory = $true)]
    [string]$SourceConfig,

    [Parameter(Mandatory = $true)]
    [string]$DiagConfig
)

$ErrorActionPreference = "Stop"

function Set-JsonProperty([object]$Object, [string]$Name, [object]$Value) {
    if ($Object.PSObject.Properties.Name -contains $Name) {
        $Object.$Name = $Value
    } else {
        $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    }
}

$cfg = Get-Content -Raw -LiteralPath $SourceConfig | ConvertFrom-Json

Set-JsonProperty $cfg "SoundEnabled" $false
Set-JsonProperty $cfg "SoundEnabledNormal" $false
Set-JsonProperty $cfg "SoundEnabledRWFF" $false
Set-JsonProperty $cfg "SoundVolume" 0
Set-JsonProperty $cfg "SoundVolumeRWFF" 0
Set-JsonProperty $cfg "SoundThrottle" $false
Set-JsonProperty $cfg "RunLuaDuringTurbo" $true
Set-JsonProperty $cfg "StartPaused" $false

Set-JsonProperty $cfg "DisplayFps" $false
Set-JsonProperty $cfg "DisplayFrameCounter" $false
Set-JsonProperty $cfg "DisplayLagCounter" $false
Set-JsonProperty $cfg "DisplayInput" $false
Set-JsonProperty $cfg "DisplayRerecordCount" $false
Set-JsonProperty $cfg "DisplayMessages" $false
Set-JsonProperty $cfg "DispChromeStatusBarWindowed" $false
Set-JsonProperty $cfg "DispChromeCaptionWindowed" $false
Set-JsonProperty $cfg "DispChromeMenuWindowed" $false
Set-JsonProperty $cfg "MainWindowMaximized" $false
Set-JsonProperty $cfg "SaveWindowPosition" $false
Set-JsonProperty $cfg "MainWindowPosition" "-32000, -32000"
Set-JsonProperty $cfg "MainWindowSize" "160, 120"

$diagDir = Split-Path -Parent $DiagConfig
if ($diagDir -and -not (Test-Path -LiteralPath $diagDir)) {
    New-Item -ItemType Directory -Force -Path $diagDir | Out-Null
}

$cfg | ConvertTo-Json -Depth 100 | Set-Content -NoNewline -LiteralPath $DiagConfig
