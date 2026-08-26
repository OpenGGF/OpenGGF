[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $SourceClassInventory,
    [Parameter(Mandatory, ValueFromRemainingArguments)] [string[]] $ReportRoot,
    [Parameter(Mandatory)] [string] $OutputPath,
    [string] $CanonicalWorktree = '',
    [string] $SessionRoot = '',
    [string] $RunId = '',
    [string] $EmptyHelperAllowlist = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$columns = @(
    'identity', 'class', 'method', 'outcome', 'red_kind', 'exception_type',
    'normalized_message', 'red_body_bytes', 'red_body_sha256', 'report'
)

function Get-OrdinalSorted {
    param([string[]] $Values)
    $copy = [System.Collections.Generic.List[string]]::new()
    foreach ($value in $Values) {
        $copy.Add($value)
    }
    $copy.Sort([System.StringComparer]::Ordinal)
    return $copy.ToArray()
}

function Read-ClassInventory {
    param([string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Source class inventory does not exist: $Path"
    }
    $classes = [System.Collections.Generic.List[string]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($raw in [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Path).Path)) {
        $className = $raw
        if ($className.Length -eq 0) {
            continue
        }
        if ($className.IndexOfAny([char[]]@("`t", "`r", "`n")) -ge 0) {
            throw "Invalid class name in source inventory: [$className]"
        }
        if (-not $seen.Add($className)) {
            throw "Duplicate class in source inventory: $className"
        }
        $classes.Add($className)
    }
    if ($classes.Count -eq 0) {
        throw "Source class inventory contains no classes: $Path"
    }
    return $classes.ToArray()
}

function Read-HelperAllowlist {
    param([string] $Path, [System.Collections.Generic.HashSet[string]] $SelectedClasses)
    $helpers = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return ,$helpers
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Empty-helper allowlist does not exist: $Path"
    }
    $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $Path)
    foreach ($row in $rows) {
        if (-not ($row.PSObject.Properties.Name -ccontains 'class') -or
            -not ($row.PSObject.Properties.Name -ccontains 'reason')) {
            throw 'Empty-helper allowlist requires class and reason columns'
        }
        $className = ConvertFrom-TsvField ([string]$row.class) 'class'
        $reason = ConvertFrom-TsvField ([string]$row.reason) 'reason'
        if ($className.Length -eq 0 -or $reason.Length -eq 0) {
            throw 'Every empty-helper allowlist entry must name a class and a reason'
        }
        if (-not $SelectedClasses.Contains($className)) {
            throw "Empty-helper allowlist class is not selected: $className"
        }
        if (-not $helpers.Add($className)) {
            throw "Duplicate empty-helper allowlist class: $className"
        }
    }
    return ,$helpers
}

function Normalize-TokenizedText {
    param([AllowEmptyString()] [string] $Value)
    $normalized = $Value.Replace("`r`n", "`n").Replace("`r", "`n")
    $replacements = [System.Collections.Generic.List[object]]::new()
    if (-not [string]::IsNullOrEmpty($CanonicalWorktree)) {
        $replacements.Add([pscustomobject]@{ Token = $CanonicalWorktree; Replacement = '<WORKTREE>' })
    }
    if (-not [string]::IsNullOrEmpty($SessionRoot)) {
        $replacements.Add([pscustomobject]@{ Token = $SessionRoot; Replacement = '<SESSION_ROOT>' })
    }
    if (-not [string]::IsNullOrEmpty($RunId)) {
        $replacements.Add([pscustomobject]@{ Token = $RunId; Replacement = '<RUN_ID>' })
    }
    foreach ($replacement in ($replacements | Sort-Object { $_.Token.Length } -Descending)) {
        $normalized = $normalized.Replace($replacement.Token, $replacement.Replacement)
    }
    $iso8601 = '(?<![0-9A-Za-z])\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:?\d{2})?(?![0-9A-Za-z:+-])'
    return [System.Text.RegularExpressions.Regex]::Replace(
        $normalized,
        $iso8601,
        '<TIMESTAMP>',
        [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
}

function Get-BodySignature {
    param([string] $NormalizedBody)
    $encoding = [System.Text.UTF8Encoding]::new($false)
    $stream = [System.IO.MemoryStream]::new()
    try {
        $writer = [System.IO.StreamWriter]::new($stream, $encoding, 4096, $true)
        try {
            $writer.Write($NormalizedBody)
            $writer.Flush()
        }
        finally {
            $writer.Dispose()
        }
        $byteCount = $stream.Length
        $stream.Position = 0
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $sha.ComputeHash($stream)
        }
        finally {
            $sha.Dispose()
        }
        return [pscustomobject]@{
            Bytes = $byteCount.ToString([System.Globalization.CultureInfo]::InvariantCulture)
            Sha256 = [System.Convert]::ToHexString($hash).ToLowerInvariant()
        }
    }
    finally {
        $stream.Dispose()
    }
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
            't' { [void]$builder.Append("`t") }
            'r' { [void]$builder.Append("`r") }
            'n' { [void]$builder.Append("`n") }
            's' { [void]$builder.Append(' ') }
            default { throw "TSV field $FieldName has an invalid escape: \$($Value[$index])" }
        }
    }
    return $builder.ToString()
}

$sourceClasses = Read-ClassInventory $SourceClassInventory
$selected = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($className in $sourceClasses) {
    [void]$selected.Add($className)
}
$helpers = Read-HelperAllowlist $EmptyHelperAllowlist $selected

