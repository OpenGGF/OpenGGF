# sample-platformer Gallery Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gallery sample #7 — "sample-platformer", a no-ROM standalone module with one TMX-authored act, an original character with a distinct `PhysicsProfile` and a double-jump `onAbilityActivate` hook, one badnik, one spring gimmick, OGG music + WAV SFX, master-title entry, and save/Continue — plus `docs/modding/guides/standalone-platformer.md` and `docs/modding/guides/ai-art.md`.

**Architecture:** Clones the `sample-standalone-src` shape (entrypoint → `PlayableSheetMaterializer` + `StandaloneLevelLoader` + `registerCharacter` + `registerGameModule`), but swaps the level pipeline to the hardened TMX path (`ggfmod convert level --from-tmx --palette <GPAL>` with the default two-profile collision: GID 0 = no collision, GID 1 = all-solid) and renders real generated art everywhere the phase-3 sample no-opped. All assets come from one checked-in deterministic Java generator. Integration testing clones `TestPhase3StandaloneSampleIntegration`'s headless no-ROM harness and adds double-jump and rewind coverage.

**Tech Stack:** Java 21, JUnit 5, `ggfmod` toolchain in-test (`GgfModCli`), Mod API 2.0 surface only (consume-only — this mod must NOT require the ROM-art API).

## Global Constraints

- **Independent of the ModRomArtIntake plan** — may execute in parallel with or before it. This mod consumes only the existing 2.0 `@ModApi` surface; it must not add, rename, or modify any `@ModApi` type or `TestModApiSignatureSurface` breaks. Everything needed is already published: `onAbilityActivate`, `getAir`, `setYSpeed`, `setJumping`, `SpringBounceHelper`, `PlatformBobHelper`, `RewindRecreatable`, `CharacterDefinition`, `PhysicsProfile`, `StandaloneLevelLoader`, `BakedLevelRef`, `StreamedMusicPort.SfxRef/TrackRef`.
- Spec: `docs/superpowers/specs/2026-07-13-example-mods-design.md` (Part 3). All assets original/generated; manifest `engineApiRange: ">=2.0.0 <3.0.0"`, `type: standalone`, NO `baseGame` field, `id: sample-platformer`.
- Tests JUnit 5 only. Never `git add -A`. Commit trailer block on every commit (all-`n/a` for sample-source/docs commits; `Changelog` handling on any `src/main/` `feat`/`fix` — this plan should touch no `src/main/` at all).
- **Gallery count coordination:** increment `TestSampleModsPackage` count assertions by one (5→6 or 6→7 depending on which sample plan executes first).
- **Known accepted limitation** (from research, set expectations in the guide): the `--playable` converter synthesizes exactly ONE `idle` animation cycling the sheet's frames by their delays. Multi-state animation (walk/jump/roll scripts) is not authorable with the shipped SDK. The character therefore renders one looping animation regardless of movement state — design the art as a self-animating character (see Task 1).
- No mutable statics in mod object/character code (static-state rewind guard class of bug); all session state in non-final instance scalar fields.
- Branch: `feature/ai-example-mods`.

## Design constants

