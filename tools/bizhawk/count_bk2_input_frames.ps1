param(
    [Parameter(Mandatory = $true)]
    [string]$Bk2Path
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Bk2Path).Path)
try {
    $entry = $zip.Entries | Where-Object { $_.FullName -eq "Input Log.txt" } | Select-Object -First 1
    $frameCount = 0
    if ($null -ne $entry) {
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try {
            while (($line = $reader.ReadLine()) -ne $null) {
                if ($line.StartsWith("|")) {
                    $frameCount++
                }
            }
        } finally {
            $reader.Dispose()
        }
    }
    [Console]::Write($frameCount)
} finally {
    $zip.Dispose()
}
