---
name: stageplay-authoring
description: Use when creating or revising OpenGGF Stageplay cutscenes, animatics, choreographed stories or staged promotional footage, including agent-authored directing without engine menus or desktop input.
---

# Stageplay authoring

Direct through `tools/stageplay/stageplay.py` from the engine checkout. It drives
the production compiler, simulator, route planner and renderer, prints one JSON
result on stdout and sends diagnostics to stderr. The `.stageplay` scene it
writes opens in both editors and plays in mods.

Read only what the question needs:

- [Stageplay from the terminal](../../../docs/guide/creating/stageplay-authoring.md):
  every command, flag, output, `ground` and `route` detail, and how preview,
  simulation and export work.
- [Reference](../../../docs/guide/creating/stageplay-reference.md): screenplay
  fields and defaults, actions, objects, camera, dialogue, voices, audio, route
  motion, both editors' commands and keys, limits.
- [Directing guide](../../../docs/guide/creating/stageplay-directing.md) and
  [troubleshooting](../../../docs/guide/creating/stageplay-troubleshooting.md):
  what each tool is for and what its messages mean.
- [How Day Off was made](../../../docs/guide/creating/stageplay-day-off.md) and
  `examples/stageplay-demo/`: working screenplays, including the approved
  *Day Off* film and the tutorial's `first-take.screenplay.json`.

## Workflow

1. Start from the requested story and length. Write short shots with a visible
   action and consequence in each beat.
2. Build once with `--build help`; rebuild after engine source changes. A run of
   an older jar does not validate current sources. Java 25 and FFmpeg are needed
   for films.
3. Discover existing ROM paths; never copy, rename or link ROMs. Use `catalog`
   for zones, start positions, music and character abilities, `level` for
   placement identities, and `ground` for floors (batch many columns in one
   query file and one JVM; never sweep per-Y JVMs).
4. Write a version-7 screenplay (it compiles to a version-8 scene). Only the
   current versions exist; there is no migration. Compile beside any relative
   assets, with a new output name for each take.
5. `simulate` with explicit ROM flags before rendering. Read actual centres,
   statuses (`ARRIVED`, `BLOCKED`), grounded state and `sample.world` events.
   `timeline` shows authored state only (Puppets are `UNRESOLVED`).
6. `render` with explicit ROM flags. Keep films, JSONL and stills in an external
   task directory, never in the repository. Commit only screenplays, canonical
   scenes, drivers and concise provenance.