$reportFiles = [System.Collections.Generic.List[string]]::new()
$reportPathsSeen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$expandedReportRoots = @($ReportRoot | ForEach-Object { $_.Split([System.IO.Path]::PathSeparator, [System.StringSplitOptions]::RemoveEmptyEntries) })
foreach ($root in $expandedReportRoots) {
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        throw "Surefire report root does not exist: $root"
    }
    foreach ($file in [System.IO.Directory]::EnumerateFiles((Resolve-Path -LiteralPath $root).Path, '*.xml', [System.IO.SearchOption]::AllDirectories)) {
        $canonical = [System.IO.Path]::GetFullPath($file)
        if ($reportPathsSeen.Add($canonical)) {
            $reportFiles.Add($canonical)
        }
    }
}
if ($reportFiles.Count -eq 0) {
    throw 'No Surefire XML reports were found'
}

$rowsByIdentity = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
$reportedClasses = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$settings = [System.Xml.XmlReaderSettings]::new()
$settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
$settings.XmlResolver = $null
$settings.MaxCharactersFromEntities = 0
$settings.MaxCharactersInDocument = 512MB

foreach ($reportPath in (Get-OrdinalSorted $reportFiles.ToArray())) {
    $document = [System.Xml.XmlDocument]::new()
    $document.PreserveWhitespace = $true
    try {
        $reader = [System.Xml.XmlReader]::Create($reportPath, $settings)
        try {
            $document.Load($reader)
        }
        finally {
            $reader.Dispose()
        }
    }
    catch {
        throw "Malformed or prohibited Surefire XML report $reportPath`: $($_.Exception.Message)"
    }

    foreach ($testcase in $document.GetElementsByTagName('testcase')) {
        $className = [string]$testcase.GetAttribute('classname')
        $methodName = [string]$testcase.GetAttribute('name')
        if ($className.Length -eq 0 -or $methodName.Length -eq 0) {
            throw "Surefire testcase in $reportPath lacks classname or name"
        }
        if (-not $selected.Contains($className)) {
            throw "Surefire report contains unselected executable class: $className"
        }
        [void]$reportedClasses.Add($className)
        $identity = "$className#$methodName"
        if ($rowsByIdentity.ContainsKey($identity)) {
            throw "Duplicate Surefire testcase identity: $identity"
        }

        $failureElements = @($testcase.ChildNodes | Where-Object { $_.LocalName -eq 'failure' })
        $errorElements = @($testcase.ChildNodes | Where-Object { $_.LocalName -eq 'error' })
        $skipElements = @($testcase.ChildNodes | Where-Object { $_.LocalName -eq 'skipped' -or $_.LocalName -eq 'disabled' })
        if (($failureElements.Count + $errorElements.Count + $skipElements.Count) -gt 1) {
            throw "Surefire testcase has multiple semantic outcomes: $identity"
        }

        $outcome = 'PASS'
        $redKind = ''
        $exceptionType = ''
        $normalizedMessage = ''
        $redBodyBytes = ''
        $redBodySha256 = ''
        $redElement = $null
        if ($errorElements.Count -eq 1) {
            $outcome = 'ERROR'
            $redKind = 'error'
            $redElement = $errorElements[0]
        }
        elseif ($failureElements.Count -eq 1) {
            $outcome = 'FAILURE'
            $redKind = 'failure'
            $redElement = $failureElements[0]
        }
        elseif ($skipElements.Count -gt 0) {
            $outcome = 'SKIPPED'
        }

        if ($null -ne $redElement) {
            if ([string]::IsNullOrWhiteSpace($CanonicalWorktree) -or
                [string]::IsNullOrWhiteSpace($SessionRoot) -or
                [string]::IsNullOrWhiteSpace($RunId)) {
                throw "Red outcome normalization requires CanonicalWorktree, SessionRoot, and RunId: $identity"
            }
            $exceptionType = [string]$redElement.GetAttribute('type')
            $normalizedMessage = Normalize-TokenizedText ([string]$redElement.GetAttribute('message'))
            $normalizedBody = Normalize-TokenizedText ([string]$redElement.InnerText)
            if ($exceptionType.Length -eq 0) {
                throw "Red outcome lacks a deterministic signature: $identity"
            }
            $signature = Get-BodySignature $normalizedBody
            $redBodyBytes = $signature.Bytes
            $redBodySha256 = $signature.Sha256
        }

        $rowsByIdentity.Add($identity, [pscustomobject]@{
            identity = $identity
            class = $className
            method = $methodName
            outcome = $outcome
            red_kind = $redKind
            exception_type = $exceptionType
            normalized_message = $normalizedMessage
            red_body_bytes = $redBodyBytes
            red_body_sha256 = $redBodySha256
            report = [System.IO.Path]::GetFileName($reportPath)
        })
    }
}

$missingClasses = [System.Collections.Generic.List[string]]::new()
foreach ($className in $sourceClasses) {
    if (-not $reportedClasses.Contains($className) -and -not $helpers.Contains($className)) {
        $missingClasses.Add($className)
    }
}
if ($missingClasses.Count -gt 0) {
    throw "Selected executable classes are ABSENT from Surefire reports: $((Get-OrdinalSorted $missingClasses.ToArray()) -join ', ')"
}

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add($columns -join "`t")
foreach ($identity in (Get-OrdinalSorted @($rowsByIdentity.Keys))) {
    $row = $rowsByIdentity[$identity]
    $fields = foreach ($column in $columns) {
        ConvertTo-TsvField ([string]$row.$column)
    }
    $lines.Add($fields -join "`t")
}
$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}
[System.IO.File]::WriteAllText($OutputPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))

Write-Host "Exported $($rowsByIdentity.Count) Surefire testcase outcomes to $OutputPath"
