# Sonic Survivors — a bounce-driven survivors roguelike for Sonic 2

A code mod for OpenGGF's JVM build. Select **Sonic or Tails** as the main character on the
engine's launch screen, then start Sonic 2. Runs are **solo**: configured sidekicks are disabled.
Every zone on the route becomes a walled **arena** cut from its own act's terrain, and badniks pour in from both sides and from above.
Your character starts with nothing but their jump: **bounce on badniks to keep going**. Each rebound off an
enemy chains a **combo** that multiplies stomp damage, chained bounces charge a six-second **Fever** burst, and defeated badniks drop **rings**, which are both
your **health** and **experience**. Level up to pick weapons, moves and buffs, open **treasure chests**
to **evolve** maxed weapons, survive the five- or ten-minute clock through zone events and warden
squads, beat the zone's **boss**, choose the next act, and push on to the Death Egg. Rings, Chaos
Emeralds, milestones and records carry between runs.

The stock act can never be finished: the signpost, capsule, boss triggers and every stock object
are gone, the camera and Sonic's level boundary are held to the arena, and the arena's only exit
is the clear screen after its boss. A physical ceiling at the arena camera's upper limit
keeps air jumps and powered rebounds away from terrain above the play area.

## A run

| Stage | Zone | Acts | Badniks | Zone event |
| --- | --- | --- | --- | --- |
| 1 | Emerald Hill | 1, 2 | Buzzer (shoots), Coconuts (hops, throws), Masher (leaps) | Coconut Rain |
| 2 | Chemical Plant | 1, 2 | Spiny (shoots), Grabber | Chemical Downpour |
| 3 | Aquatic Ruin | 1, 2 | Whisp swarms, Chop Chop (charges) | Whisp Storm |
| 4 | Casino Night | 1, 2 | Crawl (armoured) | Jackpot |
| 5 | Hill Top | 1, 2 | Spiker (spiked top: use weapons), Sol (fireballs) | Eruption |
| 6 | Mystic Cave | 1, 2 | Crawlton (lunges), Flasher (harmful while lit) | Cave-In |
| 7 | Oil Ocean | 1, 2 | Octus (hops, shoots), Aquis (shoots) | Oil Flare |
| 8 | Metropolis | 1, 2, 3 | Shellcracker, Slicer (throws pincers), Asteron (bursts into spikes) | Asteron Field |
| 9 | Wing Fortress | 1 | Clucker (turret), Balkiry (jets) | Air Strike |
| 10 | Death Egg | 1 | Silver Sonic, then Eggman on foot | — |

Sky Chase has no ground to fight on and is skipped.

1. **Title.** Under the Sonic 2 emblem, left/right picks the starting zone: any zone a previous
   run has reached. Starting further along grants four catch-up level-ups per skipped zone (at
   most 24), less than playing through earns. Start opens that zone's first act in **camp**.
2. **Camp.** START RUN, **RING SHOP**, **MODE**, **EGGMAN'S RULES**, **RECORDS** and back to the
   title; the shop, rules and records open their own pages. The START RUN row names your
   character's perk. A run begins with **50 rings** plus the shop's Ring Start.
   MODE cycles **5 MINUTES**, **10 MINUTES** and **UNLIMITED**; ten-minute, unlimited and
   Eggman's Rules unlock permanently after your first boss clear (existing profiles with a
   cleared boss already qualify). The choice is saved and fixed for the run.
   Ten-minute mode runs the pressure clock at 75% and gives its boss 25% more health. Unlimited
   stays in the starting arena, shows elapsed time, and keeps escalating without a boss or route
   transition: on top of the pressure clock, every two minutes survived counts as one more zone of
   the route (**THE HORDE GROWS STRONGER**) while rewards stay at the zone's own level, so every unlimited run eventually falls. Its best time per zone is recorded. Uncollected reward rings and monitors blink and
   expire after one minute; Emeralds and chests never expire. Death Egg always stays a direct boss finale.
3. **Survive.** Badniks spawn just off-screen on both sides and drop in from above, faster and
   tougher as the clock runs and further along the route. Every eight seconds a formation of five
   floating rings appears away from Sonic. Red chevrons at the screen edges point at badniks
   approaching from off-screen. An **elite** (150% size, ELITE label, gold health bar, six times
   the hitpoints) arrives every 30 seconds at first, down to every twelve; it drops a monitor, or
   every fourth elite a chest.
