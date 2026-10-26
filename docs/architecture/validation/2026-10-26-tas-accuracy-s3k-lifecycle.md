# S3K TAS lifecycle accuracy repairs — 2026-10-26

Assigned scope: `special-stage-entry-input-latch` (`s3k-sphere-timing:2`)
and `mhz-proxy-repeated-damage` (`s3k-boss:2`). Parent owns classification,
shared-loop/rules changes, combined validation, central documentation and delivery.
Development base: `58e1abb44c716415bf308cc591776d2429c85a41`.
Worktree: `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-f6c34218`.
Branch: `bugfix/ai-tas-accuracy-s3k-lifecycle-20261026`.

## Native rationale and boundary decisions

Pinned disassembly: `docs/skdisasm` at
`8b2cf3c4178f2382f844da67798de4befc50c5e0`, locked-on `sonic3k.asm`.
These are ordinary shipped `FixBugs=0` state transitions; neither repair needs
arbitrary RAM, trace values, route/frame tests or a compatibility exception.
The S2 sideways exception is untouched.

Blue Sphere: `SpecialStage` (sonic3k.asm:10621-10628) finishes `Pal_FadeToWhite`
then masks interrupts. `loc_8454` (sonic3k.asm:10750-10761) calls
`Process_Sprites` before the next `Wait_VSync`.
`loc_9600` / `loc_964A` (sonic3k.asm:11996-12033) read held Ctrl_1 Up and add
`$200` velocity. The final fade sample must survive into that one boot pass;
later host input must remain available to subsequent normal sampling.
The existing 22 pre-boot and 22 post-boot holds must not change.

A correction to the older assessment's control is necessary: neutral on the
final fade followed by Up only on the synthetic boot tick cannot start the
native boot pass, since no new native poll exists there. Retain this case as
a deferred-input negative control; held Up and first-mainloop Up supply positive
controls. OR-ing old and new buttons would manufacture an input sample. FAST
startup without a sampled fade remains separately covered.

MHZ: `loc_76004` / `loc_7601A` (sonic3k.asm:157019-157033) reset parent
collision/HP on every routine-0 pass before consuming the weather-child signal.
`loc_764A0` (sonic3k.asm:157363-157376) initializes the proxy's independent
collision byte and publishes its parent link.
`sub_76782` / `loc_767A0` / `loc_767B0` / `loc_767E8`
(sonic3k.asm:157639-157690) check parent and proxy collision bytes independently
of the flash countdown. A reset parent with a still-zero proxy loses HP each
owner pass; the existing nonzero flash timer prevents collision from being
cleared again. Pre-arena damage floors at HP2; ordinary collision restoration
and HP1 defeat happen only at flash expiry. The accepted-contact fixture must
retain exactly one early attack and actual weather destruction, never set HP,
routine or damaged flags to manufacture the tail.

## Validation record

$HOME substitutions below preserve the executed original absolute ROM path;
the commit resource policy forbids literal user-home paths in repository prose.
No ROM alias or copy was used.

Original S3&K ROM: `$HOME/code/projects/OpenGGF/s3k.gen`.
Java home `/usr/lib/jvm/java-25-openjdk`; Lua `/usr/bin/lua5.4`.
Session/render tests explicitly select surfaceless EGL with both `DISPLAY` and
`WAYLAND_DISPLAY` unset. No desktop input automation or ROM links/copies.

Setup-only failure: the first red-check shell redirection failed because a fresh
worktree had no `target/` directory. Maven never launched. Created `target/` and
submitted the intended focused check once.

Prerequisites passed: queued `maven_queue.py -v` reported Maven 3.10.0,
Java 25.0.5.0.2; `/usr/bin/lua5.4 -v` reported 5.4.9;
`sha1sum $HOME/code/projects/OpenGGF/s3k.gen` reported
`cfbf98c36c776677290a872547ac47c53d2761d6`. Version-probe queue wait: 334s.

