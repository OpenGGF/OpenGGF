[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $SourceClassInventory,
    [Parameter(Mandatory, ValueFromRemainingArguments)] [string[]] $ReportRoot,
    [Parameter(Mandatory)] [string] $OutputPath,
    [switch] $DirectMaven,
    [string] $CanonicalWorktree = '',
    [string] $SessionRoot = '',
    [string] $RunId = '',
    [string] $EmptyHelperAllowlist = '',
    [string] $RepeatedIdentityCardinalityPath = '',
    [string] $SelectorPatternInventory = '',
    [string] $MavenArgumentInventory = '',
    [string] $MavenLocalRepositoryPath = '',
    [string] $RuntimeInputs = '',
    [string] $EffectivePomPath = '',
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

function Get-SelectedOwningRoot {
    param([string] $ClassName, [System.Collections.Generic.HashSet[string]] $SelectedClasses)
    if ($SelectedClasses.Contains($ClassName)) {
        return $ClassName
    }
    foreach ($selectedClass in $SelectedClasses) {
        if ($ClassName.StartsWith($selectedClass + '$', [System.StringComparison]::Ordinal)) {
            return $selectedClass
        }
    }
    return ''
}

function Resolve-SurefireTestcaseClassName {
    param(
        [string] $RawClassName,
        [System.Xml.XmlElement] $Testcase,
        [string] $ReportPath,
        [System.Collections.Generic.HashSet[string]] $SelectedClasses
    )
    if ((Get-SelectedOwningRoot $RawClassName $SelectedClasses).Length -ne 0) {
        return $RawClassName
    }
    if ($RawClassName.Contains('.')) {
        return $RawClassName
    }

    $owningSuites = [System.Collections.Generic.List[System.Xml.XmlElement]]::new()
    $ancestor = $Testcase.ParentNode
    while ($null -ne $ancestor) {
        if ($ancestor.NodeType -eq [System.Xml.XmlNodeType]::Element -and $ancestor.LocalName -ceq 'testsuite') {
            $owningSuites.Add([System.Xml.XmlElement]$ancestor)
        }
        $ancestor = $ancestor.ParentNode
    }
    if ($owningSuites.Count -ne 1) {
        throw "Unselected simple Surefire testcase classname requires exactly one owning testsuite: $RawClassName suites=$($owningSuites.Count)"
    }

    $suiteName = [string]$owningSuites[0].GetAttribute('name')
    if ($suiteName.Length -eq 0 -or (Get-SelectedOwningRoot $suiteName $SelectedClasses).Length -eq 0) {
        throw "Surefire testsuite name is not owned by a selected root: [$suiteName]"
    }
    $simpleBoundary = $suiteName.LastIndexOf('.', [System.StringComparison]::Ordinal)
    $suiteSimpleName = if ($simpleBoundary -lt 0) { $suiteName } else { $suiteName.Substring($simpleBoundary + 1) }
    if ($RawClassName -cne $suiteSimpleName) {
        throw "Surefire simple classname mismatch with selected suite: raw=[$RawClassName] expected=[$suiteSimpleName]"
    }
    $expectedReportName = "TEST-$suiteName.xml"
    $actualReportName = [System.IO.Path]::GetFileName($ReportPath)
    if ($actualReportName -cne $expectedReportName) {
        throw "Surefire simple-classname report basename mismatch: expected=[$expectedReportName] actual=[$actualReportName]"
    }
    return $suiteName
}

function Read-RepeatedIdentityCardinality {
    param([string] $Path, [System.Collections.Generic.HashSet[string]] $SelectedClasses)
    $entries = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return [pscustomobject]@{ Path = ''; Entries = $entries }
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Repeated-identity cardinality allowlist does not exist: $Path"
    }
    $canonicalPath = (Resolve-Path -LiteralPath $Path).Path
    $lines = @([System.IO.File]::ReadAllLines($canonicalPath))
    if ($lines.Count -eq 0 -or $lines[0] -cne "identity`tcardinality`treason") {
        throw 'Repeated-identity cardinality allowlist requires exactly identity, cardinality, and reason columns'
    }
    for ($index = 1; $index -lt $lines.Count; $index++) {
        if ([System.Text.RegularExpressions.Regex]::Matches($lines[$index], "`t").Count -ne 2) {
            throw "Repeated-identity cardinality allowlist row $($index + 1) must contain exactly three fields"
        }
    }
    foreach ($row in @(Import-Csv -Delimiter "`t" -LiteralPath $canonicalPath)) {
        $identity = ConvertFrom-TsvField ([string]$row.identity) 'identity'
        $cardinalityText = ConvertFrom-TsvField ([string]$row.cardinality) 'cardinality'
        $reason = ConvertFrom-TsvField ([string]$row.reason) 'reason'
        $separator = $identity.IndexOf('#', [System.StringComparison]::Ordinal)
        if ($separator -le 0 -or $separator -eq ($identity.Length - 1)) {
            throw "Repeated-identity allowlist identity must contain a non-empty class and method separated by the first #: [$identity]"
        }
        $className = $identity.Substring(0, $separator)
        $methodName = $identity.Substring($separator + 1)
        if ((Get-SelectedOwningRoot $className $SelectedClasses).Length -eq 0) {
            throw "Repeated-identity allowlist identity is not owned by a selected root: $identity"
        }
        if ($cardinalityText -notmatch '^(?:[2-9]|[1-9][0-9]+)$') {
            throw "Repeated-identity allowlist cardinality must be an integer at least 2: $identity=[$cardinalityText]"
        }
        if ($reason.Length -eq 0) {
            throw "Repeated-identity allowlist reason must be non-empty: $identity"
        }
        if ($entries.ContainsKey($identity)) {
            throw "Duplicate repeated-identity allowlist identity: $identity"
        }
        $entries.Add($identity, [pscustomobject]@{
            class = $className
            method = $methodName
            cardinality = [int]::Parse($cardinalityText, [System.Globalization.CultureInfo]::InvariantCulture)
            reason = $reason
        })
    }
    return [pscustomobject]@{ Path = $canonicalPath; Entries = $entries }
}

function Assert-RuntimeInputExactlyOnce {
    param([string] $CanonicalPath, [string] $AuthenticatedRuntimeInputs, [string] $Description)
    $matches = 0
    foreach ($runtimeInput in $AuthenticatedRuntimeInputs.Split([System.IO.Path]::PathSeparator, [System.StringSplitOptions]::RemoveEmptyEntries)) {
        Assert-NoUnresolvedPlaceholder $runtimeInput "authenticated RuntimeInputs entry"
        if ([System.IO.Path]::IsPathFullyQualified($runtimeInput) -and
            (Test-PathsEqual $runtimeInput $CanonicalPath)) {
            $matches++
        }
    }
    if ($matches -ne 1) {
        throw "$Description must appear in OPENGGF_RUNTIME_INPUTS exactly once; found $matches"
    }
}

