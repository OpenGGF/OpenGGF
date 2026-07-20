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
        [Parameter(Mandatory)] [bool] $ExpectSuccess
    )

    $actionResult = & $Action 2>$null
    $succeeded = if ($null -eq $actionResult) { $LASTEXITCODE -eq 0 } else { [bool] $actionResult }
    if ($succeeded -ne $ExpectSuccess) {
        throw "Case '$Name' expected success=$ExpectSuccess but exit code was $LASTEXITCODE."
    }
    Write-Output "PASS $Name"
}

try {
    New-Item -ItemType Directory -Path $scratch | Out-Null

    $expected = Join-Path $scratch 'expected.txt'
    Set-Content -LiteralPath $expected -Value 'example.RedTest#fails'

    $reports = Join-Path $scratch 'reports'
    New-Item -ItemType Directory -Path $reports | Out-Null
    $report = Join-Path $reports 'TEST-example.xml'

    Write-SurefireReport -Path $report -TestCases @([pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'fails'; Kind = 'failure' })
    Invoke-Case -Name 'exact-match' -ExpectSuccess $true -Action { & $powerShell -NoProfile -File $comparator -ReportsPath $reports -ExpectedPath $expected }

    Write-SurefireReport -Path $report -TestCases @(
        [pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'fails'; Kind = 'failure' },
        [pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'fails'; Kind = 'error' }
    )
    Invoke-Case -Name 'duplicate-rejected' -ExpectSuccess $false -Action { & $powerShell -NoProfile -File $comparator -ReportsPath $reports }

    Write-SurefireReport -Path $report -TestCases @()
    Invoke-Case -Name 'missing-rejected' -ExpectSuccess $false -Action { & $powerShell -NoProfile -File $comparator -ReportsPath $reports -ExpectedPath $expected }

    Write-SurefireReport -Path $report -TestCases @(
        [pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'fails'; Kind = 'failure' },
        [pscustomobject]@{ ClassName = 'example.RedTest'; Name = 'extra'; Kind = 'error' }
    )
    Invoke-Case -Name 'extra-rejected' -ExpectSuccess $false -Action { & $powerShell -NoProfile -File $comparator -ReportsPath $reports -ExpectedPath $expected }

    Write-SurefireReport -Path $report -Malformed
    Invoke-Case -Name 'malformed-rejected' -ExpectSuccess $false -Action { & $powerShell -NoProfile -File $comparator -ReportsPath $reports }

    Write-SurefireReport -Path $report -TestCases @([pscustomobject]@{ ClassName = 'example.SkippedTest'; Name = 'disabled'; Kind = 'skipped' })
    Invoke-Case -Name 'skipped-rejected' -ExpectSuccess $false -Action { & $powerShell -NoProfile -File $comparator -ReportsPath $reports }

    Set-Content -LiteralPath $expected -Value 'example.CaseTest#lower'
    Write-SurefireReport -Path $report -TestCases @([pscustomobject]@{ ClassName = 'example.CaseTest'; Name = 'LOWER'; Kind = 'failure' })
    Invoke-Case -Name 'case-distinct-rejected' -ExpectSuccess $false -Action {
        & $powerShell -NoProfile -File $comparator -ReportsPath $reports -ExpectedPath $expected
        return $LASTEXITCODE -eq 0
    }

    $actualOutput = Join-Path $scratch 'actual.txt'
    Write-SurefireReport -Path $report -TestCases @(
        [pscustomobject]@{ ClassName = 'example.OrderTest'; Name = 'a'; Kind = 'failure' },
        [pscustomobject]@{ ClassName = 'example.OrderTest'; Name = 'A'; Kind = 'error' }
    )
    Invoke-Case -Name 'case-distinct-ordinal-order' -ExpectSuccess $true -Action {
        & $powerShell -NoProfile -File $comparator -ReportsPath $reports -WriteActualPath $actualOutput
        if ($LASTEXITCODE -ne 0) { return $false }
        $actualText = [string]::Join("`n", @(Get-Content -LiteralPath $actualOutput))
        return $actualText -ceq "example.OrderTest#A`nexample.OrderTest#a"
    }
}
finally {
    if (Test-Path -LiteralPath $scratch) {
        Remove-Item -LiteralPath $scratch -Recurse -Force
    }
}

exit 0
