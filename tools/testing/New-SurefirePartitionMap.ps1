[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $NextClassInventory,
    [Parameter(Mandatory)] [string] $DevelopClassInventory,
    [Parameter(Mandatory)] [string] $CandidateClassInventory,
    [Parameter(Mandatory)] [string] $OutputPath,
    [ValidateRange(1, 75)] [int] $SlotSize = 75
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-OrdinalSorted {
    param([string[]] $Values)
    $copy = [System.Collections.Generic.List[string]]::new()
    foreach ($value in $Values) { $copy.Add($value) }
    $copy.Sort([System.StringComparer]::Ordinal)
    return $copy.ToArray()
}

function Read-ClassInventory {
    param([string] $Path, [string] $Tree)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Tree source-class inventory does not exist: $Path"
    }
    $classes = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($raw in [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Path).Path)) {
        $className = $raw.Trim()
        if ($className.Length -eq 0) { continue }
        if ($className.IndexOfAny([char[]]@("`t", "`r", "`n", ',')) -ge 0) {
            throw "Invalid class name in $Tree source-class inventory: [$className]"
        }
        if (-not $classes.Add($className)) {
            throw "Duplicate class in $Tree source-class inventory: $className"
        }
    }
    return $classes
}

$nextClasses = Read-ClassInventory $NextClassInventory 'next'
$developClasses = Read-ClassInventory $DevelopClassInventory 'develop'
$candidateClasses = Read-ClassInventory $CandidateClassInventory 'candidate'
$union = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($classes in @($nextClasses, $developClasses, $candidateClasses)) {
    foreach ($className in $classes) { [void]$union.Add($className) }
}
if ($union.Count -eq 0) {
    throw 'Cannot create a partition map from an empty union with no classes'
}

$sortedUnion = Get-OrdinalSorted @($union)
$assignmentCounts = [System.Collections.Generic.Dictionary[string, int]]::new([System.StringComparer]::Ordinal)
foreach ($className in $sortedUnion) { $assignmentCounts.Add($className, 0) }
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("slot`tunion_selector`tnext_selector`tdevelop_selector`tcandidate_selector")
$slotCount = [int][System.Math]::Ceiling($sortedUnion.Count / [double]$SlotSize)
for ($slotIndex = 0; $slotIndex -lt $slotCount; $slotIndex++) {
    $start = $slotIndex * $SlotSize
    $count = [System.Math]::Min($SlotSize, $sortedUnion.Count - $start)
    $slotClasses = @($sortedUnion[$start..($start + $count - 1)])
    foreach ($className in $slotClasses) { $assignmentCounts[$className]++ }
    $nextSelector = @($slotClasses | Where-Object { $nextClasses.Contains($_) }) -join ','
    $developSelector = @($slotClasses | Where-Object { $developClasses.Contains($_) }) -join ','
    $candidateSelector = @($slotClasses | Where-Object { $candidateClasses.Contains($_) }) -join ','
    $slot = ($slotIndex + 1).ToString('000', [System.Globalization.CultureInfo]::InvariantCulture)
    $lines.Add(@($slot, ($slotClasses -join ','), $nextSelector, $developSelector, $candidateSelector) -join "`t")
}

$invalidAssignments = @($assignmentCounts.GetEnumerator() | Where-Object { $_.Value -ne 1 })
if ($invalidAssignments.Count -gt 0) {
    throw "Union classes assigned to zero or multiple slots: $((Get-OrdinalSorted @($invalidAssignments.Name)) -join ', ')"
}

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}
[System.IO.File]::WriteAllText($OutputPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
Write-Host "Created $slotCount deterministic Surefire union partition slots for $($sortedUnion.Count) classes in $OutputPath"