7. Watch the film at full speed: entrances, reactions, speaking turns, camera
   moves, transitions, accessories (Tails' tails) and pixel stability through
   every move. Check FFprobe cadence, size and audio, and decode the whole file
   with FFmpeg `-xerror`. Record the source revision and commands. A valid codec
   or one good still is not a review.

For editor refinements, return to text with either editor's **Export
screenplay** or `decompile`; recompiling reproduces the scene exactly. Don't
hand-edit generated IDs or split intervals across shots.

## Directing rules

- Players are Puppets by default: drive them with actions (`MOVE_TO` with
  urgency, `STOP`, `JUMP` with a hold, `WAIT` with a finite timeout, abilities
  where the character's game has them). Never key a parabola, force a velocity
  or teleport to fit a beat; a destination is an attempt. Interior Puppet keys
  may change visibility only. Use Posers for deliberate hand choreography.
- Use `route` (the editors' **Plan route**) for paths. Only `VERIFIED` is
  certified, and only for that exact input; search failures are not proof of
  impossibility. A verified player route is saved as a frozen input track: the
  exact controller rows from the shot's first frame, replayed as recorded. A
  failed Walk is not retried at Run; plan again with `--travel RUN` or a larger
  `--frames` when the diagnosis points there. The editors' side panel shows a
  one-line result; F1 holds the full diagnosis.
  Player routes start at a shot's first frame.
- Floors from `ground`, **Place on floor** and the picker are terrain only:
  apply, then confirm the landing in simulation. `INCOMPLETE` or truncated
  answers prove nothing.
- Objects: Free runs the object's own behaviour; Puppet and Pose suspend the
  whole object, children and contacts included. Some bosses keep their arena
  position when Free; never invent per-object position adapters. Give objects
  whose picture needs their behaviour a short Free lead-in. Clears remove
  objects, never rings or players. Check `object-coverage` for what each
  object supports.
- Facing is logical Left/Right in screen terms. Never write `flipX`.
- Make every speaker identifiable on the first readable frame: a named
  speaker (player, object or offscreen), panel placement on the speaker's side,
  framing and reaction. Colour alone is not identification. Keep panels off the
  action and dialogue out from under a transition's covered midpoint.
- Every speaker has a built-in chip voice (defaults Sonic Soft Cool, Tails
  Workshop, Knuckles Red Rattle, Motobug Patrol Buzz, Buzz Bomber Wing Chime;
  fifteen presets). Override with root `voices`; `OFF` when a recorded `VOICE`
  cue should stand alone. Check `timeline` `speech` events for sync.
- Cue music explicitly in every shot that needs it; any music cue takes over
  level music for the whole scene. Sound numbers are each game's native request
  IDs (S2 Emerald Hill is `$82` = 130); `catalog` lists music names, titles and
  numbers plus each zone's `musicId`, and screenplay `ROM_MUSIC` accepts
  `"music":"EMERALD_HILL"` instead of `soundId`. There is no old engine-ID mode.
  A cue on a shot's entry frame plays as authored in every game. Seeking is
  silent; judge sound from playback from frame 0.
- Hold integer zoom when pixels must stay still; review fractional zoom and
  tall views in motion. A larger `--scale` cannot repair shimmer.

## Boundaries

- Every shot is a fresh native world; nothing carries across a cut. Authored
  spawns and isolated simulation do not prove a continuous route or ROM parity.
- Camera, shake and zoom are presentation only.
- Mod playback holds the game and resumes it unchanged; optional player
  handover requires the same character, game and level and a free player.
- Never open a session over the user's active game or editor world, and never
  automate input on the user's desktop. The launcher renders offscreen
  (surfaceless EGL on Linux) with offline audio; see
  [headless rendering](../headless-rendering/SKILL.md).
- Imported voices are bounded relative WAV/Ogg assets; make no licensing claims.

For an explicitly requested promo, use [promo-direction](../promo-direction/SKILL.md)
and [gameplay-highlights](../gameplay-highlights/SKILL.md). A scene request alone
does not imply publishing a reel or sending messages.

## Native two-human route programmes

`StageplayRoutePlanner.planNative(hosts, launch, prefix, goals, budget, cancelled,
progress)` submits an ordered goal programme to the existing bounded native-input
search and qualifies the resulting frozen rows with a fresh linear replay. It
keeps every prefix row verbatim. Use P1/P2 `PlayerIn`, `Settled`, attachment goals,
and `All` for joint goals. Never author competition traversal rows by hand or
extend the older `ReferencePathInputAuthor` search.

The experimental S2 adapter is `StageplayCompetitionRouteHosts`: production title
entry, the native menu Visit, two human pads, read-only object observations and
copied `competition.*` facts. It enables the title row only in the isolated
qualification module; `QUALIFIED_ZONES` remains empty. Its `menuPrefix(entry)`
generates the native 2x2 menu input prefix (0 EHZ, 1 MCZ, 2 CNZ, 3 special).
The prefix is a native replay hypothesis until BizHawk confirms the whole movie.
No native qualification follows from engine qualification alone.

`StageplayRouteTool --native-route <draft.route.json> --s2-rom <ROM> --out <new-stem>`
plans from the draft's frozen prefix and goal programme. Optional `--prefix-rows`,
`--nodes`, `--ticks`, and `--bk2 <new-file>` bound work/export. Use only an isolated
worker with `DISPLAY`/`WAYLAND_DISPLAY` unset and
`-Dopenggf.headless.gl=surfaceless-egl`. A `title` inlet names the production title
entry; rows include title/menu input. `.route.json` remains the launch/goals/hash
and qualification authority; `.script` keeps both pads' exact held states.
`fact-equals` goals have `key`/`value`; missing facts fail. Tests and native checks
replay the saved rows, never re-plan. Native power-on timing and visibility goals
still need independent confirmation; do not label an unconfirmed prefix cold
native evidence.

For a new S2 programme, `StageplayRouteTool --competition-entry <0..3>
--goals <goal-program.json> --s2-rom <ROM> --out <new-stem>` generates the menu
prefix and plans its continuation. A programme normally begins checks at
`startRow:1200`, after the generated prefix. `competition.paused` observes the
native pause owner. The semantic `input.menu` fact admits a searched Start tap
when a menu or native pause needs one; it never clears pause by writing state.
Competition checkpoints use the complete registry and previous input row inside
one stable act, and refuse fades, pending loads and other modes. Boundary legs
retire earlier checkpoints before continuing. Existing P1 guard semantics remain;
add explicit P2 conditions when the programme needs to protect P2.

`facts-equal` (`left`/`right`) compares copied owner metrics, including
`competition.zoneWins.p1` / `.p2`. For a continuation, retain the original rows:
its completed goal cursor is evaluated over the prefix before any new search.
`--replay-native-route FILE --s2-rom ROM --out NEW_DIRECTORY` replays only and
writes engine witnesses; `--publish NEW_STEM` saves that same frozen tape with
fresh engine evidence. CNZ `competition.flip.visible` requires resident ROM art
in a non-flat pose, a full cell in a view, and a matching framebuffer pixel.
The native programme entry is separate from the scene `--worker` protocol;
editor jobs must select the S2 host for a title inlet and preserve both pad lanes.
