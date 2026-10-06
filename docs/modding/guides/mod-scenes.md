# Mod scenes: full-screen games and menus

A **mod scene** is a screen your mod draws entirely itself: a menu, a minigame, or a whole
game that isn't a platformer level. The engine runs it at 60 ticks a second, gives it
input, audio, storage and a canvas, and keeps it inside the mod fault boundary. Scenes can
use the player's ROM for art, so a scene looks like the game it runs on without shipping
any of its assets.

The complete example is [Slay the Robotnik](../../../examples/slay-the-robotnik/README.md),
a deck-building roguelike on Sonic 3 & Knuckles. Its source is the reference for
everything below; when this guide and the source differ, the source is authoritative.

Scenes are part of the Mod API 0.7 candidate and need the JVM build.

## 1. Register a startup scene

A **patch** mod registers a startup scene. When the player picks the mod's base game on
the master title, the scene opens instead of that game's title screen.

```yaml
# src/main/resources/META-INF/openggf-mod.yaml
formatVersion: 1
id: hello-scene
name: Hello Scene
version: 0.1.0
engineApiRange: ">=0.7.0 <0.8.0"
type: patch
baseGame: s3k
entrypoint: hello.HelloMod
```

```java
public final class HelloMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.registerStartupScene(HelloScene::new);
    }
}
```

A mod registers at most one startup scene (a second call fails registration). If several
enabled mods register one for the same game, the mod applied last in load order wins.

## 2. The scene lifecycle

```java
public final class HelloScene implements ModScene {
    private int presses;

    @Override public void enter(SceneContext ctx) {
        ctx.audio().playMusic(0x01);                  // S3K: Angel Island Act 1
    }

    @Override public void update(SceneContext ctx) {
        if (ctx.buttonPressed(SceneButtons.A | SceneButtons.C)) {
            presses++;
            ctx.audio().playSfx(0x33);                // the ring sound
        }
        if (ctx.buttonPressed(SceneButtons.B)) {      // B goes back, as on the Genesis
            ctx.exitToGameTitle();                    // fade to the stock title screen
        }
    }

    @Override public void draw(SceneContext ctx, SceneCanvas canvas) {
        canvas.clear(0x102040);
        canvas.text("PRESSES " + presses, 16, 16, 0xFFFFFFFF);
    }

    @Override public void exit(SceneContext ctx) {
        ctx.storage().write("presses.txt", Integer.toString(presses));
    }
}
```

- `enter` runs once when the scene opens, `update` 60 times a second, `draw` once per
  presented frame (it may be skipped or repeated, so it must not change state), and `exit`
  when the scene closes for any reason: it asked to leave, the player held Escape (always
  back to the master title), or the engine shut down.
- Every call runs inside the **fault boundary**. An exception disables the mod and returns
  the player to the master title with a finding in the Mod Manager instead of crashing.
- **No static state.** The mod validator rejects enums, static collections and static
  initialisers in mod classes. Keep state on the scene (or objects it owns) and use
  `String` or `int` constants for kinds. Slay the Robotnik's `CardType`, `Keyword` and
  friends show the pattern.

`SceneContext` also gives the screen size (`width()`, `height()`): 224 rows, and a width
the player's display aspect decides (320 for 4:3, 352, 400 for 16:9, 528 or 800) unless your
mod requires an aspect, so lay out from `width()`;
`ticks()` since the scene opened, and `mouse()` in game pixels (position, left and right button down/pressed/released edges,
`wheel()` notches, `over(x, y, w, h)`, and `lastInputWasMouse()` for showing hover highlights
only to mouse players).

Input comes as named buttons and keys, never raw numbers:

- `buttonDown(b)`, `buttonPressed(b)` and `buttonRepeated(b)` read player 1's pad (and the
  keyboard keys the player mapped to it) with `SceneButtons` (`UP`, `DOWN`, `LEFT`, `RIGHT`,
  `A`, `B`, `C`, `START`; combine with `|` for "any of these"). `buttonRepeated` is true when a
  button goes down and then every 4 ticks after it has been held for 24, like the engine's
  own menus: use it to move cursors.
- `keyDown(k)` and `keyPressed(k)` read the keyboard with `SceneKeys` (`ENTER`, `ESCAPE`,
  `A`..`Z`, `DIGIT_0`..`DIGIT_9`, `F1`..`F12`, ...).
