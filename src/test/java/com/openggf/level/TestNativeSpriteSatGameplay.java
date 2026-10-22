package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.graphics.*;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Actual loaded-game HUD/body admission and displayed readback, with no desktop/audio device. */
@Isolated
class TestNativeSpriteSatGameplay {
    private static final int BASE = 0x4F000; // task-owned transient-effects subrange
    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1LiveRendererCountsNativeHudBeforeOrdinaryPieces() throws Exception {
        check(RomTestUtils.ensureSonic1RomAvailable().toPath(), false);
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2LiveRendererCountsNativeHudBeforeOrdinaryPieces() throws Exception {
        check(RomTestUtils.ensureSonic2RomAvailable().toPath(), true);
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kPublishedRendererCountsNativeHudBeforeOrdinaryPieces() throws Exception {
        check(RomTestUtils.ensureSonic3kRomAvailable().toPath(), true);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kQueuedAttractedRingKeepsRingVisibilityAndAdmissionThroughRealGameplay() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            session.step(null); session.step(null);
            var level = GameServices.level();
            var player = GameServices.sprites().getMainPlayable();
            var rings = level.getRingManager();
            var camera = GameServices.camera();
            var source = new com.openggf.level.rings.RingSpawn(player.getCentreX() + 40, player.getCentreY());
            rings.resyncSpawnList(List.of(source));
            rings.reset(camera.getXWithShake());
            rings.update(camera.getXWithShake(), null, level.getFrameCounter());
            player.giveShield(com.openggf.game.ShieldType.LIGHTNING);
            rings.attractStageRings(player);
            assertEquals(1, rings.capture().attractedRings().length, "actual shield touch creates a reserved SST ring");
            session.step(null);
            var attracted = rings.capture().attractedRings();
            assertEquals(1, attracted.length);
            assertTrue(com.openggf.level.objects.NativeSpriteSubmissionAccess.admissions(level.getObjectManager())
                    .stream().anyMatch(call -> call.slot() == attracted[0].objectSlotIndex()),
                    "actual slot dispatch submits Draw_Sprite rather than inferred render fallback");
            var renderer = level.spritePresentationRenderer();
            var picture = renderer.captureSceneSpriteTable(GameServices.sprites());
            var art = picture.tiles().stream().filter(tile -> tile.layer() == SpritePresentation.Layer.RINGS).toList();
            assertFalse(art.isEmpty());
            assertTrue(art.stream().allMatch(SpritePresentation.Tile::priority));
            renderer.spriteTables.prepare(picture); renderer.spriteTables.publish();
            var blank = renderOptions(new LevelManager.LevelRenderOptions(false, false, false, false, false, false, false));
            var objectsOnly = renderOptions(new LevelManager.LevelRenderOptions(false, true, false, false, false, false, false));
            var ringsOnly = renderOptions(new LevelManager.LevelRenderOptions(false, false, true, false, false, false, false));
            int x = attracted[0].x() - camera.getXWithShake() - 8;
            int y = attracted[0].y() - camera.getYWithShake() - 8;
            assertEquals(0, differences(objectsOnly, blank, x, y, 16, 16));
            assertTrue(differences(ringsOnly, blank, x, y, 16, 16) > 0,
                    "real queued attracted art survives objects-off and disappears with rings-off");
            assertEquals(picture, renderer.captureSceneSpriteTable(GameServices.sprites()));
        }
    }

    private void check(Path rom, boolean placedListBeforeBuckets) throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 0, 0, settings);
            session.step(null); session.step(null);
            // Control the stage-list producer independently of whatever the boot camera sees.
            // Its native-before-buckets competition is exercised explicitly below.
            GameServices.level().getRingManager().resyncSpawnList(List.of());
            GameServices.level().getRingManager().reset(GameServices.camera().getXWithShake());
            var graphics = GameServices.graphics();
            var pattern = new Pattern();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) 15);
            for (int id = BASE; id < (BASE + 21); id++) graphics.cachePatternTexture(pattern, id);
            graphics.cachePatternTexture(pattern, (BASE + 100));
            graphics.cachePatternTexture(pattern, (BASE + 101));
            var probe = new Probe(graphics);
            GameServices.level().getObjectManager().addDynamicObject(probe);
            var renderer = GameServices.level().spritePresentationRenderer();
            var frame = renderer.captureSceneSpriteTable(GameServices.sprites());
            var admitted = frame.tiles().stream().filter(t -> t.patternId() >= BASE && t.patternId() < (BASE + 21)).toList();
            assertEquals(17, admitted.size(), "three native score footprints precede the probe on line8");
            assertTrue(admitted.stream().anyMatch(t -> t.patternId() == (BASE + 16)));
            assertFalse(admitted.stream().anyMatch(t -> t.patternId() == (BASE + 17)));
            assertTrue(frame.tiles().stream().anyMatch(t -> t.layer().isHud()), "HUD art remains visible");
            assertTrue(admitted.stream().allMatch(SpritePresentation.Tile::priority), "native tile priority survives CPU preparation");
            var masked = frame.tiles().stream().filter(t -> t.patternId() == (BASE + 100) || t.patternId() == (BASE + 101)).toList();
            assertEquals(2, masked.size(), "native X=0 removes only its scanline band in every game");
            assertTrue(masked.stream().noneMatch(t -> t.y() + t.rowStart() < 108 && t.y() + t.rowEnd() > 100));
            // A retained SAT is a rewind value; later preparation must not mutate it.
            var retained = List.copyOf(frame.tiles());
            assertEquals(frame, renderer.captureSceneSpriteTable(GameServices.sprites()));
            assertEquals(retained, frame.tiles());
            renderer.spriteTables.prepare(frame); renderer.spriteTables.publish();
            var table = renderer.spriteTables.capture();
            renderer.spriteTables.prepare(SpritePresentation.Frame.empty()); renderer.spriteTables.publish();
            renderer.spriteTables.restore(table);
            assertEquals(table, renderer.spriteTables.capture());
            var withSprites = session.render();
            var tilesOnly = session.render(false);
            int differences = 0;
            for (int y = 8; y < 16; y++) for (int x = 144; x < 152; x++) {
                if (withSprites.argb(x, y) != tilesOnly.argb(x, y)) differences++;
            }
            assertTrue(differences > 0, "admitted probe reaches the actual framebuffer");
            for (int y = 100; y < 108; y++) for (int x = 144; x < 152; x++) {
                assertEquals(tilesOnly.argb(x, y), withSprites.argb(x, y), "native mask reaches displayed pixels");
            }
            for (int y = 8; y < 16; y++) for (int x = 280; x < 288; x++) {
                assertEquals(tilesOnly.argb(x, y), withSprites.argb(x, y), "overflow piece must not reach framebuffer");
            }
            var camera = GameServices.camera();
            var rings = GameServices.level().getRingManager();
            var ring = new com.openggf.level.rings.RingSpawn(camera.getXWithShake() + 120,
                    camera.getYWithShake() + 16);
            rings.resyncSpawnList(List.of(ring));
            rings.reset(camera.getXWithShake());
            rings.update(camera.getXWithShake(), null, GameServices.level().getFrameCounter());
            if (!placedListBeforeBuckets) {
                var ringObject = new com.openggf.game.sonic1.objects.Sonic1RingInstance(
                        new ObjectSpawn(ring.x(), ring.y(), 0x25, 0, 0, false, 0), List.of(ring));
                GameServices.level().getObjectManager().addDynamicObject(ringObject);
                ringObject.update(0, null);
            }
            var withRing = renderer.captureSceneSpriteTable(GameServices.sprites());
            assertEquals(placedListBeforeBuckets ? 16 : 17,
                    withRing.tiles().stream().filter(t -> t.patternId() >= BASE && t.patternId() < BASE + 21).count(),
                    "S2/S3K BuildRings precedes every bucket; S1 Ring priority2 follows the priority0 probe");
            assertTrue(withRing.tiles().stream().anyMatch(t -> t.layer() == SpritePresentation.Layer.RINGS
                            && t.x() < 120 && t.x() + t.width() > 112
                            && t.y() < 24 && t.y() + t.height() > 16),
                    "ring art is submitted through the production table");
            renderer.spriteTables.prepare(withRing); renderer.spriteTables.publish();
            var blank = renderOptions(new LevelManager.LevelRenderOptions(false, false, false, false, false, false, false));
            var objectsOnly = renderOptions(new LevelManager.LevelRenderOptions(false, true, false, false, false, false, false));
            var ringsOnly = renderOptions(new LevelManager.LevelRenderOptions(false, false, true, false, false, false, false));
            assertEquals(0, differences(objectsOnly, blank, 112, 16, 16, 8),
                    "includeRings=false hides object-slot rings independently of object sprites");
            assertTrue(differences(ringsOnly, blank, 112, 16, 16, 8) > 0,
                    () -> "includeObjectSprites=false retains rings emitted through object slots: "
                            + withRing.tiles().stream().filter(t -> t.layer() == SpritePresentation.Layer.RINGS).toList());
            assertTrue(differences(objectsOnly, blank, 144, 8, 8, 8) > 0);
            assertEquals(0, differences(ringsOnly, blank, 144, 8, 8, 8), "rings-only excludes the ordinary object probe");
            int overflowX = 144 + (placedListBeforeBuckets ? 16 : 17) * 8;
            assertEquals(0, differences(objectsOnly, blank, overflowX, 8, 8, 8),
                    "hiding rings filters presentation after their native budget admission");
            assertEquals(withRing, renderer.captureSceneSpriteTable(GameServices.sprites()),
                    "orthogonal filters neither mutate nor rebuild a different admitted table");
        }
    }

    private static RgbaImage renderOptions(LevelManager.LevelRenderOptions options) {
        // Native entry fade may make ROM ring palette pixels black. Scope only its
        // presentation for these orthogonal visibility controls; do not advance gameplay/fade clocks.
        PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(), () -> {
            GameServices.level().setClearColor();
            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
            GameServices.level().drawWithRenderOptions(GameServices.sprites(), options);
            GameServices.graphics().flush();
        });
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static int differences(RgbaImage a, RgbaImage b, int x, int y, int width, int height) {
        int count = 0;
        for (int row = y; row < y + height; row++) for (int column = x; column < x + width; column++)
            if (a.argb(column, row) != b.argb(column, row)) count++;
        return count;
    }

    private static final class Probe extends AbstractObjectInstance {
        private final GraphicsManager graphics;
        Probe(GraphicsManager graphics) {
            super(new ObjectSpawn(0, 0, 1, 0, 0, false, 0), "SAT budget probe"); this.graphics = graphics;
        }
        @Override public int getPriorityBucket() { return 0; }
        @Override public boolean isHighPriority() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            var camera = GameServices.camera();
            for (int i = 0; i < 21; i++) graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                    camera.getXWithShake() + 144 + i * 8, camera.getYWithShake() + 8,
                    1, 1, BASE + i, 0, 0, false, false, true, true,
                    SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, null));
            graphics.submitSpriteSatControlEntry(camera.getXWithShake() - 127, camera.getYWithShake() + 100, 1, 1, 0);
            graphics.submitSpriteSatControlEntry(camera.getXWithShake() - 128, camera.getYWithShake() + 100, 1, 1, 0);
            graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(camera.getXWithShake() + 144,
                    camera.getYWithShake() + 96, 1, 2, (BASE + 100), 0, 0, false, false, true, true,
                    SpriteMaskReplayRole.NORMAL, 0, 1, 0, 2, null));
        }
    }
}
