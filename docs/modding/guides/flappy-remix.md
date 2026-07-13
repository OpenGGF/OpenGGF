# Build-along: a flappy-bird minigame inside Sonic 2

This guide walks through the sixth gallery sample, `sample-flappy`, start to finish:
a one-button minigame that seizes the player at a fixed point in Sonic 2, hides the
native sprite, and flies a borrowed Tails-shaped bird through a corridor of pipes
until it hits one. It exists to demonstrate a complete, non-trivial object-controlled
minigame built entirely from the additive Mod API — no engine source changes, no
shipped ROM bytes.

The finished source lives at
[`src/test/resources/mods/sample-flappy-src`](../../../src/test/resources/mods/sample-flappy-src/README.md).
Every code excerpt below is copied verbatim from that checked-in project; the sample
is the executable contract and this guide is a tour of it, not an independent
implementation. If a snippet here and the file on disk ever disagree, the file on
disk is right — `TestSampleFlappyIntegration` and `TestSampleModsPackage` build and
run the real source on every CI run, not this document.

## 1. What you'll build

Standing at a fixed spot near the end of Emerald Hill Zone Act 2, a `controller`
object takes over the main player, hides it, and puts a small flying "bird" sprite
under one-button gravity/flap control (jump = flap): hold nothing and it sinks,
tap jump and it rises. The camera is force-scrolled forward at a constant 2px/frame
so the level reads like an auto-runner, not a normal walk. A 42-pipe corridor stretches
out ahead; clearing a pipe's gap scores a ring, and flying into a pipe body (or off
the top/bottom of the screen) hands control back to the engine's own hurt/death flow.
Once the built-in respawn settles, the controller re-seizes the player and the run
starts again.

*(Screenshot placeholder: a capture of the bird mid-flight between two pipes, taken
with your own `s2.gen`, would go here. None is checked into this repository because
the rendered frame is built from copyrighted Sega art at runtime — see Chapter 3.)*

You will need your own legally-obtained Sonic 2 ROM (`s2.gen`) to actually play this
mod or to run its ROM-gated tests locally; nothing in the built jar requires it to
compile or package.

## 2. Project setup

Scaffold a project the same way any other mod starts, then adapt the generated
manifest for this sample. From the engine's release directory:

```text
ggfmod.ps1 OpenGGF-0.6.prerelease-jar-with-dependencies.jar OpenGGF-0.6.prerelease-openggf-mod-sdk.jar init sample-flappy --id sample-flappy --package example.flappysample
```

(POSIX shells use `docs/modding/ggfmod` instead of `ggfmod.ps1`.) This produces a
working Maven project with a canonical manifest, a sample badnik, a character stub,
and a minimal level export — the same starting point as every other quickstart. The
checked-in sample's `project/` directory is that scaffold hand-adapted into this
minigame: two object classes instead of the generated badnik, no character, and a
level export shaped like a pipe corridor instead of a demo room.

The manifest
([`openggf-mod.yaml`](../../../src/test/resources/mods/sample-flappy-src/project/src/main/resources/META-INF/openggf-mod.yaml))
declares:

```yaml
formatVersion: 1
id: sample-flappy
name: Sample Flappy
version: 1.0.0
authors:
  - Mod Author
description: Generated OpenGGF sample mod.
engineApiRange: ">=2.1.0 <3.0.0"
type: patch
baseGame: s2
entrypoint: example.flappysample.FlappySampleMod
dependencies: []
audioOverrides: {}
artOverrides: {}
```

`engineApiRange: ">=2.1.0 <3.0.0"` is the important field to get right for this
particular mod: it is a `patch` targeting `baseGame: s2`, and it declares API 2.1.0
rather than the more common `2.0.0` floor because the sample uses
`ModContext.registerRomObjectArt`, which only exists from 2.1.0 onward (see Chapter
3). If you copy this manifest for your own project but don't need ROM art intake,
`>=2.0.0 <3.0.0` is enough — declaring a narrower floor than your code actually needs
is a validator warning waiting to happen, not a safety net.

Every id in this project is intentionally generic — `sample-flappy`, `pipe`,
`controller`, `bird` — precisely so the ids convey nothing about any particular game's
IP. Namespacing (`sample-flappy:pipe`, `sample-flappy:bird`) is what keeps them
collision-free with stock content and other mods; see
[Namespaced identity semantics](../concepts/id-semantics.md).

