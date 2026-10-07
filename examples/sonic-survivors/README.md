# Sonic Survivors — a bounce-driven survivors roguelike for Sonic 2

A code mod for OpenGGF's JVM build. Select **Sonic or Tails** as the main character on the
engine's launch screen, then start Sonic 2. Runs are **solo**: configured sidekicks are disabled.
Every zone on the route becomes a walled **arena** cut from its own act's terrain, and badniks pour in from both sides and from above.
Your character starts with nothing but their jump: **bounce on badniks to keep going**. Each rebound off an
enemy chains a **combo** that multiplies stomp damage and ring drops, ten eligible bounces charge an eight-second **Fever** burst, and defeated badniks drop **rings**, which are both
your **health** and **experience**. Level up to pick new weapons, moves and passives,
survive the two- or five-minute clock, beat the zone's **boss**, choose the next act, and push on to
the Death Egg. Rings and Chaos Emeralds won along the way upgrade both characters between runs.

The stock act can never be finished: the signpost, capsule, boss triggers and every stock object
are gone, the camera and Sonic's level boundary are held to the arena, and the arena's only exit
is the clear screen after its boss. A physical ceiling at the arena camera's upper limit
keeps air jumps and powered rebounds away from terrain above the play area.

## A run

| Stage | Zone | Acts | Badniks |
| --- | --- | --- | --- |
| 1 | Emerald Hill | 1, 2 | Buzzer (shoots), Coconuts (hops, throws), Masher (leaps) |
| 2 | Chemical Plant | 1, 2 | Spiny (shoots), Grabber |
| 3 | Aquatic Ruin | 1, 2 | Whisp swarms, Chop Chop (charges) |
| 4 | Casino Night | 1, 2 | Crawl (armoured: double hitpoints) |
| 5 | Hill Top | 1, 2 | Spiker (spiked top: use weapons), Sol (fireballs) |
| 6 | Mystic Cave | 1, 2 | Crawlton (lunges), Flasher (harmful while lit) |
| 7 | Oil Ocean | 1, 2 | Octus (hops, shoots), Aquis (shoots) |
| 8 | Metropolis | 1, 2, 3 | Shellcracker, Slicer (throws pincers), Asteron (bursts into spikes) |
| 9 | Wing Fortress | 1 | Clucker (turret), Balkiry (jets) |
| 10 | Death Egg | 1 | Silver Sonic, then Eggman on foot |

Sky Chase has no ground to fight on and is skipped.

1. **Title.** Under the Sonic 2 emblem, left/right picks the starting zone: any zone a previous
   run has reached. Starting further along grants two catch-up level-ups per skipped zone (at
   most ten). Start opens that zone's first act in **camp**.
2. **Camp.** Spend banked rings in the shop, then START RUN. A run begins with **30 rings**.
   The **MODE** row cycles with Enter/Start between **2 MINUTES**, **5 MINUTES** and **ENDLESS**.
   Five-minute and endless modes unlock permanently after your first boss clear; existing
   profiles with a cleared boss already qualify. The choice is saved and fixed for the run.
   Five-minute mode adds more encounters with a gentler pressure ramp and a tougher boss,
   keeping 10-second ring formations. Endless stays in the starting arena,
   shows elapsed time, and keeps spawning waves without a boss or route transition;
   enemy health, batch sizes and elite frequency keep escalating. Enemies, enemy shots, reward
   pickups and player projectiles have no fixed population ceiling; arena objects do not consume
   the original game's limited object slots. Uncollected
   reward rings and monitors blink and expire after one minute to keep object slots available;
   Emeralds never expire. Escape/Back retires and banks held rings
   too; dying uses the usual game-over payout. Death Egg always stays a direct boss finale.
3. **Survive.** The clock counts down **2:00** or **5:00** (upwards in endless). Badniks spawn just off-screen on both sides and
   drop in from above, faster and tougher as the clock runs down and further along the route.
   Every 10 seconds a formation of five floating rings appears somewhere in the arena away
   from Sonic. Red chevrons at the screen edges point at badniks approaching from off-screen. Initially every 30
   seconds an **elite** (150% size, ELITE label, gold health bar, six times the hitpoints) arrives; this interval shortens with pressure down to eight seconds. It drops a monitor.
