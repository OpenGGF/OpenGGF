# S2 Crawl pause and enemy handoff — 2026-11-06

Base: develop `0d8ffdcd6480d432bab3822b11e550285c44ed23`; bounded lane D.
No recorder, fixture, bootstrap, timing, PLC, monitor or shared touch behavior changes.

## Native owner and executed evidence

`loc_3D2A6` (s2.asm:82505-82517) checks proximity on every nonnegative pause
countdown, including zero; `loc_3D416` (s2.asm:82629-82642) saves that prior
routine before entering attack. The engine omitted this pause call and turned
at zero. The existing helper now serves walking and pausing.

`loc_3D36C` (s2.asm:82573-82581) selects enemy flags for native invincibility.
The engine's timed-power-up test omitted Super Sonic. Accepted ordinary enemy
attacks then belong to `Touch_KillEnemy` (s2.asm:85514-85561), not the special
shield/direction logic. The callback now delegates retirement to its badnik
base; the existing touch controller applies the native rebound once.

Comparison-only execution reproduces all23 clean baseline chain JSON payloads,
23 owner companions and the entire chain assertion. At the failing Crawl
`($17B0,$01F0)`, the engine attack callback logs `PAUSING`, flags `$17`,
Super=true, timed invincibility=0, rolling=false, and returns without destruction.
Its successive input positions/velocities are `($17AE,$01DF)/($FD00,$0A20)` and
`($17AB,$01E9)/($FCA0,$0A58)`. Do not mistake the callback's earlier sampling
boundary for a completed comparison row. Recorded aux has routine6 before the
row7849 explosion; row7849 requires y-speed `-$0A58`. The corrected row agrees.

Two new accepted-enemy callback cases fail the destruction assertion before
implementation and pass afterward. Further local cases cover the zero pause
timer and uncurled Super special flag. No new object fields or API are added.

## Matched before/after and gate

| Focused identity / segment | Baseline | Candidate |
| --- | --- | --- |
| S2 complete-chain ARZ2 | 4442 errors; first5512 X `$2C71/$2C72` | Identical6409 rows and every field/error tuple |
| S2 complete-chain CNZ2 | 188220; first7849 Y speed `-$0A58/$0A58` | 49194; first10361 Tails X `$21AE/$21B0` |
| Chain walk boundary | Missing CNZ2 level advance at138828 | CNZ2 exit observed; missing HTZ2 segment23 advance at155218 |
| ARZ2 / CNZ2 level-select | Both pass, no skips | Both pass, no skips |

CNZ2 has13045 actual common rows, no missing row IDs. Exact tuple comparison
removes184375 and adds45349;3845 remain. Zero additions occur on the7849
baseline-error-free rows. The first differing field is removal of row7849
Y-speed error. The first added tuple is at10361, where the baseline has already
diverged: baseline Sonic `($1ACB,$059E)` versus native/candidate `($21CB,$041E)`;
baseline Tails `($1ACC,$059D)`, native `($21AE,$043B)`, candidate `($21B0,$0439)`.
Native/baseline/candidate Tails velocity is `($0C00,-$0C00)/($0ADF,-$0AE0)/(0,0)`.
Baseline camera `($1A2B,$0539)` versus native/candidate `($212B,$03B9)`.
Dynamic-art field sets differ on4634 rows; absent fields never imply agreement.
No grouped spans are expanded and expected values/tolerances are unchanged.

All prior chain payloads except CNZ2 and the aggregate art-gap report remain
identical. The four inherited special-return one-row art gaps persist. Newly
reached HTZ1/HTZ2 have first physics errors605 Y speed and6920 X speed, respectively,
and add their own art-gap observations. The old aborted walk supplies no baseline
for that suffix; no whole-chain parity or unrestricted suffix gate is claimed.

## Remaining owners and limits

ARZ2's first contact is capsule Obj3E slot17 at `($2C9C,$04B1)` with native left
edge `$2C71`, not an Obj89 pillar push. An executed later capsule solid call
uses that correct anchor and clamps `$2C75` to `$2C71`. This does not prove why
the earlier row5512 missed the clamp. Earlier admission/visibility/initialization
needs an instrumented owner check; capsule geometry was not changed.

Remaining CNZ2 first10361 is a Tails launch/control disagreement; newly reached
HTZ frontiers are uninvestigated. Art-gap lifetime/timing remains lead/C-owned;
no load-time timing inputs, recorder fields, fitted constants or mid-run imports
were introduced. Protected footprints remain unchanged.

## Reproduction and evidence

All test commands use the worktree's `tools/testing/maven_queue.py`, Java25,
Lua5.4 and surfaceless EGL, one alphabetical fork, the lane's exact unset list
and all original absolute ROM properties. Exact arrays/environment, full logs,
uncapped XML/JSON, complete raw captures, tuple lists and source patches are
under `/tmp/trace-coverage-20261106/D/`. The `run.py` driver saves each invocation
before clearing this worktree's reports. Focused `trace-replay` selections only:
no complete profiles, smoke or full suite.

Baseline selection is `TestS2CompleteEmeraldRunChain,TestS2Arz2LevelSelectTraceReplay,TestS2Cnz2LevelSelectTraceReplay`.
Observed controls add `TestCrawlBadnikInstance`; clean-source final verification
also selects shared touch, source/timing and gitlink guards. The durable handoff
records the exact final command, results, SHA and timings.

The first wrong-profile request was cancelled before admission (zero tests).
The first probe compile failed because its log called a concrete method through
`PlayableEntity` (zero tests); its full output is preserved separately. Corrected
observed baseline and candidate execute successfully with the inherited strict
chain failure. All temporary source probes are removed before delivery.

Clean-source final focus executes344 cases:343pass, the expected strict chain
failure,0errors,0skips. Shared touch83, architectural87, build-tooling124,
TraceChaser boundary12, committed timing3, timing authority26, Crawl6 and both
level-select controls pass. All27 candidate trace payloads and27 owner companions,
plus the full chain assertion, exactly reproduce the observed candidate after
source-probe removal. This is focused verification, not a full-suite result.
