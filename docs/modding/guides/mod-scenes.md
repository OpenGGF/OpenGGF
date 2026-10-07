# Mod scenes: full-screen games and menus

A **mod scene** is a screen your mod draws entirely itself: a menu, a minigame, or a whole
game that isn't a platformer level. The engine runs it at 60 ticks a second, gives it
input, audio, storage and a canvas, and keeps it inside the mod fault boundary. Scenes can
use the player's ROM for art, so a scene looks like the game it runs on without shipping
any of its assets.

Examples go with this guide:

- [hello-scene](../../../examples/hello-scene/README.md) is the place to start: two classes
  and a manifest, in which Sonic runs and jumps over Angel Island's background collecting
  rings. Copy it to begin your own scene.
- [Slay the Robotnik](../../../examples/slay-the-robotnik/README.md) is a complete
  deck-building roguelike on Sonic 3 & Knuckles, and shows how a whole game is organised
  around one scene.
- [Sitar Hero](../../../examples/sitar-hero/README.md) combines supplied Sonic 1,
  Sonic 2 and S3K content in an arcade rhythm game, with timestamped physical input
  and bounded ROM-synthesized music.

When this guide and their source differ, the source is authoritative. Build and run either
from a checkout with `python3 examples/build_example.py <name> --run` (Java 21 and Maven, and
your ROM set up as for the engine).

Scenes are part of the Mod API 0.7 candidate and need the JVM build. Their types live in two
packages: `com.openggf.mods.code` (`GgfMod`, `ModContext`) to register, and
`com.openggf.mods.scene` (`ModScene`, `SceneContext`, `SceneCanvas` and the rest) to run.

## 1. Register a startup scene

A **patch** mod registers a startup scene. When the player picks the mod's base game on
the master title, the scene opens instead of that game's title screen.

```yaml
# src/main/resources/META-INF/openggf-mod.yaml
formatVersion: 1
id: hello-scene
name: Hello Scene
version: 0.1.0
authors:
  - Your Name
description: What the mod does, in a sentence or two.
engineApiRange: ">=0.7.0 <0.8.0"
type: patch
baseGame: s3k
entrypoint: hello.HelloSceneMod
dependencies: []
audioOverrides: {}
artOverrides: {}
```

```java
package hello;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

public final class HelloSceneMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.registerStartupScene(HelloScene::new);
    }
}
```

A mod registers at most one startup scene (a second call fails registration). If several
enabled mods register one for the same game, the mod applied last in load order wins.

Use `baseGame: any` for a game-independent ROM-backed startup scene. It registers
the same scene on each installed stock game; `ggfmod run` chooses an available
configured game. This narrowly permits startup-scene and required-display-width
registration. Game-specific patches, characters, objects, zones, override tables
and insertion declarations are rejected. It does not create a no-ROM standalone
game or imply that a missing ROM is available.

## 2. The scene lifecycle

The smallest useful scene counts button presses (hello-scene's `HelloScene` does more with
the same four calls):

```java
public final class PressCounter implements ModScene {
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
- **No mutable static state.** Keep gameplay state on the scene or its owned
  objects. Literal constants and verified immutable enums with primitive/String
  fields are accepted, as are javac assertion flags and enum-switch tables.
  Authored static collections, arbitrary initializers and mutable enum payloads
  are rejected. Sitar Hero's `Role` and `Roster` demonstrate immutable enums.

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

For five-button instruments or other device-specific controls, use
`ctx.physicalInput()`: an immutable snapshot of keys, standard GLFW gamepads and
ordered `PhysicalInputEvent` transitions. Press/release events include sequence and
monotonic observation timestamps, so a short tap between updates survives. The
bounded queue reports `droppedEvents()` instead of silently claiming complete
input. Keyboard repeat is excluded; gamepad baselines carry held state without
inventing presses, and disconnect releases held inputs. Raw pads remain available
when the Genesis gamepad mapper is disabled. Persist your own remaps in scene
storage, and reset held baselines after pause/retry.

These canonical values are `com.openggf.control.PhysicalInput`,
`PhysicalInputEvent` and `PhysicalGamepad`, shared by input capture and the scene
host.

Gamepad timestamps describe polling observation, not hardware delivery. The live
window polls during its frame wait, but rendering/OS scheduling can still delay
observations. Choose one menu convention: ordinary raw pad A/B must not also be
interpreted through their mapped Genesis aliases in the same update.

## Timing-sensitive ROM music

`ctx.music()` prepares finite ROM-synthesized playback with semantic note attacks:

```java
ScenePreparedMusic song = ctx.music().prepare("s1", 0x81, 3168);
SceneMusicPlayer player = ctx.music().start(song,
        List.of(new SceneMusicPart(0, 1, 0, false)), song.sampleRate() * 3);
