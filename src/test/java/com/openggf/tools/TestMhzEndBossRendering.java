package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

/** Positioned rendering witness; the weather-machine hit is staged, not a certified route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMhzEndBossRendering {
    @Test void admittedBossAndChaseRetainTheirArt() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, 0x3A00, 0x320);
        try (var session = new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(
                    com.openggf.configuration.SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 1, settings);
            var camera = GameServices.camera();
            camera.setMinY((short) 0x280);
            camera.setMaxY((short) 0x280);
            camera.setY((short) 0x280);
            camera.setX((short) 0x3960);
            com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(session.player());
            for (int n = 0; n < 90; n++) session.step(null);
            // Admit the placed boss after the fresh-level fade has completed.
            // A positioned boot inside its gate would load its palette during that fade.
            session.player().setCentreX((short) 0x3D30);
            camera.setX((short) 0x3C90);
            camera.setY((short) 0x280);
            camera.setFrozen(true);
            for (int n = 0; n < 8; n++) session.step(null);
            com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(session.player());
            var level = GameServices.level();
            var boss = level.getObjectManager().getActiveObjects().stream()
                    .filter(MhzEndBossInstance.class::isInstance).map(MhzEndBossInstance.class::cast)
                    .findFirst().orElseThrow();
            assertEquals(0x3D90, boss.getX(), "camera gate must admit the placed ship");
            var art = (Sonic3kObjectArtProvider) level.getObjectRenderManager().getArtProvider();
            assertNotNull(art.getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP));
            assertNotNull(art.getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE));
            assertEquals(54, art.getSheet(Sonic3kObjectArtKeys.MHZ_END_BOSS_PILLAR).getPatterns().length,
                    "ArtKosM_MHZEndBossPillar's $6C0-byte archive");
            var expectedPalette = new com.openggf.level.Palette();
            expectedPalette.fromSegaFormat(GameServices.rom().getRom().readBytes(
                    com.openggf.game.sonic3k.constants.Sonic3kConstants.PAL_MHZ_END_BOSS_ADDR, 32));
            capture(session, "admitted", 98);
            assertTrue(expectedPalette.dataEquals(level.getCurrentLevel().getPalette(1)), "admitted ROM boss palette");
            level.getObjectManager().getActiveObjects().stream()
                    .filter(com.openggf.game.sonic3k.objects.bosses.MhzEndBossWeatherMachineChild.class::isInstance)
                    .map(com.openggf.game.sonic3k.objects.bosses.MhzEndBossWeatherMachineChild.class::cast)
                    .findFirst().orElseThrow().onPlayerAttack(session.player(), null);
            for (int n = 0; n < 600; n++) {
                session.step(null);
                if (n == 200) {
                    GameServices.camera().setMaxX((short) 0x45A0);
                    GameServices.camera().setX((short) 0x3F00);
                    GameServices.camera().setY((short) 0x280);
                    session.player().setCentreX((short) 0x3FA0);
                    session.player().setCentreY((short) 0x320);
                }
                if (n == 359) verifyReplay(session);
                if (n == 220) level.getRingManager().spawnLostRings(session.player(), 8, n);
                if (n == 222 || n == 280 || n == 350 || n == 450 || n == 580) capture(session, "chase-" + n, 99 + n);
            }
            assertFalse(session.player().getDead());
            assertEquals(8, level.getObjectManager().getActiveObjects().stream()
                    .filter(com.openggf.game.sonic3k.objects.bosses.MhzEndBossArenaHelperInstance.class::isInstance).count(),
                    "loc_5576A/loc_557C8 helpers must survive hidden and offscreen passes");
            var feature = (com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider) GameServices.module().getZoneFeatureProvider();
            assertEquals(level.getForegroundTileDescriptorAtWorld(0x4080, 0x198),
                    feature.foregroundDescriptorAt(0x1880, 0x298));
            assertEquals(level.getBackgroundTileDescriptorAtWorld(0x280, 0x98),
                    feature.backgroundDescriptorAt(0x1880, 0x198));
            verifyReplay(session);
            // Asset witness only: completion and capsule handoff have separate boss tests.
            level.getObjectManager().createDynamicObject(() ->
                    new com.openggf.game.sonic3k.objects.bosses.MhzEndBossEggCapsuleInstance(
                            (GameServices.camera().getX() & 0xFFFF) + 0xA0, 0x320));
            for (int n = 0; n < 4; n++) session.step(null);
            capture(session, "capsule-sheet", 702);
        }
    }

    private static void verifyReplay(GameplayCaptureSession session) {
        var registry = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int n = 0; n < 8; n++) { session.step(null); session.renderFrame(); }
        var expected = registry.capture();
        var expectedPixels = session.render().pixels();
        registry.restore(saved);
        session.restoreInputHistory(null);
        for (int n = 0; n < 8; n++) { session.step(null); session.renderFrame(); }
        var actual = registry.capture();
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        var allDifferences = new java.util.ArrayList<String>();
        for (String key : expected.entries().keySet()) {
            allDifferences.addAll(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key)));
        }
        assertTrue(allDifferences.isEmpty(), () -> allDifferences.toString());
        assertArrayEquals(expectedPixels, session.render().pixels(), "chase pixels after restore and forward replay");
        registry.restore(saved);
        session.restoreInputHistory(null);
    }

    private static void capture(GameplayCaptureSession session, String label, int steps) throws Exception {
        var frame = session.render();
        assertTrue(java.util.Arrays.stream(frame.pixels()).distinct().count() > 32, label);
        var tiles = session.render(false);
        assertTrue(java.util.stream.IntStream.range(0, frame.pixels().length)
                .filter(i -> frame.pixels()[i] != tiles.pixels()[i]).count() > 100, "visible sprites: " + label);
        String output = System.getProperty("openggf.tests.mhzRenderOut");
        if (output == null) return;
        var directory = Path.of(output);
        Files.createDirectories(directory);
        var image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, frame.width(), frame.height(), frame.pixels(), 0, frame.width());
        ImageIO.write(image, "png", directory.resolve(label + ".png").toFile());
        if (label.equals("admitted")) Files.writeString(directory.resolve("state.csv"),
                "label," + GameplayCaptureSession.stateHeader() + "\n");
        Files.writeString(directory.resolve("state.csv"), label + "," + session.stateLine(steps, null) + "\n",
                java.nio.file.StandardOpenOption.APPEND);
        System.out.println("MHZ_RENDER " + label + " player=" + session.player().getCentreX() + "," + session.player().getCentreY() + " camera=" + GameServices.camera().getX() + "," + GameServices.camera().getY());
    }
}