- By Genesis convention **A or C (or Start) confirms and B goes back**; scenes follow it, so
  `input().menuAccept()` is A, C or Start and `input().menuBack()` is B. `input()` also has
  both players' raw state.

Slay the Robotnik's `ui/Controls` turns all of this into the few verbs its screens use.

To always play in widescreen, add a game patch whose module returns
`requiredDisplayAspect() = "WIDE_16_9"`; the player's setting comes back at the master
title. See `SlayTheRobotnikMod.WidescreenPatch`.

## 3. Drawing

`SceneCanvas` draws in order, later on top, in game pixels:

- `clear(rgb)`, `fill(x, y, w, h, argb)` (alpha blends), `clip(...)`/`unclip()`;
- `draw(image, x, y)` and `drawRegion(...)` for `SceneImage`s;
- `draw(sprite, x, y, style)` for `SceneSprite`s, which carry an origin like a hardware
  sprite (scaling and mirroring happen around it);
- `text(...)`/`textWidth(...)` with the engine's menu font.

`SceneDraw` styles a draw: `SceneDraw.plain().withScale(2).withFlipX(true)`,
`withTint(argb)` multiplies (its alpha fades), `withFlash(argb)` mixes towards a solid
colour (hit flashes, silhouettes), `withAlpha(a)`.

Images come from `ctx.art()`:

```java
SceneImage logo = ctx.art().png(pngBytes);                  // a PNG from your mod's files
SceneImage dot = new SceneImage(2, 2, new int[] {            // pixels made in code
        0xFFFFFFFF, 0xFFFF0000, 0xFFFF0000, 0xFFFFFFFF});
```

The engine uploads an image to the GPU the first time it is drawn and deletes that copy once
the image has gone about two seconds without being drawn (or when the scene closes), so build
images once and reuse them; an image built every frame is uploaded every frame.

Mod files can only be read during `register`, so read them there
(`context.modAssets().readBounded(path, limit)`) and pass the bytes to the scene.
Slay the Robotnik keeps its font, icons and card pictures as editable text this way.

## 4. Art from the player's ROM

`ctx.art().rom()` is a `SceneRomArt` for the running game (null only when the engine could
not prepare ROM art, which it logs). Call it only from the scene's own calls, never from
another thread.
It decodes ROM sprites on the CPU into RGBA images, so every sprite keeps its own colours
and there are no palette-line conflicts.

```java
SceneRomArt rom = ctx.art().rom();
int[] palette = new int[64];                                   // four lines of 16 colours
System.arraycopy(rom.palette(0x0A8A3C, 16), 0, palette, 0, 16); // Pal_SonicTails -> line 0
System.arraycopy(rom.palette(0x0A8B7C, 48), 0, palette, 16, 48); // Pal_AIZ -> lines 1-3

SceneSpriteSet rhinobot = rom.sprites(RomSpriteRequest.streamed(
        0x36732A, 0xAA0,               // ArtUnc_AIZRhinobot (uncompressed, size in bytes)
        0x3615A8, 0x36156E,            // Map_Rhinobot, DPLC_Rhinobot
        RomSpriteRequest.DplcLayout.OBJECT, 1), palette);   // art_tile palette line 1
canvas.draw(rhinobot.frame(0), 200, 120, SceneDraw.plain());
```

- `RomSpriteRequest.of(art, compression, mappings, paletteLine)` covers Nemesis, Kosinski
  and Kosinski Moduled art; `streamed(...)` covers uncompressed art with a DPLC, in the
  object (`Perform_DPLC`) or player layout. `withTileOffset(n)` corrects sprites whose
  `art_tile` starts part-way into their art.
- Find addresses by label in the disassembly's listing (`ArtKosM_…`, `ArtNem_…`,
  `ArtUnc_…`, `Map_…`, `DPLC_…`, `Pal_…`) and the palette line from the object's
  `make_art_tile`. Slay the Robotnik's `art/RomSprites.java` lists dozens with their labels.
- `rom.tiles(address, compression, firstTile, widthTiles, heightTiles, columnMajor, palette)`
  decodes raw 8x8 tiles that have no mappings (reel faces, HUD digits, menu art) into one
  image; Slay the Robotnik's `Art.slotFace` reads the Slot Machine's faces this way.
