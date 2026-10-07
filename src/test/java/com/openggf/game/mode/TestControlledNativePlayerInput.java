package com.openggf.game.mode;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real ROM terrain and movement through the production controlled-frame path. */
@RequiresRom(SonicGame.SONIC_2)
class TestControlledNativePlayerInput {
    private SharedLevel bootstrap;
    private final InputHandler input = new InputHandler();
    private boolean nativeInput;
    private boolean advance = true;

    @AfterEach void cleanup() {
        if (bootstrap != null) bootstrap.dispose();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    private HeadlessTestFixture launch() throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var controller = new GameplayFrameController() {
            @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot snapshot) { return advance; }
            @Override public void afterTick(CourseControl course, boolean advanced) { }
            @Override public boolean nativePlayerInput() { return nativeInput; }
        };
        var module = new DelegatingGameModule(GameServices.module(), "test-native-input") {
            @Override public GameplayFrameController gameplayFrameController() { return controller; }
        };
        SessionManager.clear();
        GameModuleRegistry.setCurrent(module);
        TestEnvironment.activeGameplayMode();
        var engineOwner = EngineServices.current();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        // The fixture resets legacy services while retaining this existing gameplay mode.
        // Keep its registered timing owner authoritative for native stepping and rewind.
        EngineServices.configure(engineOwner);
        assertTrue(com.openggf.game.session.EngineTiming.vIntRunCounter().isObjectClockBound());
        fixture.stepIdleFrames(120);
        return fixture;
    }

    private LogicalInputSnapshot right(boolean pressed) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT,
                pressed ? AbstractPlayableSprite.INPUT_RIGHT : 0, 0, 0, false, false), PlayerInputState.neutral());
    }

    @Test void defaultNeutralInputRetainsTurnBasedMovementOwnership() throws Exception {
        var fixture = launch();
        int x = fixture.sprite().getCentreX();
        for (int frame = 0; frame < 30; frame++) ControlledFrameRuntime.step(fixture.gameplayMode(), input, right(frame == 0));
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(0, fixture.sprite().getGSpeed());
        assertFalse(input.hasLogicalOverride());
    }

    @Test void optInUsesSuppliedDirectionalAndActionEdgesAndRestoresTheOuterOverride() throws Exception {
        nativeInput = true;
        var fixture = launch();
        int x = fixture.sprite().getCentreX();
        var outer = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(AbstractPlayableSprite.INPUT_LEFT,
                0, 0, 0, false, false), PlayerInputState.neutral());
        input.setLogicalOverride(outer);
        for (int frame = 0; frame < 20; frame++) {
            ControlledFrameRuntime.step(fixture.gameplayMode(), input, right(frame == 0));
            assertTrue(input.hasLogicalOverride());
            assertSame(outer, input.logical(), "caller-owned movie/input override remains intact");
        }
        assertTrue(fixture.sprite().getCentreX() > x, "supplied right input reaches native movement");
        assertTrue(fixture.sprite().getGSpeed() > 0);
        var jump = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT,
                0, InputActionMasks.ACTION_A, InputActionMasks.ACTION_A, false, false), PlayerInputState.neutral());
        ControlledFrameRuntime.step(fixture.gameplayMode(), input, jump);
        assertTrue(fixture.sprite().getAir());
        assertTrue(fixture.sprite().getYSpeed() < 0, "supplied fresh action edge starts the native jump");
        assertSame(outer, input.logical());
    }

    @Test void heldRowsAdmitNoMovementEvenWithNativeInputEnabled() throws Exception {
        nativeInput = true;
        var fixture = launch();
        advance = false;
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        for (int frame = 0; frame < 20; frame++) {
            assertEquals(com.openggf.LevelFrameResult.HELD,
                    ControlledFrameRuntime.step(fixture.gameplayMode(), input, right(frame == 0)));
        }
        var after = registry.capture();
        for (var entry : before.entries().entrySet()) {
            assertEquals(java.util.List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    entry.getKey(), entry.getValue(), after.get(entry.getKey())), entry.getKey());
        }
        assertFalse(input.hasLogicalOverride());
    }

    @Test void nativeInputRetainsOneVBlankPerAdvancedRowAndReplaysTwoRestores() throws Exception {
        nativeInput = true;
        var fixture = launch();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var start = registry.capture();
        assertNotNull(start.get("object-manager"), "actual native object clock owner is registered");
        var counter = com.openggf.game.session.EngineTiming.vIntRunCounter();
        long firstClock = counter.value();
        for (int frame = 0; frame < 20; frame++) ControlledFrameRuntime.step(fixture.gameplayMode(), input, right(frame == 0));
        assertEquals((firstClock + 20) & 0xFFFFFFFFL, counter.value(), "native polling keeps the ordinary one-VBlank cadence");
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(start);
            assertEquals(firstClock, counter.value(), "rewind restores the native object/polling clock");
            for (int frame = 0; frame < 20; frame++) ControlledFrameRuntime.step(fixture.gameplayMode(), input, right(frame == 0));
            var actual = registry.capture();
            for (var entry : expected.entries().entrySet()) {
                assertEquals(java.util.List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                        entry.getKey(), entry.getValue(), actual.get(entry.getKey())), "cycle " + cycle + " " + entry.getKey());
            }
        }
    }
}
