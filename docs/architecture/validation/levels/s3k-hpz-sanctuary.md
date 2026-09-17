# S3K Hidden Palace sanctuary coverage matrix

Game / slot: S3K `S3K_SPECIAL_STAGE_ARENA`, engine zone `$17` act index 1, ROM `$1701`
(`HPZS_*` screen events, `HPZMini_Sprites`). Entry: `SSEntryFlash_GoSS` giant ring with seven
Chaos Emeralds in an S&K level (`sonic3k.asm:128417`); exit back to the origin level.
Implementation predates this matrix (`feature/ai-s3k-super-emeralds`, routing recovery
`56583f2dba`); this file records current bindings and gaps found during the HPZ bring-up.
Status: partial. Nothing below certifies the sanctuary.

| Obligation + spot | Contract / oracle | Test binding | Implementation | Result | Gap / action |
| --- | --- | --- | --- | --- | --- |
| ENTRY: `$1701` resources, bounds, camera, no title card | Custom `HPZ_RESOURCES`, `SpecialStage_Results` camera `$15A0,$240` | `TestSonic3kNonlinearHpzProfile#sanctuaryDescriptorSelectsRom1701Resources`, `TestSonic3kLevelLoading#superEmeraldSanctuaryLoadsRom1701LayoutBoundsAndObjectSet` | implemented | pass (HPZ branch) | Width/donor breadth not run |
| OBJECT: controller, Master Emerald, pedestals, teleporter | `Obj_HPZSSEntryControl`, `Obj_HPZMasterEmerald`, `Obj_HPZSuperEmerald` (states 1 and 2 selectable, `loc_907A8`) | `TestHpzSanctuaryObjects` (26), `TestS3kHpzSanctuaryHeadless` (4 on `$17`) | implemented; state-1 selectability corrected `365ac485b` | pass | Knuckles and width breadth open |
| PRESENT: `AnPal_HPZ`, `AniPLC_HPZ` | OffsAnPal / Offs_AniFunc entry 47 | `TestS3kHpzPatternAnimation` (`$17` row), `TestHpzZoneRuntimeStatePaletteCycle#masterEmeraldDelayHoldsTheCycleFor8000Passes` | implemented `362771221` | pass | Native sanctuary pixels not compared |
| REWIND: controller graph | Identity-table relink of controller children | `TestS3kHpzGraphRewind#sanctuaryGraphRestoresFreshAndRelinksEveryControllerOwnedChild` | implemented | pass | Ceremony-phase forward replay open |
| LOAD: results reveal behind a Super Emerald stage | `SpecialStage_Results` $1701 rebuild and `Pal_FromWhite`; `Obj_SpecialStage_Results` routines `$E`/`$10`/`$12` (pan to `$320`, converging and expanding star rings, `ObjDat2_2E984` Hyper message, `loc_2E9D8`); `loc_2EAA6` state-1 indicator gate; level-select return rebuilds the hub without replaying the reveal | `TestS3kSpecialStageResultsReveal` (frame-exact routine timeline incl. native ss_14 timeline, 16), `TestS3kSanctuaryResultsBackdropHeadless` (backdrop, palette fade, pedestal FAC0, stars, FAC1 release, hub return, 3), `TestHpzSanctuaryObjects` star-ring cases | implemented `feature/ai-hpz-sanctuary-reveal` | pass | Results are not rewindable: the rebuild is a `LEVEL_LOAD` rewind boundary (isolation asserted). Native ss_14 (see table below) matches timing, fade steps and Master Emerald rotation onset, and is visually consistent for gems, flicker and stars; results exit fades to white in the engine but to black in the ROM (shared GameLoop exit, deferred); Knuckles message frames unit-tested; Tails, Knuckles palette and HYPERk word art neither tested nor captured |

### Native comparison: results reveal (2026-09-17)

Native: complete-run `s3k-sonic-tails-complete-emeralds` ss_14 (7th Super Emerald, stage 5,
50+ rings), `native/probe-hyper-reveal` (movie frames 292900-295460, every frame CSV, PNG every
2) plus `native/probe-reveal-flicker` (294400-294409, every frame). Loop pass u = native
`Level_frame_counter - 6210` (no lag frames inside the results loop). Engine: branch
`feature/ai-hpz-sanctuary-reveal`, `TestS3kSpecialStageResultsReveal#timelineMatchesTheNativeSeventhSuperEmeraldStage`,
`TestS3kSanctuaryResultsBackdropHeadless`, capture `raw-30-hyper-reveal` (stage 0).

| Event | Native u | Engine u | Result |
| --- | --- | --- | --- |
| Results over $1701, camera ($14B0,$240) | 1 (movie 293059) | 1 | match |
| Fade whitening (line 4 colour 1 = $EEE) | 361 | 361 | match |
| `Pal_FromWhite` steps | 363,366,...,381 (7) | 363-381 (7), `Pal_fade_delay2` = 2 | match |
| Camera Y leaves $240 / reaches $2A0 / $320 | 1042 / 1137 / 1265 | 1042 / 1137 / 1265 | match |
| Camera X leaves $14B0 / reaches $15A0 | 1521 / 1760 | 1521 / 1760 | match |
| Master Emerald rotation first write ($8C0/$680), then $AC0, $CE0/$880 | 1938, 1948, 1958 | message arrival +1, +11, +21 (stage 0 run) | match |
| Results loop exit | 2243 | 2243 | match |
| Pedestal gems, flicker frame 7, grey cleared pedestal, star rings | PNGs 294362-294562, 294400/294401, 295014 | capture 1637-1837, 1736/1737, 2048 | visually consistent at the sampled frames (engine capture is stage 0, native stage 5): gems on palette lines 0-1 keep their colours, frame-7 flicker silhouettes look alike; not a pixel diff |
| Exit presentation | fade to black during level load lag | fade to white (shared `GameLoop.exitResultsScreen`) | mismatch, deferred |

