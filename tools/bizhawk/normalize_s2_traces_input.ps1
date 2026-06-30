[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string[]]$Routes,
    [string]$OutputRoot = "src/test/resources/traces/s2"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-TextMaybeGzip([string]$Path) {
    if ($Path.EndsWith(".gz", [StringComparison]::OrdinalIgnoreCase)) {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            $gzip = [System.IO.Compression.GZipStream]::new($stream, [System.IO.Compression.CompressionMode]::Decompress)
            try {
                $reader = [System.IO.StreamReader]::new($gzip, [System.Text.Encoding]::UTF8)
                try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
            } finally { $gzip.Dispose() }
        } finally { $stream.Dispose() }
    }
    return [System.IO.File]::ReadAllText($Path)
}

function Write-GzipText([string]$Path, [string]$Text) {
    $stream = [System.IO.File]::Create($Path)
    try {
        $gzip = [System.IO.Compression.GZipStream]::new($stream, [System.IO.Compression.CompressionLevel]::Optimal)
        try {
            $writer = [System.IO.StreamWriter]::new($gzip, [System.Text.UTF8Encoding]::new($false))
            try { $writer.Write($Text) } finally { $writer.Dispose() }
        } finally { $gzip.Dispose() }
    } finally { $stream.Dispose() }
}

function Resolve-TracePayload([string]$Dir, [string]$BaseName) {
    $plain = Join-Path $Dir $BaseName
    $gzip = "$plain.gz"
    if (Test-Path -LiteralPath $gzip) { return $gzip }
    if (Test-Path -LiteralPath $plain) { return $plain }
    throw "Missing trace payload $BaseName(.gz) in $Dir"
}

function Convert-Bk2P1FieldToMask([string]$P1) {
    if ($P1.Length -lt 8) { throw "Unexpected P1 input field '$P1'" }
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
        if ($null -eq $entry) { throw "Input Log.txt not found in $Bk2Path" }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try {
            $masks = New-Object System.Collections.Generic.List[int]
            while (($line = $reader.ReadLine()) -ne $null) {
                if (-not $line.StartsWith("|")) { continue }
                $parts = $line.Split("|")
                if ($parts.Length -lt 4) { throw "Unexpected BK2 input line: $line" }
                $masks.Add((Convert-Bk2P1FieldToMask $parts[2]))
            }
            return $masks.ToArray()
        } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
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
        if (-not $line -or $line.StartsWith("#") -or $line.StartsWith("frame,")) { continue }
        $bk2Index = $offset + $rowIndex
        if ($bk2Index -ge $masks.Length) {
            throw "Trace row $rowIndex needs BK2 input index $bk2Index, but movie has $($masks.Length) input rows"
        }
        $columns = $line.Split(",")
        if ($columns.Length -lt 2) { throw "Trace row $rowIndex has no input column: $line" }
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
    if (-not $text.EndsWith("`n")) { $text += "`n" }
    if ($path.EndsWith(".gz", [StringComparison]::OrdinalIgnoreCase)) {
        Write-GzipText $path $text
    } else {
        [System.IO.File]::WriteAllText($path, $text)
    }
    Write-Host "  ${TraceDir}: $changed rows normalized"
    return $changed
}

foreach ($route in $Routes) {
    $traceDir = Join-Path $OutputRoot $route
    $bk2Path = Get-ChildItem -LiteralPath $traceDir -Filter "*.bk2" | Select-Object -First 1
    if ($null -eq $bk2Path) {
        Write-Host "  ${traceDir}: no .bk2 file, skipping"
        continue
    }
    Write-Host "Normalizing $route from $($bk2Path.Name)..."
    Normalize-PhysicsInputFromBk2 $bk2Path.FullName $traceDir
}
