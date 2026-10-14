package com.openggf.game.sonic3k;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.PatternDesc;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Second Strike task 2026-10-13: native fire-dash pixels, flips and independent art ownership. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeEnemiesNativeArt {
    private GraphicsManager graphics;
    private Sonic3kObjectArtProvider provider;

    @BeforeEach void loadNativeShieldArt() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        provider = new Sonic3kObjectArtProvider();
        var load = Sonic3kObjectArtProvider.class.getDeclaredMethod("loadShieldArt");
        load.setAccessible(true);
        load.invoke(provider);
        provider.ensurePatternsCached(graphics, PatternAtlasRange.OBJECTS.base());
    }

    @AfterEach void cleanup() {
        SessionManager.clear();
        GraphicsManager.destroyForReinit();
    }

    @Test void staticDashSheetMatchesAllNativeDplcPixelsAndBothOrientations() {
        var source = provider.getShieldArtSet(Sonic3kObjectArtKeys.FIRE_SHIELD);
        var dash = provider.getRenderer("fire_shield_dash");
        assertNotNull(dash);
        var animation = source.animationSet().getScript(1);
        assertEquals(1, animation.delay());
        assertEquals(List.of(9, 10, 11, 12, 13, 14, 9, 10, 11, 12, 13, 14), animation.frames());
        var nativeRenderer = new PlayerSpriteRenderer(source, graphics);
        assertNotEquals(nativeRenderer.patternBankBase(), dash.getPatternBase(),
                "static guards must not overwrite a player's mutable shield bank");
        for (int frame = 9; frame <= 14; frame++) {
            for (boolean hFlip : List.of(false, true)) {
                for (boolean vFlip : List.of(false, true)) {
                    int f = frame;
                    var nativePixels = publish(() -> nativeRenderer.drawFrame(f, 80, 70, hFlip, vFlip));
                    var staticPixels = publish(() -> dash.drawFrameIndex(f, 80, 70, hFlip, vFlip));
                    assertEquals(visiblePixels(nativePixels), visiblePixels(staticPixels),
                            "native dash DPLC frame=" + frame + " flips=" + hFlip + "," + vFlip);
                }
            }
        }
    }

    @Test void staggeredGuardFramesRemainStableWhenPlayerShieldBankChanges() {
        var source = provider.getShieldArtSet(Sonic3kObjectArtKeys.FIRE_SHIELD);
        var dash = provider.getRenderer("fire_shield_dash");
        assertNotNull(dash);
        var first = publish(() -> dash.drawFrameIndex(9, 60, 70, false, false));
        var second = publish(() -> dash.drawFrameIndex(14, 160, 70, true, false));
        var player = new PlayerSpriteRenderer(source, graphics);
        publish(() -> player.drawFrame(3, 240, 70, false, false));
        assertEquals(first, publish(() -> dash.drawFrameIndex(9, 60, 70, false, false)));
        assertEquals(second, publish(() -> dash.drawFrameIndex(14, 160, 70, true, false)));
        var both = publish(() -> {
            dash.drawFrameIndex(9, 60, 70, false, false);
            player.drawFrame(10, 240, 70, false, false);
            dash.drawFrameIndex(14, 160, 70, true, false);
        });
        first.patternVersions().forEach((id, pixels) -> assertEquals(pixels, both.patternVersions().get(id)));
        second.patternVersions().forEach((id, pixels) -> assertEquals(pixels, both.patternVersions().get(id)));
    }

    private SpritePresentation.Frame publish(Runnable producer) {
        return SpritePresentation.prepare(graphics, 0, 0, () -> {
            // Static ROM sheets normally resolve their pixels through the atlas;
            // freeze those same pixels explicitly for this native DPLC oracle.
            var dash = provider.getRenderer(FireShieldDashArt.KEY);
            com.openggf.level.render.SpritePresentationRenderer.bindPatternBank(graphics,
                    dash.getPatternBase(), provider.getSheet(FireShieldDashArt.KEY).getPatterns());
            producer.run();
        }, descriptor -> {
            var d = (PatternDesc) descriptor;
            return new SpritePresentation.Attributes(d.getPaletteIndex(), d.getHFlip(), d.getVFlip(), d.getPriority());
        });
    }

    private record VisiblePixel(float x, float y, int palette, boolean hFlip, boolean vFlip,
                                boolean priority, SpritePresentation.PatternVersion pixels) { }
    private List<VisiblePixel> visiblePixels(SpritePresentation.Frame frame) {
        assertFalse(frame.tiles().isEmpty());
        // These six native dash mappings partition the effect into disjoint tile
        // rectangles. Submission order differs between the two native renderers;
        // require nonoverlap before comparing the same tile pixels and placement.
        assertEquals(frame.tiles().size(), frame.tiles().stream()
                .map(tile -> List.of(tile.x(), tile.y())).distinct().count());
        return frame.tiles().stream().map(tile -> {
            var pixels = frame.patternVersions().get(tile.patternId());
            assertNotNull(pixels, "actual native ROM pixels must be published at pattern " + tile.patternId());
            return new VisiblePixel(tile.x(), tile.y(), tile.palette(), tile.hFlip(), tile.vFlip(), tile.priority(), pixels);
        }).sorted(java.util.Comparator.comparingDouble(VisiblePixel::y)
                .thenComparingDouble(VisiblePixel::x)).toList();
    }
}
