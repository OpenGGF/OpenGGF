package com.openggf.sprites.managers;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.level.LevelManager;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestCreditsDemoInputPresses {
    @Test void nativeCreditsPressModesReachTheActualPublisherAndRewindUsesTheCapturedHeldWord() {
        for (boolean repeats : new boolean[] {false, true}) {
            TestEnvironment.configureGameModuleFixture(new Sonic1GameModule());
            var manager = GameServices.sprites();
            var actor = new Actor();
            manager.addSprite(actor);
            actor.setForcedInputMask(AbstractPlayableSprite.INPUT_JUMP);
            var input = new InputHandler();
            frame(manager, actor, repeats, input);
            var afterFirst = actor.captureRewindState();
            frame(manager, actor, repeats, input);
            assertEquals(List.of(true, repeats), actor.logicalPresses);
            assertEquals(List.of(true, repeats), actor.rawPresses);
            actor.restoreRewindState(afterFirst);
            frame(manager, actor, repeats, input);
            assertEquals(repeats, actor.logicalPresses.getLast());
            assertEquals(repeats, actor.rawPresses.getLast());

            SpriteManagerInternalAccess.runCreditsDemoGameplay(manager, new Sonic1GameModule(), actor, () -> {
                manager.publishHeldInputForLevelEvents(input);
                manager.update(input);
            });
            assertTrue(actor.logicalPresses.getLast(), "Ordinary forced input retains its existing repeated press");
            assertTrue(actor.rawPresses.getLast());
        }
    }

    @Test void failedCreditsFrameReleasesItsPressMode() {
        TestEnvironment.configureGameModuleFixture(new Sonic1GameModule());
        var manager = GameServices.sprites();
        var actor = new Actor();
        manager.addSprite(actor);
        actor.setForcedInputMask(AbstractPlayableSprite.INPUT_JUMP);
        actor.setLogicalInputState(false, false, false, false, true, false);
        assertThrows(IllegalStateException.class, () -> manager.withCreditsDemoInputPresses(actor, false,
                () -> { throw new IllegalStateException("test frame"); }));
        manager.update(new InputHandler());
        assertEquals(List.of(true), actor.logicalPresses);
    }

    private static void frame(SpriteManager manager, Actor actor, boolean repeats, InputHandler input) {
        var module = new Sonic1GameModule() {
            @Override public <T> T getGameService(Class<T> type) {
                return type == com.openggf.game.internal.CreditsDemoInputProfile.class
                        ? type.cast((com.openggf.game.internal.CreditsDemoInputProfile) () -> repeats)
                        : super.getGameService(type);
            }
        };
        SpriteManagerInternalAccess.runCreditsDemoGameplay(manager, module, actor, () -> {
            manager.publishHeldInputForLevelEvents(input);
            manager.update(input);
        });
    }

    private static final class Actor extends AbstractPlayableSprite implements CustomPlayablePhysics {
        final List<Boolean> logicalPresses = new ArrayList<>(), rawPresses = new ArrayList<>();
        Actor() { super("sonic", (short) 0, (short) 0); }
        @Override public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                boolean jump, boolean test, boolean speedUp, boolean slowDown, LevelManager level, int frame) {
            logicalPresses.add(isLogicalJumpPressActive());
            rawPresses.add(isJumpJustPressed());
        }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() {
            groundSensors = new Sensor[0]; ceilingSensors = new Sensor[0]; pushSensors = new Sensor[0];
        }
        @Override public void draw() { }
    }
}
