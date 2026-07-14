# sample-flappy Gallery Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gallery sample #6 — "sample-flappy", an additive Sonic 2 patch mod implementing one-button flappy gameplay in a custom zone, whose bird renders Tails' flying frames materialized from the player's ROM via `ModContext.registerRomObjectArt` — plus the `docs/modding/guides/flappy-remix.md` build-along guide.

**Architecture:** The mod clones the `sample-mod-src` template shape (manifest, pom, entrypoint, level-source, base64 binary assets). One custom zone (`insertAfter: "ehz2"` for quick reach) holds a long, thin, terrain-free sky level. A `FlappyController` layout object seizes the main player (`ObjectControlState.NATIVE_BIT_7_FULL_CONTROL` + `setHidden(true)` — this suppresses all player physics/collision/touch), drives gravity/flap/forward motion by writing position directly, force-scrolls the camera every frame (`requestForcedScroll` is frame-scoped), renders the Tails-art bird itself at the player's centre, scores pipe passes as rings, and applies `applyHurtOrDeath` on pipe/bounds contact. Pipes are level-placed layout objects with original generated art.

**Tech Stack:** Java 21, JUnit 5, `ggfmod` toolchain (in-test via `GgfModCli`), Mod API 2.1.0 (`registerRomObjectArt` from the ModRomArtIntake plan).

## Global Constraints