long audibleSample = player.samplePosition();
long inputSample = player.samplePositionAt(event.timestampNanos());
```

Preparation is bounded to ten minutes at the native 60 Hz driver cadence. Full
and selected-part stereo PCM together have a 256 MiB budget; high output rates
can therefore limit the accepted duration. Note metadata is bounded to 200,000
completed events. Preparation reads only the requested supplied ROM, with no ambient
music/SFX restore side effects. `SceneNoteEvent` identifies FM/PSG/DAC attacks,
channel, pitch/sample id, source offset, onset and duration in samples. Ties and
rests do not become fabricated attacks; equal-pitch and duration-only retriggers
remain separate. Events provide timing evidence, rather than automatic playable
charts: curate musical parts, density, lanes, chords and difficulty in your mod.

Use `prepareAsync(game, id, frames)` for long performances. Keep its
`SceneMusicPreparation` handle and poll `state()` and `progressPercent()` in
`update`; call `prepared()` only when READY. FAILED exposes a bounded `error()`.
ROM loading stays on the scene thread; independent synthesis uses one host worker
with one queued job. A different request cancels the old job. Call `cancel()`
when leaving a loading screen; scene exit also cancels and stops the worker.
Do not spin or wait for completion in your scene.

After authoring the chart's channel sections, call
`preparePartAsync(song, parts)` and poll that second job. Its READY `prepared()`
publishes the selected mix without starting audio. A subsequent `start` with the
same sections reuses it, so rendering does not interrupt the count-in. Legacy
scene hosts can complete these defaults synchronously and render the part during
`start`; the production engine supplies background preparation.

One scene retains one prepared song and one selected arrangement. Repeating the
same request reuses it; preparing a different song retires the old preparation
and stops its player. Retired metadata remains readable, but call `prepare`
again before starting that song. Stopped players and retired preparations release
their old PCM, even when a mod keeps their handles.

The part masks select the channels to subtract when `setPartAudible(false)` is
called. A sorted `SceneMusicPart` list can change that selection by section; its
first onset must be zero. FM/PSG indices are zero-based; logical PSG3 includes its
noise output. DAC remains a separate flag. The host synthesizes full and masked
mixes separately to preserve chip interactions and backing progression.
`setWhammy` bends the selected residual; it is a presentation effect rather than
an emulated guitar-controller DSP contract. Scope it to roles that use it.

The clock follows consumed final PCM on a live device, with optional OpenAL Soft
backend latency correction; a no-device capture follows PCM actually rendered.
Negative positions cover the supplied lead-in. Pause/resume and host focus pauses
freeze the coordinate, and `underrunCount()` exposes interruptions. Stop the
player on retry/exit; the host also releases playback when the scene closes.
Speaker failure freezes the scoped player and increments its interruption count.
That player cannot resume: restore output and start a fresh player. An explicitly
device-free capture still uses its rendered PCM clock.
Persist user calibration for device/display/input delays. Queued PCM still
delays a newly requested mute or whammy, so calibration does not make feedback
instantaneous. Render ticks and diagnostic/trace rows must not judge rhythm input.

To lay out for one width, call `context.requireDisplayWidth(400)` in `register` (320, 352,
400, 528 or 800): the engine switches the session to that display aspect before the scene
opens and refits the window, and the player's own setting comes back at the master title.
Slay the Robotnik asks for 400.

## Direct peer messaging

`ctx.network()` is a scene-owned text transport for explicit user Host/Connect
actions. Obtaining it opens no socket. Keep the returned `ScenePeer` on your scene:

```java
// Choose on the user's Host/Connect action:
ScenePeer peer = hosting ? ctx.network().host(24807)
        : ctx.network().connect("192.168.1.20", 24807);
