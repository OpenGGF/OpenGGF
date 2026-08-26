[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $SourceClassInventory,
    [Parameter(Mandatory, ValueFromRemainingArguments)] [string[]] $ReportRoot,
    [Parameter(Mandatory)] [string] $OutputPath,
    [string] $CanonicalWorktree = '',
    [string] $SessionRoot = '',
    [string] $RunId = '',
    [string] $EmptyHelperAllowlist = '',
    [string] $SelectorPatternInventory = '',
    [string] $MavenArgumentInventory = '',
    [string] $RuntimeInputs = '',
    [string] $EffectivePomPath = '',
    [string] $SelectorEffectivePomPath = '',
    [string] $SurefireExecutionId = 'default-test'
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

function Assert-JavaFqcn {
    param([string] $ClassName)
    $javaKeywords = @(
        'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char',
        'class', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum',
        'extends', 'false', 'final', 'finally', 'float', 'for', 'goto', 'if',
        'implements', 'import', 'instanceof', 'int', 'interface', 'long', 'native',
        'new', 'null', 'package', 'private', 'protected', 'public', 'return',
        'short', 'static', 'strictfp', 'super', 'switch', 'synchronized', 'this',
        'throw', 'throws', 'transient', 'true', 'try', 'void', 'volatile', 'while', '_'
    )
    if ($ClassName.Contains('$') -or $ClassName.Contains('/') -or $ClassName.Contains('\') -or
        $ClassName.IndexOfAny([char[]]@('*', '?', '[', ']')) -ge 0 -or
        $ClassName -match '\s') {
        throw "Source class inventory selector root is not an exact Java FQCN: [$ClassName]"
    }
    $segments = $ClassName.Split('.', [System.StringSplitOptions]::None)
    foreach ($segment in $segments) {
        if ($segment.Length -eq 0 -or
            $segment -notmatch '^[\p{L}\p{Nl}\p{Sc}\p{Pc}][\p{L}\p{Nl}\p{Sc}\p{Pc}\p{Nd}\p{Mn}\p{Mc}\p{Cf}]*$' -or
            $javaKeywords -ccontains $segment) {
            throw "Source class inventory selector root is not an exact Java FQCN: [$ClassName]"
        }
    }
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
        Assert-JavaFqcn $className
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

function ConvertFrom-MavenPropertyDefinition {
    param([string] $Definition, [string] $Argument)
    if ($Definition.Length -eq 0) {
        throw "Maven property argument has no name: $Argument"
    }
    $separator = $Definition.IndexOf('=', [System.StringComparison]::Ordinal)
    $name = if ($separator -lt 0) { $Definition } else { $Definition.Substring(0, $separator) }
    $value = if ($separator -lt 0) { '' } else { $Definition.Substring($separator + 1) }
    if ($name -notmatch '^[A-Za-z0-9_.-]+$') {
        throw "Maven property argument has an invalid name: $Argument"
    }
    return [pscustomobject]@{
        Name = $name
        Value = $value
        HasValue = ($separator -ge 0)
        Argument = $Argument
    }
}

function Read-MavenPropertyArguments {
    param([string] $Path)
    $arguments = @([System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Path).Path))
    $properties = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt $arguments.Count; $index++) {
        $argument = $arguments[$index]
        if ($argument.StartsWith('-D', [System.StringComparison]::Ordinal) -and $argument.Length -gt 2) {
            $properties.Add((ConvertFrom-MavenPropertyDefinition $argument.Substring(2) $argument))
        }
        elseif ($argument -ceq '-D' -or $argument -ceq '--define') {
            if (++$index -ge $arguments.Count) {
                throw "Maven $argument argument is missing its property definition"
            }
            $properties.Add((ConvertFrom-MavenPropertyDefinition $arguments[$index] "$argument $($arguments[$index])"))
        }
        elseif ($argument.StartsWith('--define=', [System.StringComparison]::Ordinal)) {
            $properties.Add((ConvertFrom-MavenPropertyDefinition $argument.Substring(9) $argument))
        }
    }
    return $properties.ToArray()
}

function Test-IsSelectionProperty {
    param([string] $Name)
    $normalized = $Name.ToLowerInvariant()
    $exactNames = @(
        'test', 'it.test', 'skiptests', 'maven.test.skip', 'groups', 'excludedgroups',
        'includes', 'excludes', 'includesfile', 'excludesfile', 'suitexmlfiles',
        'includetags', 'excludetags', 'includejunit5engines', 'excludejunit5engines',
        'dependenciestoscan', 'testclassesdirectory', 'testsourcedirectory',
        'provider', 'providers', 'providerclassname', 'selector', 'selectors',
        'filter', 'filters'
    )
    if ($exactNames -ccontains $normalized) {
        return $true
    }
    foreach ($prefix in @('surefire.', 'failsafe.', 'junit.', 'testng.')) {
        if ($normalized.StartsWith($prefix, [System.StringComparison]::Ordinal)) {
            $normalized = $normalized.Substring($prefix.Length)
            break
        }
    }
    if ($exactNames -ccontains $normalized) {
        return $true
    }
    foreach ($membershipTerm in @(
        'include', 'exclude', 'group', 'suite', 'engine', 'provider',
        'selector', 'filter', 'tag', 'scan', 'condition'
    )) {
        if ($normalized.Contains($membershipTerm, [System.StringComparison]::Ordinal)) {
            return $true
        }
    }
    return $false
}

function Assert-ExplicitSourceSelectorContract {
    param(
        [string[]] $SourceClasses,
        [string] $PatternPath,
        [string] $ArgumentPath,
        [string] $AuthenticatedRuntimeInputs
    )

    foreach ($path in @($PatternPath, $ArgumentPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Selector contract input does not exist: $path"
        }
    }

    $canonicalPatternPath = (Resolve-Path -LiteralPath $PatternPath).Path
    if (-not [System.IO.Path]::IsPathFullyQualified($PatternPath) -or
        [System.IO.Path]::GetFullPath($PatternPath) -cne $canonicalPatternPath) {
        throw "surefire.includesFile must use the canonical absolute selector path: $canonicalPatternPath"
    }

    $expectedPatterns = [System.Collections.Generic.List[string]]::new()
    foreach ($className in $SourceClasses) {
        $expectedPatterns.Add(($className.Replace('.', '/') + '.java'))
    }
    $sortedClasses = Get-OrdinalSorted $SourceClasses
    if (($SourceClasses -join "`n") -cne ($sortedClasses -join "`n")) {
        throw 'Explicit-source selector roots must be ordinal sorted'
    }
    $patterns = @([System.IO.File]::ReadAllLines($canonicalPatternPath))
    if ($patterns.Count -ne $expectedPatterns.Count) {
        throw 'Selector root list and slash-path patterns are not an ordinal bijection'
    }
    for ($index = 0; $index -lt $patterns.Count; $index++) {
        $pattern = $patterns[$index]
        if ($pattern.Contains('$')) {
            throw "Explicit-source selector pattern cannot contain `$: $pattern"
        }
        if ($pattern.IndexOfAny([char[]]@('*', '?', '[', ']')) -ge 0) {
            throw "Explicit-source selector pattern cannot contain a wildcard: $pattern"
        }
        if ($pattern -cne $expectedPatterns[$index]) {
            throw "Selector root list and slash-path patterns are not an ordinal bijection at $($SourceClasses[$index]): $pattern"
        }
    }

    $includesFileValues = [System.Collections.Generic.List[string]]::new()
    foreach ($property in (Read-MavenPropertyArguments $ArgumentPath)) {
        if ([string]$property.Name -ceq 'surefire.includesFile') {
            if (-not $property.HasValue -or ([string]$property.Value).Length -eq 0) {
                throw "surefire.includesFile requires a canonical absolute value: $($property.Argument)"
            }
            $includesFileValues.Add([string]$property.Value)
            continue
        }
        if (Test-IsSelectionProperty ([string]$property.Name)) {
            throw "Maven selector override is forbidden with surefire.includesFile: $($property.Argument)"
        }
    }
    if ($includesFileValues.Count -ne 1) {
        throw "Explicit-source invocation requires exactly one -Dsurefire.includesFile property; found $($includesFileValues.Count)"
    }
    if (-not [System.IO.Path]::IsPathFullyQualified($includesFileValues[0]) -or
        [System.IO.Path]::GetFullPath($includesFileValues[0]) -cne $canonicalPatternPath) {
        throw "surefire.includesFile must equal the canonical absolute selector path: $canonicalPatternPath"
    }

    $runtimeMatches = 0
    foreach ($runtimeInput in $AuthenticatedRuntimeInputs.Split([System.IO.Path]::PathSeparator, [System.StringSplitOptions]::RemoveEmptyEntries)) {
        if ([System.IO.Path]::IsPathFullyQualified($runtimeInput) -and
            [System.IO.Path]::GetFullPath($runtimeInput) -ceq $canonicalPatternPath) {
            $runtimeMatches++
        }
    }
    if ($runtimeMatches -ne 1) {
        throw "OPENGGF_RUNTIME_INPUTS must contain the canonical selector exactly once; found $runtimeMatches"
    }
    return $canonicalPatternPath
}

function Read-EffectiveSurefireConfiguration {
    param([string] $Path, [string] $ExecutionId)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Effective POM does not exist: $Path"
    }
    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $document = [System.Xml.XmlDocument]::new()
    $reader = [System.Xml.XmlReader]::Create((Resolve-Path -LiteralPath $Path).Path, $settings)
    try { $document.Load($reader) } finally { $reader.Dispose() }

    $plugins = @($document.SelectNodes("//*[local-name()='plugin'][*[local-name()='artifactId' and text()='maven-surefire-plugin']]"))
    $executions = [System.Collections.Generic.List[object]]::new()
    foreach ($plugin in $plugins) {
        foreach ($execution in @($plugin.SelectNodes("./*[local-name()='executions']/*[local-name()='execution']"))) {
            $id = $execution.SelectSingleNode("./*[local-name()='id']")
            if ($null -ne $id -and $id.InnerText -ceq $ExecutionId) {
                $executions.Add($execution)
            }
        }
    }
    if ($executions.Count -ne 1) {
        throw "Effective POM must contain exactly one maven-surefire-plugin execution '$ExecutionId'; found $($executions.Count)"
    }
    $configuration = $executions[0].SelectSingleNode("./*[local-name()='configuration']")
    if ($null -eq $configuration) {
        throw "Effective Surefire execution '$ExecutionId' has no configuration"
    }
    $competingNames = @(
        'includes', 'excludesFile', 'suiteXmlFiles', 'dependenciesToScan', 'test',
        'includeJUnit5Engines', 'excludeJUnit5Engines', 'includeTags', 'excludeTags'
    )
    foreach ($name in $competingNames) {
        if ($null -ne $configuration.SelectSingleNode("./*[local-name()='$name']")) {
            throw "Effective ordinary Surefire execution '$ExecutionId' contains competing selection channel $name"
        }
    }

    $values = [ordered]@{}
    $includesFileNodes = @($configuration.SelectNodes("./*[local-name()='includesFile']"))
    if ($includesFileNodes.Count -gt 1) {
        throw "Effective Surefire execution '$ExecutionId' contains duplicate includesFile channels"
    }
    $values.includesFile = if ($includesFileNodes.Count -eq 0) { '' } else { $includesFileNodes[0].InnerText }
    $values.excludes = [string[]]@($configuration.SelectNodes("./*[local-name()='excludes']/*[local-name()='exclude']") | ForEach-Object { $_.InnerText })
    foreach ($name in @('groups', 'excludedGroups', 'forkCount', 'reuseForks')) {
        $node = $configuration.SelectSingleNode("./*[local-name()='$name']")
        $values[$name] = if ($null -eq $node) { '' } else { $node.InnerText }
    }
    return [pscustomobject]$values
}

