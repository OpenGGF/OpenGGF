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
| LOAD: results reveal behind a Super Emerald stage | `SpecialStage_Results` $1701 rebuild and `Pal_FromWhite`; `Obj_SpecialStage_Results` routines `$E`/`$10`/`$12` (pan to `$320`, converging and expanding star rings, `ObjDat2_2E984` Hyper message, `loc_2E9D8`); `loc_2EAA6` state-1 indicator gate; level-select return rebuilds the hub without replaying the reveal | `TestS3kSpecialStageResultsReveal` (frame-exact routine timeline, 14), `TestS3kSanctuaryResultsBackdropHeadless` (backdrop, palette fade, pedestal FAC0, stars, FAC1 release, hub return, 3), `TestHpzSanctuaryObjects` star-ring cases | implemented `feature/ai-hpz-sanctuary-reveal` | pass | Results are not rewindable: the rebuild is a `LEVEL_LOAD` rewind boundary (isolation asserted). `Pal_fade_delay2` entering at 2 is inferred, not measured; native pixels and a real (non-debug-completed) Blue Sphere clear not compared; Knuckles message frames unit-tested; Tails, Knuckles palette and HYPERk word art neither tested nor captured |
