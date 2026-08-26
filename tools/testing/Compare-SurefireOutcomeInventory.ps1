[CmdletBinding()]
param(
    [Parameter(Mandatory, ValueFromRemainingArguments)] [string[]] $ParentInventoryPath,
    [Parameter(Mandatory)] [string] $CandidateInventoryPath,
    [Parameter(Mandatory)] [string] $OutputPath,
    [string] $ReviewedRemovalPath = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$inventoryColumns = @(
    'identity', 'class', 'method', 'outcome', 'red_kind', 'exception_type',
    'normalized_message', 'red_body_bytes', 'red_body_sha256', 'report'
)
$outputColumns = @(
    'identity', 'baseline_sources', 'baseline_outcome', 'candidate_outcome',
    'baseline_red_signature', 'candidate_red_signature', 'classification',
    'owner', 'disposition'
)
$redOutcomes = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
[void]$redOutcomes.Add('FAILURE')
[void]$redOutcomes.Add('ERROR')

function Get-OrdinalSorted {
    param([string[]] $Values)
    $copy = [System.Collections.Generic.List[string]]::new()
    foreach ($value in $Values) { $copy.Add($value) }
    $copy.Sort([System.StringComparer]::Ordinal)
    return $copy.ToArray()
}

function Get-RedSignature {
    param($Row)
    if ($null -eq $Row -or -not $redOutcomes.Contains([string]$Row.outcome)) {
        return ''
    }
    return @(
        [string]$Row.red_kind,
        [string]$Row.exception_type,
        [string]$Row.normalized_message,
        [string]$Row.red_body_bytes,
        [string]$Row.red_body_sha256
    ) -join '|'
}

function Read-Inventory {
    param([string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Surefire outcome inventory does not exist: $Path"
    }
    $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $Path)
    if ($rows.Count -eq 0) {
        throw "Surefire outcome inventory is empty: $Path"
    }
    foreach ($column in $inventoryColumns) {
        if (-not ($rows[0].PSObject.Properties.Name -ccontains $column)) {
            throw "Surefire outcome inventory $Path lacks column: $column"
        }
    }
    $byIdentity = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    foreach ($row in $rows) {
        $identity = [string]$row.identity
        if ([string]::IsNullOrWhiteSpace($identity)) {
            throw "Surefire outcome inventory $Path contains an empty identity"
        }
        if ($byIdentity.ContainsKey($identity)) {
            throw "Duplicate Surefire outcome identity in $Path`: $identity"
        }
        if (@('PASS', 'FAILURE', 'ERROR', 'SKIPPED') -cnotcontains [string]$row.outcome) {
            throw "Invalid Surefire outcome for $identity in $Path`: $($row.outcome)"
        }
        if ($redOutcomes.Contains([string]$row.outcome)) {
            if ([string]::IsNullOrWhiteSpace([string]$row.red_kind) -or
                [string]::IsNullOrWhiteSpace([string]$row.exception_type) -or
                ([string]$row.red_body_sha256) -notmatch '^[0-9a-f]{64}$' -or
                ([string]$row.red_body_bytes) -notmatch '^\d+$') {
                throw "Red outcome lacks a deterministic signature in $Path`: $identity"
            }
        }
        $byIdentity.Add($identity, $row)
    }
    return $byIdentity
}

function Read-ReviewedRemovals {
    param([string] $Path)
    $removals = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $removals
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Reviewed-removal file does not exist: $Path"
    }
    $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $Path)
    foreach ($row in $rows) {
        if (-not ($row.PSObject.Properties.Name -ccontains 'identity') -or
            -not ($row.PSObject.Properties.Name -ccontains 'reason')) {
            throw 'Reviewed-removal file requires identity and reason columns'
        }
        $identity = ([string]$row.identity).Trim()
        $reason = ([string]$row.reason).Trim()
        if ($identity.Length -eq 0 -or $reason.Length -eq 0) {
            throw 'Every reviewed-removal entry must name an identity and a reason'
        }
        if ($removals.ContainsKey($identity)) {
            throw "Duplicate reviewed-removal identity: $identity"
        }
        $removals.Add($identity, $reason)
    }
    return $removals
}

function Escape-TsvField {
    param([AllowEmptyString()] [string] $Value, [string] $FieldName)
    if ($Value.Contains("`t") -or $Value.Contains("`r") -or $Value.Contains("`n")) {
        throw "TSV field $FieldName contains a tab or line break"
    }
    return $Value
}

$parents = [System.Collections.Generic.List[object]]::new()
$parentIdentitySet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$expandedParentPaths = @($ParentInventoryPath | ForEach-Object { $_.Split([System.IO.Path]::PathSeparator, [System.StringSplitOptions]::RemoveEmptyEntries) })
foreach ($path in $expandedParentPaths) {
    $resolved = (Resolve-Path -LiteralPath $path).Path
    $inventory = Read-Inventory $resolved
    $parents.Add([pscustomobject]@{
        Name = [System.IO.Path]::GetFileNameWithoutExtension($resolved)
        Path = $resolved
        Rows = $inventory
    })
    foreach ($identity in $inventory.Keys) { [void]$parentIdentitySet.Add($identity) }
}
if ($parents.Count -eq 0) {
    throw 'At least one parent inventory is required'
}
$candidate = Read-Inventory $CandidateInventoryPath
$removals = Read-ReviewedRemovals $ReviewedRemovalPath
$union = [System.Collections.Generic.HashSet[string]]::new($parentIdentitySet, [System.StringComparer]::Ordinal)
foreach ($identity in $candidate.Keys) { [void]$union.Add($identity) }