## 3. Borrowing Tails from your ROM

The bird you fly is not original art shipped in the jar — it's Tails' own flying
animation frames, decoded from *your* Sonic 2 ROM the moment the mod launches. This
is the ROM art intake path added in Mod API 2.1.0, and it exists specifically so a
mod can remix a stock game's existing art into a new object without redistributing a
single byte of it. Read the full contract in
[Content mods — "ROM art intake"](../content-mods.md#rom-art-intake-sonic-2-patch-mods)
before adapting this pattern; this chapter only covers what changes when you point it
at a different piece of stock art.

The entrypoint
([`FlappySampleMod.java`](../../../src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappySampleMod.java))
registers the request like this:

```java
// Tails' flying body frames, materialized from the player's ROM at launch.
// Literals verified against Sonic2Constants.java:112-117 and ART_TILE_TAILS
// (0x07A0 -> palette bits 13-14 = line 0). Re-confirm at implementation.
context.registerRomObjectArt("bird", new RomArtRequest(
        0x64320 /* ART_UNC_TAILS_ADDR */, RomArtCompression.UNCOMPRESSED,
        0xB8C0 /* ART_UNC_TAILS_SIZE */,
        0x739E2 /* MAP_UNC_TAILS_ADDR */,
        0x7446C /* MAP_R_UNC_TAILS_ADDR (DPLC) */,
        0 /* palette line from ART_TILE_TAILS */, 1));
```

That call materializes as the namespaced sheet `sample-flappy:bird`, exactly the
same as `registerObjectArt` would for a baked `.ggfs` — object code later calls
`getRenderer("sample-flappy:bird")` and never knows the difference.

**Finding these five numbers yourself.** They are not guessed; they're
`Sonic2Constants.ART_UNC_TAILS_ADDR`, `..._SIZE`, `MAP_UNC_TAILS_ADDR`,
`MAP_R_UNC_TAILS_ADDR`, and the palette line implied by `ART_TILE_TAILS`. If you were
hunting for a *different* stock object's art from scratch, `RomOffsetFinder` (built
into this engine repository, not part of the published creator SDK) is the tool that
gets you there:

```text
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search Tails" -q
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="verify ArtUnc_Tails" -q
```

`search` finds candidate labels and their ROM offsets in `docs/s2disasm/`; `verify`
confirms a specific label resolves to the offset you expect. A label's compression is
usually obvious from its name (`ArtUnc_` = uncompressed, `ArtNem_` = Nemesis,
`ArtKos_` = Kosinski) or from the surrounding disassembly. The mapping table address
and its optional DPLC (VRAM-remap) table address are found the same way, searching for
`Map_` / `MapUnc_` labels near the object's animation script.

**The `RomArtRequest` fields**, in order: the art address, its compression, an
uncompressed byte size (only meaningful for `UNCOMPRESSED`), the mapping table
address, an optional DPLC table address (`0` skips DPLC flattening when the mapping's
pieces already reference art tiles directly), a palette line (0-3), and a bank size
(`1` for a static, non-animated-tile sheet like this one).

**Palette lines are relative, not absolute.** `paletteLine` here is an index into
whichever palette the object is actually rendered against — the mod zone's own
`palettes.bin`, or a stock zone's palette if the object is placed somewhere
unmodified — not a fixed CRAM color address. When you draw a frame later,
`SpritePieceRenderer` computes the *rendered* line as
`(piece.paletteIndex + sheet.paletteLine) & 3` (Genesis `art_tile`-addition
semantics), so a piece's own `paletteIndex` in its sheet YAML still shifts which line
it actually lands on relative to this base. `pipe-sheet.yaml` documents a concrete
example of getting this arithmetic wrong on purpose as a warning — see Chapter 6.

**Why this can only target Sonic 2 patch mods.** ROM art intake reads from the
*player's* Sonic 2 ROM at launch, which only exists as a concept for an additive
`baseGame: s2` `patch` mod — a standalone module has no base ROM to borrow from, and
no other game's ROM layout is wired up yet. Registering `registerRomObjectArt` from a
standalone or non-S2 manifest fails registration outright, at `register()` time,
before any ROM is ever opened.