| Item | Value |
|---|---|
| Character | "Bolt" — a round robot with a blinking antenna (2-frame idle cycle carries the personality since one animation is all we get) |
| PhysicsProfile | distinct floaty tuning: `runAccel 0x20, runDecel 0x80, friction 0x20, max 0x480, jump 0x780, slopeRunning 0x20, slopeRollingUp 0x14, slopeRollingDown 0x50, rollDecel 0x20, minStartRollSpeed 0x80, minRollSpeed 0x80, maxRoll 0x1000, rollHeight 28, runHeight 38, standXRadius 9, standYRadius 19, rollXRadius 7, rollYRadius 14, singleFacingBalance false, onObjectBalanceShift 2` (all `short`; asserts distinguish it from `PhysicsProfile.SONIC_2_SONIC` and from the phase-3 sample's `max 0x500`) |
| Double jump | on airborne ability press, once per airtime: `setYSpeed(-0x600)`, `setJumping(false)`, latch `doubleJumpUsed=true`, reset on `getAir()` false edge; plays namespaced `jump2` SFX |
| Level | TMX 128×16 tiles (2048×256 px), solid ground rows + 3 floating platforms + a pit, start marker at (64, 160), rings via `ring` markers, badnik + spring via `object` markers |
| Music/SFX | track `zone-theme` (OGG, looped), sfx `jump2` + `hit` + `spring` (WAV) |
| Sidekick | none — `supportsSidekick()` returns `false` (spec scope: one character) |

---

### Task 1: Deterministic asset generator + all assets

**Files:**
- Create: `src/test/java/com/openggf/tools/modsdk/SamplePlatformerAssetGenerator.java` (a `main()` utility; output checked in)
- Create under `src/test/resources/mods/sample-platformer-src/project/src/main/mod/`:
  - `tileset.png` (16×16 tiles: grass top, dirt fill, platform, decoration — drawn with `BufferedImage` flat fills + 1px outlines)
  - `level.tmx` (generator-emitted XML)
  - `palette.gpal` (generator-emitted binary)
  - `bolt.png` + `bolt-sheet.yaml` (character: 2+ frames, antenna blink cycle; `paletteLine: 0`, 16 `#RRGGBB` Genesis-representable colors)
  - `zapbug.png` + `zapbug-sheet.yaml` (badnik: 2 frames)
  - `springpad.png` + `springpad-sheet.yaml` (gimmick: compressed + extended frames)
- Create under `.../project/src/main/resources/audio/`: `zone-theme.ogg`, `jump2.wav`, `hit.wav`, `spring.wav`, `audio-manifest.yaml`
- Test: `src/test/java/com/openggf/tools/modsdk/TestSamplePlatformerLevelSource.java`

**Interfaces:**
- Produces: a `level.tmx` + `palette.gpal` pair that `GgfModCli convert level --from-tmx ... --palette ... --out ...` converts cleanly (exit 0, deterministic output), and PNG/YAML/WAV/OGG assets the later tasks package.

- [ ] **Step 1: Read the format sources**

Read `TmxLevelImporter.parse` (exact structural rules: orthogonal, `tilewidth/tileheight=16`, width/height in tiles `%8==0`, one tileset `firstgid="1"` with `margin=0 spacing=0`, layers `FG` (required)/`BG`/`COLLISION`/`COLLISION_ALT` each one `<data encoding="csv">`, optional `<objectgroup name="OBJECTS">` with point objects of class `object|ring|start` carrying `stockObjectId` XOR `objectKey` + optional `subtype`/`respawnTracked` properties), `TestTmxLevelImporter` (inline TMX strings to crib exact XML shape), the GPAL layout (magic `"GPAL"`, u16 ver=1, u16 lines 1-4, u16=16, u16=0, then `lines*16` u16 words `(b3<<9)|(g3<<5)|(r3<<1)`, length `12+lines*32`), the sheet-YAML schema from `sample-standalone-src/project/src/main/mod/runner-sheet.yaml`, `ModAudioManifestParser` (tracks require `id, assetPath, loop, loopStartFrame, gain, tempoEffects`; sfx require `id, assetPath, gain`), and `docs/modding/samples/phase4-gallery-music-pack/generate-assets.py` (the deterministic WAV precedent: integer-cycle tones for seamless loops).

- [ ] **Step 2: Write the failing TMX-conversion test**

```java
package com.openggf.tools.modsdk;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestSamplePlatformerLevelSource {

    @TempDir Path temp;

    @Test
    void platformerTmxConvertsCleanlyAndDeterministically() throws Exception {
        Path mod = Path.of("src/test/resources/mods/sample-platformer-src/project/src/main/mod");
        Path out1 = temp.resolve("out1");
        Path out2 = temp.resolve("out2");
        for (Path out : new Path[] { out1, out2 }) {
            int exit = GgfModCli.run(new String[] { "convert", "level",
                    "--from-tmx", mod.resolve("level.tmx").toString(),
                    "--palette", mod.resolve("palette.gpal").toString(),
                    "--out", out.toString() });
            assertEquals(0, exit);
            assertTrue(Files.exists(out.resolve("level.json")));
        }
        // determinism: byte-identical across runs (mirrors TestTmxLevelImporter's guarantee)
        assertArrayEquals(Files.readAllBytes(out1.resolve("fg-map.bin")),
                Files.readAllBytes(out2.resolve("fg-map.bin")));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSamplePlatformerLevelSource" test`
Expected: FAIL — assets don't exist.

- [ ] **Step 4: Write and run the generator**

`SamplePlatformerAssetGenerator.main()` emits, deterministically (fixed seeds/constants, no timestamps):
1. **`palette.gpal`** — 2 lines: line 0 character colors, line 1 tile/object colors (write the exact binary layout from Step 1).
2. **`tileset.png`** — 8 tiles of 16×16 in one row (blank, grass, dirt, platform, spike-free decoration, sky details), colors drawn from palette line 1's RGB values so quantization is lossless.
3. **`level.tmx`** — XML with FG layer (ground rows y=13-15 solid run with a 4-tile pit at x=60-63, three floating 4-tile platforms), COLLISION layer (GID 1 under every solid FG cell, GID 0 elsewhere), OBJECTS group: `start` at (64,160); `object` markers with `objectKey: "sample-platformer:zapbug"` (one, on the ground at x≈900 px) and `objectKey: "sample-platformer:springpad"` (one, before the pit); ~20 `ring` markers along the route. Width 128, height 16 tiles.
4. **Sprite PNGs + sheet YAMLs** — `bolt.png` (2 frames of 24×32, antenna blink; YAML `paletteLine: 0`, per-frame `delay` ~30), `zapbug.png` (2 frames 24×16, `paletteLine: 1`), `springpad.png` (2 frames 32×16 compressed/extended, `paletteLine: 1`). Piece coords 8-aligned per the YAML schema.
5. **WAV SFX** — `jump2.wav`, `hit.wav`, `spring.wav`: short (≤0.4 s) 16-bit mono 22050 Hz square/triangle blips written with `javax.sound.sampled`/raw RIFF (integer cycles, distinct pitches).
6. **`zone-theme.ogg`** — the generator writes a deterministic looping `zone-theme.wav` source (integer-cycle chiptune arpeggio, ~8 s, like the music-pack generator); encode it once with `ffmpeg -i zone-theme.wav -c:a libvorbis -q:a 3 zone-theme.ogg` and check in the OGG (delete the intermediate WAV). If no Vorbis encoder is available on the machine, **stop and surface the gap** rather than silently shipping WAV music — the spec pins OGG for the track.
7. **`audio-manifest.yaml`**:

```yaml
formatVersion: 1
tracks:
  - id: zone-theme
    assetPath: audio/zone-theme.ogg
    loop: true
    loopStartFrame: 0
    gain: 1.0
    tempoEffects: false
sfx:
  - id: jump2
    assetPath: audio/jump2.wav
    gain: 1.0
  - id: hit
    assetPath: audio/hit.wav
    gain: 1.0
  - id: spring
    assetPath: audio/spring.wav
    gain: 1.0
```

(Adapt `tempoEffects`' type to what `ModAudioManifestParser` actually expects, and `loopEndFrame` if required when `loop: true`.) Run the generator; check in all outputs.

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSamplePlatformerLevelSource" test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/openggf/tools/modsdk/SamplePlatformerAssetGenerator.java src/test/java/com/openggf/tools/modsdk/TestSamplePlatformerLevelSource.java src/test/resources/mods/sample-platformer-src
git commit -m "test: sample-platformer deterministic assets and TMX level source"
```

---

### Task 2: Mod project scaffold + gallery registration

**Files:**
- Create (clone `sample-standalone-src` with renames): `src/test/resources/mods/sample-platformer-src/{README.md,build.sh,build.ps1}`, `project/pom.xml` (swap the level execution to `convert level --from-tmx ... --palette ...`; add the three extra art conversions; keep audio as plain resources), `project/README.md`, `project/src/main/resources/META-INF/openggf-mod.yaml`, `project/src/main/java/example/platformer/{PlatformerMod,PlatformerModule,BoltCharacter,ZapBug,SpringPad}.java` (compilable stubs beyond the module skeleton)
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java` (count +1; `EXPECTED_IDS`, `EXPECTED_API_RANGES` (`">=2.0.0 <3.0.0"`), `TRUSTED_CODE_SAMPLES`; add `materializePlatformer(Path)` modeled on `materializeStandalone` but running `convert art` for the three object sheets, `convert art --playable` for bolt, and `convert level --from-tmx --palette` for the level)

**Interfaces:**
- Produces: `sample-platformer` builds, packages, and validates green in the gallery test. Entrypoint (final shape — later tasks fill class bodies):

```java
public final class PlatformerMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        try {
            ModAssetRoot assets = context.modAssets();
            var materialized = PlayableSheetMaterializer.read(
                    assets.readBounded("art/bolt.ggfp", assets.limits().maxAssetBytes()));
            Level level = StandaloneLevelLoader.load(assets,
                    new BakedLevelRef("levels/act1/level.json"), context.ownerModId(),
                    new RingSpriteSheet(new Pattern[0], List.of(), 0, 8, 0, 0));
            context.registerObject("zapbug", (spawn, registry) -> new ZapBug(spawn));
            context.registerObjectArt("zapbug", new BakedSheetRef("art/zapbug.ggfs"));
            context.registerObject("springpad", (spawn, registry) -> new SpringPad(spawn));
            context.registerObjectArt("springpad", new BakedSheetRef("art/springpad.ggfs"));
            context.registerCharacter("bolt", BoltCharacter.definition(
                    context.ownerModId(), materialized));
            context.registerGameModule(new PlatformerModule(context.ownerModId(), level));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

Mirror `Phase3StandaloneMod`'s real imports/idioms exactly (`RingSpriteSheet` construction, `context.modAssets()` accessor name, ring-sheet handling — if the phase-3 sample passes a real ring sheet, copy that; the level has rings). `PlatformerModule` clones `SampleStandaloneModule`'s structure: one zone, one act, `loadLevel(0x400)`, `getMusicReference → MusicReference.namespaced(owner, "zone-theme")`, `supportsSidekick() → false`, the save-snapshot provider map (`zone/act/mainCharacter/sidekicks/clear` with topology clamping), silent `GameAudioProfile`, and the same registry/init-profile inner classes.

- [ ] **Step 1: Read the template end-to-end**

Every file under `src/test/resources/mods/sample-standalone-src/` plus `TestSampleModsPackage`'s `materializeStandalone` and the build-script three-arg contract (engine jar, sdk jar, out dir).

- [ ] **Step 2: Extend the gallery test first (failing)**

Add `sample-platformer` to the source list, expectation maps, trusted-code set; increment counts; write `materializePlatformer`.

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test`
Expected: FAIL — project files missing.

- [ ] **Step 3: Create the project**

Clone + rename per the Interfaces block. `BoltCharacter`/`ZapBug`/`SpringPad` compile with minimal bodies (`BoltCharacter.definition(...)` already returns the full `CharacterDefinition` with the Design-constants `PhysicsProfile` literal, `behavesLike: PlayerCharacter.SONIC_ALONE`, `secondaryAbility: SecondaryAbility.NONE`, `supportsSuperForm: false`, sprite factory `(code,x,y) -> new BoltCharacter(code,x,y)`, art/palette suppliers from the materialized `.ggfp`; the sprite class clones `SampleCharacter`'s ctor/`characterKey()`/`draw()`/`defineSpeeds()`/`createSensorLines()` shape). `ZapBug`/`SpringPad` stubs: spawn-holding, no-op update/render.

- [ ] **Step 4: Run gallery test to verify it passes**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test`
Expected: PASS — packages and validates with zero findings.

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/mods/sample-platformer-src src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java
git commit -m "test: sample-platformer standalone scaffold builds and validates"
```

---

### Task 3: Integration harness — boot, title, save/Continue

**Files:**
- Test: `src/test/java/com/openggf/mods/integration/TestSamplePlatformerIntegration.java` (`@Isolated`, headless, ROM-independent)

**Interfaces:**
- Consumes: `TestPhase3StandaloneSampleIntegration`'s harness idioms — build via `build.ps1`/`build.sh`, `ModCatalogValidator`, `ModClassLoaderFactory`, `graphics().initHeadless()`, `NullAudioBackend`.
- Produces: the integration test file that Tasks 4-5 extend with gameplay assertions.

- [ ] **Step 1: Read the phase-3 integration test end-to-end**

`src/test/java/com/openggf/mods/integration/TestPhase3StandaloneSampleIntegration.java` — including `exerciseMasterTitleNewCompleteAndContinue` (L195-290: how "complete a level" is driven in a standalone module, how Continue restoration is asserted, how corrupt-slot cases hide Continue) and the jar-build/classloader setup.

- [ ] **Step 2: Write the failing test (structural clone)**

Clone the phase-3 test's skeleton for `sample-platformer`, asserting in this task:
- `GameId.STANDALONE`; `getIdentifier()` == `"sample-platformer"`; `getZoneCount()==1`; terminal progression is `Credits`.
- `loadLevel(0x400)` returns the level; any other index throws.
- `getMusicReference(0,0)` is namespaced `("sample-platformer","zone-theme")`.
- Character definition: `SecondaryAbility.NONE`, `!supportsSuperForm()`, `supportsSidekick()==false` on the module, profile `max()==0x480` and `jump()==0x780`, `!profile.equals(PhysicsProfile.SONIC_2_SONIC)`, non-empty mapping frames, non-null palette.
- All four audio assets decode non-zero PCM through the bounded pool (mirror the phase-3 WAV-decode assertion, plus the OGG track).
- Master-title New Game → save → complete → credits → Continue restoration (adapt `exerciseMasterTitleNewCompleteAndContinue` wholesale), plus the corrupt-slot Continue-hiding cases.

Run: `mvn "-Dtest=com.openggf.mods.integration.TestSamplePlatformerIntegration" test`
Expected: FAIL or ERROR on the module-behavior assertions not yet implemented (module skeleton exists from Task 2 — most structural assertions may already pass; the failing frontier tells you what Task 2 stubbed wrong; fix module-level gaps now until this task's assertion set is green).

- [ ] **Step 3: Make it pass, then commit**

Run: `mvn "-Dtest=com.openggf.mods.integration.TestSamplePlatformerIntegration" test` — Expected: PASS.

```bash
git add src/test/java/com/openggf/mods/integration/TestSamplePlatformerIntegration.java src/test/resources/mods/sample-platformer-src
git commit -m "test: sample-platformer integration harness with title/save coverage"
```

---

### Task 4: Bolt character — double jump + rewind

**Files:**
- Modify: `src/test/resources/mods/sample-platformer-src/project/src/main/java/example/platformer/BoltCharacter.java`
- Modify: `src/test/java/com/openggf/mods/integration/TestSamplePlatformerIntegration.java` (add gameplay assertions)

**Interfaces:**
- Consumes: `protected boolean onAbilityActivate(boolean up, boolean down, boolean left, boolean right)` (`AbstractPlayableSprite:1560` — fires on airborne ability-press before built-in secondary-ability dispatch; return `true` consumes), `setYSpeed(short)`, `setJumping(boolean)`, `getAir()`, `services()`-free SFX from a sprite context (find how a playable sprite plays SFX — the phase-3 sample or `PlayableSpriteMovement.lightningShieldJump()` L1437-1450 shows the idiom; if sprites lack a clean SFX path, skip the sound rather than adding API).
- Produces: double-jump behavior with a rewind-captured latch.

```java
public final class BoltCharacter extends AbstractPlayableSprite {
    private boolean doubleJumpUsed;   // non-final => rewind-captured; resets on landing

    // ctor/characterKey()/draw()/defineSpeeds()/createSensorLines(): clone SampleCharacter

    @Override
    protected boolean onAbilityActivate(boolean up, boolean down, boolean left, boolean right) {
        if (doubleJumpUsed) {
            return false;
        }
        doubleJumpUsed = true;
        setYSpeed((short) -0x600);
        setJumping(false);
        /* play namespaced "jump2" SFX via the sprite-side idiom found in Step 1, if one exists */
        return true;
    }

    /* landing reset: find the per-frame hook or air-state transition SampleCharacter/
       AbstractPlayableSprite exposes (an overridable update/land hook) and clear
       doubleJumpUsed when getAir() goes false. If no overridable per-frame hook exists,
       clear it inside onAbilityActivate's guard via a grounded check:
       doubleJumpUsed && !getAir() cannot happen at press-time (hook is airborne-only),
       so a lazily-reset latch needs the real landing hook — resolve from source. */
}
```

The landing-reset mechanism is this task's one discovery point — resolve it from `AbstractPlayableSprite`/`SampleCharacter` source before writing assertions.

- [ ] **Step 1: Read the hook dispatch and landing path**

`AbstractPlayableSprite.onAbilityActivate` (L1560) + its dispatch (`CharacterRuntimeHooks.activateAbility`), `PlayableSpriteMovement.lightningShieldJump()` (L1437-1450, the impulse idiom), and the air→ground transition to find the reset seam.

- [ ] **Step 2: Add failing integration assertions**

In `TestSamplePlatformerIntegration`, drive the constructed player headlessly (the phase-3 test constructs the player and steps physics — reuse; `HeadlessTestRunner` if compatible):
- jump from ground, then ability-press mid-air → `getYSpeed()` becomes `-0x600` (double jump fired);
- second mid-air press → velocity unchanged (latch);
- land, jump again, mid-air press → fires again (reset works);
- **rewind coverage**: capture the sprite's rewind state after the double jump, restore it, assert `doubleJumpUsed` survives (drive whatever capture/restore surface the phase-3 or rewind tests use for playable sprites — `GenericFieldCapturer` directly is acceptable at test level).

Run: expect the new assertions to FAIL against the stub.

- [ ] **Step 3: Implement, pass, commit**

Run: `mvn "-Dtest=com.openggf.mods.integration.TestSamplePlatformerIntegration" test` — Expected: PASS.
Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test` — Expected: PASS.

```bash
git add src/test/resources/mods/sample-platformer-src src/test/java/com/openggf/mods/integration/TestSamplePlatformerIntegration.java
git commit -m "test: sample-platformer double-jump character with rewind latch"
```

---

### Task 5: ZapBug badnik + SpringPad gimmick

**Files:**
- Modify: `.../example/platformer/ZapBug.java`, `.../example/platformer/SpringPad.java`
- Modify: `src/test/java/com/openggf/mods/integration/TestSamplePlatformerIntegration.java`

**Interfaces:**
- Consumes: `AbstractBadnikInstance` (`updateMovement(int,PlayableEntity)`, `getCollisionSizeIndex()`, `getDestructionConfig()`, `appendRenderCommands`), `PatrolMovementHelper` (the intended patrol API — use it, unlike the phase-3 sample's raw subpixel counter), `SpringBounceHelper` (`strength(boolean)`, `STRENGTH_YELLOW`, `CONTROL_LOCK_FRAMES`), `getRenderer("sample-platformer:zapbug"/"sample-platformer:springpad")`, `services().playSfx(new StreamedMusicPort.SfxRef("sample-platformer","spring"/"hit"))`, `RewindRecreatable`.
- Produces:
  - `ZapBug extends AbstractBadnikInstance implements RewindRecreatable` — patrols via `PatrolMovementHelper`, 2-frame animation, real rendering, ENEMY touch (inherited badnik behavior), destruction config with the `hit` SFX fired on destruction if the badnik surface exposes it (else on player contact — mirror how stock badnik destruction SFX works via `DestructionEffects`).
  - `SpringPad extends AbstractObjectInstance implements RewindRecreatable` — when the player lands on its 32×16 top (detect via the solid/riding surface available on the 2.0 surface: if `SolidObjectProvider` is NOT published, do proximity+velocity detection: player centre within the pad rect and `getYSpeed() > 0`), apply `player.setYSpeed(-SpringBounceHelper.STRENGTH_YELLOW)` semantics via the helper's real API, play `spring` SFX, switch to the extended frame for 8 frames (`animTick` non-final field).

- [ ] **Step 1: Read the helper surfaces**

`PatrolMovementHelper`, `SpringBounceHelper` (real method names/semantics), `AbstractBadnikInstance` hooks, `sample-mod-src`'s `SampleBadnik` (rendering idiom), and check `mod-api-signatures-2.0.txt` for whether any solid-object marker interface is published (research says no — plan for proximity detection).

- [ ] **Step 2: Add failing integration assertions**

- ZapBug: after N frames its X changed (patrol) and reverses at patrol bounds; renderer for `"sample-platformer:zapbug"` resolves.
- SpringPad: place the player falling onto the pad rect; after the contact frame `getYSpeed()` is negative with the helper's yellow strength; `spring` SFX fired (assert via the audio-port seam the phase-3 test uses for SFX assertions).
- Rewind: `recreateForRewind` on both returns fresh instances of the same class from `ctx.spawn()`.

- [ ] **Step 3: Implement, pass, commit**

Run: `mvn "-Dtest=com.openggf.mods.integration.TestSamplePlatformerIntegration" test` and `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test` — Expected: PASS.

```bash
git add src/test/resources/mods/sample-platformer-src src/test/java/com/openggf/mods/integration/TestSamplePlatformerIntegration.java
git commit -m "test: sample-platformer badnik and spring gimmick"
```

---

### Task 6: Guides (platformer + AI-art), gallery docs, CHANGELOG

**Files:**
- Create: `docs/modding/guides/standalone-platformer.md`, `docs/modding/guides/ai-art.md`
- Modify: `docs/modding/index.md`, `docs/modding/samples/index.md`, `src/test/resources/mods/sample-platformer-src/README.md`, `CHANGELOG.md`

- [ ] **Step 1: Write `standalone-platformer.md`**

Build-along chapters, real commands, code excerpted from the committed sample (the executable contract): (1) what you'll build + no ROM needed; (2) project setup (`ggfmod init`, standalone manifest, no `baseGame`); (3) authoring the level in Tiled — layer names, per-tile collision GIDs, object/ring/start markers, `convert level --from-tmx --palette`, the GPAL file, determinism guarantee; (4) the character — sheet YAML, `convert art --playable`, the **single-`idle`-animation limitation stated plainly**, `CharacterDefinition` fields, the `PhysicsProfile` knobs and what each did to Bolt's feel; (5) the double jump — `onAbilityActivate` semantics, the latch, the landing reset, **the rewind checklist step** (non-final scalar fields, `RewindRecreatable`, no mutable statics); (6) badnik + gimmick — `AbstractBadnikInstance`, `PatrolMovementHelper`, `SpringBounceHelper`; (7) music and SFX — audio manifest, OGG loop frames, the 16-voice pool; (8) package, trust, play — `mods/` dir, Mod Manager, per-jar-hash trust prompt, master-title entry, save/Continue.

- [ ] **Step 2: Write `ai-art.md`**

The spec's AI-art chapter: generating your own character/tile PNGs with an image model — prompt guidance for pixel-art sprites (fixed frame sizes, flat colors, transparent background), quantizing to a 16-color Genesis-representable palette (each channel to 3-bit — show a short imagemagick/Python recipe), laying frames out on an 8-aligned grid, writing the sheet YAML, reading the converter's VRAM/bank cost warning, and swapping your art into either sample (replace `bolt.png` + rerun the build). Explicitly note what stays hand-authored: sheet YAML, palette line assignments, TMX collision.

- [ ] **Step 3: Indexes + CHANGELOG**

`docs/modding/index.md`: add both guides to the guides list (create the list if the flappy plan hasn't already). `docs/modding/samples/index.md`: add the sample-platformer entry; fix the count wording. `CHANGELOG.md`: one entry (new gallery sample + two guides). Flesh out both READMEs (sample root + project) mirroring the phase-3 sample's README structure.

- [ ] **Step 4: Full verification**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test`, `mvn "-Dtest=com.openggf.mods.integration.TestSamplePlatformerIntegration" test`, then `mvn test` — no new failures vs branch baseline.

- [ ] **Step 5: Commit**

```bash
git add docs/modding/guides/standalone-platformer.md docs/modding/guides/ai-art.md docs/modding/index.md docs/modding/samples/index.md src/test/resources/mods/sample-platformer-src/README.md CHANGELOG.md
git commit -m "docs: standalone-platformer and ai-art build-along guides" # trailers: Changelog: updated
```

---

## Plan-level notes for the executor

- **Consume-only discipline:** touching any `@ModApi` type breaks `TestModApiSignatureSurface`. If a needed surface is missing (e.g. a sprite-side SFX path or a published solid-object interface), work around it inside the mod (skip the sound / proximity detection) and note the gap in the guide — do NOT extend the API in this plan.
- The double-jump landing-reset seam (Task 4) and the spring contact detection (Task 5) are the two genuine discovery points; both have designated fallbacks.
- The OGG encode is the one non-hermetic generator step: encode once, check in, document the exact command in the generator's Javadoc. If no encoder exists on the machine, surface it — don't silently substitute WAV for the track.
- If this plan executes before the flappy plan, it creates `docs/modding/guides/` and the index list; if after, merge into them.
