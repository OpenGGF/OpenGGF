# Mutator Lab

A maintained JVM mod with one common settings screen and eighteen independently
configurable effects for Sonic 1, Sonic 2 and Sonic 3 & Knuckles. Play uses native
ROM levels, sprites, music and sound effects. Every mutator starts off; the jar
contains creator code and declarations, never commercial assets.

The [native-game coverage matrix](../../docs/architecture/validation/levels/mutator-lab-native-games.md)
defines the tested routes and rewind controls. The
[expansion plan](../../docs/architecture/plans/2026-10-08-mutator-lab-expansion.md)
records source-attributed verification; registration or a successful load alone
does not certify every route, pose or character.

## Build and play

Use Java 25 and configure your original ROM images in the game hub:

```bash
python3 examples/example-mutators/build.py --run
```

The builder uses the local Maven wrapper for engine compilation and classpath
preparation, compiles the maintained example source and runs `ggfmod package`.
Known build-only preparation holds this worktree's build/test lock without
reserving a shared test slot. Its jar is
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
| Ringfall Manipulator | 10–100% of the native spill or, with Drop full inventory, of every held ring; optional 1–32 ring ceiling | Resume |
| Big Head | 100–200% head size, optional ring scaling; independent Player, Sidekicks, Badniks and Bosses | Resume |
| Stealth | Leader/all supported team; hide attached effects checkbox | Resume |
| No Powerups | Separate checkbox for every semantic monitor type | Full load/restart |
| No Checkpoints | Death ignores the checkpoint bank and restarts from the native act start | Full load/restart |
| No Rings | Deny main-level ring placement, collection and rewards | Full load/restart |
| Game Speed | 25–400%; optional audio follows speed | Resume |
| No Special Stages | Refuse new emerald-stage entry | Resume |
| No Bonus Stages | Refuse new Sonic 3 & Knuckles bonus-stage entry | Resume |
| Violent Explosions | Native badnik rebound at 150–300%; vertical speed cap | Resume |
| Homing Attack | Homing or Instagib; Sonic, Tails and Knuckles as leader | Resume |
| Beta Flight | Knuckles as leader in Sonic 3 & Knuckles flies with the unused flight from the Dec 1993 Sonic 3 CES cartridge instead of gliding | Resume |
| Start with Rings | 0–999 rings (default 50) granted at every act start and life, after a 0–300 s delay (default 0) | Resume; next act or life |
| Start with Time | 1–570 s already on the level timer (default 300, 5:00) at act starts and restarts from the act start | Resume; next act or life |
| Start with Score | 0–999,990 points in steps of 10 (default 0) | New game |
| Start with Lives | 1–99 lives (default 3) | New game |
| Hungry for Rings | Grace 0–300 s (30), lose 1–100 rings (1) every 1–60 s (1); at or under 0–999 rings (0) for 1–60 s (5) you die | Resume |

Each toggle and option declares its own lifecycle. The screen displays requested
values, admitted values and pending boundaries separately. Resume applies LIVE
edits; a full restart, death reload or qualifying full stage return applies LOAD
edits. Loading a preview, restoring a checkpoint or a seamless handoff does not.
The host also supports LAUNCH-scoped authoring, which requires a new game session:
Start with Score and Start with Lives are launch-only, because score and lives belong to
the whole game session. Edit them from the Lab title before Start; an edit during play
waits for the next new game.

No Powerups, No Checkpoints and No Rings leave the existing world intact while an
edit is pending. Native placement order, stable layout indices and object slots
remain owners. Removing a monitor is decided before creation, rather than deleting
it while someone stands on it. Ringfall changes only how many lost rings are
created: hurt still loses the entire carried inventory, and the native ceiling is
32. **Drop full inventory** (default off, so saved choices keep the ceiling) lifts
that ceiling: the percentage applies to every held ring, bounded by the native
999-ring counter, and the hard cap still limits each spill when it is on. The
first 32 rings keep native Obj37 slots and allocation order; the rest are real
recoverable rings that run as slotless continuations, so they never take object
slots from the level. Rings past the native 48-ring fan rotate between earlier
directions instead of stacking. Touch still collects one ring per frame, and
Sonic 3 & Knuckles' native 63-entry collision-response list still limits how
many spilled objects each frame can touch.
No Rings dominates spills and main-level monitor/checkpoint/stage-return grants.
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

Beta Flight replaces Knuckles' glide with the flight left in the Sonic 3 Winter CES
cartridge (Dec 22, 1993). That build's unused Knuckles object is a copy of Sonic's
with Tails-style flight added: the air press launches him sideways at no less than
normal top speed, he sinks slowly, and each new press flaps him upward. There is no
time limit, sound or tired pose. Left and right only turn him around: the cartridge's
steering writes the ground speed, so he keeps his launch speed until he lands or hits
a wall. The engine reproduces that as found. The **Fixed Steering** option, which is
not in the cartridge, applies the same steering to his air speed instead, so left and
right speed him up and slow him down up to his normal top speed. The **Super Jump**
option gives Super and Hyper Knuckles the cartridge object's higher jump ($700 where
S&K uses $600); underwater he still jumps at $300. He uses the S&K glide pose, the same frame
the cartridge's animation names. Objects that react to a gliding Knuckles also react
while he sinks in beta flight, because both use the same state value.

