# S3K TAS slot accuracy: bounded repairs

Task base: `58e1abb44c716415bf308cc591776d2429c85a41` (`develop`).
Worktree: `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-a07ef6f9`.
Branch: `bugfix/ai-tas-accuracy-s3k-slots-20261026`.
Final source commit: `7e0f20ef29b69a4eee499f89871ea66d1acab86e`. The completed post-repair runs documented below used this exact runtime code; the final affected-class rerun used its final test sources.

This is the implementation follow-up to [the supportability assessment](2026-10-26-tas-supportability.md) and [the executed S3K verdicts](2026-10-26-tas-verdicts-s3k.md). Original independent source evidence remains outside the repository at `$HOME/scratch/agent-tmp/codex/tas-trick-verdicts-2026-10-26/`. No new emulator movie or native full-route execution was performed here. Source derivations and executed engine component checks are distinguished below.

## Architectural decisions

**Accept `water-skim-slot-lifetime` (`s3k-hczslot:1`) as a bounded lifecycle repair.** `HCZWaterSplash_SkimMain` copies P1 X, processes both native participants, and ends at `HCZWaterSplash_DisplaySkim` / `Sprite_OnScreen_Test` (sonic3k.asm:75360-75404). `Sprite_OnScreen_Test` aligns X to $80, subtracts the coarse camera-back word, and deletes only when the unsigned difference exceeds $280 (sonic3k.asm:37302-37318). The current engine already has that range helper and real allocator retirement. The slot owner now runs that tail after the existing post-player participant hooks. Manager persistence remains solely to prevent generic pre-culling of its dummy spawn position. It no longer means immortal allocation.

The handler records retirement in its existing rewind snapshot, clears object-local participant/splash state, and stops subsequent physics/render hooks. Art and level configuration remain available for rewind. This preserves participant consequences on the deletion tick, releases the real slot for ordinary allocation, and prevents returning to the water from recreating an init-only owner. No stale address is treated as a live skim object. The existing viewport extension is retained by using `coarseXCullViewport`; native-width boundary assertions are the measured coverage here.

**Accept `cutscene-slot-six-alias` (`s3k-bridge:1`) as a bounded read of an existing slot.** Correct the assessment's fixed-slot shorthand: `_unkFAA4` is written from `a0` by `Obj_CutsceneKnuckles` (sonic3k.asm:128646-128652). Six is one demonstrated slot choice, not an unconditional default. `loc_65C04` tests the pointer for zero, reads its current X/Y through `Check_InMyRange`, and can press itself (sonic3k.asm:133993-134012). `Check_InMyRange` uses signed word endpoints (sonic3k.asm:180055-180073).

A separate numeric slot in `Aiz2BossEndSequenceState` is armed only by the real allocated actor's first initialization. Existing typed actor bindings continue to serve animation consumers; restore rebinding cannot arm the raw reference. `NativeSstSlotAccess.currentOwner` supplies the current live occupant without any new ObjectManager API. The button reads that owner's existing position words; a genuinely empty deleted slot reads zero. This needs neither a general writable SST model nor an unsafe cast or synthetic runtime occupant.

`loc_60DE` clears `_unkFA80..DMA_queue`, including `_unkFAA4` (sonic3k.asm:7650-7655). The existing level-event reset clears the new field. Boss-construction coordination resets its Java latches separately and preserves the native reference. The raw slot is captured/restored even when the referenced actor has retired, while the typed pointer retains its existing restore-rebind contract.

The rejected alternatives were hardcoding slot 6 when unarmed, using the retired Java actor's coordinates, keeping the water slot permanently allocated, or introducing arbitrary memory writes. None is required by these two native owners. The named S2 sideways safe-admission exception and all shared slot APIs remain unchanged.

## Executed checks

Home-directory paths below use `$HOME` to comply with repository resource policy; execution used the corresponding original absolute paths, without links or copies.

Original ROM: `$HOME/code/projects/OpenGGF/s3k.gen`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` verified with `sha1sum`. All Maven commands ran from the worktree above with Java 25 and Lua 5.4. `/usr/bin/lua5.4 -v` reported Lua `5.4.9`. The version probe reported Java `25.0.5.0.2` and Maven `3.10.0`:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -v
```

Before runtime edits, base plus new native assertions:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestTasS3kWaterSkimLifetime,TestTasS3kMemoryLimitations' \
  "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

Completed 2026-10-26 19:18:21 UTC: **14 tests, 11 assertion failures, 0 errors, 0 skips**. Five water cases failed on the unreleased owner/slot: range equality's next block, active departure, mixed P2 departure, post-jump camera departure, and allocation at 6 rather than the freed 5. Six button cases failed on self reuse, other-occupant reuse, the inclusive left range edge, restore-forward setup, stale coordinates in an empty slot, and typed rebinding spuriously arming an uninitialized actor. Ordinary initialized Knuckles, level-reset control and the unchanged full/free KosM control passed. These were reached assertions, not compilation/setup failures.