- **Prerequisite:** `docs/superpowers/plans/2026-07-13-mod-rom-art-intake.md` fully executed — `ModContext.registerRomObjectArt(String, RomArtRequest)` and `RomArtCompression` exist and Mod API is 2.1.0.
- Spec: `docs/superpowers/specs/2026-07-13-example-mods-design.md` (Part 2). Sample sources contain ONLY original/generated assets — no ROM bytes, no copyrighted art. Tails' art enters at runtime from the user's ROM only.
- Tests JUnit 5 only. Never `git add -A`. Commit trailer block on every commit (see the ModRomArtIntake plan's Global Constraints for the exact rules; sample-source + docs commits are typically all-`n/a`, engine/test `feat` commits touching `src/main/` need `Changelog` handling).
- PowerShell: quote Maven `-D` properties. ROM-gated tests: `RomTestUtils` S2 helpers + `Assumptions.assumeTrue` (property `sonic2.rom.path`, env `SONIC_2_ROM_PATH`, or `s2.gen` in the working dir).
- The mod's manifest declares `engineApiRange: ">=2.1.0 <3.0.0"` (it requires the ROM-art API).
- **Gallery count coordination:** this plan and the sample-platformer plan each increment `TestSampleModsPackage`'s expected-count assertions and id/range/trust lists by one. Whichever executes first goes 5→6, the second 6→7. Steps below say "increment by one," not absolute numbers.
- Branch: `feature/ai-example-mods`.

## Design constants (fixed for tests and guide)

| Constant | Value | Meaning |
|---|---|---|
| Forward speed | `0x0200` | 2 px/frame rightward |
| Gravity | `0x0038` | added to vertical velocity per frame (S2 standard) |
| Flap impulse | `-0x0400` | vertical velocity on `isJumpJustPressed()` |
| Terminal fall | `0x0800` | max downward velocity |
| Camera lead | 96 | bird sits 96 px from the screen's left edge |
| Pipe spacing | 224 px | one pipe pair per 224 px after x=512 |
| Pipe gap | 96 px | vertical opening |
| Pipe body width | 32 px | contact rect width |
| Level size | 80×2 blocks (10240×256 px) | long thin sky strip |
| Kill bounds | y < 16 or y > 240 | out-of-bounds contact |

All velocities are subpixel units (0x100 = 1 px/frame), matching `setYSpeed`-style semantics; the controller integrates them itself since player physics is suppressed.

---

### Task 1: Level asset generator + level-source

The mod zone needs `level.json` plus the 10-11 binary assets of the exact `ggfmod convert level --from-export` inventory (`patterns, chunks, blocks, foregroundMap, [backgroundMap], solidHeights, solidWidths, solidAngles, collisionPrimary, collisionSecondary, palettes`). `sample-mod-src` ships them base64-embedded in `level-source/binary-assets.properties`, decoded by `TestSampleModsPackage.materializeLevel` at build time. We generate ours with a checked-in deterministic generator.

**Files:**
- Create: `src/test/java/com/openggf/tools/modsdk/SampleFlappyAssetGenerator.java` (a `main()` utility, run manually; output checked in)
- Create: `src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source/level.json`
- Create: `src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source/binary-assets.properties` (generator output)
- Test: `src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java`

**Interfaces:**
- Produces: a level-source dir that `GgfModCli convert level --from-export` accepts, with `zoneName: "FLAPPY GARDEN"`, `zoneIndex: 0x40`, `levelIndex: 0x400`, `blockGridSide: 8`, `width: 80`, `height: 2`, bounds `{minX:0, maxX:10240, minY:0, maxY:256}` (**level-pixel** semantics — full level size, matching sample-mod-src's `maxX:256, maxY:256` precedent; these are NOT camera bounds, and start must lie inside them per `ModLevelDefinitionParser.parseStart`), start `{x:128, y:128}`, music `{"stockId": <stock S2 id — reuse sample-mod-src's 129 unless the guide picks another>}`, `objects[]` = one `sample-flappy:controller` at (128,128) + pipe entries `sample-flappy:pipe` every 224 px from x=512 to x=9700 with `subtype` = gap-centre row variant (0-4 cycling deterministically by index), `rings: []`.

- [ ] **Step 1: Read the format sources**

Read `src/test/resources/mods/sample-mod-src/project/src/main/mod/level-source/level.json` (the working example — copy its field spelling exactly, including `rawYWord = y | (renderFlags<<13) | (respawnTracked?0x8000:0)` with `renderFlags: 0`, `respawnTracked: false`), `ModLevelDefinitionParser` (strict field validation), `FullLevelExporter` (`src/main/java/com/openggf/editor/persistence/FullLevelExporter.java` — the exact binary layouts it writes, especially `palettes()`'s GPAL packing `(b<<9)|(g<<5)|(r<<1)` with magic `"GPAL"`, ver 1, lineCount u16, colorsPerLine u16=16, reserved u16), and `TestSampleModsPackage.materializeLevel` (base64 properties handling).

- [ ] **Step 2: Write the failing test**

```java
package com.openggf.tools.modsdk;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestSampleFlappyLevelSource {

    @TempDir Path temp;

    @Test
    void flappyLevelSourceConvertsCleanly() throws Exception {
        Path src = Path.of("src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source");
        Path export = temp.resolve("export");
        Files.createDirectories(export);
        Files.copy(src.resolve("level.json"), export.resolve("level.json"));
        Properties props = new Properties();
        try (var in = Files.newInputStream(src.resolve("binary-assets.properties"))) {
            props.load(in);
        }
        for (String name : props.stringPropertyNames()) {
            Files.write(export.resolve(name), Base64.getDecoder().decode(props.getProperty(name)));
        }
        Path out = temp.resolve("out");
        int exit = GgfModCli.run(new String[] {
                "convert", "level", "--from-export", export.toString(), "--out", out.toString() });
        assertEquals(0, exit);
        assertTrue(Files.exists(out.resolve("level.json")));
    }
}
```

Adapt the `GgfModCli.run` invocation and decode loop to exactly how `TestSampleModsPackage.materializeLevel` does it (reuse its helper if extractable, else copy the idiom).

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleFlappyLevelSource" test`
Expected: FAIL — source files don't exist.

- [ ] **Step 4: Write the generator and run it**

`SampleFlappyAssetGenerator` (a plain `main()`): builds the minimal binary set in memory and writes `binary-assets.properties` (sorted keys, one base64 prop per inventory file) plus prints the objects[] JSON fragment for pasting into `level.json`:

- **patterns.bin**: a handful of original 8×8 patterns — index 0 blank, 1 sky fill (flat color), 2 a dimmer sky for a simple horizontal band. Write raw 32-byte 4bpp tiles (two pixels per byte, matching the engine's pattern format — confirm byte order against `PatternDecompressor.fromBytes`).
- **chunks.bin / blocks.bin**: chunks composed of the sky patterns; one block type (index 0) of sky chunks; whatever per-entry encoding `FullLevelExporter` writes — mirror it exactly (read its chunk/block writer methods).
- **foregroundMap.bin**: 80×2 map cells, all block 0. Skip `backgroundMap` (optional).
- **solidHeights/solidWidths/solidAngles/collisionPrimary/collisionSecondary**: the all-empty/no-collision profiles, sized as the exporter sizes them (the level is terrain-free; the controller owns all contact).
- **palettes.bin**: GPAL v1, 4 lines × 16 colors. Line 0: reserved comment "player/Tails palette line — loaded by player art path". Lines 1-3: original sky blues/pipe greens/white for HUD-safe colors.

Then hand-write `level.json` per the Interfaces block above, with the pipe objects list generated by the same `main()` (paste output). Run: `mvn test-compile` then execute the generator with the repo root as working dir (e.g. `mvn "-Dexec.mainClass=com.openggf.tools.modsdk.SampleFlappyAssetGenerator" test-compile exec:java` or run the class from the IDE — use whatever exec approach the repo already supports; a plain `java -cp target/test-classes;target/classes ...` also works).

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleFlappyLevelSource" test`
Expected: PASS — the converter validates the full inventory strictly; iterate on the generator until clean.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/openggf/tools/modsdk/SampleFlappyAssetGenerator.java src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java "src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source/level.json" "src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source/binary-assets.properties"
git commit -m "test: sample-flappy level source + deterministic asset generator"
```

---

### Task 2: Mod project scaffold + gallery registration

**Files:**
- Create (clone `sample-mod-src` file-for-file, renaming): `src/test/resources/mods/sample-flappy-src/{build.ps1,build.sh,sample.properties,README.md}`, `project/pom.xml`, `project/README.md`, `project/src/main/resources/META-INF/openggf-mod.yaml`, `project/src/main/java/example/flappysample/FlappySampleMod.java`, plus placeholder pipe art `project/src/main/mod/{pipe.png,pipe-sheet.yaml}` (Task 3 replaces the PNG content; the file must exist for the build).
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java` (count +1, `EXPECTED_IDS`, `EXPECTED_API_RANGES` — this sample declares `>=2.1.0 <3.0.0` — `TRUSTED_CODE_SAMPLES`, source-dir constants, and whatever per-sample build steps list the art/level conversions).

**Interfaces:**
- Produces: `sample-flappy` builds, packages, and validates green through the gallery test. Manifest: `formatVersion: 1`, `id: sample-flappy`, `type: patch`, `baseGame: s2`, `entrypoint: example.flappysample.FlappySampleMod`, `engineApiRange: ">=2.1.0 <3.0.0"`.
- Entrypoint registrations (Tasks 3-4 fill the classes; the keys are fixed now):

```java
public final class FlappySampleMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.registerObject("controller", (spawn, registry) -> new FlappyController(spawn));
        context.registerObject("pipe", (spawn, registry) -> new FlappyPipe(spawn));
        context.registerObjectArt("pipe", new BakedSheetRef("art/pipe.ggfs"));
        context.registerObjectPreview("pipe", "pipe");
        // Tails' flying body frames, materialized from the player's ROM at launch.
        // Literals verified against Sonic2Constants.java:112-117 and ART_TILE_TAILS
        // (0x07A0 -> palette bits 13-14 = line 0). Re-confirm at implementation.
        context.registerRomObjectArt("bird", new RomArtRequest(
                0x64320 /* ART_UNC_TAILS_ADDR */, RomArtCompression.UNCOMPRESSED,
                0xB8C0 /* ART_UNC_TAILS_SIZE */,
                0x739E2 /* MAP_UNC_TAILS_ADDR */,
                0x7446C /* MAP_R_UNC_TAILS_ADDR (DPLC) */,
                0 /* palette line from ART_TILE_TAILS */, 1));
        context.registerZone(new ModZoneContribution("flappy-garden",
                new BakedLevelRef("levels/flappy/level.json"), "ehz2", null));
    }
}
```

The mod source cannot import `Sonic2Constants` (creator code uses literals — the guide explains finding them with RomOffsetFinder); copy the real literal values out of `src/main/java/com/openggf/game/sonic2/constants/Sonic2Constants.java:112-117` and verify the palette line before committing. Match `GgfMod`'s real interface name/method from `Phase2SampleMod`.

- [ ] **Step 1: Read the template**

Read every file under `src/test/resources/mods/sample-mod-src/` and the sample-flappy-relevant parts of `TestSampleModsPackage` (source-dir list, per-sample expected metadata, build/convert steps, trust handling for code-bearing samples).

- [ ] **Step 2: Extend the gallery test first (failing)**

Add `sample-flappy` to the source list and all expectation maps; increment every count assertion by one.

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test`
Expected: FAIL — `sample-flappy-src` project files missing.

- [ ] **Step 3: Create the mod project**

Clone the template files with renames per the Interfaces block. `FlappyController`/`FlappyPipe` start as compilable stubs (empty `updateMovement`/render); `pipe.png`+`pipe-sheet.yaml` copy the template sheet's structure with pipe-sized frames (Task 3 draws real art). `sample.properties`/build scripts: mirror the template's fields with flappy names (they drive the in-test build — confirm which properties the materializer reads).

- [ ] **Step 4: Run the gallery test to verify it passes**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test`
Expected: PASS — builds, packages, validates `sample-flappy` with zero findings alongside the existing samples.

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/mods/sample-flappy-src src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java
git commit -m "test: sample-flappy gallery scaffold builds and validates"
```

---

### Task 3: Pipe art + `FlappyPipe` object

**Files:**
- Modify: `src/test/java/com/openggf/tools/modsdk/SampleFlappyAssetGenerator.java` (add a `pipe.png` emitter — deterministic pixel-drawn pipe pair sheet)
- Create/replace: `src/test/resources/mods/sample-flappy-src/project/src/main/mod/pipe.png`, `pipe-sheet.yaml`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java`

**Interfaces:**
- Consumes: `AbstractObjectInstance` (`update(int,PlayableEntity)`, `appendRenderCommands(List<GLCommand>)`, `getRenderer("sample-flappy:pipe")`, `services()`), spawn `subtype` = gap-centre variant 0-4.
- Produces (consumed by Task 4's controller): `FlappyPipe` exposes its contact geometry as plain methods the controller reads via the object manager scan — `int gapTop()`, `int gapBottom()`, `int leftEdge()`, `int rightEdge()` — derived from `spawn.x()` and subtype: gap centre `= 64 + subtype * 24`, gap half `= 48`, body half-width `= 16`. Renders a top pipe column from y=16 down to `gapTop()` and a bottom column from `gapBottom()` to y=240 by tiling one 32×32 frame plus a 32×16 lip frame. Implements `RewindRecreatable` (`recreateForRewind(ctx) -> new FlappyPipe(ctx.spawn())`; it is stateless beyond spawn).

- [ ] **Step 1: Draw the art deterministically**

Extend the generator: emit `pipe.png` — an original 64×48 sheet (frame 0: 32×32 pipe body tile, frame 1: 32×16 pipe lip) drawn with `BufferedImage` fills/borders in 4 flat greens + black outline (colors from palette line 1 written in Task 1). Write `pipe-sheet.yaml` describing the two frames in the same schema as the template's `sample-sheet.yaml`. Re-run the generator; check in the PNG and YAML.

- [ ] **Step 2: Implement `FlappyPipe`**

```java
public final class FlappyPipe extends AbstractObjectInstance implements RewindRecreatable {
    private final int gapCenter;

    public FlappyPipe(ObjectSpawn spawn) {
        super(spawn);
        this.gapCenter = 64 + (spawn.subtype() % 5) * 24;
    }

    public int leftEdge()   { return spawn().x() - 16; }
    public int rightEdge()  { return spawn().x() + 16; }
    public int gapTop()     { return gapCenter - 48; }
    public int gapBottom()  { return gapCenter + 48; }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // static obstacle; contact and scoring are owned by FlappyController
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer("sample-flappy:pipe");
        if (renderer == null) return;
        for (int y = 16; y + 32 <= gapTop(); y += 32) {
            renderer.drawFrameIndex(0, spawn().x(), y, false, false);
        }
        renderer.drawFrameIndex(1, spawn().x(), gapTop() - 16, false, true);   // top lip
        renderer.drawFrameIndex(1, spawn().x(), gapBottom(), false, false);    // bottom lip
        for (int y = gapBottom() + 16; y < 240; y += 32) {
            renderer.drawFrameIndex(0, spawn().x(), y, false, false);
        }
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FlappyPipe(ctx.spawn());
    }
}
```

Adapt to reality: the exact base-class hook names, `spawn()` accessor, `drawFrameIndex` argument order, and whether `update` is the right override (mirror `SampleBadnik`'s structure — but note `AbstractBadnikInstance` is wrong here; pipes are not badniks and must not take touch damage, so extend `AbstractObjectInstance` directly and implement NO touch/solid marker interfaces). `gapCenter` is derived from spawn — keep it `final` (recreated from spawn, no capture needed; final-scalar capture rules don't bite because recreation re-derives it).

- [ ] **Step 3: Verify through the gallery build**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test`
Expected: PASS (compiles, art converts, packages, validates).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/openggf/tools/modsdk/SampleFlappyAssetGenerator.java src/test/resources/mods/sample-flappy-src
git commit -m "test: sample-flappy pipe art and obstacle object"
```

---

### Task 4: `FlappyController` + ROM-gated integration test

The heart of the mod. The controller is a layout object at the level start that: (1) seizes the main player, (2) runs flappy physics by direct position writes, (3) force-scrolls the camera every frame, (4) renders the Tails bird at the player's centre, (5) scores pipe passes as rings, (6) applies hurt/death on contact and re-seizes after respawn.

**Files:**
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java`
- Test: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java` (ROM-gated on S2)

**Interfaces:**
- Consumes: `ObjectPlayerQuery` via `services().playerQuery().mainPlayerOrNull()`; `AbstractPlayableSprite` control surface (`applyObjectControlState(ObjectControlState.NATIVE_BIT_7_FULL_CONTROL)`, `releaseFromObjectControl(int)`, `setHidden`, `isJumpJustPressed`, `getCentreX/getCentreY`); position writes via the **published** sprite methods `setCentreXPreserveSubpixel(short)` / `setCentreYPreserveSubpixel(short)` / `shiftX(int)` / `shiftY(int)` — do NOT reference `NativePositionOps` from mod source: it is not `@ModApi`, and any non-API engine reference makes `ModValidator` emit `NON_API_ENGINE_REFERENCE`, failing the gallery's zero-findings gate; `services().camera().requestForcedScroll(int,int)` **every frame** (it is frame-scoped; it takes ROM `Scroll_forced_X/Y_pos` **focus coordinates**, not a camera origin — Javadoc at `Camera.java:1302-1314`, MHZ vine precedent passes focus-point coords); `services().levelGamestate().addRings(int)`; `services().playSfx(GameSound.RING)` (or raw stock SFX id); `player.applyHurtOrDeath(int, DamageCause.NORMAL, boolean)`; `FlappyPipe` geometry accessors (Task 3); `getRenderer("sample-flappy:bird")` (ROM-art key).
- Produces: controller behavior contract below (the integration test asserts it).

**Controller structure (all session state in non-final capturable scalar fields for rewind):**

```java
public final class FlappyController extends AbstractObjectInstance implements RewindRecreatable {
    // routine: 0 = waiting to seize, 1 = flying, 2 = released for hurt/death (waiting to re-seize)
    private int routine;
    private int velY;          // subpixels/frame, 0x100 = 1px
    private int ySub;          // fractional y accumulator
    private int lastScoredX;   // rightmost pipe centre already scored
    private int bestScore;     // session best, rewind-captured
    private int animTick;      // bird flying-frame cycle

    public FlappyController(ObjectSpawn spawn) { super(spawn); }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        var player = (AbstractPlayableSprite) services().playerQuery().mainPlayerOrNull();
        if (player == null) return;
        // keep self alive: layout objects despawn when off-camera; track the player
        /* move own currentX/currentY (or equivalent position fields) to player centre */
        switch (routine) {
            case 0 -> { // seize once the player is in NORMAL control on the ground
                player.applyObjectControlState(ObjectControlState.NATIVE_BIT_7_FULL_CONTROL);
                player.setHidden(true);
                velY = 0; routine = 1;
            }
            case 1 -> {
                if (player.isJumpJustPressed()) velY = -0x400;
                velY = Math.min(velY + 0x38, 0x800);
                /* x += 0x200, y += velY: integrate via the ySub/xSub accumulators and write
                   whole-pixel deltas with player.shiftX(dx)/player.shiftY(dy) (published);
                   or write absolute positions with setCentreX/YPreserveSubpixel */
                int px = player.getCentreX(), py = player.getCentreY();
                services().camera().requestForcedScroll(px - 96 + 160, /* fixed */ 112);
                scoreAndCollide(player, px, py, frameCounter);
                animTick++;
            }
            case 2 -> { // engine owns hurt/death/respawn; re-seize when player is back to normal
                if (/* player back in NORMAL state, not hurt/dead — find the predicate on
                       AbstractPlayableSprite (e.g. a status/routine accessor) when reading it */) {
                    routine = 0;
                }
            }
        }
    }

    private void scoreAndCollide(AbstractPlayableSprite player, int px, int py, int frame) {
        if (py < 16 || py > 240) { release(player, frame); player.applyHurtOrDeath(px, DamageCause.NORMAL, true); return; }
        for (var pipe : /* on-screen FlappyPipe instances via services().objectManager() —
                           find the iteration surface (active-object list) when reading it */) {
            if (px > pipe.rightEdge() && pipe.leftEdge() > lastScoredX) {
                lastScoredX = pipe.leftEdge();
                services().levelGamestate().addRings(1);
                services().playSfx(GameSound.RING);
                bestScore = Math.max(bestScore, /* current rings via levelGamestate */);
            }
            boolean insideX = px + 8 > pipe.leftEdge() && px - 8 < pipe.rightEdge();
            boolean insideGap = py - 8 > pipe.gapTop() && py + 8 < pipe.gapBottom();
            if (insideX && !insideGap) { release(player, frame); player.applyHurtOrDeath(px, DamageCause.NORMAL, true); return; }
        }
    }

    private void release(AbstractPlayableSprite player, int frame) {
        player.setHidden(false);
        player.releaseFromObjectControl(frame);
        routine = 2;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 1) return;
        var renderer = getRenderer("sample-flappy:bird");
        if (renderer == null) return;
        var player = (AbstractPlayableSprite) services().playerQuery().mainPlayerOrNull();
        if (player == null) return;
        int frame = /* Tails FLY animation frame indices, cycled by animTick/4 — the exact
                       flying frame numbers come from the mapping order; pick them by eyeballing
                       the materialized sheet during Step 4 and hardcode the cycle */;
        renderer.drawFrameIndex(frame, player.getCentreX(), player.getCentreY(), false, false);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FlappyController(ctx.spawn());
    }
}
```

The `/* ... */` spots are read-then-adapt against real surfaces (exact position-field names on `AbstractObjectInstance`, the active-object iteration API on `ObjectServices.objectManager()`, the player-normal-state predicate, `NativePositionOps` method names, camera x-offset semantics — `requestForcedScroll` takes the camera *focus* coords; verify from the MHZ swing-vine call at `Camera.java:1308-1310` whether the passed X is the focus point or the camera origin, and adjust the `+160` screen-half accordingly). The behavioral contract is fixed: seize→fly→release-on-hit→re-seize; per-frame forced scroll; ring-per-pipe; kill bounds.

- [ ] **Step 1: Read the integration-test precedent and control surfaces**

Read `TestPhase2SampleModIntegration` end-to-end (how it builds+enables the sample mod headlessly, boots `loadZoneAndAct(11, 0)`, steps frames, and what services it reaches), plus `AbstractPlayableSprite`'s control region (L2970-3260), `NativePositionOps`, `ObjectServices.objectManager()`'s object-iteration surface, and `Camera.requestForcedScroll` (L1308-1320).

- [ ] **Step 2: Write the failing integration test**

`TestSampleFlappyIntegration`, modeled structurally on `TestPhase2SampleModIntegration`, gated with `assumeTrue` on the S2 ROM. Assertions, stepping frames headlessly:

```java
// after boot into the flappy zone (synthetic index = stock count + insertion position):
// 1. within a few frames the main player is object-controlled and hidden
assertTrue(player.isObjectControlled());
assertTrue(player.isHidden());
// 2. with no input, vertical position increases (gravity) over 30 frames
// 3. a simulated jump edge makes centreY decrease over the following 10 frames
// 4. the camera x advances monotonically across 60 frames (forced scroll)
// 5. the bird renderer is ready: mod art registry serves "sample-flappy:bird"
//    (reach the ObjectArtProvider via the resolved module and assert getRenderer != null)
// 6. drive the player past the first pipe's rightEdge (step enough frames);
//    assert rings incremented by 1
// 7. force a collision (position the player into a pipe body via the controller's
//    own physics by not flapping); assert player no longer object-controlled
//    (released) and hurt/death engaged
```

How to simulate the jump edge headlessly: find how `TestPhase2SampleModIntegration` (or `HeadlessTestRunner`) injects input for the main player and reuse it; if the phase-2 test never presses buttons, use `HeadlessTestRunner.stepFrame(false,false,false,false,true)` style stepping if compatible, else the input-provider seam that test's boot path exposes. This is the step's main discovery burden — resolve it before writing assertions 2-3, and keep assertions 6-7 as deterministic frame-count playouts (no input: the bird sinks; scripted flaps: survives to pipe 1).

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" test`
Expected: FAIL — controller stub does nothing (player never seized).

- [ ] **Step 4: Implement the controller**

Fill `FlappyController` per the structure above against the real surfaces. Verify the bird's palette line and flying-frame indices against the materialized Tails sheet (run the integration test with a temporary frame-dump or assert-print, pick the 2-frame fly cycle, hardcode). Iterate until all assertions pass.

- [ ] **Step 5: Run the integration + gallery + rewind-relevant tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" test` — Expected: PASS.
Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test` — Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/test/resources/mods/sample-flappy-src src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java
git commit -m "test: sample-flappy controller gameplay + ROM-art bird integration"
```

---

### Task 5: Guide + gallery docs + CHANGELOG

**Files:**
- Create: `docs/modding/guides/flappy-remix.md`
- Modify: `docs/modding/index.md` (link the guide under a new "Guides" list), `docs/modding/samples/index.md` (add the sample entry; fix the "exactly five" wording to the current count), `src/test/resources/mods/sample-flappy-src/README.md` (already scaffolded — flesh out), `CHANGELOG.md`

**Interfaces:** none — documentation.

- [ ] **Step 1: Write the build-along guide**

`docs/modding/guides/flappy-remix.md`, narrated in build order with real commands and the real code (excerpted from the committed sample — the sample is the executable contract; the guide points at it per gallery convention). Chapters:
1. **What you'll build** — one-button flappy minigame inside Sonic 2; screenshot placeholder note; requires your own legally-obtained S2 ROM.
2. **Project setup** — `ggfmod init`, manifest fields, `engineApiRange: ">=2.1.0 <3.0.0"`.
3. **Borrowing Tails from your ROM** — the ROM-art intake story: why the jar ships zero ROM bytes, finding the art/mapping/DPLC addresses with RomOffsetFinder, `registerRomObjectArt`, palette lines, the launch-abort fault behavior on a bad address.
4. **The level** — `ModLevelDefinition` fields, generating/authoring the sky strip, placing pipes as objects with subtypes, `insertAfter: "ehz2"` and how progression reaches the zone (finish EHZ act 2).
5. **Seizing the player** — object control states, why `NATIVE_BIT_7_FULL_CONTROL`, hidden player, direct position writes, per-frame `requestForcedScroll`.
6. **Pipes, score, death** — contact rects, rings as score, hurt/death and re-seize, the rewind checklist step (non-final scalar fields + `RewindRecreatable` on every object).
7. **Package, trust, play** — `mvn package`, `ggfmod package`, `ggfmod validate`, drop in `mods/`, Mod Manager enable + restart, the per-jar-hash trust prompt for code-bearing mods.

Keep ids IP-neutral (`sample-flappy`); the prose may say "Tails' flying art" descriptively. Match the quickstarts' tone but longer-form.

- [ ] **Step 2: Update the handbook index, samples index, CHANGELOG**

`docs/modding/index.md`: add a "Follow-along guides" list item linking the new guide. `docs/modding/samples/index.md`: add entry 6 (source path, one-line scope: "additive S2 patch — object-controlled minigame gameplay, ROM-art intake, forced scroll, layout obstacles") and correct the "exactly these five" sentence to the current count. `CHANGELOG.md`: one entry for the new gallery sample + guide.

- [ ] **Step 3: Full verification sweep**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test` and `mvn "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" test`
Expected: PASS. Then `mvn test` — no new failures vs branch baseline.

- [ ] **Step 4: Commit**

```bash
git add docs/modding/guides/flappy-remix.md docs/modding/index.md docs/modding/samples/index.md src/test/resources/mods/sample-flappy-src/README.md CHANGELOG.md
git commit -m "docs: flappy-remix build-along guide for gallery sample" # trailers: Changelog: updated
```

---

## Plan-level notes for the executor

- **Hard dependency:** do not start before the ModRomArtIntake plan is merged into this branch — Task 2's entrypoint and Task 4's bird rendering need `registerRomObjectArt` + Mod API 2.1.0.
- **Mod source may reference ONLY `@ModApi` types** (check `mod-api-signatures-2.*.txt`): `ModValidator` emits `NON_API_ENGINE_REFERENCE` for anything else and the gallery gate requires zero findings. Known verified-published surfaces this plan uses: `setCentreX/YPreserveSubpixel`, `shiftX/shiftY`, `ObjectManager.activeObjectsOfType(Class)`, `GameSound.RING`, `LevelState.addRings(int)`, `DamageCause`, `GLCommand`.
- **Base-class facts** (verified): `AbstractObjectInstance` has only the 2-arg constructor `(ObjectSpawn spawn, String name)` and a `protected final ObjectSpawn spawn` **field** (no `spawn()` accessor) — sketches' `super(spawn)`/`spawn()` adapt to `super(spawn, "sample-flappy:pipe")`/`spawn.x`. `update(int, PlayableEntity)` is overridable on `AbstractObjectInstance` (final only on `AbstractBadnikInstance`). `GgfModCli.run` is `run(String[], PrintStream)` — no 1-arg overload.
- **Spec deviation (documented):** the spec's "bird companion object" is folded into the controller's own `appendRenderCommands` — two mod object classes (controller, pipe), not three. State this in the guide so the spec's rewind sentence reads correctly.
- **`bestScore` is controller-lifetime:** a death reload recreates the controller and resets it. That is consistent with the spec's session-lifetime-only scope; say so in the guide rather than fighting it.
- The controller/pipe code sketches are behavioral contracts; every `/* ... */` marks a read-then-adapt against a named real surface. The constants table at the top is fixed — tests and guide use those exact values.
- **Two verified-at-implementation facts** must be pinned before Task 2 commits: the Sonic2Constants Tails address literals and the S2 player palette line (from `Sonic2PlayerArt.loadTails()`).
- Layout-object despawn windowing is the classic trap: the controller must move its own object position with the player every frame or it despawns once the camera scrolls away from the level start.
- If TestSampleModsPackage's per-sample build steps hardcode the art-convert file names, the flappy entries must list both `pipe.png` conversion and the level conversion; mirror exactly how sample-mod-src's steps are declared.
