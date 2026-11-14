# Sonic CD: hosting the non-level programs

Date: 2026-11-12. Lane K0. A section of the [re-host plan](2026-11-12-sonic-cd-rehost.md).
Labels are in `docs/scddisasm/src` at `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`.

## Who owns the flow

`CD Initial Program/IPX.asm` is the flow: `.GameLoop` runs the title, dispatches its exit code
through `.Scenes`, and `NewGame`/`RunLevel`/`RunBossLevel` chain the stages. `program/CdProgramFlow`
is that chain as game logic, with the IPX save variables. Program owners report how their program
returned; `program/CdSceneCoordinator` (a module game service) asks the flow for the next program and
turns it into an engine request. It never writes the mode: `GameLoop` commits every mode change.

| Program returns | Coordinator call | Engine request `GameLoop` commits |
| --- | --- | --- |
| Title exit code | `titleExited(code)` | `TitleScreenAction`: stage `ONE_PLAYER` (+ zone/act), secret `LEVEL_SELECT`, other `OPTIONS` |
| Stage-select row | `stageSelected(index)` | `LevelSelectProvider` zone/act, then the host's level load |
| Stage (results, warp, death) | `stageEnded(CdStageResult, LevelManager)` | `requestZoneAndAct` or `requestGameOverExit(TITLE_SCREEN)` |

A program this build does not host is a typed `CdProgramRequest.NotHosted(program, caller, then)`: the
coordinator records it (`notHosted()`, `route()`) and continues where IPX continues once that program
returns. Nothing falls back silently.

## Which mode hosts what

| Native family | Host mode and provider | State (at `6c86bed2e8`) |
| --- | --- | --- |
| Stages, warp destinations | `LEVEL` | hosted (K3b) |
| Title (`TITLEM`, `TITLES`) | `TITLE_SCREEN`, `title/CdTitleScreen` | hosted |
| Stage-select secret (`STSEL_`) | `LEVEL_SELECT`, `title/CdStageSelectScreen` | hosted |
| Special stage (`SPECSTAGE`) | `SPECIAL_STAGE`/`SPECIAL_STAGE_RESULTS`, `special/CdSpecialStageProvider` | hosted from stages; not from Time Attack or the sound test |
| Endings, Thank You (`BADEND`, `GOODEND`, `THANKYOU`) | `ENDING_CUTSCENE`, `ending/CdEndingProvider` | endings hosted; Thank You not hosted (IPX runs it only after Visual Mode's good ending) |
| Time Attack, D.A. Garden, Visual Mode and its FMVs, sound test and its secrets, best staff times, backup RAM manager, demos, opening FMV, Comin' Soon | `OPTIONS` through `NativeTitleModeProvider` and a `CdProgramHost` screen | hosted: Time Attack, sound test and its secrets, best staff times, opening FMV. Not hosted: D.A. Garden, Visual Mode, pencil test, backup RAM manager, demos, Comin' Soon |
| Time-warp cutscene (`WARP`) | `OPTIONS`, `warp/CdWarpCutsceneHost` | hosted |

No new `GameMode` was needed. `OPTIONS` is the existing native-program host: a `NativeMenuScreen`
opens from a title action, runs with input and presentation, and exits to `TITLE_SCREEN`, `LEVEL`
or `COMPETITION`. Every program in its row is entered from the title (directly or through IPX after
a title exit) and returns to `.GameLoop` or starts a stage, which is exactly that lifecycle. The
`NATIVE_PROGRAM` value the design proposed is therefore not added, and `@ModApi` is unchanged.

Known limits of the reused hosts: (1) resolved: a stage-select row naming a program now returns to
the title as native does (`game.internal.TitleReturningLevelSelect`). (2) `LEVEL` launches from `OPTIONS`
go through `openNativeMenuLaunch`, which pins a cartridge source; Time Attack's stage starts need a
disc-aware launch before they use it. (3) Entering `TITLE_SCREEN`/`LEVEL_SELECT` asks `RomManager` for
the configured cartridge ROM and publishes it to audio; resolved for Sonic CD, whose title and audio
read the disc source. (4) Host fades wrap the programs' own native fades.

## Program lanes

Each lane owns one directory, writes its screen there and implements `program/CdProgramHost` (or the
named provider). The lead lists the host in `program/CdProgramHosts` and the provider on the module at
integration; lanes edit neither, nor the flow. Program screens draw with `program/CdProgramScreenRenderer`
(tile store, two planes, line scroll, sprite list, priority) and read disc data through
`program/CdProgramData`, extending `tools/soniccd/program_catalogue.py` for their own blobs.

| Lane | Owns | Plugs into |
| --- | --- | --- |
| Title secrets: sound test, SS8 credits, easter eggs, best times | `title/secrets/` | `CdProgramHost` for `SNDTEST`, `STAFFTIMES`; its exits per `SoundTest .Exits` |
| Special stage | `special/` | `SpecialStageProvider`; replaces `NotHosted(SPECSTAGE)` in `RunLevel.SpecialStage`, `SpecStage1Demo`, `TimeAttack_SS`, `SoundTest_SpecStg8`, returning `timeStones`/`specStageLost` |
| Time Attack | `timeattack/` | `CdProgramHost` for `TIMEATK`; stage rows from `.Selections` and `StageSels` |
| D.A. Garden, Visual Mode, FMV | `dagarden/`, `fmv/` | `CdProgramHost` for `DAGARDEN`, `VISMODE`, `OPENING`, `PENCILTEST` |
| Backup RAM | `backupram/` | `CdProgramHost` for `BURAMMGR`; `CdProgramFlow` save variables through `ReadSaveData`/`WriteSaveData` |
| Endings | `ending/` | `EndingProvider` for `BADEND`/`GOODEND`/`THANKYOU`, `flow.endingId()` |
| Time travel (wave 1) | `scene/`, time posts | reports `CdStageResult.warp(...)`; hosts `WARP` |
| Results, game over (objects) | their object dirs | report `CdStageResult` through `stageEnded` |

## Checks

`TestCdProgramFlow` (IPX decisions), `TestCdTitlePicture` (title against the native USA capture) and
`TestCdRetailRouteReplay` (three fixture movies reach their stages through the title and secret).
