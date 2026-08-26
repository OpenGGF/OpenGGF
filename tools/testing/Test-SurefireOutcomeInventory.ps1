[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$exportScript = Join-Path $PSScriptRoot 'Export-SurefireOutcomeInventory.ps1'
$compareScript = Join-Path $PSScriptRoot 'Compare-SurefireOutcomeInventory.ps1'
$partitionScript = Join-Path $PSScriptRoot 'New-SurefirePartitionMap.ps1'
$failures = [System.Collections.Generic.List[string]]::new()
$testCount = 0

function Invoke-Case {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [scriptblock] $Body
    )

    $script:testCount++
    try {
        & $Body
        Write-Host "PASS $Name"
    }
    catch {
        $script:failures.Add("${Name}: $($_.Exception.Message)")
        Write-Host "FAIL $Name"
    }
}

function Assert-Equal {
    param(
        [AllowNull()] $Actual,
        [AllowNull()] $Expected,
        [Parameter(Mandatory)] [string] $Context
    )

    if ($Actual -cne $Expected) {
        throw "$Context expected [$Expected], actual [$Actual]"
    }
}

function Assert-True {
    param(
        [Parameter(Mandatory)] [bool] $Condition,
        [Parameter(Mandatory)] [string] $Context
    )

    if (-not $Condition) {
        throw $Context
    }
}

function Write-Utf8File {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [AllowEmptyString()] [string] $Content
    )

    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-Tool {
    param(
        [Parameter(Mandatory)] [string] $Script,
        [Parameter(Mandatory)] [string[]] $Arguments
    )

    $lines = @(& pwsh -NoProfile -File $Script @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($lines -join "`n")
    }
}

function Assert-Succeeded {
    param($Result, [string] $Context)
    if ($Result.ExitCode -ne 0) {
        throw "$Context failed with exit $($Result.ExitCode): $($Result.Output)"
    }
}

function Assert-Failed {
    param($Result, [string] $Pattern, [string] $Context)
    if ($Result.ExitCode -eq 0) {
        throw "$Context unexpectedly succeeded"
    }
    if ($Result.Output -notmatch $Pattern) {
        throw "$Context did not report /$Pattern/: $($Result.Output)"
    }
}

function New-InventoryRow {
    param(
        [string] $Identity,
        [string] $Class,
        [string] $Method,
        [string] $Outcome,
        [string] $RedKind = '',
        [string] $ExceptionType = '',
        [string] $Message = '',
        [string] $BodyBytes = '',
        [string] $BodySha = '',
        [string] $Report = 'TEST-fixture.xml'
    )
    return "$Identity`t$Class`t$Method`t$Outcome`t$RedKind`t$ExceptionType`t$Message`t$BodyBytes`t$BodySha`t$Report"
}

foreach ($implementation in @($exportScript, $compareScript, $partitionScript)) {
    if (-not (Test-Path -LiteralPath $implementation -PathType Leaf)) {
        throw "Required implementation script is absent: $implementation"
    }
}