Homing Attack turns the leader's jump re-press into a strike at the best target in
range, if there is one, and the native double-jump move otherwise: Sonic's
insta-shield or shield moves in Sonic 3 & Knuckles, Tails' flight or Knuckles' glide.
Without a target or a native move, Sonic dashes forward instead. Targets must be on screen. They rank by a
hidden progress priority, then whether they are ahead of the player, then kind (boss,
badnik, breakable wall, grab point, breakable) and then distance; the line to them must
clear solid terrain. The player stays in the native roll, so native touch, solid and
defeat code decides each hit. A strike counts as a spindash where the native code
distinguishes ground from air: breakable walls, Angel Island's rocks and the Death
Egg Act 2 spike cars.
Hits refresh the air action, so attacks chain, and bosses bounce the player off.
A reticle marks the target a re-press would take, launches lag the camera like
the Sonic 3 & Knuckles fire-shield dash, and a faint blue glow and echoes trail the strike.
Knuckles-only walls and rocks remain his. Multi-part bosses carry a hidden priority
on the part that must be struck, and parts that only hurt or pop are never targets.
Instagib builds a bow of friction heat in front of the player, sheds embers and
hue-shifted afterimages, and makes the leader invulnerable for the strike and 32
ticks after it. The next hit on any boss is its defeating one; the native defeat
sequence still runs. Each defeated enemy bursts into 32 shards carrying the strike's
momentum. Shards are gameplay: they live in rewind history and defeat any badnik they
pass through, which can chain. Bosses never take shard hits. Each shard is drawn with one of the enemy's own
on-screen tiles, so parts repeat; a world that never rendered draws sparks instead.
The heat, embers, afterimages and reticle are presentation only.

## Starting values and ring hunger

The four starting mutators write through each game's native counter owners at
real boundaries; enabled at their defaults, Start with Score (0) and Start with
Lives (3) are identity settings.

