[CmdletBinding()]
param(
    [string]$RomPath = "Sonic The Hedgehog 2 (W) (REV01) [!].gen",
    [string]$MoviesDir = "docs/BizHawk-2.11-win-x64/Movies",
    [string]$OutputRoot = "src/test/resources/traces/s2",
    [string]$Only,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

$Routes = @(
    [pscustomobject]@{ Route = "arz"; Bk2 = "s2-lvl-select-ARZ.bk2"; EngineZone = 2; RomZone = 0x0F; Act = 1; Mode = "replay" },
    [pscustomobject]@{ Route = "cnz"; Bk2 = "s2-lvl-select-CNZ.bk2"; EngineZone = 3; RomZone = 0x0C; Act = 1; Mode = "replay" },
    [pscustomobject]@{ Route = "cpz"; Bk2 = "s2-lvl-select-CPZ.bk2"; EngineZone = 1; RomZone = 0x0D; Act = 1; Mode = "replay" },
    [pscustomobject]@{ Route = "dez_ending"; Bk2 = "s2-lvl-select-DEZ-Ending.bk2"; EngineZone = 10; RomZone = 0x0E; Act = 1; Mode = "parser-only" },
    [pscustomobject]@{ Route = "htz"; Bk2 = "s2-lvl-select-HTZ.bk2"; EngineZone = 4; RomZone = 0x07; Act = 1; Mode = "replay" },
    [pscustomobject]@{ Route = "mcz"; Bk2 = "s2-lvl-select-MCZ.bk2"; EngineZone = 5; RomZone = 0x0B; Act = 1; Mode = "replay" },
    [pscustomobject]@{ Route = "ooz"; Bk2 = "s2-lvl-select-OOZ.bk2"; EngineZone = 6; RomZone = 0x0A; Act = 1; Mode = "replay" },
    [pscustomobject]@{ Route = "scz"; Bk2 = "s2-lvl-select-SCZ.bk2"; EngineZone = 8; RomZone = 0x10; Act = 1; Mode = "replay" },
    [pscustomobject]@{ Route = "wfz"; Bk2 = "s2-lvl-select-WFZ.bk2"; EngineZone = 9; RomZone = 0x06; Act = 1; Mode = "replay" }
)

function Show-Usage {
    Write-Host "Usage:"
    Write-Host "  powershell -NoProfile -ExecutionPolicy Bypass -File tools/bizhawk/record_s2_level_select_traces.ps1 -RomPath `"<rom>`" [-Only cpz]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -RomPath     Sonic 2 REV01 ROM path. Defaults to repo-root expected filename."
    Write-Host "  -MoviesDir   Directory containing s2-lvl-select-*.bk2 movies."
    Write-Host "  -OutputRoot  Trace resource root. Defaults to src/test/resources/traces/s2."
    Write-Host "  -Only        Generate one route slug: arz, cnz, cpz, dez_ending, htz, mcz, ooz, scz, wfz."
    Write-Host ""
    Write-Host "Routes:"
    foreach ($route in $Routes) {
        Write-Host ("  {0,-10} {1,-34} engine={2,2} rom=0x{3:X2} mode={4}" -f `
            $route.Route, $route.Bk2, $route.EngineZone, $route.RomZone, $route.Mode)
    }
}

