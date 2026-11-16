# LBZ event publication — 2026-11-16

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (develop).
Initial delivery/review head: `17d7ce1158d876ceb17ed0ec499398a611f12489`.
The [initial audit at that head](https://github.com/OpenGGF/OpenGGF/blob/17d7ce1158d876ceb17ed0ec499398a611f12489/docs/architecture/audits/2026-11-16-s3k-lbz-event-publication.md)
retains the original investigation and complete initial verification ledger.
This revision records the lead's independent review and its correction witnesses.

## Native owners and design

- `LevelSetup` (sonic3k.asm:102231-102240) clears the flag and both shake offsets.
  The shared Java boolean survives level reset and is not this native state.
- `LBZ2BGE_DeathEgg` (sonic3k.asm:111531-111546) reads the whole flag word.
  `loc_62942` (sonic3k.asm:129688-129696) and `loc_62A0A`
  (sonic3k.asm:129756-129763) read its high byte. Ship `loc_8D450`
  (sonic3k.asm:193052-193058) sets that byte; `loc_8D47C`
  (sonic3k.asm:193069-193080) clears the word. Objects now write only this zone flag.
- `Sonic3kLBZEvents` owns `S3kScreenShake`, foreground copies, the numeric
  runtime publication and `LbzBackgroundEvents`. The managed registry adapter
  captures identity with no duplicate byte payload; constructor identity is
  explicitly framed because generic scalar codecs omit final fields.
- `LBZ1_ScreenEvent` (sonic3k.asm:110931-110934) and `LBZ2_ScreenEvent`
  (sonic3k.asm:111353-111356) use the preceding shake offset before BG setup.
  `ShakeScreen_Setup` (sonic3k.asm:104234-104255) reads the native primary player
  and level clock; positive flags above $14 continue into the second table.
- `LBZ2_DeathEggMoveScreen` (sonic3k.asm:112111-112187) owns FG/BG speeds,
  accumulators, water target and direct ship movement. Foreground copies precede
  these live-camera writes. Ship movement is a non-consuming per-dispatch effect;
  a skipped movement routine publishes zero. Native routine >= 6 gates movement.
- `LBZ2_Deform` (sonic3k.asm:111632-111711) owns waterline/art/camera outputs;
  `LBZ2_DeathEggDeform` (sonic3k.asm:111898-111950) retains HScroll words and
  Death Egg latches. Subtracting shake from the already-shaken copy recovers its
  base, rather than subtracting again from live Y. The cloud longword advances
  per deformation dispatch (sonic3k.asm:111803-111804,112045-112046).
- `SwScrlLbz` reads completed output. Explicit detached pictures are presentation
  only; an uninitialized live owner renders flat without creating or ticking one.
  The composed payload captures words, latches, clock/source coordinates and all
  three shake words. Rebuilds consume nothing. Same-build only; no migration.
- Existing layout/collision/terrain and window owners remain scoped to LBZ.
  No new background collision difference or fixed-bug alternative is introduced.
  Existing act matrices retain full-route/viewport/donor/roster certification gaps.

Rejected designs: retaining renderer ownership with a bigger snapshot preserves
call-count effects; moving shake alone leaves cloud/table state outside the owner;
object-computed offsets duplicate native clock and death-gate decisions.
The original real-load witness observed shake -5 becoming 0 on a same-frame rebuild.

## Independent review dispositions

1. **Fixed — seamless publication.** The review holds: a new event owner was
   created by the actual act executor without fresh ScreenInit. The existing
   transition test reached LBZ2 but never asserted its background.
   `LBZ1BGE_DoTransition` (sonic3k.asm:111285-111332) preserves HScroll +$1E2;
   `loc_5432E` (sonic3k.asm:111330-111334) applies retained LBZ1 deformation.
   The synchronous load continuation now transfers that publication to LBZ2.
   Waiting/transfer updates foreground output without another deformation;
   the next `LBZ2BGE_FromTransition` (sonic3k.asm:111475-111485) advances it.
   A real load/executor witness seeds a declared retained longword ($12340000),
   preserves a nonzero native clock through the act change, and sees $12340E00
   after the first LBZ2 dispatch. Held projection/rewind retains source geometry.
2. **Fixed — clocks.** Abstract manager update count is a different clock and
   resets on act init. LBZ now reads `levelManager().getFrameCounter()` through
   its event helper. Shake and both water-wave passes use that word; the completed
   projection stores it instead of resampling a later render counter.
   Witnesses deliberately disagree with the event ordinal and compare native outputs.
3. **Fixed — atomic manager restore.** LBZ is included before `super.restore`.
   Preflight checks structural framing AND bound runtime identity. Both corrupt
   version and structurally valid foreign-player payloads leave all tested base
   fields, arrays, extra bytes and registry owner unchanged.
4. **Fixed — falling return.** `LBZ2BGE_Falling` (sonic3k.asm:111600-111608)
   overwrites the FG copy from live Y, decrements live Y by two and returns.
   It now skips shake setup, retains native flag/offset words and publishes no
   applied FG shake. The witness checks flag 19 and offset -5 remain retained.
5. **Fixed — tests.** The collapse witness now completes native startup and
   checks nonzero shake on the following screen dispatch. The rebind test drives
   a real skipped-movement dispatch and observes zero replacing a prior delta;
   it no longer calls a side-effect-free getter twice as a purported witness.
6. **Fixed/recorded — identity and primary slot.** Differing bound act OR player
   is rejected before mutation; only an unbound standalone owner may construct
   its payload's runtime identity. Manager preflight enforces the same condition.
   Shake reads SpriteManager's primary player, not camera focus. The public camera
   setter rejects null, so the stored nullable focus field is cleared in the
   lifecycle witness while the actual primary remains alive and counts down.

No substantive review finding was rejected. The sweep also found two disallowed
LBZ service-facade reads (one from the initial delivery); existing event helpers
replace them, with the runtime-access guard as the failing witness.

## Verification ledger and limits

All Maven commands use `python3 tools/testing/maven_queue.py -Dmse=off` (`Q`).
Except the access guard, commands also use `-Dopenggf.headless.gl=surfaceless-egl`.
Selectors below are single quoted `-Dtest=...` arguments, followed by `test`.
ROM discovery reported all configured properties found, `missing=none`.
Logs are disposable worktree-local `target/lbz-review-*.log`; none are committed.

- `Q -Dtest=TestS3kLbzScreenPublication,TestS3kLbz1MinibossAndTransitionHeadless,TestS3kLbz1KnucklesSequenceHeadless test`
  (`red`): 53 tests, 5 failures, 2 fixture errors, 0 skips. Null setter/startup
  setup was corrected before attributing the implementation defects.
- `Q -Dtest=TestS3kLbzScreenPublication,TestS3kLbz1MinibossAndTransitionHeadless test`
  (`red2`): 23 tests, 7 failures, 0 errors/skips; all core review defects witnessed.
- First selection again (`focused`): 53 tests, 1 next-frame fixture failure,
  0 errors/skips. Completing startup fixes that witness; core cases passed.
- `Q L,G test` (`local`): 706 tests, 1 fixture-clock failure, 0 errors/skips.
  The detach component test now explicitly sets the native level clock it declares.
- `Q -Dtest=TestS3kLbzScreenPublication,TestS3kAdditionalScrollProjection test`
  (`context-red`): 28 tests, 2 failures, 0 errors/skips: foreign bound identity
  still mutated manager state, and transferred projection used new BG coordinates.
- `Q L,G test` (`qualified`): 708 tests, 0 failures/errors/skips.
- `Q -Dtest=TestZoneEventRuntimeAccessGuard test` (`access-red`): 12 tests,
  1 failure, 0 errors/skips, identifying exactly the two LBZ service calls.
- Final `Q L,G,TestZoneEventRuntimeAccessGuard test` (`final-local`): 720 tests,
  0 failures/errors/skips. Final `Q T test` (`final-traces`): 5 inherited assertion
  failures, 0 errors/skips; all five full JSON reports remain identical to base.
- Base and candidate `Q T test` (`traces-baseline`, `traces`): each 5 assertion
  failures, 0 errors/skips. Every key in all five full report JSON files matches.

`L = *Lbz*,*LBZ*,!**/trace/**`.
`G = TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,
TestS3kZoneRuntimeStateAdapters,TestS3kAdditionalScrollProjection`.
`T = *Lbz*TraceReplay`. Baseline is a detached cow checkout of the common base.

| Trace suffix | Frames | Baseline errors | First error |
| --- | ---: | ---: | --- |
| KnucklesLbz2BigArm | 6309 | 263 | 76, rings |
| LbzZoneSlice | 46075 | 2091 | 30582, tails_y |
| KnucklesSuperLbzSegment | 24832 | 3035 | 0, y |
| SonicTailsLbzSegment | 32581 | 4940 | 1171, x |
| TailsFullChainLbzSegment | 42161 | 3281 | 0, y |

Strict citations retain three baseline manager stales (`ShakeScreen_Setup`,
`Change_Act2Sizes`, `Process_Sprites`); new LBZ citations have none. An unrelated
scddisasm checkout warning is inherited. No category/full-suite/smoke sweep ran;
the lead owns smoke/review. There is no pixel or new full-route certification.
Queue wait and Maven execution are logged separately; user wait is zero.
Uninstrumented investigation/edit time is unknown. Final local queue wait was at
least 120s, Maven execution 1m47s; trace wait at least 271s, execution 1m17s.
