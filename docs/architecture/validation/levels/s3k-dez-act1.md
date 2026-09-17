# S3K Death Egg Zone act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_DEATH_EGG_1`, engine zone `$0B` act index 0,
ROM `Current_zone_and_act = $B00`, SKL object set. **Not Sonic 2's Death Egg**: the
`TestDEZ*`/`TestS2Dez*` classes and the `*dez-boss-fixes*` documents are Sonic 2.
Owning plan: [S3K DEZ bring-up](../../plans/2026-09-17-s3k-dez-bring-up.md).
Status: not started. Nothing below certifies the act.

LevelSizes (sonic3k.asm:38119): x `0`-`$6000`, y `0`-`$B20`. Start location `$30,$9AC`
(measured: the engine's cold `$B00` load places Sonic at centre `$30,$9AC`).
Level art: `levartptrs $36,$36,$20` (PLC `$36`, palette `$20`,
`ArtKosM_DEZ_Primary`/`ArtKosM_DEZ1_Secondary`, sonic3k.asm:199455). Music
`Sonic3kMusic.DEZ1`. `Obj_LevelIntro_PlayerRun` runs at cold entry
(`SpawnLevelMainSprites` `loc_6986`; already implemented).
Placements: 365 objects, 278 rings, no gravity writer (`$58/$59/$5B` are act 2 only).

Incoming: SSZ `$A01` → `$B00` (SSZ campaign owns the request). Outgoing: seamless
`$B00` → `$B01` through `DEZ1_BackgroundEvent` `loc_593EC`.

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`):
widths 320/400/512/640/800; off×{Sonic,Tails,Knuckles}, S1×Sonic, S2×{Sonic,Tails};
teams solo, Sonic+Tails, S1 Sonic+Sonic, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.
Knuckles is **level-select only** for this act (user decision 2026-09-17): he must load,
play and invert correctly, but owes no cold chain and no trace frontier.

## Five claims

| Claim | State |
| --- | --- |
| Implemented | Not started (level load, music, intro run, slope-angle rule and shared objects only) |
| Cold-reachable | Not started |
| Rewind-verified | Not started |
| Native behaviour matched | Not started; replay frontier measured at `035e48a58`, see the row below |
| Visually matched | Not started; `raw-00-baseline-before-work/b00-act1` is the "before" capture |

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: `$B00` resources, bounds, object set | LevelSizes `$6000`x`$B20`, `levartptrs $36/$36/$20`, SKL set | — | — | present before this campaign | unrun | Slice 0 records it; no assertion yet |
| ENTRY: title card, intro run | `Obj_LevelIntro_PlayerRun` `loc_6986` | — | — | implemented (shared) | unrun | Slice 6 |
| PRESENT: background scroll | `DEZ1_BackgroundInit` clears `Camera_X/Y_pos_BG_copy`; `PlainDeformation` never rewrites them, so both BG scroll words stay 0 | 320 + one wide | `TestS3kDezScrollHeadless` | missing (`SwScrlS3kDefault` gives camera/4) | unrun | Slice 1 |
| PRESENT: `AnPal_DEZ1` channel A | counter `Palette_cycle_counters+$0A` reload `$F`, index `+$04` step 8 limit `$30`, `AnPal_PalDEZ1` ($349C, $30 bytes) → palette line 4 colours 12-15 | 320 | `TestS3kDezPaletteCycling` | missing | unrun | Slice 1 |
| PRESENT: shared AnPal channels B and C | B: `Palette_cycle_counter1` reload 4, `counter0` step 4 limit `$30`, `AnPal_PalDEZ12_1` ($3444) → line 3 colours 13-14. C: `counters+$08` reload `$13`, `counters+$02` step `$A` limit `$28`, `AnPal_PalDEZ12_2` ($3474) → line 3 colours 8-12 | 320 | `TestS3kDezPaletteCycling` | missing | unrun | Slice 1 |
| PRESENT: `AniPLC_DEZ` 8 scripts | `AniPLC_DEZ` ($28AEE), durations `0,1,3,-1,4,4,1,0`, script 7 = 132 frames; generic `AnimateTiles_DoAniPLC`, no gate | 320 | `TestS3kDezAnimatedTiles` | missing | unrun | Slice 1 |
| EVENT: `DEZ1_ScreenEvent` chunk `$BD` | `Events_fg_4` → `movea.w $14(a3),a1; move.b #$BD,$6E(a1)` = FG layout row 5, column `$6E` | 320 | `TestS3kDezScreenEvents` | missing | unrun | Slice 1; production trigger is the miniboss (slice 6) |
| PLACEMENT: 365 act 1 objects | [inventory](../../research/s3k-zones/dez-object-inventory.md) | — | `TestS3kDezPlacementCensus` | placeholder baseline recorded | unrun | Slices 3-6 |
| BOSS: `$A6` miniboss | `word_7DDA4` range Y `$18C`-`$38C` X `$3400`-`$3780`; arena `$28C,$28C,$3680,$36C0`; 8 hits | — | `TestS3kDezMinibossHeadless` | missing | unrun | Slice 6 |
| ROUTE (Sonic + Tails cold): `$B00` entry → results | Complete-run BK2 from movie frame 468982 (segment directory `ssz`, `zone_id 11`) | native 320 | `TestS3kDezColdRoutes` | missing | unrun | Slice 6 |
| ROUTE (Tails cold) | `runs/s3k-tails-full-chain-all-emeralds` `ssz`, offset 444059 | native 320 | `TestS3kDezColdRoutes` | missing | unrun | Slice 6 |
| REWIND: entry, cycle counters, event routine words | Registry restore equals capture plus forward replay | — | slice 1 tests | missing | unrun | Slice 1 onwards |
| ORACLE: route timing | `runs/s3k-sonic-tails-complete-emeralds/ssz` (DEZ, `zone_id 11`, 40,049 rows, offset 468982; both acts and the handover) | — | `TestS3kSonicTailsSszSegmentTraceReplay` (expected red) | — | blocked: 7005 errors, first error frame 0 `camera_x` expected `0x0040` actual `0x0000` (`035e48a58`, `-Ptrace-replay-r7`) | Whole campaign |
| ORACLE: Tails route timing | `runs/s3k-tails-full-chain-all-emeralds/ssz` (act 1, 23,249 rows, offset 444059) | — | `TestS3kTailsFullChainSszSegmentTraceReplay` (expected red) | — | blocked: 1661 errors, first error frame 0 `camera_x` expected `0x0040` actual `0x0018` (`035e48a58`) | Whole campaign |

## Execution evidence

Worktree `.worktrees/ai-s3k-dez-bring-up`, ROM by absolute path, `maven_queue.py -Dmse=off`.
Frontier command (2026-09-17, `035e48a58`):
`python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=<abs>/s3k.gen -Ptrace-replay-r7 "-Dtest=TestS3kSonicTailsSszSegmentTraceReplay,TestS3kSonicTailsDez238SegmentTraceReplay,TestS3kTailsFullChainSszSegmentTraceReplay,TestS3kTailsFullChainSsz2SegmentTraceReplay,TestS3kTailsFullChainSsz3SegmentTraceReplay,TestS3kTailsFullChainDez238SegmentTraceReplay" -DfailIfNoSpecifiedTests=false test`
— 6 tests, 6 failures, 0 errors, **0 skipped** (the ROM path resolved).
