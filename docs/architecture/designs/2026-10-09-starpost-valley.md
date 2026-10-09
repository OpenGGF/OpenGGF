# Starpost Valley — design brainstorm

A farming and life-sim example mod inspired by *Stardew Valley*: a farm, crops, seasons,
neighbours, fishing, mines, animals, festivals and a long-term restoration goal, rebuilt
from Sonic's own ROM art, music and movement. Status: **brainstorm, nothing built**.
Base: `8668a9012` (`develop`). Branch: `feature/ai-starpost-valley` in
`.worktrees/ai-starpost-valley`.

The quality bar is the rest of the series: Slay the Robotnik (28,770 main lines), Eggman's
Sky (15,036), Sonic Survivors, Sitar Hero and Starfall Frontier. Only inspiration comes
from *Stardew Valley*. The mod uses none of its names, characters, text, art or data.

How this was produced: a cited survey of the Mod API and examples, an independent creative
brainstorm from the Fable model, and the lead's own pass. They were merged here and every
claim was checked against the engine survey. Section 12 lists what was changed or dropped
for feasibility.

## Kickoff decisions (2026-10-09)

| Question | Decision |
|---|---|
| Foundation | A scene mod with **two views** (§3.4). The **farm is a belt-scroller field** (§3.2 D): plot rows in front of a Green Hill back wall. The **rest of the valley is side view** (§3.2 B): town, lake, plateau, Ruins and festivals, built from Green Hill's own level blocks and collision. The user first chose side view after the §13 look test, then changed it to this hybrid ("belt view for the farm itself is sick"). |
| ROMs | Base game **S3K**; **S1 required** for Green Hill; **S2 optional** extras, each with a fallback (§3.3). |
| Scope | The **complete one-year game** (§10). |
| Concepts | All four accepted: **Momentum** instead of stamina (conventional energy stays behind a switch), **choose your farmer** (Sonic, Tails or Knuckles), **Partners** instead of romance, and animal villagers who **speak in pictures until the translator**. |
| Name | **Starpost Valley** (the user's rename, replacing "Green Hill Valley"). A Star Post is the checkpoint you always return to, which fits §9.21's home for a hero who never stops, and the name echoes the genre's inspiration. The valley still sits in Green Hill. The Inn becomes the **Lamppost Inn**, Sonic 1's own name for the checkpoint, so the two names don't collide. |
| Delivery | Assumed from the previous series: one pull request against `develop` when finished, with a lead plus a few subagents. |

## 1. The pitch

> Every Sonic game ends with Sonic running away from the wreckage of his victory.
> **Starpost Valley** is the part the credits skip.

The Sonic 1 ending plays: Sonic runs through Green Hill with freed animals streaming
behind him. This time, at the signpost, he stops, and so do the animals. The camera tilts
up the cliff to show the damage. The checkered soil is churned by Motobug tracks, a rusted
Buzz Bomber lies in the creek and the animal capsule on the hill is cracked. A Flicky lands
on his nose and the title card reads `SPRING 1 — STARPOST VALLEY`. Sonic has a note
from Tails, a shovel and about sixty animals with nowhere to go.

At the valley gate an old Star Post leans in the grass. Sonic taps it, it spins and lights,
and the checkpoint chime plays: this is where he will keep coming back. It is the valley's
namesake, where you wake after fainting (§9.20), and the last thing the ending shows.

**Design rule:** speed buys more actions in a day, never a shorter season. Sonic does not
slow down and crops do not speed up. The game lives in that gap. "Gotta grow slow" stays as
a loading-screen gag rather than the thesis: a forty-hour argument that Sonic should stop
being Sonic fights the licence. The genre's real emotional core is restoration, and Sonic
already has all three ingredients in the ROM: despoiled land, a corporate villain
(Robotnik) and a community (the animals he freed).

### Pillars

1. **It plays like Sonic between the chores.** The walk to the field is the best part of a
   Sonic game: momentum, springs, slopes, a loop on the farm.
2. **Everything you see comes from your ROMs.** Original art is limited to fish, food and
   item icons, UI and one sleeping pose. Nothing original has a face.
3. **Every named thing changes a rule.** A *Fire Shield Pepper* that behaves like any other
   crop is just a reskin. Eating one grants lava immunity in the Ruins.
4. **Restoration you can see.** Every badnik you pop frees an animal who walks home. The
   valley's population, palette, music and festivals visibly recover.

## 2. What the engine gives us (survey at `8668a9012`)

The Mod API and existing examples already support or constrain the game in these ways:

- **Mod scene.** `ModContext.registerStartupScene` and a full-screen `ModScene` with a
  60 Hz `update`, a side-effect-free `draw`, fault boundaries and `requireDisplayWidth`
  (320/352/400/528/800 × 224). This is the route used by Slay the Robotnik, Eggman's Sky
  and Starfall Frontier.
- **Multi-ROM art.** `ctx.art().rom("s1"|"s2"|"s3k")` gives independent views. A missing
  game returns `null`, and Eggman's Sky probes each one.
  - `SceneRomArt.levelKit(zone, act)` returns 256-px block images with collision,
    palette and backdrop. Kits exist for every S1 act, so Green Hill, Marble, Spring Yard,
    Labyrinth, Star Light and Scrap Brain are all available.
  - `levelOverview`, `zoneBackdrop`, `levelStages` and `levelForeground` exist for S1 GHZ1.
  - `sprites(RomSpriteRequest, palette)` decodes Nemesis/Kosinski/KosM art with DPLCs.
  - `character()` gives Sonic (S1), Sonic/Tails (S2) and Sonic/Tails/Knuckles (S3K).
- **Recolouring is free.** Every decode takes a caller palette. Terrain can be recoloured
  through `pixels()` the way Eggman's Sky does it in `core/Recolor`, and `withTint` /
  `withFlash` work at draw time. Seasons, dusk and weather are therefore palette work,
  not new art.
- **Assets already catalogued.**
  - Animals: S1 and S3K have the rabbit, chicken, penguin, seal, pig, Flicky and squirrel.
    S2 adds a mouse, monkey, eagle, turtle and bear. `eggsky.art.FaunaCatalog` has 83
    ready `RomSpriteRequest`s.
  - S1 objects: purple rock, spikes, bridge, monitor, ring, signpost, lamppost, prison
    capsule, Eggman and the S1 badniks.
  - S3K objects: Egg Robo, Robotnik ship, egg capsule, ring, monitors, starpost and springs.
  - Animated GHZ flowers and water are uncompressed art. Kits only show their first frame,
    so the mod animates them itself.
- **Audio.** `ctx.audio()` drives the base game's sound driver, so its music and SFX play
  together. `ctx.music().prepare("s1", id, frames)` synthesises any supplied ROM's song as
  PCM, so S1 music can play on S3K.
  - **Constraint:** while a prepared player exists its PCM replaces all driver output,
    so SFX go silent. A soundtrack that crosses ROMs needs the engine change in §3.3,
    now built as `ctx.audio().playMusic(game, id)` (§3.3.1).
- **Storage.** UTF-8 text files of at most 1 MiB under `saves/mods/<id>/`, replaced
  atomically. Starfall's gzip+Base64 split-part codec with a backup already handles larger
  saves.
- **No scene-to-level handoff.** A scene cannot start a real stock level and get control
  back. Patch mods can run custom objects inside real levels (Infinite Sonic rewrites GHZ
  terrain with real physics, and Sonic Survivors runs a whole roguelike UI in real S2
  acts). That is a different mod kind with its own session.
- **Packaging.** `ggfmod package` rejects mutable static state (`STATIC_STATE_UNSUPPORTED`).
  Catalogues must be instance-owned.
- **Tests and footage.** `ExampleModHarness` gives bridge tests, and `ExampleModCapture`
  produces scripted PNG, MP4 and WAV.

## 3. The three foundation decisions

### 3.1 Architecture: scene mod (recommended) or patch mod

| | **Scene mod** (own simulation) | **Patch mod** (real S1 levels) |
|---|---|---|
| Movement | Creator controller ported from `Sonic_Move`/`Sonic_Roll`/`Sonic_Jump`/slope resistance, on authored height maps. Starfall's flat controller is the starting point. Loops are scripted. | The engine's real physics: slopes, loops, spindash, rolling, rings-as-health. |
| Art | Kit blocks, ROM sprites and palettes, all drawn by the mod. GHZ flower and water animation reimplemented. | Real GHZ rendering, animated tiles, palette cycles, parallax and objects. |
| UI (inventory, shops, dialogue, calendar, crafting) | Natural. Slay has a screen stack, focus regions and mouse support. | Overlays over gameplay (`LevelOverlayCanvas`). Every modal screen fights the level loop. |
| World state (days, schedules, off-screen growth) | Mod-owned; save on sleep. | Rebuild each area from mod state on every load, and give every mod object rewind recreation and captured state. |
| Cross-game cast (Tails, Knuckles, Egg Robo) | `rom("s3k")` sprites through the API. | Patch ROM-art intake is S2-only. An S1 patch would need new hooks or non-API engine references (Survivors imports `GraphicsManager` and `AbstractPlayableSprite`). |
| API cleanliness as an example | Clean, like Slay and Eggman's Sky. | Heavy use of non-API engine internals. |

**Recommendation: scene mod.** Every system the genre needs is mod-owned state plus UI,
and the cast spans three ROMs. Authentic movement is achievable with a ported controller
on terrain we author ourselves. The patch route buys real physics but would need several
engine hooks and still fight the UI.

A real engine level inside a scene (for an arcade cabinet that runs the real GHZ1, say)
would be a large new Mod API capability: a scene-to-gameplay-session bridge. It is noted
as a stretch, not a dependency.

### 3.2 Perspective: side view, the "cross-section valley" (recommended)

- **(A) Top-down 3/4.** This gives the deepest farm grid, but no ROM has front-facing
  walk frames for any character. Blue Sphere has back views and everything else is side
  view, so "walking toward the camera", the most common facing in that view, would be
  original art for the three heroes and every villager. Green Hill's tiles are side
  elevations: the checker pattern is a cliff face, not a floor. The result is high cost,
  weak identity and the biggest risk of looking like a fan sprite sheet.
- **(B) Side view.** Every kit, character, badnik and animal works unmodified, and the
  walk between chores becomes Sonic movement. The cost is that each terrace's farm is a
  one-dimensional row. Verticality, a cellar, width and terrace allocation compensate.
- **(C) Hybrid** (top-down valley, side-view expeditions). This inherits A's art problem
  and B's full cost: two half-games.
- **(D) Belt-scroller depth band** (River City Ransom style). Characters stay in profile
  and walk up and down inside a shallow ground band, which gives the farm two or three
  rows of depth. It is worth a look test if B's single row proves thin.

**Recommendation: B** (superseded for the farm by §3.4). The valley is one continuous side-view world, roughly five
screens wide and three terraces plus a cellar high, about the footprint of GHZ Act 1:

- the farm on the left third;
- the town in the middle;
- the waterfall lake on the right;
- the Marble Ruins under the cliffs;
- a plateau above.

Plots are 16 px wide, so a 400-px screen holds 25. The farm starts as two terraces of
about 40 plots and grows to six terraces plus the cellar, about 300 plots. That is within
what most players of the genre actually cultivate. Prototype a back-row lane (D-lite:
holding up while standing still steps to a second plot lane 8 px up, drawn one shade
darker) only if the slice shows that single rows feel thin.

### 3.3 ROMs, base game and audio

- **Base game: S3K.** Sonic, Tails and Knuckles, the Egg Robo, the elemental shields and
  their sounds, and the richest SFX set are all native, as in Slay and Eggman's Sky.
- **Required: S1 as well.** Green Hill is Sonic 1. Without it the mod shows a friendly
  "needs Sonic 1" screen.
- **Optional: S2.** It adds Emerald Hill (summer), Casino Night (the fair), Sky Chase (the
  flight to Angel Island), and the bear, monkey, eagle, mouse and turtle villagers and
  livestock. Each has a fallback.
- **Engine addition (Mod API): background music from another supplied ROM, played
  under the base driver's SFX (built; §3.3.1).** The season/hour soundtrack in §8 crosses all three ROMs, and
  today that silences every tool, spring and ring sound. Any multi-ROM scene benefits,
  Eggman's Sky included. The fallback is a soundtrack drawn from S3K only, which loses
  Green Hill's own theme in spring. That loss is unacceptable for this game.

#### 3.3.1 Background music engine addition

**Built:** `SceneAudio.playMusic(game, id)`, a default method next to the existing
`playMusic(id)`. The running game's songs take the base route. Another supplied game's
song takes the existing cross-game **donor route** (`AudioManager.playDonorMusic`,
`MusicRoute.DONOR_SMPS`), the route that plays S3K Super music in an S2 game. The call
returns false when that ROM was not supplied or has no such song. The creator recipe is
in [mod-scenes.md](../../modding/guides/mod-scenes.md#5-audio-and-storage).

**How the donor route fits.** The song is a second game's sequencer running inside the
base driver session, with its own loader, DAC bank, sequencer config and coordination-flag
handlers. It is the driver's current music, so the stock rules apply unchanged:

- It loops at its own loop jump.
- Effects take over music channels and hand them back.
- `stopMusic` and `fadeOutMusic` act on it.
- A stock jingle saves and restores it.
- It is recorded in the command timeline like any song.

Evidence (`TestSceneDonorMusic`, an S3K session playing S1 Green Hill over 3,768 frames,
past its loop jump at 3,168):

- **It is Green Hill as Sonic 1 plays it.** The 100 ms loudness envelope matches Sonic 1's
  own driver rendering with correlation 0.997, at zero lag. The Marble control scores 0.03.
- **It loops to the body, not the intro.** The repeat matches the loop body with
  correlation 0.999; the same window shifted 97 frames scores 0.36.
- **It never falls silent.** The quietest 100 ms window scores RMS 152.
- **Effects play over it.** An S3K ring's contribution correlates 0.67 with the ring
  alone. The music channel the ring takes over should explain the shortfall, but that
  was not measured separately.
- **The 1-up hands it back.** The S3K 1-up replaces the song while it plays (relative
  change 1.36), and the song returns afterwards.

No engine fix was needed to play S1 songs in the S3K driver session.

**The one gap was lifecycle.** The donor registry is global and keyed by the real game
code, which is also the key presentation coordination handlers resolve by. A scene's
loaders read `SceneRomLibrary`'s own ROM views, which close with the scene. The registry
had no per-key unregister, and `clearDonorAudio()` would also wipe the
`CrossGameFeatureProvider` donor registered at gameplay bootstrap. The fix is
`com.openggf.audio.ScopedDonorAudio`, an engine-internal helper over package-private
`AudioManager` state capture, so it adds no creator surface. Opening it captures the
key's route and its music and sound bindings. Closing it restores them, under a fresh
generation so no asset cached for the scene can answer for the restored route.
`TestScopedDonorAudio` registers a stand-in prior donor, borrows the key and checks that
the prior one is back. Leaving the scene also stops another game's song that is still
playing; the base game's own music is left as it was.

**Accepted limits.**

- Every profile's `OrdinaryMusicSfxPolicy` is `STOP_ALL`, so starting or changing a song
  stops effects still ringing, as every stock song change does.
- There is no music volume. Fades are the base game's own (S3K about four seconds).
- Effects steal music channels, as on the console.
- A `ctx.music()` song player still replaces all driver output, donor song included.

**Considered and set aside: a second, additive synthesizer.** This was built first and is
kept off the branch as a patch. Each song ran in its own `OwnedSmpsAudioStream`, the
session finite `prepare` uses, and its PCM was added to the base driver's final mix
through the `ScenePcmSource` hook. It shared `prepare`'s session code and offered volume,
fades of any length and no global registry. Its tests were written but never run: the
first queued run failed to compile on an unrelated probe, and the lead chose the donor
route before a second run. It cost one more emulated chip per frame, its effects never
shared a channel with the music, and a jingle ended the background instead of pausing it.
More decisively, it duplicated what cross-game donation already does, and the user asked
for one shared route.

**Rejected outright.**

1. **Restarting a finite `prepare` song.** It replays the intro instead of the loop body,
   leaves a seam, is capped at ten minutes, and a started player silences every effect.
2. **Prepared PCM with a loop region.** SMPS has no song-level loop point: each track
   jumps on its own (`smpsJump`, `F6`), and local `F7` repeats also jump backwards. A
   splice also cannot carry chip state across the seam (FM release tails, envelopes, PSG
   noise and LFO phase).
3. **The patch-mod `StreamedMusicPort`.** It is a launch-prepared, rewind-snapshotted
   `@ModApi` port whose `State.sourceFramePosition` restore needs a seekable source,
   which live SMPS synthesis is not.

### 3.4 Two views: the belt-view farm inside a side-view valley

The look test showed each view's strength. The belt field is the most convincing farm: rows
of crops with depth and a Stardew-like density. The side view is the most convincing Green
Hill: springs, slopes and the loop, using the act's own blocks. The game uses each where it
is strongest.

- **The farm (belt view).**
  - Plot rows run in front of an upright back wall of Green Hill blocks drawn at 1:1. The
    farmhouse, coop, barn and Capsule Garden stand along that wall as facades.
  - Sonic, Tails or Knuckles walks side-on, moving into and out of the screen, with
    Sonic_Move's acceleration and a jump. Animals, villagers, Flickies and pests share the
    depth-sorted field.
  - The field grows by rows and columns as it expands, instead of by terraces.
  - The spin dash runs along a row, tilling as it goes.
- **Everything else (side view).** The valley path, the town, the lake and fishing, the
  plateau, the Ruins, festivals and Angel Island use the ported side-view controller with
  springs, slopes and loops.
- **The seam.** The farm gate at the valley's west end is a Star Post. Running past it
  folds the view: the side-view terrace tilts down into the field over about half a second,
  and Sonic keeps his speed and facing. Leaving through the gate reverses it.
  - Each view owns its controller and camera; the day clock, inventory and Momentum carry
    across.
  - A visit never changes view mid-screen anywhere except at the gate.
- **The cost, accepted.** There are two controllers and two renderers, but the look test
  already has both. Every farm system (plots, buildings, animals, automation) is laid out
  in belt coordinates (column, row). The valley's systems use side-view world coordinates.

## 4. Who farms

Use the S3K convention: **choose Sonic, Tails or Knuckles**, and the other two become
villagers. Each plays differently, so the choice is more than a skin:

- **Sonic.** Fastest. Spin dash tills three plots in a line. Needs springs to reach the
  upper terraces. This is the canonical story and the default.
- **Tails.** Flies between terraces freely and carries two held items. His spin is weaker
  (tills one plot).
- **Knuckles.** Glides and climbs cliff faces. He **digs** instead of tilling, which is
  instant and can turn up buried items, and he breaks rocks in one punch. He is too slow
  for the farm loop's full payoff.

Each unchosen pair needs dialogue variants. Budget for that, or ship Sonic first and add
the other two farmers in the full game (§10).

## 5. The day

- **Time.** A day runs 06:00–02:00 over about 14 real minutes (configurable), in 28-day
  seasons. Time pauses in menus and cutscenes. At 02:00 you faint.
- **Energy: Momentum (prototype) or conventional stamina (fallback).** Sonic does not get
  tired, so a stamina bar is the wrong fiction. **Momentum** is drained 2–4 points per
  chore (till, plant, water, harvest, chop, mine). It refills only through Sonic things:
  a lap of the farm loop (+30), springs, rings, popping badniks and food. A full bar is
  about 60 chores. This is the single most likely mechanic to become annoying, so it sits
  behind one switch and the slice decides.
- **Rings are currency and health.** A pest hit scatters rings in the classic burst. Grab
  them back within three seconds or lose them. A hit at zero rings means fainting: the
  "you fainted" jingle, a Continue screen, and the rest of the day lost.
- **The HUD is Sonic 1's.** SCORE shows banked rings, TIME is the day clock and RINGS is
  rings in hand.
- **Day start.** The S1 title card reads `SUMMER 12` / `STARPOST VALLEY`.
- **Day end.** The shipping bin is a signpost. At bedtime it spins, and the act-clear tally
  counts up `CROP BONUS / ANIMAL BONUS / ARTISAN BONUS / TOTAL` to the clear jingle.
- **Idle.** Sonic's foot tap tells the time: it quickens as the night gets later.
- **Saving.** The game saves on sleep, as the genre does.

## 6. Systems, mapped

### 6.1 Farm buildings and placeables

| Genre role | Starpost Valley | Notes |
|---|---|---|
| Chest | **Item Monitor** | Punch it open. Its screen shows the icon of what's inside. |
| Coop, upgraded twice | **Cucky Coop** → Flicky nests → Pecky roost | Eggs, blue feathers, Ice Eggs (winter's only animal income). |
| Barn, upgraded twice | **Pocky Pen** → Picky sty → S2 bear den (optional) | Fluff (wool), Hill Truffles, honeycomb. |
| Well | **Waterfall Tap** | Redirects a GHZ waterfall onto one terrace, giving that row unlimited water. |
| Greenhouse | **Capsule Garden** | The animal capsule, its dome rebuilt in glass. The reward for restoring the Great Capsule. Any crop, any season. |
| Paths and stable | **Springs and the Farm Loop** | Placed red and yellow springs connect terraces. The loop refills Momentum. |
| Sprinklers | **Reprogrammed Buzz Bomber** (8 plots) → Mk II (16) → **Caterkiller Crawler** (crawls a whole row) | Tails rebuilds badnik shells you bring back from the Ruins. |
| Auto-harvest huts | **Flicky Roost** | A flock of 4–6 Flickies harvests one terrace into a basket, then circles Sonic in the S3K-ending formation. |
| Fish pond | **Rocky's Pool** | A Rocky seal fishes passively. |
| Tree tapper | **Ricky Roost** | A squirrel gathers nuts and sap. |
| Bee house | **Buzz Hive** | A docile Buzz Bomber colony. Honey flavour follows the nearest flower. |
| Monster hutch | **Badnik Garage** | Where your automation badniks sleep. |
| Warp totems | **Big Ring warps** | Late game. |
| Scarecrow | **Sonic Scarecrow** (totem wood) | Badniks avoid it. Mistakenly crafting the **Robotnik** scarecrow scares your own animals. |
| Checkpoint | **Star Post** | You wake here after fainting. |

### 6.2 Tools: moves plus the S3K shields

Sonic's moveset is the tool belt. The three elemental shields are the upgrade tiers: one
is equipped at a time from a Monitor Rack at home, with one spare carried (rising to
three). Sonic never holds a hoe.

| Job | Tool | Upgrade ladder (built by Rusty the Egg Robo) |
|---|---|---|
| Till | **Spin dash** (innate) | Sneakers (1 plot) → Power Sneakers (2) → Speed Shoes (3) → Chaos Sneakers (3, and you can run on water) |
| Water | **Water Shield** | 10 → 20 → 40 → 80 plots per fill. Refills instantly from any water. |
| Chop and mine | **Fire Shield** | Fewer hits per tier. Burns weeds. Lava immunity in the Ruins. |
| Harvest | **Lightning Shield** | Ripe produce flies to you, and dropped rings with it. The radius grows by tier. |
| Fish | **Fishing rod** | Tails makes it from a Buzz Bomber stinger. Tiers add bait and tackle. |
| Inventory | **Monitor slots** | 12 → 24 → 36. |

### 6.3 Crops (original names; each must change a rule)

- **Spring**
  - Ring Radish: the starter, 4 days, 35 rings.
  - Green Hill Sunflower: the zone's own sunflower art. Regrows, and its seeds feed Cuckies.
  - Palm Bean: grows on a trellis and regrows.
  - Checker Cauliflower: can become a giant crop.
  - Spring Tulip: the festival flower.
  - Spin Spud.
- **Summer**
  - Emerald Melon: can become a giant crop.
  - Motobug Tomato: regrows.
  - Fire Shield Pepper: grants lava immunity for a day.
  - Bluesphere Berry.
  - Star Post Corn: summer and fall.
  - Spring Yard Hops: trellis.
- **Fall**
  - Eggman Pumpkin: a three-plot giant crop that grows a Robotnik face, the best
    screenshot in the game.
  - Egg-plant: 1% grow a moustache, and Robotnik loves them.
  - Marble Grape: trellis; Knuckles loves them.
  - Ruby Berry.
  - Totem Choke.
  - Scrap Brain Amaranth.
- **Winter.** Nothing can be sown. The valley takes on Ice Cap colours. Forage Snow Spuds,
  Ice Crystals and Frost Rings (rings frozen in ice that the Fire Shield breaks out).
- **Trees**
  - The real GHZ palm, which gives coconuts.
  - **Ring Fruit Tree**: matures in 28 days, then drops 10 rings a day in season. This is
    the slow investment.
  - Chaos Cherry.
- **Rares**
  - **Super Sunflower**: gold, from the Ruins.
  - **Emerald Seedlings**: seven, each needing a full season of particular care (watered
    only at night, planted on the cliff, and so on) to grow a Chaos Emerald. Each Emerald
    adds +10 maximum Momentum. All seven unlock Super Sonic Harvest Day (§9).

### 6.4 The Great Capsule and EGG (the community-centre and corporate paths)

**The Great Capsule** is the cracked animal capsule on the hill. Restoring Robotnik's
prison inverts its meaning. Flickies in six **Chambers** accept bundles:

| Chamber | Accepts |
|---|---|
| Pantry | Crops |
| Hatchery | Animal goods |
| Reef | Fish and caught badniks |
| Scrapyard | Minerals and badnik parts |
| Bulletin | Friendship milestones |
| Vault | 2,500 / 5,000 / 10,000 / 25,000 rings |

Rewards: the Capsule Garden, the lake bridge, the Ruins minecart, the Tornado's parts (the
route to Angel Island) and the Big Ring warps.

**EGG (Eggman Enterprises General Goods)** opens at the valley gate in Scrap Brain
storefront tiles, staffed by Egg Robos. It offers cheap seeds, an Egg Membership (5,000
rings) and a **Valley Development Form**: badniks build each improvement for rings. Finish
the form and the Capsule becomes a **Badnik Factory**. The cost is not a mechanical
penalty but the population: animals leave, villagers' dialogue sours, and at the year-two
evaluation the signpost stops on Robotnik and stays there.

**Renamed (2026-10-09, the user's idea "Joja = Robo"):** the store is **Robomart** (the
membership, the badnik-built Development Form upgrades and the facade follow), and its own drink is
**Robo Cola**: cheap, a little Momentum, and the classic junk catch once fishing lands. Robotnik's
Egg Mobile keeps its name.

### 6.5 The Marble Ruins and Scrap Brain Depths (mines)

- **The Marble Ruins** lie under the cliffs.
  - 40 chambers, each a one- or two-screen room assembled from Marble Zone kit blocks
    with Eggman's Sky's seam-repair remixer.
  - A hand-authored landmark every fifth chamber, plus Star Post elevators.
  - Hazards: lava, pushable blocks, Caterkillers, Batbrains and Buzz Bombers.
  - Yields: Scrap (from popped badniks), Marble Ore, Rubies and Emerald Shards.
  - Combat is the spin jump, and every popped badnik frees an animal who walks home.
- **Scrap Brain Depths** is the endless dungeon, reached through a flooded Labyrinth
  passage after chamber 40. It has conveyors, electric beams, Ball Hogs, Bombs, and the
  real drowning countdown wherever water intrudes.

### 6.6 Fishing

You can catch two kinds of thing:

- **Fish:** 12–16 original fish in the S1 palette. Fish are the lowest-risk original art
  there is.
- **Submerged badniks:** Choppers, Jaws, and from S3K Jawz and Blastoid. They are the
  legendary catches, and each yields an animal and a shell.

The legendary of legendaries is **the Red Chopper**, the giant Chopper under the lake
bridge that ate Barnaby's hat.

The minigame is the **Bubble Bar**. Hold to rise and release to sink, keeping the catch
inside a Labyrinth air bubble that shrinks under tension. On legendary catches the
drowning-countdown digits appear as pure theatre.

Fishing spots: the farm pond, the river, Waterfall Lake, the Labyrinth Cistern (diving,
with the real air timer) and, later, the Angel Island shore.

### 6.7 Animals and the population counter

Livestock: Cucky (eggs), Pocky (Fluff), Picky (Hill Truffles, dug from the terrace), Pecky
(Ice Eggs), Rocky (passive fish), Ricky (nuts and sap). With S2: the bear tends the Buzz
Hive and the monkey picks tree fruit. Flickies are never livestock; they are helpers and
couriers.

Every freed animal joins the **valley population**, from wherever it was freed: farm
pests, the Ruins or a fishing line. It starts at 6 and caps at 60. Every 10 unlocks a
market stall, a festival booth or a new villager. The population takes the place of the
genre's family: your household is the town.

### 6.8 Town, collections and progression

- **The Lamppost Inn.** Its lamp spins while it's open. Clementine cooks and Rusty tends
  bar. Friday is Jukebox Night. Its music is Spring Yard Zone, which is already a lounge
  tune.
- **Arcade cabinets**, paid in Inn tokens that buy a prize shelf:
  - **Spin the Signpost**, a timing game;
  - a **Special Stage**, the S1 rotating maze rebuilt in-scene, whose goal is a rare seed;
  - a **GHZ Dash** sprint on the valley's own physics. The real stock GHZ1 would need the
    §3.1 bridge.
- **Tails's Workshop Museum.**
  - A Scrap Collection (the badnik bestiary).
  - Minerals.
  - Relics: totem fragments, ring moulds and Star Post caps.
  - The **Sound Test.** "Records" found in the Ruins unlock ROM tracks for the jukebox and
    your bedside radio. A ROM-music collection is the most Sonic collectible available.
- **Signpost Board.** Quests are pinned outside the Inn and mail arrives by Flicky.
  Robotnik posts suspicious special orders ("500 Egg-plants, no questions asked").
- **Skills.** The level-5 and level-10 choices branch, as in the genre:

  | Skill | Covers | Level-5 choices |
  |---|---|---|
  | Farming | Crops and animals | Ringgrower / Rancher |
  | Ranging | Foraging | Forester / Gatherer |
  | Fishing | Fishing | Angler / Trapper |
  | Scrapping | Mining | Scrapper / Geologist |
  | Bopping | Combat | Insta-Shield / Drop Dash |

  Bopping's late choices include **Ring Keeper** (lose fewer rings).
- **Crafting and artisan goods.**
  - Machines: the Scrap Brain Furnace (Scrap → Steel → Chrome → Eggmanium), Monitor Jar,
    Spring Yard Keg, Fluff Loom, Egg Machine (Robotnik's design), Sunflower Press.
  - **Chili Dogs**, the one canon Sonic food, are the best Momentum meal in the game.

### 6.9 Angel Island (the later-game island)

The repaired Tornado flies to Angel Island. The flight is a Sky Chase shooter: on S2's Sky
Chase kit if supplied, otherwise over the S3K AIZ intro sea.

| Island location | Purpose |
|---|---|
| Mushroom Hill | Its mushrooms are the island's crops |
| Lava Reef | Dungeon |
| Hidden Palace | Knuckles's home and shrine |

## 7. Neighbours

The ROMs contain no humans except Robotnik, so the valley is populated by the animals Sonic
freed. In canon they only chirp, so **animal villagers speak in icon bubbles until Tails's
2-heart event, when you receive the Chirp Translator** and their speech becomes text. The
first fortnight is spent reading pictures, and the moment the valley finds its voice is a
real beat.

**No romance.** A cast of a teenage hedgehog and rescued rabbits makes romance wrong. At 10
hearts a villager becomes a **Partner**: they build a cabin on your farm (Knuckles just
visits) and give a daily effect, and you can have several. Bonds fit a hero whose whole
mythology is friendship.

| Villager | Art | Home | Personality | Loves | Arc (2/4/6/8/10 hearts) |
|---|---|---|---|---|---|
| **Tails** | S3K | Workshop under the wrecked Tornado | Earnest inventor, over-explains, hero-worships you | Scrap, Chili Dogs | Reprograms Moto → Translator → Buzz Bomber sprinkler blueprint → finds the Tornado engine → first flight to Angel Island |
| **Knuckles** | S3K | Cliff shrine; glides in during Summer Y1 after an Emerald Shard | Blunt, proud, allergic to lies | Marble Grapes, Emerald Shards | Suspects you → tests you in the Ruins → teaches digging → admits he's lonely → Hidden Palace |
| **Dr. Robotnik** | S1/S3K | Egg Mobile caravan behind EGG | Grandiloquent schemer, occasionally and genuinely helpful | Egg-plants, eggs | Sells to you → tries to buy the farm → the Fair judging scandal → shows the "badniks were meant to be *rides*" blueprint → betrays you anyway, warmly. Never a Partner. |
| **Rusty** (Egg Robo) | S3K | The Inn's back room | Literal and gentle; left Robotnik after a firmware fault | Oil, batteries | Learns to want things → asks what a day off is → repairs the Star Posts → chooses a name → becomes the valley clockmaker |
| **Pip** (Flicky) | S1/S3K | Nests in your signpost | Gossip and courier, never lands for long | Sunflower seeds | Postal route → the lost letter → organises Night of the Flickies → leads the migration and comes back |
| **Dandel** (Pocky) | S1 | Seed stall | Anxious rabbit, undercut by EGG | Ring Radish | Stall failing → you stock it → Robotnik's buyout → refuses → it becomes a co-op |
| **Clementine** (Cucky) | S1 | Inn kitchen | Warm, bossy, feeds everyone | Star Post Corn, honey | Recipes → cookbook → the Chili Dog recipe → caters the Fair → the Inn is named for her |
| **Pud** (Picky) | S1 | Shack at the Ruins mouth | Brave about gems, scared of the dark since his capture | Rubies, anything shiny | Won't enter → sells you a lamp → follows you to chamber 10 → finds the Super Sunflower seed → opens the minecart |
| **Barnaby** (Rocky) | S1 | The lake jetty | Old fisherman with one story | Ice Eggs | Teaches fishing → the hat story → the Red Chopper → you catch it → he gives you the hat |
| **Frost** (Pecky) | S1 | Ice hut by the river | The only one who loves winter; homesick | Snow Spuds | Hosts the Ice Cap Festival → snowboard → admits homesickness → the Ice Cap Record → stays |
| **Hazel** (Ricky) | S1 | Treehouse over the museum | Kid squirrel who wants to be fast | Acorns, relics | Museum assistant → the stolen relic → you teach her the spin dash → she takes the loop → she's fine |
| **Moto** (Motobug) | S1 | Your farm | Pet, beeps | Petting | At game start, choose Moto or a Crabmeat |
| **The Elder Totem** | GHZ totem | Hilltop | The valley's memory; faces animate by palette; speaks in riddles | Festival offerings | Gives the year-two evaluation. A grandparent figure without inventing a human. |
| S2 extras | S2 bear, monkey | Arrive at population 30/40 | Beekeeper, carny | Honey, mangoes | Short arcs; they run the Hives and the Fair booth. |

Example Partner effects:

- Tails tops up one automation badnik a day.
- Knuckles digs up one buried item.
- Clementine leaves a meal in your monitor.
- Pip ships your bin for +5%.

## 8. Calendar, festivals, weather and music

| Season | Festivals | Day / night music | Look |
|---|---|---|---|
| Spring | 13 **Ring Hunt** (60 s to find rings around town; Tails wins until you beat him). 24 **Sunflower Parade** (every sunflower blooms, palette cycles). | Green Hill (S1) / Star Light (S1) | Stock GHZ |
| Summer | 11 **Great Valley Race** (a looped GHZ track against Tails, Knuckles and Robotnik in the Egg Mobile). 28 **Night of the Flickies** (the S3K-ending migration over the lake). | Emerald Hill (S2) or Angel Island 1 / Mushroom Hill | Warmer, saturated |
| Fall | 16 **Valley Fair** (a grange display judged by a biased Robotnik, Casino Night slots, a spring-launch strength test). 27 **Scrap Brain Night** (a haunted maze with a Mecha Sonic silhouette at the end). | Mushroom Hill 2 (its autumn turn) / Angel Island 2 (the burning palette) | MHZ autumn ramps |
| Winter | 8 **Ice Cap Festival** (a fishing contest and the snowboard run). 25 **Star Light Feast** (secret gifts). | Ice Cap (S3K) / Star Light | Ice Cap palette on GHZ, snow caps |

- **Weather.** Sun, rain, storms, snow and two special days:
  - Rain is the Labyrinth palette, and the Labyrinth theme plays.
  - A storm gives a free Lightning Shield charge.
  - A **Badnik Swarm** day: the TV warns "Robotnik is active" and a Buzz Bomber wave
    crosses at noon.
  - On the rare **Emerald Aurora** night, crops planted that day roll for Super quality.
- **Other music.**
  - The Ruins play Marble, then Labyrinth, then Scrap Brain.
  - EGG plays Scrap Brain.
  - Bosses and legendary catches use the boss theme.
  - The credits use the S1 ending.
  - The jukebox overrides everything once you own a Record.
- **Seasonal palettes.** Every palette is derived from palettes that already exist in the
  ROMs. Emerald Hill is a Green Hill recolour, Mushroom Hill has an autumn turn, Angel
  Island 2 has a burnt palette and Ice Cap is winter. Never swap the kit: the valley must
  always read as Green Hill.

## 9. Signature ideas only a Sonic game can have

1. **Tilled soil is the checkered dirt.** Spin-dash across grass and it becomes the iconic
   brown-and-tan checker.
2. **Rings are health,** with a three-second recovery window.
3. **The farm has a loop on purpose:** take a lap to refill Momentum.
4. **The HUD is Sonic 1's;** the end-of-day tally is the act-clear screen.
5. **The day opens with a title card.**
6. **The foot tap is the clock.**
7. **Pests are badniks, and popping them grows the town.**
8. **Monitors are chests** and show their contents.
9. **The elemental shields are the tools.**
10. **Reprogrammed badniks are the automation.**
11. **Flickies are the harvesters.**
12. **The animal capsule becomes the greenhouse.**
13. **Deep fishing uses the drowning countdown** honestly. The Water Shield extends it.
14. **Big Ring bonus.** Bank 50 rings in a day and a Big Ring hangs over the signpost at
    dusk, leading to a special-stage maze with a rare seed.
15. **Seven Emerald Seedlings unlock Super Sonic Harvest Day,** once a season. For 60
    seconds the clock stops, the Super palette cycles and you harvest the entire farm.
16. **The Signpost Spin is the year-two evaluation.** The post flips through Robotnik and
    the Elder Totem's faces and, if you did well, stops on yours, as the S1 end-of-act post
    does. The number of flips (1–4) is the score.
17. **Weather is palette.**
18. **The Sound Test is a collectible.**
19. **The Giant Eggman Pumpkin.**
20. **Placed springs and Star Posts are your paths and checkpoints.**
21. **The ending.** Restore the Capsule and grow all seven Emeralds, and the giant
    Special Stage ring appears at the valley gate at dusk.
    - Sonic stands at the signpost, tapping his foot and looking at it. The animals wave.
    - He runs through, and the credits roll over the S1 ending: flowers bloom where he
      runs.
    - Then `YEAR 3 — SPRING 1`, and he is back in his hammock. The ring stays as a door.
    - The valley becomes the place a hero who never stops comes back to.

## 10. Scope tiers

- **Vertical slice (proves the fantasy):**
  - Spring only, with Sonic as the farmer.
  - Two terraces and the loop.
  - Five crops and the Water Shield.
  - Momentum, and rings-as-health with Motobug and Buzz Bomber pests.
  - Four villagers (Tails, Robotnik, Pip, Dandel) up to 4 hearts with icon speech.
  - The Inn and Ruins chambers 1–5.
  - Pond fishing with four fish and a Chopper.
  - The Ring Hunt, the signpost tally, day and night, and saving on sleep.

  Success test: a player who knows neither game reference says "this feels like Sonic"
  unprompted.
- **Complete one-year game (recommended delivery):**
  - All four seasons, every crop and the trees.
  - Three shields and four sneaker tiers.
  - All villagers up to 8 hearts, the Translator, and at least two Partners.
  - The Great Capsule and the EGG route with its ending.
  - Ruins to chamber 40.
  - 16 fish and the badnik legends.
  - All the animals and the automation.
  - Eight festivals.
  - The museum and the Sound Test.
  - Population up to 60.
  - The Signpost Spin evaluation.
- **Full game:**
  - Angel Island.
  - Scrap Brain Depths.
  - The Emerald Seedlings, Super Harvest Day, and the Big Ring ending into Year 3.
  - All 10-heart events, including Robotnik's.
  - Tails and Knuckles as farmers.
  - Partner cabins and Big Ring warps.
  - Achievements.
  - Optional direct-connect co-op, using Putt-Putt Paradise's network precedent.

## 11. Risks of a cheap reskin

| Risk | Cure |
|---|---|
| Top-down by reflex | Side view, decided now. |
| Content only pun-deep | Every named thing changes a rule (§1 pillar 3). |
| Villagers who are genre archetypes in costume | Cast from canon relationships (Knuckles's distrust, Tails's hero-worship, Robotnik's showmanship). The Translator arc makes the animals' silence a story. |
| A thin one-dimensional farm | Terraces, cellar, density. A back-row lane only if the slice needs it. |
| Momentum becomes a chore | A generous bar, food, Emerald growth, and a fallback behind a switch. |
| Garish seasons | Ramps derived from existing ROM palettes only. |
| A defanged Robotnik | He escalates: Swarm days, the Fair scandal, the Development Form, and an EGG ending that is a real loss. |
| Soundtrack loop fatigue | Season and hour rotation, plus the Sound Test as progression. |
| Scope sprawl toward Angel Island | Nothing beyond the one-year game starts before the Signpost Spin works. |
| Original art creeping in | A hard rule: original art covers fish, food and icons, UI and one sleeping pose. Nothing with a face. |
| The arcade steals the show | The farm's movement must feel at least as good as the cabinets. Cabinets cost game time. |
| Save-state sprawl | Simulation lives in a few mod-owned models with one versioned codec. Save on sleep, plus a recoverable backup. |

## 12. Changes from the raw brainstorm, for feasibility

- **The real GHZ1 in the Inn cabinet** needs a scene-to-gameplay bridge that doesn't exist
  (§2). It becomes a GHZ Dash on the valley's own physics. The bridge is listed as a
  stretch engine capability.
- **"Real CNZ slots"** become CNZ slot art (optional S2) driven by the scene. They can't
  be the stock object.
- **Bear and monkey villagers** were described as S2/S3K. The survey shows they are S2
  only, so they are optional extras with fallbacks.
- **"Real Sonic physics"** in a scene means a creator controller ported from the ROM's
  movement routines. Starfall's controller is flat-only, so slopes, rolling and spin dash
  are new work. Loops are scripted.
- **Season music across three ROMs** needs the §3.3 mixing addition, because a prepared
  cross-ROM player silences driver SFX today.
- **Rewind adapters** were listed as a save risk. They don't apply to a scene mod, whose
  state is mod-owned. The real risk is save-state size and versioning.

## 13. Proposed next step: a look test before any systems

Spend about a day on a throwaway scene, captured with `ExampleModCapture`, to answer the
two questions that could overturn this design:

1. A composed farm terrace built from GHZ kit blocks, with tilled checker soil, three crop
   rows at different growth stages, the four seasonal palettes and dusk, at 400×224.
   Does it read as Green Hill **and** as a farm?
2. Sonic on the ported controller running the terrace, springing up a level and taking
   the loop. Does the walk between chores feel like Sonic?

Kill conditions: if the terrace reads as wallpaper or the single row looks thin, prototype
the back-row lane (D-lite) before building systems. If the controller doesn't feel right
on slopes, fix it before any farm system depends on it.

### Look test as built (2026-10-09, uncommitted, base `8668a9012`)

`examples/starpost-valley` with the `starpost.looktest` package (about 1,700 lines,
throwaway). The jar validates with zero findings. Both views share their art, crops, seasons,
time of day, farming actions and Green Hill's music. Tab switches between them.

- **Side view.** Green Hill act 1 blocks 13, 45, 60, 3, 60, 45, 53, 38 and 1 (waterfall,
  palms, field, totem ledge, field, palms, loop, slope, meadow), using the kit's own per-pixel
  collision. Flat floors are at the ROM's row 192 and the ledge at row 96.
  - Sonic uses a controller ported from `Sonic_Move`, `Sonic_RollSpeed`, `Sonic_Jump` and
    `Sonic_SpinDash`.
  - A tilled plot drops that column's grass lip and shows Green Hill's checker.
  - The loop is scripted. Block 53's primary-path collision holds only the entry ramp, the
    right inner wall and the top, so at speed Sonic rolls round it and exits past its right
    foot.
- **Belt view.** The same blocks stand upright at 1:1 as a back wall, with their floor line on
  screen row 136. In front is a field of Green Hill's three grass greens with four rows of
  plots, a shadow fading toward the viewer, and depth-sorted crops, props and Sonic (side-on,
  with a shadow).
- **Seasons** are colour maps over the ROM art, snapped to the engine's Mega Drive levels.
  **Dusk and night** are palette-mapped skies (sunset orange and pink, or navy) plus a
  multiply over the land.
- **Background.** The kit's backdrop is cut into Sonic 1's Green Hill parallax: three
  drifting cloud strips, mountains, hills, and water strips that move faster toward the
  viewer.
- **Footage** (outside the repository): `~/Videos/OGGF/starpost-valley/look-test/`
  - `side-vs-belt.png`: both views in spring, fall, winter, dusk and night;
  - `look-test-clip.mp4`: 21 seconds with Green Hill's music, made with `ExampleModCapture`
    from the stills/clip scripts.

Rejected during the look test:

- **Terrain cut into a tiled grass strip.** Replaced by whole kit blocks, which keep the
  zone's decoration, slopes and collision exactly.
- **A belt-view back wall scaled to two thirds.** Non-integer scaling distorts the ROM's
  pixels; the wall is drawn at 1:1.
- **Dusk and night as a translucent fill.** Over Green Hill's deep blue sky it read as
  purple; replaced by mapping the sky's palette.
- **13-pixel crops.** Unreadable next to Sonic; the ripe crops are now 20–24 pixels tall.
- **Static arrays for the crop pictures and names.** The validator rejects them
  (`STATIC_STATE_UNSUPPORTED`); they are now methods and instance fields.

Known look-test limits:

- The keyboard's default pad mapping binds only A (Space), so X farms directly.
- Prepared cross-ROM music silences sound effects (§3.3), so with music on the farming sounds
  are inaudible.
- The controller has no slope physics. Sonic follows slopes but they don't change his speed.

## 14. Build plan and architecture

The look test's throwaway package has been replaced by the game's own packages (base of the
skeleton: `5ddc5131b`). The source is `examples/starpost-valley/src/main/java/starpost/`.

| Package | Owns |
|---|---|
| `core` | Engine-free rules and state. `Calendar`, `Catalog` and `Content` (items, crops), `Inventory`, `Farm` and `Plot` (belt grid: 5 rows × 60 columns, 24 open at the start), `Game` (the save's root), `SaveCodec`, and `SaveSection` (one per further system). Tested without a ROM by `src/test/java/starpost/core`. |
| `art` | Everything from the ROMs. `Art` loads the Green Hill kit, characters, sprites and solidity. `Tone` does seasons and skies, `CropArt` the original crop pictures (5 stages), `ItemIcons` the icons (monitor screens for the shields), and `Anim` steps character animations. |
| `scene` | `StarpostScene` (the startup scene), `Shell` (screen stack with overlays, fades, saves, music), `PlayScreen` (both views, clock, HUD, the fold at the gate), menus, `DayEndScreen`, `TitleScreen`, `FarmerSelect`, `Debug` (capture commands), `Music`, `Sfx`. Also the extension points `Actor` and `Systems`. |
| `farm` | `FarmView` (the belt field) and `BeltRunner`. |
| `valley` | `Valley` (the side map, Green Hill blocks 13, 45, 60×4, 45, 3, 45, 53, 38, 1, 16, and its places), `ValleyView`, and `Runner` (the ported controller). |
| `ui` | `Controls` (one read of pad and keyboard per tick) and `Text`. |

Every further system plugs in through three seams, so lanes rarely edit the same files:

1. **Save state.** Implement `SaveSection` (its own key prefix, validated load, overnight
   work). List it in `Systems.sections`.
2. **Things in the world.** Implement `Actor` (a view, a position, update, draw, and
   interact on the action button). `Systems.install` adds actors to the play screen. Farm
   actors are depth-sorted with the crops.
3. **Doorways.** `Valley.places` names them, and `PlayScreen.places` maps an id to a
   handler, usually pushing a full-screen or overlay `Screen`.

Content is added through new `Content`-style registrars called from `Content.register`, never
static tables (the validator rejects them).

### Lead progress (commits `facc4b595`..`7218cfbff`)

Built on the skeleton, each step verified with `ExampleModCapture` stills and the creator rules
suite (23 engine-free tests at `94c3d883c`):

- **Valley life.** Rings and seasonal forage along the path every morning (each ring is one
  Momentum). The farm's back wall ends in Green Hill's loop: a lap at speed refills Momentum.
- **Presentation.**
  - The Sonic 1 HUD comes from the ROM (Nem_Hud labels, Art_Hud digits).
  - A title-card font is gathered from the S3K zone names.
  - The title screen.
  - The opening cutscene to Sonic 1's ending theme, played by the real game with scripted
    input.
  - The morning card, and the night tally in Sonic 1's own "SONIC HAS PASSED".
  - Buildings are assembled from Green Hill's pixels (`Facades`): sod roofs of the grass lip,
    checker walls, plank doors from block 6, windows of the lake.
- **Farm systems.**
  - Placeable objects: Buzz Bomber waterers, the Caterkiller Crawler, scarecrows, Item Monitor
    chests and Star Posts. Sprinkler coverage is computed before growth, so covered soil never
    grasses over.
  - Tails's workshop: recipes, the Water Shield tank, monitor slots, shields, the rod, land
    clearing.
  - The pond refills the Water Shield.
  - Badnik pests: Motobugs eat crops, and each one popped frees an animal and raises the
    valley's population.
- **Progress.**
  - The Great Capsule's chambers and bundles. Unknown items are left out until their system
    is installed.
  - The EGG store and Valley Development Form (renamed Robomart at `93eb0fb10`, after the
    user's naming: Joja → Robo, JojaMart → Robomart, Joja Cola → Robo Cola).
  - Five skills with professions at levels 5 and 10.
  - Weather: storms, snow, badnik swarms and the Emerald Aurora.
  - The Lamppost Inn's counter and jukebox (Records unlock tracks).
  - Options: music, a 14/20/28-minute day, and Momentum or stamina.
  - The year-two Signpost Spin and the credits.

Rejected or corrected along the way:

- **A scaled sprite.** A 0.75× Buzz Bomber smeared the ROM pixels; it is drawn at 1×.
- **A debug command parser.** `give` split on underscores, breaking item ids with underscores.
- **Menu layout.** Menus overprinted long names until they gained fit-to-width text.
- **A title-screen banner.** The S3K card's red banner carries the game's name at its foot;
  that part is painted over with the banner's red.
- **The intro's side effects.** It picked up forage and could fold back out through the gate;
  cutscenes now remove pickups, lock the gate, and hide the HUD and labels.

Lanes (lead plus a few at a time, each in its own worktree, merged into
`feature/ai-starpost-valley`):

| Lane | Scope |
|---|---|
| Lead | Integration, art direction (buildings assembled from Green Hill pieces, the Sonic 1 HUD, the title, the intro and ending), Momentum and the farm loop, crafting and Tails's upgrades, the Capsule and Robomart, the three farmers, weather, balance, captures, README, PR |
| Audio | The engine's background-music addition (§3.3) |
| People | `starpost.people`: the villager roster, schedules, picture speech, the translator, gifts, hearts, heart events, mail, Partners |
| Ruins | `starpost.ruins`: the Marble Ruins chambers from the Marble Zone kit, spin-jump combat, ores and minerals, Star Post elevators, Scrap Brain Depths |
| Waters and barns | Fishing (Bubble Bar, fish, badnik legends, the lake), animals and buildings on the farm, artisan machines, badnik automation, Flicky roosts |
| Festivals | `starpost.festivals`: the eight festivals and their games, the Signpost Board's requests, the trophy shelf |

### Integration and the farmers (`afc4d901e`..)

- **The three farmers (§4).** In the valley and the Ruins, `Runner.secondMove` gives Tails
  flight (`FLY_TIME` 480 frames, then tired) and Knuckles a glide that grabs walls to climb. On
  the farm, `core.Farmers` holds the differences as engine-free rules (`FarmersTest`). Sonic's
  spin dash (9) tills its whole roll. Tails's spin (6) tills three plots, but a Water Shield
  charge also waters the next plot on. Knuckles's spin is 8; his tilling is a dig that turns
  something up one time in eight (rings 5–20, a marble chip or the season's forage), he punches
  rocks without the Fire Shield, and he gets half the farm loop's Momentum. The brainstorm's "Tails
  carries two held items" was dropped: the hotbar has one selection, and a second would change
  every menu.
- **Merges.** Ruins `6d315491b`, People `358bd88c1`, audio `29e66cd79`, Waters and Barns
  `736f2a231`, Festivals `3a5dfeda8`. The shared hooks (`Content.register`, `Systems.sections`
  and `install`, `Debug`) were the only conflicts, resolved as unions. `Systems.install` hands
  the Ice Cap Festival the Waters lane's `FishingSystem::contest` through the Festivals lane's
  `FishingContest` seam. The board test that forbade fish requests was written before fishing
  existed; it now checks that a requested fish is a priced catch.
- **The soundtrack.** `Music` now calls `ctx.audio().playMusic(game, id)` (§3.3.1) for every
  track. Songs loop at their own loop points and sound effects play over them. The prepared-PCM
  player and its three-minute restart are gone.
- **A session limit stopped three lanes at once.** Audio and Waters had committed; Festivals
  had about an hour of uncommitted work. The lead compiled it, ran the creator tests (88/88),
  committed it as a checkpoint (`ab519c910`) and briefed a fresh agent to do the visual pass.
  Lesson for lanes: commit at each working milestone, not only at the end.
- **Scene smoke test.** `TestStarpostValleyScene` (engine suite, S3K plus Sonic 1) plays each
  farmer on the farm and in the valley, six Ruins chambers across the three zones, a talk, the
  social page and a heart event, the lake and the Bubble Bar, every festival, the board, a
  night's tally and the year's end. Everything goes through debug jumps with frames ticked and
  drawn. The fault boundary must catch nothing.
- **First-run playtest with real input (no debug jumps)** found four seams that only show when
  lanes meet:
  1. A new game's intro ends on the farm, where the Flicky post arrived and froze the cutscene
     behind a letter. The post now waits while the clock is held.
  2. The board's morning notice fired during the intro's fade and rode over the black morning
     card. Notices now wait out transitions.
  3. Pip standing beside the shipping signpost took the action button, so nothing could be
     shipped. The signpost answers first.
  4. Closing a menu with the action button left the key held into the farm, whose tap-on-release
     acted on the signpost and reopened its menu. A hold that began in a menu is now ignored.

  Also: the title band of the intro, the farmer-select lines, and labels drawn above the field
  (`Actor.drawOver`). Verified afterwards: title to farmer to intro to morning card to letters, a
  shipped tally (540 rings) and the next morning, and Continue loading the save. Capture
  scripts must read letters with confirm: the `close` debug command dismisses a letter unread, so
  the Flicky brings it again.
- **Knuckles's climbing** had never been seen: no valley wall stands at glide height, and the
  probed Ruins chambers had none either. `AbilitiesTest` drives the controller against a synthetic
  wall: glide, cling, climb, pull up, kick off.

## 15. People (lane)

Branch `feature/ai-starpost-people` (base `facc4b595`), package `starpost.people`. The valley's
neighbours from §7 with the kickoff decisions: Partners instead of romance, and animal villagers
who speak in pictures until the Chirp Translator.

### What is built

| Part | Where | Notes |
|---|---|---|
| Rules and save | `People` (the `people` `SaveSection`), `Bond`, `VillagerDef`, `Line`, `Situation` | 250 points a heart, ten hearts; villagers without a Partner arc stop at eight. Talking once a day +20; gifts by taste +80/+45/+20/-20/-40 (item entry, else the item's `Kind`, else neutral), one a day and two a week, eight times on a birthday (which does not use up the week). −2 a day without a word, except Partners. Tools are never gifts. |
| Cast | `cast/*.java`, one builder class per villager, `Cast` lists them | 14 neighbours: Sonic, Tails, Knuckles (two of them, whoever does not farm), Dr. Robotnik, Rusty, Pip, Dandel, Clementine, Pud, Barnaby, Frost, Hazel, the Elder Totem, and Moto the pet. About 25 daily lines each plus gift reactions, second-talk lines and thank-you notes (about 500 lines in all). |
| Schedules | `Schedule`, `Spot`, `Anchors`, `VillagerActor` | Plans by season, weekday, weather and story flag, most specific first, as timed stops (`at`, `inside`, `farm`). Anchors are `Valley.places` ids at run time (a moved building keeps its regulars), with fallback x positions for tests. Villagers walk along the valley floor, cross the farm gate to visit the belt-view farm, go indoors (not drawn), and come out of the same door. |
| Talking | `DialogueScreen`, `Speech`, `PeopleArt`, `Pictures`, `Glyphs` | The action button near a villager. The speaker's ROM sprite at 1x in a framed portrait, a name plate and typed, paged text. Holding a giftable item asks "GIVE THE … TO …?" first. Lines are chosen per day: special lines (first meeting, birthday, a date such as a festival eve, the week after a heart event) win; otherwise a weighted draw that favours rain, season, heart and farmer lines and avoids the last six said. |
| Picture speech | `Pictures`, `Glyphs` | Animals before the translator speak in a bubble of pictures shown one by one: item icons (`art.icons`), faces (head crops of ROM sprites; Sonic and Robotnik are the signpost's frames 3 and 0), the ROM's ring, Motobug and Flicky, and small original glyphs (heart, rain, sun, snow, moon, note, house, gift, clock...). Lines can author their pictures; otherwise they are read from the words (item names, villager names, a word list, then the punctuation's mood). |
| The translator | `People.TRANSLATOR` story flag in `Game.flags` | Set by Tails's 2-heart event (which also reprograms Moto, flag `moto_reprogrammed`). When Tails farms he is not a villager, so Sonic's 2-heart event (`sonic_2t`) has Tails build it at Sonic's prodding. |
| Heart events | `HeartEvent`, `Step`, `EventScreen` | Scripts of place/walk/move/face/emote/say/pause/give/flag/music/sfx/pose/hop/leave/fade, positioned relative to where the farmer stood, so a scene plays wherever it triggers. Triggered by standing near an anchor in the valley (or anywhere on the farm) in a time window, with enough hearts and the previous event seen; marked seen when it starts. Letterbox bars hide the HUD; the clock stops. 42 events (table below). |
| Mail | `Letter`, `cast/Mail.java`, `LetterScreen`, `PeopleSystem` | Each morning a Flicky flies in to the farmer with the day's letters (welcomes, Robotnik's offers and adverts, news, notes the morning after events, thank-you notes for loved gifts, Pip's birthday reminders the day before). Dated letters without `yearOne` come every year. Enclosures are given when read, or wait for room. |
| Partners | `VillagerDef.partner`, `People.morning` | Tails (10 hearts: +10 Water Shield charges each morning) and Clementine (a Chili Dog in your monitors each morning). |
| Social page | `SocialPage` | From the monitor slots (UP on the top row, or E): everyone met with hearts (partial fill toward the next), birthday, gifts this week, today's talk, Partner badge, the selected neighbour's line and discovered loved gifts. |
| Debug | `PeopleDebug` (`jump=people_...`) | `hearts ID|all N`, `partner ID`, `translator on|off`, `talk ID`, `event ID`, `social`, `mail ID`, `hold ID X [DEPTH] [left]`, `release ID`, `pose ID NAME`, `snap`. |

Bodies are ROM sprites only: the S3K heroes (Tails with his tails behind him while standing,
`Obj_Tails_Tail_AniSelection`'s swish frames $22–$26), the S3K Egg Robo, Sonic 1's on-foot Eggman
(`ArtNem_SBZ2_Eggman` $5E4CE / `Map_SEgg` $1A1E4 in `Pal_Sonic` $2380 plus `Pal_SBZ2` $2660;
running is `Ani_SEgg`'s 7, 4, 8, 4), Sonic 1's freed animals at their native 16×24 (`Map_Animal`
frames 0–1 to hop or flap, 2 to stand), Sonic 1's Motobug and Green Hill's totem pole in the
season's colours.

### Heart events

| Villager | 2 | 4 | 6 | 8 | 10 |
|---|---|---|---|---|---|
| Tails | Chirp Translator, Moto reprogrammed | Buzz Bomber sprinkler blueprint (farm) | The Tornado's engine humming in the Ruins | Under the wing at night: "I'm scared you'll leave" | The engine roars: Partner |
| Clementine | Second breakfast, radish stew recipe | The Lamppost Cookbook | The Chili Dog recipe (half Fire Shield Pepper) | At the Capsule: the night it opened; she stays | Breakfast at dawn on the farm: Partner |
| Dr. Robotnik | The salesman: free Egg-plant seeds | Tries to buy the farm (farm) | Appoints himself Fair judge: Eggman Pumpkin seeds | "Badniks were meant to be rides" blueprint | — (out of scope) |
| Pip | Moves into the signpost (farm) | Her mother's lost letter in the waterfall | Organises the Night of the Flickies | Will lead the migration and come back (farm) | — |
| Dandel | A slow day: free radish seeds | Sells the farm's crops | Refuses Robotnik's buyout | The stall becomes the co-op | — |
| Knuckles | Suspects the farmer | A dig test in the meadow | A digging lesson and island grape seeds (farm) | Lonely on the ledge at night | — |
| Sonic | Translator (Tails farms) / loop dare (Knuckles farms); the valley at speed | Chili dogs on the Inn's porch | | | |
| Rusty, Pud, Barnaby, Frost, Hazel, Elder Totem | Day off; the lamp; fishing lesson; the cold spot; a race; the meadow's memory | The Star Post chimes; into the Ruins' mouth; the hat story; homesick for Ice Cap; a missing relic; a promise and totem leeks | | | |

Events leave story flags for other systems: `blueprint_buzz_sprinkler`, `blueprint_motobug_ride`,
`recipe_radish_stew`, `recipe_chili_dog`, `lamppost_cookbook`, `pud_lamp`, `pud_brave`,
`barnaby_fishing_lesson`, `red_chopper_story`, `knuckles_dig_lesson`, `seed_coop`,
`starposts_repaired`, `rusty_day_off` (changes his Sundays), `tornado_engine_heard`,
`museum_relic_missing`, `partner_tails`, `partner_clementine`, `inn_kitchen_garden`.

### Seams touched outside the package

- `Systems.sections` adds `new People()`; `Systems.install` calls `PeopleSystem.install`.
- `Debug` routes `people ...` to `PeopleDebug`.
- `InventoryMenu`: UP on the top row (or E) opens the social page, with a hint line.

Notes for the lead: `DayEndScreen` stops the signpost on `Map_Sign` frame 3, which is the
third spin frame; Sonic's face is frame 4 (`.sonic`). `Debug` splits commands on underscores, so
ids with underscores need rejoining (`PeopleDebug.merge` does it for people commands; core
`give_ring_radish_1` cannot reach `ring_radish`).

### Decisions and rejected approaches

- **Hero worship needs a hero.** Lines that only make sense for one farmer are conditioned with
  `farmer(...)` (`sayIf` in events); a test talks to Tails for forty days with Knuckles farming and
  checks no Sonic-only line appears.
- **Pictures read from words, authored where it matters.** Authoring a picture version for every
  animal line doubles the writing; reading them from item and villager names gives every line
  pictures (tested), and the key lines (events, gifts, greetings) carry authored ones.
- **Events triggered by approach, not by doorways.** The doorways' handlers belong to other
  systems (the Inn, the shop), so events fire when the farmer stands within a radius of an anchor.
- **No static tables.** Every list (glyph grids, keyword pictures, anchors) is a method returning a
  new value or a `switch`, for the mod validator.
- **Picture speech at 2x.** At 1x the 16-pixel icons were hard to read in a 400-pixel bubble
  (look-test capture of Dandel's greeting); icons and glyphs now draw at 2x, faces and signs
  (already 22–32 pixels) at 1x. The speakers' portraits stay at 1x as the brief asks.
- **Names under the feet.** Name labels over villagers' heads collided with the doorway labels
  ("UP: DANDEL'S SEEDS") and with each other; only the nearest villager is named, on the
  ground beneath it.
- **The Egg Robo is three objects.** `Map_EggRobo` frame 0 is empty; Rusty is the body (frame 1)
  plus the jet flame child (frames 4–6) at `ChildObjDat_919D0`'s offset, without the gun arm.
- **Not built:** S2's bear and monkey, Partner cabins on the farm, 10-heart events beyond Tails
  and Clementine (Robotnik's is full-game scope), choices inside events, and gifts or talk with
  villagers who are indoors.

### Tests

`src/test/java/starpost/people/PeopleRulesTest.java` (engine-free): friendship and caps,
tastes and kind overrides, the daily and weekly gift limits and birthdays, fading and Partners,
every schedule resolving every ten minutes of every kind of day to a known spot, heroes absent
when they farm and Knuckles arriving in Summer, events firing only in their place and time and
only once, the translator switching speech mode (and every animal line having pictures), the cast
having its lines, reactions and events, line variety and farmer-specific lines, the morning post
and Partner perks, the section's round trip through `SaveCodec`, and damaged input rejected or
clamped.

```
# fast creator tests through CreatorTestLauncher (a throwaway runner outside the repository)
java -cp "$R/out:$CP" RunCreatorTests $R/tests $R/main        # 24 tests (9 core, 15 people), all pass
# captures (ExampleModCapture, scripts use jump=people_...); validation: "Validation passed: 0 findings"
java -cp "$CP" com.openggf.mods.code.ExampleModCapture --rom "$PWD/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  --mod examples/starpost-valley --out <dir> --script-file <script> --every 15 --ticks 430
python3 tools/testing/maven_queue.py --lean -B -Dmse=off -Dtest=TestStarpostValleyExample test
```

A mutation check (Partners fading; first-meeting lines always matching) turned two tests red, so
the rules tests do assert what they claim.

## 16. Ruins (lane)

Branch `feature/ai-starpost-ruins` (base `facc4b595`), package `starpost.ruins`. The valley's
`ruins` doorway opens the Marble Ruins: forty side-view chambers on the ported Sonic controller,
bands 1–15 Marble Zone, 16–30 Labyrinth Zone, 31–40 Scrap Brain Zone, with the zones' music
(S1 `$83`, `$82`, `$86`).

### Chambers

- **Kits.** `RuinsArt` loads S1 public zones 1 (MZ), 3 (LZ) and 5 (SBZ), acts 1–3 (SBZ act 3 is
  Labyrinth-built and unused). `Kit` abstracts a kit (layout, per-block collision, picture opacity)
  so generation and its checks run without a ROM; `RomKit` wraps `SceneLevelKit`.
- **Window.** A chamber is 2–4 blocks wide and 1–2 high (one or two screens), copied from the whole
  stock layout. Each next column continues the act or, 40% of the time, jumps to a column whose
  pair with the previous one occurs, row for row, side by side in the stock layout; remaining
  seam cells are repaired with a block seen next to both neighbours and above/below (Eggman's
  Sky's remixer at chamber scale). Windows with floor under less than 70% of the width or under
  8% / over 80% collision are refused.
- **Traversal check (`Reach`).** Standing spots every 8 pixels; from each reached spot the real
  `Runner` plus `Chamber.afterStep` (springs, water) plays eleven input programs each way (walk,
  walk off and drop, held/short/late/straight/reversed jumps, short and long run-up jumps, spin
  dash, dash jump). Every spot stood on is reached; lava and spikes end a program; falling out of
  the bottom reaches the shaft down (a pit drops you one chamber deeper). Up to 30 windows are
  tried with up to three entries each, scored by reached spots, reached width and the farthest
  flat spot; play hugging the top edge (median reached height under 100) is scored down.
- **Placement.** The exit hatch goes on the farthest flat reached spot, so every exit is reachable
  by construction; a yellow spring is added where it opens at least a seventh more of the room
  (two at most). Then badniks, rocks, a ring monitor (20%), a ring trail that follows the found
  route's floors and arcs over its jumps, a few extra ring groups, spikes from chamber 4 (kept only
  if the exit, elevator and every placed thing stay reached), and air-bubble vents under water.
  Chamber seed = mix(section seed, day number, chamber): the same all day, new each morning.
- **Landmarks** (every fifth chamber; S1 windows chosen by eye from kit surveys, hand-picked entry
  and find points, Star Post elevator placed by the check): 5 The Broken Temple (MZ2, Lava Ruby),
  10 The Battlements (MZ1, Pud's Super Sunflower seed), 15 The Pillared Shrine (MZ3, Record:
  Marble), 20 The Crystal Gallery (LZ1, water at 300, Tide Sapphire), 25 The Drowned Tunnel (LZ3,
  flooded, Record: Labyrinth), 30 The Golden Stair (LZ3, Emerald Shard), 35 The Conveyor Hall
  (SBZ2, Record: Invincibility), 40 The Sealed Depths (SBZ1, Records: Final Zone and Boss; no way
  down yet — Scrap Brain Depths). A kit without the window falls back to a generated chamber with
  the same elevator and finds.
- **Marble lava.** The kit draws animated tiles at their first frame, which leaves MZ's lava blank
  while its top-solid collision stays (S1 stands Sonic on lava; a Lava Tag hurts him). Runs of
  floor with no picture at least 16 pixels wide, below row 40, become lava down to the bed beneath
  and are drawn from `Art_MzLava1` (surface, 3 frames, 20 frames each, palette line 3) over
  `Art_MzLava2` (magma), as `AniArt_MZ` animates them.

### Rules (s1disasm routines)

- **Rings are health.** Rings collected in the Ruins are *in hand* (banked into the wallet on
  leaving or when the day runs out). A hit scatters them as `RLoss_Count` does (at most 32,
  mirrored pairs from spread `$288`: sixteen at boost 2, sixteen at boost 1), bouncing per
  `RLoss_Bounce` (`$18` gravity, floor check every fourth frame, a quarter lost per bounce) for the
  shared 255-frame timer (FixBugs=0) — about 4.3 s, the ROM's figure rather than the brief's
  "about 3". `HurtSonic` knock-back (`-$400`/`$200`, under water `-$200`/`$100`), hurt gravity
  `$30`, 120 frames of flashing, no ring collection above 90 (`ReactToItem`). A hit with none in
  hand faints: the S3K death leap, up to three non-tool stacks lose half, then `DayEndScreen(true)`
  (the night's own 10% wallet loss).
- **Bopping.** Rolling, jumping, spin-dashing or fire-dashing into a badnik pops it (S3K
  explosion, `$B4`), with `React_Enemy`'s bounce; it frees one of its zone's two animals
  (`Anml_VarIndex`: MZ squirrel/seal, LZ penguin/seal, SBZ rabbit/chicken) who hops away left at
  `Anml_Variables` speeds, gives +3 Momentum and drops Scrap 35% of the time. Badniks: MZ Batbrain,
  Caterkiller, Buzz Bomber, Yadrin; LZ Jaws, Burrobot, Orbinaut; SBZ Caterkiller, Bomb, Ball Hog,
  with each object's collision size and speeds. The Bomb cannot be popped (`col_hurt`), the
  Caterkiller's body and the Orbinaut's spike balls always hurt, and the Yadrin's back hurts from
  above. Lava (unless lava is no threat) and spikes hurt too.
- **Water** (Labyrinth: a line at 30–80% of the room, or flooded 25% of the time).
  `Sonic_Water`: top speed, acceleration and deceleration halved, x speed halved and y speed
  quartered on entry, y doubled (capped `-$1000`) on exit; jump `$380`, release cap `$200`, gravity
  `$10`. `Drown_Countdown`: 30 seconds, a ding at 25/20/15, S1's drowning music `$92` from 12,
  number bubbles (`Map_Bub` frames `$E`–`$12`), large vent bubbles refill air. Below the line
  blocks are recoloured entry by entry to `Pal_LZWater`.
- **Shields and finds.** Water Shield held: no air loss (S3K Bubble Shield). Fire Shield held:
  lava is harmless, a second jump press is S3K's `$800` dash, and rocks burn for double yield;
  eating a Fire Shield Pepper sets `ruins.lava_immune` until morning. Lightning Shield held: rings
  within 64 pixels fly in. A carried Tide Sapphire is spent to save you from drowning; a carried
  Spark Topaz makes every popped badnik drop Scrap.
- **Rocks** (MZ smashable green block, else the S1 purple rock) break when rolled into at 3 px a
  frame or more (2 Momentum) or burned with the Fire Shield (3, double yield); yields per band:
  marble chips or scrap always, then Marble Ore, the band's gem, its geode, Emerald Shards (1–2%)
  and, once, the band's Record (0.8%).
- **Items** (`RuinsContent`, called from `Content.register`): Marble Ore, Lava Ruby, Tide Sapphire,
  Spark Topaz, Emerald Shard, three geodes, seven Records (flag `record.s1.<id>` on pickup;
  `RuinsContent.recordSong` maps them to S1 songs for the Sound Test) and Pud's
  `super_sunflower_seeds` (registered as a relic until the farm defines its crop). Icons come from
  `RuinsIcons` through the new `ItemIcons.Source` hook.
- **Progress.** `RuinsSection` (`ruins.deepest`, `seed`, `popped`, `freed`, `best`, `seedFound`;
  validated on load; `freed` is the Ruins' share of the valley population). Touching a landmark's
  Star Post records it; at the doorway the elevator offers chamber 1 and every reached landmark.
  Leave by the shaft of light at each entry or by riding an elevator (`shell.go(play)`); when the
  day runs out Sonic passes out in the Ruins straight to `DayEndScreen(true)`, keeping his rings
  (going to `play` first would show the valley for a frame before its own overtime check faded out: `Shell` starts the second fade from full brightness).

### Seams outside the package

`Catalog.add` public; `Content.register` calls `RuinsContent`; `ItemIcons.Source`/`addSource`/
`picture`; `Systems.sections` adds `RuinsSection`, `Systems.install` calls `RuinsSystem.install`
(which overrides the `ruins` toast handler); `DayEndScreen` and `InventoryMenu` public;
`PlayScreen.drawHotbar` public; `InventoryMenu` sets the lava flag when a Fire Shield Pepper is
eaten; `Debug` forwards `ruins …` (`ruins N`, `rings N`, `hit`, `spawn KIND DX`, `goto
exit|elevator|monitor|rock|entry`, `at X Y`, `state`, `deepest N`, `elevator`). `Runner` gains
`Ground.ceiling` (default none), S1 water physics, `knockBack`/`hurt` and the jump headroom check;
with no ceilings and dry, its arithmetic is unchanged for the valley.

### Rejected on the way (evidence from `ChamberProbe` and captures)

- Windows from `playableArea()`: S1 MZ1's area is 688 pixels tall (its opening camera bounds), which
  cut off the whole underground; the full layout is used.
- Every floor with air above as a standing spot: S1 gives brick masses collision only at their
  edges, so spots appeared inside walls and became entries; spots now need visible open air.
- Lava as a thin band at the surface: MZ3's lake bed lies 46 pixels under the surface, so spots on
  the bed counted as safe and an entry was placed inside the lava; lava now reaches the bed.
- Ring trails from the route's parent links: one walk program links entry to exit in a single hop,
  so trails became arcs through walls; trails now follow the floor column by column.
- Exit on the farthest reached spot: on LZ slopes it fell back next to the entry (chamber 16: 27 of
  63 spots, exit beside the entry); exits and scoring now use flat spots only.
- Scattered rings collected on the hit frame: the collect check was read before the hit, so all
  rings came straight back; it is read after.

### Tests and captures

- Creator tests (`src/test/java/starpost/ruins`, ROM-free on `TestKit`): chambers 1–40 identical on
  regeneration and their exit, elevator and every placed thing reached by an independent traversal;
  most chambers change overnight; a 120-pixel wall stops the check and a spring clears it; lava
  found where floor has no picture and avoided; ring burst, bounces, hit/faint, faint losses, ore
  yields, air, shields, content, and the section's round trip and damage clamping. 25/25 with the
  core tests (RunCreatorTests).
- ROM probe (scratch `ChamberProbe` over `RomKit`): seeds 12345, 777 and 31337 on days 0 and 5, all
  240 chambers built, every exit and elevator reached, at most 126 ms per chamber.
- `TestStarpostValleyExample` and `ExampleModCapture` (validation 0 findings); captures in
  `~/scratch/sv-ruins/final/`.

Not done: Scrap Brain Depths, pushable blocks and electric beams, solid rocks (rocks are
non-solid so they never block the route), slope speed, Knuckles's and Tails's own moves in the
Ruins, and Pud's lamp (People lane).

## 17. Waters and barns (lane)

Branch `feature/ai-starpost-waters` (base `93eb0fb10`), packages `starpost.fishing` and
`starpost.barn`: fishing (§6.6), animals and their buildings (§6.1, §6.7), the artisan machines
(§6.8) and the Flicky Roost (§6.1, §9.11).

### Fishing

| Part | Where | Notes |
|---|---|---|
| Who bites | `FishDef`, `FishTable` | 16 original fish and 5 submerged badniks by spot (farm pond, Waterfall Lake), season, hour, weather (dry, wet, storm, snow, the morning after a swarm, the Emerald Aurora), cast depth and story flag. Robo Cola is the junk catch (12% at the pond, 7% at the lake, always from empty water). Deep casts scale badnik weights by half plus the depth. The Reef bundles' ids (`bubble_bass`, `loop_pike`, `ring_carp`, `chopper_shell`, `jaws_fin`) are all here. |
| The legends | `FishTable`, `Fishing.land` | Sonic 1's Chopper (pond and lake) and Jaws (lake, fall and winter), S3K's Jawz (rain) and Blastoid (storms), and the Red Chopper: lake, summer or fall mornings, deep casts, once a game, only after Barnaby has told the hat story (`red_chopper_story`). Landing one pops it: an animal goes free (`game.free()`) and its shell is kept; the Red Chopper also returns Barnaby's hat (he loves it). |
| Catches that do things | `Fishing.land` | Ring Carp +5 rings, Bubble Bass +5 Water Shield charges, Scrap Sucker +1 scrap, Emerald Koi +30 Momentum; experience by difficulty, half again for a perfect catch. Badnik shells carry a `badnik:` icon key so the Reef Hand profession (+50%) pays for them in `Game.sellPrice`; the Angler's +25% covers every catch. |
| The Bubble Bar | `BubbleBar` (rules), `BubbleBarScreen` | Hold to rise, release to sink. Inside the bubble the catch reels in; outside, tension builds, the bubble shrinks (up to half) and the catch slips; 45 frames' grace while the hook sets. The bubble is Map_Bub's full bubble with its top and bottom halves at 1x and its middle row repeated, giving way to frames 5 and 4 as it shrinks. Badniks move as their objects do, at the column's scale: `Chop_ChgSpeed` (launch -$700, gravity $18: a leap every 149 frames, scaled so a full leap reaches the top; the Red Chopper varies the height and darts between leaps), `Jaws_Swim` (constant speed, turning every 64 frames per subtype), `Obj_Jawz` ($200, aimed at the bubble and never steered), `AniRaw_BlastoidAttack` (128 frames' wait, three shots 15 frames apart, each kicking it up). On a badnik the drowning countdown's digits (Map_Bub 14-18) count down over the bubble with `sfx_AirDing` as the catch slips, as theatre. Barnaby's two-heart lesson (`barnaby_fishing_lesson`) and Fishing levels make the bubble bigger. |
| The pond | `PondLine` (actor) | With the rod, the action button at the pond's edge casts (the new `FarmView.pondAction` hook; the Water Shield still refills there). The bobber arcs in and bobs; Labyrinth's splash (Nem_Splash, Map_Splash) marks the landing and the bite; walking off reels in. |
| Waterfall Lake | `LakeScreen` | The valley's `lake` doorway. Green Hill blocks 1 (the shore), 51 (the log bridge over its pool: Barnaby's jetty) and 52 (a waterfall), with their collision; the water shimmers with `PalCycle_GHZ` (Pal_GHZCyc's four steps into line 3, colours 8-11, every 6 frames). Hold the action button to wind up a cast; the throw sets the depth. Fish shadows drift under the surface; Barnaby (Sonic 1's seal) sits on the jetty when People's own schedule puts him there; falling in sends the farmer back to the shore. Once the Capsule's Reef chamber sets `lake_bridge`, Sonic 1's bridge logs (Map_Bri frame 0) run from the jetty to the falls. The clock runs and the day can end there. |
| Fishing contest | `FishingSystem.contest`, `LakeScreen` | A festival's timed contest (the Festivals lane's Ice Cap Festival hook has the same shape: `FishingSystem::contest`): the lake with a lent rod, the day's clock still, bites twice as soon, points per catch (5 plus a fifth of its difficulty, 10 more for a badnik, none for junk) on a board over the lake; at the whistle (or on walking off) the score is handed back once and the screen returns to the play screen. |
| Save | `FishingSection` (`fishing`) | Landed counts per catch; unknown ids and bad numbers are dropped, counts clamped, once-only catches kept at one. |

### Animals and barns

| Part | Where | Notes |
|---|---|---|
| Buildings | `BarnSystem.workshopOffers`, `BuildingActor`, `BarnArt` | The Cucky Coop (2,000 rings) and Pocky Pen (4,000) are Tails's workshop offers; the Big Coop and Big Pen need `big_coop` (the Hatchery chamber or Robomart) and Hill Cloth. They stand on the back wall behind columns 25-46, assembled from Green Hill's pixels like the town (checker walls, sod roofs, log stilts, ramps and fence, plank doors), with Sonic 1's Cucky or Pocky standing on a plank sign. Up or the action button at the door opens the house's menu; goods wait by the door. |
| Animals | `Animals`, `Animal`, `AnimalActor` | Cucky (eggs daily), Pecky (Ice Eggs every other day, winter only), Pocky (fluff every three days, two for a Shepherd), Picky (Hill Truffles dug into open grass on dry days outside winter, picked up as `TruffleActor`s), Rocky (up to two, in the farm pond, a pond fish a day, handed over when petted). Bought at the coop or pen; 4 to a small house, 8 to a big one. They wander before their house on dry days (Peckies in snow too) from 07:00 to 19:30, Sonic 1's sprites at 1x (Map_Animal 0-1 hopping, 2 standing); Rocky swims with only his top half showing. |
| Rules | `Barn` (`barn` section) | Overnight: animals fed yesterday may give when grown and due, with a chance of 50% plus up to 50% from affection (two at once now and then above four hearts); affection drifts (-4 without a pet, -20 hungry, +3 in a big house); then each eats for the new day by grazing (dry weather outside winter, four plots of open grass each) or from its hopper (a fibre a day; in the coop a sunflower is three days, the sunflower's own rule). Petting once a day: +15, +30 for a Cuddler. |
| Artisan machines | `Artisan`, core `Machine` | Monitor Jar (a crop or forage, three days, a jar worth twice the input plus 50; drawn as S3K's monitor, its screen static when empty and showing its item when loaded), Spring Yard Keg (fruit fizz in five days at three times the fruit; Spring Yard Hops make Spring Yard Fizz in two; its yellow spring bounces when ready), Fluff Loom (Hill Cloth overnight), Sunflower Press (oil overnight, Truffle Oil in two days). Sunflower oil dabbed on a working machine finishes it a day sooner. Jars and fizzes are generated per crop and forage from the catalogue (Kind ARTISAN). |
| Flicky Roost | `Barn.harvestRoosts`, `FlickyFlock` | Recipe offered once `flicky_roost` is set. Each morning it picks ripe crops within 6 columns on its row into its 12-slot basket (chest storage, opened with the action button); its four blue Flickies (S3K Map_Animals1) fly each crop home, then circle the roof in `Obj_SuperTailsBirds`' formation (four birds a quarter turn apart, the angle advancing 2 of 256 a frame, aiming at sine/8 across and cosine/16 down from a point $20 above, accelerating $20 a frame and four times that to turn, vertical speed capped at $1000, wings every second frame). At night and in bad weather they perch. |

### Seams outside the packages

- `core`: `Content.register` calls `FishingContent` and `BarnContent`; `Game.sellPrice` applies Reef
  Hand to `badnik:` icon keys; `PlaceableDef` gains `MACHINE` and `ROOST` and `slots()`; `Farm.machines`
  holds `Machine` work; `SaveCodec` writes `machine.r.c` lines (kept only on a plot holding a
  machine, with known items, count and day clamped) and restores chest contents into any object with
  `slots()` (roost baskets).
- `farm/FarmView`: `pondAction`, and `objectHooks` (`ObjectHook.use`, `removable`, `draw`); a loaded
  machine or full basket is not knocked loose ("EMPTY IT FIRST").
- `scene`: `Systems.sections`, `install` and the new `workshopOffers` with the `WorkshopOffer`
  record; `WorkshopMenu` lists system offers and now honours `Recipe.unlock`; `PlayScreen.drawHud`
  public; `Debug` routes `fish ...` and `barn ...`.
- `people/cast/Barnaby`: loves `barnabys_hat`. `LakeScreen` reads Barnaby's schedule through People's
  public API.

### Decisions and rejected approaches

- **Peckies and Pickies in the plain coop and pen.** §6.1 puts the Pecky behind the coop's second
  upgrade, but the Hatchery bundle that grants `big_coop` asks for Ice Eggs and Hill Truffles: gated
  animals would deadlock the chamber (only Robomart's 20,000-ring form could break it). The upgrade
  gives room and comfort instead.
- **The lake's pool from one column.** Block 51's water is see-through stripes over the background;
  scanning one column for its first pixel ran to row ~240, so the lake showed Green Hill's background
  hills and block 52's ground through the water (pixel samples alternated water and ground colours).
  The pool now starts at the first row a quarter drawn, lies on a bed of its darkest water colour,
  and the waterfall stops at it.
- **A roost under its own roof.** The grass lip used as a sod roof is 24 pixels tall and hid the
  16-pixel walls, so the first roost vanished into the field; the walls now sit below the roof.
- **A held button after the Bubble Bar.** FarmView acts on the release of a short press, so the hold
  carried over from the bar cast a new line after every escape (a capture showed a bobber in flight
  after "IT GOT AWAY"); the pond ignores casts for 30 frames after a fight.
- **The Red Chopper overhead.** Held up at twice the size above the farmer it left the screen on the
  jetty; it now stands beside him.
- **The Flickies' formation.** The brief names the S3K ending; the ending's flock was not found in the
  disassembly, so the flock uses S3K's own Flicky formation, Super Tails's birds, with its numbers.
- **Fish movement.** Fish seek targets on an original model (no ROM analogue); only the badniks use
  their objects' motions.

### Tests and captures

`src/test/java/starpost/fishing/FishingRulesTest.java` (19) and
`src/test/java/starpost/barn/BarnRulesTest.java` (16), engine-free: the table's size and the
bundles' ids, the Reef and Hatchery chambers now fillable, bites by spot, season, hour, weather,
aurora and depth, deep casts favouring badniks, the Red Chopper's story, morning and once-only rules,
junk rates, a careful hand landing easy fish while an idle one loses them, harder fish escaping more,
tension and the hook's grace, the bubble rising and sinking, the Chopper's 149-frame leap, the Jawz's
$200 charge, the bubble's skill and lesson growth, the line's flight, bite, window and miss, each
catch's rule, badniks freeing animals and the hat, Angler and Reef Hand prices, contest points;
buying and room,
laying and hunger, grazing, hoppers and sunflowers, fluff timing with the Shepherd, Ice Eggs and
truffles by season and weather, Rocky's catch, petting, Cuddlers and big houses, collecting, each
machine's recipes and timing, oil, the roost's reach and basket, and the sections' and machines'
round trips with damaged values clamped or dropped and malformed numbers rejecting the save. 89/89
with the other lanes' tests; a mutation check (fluff every four days, Chopper gravity $20, roost
reach one wider) turned three tests red.

```
# fast creator tests (RunCreatorTests, a throwaway runner outside the repository)
java -cp "$R/out:$CP" RunCreatorTests $R/tests $R/main
# captures (ExampleModCapture; jump=fish_..., jump=barn_...); validation: "Validation passed: 0 findings"
java -cp "$CP" com.openggf.mods.code.ExampleModCapture --rom "$PWD/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  --mod examples/starpost-valley --out <dir> --script-file <script> --every 10 --ticks 540
python3 tools/testing/maven_queue.py --lean -B -Dmse=off -Dtest=TestStarpostValleyExample test
```

Captures in `~/scratch/sv-waters/final/` (`contact-sheet.png`).

Not done: Rocky's Pool, crab pots, bait and lures (the Trapper, Pot Master and Lure Maker
professions do nothing yet), the river, the Labyrinth Cistern and Angel Island's shore, the Egg
Machine and Scrap Brain Furnace, the Ricky Roost and Buzz Hive, S2's bear and monkey, naming
animals, a collection page for catches (the section counts them), and talking to Barnaby at the lake
(he talks in the valley).

## 18. Festivals and the board (lane)

Branch `feature/ai-starpost-festivals`, package `starpost.festivals`: the year's eight festivals
(§8), the Signpost Board (§6.8), prizes, records and the trophy shelf. The first agent's session
ended before its visual pass; its work was checkpointed by the lead (`ab519c910`), and a second
pass played every festival in captures and fixed what looked wrong (`9af6907eb`).

### Festivals

Each festival is a `FestivalScreen` over the day's `PlayScreen`: the clock stops and the HUD hides
behind letterbox bars, a title card in the S3K lettering opens it, a results panel closes it, and
leaving moves the clock on by the festival's length (`Festival.after`, never past 1AM) and goes
back to the same play screen. On the day the valley gathers by the festival's sign from half an
hour before it opens (`Festivals` is a `People.Gathering`); walking up during the posted hours
invites the farmer (`Ask`). Every festival is played once a year (`Festivals.joined`).

| Festival | Date, hours, length | What is played | Prizes |
|---|---|---|---|
| Ring Hunt (`RingHunt`) | Spring 13, 9AM-2PM, plaza, 2 h | 60 s in the town on the real controller to S1's Special Stage music (`$89`): lines, arcs and high lines along the street (under the name boards), a row on the totem ledge and a column over the spring, laid out per year. The champion, Tails (Sonic when Tails farms), flies to the nearest ring (`Map_Tails` `$A0` with the tail object's `$27`-`$28`) and stops 90 ticks to count each; ties go to him. The festival draws the crowd itself so the champion is not also standing by the sign. | 10 rings a ring; first win the Special Stage Record and a gold ring on the shelf, later wins +500 |
| Sunflower Parade (`Parade`) | Spring 24, 10AM-3PM, plaza, 3 h | Green Hill's big flower (`Art_GhzFlower1`, the two frames `AniArt_GHZ` swaps every 16) along the street, petals cycling through four colours; the valley marches with flowers to Mushroom Hill 1; Dandel judges the farmer's flower against Clementine (76), Pud (62, painted gold), Hazel (41) and Robotnik's Robomart plastic one (disqualified). | First win Pud's Super Sunflower seed, the shelf's sunflower and Dandel's thanks; later +500; second +200; else 3 sunflower seeds |
| Great Valley Race (`Race`, `RaceTrack`) | Summer 11, 9AM-1PM, plaza, 2.5 h | Twice round a Green Hill circuit of act 1's own blocks with their collision, on the ported controller, with the scripted loops and the totem ledge's spring, to the Knuckles theme. Tails or Sonic spin dash off the line, Knuckles runs at 0.98 pace, Robotnik flies the Egg Mobile (`Map_RobotnikShip` frames 5 and 2, flame 6) at 5.4 a tick and boosts to 8 once a lap when 300 behind. The signpost spins for the first across. | 1000/400/200/50; first win the Speed Shoes (+20 Momentum for good) and a Star Post on the shelf |
| Night of the Flickies (`FlickyNight`) | Summer 28, 8PM-12AM, meadow, 2.5 h | Waves of V formations of S1 and S3K Flickies over the lake, as many as the valley's population (at least six), Pip leading; the action button under a passing wave brings one down to circle the farmer. A moon and stars are painted into the night backdrop. S3K's ending music. | No winner: friendship with everyone and more with Pip; the first year the Migration Record |
| Valley Fair (`Fair`) | Fall 16, 9AM-4PM, plaza, 4 h | Three booths along the town as doorways, to Carnival Night 1. The grange display (nine places; 6 a kind, 3 an item, value up to 35, 5 for a full table, 12 for each Robomart good) judged by Robotnik against his own hamper (78): first only if undeniable (93+). The slot booth on Sonic 2's reel strips and `SlotMachine_ChooseReward` (×5 rings, 25 a spin, three Robotniks take 100), Casino Night's faces with S2, else Sonic 3's. The spring test: stop the meter, the spring launches the farmer under Sonic's gravity, the bell at 400. Calling the judge ends the fair. | 1500 (then 800)/300/100 and a bumper on the shelf; the first triple the Slot Bonus Record; the bell 120 rings and, once, a chili dog |
| Scrap Brain Night (`Maze`) | Fall 27, 7PM-12AM, plaza, 2 h | A perfect 13×7 maze per year and farm, from above, in Scrap Brain act 1's steel, dark but for a pool of light; Caterkiller shadows give frights (3 rings dropped); dead ends hold rings or scrap; 90 s; Mecha Sonic waits at the exit and lunges (S3K boss music). | 10 rings a ring, 300 for getting out; the first time the Death Egg Record and Mecha Sonic on the shelf |
| Ice Cap Festival (`Snowboard`, `IceCapScreen`) | Winter 8, 9AM-3PM, meadow, 3 h | A choice: the Waters lane's fishing contest (`FishingSystem.contest`, 120 s at the lake, against Frost's catch record of 40) or the snowboard run down the year's course of open-air winter blocks on Sonic's Ice Cap board (`ArtUnc_SonicSnowboard`; Tails and Knuckles crouch on the empty board), to Ice Cap 1: jump the rocks, spin in the air for tricks, against Frost's 900. The contest comes back to the meadow for Frost's verdict. | Points or rings ×5 rings; beating Frost the first time the S3 Ice Cap Record, a snowboard on the shelf and Frost's +150, later +600 |
| Star Light Feast (`Feast`) | Winter 25, 5PM-11PM, plaza, 4 h | Star Light Zone's sky (S1's level-kit backdrop) and music over the plaza, Clementine's long table, the secret friend (drawn on Winter 18 and named by letter) stepping up for a gift from the monitors (their taste ×3), another neighbour's present for the farmer, fireworks of ring sparkles. | Momentum full, friendship with everyone, the present |

### The board, prizes and records

- **Requests** (`Board`, `Request`): each morning old notes come down and a neighbour pins a
  delivery or popping job for what can be had this season; Mondays bring a weekly one and, from
  Robomart's opening, Robotnik's special order (four times the price, partly in Robo Cola; Dandel
  loses 150 friendship). Three can be taken on. A delivery is finished by talking to whoever asked
  (`Festivals` is a `People.Errands`); popping finishes itself.
- **Board screen** (`BoardScreen`, the `board` doorway by the Lamppost Inn): requests, the season's
  calendar (festivals and birthdays) and the records page: each festival's date, this year's result,
  its best in its own terms (rings, points, a time, flocks, the run and the catch apart) and its
  trophy. The board says what happened on its own bottom line. Prizes owed for want of room are
  handed over when it opens.
- **In the valley** (`FestivalSystem`): the board with a note per posting and its little signpost
  spinning on a new one; the trophy shelf between the Inn and the Workshop (Green Hill's bridge logs,
  six trophies in standing frames); on a festival day palms, bunting and the festival's banner, S1
  lampposts, and the fair's stalls or the feast's table.
- **Prizes** (`Prizes`): items into the monitors or owed at the board; Records set
  `record.<game>.<id>` and open their songs on the Inn's jukebox (`FestivalContent.recordSong`).
- **Mail** (`FestivalMail`): the board's opening notice, Robotnik's first order and the secret
  friend's letter.
- **Save** (`festivals` section): places by year, bests (`id` or `id:event`), best times, prizes,
  trophies, owed items, the secret friend and the board; damaged entries reject the save, unknown
  festivals and items are dropped and numbers clamped.

### Seams touched outside the package

`Content.register` (`FestivalContent`); `Cast` (`FestivalMail`); `People` (`Gathering`, `Errands`,
`spotFor`, `errand`), `PeopleSystem` (`offstage`, `playScene`, `present`), `VillagerActor` (hidden
when offstage), `DialogueScreen` (an errand first), `EventScreen` and `HeartEvent.scene` (scenes for
other systems); `Systems.sections`, `install` and `morningNote` (the lead wired
`Festivals.fishingContest = FishingSystem::contest`); `Debug` (`festival ...`, `board ...` to
`FestivalDebug`); `InnMenu` (five Record tracks); `MorningCard` (the note); `PlayScreen.placeInValley`
and `lightTint`; `ValleyView.sky`.

### Decisions and rejected approaches (evidence from the captures)

- **See-through panels.** `Text.panel` is 88% opaque: the parade's card showed the banner's letters
  through it, the grange showed through its item list. Festival panels are solid.
- **Captions lost their ends.** The caption box shows three lines; the fair's welcome lost "THE
  CARAVAN." and the maze's "OVERDONE IT." Long speeches now turn pages every 170 ticks.
- **Two Tails.** The Ring Hunt drew the champion flying while the People lane drew Tails as the host
  by the sign. The hunt takes the neighbours offstage and draws the crowd at their gathering spots.
- **Rings over the shop signs.** High lines at 70 pixels hid "DANDEL'S SEEDS"; rings now stay
  within 52. Lower rings let the champion take 38 of 65 (the test wants under half); his count
  per ring went from 70 to 90 ticks.
- **A moon over the palms.** Plain shapes drawn after the valley sat in front of the palm leaves;
  the moon is painted into the backdrop on the sky's own colour, and the drifting cloud rows are held
  still so it does not drift. A first version painted its glow rings outer to inner, each only on
  sky, so the disc never painted and the moon came out a dim halo.
- **The banner in the HUD.** The rope hung from the left palm's ground; at the meadow that palm
  stood on a checker pillar. Palms now walk in to ground level with the sign, and the rope hangs from
  the sign's ground. Fireworks over the feast covered the signs and moved to the dark band above.
- **Cave walls in the sky.** A probe of every Green Hill block with its surface line showed blocks
  12, 21, 26, 35 and 47 have cave walls or cliffs above the path; the run crossed in front of them.
- **Fishing skipped its verdict.** The contest's callback toasted the prizes and left for the day,
  and recorded the catch (tens of points) as the run's best (hundreds). It now returns to the
  festival's screen (`Shell.go` keeps the first screen asked for, so the lake's own `go(play)` gives
  way), and the catch's best is `ice_cap:fishing`.
- **A fair without an end.** The grange refused an empty table and the judging is the only way out:
  a farmer with nothing to show was stuck. An empty table now asks to end the fair (no place).
- **Sonic 2 is optional.** `rom("s2")` is null without it; only the slot faces use it and fall back
  to Sonic 3's slot bonus faces. Every festival was run from a scratch directory whose `config.yaml`
  names only the S1 and S3K images (no ROM copies or links): no crash, the booth reads "SLOT BONUS".

### Tests and captures

`src/test/java/starpost/festivals/FestivalRulesTest.java` (engine-free): the calendar and gathering,
hosts, each festival's scoring, places and prizes (first wins once, later purses), the race field
and Robotnik's boost, the flock's size, Casino Night's rewards, the grange's bias, the spring test,
the perfect maze, the snowboard's open-air course, the fishing contest against Frost's catch, the
records page's lines, the shelf's six trophies, ring heights, the morning line's width, the secret
friend, the board's requests, specials, popping, expiry and the section's round trip and damage.
130/130 with the other lanes' tests; a mutation check (a cave block in the course, the Flickies with
a trophy, rings at 70, any best key accepted) turned four tests red.

```
# fast creator tests (RunCreatorTests, a throwaway runner outside the repository)
java -cp "$R/out:$CP" RunCreatorTests $R/tests $R/main
# captures (ExampleModCapture): jump=festival_start_<id>, festival_day_<id>, festival_catch_N,
# board, board_calendar, board_records, board_post_3, board_accept, festival_trophies
java -cp "$CP" com.openggf.mods.code.ExampleModCapture --rom "$PWD/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  --mod examples/starpost-valley --out <dir> --script-file <script> --every 30 --ticks 4700
python3 tools/testing/maven_queue.py --lean -B -Dmse=off -Dtest=TestStarpostValleyExample test
```

Captures in `~/scratch/sv-festivals/final/` (a contact sheet per festival, and the board's). Every
capture reports "Validation passed: 0 findings".

Not done: the Ice Cap contest's clock stands still during Bubble Bar fights (the Waters lane's
contest), so it runs longer than two minutes; no waving pose for the farmer at the Flickies; the
parade's petals are recoloured rather than a ROM palette cycle; the board screen is a menu panel,
not the board's own planks; festivals are the same each year apart from per-year layouts (hunt,
maze, course); and the fair's "bribes at the caravan" is only a line.
