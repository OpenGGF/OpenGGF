[CmdletBinding()]
param(
    [string]$RomPath = "s2.gen",
    [string]$MoviesDir = "docs/BizHawk-2.11-win-x64/Movies",
    [string]$OutputRoot = "src/test/resources/traces/s2",
    [string]$Only,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

$Routes = @(
    [pscustomobject]@{ Route = "arz"; Zone = "arz"; Bk2 = "s2-lvl-select-ARZ.bk2"; EngineZone = 2; RomZone = 0x0F; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "arz2"; Zone = "arz"; Bk2 = "s2-lvl-select-ARZ.bk2"; EngineZone = 2; RomZone = 0x0F; StartAct = 2; ExpectedActs = @(1); Segment = 1; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "cnz"; Zone = "cnz"; Bk2 = "s2-lvl-select-CNZ.bk2"; EngineZone = 3; RomZone = 0x0C; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "cnz2"; Zone = "cnz"; Bk2 = "s2-lvl-select-CNZ.bk2"; EngineZone = 3; RomZone = 0x0C; StartAct = 2; ExpectedActs = @(1); Segment = 1; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "cpz"; Zone = "cpz"; Bk2 = "s2-lvl-select-CPZ.bk2"; EngineZone = 1; RomZone = 0x0D; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "cpz2"; Zone = "cpz"; Bk2 = "s2-lvl-select-CPZ.bk2"; EngineZone = 1; RomZone = 0x0D; StartAct = 2; ExpectedActs = @(1); Segment = 1; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "dez_ending"; Zone = "dez"; Bk2 = "s2-lvl-select-DEZ-Ending.bk2"; EngineZone = 10; RomZone = 0x0E; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "parser-only"; Profile = "level" },
    [pscustomobject]@{ Route = "htz"; Zone = "htz"; Bk2 = "s2-lvl-select-HTZ.bk2"; EngineZone = 4; RomZone = 0x07; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "htz2"; Zone = "htz"; Bk2 = "s2-lvl-select-HTZ.bk2"; EngineZone = 4; RomZone = 0x07; StartAct = 2; ExpectedActs = @(1); Segment = 1; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "mcz"; Zone = "mcz"; Bk2 = "s2-lvl-select-MCZ.bk2"; EngineZone = 5; RomZone = 0x0B; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "mcz2"; Zone = "mcz"; Bk2 = "s2-lvl-select-MCZ.bk2"; EngineZone = 5; RomZone = 0x0B; StartAct = 2; ExpectedActs = @(1); Segment = 1; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "mtz"; Zone = "mtz"; Bk2 = "s2-lvl-select-MTZ.bk2"; EngineZone = 7; RomZone = 0x04; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "mtz2"; Zone = "mtz"; Bk2 = "s2-lvl-select-MTZ.bk2"; EngineZone = 7; RomZone = 0x04; StartAct = 2; ExpectedActs = @(1); Segment = 1; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "mtz3"; Zone = "mtz"; Bk2 = "s2-lvl-select-MTZ.bk2"; EngineZone = 7; RomZone = 0x05; StartAct = 3; ExpectedActs = @(0); Segment = 2; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "ooz"; Zone = "ooz"; Bk2 = "s2-lvl-select-OOZ.bk2"; EngineZone = 6; RomZone = 0x0A; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "ooz2"; Zone = "ooz"; Bk2 = "s2-lvl-select-OOZ.bk2"; EngineZone = 6; RomZone = 0x0A; StartAct = 2; ExpectedActs = @(1); Segment = 1; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "scz"; Zone = "scz"; Bk2 = "s2-lvl-select-SCZ.bk2"; EngineZone = 8; RomZone = 0x10; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "wfz"; Zone = "wfz"; Bk2 = "s2-lvl-select-WFZ.bk2"; EngineZone = 9; RomZone = 0x06; StartAct = 1; ExpectedActs = @(0); Segment = 0; Mode = "replay"; Profile = "level" },
    [pscustomobject]@{ Route = "special_stage"; Zone = "special_stage"; Bk2 = "s2-lvl-select-special-stage.bk2"; EngineZone = 0; RomZone = 0; StartAct = 0; ExpectedActs = @(); Segment = 0; Mode = "replay"; Profile = "s2_special_stage" }
)

function Show-Usage {
    Write-Host "Usage:"
    Write-Host "  powershell -NoProfile -ExecutionPolicy Bypass -File tools/bizhawk/record_s2_level_select_traces.ps1 -RomPath `"<rom>`" [-Only cpz]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -RomPath     Sonic 2 REV01 ROM path. Defaults to repo-root expected filename."
    Write-Host "  -MoviesDir   Directory containing s2-lvl-select-*.bk2 movies."
    Write-Host "  -OutputRoot  Trace resource root. Defaults to src/test/resources/traces/s2."
    Write-Host ("  -Only        Generate one route slug: {0}." -f (($Routes | ForEach-Object { $_.Route }) -join ", "))
    Write-Host ""
    Write-Host "Routes:"
    foreach ($route in $Routes) {
        Write-Host ("  {0,-10} {1,-34} engine={2,2} rom=0x{3:X2} startAct={4} segment={5} mode={6}" -f `
            $route.Route, $route.Bk2, $route.EngineZone, $route.RomZone,
            $route.StartAct, $route.Segment, $route.Mode)
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

# Convert one BK2 pad field ("UDLRABCS", 8 chars) to the engine input mask.
# Level traces use the Start-less mask (UP=0x01 DOWN=0x02 LEFT=0x04 RIGHT=0x08
# JUMP=0x10); the special-stage schema additionally maps Start to 0x80.
function Convert-Bk2PadFieldToMask([string]$Field, [switch]$IncludeStart) {
    if ($Field.Length -lt 8) {
        throw "Unexpected pad input field '$Field'"
    }
    $mask = 0
    if ($Field[0] -ne '.') { $mask = $mask -bor 0x01 } # Up
    if ($Field[1] -ne '.') { $mask = $mask -bor 0x02 } # Down
    if ($Field[2] -ne '.') { $mask = $mask -bor 0x04 } # Left
    if ($Field[3] -ne '.') { $mask = $mask -bor 0x08 } # Right
    if ($Field[4] -ne '.' -or $Field[5] -ne '.' -or $Field[6] -ne '.') {
        $mask = $mask -bor 0x10                        # A/B/C -> JUMP
    }
    if ($IncludeStart -and $Field[7] -ne '.') {
        $mask = $mask -bor 0x80                        # Start
    }
    return $mask
}

function Convert-Bk2P1FieldToMask([string]$P1) {
    return Convert-Bk2PadFieldToMask $P1
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

# Returns per-frame @{ P1 = mask; P2 = mask } objects (Start-aware masks) from
# the BK2 Input Log for the special-stage input-alignment check. P2 is parsed
# from field index 3; a movie with no recorded P2 input yields all-zero P2 masks.
function Get-Bk2SsInputMaskPairs([string]$Bk2Path) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Bk2Path)
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq "Input Log.txt" }
        if ($null -eq $entry) {
            throw "Input Log.txt not found in $Bk2Path"
        }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try {
            $pairs = New-Object System.Collections.Generic.List[object]
            while (($line = $reader.ReadLine()) -ne $null) {
                if (-not $line.StartsWith("|")) {
                    continue
                }
                $parts = $line.Split("|")
                if ($parts.Length -lt 5) {
                    throw "Unexpected BK2 input line (no P2 field): $line"
                }
                $pairs.Add([pscustomobject]@{
                    P1 = (Convert-Bk2PadFieldToMask $parts[2] -IncludeStart)
                    P2 = (Convert-Bk2PadFieldToMask $parts[3] -IncludeStart)
                })
            }
            return $pairs.ToArray()
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
    if ($metadata.zone -ne $Route.Zone) {
        throw "metadata.zone expected route zone $($Route.Zone) for $($Route.Route), got $($metadata.zone)"
    }
    if ([int]$metadata.zone_id -ne [int]$Route.EngineZone) {
        throw "metadata.zone_id expected $($Route.EngineZone), got $($metadata.zone_id)"
    }
    if ([int]$metadata.rom_zone_id -ne [int]$Route.RomZone) {
        throw ("metadata.rom_zone_id expected 0x{0:X2}, got {1}" -f $Route.RomZone, $metadata.rom_zone_id)
    }
    if ([int]$metadata.act -ne [int]$Route.StartAct) {
        throw "metadata.act expected start act $($Route.StartAct), got $($metadata.act)"
    }
    if ([int]$metadata.gameplay_segment -ne [int]$Route.Segment) {
        throw "metadata.gameplay_segment expected $($Route.Segment), got $($metadata.gameplay_segment)"
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

function Assert-SsMetadata([object]$Route, [string]$TraceDir) {
    $metadataPath = Join-Path $TraceDir "metadata.json"
    if (-not (Test-Path -LiteralPath $metadataPath)) {
        throw "metadata.json missing in $TraceDir"
    }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    if ($metadata.game -ne "s2") { throw "metadata.game expected s2, got $($metadata.game)" }
    if ($metadata.trace_profile -ne "s2_special_stage") {
        throw "metadata.trace_profile expected s2_special_stage, got $($metadata.trace_profile)"
    }
    if ($null -eq $metadata.special_stage_index -or [int]$metadata.special_stage_index -ne 0) {
        throw "metadata.special_stage_index expected 0, got $($metadata.special_stage_index)"
    }
    if ($metadata.source_bk2 -ne $Route.Bk2) {
        throw "metadata.source_bk2 expected $($Route.Bk2), got $($metadata.source_bk2)"
    }
    if ([int]$metadata.bk2_frame_offset -le 0) {
        throw "metadata.bk2_frame_offset expected > 0, got $($metadata.bk2_frame_offset)"
    }
    if ([int]$metadata.trace_frame_count -le 0) {
        throw "metadata.trace_frame_count expected > 0, got $($metadata.trace_frame_count)"
    }

    $rows = Get-PhysicsRows $TraceDir
    if ($rows.Count -ne [int]$metadata.trace_frame_count) {
        throw "metadata.trace_frame_count expected $($rows.Count) (gzipped physics.csv row count), got $($metadata.trace_frame_count)"
    }
}

# Special-stage analog of Assert-Bk2InputAlignment: verifies BOTH the csv
# `input` (column 1) and `input_p2` (column 2) values against the BK2's
# Input Log P1/P2 fields (Start-aware masks) for every recorded frame from
# bk2_frame_offset onward.
function Assert-SsBk2InputAlignment([string]$Bk2Path, [string]$TraceDir) {
    $metadata = Get-Content -LiteralPath (Join-Path $TraceDir "metadata.json") -Raw | ConvertFrom-Json
    $offset = [int]$metadata.bk2_frame_offset
    $rows = Get-PhysicsRows $TraceDir
    $pairs = Get-Bk2SsInputMaskPairs $Bk2Path
    for ($i = 0; $i -lt $rows.Count; $i++) {
        $bk2Index = $offset + $i
        if ($bk2Index -ge $pairs.Length) {
            throw "SS trace row $i needs BK2 input index $bk2Index, but movie has $($pairs.Length) input rows"
        }
        $columns = $rows[$i].Split(",")
        if ($columns.Length -lt 3) {
            throw "SS trace row $i has no input/input_p2 columns: $($rows[$i])"
        }
        $traceInput = [Convert]::ToInt32($columns[1], 16)
        $traceInputP2 = [Convert]::ToInt32($columns[2], 16)
        if ($traceInput -ne $pairs[$bk2Index].P1) {
            throw ("SS input mismatch at trace row {0}, BK2 index {1}: trace=0x{2:X2} bk2=0x{3:X2}" -f `
                $i, $bk2Index, $traceInput, $pairs[$bk2Index].P1)
        }
        if ($traceInputP2 -ne $pairs[$bk2Index].P2) {
            throw ("SS input_p2 mismatch at trace row {0}, BK2 index {1}: trace=0x{2:X2} bk2=0x{3:X2}" -f `
                $i, $bk2Index, $traceInputP2, $pairs[$bk2Index].P2)
        }
    }
    Write-Host "SS BK2 input alignment verified for $($rows.Count) frames (P1 + P2)"
}

# Collapse the ROM's distinct A/B/C bits to the trace/engine jump bit while
# preserving D-pad and Start. RunObjects aux stores the authoritative raw Ctrl
# byte; the BK2 contract stores the engine-normalized movie mask.
function Convert-SsRawHeldToMovieMask([int]$RawHeld) {
    $mask = $RawHeld -band 0x8F
    if (($RawHeld -band 0x70) -ne 0) { $mask = $mask -bor 0x10 }
    return $mask
}

# The SS recorder's aux stream is part of the comparison contract. Completed
# RunObjects passes bind forward to the first non-lag observation after return,
# except the single terminal pass owned by the raw finish observation; each
# event also names and carries the physical BK2 sample consumed at entry.
function Assert-SsAuxCoverage([string]$TraceDir) {
    $events = Get-AuxEvents $TraceDir
    $rows = Get-PhysicsRows $TraceDir
    $types = New-Object System.Collections.Generic.HashSet[string]
    $controlInitial = $false
    $controlUnlock = $false
    $runObjectsEndCount = 0
    $expectedRunObjectsSequence = 0
    $delayedRunObjectsPassCount = 0
    $terminalFinishPassCount = 0
    $runObjectsPassesByFrame = @{}
    $metadata = Get-Content -LiteralPath (Join-Path $TraceDir "metadata.json") -Raw | ConvertFrom-Json
    $bk2Path = Join-Path $TraceDir $metadata.source_bk2
    $bk2Pairs = Get-Bk2SsInputMaskPairs $bk2Path
    $previousP1Held = $null
    $previousP2Held = $null
    $previousInputSampleSequence = $null
    $stageFinishedEvents = @($events | Where-Object { $_.type -eq "stage_finished" })
    if ($stageFinishedEvents.Count -ne 1) {
        throw "SS aux stream must contain exactly one stage_finished event; got $($stageFinishedEvents.Count)"
    }
    $stageFinishedEvent = $stageFinishedEvents[0]
    $stageFinishedFrame = [int]$stageFinishedEvent.frame
    $checkpointEvents = @($events | Where-Object { $_.type -eq "checkpoint" })
    $resultsStartedEvents = @($events | Where-Object { $_.type -eq "results_started" })
    if ($resultsStartedEvents.Count -ne 1) {
        throw "SS aux stream must contain exactly one results_started event; got $($resultsStartedEvents.Count)"
    }
    $resultsStartedEvent = $resultsStartedEvents[0]
    $finishObservation = [int]$stageFinishedEvent.observed_frame
    $matchingFinishCheckpoints = @($checkpointEvents | Where-Object {
        [int]$_.frame -eq $finishObservation
    })
    if ($matchingFinishCheckpoints.Count -ne 1) {
        throw "stage_finished must identify exactly one raw checkpoint observation"
    }
    if ([int]$resultsStartedEvent.frame -le $stageFinishedFrame) {
        throw "results_started must follow the logical stage_finished boundary"
    }
    if ([int]$resultsStartedEvent.frame -ge $rows.Count) {
        throw "results_started must leave a recorded results tail"
    }
    if ($stageFinishedFrame -lt 0 -or $stageFinishedFrame -ge $rows.Count) {
        throw "stage_finished frame $stageFinishedFrame is outside physics row range"
    }
    $stageFinishedColumns = $rows[$stageFinishedFrame].Split(",")
    if ($stageFinishedColumns.Length -lt 4 -or [int]$stageFinishedColumns[3] -ne 0) {
        throw "stage_finished frame $stageFinishedFrame is not a logical non-lag observation"
    }
    $requiredRunObjectsFields = @(
        "pass_sequence", "first_eligible_frame", "completion_cursor_frame",
        "input_sample_frame", "input_sample_bk2_frame", "input_sample_sequence",
        "previous_input_sample_frame", "previous_input_sample_bk2_frame",
        "input_source", "started_at_input_sample", "p1_held", "p2_held",
        "previous_p1_held", "previous_p2_held",
        "speed_factor", "track_anim", "track_anim_frame", "track_drawing_index",
        "track_orientation", "track_duration_timer", "current_segment",
        "player_anim_frame_timer", "rings_togo_bcd", "check_rings_flag",
        "tails_control_counter", "swap_positions_flag",
        "sonic_present", "sonic_ss_x", "sonic_ss_x_sub", "sonic_ss_y",
        "sonic_ss_y_sub", "sonic_ss_z", "sonic_angle", "sonic_routine",
        "sonic_routine_secondary", "sonic_status", "sonic_anim",
        "sonic_anim_frame", "sonic_rings_bcd", "sonic_hurt_timer",
        "sonic_slide_timer", "sonic_flip_timer",
        "tails_present", "tails_ss_x", "tails_ss_x_sub", "tails_ss_y",
        "tails_ss_y_sub", "tails_ss_z", "tails_angle", "tails_routine",
        "tails_routine_secondary", "tails_status", "tails_anim",
        "tails_anim_frame", "tails_rings_bcd", "tails_hurt_timer",
        "tails_slide_timer", "tails_flip_timer"
    )

    foreach ($event in $events) {
        if ($null -eq $event.type) { continue }
        [void]$types.Add([string]$event.type)
        if ($event.type -eq "control_state") {
            if ([int]$event.frame -eq 0 -and [int]$event.started -eq 0) {
                $controlInitial = $true
            }
            if ([int]$event.started -ne 0) {
                $controlUnlock = $true
            }
        } elseif ($event.type -eq "run_objects_end") {
            $runObjectsEndCount++
            $frame = [int]$event.frame
            $isTerminalFinishPass = $frame -eq $finishObservation
            if ($isTerminalFinishPass) {
                $terminalFinishPassCount++
            }
            if ($null -ne $stageFinishedFrame -and $frame -gt $stageFinishedFrame -and
                    -not $isTerminalFinishPass) {
                throw "run_objects_end frame $frame occurs after stage_finished frame $stageFinishedFrame"
            }
            if ([int]$event.pass_sequence -ne $expectedRunObjectsSequence) {
                throw "run_objects_end sequence discontinuity: expected $expectedRunObjectsSequence got $($event.pass_sequence)"
            }
            $expectedRunObjectsSequence++
            if ([int]$event.first_eligible_frame -gt $frame) {
                throw "run_objects_end frame $frame precedes first eligible frame $($event.first_eligible_frame)"
            }
            $completionCursor = [int]$event.completion_cursor_frame
            if ($completionCursor -gt $frame) {
                throw "run_objects_end frame $frame precedes completion cursor $completionCursor"
            }
            if ($completionCursor -lt [int]$event.first_eligible_frame) {
                throw "run_objects_end completion cursor $completionCursor precedes entry $($event.first_eligible_frame)"
            }
            $firstEligibleAtOrAfterCompletion = $completionCursor
            while ($firstEligibleAtOrAfterCompletion -lt $rows.Count) {
                $candidateColumns = $rows[$firstEligibleAtOrAfterCompletion].Split(",")
                if (($candidateColumns.Length -ge 4 -and [int]$candidateColumns[3] -eq 0) -or
                        $firstEligibleAtOrAfterCompletion -eq $finishObservation) {
                    break
                }
                $firstEligibleAtOrAfterCompletion++
            }
            if ($firstEligibleAtOrAfterCompletion -ne $frame) {
                throw "run_objects_end frame $frame is not first eligible observation $firstEligibleAtOrAfterCompletion at/after completion $completionCursor"
            }
            $inputSampleFrame = [int]$event.input_sample_frame
            if ($inputSampleFrame -gt $completionCursor) {
                throw "run_objects_end input sample $inputSampleFrame follows pass completion"
            }
            if ($completionCursor -lt $frame) {
                $delayedRunObjectsPassCount++
            }
            if ($runObjectsPassesByFrame.ContainsKey($frame)) {
                $runObjectsPassesByFrame[$frame]++
            } else {
                $runObjectsPassesByFrame[$frame] = 1
            }
            if ($frame -lt 0 -or $frame -ge $rows.Count) {
                throw "run_objects_end frame $frame is outside physics row range"
            }
            $columns = $rows[$frame].Split(",")
            if ($columns.Length -lt 4 -or
                    ([int]$columns[3] -ne 0 -and -not $isTerminalFinishPass)) {
                throw "run_objects_end frame $frame is not associated with a non-lag logical row"
            }
            $propertyNames = @($event.PSObject.Properties.Name)
            foreach ($required in $requiredRunObjectsFields) {
                if ($propertyNames -notcontains $required) {
                    throw "run_objects_end frame $frame is missing required field '$required'"
                }
            }
            $p1Held = [int]$event.p1_held
            $p2Held = [int]$event.p2_held
            if ([string]$event.input_source -ne "vint_s2ss_read_joypads") {
                throw "run_objects_end has unknown input source '$($event.input_source)'"
            }
            if ([int]$event.started_at_input_sample -eq 0) {
                throw "run_objects_end includes the SpecialStage_Started transition pass"
            }
            if ($null -ne $previousP1Held -and [int]$event.previous_p1_held -ne $previousP1Held) {
                throw "run_objects_end P1 held chain discontinuity at sequence $($event.pass_sequence)"
            }
            if ($null -ne $previousP2Held -and [int]$event.previous_p2_held -ne $previousP2Held) {
                throw "run_objects_end P2 held chain discontinuity at sequence $($event.pass_sequence)"
            }
            $previousP1Held = $p1Held
            $previousP2Held = $p2Held
            $inputSampleSequence = [int]$event.input_sample_sequence
            if ($null -ne $previousInputSampleSequence -and
                    $inputSampleSequence -ne $previousInputSampleSequence + 1) {
                throw "run_objects_end input sample sequence is not contiguous at pass $($event.pass_sequence)"
            }
            $previousInputSampleSequence = $inputSampleSequence
            $bk2Index = [int]$event.input_sample_bk2_frame
            if ($bk2Index -ne [int]$metadata.bk2_frame_offset + $inputSampleFrame) {
                throw "run_objects_end input sample relative/absolute identity mismatch at pass $($event.pass_sequence)"
            }
            $previousInputSampleFrame = [int]$event.previous_input_sample_frame
            $previousInputBk2Index = [int]$event.previous_input_sample_bk2_frame
            if ($previousInputBk2Index -ne [int]$metadata.bk2_frame_offset + $previousInputSampleFrame) {
                throw "run_objects_end previous input sample relative/absolute identity mismatch at pass $($event.pass_sequence)"
            }
            if ($bk2Index -lt 0 -or $bk2Index -ge $bk2Pairs.Length) {
                throw "run_objects_end input sample needs missing BK2 row $bk2Index"
            }
            if ($previousInputBk2Index -lt 0 -or $previousInputBk2Index -ge $bk2Pairs.Length) {
                throw "run_objects_end previous input sample needs missing BK2 row $previousInputBk2Index"
            }
            $normalizedP1 = Convert-SsRawHeldToMovieMask $p1Held
            $normalizedP2 = Convert-SsRawHeldToMovieMask $p2Held
            if ($normalizedP1 -ne $bk2Pairs[$bk2Index].P1) {
                throw ("run_objects_end P1 sample mismatch at sequence {0}, BK2 {1}: raw=0x{2:X2} normalized=0x{3:X2} bk2=0x{4:X2}" -f `
                    $event.pass_sequence, $bk2Index, $p1Held, $normalizedP1, $bk2Pairs[$bk2Index].P1)
            }
            if ($normalizedP2 -ne $bk2Pairs[$bk2Index].P2) {
                throw ("run_objects_end P2 sample mismatch at sequence {0}, BK2 {1}: raw=0x{2:X2} normalized=0x{3:X2} bk2=0x{4:X2}" -f `
                    $event.pass_sequence, $bk2Index, $p2Held, $normalizedP2, $bk2Pairs[$bk2Index].P2)
            }
            $normalizedPreviousP1 = Convert-SsRawHeldToMovieMask ([int]$event.previous_p1_held)
            $normalizedPreviousP2 = Convert-SsRawHeldToMovieMask ([int]$event.previous_p2_held)
            if ($normalizedPreviousP1 -ne $bk2Pairs[$previousInputBk2Index].P1) {
                throw ("run_objects_end previous P1 sample mismatch at sequence {0}, BK2 {1}" -f `
                    $event.pass_sequence, $previousInputBk2Index)
            }
            if ($normalizedPreviousP2 -ne $bk2Pairs[$previousInputBk2Index].P2) {
                throw ("run_objects_end previous P2 sample mismatch at sequence {0}, BK2 {1}" -f `
                    $event.pass_sequence, $previousInputBk2Index)
            }
        }
    }

    foreach ($required in @("control_state", "run_objects_end", "stage_finished", "checkpoint", "message_state", "results_started")) {
        if (-not $types.Contains($required)) {
            throw "SS aux stream missing required event family '$required'"
        }
    }
    if (-not $controlInitial) {
        throw "SS aux stream missing frame-0 control_state started=0 sample"
    }
    if (-not $controlUnlock) {
        throw "SS aux stream missing control_state unlock transition"
    }
    $multiPassObservationFrames = @(
        $runObjectsPassesByFrame.GetEnumerator() | Where-Object { $_.Value -gt 1 }
    ).Count
    if ($runObjectsEndCount -le 2900) {
        throw "SS aux stream has insufficient run_objects_end coverage: $runObjectsEndCount"
    }
    if ($multiPassObservationFrames -le 0) {
        throw "SS aux stream lost multi-pass observation frames (possible pass dedupe regression)"
    }
    if ($delayedRunObjectsPassCount -le 0) {
        throw "SS aux stream lost delayed RunObjects passes (possible eligibility regression)"
    }
    if ($terminalFinishPassCount -ne 1) {
        throw "raw finish observation must own exactly one terminal RunObjects pass; got $terminalFinishPassCount"
    }
    Write-Host ("SS aux coverage verified ({0} passes, {1} multi-pass observations, {2} delayed passes)" -f `
        $runObjectsEndCount, $multiPassObservationFrames, $delayedRunObjectsPassCount)
}

function Assert-SsTraceOutput([object]$Route, [string]$TraceDir, [string]$Bk2Path) {
    Assert-CompressedPayloads $TraceDir
    Assert-SsMetadata $Route $TraceDir
    Assert-SsBk2InputAlignment $Bk2Path $TraceDir
    Assert-SsAuxCoverage $TraceDir
}

function Assert-ZoneActCoverage([object]$Route, [string]$TraceDir) {
    $events = Get-AuxEvents $TraceDir
    $seenActs = New-Object System.Collections.Generic.HashSet[int]
    foreach ($event in $events) {
        if ($event.event -eq "zone_act_state" -and [int]$event.game_mode -eq 12) {
            [void]$seenActs.Add([int]$event.actual_act)
        }
    }

    foreach ($expectedAct in @($Route.ExpectedActs)) {
        if (-not $seenActs.Contains([int]$expectedAct)) {
            throw "Missing zone_act_state coverage for $($Route.Route) act $([int]$expectedAct + 1)"
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
    Assert-ZoneActCoverage $Route $TraceDir
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
$bizhawkToolsDir = Resolve-RepoPath "tools/bizhawk"
$traceOutput = [System.IO.Path]::GetFullPath(
    (Join-Path $bizhawkToolsDir "trace_output"))
$runBizhawkLuaBat = Resolve-RepoPath "tools/bizhawk/run_bizhawk_lua.bat"
$countBk2FramesScript = Resolve-RepoPath "tools/bizhawk/count_bk2_input_frames.ps1"
$compressScript = Resolve-RepoPath "tools/traces/compress-traces.ps1"
$ssLuaScript = Resolve-RepoPath "tools/bizhawk/s2_ss_trace_recorder.lua"

foreach ($route in $selectedRoutes) {
    $bk2Path = Join-Path $moviesFullPath $route.Bk2
    if (-not (Test-Path -LiteralPath $bk2Path)) {
        throw "Missing BK2 for route $($route.Route): $bk2Path"
    }

    $resolvedTraceOutput = [System.IO.Path]::GetFullPath($traceOutput)
    $expectedTraceOutput = [System.IO.Path]::GetFullPath(
        (Join-Path $bizhawkToolsDir "trace_output"))
    if ($resolvedTraceOutput -ne $expectedTraceOutput) {
        throw "Refusing to clean unexpected trace output path: $resolvedTraceOutput"
    }
    if (Test-Path -LiteralPath $traceOutput) {
        Remove-Item -LiteralPath $traceOutput -Recurse -Force
    }
    New-Item -ItemType Directory -Path $traceOutput -Force | Out-Null

    Write-Host "=== Recording S2 route $($route.Route) from $($route.Bk2) ==="
    if ($route.Profile -eq "s2_special_stage") {
        $frameCount = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $countBk2FramesScript -Bk2Path $bk2Path
        if (-not $frameCount) {
            throw "Failed to count BK2 input frames for $bk2Path"
        }
        $env:OGGF_BK2_BASENAME = $route.Bk2
        $env:OGGF_BK2_FRAME_COUNT = [string]$frameCount
        $env:OGGF_TRACE_OUTPUT_DIR = $resolvedTraceOutput

        # Invoke run_bizhawk_lua.bat directly (not record_s2_trace.bat, which
        # hardcodes the level recorder script s2_trace_recorder.lua).
        & $runBizhawkLuaBat $ssLuaScript $bk2Path $romFullPath
        if ($LASTEXITCODE -ne 0) {
            throw "run_bizhawk_lua.bat failed for $($route.Route) with exit code $LASTEXITCODE"
        }

        if (Test-Path -LiteralPath (Join-Path $traceOutput "metadata.json")) {
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $compressScript $traceOutput -ThresholdBytes 0
            if ($LASTEXITCODE -ne 0) {
                throw "Trace compression failed for $($route.Route) with exit code $LASTEXITCODE"
            }
        } else {
            throw "No trace output found in $traceOutput for route $($route.Route)"
        }
    } else {
        $env:OGGF_TRACE_GAMEPLAY_SEGMENT = [string]$route.Segment
        & $recordScript $romFullPath $bk2Path "level_gated_reset_aware"
        if ($LASTEXITCODE -ne 0) {
            throw "record_s2_trace.bat failed for $($route.Route) with exit code $LASTEXITCODE"
        }
    }

    $targetDir = Join-Path $outputFullPath $route.Route
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $traceOutput "metadata.json") -Destination $targetDir -Force
    Copy-Item -LiteralPath (Join-Path $traceOutput "physics.csv.gz") -Destination $targetDir -Force
    Copy-Item -LiteralPath (Join-Path $traceOutput "aux_state.jsonl.gz") -Destination $targetDir -Force
    Copy-Item -LiteralPath $bk2Path -Destination $targetDir -Force

    if ($route.Profile -eq "s2_special_stage") {
        Assert-SsTraceOutput $route $targetDir (Join-Path $targetDir $route.Bk2)
    } else {
        Normalize-PhysicsInputFromBk2 (Join-Path $targetDir $route.Bk2) $targetDir
        Assert-TraceOutput $route $targetDir (Join-Path $targetDir $route.Bk2)
    }
    Write-Host "Route $($route.Route) validated into $targetDir"
}