- `rom.character("sonic")` returns a playable character's frames with the ROM's animation
  scripts (`animationFrames(id)`, `animationDelay(id)`); `characterAccessory("tails")` is
  Tails' tails.
- Bigger enemies are built from several frames at the offsets the original object uses
  for its children. Slay the Robotnik's `scene/EnemyVisuals.java` does this for every
  boss it shows, citing the disassembly tables.

For Sonic 3 & Knuckles, `rom.zoneBackdrop(zone, act)` returns a zone's background as a
`SceneBackdrop`: one picture cut into horizontal bands with the stock parallax speeds, drawn
with `canvas.drawBackdrop(backdrop, top, scrollX, ticks)` (or into a window with the
`x, y, width, height` overload), and `rom.levelOverview(zone, act, maxHeight)` returns a
zoomed-out picture of the act's whole level. To put characters in the level itself,
`rom.levelStages(zone, act, width, headroom, maxRise)` lists runs of the act's floor with room
above them, read from its collision, each with the floor row of every column, and
`rom.levelForeground(zone, act, x, y, width, height)` renders that part of the level at full
size with transparent sky, to draw over the backdrop. All of these are built from the level
data without starting a level. Ask `rom.hasZonePictures(zone, act)` first: today it is true for
five acts (zone ids 0 Angel Island, 1 Hydrocity, 6 Launch Base, 10 Sky Sanctuary; act 0 is act
1), each shown in one state chosen for presentation:

| Zone, act | Pictured as |
|---|---|
| 0, 0 (Angel Island 1) | the main level after the intro (never the intro beach) |
| 0, 1 (Angel Island 2) | the burnt jungle a fresh act 2 load shows |
| 1, 0 (Hydrocity 1) | from below the waterline |
| 6, 0 (Launch Base 1) | as it loads |
| 10, 0 (Sky Sanctuary 1) | backdrop: the cloud sea; overview and foreground as it loads |

Elsewhere, and in Sonic 1 and 2, the methods return null (or no stages).

`rom.titleCard(zone, act)` returns an act's stock title card as four sprites (the red banner,
the zone's name, "ZONE" and the act number) whose origins are the ROM's object positions; its
javadoc lists where each slides from and to, at what speed, and when each leaves, so a scene
can play the card. `rom.hasTitleCard(zone, act)` says which acts have one (Sonic 3 & Knuckles
zones 0-12, 22 and 23 today). Slay the Robotnik stages its fights, Starposts and capsules on the level
(`scene/LevelStages`, standing each character on the floor under it) and draws its act maps
over the overview (`scene/MapView`).

The engine's test tree has a CPU-only tool to see every frame of a sprite at once:

```bash
java -cp target/test-classes:target/classes:<classpath> com.openggf.mods.scene.SpriteSheetDump \
  s3k.gen s3k rhinobot.png art=0x36732A comp=UNCOMPRESSED size=0xAA0 map=0x3615A8 \
  dplc=0x36156E layout=OBJECT line=1 pal=0x0A8A3C:16:0 pal=0x0A8B7C:48:1
```

## 5. Audio and storage

`ctx.audio()` plays the base game's music and sound effects by driver ID (`playMusic`,
`playSfx`, `fadeOutMusic`, `stopMusic`).

`ctx.storage()` keeps small text files for your mod under the save root
(`saves/mods/<mod-id>/`): `read`, `write`, `delete`, `list`. Slay the Robotnik saves the
run in progress, the player's records and the compendium there.

## 6. Testing a scene

Scenes run without GL in tests: the host records each frame's draw calls instead of
rendering them. The engine tests `TestModSceneHost`, `TestSlayTheRobotnikExample` and
`TestSlayTheRobotnikScene` show the patterns:

- build, package and validate the mod from source as `ggfmod` would, then run its own unit
  tests;
- open the scene against a real game session, play ticks, and assert on the recorded frame
  and on the fault boundary's findings;
- give the scene a debug entry point (Slay the Robotnik's `SlayScene.debugJump`) so tests
  and screenshot tools can go straight to any screen.

`SlayTheRobotnikCapture` renders headless screenshots of the example with GL; reuse its
approach for your own scene.
