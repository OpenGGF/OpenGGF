[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Mode,

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RemainingArgs
)

$ErrorActionPreference = "Stop"
$script:GithubFileSizeLimitBytes = 100000000
$script:TraceCompressionThresholdBytes = 1048576
$script:ReleaseTrailerCutoverBase = "677447024a08db9e25f3461588d661c23ba26848"

function Fail([string]$Message) {
    [Console]::Error.WriteLine("policy: $Message")
    exit 1
}

function Note([string]$Message) {
    [Console]::Error.WriteLine("policy: $Message")
}

function Invoke-GitText([string[]]$Arguments, [switch]$AllowFailure) {
    $output = & git @Arguments 2>$null
    if (-not $AllowFailure -and $LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
    if ($LASTEXITCODE -ne 0) {
        return ""
    }
    if ($null -eq $output) {
        return ""
    }
    return ($output -join "`n").TrimEnd("`r", "`n")
}

function Invoke-GitLines([string[]]$Arguments, [switch]$AllowFailure) {
    $text = Invoke-GitText $Arguments -AllowFailure:$AllowFailure
    if ([string]::IsNullOrEmpty($text)) {
        return @()
    }
    return ($text -split "`r?`n") | Where-Object { $_ -ne "" }
}

function Test-GitSuccess([string[]]$Arguments) {
    & git @Arguments *> $null
    return ($LASTEXITCODE -eq 0)
}

function Get-CurrentBranch() {
    $branch = Invoke-GitText @("symbolic-ref", "--quiet", "--short", "HEAD") -AllowFailure
    if ([string]::IsNullOrWhiteSpace($branch)) {
        return "HEAD"
    }
    return $branch
}

function Test-MergeInProgress() {
    & git rev-parse -q --verify MERGE_HEAD *> $null
    return ($LASTEXITCODE -eq 0)
}

function Get-MergeHeadOid() {
    return Invoke-GitText @("rev-parse", "-q", "--verify", "MERGE_HEAD") -AllowFailure
}

function Get-MasterTipOid() {
    return Invoke-GitText @("rev-parse", "-q", "--verify", "refs/heads/master") -AllowFailure
}

function Test-MergeFromMaster() {
    $mergeOid = Get-MergeHeadOid
    $masterOid = Get-MasterTipOid
    return (-not [string]::IsNullOrWhiteSpace($mergeOid) -and -not [string]::IsNullOrWhiteSpace($masterOid) -and $mergeOid -eq $masterOid)
}

function Get-StagedFiles() {
    return Invoke-GitLines @("diff", "--cached", "--name-only", "--diff-filter=ACMR")
}

function Get-CommitFiles([string]$Commit) {
    return Invoke-GitLines @("diff-tree", "--root", "--no-commit-id", "--name-only", "--diff-filter=ACMR", "-r", $Commit)
}

function Get-StagedBlobSize([string]$Path) {
    $size = Invoke-GitText @("cat-file", "-s", ":$Path") -AllowFailure
    if ([string]::IsNullOrWhiteSpace($size)) {
        return $null
    }
    return [long]$size
}

function Get-CommitBlobSize([string]$Commit, [string]$Path) {
    $spec = "${Commit}:$Path"
    $size = Invoke-GitText @("cat-file", "-s", $spec) -AllowFailure
    if ([string]::IsNullOrWhiteSpace($size)) {
        return $null
    }
    return [long]$size
}

function Test-HasExact([string[]]$Files, [string]$Needle) {
    return $Files -contains $Needle
}

function Test-HasPrefix([string[]]$Files, [string]$Prefix) {
    foreach ($path in $Files) {
        if ($path.StartsWith($Prefix, [System.StringComparison]::Ordinal)) {
            return $true
        }
    }
    return $false
}

function Get-TrailerValue([string]$Key, [string]$Message) {
    $value = ""
    foreach ($line in ($Message -split "`r?`n")) {
        $text = [string]$line
        if ($text.StartsWith("${Key}:", [System.StringComparison]::Ordinal)) {
            $value = $text.Substring($Key.Length + 2).Trim()
        }
    }
    return $value
}

function Get-DecisionKind([string]$Value) {
    $normalized = $Value.ToLowerInvariant()
    if ($normalized -eq "updated" -or $normalized.StartsWith("updated ") -or $normalized.StartsWith("updated:") -or $normalized.StartsWith("updated-")) {
        return "updated"
    }
    if ($normalized -eq "n/a" -or $normalized.StartsWith("n/a ") -or $normalized.StartsWith("n/a:") -or $normalized.StartsWith("n/a-")) {
        return "na"
    }
    return "invalid"
}

function Print-CommitTemplate() {
    # Single-quote here-string so the backtick before `updated` on the final
    # line is treated as a literal character, not a PowerShell escape (the
    # double-quote here-string parser would otherwise see `u and try to
    # consume a Unicode escape, causing a parse error on PS 5.1+).
    [Console]::Error.WriteLine(@'
Use these trailers on non-master branch commits:

Changelog: updated|n/a
Guide: updated|n/a
Known-Discrepancies: updated|n/a
S3K-Known-Discrepancies: updated|n/a
Agent-Docs: updated|n/a
Configuration-Docs: updated|n/a
Skills: updated|n/a

If a trailer says `updated`, the matching files must be staged in the same commit.
'@)
}

$script:Errors = New-Object System.Collections.Generic.List[string]

function Reset-ValidationErrors() {
    $script:Errors = New-Object System.Collections.Generic.List[string]
}

function Add-ValidationError([string]$Message) {
    $script:Errors.Add("- $Message") | Out-Null
}

function Validate-FileSizePolicyForFiles([string[]]$Files, [scriptblock]$SizeResolver) {
    foreach ($path in $Files) {
        $size = & $SizeResolver $path
        if ($null -eq $size) {
            continue
        }
        $fileName = [System.IO.Path]::GetFileName($path)
        if ((($fileName -like "aux_state*.jsonl") -or ($fileName -like "physics*.csv")) -and
                $size -ge $script:TraceCompressionThresholdBytes) {
            $message = ("``$path`` is an uncompressed trace payload ({0} bytes). " +
                    "Run ``tools/traces/compress-traces.ps1`` and commit the ``.gz`` file instead.") -f $size
            Add-ValidationError $message
        }
        if ($size -ge $script:GithubFileSizeLimitBytes) {
            $message = ("``$path`` is {0} bytes; GitHub rejects files >= {1} bytes.") -f `
                    $size, $script:GithubFileSizeLimitBytes
            Add-ValidationError $message
        }
    }
}

function Validate-ExactTrailer([string]$Message, [string[]]$Files, [string]$Key, [string]$Path, [string]$Label) {
    $value = Get-TrailerValue $Key $Message
    if ([string]::IsNullOrWhiteSpace($value)) {
        Add-ValidationError "Missing ``$Key`` trailer."
        return
    }

    $kind = Get-DecisionKind $value
    $changed = Test-HasExact $Files $Path

    switch ($kind) {
        "updated" {
            if (-not $changed) {
                Add-ValidationError "``$Key`` says updated, but ``$Label`` is not staged."
            }
        }
        "na" {
            if ($changed) {
                Add-ValidationError "``$Key`` says n/a, but ``$Label`` is staged."
            }
        }
        default {
            Add-ValidationError "``$Key`` must start with ``updated`` or ``n/a``."
        }
    }
}

function Validate-PrefixTrailer([string]$Message, [string[]]$Files, [string]$Key, [string]$Prefix, [string]$Label) {
    $value = Get-TrailerValue $Key $Message
    if ([string]::IsNullOrWhiteSpace($value)) {
        Add-ValidationError "Missing ``$Key`` trailer."
        return
    }

    $kind = Get-DecisionKind $value
    $changed = Test-HasPrefix $Files $Prefix

    switch ($kind) {
        "updated" {
            if (-not $changed) {
                Add-ValidationError "``$Key`` says updated, but ``$Label`` has no staged changes."
            }
        }
        "na" {
            if ($changed) {
                Add-ValidationError "``$Key`` says n/a, but ``$Label`` has staged changes."
            }
        }
        default {
            Add-ValidationError "``$Key`` must start with ``updated`` or ``n/a``."
        }
    }
}

function Validate-AgentDocsTrailer([string]$Message, [string[]]$Files) {
    $key = "Agent-Docs"
    $value = Get-TrailerValue $key $Message
    if ([string]::IsNullOrWhiteSpace($value)) {
        Add-ValidationError "Missing ``$key`` trailer."
        return
    }

    $kind = Get-DecisionKind $value
    $agentsChanged = Test-HasExact $Files "AGENTS.md"
    $claudeChanged = Test-HasExact $Files "CLAUDE.md"

    switch ($kind) {
        "updated" {
            if (-not ($agentsChanged -and $claudeChanged)) {
                Add-ValidationError "``Agent-Docs`` says updated, but both ``AGENTS.md`` and ``CLAUDE.md`` must be staged together."
            }
        }
        "na" {
            if ($agentsChanged -or $claudeChanged) {
                Add-ValidationError "``Agent-Docs`` says n/a, but agent docs are staged."
            }
        }
        default {
            Add-ValidationError "``Agent-Docs`` must start with ``updated`` or ``n/a``."
        }
    }
}

function Validate-SkillsTrailer([string]$Message, [string[]]$Files) {
    $key = "Skills"
    $value = Get-TrailerValue $key $Message
    if ([string]::IsNullOrWhiteSpace($value)) {
        Add-ValidationError "Missing ``$key`` trailer."
        return
    }

    $kind = Get-DecisionKind $value
    $agentsChanged = Test-HasPrefix $Files ".agents/skills/"
    $claudeChanged = Test-HasPrefix $Files ".claude/skills/"

    switch ($kind) {
        "updated" {
            if (-not ($agentsChanged -and $claudeChanged)) {
                Add-ValidationError "``Skills`` says updated, but both ``.agents/skills/`` and ``.claude/skills/`` must have staged changes."
            }
        }
        "na" {
            if ($agentsChanged -or $claudeChanged) {
                Add-ValidationError "``Skills`` says n/a, but skill changes are staged."
            }
        }
        default {
            Add-ValidationError "``Skills`` must start with ``updated`` or ``n/a``."
        }
    }
}

# A feat/fix/perf commit that touches engine source (src/main/) is almost always
# changelog-worthy. The base trailer gate only checks staged<->trailer consistency,
# so it cannot catch a wrong `Changelog: n/a`. This requires such commits to either
# set `Changelog: updated` or justify the skip with a reason, e.g. `Changelog: n/a: test-only helper`.
function Test-ChangelogJustified([string]$Value) {
    $rest = $Value
    $rest = [System.Text.RegularExpressions.Regex]::Replace($rest, '^\s*n/a', '', 'IgnoreCase')
    $rest = [System.Text.RegularExpressions.Regex]::Replace($rest, '^[\s:,_-]+', '')
    return -not [string]::IsNullOrWhiteSpace($rest.Trim())
}

function Validate-ChangelogJustification([string]$Message, [string[]]$Files) {
    $subject = ($Message -split "`r?`n")[0]
    if ($subject -notmatch '^(feat|fix|perf)(\(.+\))?!?:') {
        return
    }

    if (-not (Test-HasPrefix $Files "src/main/")) {
        return
    }

    $value = Get-TrailerValue "Changelog" $Message
    if ([string]::IsNullOrWhiteSpace($value)) {
        return
    }

    if ((Get-DecisionKind $value) -ne "na") {
        return
    }

    if (-not (Test-ChangelogJustified $value)) {
        $type = ($subject -split '[:(!]')[0]
        Add-ValidationError "``Changelog`` is ``n/a`` on a ``$type`` commit touching ``src/main/``. Set ``Changelog: updated`` (and stage CHANGELOG.md) or justify the skip, e.g. ``Changelog: n/a: <reason>``."
    }
}

function Validate-NonMasterCommitMessage([string]$Message, [string[]]$Files) {
    Reset-ValidationErrors

    Validate-FileSizePolicyForFiles $Files { param($path) Get-StagedBlobSize $path }
    Validate-ExactTrailer $Message $Files "Changelog" "CHANGELOG.md" "CHANGELOG.md"
    Validate-ChangelogJustification $Message $Files
    Validate-PrefixTrailer $Message $Files "Guide" "docs/guide/" "docs/guide/"
    Validate-ExactTrailer $Message $Files "Known-Discrepancies" "docs/KNOWN_DISCREPANCIES.md" "docs/KNOWN_DISCREPANCIES.md"
    Validate-ExactTrailer $Message $Files "S3K-Known-Discrepancies" "docs/S3K_KNOWN_DISCREPANCIES.md" "docs/S3K_KNOWN_DISCREPANCIES.md"
    Validate-AgentDocsTrailer $Message $Files
    Validate-ExactTrailer $Message $Files "Configuration-Docs" "CONFIGURATION.md" "CONFIGURATION.md"
    Validate-SkillsTrailer $Message $Files

    if ($script:Errors.Count -gt 0) {
        Note "non-master branch commits must declare the documentation/discrepancy policy explicitly."
        foreach ($entry in $script:Errors) {
            [Console]::Error.WriteLine($entry)
        }
        Print-CommitTemplate
        exit 1
    }
}

function Validate-MergeIntoDevelop() {
    if ((Get-CurrentBranch) -ne "develop") {
        return
    }

    if (-not (Test-MergeInProgress)) {
        return
    }

    if (Test-MergeFromMaster) {
        return
    }

    if (-not (Test-HasExact (Get-StagedFiles) "README.md")) {
        Fail "merging a non-master branch into develop requires a staged README.md update summarizing the branch change."
    }
}

function Prepare-CommitMessage([string]$MessageFile, [string]$Source) {
    if ((Get-CurrentBranch) -eq "master") {
        return
    }

    if ($Source -in @("merge", "squash")) {
        return
    }

    if (Test-MergeInProgress) {
        return
    }

    $message = Get-Content -LiteralPath $MessageFile -Raw

    $append = New-Object System.Collections.Generic.List[string]
    foreach ($key in @(
        "Changelog",
        "Guide",
        "Known-Discrepancies",
        "S3K-Known-Discrepancies",
        "Agent-Docs",
        "Configuration-Docs",
        "Skills"
    )) {
        if ([string]::IsNullOrWhiteSpace((Get-TrailerValue $key $message))) {
            $append.Add("${key}: TODO") | Out-Null
        }
    }

    if ($append.Count -eq 0) {
        return
    }

    Add-Content -LiteralPath $MessageFile -Value ("`n" + ($append -join "`n"))
}

function Validate-CommitMsgHook([string]$MessageFile) {
    if ((Get-CurrentBranch) -eq "master") {
        return
    }

    if (Test-MergeInProgress) {
        Validate-MergeIntoDevelop
        return
    }

    $message = Get-Content -LiteralPath $MessageFile -Raw
    Validate-NonMasterCommitMessage $message (Get-StagedFiles)
}

function Validate-CiPr([string]$BaseSha, [string]$HeadSha, [string]$BaseRef, [string]$HeadRef) {
    if ($BaseRef -ne "develop" -and $BaseRef -ne "master") {
        return
    }

    $effectiveBaseSha = Get-EffectiveBaseForCiPr $BaseSha $HeadSha $BaseRef $HeadRef
    $rangeFiles = Invoke-GitLines @("diff", "--name-only", "--diff-filter=ACMR", "$effectiveBaseSha...$HeadSha")

    if ($BaseRef -eq "develop") {
        if ($HeadRef -ne "master" -and -not (Test-HasExact $rangeFiles "README.md")) {
            Fail "PRs from non-master branches into develop must update README.md with a brief branch summary."
        }

        if ($HeadRef -eq "master") {
            return
        }
    }

    $commits = Invoke-GitLines @("rev-list", "--reverse", "--no-merges", "$effectiveBaseSha..$HeadSha")
    foreach ($commit in $commits) {
        $message = Invoke-GitText @("show", "-s", "--format=%B", $commit)
        $files = Get-CommitFiles $commit
        Reset-ValidationErrors
        Validate-FileSizePolicyForFiles $files { param($path) Get-CommitBlobSize $commit $path }
        Validate-ExactTrailer $message $files "Changelog" "CHANGELOG.md" "CHANGELOG.md"
        Validate-ChangelogJustification $message $files
        Validate-PrefixTrailer $message $files "Guide" "docs/guide/" "docs/guide/"
        Validate-ExactTrailer $message $files "Known-Discrepancies" "docs/KNOWN_DISCREPANCIES.md" "docs/KNOWN_DISCREPANCIES.md"
        Validate-ExactTrailer $message $files "S3K-Known-Discrepancies" "docs/S3K_KNOWN_DISCREPANCIES.md" "docs/S3K_KNOWN_DISCREPANCIES.md"
        Validate-AgentDocsTrailer $message $files
        Validate-ExactTrailer $message $files "Configuration-Docs" "CONFIGURATION.md" "CONFIGURATION.md"
        Validate-SkillsTrailer $message $files
        if ($script:Errors.Count -gt 0) {
            Note "commit $commit violates the non-master branch documentation/artifact policy."
            foreach ($entry in $script:Errors) {
                [Console]::Error.WriteLine($entry)
            }
            Print-CommitTemplate
            exit 1
        }
        if ($LASTEXITCODE -ne 0) {
            Note "commit $commit violates the non-master branch documentation policy."
            exit $LASTEXITCODE
        }
    }
}

function Validate-CiPush([string]$BeforeSha, [string]$AfterSha, [string]$RefName) {
    if ($BeforeSha -eq "0000000000000000000000000000000000000000") {
        $parent = Invoke-GitText @("rev-parse", "$AfterSha^") -AllowFailure
        if (-not [string]::IsNullOrWhiteSpace($parent)) {
            $BeforeSha = $parent
        } else {
            $BeforeSha = $AfterSha
        }
    }
    Validate-CiPr $BeforeSha $AfterSha $RefName $RefName
}

function Get-EffectiveBaseForCiPr([string]$BaseSha, [string]$HeadSha, [string]$BaseRef, [string]$HeadRef) {
    if ($BaseRef -ne "master") {
        return $BaseSha
    }
    if ([string]::IsNullOrWhiteSpace($script:ReleaseTrailerCutoverBase)) {
        return $BaseSha
    }
    if (-not (Test-GitSuccess @("merge-base", "--is-ancestor", $script:ReleaseTrailerCutoverBase, $HeadSha))) {
        Fail "release trailer cutover baseline $script:ReleaseTrailerCutoverBase is not reachable from PR head $HeadSha."
    }
    if (Test-GitSuccess @("merge-base", "--is-ancestor", $BaseSha, $script:ReleaseTrailerCutoverBase)) {
        return $script:ReleaseTrailerCutoverBase
    }
    return $BaseSha
}

switch ($Mode) {
    "prepare-commit-msg" {
        if ($RemainingArgs.Count -lt 1) {
            Fail "usage: validate-policy.ps1 prepare-commit-msg <message-file> [source]"
        }
        $source = if ($RemainingArgs.Count -ge 2) { $RemainingArgs[1] } else { "" }
        Prepare-CommitMessage $RemainingArgs[0] $source
    }
    "commit-msg" {
        if ($RemainingArgs.Count -lt 1) {
            Fail "usage: validate-policy.ps1 commit-msg <message-file>"
        }
        Validate-CommitMsgHook $RemainingArgs[0]
    }
    "pre-merge-commit" {
        Validate-MergeIntoDevelop
    }
    "ci-pr" {
        if ($RemainingArgs.Count -lt 4) {
            Fail "usage: validate-policy.ps1 ci-pr <base-sha> <head-sha> <base-ref> <head-ref>"
        }
        Validate-CiPr $RemainingArgs[0] $RemainingArgs[1] $RemainingArgs[2] $RemainingArgs[3]
    }
    "ci-push" {
        if ($RemainingArgs.Count -lt 3) {
            Fail "usage: validate-policy.ps1 ci-push <before-sha> <after-sha> <ref-name>"
        }
        Validate-CiPush $RemainingArgs[0] $RemainingArgs[1] $RemainingArgs[2]
    }
    default {
        Fail "usage: validate-policy.ps1 {prepare-commit-msg|commit-msg|pre-merge-commit|ci-pr|ci-push} ..."
    }
}
