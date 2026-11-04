# S3K destination player placement at bonus entry and return

Base: `origin/develop` at `c24e9fa6e`. Branch:
`bugfix/ai-trace-s3k-destination-player-spawn`. Follow-up 3 of the
[bonus-entry tail audit](2026-11-03-s3k-bonus-entry-tail.md).

**Result: an engine ordering defect, fixed.** Bonus-stage entries and returns
assembled their destination players at the load step. The ROM clears the slot
first, places the players when the title loop exits, and creates them just
before the stage starts. The engine already modelled this for zone and act
requests. Both bonus paths now open the same boundary.

## Native evidence (Mega fixture, recorded rows)

Let R be the row with the fade's last V-int.

| Load | R+1 | Title-loop exit | Player created |
| --- | --- | --- | --- |
| AIZ to Gumball | 4568: player x/y = 0, routine 0; camera stays at AIZ | 4638: x/y = `$0100/$0120`, routine 0; camera `$0060/$00C0` | 4653: routine 0 to 2 |
| Gumball to AIZ | 1300: x/y = 0, routine 0; camera stays at Gumball `$0060/$0240` | 1380: x/y = `$2778/$025C`; camera `$26D8/$01FC` | after the last recorded row |

The ROM owners are:

- `loc_60DE` clears `Object_RAM` (sonic3k.asm:7649-7651).
- `loc_6310` calls `Get_LevelSizeStart` after the title loop (7789-7792).
- `loc_6468` runs `SpawnLevelMainSprites` and the first `Process_Sprites`
  (7880-7915).

`FreshLevelTransitionBoundaryController` already implements these phases for
zone requests:

- the load zeroes the slots and keeps the source camera;
- `publishInitial` fires at terrain start;
- `complete` runs loc_6468's initial pass.

`GameLoopBonusStageSession` loaded with plain `loadZoneAndAct`, then placed the
players itself. The return calls `restoreReturnState`; the entry calls
`prepareBonusStageForTitleCard`. Neither opened the boundary.

## Change

- `FreshLevelBoundaryAccess.beginAfterSetup` opens the existing boundary
  after a caller's own placement. It holds the placed players and the source
  camera and rings, and keeps the camera that setup chose as the destination
  camera. This is an engine-internal bridge; `LevelManager` gains only
  package-private methods, so the `@ModApi` surface is unchanged.
- Both bonus paths open it when the title owner models the fresh title and
  terrain loop. This is the same `FreshLevelTitleBoundaryPublication` gate the
  zone-request path uses.
- A `BONUS_STAGE` title release completes the boundary in
  `PostTitleCardDestination`, because no LevelLoop owner exists there. The
  release keeps loc_6468's setup-only result.

## Witness

| Tail | Develop | After | Remaining first error |
| --- | ---: | ---: | --- |
| Gumball return (segment 1, compared on develop) | 16 | 12 | 1300 `queue.s3k_kos_direct.busy` |
| AIZ to Gumball entry (segment 0, withheld evidence `d8baf2675`) | 16 | 12 | 4568 queue |
| AIZ2 to Slots entry (segment 2, same evidence) | 16 | 12 | 2025 queue |

A temporary error dump shows which errors went and which remain:

- **Removed:** row 1300 `x`, `y`, `camera_x` and `camera_y`.
- **Remaining:** 12 queue fields on rows 1300 and 1380. These come from the
  title KosM and terrain piece starting one row early. That offset is the
  Level-setup lag V-int (`Clear_DisplayData` under `#$2700`), which the
  [load-time trial](2026-11-03-load-vint-trial.md) proved cycle-bound. It is
  not player placement.

Mega Slots' error list is byte-identical, 7947@232.

## Validation

Full `-Ptrace-replay`, `-Ptrace-replay-r7` and `-Ptrace-segments` runs were
made on develop `c24e9fa6e` and on `f49457530`:

| Profile | Develop | After |
| --- | --- | --- |
| r6 | 887 tests: 14F 0E 7S | identical |
| r7 | 119 tests: 81F 2E | identical except Mega segment 1 |
| seg | 70 tests: 46F 2E | identical |

Full chain messages and every trace and run-chain report were compared.
The only difference is Mega segment 1, 16 to 12 errors. Its new error list is
an exact subset of develop's, so nothing was added.

Focused checks pass: `TestFreshLevelBoundaryAfterSetup` (new: hold, publish,
complete, and no reopening), the bonus-session, title and PLC owners, and the
S3K keep-green classes. That is 138 tests with one failure.
`TestGameLoopFreshLevelHandoff.realFadeCallbackKeepsItsOwnerAndDefersTheFourTitleArchives`
fails identically on develop `c24e9fa6e` and is pre-existing.


## Combined-wave title-control follow-up

The combined wave7 and pending-bonus freeze repair reaches an ordinary route
that was previously stuck before entry. `TestS3kTitleCardHoldRoutes` then
finds the player unlocked during the bonus title terrain wait (entry step124).
The after-setup boundary captured its player before the title host locked it;
`publishInitial` restored that older engine control flag with the native
position. The boundary's snapshot restoration must retain the live control
lock, whose current owner may also release it before final assembly.

An attempted call-order correction captured a locked destination instead.
It fixed entry, but the later LEVEL return assembly restored that lock after
the title host released it (hold counter1). That experiment was discarded.
The accepted correction preserves `isControlLocked()` across this boundary's
`restoreRewindState` call only. Full rewind restoration and native player
values remain unchanged. The existing title owner still acquires/releases its
lock; an initial object pass may independently acquire a zone cutscene lock.

The boundary test now exercises a lock acquired after capture, retained at
initial publication, and released before final assembly. Its first strengthened
version encountered AIZ's legitimate plane-intro lock at the initial sprite
pass; the fixture now skips that independent intro owner to isolate the title
boundary. The actual bonus-entry/return route passes with the production
correction. Across the focused owner/guard/rewind/keep-green run and the final
two-case fixture-only rerun,1566 unique cases pass with no skips. The final
fixture run uses queued Maven `-Dmse=off -B
-Dtest=TestFreshLevelBoundaryAfterSetup test`, Java25, surfaceless EGL and the
six original ROM properties. The unchanged broader focused results come from
that same production correction. Combined three-profile validation is pending;
this is not a claim of trace parity or completed delivery.