**What happens when an address is wrong.** Because registration runs before any ROM
is open, the engine can only sanity-check your addresses against a static Sonic 2
ROM-length bound at registration time — it can't yet tell you whether `0x64320` is
*actually* Tails' art versus some other, wrong-but-still-in-bounds address. The real
decompression, mapping parse, and DPLC flattening happen at gameplay launch, once the
player's ROM is available. If any of that fails — a bad address, a corrupt
decompression, a sheet that blows past `ModInputLimits`' sheet caps — launch aborts
with an owner-attributed `MOD_ROM_ART_INVALID` diagnostic naming the offending key and
the hex address, the same creator-apply fault contract every other launch-time mod
failure uses. There is no silent partial materialization; either the sheet is right
or launch does not proceed with this mod enabled.

**On the IP question:** the jar you package from this project ships zero ROM bytes.
The prose in this guide and in the sample's own comments freely says "Tails' flying
frames" because that's descriptively what the request points at, but the actual pixel
data only ever exists in memory, and only after the engine opens the ROM file the
*player* supplied. Distributing this mod does not distribute any Sega asset.

## 4. The level

[`FlappySampleMod.registerZone`](../../../src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappySampleMod.java)
registers the level:

```java
context.registerZone(new ModZoneContribution("flappy-garden",
        new BakedLevelRef("levels/flappy/level.json"), "ehz2", null));
```

`ModZoneContribution` takes an owner-local zone name, a reference to the baked level
output, an `insertAfter` progression anchor, and (here, `null`) an optional custom
event handler. `"ehz2"` means this zone is spliced into progression right after
Emerald Hill Zone Act 2's results screen — finish EHZ2 in-game and the next zone you
land in is `flappy-garden`, without renumbering any stock zone. See
[Content mods — "Add a Sonic 2 zone"](../content-mods.md#add-a-sonic-2-zone) for the
full progression-anchor contract; `ehz2` here plays the same role `mtz3` plays in that
guide's own example.

The level source lives under
[`project/src/main/mod/level-source/`](../../../src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source/)
as an ordinary full-level export directory (`level.json` plus the ten binary asset
files the [level-definition format](../formats/level-definition.md) requires). Unlike
the badnik/zone sample, which was exported from the in-engine editor, this export is
generated procedurally by a small test-only asset generator — a flat sky-blue strip of
terrain-free chunks, 80 blocks wide, with 42 `sample-flappy:pipe` object placements at
224px intervals starting after one `sample-flappy:controller` spawn near the start.
Object placements in `level.json` reference namespaced keys exactly the way a hand-
authored export would:

```json
{"placementId":1,"x":128,"y":128,"objectKey":"sample-flappy:controller","subtype":0, ...}
{"placementId":2,"x":512,"y":128,"objectKey":"sample-flappy:pipe","subtype":0, ...}
```

Each pipe's `subtype` (cycling 0-4) picks a different gap height — see Chapter 6 for
how `FlappyPipe` turns that into a gap position.

**A gotcha worth knowing before you build your own level export:** `ModLevel`
unconditionally replaces block index 0 with an empty block at load time, no matter
what you baked into it. The first version of this sample's generator put its sky
content at block 0 and got a blank screen for it. The fix — and the pattern every
other checked-in sample level already follows — is to leave block 0 reserved-empty and
put real content starting at block 1, then point every foreground-map cell that should
be visible at block 1 or higher. If a freshly-baked level renders blank despite a
correct-looking `fg-map.bin`, this is the first thing to check.

Convert the level the same way any other sample does:

```text
ggfmod convert level --from-export src/main/mod/level-source --out target/classes/levels/flappy
```

## 5. Seizing the player

`FlappyController`
([`FlappyController.java`](../../../src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java))
is a plain `AbstractObjectInstance` with a three-state routine: `0` = waiting to
seize, `1` = flying, `2` = released and waiting for a normal player to re-seize.

Seizing happens the moment the controller first updates:

```java
case 0 -> {
    player.applyObjectControlState(ObjectControlState.NATIVE_BIT_7_FULL_CONTROL);
    player.setHidden(true);
    velY = 0;
    ySub = 0;
    routine = 1;
}
```

`NATIVE_BIT_7_FULL_CONTROL` is the `ObjectControlState` that hands an object full,
unshared authority over the player's position and physics — no native input
processing, no CPU-sidekick movement, nothing fighting the controller's own writes.
It's the same state family traversal objects across the engine use (vines, grapples,
moving platforms) when they need to own player movement outright rather than merely
nudge it. `setHidden(true)` stops the native player sprite from drawing at all, since
the bird is what should render in its place.

