# Super Sonic Bros. — a platform fighter for Sonic 3 & Knuckles

A code mod for OpenGGF's JVM build: a Smash-style platform fighter for one to four players
(people or CPUs), played as a full-screen mod scene. Every fighter, stage, monitor, sound and
song comes from the player's own **Sonic 3 & Knuckles** ROM.

Everyone starts at **0%**. Hits add damage, and the more damage you carry the further every
hit launches you. Knock your rivals past the blast lines at the edges of the screen to KO them.

## Fighters

| Fighter | Weight | Style | Specials (neutral / side / up / down) |
| --- | --- | --- | --- |
| Sonic | 85 | Fast and slippery, strong back air and forward smash | Homing Attack / Spin Dash (hold to rev) / Spring Jump / Insta-Shield (reflects shots) |
| Tails | 78 | Featherweight, three jumps, long recovery | Dummy Ring Bomb / Propeller Dash / Flight (tap jump to flap) / Tail Tornado |
| Knuckles | 108 | Heavy hitter with a glide recovery | Maximum Heat (hold to charge, armoured) / Glide / Spiral Upper / Earthquake |
| Robotnik | 128 | Heaviest; floats in the Egg Mobile with three air jumps | Egg Bomb / Egg Missile / Overdrive Thrusters / Spike Mine |
| Mecha Sonic | 102 | Sonic's steel rival: heavier, harder hitting | Chest Laser / Mecha Blitz (hold to charge) / Jet Burst / Reflector Field |
| Egg Robo | 104 | Tall robot trooper, strong at range | Laser Blaster (pierces) / Rocket Punch / Jetpack / Overload (armoured burst) |
| Rhinobot | 92 | Fastest runner, low to the ground, all horn | Horn Charge (hold to charge) / Zig-Zag Dash / Spring Horn / Dust Kick |

Each fighter has a jab, three tilts, a dash attack, three chargeable smash attacks, five aerials,
four specials, a grab with pummel and four throws, and ledge and getup attacks. Sonic, Tails and
Knuckles use their own sprite sets and animation frames; Mecha Sonic and Rhinobot use their
boss and badnik sprites; Robotnik is his Egg Mobile with his head behind the dome, and Egg
Robo is assembled from body, legs and gun arm at the original child-object offsets. Robotnik's
wrecking ball, flames, bombs and missiles are drawn in code.