4. **Boss.** At 0:00 the zone champion arrives. Most zones use Eggman's complete vehicle
   assembled from its ROM mapping components, hovering within a jump of the player's ground, sweeping the arena, dropping volleys of the zone's
   projectiles (Mystic Cave drops rocks from the roof) and swooping at the player after a
   **DIVE!** warning; Hill Top's tank rolls along the ground and charges instead. Aquatic Ruin has a giant **Whisp Queen** with
   spreading volleys, Wing Fortress a **Balkiry Ace** with committed strafing runs, and Oil
   Ocean an **Oil Sentinel** core with orbiting escorts. Metropolis act 3 uses an armoured
   core ringed by Asterons. Bosses enrage below half health. Stomps rebound the player
   with the stock boss bounce; weapons hit them too. Beating a boss destroys every badnik still
   standing (their rings are the prize).
5. **Clear.** The first time you beat each boss from Emerald Hill to Oil Ocean it drops that
   zone's **Chaos Emerald** (kept forever). The clear screen shows the zone's results and offers
   the next zone's acts: **act 1** normal, **act 2** hard (+20% badnik hitpoints, +50% rings),
   Metropolis **act 3** brutal (+40%, +100%). Or **retire** and bank everything, including the
   rings you are holding. Rings carry from zone to zone.
6. **Death Egg.** Silver Sonic walks at Sonic, crouches, and spin-dashes wall to wall (harmful
   while spinning; enraged, he leaps and sends shockwaves along the floor). When he falls,
   Eggman bolts on foot. Catch him: three stomps win the run.
7. **Game over** banks half the rings collected, 25 per zone cleared and one per five badniks;
   a win adds 300. TRY AGAIN returns to camp at the run's starting zone.

Press **Escape** (or the gamepad Back button) at any time to bank the run and return to the title.

## Encounters and pressure

Every 30 seconds brings a new encounter: **Crossfire**, **Air Raid**, then **Stampede**,
repeating with increasing pressure. Air Raids favour the zone's flyers; Stampedes favour
its ground badniks (zones without one group use their available enemies). At 15 seconds a
**SURGE IN 3...** warning announces seven seconds of faster, larger waves starting at 18.
The last five seconds are **REGROUP**: ordinary reinforcements stop so you can clean up and
collect rewards. Scheduled elites can still arrive. The HUD shows the wave, phase and progress.
Boss fights use mixed, slower reinforcements without the surge cycle.

Enemy health continues growing with elapsed combat time, route tier and chosen act. Spawn
batches grow too, even once spawn intervals reach their minimum. Five-minute mode uses 75%
of the normal time-pressure ramp and doubles the timed boss's health. Death Egg remains its
direct finale. Endless keeps escalating instead of reaching a population/health plateau.

## Rings are health

Any badnik, projectile or boss hit costs a **ring toll**: 10 rings or one twelfth of your
held rings rounded up, whichever is greater, before Armor. Armor reduces that toll by 8%
per level; the red emerald adds 15% reduction, with a five-ring minimum. The HUD shows the
current cost. Large banks still help, but cannot trivialise an entire run. Hits give one
second of protection, with no knockback; half the toll scatters as rings you can grab back. These **lost rings** ignore
all magnets, award no new experience or collected-ring credit, blink after four seconds and
expire after five seconds; reward rings remain magnetic. A shield absorbs a hit instead.
A hit with **no rings** is lethal, unless a revive remains (Revival shop item, Oil Ocean's
emerald), which restores 20 rings. Rings flash red on the HUD while one more hit would empty
them. As in stock Sonic 2, rings cannot be collected during the first half-second after a hit.
Ring pickup chimes are grouped: the first pickup after half a second without a chime is
immediately audible, while rapid collections chime roughly every ten rings, at most five
times per second. This includes lost rings and Super Ring monitors; every ring still grants
its full health and, for reward rings, experience. A single large pickup makes at most one chime.

Nearby reward rings consolidate after their initial scatter. Rings of the same tier within
48 pixels combine when their total reaches the next tier; their **full combined value**
stays in the surviving pickup. Larger piles use larger coloured rings:

