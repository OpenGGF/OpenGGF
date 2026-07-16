package com.openggf.game.sonic2;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.events.Sonic2ZoneEvents;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectArtKeys;
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
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2RuntimePlcRendererRefresh {
    private Sonic2ObjectArtProvider provider;
    private LevelManager levelManager;
    private TestableZoneEvents events;
    private PatternSpriteRenderer preExistingRenderer;
    private int preExistingPatternBase;

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
        levelManager.refreshObjectArtPatterns();
        preExistingRenderer = provider.getRenderer(ObjectArtKeys.MONITOR);
        preExistingPatternBase = preExistingRenderer.getPatternBase();
        clearInvocations(levelManager);
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
        assertSame(preExistingRenderer, provider.getRenderer(ObjectArtKeys.MONITOR));
        assertEquals(preExistingPatternBase, preExistingRenderer.getPatternBase(),
                "appending runtime PLC art must not relocate already-cached renderers");
        assertTrue(renderer.getPatternBase() > preExistingPatternBase,
                "the runtime PLC renderer should append after the initial WFZ allocation");
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
    void rewindReplayThroughZoneEventReusesRendererWithoutDuplicateRefreshOrAllocation() {
        PlcProgressSnapshot beforeRequest = provider.capture();

        events.requestForTest(Sonic2Constants.PLC_TORNADO);
        PatternSpriteRenderer first = provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER);
        int firstBase = first.getPatternBase();
        int firstPatternCount = provider.getRegularPatternCount();
        int firstEpoch = provider.capture().loadEpoch();
        verify(levelManager, times(1)).refreshObjectArtPatterns();

        provider.restore(beforeRequest);
        assertEquals(beforeRequest.loadEpoch(), provider.capture().loadEpoch());

        events.requestForTest(Sonic2Constants.PLC_TORNADO);

        assertSame(first, provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER));
        assertEquals(firstBase, first.getPatternBase());
        assertEquals(firstPatternCount, provider.getRegularPatternCount(),
                "replay must retain immutable sheets instead of appending duplicates");
        assertEquals(firstEpoch, provider.capture().loadEpoch(),
                "replay should advance once from the restored PLC epoch");
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
        RuntimeFixture failingRuntime = installRuntimeWithProvider(failingProvider);

        assertDoesNotThrow(() -> new TestableZoneEvents().requestForTest(Sonic2Constants.PLC_TORNADO));
        assertNull(failingProvider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER));
        verify(failingRuntime.levelManager(), never()).refreshObjectArtPatterns();
    }

    @Test
    void nonSonic2ObjectArtProviderIsAcceptedWithoutRefresh() throws Exception {
        ObjectArtProvider otherProvider = mock(ObjectArtProvider.class);
        RuntimeFixture otherRuntime = installRuntimeWithProvider(otherProvider);

        assertDoesNotThrow(() -> new TestableZoneEvents().requestForTest(Sonic2Constants.PLC_TORNADO));
        verify(otherRuntime.levelManager(), never()).refreshObjectArtPatterns();
    }

    private static RuntimeFixture installRuntimeWithProvider(ObjectArtProvider artProvider)
            throws Exception {
        GameModule module = mock(GameModule.class, delegatesTo(GameServices.module()));
        when(module.getObjectArtProvider()).thenReturn(artProvider);
        GameplayModeContext gameplay = SessionManager.openGameplaySession(module);
        TestEnvironment.activeGameplayMode();
        LevelManager manager = spy(gameplay.getLevelManager());
        gameplay.attachLevelManagers(
                gameplay.getWaterSystem(),
                gameplay.getParallaxManager(),
                gameplay.getTerrainCollisionManager(),
                gameplay.getCollisionSystem(),
                gameplay.getSpriteManager(),
                manager);
        setCurrentLevel(manager, gameplay, mock(Level.class));
        clearInvocations(manager);
        return new RuntimeFixture(manager);
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

    private record RuntimeFixture(LevelManager levelManager) {
    }
}