4. **Boss.** At 0:00 the zone champion arrives. Most zones use Eggman's complete vehicle
   assembled from its ROM mapping components, hovering within a jump of the player's ground, sweeping the arena, dropping volleys of the zone's
   projectiles (Mystic Cave drops rocks from the roof) and swooping at the player after a
   **DIVE!** warning; Hill Top's tank rolls along the ground and charges instead. Aquatic Ruin has a giant **Whisp Queen** with
   spreading volleys, Wing Fortress a **Balkiry Ace** with committed strafing runs, and Oil
   Ocean an **Oil Sentinel** core with orbiting escorts. Metropolis act 3 uses an armoured
   core ringed by Asterons. Bosses enrage below half health. Stomps rebound the player
   with the stock boss bounce; weapons hit them too. Beating a boss destroys every badnik still
   standing (their rings are the prize; that sweep drops no monitors or chests).
5. **Clear.** Every boss leaves a **treasure chest** (three prizes, one in five times five). The
   first time you beat each boss from Emerald Hill to Oil Ocean it also drops that zone's
   **Chaos Emerald** (kept forever). The clear screen shows the zone's results and any upgrades
   unlocked, and offers the next zone's acts: **act 1** normal, **act 2** hard (+20% badnik
   hitpoints, +50% rings), Metropolis **act 3** brutal (+40%, +100%). Or **retire** and bank
   everything, including the rings you are holding beyond the run's free start and revive rings.
   Rings carry from zone to zone.
6. **Death Egg.** Silver Sonic walks at Sonic, crouches, and spin-dashes wall to wall (harmful
   while spinning; enraged, he leaps and sends shockwaves along the floor). When he falls,
   Eggman bolts on foot. Catch him: three stomps win the run.
7. **Game over** banks a quarter of the rings collected (badnik ring values grow along the
   route), 25 per zone cleared and one per ten badniks;
   a win adds 300, and Eggman's Rules add their bonus. TRY AGAIN returns to camp at the run's
   starting zone.

Press **Escape** (or the gamepad Back button) at any time to leave for the title. At a zone's
clear screen that retires the run; anywhere else it forfeits it, banking like a game over, so
leaving just before a fatal hit is never better than playing on.

## Characters

| Leader | Perk |
| --- | --- |
| Sonic | Fever charges in **10** bounces; the combo grows **+30%** per bounce |
| Tails | **Hold jump while falling to hover** (75 frames per jump, renewed on every bounce); ring pull **+24 px**; Fever charges in 12 bounces; combo +25% per bounce |

## Encounters, events and pressure

Every 30 seconds brings a new encounter: **Crossfire**, **Air Raid**, then **Stampede**,
repeating with increasing pressure. Air Raids favour the zone's flyers; Stampedes favour
its ground badniks (zones without one group use their available enemies). At 15 seconds a
**SURGE IN 3...** warning announces seven seconds of faster, larger waves starting at 18.
The last five seconds are **REGROUP**: ordinary reinforcements stop so you can clean up and
collect rewards. Scheduled elites can still arrive. The HUD shows the wave, phase and progress.
Boss fights use mixed reinforcements without the surge cycle, a third as often and one smaller
per batch, so weapons can reach the boss.

Two mid-stage beats break up every survival clock, repeating every 150 seconds (twice in ten-minute
mode, forever in unlimited):

- **Zone event** (from 1:45): ten seconds of the zone's own trouble while ordinary spawns halve.
  Rain events drop a hazard every 11 frames on the first zones, down to every 6 later.
  *Rain* events (Coconut Rain, Chemical Downpour, Eruption, Cave-In, Oil Flare, Air Strike) drop
  the zone's projectiles from the sky, each landing spot marked by a blinking red chevron.
  *Swarm* events (Whisp Storm, Asteron Field) send lines of six badniks from one side every two
  seconds. Casino Night's **Jackpot** showers ring formations around Sonic instead.
- **Warden squad** (from 3:00): three elites at once. The first to fall drops a chest.

The route is meant to get **hard**: badnik health grows faster than linearly with the zone, so
Metropolis, Wing Fortress and the Death Egg expect a profile built up over many runs (the shop,
emeralds, unlocks and evolutions), not a lucky first build. Everything that sets the pressure
reads one curve (`Difficulty`): the route tier (stage plus
act) and the current arena's pressure clock (survival seconds, at 75% in ten-minute mode). Total
run time is never used, so a new arena never opens with soft badniks and a crushing toll, and
starting further along the route is not safer than playing through to it.

