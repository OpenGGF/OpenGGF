package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.internal.HudSatLayout;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kHudStaticArtFactory;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpriteMaskReplayRole;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises native admission plus expanded HUD replay together, without ROM or GL prerequisites. */
class TestHudSatProjection {
    private static final int CAMERA_X = 120;
    private static final int CAMERA_Y = 240;
    private static final int TEXT_BASE = 1000;
    private static final int DIGIT_BASE = 200;
    private static final int LIFE_BASE = 300;
    private GraphicsManager graphics;
    private GameStateManager gameState;
    private LevelState levelState;
    private HudRenderManager hud;

    @BeforeEach
    void setup() {
        GraphicsManager.destroyForReinit();
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        Camera camera = mock(Camera.class);
        when(camera.getXWithShake()).thenReturn((short) CAMERA_X);
        when(camera.getYWithShake()).thenReturn((short) CAMERA_Y);
        gameState = mock(GameStateManager.class);
        when(gameState.getLives()).thenReturn(3);
        levelState = mock(LevelState.class);
        when(levelState.getRings()).thenReturn(7);
        when(levelState.getDisplayTime()).thenReturn("0:10");
        hud = new HudRenderManager(graphics, camera, gameState);
        hud.setDigitPatternIndex(DIGIT_BASE);
        hud.setLivesNumbersPatternIndex(LIFE_BASE);
        hud.setStaticHudArt(TEXT_BASE, Sonic3kHudStaticArtFactory.create(patterns(24), patterns(12)));
        hud.setSatLayout(nativeLayout());
    }

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        GraphicsManager.destroyForReinit();
    }

    @Test
    void nativeLifeFootprintKeepsOneDigitAt56AndBothDigitsAt48And56() {
        var single = prepare(320, () -> { });
        assertEquals(List.of(56f), lifeDigits(single).stream().map(SpritePresentation.Tile::x).toList());
        assertEquals(List.of(LIFE_BASE + 3), lifeDigits(single).stream().map(SpritePresentation.Tile::patternId).toList());
        when(gameState.getLives()).thenReturn(12);
        var two = prepare(320, () -> { });
        assertEquals(List.of(48f, 56f), lifeDigits(two).stream()
                .sorted(java.util.Comparator.comparingDouble(SpritePresentation.Tile::x))
                .map(SpritePresentation.Tile::x).toList());
        assertTrue(two.tiles().stream().anyMatch(tile -> tile.patternId() == LIFE_BASE + 1 && tile.x() == 48 && tile.y() == 208));
        assertTrue(two.tiles().stream().anyMatch(tile -> tile.patternId() == LIFE_BASE + 2 && tile.x() == 56 && tile.y() == 208));
        assertFalse(lifeDigits(two).stream().anyMatch(tile -> tile.x() >= 64));
    }

    @Test
    void bonusHudRetainsItsLabelsDigitsAndLivesAtNativeAndWideLeftOrigin() {
        hud.setBonusStageHudLayout(true);
        for (int width : new int[]{320, 400}) {
            var frame = prepare(width, () -> { });
            assertTrue(frame.tiles().stream().anyMatch(tile -> tile.patternId() == TEXT_BASE + 8 && tile.x() == 16 && tile.y() == 8),
                    "RINGS label survives grouped admission at width " + width);
            assertTrue(frame.tiles().stream().anyMatch(tile -> tile.patternId() == DIGIT_BASE + 14 && tile.x() == 80 && tile.y() == 8),
                    "ring digit survives grouped admission at width " + width);
            assertTrue(frame.tiles().stream().anyMatch(tile -> tile.patternId() == TEXT_BASE + 24 && tile.x() == 16 && tile.y() == 200),
                    "life icon survives grouped admission at width " + width);
            assertEquals(List.of(56f), lifeDigits(frame).stream().map(SpritePresentation.Tile::x).toList());
        }
    }

    @Test
    void optionalTimeTailUsesTheSameNativeAndWideAnchorAsItsReadout() {
        HudProfileAccess.setHostTime(hud, 119, true);
        for (int width : new int[]{320, 400}) {
            for (boolean left : new boolean[]{false, true}) {
                hud.setScreenLeftAnchorEnabled(left);
                var frame = prepare(width, () -> { });
                int origin = left ? 0 : (width - 320) / 2;
                assertTrue(frame.tiles().stream().anyMatch(tile -> tile.patternId() == DIGIT_BASE + 21
                        && tile.x() == origin + 88 && tile.y() == 33), "optional decimal dot");
                assertTrue(frame.tiles().stream().anyMatch(tile -> tile.patternId() == DIGIT_BASE + 18
                        && tile.x() == origin + 96 && tile.y() == 24), "first hundredth");
                assertTrue(frame.tiles().stream().anyMatch(tile -> tile.patternId() == DIGIT_BASE + 16
                        && tile.x() == origin + 104 && tile.y() == 24), "second hundredth");
            }
        }
    }

    @Test
    void oneNativeNamePieceAdmitsItsManyFragmentsAsOneSprite() {
        // Native second lives piece spans x32..64, y200..216. Its label and
        // two number fragments must consume one SAT slot, not one per art tile.
        var nameOnly = new SpriteMappingFrame(List.of(piece(16, 64, 4, 0x10E)));
        hud.setSatLayout(new HudSatLayout(Collections.nCopies(4, nameOnly)));
        when(gameState.getLives()).thenReturn(12);
        var frame = prepare(320, () -> {
            SpritePresentation.layer(graphics, SpritePresentation.Layer.OBJECT);
            for (int i = 0; i < 20; i++) {
                graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                        CAMERA_X + 144, CAMERA_Y + 208, 1, 1, 500 + i, 0,
                        0, false, false, false, false, SpriteMaskReplayRole.NORMAL,
                        0, 1, 0, 1, null));
            }
        });
        assertEquals(19, frame.tiles().stream().filter(tile -> tile.patternId() >= 500 && tile.patternId() < 520).count());
        assertTrue(frame.tiles().stream().anyMatch(tile -> tile.patternId() == 518));
        assertFalse(frame.tiles().stream().anyMatch(tile -> tile.patternId() == 519));
        assertEquals(2, lifeDigits(frame).size(), "both numbers replay within that one admitted piece");
    }

    private SpritePresentation.Frame prepare(int width, Runnable afterHud) {
        hud.setViewportWidth(width);
        return SpritePresentationRenderer.prepare(graphics, CAMERA_X, CAMERA_Y, () -> {
            SpriteSatInternalAccess.beginCollection(graphics,
                    SpriteSatMaskPostProcessor.Raster.extended(CAMERA_X, CAMERA_Y, width, 224));
            hud.draw(levelState, null);
            afterHud.run();
            graphics.endSpriteSatCollectionAndReplay();
        });
    }

    private static List<SpritePresentation.Tile> lifeDigits(SpritePresentation.Frame frame) {
        return frame.tiles().stream().filter(tile -> tile.patternId() >= LIFE_BASE && tile.patternId() < LIFE_BASE + 10).toList();
    }

    private static HudSatLayout nativeLayout() {
        var stock = new SpriteMappingFrame(List.of(
                piece(0, -128, 4, 0), piece(32, -128, 4, 0x18), piece(64, -128, 4, 0x20),
                piece(0, -112, 4, 0x10), piece(40, -112, 4, 0x28),
                piece(0, -96, 4, 8), piece(32, -96, 1, 0), piece(48, -96, 3, 0x30),
                piece(0, 64, 2, 0x10A), piece(16, 64, 4, 0x10E)));
        var frames = new ArrayList<>(Collections.nCopies(4, stock));
        frames.add(new SpriteMappingFrame(List.of(piece(0, -128, 4, 8), piece(32, -128, 1, 0),
                piece(48, -128, 3, 0x30), piece(0, 64, 2, 0x10A), piece(16, 64, 4, 0x10E))));
        frames.add(new SpriteMappingFrame(List.of(piece(48, -128, 3, 0x30),
                piece(0, 64, 2, 0x10A), piece(16, 64, 4, 0x10E))));
        return new HudSatLayout(frames);
    }

    private static SpriteMappingPiece piece(int x, int y, int width, int tile) {
        return new SpriteMappingPiece(x, y, width, 2, tile, false, false, 1);
    }

    private static Pattern[] patterns(int count) {
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) patterns[i] = new Pattern();
        return patterns;
    }
}
