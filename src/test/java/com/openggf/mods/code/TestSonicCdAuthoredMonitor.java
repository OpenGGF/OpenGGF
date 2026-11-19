package com.openggf.mods.code;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.ZoneKey;
import com.openggf.game.internal.HostedGameMedia;
import com.openggf.game.modzone.ModZoneRegistrationException;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.objects.common.CdMonitor;
import com.openggf.game.soniccd.objects.common.CdMonitorItem;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.LevelManager.LevelRenderOptions;
import com.openggf.level.ModLevel;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

class TestSonicCdAuthoredMonitor {
    private static final ZoneKey.Mod ZONE = new ZoneKey.Mod("cd-monitor", "test-act");
    private static final int MONITOR_X = 140;
    private static final int MONITOR_Y = 498;
    private static final int AUTHORED_COLOR = 0xFF00FF;

    @Test
    void coldAuthoredMonitorDrawsBreaksAndRetiresAcrossRewind() throws Exception {
        var module = authoredAct(0x19, 1);
        int zone = module.getZoneRegistry().resolveZoneKey(ZONE).orElseThrow();
        var config = GameServices.configuration();
        boolean audio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var disc = (CdDiscDataSource) HostedGameMedia.of(module).openMedia(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, module, zone, 0);
            var levels = GameServices.level();
            assertInstanceOf(ModLevel.class, levels.getCurrentLevel());
            var art = module.getGameService(CdObjectArtProvider.class);
            assertNull(art.image());
            assertNull(art.entry());
            assertNull(art.payload());
            assertNull(art.vram());
            assertNull(art.getSheet(CdObjectArtProvider.HUD));

            var sonic = GameServices.camera().getFocusedSprite();
            for (int frame = 0; frame < 10; frame++) loop.step();
            var monitor = live(CdMonitor.class).getFirst();
            assertPixels(draw(LevelRenderOptions.previewCapture()), draw(LevelRenderOptions.tilesOnly()),
                    MONITOR_X, MONITOR_Y, 16);
            assertFalse(sonic.getAir(), "Sonic starts on the authored floor");
            int rings = levels.getLevelGamestate().getRings();

            var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var intact = rewind.capture();
            int[] intactFrames = monitorFrames(loop);
            assertTrue(Arrays.stream(intactFrames).anyMatch(frame -> frame == 1), "the monitor shows the ring icon");
            assertTrue(Arrays.stream(intactFrames).anyMatch(frame -> frame == 0x10), "the monitor flashes static");
            rewind.restore(intact);
            assertArrayEquals(intactFrames, monitorFrames(loop), "rewind restores the intact monitor's script cursor");

            monitor = live(CdMonitor.class).getFirst();
            try {
                step(loop, 0, InputActionMasks.ACTION_A, InputActionMasks.ACTION_A);
                assertTrue(sonic.getRolling(), "jump input enters a rolling jump");
                for (int frame = 0; frame < 60 && sonic.getYSpeed() < 0; frame++) {
                    step(loop, 0, InputActionMasks.ACTION_A, 0);
                }
                for (int frame = 0; frame < 60 && monitor.mapFrame() != 0x11; frame++) {
                    step(loop, INPUT_RIGHT, 0, 0);
                }
            } finally {
                loop.getInputHandler().clearLogicalOverride();
            }
            assertEquals(0x11, monitor.mapFrame(), "the descending rolling jump breaks the monitor through touch");
            assertEquals(1, live(CdExplosion.class).size());
            assertEquals(1, live(CdMonitorItem.class).size());
            assertEquals(rings, levels.getLevelGamestate().getRings(), "the rising item pays out later");

            for (int frame = 0; frame < 12; frame++) loop.step();
            var item = live(CdMonitorItem.class).getFirst();
            assertTrue(item.getY() < MONITOR_Y - 20);
            var duringBreak = draw(LevelRenderOptions.previewCapture());
            var tiles = draw(LevelRenderOptions.tilesOnly());
            assertPixels(duringBreak, tiles, item.getX(), item.getY() - 2, 8);
            // The broken monitor is entirely below its origin, and the item is already higher than this crop.
            assertPixels(duringBreak, tiles, MONITOR_X, MONITOR_Y - 8, 8);

            var midBreak = rewind.capture();
            for (int pass = 0; pass < 2; pass++) {
                for (int frame = 0; frame < 75; frame++) loop.step();
                assertEquals(rings + 10, levels.getLevelGamestate().getRings());
                assertTrue(live(CdMonitorItem.class).isEmpty(), "the awarded item retires");
                assertTrue(live(CdExplosion.class).isEmpty(), "the explosion script reaches its delete routine");
                assertEquals(0x11, live(CdMonitor.class).getFirst().mapFrame());
                if (pass == 0) {
                    rewind.restore(midBreak);
                    assertEquals(rings, levels.getLevelGamestate().getRings());
                    assertEquals(1, live(CdMonitorItem.class).size());
                    assertEquals(1, live(CdExplosion.class).size());
                    assertArrayEquals(duringBreak.pixels(), draw(LevelRenderOptions.previewCapture()).pixels(),
                            "restoring the transients restores their current pictures before the next frame");
                }
            }
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, audio);
        }
    }

    @Test
    void timePostAndOtherStockPlacementsStillRequireNativeStageContext() {
        for (int subtype : new int[]{0, 2, 3, 4, 5, 6, 7, 8, 9, 0x81}) {
            assertThrows(ModZoneRegistrationException.class, () -> authoredAct(0x19, subtype),
                    "monitor/time post subtype " + subtype);
        }
        assertThrows(ModZoneRegistrationException.class, () -> authoredAct(0x18, 1));
        assertThrows(ModZoneRegistrationException.class, () -> authoredAct(0x1A, 1));
        assertThrows(ModZoneRegistrationException.class, () -> authoredAct(0x7F, 0));
    }

    private static void step(GameLoop loop, int directions, int action, int pressed) {
        loop.getInputHandler().setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(directions, 0, action, pressed, false, false), PlayerInputState.neutral()));
        loop.step();
    }

    private static <T extends ObjectInstance> List<T> live(Class<T> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(type::isInstance).filter(o -> !o.isDestroyed()).map(type::cast).toList();
    }

    private static int[] monitorFrames(GameLoop loop) {
        var monitor = live(CdMonitor.class).getFirst();
        int[] frames = new int[24];
        for (int i = 0; i < frames.length; i++) {
            loop.step();
            frames[i] = monitor.mapFrame();
        }
        return frames;
    }

    private static GameModule authoredAct(int id, int subtype) {
        var declared = ModZoneContribution.singleAct(ZONE.localName(), new BakedLevelRef("level.json"), null, null, false);
        var monitor = new ModLevelDefinition.StockObjectSpawn(1, MONITOR_X, MONITOR_Y, id, subtype, 0, true, MONITOR_Y);
        byte[][] palettes = {new byte[32], new byte[32], new byte[32], new byte[32]};
        for (int color = 2; color < 32; color += 2) {
            palettes[0][color] = 0x0E;
            palettes[0][color + 1] = 0x0E;
        }

        // The last row of 256px blocks starts at Y=512. Fill it with fully solid, transparent chunks.
        byte[] blocks = new byte[1024];
        for (int offset = 512; offset < blocks.length; offset += 2) blocks[offset] = (byte) 0xF0;
        byte[] profiles = new byte[32];
        Arrays.fill(profiles, 16, 32, (byte) 16);
        var data = new ModLevelDefinition(1, "Monitor", 0x40, 0x400, 16, 2, 3,
                new ModLevelDefinition.Bounds(0, 511, 0, 767), new ModLevelDefinition.Start(96, 492),
                new ModLevelDefinition.StockMusic(1), List.of(monitor), List.of(),
                new byte[32], new byte[8], blocks, new byte[]{0, 0, 0, 0, 1, 1}, null,
                profiles, profiles, new byte[2], new int[]{1}, new int[]{1}, palettes, 1, 1, 2, 2);
        var prepared = PreparedModZone.prepared(ZONE.ownerModId(), declared, data);
        var plan = new ModRegistrationPlan(ZONE.ownerModId(), "scd", Map.of(), Map.of(), Map.of(),
                List.of(), List.of(declared), List.of(prepared));
        return new ModBackedGamePatch(plan).apply(new SonicCdGameModule(),
                new PatchContext(ignored -> null, GameServices.configuration()));
    }

    private static RgbaImage draw(LevelRenderOptions options) {
        var levels = GameServices.level();
        var graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        levels.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        levels.drawWithRenderOptions(GameServices.sprites(), options);
        graphics.flush();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static void assertPixels(RgbaImage image, RgbaImage baseline, int worldX, int worldY, int radius) {
        int screenX = worldX - GameServices.camera().getX();
        int screenY = worldY - GameServices.camera().getY();
        int colored = 0;
        for (int y = screenY - radius; y < screenY + radius; y++) {
            for (int x = screenX - radius; x < screenX + radius; x++) {
                int color = image.argb(x, y);
                if (color != baseline.argb(x, y) && (color & 0xFFFFFF) == AUTHORED_COLOR) colored++;
            }
        }
        assertTrue(colored > 16, "native art uses visible authored-palette pixels near " + worldX + "," + worldY
                + " (found " + colored + ")");
    }
}