function Assert-RuntimeInputAbsent {
    param([string] $CanonicalPath, [string] $AuthenticatedRuntimeInputs, [string] $Description)
    foreach ($runtimeInput in $AuthenticatedRuntimeInputs.Split([System.IO.Path]::PathSeparator, [System.StringSplitOptions]::RemoveEmptyEntries)) {
        Assert-NoUnresolvedPlaceholder $runtimeInput 'authenticated RuntimeInputs entry'
        if ([System.IO.Path]::IsPathFullyQualified($runtimeInput) -and
            (Test-PathsEqual $runtimeInput $CanonicalPath)) {
            throw "$Description must not appear in OPENGGF_RUNTIME_INPUTS"
        }
    }
}

function Assert-NoUnresolvedPlaceholder {
    param([AllowEmptyString()] [string] $Value, [string] $Description)
    if ($Value -match '\$\{[^}]*\}|@\{[^}]*\}') {
        throw "$Description contains an unresolved property placeholder: [$Value]"
    }
}

function Test-CanonicalCapacityArgLineTemplate {
    param([string] $Value)
    return $Value -ceq '${test.cds.argLine} ${mockito.agent.argLine} -Xmx3g' -or
        $Value -ceq '-XstartOnFirstThread ${test.cds.argLine} ${mockito.agent.argLine} -Xmx3g'
}

function ConvertFrom-JvmArgumentLine {
    param([string] $Value, [string] $Description)
    $tokens = [System.Collections.Generic.List[string]]::new()
    $token = [System.Text.StringBuilder]::new()
    $quoted = $false
    $started = $false
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '"') {
            $quoted = -not $quoted
            $started = $true
            continue
        }
        if (-not $quoted -and [char]::IsWhiteSpace($character)) {
            if ($started) {
                $tokens.Add($token.ToString())
                [void]$token.Clear()
                $started = $false
            }
            continue
        }
        [void]$token.Append($character)
        $started = $true
    }
    if ($quoted) {
        throw "$Description contains an unmatched quote"
    }
    if ($started) {
        $tokens.Add($token.ToString())
    }
    return $tokens.ToArray()
}