The version probe waited 341 seconds; the red test job waited 91 seconds and Maven reported 1:31 execution. Waiting intervals overlap and are not added as delivery time. First-build `java.io.tmpdir` absence was a launcher warning; the build created its own target directories and all tests executed.

Focused post-repair selection (700-second queue wait):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestTasS3kWaterSkimLifetime,TestTasS3kMemoryLimitations,TestHCZWaterSkimHandler,TestHczWaterRewindStaticAdapters,TestAiz2BossEndSequenceObjects,TestObjectManagerFixedSstInitialization' \
  "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

Completed 2026-10-26 19:33:21 UTC: **105 tests, 0 assertion failures, 1 setup error, 0 skips**, Maven 1:35 execution. All original red assertions and both restore-forward tests passed. `bossSequenceSetupDoesNotClearNativeReference` failed before its assertion because the new fixture directly constructed `AizEndBossInstance` without injected services. The fixture now uses `ObjectManager.createDynamicObject`; no runtime repair was changed in response. The final affected-class run also strengthens the self case to use ordinary allocation/reuse, tests a real monitor as the other occupant, and adds restore-before-initialization coverage.

Final affected-class rerun (154-second queue wait):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestTasS3kMemoryLimitations' \
  "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

Completed 2026-10-26 19:37:33 UTC: **11 tests, 0 failures, 0 errors, 0 skips**, Maven 30.301 seconds. The other five classes had already passed all 95 cases on identical runtime code. They were not rerun after the fixture-only correction. Final bounded coverage is 106 distinct passing cases across those completed runs, not a full-suite result.

Rewind checks capture real object-manager slots and participant/latch adapters before retirement, after clearing, after reuse and before press; they restore and compare forward snapshots with `RewindSnapshotDiff`, without excluding fields. The ordinary button history test now allocates and initializes Knuckles through the real owner; it retains the consumed-UP and next-dispatch unlock assertions.

## Scope and handoff limits

- Water checks cover native-width near-range equality, real activation/jump exit, an ordinary nonpersistent range control, actual allocator reuse, active/inactive P1 and mixed P2 departure, no reactivation on return, and bounded restore-forward. They do not execute next-act boss overwrite, quick death or the later locked-camera route.
- Button checks cover real actor initialization/retirement, self reuse at 6, a real monitor occupant at 9, ordinary live actor activation, unarmed/empty distinctions, X/Y range edges, level versus boss-sequence reset, and bounded restore-forward. They do not reproduce the publication's unusual allocation history or demonstrate the entire bridge/camera route.
- Existing capture hooks remain on the original scenarios. Renamed IDs: `currentLimitationWaterSkimOwnerSurvivesParticipantExitAndRangeDeparture` becomes `waterSkimOwnerRetiresAfterParticipantExitAndRangeDeparture`; `currentLimitationSelfOccupiedSlotSixCannotActAsCutsceneKnuckles` becomes `selfOccupiedCutsceneSlotPressesButtonAfterRealKnucklesRetirement`. The latter now records the immediate successful press, and its ordinary actor control has its own `ordinaryInitializedKnucklesStillPressesInRange` hook. Parent owns reel selector reconciliation.
- Neither assertion pass nor a hook call proves rendered pixels: no new reel/pixel capture is claimed. Broader S3K domain/combined smoke, central classification, changelog/discrepancy/index/scope changes and integration remain parent-owned. No broad suite or push is performed by this worker.

## Local delivery

Source/tests are committed as `7e0f20ef29b69a4eee499f89871ea66d1acab86e` with repository hooks and all seven policy trailers. Central documentation trailers are justified as parent-owned. The exact final model coauthor block was checked with `git interpret-trailers --parse` before committing. This record is a separate documentation commit so it can name the immutable tested source SHA.

The first documentation commit attempt was stopped by the resource-policy hook for literal home-directory paths; those paths were normalized to `$HOME` and the hook rerun. Task-report relative links and `git diff --check` pass. Disassembly references were read in the pinned `8b2cf3c4178f` source; the citation tool returned no errors for the edited source/report paths, but marked nine pre-existing/multilabel source references unverifiable (one recognized verified reference). This is not a claim that the tool verified every prose citation. No category runner was launched and no category diagnostics need acknowledgment. All owned Maven sessions completed; there are no live test jobs and no push was made. Parent retains the worktree/branch for integration and owns its later removal.
