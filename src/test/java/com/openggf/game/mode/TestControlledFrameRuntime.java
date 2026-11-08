package com.openggf.game.mode;

import com.openggf.LevelFrameResult;
import com.openggf.InputBindingFactory;
import com.openggf.control.InputHandlerInternalAccess;
import static org.lwjgl.glfw.GLFW.*;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real native stepping: one row decision, shared filters/P2, exact HOLD and legacy neutral policy. */
@RequiresRom(SonicGame.SONIC_2)
class TestControlledFrameRuntime {
    SharedLevel bootstrap;
    HeadlessTestFixture fixture;

    @AfterEach void close() {
        if (bootstrap != null) bootstrap.dispose();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    void load() throws Exception { load(SonicGame.SONIC_2); }
    void load(SonicGame game) throws Exception {
        bootstrap = SharedLevel.load(game, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        fixture.stepIdleFrames(2);
    }
    void controller(GameplayFrameController controller) throws Exception {
        var world = fixture.runtime().getWorldSession();
        var base = world.getGameModule();
        var field = world.getClass().getDeclaredField("gameModule");
        field.setAccessible(true);
        field.set(world, new DelegatingGameModule(base, "test:frames") {
            @Override public GameplayFrameController gameplayFrameController() { return controller; }
        });
    }
    static LogicalInputSnapshot right(boolean jump, boolean pressed) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT, 0,
                jump ? InputActionMasks.ACTION_A : 0, pressed ? InputActionMasks.ACTION_A : 0, false, false),
                PlayerInputState.neutral());
    }
    static class Decision implements GameplayFrameController {
        final GameplayFrameDisposition disposition;
        int decisions, beforeTicks, afterTicks, advanced;
        ObjectServices escaped;
        Decision(GameplayFrameDisposition disposition) { this.disposition = disposition; }
        public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { beforeTicks++; return true; }
        public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
            decisions++; escaped = course.objectServices(); return disposition;
        }
        public void afterTick(CourseControl course, boolean advance) { afterTicks++; if (advance) advanced++; }
    }

    @Test void nativeAdvanceMatchesOrdinaryMovementAndAirborneInput() throws Exception {
        load();
        var captured = fixture.runtime().getRewindRegistry().capture();
        for (int row = 0; row < 50; row++) fixture.stepFrame(false, false, false, true, row >= 10 && row < 15);
        var ordinary = new CourseControl(fixture.runtime()).playerState();
        int ordinaryLevelClock = GameServices.level().getFrameCounter();
        var ordinaryRng = fixture.runtime().getRng().capture();
        fixture.runtime().getRewindRegistry().restore(captured);
        var decision = new Decision(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT);
        controller(decision);
        var input = new InputHandler();
        for (int row = 0; row < 50; row++)
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, ControlledFrameRuntime.step(fixture.runtime(), input,
                    right(row >= 10 && row < 15, row == 10)));
        assertEquals(ordinary, new CourseControl(fixture.runtime()).playerState());
        assertEquals(ordinaryLevelClock, GameServices.level().getFrameCounter());
        assertEquals(ordinaryRng, fixture.runtime().getRng().capture());
        assertEquals(50, decision.decisions);
        assertEquals(50, decision.afterTicks);
        assertEquals(50, decision.advanced);
        assertEquals(0, decision.beforeTicks, "only the disposition callback decides the row");
        assertThrows(IllegalStateException.class, () -> decision.escaped.camera());
    }

    @Test void twoReleasedLiveTapsReachFourConsumingNativeRowsInOrder() throws Exception {
        load();
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
        var input = new InputHandler(InputBindingFactory.supplier(config));
        controller(new Decision(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT));
        for (int tap = 0; tap < 2; tap++) {
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
        }
        int initialFrame = GameServices.sprites().getFrameCounter();
        for (int row = 0; row < 4; row++) {
            try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
                input.refreshLogicalSnapshot();
                assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                        ControlledFrameRuntime.step(fixture.runtime(), input, input.logical()));
                var nativePress = GameServices.sprites().getJoypadPressSnapshot();
                int action = row % 2 == 0 ? InputActionMasks.ACTION_A : 0;
                assertEquals(action, nativePress.player1ActionPressed(), "native press row " + row);
                assertEquals(action, input.logical().player1().actionHeldMask(), "native held row " + row);
                assertEquals(initialFrame + row + 1, GameServices.sprites().getFrameCounter());
                assertFalse(input.hasLogicalOverride(), "temporary native publication must restore live authority");
                input.update();
            }
        }
    }

    @Test void legacyBooleanControllersRetainNeutralInput() throws Exception {
        load();
        var starts = fixture.runtime().getRewindRegistry().capture();
        fixture.stepIdleFrames(30);
        var neutral = new CourseControl(fixture.runtime()).playerState();
        fixture.runtime().getRewindRegistry().restore(starts);
        var count = new int[2];
        controller(new GameplayFrameController() {
            public boolean beforeTick(CourseControl c, LogicalInputSnapshot s) { count[0]++; return true; }
            public void afterTick(CourseControl c, boolean advanced) { assertTrue(advanced); count[1]++; }
        });
        var input = new InputHandler();
        for (int row = 0; row < 30; row++) ControlledFrameRuntime.step(fixture.runtime(), input, right(true, row == 0));
        assertEquals(neutral, new CourseControl(fixture.runtime()).playerState());
        assertArrayEquals(new int[]{30, 30}, count);
    }

    @Test void holdLeavesEveryWorldOwnerAndProtectionClockUntouched() throws Exception {
        load();
        var player = fixture.sprite();
        player.giveSpeedShoes(); player.setInvincibleFrames(30); player.setInvulnerableFrames(20);
        player.setAir(true); player.setRolling(true); player.setXSpeed((short) 0x123); player.setYSpeed((short) -0x180);
        var before = new CourseControl(fixture.runtime()).playerState();
        int levelFrames = GameServices.level().getFrameCounter();
        int spriteFrames = GameServices.sprites().getFrameCounter();
        int shoes = GameServices.timers().getTimerForCode("SpeedShoes-sonic").getTicks();
        var rng = fixture.runtime().getRng().capture();
        var decision = new Decision(GameplayFrameDisposition.HOLD);
        controller(decision);
        var input = new InputHandler();
        for (int row = 0; row < 100; row++) assertEquals(LevelFrameResult.HELD,
                ControlledFrameRuntime.step(fixture.runtime(), input, right(true, row == 0)));
        assertEquals(before, new CourseControl(fixture.runtime()).playerState());
        assertEquals(levelFrames, GameServices.level().getFrameCounter());
        assertEquals(spriteFrames, GameServices.sprites().getFrameCounter());
        assertEquals(shoes, GameServices.timers().getTimerForCode("SpeedShoes-sonic").getTicks());
        assertEquals(30, player.getInvincibleFrames()); assertEquals(20, player.getInvulnerableFrames());
        assertEquals(rng, fixture.runtime().getRng().capture());
        assertEquals(100, decision.decisions); assertEquals(100, decision.afterTicks); assertEquals(0, decision.advanced);
    }

    @Test void nativeInputUsesTheExistingFilterOnceAndRetainsP2AndOriginalOverride() throws Exception {
        load();
        var filterCalls = new int[1];
        GameplayInputFilterAccess.install(fixture.runtime(), raw -> { filterCalls[0]++; return PlayerInputState.neutral(); });
        var decision = new Decision(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT);
        controller(decision);
        var input = new InputHandler();
        var oldOverride = LogicalInputSnapshot.neutral(); input.setLogicalOverride(oldOverride);
        var p2 = PlayerInputState.of(0, 0, InputActionMasks.ACTION_B, InputActionMasks.ACTION_B, true, true);
        var snapshot = LogicalInputSnapshot.ofPlayers(right(false, false).player1(), p2);
        ControlledFrameRuntime.step(fixture.runtime(), input, snapshot);
        assertSame(oldOverride, input.logical()); assertTrue(input.hasLogicalOverride());
        assertEquals(1, filterCalls[0]);
        assertFalse(fixture.sprite().isRightPressed());
        var edges = fixture.runtime().getSpriteManager().getJoypadPressSnapshot();
        assertEquals(InputActionMasks.ACTION_B, edges.player2ActionPressed()); assertTrue(edges.player2StartPressed());
        input.clearLogicalOverride();
        ControlledFrameRuntime.step(fixture.runtime(), input, snapshot);
        assertFalse(input.hasLogicalOverride());
    }

    @Test void sonic2NativeDestinationRunsItsLockedPresentationWithoutConsumingControlledInput() throws Exception {
        nativeDestination(SonicGame.SONIC_2);
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1NativeDestinationRunsItsLockedPresentationWithoutConsumingControlledInput() throws Exception {
        nativeDestination(SonicGame.SONIC_1);
    }

    private void nativeDestination(SonicGame game) throws Exception {
        load(game);
        var decision = new Decision(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT);
        controller(decision);
        var context = fixture.runtime();
        var level = context.getLevelManager();
        long previousLoad = level.getCompletedProductionLoadGeneration();
        level.requestZoneAndAct(0, 0);
        var input = new InputHandler();
        boolean titleObserved = false;
        int rows = 0;
        for (; rows < 500; rows++) {
            assertEquals(LevelFrameResult.HELD, ControlledFrameRuntime.step(context, input, right(true, true)));
            titleObserved |= level.isTitleCardRequested() || context.controlledPresentation().capture().active();
            assertEquals(0, decision.advanced, "locked native presentation is not a gameplay callback");
            assertFalse(context.getCamera().getFocusedSprite().isRightPressed());
            if (level.getCompletedProductionLoadGeneration() != previousLoad
                    && !context.controlledPresentation().hasPendingWork(context)
                    && !context.getFadeManager().isActive()) break;
        }
        assertTrue(titleObserved);
        assertTrue(rows < 500, "native presentation releases in bounded time");
        assertEquals(0, context.getSpriteManager().getFrameCounter(), "native release resets the gameplay mirrors");
        assertEquals(0, level.getFrameCounter());
        int vint = level.getObjectManager().getVblaCounter();
        int[] filtered = {0};
        GameplayInputFilterAccess.install(context, raw -> { filtered[0]++; return raw; });
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, ControlledFrameRuntime.step(context, input, right(false, false)));
        assertEquals(1, filtered[0]);
        assertEquals(1, decision.advanced);
        assertEquals(1, context.getSpriteManager().getFrameCounter());
        assertEquals(1, level.getFrameCounter());
        assertEquals(vint + 1, level.getObjectManager().getVblaCounter());
        assertTrue(context.getCamera().getFocusedSprite().isRightPressed());
    }

    @Test void callbackFailureRevokesTheServicesItPublished() throws Exception {
        load();
        var escaped = new ObjectServices[1];
        controller(new GameplayFrameController() {
            public boolean beforeTick(CourseControl c, LogicalInputSnapshot s) { return true; }
            public GameplayFrameDisposition beforeFrame(CourseControl c, LogicalInputSnapshot s) {
                escaped[0] = c.objectServices(); throw new IllegalArgumentException("failed row");
            }
            public void afterTick(CourseControl c, boolean advanced) { fail("failed callback has no completion"); }
        });
        assertThrows(IllegalArgumentException.class, () -> ControlledFrameRuntime.step(fixture.runtime(), new InputHandler(), LogicalInputSnapshot.neutral()));
        assertThrows(IllegalStateException.class, () -> escaped[0].camera());
    }

    @Test void levelReplacementRevokesAServicesHandleWithinItsCallbackRow() throws Exception {
        load();
        controller(new GameplayFrameController() {
            public boolean beforeTick(CourseControl c, LogicalInputSnapshot s) { return false; }
            public GameplayFrameDisposition beforeFrame(CourseControl c, LogicalInputSnapshot s) {
                var previousVisit = c.objectServices();
                c.loadLevel(0, 1);
                assertThrows(IllegalStateException.class, previousVisit::camera);
                assertNotNull(c.objectServices().camera());
                return GameplayFrameDisposition.HOLD;
            }
            public void afterTick(CourseControl c, boolean advanced) { assertFalse(advanced); }
        });
        assertEquals(LevelFrameResult.HELD, ControlledFrameRuntime.step(fixture.runtime(), new InputHandler(), LogicalInputSnapshot.neutral()));
    }
}
