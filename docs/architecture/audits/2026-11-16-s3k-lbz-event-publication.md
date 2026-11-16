# LBZ event publication — 2026-11-16

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (develop).
Initial delivery/review head: `17d7ce1158d876ceb17ed0ec499398a611f12489`.
The [initial audit at that head](https://github.com/OpenGGF/OpenGGF/blob/17d7ce1158d876ceb17ed0ec499398a611f12489/docs/architecture/audits/2026-11-16-s3k-lbz-event-publication.md)
retains the original investigation and complete initial verification ledger.
This revision records the lead's independent review and its correction witnesses.

## Native owners and design

`Sonic3kLBZEvents` owns shake, foreground copies, numeric runtime publication and
`LbzBackgroundEvents`. One validated same-build payload includes retained tables,
latches, sampled clock/source geometry and constructor identity. No migration.
`SwScrlLbz` projects it; objects write only the zone flag. The shared shake API,
game-wide boolean semantics and other zone owners remain unchanged.
`LevelSetup` (sonic3k.asm:102231-102240) clears the three native shake words;
`ShakeScreen_Setup` (sonic3k.asm:104234-104255) advances them after the FG read.
The original audit maps word versus high-byte flag readers and every native owner.
Native movement writes the live camera after the foreground copy; cloud scrolling
advances $E00 per deformation dispatch. The shipped arithmetic is retained.
Rejected: renderer ownership plus a bigger snapshot; shake-only extraction leaving
cloud/table state elsewhere; object-computed offsets duplicating native setup.
The initial real-load red witness observed shake -5 becoming 0 on renderer rebuild.

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

## Lead smoke correction — a9202b5388

All three new findings hold; none was rejected.

1. **Fixed:** initial `DeformBgLayer` precedes LevelSetup/ScreenInit at
   `loc_6310` (sonic3k.asm:7788-7798). The live pre-ScreenInit flat output now
   has an explicit immutable projection phase; it does not create/tick a BG owner.
   Both actual directed-load paths (acts 1/2) and native/tall sampling are witnessed.
   The load follows SharedLevel → directed module → HeadlessTestFixture, as the
   Typing Hedgehog host does. Native publication bytes stay unchanged by sampling.
2. **Fixed:** the audio guard bans identifiers from the superseded split-runtime
   and recording-lease-switch system. Unified audio presentation owns cadence,
   PCM, history and leases. LBZ has no such audio role: its unrelated numeric
   snapshot encoder is now named `captureNumericPublication`; no guard exemption.
3. **Fixed:** complete LBZ structural/bound-identity validation already precedes
   manager mutation. The obsolete catch/rollback/logger would swallow unexpected
   valid-payload failures. It is removed; unexpected owner/service errors propagate.
   Exactly two retired LBZ inventory rows were deleted; no new allowances added.
   A fault-injected owner witness proves the original exception reaches the caller.

## Verification ledger and limits

The [previous correction ledger](https://github.com/OpenGGF/OpenGGF/blob/a9202b53880f01b81b028876d29bdaae3111b176/docs/architecture/audits/2026-11-16-s3k-lbz-event-publication.md)
records all earlier red runs and the 720-test/trace qualification at that head.
Current commands use `python3 tools/testing/maven_queue.py -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl` (`Q`), never `-Pguards`.
All selectors are single quoted `-Dtest=...` arguments, followed by `test`.
ROM discovery reports all configured properties found, `missing=none`.
Worktree-local logs are `target/lbz-smoke-*.log`; no generated reports are committed.

- `Q -Dtypinghog.sections=s3k:6:1 -Dtest=P,TestTypingHedgehogSections#sonic3kSections,A,B,Z,O test`
  (`red`): 111 tests, 5 failures, 1 typing-load error, 0 skips. Both directed-load
  witnesses reproduce the NPE; the owner-failure witness exposes swallowing;
  audio/broad-catch guards reproduce the lead's exact new violations.
- The same selection (`focused`): 111 tests, 0 failures/errors/skips.
- `Q -Dtest=L,G,A,B,O,TestTypingHedgehogSections test` (`qualified`):
  **834 tests, 0 failures/errors/skips**, including all 27 typing sections.
- `Q -Dtest=*Lbz*TraceReplay test` (`traces`): 5 inherited assertion failures,
  0 errors/skips. Every key in all five full JSON reports remains identical to
  the qualified common-base reports. Baseline reused: same e676eb5086 source,
  unchanged trace classes/fixtures/ROMs, Java 25, selection and execution properties;
  prior baseline equality was preserved before replay overwrote the current files.
- Strict citations retain the same three baseline manager stales below; all new
  citations resolve. `git diff --check` passes. No categories/full-suite sweep.

Aliases: `P = TestS3kLbzScreenPublication`, `A = TestAudioPresentationArchitectureGuard`,
`B = TestBroadCatchSwallowingGuard`, `Z = TestZoneEventRuntimeAccessGuard`,
`O = TestObjectPhysicsStandardizationGuard`, `L = *Lbz*,*LBZ*,!**/trace/**`.
`G = TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,
TestS3kZoneRuntimeStateAdapters,TestS3kAdditionalScrollProjection,Z`.

Actual qualified log lines confirm each requested class executed by name:

| Class | Tests | Failures/errors/skips |
| --- | ---: | --- |
| TestAudioPresentationArchitectureGuard | 45 | 0/0/0 |
| TestBroadCatchSwallowingGuard | 4 | 0/0/0 |
| TestZoneEventRuntimeAccessGuard | 12 | 0/0/0 |
| TestObjectPhysicsStandardizationGuard | 35 | 0/0/0 |
| TestTypingHedgehogSections | 27 | 0/0/0 |

| Trace suffix | Frames | Baseline/candidate errors | First error |
| --- | ---: | ---: | --- |
| KnucklesLbz2BigArm | 6309 | 263 | 76, rings |
| LbzZoneSlice | 46075 | 2091 | 30582, tails_y |
| KnucklesSuperLbzSegment | 24832 | 3035 | 0, y |
| SonicTailsLbzSegment | 32581 | 4940 | 1171, x |
| TailsFullChainLbzSegment | 42161 | 3281 | 0, y |

Inherited manager citations: `ShakeScreen_Setup`, `Change_Act2Sizes`,
`Process_Sprites`; an unrelated scddisasm checkout warning also remains.
The lead owns combined smoke/review. No new pixel/full-route certification.
Final local queue wait was under 30s reporting resolution, Maven execution 1m10s;
trace queue wait at least 90s, execution 1m15s. User wait is zero; uninstrumented
investigation/edit time is unknown. All Maven jobs completed before delivery.
