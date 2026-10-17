package com.openggf.game.sonic3k.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Draw_SSShadows/word_94FC, gated by the first regular loc_84C2 presentation. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSpecialStageShadowPublication {
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @ValueSource(strings = {"sonic", "sonic+tails", "tails", "knuckles"})
    void shadowsBeginAfterTheBootFadeAndReplayAcrossThatBoundary(String team) throws Exception {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,
                team.equals("sonic+tails") ? "sonic" : team);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                team.equals("sonic+tails") ? "tails" : "");
        GameServices.graphics().initHeadless();
        var manager = new Sonic3kSpecialStageManager();
        manager.initialize(0);
        var graphics = new ShadowGraphics();
        var renderer = new Sonic3kSpecialStageRenderer(graphics);
        renderer.setShadowPatternBase(0x9000);
        renderer.setArtLoaded(true);
        for (int width : new int[]{320, 800}) {
            renderer.setSpecialStageViewport(SpecialStageViewport.fromLogicalWidth(width));
            var fresh = manager.captureRewindSnapshot();
            for (int i = 0; i < 22; i++) manager.update(); // Pal_FadeToWhite
            manager.update(); // boot Process_Sprites, before Pal_FadeFromWhite
            assertEquals(List.of(), shadows(renderer, manager, graphics));
            var boot = manager.captureRewindSnapshot();
            for (int i = 0; i < 22; i++) {
                manager.update();
                assertEquals(List.of(), shadows(renderer, manager, graphics), "fade/leading wait " + i);
            }
            var lastWait = manager.captureRewindSnapshot();
            manager.update(); // first regular Process_Sprites + Draw_SSSprites + Draw_SSShadows
            var expected = expected(width, team.equals("sonic+tails"));
            assertEquals(expected, shadows(renderer, manager, graphics));
            var published = manager.captureRewindSnapshot();
            manager.restoreRewindSnapshot(lastWait);
            assertTrue(shadows(renderer, manager, graphics).isEmpty(), "restore retires future shadows");
            manager.update();
            assertEquals(expected, shadows(renderer, manager, graphics), "first forward replay");
            manager.restoreRewindSnapshot(boot);
            for (int i = 0; i < 22; i++) manager.update();
            assertTrue(shadows(renderer, manager, graphics).isEmpty());
            manager.update();
            assertEquals(expected, shadows(renderer, manager, graphics), "second forward replay");
            manager.restoreRewindSnapshot(published);
            assertEquals(expected, shadows(renderer, manager, graphics), "published snapshot");
            manager.restoreRewindSnapshot(fresh);
        }
    }
    private static List<Tile> expected(int width, boolean sidekick) {
        var result = new ArrayList<Tile>();
        for (int player = 0; player < (sidekick ? 2 : 1); player++) {
            for (int col = 0; col < 4; col++) for (int row = 0; row < 4; row++)
                result.add(new Tile(0x9000 + col * 4 + row,
                        (width - 320) / 2 + 0x110 - 0x80 + col * 8,
                        (player == 0 ? 0x116 : 0x127) - 0x80 + row * 8));
        }
        return result;
    }
    private static List<Tile> shadows(Sonic3kSpecialStageRenderer renderer,
                                      Sonic3kSpecialStageManager manager, ShadowGraphics graphics) {
        graphics.tiles.clear(); renderer.render(manager); return List.copyOf(graphics.tiles);
    }
    private record Tile(int pattern, int x, int y) { }
    private static final class ShadowGraphics extends GraphicsManager {
        final List<Tile> tiles = new ArrayList<>();
        @Override public void beginPatternBatch() { }
        @Override public void flushPatternBatch() { }
        @Override public void beginShadowBatch() { }
        @Override public void flushShadowBatch() { }
        @Override public void addShadowPattern(int pattern, PatternDesc descriptor, int x, int y) {
            tiles.add(new Tile(pattern, x, y));
        }
    }
}
