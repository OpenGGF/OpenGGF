# LRZ title-owned enemy art and FIFO overflow — 2026-11-07

Base: `8afbc8b7ca0bc0746824aa8fb9ce95e545898114`; original locked-on S3K
SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`. Native reference pin:
`8b2cf3c4178f2382f844da67798de4befc50c5e0`. Java 25, surfaceless EGL,
one alphabetical Surefire fork, seven explicit original ROM properties.

## Production cause and bounded correction

`Sonic3kObjectArtProvider.scheduleEnemyKosArt` omitted LRZ. Preparing standalone
badnik renderers did not submit the ROM's `LoadEnemyArt` jobs. Both acts select
`PLCKosM_LRZ`: Fireworm segments `$16F332 -> $A240`, Iwamodoki
`$16F4E4 -> $A600`, then Toxomister `$16F7E6 -> $AC40`.
The actual ROM table at `$2F8FC` is
`0002 0016F332 A240 0016F4E4 A600 0016F7E6 AC40`; the three reference
compressed assets exactly match the supplied ROM. Native owners:
`LoadEnemyArt (sonic3k.asm:64321-64353)`,
`Offs_LoadEnemyArt (sonic3k.asm:64375-64376)`, and
`PLCKosM_LRZ (sonic3k.asm:64463-64467)`. No FixBugs conditional applies.

The missing recipe shifts subsequent producer identities: in the unchanged
control, the first starpost job takes module ordinal 759 / direct 1124. Native
modules 759–761 are the missing enemy archives, and direct 1124 belongs to the
Fireworm module. The queued starpost child therefore cannot receive a matching
completion; it remains unready. A second starpost, the miniboss and the first
results archive fill the remaining module slots. The next results archive
throws after 5,956 comparisons. The results producer exposes the stall; changing
its submission order, waiting artificially or enlarging the FIFO would conceal
the missing owner.

Production adds only the three LRZ recipe entries. The existing title admission
lease, teardown, pending FIFO batch, readiness, claim and rewind owners retain
their contracts. Renderer preparation is still distinct from queue submission;
this change adds no new physical-DMA implementation or timing authority.

## Evidence and limits

The exact independent target is
`TestS3kKnucklesSuperLrz2SegmentTraceReplay#replayMatchesTrace`,
Knuckles super-emerald run `lrz_2`, movie offset 393340, planned 8,945 rows.
Despite its name, the recorded native level is zone 9 / act index 0 (Act 1).
The unobserved current `8af` report exactly equals the older frozen `93a` full JSON:
803 groups, zero warnings, 5,956 compared rows, first mismatch row 34
`queue.s3k_kos_direct.busy` (true/false). The overflow message also matches;
stack line numbers differ after intervening commits. A late Jackson observation
reproduces the same report and preserves 5,956 actual comparisons, 47,413 raw
ERROR tuples and 804 clean rows. The 2,989-row suffix is unobserved in that
baseline. Temporary observation is removed before delivery.

The first three independently computed ROM descriptor fingerprints exactly
match the recorded module 759/760/761 completions at rows 36/40/43. This proves
the missing batch independently of the alphabetical first-field presentation.
No fixture, bootstrap, tolerance, queue capacity, readiness guard or source
closure change is involved. No capture or publication was performed.

Candidate qualification (four exact replay classes, no skips or execution
errors):

| Witness | Compared rows before → after | Groups before → after | First error after |
| --- | ---: | ---: | --- |
| Knuckles `lrz_2` target | 5,956 → 8,825 | 803 → 1,059 | 604 `player_mapping_frame`, 0x0021 / 0x0007 |
| Knuckles `lrz` | 3,199 → 3,199 | 280 → 235 | 892 `player_animation_id`, 0x0021 / 0x0020 |
| Knuckles `lrz_3` | 6,891 → 6,891 | 631 → 615 | 49 `rings`, 75 / 76 |
| Tails `lrz_2` | 9,405 → 9,405 | 1,212 → 1,212 | 490 `g_speed`, -0C40 / -0D40 |

The original FIFO exception is resolved and the target's prescribed comparison
gate is reached. This remains a red replay. Its 2,869 newly observed rows have
no paired baseline; the 120 planned row IDs outside the comparison export are
not claimed as agreement. A higher whole-report group count cannot grade an
unobserved baseline suffix.

`tools/traces/compare_frame_comparisons.py` compares actual common rows:

