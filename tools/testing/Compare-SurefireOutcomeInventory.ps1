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
    'identity', 'baseline_source', 'baseline_outcome', 'candidate_outcome',
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

function ConvertTo-TsvField {
    param([AllowEmptyString()] [string] $Value)
    $builder = [System.Text.StringBuilder]::new()
    $firstNonSpace = 0
    while ($firstNonSpace -lt $Value.Length -and $Value[$firstNonSpace] -eq ' ') { $firstNonSpace++ }
    $lastNonSpace = $Value.Length - 1
    while ($lastNonSpace -ge 0 -and $Value[$lastNonSpace] -eq ' ') { $lastNonSpace-- }
    for ($index = 0; $index -lt $Value.Length; $index++) {
        switch ($Value[$index]) {
            '\' { [void]$builder.Append('\\') }
            '"' { [void]$builder.Append('\q') }
            "`t" { [void]$builder.Append('\t') }
            "`r" { [void]$builder.Append('\r') }
            "`n" { [void]$builder.Append('\n') }
            ' ' {
                if ($index -lt $firstNonSpace -or $index -gt $lastNonSpace) { [void]$builder.Append('\s') }
                else { [void]$builder.Append(' ') }
            }
            default { [void]$builder.Append($Value[$index]) }
        }
    }
    return $builder.ToString()
}

function ConvertFrom-TsvField {
    param([AllowEmptyString()] [string] $Value, [string] $FieldName)
    $builder = [System.Text.StringBuilder]::new()
    for ($index = 0; $index -lt $Value.Length; $index++) {
        $character = $Value[$index]
        if ($character -ne '\') {
            [void]$builder.Append($character)
            continue
        }
        if (++$index -ge $Value.Length) {
            throw "TSV field $FieldName has a dangling escape"
        }
        switch ($Value[$index]) {
            '\' { [void]$builder.Append('\') }
            'q' { [void]$builder.Append('"') }
            't' { [void]$builder.Append("`t") }
            'r' { [void]$builder.Append("`r") }
            'n' { [void]$builder.Append("`n") }
            's' { [void]$builder.Append(' ') }
            default { throw "TSV field $FieldName has an invalid escape: \$($Value[$index])" }
        }
    }
    return $builder.ToString()
}

