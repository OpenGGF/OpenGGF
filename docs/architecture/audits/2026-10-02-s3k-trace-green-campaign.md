# S3K trace green campaign — 2026-10-02

## Scope

Goal: make the Sonic, Tails and Sonic+Tails S3K traces green without regressing
S1, S2 or other S3K traces. Knuckles work already underway was finished, but no
new Knuckles frontiers were opened. Comparator weakening, fixture-derived
gameplay state and fitted delays were out of bounds. Integration base: develop
`67c850fc51`. Milestones were committed and pushed to `develop` as they became
ready.

The verified reference S3K ROM (CRC32 `63522553`) differs from the original root
image (CRC32 `0C06AA82`) only at region byte `0x2001F0`. Both produced identical
chain failures, so the byte difference is not a factor.

Per-fix trace numbers, with commands, are in the
[frontier log](../../status/trace-frontier-log.md#2026-10-03--s3k-trace-green-campaign-summary).
The ROM pitfalls are in the S3K object skill's `rom-pitfalls.md` and in
[implementation pitfalls](../implementation-pitfalls.md).

## Delivered fixes

| Commit | ROM owner | Change |
|---|---|---|
| `5566b8db17` | `loc_6468` | Bonus title release runs the initial `Process_Sprites` before `LevelLoop`. The first input row is gameplay, not setup. |
| `56afa227ad`, `d9fd107179` | `Get_LevelSizeStart/loc_1BF74` | The level-start camera saturates only at zero and the maximum bound, never the minimum. MHZ1 keeps its separate `$160` focus. Positioned captures and replays use `LevelCameraInitialization.recenterPositionedEntry`. |
| `ee8565ab6e` | — | Continuous runs keep the production load camera. Declared-position replays still resnap. |
| `7e256310e4` | `Obj_WaitOffscreen/loc_85B02`, `loc_871C2`, `loc_87218` | Monkey Dude's body keeps running after its first release, and script changes clear the raw animation timer. |
| `fe852448a9` | `SSEntryFlash_Main` | The ring deletion test reads the animation counter after it advances. |
| `b109c30b67` | `loc_852E`/`loc_8588` | The special-stage exit uses the 60-VBlank `Demo_timer`, and the return re-enters the title resource wait. |
| `df0b6eaab2` | `SpecialStage_Results`/`loc_2E0C4` | Four KosM archives plus the ring-HUD Nemesis PLC must drain before the results owner is installed. |
| `56b7972f99` | `LoadLevelLoadBlock` | The loaded level keeps its resolved terrain sources. Re-resolving after Saved2 was consumed selected AIZ intro art (`$3A647C`, 7 modules) instead of the return art (`$3A944E`, 5 modules). |
| `8bf6486f60` | — | An interstitial span that production already submitted and claimed is verified per ordinal against its fingerprint. Cursors and jobs are unchanged. |
| `298167c96a` | `loc_2E3DA` | The perfect bonus depends on `Special_stage_rings_left == 0`, not on the emerald. |
| `8b947396f3` | `AIZ1BGE_Finish` | The fixed `$10/$10` X lock also pins the engine's smoothing targets. |
| `c60df46ed6` | `Player_Boundary_CheckBottom`, `loc_61076`, `word_610AE` | `Disable_death_plane` suppresses the kill only. The gumball exit child owns the exit, with range X `[-$100,$100)` and Y `[-$10,$30)`. |
| `bda587a99b` | `loc_26EEA` | The cup elevator runs its per-player solid check before testing for capture. |
| `7a85c01222` | `loc_2C3CA` | The rolling drum runs both participant updates before range deletion and does not release live native riders. |
| `3fa9c0a88a`, `c54cbfdf93` | `loc_26F26`, `loc_26FF4`, `sub_62800`, `loc_6278A` | Cup capture alone writes `object_control=$03`, which sets the animation bit. Later full-byte writers own the release. |
| `b38e8354d4` | `LevelLoop` (`sonic3k.asm:7908/7887`) | Held LEVEL iterations in the live loop still service the Kos queue tail, via `TraceSuppressedRowClosure`. |
| `b217fe6bd8` | `sub_875B4`, `sub_8756A`, `sub_87592` | Monkey Dude has five linked children with 16.16 positions and throws one coconut from the hand's previous position. |
| `6a3131036c` | `AIZTree_FallOff` | Tree release writes literal radii 9/`$13` until `Tails_TouchFloor` restores them. |
| `36e73ddcc2` | — | Compared bonus interiors drive physical movie rows and close source ownership after the last published row. |
| `588999752d` | `loc_7289A`, `BossDefeated` | The LBZ miniboss fatal-hit dispatch installs `$3F` without decrementing it. |
| `aea1bb0206` | `Obj_LevelResultsWait2`, `loc_2DD06` | Carried results wait for their twelve real children to retire, then publish. The title initializes on the following dispatch. |
| `d427fdd9ba` | — | Test chains close their playback session on every exit path. |
| `030f66cb40` | `Obj_TitleCardWait/loc_2D810` | Retained title owners reset counters through the native child-movement gate, without restarting `$2E`. |
| `9d48e7ddbd` | `Level/loc_6310`, `loc_6468`, `loc_64DC` | Live zone loads use the fresh title/terrain boundary with an immediate palette. Only title owners that implement `FreshLevelTitleBoundaryPublication` (S3K) take this path. S1/S2 also took it (the headless recording driver since `8fa54b5787`, the live loop since `9d48e7ddbd`), which carried the old ring count and moved held players during the title card. Both now gate it on that capability. |
| `7324b9c50e` | `LBZ1BGE_DoTransition`, `Change_Act2Sizes` | LBZ1→2 keeps its inherited bounds until the title owner runs the size workers in their creation pass. |

## Rejected approaches

- **Monkey Dude, timer fix alone.** This exposed an earlier hurt at row 419
  (14,717 errors) because the repeated visibility gate had dropped 66 body
  dispatches. Both owners needed fixing.
- **Removing every compatibility player bootstrap in continuous runs.** S2 gained
  26 bootstrap history-Y mismatches (`$290` vs `$28F`). Only the camera snap was
  skipped.
- **Repositioning run-chain metadata.** This brought back the 17 AIZ camera
  errors, and a three-context variant added an MHZ row-0 camera error.
- **Gumball: keeping the pit-death `requestExit`.** It fired at Y `$32F` and froze
  the player two ticks before the native child admitted them at `$358`.
- **Compared bonus interior, delaying source closure until destination
  gameplay.** This assigned title production to the exhausted bonus owner.
  Closure uses the existing `levelLoopRowCount` predicate instead.
- **Cup elevator: reviving the cutscene-release flag or forcing the jump
  mapping.** Neither exists in the ROM. Write-site ownership covers both cases.
- **LBZ miniboss: a fitted signpost delay.** The one-row lead begins at the fatal
  hit and carries through unchanged.
- **Carried results: changing the short-path reset from 39 to 40.** This was
  unproven and was reverted.
- **Retained title: reusing the native gate and also restarting `stateTimer`.**
  This added 8 queue errors at 22331.
- **MGZ results: moving the miniboss slot into the signpost-flow replacement
  API.** The four pose errors remained, and the first error moved earlier to
  13903, with 8,477 errors in total.
- **LBZ camera: holding the request targets only.** The first error moved
  22258→22334, but total errors rose to 4,477. The size-worker creation pass was
  also needed.
- **SOZ authored route: restoring the old `PostTitleCardDestination`.** That
  restores incorrect cadence. The controller-only movie was re-authored instead
  (24,052 inputs).

## Native observations

These come from read-only BizHawk/GPGX captures of the committed BK2 movies.
They serve as evidence only, never as engine input.

- **Sonic+Tails first special-stage return.** Results resources drain at row
  7411, and the owner initializes at 7413. The return title owner appears at 8687,
  its archives drain by 8696 and its children are created at 8697. Nemesis
  finishes at 8767 and terrain KosM at 8793. `LoadLevelLoadBlock2` runs
  `Kos_Decomp` from 8793 to 8812, then tile-row fill from 8813 to 8816. LEVEL with
  title timer 22 appears at 8817 (a lag row), and the first level iteration is at
  8818.
- **AIZ→HCZ title parents** (`Obj_TitleCardInit/loc_2D6C8`):
  - RedAct `0D6F28`→`0500`
  - S3KZone `15C3A2`→`0510`
  - Num1 `0D6D84`→`053D`
  - HCZ letters `39BEDA`→`054D`
- **MGZ results.** Results slot 8 retires its last child at trace row 14383 and
  publishes and mutates at 14384. EndSignControl slot 7 restores control at
  14385. The engine has these slots in reverse order (results 7, control 21).
  This accounts for the four added MGZ errors after `aea1bb0206`.
- **LBZ size workers.** At row 22331, slots 35, 36 and 37 already hold
  accumulators `$4000`, `$4000` and `$8000` in their creation pass.
- **MGZ ring tally.** The tally differs by one dispatch (58 vs 59 rings, carried
  over from row 9260). Once the synthetic retirement tail was removed, this
  became visible as the slice increase from 10,046 to 10,634 errors.

## Open items

- **Sonic+Tails.** Segment 6 has 189 errors, first row 3319 `sidekick_x`. Segment
  8 has 13,254 errors, first row 1583 `sidekick_x`. HCZ segment 9 compares fully
  but misses the giant-ring exit.
- **Tails.** Segments 4 and 6 have 1,783 and 67,150 errors, first `x` at rows 4930
  and 101.
- **LBZ1.** 4,585 errors; the first is an unwanted hurt at row 23533.
- **MGZ results/control slot order** needs observing the engine's earlier SST
  population.
- **Coverage gap.** No main-Sonic S3K fixture has an empty sidekick list.
  Sonic-led parity means Sonic+Tails.
- **Inherited failures at `67c850fc51`:**
  - `TestRemainingRewindTailInventory` (expects 1,315/1,072, gets 1,316/1,073)
  - the LRZ flame `TOUCH_PROFILE_HOOK_WITHOUT_PROFILE` guard
  - S2 special stages 2, 5, 6 and 7 (row-0 dynamic-art errors)
  - ten LRZ/SSZ cold-route methods
- **Validation not yet done.** Combined change-based validation against
  `67c850fc51` (about 2,964 ordinary classes plus guards) has not completed. The
  only ordinary run, at `6e6f13036f`, timed out after 23,721 cases.