Flight physics run entirely on direct position writes, using the same
`SubpixelMotion` utility every other object in the engine uses for 16:8 fixed-point
movement — no reimplementation:

```java
case 1 -> {
    if (player.isJumpJustPressed()) {
        velY = FLAP_VELOCITY;
    }
    velY = Math.min(velY + GRAVITY, MAX_FALL_VELOCITY);

    SubpixelMotion.State motion =
            new SubpixelMotion.State(0, 0, 0, ySub, FORWARD_SPEED, velY);
    SubpixelMotion.moveSprite2(motion);
    ySub = motion.ySub;
    player.shiftX(motion.x);
    player.shiftY(motion.y);
    ...
}
```

`isJumpJustPressed()` is edge-triggered (a single tap flaps once, holding the button
does not spam flaps), gravity accumulates every frame up to a terminal fall speed, and
`shiftX`/`shiftY` apply the resulting subpixel motion straight to the (hidden) native
player position — the bird you see on screen is rendered separately (Chapter 6) at
that same position, not as a child object trailing behind it.

**The camera has to be told to follow, every single frame.** Because the player is
object-controlled and the level is one long forward corridor, nothing else drives the
camera. The controller calls:

```java
services().camera().requestForcedScroll(px + 64, 112);
```

`requestForcedScroll(x, y)` takes a world-space *focus point* — the same coordinate
space a focused sprite's centre would occupy — and makes the camera track that point
for the next update instead of any focused sprite. Critically, **the request is
frame-scoped**: it's consumed and cleared by the camera's own per-frame update, so it
must be called again every single frame flight continues, not just once at seize time.
Leading the focus 64px ahead of the bird's own centre keeps the bird sitting around
screen x≈96 rather than dead-center, so oncoming pipes stay visible before the bird
reaches them.

**Layout objects despawn when the camera scrolls away from them — including this
one.** Placement-window despawn logic doesn't know or care that this particular
object is the thing driving the camera; if the controller's own position doesn't move
with the player, the level scrolling out from under its original spawn point will
eventually collect it like any other off-camera object. Every update call re-anchors
the controller's tracked position to the live player position before doing anything
else:

```java
updateDynamicSpawn(player.getCentreX(), player.getCentreY());
```

Skipping this call is the single easiest way to make this pattern silently break a
few seconds into a real playthrough — the controller vanishes mid-flight and the
player snaps back to native control with no error.

Release, when a collision or the kill bounds are hit, is the mirror image of seizing:

```java
private void release(AbstractPlayableSprite player, int frame) {
    player.setHidden(false);
    player.releaseFromObjectControl(frame);
    routine = 2;
}
```

...and routine `2` waits for the player to be fully back to a normal, unowned state
(post-respawn, neither hurt nor dead nor still object-controlled by anything else)
before re-arming routine `0` to seize again — see Chapter 6 for exactly what "back to
normal" means here.

## 6. Pipes, score, death

`FlappyPipe`
([`FlappyPipe.java`](../../../src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java))
is deliberately inert. It owns no touch/solid marker interface and never touches the
player itself — it just exposes plain contact-geometry accessors that
`FlappyController` polls every frame:

```java
public int leftEdge()  { return spawn.x() - 16; }
public int rightEdge() { return spawn.x() + 16; }
public int gapTop()    { return gapCenter - 48; }
public int gapBottom() { return gapCenter + 48; }
```

`gapCenter` is derived once, in the constructor, from the immutable spawn subtype:

```java
this.gapCenter = 64 + (spawn.subtype() % 5) * 24;
```

Because that derivation is pure and repeatable from the unchanged spawn, `gapCenter`
is safe to declare `final` — normally a rewind red flag (see below) — as long as
`recreateForRewind` re-derives it from the same spawn rather than needing to
capture/restore its value across a rewind seek. The field carries a
`@RewindTransient` annotation that tells both the generic field capturer and
`ggfmod validate`'s `FINAL_SCALAR_REWIND_GAP` check exactly that:

```java
@RewindTransient(reason = "derived deterministically from spawn.subtype(); recreateForRewind "
        + "re-derives it from the unchanged spawn, so no capture/restore is needed")
private final int gapCenter;
```