Red command (production source unchanged at the base; strengthened tests):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestTasS3kSpecialStageEntryInput,TestTasS3kMhzRapidHit,TestSonic3kSpecialStageManagerSnapshot' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

17 tests, 4 failures, 0 errors, 0 skips. Queue wait 147s; Maven execution 1:32.
Exact failures:

- `TestTasS3kMhzRapidHit#oneEarlyProxyHitRepeatsNativeDamageAfterWeatherDestruction(boolean)[2]`:
  early-hit HP expected 2, observed 8 after all six updates.
- `TestSonic3kSpecialStageManagerSnapshot#finalFadeLatchRestoresAcrossBootWithoutOverwritingTheNextSample`:
  expected started=true, observed false at boot.
- `TestTasS3kSpecialStageEntryInput#finalEntryFadeInputOwnsBootAndNormalSamplingResumes(int)[2]`:
  final-fade pulse expected started=true, observed false.
- The same session selector `[3]`: boot-only Up expected started=false,
  observed true. Neutral `[1]`, held Up `[4]`, first-mainloop Up `[5]` passed.

Candidate command (all implementation and fixture changes present before admission):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestTasS3kSpecialStageEntryInput,TestTasS3kMhzRapidHit,TestSonic3kSpecialStageManagerSnapshot,TestSonic3kSpecialStageRewindAdapter,TestSonic3kSpecialStageGameplaySnapshot,TestMhzEndBossNativeDamage,TestMhzEndBossHitProxyRewind,TestMhzBossObjects,TestS3kMhzEndBossGraphRewind' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

**169 tests passed, 0 failures, 0 errors, 0 skips.** Queue wait 621s;
Maven execution1:33. No intermediate candidate failure or unchanged rerun.
Per-class executed counts: session entry 5; rapid MHZ 3; manager snapshot 10;
stage rewind adapter 2; stage gameplay snapshot 4; native MHZ damage 16;
proxy rewind 2; MHZ boss objects 124; MHZ graph rewind 3.

The original pulse and HP2 assertions are now green. Boot uses velocity `$200`
for pulse/held Up, all 22 fade holds preserve it, and the first mainloop pass
uses its new sample. The pressed-edge/P2 owner check and pending-latch
restore-forward checks pass. The single early MHZ callback yields HP8 at the
weather transition, then 7/6/5/4/3/2 over six owner passes. No-early-hit remains HP9.
Timer `$39`, flash 24, one boss-hit sound and the independently active parent /
cleared proxy are asserted. The reached continuation clamps HP2 for the remaining
flash, restores collision, follows routines 4/6/8/A/C, and accepts an ordinary
terminal hit to HP1 with defeat only after its 32-tick flash. Fresh ObjectManager
recreation and forward replay pass at all captured changed-owner boundaries.

Additional checks:

```bash
git diff --check
python3 tools/disasm/disasm_citations.py check src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossInstance.java src/main/java/com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageManager.java
python3 tools/disasm/disasm_citations.py check docs/architecture/validation/2026-10-26-tas-accuracy-s3k-lifecycle.md
```

Whitespace check passed. The source citation checker reported near 1 / ok 16 /
stale 5 / unverifiable 29. All five stale warnings are existing manager comments
that refer to calls/branches rather than the named label declaration (fade calls,
`sub_9580`, `locret_972C`); new labelled anchors were checked against the pinned
source. The dated Markdown invocation had no recognized citations. This is
advisory citation validation, not native execution. The ROM session and unit
results above are engine execution; no new emulator run is claimed.

This is focused validation only. Parent owns combined smoke/domain coverage,
central support classifications, integration and push. No broad suite ran here.
Final source SHA is recorded in the delivery checkpoint below.

## Implementation shape and inherited limits

The special-stage manager owns an immutable pending input sample captured only
when the final entry-fade update completes. Boot consumes it once using local
inputs, leaving current sampled input intact. Its existing snapshot includes the
pending sample; initialization/cleanup clears it. No fade count, extra update,
shared-loop behavior or movement owner changes.

