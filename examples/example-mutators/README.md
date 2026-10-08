# Mutator Lab

A maintained JVM mod with one common settings screen and eleven independently
configurable effects for Sonic 1, Sonic 2 and Sonic 3 & Knuckles. Play uses native
ROM levels, sprites, music and sound effects. Every mutator starts off; the jar
contains creator code and declarations, never commercial assets.

The expanded catalogue is being verified against all three games. The
[expansion plan](../../docs/architecture/plans/2026-10-08-mutator-lab-expansion.md)
records current evidence and remaining gates; registration or a successful load
alone does not certify every route, pose or character.

## Build and play

Use Java 21 and configure your original ROM images in the game hub:

```bash
python3 examples/example-mutators/build.py --run
```

The builder queues Maven compilation, compiles the maintained example source and
runs `ggfmod package`. Its jar is
`target/example-mutators/example-mutators.jar`. The shared `baseGame: any`
catalogue installs once; choose **Sonic 1**, **Sonic 2** or **Sonic 3 & Knuckles**
from the hub with native Sonic as leader. Configure the Lab title, then start
Green Hill, Emerald Hill or Angel Island respectively. Lab Start opens a fresh
challenge rather than the Sonic 3 & Knuckles stock save-slot screen. Native
progression and supported native teammates remain available. For packaging only, omit `--run`.

Ordinary JVM launches can install that jar in the Mod Manager: enable it, trust
its exact hash and restart the executable. Code installation/replacement remains
boot-scoped. Prepared settings can be changed without restarting the executable.
Compiled creator code is unavailable in the engine's standard native image; see the
[trust guide](../../docs/modding/concepts/trust.md).

## Catalogue and boundaries

| Mutator | Options | Applies on |
| --- | --- | --- |
| Gravity | Ordinary dry Sonic fall acceleration, 25–200%, 5% steps | Resume |
| Ringfall Manipulator | 10–100% of the native spill; optional 1–32 ring ceiling | Resume |
| Big Head | 100–200% head size; leader or supported team members | Resume |
| Stealth | Leader/all supported team; hide attached effects checkbox | Resume |
| No Powerups | Separate checkbox for every semantic monitor type | Full load/restart |
| No Checkpoints | Death ignores the checkpoint bank and restarts from the native act start | Full load/restart |
| No Rings | Deny main-level ring placement, collection and rewards | Full load/restart |
| Game Speed | 25–400%; optional audio follows speed | Resume |
| No Special Stages | Refuse new emerald-stage entry | Resume |
| No Bonus Stages | Refuse new Sonic 3 & Knuckles bonus-stage entry | Resume |
| Violent Explosions | Native badnik rebound at 150–300%; vertical speed cap | Resume |

Each toggle and option declares its own lifecycle. The screen displays requested
values, admitted values and pending boundaries separately. Resume applies LIVE
edits; a full restart, death reload or qualifying full stage return applies LOAD
edits. Loading a preview, restoring a checkpoint or a seamless handoff does not.
The host also supports LAUNCH-scoped authoring, which requires a new game session;
these eleven examples do not invent a launch-only setting.

No Powerups, No Checkpoints and No Rings leave the existing world intact while an
edit is pending. Native placement order, stable layout indices and object slots
remain owners. Removing a monitor is decided before creation, rather than deleting
it while someone stands on it. Ringfall changes only how many lost rings are
created: hurt still loses the entire carried inventory, and the native ceiling is
32. No Rings dominates spills and main-level monitor/checkpoint/stage-return grants.
Special and bonus interiors retain their native puzzle ring rules; a No Rings
main-level return restores zero rings.

Stage switches govern new entry. A player already captured by a native flash or
results transition retains an engine-owned, rewindable entry permit. Turning a
switch on during that transition cannot abandon the player. Sonic 1 and Sonic 2
have no bonus stages, so that row is unavailable there; unsupported monitor types
are labelled per game instead of pretending every subtype exists everywhere.

Game Speed schedules complete native ticks in ordinary level play, the three
native special stages and Sonic 3 & Knuckles bonus gameplay, with a captured
fractional remainder. Native stage startup/setup, results and exits keep their
existing cadence; bonus setup finishes before modified gameplay ticks begin.
At 25%, three presentation frames have no native tick and the fourth has one;
at 150%, frames alternate one and two ticks. Inputs remain held across skipped
frames, and a press is consumed once. Pause, settings and focus still respond
without advancing native timers, art queues or rewind. The separate canonical
movie/trace tick remains unchanged. Audio can retain its presentation rate or
follow the selected speed; following speed can change pitch.

Violent Explosions amplifies the badnik defeat's resolved native vertical rebound,
then applies the selected cap. Horizontal velocity, ground/air flags and ordinary
hazard damage remain native. This example does not create a new radial blast,
modify bosses or apply the rebound twice.