| Witness | Common rows / baseline clean rows | Added / removed ERROR tuples | First added row | Added on clean rows |
| --- | ---: | ---: | ---: | ---: |
| target | 5,956 / 804 | 449 / 29,835 | 5,886 | 0 |
| Knuckles `lrz` | 3,199 / 475 | 41 / 18,953 | 950 | 0 |
| Knuckles `lrz_3` | 6,891 / 40 | 0 / 49 | none | 0 |
| Tails `lrz_2` | 9,405 / 490 | 13 / 13 | 9,426 | 0 |

No baseline row is lost. The target's added tuples are queue fields on already
failing rows: at 5,886 native resultsGeneral is active, corrected engine queues
are idle before its later results producer, while the old unrelated stalled
starpost accidentally matched busy=true. Error values changing count as both
added and removed tuples. The existing gameplay/animation frontier is earlier;
no results delay was fitted to it.

Knuckles `lrz` additions are later queue fields and 12 changed unmatched-completion
diagnostic values; Tails' 13 additions replace 13 existing diagnostic values
(12 unmatched completions and closing pending submissions), with unchanged raw
error count. Knuckles `lrz` also stops emitting the conditional closing
`hardware_timing.pending_submissions` field at row 3,322 because its pending
list is empty; this coverage difference is explicit, not inferred agreement
on an absent field. Core compared fields remain present. All common-row ERROR changes are confined
to queue fields and hardware-timing diagnostic text; gameplay/animation ERROR
tuples are unchanged. Tails has readiness
edges but no per-frame queue-state coverage. All 1,809 baseline clean rows across
these witnesses gain no ERROR; all nonempty actual-row additions follow the
remaining first frontier.

The final observer-free selection uses 17 exact class selectors (18 actual
classes because `TestSonic3kLevelLoading` exists in two packages): 171 tests pass,
one inherited target assertion failure, zero execution errors/skips. The two
new producer tests pass; all four required S3K keep-greens pass, along with
module/direct queues, results, title admission, rewind, art-mapping and timing
authority checks. Its unobserved target JSON exactly matches the observed
candidate, including the complete report and comparison-gate receipt.

Temporary observation is fully restored: source equals the base byte-for-byte;
all three rebuilt `AbstractTraceReplayTest*.class` files match an independent
compilation of the archived original byte-for-byte. The observer marker is
present in the retained observed class and absent in the restored classes.
Only this S3K recipe changes production code, so no cross-game shared-code S1
chain control or broad smoke/profile sweep is selected.

## Reproduction and retained evidence

Every executed Maven test uses this tree's `tools/testing/maven_queue.py`,
`-Dmse=off -B`, Java 25, surfaceless EGL, seven explicit original ROM properties,
`-Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical`. Replay selection uses
`-Ptrace-replay-r7` with these exact class selectors:

```text
TestS3kKnucklesSuperLrz2SegmentTraceReplay
TestS3kKnucklesSuperLrzSegmentTraceReplay
TestS3kKnucklesSuperLrz3SegmentTraceReplay
TestS3kTailsFullChainLrz2SegmentTraceReplay
```

The target control selects `#replayMatchesTrace`. The red unit is
`-Dtest=TestLrzEnemyKosOwner` without a trace profile. Final exact selection:

```text
TestLrzEnemyKosOwner,TestS3kKosModuleQueue,TestS3kKosDecompressionQueue,TestS3kKosDecompressionQueueLifecycle,TestS3kKosTimingRewindIntegration,TestS3kResultsKosQueueAndChildren,TestS3kResultsKosQueueRewind,TestKosinskiModuleQueueGameplayIntegration,TestSonic3kTitleCardKosQueue,TestRuntimeArtAdmissionContract,TestHardwareTimingAuthorityGuard,TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kKnucklesSuperLrz2SegmentTraceReplay#replayMatchesTrace
```

The external task archive `/tmp/trace-frontiers-20261107/lrz2/` retains complete
frozen drivers, exact source/input/ROM/environment hashes, native table/descriptor
proof, full uncapped XML/assertions/JSON, actual `FrameComparison` JSONL and exact
row deltas, observer restoration proof, timing, STATUS and HANDOFF. Workers do
not integrate, push, publish traces or edit the frontier log. Static citation,
link and policy checks qualify the documentation; no extra Maven run follows
prose-only completion.

All broader character, donor, viewport, continuous-chain and physical-residency
gaps in the LRZ matrices remain inherited; a segment pass would certify only
its actual compared rows and fields.
