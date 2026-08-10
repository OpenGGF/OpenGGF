# Unfinished-remediation closeout and `next` integration ledger

Date: 2026-08-10

## Scope and delivery boundary

This ledger originally separated delivered `develop` behavior from the second
remediation wave's local review candidates. On 2026-08-10 those reviewed tips
were integrated locally into `next`, based on exact `origin/next` commit
`3f510b900c4f897185af4f5d2ab4e1faf4a6cd0e`. The integration remains local:
`next` has not been pushed by this workflow.

The first remediation wave was delivered by merge `524695cd4`. That merge also
contains:

- `862ed8689`, which preserves the fixed `$48` checkpoint-wing Y while the
  independent Obj5A hand peer bobs; and
- `8754a28cf`, which seeds DEZ Plane B sampling from the ROM-owned background
  camera Y so the opening exterior window shows its moving star field.

Both fixes remain ancestors of the final observed remote tip. README, player
status, and the Special Stage bug list now agree on that delivered state.

## Recorded integration evidence

The accepted post-merge comparison ran in an isolated detached worktree at
exact commit `524695cd4`. An earlier main-workspace attempt was rejected because
concurrent Claude changes meant it was not an exact-commit result. The accepted
run used Maven's JDK 21 runtime, one Surefire fork, alphabetical order, and the
canonical Sonic 1 REV01, Sonic 2 REV01, and locked-on S3K ROMs:

```bash
mvn -Dmse=off -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dsonic1.rom.path=<Sonic 1 REV01> \
  -Dsonic2.rom.path=<Sonic 2 REV01> \
  -Ds3k.rom.path=<locked-on S3K> clean test
```

| Exact commit | Total | Pass | Failure | Error | Skipped |
|---|---:|---:|---:|---:|---:|
| updated baseline `c18beabae` | 14,349 | 14,273 | 31 | 14 | 31 |
| merged result `524695cd4` | 14,417 | 14,342 | 30 | 14 | 31 |

The 14,417 normalized class, invocation, outcome, and exception keys were
byte-identical to the accepted development-candidate manifest. Compared with
the updated baseline, the 71 added PASS outcomes include one red invocation
becoming green; exactly two obsolete scalar-only napalm pass rows were removed.
These are recorded 2026-08-09 integration results, not a rerun performed by this
documentation branch.

## `next` integration mapping

The feature histories diverged from `next` through earlier rebases and branch
rewrites, so literal merges attempted to reintroduce obsolete history. The
reviewed tip deltas were integrated in order and conflicts were reconciled
against the current `next` owners. This preserves the reviewed content without
claiming that the original feature commits are ancestors of `next`.

| Scope | Reviewed tip | Integrated `next` commit |
|---|---|---|
| AIZ miniboss napalm evidence and rewind identity | `23e7e5e0609d49cac665b1742566b4bb0b8f3a98` | `49fd2de6b` |
| AIZ2 end-boss splash lifecycle | `fdb74aded8ffc4a72d461241e5a17e5498f06a32` | `f7bc8ce4b` |
| Reserved load-time modes | `907b2baf214a736b0f78092aa2b06d4d04b848a4` | `5178cf3b3` |
| S2 native debug placement contract | `85862cdd3056111402e1d5590dfdf0528f27f54f` | `9a650ce3b` |
| S2 competition / human-P2 boundary | `97237c973d5f7e62d4661c62d302aed533f2479a` | `99ad8f6f2` |
| S3K custom SMPS meta-command boundary | `fbd411c1bb3c683dfef1c127aeff8c8eeb751e41` | `c4b438bcf` |
| LBZ Big Arm ROM port | `f50162928805f5f8d4b933eb287770655ef13964` | `2dad85fc6` |
| Documentation closeout | `638bcfb2b4b2b0449336ddc5c4b348d999e0ba04` | `b7a44d203` |

Integration reconciliation is recorded by `83310bbf4`. It adapts the reviewed
ports to owners that already existed on `next`: native AIZ barrel/splash
allocation, Big Arm fade and event boundaries, shared explosion construction,
and the exact rewind-policy inventory.

## `next` regression comparison

Both sides were run from clean JDK 21 worktrees with one alphabetical Surefire
fork, a 2 GiB fork heap, and the three canonical ROMs. The initial default 1 GiB
post-integration fork exhausted its heap; it produced no accepted comparison.
The corrected clean baseline also showed why the earlier non-clean baseline was
invalid: its stale compiled test tree omitted source-present tests.

| Exact tree | Total | Pass | Failure | Error | Skipped |
|---|---:|---:|---:|---:|---:|
| `next` baseline `3f510b900` | 16,874 | 16,767 | 65 | 29 | 13 |
| integrated `next` candidate, no disassembly links | 16,988 | 16,881 | 65 | 29 | 13 |

The final comparison has 115 added PASS outcomes and one removed PASS outcome.
The removed load-profile method was replaced by the renamed, stronger
`reservedModesWarnOnEveryResolutionAndExposeScopedFallbacks` PASS. No baseline
failure or error count worsened. The final no-disassembly run retained the exact
baseline aggregate of 65 failures, 29 errors, and 13 skips; its six red-key
substitutions are order-dependent singleton/registry tests, and the three
apparent new failures pass in the isolated six-class check. The earlier
pre-portability integrated comparison had byte-identical red keys. The focused
integration-regression selector passed 841/841, and the 46 rewritten
source-data checks plus two portability-policy checks passed with all five
`docs/*disasm` worktree links absent.

## Review rule

The bounded open items documented by each feature remain open; integration does
not broaden their evidence. In particular, do not infer LBZ trace parity past
raw frame 6314, custom SMPS execution, S2 native debug activation, or a human-P2
competition route. Do not combine the ordinary S2 CPU sidekick with a human-P2
role.