$scratch = Join-Path ([System.IO.Path]::GetTempPath()) ("openggf-surefire-inventory-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null

try {
    Invoke-Case 'export emits ordinal exact identities and outcomes with complete normalized red signatures' {
        $case = Join-Path $scratch 'export-complete'
        $reports = Join-Path $case 'reports'
        New-Item -ItemType Directory -Path $reports -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "zeta.PassTest`nalpha.FailTest`nmu.ErrorTest`nbeta.SkipTest`nbeta.DisabledTest`ngamma.ParamTest`n"

        $longTail = ('A' * 5000) + 'TAIL'
        $xml = @"
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="fixture" tests="6">
  <testcase classname="zeta.PassTest" name="passes"/>
  <testcase classname="alpha.FailTest" name="fails"><failure type="java.lang.AssertionError" message="wanted /work/tree at 2026-08-26T12:34:56Z">boom at /work/tree/src/Test.java:1
run session-20260826-abcdef12 2026-08-26T12:34:56Z</failure></testcase>
  <testcase classname="mu.ErrorTest" name="errors"><error type="java.lang.IllegalStateException" message="under /session/root">$longTail</error></testcase>
  <testcase classname="beta.SkipTest" name="skips"><skipped message="assumption"/></testcase>
  <testcase classname="beta.DisabledTest" name="disabled"><disabled message="disabled"/></testcase>
  <testcase classname="gamma.ParamTest" name="case[2] value=two"/>
</testsuite>
"@
        Write-Utf8File (Join-Path $reports 'TEST-fixture.xml') $xml

        $result = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes,
            '-OutputPath', $output,
            '-CanonicalWorktree', '/work/tree',
            '-SessionRoot', '/session/root',
            '-RunId', 'session-20260826-abcdef12',
            '-ReportRoot', $reports
        )
        Assert-Succeeded $result 'complete export'

        $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal $rows.Count 6 'row count'
        Assert-Equal (($rows.identity) -join '|') 'alpha.FailTest#fails|beta.DisabledTest#disabled|beta.SkipTest#skips|gamma.ParamTest#case[2] value=two|mu.ErrorTest#errors|zeta.PassTest#passes' 'ordinal identities'
        Assert-Equal (($rows.outcome) -join '|') 'FAILURE|SKIPPED|SKIPPED|PASS|ERROR|PASS' 'exact outcomes'

        $failure = $rows[0]
        Assert-Equal $failure.red_kind 'failure' 'failure kind'
        Assert-Equal $failure.exception_type 'java.lang.AssertionError' 'failure exception type'
        Assert-Equal $failure.normalized_message 'wanted <WORKTREE> at <TIMESTAMP>' 'failure normalized message'
        Assert-Equal $failure.red_body_bytes '59' 'failure complete normalized byte count'
        Assert-Equal $failure.red_body_sha256 '3e065ef48bd7d661955a52a6edaa292b083ff9d3f02c43f6c7fe23bd0894a5a1' 'failure complete normalized SHA-256'

        $error = $rows[4]
        Assert-Equal $error.red_body_bytes '5004' 'long error body is not truncated'
        Assert-Equal $error.red_body_sha256 'e03d59d85356a00a97097d7e74c5b967db83858ff52097853ee863007e33d67f' 'long error body SHA-256'
        Assert-Equal $error.report 'TEST-fixture.xml' 'relative report identity'
    }

    Invoke-Case 'export preserves boundary whitespace in exact parameterized identities' {
        $case = Join-Path $scratch 'export-boundary-identities'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes " example.BoundaryTest`nexample.BoundaryTest`nexample.BoundaryTest `n"
        Write-Utf8File (Join-Path $case 'TEST-boundaries.xml') @'
<testsuite>
  <testcase classname=" example.BoundaryTest" name="case[1]"/>
  <testcase classname="example.BoundaryTest" name=" case[1]"/>
  <testcase classname="example.BoundaryTest" name="case[1]"/>
  <testcase classname="example.BoundaryTest" name="case[1] "/>
  <testcase classname="example.BoundaryTest " name="case[1]"/>
</testsuite>
'@
        $result = Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $output, '-ReportRoot', $case)
        Assert-Succeeded $result 'boundary identity export'
        $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal (($rows.identity) -join '|') '\sexample.BoundaryTest#case[1]|example.BoundaryTest #case[1]|example.BoundaryTest# case[1]|example.BoundaryTest#case[1]|example.BoundaryTest#case[1]\s' 'boundary whitespace remains identity data'
        Assert-Equal (($rows.class) -join '|') '\sexample.BoundaryTest|example.BoundaryTest\s|example.BoundaryTest|example.BoundaryTest|example.BoundaryTest' 'boundary classname values remain exact'
        Assert-Equal (($rows.method) -join '|') 'case[1]|case[1]|\scase[1]|case[1]|case[1]\s' 'boundary parameterized names remain exact'
    }

    Invoke-Case 'export reversibly escapes actual controls and literal backslash sequences' {
        $case = Join-Path $scratch 'export-tsv-escaping'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.EscapeTest`n"
        Write-Utf8File (Join-Path $case 'TEST-escape.xml') @'
<testsuite>
  <testcase classname="example.EscapeTest" name="case&#xA;value"/>
  <testcase classname="example.EscapeTest" name="case&#xD;value"/>
  <testcase classname="example.EscapeTest" name="case\nvalue"/>
  <testcase classname="example.EscapeTest" name="red"><failure type="example.Type\Kind" message="actual&#xA;line carriage&#xD;return literal \n tab&#x9;slash\end">body</failure></testcase>
</testsuite>
'@
        $result = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes,
            '-OutputPath', $output,
            '-CanonicalWorktree', '/work/tree',
            '-SessionRoot', '/session/root',
            '-RunId', 'run-escape-12345678',
            '-ReportRoot', $case
        )
        Assert-Succeeded $result 'TSV escaping export'
        $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal $rows[0].identity 'example.EscapeTest#case\nvalue' 'actual identity newline encoding'
        Assert-Equal $rows[1].identity 'example.EscapeTest#case\rvalue' 'actual identity carriage-return encoding'
        Assert-Equal $rows[2].identity 'example.EscapeTest#case\\nvalue' 'literal identity backslash-n encoding'
        Assert-True ($rows[0].identity -cne $rows[2].identity) 'actual newline and literal backslash-n identities stay distinct'
        Assert-Equal $rows[3].exception_type 'example.Type\\Kind' 'exception type backslash encoding'
        Assert-Equal $rows[3].normalized_message 'actual\nline carriage\nreturn literal \\n tab\tslash\\end' 'normalized newlines literal backslash-n tab and backslash encoding'

        $compareOutput = Join-Path $case 'comparison.tsv'
        $comparison = Invoke-Tool $compareScript @('-CandidateInventoryPath', $output, '-OutputPath', $compareOutput, '-ParentInventoryPath', $output)
        Assert-Succeeded $comparison 'escaped inventory decode and validation'
        Assert-Equal ((Import-Csv -Delimiter "`t" -LiteralPath $compareOutput).classification -join '|') 'MATCH|MATCH|MATCH|MATCH' 'escaped identities round-trip through consumer'
    }

    Invoke-Case 'export reversibly escapes quotes at every position and distinguishes literal quote escapes' {
        $case = Join-Path $scratch 'export-tsv-quotes'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.QuoteTest`n"
        Write-Utf8File (Join-Path $case 'TEST-quotes.xml') @'
<testsuite>
  <testcase classname="example.QuoteTest" name="&quot;begin"><failure type="&quot;Type&quot;" message="&quot;quoted&quot; mid&quot;dle end&quot;">body</failure></testcase>
  <testcase classname="example.QuoteTest" name="mid&quot;dle"/>
  <testcase classname="example.QuoteTest" name="end&quot;"/>
  <testcase classname="example.QuoteTest" name="\q"/>
</testsuite>
'@
        $result = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes,
            '-OutputPath', $output,
            '-CanonicalWorktree', '/work/tree',
            '-SessionRoot', '/session/root',
            '-RunId', 'run-quotes-12345678',
            '-ReportRoot', $case
        )
        Assert-Succeeded $result 'quote TSV export'
        $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal (($rows.identity) -join '|') 'example.QuoteTest#\qbegin|example.QuoteTest#\\q|example.QuoteTest#end\q|example.QuoteTest#mid\qdle' 'quote identities use reversible wire escapes in ordinal raw order'
        Assert-Equal (($rows.method) -join '|') '\qbegin|\\q|end\q|mid\qdle' 'beginning middle end and literal quote escapes remain distinct'
        Assert-Equal $rows[0].exception_type '\qType\q' 'exception type boundary quotes escape'
        Assert-Equal $rows[0].normalized_message '\qquoted\q mid\qdle end\q' 'message beginning middle and end quotes escape'
        Assert-True (([System.IO.File]::ReadAllText($output)).IndexOf('"', [System.StringComparison]::Ordinal) -lt 0) 'wire rows contain no quote characters for Import-Csv to reinterpret'

        $compareOutput = Join-Path $case 'comparison.tsv'
        $comparison = Invoke-Tool $compareScript @('-CandidateInventoryPath', $output, '-OutputPath', $compareOutput, '-ParentInventoryPath', $output)
        Assert-Succeeded $comparison 'quoted inventory decode and validation'
        Assert-Equal ((Import-Csv -Delimiter "`t" -LiteralPath $compareOutput).classification -join '|') 'MATCH|MATCH|MATCH|MATCH' 'quotes round-trip through consumer'
        Assert-True (([System.IO.File]::ReadAllText($compareOutput)).IndexOf('"', [System.StringComparison]::Ordinal) -lt 0) 'comparison wire rows also contain no quote characters'
    }

    Invoke-Case 'export accepts nested-only and combined ownership while preserving actual classnames' {
        $case = Join-Path $scratch 'export-nested-ownership'
        $nestedOnly = Join-Path $case 'nested-only'
        $combined = Join-Path $case 'combined'
        New-Item -ItemType Directory -Path $nestedOnly, $combined -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.Outer`n"
        Write-Utf8File (Join-Path $nestedOnly 'TEST-nested.xml') '<testsuite><testcase classname="example.Outer$Nested" name="nestedOnly"/></testsuite>'

        $nestedResult = Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $output, '-ReportRoot', $nestedOnly)
        Assert-Succeeded $nestedResult 'nested-only owner export'
        $nestedRows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal $nestedRows.Count 1 'nested-only row count'
        Assert-Equal $nestedRows[0].identity 'example.Outer$Nested#nestedOnly' 'nested-only actual identity'
        Assert-Equal $nestedRows[0].class 'example.Outer$Nested' 'nested-only actual classname'

        Write-Utf8File (Join-Path $combined 'TEST-combined.xml') '<testsuite><testcase classname="example.Outer" name="outer"/><testcase classname="example.Outer$Nested" name="nested"/></testsuite>'
        $combinedResult = Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $output, '-ReportRoot', $combined)
        Assert-Succeeded $combinedResult 'combined owner export'
        $combinedRows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal (($combinedRows.identity) -join '|') 'example.Outer#outer|example.Outer$Nested#nested' 'combined actual identities'
        Assert-Equal (($combinedRows.class) -join '|') 'example.Outer|example.Outer$Nested' 'combined actual classnames'
    }

    Invoke-Case 'export rejects nested selector roots lookalike prefixes duplicate descendants and uncovered roots' {
        $case = Join-Path $scratch 'export-nested-rejections'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'

        Write-Utf8File $classes "example.Outer`$Nested`n"
        Write-Utf8File (Join-Path $case 'TEST-case.xml') '<testsuite><testcase classname="example.Outer$Nested" name="nested"/></testsuite>'
        Assert-Failed (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $output, '-ReportRoot', $case)) 'selector root|source inventory|\$' 'nested selector root'

        Write-Utf8File $classes "example.Outer`n"
        Write-Utf8File (Join-Path $case 'TEST-case.xml') '<testsuite><testcase classname="example.Outerish$Nested" name="lookalike"/></testsuite>'
        Assert-Failed (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $output, '-ReportRoot', $case)) 'unselected|Outerish' 'lookalike nested prefix'

        Write-Utf8File (Join-Path $case 'TEST-case.xml') '<testsuite><testcase classname="example.Outer$Nested" name="same"/><testcase classname="example.Outer$Nested" name="same"/></testsuite>'
        Assert-Failed (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $output, '-ReportRoot', $case)) 'duplicate.*Outer\$Nested#same' 'duplicate nested identity'

        Write-Utf8File (Join-Path $case 'TEST-case.xml') '<testsuite><testcase classname="example.Outerish$Nested" name="lookalike"/></testsuite>'
        Write-Utf8File $classes "example.Outer`nexample.Outerish`n"
        Assert-Failed (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $output, '-ReportRoot', $case)) 'ABSENT[\s\S]*example\.Outer' 'root without exact or nested coverage'
    }

    Invoke-Case 'export normalizes nested red outcomes without changing descendant identity' {
        $case = Join-Path $scratch 'export-nested-red'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.Outer`n"
        Write-Utf8File (Join-Path $case 'TEST-red.xml') '<testsuite><testcase classname="example.Outer$Nested" name="red"><failure type="example.NestedFailure" message="at /work/tree 2026-08-26T12:34:56Z">nested /session/root run-nested-12345678</failure></testcase></testsuite>'
        $result = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes, '-OutputPath', $output,
            '-CanonicalWorktree', '/work/tree', '-SessionRoot', '/session/root', '-RunId', 'run-nested-12345678',
            '-ReportRoot', $case
        )
        Assert-Succeeded $result 'nested red export'
        $row = @(Import-Csv -Delimiter "`t" -LiteralPath $output)[0]
        Assert-Equal $row.identity 'example.Outer$Nested#red' 'nested red identity'
        Assert-Equal $row.normalized_message 'at <WORKTREE> <TIMESTAMP>' 'nested red message normalization'
        Assert-Equal $row.red_body_bytes '30' 'nested red body byte count'
        Assert-Equal $row.red_body_sha256 '3d28e9114476422d99e9e02cfda0a90cb67e5cec0ac467acb1ef03adfa311529' 'nested red body SHA-256'
    }

    Invoke-Case 'export authenticates exact source selector mapping invocation and runtime input' {
        $case = Join-Path $scratch 'export-selector-contract'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $patterns = Join-Path $case 'ordinary.includes'
        $arguments = Join-Path $case 'maven-arguments.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "com.openggf.AlphaTest`ncom.openggf.deep.ZuluTest`n"
        Write-Utf8File $patterns "com/openggf/AlphaTest.java`ncom/openggf/deep/ZuluTest.java`n"
        Write-Utf8File $arguments "-Dmse=relaxed`n-Dsurefire.includesFile=$patterns`ntest`n"
        Write-Utf8File (Join-Path $case 'TEST-fixture.xml') '<testsuite><testcase classname="com.openggf.AlphaTest" name="alpha"/><testcase classname="com.openggf.deep.ZuluTest$Nested" name="zulu"/></testsuite>'

        $result = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes, '-SelectorPatternInventory', $patterns,
            '-MavenArgumentInventory', $arguments, '-RuntimeInputs', $patterns,
            '-OutputPath', $output, '-ReportRoot', $case
        )
        Assert-Succeeded $result 'authenticated selector contract'
        Assert-Equal ((Import-Csv -Delimiter "`t" -LiteralPath $output).Count) 2 'authenticated selector export row count'
    }

    Invoke-Case 'export rejects non-bijective unsafe or competing selector invocations' {
        $case = Join-Path $scratch 'export-selector-rejections'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $patterns = Join-Path $case 'ordinary.includes'
        $arguments = Join-Path $case 'maven-arguments.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "com.openggf.AlphaTest`ncom.openggf.deep.ZuluTest`n"
        Write-Utf8File (Join-Path $case 'TEST-fixture.xml') '<testsuite><testcase classname="com.openggf.AlphaTest" name="alpha"/><testcase classname="com.openggf.deep.ZuluTest" name="zulu"/></testsuite>'

        $invalidPatterns = @(
            [pscustomobject]@{ Name = 'wildcard selector'; Content = "com/openggf/*Test.java`ncom/openggf/deep/ZuluTest.java`n"; Pattern = 'wildcard|pattern' },
            [pscustomobject]@{ Name = 'nested selector'; Content = "com/openggf/AlphaTest.java`ncom/openggf/deep/ZuluTest`$Nested.java`n"; Pattern = '\$|nested' },
            [pscustomobject]@{ Name = 'wrong mapping'; Content = "com/openggf/AlphaTest.java`ncom/openggf/deep/Wrong.java`n"; Pattern = 'bijection|mapping|Zulu' },
            [pscustomobject]@{ Name = 'wrong order'; Content = "com/openggf/deep/ZuluTest.java`ncom/openggf/AlphaTest.java`n"; Pattern = 'ordinal|bijection|mapping' }
        )
        foreach ($fixture in $invalidPatterns) {
            Write-Utf8File $patterns $fixture.Content
            Write-Utf8File $arguments "-Dsurefire.includesFile=$patterns`ntest`n"
            Assert-Failed (Invoke-Tool $exportScript @(
                '-SourceClassInventory', $classes, '-SelectorPatternInventory', $patterns,
                '-MavenArgumentInventory', $arguments, '-RuntimeInputs', $patterns,
                '-OutputPath', $output, '-ReportRoot', $case
            )) $fixture.Pattern $fixture.Name
        }

        Write-Utf8File $patterns "com/openggf/AlphaTest.java`ncom/openggf/deep/ZuluTest.java`n"
        $invalidInvocations = @(
            [pscustomobject]@{ Name = '-Dtest override'; Content = "-Dtest=com.openggf.AlphaTest`n-Dsurefire.includesFile=$patterns`ntest`n"; Runtime = $patterns; Pattern = '-Dtest|selector override' },
            [pscustomobject]@{ Name = 'surefire includes override'; Content = "-Dsurefire.includes=**/Test*.java`n-Dsurefire.includesFile=$patterns`ntest`n"; Runtime = $patterns; Pattern = 'surefire.includes|selector override' },
            [pscustomobject]@{ Name = 'duplicate includes file'; Content = "-Dsurefire.includesFile=$patterns`n-Dsurefire.includesFile=$patterns`ntest`n"; Runtime = $patterns; Pattern = 'exactly one|duplicate|includesFile' },
            [pscustomobject]@{ Name = 'other selector override'; Content = "-DexcludedGroups=slow`n-Dsurefire.includesFile=$patterns`ntest`n"; Runtime = $patterns; Pattern = 'excludedGroups|selector override' },
            [pscustomobject]@{ Name = 'missing runtime input'; Content = "-Dsurefire.includesFile=$patterns`ntest`n"; Runtime = (Join-Path $case 'other-input'); Pattern = 'OPENGGF_RUNTIME_INPUTS|runtime input' },
            [pscustomobject]@{ Name = 'relative includes file'; Content = "-Dsurefire.includesFile=ordinary.includes`ntest`n"; Runtime = $patterns; Pattern = 'canonical absolute|includesFile' }
        )
        foreach ($fixture in $invalidInvocations) {
            Write-Utf8File $arguments $fixture.Content
            Assert-Failed (Invoke-Tool $exportScript @(
                '-SourceClassInventory', $classes, '-SelectorPatternInventory', $patterns,
                '-MavenArgumentInventory', $arguments, '-RuntimeInputs', $fixture.Runtime,
                '-OutputPath', $output, '-ReportRoot', $case
            )) $fixture.Pattern $fixture.Name
        }
    }

    Invoke-Case 'export parses unchanged effective ordinary Surefire selector configuration' {
        $case = Join-Path $scratch 'export-effective-pom'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $baselinePom = Join-Path $case 'baseline-effective-pom.xml'
        $selectorPom = Join-Path $case 'selector-effective-pom.xml'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.Test`n"
        Write-Utf8File (Join-Path $case 'TEST-fixture.xml') '<testsuite><testcase classname="example.Test" name="test"/></testsuite>'
        $pom = @'
<project xmlns="http://maven.apache.org/POM/4.0.0"><build><plugins><plugin>
  <artifactId>maven-surefire-plugin</artifactId><executions><execution><id>default-test</id><configuration>
    <excludes><exclude>**/*Guard.java</exclude><exclude>**/ExcludedTest.java</exclude></excludes>
    <groups>ordinary</groups><excludedGroups>quarantined</excludedGroups><forkCount>1</forkCount><reuseForks>true</reuseForks>
  </configuration></execution></executions>
</plugin></plugins></build></project>
'@
        Write-Utf8File $baselinePom $pom
        Write-Utf8File $selectorPom $pom
        $result = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes, '-EffectivePomPath', $baselinePom,
            '-SelectorEffectivePomPath', $selectorPom, '-SurefireExecutionId', 'default-test',
            '-OutputPath', $output, '-ReportRoot', $case
        )
        Assert-Succeeded $result 'unchanged effective POM contract'
        Assert-True ($result.Output -match 'excludes=.*Guard.*ExcludedTest') 'effective POM evidence records excludes'
        Assert-True ($result.Output -match 'groups=ordinary.*excludedGroups=quarantined.*forkCount=1.*reuseForks=true') 'effective POM evidence records groups and fork settings'

        Write-Utf8File $selectorPom ($pom.Replace('<forkCount>1</forkCount>', '<forkCount>2</forkCount>'))
        Assert-Failed (Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes, '-EffectivePomPath', $baselinePom,
            '-SelectorEffectivePomPath', $selectorPom, '-SurefireExecutionId', 'default-test',
            '-OutputPath', $output, '-ReportRoot', $case
        )) 'forkCount|unchanged|effective' 'changed effective fork setting'

        Write-Utf8File $selectorPom ($pom.Replace('<excludes>', '<includes><include>**/Test.java</include></includes><excludes>'))
        Assert-Failed (Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes, '-EffectivePomPath', $baselinePom,
            '-SelectorEffectivePomPath', $selectorPom, '-SurefireExecutionId', 'default-test',
            '-OutputPath', $output, '-ReportRoot', $case
        )) 'includes|ordinary' 'configured ordinary includes'
    }

    Invoke-Case 'export reads all report roots and accepts only reviewed empty helpers with reasons' {
        $case = Join-Path $scratch 'export-roots-helper'
        $rootA = Join-Path $case 'a'
        $rootB = Join-Path $case 'b'
        New-Item -ItemType Directory -Path $rootA, $rootB -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $allowlist = Join-Path $case 'helpers.tsv'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.FirstTest`nexample.SecondTest`nexample.SharedFixture`n"
        Write-Utf8File $allowlist "class`treason`nexample.SharedFixture`tAbstract fixture selected by naming convention`n"
        Write-Utf8File (Join-Path $rootA 'TEST-first.xml') '<testsuite><testcase classname="example.FirstTest" name="first"/></testsuite>'
        Write-Utf8File (Join-Path $rootB 'TEST-second.xml') '<testsuite><testcase classname="example.SecondTest" name="second"/></testsuite>'

        $result = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes,
            '-OutputPath', $output,
            '-EmptyHelperAllowlist', $allowlist,
            '-ReportRoot', ($rootA + [System.IO.Path]::PathSeparator + $rootB)
        )
        Assert-Succeeded $result 'multi-root helper export'
        Assert-Equal ((Import-Csv -Delimiter "`t" -LiteralPath $output).Count) 2 'helper does not create an outcome'

        Write-Utf8File $allowlist "class`treason`nexample.SharedFixture`t`n"
        $invalid = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes,
            '-OutputPath', $output,
            '-EmptyHelperAllowlist', $allowlist,
            '-ReportRoot', ($rootA + [System.IO.Path]::PathSeparator + $rootB)
        )
        Assert-Failed $invalid 'reason' 'reasonless helper allowlist'
    }

    Invoke-Case 'export rejects malformed XML, DTD input, duplicate identities, and selected classes without reports' {
        $case = Join-Path $scratch 'export-rejections'
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        Write-Utf8File $classes "example.OneTest`n"

        $malformedRoot = Join-Path $case 'malformed'
        New-Item -ItemType Directory -Path $malformedRoot | Out-Null
        Write-Utf8File (Join-Path $malformedRoot 'TEST-bad.xml') '<testsuite><testcase classname="example.OneTest" name="broken"></testsuite>'
        Assert-Failed (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-ReportRoot', $malformedRoot, '-OutputPath', $output)) 'malformed|XML|parse' 'malformed report'

        $dtdRoot = Join-Path $case 'dtd'
        New-Item -ItemType Directory -Path $dtdRoot | Out-Null
        Write-Utf8File (Join-Path $dtdRoot 'TEST-dtd.xml') '<!DOCTYPE testsuite [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><testsuite><testcase classname="example.OneTest" name="&xxe;"/></testsuite>'
        Assert-Failed (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-ReportRoot', $dtdRoot, '-OutputPath', $output)) 'DTD|prohibit|XML|parse' 'DTD report'

        $duplicateRoot = Join-Path $case 'duplicate'
        New-Item -ItemType Directory -Path $duplicateRoot | Out-Null
        Write-Utf8File (Join-Path $duplicateRoot 'TEST-a.xml') '<testsuite><testcase classname="example.OneTest" name="same"/></testsuite>'
        Write-Utf8File (Join-Path $duplicateRoot 'TEST-b.xml') '<testsuite><testcase classname="example.OneTest" name="same"/></testsuite>'
        Assert-Failed (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-ReportRoot', $duplicateRoot, '-OutputPath', $output)) 'duplicate.*example\.OneTest#same' 'duplicate identity'

        $missingRoot = Join-Path $case 'missing'
        New-Item -ItemType Directory -Path $missingRoot | Out-Null
        Write-Utf8File (Join-Path $missingRoot 'TEST-empty.xml') '<testsuite tests="0"/>'
        Assert-Failed (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-ReportRoot', $missingRoot, '-OutputPath', $output)) 'ABSENT[\s\S]*example\.OneTest' 'missing selected class'
    }

    Invoke-Case 'export rejects multiple semantic outcomes including red plus skip and repeated skip' {
        $case = Join-Path $scratch 'export-multiple-outcomes'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.OutcomeTest`n"
        Write-Utf8File (Join-Path $case 'TEST-red-skip.xml') '<testsuite><testcase classname="example.OutcomeTest" name="both"><failure type="example.Failure" message="red">body</failure><skipped/></testcase></testsuite>'
        $redSkip = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes, '-OutputPath', $output,
            '-CanonicalWorktree', '/work/tree', '-SessionRoot', '/session/root', '-RunId', 'run-outcomes-12345678',
            '-ReportRoot', $case
        )
        Assert-Failed $redSkip 'multiple semantic outcomes|multiple.*outcomes' 'red plus skip outcome'

        Write-Utf8File (Join-Path $case 'TEST-red-skip.xml') '<testsuite><testcase classname="example.OutcomeTest" name="twice"><skipped/><disabled/></testcase></testsuite>'
        $repeatedSkip = Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $output, '-ReportRoot', $case)
        Assert-Failed $repeatedSkip 'multiple semantic outcomes|multiple.*outcomes' 'repeated skip outcome'
    }

    Invoke-Case 'export rejects red elements without deterministic kind type message and body signature' {
        $case = Join-Path $scratch 'export-red-signature'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.RedTest`n"
        Write-Utf8File (Join-Path $case 'TEST-red.xml') '<testsuite><testcase classname="example.RedTest" name="red"><failure type="" message=""></failure></testcase></testsuite>'
        Assert-Failed (Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes, '-OutputPath', $output,
            '-CanonicalWorktree', '/work/tree', '-SessionRoot', '/session/root', '-RunId', 'run-red-12345678',
            '-ReportRoot', $case
        )) 'deterministic signature|red signature' 'empty red signature'
    }

    Invoke-Case 'export accepts an empty exception message when type and complete body are deterministic' {
        $case = Join-Path $scratch 'export-empty-message'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.RedTest`n"
        Write-Utf8File (Join-Path $case 'TEST-red.xml') '<testsuite><testcase classname="example.RedTest" name="red"><error type="java.lang.AssertionError">failure body one</error></testcase></testsuite>'
        $result = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes,
            '-OutputPath', $output,
            '-CanonicalWorktree', '/work/tree',
            '-SessionRoot', '/session/root',
            '-RunId', 'run-12345678',
            '-ReportRoot', $case
        )
        Assert-Succeeded $result 'empty red message export'
        $row = @(Import-Csv -Delimiter "`t" -LiteralPath $output)[0]
        Assert-Equal $row.normalized_message '' 'empty message remains deterministic empty data'
        Assert-Equal $row.red_body_sha256 '795173f7d6efb18432c702234cd3754e36885ea42ab46330124ceee197feff80' 'body still owns deterministic hash'
    }

    Invoke-Case 'export requires red normalization inputs and accepts a deterministic empty body' {
        $case = Join-Path $scratch 'export-red-context-empty-body'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.EmptyBodyTest`n"
        Write-Utf8File (Join-Path $case 'TEST-empty-body.xml') '<testsuite><testcase classname="example.EmptyBodyTest" name="empty"><error type="example.Empty" message="empty"></error></testcase></testsuite>'
        $missingContext = Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $output, '-ReportRoot', $case)
        Assert-Failed $missingContext 'CanonicalWorktree|SessionRoot|RunId|normalization' 'missing red normalization context'

        $result = Invoke-Tool $exportScript @(
            '-SourceClassInventory', $classes, '-OutputPath', $output,
            '-CanonicalWorktree', '/work/tree', '-SessionRoot', '/session/root', '-RunId', 'run-empty-12345678',
            '-ReportRoot', $case
        )
        Assert-Succeeded $result 'deterministic empty red body'
        $row = @(Import-Csv -Delimiter "`t" -LiteralPath $output)[0]
        Assert-Equal $row.red_body_bytes '0' 'empty normalized body byte count'
        Assert-Equal $row.red_body_sha256 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855' 'empty normalized body SHA-256'
    }

    Invoke-Case 'export normalizes zoned offset and unzoned ISO timestamps to prevent false red changes' {
        $case = Join-Path $scratch 'export-timestamp-equivalence'
        $parentReports = Join-Path $case 'parent-reports'
        $candidateReports = Join-Path $case 'candidate-reports'
        New-Item -ItemType Directory -Path $parentReports, $candidateReports -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $parentOutput = Join-Path $case 'parent.tsv'
        $candidateOutput = Join-Path $case 'candidate.tsv'
        $compareOutput = Join-Path $case 'comparison.tsv'
        Write-Utf8File $classes "example.TimeTest`n"
        Write-Utf8File (Join-Path $parentReports 'TEST-time.xml') '<testsuite><testcase classname="example.TimeTest" name="time"><failure type="example.Time" message="/parent/tree /parent/session parent-run-12345678 2026-08-26T12:34:56Z 2026-08-26T13:34:56+01:00 2026-08-26T12:34:56">at /parent/tree in /parent/session parent-run-12345678 2026-08-26T12:34:56.123Z 2026-08-26T13:34:56+0100 2026-08-26T12:34:56.123</failure></testcase></testsuite>'
        Write-Utf8File (Join-Path $candidateReports 'TEST-time.xml') '<testsuite><testcase classname="example.TimeTest" name="time"><failure type="example.Time" message="/candidate/tree /candidate/session candidate-run-87654321 2030-01-02T03:04:05Z 2030-01-02T04:04:05+01:00 2030-01-02T03:04:05">at /candidate/tree in /candidate/session candidate-run-87654321 2030-01-02T03:04:05.987Z 2030-01-02T04:04:05+0100 2030-01-02T03:04:05.987</failure></testcase></testsuite>'
        Assert-Succeeded (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $parentOutput, '-CanonicalWorktree', '/parent/tree', '-SessionRoot', '/parent/session', '-RunId', 'parent-run-12345678', '-ReportRoot', $parentReports)) 'parent timestamp export'
        Assert-Succeeded (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-OutputPath', $candidateOutput, '-CanonicalWorktree', '/candidate/tree', '-SessionRoot', '/candidate/session', '-RunId', 'candidate-run-87654321', '-ReportRoot', $candidateReports)) 'candidate timestamp export'
        $parentRow = @(Import-Csv -Delimiter "`t" -LiteralPath $parentOutput)[0]
        $candidateRow = @(Import-Csv -Delimiter "`t" -LiteralPath $candidateOutput)[0]
        Assert-Equal $parentRow.normalized_message '<WORKTREE> <SESSION_ROOT> <RUN_ID> <TIMESTAMP> <TIMESTAMP> <TIMESTAMP>' 'all parent timestamp forms normalize'
        Assert-Equal $candidateRow.normalized_message $parentRow.normalized_message 'timestamp/path/run normalization equivalence'
        Assert-Equal $candidateRow.red_body_bytes $parentRow.red_body_bytes 'normalized red body byte equivalence'
        Assert-Equal $candidateRow.red_body_sha256 $parentRow.red_body_sha256 'normalized red body hash equivalence'
        Assert-Succeeded (Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidateOutput, '-OutputPath', $compareOutput, '-ParentInventoryPath', $parentOutput)) 'normalized false-change comparison'
        Assert-Equal @(Import-Csv -Delimiter "`t" -LiteralPath $compareOutput)[0].classification 'MATCH' 'normalized volatile tokens do not create false signature change'
    }

    Invoke-Case 'comparison emits deterministic union classifications including false ABSENT and approved removal' {
        $case = Join-Path $scratch 'compare-union'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $parent = Join-Path $case 'parent.tsv'
        $candidate = Join-Path $case 'candidate.tsv'
        $removals = Join-Path $case 'removals.tsv'
        $output = Join-Path $case 'comparison.tsv'
        $header = "identity`tclass`tmethod`toutcome`tred_kind`texception_type`tnormalized_message`tred_body_bytes`tred_body_sha256`treport"
        Write-Utf8File $parent (($header, (New-InventoryRow 'a.ParentOnlyTest#gone' 'a.ParentOnlyTest' 'gone' 'PASS'), (New-InventoryRow 'b.SharedTest#stays' 'b.SharedTest' 'stays' 'PASS')) -join "`n")
        Write-Utf8File $candidate (($header, (New-InventoryRow 'b.SharedTest#stays' 'b.SharedTest' 'stays' 'PASS'), (New-InventoryRow 'c.CandidateOnlyTest#new' 'c.CandidateOnlyTest' 'new' 'PASS')) -join "`n")
        Write-Utf8File $removals "identity`treason`na.ParentOnlyTest#gone`tReviewed obsolete parent test removal`n"

        $result = Invoke-Tool $compareScript @(
            '-CandidateInventoryPath', $candidate,
            '-OutputPath', $output,
            '-ReviewedRemovalPath', $removals,
            '-ParentInventoryPath', $parent
        )
        Assert-Succeeded $result 'approved union comparison'
        $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal (($rows.identity) -join '|') 'a.ParentOnlyTest#gone|b.SharedTest#stays|c.CandidateOnlyTest#new' 'comparison ordinal union'
        Assert-Equal (($rows.candidate_outcome) -join '|') 'ABSENT|PASS|PASS' 'candidate absence is explicit without false candidate-only absence'
        Assert-Equal (($rows.classification) -join '|') 'APPROVED_REMOVAL|MATCH|CANDIDATE_ONLY' 'comparison classifications'
        Assert-Equal $rows[0].disposition 'Reviewed obsolete parent test removal' 'approved removal reason is disposition'
    }

    Invoke-Case 'comparison rejects parent pass regressions and unapproved absence after writing the report' {
        $case = Join-Path $scratch 'compare-regressions'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $header = "identity`tclass`tmethod`toutcome`tred_kind`texception_type`tnormalized_message`tred_body_bytes`tred_body_sha256`treport"
        $parent = Join-Path $case 'parent.tsv'
        $candidate = Join-Path $case 'candidate.tsv'
        $output = Join-Path $case 'comparison.tsv'
        Write-Utf8File $parent (($header,
            (New-InventoryRow 'a.Test#absent' 'a.Test' 'absent' 'PASS'),
            (New-InventoryRow 'b.Test#red' 'b.Test' 'red' 'PASS'),
            (New-InventoryRow 'c.Test#skip' 'c.Test' 'skip' 'PASS')) -join "`n")
        Write-Utf8File $candidate (($header,
            (New-InventoryRow 'b.Test#red' 'b.Test' 'red' 'FAILURE' 'failure' 'java.lang.AssertionError' 'bad' '16' '795173f7d6efb18432c702234cd3754e36885ea42ab46330124ceee197feff80'),
            (New-InventoryRow 'c.Test#skip' 'c.Test' 'skip' 'SKIPPED')) -join "`n")

        $result = Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', $parent)
        Assert-Failed $result 'regression|ABSENT|PASS' 'pass regression comparison'
        $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal (($rows.classification) -join '|') 'REGRESSION_PASS_TO_ABSENT|REGRESSION_PASS_TO_FAILURE|REGRESSION_PASS_TO_SKIPPED' 'pass regression classifications'
    }

    Invoke-Case 'comparison rejects skipped baselines that become red' {
        $case = Join-Path $scratch 'compare-skipped-red'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $header = "identity`tclass`tmethod`toutcome`tred_kind`texception_type`tnormalized_message`tred_body_bytes`tred_body_sha256`treport"
        $parent = Join-Path $case 'parent.tsv'
        $candidate = Join-Path $case 'candidate.tsv'
        $output = Join-Path $case 'comparison.tsv'
        Write-Utf8File $parent (($header, (New-InventoryRow 'a.Test#becameRed' 'a.Test' 'becameRed' 'SKIPPED')) -join "`n")
        Write-Utf8File $candidate (($header, (New-InventoryRow 'a.Test#becameRed' 'a.Test' 'becameRed' 'ERROR' 'error' 'java.lang.IllegalStateException' 'bad' '14' 'aa910e3fe47eff8080814e0f3d0caa09200bb413e8c353d4e1d3aef6a36bd7f3')) -join "`n")
        $result = Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', $parent)
        Assert-Failed $result 'REGRESSION_SKIPPED_TO_ERROR|regression' 'skipped-to-red comparison'
        Assert-Equal @(Import-Csv -Delimiter "`t" -LiteralPath $output)[0].classification 'REGRESSION_SKIPPED_TO_ERROR' 'skipped-to-red classification'
    }

    Invoke-Case 'comparison flags changed red kind and every same-kind failure or error signature change' {
        $case = Join-Path $scratch 'compare-red'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $header = "identity`tclass`tmethod`toutcome`tred_kind`texception_type`tnormalized_message`tred_body_bytes`tred_body_sha256`treport"
        $parent = Join-Path $case 'parent.tsv'
        $candidate = Join-Path $case 'candidate.tsv'
        $output = Join-Path $case 'comparison.tsv'
        Write-Utf8File $parent (($header,
            (New-InventoryRow 'a.Test#failureChanged' 'a.Test' 'failureChanged' 'FAILURE' 'failure' 'java.lang.AssertionError' 'one' '16' '795173f7d6efb18432c702234cd3754e36885ea42ab46330124ceee197feff80'),
            (New-InventoryRow 'b.Test#errorChanged' 'b.Test' 'errorChanged' 'ERROR' 'error' 'java.lang.IllegalStateException' 'one' '14' 'aa910e3fe47eff8080814e0f3d0caa09200bb413e8c353d4e1d3aef6a36bd7f3'),
            (New-InventoryRow 'c.Test#kindChanged' 'c.Test' 'kindChanged' 'FAILURE' 'failure' 'java.lang.AssertionError' 'one' '16' '795173f7d6efb18432c702234cd3754e36885ea42ab46330124ceee197feff80')) -join "`n")
        Write-Utf8File $candidate (($header,
            (New-InventoryRow 'a.Test#failureChanged' 'a.Test' 'failureChanged' 'FAILURE' 'failure' 'java.lang.AssertionError' 'two' '16' '3757de10cb8501780d1b2be4e1c2047313c4b3c9b5c7431950724018be73e5a2'),
            (New-InventoryRow 'b.Test#errorChanged' 'b.Test' 'errorChanged' 'ERROR' 'error' 'java.lang.IllegalStateException' 'two' '14' '99349fc0ff6066086b6ee01b1b513c91f89918c4b082a667fdc12b61ed8b313a'),
            (New-InventoryRow 'c.Test#kindChanged' 'c.Test' 'kindChanged' 'ERROR' 'error' 'java.lang.IllegalStateException' 'kind' '10' 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')) -join "`n")

        $result = Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', $parent)
        Assert-Failed $result 'isolated rerun|red|signature|kind' 'red change comparison'
        $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal (($rows.classification) -join '|') 'RED_SIGNATURE_CHANGED_REQUIRES_PAIRED_RERUN|RED_SIGNATURE_CHANGED_REQUIRES_PAIRED_RERUN|RED_KIND_CHANGED' 'red change classifications'
        Assert-True ($rows[0].baseline_red_signature -ne $rows[0].candidate_red_signature) 'failure signatures must be preserved separately'
        Assert-True ($rows[1].baseline_red_signature -ne $rows[1].candidate_red_signature) 'error signatures must be preserved separately'
        Assert-Equal $rows[0].owner '' 'unclassified rerun owner starts empty'
        Assert-Equal $rows[0].disposition '' 'unclassified rerun disposition starts empty'
    }

    Invoke-Case 'comparison accepts identical deterministic red signatures with empty messages' {
        $case = Join-Path $scratch 'compare-empty-message'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $header = "identity`tclass`tmethod`toutcome`tred_kind`texception_type`tnormalized_message`tred_body_bytes`tred_body_sha256`treport"
        $parent = Join-Path $case 'parent.tsv'
        $candidate = Join-Path $case 'candidate.tsv'
        $output = Join-Path $case 'comparison.tsv'
        $row = New-InventoryRow 'a.Test#emptyMessage' 'a.Test' 'emptyMessage' 'ERROR' 'error' 'java.lang.AssertionError' '' '16' '795173f7d6efb18432c702234cd3754e36885ea42ab46330124ceee197feff80'
        Write-Utf8File $parent (($header, $row) -join "`n")
        Write-Utf8File $candidate (($header, $row) -join "`n")
        $result = Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', $parent)
        Assert-Succeeded $result 'empty-message red comparison'
        Assert-Equal @(Import-Csv -Delimiter "`t" -LiteralPath $output)[0].classification 'MATCH' 'empty-message red signatures match'
    }

    Invoke-Case 'comparison validates decoded identity outcome metadata counts hashes and schema' {
        $case = Join-Path $scratch 'compare-validation'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $header = "identity`tclass`tmethod`toutcome`tred_kind`texception_type`tnormalized_message`tred_body_bytes`tred_body_sha256`treport"
        $candidate = Join-Path $case 'candidate.tsv'
        $parent = Join-Path $case 'parent.tsv'
        $output = Join-Path $case 'comparison.tsv'
        $validPass = New-InventoryRow 'a.Test#method' 'a.Test' 'method' 'PASS'
        Write-Utf8File $candidate (($header, $validPass) -join "`n")

        $invalidRows = @(
            [pscustomobject]@{ Name = 'identity mismatch'; Row = (New-InventoryRow 'a.Test#other' 'a.Test' 'method' 'PASS'); Pattern = 'identity.*class.*method|does not equal' },
            [pscustomobject]@{ Name = 'failure red kind'; Row = (New-InventoryRow 'a.Test#method' 'a.Test' 'method' 'FAILURE' 'error' 'example.Type' '' '1' 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'); Pattern = 'red_kind|FAILURE' },
            [pscustomobject]@{ Name = 'error red kind'; Row = (New-InventoryRow 'a.Test#method' 'a.Test' 'method' 'ERROR' 'failure' 'example.Type' '' '1' 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'); Pattern = 'red_kind|ERROR' },
            [pscustomobject]@{ Name = 'pass red metadata'; Row = (New-InventoryRow 'a.Test#method' 'a.Test' 'method' 'PASS' 'failure' '' '' '' ''); Pattern = 'non-red|metadata' },
            [pscustomobject]@{ Name = 'negative body bytes'; Row = (New-InventoryRow 'a.Test#method' 'a.Test' 'method' 'ERROR' 'error' 'example.Type' '' '-1' 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'); Pattern = 'non-negative|byte' },
            [pscustomobject]@{ Name = 'wrong empty hash'; Row = (New-InventoryRow 'a.Test#method' 'a.Test' 'method' 'ERROR' 'error' 'example.Type' '' '0' 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'); Pattern = 'empty-body|empty body|SHA' },
            [pscustomobject]@{ Name = 'bad escape'; Row = (New-InventoryRow 'a.Test#method\x' 'a.Test' 'method\x' 'PASS'); Pattern = 'escape' }
        )
        foreach ($fixture in $invalidRows) {
            Write-Utf8File $parent (($header, $fixture.Row) -join "`n")
            Assert-Failed (Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', $parent)) $fixture.Pattern $fixture.Name
        }

        $extraHeader = "$header`textra"
        Write-Utf8File $parent (($extraHeader, "$validPass`textra") -join "`n")
        Assert-Failed (Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', $parent)) 'schema|column' 'extra schema column'

        $emptyRed = New-InventoryRow 'a.Test#empty' 'a.Test' 'empty' 'ERROR' 'error' 'example.Type' '' '0' 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
        Write-Utf8File $parent (($header, $emptyRed) -join "`n")
        Write-Utf8File $candidate (($header, $emptyRed) -join "`n")
        Assert-Succeeded (Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', $parent)) 'valid zero-byte red signature'
    }

    Invoke-Case 'comparison evaluates both parents independently and rejects duplicate inventories or reasonless removals' {
        $case = Join-Path $scratch 'compare-parents-validation'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $header = "identity`tclass`tmethod`toutcome`tred_kind`texception_type`tnormalized_message`tred_body_bytes`tred_body_sha256`treport"
        $parentA = Join-Path $case 'parent-a.tsv'
        $parentB = Join-Path $case 'parent-b.tsv'
        $candidate = Join-Path $case 'candidate.tsv'
        $output = Join-Path $case 'comparison.tsv'
        Write-Utf8File $parentA (($header, (New-InventoryRow 'a.Test#one' 'a.Test' 'one' 'PASS')) -join "`n")
        Write-Utf8File $parentB (($header, (New-InventoryRow 'b.Test#two' 'b.Test' 'two' 'PASS')) -join "`n")
        Write-Utf8File $candidate (($header, (New-InventoryRow 'a.Test#one' 'a.Test' 'one' 'PASS'), (New-InventoryRow 'b.Test#two' 'b.Test' 'two' 'PASS')) -join "`n")
        Assert-Succeeded (Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', ($parentA + [System.IO.Path]::PathSeparator + $parentB))) 'two-parent comparison'
        Assert-Equal ((Import-Csv -Delimiter "`t" -LiteralPath $output).Count) 2 'both parent identities compared'

        Write-Utf8File $parentA (($header, (New-InventoryRow 'a.Test#one' 'a.Test' 'one' 'PASS'), (New-InventoryRow 'a.Test#one' 'a.Test' 'one' 'PASS')) -join "`n")
        Assert-Failed (Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', $parentA)) 'duplicate' 'duplicate normalized inventory'

        $removals = Join-Path $case 'removals.tsv'
        Write-Utf8File $removals "identity`treason`na.Test#one`t`n"
        Assert-Failed (Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ReviewedRemovalPath', $removals, '-ParentInventoryPath', $parentB)) 'reason' 'reasonless reviewed removal'
    }

    Invoke-Case 'comparison emits one exact source-bound row per parent identity and one candidate-only row' {
        $case = Join-Path $scratch 'compare-source-attribution'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $header = "identity`tclass`tmethod`toutcome`tred_kind`texception_type`tnormalized_message`tred_body_bytes`tred_body_sha256`treport"
        $parentA = Join-Path $case 'frozen-next.tsv'
        $parentB = Join-Path $case 'frozen-develop.tsv'
        $candidate = Join-Path $case 'candidate.tsv'
        $output = Join-Path $case 'comparison.tsv'
        Write-Utf8File $parentA (($header, (New-InventoryRow 'a.SharedTest#same' 'a.SharedTest' 'same' 'PASS')) -join "`n")
        Write-Utf8File $parentB (($header, (New-InventoryRow 'a.SharedTest#same' 'a.SharedTest' 'same' 'FAILURE' 'failure' 'example.Failure' 'old' '1' 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')) -join "`n")
        Write-Utf8File $candidate (($header,
            (New-InventoryRow 'a.SharedTest#same' 'a.SharedTest' 'same' 'FAILURE' 'failure' 'example.Failure' 'old' '1' 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
            (New-InventoryRow 'z.NewTest#new' 'z.NewTest' 'new' 'PASS')) -join "`n")
        $result = Invoke-Tool $compareScript @('-CandidateInventoryPath', $candidate, '-OutputPath', $output, '-ParentInventoryPath', ($parentA + [System.IO.Path]::PathSeparator + $parentB))
        Assert-Failed $result 'frozen-next|REGRESSION_PASS_TO_FAILURE' 'source-bound parent regression'
        $rows = @(Import-Csv -Delimiter "`t" -LiteralPath $output)
        Assert-Equal $rows.Count 3 'two parent rows plus one candidate-only row'
        Assert-Equal (($rows.identity) -join '|') 'a.SharedTest#same|a.SharedTest#same|z.NewTest#new' 'identity-primary deterministic ordering'
        Assert-Equal (($rows.baseline_source) -join '|') 'frozen-develop|frozen-next|CANDIDATE_ONLY' 'source-secondary deterministic ordering and explicit candidate row'
        Assert-Equal (($rows.baseline_outcome) -join '|') 'FAILURE|PASS|ABSENT' 'source-bound baseline outcomes'
        Assert-Equal (($rows.classification) -join '|') 'MATCH|REGRESSION_PASS_TO_FAILURE|CANDIDATE_ONLY' 'source-bound classifications'
        Assert-True ($rows[0].baseline_red_signature.Length -gt 0) 'develop row keeps its exact red signature'
        Assert-Equal $rows[1].baseline_red_signature '' 'next pass row carries no borrowed red signature'
    }

    Invoke-Case 'partition map is an ordinal union with stable bounded slots and per-tree filters' {
        $case = Join-Path $scratch 'partitions'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $next = Join-Path $case 'next.txt'
        $develop = Join-Path $case 'develop.txt'
        $candidate = Join-Path $case 'candidate.txt'
        $output = Join-Path $case 'partitions.tsv'
        Write-Utf8File $next "a.AlphaTest`nc.CommonTest`ne.NextOnlyTest`n"
        Write-Utf8File $develop "b.DevelopOnlyTest`nc.CommonTest`nf.ZuluTest`n"
        Write-Utf8File $candidate "a.AlphaTest`nb.DevelopOnlyTest`nd.CandidateOnlyTest`nf.ZuluTest`n"

        $result = Invoke-Tool $partitionScript @(
            '-NextClassInventory', $next,
            '-DevelopClassInventory', $develop,
            '-CandidateClassInventory', $candidate,
            '-OutputPath', $output,
            '-SlotSize', '2'
        )
        Assert-Succeeded $result 'partition map'
        $raw = [System.IO.File]::ReadAllText($output, [System.Text.Encoding]::UTF8).TrimEnd("`r", "`n")
        $expected = @"
slot	union_selector	next_selector	develop_selector	candidate_selector
001	a.AlphaTest,b.DevelopOnlyTest	a.AlphaTest	b.DevelopOnlyTest	a.AlphaTest,b.DevelopOnlyTest
002	c.CommonTest,d.CandidateOnlyTest	c.CommonTest	c.CommonTest	d.CandidateOnlyTest
003	e.NextOnlyTest,f.ZuluTest	e.NextOnlyTest	f.ZuluTest	f.ZuluTest
"@.TrimEnd("`r", "`n")
        Assert-Equal $raw $expected 'exact deterministic partition TSV'
    }

    Invoke-Case 'partition map rejects duplicate classes and an empty union' {
        $case = Join-Path $scratch 'partition-rejections'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $next = Join-Path $case 'next.txt'
        $develop = Join-Path $case 'develop.txt'
        $candidate = Join-Path $case 'candidate.txt'
        $output = Join-Path $case 'partitions.tsv'
        Write-Utf8File $next "a.Test`na.Test`n"
        Write-Utf8File $develop "b.Test`n"
        Write-Utf8File $candidate "c.Test`n"
        Assert-Failed (Invoke-Tool $partitionScript @('-NextClassInventory', $next, '-DevelopClassInventory', $develop, '-CandidateClassInventory', $candidate, '-OutputPath', $output)) 'duplicate.*a\.Test' 'duplicate class'

        Write-Utf8File $next ''
        Write-Utf8File $develop ''
        Write-Utf8File $candidate ''
        Assert-Failed (Invoke-Tool $partitionScript @('-NextClassInventory', $next, '-DevelopClassInventory', $develop, '-CandidateClassInventory', $candidate, '-OutputPath', $output)) 'empty union|no classes' 'empty union'
    }
}
finally {
    if (Test-Path -LiteralPath $scratch) {
        Remove-Item -LiteralPath $scratch -Recurse -Force
    }
}

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        Write-Host "ERROR $failure"
    }
    throw "$($failures.Count) of $testCount Surefire inventory fixture cases failed"
}

Write-Host "All $testCount Surefire inventory fixture cases passed."
