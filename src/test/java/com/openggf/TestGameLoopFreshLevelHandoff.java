package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.PlayableEntity;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.RecordingFrameDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RequiresRom(SonicGame.SONIC_3K)
class TestGameLoopFreshLevelHandoff {
    private GameplayModeContext context;
    private HeadlessTestFixture fixture;
    private GameLoop loop;

    @BeforeEach
    void setUp() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        context = SessionManager.getCurrentGameplayMode();
        loop = new GameLoop(new InputHandler());
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void realFadeCallbackKeepsItsOwnerAndDefersTheFourTitleArchives() throws Exception {
        var timing = context.hardwareTiming();
        int jobsBefore = timing.capture().jobs().size();
        long parentsBefore = moduleParents();
        AtomicBoolean loaded = new AtomicBoolean();
        var fade = context.getFadeManager();
        GameLoopPlcLifecycle.startToBlack(context, fade, () -> {
            invoke("doZoneAct", new Class<?>[]{int.class, int.class, int.class}, 1, 0, -1);
            loaded.set(true);
            assertEquals(jobsBefore, timing.capture().jobs().size(),
                    "the load callback submits neither title nor terrain work");
        });
        for (int n = 0; n < 64 && !loaded.get(); n++) {
            context.plcFrameLifecycle().runLogicalIteration(fade::update, frame -> {
                if (loaded.get()) {
                    assertTrue(frame.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE),
                            "closing the fade owner cannot change this iteration's latched phase");
                    set(loop, "activePlcLifecycleFrame", frame);
                    assertEquals(false, invoke("prepareAdmittedIteration", new Class<?>[]{boolean.class, boolean.class}, false, false));
                    set(loop, "activePlcLifecycleFrame", null);
                    assertEquals(jobsBefore, timing.capture().jobs().size());
                }
                return null;
            });
        }
        assertTrue(loaded.get());
        assertTrue(context.getLevelManager().hasPendingFreshLevelTransitionBoundary());
        assertEquals(FadeManager.FadeState.NONE, fade.getState(),
                "LoadPalette_Immediate must clear HOLD_BLACK before deferred title init");
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        loop.step();
        assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode());
        assertEquals(parentsBefore + 4, moduleParents(), "Obj_TitleCardInit owns four ROM archives");
    }

    @Test
    void genericProviderRetainsItsFadeFromBlack() throws Exception {
        set(loop, "titleCardProvider", mock(TitleCardProvider.class));
        invoke("doZoneAct", new Class<?>[]{int.class, int.class, int.class}, 1, 0, -1);
        assertEquals(FadeManager.FadeState.FADING_FROM_BLACK, context.getFadeManager().getState());
    }

    @Test
    void recordingDriverRestoresPlayersThenRunsInitialPassWithoutAnExtraOrdinaryRow() throws Exception {
        var level = context.getLevelManager();
        level.loadZoneAndActAtFreshTitleCardBoundary(1, 0);
        Observer observer = level.getObjectManager().createDynamicObjectAtSlot(Observer::new, 90);
        assertNotNull(observer);
        RecordingFrameDriver driver = new RecordingFrameDriver(fixture.sprite());
        driver.stepFrame(false, false, false, false, false);
        assertTrue((Boolean) get(driver, "normalTitleCardActive"));
        for (int n = 0; n < 2_000 && (Boolean) get(driver, "normalTitleCardActive"); n++) {
            driver.stepFrame(false, false, false, false, false);
        }
        assertFalse((Boolean) get(driver, "normalTitleCardActive"));
        assertTrue(level.hasPendingFreshLevelTransitionBoundary());
        assertTrue(observer.playerPositions.isEmpty(), "held title players must not enter initial Process_Sprites");
        driver.stepFrame(false, false, false, false, false);
        assertTrue(level.hasPendingFreshLevelTransitionBoundary(), "the first ordinary boundary remains held");
        assertTrue(observer.playerPositions.isEmpty());
        driver.stepFrame(false, false, false, false, false);
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        assertEquals(2, observer.playerPositions.size(),
                "loc_6468's no-VInt initial pass precedes this same iteration's LevelLoop pass");
        assertTrue(observer.playerPositions.stream().allMatch(y -> y != 0),
                "both passes see the destination player, not the held zeroed slot");
        driver.stepFrame(false, false, false, false, false);
        assertEquals(3, observer.playerPositions.size(), "initial setup is consumed exactly once");
    }

    @Test
    void liveCompletionHonoursPauseAndRepeatsWithFreshPublicationState() throws Exception {
        var level = context.getLevelManager();
        for (int destination = 0; destination < 2; destination++) {
            invoke("doZoneAct", new Class<?>[]{int.class, int.class, int.class}, 1, destination, -1);
            Observer observer = level.getObjectManager().createDynamicObjectAtSlot(Observer::new, 90);
            assertNotNull(observer);
            loop.step();
            assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode());
            for (int n = 0; n < 2_000 && loop.getCurrentGameMode() == GameMode.TITLE_CARD; n++) {
                loop.step();
            }
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
            assertTrue(level.hasPendingFreshLevelTransitionBoundary());
            assertEquals(true, invoke("isRewindBlocked", new Class<?>[]{}));
            assertTrue(observer.playerPositions.isEmpty());
            context.getGameStateManager().setGamePaused(true);
            loop.step();
            assertTrue(level.hasPendingFreshLevelTransitionBoundary());
            assertTrue(observer.playerPositions.isEmpty());
            context.getGameStateManager().setGamePaused(false);
            loop.step();
            assertTrue(level.hasPendingFreshLevelTransitionBoundary(), "pause cannot consume the first ordinary hold");
            loop.step();
            assertFalse(level.hasPendingFreshLevelTransitionBoundary());
            assertEquals(false, invoke("isRewindBlocked", new Class<?>[]{}));
            assertEquals(2, observer.playerPositions.size(), "restoration precedes initial and ordinary dispatch");
            loop.step();
            assertEquals(3, observer.playerPositions.size());
        }
    }

    private long moduleParents() {
        return context.hardwareTiming().capture().jobs().stream()
                .filter(job -> job.kind() == HardwareWorkKind.KOS_MODULE_QUEUE).count();
    }

    private Object invoke(String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = GameLoop.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(loop, arguments);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object get(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void set(Object owner, String name, Object value) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class Observer extends AbstractObjectInstance {
        private final List<Integer> playerPositions = new ArrayList<>();
        Observer() {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "FreshBoundaryObserver");
            setRomWorldPositioned(false);
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            playerPositions.add((int) ((AbstractPlayableSprite) player).getCentreY());
        }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {}
    }
}