$resultRows = [System.Collections.Generic.List[object]]::new()
$blocking = [System.Collections.Generic.List[string]]::new()
foreach ($identity in (Get-OrdinalSorted @($union))) {
    $baselineRows = [System.Collections.Generic.List[object]]::new()
    foreach ($parent in $parents) {
        if ($parent.Rows.ContainsKey($identity)) {
            $baselineRows.Add([pscustomobject]@{ Source = $parent.Name; Row = $parent.Rows[$identity] })
        }
    }
    $candidateRow = if ($candidate.ContainsKey($identity)) { $candidate[$identity] } else { $null }
    $candidateOutcome = if ($null -eq $candidateRow) { 'ABSENT' } else { [string]$candidateRow.outcome }
    $baselineSources = (@($baselineRows | ForEach-Object { $_.Source }) -join ',')
    $baselineOutcomes = @($baselineRows | ForEach-Object { [string]$_.Row.outcome } | Select-Object -Unique)
    $baselineOutcome = if ($baselineOutcomes.Count -eq 0) { 'ABSENT' } else { $baselineOutcomes -join ',' }
    $baselineSignatures = @($baselineRows | ForEach-Object { Get-RedSignature $_.Row } | Where-Object { $_.Length -gt 0 } | Select-Object -Unique)
    $baselineRedSignature = $baselineSignatures -join ','
    $candidateRedSignature = Get-RedSignature $candidateRow
    $classification = 'MATCH'
    $owner = ''
    $disposition = ''

    if ($baselineRows.Count -eq 0) {
        $classification = 'CANDIDATE_ONLY'
    }
    elseif ($candidateOutcome -eq 'ABSENT' -and $removals.ContainsKey($identity)) {
        $classification = 'APPROVED_REMOVAL'
        $disposition = $removals[$identity]
    }
    elseif ($candidateOutcome -eq 'ABSENT') {
        if ($baselineOutcomes -ccontains 'PASS') {
            $classification = 'REGRESSION_PASS_TO_ABSENT'
        }
        else {
            $classification = 'REGRESSION_TO_ABSENT'
        }
    }
    elseif (($baselineOutcomes -ccontains 'PASS') -and $candidateOutcome -ne 'PASS') {
        $classification = "REGRESSION_PASS_TO_$candidateOutcome"
    }
    elseif (($baselineOutcomes -ccontains 'SKIPPED') -and $redOutcomes.Contains($candidateOutcome)) {
        $classification = "REGRESSION_SKIPPED_TO_$candidateOutcome"
    }
    else {
        $baselineRedRows = @($baselineRows | Where-Object { $redOutcomes.Contains([string]$_.Row.outcome) })
        if ($baselineRedRows.Count -gt 0) {
            if ($candidateOutcome -eq 'PASS') {
                $classification = 'BASELINE_RED_RESOLVED'
            }
            elseif (-not $redOutcomes.Contains($candidateOutcome)) {
                $classification = "RED_CHANGED_TO_$candidateOutcome"
            }
            elseif (@($baselineRedRows | Where-Object { [string]$_.Row.outcome -ne $candidateOutcome }).Count -gt 0) {
                $classification = 'RED_KIND_CHANGED'
            }
            elseif (@($baselineRedRows | Where-Object { (Get-RedSignature $_.Row) -cne $candidateRedSignature }).Count -gt 0) {
                $classification = 'RED_SIGNATURE_CHANGED_REQUIRES_PAIRED_RERUN'
            }
        }
        elseif (($baselineOutcomes -join ',') -cne $candidateOutcome) {
            $classification = "OUTCOME_CHANGED_TO_$candidateOutcome"
        }
    }

    if ($classification -match '^(REGRESSION_|RED_KIND_CHANGED$|RED_SIGNATURE_CHANGED_|RED_CHANGED_)') {
        $blocking.Add("$identity=$classification")
    }
    $resultRows.Add([pscustomobject]@{
        identity = $identity
        baseline_sources = $baselineSources
        baseline_outcome = $baselineOutcome
        candidate_outcome = $candidateOutcome
        baseline_red_signature = $baselineRedSignature
        candidate_red_signature = $candidateRedSignature
        classification = $classification
        owner = $owner
        disposition = $disposition
    })
}

foreach ($identity in $removals.Keys) {
    if (-not $parentIdentitySet.Contains($identity)) {
        throw "Reviewed removal does not name a parent identity: $identity"
    }
    if ($candidate.ContainsKey($identity)) {
        throw "Reviewed removal identity is not absent from candidate: $identity"
    }
}

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add($outputColumns -join "`t")
foreach ($row in $resultRows) {
    $fields = foreach ($column in $outputColumns) {
        Escape-TsvField ([string]$row.$column) $column
    }
    $lines.Add($fields -join "`t")
}
$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}
[System.IO.File]::WriteAllText($OutputPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))

if ($blocking.Count -gt 0) {
    throw "Surefire outcome comparison found regressions or red changes requiring paired isolated reruns: $($blocking -join '; ')"
}
Write-Host "Compared $($resultRows.Count) ordinal Surefire identities into $OutputPath"
