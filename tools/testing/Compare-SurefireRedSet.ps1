[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string] $ReportsPath,

    [string] $ExpectedPath,

    [string] $WriteActualPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Fail-Contract {
    param([Parameter(Mandatory)] [string] $Message)

    Write-Error $Message
    exit 1
}

function Get-ExpectedIdentities {
    param([Parameter(Mandatory)] [string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Fail-Contract "Expected red-set file does not exist: $Path"
    }

    $identities = @(
        Get-Content -LiteralPath $Path | ForEach-Object {
            $identity = $_.Trim()
            if ($identity.Length -gt 0) { $identity }
        }
    )
    $duplicates = @($identities | Group-Object | Where-Object Count -gt 1)
    if ($duplicates.Count -gt 0) {
        Fail-Contract ("Expected red-set contains duplicate identities: " + (($duplicates | ForEach-Object Name) -join ', '))
    }
    return @($identities | Sort-Object)
}

function Get-SurefireTestCases {
    param([Parameter(Mandatory)] [string] $Path)

    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = $null
    try {
        $reader = [System.Xml.XmlReader]::Create($Path, $settings)
        $document = [System.Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($reader)
    }
    catch {
        Fail-Contract "Malformed Surefire XML '$Path': $($_.Exception.Message)"
    }
    finally {
        if ($null -ne $reader) { $reader.Dispose() }
    }

    return @($document.SelectNodes('//testcase'))
}

if (-not (Test-Path -LiteralPath $ReportsPath -PathType Container)) {
    Fail-Contract "Reports path does not exist or is not a directory: $ReportsPath"
}

$reports = @(Get-ChildItem -LiteralPath $ReportsPath -Filter 'TEST-*.xml' -File -Recurse | Sort-Object FullName)
if ($reports.Count -eq 0) {
    Fail-Contract "No TEST-*.xml files found under: $ReportsPath"
}

$actual = [System.Collections.Generic.List[string]]::new()
foreach ($report in $reports) {
    foreach ($testCase in (Get-SurefireTestCases -Path $report.FullName)) {
        $className = $testCase.GetAttribute('classname')
        $testName = $testCase.GetAttribute('name')
        if ([string]::IsNullOrWhiteSpace($className) -or [string]::IsNullOrWhiteSpace($testName)) {
            Fail-Contract "Surefire testcase in '$($report.FullName)' is missing classname or name."
        }

        $children = @($testCase.ChildNodes | Where-Object { $_.NodeType -eq [System.Xml.XmlNodeType]::Element })
        $disabled = @($children | Where-Object { $_.LocalName -in @('skipped', 'disabled') })
        $disabledAttribute = $testCase.GetAttribute('disabled')
        $statusAttribute = $testCase.GetAttribute('status')
        if ($disabled.Count -gt 0 -or $disabledAttribute -eq 'true' -or $statusAttribute -in @('skipped', 'disabled')) {
            Fail-Contract "Skipped or disabled testcase found: $className#$testName in '$($report.FullName)'."
        }

        $outcomeNodes = @($children | Where-Object { $_.LocalName -in @('failure', 'error') })
        foreach ($outcome in $outcomeNodes) {
            [void] $actual.Add("$className#$testName")
        }
    }
}

$actual = @($actual | Sort-Object)
$actualDuplicates = @($actual | Group-Object | Where-Object Count -gt 1)
if ($actualDuplicates.Count -gt 0) {
    Fail-Contract ("Actual red set contains duplicate identities: " + (($actualDuplicates | ForEach-Object Name) -join ', '))
}

if ($WriteActualPath) {
    $parent = Split-Path -Parent $WriteActualPath
    if ($parent -and -not (Test-Path -LiteralPath $parent -PathType Container)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    Set-Content -LiteralPath $WriteActualPath -Value $actual
}

if ($ExpectedPath) {
    $expected = Get-ExpectedIdentities -Path $ExpectedPath
    $missing = @($expected | Where-Object { $_ -notin $actual })
    $extra = @($actual | Where-Object { $_ -notin $expected })
    if ($missing.Count -gt 0 -or $extra.Count -gt 0) {
        $details = [System.Collections.Generic.List[string]]::new()
        if ($missing.Count -gt 0) { [void] $details.Add("missing expected: $($missing -join ', ')") }
        if ($extra.Count -gt 0) { [void] $details.Add("unexpected actual: $($extra -join ', ')") }
        Fail-Contract ("Surefire red-set mismatch: " + ($details -join '; '))
    }
}

exit 0