| Lever | Value |
| --- | --- |
| Badnik hitpoints | base × (1 + 0.40 × tier + 0.06 × tier²) × (1 + 1.4p + 0.4p²) × (1 + 0.2 × act), p = pressure seconds / 120 |
| Spawn interval | max(24, 60 − tier − seconds / 10) frames; surges two thirds of that |
| Batch | 1 + seconds / 75 + tier / 4; surges +1 |
| Ring toll threat | 1 + 0.25 per pressure minute + 0.30 per stage + 0.10 per act |
| Ring value per badnik | 1 + tier / 4 (fractions round by chance) |
| Elite spacing | 30 s, less 2 frames per pressure second and 1 s per tier, never under 12 s |
| Boss hitpoints | 12 × the zone line-up's average badnik at the moment the boss arrives (Death Egg 18 × at five minutes' pressure) |

Enemy fire is rationed so a crowd stays dodgeable: each shooter waits twice its species' period,
and every badnik shot also draws on one shared fire budget for the whole arena, one shot every
1.5 seconds on the first zones down to every half second in the last (Asteron bursts and boss attacks
excepted). Thirty Buzzers on screen fire no faster than one.

Every enemy projectile (badnik shots, boss volleys and zone-event rain) is drawn at twice its
ROM size inside a red danger disc with a flashing red/yellow rim, a white-hot core and a fading
trail, with a yellow muzzle flash where it was fired. Its hit area is unchanged, so the larger
look is forgiving.

Population has a soft ceiling: with **160** badniks alive, ordinary batches stop joining the
horde and every twelve held-back badniks arrive as one elite instead. Enemies, enemy shots,
reward pickups and player projectiles still have no fixed object ceiling; arena objects do not
consume the original game's limited object slots.

## Rings are health