function Resolve-RepoPath([string]$Path) {
    return (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
}

function Read-TextMaybeGzip([string]$Path) {
    if ($Path.EndsWith(".gz", [StringComparison]::OrdinalIgnoreCase)) {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            $gzip = [System.IO.Compression.GZipStream]::new($stream, [System.IO.Compression.CompressionMode]::Decompress)
            try {
                $reader = [System.IO.StreamReader]::new($gzip, [System.Text.Encoding]::UTF8)
                try {
                    return $reader.ReadToEnd()
                } finally {
                    $reader.Dispose()
                }
            } finally {
                $gzip.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    }
    return [System.IO.File]::ReadAllText($Path)
}

function Write-GzipText([string]$Path, [string]$Text) {
    $stream = [System.IO.File]::Create($Path)
    try {
        $gzip = [System.IO.Compression.GZipStream]::new($stream, [System.IO.Compression.CompressionLevel]::Optimal)
        try {
            $writer = [System.IO.StreamWriter]::new($gzip, [System.Text.UTF8Encoding]::new($false))
            try {
                $writer.Write($Text)
            } finally {
                $writer.Dispose()
            }
        } finally {
            $gzip.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Resolve-TracePayload([string]$Dir, [string]$BaseName) {
    $plain = Join-Path $Dir $BaseName
    $gzip = "$plain.gz"
    if (Test-Path -LiteralPath $gzip) {
        return $gzip
    }
    if (Test-Path -LiteralPath $plain) {
        return $plain
    }
    throw "Missing trace payload $BaseName(.gz) in $Dir"
}

function Get-PhysicsRows([string]$TraceDir) {
    $path = Resolve-TracePayload $TraceDir "physics.csv"
    $text = Read-TextMaybeGzip $path
    return @($text -split "`r?`n" | Where-Object {
        $_ -and -not $_.StartsWith("frame,") -and -not $_.StartsWith("#")
    })
}

function Get-PhysicsLines([string]$TraceDir) {
    $path = Resolve-TracePayload $TraceDir "physics.csv"
    $text = Read-TextMaybeGzip $path
    return @($text -split "`r?`n" | Where-Object { $_ -and -not $_.StartsWith("#") })
}

function Get-AuxEvents([string]$TraceDir) {
    $path = Resolve-TracePayload $TraceDir "aux_state.jsonl"
    $text = Read-TextMaybeGzip $path
    return @($text -split "`r?`n" | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json })
}

function Convert-Bk2P1FieldToMask([string]$P1) {
    if ($P1.Length -lt 8) {
        throw "Unexpected P1 input field '$P1'"
    }
    $mask = 0
    if ($P1[0] -ne '.') { $mask = $mask -bor 0x01 } # Up
    if ($P1[1] -ne '.') { $mask = $mask -bor 0x02 } # Down
    if ($P1[2] -ne '.') { $mask = $mask -bor 0x04 } # Left
    if ($P1[3] -ne '.') { $mask = $mask -bor 0x08 } # Right
    if ($P1[4] -ne '.' -or $P1[5] -ne '.' -or $P1[6] -ne '.') {
        $mask = $mask -bor 0x10
    }
    return $mask
}

function Get-Bk2InputMasks([string]$Bk2Path) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Bk2Path)
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq "Input Log.txt" }
        if ($null -eq $entry) {
            throw "Input Log.txt not found in $Bk2Path"
        }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try {
            $masks = New-Object System.Collections.Generic.List[int]
            while (($line = $reader.ReadLine()) -ne $null) {
                if (-not $line.StartsWith("|")) {
                    continue
                }
                $parts = $line.Split("|")
                if ($parts.Length -lt 4) {
                    throw "Unexpected BK2 input line: $line"
                }
                $masks.Add((Convert-Bk2P1FieldToMask $parts[2]))
            }
            return $masks.ToArray()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $zip.Dispose()
    }
}

function Normalize-PhysicsInputFromBk2([string]$Bk2Path, [string]$TraceDir) {
    $metadata = Get-Content -LiteralPath (Join-Path $TraceDir "metadata.json") -Raw | ConvertFrom-Json
    $offset = [int]$metadata.bk2_frame_offset
    $path = Resolve-TracePayload $TraceDir "physics.csv"
    $masks = Get-Bk2InputMasks $Bk2Path
    $lines = @((Read-TextMaybeGzip $path) -split "`r?`n")
    $rowIndex = 0
    $changed = 0
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if (-not $line -or $line.StartsWith("#") -or $line.StartsWith("frame,")) {
            continue
        }
        $bk2Index = $offset + $rowIndex
        if ($bk2Index -ge $masks.Length) {
            throw "Trace row $rowIndex needs BK2 input index $bk2Index, but movie has $($masks.Length) input rows"
        }
        $columns = $line.Split(",")
        if ($columns.Length -lt 2) {
            throw "Trace row $rowIndex has no input column: $line"
        }
        $bk2Input = $masks[$bk2Index]
        $normalized = "{0:X4}" -f $bk2Input
        if ($columns[1] -ne $normalized) {
            $columns[1] = $normalized
            $lines[$i] = [string]::Join(",", $columns)
            $changed++
        }
        $rowIndex++
    }

    $text = [string]::Join("`n", $lines)
    if (-not $text.EndsWith("`n")) {
        $text += "`n"
    }
    if ($path.EndsWith(".gz", [StringComparison]::OrdinalIgnoreCase)) {
        Write-GzipText $path $text
    } else {
        [System.IO.File]::WriteAllText($path, $text, [System.Text.UTF8Encoding]::new($false))
    }
    if ($changed -gt 0) {
        Write-Host "Normalized $changed physics input rows from BK2 input log"
    }
}