| Colour | Stored ring value |
| --- | --- |
| Yellow (normal ROM ring) | 1–4 |
| Cyan | 5–24 |
| Purple | 25–124 |
| Red | 125+ |

Red piles can keep combining. Consolidation pauses with menus and leaves rings already
flying toward you alone. Hit-spilled rings, monitors and emeralds never join a pile.
Merged rewards keep the youngest constituent's remaining lifetime, rather than refreshing
old piles indefinitely. Collection still grants the stored ring value and applies the usual
experience bonuses; one large pickup makes at most one chime.

## Bouncing and the combo

A stomp (jumping or rolling into a badnik) deals **2 x (1 + Spring Heels)** damage, times
Power and the shop, times the **combo multiplier**. Every rebound off an enemy while airborne
adds one to the combo: +25% per bounce after the first, capped at 6x (8x with Aquatic Ruin's
emerald). Sonic always rebounds upward from a stomp, so a well-aimed chain can stay off the
ground for a long time. Landing ends the combo (Combo Keeper adds a grace period). A chain of
five or more pays half its length in bonus rings (rounded down, at most 20) when it ends.
Combo damage retains its full multiplier; enemy ring rewards gain only 10% of the multiplier's
extra portion, preventing damage, healing and experience from all snowballing together.

**Fever** charges over ten bounces, across chains. It grants eight seconds of invincibility,
then needs fifteen seconds of active play to recover before charging again. Bounces while
invincible, Super, or recharging do not charge or refresh it. The HUD shows charge, remaining
Fever time and recovery. Card menus freeze these timers; rewind restores them. Invincibility
monitors keep their own stock duration.

## Level-ups

Reward rings collected are experience. Each level-up pauses play and deals **three cards** (four with
the Talent shop item), favouring upgrades you already own. Until you own a weapon, one card is
always a weapon. **Reroll** deals again (one per zone, plus the shop and Chemical Plant's
emerald). Up/down chooses and **Enter** (keypad Enter also works) or **gamepad Start** confirms.
Jump never selects a card. Camp, cards and results own Enter/Start, so confirming does not
also toggle the engine pause. During ordinary play the usual pause controls still work.

| Upgrade | Kind | Max | Effect |
| --- | --- | --- | --- |
| Shockwave | on bounce | 5 | A blast around the bounce (radius 48-96, damage 3-7); also pops enemy shots |
| Spark Burst | on bounce | 5 | 3-8 sparks fly out from the bounce |
| Chain Zap | on bounce | 5 | Lightning jumps between 2-6 nearby badniks |
| Homing Rings | on bounce | 5 | 1-3 rings launch and seek the nearest badnik |
| Orbit Rings | auto | 5 | 2-6 rings circle Sonic, hitting whatever they touch |
| Sonic Boom | auto | 5 | A piercing wave fires ahead every 2.2-1.0 s (both ways from level 4); pops shots |
| Flicky Squad | auto | 5 | Freed Flickies dive at badniks every 3.2-1.8 s |
| Homing Dash | move | 3 | Jump in the air to dash at the nearest badnik or the boss |
| Air Jump | move | 3 | Jump again in the air, 1-3 times per jump |
| Ground Pound | move | 3 | Press down in the air to slam; the landing quakes the floor |
| Power | passive | 5 | +25% damage per level |
| Magnet | passive | 5 | Rings fly to Sonic from further away |
| Armor | passive | 5 | Hit toll -8% per level (minimum 5 rings) |
| Haste | passive | 5 | Weapon cooldowns -10% per level |
| Greed | passive | 5 | Extra ring drops and +20% experience per level |
| Spring Heels | passive | 5 | Higher rebounds and +1 stomp damage per level |
| Combo Keeper | passive | 3 | The combo survives landing for 0.3-1 s |
| Barrier | passive | 3 | A shield returns every 30, 25, then 20 s |

## Monitors (dropped by elites)

Super Ring (+10 rings), Shield, Invincibility, Speed Shoes, **Eggman** (here a bomb: every
badnik on screen takes a heavy hit) and the **?** monitor (every reward ring on the field flies to the player; lost rings stay where they fell).

## Between runs

**Ring bank shop** (in camp):

