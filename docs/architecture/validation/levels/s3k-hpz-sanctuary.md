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
| LOAD: results return reveal | `Obj_SpecialStage_Results` routine `$E`, HPZ backdrop, pan to `$320`, Hyper message | `TestS3kSpecialStageResultsReveal` (message suppression and word only) | missing | — | Tracked in `docs/status/s3k-known-bugs.md` ("Super Emerald Results: Sanctuary Reveal Not Implemented") |