function Assert-CompressedPayloads([string]$TraceDir) {
    foreach ($name in @("physics.csv", "aux_state.jsonl")) {
        $plain = Join-Path $TraceDir $name
        $gzip = "$plain.gz"
        if (Test-Path -LiteralPath $plain) {
            throw "Uncompressed payload should not remain in generated route dir: $plain"
        }
        if (-not (Test-Path -LiteralPath $gzip)) {
            throw "Compressed payload missing: $gzip"
        }
    }
}

function Assert-Metadata([object]$Route, [string]$TraceDir) {
    $metadataPath = Join-Path $TraceDir "metadata.json"
    if (-not (Test-Path -LiteralPath $metadataPath)) {
        throw "metadata.json missing in $TraceDir"
    }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    if ($metadata.game -ne "s2") { throw "metadata.game expected s2, got $($metadata.game)" }
    if ($metadata.zone -ne ($Route.Route -replace "_ending$", "")) {
        throw "metadata.zone expected route zone for $($Route.Route), got $($metadata.zone)"
    }
    if ([int]$metadata.zone_id -ne [int]$Route.EngineZone) {
        throw "metadata.zone_id expected $($Route.EngineZone), got $($metadata.zone_id)"
    }
    if ([int]$metadata.rom_zone_id -ne [int]$Route.RomZone) {
        throw ("metadata.rom_zone_id expected 0x{0:X2}, got {1}" -f $Route.RomZone, $metadata.rom_zone_id)
    }
    if ([int]$metadata.act -ne [int]$Route.Act) {
        throw "metadata.act expected $($Route.Act), got $($metadata.act)"
    }
    if ($metadata.trace_profile -ne "level_gated_reset_aware") {
        throw "metadata.trace_profile expected level_gated_reset_aware, got $($metadata.trace_profile)"
    }
    if ($metadata.source_bk2 -ne $Route.Bk2) {
        throw "metadata.source_bk2 expected $($Route.Bk2), got $($metadata.source_bk2)"
    }

    $rows = Get-PhysicsRows $TraceDir
    if ([int]$metadata.trace_frame_count -ne $rows.Count) {
        throw "metadata.trace_frame_count expected $($rows.Count), got $($metadata.trace_frame_count)"
    }

    $lines = Get-PhysicsLines $TraceDir
    if ($lines.Count -gt 1 -and $lines[0].StartsWith("frame,")) {
        $header = $lines[0].Split(",")
        $tailsPresentColumn = [Array]::IndexOf($header, "tails_present")
        if ($tailsPresentColumn -ge 0) {
            $hasRecordedSidekick = $false
            foreach ($row in $lines[1..($lines.Count - 1)]) {
                $columns = $row.Split(",")
                if ($columns.Length -gt $tailsPresentColumn -and $columns[$tailsPresentColumn] -ne "0") {
                    $hasRecordedSidekick = $true
                    break
                }
            }
            $metadataSidekicks = if ($null -eq $metadata.sidekicks) { @() } else { @($metadata.sidekicks) }
            $metadataCharacters = if ($null -eq $metadata.characters) { @() } else { @($metadata.characters) }
            if ($hasRecordedSidekick) {
                if ($metadataSidekicks -notcontains "tails") {
                    throw "metadata.sidekicks must include tails when physics.csv records tails_present=1"
                }
                if ($metadataCharacters -notcontains "tails") {
                    throw "metadata.characters must include tails when physics.csv records tails_present=1"
                }
            } else {
                if ($metadataSidekicks.Count -ne 0) {
                    throw "metadata.sidekicks must be empty when physics.csv records no active Tails sidekick"
                }
                if ($metadataCharacters -contains "tails") {
                    throw "metadata.characters must not include tails when physics.csv records no active Tails sidekick"
                }
            }
        }
    }
}

function Assert-FrameZeroGameplayMarker([string]$TraceDir) {
    $events = Get-AuxEvents $TraceDir
    $found = $false
    foreach ($event in $events) {
        if ([int]$event.frame -ne 0) {
            continue
        }
        if ($event.event -eq "zone_act_state" -and [int]$event.game_mode -eq 12) {
            $found = $true
        }
        if ($event.event -eq "checkpoint" -and $event.name -eq "gameplay_start" -and [int]$event.game_mode -eq 12) {
            $found = $true
        }
    }
    if (-not $found) {
        throw "Frame 0 gameplay marker with game_mode=12 not found"
    }
}

