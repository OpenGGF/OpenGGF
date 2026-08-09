# Unfinished-remediation closeout and local-branch ledger

Date: 2026-08-09

## Scope and delivery boundary

This ledger separates delivered `develop` behavior from the second remediation
wave's local review candidates. The documentation audit used `origin/develop`
`e2aa50cd5980efc720f70c1c2a6209b2637b3042` as its baseline. At final ledger
reconciliation, the observed remote tip was
`eb619f787c13c41c30df842c377b577dbb74d5a2`; the five intervening commits do
not integrate any candidate listed below. A branch row is not a release claim:
every listed feature tip remains local, unmerged, and unpushed until a later
integration explicitly records otherwise.

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

## Local human-review candidates

| Scope | Local branch and commit | Bounded result and evidence | Remaining blocker / integration note |
|---|---|---|---|
| AIZ2 end-boss splash lifecycle | `feature/ai-aiz2-splash-evidence` at `fdb74aded8ffc4a72d461241e5a17e5498f06a32` | Fixes the one-dispatch `Go_Delete_Sprite` marker and removes the synthetic range tail. Production AIZ2 event/slot and graph-rewind evidence passed 116/116; the focused route passed 1/1, graph rewind 5/5, and timing/compression guards 4/4. | Local/unmerged; six commits behind and one ahead of the final observed `origin/develop`. The full AIZ replay still stops before gameplay on the existing held-`VBLANK_ONLY` POST admission gap; native recapture also needs BizHawk 2.11. |
| Reserved load-time modes | `feature/ai-load-time-profile-semantics` at `907b2baf214a736b0f78092aa2b06d4d04b848a4` | Keeps `FAST`/`REALISTIC` behavior unchanged while scoping warnings and docs to `HardwareTimingService` admission. Configuration, timing, rewind, and authority coverage passed 87/87. | Local/unmerged; six commits behind and one ahead of the final observed `origin/develop`. Both modes remain unfinished: `FAST` needs an approved safety policy; `REALISTIC` needs uncensored S3K measurements and a confidence/context rule. |
| AIZ miniboss napalm evidence and rewind identity | `feature/ai-aiz-miniboss-napalm-evidence` at `23e7e5e0609d49cac665b1742566b4bb0b8f3a98` | Replaces nearest-barrel rewind guessing with captured graph identity. A live production route proves Knuckles-only activation, three barrel pairs, native waits/slots, terrain impacts, collision/lifetime, and deterministic restore; the focused gate passed 74/74 and the committed `aiz_3` trace-v5 fixture validated. | Local/unmerged; six commits behind and one ahead of the final observed `origin/develop`. Evidence is bounded to the miniboss route; no 68-segment Knuckles run-chain replay or frontier advance is claimed. |
| S2 native debug placement | `feature/ai-s2-native-debug-placement` at `85862cdd3056111402e1d5590dfdf0528f27f54f` | Test/docs-only REV01 catalog and lifecycle ratchet: ROM contract 3/3, adjacent owner sweep 22/22, singleton/rewind guards 43/43. No `src/main` scaffold or activation landed. | Local/unmerged; five commits behind and one ahead of the final observed `origin/develop`. No native-debug BK2 exists; four catalog IDs still lack placement-safe factories, and controller/global gates, preview, allocation, rewind, and `hasLevelDebug()` must land atomically. |
| S2 competition / human P2 | `feature/ai-s2-competition-capability` at `97237c973d5f7e62d4661c62d302aed533f2479a` | Test/docs-only REV01 boundary plus executable architecture plan. The new characterization passed 2/2 and the final adjacent owner sweep 60/60. Singleton and rewind guards passed 43/43; 67/69 architectural methods passed with the same two existing size-ratchet failures, and `src/main` is unchanged. | Local/unmerged; five commits behind and one ahead of the final observed `origin/develop`. There is no native two-player movie or production mode owner; roles, input, two views, object/ring windows, scoring/results, act/zone lifecycle, rewind, and monitor consumption remain required. |
| LBZ Big Arm | `feature/ai-lbz-big-arm-evidence` at `f50162928805f5f8d4b933eb287770655ef13964` (base `9de7ecf7230100626fb7084b3f678daa6a5f478c`) | Bounded ROM port replaces the inert placeholder with the shipped `FixBugs=0` fight, exact child graph, defeat, capsule/results gate, falling floor, carrier escape, and MHZ handoff. Fresh evidence passed bridge 5/5, behavior 7/7, graph slice 4/4, production route 7/7, graph 10/10, legacy/route/graph/rewind gate 957/957, and consolidated focused/rewind 1,483/1,483. The 69-method architecture guard retained exactly its two known size failures; policy, pre-commit, and synthetic pre-push checks passed. | Clean local/unmerged/unpushed candidate; six commits behind and one ahead of the final observed `origin/develop`. The comparison lane errors before replay at raw frame 6314 because the strict timing-schedule compiler cannot represent its schema-valid VBlank-only `post_objects` completion. No trace parity or frontier movement is claimed. |
| S3K custom SMPS meta commands | `feature/ai-s3k-smps-custom-meta-capability` at `fbd411c1bb3c683dfef1c127aeff8c8eeb751e41` | Defines shipped-unreachable `FF01`/`FF02`/`FF03` as recognized source-width syntax with custom execution explicitly unsupported. Production changes are comments only; six characterizations (three exact-width and three non-effect) prove no `AudioManager` dispatch, no song-slot halt, and no mutable-Z80-memory copy. Focused tests passed 59/59, `TestBuildToolingGuard` passed 78/78, and 67/69 architectural methods passed with the same two existing size-ratchet failures. | Clean local/unmerged; five commits behind and one ahead of the final observed `origin/develop`. No custom-driver ownership, halt lifetime, mutable-memory/rewind contract, or trace/frontier advance is claimed. |

The documentation-closeout branch itself is `feature/ai-documentation-closeout`.
Its exact commit and validation commands are reported in the handoff because a
commit cannot embed its own hash.

## Review rule

Integrate each candidate independently after rebasing or merging the current
remote tip, rerunning its focused JDK 21 evidence, and comparing policy/guard
results. Do not infer delivery from a worktree's presence, reuse a branch-local
test count as current-`develop` evidence, or combine the S2 CPU sidekick with a
human-P2 role.
