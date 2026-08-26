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

    Invoke-Case 'export rejects red elements without deterministic kind type message and body signature' {
        $case = Join-Path $scratch 'export-red-signature'
        New-Item -ItemType Directory -Path $case -Force | Out-Null
        $classes = Join-Path $case 'classes.txt'
        $output = Join-Path $case 'outcomes.tsv'
        Write-Utf8File $classes "example.RedTest`n"
        Write-Utf8File (Join-Path $case 'TEST-red.xml') '<testsuite><testcase classname="example.RedTest" name="red"><failure type="" message=""></failure></testcase></testsuite>'
        Assert-Failed (Invoke-Tool $exportScript @('-SourceClassInventory', $classes, '-ReportRoot', $case, '-OutputPath', $output)) 'deterministic signature|red signature' 'empty red signature'
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