| Item | Levels | Effect | Cost |
| --- | --- | --- | --- |
| Power Up | 5 | +10% damage | 60, 120, ... |
| Ring Start | 5 | +10 starting rings | 40, 80, ... |
| Reroll | 3 | +1 reroll each zone | 100, 200, 300 |
| Magnet | 3 | +16 px ring pull | 50, 100, 150 |
| Growth | 3 | +10% experience | 80, 160, 240 |
| Revival | 2 | +1 revive per run | 300, 600 |
| Talent | 1 | Four cards per level-up | 500 |

**Chaos Emeralds** (one per boss, Emerald Hill to Oil Ocean, kept forever):

| Emerald | From | Power on every later run |
| --- | --- | --- |
| Green | Emerald Hill | A free level-up at the start |
| Yellow | Chemical Plant | +1 reroll each zone |
| Blue | Aquatic Ruin | Combo multiplier cap +2x |
| Pink | Casino Night | +1 ring from every badnik |
| Red | Hill Top | Hit toll -15% |
| Grey | Mystic Cave | A shield at every zone start |
| Cyan | Oil Ocean | +1 revive per run |
| All seven | | +25% damage. Super Sonic is disabled pending a future Survivors implementation. |

Progress is saved in `saves/sonic-survivors/profile.txt` under the engine's save root.

## Build and run

```bash
python3 examples/sonic-survivors/build.py        # compile and package target/sonic-survivors/sonic-survivors.jar
python3 examples/sonic-survivors/build.py --run  # ... then launch the engine with the mod (JVM build)
```

Install the jar through the Mod Manager like any trusted code mod. Native builds cannot load
code mods. The mod plays at 16:9 and hides the stock level select. Version 0.4.0 requires the engine
build containing `LevelInputOverlay`; rebuild this checkout when updating an older engine. Existing
profile saves remain compatible.

Film it headlessly with the gameplay capture tool's `--mod` option, for example:

```bash
java -XstartOnFirstThread -cp "target/classes:$(cat target/survivors-classpath.txt)" \
  com.openggf.tools.GameplayCaptureTool --game s2 --zone 0 --act 1 --width 400 \
  --mod target/sonic-survivors/sonic-survivors.jar --input run.txt --out-dir /tmp/survivors
```

(`-XstartOnFirstThread` is for macOS only.) Development properties: `sonic-survivors.seed`
pins the run's random stream, `sonic-survivors.survival=N` overrides timed survival clocks to N
seconds and `sonic-survivors.bossHp=N` sets every boss's hitpoints.

## How it is built

- `SurvivorsMod` registers the objects and a game patch over the Sonic 2 module that loads route
  acts as `ArenaLevel` (stock terrain and art, no stock objects or rings, one `Stage`
  controller), starts Sonic in the arena (`ArenaZones`), runs only the stock zone events'
  level setup (`initLevel`, which installs runtime state such as Hill Top's scroll), removes
  water, replaces the game-over cards and wraps the title (`SurvivorsTitle`).
- `Stages` holds the route, each act's arena bounds (found with the
  `FloorSegmentSurveyProbe` and checked in captures), the zone line-ups and the boss art.
- `Stage` is the arena controller: camera and boundary, phases, waves, weapons and moves (in
  growable projectile arrays and a bounded cosmetic-effect pool so mod-object rewind capture restores them), the combo, level-up cards
  and menus. `Hud` draws everything with a code-drawn font.
- `Enemy` (one class, seven AI archetypes over `Species`), `Boss`, `Shot` and `Pickup` are mod
  objects drawn with the ROM art the zone already loads; frames were picked with the
  `ObjectArtContactSheetProbe`. `Guard` turns hits into ring tolls.
- `RunState` (the run, captured for rewind) and `Profile` (the save) live in the module.

`TestSonicSurvivors` packages the mod through `ggfmod` and drives it headlessly: camp, the
arena walls, every route act, stomps and the combo, level-ups and all weapons at once, the boss,
emerald and route choice, the Death Egg finale, death and banking, the shop, ring tolls and a
rewind round trip. Coverage includes both leaders across all 19 acts, the ARZ ceiling, forced solo
teams, jump rejection/Enter confirmation through the host game loop, and lost-ring provenance
through rewind. Design notes and rejected approaches are in
[the design record](../../docs/architecture/designs/2026-10-06-sonic-survivors.md).