// In update:
if (peer.state() == ScenePeer.State.CONNECTED) {
    for (ScenePeer.Message message : peer.poll()) {
        // Validate your own protocol and update local state from message.text().
    }
}
// On the user's Ready action, once connected:
boolean queued = peer.send("READY 1");
// Show peer.state() and, for FAILED, peer.error(); close before hosting again.
```

The host listens on all local interfaces for one peer; after that acceptance the
listener closes. A scene may have only one listening, connecting or connected
endpoint. Ports must be 1..65535. Connect accepts IPv4/IPv6 literals without
brackets or scope ids, and `localhost`; DNS names are unsupported. Invalid
arguments throw `IllegalArgumentException`; an active endpoint or closed scene
throws `IllegalStateException`. Bind and connection failures are asynchronous:
check `state()` (`LISTENING`, `CONNECTING`, `CONNECTED`, `CLOSED`, `FAILED`) and
`error()` (a bounded reason for `FAILED`, otherwise null). `LISTENING` includes
the asynchronous bind phase, so a connection attempted immediately on another
thread may need a user retry.

`send` returns true when queued, without guaranteeing delivery. Null, malformed
UTF-16, more than 4096 Java characters, disconnected peers and full queues return
false. Empty text is valid. Incoming and outgoing messages share 256 pending
slots, including a write in progress; incoming overflow fails the connection
explicitly. `poll` drains an immutable list in receive order. Each
`Message(text, receivedNanos)` uses the local monotonic observation clock shared
with physical input. It includes network/scheduling delay, is not the remote
clock and does not synchronize song clocks or gameplay automatically.

All I/O belongs to one lazy engine worker per scene; tick calls never wait for
sockets, DNS or worker termination. Listening times out after 60 seconds,
connecting after five, and an incomplete read or stalled write after five.
Established idle peers may remain connected. `close` cancels every phase,
discards pending messages and is idempotent; failed handles retain their error.
A clean remote EOF becomes `CLOSED` while retaining complete received messages
until polled or explicitly closed. Scene exit requests, replacement, shutdown
and callback faults close the transport permanently, including accept/connect
waits; stale contexts cannot reopen it.

The wire format is a four-byte big-endian UTF-8 byte length followed by strict
UTF-8 text, with a 16384-byte frame ceiling and the 4096-character limit checked
after decoding. Truncated/invalid frames fail the connection. This is plaintext,
unauthenticated direct TCP; it supplies no discovery, relay, NAT traversal or
automatic reconnect. Validate version, session and every application field.
Send gameplay/control text only: never encode ROM or content assets into
messages. Creators receive no sockets, threads or filesystem handles through
this facade. Existing fixtures may leave `SceneContext.network()` unsupported.

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

Sonic 1 provides detached static pictures for Green Hill act 1 (`0, 0`), and
Sonic 2 for Chemical Plant act 1 (`1, 0`). Those pictures use
ROM art, palettes, layouts and collision without publishing graphics or changing
the running level; they do not run level animation/events/parallax. Unsupported
requests return null or no stages, so query support before composing a scene.

`ctx.art().availableGames()` lists configured, available stock ROMs.
`ctx.art().rom("s1")`, `rom("s2")` and `rom("s3k")` open art from those supplied
games independently of the song or active module. `rom()` retains its original
active-game meaning. Additional ROM handles belong to the scene and close with
it; the active session's borrowed ROM remains open. Missing games return null.

`rom.titleCard(zone, act)` returns an act's stock title card as four sprites (the red banner,
the zone's name, "ZONE" and the act number) whose origins are the ROM's object positions; its
javadoc lists where each slides from and to, at what speed, and when each leaves, so a scene
can play the card. `rom.hasTitleCard(zone, act)` says which acts have one (Sonic 3 & Knuckles
zones 0-12, 22 and 23 today). Slay the Robotnik stages its fights, Starposts and capsules on the level
(`scene/LevelStages`, standing each character on the floor under it) and draws its act maps
over the overview (`scene/MapView`).

`ggfmod sprites` draws every frame of a sprite into one numbered PNG, with each frame's origin
marked, so you can pick frames and check a request before writing any scene code:

```bash
ggfmod sprites s3k.gen s3k rhinobot.png art=0x36732A comp=UNCOMPRESSED size=0xAA0 map=0x3615A8 \
  dplc=0x36156E layout=OBJECT line=1 pal=0x0A8A3C:16:0 pal=0x0A8B7C:48:1