The controller owns all contact resolution and scoring, scanning for live pipes with
the `@ModApi` accessor `ObjectManager.activeObjectsOfType(Class)`:

```java
for (FlappyPipe pipe : services().objectManager().activeObjectsOfType(FlappyPipe.class)) {
    if (px > pipe.rightEdge() && pipe.leftEdge() > lastScoredX) {
        lastScoredX = pipe.leftEdge();
        services().levelGamestate().addRings(1);
        services().playSfx(GameSound.RING);
        bestScore = Math.max(bestScore, services().levelGamestate().getRings());
    }
    boolean insideX = px + BIRD_HALF_SIZE > pipe.leftEdge() && px - BIRD_HALF_SIZE < pipe.rightEdge();
    boolean insideGap = py - BIRD_HALF_SIZE > pipe.gapTop() && py + BIRD_HALF_SIZE < pipe.gapBottom();
    if (insideX && !insideGap) {
        release(player, frame);
        player.applyHurtOrDeath(px, DamageCause.NORMAL, true);
        return;
    }
}
```

Score is just ordinary rings: `LevelState.addRings(1)` and `GameSound.RING` reuse the
stock ring-collect presentation instead of inventing a bespoke score HUD. Passing a
pipe's right edge while its left edge is further right than the last-scored pipe's
left edge scores exactly once per pipe, regardless of how many frames the bird spends
inside the gap. Flying into a pipe body — inside the pipe's X span but outside its
gap's Y span — or crossing the controller's own `KILL_TOP_Y`/`KILL_BOTTOM_Y` bounds
both call `release()` and then `player.applyHurtOrDeath(...)`, handing off to the
engine's own hurt/death/respawn machinery rather than reimplementing it. From that
point on, the controller is a spectator until routine `2`'s re-seize check passes.

**Rendering the pipe body is where the "pieces are centre-anchored" convention
matters.** `pipe-sheet.yaml`'s two frames both set `xOffset`/`yOffset` to
`-halfWidth`/`-halfHeight` of their piece, the same convention `sample-sheet.yaml`
uses elsewhere in the gallery. That means `drawFrameIndex(frame, x, y, ...)` draws the
piece *centered* on `(x, y)`, not with `(x, y)` as a top-left corner — a tile drawn at
`y` covers `[y - halfHeight, y + halfHeight)`. `FlappyPipe.appendRenderCommands`
leans on that directly to tile a pipe stack seamlessly outward from each gap edge:

```java
// Top pipe stack: lip at the gap edge (flange faces down into the gap), then body
// tiles walking upward past y=0.
renderer.drawFrameIndex(1, x, gapTop(), false, true);
for (int y = gapTop() - 16; y > -16; y -= 32) {
    renderer.drawFrameIndex(0, x, y, false, false);
}
```

Anchoring the lip flush against the gap boundary first, then tiling the 32px body
frame outward from *that*, is what keeps consecutive tiles contiguous by
construction — get the offset convention backwards and you'll see either gaps or
overlaps between tile edges instead of a seamless pipe.

**The palette-line arithmetic is a second place this sample deliberately documents a
near-miss**, in `pipe-sheet.yaml`'s header comment: the sheet declares
`paletteLine: 1`, but both frames also set `paletteIndex: 1` on their pieces, so the
pipe actually renders on CRAM line `(1 + 1) & 3 = 2` — not line 1. That's intentional:
the generated palette's line 1 is sky-blue level-tile color data the pipe must *not*
collide with, and line 2 is where the generator actually placed pipe-green colors.
"Simplifying" `paletteIndex` back to 0 to match the sheet's own `paletteLine` would
silently move the pipe onto the wrong palette line. This is the same
`(piece.paletteIndex + sheet.paletteLine) & 3` arithmetic Chapter 3 introduced for the
ROM-materialized bird sheet — it applies uniformly to baked and ROM-materialized
sheets alike.

**Two deliberate design notes**, called out explicitly so they read as intentional
rather than as gaps:

- The spec that inspired this sample sketched a *third* object class for a bird
  companion. The shipped sample folds that rendering entirely into the controller's
  own `appendRenderCommands` instead — there are exactly two mod object classes here
  (`controller`, `pipe`), not three. The controller's rewind story is simpler for it:
  there is no separate bird object whose lifetime needs to track the controller's.
