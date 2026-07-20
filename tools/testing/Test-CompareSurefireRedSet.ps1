[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$comparator = Join-Path $PSScriptRoot 'Compare-SurefireRedSet.ps1'
$powerShell = (Get-Process -Id $PID).Path
$scratch = Join-Path ([System.IO.Path]::GetTempPath()) ("compare-surefire-red-set-" + [Guid]::NewGuid().ToString('N'))

function Write-SurefireReport {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [AllowEmptyCollection()] [object[]] $TestCases = @(),
        [switch] $Malformed
    )

    if ($Malformed) {
        Set-Content -LiteralPath $Path -Value '<testsuite><testcase>' -NoNewline
        return
    }

    $document = New-Object System.Xml.XmlDocument
    $suite = $document.CreateElement('testsuite')
    [void] $document.AppendChild($suite)
    foreach ($testCase in $TestCases) {
        $node = $document.CreateElement('testcase')
        [void] $node.SetAttribute('classname', $testCase.ClassName)
        [void] $node.SetAttribute('name', $testCase.Name)
        if ($testCase.Kind) {
            [void] $node.AppendChild($document.CreateElement($testCase.Kind))
        }
        [void] $suite.AppendChild($node)
    }
    $document.Save($Path)
}

function Invoke-Case {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [scriptblock] $Action,
        [Parameter(Mandatory)] [bool] $ExpectSuccess,
        [string] $ExpectedOutput
    )

    $actionResult = & $Action
    $succeeded = $actionResult.ExitCode -eq 0
    if ($succeeded -ne $ExpectSuccess) {
        throw "Case '$Name' expected success=$ExpectSuccess but exit code was $($actionResult.ExitCode): $($actionResult.Output)"
    }
    if ($ExpectedOutput -and $actionResult.Output -notmatch $ExpectedOutput) {
        throw "Case '$Name' did not print the expected output '$ExpectedOutput'."
    }
    Write-Output "PASS $Name"
}

function Invoke-Comparator {
    param([Parameter(Mandatory)] [string[]] $Arguments)

    $output = @(& $powerShell -NoProfile -File $comparator @Arguments 2>&1)
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = [string]::Join("`n", @($output | ForEach-Object ToString))
    }
}

try {
    New-Item -ItemType Directory -Path $scratch | Out-Null

    $expected = Join-Path $scratch 'expected.txt'
    Set-Content -LiteralPath $expected -Value 'example.RedTest#fails'

    $reports = Join-Path $scratch 'reports'
    New-Item -ItemType Directory -Path $reports | Out-Null
    $report = Join-Path $reports 'TEST-example.xml'

    Write-SurefireReport -Path $report -TestCases @([pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'fails'; Kind = 'failure' })
    Invoke-Case -Name 'exact-match' -ExpectSuccess $true -ExpectedOutput 'failures=1 errors=0 skipped_disabled=0' -Action { Invoke-Comparator @('-ReportsPath', $reports, '-ExpectedPath', $expected) }

    Write-SurefireReport -Path $report -TestCases @(
        [pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'fails'; Kind = 'failure' },
        [pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'fails'; Kind = 'error' }
    )
    Invoke-Case -Name 'duplicate-rejected' -ExpectSuccess $false -Action { Invoke-Comparator @('-ReportsPath', $reports) }

    Write-SurefireReport -Path $report -TestCases @()
    Invoke-Case -Name 'missing-rejected' -ExpectSuccess $false -ExpectedOutput 'missing expected' -Action { Invoke-Comparator @('-ReportsPath', $reports, '-ExpectedPath', $expected) }

    Write-SurefireReport -Path $report -TestCases @(
        [pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'fails'; Kind = 'failure' },
        [pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'extra'; Kind = 'error' }
    )
    Invoke-Case -Name 'extra-rejected' -ExpectSuccess $false -Action { Invoke-Comparator @('-ReportsPath', $reports, '-ExpectedPath', $expected) }

    Write-SurefireReport -Path $report -Malformed
    Invoke-Case -Name 'malformed-rejected' -ExpectSuccess $false -Action { Invoke-Comparator @('-ReportsPath', $reports) }

    Write-SurefireReport -Path $report -TestCases @([pscustomobject]@{ ClassName = 'example.SkippedTest'; Name = 'disabled'; Kind = 'skipped' })
    Invoke-Case -Name 'skipped-rejected' -ExpectSuccess $false -ExpectedOutput 'failures=0 errors=0 skipped_disabled=1' -Action { Invoke-Comparator @('-ReportsPath', $reports) }

    Set-Content -LiteralPath $expected -Value 'example.CaseTest#lower'
    Write-SurefireReport -Path $report -TestCases @([pscustomobject]@{ ClassName = 'example.CaseTest'; Name = 'LOWER'; Kind = 'failure' })
    Invoke-Case -Name 'case-distinct-rejected' -ExpectSuccess $false -Action { Invoke-Comparator @('-ReportsPath', $reports, '-ExpectedPath', $expected) }

    $actualOutput = Join-Path $scratch 'actual.txt'
    Write-SurefireReport -Path $report -TestCases @(
        [pscustomobject]@{ ClassName = 'example.OrderTest'; Name = 'a'; Kind = 'failure' },
        [pscustomobject]@{ ClassName = 'example.OrderTest'; Name = 'A'; Kind = 'error' }
    )
    Invoke-Case -Name 'case-distinct-ordinal-order' -ExpectSuccess $true -Action {
        $result = Invoke-Comparator @('-ReportsPath', $reports, '-WriteActualPath', $actualOutput)
        if ($result.ExitCode -ne 0) { return $result }
        if (([string]::Join("`n", @(Get-Content -LiteralPath $actualOutput))) -cne "example.OrderTest#A`nexample.OrderTest#a") { $result.ExitCode = 1 }
        return $result
    }

    Set-Content -LiteralPath $expected -Value @('# exported inventory comment', '', 'example.CommentedTest#red')
    Write-SurefireReport -Path $report -TestCases @([pscustomobject]@{ ClassName = 'example.CommentedTest'; Name = 'red'; Kind = 'failure' })
    Invoke-Case -Name 'commented-allowlist' -ExpectSuccess $true -Action { Invoke-Comparator @('-ReportsPath', $reports, '-ExpectedPath', $expected) }

    Set-Content -LiteralPath $expected -Value @('# empty red-set', '')
    Write-SurefireReport -Path $report -TestCases @()
    Invoke-Case -Name 'empty-actual-empty-expected' -ExpectSuccess $true -ExpectedOutput 'failures=0 errors=0 skipped_disabled=0' -Action { Invoke-Comparator @('-ReportsPath', $reports, '-ExpectedPath', $expected) }

    Set-Content -LiteralPath $expected -Value 'example.RedTest#fails'
    Invoke-Case -Name 'empty-actual-missing-expected' -ExpectSuccess $false -ExpectedOutput 'missing expected' -Action { Invoke-Comparator @('-ReportsPath', $reports, '-ExpectedPath', $expected) }
}
finally {
    if (Test-Path -LiteralPath $scratch) {
        Remove-Item -LiteralPath $scratch -Recurse -Force
    }
}

exit 0
