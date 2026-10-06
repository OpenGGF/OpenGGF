# Sonic Survivors — a bounce-driven survivors roguelike for Sonic 2

A code mod for OpenGGF's JVM build. Start Sonic 2 with **Sonic** as the main character (a CPU
Tails, if your team has one, fights alongside him). Every zone on the route becomes a walled
**arena** cut from its own act's terrain, and badniks pour in from both sides and from above.
Sonic starts with nothing but his jump: **bounce on badniks to keep going**. Each rebound off an
enemy chains a **combo** that multiplies stomp damage and ring drops, every tenth chained bounce
sets off **Fever** (the stock invincibility), and defeated badniks drop **rings**, which are both
Sonic's **health** and his **experience**. Level up to pick new weapons, moves and passives,
survive the two-minute clock, beat the zone's **Eggman**, choose the next act, and push on to
the Death Egg. Rings and Chaos Emeralds won along the way upgrade Sonic between runs.

The stock act can never be finished: the signpost, capsule, boss triggers and every stock object
are gone, the camera and Sonic's level boundary are held to the arena, and the arena's only exit
is the clear screen after its boss.

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
3. **Survive.** The clock counts down **2:00**. Badniks spawn just off-screen on both sides and
   drop in from above, faster and tougher as the clock runs down and further along the route.
   Red chevrons at the screen edges point at badniks approaching from off-screen. Every 30
   seconds an **elite** (gold health bar, six times the hitpoints) arrives; it drops a monitor.
4. **Boss.** At 0:00 Eggman arrives in the zone's own vehicle (ROM boss art for every zone),
   sweeping the arena, dropping volleys of the zone's projectiles (Mystic Cave drops rocks from
   the roof) and swooping at Sonic. He enrages below half health. Stomps rebound Sonic off him
   with the stock boss bounce; weapons hit him too. Beating him destroys every badnik still
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

## Rings are health

Any badnik, projectile or boss hit costs a **ring toll** of 10 rings (less with Armor), with no
knockback; half the toll scatters as rings you can grab back. A shield absorbs a hit instead.
A hit with **no rings** is lethal, unless a revive remains (Revival shop item, Oil Ocean's
emerald), which restores 20 rings. Rings flash red on the HUD while one more hit would empty
them. As in stock Sonic 2, rings cannot be collected during the first half-second after a hit.

## Bouncing and the combo

A stomp (jumping or rolling into a badnik) deals **2 x (1 + Spring Heels)** damage, times
Power and the shop, times the **combo multiplier**. Every rebound off an enemy while airborne
adds one to the combo: +25% per bounce after the first, capped at 6x (8x with Aquatic Ruin's
emerald). Sonic always rebounds upward from a stomp, so a well-aimed chain can stay off the
ground for a long time. Landing ends the combo (Combo Keeper adds a grace period). A chain of
five or more pays out a shower of rings when it ends, and every tenth bounce triggers **Fever**.

## Level-ups

Rings collected are experience. Each level-up pauses play and deals **three cards** (four with
the Talent shop item), favouring upgrades you already own. Until you own a weapon, one card is
always a weapon. **Reroll** deals again (one per zone, plus the shop and Chemical Plant's
emerald). Up/down chooses and jump confirms.

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
| Armor | passive | 5 | Hits cost 2 fewer rings per level (never fewer than 2) |
| Haste | passive | 5 | Weapon cooldowns -10% per level |
| Greed | passive | 5 | Extra ring drops and +20% experience per level |
| Spring Heels | passive | 5 | Higher rebounds and +1 stomp damage per level |
| Combo Keeper | passive | 3 | The combo survives landing for 0.3-1 s |
| Barrier | passive | 3 | A shield returns every 30, 25, then 20 s |

## Monitors (dropped by elites)

Super Ring (+10 rings), Shield, Invincibility, Speed Shoes, **Eggman** (here a bomb: every
badnik on screen takes a heavy hit) and the **?** monitor (every ring on the field flies to
Sonic).

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
| Red | Hill Top | Hits cost 3 fewer rings |
| Grey | Mystic Cave | A shield at every zone start |
| Cyan | Oil Ocean | +1 revive per run |
| All seven | | +25% damage, and Super Sonic: with 50 rings, jump again in mid-air to transform (invincible; rings drain each second) |

Progress is saved in `saves/sonic-survivors/profile.txt` under the engine's save root.

## Build and run

```bash
python3 examples/sonic-survivors/build.py        # compile and package target/sonic-survivors/sonic-survivors.jar
python3 examples/sonic-survivors/build.py --run  # ... then launch the engine with the mod (JVM build)
```

Install the jar through the Mod Manager like any trusted code mod. Native builds cannot load
code mods. The mod plays at 16:9 and hides the stock level select.

Film it headlessly with the gameplay capture tool's `--mod` option, for example:

```bash
java -XstartOnFirstThread -cp "target/classes:$(cat target/survivors-classpath.txt)" \
  com.openggf.tools.GameplayCaptureTool --game s2 --zone 0 --act 1 --width 400 \
  --mod target/sonic-survivors/sonic-survivors.jar --input run.txt --out-dir /tmp/survivors
```

(`-XstartOnFirstThread` is for macOS only.) Development properties: `sonic-survivors.seed`
pins the run's random stream, `sonic-survivors.survival=N` shortens every stage's clock to N
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
  fixed primitive pools so mod-object rewind capture restores them), the combo, level-up cards
  and menus. `Hud` draws everything with a code-drawn font.
- `Enemy` (one class, seven AI archetypes over `Species`), `Boss`, `Shot` and `Pickup` are mod
  objects drawn with the ROM art the zone already loads; frames were picked with the
  `ObjectArtContactSheetProbe`. `Guard` turns hits into ring tolls.
- `RunState` (the run, captured for rewind) and `Profile` (the save) live in the module.

`TestSonicSurvivors` packages the mod through `ggfmod` and drives it headlessly: camp, the
arena walls, every route act, stomps and the combo, level-ups and all weapons at once, the boss,
emerald and route choice, the Death Egg finale, death and banking, the shop, ring tolls and a
rewind round trip. Design notes and rejected approaches are in
[the design record](../../docs/architecture/designs/2026-10-06-sonic-survivors.md).
