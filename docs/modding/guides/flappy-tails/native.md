# Path A: Flappy Tails in the native game

*Part 1 of the [Flappy Tails tutorial](README.md) asked whether to change the native game or
replace the screen. This page is the native road. The scene road starts at
[part 2](02-first-scene.md).*

The native version already exists: the maintained **`sample-flappy`** gallery sample. Its
source is
[`src/test/resources/mods/sample-flappy-src`](../../../../src/test/resources/mods/sample-flappy-src/README.md),
and the [native-Tails Flappy build-along](../native-tails-flappy.md) explains it chapter by
chapter. This page shows it running, points at the four ideas it is made of, suggests changes
to try, and then says plainly where the native road ends.

## Build and play it

The sample is an ordinary creator project: Maven converts its generated sky and pipe art and
its level, compiles it and packages it with `ggfmod`. It builds against the engine and SDK jars
from the same checkout:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package
src/test/resources/mods/sample-flappy-src/build.sh \
  "$PWD/target/OpenGGF-0.7.prerelease.jar" \
  "$PWD/target/OpenGGF-0.7.prerelease-openggf-mod-sdk.jar" \
  /tmp/sample-flappy
cp /tmp/sample-flappy/target/sample-flappy-mod.jar mods/
```

Enable **Sample Flappy** in the Mod Manager, trust it, restart, choose Sonic 3 & Knuckles and
start a **new game** (No Save, or an empty slot). Instead of Angel Island you are in a short
custom sky with Tails flying. A, B or C flaps; pipes approach; passing one adds a point to the
counter the HUD now calls SCORE. Touching a pipe, or leaving the top or bottom of the screen, is
an ordinary S3K death: lives, restart and all. Disable the mod and a new game starts in Angel
Island again.

## What it is made of

Four small contributions, all scoped to one zone, and three classes.

1. **A game-start zone.** `FlappySampleMod` registers `flappy-garden` as the fresh-game
   destination with no progression anchor: new games go there, saves and the stock route are
   untouched.
2. **A launch team.** For that zone only, Tails is the sole player.
3. **An input filter.** Left and right are removed from the effective input; the raw input is
   still recorded, so replays and rewind stay deterministic.
4. **A HUD profile.** The score row is hidden and the rings row relabelled SCORE.

`FlappyController` relocates the real Tails to a fixed screen column, switches on his real
flight controller and, every frame, pins his X and refills his flight timer (the same
`double_jump_property` the ROM's MGZ2 scripted flight sets). Everything vertical is the
engine's Tails. `FlappyPipe`s are a fixed pool of six objects that the controller moves and
recycles; they keep stable identities so rewind restores them exactly.

The build-along covers each in detail, including palette ownership, the level's fixed camera
and rewind.

## Try it: change the native game

Each is a one-line change in the sample's source; rebuild with the same `build.sh` command.

- **Faster pipes.** `FlappyController.PIPE_SPEED` is in 1/256 pixel a frame: `0x200` is two
  pixels. Try `0x280`.
- **Let Tails tire.** Delete the `setDoubleJumpProperty((byte) FLIGHT_REFILL)` line in
  `update`. Now the engine's own timer runs down: after eight seconds Tails droops into the
  tired animation, stops climbing, and the S3K flight-tired sound plays. You changed nothing
  about flight; you stopped interfering with it.
- **Wider gaps.** `FlappyPipe.gapTop()` and `gapBottom()` are 48 pixels from the gap's centre.
- **Relabel the HUD.** `FlappySampleMod.flappyHud()` lists the rows: label, metric, position,
  digits and warning policy.

## Where the native road ends

The sample is a good teacher of the native Mod API because it does so little to Tails. The
same restraint shows its limits as soon as you want Flappy Tails to look and feel like a
finished game:

- **The look is a level.** A mod zone draws level tiles and object sprites. An S3K mod zone
  cannot borrow a stock zone's background, parallax, palette cycles or events (the
  [S3K mod-zone adapter](../../../architecture/designs/2026-07-14-s3k-mod-zone-adapter-design.md)
  rules that out on purpose), so the sample's sky is a generated strip and the camera never
  moves.
- **ROM art for objects is Sonic 2 only.** `registerRomObjectArt` materialises object art from
  a Sonic 2 ROM; an S3K patch cannot ask for Angel Island's trees or a Buggernaut as a pipe or
  a badnik, so the pipes are original generated art.
- **Screens are overlays.** Menus, results and medals would be drawn over a running level, and
  a crash goes through the stock death, life loss and level reload unless you replace those
  flows too.

None of these is a bug; each is a boundary of what a native patch can safely change. Crossing
them means engine work (teaching S3K mod zones to transform stock acts, say), or the other
road.

## Take the other road

A scene draws everything itself, from the same ROM: Angel Island's real parallax, pillars cut
from the act's real ground, Tails' real frames, the zone's real title card. Tails' movement
becomes your code, but it can be the ROM's routine ported line for line, so he flies exactly
as he does natively.

**[Continue to part 2: a scene of your own](02-first-scene.md).**
