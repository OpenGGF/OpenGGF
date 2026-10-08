package com.openggf.game.sonic2.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.GameModuleRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native Obj6F exhausted-tally dispatch and stock Super Sonic motion/hold order. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SpecialStageSuperResults {
    private TestObjectServices services;
    private SonicConfigurationService configuration;

    @BeforeEach
    void setUp() {
        SessionManager.clear();
        SessionManager.openGameplaySession(GameModuleRegistry.getCurrent());
        GameServices.module().createGame(TestEnvironment.currentRom());
        configuration = mock(SonicConfigurationService.class);
        when(configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn("sonic");
        services = new TestObjectServices() {
            @Override
            public <T> T gameService(Class<T> type) {
                return GameServices.module().getGameService(type);
            }
        }.withGameModule(GameServices.module()).withGameState(new GameStateManager())
                .withConfiguration(configuration).withRom(TestEnvironment.currentRom())
                .withRomManager(EngineServices.current().roms());
    }

    @AfterEach
    void clearSession() {
        SessionManager.clear();
    }

    private SpecialStageResultsScreenObjectInstance exhaustedTally(boolean gotEmerald, int count) {
        var screen = new SpecialStageResultsScreenObjectInstance(0, 0, gotEmerald, 6, count, services);
        for (int frame = 1; frame < 1000; frame++) {
            screen.update(frame, null);
            if (screen.getState() != 0 && screen.getState() != 1 && screen.getState() != 2) {
                return screen;
            }
        }
        fail("Obj6F never left its tally");
        return screen;
    }

    @Test
    void seventhEmeraldEntersSuperMotionWithoutTheOrdinaryWait() {
        var screen = exhaustedTally(true, 7);
        assertEquals(10, screen.getState(), "Obj6F_TallyScore $14580 selects routine $30 directly");
    }

    @Test
    void nativeMotionArrivalHoldAndDisplayOnlyTake210FollowingPasses() {
        var screen = exhaustedTally(true, 7);
        // $146A6: 9 leave passes + init; $14736: 18 return passes + timer latch;
        // $14572: 180 predecrements, then Obj6F_DisplayOnly on its following pass.
        for (int pass = 1; pass < 210; pass++) {
            screen.update(pass, null);
            assertFalse(screen.isComplete(), "premature completion on results pass " + pass);
        }
        screen.update(210, null);
        assertTrue(screen.isComplete(), "native motion and hold finish on pass 210");
    }

    @Test
    void stockMessagesMoveToTheirSourcesThenReplaceAndReturnInNativeSlotOrder() throws Exception {
        var screen = exhaustedTally(true, 7);
        for (int pass = 1; pass <= 9; pass++) screen.update(pass, null);
        assertEquals(448, coordinate(screen, "stockSuperMainX"));
        assertEquals(-128, coordinate(screen, "stockSuperHeadingX"));
        screen.update(10, null);
        assertEquals(448, coordinate(screen, "stockSuperMainX"), "main switches routine without moving");
        assertEquals(-112, coordinate(screen, "stockSuperHeadingX"), "later heading runs its new routine");
        assertEquals(-112, coordinate(screen, "stockSuperLineX"), "new later slot runs on the allocation pass");
        for (int pass = 11; pass <= 28; pass++) screen.update(pass, null);
        assertEquals(160, coordinate(screen, "stockSuperMainX"));
        assertEquals(160, coordinate(screen, "stockSuperHeadingX"));
        assertEquals(160, coordinate(screen, "stockSuperLineX"));
        screen.update(29, null);
        assertEquals(180, coordinate(screen, "superMsgTimer"), "arrival latches the timer on its following pass");
    }

    private int coordinate(SpecialStageResultsScreenObjectInstance screen, String name) throws Exception {
        Field field = screen.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(screen);
    }

    @Test
    void nativeAndWideRenderersRespectMovementVisibilityButDisplayTheInitPass() {
        for (int width : new int[] {320, 528}) {
            RecordingGraphics graphics = new RecordingGraphics();
            services.withGraphicsManager(graphics);
            var screen = exhaustedTally(true, 7);
            screen.setViewportWidth(width);
            for (int pass = 1; pass <= 14; pass++) {
                screen.update(pass, null);
                graphics.rows.clear();
                screen.appendRenderCommands(new ArrayList<>());
                int mainY = pass < 10 ? 42 : 34;
                boolean hasMain = graphics.rows.stream().anyMatch(y -> y == mainY || y == mainY - 8);
                // Both native movement routines skip DisplaySprite when x_pixel>$200.
                // Init instead branches directly to DisplaySprite at hardware x=$240.
                boolean expected = pass <= 7 || pass == 10 || pass >= 14;
                assertEquals(expected, hasMain, "width=" + width + ", native results pass=" + pass);
            }
        }
    }

    private static final class RecordingGraphics extends GraphicsManager {
        private final List<Integer> rows = new ArrayList<>();
        @Override public void cachePatternTexture(Pattern pattern, int patternId) {}
        @Override public void cachePaletteTexture(Palette palette, int index) {}
        @Override public void renderPatternWithId(int id, PatternDesc desc, int x, int y) {
            rows.add(y);
        }
        @Override public void beginPatternBatch() {}
        @Override public void flushPatternBatch() {}
    }

    @Test
    void sixEmeraldsKeepTheOrdinaryWait() {
        assertEquals(3, exhaustedTally(true, 6).getState());
    }

    @Test
    void failedStageWithSevenPreviouslyCollectedEmeraldsKeepsTheOrdinaryWait() {
        assertEquals(3, exhaustedTally(false, 7).getState());
    }

    @Test
    void aboveSevenDoesNotSatisfyTheNativeEqualityGate() {
        var screen = exhaustedTally(true, 8);
        assertEquals(3, screen.getState());
        for (int pass = 1; pass <= 121; pass++) screen.update(pass, null);
        assertTrue(screen.isComplete());
    }

    @Test
    void tailsAloneDoesNotEnterSuperSonicAfterTheOrdinaryWait() {
        when(configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn("tails");
        var screen = exhaustedTally(true, 7);
        assertEquals(3, screen.getState(), "native Player_mode == 2 bypasses routine $30");
        for (int pass = 1; pass <= 121; pass++) screen.update(pass, null);
        assertTrue(screen.isComplete(), "Tails retains the ordinary result exit");
    }
}
