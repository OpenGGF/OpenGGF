package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.MenuInput;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A frame controller drives the native step's pad ({@code nativeInput}) and claims the keyboard
 * for typed text ({@code capturesKeyboardText}); the default stays neutral.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestControlledCourseInput {
    private SharedLevel bootstrap;

    @AfterEach void dispose() { if (bootstrap != null) bootstrap.dispose(); }

    static final class Rails implements GameplayFrameController, RewindSnapshottable<Integer> {
        final boolean drive;
        final boolean capture;
        final List<String> typed = new ArrayList<>();
        int previousMask;
        Rails(boolean drive, boolean capture) { this.drive = drive; this.capture = capture; }
        @Override public String key() { return "rails"; }
        @Override public Integer capture() { return previousMask; }
        @Override public void restore(Integer value) { previousMask = value; }
        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
            String text = course.typedText();
            if (!text.isEmpty()) typed.add(text);
            return true;
        }
        @Override public void afterTick(CourseControl course, boolean advanced) { }
        @Override public LogicalInputSnapshot nativeInput(CourseControl course) {
            if (!drive) return GameplayFrameController.super.nativeInput(course);
            int mask = AbstractPlayableSprite.INPUT_RIGHT;
            var p1 = PlayerInputState.of(mask, mask & ~previousMask, 0, 0, false, false);
            previousMask = mask;
            return LogicalInputSnapshot.ofPlayers(p1, PlayerInputState.neutral());
        }
        @Override public boolean capturesKeyboardText() { return capture; }
    }

    private HeadlessTestFixture launch(Rails rails) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var context = new ModContext("rails", "s2", ModAssetRoot.forTests("rails"));
        context.registerServiceBundle("rails", () -> GameServiceBundle.builder().frameController("mode", rails).build());
        var boundary = new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> { });
        GameModule effective = new ModBackedGamePatch(context.freeze(), boundary).apply(GameServices.module(), null);
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
    }

    private static int run(HeadlessTestFixture fixture, InputHandler input, int rows) {
        int startX = fixture.sprite().getCentreX();
        for (int row = 0; row < rows; row++) {
            ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
            input.update();
        }
        return fixture.sprite().getCentreX() - startX;
    }

    @Test void controllerSuppliedPadReachesTheNativeStep() throws Exception {
        var fixture = launch(new Rails(true, false));
        int moved = run(fixture, new InputHandler(), 240);
        assertTrue(moved > 64, "held RIGHT from nativeInput moves Sonic; moved " + moved);
    }

    @Test void defaultNativeInputStaysNeutralEvenWithLiveKeysHeld() throws Exception {
        var fixture = launch(new Rails(false, false));
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT, 0, 0, 0, false, false), PlayerInputState.neutral()));
        assertEquals(0, run(fixture, input, 240), "live input never reaches a controlled course");
    }

    @Test void capturingControllersReadTypedTextAndHideLetterShortcuts() throws Exception {
        var rails = new Rails(false, true);
        var fixture = launch(rails);
        var input = new InputHandler();
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_R, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        MenuInput.handleCharEvent(input, 'r');
        ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
        assertEquals(List.of("r"), rails.typed);
        assertTrue(input.isKeyboardTextCaptured());
        assertFalse(input.isKeyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_R), "R cannot also reach live rewind");
    }
}