- **Start with Rings** grants its rings on the first active frame of every act
  start (including the Sonic 3 & Knuckles results screen's move to act 2) and
  every life restart, checkpoint restarts included. With a delay, the grant
  arrives after that many seconds of active play and joins any rings already
  collected. The count is written directly rather than collected, so reaching
  100 or 200 this way gives **no extra life**: the thresholds it reaches are
  marked as already paid in the native extra-life bank, and later pickups still
  award the thresholds it did not reach. No Rings dominates the grant.
  Turning the mutator off during the delay cancels that grant.
- **Start with Time** adds its seconds to the level timer at a fresh act start
  and at a life restarted from the act start. A checkpoint restart keeps the
  native saved checkpoint time, which already includes the seed if the
  checkpoint was reached under it. The 570-second maximum is 9:30, which leaves
  the native 10:00 time over 30 seconds away. The default, 5:00, visibly changes
  the clock while leaving more time than an ordinary act needs, so the setting
  alone rarely forces a time over.
- **Start with Score** and **Start with Lives** apply once, at the first act
  start of a new game session. The score is capped at 999,990 (the six-digit HUD
  counter) in native units of ten; lives at 99 (the two-digit counter, and Sonic
  3 & Knuckles' own clamp). A native continue keeps its own score/lives reset.
- Stage returns, checkpoint restores and seamless act handoffs restore their own
  saved counters and grant nothing.

**Hungry for Rings** starts a grace countdown at every act start and life
restart. When it ends, the chosen number of rings leaves the native ring counter
every period (a drain is not a hurt: nothing scatters and the extra-life bank is
kept). Holding the starving count or fewer for the chosen time kills the player
through the native kill routine, as a crush does, so shields and invincibility do
not save you; debug mode does. Collecting rings above the starving count cancels
the countdown. The clocks advance only during active main-level play: pause,
title cards, special and bonus stages, results, signposts and capsules (any time
the native level timer is stopped), scripted or object control, and boss fights
(the native boss flag) hold them. The boss pause is the same gate the motion mutators use
(the native boss-fight and end-of-level flags); Sonic 2 never clears its boss id,
so in Sonic 2 the clocks stay held from the boss arena until the act ends. A checkpoint restart, a death and an act change
all begin a fresh grace; returning from a special or bonus stage resumes where it
stopped. The state is rewind history. No Rings makes Hungry for Rings fatal by
design.

A 16-pixel dial beside the ring counter shows the hunger. It is drawn with the
RINGS label's own palette ink inside a ring of the HUD digits' light ink with a
dark outer edge, so it reads over dark and light scenery: a yellow pie empties
clockwise during the grace, an empty dial means rings are draining, and a red pie (the red of the HUD's
warning line, as Sonic 2's RINGS warning uses) counts down to starvation,
blinking on the native warning cadence for its last quarter. The dial follows
the HUD in both widescreen anchors and is hidden in debug mode and bonus-stage
HUD layouts.

## Presentation and support limits

The Lab decorates each native game. Effects are unavailable while cross-game
features are enabled; use native ROM art with donors off. Gravity changes ordinary
dry Sonic air acceleration only: jump impulse/release, hurt, water, death, flight,
glide and scripted movement retain their native owners. The identity value is
100%; enabling the default therefore changes no acceleration.

With **Scale with rings** on (default off), the head is normal size at zero rings
and grows linearly to the Head size setting at 100 rings, shrinking again as rings
are lost. No Rings therefore keeps a normal head, and Stealth still hides it.

Big Head's **Afflicts:** group has four independent checkboxes: **Player** is
checked by default; **Sidekicks**, **Badniks** and **Bosses** are off. All four
fit on the same options view. Turn off Player for an enemy-only effect, or stack
any combination. Older saved “All team” settings migrate to Player + Sidekicks;
explicit new checkbox choices take precedence. Settings apply on Resume.

The head is an irregular, pose-specific lasso of actual admitted ROM pixels,
scaled about a reviewed neck anchor. Foreground gloves and shoes remain native
size. Feet, torso, collision and camera stay native. Native Sonic is supported
in all three games, Tails in Sonic 2/3&K, Knuckles in 3&K, and the separate 3&K
Super Sonic bank. Badniks and bosses use explicit object ownership and reviewed
ROM-bank masks; vehicles, weapons and projectiles retain native size. Their
ring-scaled size follows the main player's rings; sidekicks use their own count.

Curled ball poses, heavily obscured heads and unknown/custom art retain native
presentation. Source pixels do not reveal a head hidden behind a glove; masks
do not reconstruct it. Some boss/badnik art remains unreviewed and stays native.
A checkbox does not certify every object or transformed bank. Stealth still
hides the selected player's head without hiding enemies.

Stealth filters body, appendage and optional attached effects after native sprite
admission. It preserves targeting, collision, rings, audio and the world/HUD.
Native team members remain independent. Deposited skid puffs and water splashes
are world effects; hiding an attached shield or spindash effect must not remove
those world effects. Turning Stealth off restores presentation without rewriting
movement state.

The title card and rows animate on the native 320×224 grid. Long catalogues and
monitor options scroll while keeping the focused row visible, and backing out of
a page returns to the row that opened it. Each row names the choice that applies
its edit: Start before play, then Resume or a restart/death reload. Unsupported
rows are dimmed with their reason; an already saved unavailable toggle still shows
On and can be turned off. Choosing Restart or Return to game hub holds native play
frozen behind its fade-out. Menus use each game's native sound IDs and priority arbitration.
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

Holding Left or Right on a long slider speeds up after about half a second, then
moves ten (and, on very long sliders, a hundred or a thousand) steps at a time;
single presses always move one step. The 570-second time slider sweeps end to end
in about five seconds.

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

`GameplayCaptureTool` and `MutatorGameplayCaptureTool` enable Lab mutators for one
capture with a repeatable `--mutator <id>[:<option>=<value>,...]` flag (it needs
`--mod`). The requests are laid over the saved preferences, admitted at the
capture's launch boundary like Start, and never saved; an unknown id, option or
out-of-range value fails before filming, and a mutator the launch did not admit
fails after boot. For example, a mid-act demo on 85 rings, 4:30, 25,000 points and
seven lives:

```bash
... GameplayCaptureTool --game s2 --zone ehz --act 1 --mod target/example-mutators/example-mutators.jar \
  --mutator start-rings:rings=85 --mutator start-time:seconds=270 \
  --mutator start-score:score=25000 --mutator start-lives:lives=7 \
  --mutator hungry-for-rings:grace=10,loss=1,period=1,threshold=0,starve=5 --out-dir <task>/demo
```

Option ids are the Lab's: `start-rings` (`rings`, `delay`), `start-time`
(`seconds`), `start-score` (`score`), `start-lives` (`lives`) and
`hungry-for-rings` (`grace`, `loss`, `period`, `threshold`, `starve`); every other
mutator's ids are in its class under `src/main/java/mutators`.

The development launcher opens this package's declared Sonic 2 base game. The
Sonic 1 and Sonic 3 & Knuckles window recipes first finish that intro, return
to the master hub with Escape, and select their native game there. Read the
actual title and game subtitle before interpreting the following screenshots;
an action filename does not establish which game was launched.

Use `capture.script` for Sonic 1/2 and `capture-s3k.script` for Sonic 3 &
Knuckles. The S3K input script and its window walkthrough wait thirty seconds
after entry/restart for the native intro; this is an input margin, not an engine
readiness gate. Inspect actual control release before using the following play
frames. The canonical recipes remain one native tick per frame and cannot
certify the interactive Game Speed setting.
