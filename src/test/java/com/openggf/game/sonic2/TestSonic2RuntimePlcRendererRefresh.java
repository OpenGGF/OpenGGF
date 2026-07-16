package com.openggf.game.sonic2;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.events.Sonic2ZoneEvents;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.delegatesTo;

@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2RuntimePlcRendererRefresh {
    private Sonic2ObjectArtProvider provider;
    private LevelManager levelManager;
    private TestableZoneEvents events;

    @BeforeEach
    void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        GameplayModeContext gameplay = TestEnvironment.activeGameplayMode();
        provider = (Sonic2ObjectArtProvider) GameServices.module().getObjectArtProvider();
        provider.loadArtForZone(Sonic2ZoneConstants.ROM_ZONE_WFZ);

        levelManager = spy(gameplay.getLevelManager());
        setObjectRenderManager(levelManager, new ObjectRenderManager(provider));
        gameplay.attachLevelManagers(
                gameplay.getWaterSystem(),
                gameplay.getParallaxManager(),
                gameplay.getTerrainCollisionManager(),
                gameplay.getCollisionSystem(),
                gameplay.getSpriteManager(),
                levelManager);
        setCurrentLevel(levelManager, gameplay, mock(Level.class));
        events = new TestableZoneEvents();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void runtimePlcPublishesNewRendererInSameFrameAndRefreshesOnce() {
        assertNull(provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER));

        events.requestForTest(Sonic2Constants.PLC_TORNADO);

        PatternSpriteRenderer renderer =
                provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER);
        assertNotNull(renderer);
        assertTrue(renderer.isReady(), "the event-triggered renderer must be cache-ready immediately");
        verify(levelManager, times(1)).refreshObjectArtPatterns();
    }

    @Test
    void repeatedRuntimePlcKeepsRendererIdentityAndPatternBaseWithoutAnotherRefresh() {
        events.requestForTest(Sonic2Constants.PLC_TORNADO);
        PatternSpriteRenderer first = provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER);
        int firstBase = first.getPatternBase();

        events.requestForTest(Sonic2Constants.PLC_TORNADO);

        PatternSpriteRenderer repeated = provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER);
        assertSame(first, repeated);
        assertTrue(repeated.isReady());
        assertTrue(firstBase >= 0);
        assertEquals(firstBase, repeated.getPatternBase());
        verify(levelManager, times(1)).refreshObjectArtPatterns();
    }

    @Test
    void requestWithoutGameplayRuntimeReturnsWithoutLoading() {
        SessionManager.clear();

        assertDoesNotThrow(() -> events.requestForTest(Sonic2Constants.PLC_TORNADO));
        assertNull(provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER));
    }

    @Test
    void requestWithoutLoadedLevelReturnsWithoutLoading() throws Exception {
        setCurrentLevel(levelManager, SessionManager.getCurrentGameplayMode(), null);

        assertDoesNotThrow(() -> events.requestForTest(Sonic2Constants.PLC_TORNADO));
        assertNull(provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER));
    }

    @Test
    void ioFailureFromRuntimePlcRemainsNonFatal() throws Exception {
        Sonic2ObjectArtProvider failingProvider = new Sonic2ObjectArtProvider() {
            @Override
            public boolean requestPlc(int plcId) throws IOException {
                throw new IOException("synthetic PLC read failure");
            }
        };
        GameModule module = mock(GameModule.class, delegatesTo(GameServices.module()));
        when(module.getObjectArtProvider()).thenReturn(failingProvider);
        GameplayModeContext gameplay = SessionManager.openGameplaySession(module);
        TestEnvironment.activeGameplayMode();
        setCurrentLevel(gameplay.getLevelManager(), gameplay, mock(Level.class));

        assertDoesNotThrow(() -> new TestableZoneEvents().requestForTest(Sonic2Constants.PLC_TORNADO));
    }

    private static void setObjectRenderManager(LevelManager manager, ObjectRenderManager renderManager)
            throws ReflectiveOperationException {
        Field field = LevelManager.class.getDeclaredField("objectRenderManager");
        field.setAccessible(true);
        field.set(manager, renderManager);
    }

    private static void setCurrentLevel(LevelManager manager, GameplayModeContext gameplay, Level level)
            throws ReflectiveOperationException {
        Field field = LevelManager.class.getDeclaredField("level");
        field.setAccessible(true);
        field.set(manager, level);
        gameplay.getWorldSession().setCurrentLevel(level);
    }

    private static final class TestableZoneEvents extends Sonic2ZoneEvents {
        @Override
        public void update(int act, int frameCounter) {
        }

        private void requestForTest(int plcId) {
            requestSonic2Plc(plcId);
        }
    }
}