function Assert-Bk2InputAlignment([string]$Bk2Path, [string]$TraceDir) {
    $metadata = Get-Content -LiteralPath (Join-Path $TraceDir "metadata.json") -Raw | ConvertFrom-Json
    $offset = [int]$metadata.bk2_frame_offset
    $rows = Get-PhysicsRows $TraceDir
    $masks = Get-Bk2InputMasks $Bk2Path
    for ($i = 0; $i -lt $rows.Count; $i++) {
        $bk2Index = $offset + $i
        if ($bk2Index -ge $masks.Length) {
            throw "Trace row $i needs BK2 input index $bk2Index, but movie has $($masks.Length) input rows"
        }
        $columns = $rows[$i].Split(",")
        if ($columns.Length -lt 2) {
            throw "Trace row $i has no input column: $($rows[$i])"
        }
        $traceInput = [Convert]::ToInt32($columns[1], 16)
        if ($traceInput -ne $masks[$bk2Index]) {
            throw ("Input mismatch at trace row {0}, BK2 index {1}: trace=0x{2:X4} bk2=0x{3:X4}" -f `
                $i, $bk2Index, $traceInput, $masks[$bk2Index])
        }
    }
}

function Assert-TraceOutput([object]$Route, [string]$TraceDir, [string]$Bk2Path) {
    Assert-CompressedPayloads $TraceDir
    Assert-Metadata $Route $TraceDir
    Assert-FrameZeroGameplayMarker $TraceDir
    Assert-Bk2InputAlignment $Bk2Path $TraceDir
}

if ($Help) {
    Show-Usage
    exit 0
}

$selectedRoutes = $Routes
if ($Only) {
    $selectedRoutes = @($Routes | Where-Object { $_.Route -eq $Only })
    if ($selectedRoutes.Count -eq 0) {
        throw "Unknown route '$Only'. Use -Help to list route slugs."
    }
}

$romFullPath = Resolve-RepoPath $RomPath
$moviesFullPath = Resolve-RepoPath $MoviesDir
$outputFullPath = if (Test-Path -LiteralPath $OutputRoot) {
    (Resolve-Path -LiteralPath $OutputRoot).Path
} else {
    New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
    (Resolve-Path -LiteralPath $OutputRoot).Path
}
$recordScript = Resolve-RepoPath "tools/bizhawk/record_s2_trace.bat"
$traceOutput = Resolve-RepoPath "tools/bizhawk/trace_output"

foreach ($route in $selectedRoutes) {
    $bk2Path = Join-Path $moviesFullPath $route.Bk2
    if (-not (Test-Path -LiteralPath $bk2Path)) {
        throw "Missing BK2 for route $($route.Route): $bk2Path"
    }

    $resolvedTraceOutput = [System.IO.Path]::GetFullPath($traceOutput)
    $expectedTraceOutput = [System.IO.Path]::GetFullPath((Join-Path (Resolve-Path "tools/bizhawk").Path "trace_output"))
    if ($resolvedTraceOutput -ne $expectedTraceOutput) {
        throw "Refusing to clean unexpected trace output path: $resolvedTraceOutput"
    }
    if (Test-Path -LiteralPath $traceOutput) {
        Remove-Item -LiteralPath $traceOutput -Recurse -Force
    }
    New-Item -ItemType Directory -Path $traceOutput -Force | Out-Null

    Write-Host "=== Recording S2 route $($route.Route) from $($route.Bk2) ==="
    & $recordScript $romFullPath $bk2Path "level_gated_reset_aware"
    if ($LASTEXITCODE -ne 0) {
        throw "record_s2_trace.bat failed for $($route.Route) with exit code $LASTEXITCODE"
    }

    $targetDir = Join-Path $outputFullPath $route.Route
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $traceOutput "metadata.json") -Destination $targetDir -Force
    Copy-Item -LiteralPath (Join-Path $traceOutput "physics.csv.gz") -Destination $targetDir -Force
    Copy-Item -LiteralPath (Join-Path $traceOutput "aux_state.jsonl.gz") -Destination $targetDir -Force
    Copy-Item -LiteralPath $bk2Path -Destination $targetDir -Force

    Normalize-PhysicsInputFromBk2 (Join-Path $targetDir $route.Bk2) $targetDir
    Assert-TraceOutput $route $targetDir (Join-Path $targetDir $route.Bk2)
    Write-Host "Route $($route.Route) validated into $targetDir"
}