function Assert-EffectiveSurefireContract {
    param([string] $BaselinePath, [string] $SelectorPath, [string] $ExecutionId, [string] $AuthenticatedSelectorPath)
    $baseline = Read-EffectiveSurefireConfiguration $BaselinePath $ExecutionId
    $selector = Read-EffectiveSurefireConfiguration $SelectorPath $ExecutionId
    if (([string]$baseline.includesFile).Length -ne 0) {
        throw "Baseline effective Surefire execution contains selector override includesFile: $($baseline.includesFile)"
    }
    if ([string]$selector.includesFile -cne $AuthenticatedSelectorPath) {
        throw "Selector effective Surefire includesFile must equal the authenticated canonical path: expected=[$AuthenticatedSelectorPath] actual=[$($selector.includesFile)]"
    }
    $baselineExcludes = @($baseline.excludes)
    $selectorExcludes = @($selector.excludes)
    if ($baselineExcludes.Count -ne $selectorExcludes.Count) {
        throw "Effective Surefire excludes changed under the selector property: baseline=[$($baselineExcludes -join ',')] selector=[$($selectorExcludes -join ',')]"
    }
    for ($index = 0; $index -lt $baselineExcludes.Count; $index++) {
        if ([string]$baselineExcludes[$index] -cne [string]$selectorExcludes[$index]) {
            throw "Effective Surefire excludes changed under the selector property at ordinal $index`: baseline=[$($baselineExcludes[$index])] selector=[$($selectorExcludes[$index])]"
        }
    }
    foreach ($name in @('groups', 'excludedGroups', 'forkCount', 'reuseForks')) {
        if ([string]$baseline.$name -cne [string]$selector.$name) {
            throw "Effective Surefire $name changed under the selector property: baseline=[$($baseline.$name)] selector=[$($selector.$name)]"
        }
    }
    Write-Host "Effective Surefire unchanged: excludes=$($baselineExcludes -join ',') groups=$($baseline.groups) excludedGroups=$($baseline.excludedGroups) forkCount=$($baseline.forkCount) reuseForks=$($baseline.reuseForks)"
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

$sourceClasses = Read-ClassInventory $SourceClassInventory
$preflightValues = [ordered]@{
    SelectorPatternInventory = $SelectorPatternInventory
    MavenArgumentInventory = $MavenArgumentInventory
    RuntimeInputs = $RuntimeInputs
    EffectivePomPath = $EffectivePomPath
    SelectorEffectivePomPath = $SelectorEffectivePomPath
}
$suppliedPreflight = @($preflightValues.Values | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }).Count
if ($suppliedPreflight -ne 0 -and $suppliedPreflight -ne $preflightValues.Count) {
    $missingPreflight = @($preflightValues.Keys | Where-Object { [string]::IsNullOrWhiteSpace([string]$preflightValues[$_]) })
    throw "Explicit-source preflight is atomic; missing: $($missingPreflight -join ', ')"
}
if ($suppliedPreflight -eq $preflightValues.Count) {
    $authenticatedSelectorPath = Assert-ExplicitSourceSelectorContract $sourceClasses $SelectorPatternInventory $MavenArgumentInventory $RuntimeInputs
    Assert-EffectiveSurefireContract $EffectivePomPath $SelectorEffectivePomPath $SurefireExecutionId $authenticatedSelectorPath
}
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
$coveredRoots = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
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
        $owningRoot = $className
        if (-not $selected.Contains($owningRoot)) {
            $nestedBoundary = $className.IndexOf('$', [System.StringComparison]::Ordinal)
            if ($nestedBoundary -ge 0) {
                $owningRoot = $className.Substring(0, $nestedBoundary)
            }
        }
        if (-not $selected.Contains($owningRoot)) {
            throw "Surefire report contains unselected executable class: $className"
        }
        [void]$coveredRoots.Add($owningRoot)
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
    if (-not $coveredRoots.Contains($className) -and -not $helpers.Contains($className)) {
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