function Read-Inventory {
    param([string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Surefire outcome inventory does not exist: $Path"
    }
    $reader = [System.IO.StreamReader]::new($Path, [System.Text.Encoding]::UTF8, $true)
    try {
        $header = $reader.ReadLine()
    }
    finally {
        $reader.Dispose()
    }
    $expectedHeader = $inventoryColumns -join "`t"
    if ($header -cne $expectedHeader) {
        throw "Surefire outcome inventory schema does not exactly match required columns in $Path"
    }
    $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $Path)
    if ($rows.Count -eq 0) {
        throw "Surefire outcome inventory is empty: $Path"
    }
    $byIdentity = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    foreach ($encodedRow in $rows) {
        $decoded = [ordered]@{}
        foreach ($column in $inventoryColumns) {
            $decoded[$column] = ConvertFrom-TsvField ([string]$encodedRow.$column) $column
        }
        $row = [pscustomobject]$decoded
        $identity = [string]$row.identity
        if ($identity.Length -eq 0 -or ([string]$row.class).Length -eq 0 -or ([string]$row.method).Length -eq 0) {
            throw "Surefire outcome inventory $Path contains an empty identity"
        }
        $expectedIdentity = ([string]$row.class) + '#' + ([string]$row.method)
        if ($identity -cne $expectedIdentity) {
            throw "Surefire identity does not equal decoded class + # + method in $Path`: $identity"
        }
        if ($byIdentity.ContainsKey($identity)) {
            throw "Duplicate Surefire outcome identity in $Path`: $identity"
        }
        if (@('PASS', 'FAILURE', 'ERROR', 'SKIPPED') -cnotcontains [string]$row.outcome) {
            throw "Invalid Surefire outcome for $identity in $Path`: $($row.outcome)"
        }
        if ($redOutcomes.Contains([string]$row.outcome)) {
            $expectedRedKind = if ([string]$row.outcome -ceq 'FAILURE') { 'failure' } else { 'error' }
            if ([string]$row.red_kind -cne $expectedRedKind) {
                throw "red_kind does not match $($row.outcome) in $Path`: $identity"
            }
            if ([string]::IsNullOrWhiteSpace([string]$row.exception_type) -or
                ([string]$row.red_body_sha256) -notmatch '^[0-9a-f]{64}$') {
                throw "Red outcome lacks a deterministic signature in $Path`: $identity"
            }
            $byteCount = 0UL
            if (-not [UInt64]::TryParse(
                [string]$row.red_body_bytes,
                [System.Globalization.NumberStyles]::None,
                [System.Globalization.CultureInfo]::InvariantCulture,
                [ref]$byteCount
            )) {
                throw "Red body byte count must be a non-negative integer in $Path`: $identity"
            }
            if ($byteCount -eq 0 -and [string]$row.red_body_sha256 -cne 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855') {
                throw "Zero-byte red body requires the SHA-256 empty-body hash in $Path`: $identity"
            }
        }
        else {
            foreach ($metadataColumn in @('red_kind', 'exception_type', 'normalized_message', 'red_body_bytes', 'red_body_sha256')) {
                if (([string]$row.$metadataColumn).Length -ne 0) {
                    throw "Non-red outcome must have empty red metadata in $Path`: $identity"
                }
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
    $reader = [System.IO.StreamReader]::new($Path, [System.Text.Encoding]::UTF8, $true)
    try { $header = $reader.ReadLine() } finally { $reader.Dispose() }
    if ($header -cne "identity`treason") {
        throw 'Reviewed-removal file schema requires exactly identity and reason columns'
    }
    $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $Path)
    foreach ($row in $rows) {
        $identity = ConvertFrom-TsvField ([string]$row.identity) 'identity'
        $reason = ConvertFrom-TsvField ([string]$row.reason) 'reason'
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

$parents = [System.Collections.Generic.List[object]]::new()
$parentIdentitySet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$parentSources = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$expandedParentPaths = @($ParentInventoryPath | ForEach-Object { $_.Split([System.IO.Path]::PathSeparator, [System.StringSplitOptions]::RemoveEmptyEntries) })
foreach ($path in $expandedParentPaths) {
    $resolved = (Resolve-Path -LiteralPath $path).Path
    $inventory = Read-Inventory $resolved
    $source = [System.IO.Path]::GetFileNameWithoutExtension($resolved)
    if (-not $parentSources.Add($source)) {
        throw "Parent inventory source names must be unique: $source"
    }
    $parents.Add([pscustomobject]@{
        Name = $source
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
    $candidateRow = if ($candidate.ContainsKey($identity)) { $candidate[$identity] } else { $null }
    $candidateOutcome = if ($null -eq $candidateRow) { 'ABSENT' } else { [string]$candidateRow.outcome }
    $candidateRedSignature = Get-RedSignature $candidateRow
    $owningParents = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    foreach ($parent in $parents) {
        if ($parent.Rows.ContainsKey($identity)) { $owningParents.Add($parent.Name, $parent) }
    }

    if ($owningParents.Count -eq 0) {
        $resultRows.Add([pscustomobject]@{
            identity = $identity
            baseline_source = 'CANDIDATE_ONLY'
            baseline_outcome = 'ABSENT'
            candidate_outcome = $candidateOutcome
            baseline_red_signature = ''
            candidate_red_signature = $candidateRedSignature
            classification = 'CANDIDATE_ONLY'
            owner = ''
            disposition = ''
        })
        continue
    }

    foreach ($source in (Get-OrdinalSorted @($owningParents.Keys))) {
        $baselineRow = $owningParents[$source].Rows[$identity]
        $baselineOutcome = [string]$baselineRow.outcome
        $baselineRedSignature = Get-RedSignature $baselineRow
        $classification = 'MATCH'
        $disposition = ''

        if ($candidateOutcome -eq 'ABSENT' -and $removals.ContainsKey($identity)) {
            $classification = 'APPROVED_REMOVAL'
            $disposition = $removals[$identity]
        }
        elseif ($candidateOutcome -eq 'ABSENT') {
            if ($baselineOutcome -ceq 'PASS') {
                $classification = 'REGRESSION_PASS_TO_ABSENT'
            }
            else {
                $classification = 'REGRESSION_TO_ABSENT'
            }
        }
        elseif ($baselineOutcome -ceq 'PASS' -and $candidateOutcome -cne 'PASS') {
            $classification = "REGRESSION_PASS_TO_$candidateOutcome"
        }
        elseif ($baselineOutcome -ceq 'SKIPPED' -and $redOutcomes.Contains($candidateOutcome)) {
            $classification = "REGRESSION_SKIPPED_TO_$candidateOutcome"
        }
        elseif ($redOutcomes.Contains($baselineOutcome)) {
            if ($candidateOutcome -eq 'PASS') {
                $classification = 'BASELINE_RED_RESOLVED'
            }
            elseif (-not $redOutcomes.Contains($candidateOutcome)) {
                $classification = "RED_CHANGED_TO_$candidateOutcome"
            }
            elseif ($baselineOutcome -cne $candidateOutcome) {
                $classification = 'RED_KIND_CHANGED'
            }
            elseif ($baselineRedSignature -cne $candidateRedSignature) {
                $classification = 'RED_SIGNATURE_CHANGED_REQUIRES_PAIRED_RERUN'
            }
        }
        elseif ($baselineOutcome -cne $candidateOutcome) {
            $classification = "OUTCOME_CHANGED_TO_$candidateOutcome"
        }

        if ($classification -match '^(REGRESSION_|RED_KIND_CHANGED$|RED_SIGNATURE_CHANGED_|RED_CHANGED_)') {
            $blocking.Add("$source/$identity=$classification")
        }
        $resultRows.Add([pscustomobject]@{
            identity = $identity
            baseline_source = $source
            baseline_outcome = $baselineOutcome
            candidate_outcome = $candidateOutcome
            baseline_red_signature = $baselineRedSignature
            candidate_red_signature = $candidateRedSignature
            classification = $classification
            owner = ''
            disposition = $disposition
        })
    }
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
        ConvertTo-TsvField ([string]$row.$column)
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