ggfmod sprites s3k.gen s3k knuckles.png char=knuckles   # a playable character, plus its animation scripts
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
- implement `DebuggableScene` (`boolean debugJump(String command)`) so tests and screenshot
  tools can go straight to any screen through the host; return false for commands the scene
  does not know. Slay the Robotnik's `SlayScene.debugJump` documents its command grammar.

Two engine test-tree tools work with any example mod under `examples/` that registers a
startup scene. `ExampleModHarness` builds the mod from source, packages and validates it,
registers it and opens its scene, for your own tests. `ExampleModCapture` boots the base
game headless with GL and plays a script of inputs (`tick:key`, `+ticks:click=x,y`,
`wheel=n`, `jump=<debug command>`...). It saves PNG frames, and can also record the scene's
music and sound effects to a WAV and encode an MP4 with ffmpeg:

```bash
java -cp target/test-classes:target/classes:$(cat target/test-classpath.txt) \
  com.openggf.mods.code.ExampleModCapture --rom s3k.gen --mod examples/slay-the-robotnik \
  --out /tmp/cap --jump "sonic:42:fight:hcz:big_shaker" --script "150:enter +30:enter" \
  --every 30 --video /tmp/cap/fight.mp4 --audio /tmp/cap/fight.wav
```

The class's Javadoc lists every option and script step. To get the classpath file, run
`mvn dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test`.

## 7. Troubleshooting

| What you see | Why, and what to do |
|---|---|
| The stock title screen opens instead of your scene | The mod is not enabled (or not trusted) in the Mod Manager; another enabled mod later in load order also registers a startup scene; you are running a native build, which loads no code mods; or test mode is on (`debug.testMode.enabled`), which skips startup scenes. |
| `ggfmod package` fails with `STATIC_STATE_UNSUPPORTED` | A mod class has mutable static state, an unsupported enum payload, a static collection or array, or arbitrary initialization. Move the state onto your scene or an object it owns; use `static final` numbers and strings for kinds. |
| The mod is disabled and the Mod Manager shows a finding | One of your scene calls threw an exception. The finding and the engine log say which. |
| Reading a mod file from the scene fails | Mod files can only be read during `register`. Read them there and pass the bytes to the scene. |
| A ROM sprite has the wrong colours or is scrambled | The palette line, palette address, compression or DPLC layout does not match the object. Check the request with `ggfmod sprites`, which draws every frame with your settings. |
| Animations stand still in captures, or run fast | State changes in `draw`. The engine may skip or repeat `draw`; advance timers in `update` only. |
| `zoneBackdrop`, `levelStages` or `titleCard` return null | The game has no such picture for that act yet. Ask `hasZonePictures` and `hasTitleCard` first and draw something of your own otherwise. |