MHZ stores parent collision eligibility separately from its flash countdown and
stores the proxy's own collision byte on the proxy. The exact parent `$44` link
and proxy `parent3` are normal captured object references. Native retirement, allocation rejection and explicit removal
clear the parent link so a released proxy cannot remain an authority after
rewind. Routine0 resets HP9 and parent collision eligibility on every pass;
the damage tail consumes a zero proxy byte while the parent is active, including
during an existing flash. Ordinary flash expiry restores both collision owners.

The existing ordinary proxy-hit fixture in `TestMhzBossObjects` seeded HP2 while
leaving routine0 active. It now explicitly selects the ordinary active phase
(routine `$E`) before that component contact; its HP/offset/attacker-stop
assertions remain. Its two palette-only flash fixtures also select the
post-weather wait phase explicitly, retaining every native color/countdown
assertion. Otherwise a routine0 parent with no admitted proxy cannot represent
the ordinary flash boundary. These fixture corrections followed source review
before candidate execution. The separate unseeded TAS fixture tests routine0
reset itself.
`TestMhzEndBossHitProxyRewind` no longer asserts that assigning the parent's
flash boolean rewrites the proxy byte. It checks independent collision state,
the accepted-hit clear and the exact restored reverse link.

Bounds: entry sessions use real ROM loading and GameLoop input at 320px,
Knuckles solo, native/off movement, with a declared special-stage request.
This does not prove the preceding giant-ring approach or an optimal sphere route.
The owner snapshot test covers held/pressed/P2 samples, all 22 post-boot holds,
and restore-forward before the final fade, pending boot, and first mainloop
sample. MHZ uses production objects and ObjectManager identities with a declared
valid gliding contact callback and actual weather-child destruction. It does not
prove published approach pixels or the whole scrolling encounter. The added
continuation follows real movement callbacks and terminal hit state; its player
setup is a contact inlet, not a restored complete gameplay world. Object graphs
and RNG are restored; unrelated world/camera/audio state is outside this fixture.
Existing ordinary damage/defeat/retirement tests remain in the focused selection.

Both fixes concern manager/encounter lifecycle rather than viewport projection
or donated player movement. Broad viewport/donor/team/route certification is
inherited coverage work, not established by this local repair. Parent owns the
coverage-matrix/backlog and final support classification updates.

## Reel selector reconciliation

Preserve the capture hooks; these method identities change with their meaning:

- `TestTasS3kSpecialStageEntryInput#currentLimitationPreLoadPulseIsLostBeforeFirstSpecialStagePlayerUpdate`
  becomes `#finalEntryFadeInputOwnsBootAndNormalSamplingResumes`, now parameter
  values 0 neutral, 1 pre-load pulse, 2 boot-only pulse (deferred negative), 3 held,
  4 first-mainloop Up. The former boot-only positive is corrected for native
  polling order; FAST first input has a separate positive component test.
- `TestTasS3kMhzRapidHit#currentLimitationEarlyProxyHitDoesNotRepeatDamageAfterWeatherDestruction`
  becomes `#oneEarlyProxyHitRepeatsNativeDamageAfterWeatherDestruction`, retaining
  false/true no-early-hit/early-hit controls. A separate
  `#earlyHitGraphRestoresThroughWeatherDamageClampAndFinalDefeat` case exercises
  restore-forward and continuation; it is not a second published-route claim.

Parent must reconcile these names with the central scope/reel metadata.


## Local delivery checkpoint

Implementation source commit: `1926b38052161dd0c4361a59fece36360689f75c`.
This documentation checkpoint changes no tested Java source. The implementation
commit contains the exact candidate source exercised by the 169-test focused run.
The first local commit attempt was rejected solely because this record used
literal user-home paths; commands were normalized to `$HOME` and the hook was
rerun. No Java source changed after the passing focused run.
No live validation job remains. Focused logs were consumed into this record;
workflow-generated diagnostic files are disposable. Central changelog,
discrepancy/index/scope files were deliberately left to the parent as assigned.