Any badnik, projectile or boss hit costs a **ring toll**: 8 rings or one twelfth of your
held rings rounded up, whichever is greater, times the threat above. The Armor upgrade reduces that toll by 8%
per level, the shop's Armor Plating by 2% per level and the red emerald by 15%, together never
more than 60%, with a five-ring minimum. The HUD shows the
current cost. Large banks still help, but cannot trivialise an entire run. Hits give one
second of protection, with no knockback; half the toll scatters as rings you can grab back. These **lost rings** ignore
all magnets, award no new experience or collected-ring credit, fade smoothly from gold to transparent over **0.75 seconds**, then disappear; reward rings remain magnetic. A shield absorbs a hit instead.
Runs start with **50 rings** and a base toll of 8, six hits of grace. Rings are a health bar: **a hit whose toll would take your last rings ends the run** (unlike
stock Sonic, where a single ring survives anything), unless a revive remains (Revival shop item,
Oil Ocean's emerald), which restores three hits at the base toll (at least 20 rings). Rings and
the HIT cost flash red on the HUD while the next hit would be fatal. Ring pickup chimes are grouped: the first pickup after half a second without a chime is
immediately audible, while rapid collections chime roughly every ten rings, at most five
times per second. Every ring still grants its full health and, for reward rings, experience.

Rolling attacks: a roll into a badnik or boss deals stomp damage, and if the badnik survives it
knocks Sonic back off it without costing rings. Only a crouched spin-dash charge (or a roll that
has all but stopped) is unsafe: the badnik's touch is a hit, so charging in place is not an
untouchable attack.

Nearby reward rings—including enemy drops—consolidate within **48 pixels**, checked
every quarter second after the first 12 frames of their spawn. They merge while
scattering or flying toward Sonic, across all colour tiers, even when the total
is too small to change colour. Their **full combined value** stays in the surviving
pickup; if either ring was magnetized, the combined ring keeps flying toward Sonic. Larger piles use larger coloured rings:

| Colour | Stored ring value |
| --- | --- |
| Yellow (normal ROM ring) | 1–4 |
| Cyan | 5–24 |
| Purple | 25–124 |
| Red | 125+ |

Coloured rings spin like the ROM ring, turning edge-on and back about twice a second.
Red piles can keep combining. Consolidation pauses with menus. Hit-spilled rings, monitors, chests and emeralds never join a pile.
Merged rewards keep the youngest constituent's remaining lifetime, rather than refreshing
old piles indefinitely.

## Damage feedback

Hits show the actual HP removed, including the finishing hit (overkill is excluded).
Small hits (1–9) float up in red, 10–49 in larger orange digits, and 50+ in the
largest pink-red digits. Bosses use the same scale. Stock badnik score popups are
hidden; explosions, freed animals and ring rewards remain.

## Bouncing and the combo

A stomp (jumping or rolling into a badnik) deals **2 x (1 + Spring Heels)** damage, times
Power and the shop, times the **combo multiplier**. Every rebound off an enemy while airborne
adds one to the combo: +30% per bounce after the first for Sonic, +25% for Tails, capped at 6x
(8x with Aquatic Ruin's emerald). Sonic always rebounds upward from a stomp, so a well-aimed
chain can stay off the ground for a long time. Landing ends the combo (Combo Keeper adds a grace
period). A chain of five or more pays half its length in bonus rings (at most 20, times the
zone's ring value) when it ends. Combo damage retains its full multiplier; enemy ring rewards
gain only 10% of the multiplier's extra portion.

**Fever** charges over ten bounces (Tails twelve), across chains. It grants six seconds of
invincibility, then needs eighteen seconds of active play to recover before charging again.
Bounces while invincible, Super, or recharging do not charge or refresh it. The HUD shows charge,
remaining Fever time and recovery. Card menus and chests freeze these timers; rewind restores
them. Fever and invincibility monitors leave arena and boss music playing continuously.

## Level-ups

Reward rings collected are experience. Each level-up pauses play and deals **three cards** (four with
the Talent shop item), favouring upgrades you already own. Until you own a weapon, one card is
always a weapon. Your build holds **three weapon types and three buff types**.
Ranks do not consume extra slots. Homing Dash and Ground Pound count as weapons;
Air Jump and all passives count as buffs. Full categories offer only upgrades to
owned types, including on rerolls. Once your six equipped types are maxed, each level offers
**OVERDRIVE** (all damage +3%, stacking without limit) or a ring bonus (three hits at the base
toll). Shop upgrades, emerald relics and temporary monitors use no build slots. The HUD shows both
slot counts and the owned ranks (an evolved weapon shows `*`). **Reroll** deals again (one per zone,
plus the shop and Chemical Plant's emerald). Up/down chooses and **Enter** (keypad Enter also works) or **gamepad Start** confirms.
Jump never selects a card. Camp, cards, chests and results own Enter/Start, so confirming does not
also toggle the engine pause. During ordinary play the usual pause controls still work.

The experience curve is 6 + 8L + 2L² from level L: the first weapons come quickly, and a full
six-slot build arrives around the middle of the route rather than in the first zone.

Twelve upgrades are available from the start; the other twelve join the pool when your profile
reaches a milestone (lifetime records count, so older profiles keep what they have earned;
milestones reached mid-run join that run's pool at the next zone). The camp's RECORDS page lists
every requirement.

| Upgrade | Kind | Max | Effect | Unlock |
| --- | --- | --- | --- | --- |
| Shockwave | on bounce | 5 | A blast around the bounce (radius 48-96, damage 3-7); also pops enemy shots | start |
| Spark Burst | on bounce | 5 | 3-8 sparks fly out from the bounce | start |
| Homing Rings | on bounce | 5 | 1-3 rings launch and seek the nearest badnik | start |
| Orbit Rings | auto | 5 | 2-6 rings circle Sonic, hitting whatever they touch | start |
| Sonic Boom | auto | 5 | A piercing wave fires ahead every 2.2-1.0 s (both ways from level 4); pops shots | start |
| Air Jump | move (buff) | 3 | Jump again in the air, 1-3 times per jump | start |
| Power | passive | 5 | +25% damage per level | start |
| Magnet | passive | 5 | Rings fly to Sonic from further away | start |
| Armor | passive | 5 | Hit toll -8% per level (minimum 5 rings) | start |
| Haste | passive | 5 | Weapon cooldowns -10% per level | start |
| Greed | passive | 5 | Extra ring drops and +20% experience per level | start |
| Spring Heels | passive | 5 | Higher rebounds and +1 stomp damage per level | start |
| Chain Zap | on bounce | 5 | Lightning jumps between 2-6 nearby badniks | 300 badniks |
| Flicky Squad | auto | 5 | Freed Flickies dive at badniks every 3.2-1.8 s | clear Emerald Hill |
| Homing Dash | move (weapon) | 3 | Jump in the air to dash at the nearest badnik or the boss | 15-bounce combo |
| Ground Pound | move (weapon) | 3 | Press down in the air to slam; the landing quakes the floor | clear Chemical Plant |
| Combo Keeper | passive | 3 | The combo survives landing for 0.3-1 s | 25-bounce combo |
| Barrier | passive | 3 | A shield returns every 30, 25, then 20 s | clear Aquatic Ruin |
| Twin Lance | on bounce | 5 | Two horizontal piercing lances, damage 6–14, 3–7 hits each | 1500 badniks |
| Meteor Shower | auto | 5 | 3–7 falling sparks every 3 s, damage 4–8, two hits each | clear Hill Top |
| Pulse Field | auto | 5 | A nearby blast every 2 s, radius 48–80, damage 3–7 | clear Casino Night |
| Amplifier | passive | 5 | +15–75% area attack radius (including orbit contact); excludes chain range and projectiles | 20 elites |
| Second Wind | passive | 5 | +10–50% protection time after a hit or shield break; revive protection unchanged | bank 1500 rings in total |
| Quick Study | passive | 5 | +25–125% XP, multiplying Greed and shop growth | reach level 20 |

## Treasure chests and evolutions

Opening a chest is an event: play pauses and the screen dims, the chest drops in and rattles
faster and faster, then the lid bursts open in a white flash with turning light rays, a pillar
of light and a fountain of rings, to Super Sonic's theme. Each prize then spins through the
catalogue before landing (evolutions in shifting rainbow colours). Enter skips straight to the
full reveal; closing the chest restores the zone or boss music. Each prize, in order of preference:
**evolves** a ready weapon; otherwise ranks up an upgrade you own (a new upgrade only when no owned
one can grow); otherwise pays a ring hoard (two hits at the base toll). Bosses drop three-prize
chests (one in five has five prizes, glowing pink); every fourth elite and each warden squad drop
one-prize chests. A weapon is **ready** when it is at its maximum rank and you own its partner buff
(any rank); the HUD flashes EVOLUTION READY, and cards for either half show the pairing in pink.
An evolved weapon keeps its slot and is shown with `*`.

| Evolution | Weapon (max) | Partner buff | Effect |
| --- | --- | --- | --- |
| Grand Quake | Shockwave | Amplifier | 1.5x radius, 2.5x damage, gold blast |
| Star Nova | Spark Burst | Combo Keeper | Twice the sparks, 2x damage, each pierces three |
| Thunder Lattice | Chain Zap | Power | +6 targets, 240 px reach, 2.5x damage |
| Ring Tempest | Homing Rings | Greed | Six seekers, 2x damage, each pierces three |
| Ring Saturn | Orbit Rings | Magnet | +2 rings on a wider 56 px orbit, 2x damage |
| Sonic Cyclone | Sonic Boom | Haste | Fires both ways twice as often, 2x damage |
| Flicky Flock | Flicky Squad | Quick Study | Twice the flock, 30% faster, 8 damage each |
| Cross Lance | Twin Lance | Spring Heels | Lances fire up and down too, 2x damage, +4 pierce |
| Comet Storm | Meteor Shower | Armor | Twice the comets every 2 s, 2.5x damage |
| Guardian Pulse | Pulse Field | Barrier | 1.4x radius, 2.5x damage, pops shots |
| Light Speed Dash | Homing Dash | Air Jump | 1.6x dash range; every dash hit ends in a blast |
| Impact Star | Ground Pound | Second Wind | Untouchable while slamming; 1.5x quake radius, 3x damage, pops shots |

## Monitors (dropped by elites)

**Super Ring** (two hits' worth at the base toll, with experience), Shield, **Invincibility**
(ten seconds, matching Fever's scale rather than stock's twenty), Speed Shoes, **Eggman** (a bomb:
ordinary badniks on screen are destroyed, elites lose 40% of their health and a boss 6%) and the
**?** monitor (every reward ring on the field flies to the player; lost rings stay where they fell).

## Eggman's Rules

Optional handicaps, toggled on their own camp page once a boss has been cleared. Each adds its
bonus to every ring the run banks; the HUD and results show the total.

| Rule | Effect | Ring bonus |
| --- | --- | --- |
| Heavy Toll | Hits cost 50% more rings | +25% |
| No Fever | Bounces never charge Fever | +20% |
| Elite Horde | Elites arrive twice as often | +20% |
| Tough Hides | Badniks and bosses +50% hitpoints | +30% |
| No Magnets | Ring pull only from the ? monitor; Magnet leaves the pool | +15% |
| Brittle Rings | Hits scatter no rings to win back | +15% |

## Between runs

**Ring shop** (its own camp page). Global power-ups that apply to every run: most are small
increments with a long tail and prices that rise with each level, so the bank always has a use.

| Item | Per level | Levels | First price, growth |
| --- | --- | --- | --- |
| Power Up | +3% damage | 99 | 50, ×1.15 |
| Growth | +3% experience | 99 | 60, ×1.15 |
| Ring Start | +5 starting rings | 40 | 30, ×1.15 |
| Magnet | +4 px ring pull | 25 | 40, ×1.15 |
| Armor Plating | Hit toll −2% (all toll reductions together stop at 60%) | 15 | 80, ×1.18 |
| Head Start | +1 level-up at the start of each run | 10 | 150, ×1.4 |
| Reroll | +1 reroll each zone | 3 | 200, ×3 |
| Revival | +1 revive per run | 2 | 500, ×3 |
| Treasure Hunter | +1 prize in every boss chest | 3 | 400, ×3 |
| Talent | Four cards per level-up | 1 | 1500 |

Saves from the earlier, coarser shop (+10% damage, +10 rings, +16 px, +10% experience a level)
convert once to the same totals on this scale, so nothing already bought is lost.

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

**Records and collection** (camp, left/right turns the page): lifetime records and each zone's
longest unlimited survival; every upgrade with its unlock milestone; the twelve evolutions
(discovered ones named, every recipe shown); and a bestiary of badniks defeated per species.

Progress is saved in `saves/sonic-survivors/profile.txt` under the engine's save root. The file
is plain `key=value` lines; new records are added keys, so older saves load unchanged.

## Build and run

```bash
python3 examples/sonic-survivors/build.py        # compile and package target/sonic-survivors/sonic-survivors.jar
python3 examples/sonic-survivors/build.py --run  # ... then launch the engine with the mod (JVM build)
```

Install the jar through the Mod Manager like any trusted code mod. Native builds cannot load
code mods. The mod plays at 16:9 and hides the stock level select. Version 0.5.0 requires the engine
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
- `Difficulty` is the single pressure curve; `Upgrades` holds the catalogue, evolution pairs and
  unlock milestones; `Rules` holds Eggman's Rules.

`TestSonicSurvivors` packages the mod through `ggfmod` and drives it headlessly: camp, the
arena walls, every route act, stomps and the combo, level-ups and all weapons at once, the boss,
emerald and route choice, the Death Egg finale, death and banking, the shop, ring tolls and a
rewind round trip, plus the forfeit and retire payouts, the spin-dash rule, chests and evolutions,
unlocks, rules, character perks, zone events and warden squads. Coverage includes both leaders across all 19 acts, the ARZ ceiling, forced solo
teams, jump rejection/Enter confirmation through the host game loop, and lost-ring provenance
through rewind.

`TestSonicSurvivors#balanceProbe` is an opt-in diagnostic: a scripted bot (jumps at the nearest
badnik, takes weapons until it holds three, then ranks owned upgrades) plays whole runs and writes
the pressure curve every 30 seconds. Pass `-Dsonic-survivors.balance=<csv>[,fresh|mid|max[,mode[,sonic|tails[,seed]]]]`;
without it the case is skipped. Add `-Dsonic-survivors.balance.hits=true` for a line per hit taken. The bot neither dodges nor plans evolutions, so compare its
results across changes rather than reading them as a human's difficulty. Design notes and rejected approaches are in
[the design record](../../docs/architecture/designs/2026-10-06-sonic-survivors.md).


## Use matching creator artifacts

The mutable 0.7 Mod API is unpublished. See [candidate setup](../../docs/modding/getting-started.md) for Java 21 and matching engine/SDK jar paths. From this checkout the shared launcher supports artifact-only builds and explicit ROM paths:

```sh
python3 examples/build_example.py sonic-survivors --engine /absolute/engine.jar --sdk /absolute/sdk.jar --run --s3k /absolute/own-s3k.gen
```

Use `--s1`, `--s2`, or `--s3k` for the games this example consumes. Explicit paths create isolated development configuration and saves; no ROM is copied or linked. The creator kit exports this example with a portable POM and `tools/build_project.py`; it needs no engine source checkout. Only production sources/resources enter the validated mod jar. Read [recipient installation](../../docs/modding/installing-mods.md) before sharing.