- `bestScore` lives in a plain instance field and is captured by the default compact
  rewind schema like every other non-final scalar field on the controller — but it is
  **not** persisted to a save slot or across a fresh level load. A death and
  respawn recreates the controller object from scratch (see below), which resets
  `bestScore` to zero along with everything else. That's consistent with this being a
  session-lifetime high score, not a permanent one — treat this as the sample's
  answer to "how would a persistent best score work here", not as a bug to fix.

**Rewind, in one sentence per class:** every field on both objects is either a
non-final scalar (captured/restored automatically by the default compact rewind
schema) or a `@RewindTransient`-annotated final scalar with a documented reason, and
both classes implement `RewindRecreatable` trivially, rebuilding themselves from the
unchanged spawn:

```java
@Override
public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
    return new FlappyController(context.spawn());
}
```

That's the whole checklist for a rewind-safe object family this simple: no final
scalar field goes uncaptured and unexplained, and every object can always rebuild
itself from its own spawn.

## 7. Package, trust, play

Compile the two Java classes against the engine jar, run both source-asset
converters, then package and validate — the same shape as every other quickstart,
just with this project's actual paths:

```text
mvn package
ggfmod convert art --image src/main/mod/pipe.png --sheet src/main/mod/pipe-sheet.yaml --out target/classes/art/pipe.ggfs
ggfmod convert level --from-export src/main/mod/level-source --out target/classes/levels/flappy
ggfmod package --input target/classes --out target/sample-flappy-mod.jar
ggfmod validate target/sample-flappy-mod.jar
```

(The checked-in project wires the two `convert` steps and the final `package` step
into the Maven build itself via `exec-maven-plugin`, so a plain `mvn package` already
runs them in the right order — see
[`pom.xml`](../../../src/test/resources/mods/sample-flappy-src/project/pom.xml). The
explicit commands above are the same thing spelled out for a hand-rolled build.)

`package` always validates its own staging jar before publishing, and it refuses to
overwrite an existing output path — delete or rename a previous `target/sample-flappy-mod.jar`
before repackaging. The separate `ggfmod validate` invocation above is for printing
the sorted findings for a jar that already exists, which is what you want when
iterating: build once, then re-run `validate` alone while you chase down warnings.

To actually play it: drop the packaged jar into your engine's `mods/` directory,
start the engine, open the Mod Manager, enable `Sample Flappy`, and restart. Because
this mod carries an entrypoint (`example.flappysample.FlappySampleMod`) and therefore
executes creator code, the Mod Manager will show a code-trust prompt naming the jar's
exact SHA-256 hash before it will run — see
[Executable-mod trust](../concepts/trust.md). **That prompt reappears on every
rebuild**, not just the first time: `package` produces a deterministic jar from its
input, but any change to your source, art, or level changes the resulting bytes,
which changes the hash, which is a new grant as far as the trust store is concerned.
Don't be surprised that re-enabling a mod you've already trusted once asks again after
you've rebuilt it.

From the master title, start a normal Sonic 2 game as Sonic, play through Emerald
Hill Zone Act 2 to its results screen, and the next zone you land in is Flappy Garden.

## What proves this actually works

You don't have to take the prose above on faith. `TestSampleFlappyIntegration`
(`src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`) is a
ROM-gated, headless, real-build-and-load test that exercises the whole path against
your local `s2.gen`: it packages this exact project from source, loads it through a
real owner class loader, resolves the `flappy-garden` zone, confirms the ROM-baked
bird sheet actually contains fly frames 94/95 with real mapping pieces, then drives
real frames and asserts that the controller seizes and hides the player, that
unflapped flight sinks under gravity, that a flap reverses that trend, that the camera
force-scrolls monotonically forward every frame, that clearing the first pipe scores
exactly one ring, that an unavoided fall hands off to the engine's own hurt/death, that
the engine's pit-death flow reaches a real respawn request, and that the controller
re-seizes the player once respawn settles. `TestSampleModsPackage` builds this project
alongside the other five gallery sources as one repository and confirms it validates
with zero findings. Run both locally with:

```text
mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test
mvn "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" test
```

`TestSampleFlappyIntegration` is skipped (not failed) if `s2.gen` isn't present in the
working directory; `TestSampleModsPackage` needs no ROM at all.