function ConvertTo-PortablePath {
    param([string] $Value)
    return $Value.Replace('\', '/')
}

function Test-PortableFullyQualifiedPath {
    param([string] $Value)
    $portable = ConvertTo-PortablePath $Value
    return $portable.StartsWith('/', [System.StringComparison]::Ordinal) -or
        $portable -match '^[A-Za-z]:/'
}

function Test-PathWithin {
    param([string] $Child, [string] $Parent)
    $comparison = if ([System.IO.Path]::DirectorySeparatorChar -eq '\') {
        [System.StringComparison]::OrdinalIgnoreCase
    } else {
        [System.StringComparison]::Ordinal
    }
    $parentFull = [System.IO.Path]::GetFullPath($Parent).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    $childFull = [System.IO.Path]::GetFullPath($Child)
    return $childFull.Equals($parentFull, $comparison) -or
        $childFull.StartsWith($parentFull + [System.IO.Path]::DirectorySeparatorChar, $comparison)
}

function Test-PathsEqual {
    param([string] $Left, [string] $Right)
    $comparison = if ([System.IO.Path]::DirectorySeparatorChar -eq '\') {
        [System.StringComparison]::OrdinalIgnoreCase
    } else {
        [System.StringComparison]::Ordinal
    }
    return [System.IO.Path]::GetFullPath($Left).Equals(
        [System.IO.Path]::GetFullPath($Right), $comparison)
}

function Resolve-MavenLocalRepositoryEvidence {
    param([string] $RepositoryPath, [string[]] $MockitoArtifactSegments)
    if ([string]::IsNullOrWhiteSpace($RepositoryPath)) {
        throw 'DirectMaven effective Mockito placeholder requires MavenLocalRepositoryPath evidence'
    }
    Assert-NoUnresolvedPlaceholder $RepositoryPath 'MavenLocalRepositoryPath'
    if (-not [System.IO.Path]::IsPathFullyQualified($RepositoryPath)) {
        throw 'MavenLocalRepositoryPath must be a canonical absolute directory'
    }
    $fullRepositoryPath = [System.IO.Path]::GetFullPath($RepositoryPath)
    if (-not (Test-Path -LiteralPath $fullRepositoryPath -PathType Container)) {
        throw "MavenLocalRepositoryPath directory does not exist: $fullRepositoryPath"
    }
    $resolvedRepositoryPath = (Resolve-Path -LiteralPath $fullRepositoryPath).Path
    if (-not (Test-PathsEqual $fullRepositoryPath $resolvedRepositoryPath)) {
        throw "MavenLocalRepositoryPath must be canonical: $resolvedRepositoryPath"
    }
    $trustedRepositoryPath = Assert-NoReparsePointInAncestry `
        $fullRepositoryPath `
        'Maven local repository'
    $expectedMockitoJar = $trustedRepositoryPath
    foreach ($segment in $MockitoArtifactSegments) {
        $expectedMockitoJar = [System.IO.Path]::Combine($expectedMockitoJar, $segment)
    }
    if (-not (Test-Path -LiteralPath $expectedMockitoJar -PathType Leaf)) {
        throw "MavenLocalRepositoryPath does not contain the exact effective Mockito jar: $expectedMockitoJar"
    }
    $trustedMockitoJar = Assert-NoReparsePointInAncestry `
        $expectedMockitoJar `
        'Mockito agent jar'
    if (-not (Test-PathWithin $trustedMockitoJar $trustedRepositoryPath)) {
        throw 'Effective Mockito jar must remain beneath MavenLocalRepositoryPath'
    }
    return [pscustomobject]@{
        RepositoryPath = $trustedRepositoryPath
        MockitoJarPath = $trustedMockitoJar
    }
}

function Get-MavenRepositoryMockitoTokenIndex {
    param([string[]] $Tokens, [string] $Description)
    $repositoryPlaceholder = '${settings.localRepository}'
    $placeholderCount = 0
    foreach ($token in $Tokens) {
        $placeholderCount += [System.Text.RegularExpressions.Regex]::Matches(
            $token,
            [System.Text.RegularExpressions.Regex]::Escape($repositoryPlaceholder)).Count
    }
    if ($placeholderCount -eq 0) {
        return -1
    }
    if ($placeholderCount -ne 1) {
        throw "$Description contains multiple Maven repository placeholders"
    }
    $javaAgentIndexes = @(
        for ($index = 0; $index -lt $Tokens.Count; $index++) {
            if ($Tokens[$index].StartsWith('-javaagent:', [System.StringComparison]::Ordinal)) {
                $index
            }
        }
    )
    if ($javaAgentIndexes.Count -ne 1) {
        throw "$Description Maven repository placeholder requires one expected Mockito javaagent token"
    }
    $javaAgentIndex = $javaAgentIndexes[0]
    $mockitoTemplatePath = $Tokens[$javaAgentIndex].Substring('-javaagent:'.Length)
    if (-not (ConvertTo-PortablePath $mockitoTemplatePath).StartsWith(
            $repositoryPlaceholder + '/', [System.StringComparison]::Ordinal)) {
        throw "$Description Maven repository placeholder is permitted only as the exact Mockito javaagent path prefix"
    }
    return $javaAgentIndex
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

function Get-ApprovedMavenPropertyName {
    param([string] $Name)
    $approvedNames = @(
        'mse',
        'sonic1.rom.path', 'sonic2.rom.path', 's3k.rom.path',
        'surefire.argLine', 'surefire.forkCount', 'surefire.reuseForks', 'surefire.runOrder'
    )
    foreach ($approvedName in $approvedNames) {
        if ($Name.Equals($approvedName, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $approvedName
        }
    }
    return ''
}

function Assert-ExplicitSourceSelectorContract {
    param(
        [string[]] $SourceClasses,
        [string] $PatternPath,
        [string] $ArgumentPath,
        [string] $AuthenticatedRuntimeInputs,
        [bool] $IsDirectMaven
    )

    foreach ($path in @($PatternPath, $ArgumentPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Selector contract input does not exist: $path"
        }
    }

    $canonicalPatternPath = (Resolve-Path -LiteralPath $PatternPath).Path
    $canonicalArgumentPath = (Resolve-Path -LiteralPath $ArgumentPath).Path
    if (-not [System.IO.Path]::IsPathFullyQualified($PatternPath) -or
        -not (Test-PathsEqual $PatternPath $canonicalPatternPath)) {
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
    $approvedProperties = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($property in (Read-MavenPropertyArguments $ArgumentPath)) {
        if ($IsDirectMaven) {
            $isCapacityTemplate = ([string]$property.Name).Equals(
                'surefire.argLine', [System.StringComparison]::OrdinalIgnoreCase) -and
                (Test-CanonicalCapacityArgLineTemplate ([string]$property.Value))
            if (-not $isCapacityTemplate) {
                Assert-NoUnresolvedPlaceholder ([string]$property.Value) "authenticated Maven property $($property.Name)"
            }
        }
        elseif (([string]$property.Value).Replace('${surefire.forkNumber}', '') -match '\$\{[^}]*\}|@\{[^}]*\}') {
            throw "authenticated Maven property $($property.Name) contains an unresolved unsupported property placeholder"
        }
        if ([string]$property.Name -ceq 'surefire.includesFile') {
            if (-not $property.HasValue -or ([string]$property.Value).Length -eq 0) {
                throw "surefire.includesFile requires a canonical absolute value: $($property.Argument)"
            }
            $includesFileValues.Add([string]$property.Value)
            continue
        }
        $approvedName = Get-ApprovedMavenPropertyName ([string]$property.Name)
        if ($approvedName.Length -eq 0) {
            throw "Maven property is unapproved for explicit-source preflight: $($property.Argument)"
        }
        if (-not $property.HasValue -or ([string]$property.Value).Length -eq 0) {
            throw "Approved Maven property requires an explicit value: $($property.Argument)"
        }
        if ($approvedProperties.ContainsKey($approvedName)) {
            throw "Approved Maven property appears more than once: $approvedName"
        }
        if ($approvedName -ceq 'mse' -and @('off', 'relaxed') -cnotcontains [string]$property.Value) {
            throw "Maven Silent Extension mode is not an approved production value: $($property.Value)"
        }
        $approvedProperties.Add($approvedName, $property)
    }
    if ($includesFileValues.Count -ne 1) {
        throw "Explicit-source invocation requires exactly one -Dsurefire.includesFile property; found $($includesFileValues.Count)"
    }
    if (-not [System.IO.Path]::IsPathFullyQualified($includesFileValues[0]) -or
        -not (Test-PathsEqual $includesFileValues[0] $canonicalPatternPath)) {
        throw "surefire.includesFile must equal the canonical absolute selector path: $canonicalPatternPath"
    }

    $runtimeMatches = 0
    foreach ($runtimeInput in $AuthenticatedRuntimeInputs.Split([System.IO.Path]::PathSeparator, [System.StringSplitOptions]::RemoveEmptyEntries)) {
        Assert-NoUnresolvedPlaceholder $runtimeInput 'authenticated RuntimeInputs entry'
        if ([System.IO.Path]::IsPathFullyQualified($runtimeInput) -and
            (Test-PathsEqual $runtimeInput $canonicalPatternPath)) {
            $runtimeMatches++
        }
    }
    if ($runtimeMatches -ne 1) {
        throw "OPENGGF_RUNTIME_INPUTS must contain the canonical selector exactly once; found $runtimeMatches"
    }
    if ($IsDirectMaven) {
        if (-not [System.IO.Path]::IsPathFullyQualified($ArgumentPath) -or
            -not (Test-PathsEqual $ArgumentPath $canonicalArgumentPath)) {
            throw "MavenArgumentInventory must use its canonical absolute path: $canonicalArgumentPath"
        }
        Assert-RuntimeInputExactlyOnce $canonicalArgumentPath $AuthenticatedRuntimeInputs 'Maven argument inventory'
        foreach ($requiredName in @('surefire.argLine', 'surefire.forkCount', 'surefire.reuseForks', 'surefire.runOrder')) {
            if (-not $approvedProperties.ContainsKey($requiredName)) {
                throw "DirectMaven capacity preflight requires exactly one explicit $requiredName property"
            }
        }
        if ([string]$approvedProperties['surefire.forkCount'].Value -cne '1') {
            throw 'DirectMaven capacity preflight requires surefire.forkCount=1'
        }
        if ([string]$approvedProperties['surefire.reuseForks'].Value -cne 'true') {
            throw 'DirectMaven capacity preflight requires surefire.reuseForks=true'
        }
        if ([string]$approvedProperties['surefire.runOrder'].Value -cne 'alphabetical') {
            throw 'DirectMaven capacity preflight requires surefire.runOrder=alphabetical'
        }
    }
    return [pscustomobject]@{
        SelectorPath = $canonicalPatternPath
        ArgumentPath = $canonicalArgumentPath
        ApprovedProperties = $approvedProperties
    }
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
    $projectArgLineNodes = @($document.DocumentElement.SelectNodes("./*[local-name()='properties']/*[local-name()='surefire.argLine']"))
    if ($projectArgLineNodes.Count -ne 1 -or [string]::IsNullOrEmpty([string]$projectArgLineNodes[0].InnerText)) {
        throw "Effective POM must contain exactly one non-empty project property surefire.argLine; found $($projectArgLineNodes.Count)"
    }
    $configuration = $executions[0].SelectSingleNode("./*[local-name()='configuration']")
    if ($null -eq $configuration) {
        throw "Effective Surefire execution '$ExecutionId' has no configuration"
    }
    $competingNames = @(
        'includes', 'includesFile', 'excludesFile', 'suiteXmlFiles', 'dependenciesToScan', 'test',
        'includeJUnit5Engines', 'excludeJUnit5Engines', 'includeTags', 'excludeTags'
    )
    foreach ($name in $competingNames) {
        if ($null -ne $configuration.SelectSingleNode("./*[local-name()='$name']")) {
            throw "Effective ordinary Surefire execution '$ExecutionId' contains competing selection channel $name"
        }
    }

    $values = [ordered]@{}
    $values.projectArgLine = $projectArgLineNodes[0].InnerText
    foreach ($name in @('test.cds.argLine', 'mockito.agent.argLine', 'openggf.test.tmpdir')) {
        $nodes = @($document.DocumentElement.SelectNodes("./*[local-name()='properties']/*[local-name()='$name']"))
        if ($nodes.Count -ne 1 -or [string]::IsNullOrEmpty([string]$nodes[0].InnerText)) {
            throw "Effective POM must contain exactly one non-empty project property $name; found $($nodes.Count)"
        }
        $values[$name] = $nodes[0].InnerText
    }
    $buildDirectoryNodes = @($document.DocumentElement.SelectNodes("./*[local-name()='build']/*[local-name()='directory']"))
    if ($buildDirectoryNodes.Count -ne 1 -or [string]::IsNullOrEmpty([string]$buildDirectoryNodes[0].InnerText)) {
        throw "Effective POM must contain exactly one non-empty project build directory; found $($buildDirectoryNodes.Count)"
    }
    $values.projectBuildDirectory = $buildDirectoryNodes[0].InnerText
    $values.excludes = [string[]]@($configuration.SelectNodes("./*[local-name()='excludes']/*[local-name()='exclude']") | ForEach-Object { $_.InnerText })
    foreach ($name in @('groups', 'excludedGroups', 'argLine', 'forkCount', 'reuseForks')) {
        $node = $configuration.SelectSingleNode("./*[local-name()='$name']")
        $values[$name] = if ($null -eq $node) { '' } else { $node.InnerText }
    }
    $runOrderNodes = @($configuration.SelectNodes("./*[local-name()='runOrder']"))
    $values.runOrderCount = $runOrderNodes.Count
    $values.runOrder = if ($runOrderNodes.Count -eq 1) { $runOrderNodes[0].InnerText } else { '' }
    return [pscustomobject]$values
}

function Assert-EffectiveSurefireContract {
    param(
        [string] $Path,
        [string] $ExecutionId,
        [System.Collections.Generic.Dictionary[string, object]] $ApprovedProperties,
        [bool] $IsDirectMaven,
        [string] $Worktree,
        [string] $LocalRepositoryPath
    )
    $effective = Read-EffectiveSurefireConfiguration $Path $ExecutionId
    if ($ApprovedProperties.ContainsKey('surefire.argLine')) {
        $argumentValue = [string]$ApprovedProperties['surefire.argLine'].Value
        if ($IsDirectMaven) {
            $usesCapacityTemplate = Test-CanonicalCapacityArgLineTemplate $argumentValue
            if (-not $usesCapacityTemplate) {
                Assert-NoUnresolvedPlaceholder $argumentValue 'DirectMaven surefire.argLine proof'
            }
            Assert-NoUnresolvedPlaceholder ([string]$effective.'test.cds.argLine') 'DirectMaven effective CDS argument'
            $cdsTokens = @(ConvertFrom-JvmArgumentLine ([string]$effective.'test.cds.argLine') 'effective CDS argument')
            if ($cdsTokens.Count -ne 1 -or $cdsTokens[0] -cne '-Xshare:off') {
                throw 'DirectMaven effective CDS content must be exactly -Xshare:off'
            }

            $repositoryPlaceholder = '${settings.localRepository}'
            $mockitoTemplate = [string]$effective.'mockito.agent.argLine'
            if ($mockitoTemplate -match '@\{[^}]*\}') {
                throw 'DirectMaven effective Mockito agent content contains an unresolved Surefire placeholder'
            }
            $placeholderCount = [System.Text.RegularExpressions.Regex]::Matches(
                $mockitoTemplate, [System.Text.RegularExpressions.Regex]::Escape($repositoryPlaceholder)).Count
            $mockitoTemplateRemainder = $mockitoTemplate.Replace($repositoryPlaceholder, '')
            if ($mockitoTemplateRemainder -match '\$\{[^}]*\}' -or $placeholderCount -gt 1) {
                throw 'DirectMaven effective Mockito agent content contains an unresolved unsupported property placeholder'
            }
            $sourceMockitoTemplateTokens = @(ConvertFrom-JvmArgumentLine $mockitoTemplate 'effective Mockito agent argument')
            if ($sourceMockitoTemplateTokens.Count -ne 1 -or
                -not $sourceMockitoTemplateTokens[0].StartsWith('-javaagent:', [System.StringComparison]::Ordinal)) {
                throw 'DirectMaven effective Mockito agent content must contain exactly one javaagent option'
            }
            $templateMockitoPath = $sourceMockitoTemplateTokens[0].Substring('-javaagent:'.Length)
            $portableTemplate = ConvertTo-PortablePath $templateMockitoPath
            if ($placeholderCount -eq 1) {
                if (-not $portableTemplate.StartsWith(
                        $repositoryPlaceholder + '/', [System.StringComparison]::Ordinal)) {
                    throw 'DirectMaven effective Mockito agent must resolve directly below settings.localRepository'
                }
                $artifactPath = $portableTemplate.Substring($repositoryPlaceholder.Length).TrimStart('/')
            }
            else {
                if (-not (Test-PortableFullyQualifiedPath $portableTemplate)) {
                    throw 'DirectMaven effective Mockito javaagent path must be absolute'
                }
                $artifactMarker = '/org/mockito/mockito-core/'
                $artifactOffset = $portableTemplate.LastIndexOf(
                    $artifactMarker, [System.StringComparison]::Ordinal)
                if ($artifactOffset -lt 0) {
                    throw 'DirectMaven effective Mockito agent must name the exact mockito-core repository artifact'
                }
                $artifactPath = $portableTemplate.Substring($artifactOffset + 1)
            }
            $artifactSegments = @($artifactPath.Split('/'))
            if ($artifactSegments.Count -ne 5 -or
                $artifactSegments[0] -cne 'org' -or
                $artifactSegments[1] -cne 'mockito' -or
                $artifactSegments[2] -cne 'mockito-core' -or
                $artifactSegments[3].Length -eq 0 -or
                $artifactSegments[4] -cne "mockito-core-$($artifactSegments[3]).jar") {
                throw 'DirectMaven effective Mockito agent must name the exact mockito-core repository artifact'
            }

            $effectiveProjectArgLine = [string]$effective.projectArgLine
            $projectTokens = @(ConvertFrom-JvmArgumentLine $effectiveProjectArgLine 'effective project argLine')
            $projectRepositoryTokenIndex = Get-MavenRepositoryMockitoTokenIndex `
                $projectTokens `
                'DirectMaven effective project argLine'
            $effectiveExecutionArgLine = [string]$effective.argLine
            $executionTokens = @(ConvertFrom-JvmArgumentLine $effectiveExecutionArgLine 'effective Surefire execution argLine')
            $executionRepositoryTokenIndex = Get-MavenRepositoryMockitoTokenIndex `
                $executionTokens `
                'DirectMaven effective Surefire execution argLine'
            $unsupportedExecutionRemainder = $effectiveExecutionArgLine.Replace(
                $repositoryPlaceholder, '').Replace('${surefire.forkNumber}', '')
            if ($unsupportedExecutionRemainder -match '\$\{[^}]*\}|@\{[^}]*\}') {
                throw 'DirectMaven effective Surefire execution argLine contains an unresolved unsupported property placeholder'
            }
            $localRepositoryEvidence = $null
            if ($placeholderCount -eq 1 -or
                $projectRepositoryTokenIndex -ge 0 -or
                $executionRepositoryTokenIndex -ge 0) {
                $localRepositoryEvidence = Resolve-MavenLocalRepositoryEvidence `
                    $LocalRepositoryPath `
                    $artifactSegments
                $mockitoTemplate = $mockitoTemplate.Replace(
                    $repositoryPlaceholder, [string]$localRepositoryEvidence.RepositoryPath)
                if ($executionRepositoryTokenIndex -ge 0) {
                    $executionTokens[$executionRepositoryTokenIndex] =
                        $executionTokens[$executionRepositoryTokenIndex].Replace(
                            $repositoryPlaceholder,
                            [string]$localRepositoryEvidence.RepositoryPath)
                }
                if ($projectRepositoryTokenIndex -ge 0) {
                    $projectTokens[$projectRepositoryTokenIndex] =
                        $projectTokens[$projectRepositoryTokenIndex].Replace(
                            $repositoryPlaceholder,
                            [string]$localRepositoryEvidence.RepositoryPath)
                }
            }
            elseif (-not [string]::IsNullOrWhiteSpace($LocalRepositoryPath)) {
                throw 'MavenLocalRepositoryPath is inconsistent because all effective Mockito paths are already absolute'
            }

            $mockitoTemplateTokens = @(ConvertFrom-JvmArgumentLine $mockitoTemplate 'resolved effective Mockito agent argument')
            $templateMockitoPath = $mockitoTemplateTokens[0].Substring('-javaagent:'.Length)
            $rawTemplateRequiresMacLauncher = $usesCapacityTemplate -and
                $argumentValue.StartsWith('-XstartOnFirstThread ', [System.StringComparison]::Ordinal)
            if ($usesCapacityTemplate) {
                $capacityTokenCount = if ($rawTemplateRequiresMacLauncher) { 4 } else { 3 }
                if ($executionTokens.Count -ne ($capacityTokenCount + 2)) {
                    throw 'DirectMaven canonical capacity template did not resolve to the expected effective execution shape'
                }
                $argumentTokens = @($executionTokens[0..($capacityTokenCount - 1)])
            }
            else {
                $argumentTokens = @(ConvertFrom-JvmArgumentLine $argumentValue 'DirectMaven surefire.argLine')
            }
            $capacityOffset = 0
            if ($argumentTokens.Count -eq 4 -and $argumentTokens[0] -ceq '-XstartOnFirstThread') {
                $capacityOffset = 1
            }
            if ($argumentTokens.Count -ne (3 + $capacityOffset) -or
                $argumentTokens[$capacityOffset] -cne '-Xshare:off' -or
                -not $argumentTokens[$capacityOffset + 1].StartsWith('-javaagent:', [System.StringComparison]::Ordinal) -or
                $argumentTokens[$capacityOffset + 2] -cne '-Xmx3g') {
                throw 'DirectMaven capacity argLine must contain only optional -XstartOnFirstThread, exact CDS, one Mockito agent, and the proven-sufficient -Xmx3g heap'
            }

            $actualMockitoPath = $argumentTokens[$capacityOffset + 1].Substring('-javaagent:'.Length)
            if (-not (Test-PortableFullyQualifiedPath $actualMockitoPath)) {
                throw 'DirectMaven Mockito javaagent path must be absolute'
            }
            $portableActual = ConvertTo-PortablePath $actualMockitoPath
            $portableTemplate = ConvertTo-PortablePath $templateMockitoPath
            $pathComparison = if ($portableActual -match '^[A-Za-z]:/') {
                [System.StringComparison]::OrdinalIgnoreCase
            }
            else {
                [System.StringComparison]::Ordinal
            }
            if (-not $portableActual.Equals($portableTemplate, $pathComparison)) {
                throw 'DirectMaven Mockito javaagent path does not equal the resolved effective Mockito agent path'
            }
            if ($null -ne $localRepositoryEvidence -and
                -not (Test-PathsEqual $actualMockitoPath ([string]$localRepositoryEvidence.MockitoJarPath))) {
                throw 'DirectMaven resolved Mockito javaagent path does not equal the evidenced Maven repository jar'
            }

            $projectTemplate = $effectiveProjectArgLine
            $permittedTemplate = '${test.cds.argLine} ${mockito.agent.argLine} '
            $projectRequiresMacLauncher = $projectTemplate.StartsWith(
                '-XstartOnFirstThread ', [System.StringComparison]::Ordinal)
            $argumentHasMacLauncher = ($capacityOffset -eq 1)
            if ($usesCapacityTemplate -and
                $rawTemplateRequiresMacLauncher -ne $argumentHasMacLauncher) {
                throw 'DirectMaven canonical capacity template macOS launcher presence must match its resolved execution'
            }
            if ($projectRequiresMacLauncher -ne $argumentHasMacLauncher) {
                throw 'DirectMaven -XstartOnFirstThread presence must match the effective project argLine launcher contract'
            }
            if ($projectRequiresMacLauncher) {
                $projectTemplate = $projectTemplate.Substring('-XstartOnFirstThread '.Length)
            }
            if ($projectTemplate.StartsWith($permittedTemplate, [System.StringComparison]::Ordinal)) {
                $projectHeap = $projectTemplate.Substring($permittedTemplate.Length)
                if ($projectHeap -notmatch '^-Xmx[1-9][0-9]*[kKmMgG]$') {
                    throw 'DirectMaven effective project argLine contains unsupported or unresolved JVM content'
                }
            }
            else {
                foreach ($projectToken in $projectTokens) {
                    Assert-NoUnresolvedPlaceholder $projectToken 'DirectMaven resolved effective project argLine token'
                }
                $projectCapacityOffset = 0
                if ($projectTokens.Count -eq 4 -and $projectTokens[0] -ceq '-XstartOnFirstThread') {
                    $projectCapacityOffset = 1
                }
                if ($projectTokens.Count -ne (3 + $projectCapacityOffset) -or
                    $projectTokens[$projectCapacityOffset] -cne '-Xshare:off' -or
                    -not $projectTokens[$projectCapacityOffset + 1].StartsWith('-javaagent:', [System.StringComparison]::Ordinal) -or
                    $projectTokens[$projectCapacityOffset + 2] -notmatch '^-Xmx[1-9][0-9]*[kKmMgG]$') {
                    throw 'DirectMaven effective project argLine must preserve exact CDS, Mockito agent, and heap content'
                }
                $projectMockitoPath = $projectTokens[$projectCapacityOffset + 1].Substring('-javaagent:'.Length)
                if (-not (Test-PortableFullyQualifiedPath $projectMockitoPath)) {
                    throw 'DirectMaven effective project Mockito javaagent path must resolve absolute'
                }
                $portableProjectMockito = ConvertTo-PortablePath $projectMockitoPath
                if (-not $portableProjectMockito.Equals($portableTemplate, $pathComparison) -or
                    -not $portableProjectMockito.Equals($portableActual, $pathComparison)) {
                    throw 'DirectMaven effective project and execution Mockito javaagent paths must match'
                }
            }

            if ($executionTokens.Count -ne ($argumentTokens.Count + 2)) {
                throw 'DirectMaven same-invocation effective Surefire execution argLine has unexpected options'
            }
            for ($index = 0; $index -lt $argumentTokens.Count; $index++) {
                if ($executionTokens[$index] -cne $argumentTokens[$index]) {
                    throw 'DirectMaven surefire.argLine does not match the same-invocation effective Surefire execution argLine'
                }
            }
            $tmpPrefix = '-Djava.io.tmpdir='
            $lwjglPrefix = '-Dorg.lwjgl.system.SharedLibraryExtractPath='
            if (-not $executionTokens[-2].StartsWith($tmpPrefix, [System.StringComparison]::Ordinal) -or
                -not $executionTokens[-1].StartsWith($lwjglPrefix, [System.StringComparison]::Ordinal)) {
                throw 'DirectMaven effective execution must retain only java.io.tmpdir and LWJGL fork-local properties after capacity arguments'
            }
            $executionTmp = $executionTokens[-2].Substring($tmpPrefix.Length)
            $executionLwjgl = $executionTokens[-1].Substring($lwjglPrefix.Length)
            Assert-NoUnresolvedPlaceholder $executionTmp 'DirectMaven effective java.io.tmpdir'
            if ($executionLwjgl -match '@\{[^}]*\}' -or
                $executionLwjgl.Replace('${surefire.forkNumber}', '') -match '\$\{[^}]*\}') {
                throw 'DirectMaven effective LWJGL path contains an unresolved property placeholder'
            }
            if ([System.Text.RegularExpressions.Regex]::Matches(
                    $executionLwjgl, '\$\{surefire\.forkNumber\}').Count -ne 1) {
                throw 'DirectMaven effective LWJGL path must contain exactly one fork-number placeholder'
            }

            foreach ($pathProof in @(
                    [pscustomobject]@{ Value = [string]$effective.projectBuildDirectory; Name = 'project.build.directory' },
                    [pscustomobject]@{ Value = [string]$effective.'openggf.test.tmpdir'; Name = 'openggf.test.tmpdir' },
                    [pscustomobject]@{ Value = $Worktree; Name = 'CanonicalWorktree' })) {
                Assert-NoUnresolvedPlaceholder $pathProof.Value "DirectMaven effective $($pathProof.Name)"
                if (-not [System.IO.Path]::IsPathFullyQualified($pathProof.Value)) {
                    throw "DirectMaven $($pathProof.Name) must be an absolute path"
                }
            }
            $worktreePath = [System.IO.Path]::GetFullPath($Worktree)
            $buildPath = [System.IO.Path]::GetFullPath([string]$effective.projectBuildDirectory)
            $tmpPath = [System.IO.Path]::GetFullPath([string]$effective.'openggf.test.tmpdir')
            if (-not (Test-PathWithin $buildPath $worktreePath)) {
                throw 'DirectMaven project.build.directory must be contained by CanonicalWorktree'
            }
            $expectedTmp = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($buildPath, 'test-tmp'))
            if (-not (Test-PathsEqual $tmpPath $expectedTmp) -or
                -not (Test-PathsEqual $executionTmp $expectedTmp)) {
                throw 'DirectMaven effective java.io.tmpdir must equal resolved openggf.test.tmpdir below project.build.directory'
            }
            $expectedLwjgl = [System.IO.Path]::Combine($expectedTmp, 'lwjgl-${surefire.forkNumber}')
            if (-not (Test-PathsEqual $executionLwjgl $expectedLwjgl)) {
                throw 'DirectMaven effective LWJGL path must be fork-local below resolved openggf.test.tmpdir'
            }
        }
        else {
            $expectedPrefix = [string]$effective.projectArgLine + ' -Duser.home='
            if (-not $argumentValue.StartsWith($expectedPrefix, [System.StringComparison]::Ordinal)) {
                throw 'Approved Maven property surefire.argLine must preserve the effective project property before adapter-owned session properties'
            }
            $adapterSuffix = $argumentValue.Substring($expectedPrefix.Length)
            $lwjglMarker = ' -Dorg.lwjgl.system.SharedLibraryExtractPath='
            $lwjglOffset = $adapterSuffix.IndexOf($lwjglMarker, [System.StringComparison]::Ordinal)
            if ($lwjglOffset -le 0) {
                throw 'Approved Maven property surefire.argLine must contain the adapter-owned user.home and LWJGL extraction properties'
            }
            $userHomeValue = $adapterSuffix.Substring(0, $lwjglOffset)
            $lwjglValue = $adapterSuffix.Substring($lwjglOffset + $lwjglMarker.Length)
            if ($userHomeValue -match '\s' -or $lwjglValue -match '\s' -or
                $lwjglValue -notmatch '[/\\]lwjgl-\$\{surefire\.forkNumber\}$') {
                throw 'Approved Maven property surefire.argLine has invalid adapter-owned session property values'
            }
        }
    }
    if ($ApprovedProperties.ContainsKey('surefire.forkCount')) {
        $argumentValue = [string]$ApprovedProperties['surefire.forkCount'].Value
        if ($argumentValue -cne [string]$effective.forkCount) {
            throw "Approved Maven property surefire.forkCount does not equal the effective forkCount: argv=[$argumentValue] effective=[$($effective.forkCount)]"
        }
    }
    if ($ApprovedProperties.ContainsKey('surefire.reuseForks')) {
        $argumentValue = [string]$ApprovedProperties['surefire.reuseForks'].Value
        if ($argumentValue -cne [string]$effective.reuseForks) {
            throw "Approved Maven property surefire.reuseForks does not equal the effective reuseForks: argv=[$argumentValue] effective=[$($effective.reuseForks)]"
        }
    }
    $authenticatedRunOrder = ''
    $effectiveRunOrderEvidence = if ([int]$effective.runOrderCount -eq 0) { '<absent>' } else { [string]$effective.runOrder }
    if ($ApprovedProperties.ContainsKey('surefire.runOrder')) {
        if ([int]$effective.runOrderCount -gt 1) {
            throw "Effective Surefire configuration may contain at most one runOrder; found $($effective.runOrderCount)"
        }
        $argumentValue = [string]$ApprovedProperties['surefire.runOrder'].Value
        $authenticatedRunOrder = $argumentValue
        if ([int]$effective.runOrderCount -eq 1) {
            if ([string]::IsNullOrEmpty([string]$effective.runOrder)) {
                throw 'Effective Surefire runOrder must be non-empty when present'
            }
            if ($argumentValue -cne [string]$effective.runOrder) {
                throw "Approved Maven property surefire.runOrder does not equal the effective runOrder: argv=[$argumentValue] effective=[$($effective.runOrder)]"
            }
            if ([string]$effective.runOrder -cne 'alphabetical') {
                throw "Effective Surefire runOrder must be exactly alphabetical: [$($effective.runOrder)]"
            }
        }
    }
    Write-Host "Effective Surefire configuration: excludes=$(@($effective.excludes) -join ',') groups=$($effective.groups) excludedGroups=$($effective.excludedGroups) projectArgLine=$($effective.projectArgLine) executionArgLine=$($effective.argLine) forkCount=$($effective.forkCount) reuseForks=$($effective.reuseForks) runOrder=$authenticatedRunOrder effectiveRunOrder=$effectiveRunOrderEvidence"
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
    $junitWorktreeTemp = '(?<=<WORKTREE>[\\/]target[\\/]test-tmp[\\/])junit\d+(?=[\\/])'
    $normalized = [System.Text.RegularExpressions.Regex]::Replace(
        $normalized,
        $junitWorktreeTemp,
        '<JUNIT_TEMP>',
        [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
    $modSnapshotTemp = '(?<=[\\/])openggf-mod-snapshot-\d+(?=[:\\/]|$)'
    $normalized = [System.Text.RegularExpressions.Regex]::Replace(
        $normalized,
        $modSnapshotTemp,
        'openggf-mod-snapshot-<TEMP_ID>',
        [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
    $jansiExtraction = '(?<![0-9A-Za-z])jansi-(\d+(?:\.\d+)*)-[0-9a-fA-F]+-(?=libjansi(?:\.|$))'
    $normalized = [System.Text.RegularExpressions.Regex]::Replace(
        $normalized,
        $jansiExtraction,
        'jansi-$1-<EXTRACTION_ID>-',
        [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
    $iso8601 = '(?<![0-9A-Za-z])\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:?\d{2})?(?![0-9A-Za-z:+-])'
    return [System.Text.RegularExpressions.Regex]::Replace(
        $normalized,
        $iso8601,
        '<TIMESTAMP>',
        [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
}

function Assert-NoReparsePointInAncestry {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Context
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $root = [System.IO.Path]::GetPathRoot($fullPath)
    if ([string]::IsNullOrEmpty($root)) {
        throw "DirectMaven cannot determine the physical root for ${Context}: $fullPath"
    }
    $current = $root
    $segments = [System.Text.RegularExpressions.Regex]::Split(
        $fullPath.Substring($root.Length),
        '[/\\]+')
    $paths = [System.Collections.Generic.List[string]]::new()
    $paths.Add($root)
    foreach ($segment in $segments) {
        if ($segment.Length -eq 0) {
            continue
        }
        $current = [System.IO.Path]::Combine($current, $segment)
        $paths.Add($current)
    }
    foreach ($candidate in $paths) {
        try {
            $attributes = [System.IO.File]::GetAttributes($candidate)
        }
        catch {
            throw "DirectMaven cannot inspect trusted ${Context} ancestry at ${candidate}: $($_.Exception.Message)"
        }
        if (($attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "DirectMaven trusted ${Context} ancestry contains a symbolic link or reparse point: $candidate"
        }
    }
    return $fullPath
}

function Get-TrustedSurefireXmlFiles {
    param([Parameter(Mandatory)] [string] $Root)

    $files = [System.Collections.Generic.List[string]]::new()
    $directories = [System.Collections.Generic.Stack[string]]::new()
    $directories.Push($Root)
    $extensionComparison = if ([System.IO.Path]::DirectorySeparatorChar -eq '\') {
        [System.StringComparison]::OrdinalIgnoreCase
    } else {
        [System.StringComparison]::Ordinal
    }
    while ($directories.Count -gt 0) {
        $directory = $directories.Pop()
        foreach ($entry in [System.IO.Directory]::EnumerateFileSystemEntries(
                $directory, '*', [System.IO.SearchOption]::TopDirectoryOnly)) {
            $fullEntry = [System.IO.Path]::GetFullPath($entry)
            try {
                $attributes = [System.IO.File]::GetAttributes($fullEntry)
            }
            catch {
                throw "DirectMaven cannot inspect trusted report entry ${fullEntry}: $($_.Exception.Message)"
            }
            if (($attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "DirectMaven trusted report tree contains a symbolic link or reparse point: $fullEntry"
            }
            if (($attributes -band [System.IO.FileAttributes]::Directory) -ne 0) {
                $directories.Push($fullEntry)
            }
            elseif ([System.IO.Path]::GetExtension($fullEntry).Equals('.xml', $extensionComparison)) {
                $files.Add($fullEntry)
            }
        }
    }
    return $files.ToArray()
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
$selected = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($className in $sourceClasses) {
    [void]$selected.Add($className)
}
$repeatedIdentityContract = Read-RepeatedIdentityCardinality $RepeatedIdentityCardinalityPath $selected
$preflightValues = [ordered]@{
    SelectorPatternInventory = $SelectorPatternInventory
    MavenArgumentInventory = $MavenArgumentInventory
    RuntimeInputs = $RuntimeInputs
    EffectivePomPath = $EffectivePomPath
}
$suppliedPreflight = @($preflightValues.Values | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }).Count
if ($suppliedPreflight -ne 0 -and $suppliedPreflight -ne $preflightValues.Count) {
    $missingPreflight = @($preflightValues.Keys | Where-Object { [string]::IsNullOrWhiteSpace([string]$preflightValues[$_]) })
    throw "Explicit-source preflight is atomic; missing: $($missingPreflight -join ', ')"
}
if ($repeatedIdentityContract.Path.Length -ne 0 -and $suppliedPreflight -ne $preflightValues.Count) {
    throw 'RepeatedIdentityCardinalityPath requires the complete atomic explicit-source preflight'
}
if (-not [string]::IsNullOrWhiteSpace($MavenLocalRepositoryPath) -and
    (-not $DirectMaven -or $suppliedPreflight -ne $preflightValues.Count)) {
    throw 'MavenLocalRepositoryPath is used only by complete DirectMaven effective-POM preflight'
}
if ($suppliedPreflight -eq $preflightValues.Count) {
    $selectorContract = Assert-ExplicitSourceSelectorContract `
        $sourceClasses `
        $SelectorPatternInventory `
        $MavenArgumentInventory `
        $RuntimeInputs `
        $DirectMaven.IsPresent
    Assert-EffectiveSurefireContract `
        $EffectivePomPath `
        $SurefireExecutionId `
        $selectorContract.ApprovedProperties `
        $DirectMaven.IsPresent `
        $CanonicalWorktree `
        $MavenLocalRepositoryPath
    if ($DirectMaven) {
        $canonicalEffectivePom = (Resolve-Path -LiteralPath $EffectivePomPath).Path
        Assert-RuntimeInputExactlyOnce $canonicalEffectivePom $RuntimeInputs 'Effective POM capacity proof'
        if (-not [string]::IsNullOrWhiteSpace($MavenLocalRepositoryPath)) {
            $canonicalMavenLocalRepository = (Resolve-Path -LiteralPath $MavenLocalRepositoryPath).Path
            Assert-RuntimeInputAbsent `
                $canonicalMavenLocalRepository `
                $RuntimeInputs `
                'MavenLocalRepositoryPath'
        }
    }
    if ($repeatedIdentityContract.Path.Length -ne 0) {
        Assert-RuntimeInputExactlyOnce $repeatedIdentityContract.Path $RuntimeInputs 'Repeated-identity cardinality allowlist'
    }
}
$helpers = Read-HelperAllowlist $EmptyHelperAllowlist $selected

$reportFiles = [System.Collections.Generic.List[string]]::new()
$reportPathsSeen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$expandedReportRoots = @($ReportRoot | ForEach-Object { $_.Split([System.IO.Path]::PathSeparator, [System.StringSplitOptions]::RemoveEmptyEntries) })
$directMavenReportRoot = ''
$pathComparison = if ([System.IO.Path]::DirectorySeparatorChar -eq '\') {
    [System.StringComparison]::OrdinalIgnoreCase
} else {
    [System.StringComparison]::Ordinal
}
if ($DirectMaven) {
    if ([string]::IsNullOrWhiteSpace($CanonicalWorktree)) {
        throw 'DirectMaven provenance requires CanonicalWorktree'
    }
    if (-not [string]::IsNullOrWhiteSpace($SessionRoot) -or
        -not [string]::IsNullOrWhiteSpace($RunId)) {
        throw 'DirectMaven provenance rejects SessionRoot and RunId; no coordinator session exists'
    }
    $canonicalWorktreeInput = [System.IO.Path]::GetFullPath($CanonicalWorktree)
    if (-not (Test-Path -LiteralPath $canonicalWorktreeInput -PathType Container)) {
        throw "DirectMaven CanonicalWorktree does not exist: $CanonicalWorktree"
    }
    $CanonicalWorktree = Assert-NoReparsePointInAncestry $canonicalWorktreeInput 'canonical worktree'
    $directMavenReportRoot = [System.IO.Path]::GetFullPath(
        [System.IO.Path]::Combine($CanonicalWorktree, 'target', 'surefire-reports'))
}
foreach ($root in $expandedReportRoots) {
    $reportRootInput = [System.IO.Path]::GetFullPath($root)
    if (-not (Test-Path -LiteralPath $reportRootInput -PathType Container)) {
        throw "Surefire report root does not exist: $root"
    }
    $resolvedRoot = if ($DirectMaven) {
        Assert-NoReparsePointInAncestry $reportRootInput 'report root'
    } else {
        [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $root).Path)
    }
    if ($DirectMaven) {
        $rootPrefix = $directMavenReportRoot + [System.IO.Path]::DirectorySeparatorChar
        if (-not $resolvedRoot.Equals($directMavenReportRoot, $pathComparison) -and
            -not $resolvedRoot.StartsWith($rootPrefix, $pathComparison)) {
            throw "DirectMaven report root must be target/surefire-reports inside CanonicalWorktree: $resolvedRoot"
        }
    }
    $enumeratedFiles = if ($DirectMaven) {
        @(Get-TrustedSurefireXmlFiles $resolvedRoot)
    } else {
        @([System.IO.Directory]::EnumerateFiles($resolvedRoot, '*.xml', [System.IO.SearchOption]::AllDirectories))
    }
    foreach ($file in $enumeratedFiles) {
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
        $rawClassName = [string]$testcase.GetAttribute('classname')
        $methodName = [string]$testcase.GetAttribute('name')
        if ($rawClassName.Length -eq 0 -or $methodName.Length -eq 0) {
            throw "Surefire testcase in $reportPath lacks classname or name"
        }
        $className = Resolve-SurefireTestcaseClassName $rawClassName $testcase $reportPath $selected
        $owningRoot = Get-SelectedOwningRoot $className $selected
        if ($owningRoot.Length -eq 0) {
            throw "Surefire report contains unselected executable class: $className"
        }
        [void]$coveredRoots.Add($owningRoot)
        $identity = "$className#$methodName"
        if ($rowsByIdentity.ContainsKey($identity)) {
            if (-not $repeatedIdentityContract.Entries.ContainsKey($identity)) {
                throw "Duplicate Surefire testcase identity: $identity"
            }
        }
        else {
            $rowsByIdentity.Add($identity, [System.Collections.Generic.List[object]]::new())
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
            if ((-not $DirectMaven) -and
                ([string]::IsNullOrWhiteSpace($CanonicalWorktree) -or
                 [string]::IsNullOrWhiteSpace($SessionRoot) -or
                 [string]::IsNullOrWhiteSpace($RunId))) {
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

        $rowsByIdentity[$identity].Add([pscustomobject]@{
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
            report_path = $reportPath
        })
    }
}

foreach ($identity in $repeatedIdentityContract.Entries.Keys) {
    $expected = $repeatedIdentityContract.Entries[$identity].cardinality
    $actual = if ($rowsByIdentity.ContainsKey($identity)) { $rowsByIdentity[$identity].Count } else { 0 }
    if ($actual -ne $expected) {
        throw "Repeated Surefire testcase identity cardinality mismatch: $identity expected=$expected actual=$actual"
    }
    $reports = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($row in $rowsByIdentity[$identity]) {
        [void]$reports.Add([string]$row.report_path)
    }
    if ($reports.Count -ne 1) {
        throw "Repeated Surefire testcase identity must occur in exactly one report: $identity reports=$($reports.Count)"
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
$exportedIdentityCount = 0
$emittedIdentities = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($identity in (Get-OrdinalSorted @($rowsByIdentity.Keys))) {
    $identityRows = $rowsByIdentity[$identity]
    for ($index = 0; $index -lt $identityRows.Count; $index++) {
        $row = $identityRows[$index].PSObject.Copy()
        if ($identityRows.Count -gt 1) {
            $suffix = "@xml-occurrence[$($index + 1)/$($identityRows.Count)]"
            $row.method = [string]$row.method + $suffix
            $row.identity = [string]$row.class + '#' + [string]$row.method
        }
        if (-not $emittedIdentities.Add([string]$row.identity)) {
            throw "Repeated-identity occurrence suffix collides with another exported identity: $($row.identity)"
        }
        $fields = foreach ($column in $columns) {
            ConvertTo-TsvField ([string]$row.$column)
        }
        $lines.Add($fields -join "`t")
        $exportedIdentityCount++
    }
}
$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}
[System.IO.File]::WriteAllText($OutputPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))

Write-Host "Exported $exportedIdentityCount Surefire testcase outcomes to $OutputPath"