## Presentation and support limits

The Lab decorates each native game. Effects are unavailable while cross-game
features are enabled; use native ROM art with donors off. Gravity changes ordinary
dry Sonic air acceleration only: jump impulse/release, hurt, water, death, flight,
glide and scripted movement retain their native owners. The identity value is
100%; enabling the default therefore changes no acceleration.

Big Head enlarges reviewed anatomical head masks from the actual admitted ROM
sprite pieces. Feet, torso, collision bounds and camera stay native. Mask identity
is tied to the actual art, mappings and DPLC structure. Curled ball poses,
unreviewed poses, powered forms, donor/custom art and unreviewed characters retain
their native presentation. A checkbox never implies every pose or character has
been reviewed. Stealth takes precedence over a hidden head.

Stealth filters body, appendage and optional attached effects after native sprite
admission. It preserves targeting, collision, rings, audio and the world/HUD.
Native team members remain independent. Deposited skid puffs and water splashes
are world effects; hiding an attached shield or spindash effect must not remove
those world effects. Turning Stealth off restores presentation without rewriting
movement state.

The title card and rows animate on the native 320×224 grid. Long catalogues and
monitor options scroll while keeping the focused row visible. Unsupported rows
are dimmed with an explanation; an already saved unavailable toggle can still be
turned off. Menus use each game's native sound IDs and priority arbitration.
Actual graphics and PCM verification are recorded in the expansion plan; a queued
cue, synthetic screenshot or nonzero audio amplitude alone is not audible proof.

## Controls and persistence

| Context | Keyboard defaults | Controller |
| --- | --- | --- |
| Title/configuration | Arrows choose/change; Enter select; Esc back/hub | D-pad; displayed confirm/back buttons |
| Native play | Left/Right move; Space jump | Native mapped movement and A/B/C |
| Open configuration | Enter (Pause), or Backspace (Start) | Start |
| Apply live edits | Choose Resume play | Same |
| Apply load edits | Choose Restart from act start | Same |
| Leave | Choose Return to game hub | Same |

How to play quotes your actual bindings. Esc inside gameplay configuration backs
out of options and keeps play held. Simultaneous cancel/confirm cancels. Native
fade commands stay visibly queued until the fade completes. Resume releases user
pause, while a window-focus pause remains an independent owner.

Requested settings are saved per game under
`<openggf.saveRoot>/mutators/mutator-preferences-{s1,s2,s3k}.json`. The development
builder defaults to `target/example-mutators/player-settings`; `--save-root`
selects another owned writable root. Existing schema-1 Gravity/Stealth choices
remain valid; newly added mutators default off. Rewind captures admitted policies,
entry permits and pacing, and never rewrites player preferences. The native Slots
bonus stage remains non-rewindable. Historical values
that differ from the saved choice require an explicit edit to branch future events.

Start, Resume, Restart and Return save before admitting or leaving. A failed save
holds the draft and visible error for retry. Secure directory I/O and atomic
replacement are required; unsafe or unsupported paths report an error rather than
publishing bytes. Preparation faults cross the creator fault boundary, quarantine
the owner and recover through the host's session fault path.

Ordinary user recordings remain unavailable in prepared modified sessions. Stock
recording replay disables external content. The capture recipe is an input-only
external driver; it is not a new modified recording format.

## Troubleshooting and authoring

- Configure the game's original recognized ROM in the hub. No fallback art,
  sound or physics is supplied; do not create ROM links just to satisfy a recipe.
- Check JVM mode, enabled state and exact-hash trust if the Lab is absent. Rebuild
  the package after API drift.
- Enable the effect and choose its displayed boundary. A pending LOAD edit is not
  applied by Resume; a 100% slider can deliberately be an identity setting.
- No Rings can make ring-dependent paths, including the final Sonic 3 & Knuckles
  flight route, unsatisfiable. Disable it and perform the displayed full restart
  before entering a route that requires a ring budget.
- Repair a failed settings path and retry the same action. Closing the Engine
  window remains available if persistence cannot succeed.

Read the [build-along guide](../../docs/modding/guides/mutators.md) and one class per
configurable example under `src/main/java/mutators`. The shared typed catalogue
uses concrete native-game decorators, with game-specific registrations kept out
of the `any` transaction. Installation refresh, hot code replacement, modified
recordings and universal character/pose coverage remain separate work.

## Maintained capture inputs

Use `capture.script` for Sonic 1/2 and `capture-s3k.script` for Sonic 3 &
Knuckles. The S3K input script and its window walkthrough wait thirty seconds
after entry/restart for the native intro; this is an input margin, not an engine
readiness gate. Inspect actual control release before using the following play
frames. The canonical recipes remain one native tick per frame and cannot
certify the interactive Game Speed setting.