Four colour variants per fighter (Emerald, Shadow and Golden Sonic, Knuckles' 2P green...) let
the same fighter appear more than once.

## Stages

Ten stages, each a floating island carved from a real act: the floor line comes from the act's
collision, a little of the art standing on it is kept (grass, crystals, flowers) and the earth
beneath tapers into a shaded underside. The zone's own parallax background and music play
behind it.

| Stage | Layout |
| --- | --- |
| Angel Island — Jungle Ledge | Three platforms (Battlefield) |
| Hydrocity — Ruin Terrace | Twin platforms |
| Marble Garden — Hilltop Shrine | Low triple |
| Carnival Night — Bumper Deck | Two lifts moving up and down |
| IceCap — Crystal Shelf | Twin platforms |
| Launch Base — Gantry Walk | One shuttle platform sliding side to side |
| Mushroom Hill — Toadstool Glade | Three platforms |
| Sandopolis — Pyramid Steps | Low triple |
| Sky Sanctuary — Temple in the Clouds | Twin platforms |
| Death Egg — Final Platform | No platforms |

## Modes and rules

- **Stock** (1–9 lives): the last fighter, or team, with lives left wins.
- **Time** (1–10 minutes): +1 per KO, −1 per fall; ties go to **Sudden Death** at 300%.
- **Stamina** (50–300 HP): hit points instead of damage; reach zero and you are out.

The Rules page also sets monitor frequency (off, low, medium, high) and which monitors can
drop, team battle (red, blue, green) with optional friendly fire, up-to-jump, and a damage
ratio. Rules are saved.

### Monitors

Monitors fall onto the stage; break one with any attack or projectile and its contents go to
whoever broke it.

| Monitor | Effect |
| --- | --- |
| Super Ring | Heals 15% (15 HP in stamina) |
| Flame Shield | Blocks one hit; +15% attack power while held |
| Bubble Shield | Blocks one hit; harder to launch while held |
| Thunder Shield | Blocks one hit; an extra mid-air jump |
| Invincibility | Eight seconds untouchable (with the invincibility theme) |
| Speed Shoes | Ten seconds of extra speed |
| Eggman | A trap: it explodes on whoever opens it |
| Super Emerald ("S") | Twelve seconds of Super form: invincible, faster, stronger |

## Controls

Press attack (or start) on any controller at the character screen to join. Two people can share
the keyboard.

| Action | Keyboard (arrows) | Keyboard (WASD) | Gamepad |
| --- | --- | --- | --- |
| Move | Arrow keys | W A S D | D-pad / left stick |
| Attack | Z (or comma) | F | X / Square |
| Special | X (or full stop) | G | B / Circle |
| Jump | C, Space (or slash) | H | A / Cross, Y / Triangle |
| Shield | V (or right Shift) | R | Shoulders and triggers |
| Pause | Enter | Q | Start |

- **Tilt attacks**: hold a direction, then attack. **Smash attacks**: tap a direction and attack
  together; hold attack to charge (up to 40% more damage).
- **Shield** blocks hits but shrinks as it takes them; a broken shield leaves you dizzy.
  Shield + attack grabs, shield + left/right rolls, shield + down dodges, and shield in the air
  air-dodges.
- **Tech**: press shield just before hitting the ground while tumbling to recover instantly.
- **Directional influence**: hold across your launch line to bend it by up to 15 degrees.
- **Ledges**: fall near the island's edge to catch it. Climb (toward the stage), jump, roll
  (shield) or attack from the ledge.
- Holding down on a platform drops through it.

Menus: up/down to choose, attack/jump/start to confirm, special to go back. On the character
screen move your cursor over a fighter and press attack; point at a panel's tab to add or
remove a CPU (or press Tab), at its arrows to change colour, CPU level (1–9) or team. The mouse
works as player 1's cursor.

## Balance

The simulation (`sonicbros.sim`) has no engine dependencies, so it runs thousands of
headless matches. Fighters were tuned against each other with level-9 CPUs, three stocks, on a
288-pixel island with Battlefield platforms: every fighter's overall win rate sits between
roughly 40% and 60%, self-destructs are rare, and average KOs land between 130% and 160%
(heavier fighters later). The CPU picks moves from their frame data (reach, startup, damage,
knockback) rather than from per-character scripts, so these numbers measure the characters,
not the AI. `BalanceTest` in `src/test` repeats a smaller version of the probe.

Knockback follows the familiar formula: `((p/10 + p·d/20) · 200/(w+100) · 1.4 + 18) · growth/100 + base`
for damage `d`, the target's percent `p` after the hit and weight `w`; launch speed is
0.059 px/frame per point, decaying by 0.085 px/frame², and hitstun is 0.4 frames per point.
Repeating the same move stales it (up to 45% weaker), and fresh moves get a 5% bonus.

## Records

`saves/mods/super-sonic-bros/records.txt` keeps matches played, your wins, and per fighter
picks, wins, KOs, falls and damage dealt; `rules.txt` keeps the last rules.

## Build and run

```bash
python3 examples/super-sonic-bros/build.py        # compile and package target/super-sonic-bros/super-sonic-bros.jar
python3 examples/super-sonic-bros/build.py --run  # ...then launch the engine with the mod (JVM build)
```

Install the jar through the Mod Manager like any trusted code mod, then pick Sonic 3 &
Knuckles on the master title. The mod lays out for the 400-pixel 16:9 view and switches to it
while it runs. Native builds cannot load code mods.

Debug commands for tests and the capture tool (`BrosScene.debugJump`): `title`, `menu`,
`rules`, `records`, `howto`, `select`, `stage`, and
`battle:<stage>:<fighter>[/c<level>],...[:stock3|time2|stamina150|items0..3|teams]`, for example

```bash
java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED \
  -cp "target/test-classes:target/classes:$(cat target/test-classpath.txt)" \
  com.openggf.mods.code.ExampleModCapture --rom s3k.gen --mod examples/super-sonic-bros \
  --out /tmp/bros --jump "battle:0:sonic/c9,knuckles/c9,tails/c9,robotnik/c9:stock3:items2" --every 60
```

(`-XstartOnFirstThread` is for macOS only.)

## How it is built

- `sim` — the fight itself, free of engine and drawing code: `Fighter` (the state machine:
  running, jumping, attacks, shields, dodges, ledges, hitstun, grabs, respawn), `Move`/`Hit`
  (frame data), `Knockback`, `Projectile`, `Item` (monitors), `Arena` (island floor line,
  underside, platforms, blast lines), `Match` (hit resolution, KOs, modes and placements) and
  `Cpu`. A match is deterministic for a seed and inputs.
- `content` — `Roster` (the seven fighters' stats, poses and moves) and `Stages`.
- `view` — `StageCarver`/`StageBuilder` cut islands from `SceneLevelKit` blocks, `Art` and
  `FighterArt` decode and assemble the ROM sprites (with `Costumes` recolouring), `MatchView`
  draws the camera, effects and HUD, `BigFont` the lettering and `Gfx` the code-drawn props.
- `input/Controls` turns two keyboard layouts and every gamepad into separate controllers.
- `ui` — the screens: title, main menu, rules, character select, stage select, battle (with
  pause), results, records and how to play.
