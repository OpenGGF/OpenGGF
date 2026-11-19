package com.openggf.game.soniccd.flow;

import com.openggf.NativeMenuPresentationAccess;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.game.soniccd.special.CdSpecialStage;
import com.openggf.game.soniccd.special.CdSpecialStageProvider;
import com.openggf.game.soniccd.special.CdSpecialStageUfos;
import com.openggf.game.soniccd.title.CdTitleScreen;
import com.openggf.game.soniccd.title.secrets.CdSecretsScreen;
import com.openggf.graphics.FadeManager.FadeState;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;

@EnabledOnOs(OS.LINUX)
class TestCdSoundTestStageEntry {
    @Test
    void soundTestLaunchesTheSecretStageAndStartReturnsToTitle() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            var provider = enterSecretStage(h);
            int stones = h.scenes.flow().timeStones();
            int nextStage = h.scenes.flow().currentSpecialStage();

            var input = h.loop.getInputHandler();
            int enter = GameServices.configuration().getInt(SonicConfiguration.PAUSE_KEY);
            input.handleKeyEvent(enter, GLFW_PRESS);
            for (int i = 0; !provider.isFinished() && i < 10; i++) h.step(0, 0, true);
            input.handleKeyEvent(enter, GLFW_RELEASE);
            assertFalse(h.loop.isUserPaused());
            assertTrue(provider.result().manualExit());
            assertEquals(FadeState.FADING_TO_WHITE, GameServices.fade().getState());
            h.until(() -> {
                GameMode mode = h.loop.getCurrentGameMode();
                assertTrue(mode == GameMode.SPECIAL_STAGE || mode == GameMode.OPTIONS
                        || mode == GameMode.TITLE_SCREEN, "unexpected return mode " + mode);
                return mode == GameMode.TITLE_SCREEN;
            }, 120, 0);
            assertTrue(h.scenes.route().stream().anyMatch(step -> step.startsWith("special stage ended ")));
            assertEquals(stones, h.scenes.flow().timeStones());
            assertEquals(nextStage, h.scenes.flow().currentSpecialStage());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 400})
    void controlledZeroUfoCompletionWaitsForFreshInputAndPreservesSave(int width) throws Exception {
        var saved = CdSaveRecord.decode(new byte[CdSaveRecord.FILE_BYTES]);
        saved.store(new CdSaveVariables(0x0215, 3, 0x08, 0x15, 0, 4, 0x0f));
        try (var h = new CdFlowHarness(width, module -> {
            CdFlowHarness.attachReadOnlySave(module, saved);
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
        })) {
            var flow = h.scenes.flow();
            var backupRam = flow.backupRam();
            var before = backupRam.rewindAdapter().capture();
            assertEquals(saved, backupRam.readSave());
            int stones = flow.timeStones();
            int nextStage = flow.currentSpecialStage();
            var provider = enterSecretStage(h);
            var stageProgram = h.scenes.lastProgramScreen();
            setControlledZeroUfoState(provider.session().stage());

            // Hold a menu confirmation from gameplay through the exit fade and the new menu.
            for (int i = 0; i < 120; i++) {
                h.step(0, CdFlowHarness.A, false);
                if (h.loop.getCurrentGameMode() == GameMode.OPTIONS && h.scenes.lastProgramScreen() != stageProgram
                        && !GameServices.fade().isActive()) break;
            }
            assertEquals(GameMode.OPTIONS, h.loop.getCurrentGameMode(), "a clear should open the acknowledgement");
            assertFalse(GameServices.fade().isActive());
            var acknowledgement = NativeMenuPresentationAccess.screen(h.loop);
            assertSame(h.scenes.lastProgramScreen(), acknowledgement);
            assertTrue(h.scenes.route().stream().anyMatch(step -> step.startsWith("special stage ended ")
                    && step.contains("ufosLeft=0") && step.contains("manualExit=false")));
            for (int i = 0; i < 30; i++) h.step(0, CdFlowHarness.A, false);
            assertEquals(GameMode.OPTIONS, h.loop.getCurrentGameMode());
            assertSame(acknowledgement, NativeMenuPresentationAccess.screen(h.loop));
            var profile = assertInstanceOf(SonicCdAudioProfile.class, GameServices.audio().getAudioProfile());
            var audio = profile.subCpuAudio();
            assertEquals(35, audio.state().cdda().track(), "the clear uses the ending cue");
            assertEquals(width, GameServices.graphics().getProjectionWidth());
            acknowledgement.setClearColor();
            glClear(GL_COLOR_BUFFER_BIT);
            acknowledgement.draw();
            GameServices.graphics().flushScreenSpace();
            assertTrue(Arrays.stream(ScreenshotCapture.captureFramebuffer(width, 224).pixels())
                    .distinct().count() > 8, "the acknowledgement draws MenuChrome at width " + width);

            h.idle(1);
            assertEquals(GameMode.OPTIONS, h.loop.getCurrentGameMode(), "releasing the button does not dismiss");
            h.step(0, CdFlowHarness.A, false);
            var title = (CdTitleScreen) h.loop.getTitleScreenProvider();
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN
                    && title.program() != null && title.program().acceptsMenuInput(), 600, 0);
            assertEquals(26, audio.state().cdda().track(), "the title restores its own cue");
            assertEquals(stones, flow.timeStones());
            assertEquals(nextStage, flow.currentSpecialStage());
            assertEquals(saved, backupRam.readSave());
            assertArrayEquals(before.internal(), backupRam.rewindAdapter().capture().internal());
        }
    }

    private static CdSpecialStageProvider enterSecretStage(CdFlowHarness h) {
        h.until(() -> h.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
        var title = (CdTitleScreen) h.loop.getTitleScreenProvider();
        h.until(() -> title.program() != null && title.program().acceptsMenuInput(), 3000, 0);
        int[][] code = {{CdFlowHarness.DOWN, 0}, {CdFlowHarness.DOWN, 0}, {CdFlowHarness.DOWN, 0},
                {CdFlowHarness.LEFT, 0}, {CdFlowHarness.RIGHT, 0}, {0, CdFlowHarness.A}};
        for (int[] press : code) {
            for (int i = 0; i < 4; i++) h.step(press[0], press[1], false);
            h.idle(12);
        }

        h.until(() -> h.loop.getCurrentGameMode() == GameMode.OPTIONS, 300, 0);
        var screen = assertInstanceOf(CdSecretsScreen.class, h.scenes.lastProgramScreen());
        var soundTest = screen.soundTest();
        for (int column = 0; column < 3; column++) {
            int index = column;
            if (column != 0) h.until(() -> soundTest.soundType() == index, 30, CdFlowHarness.RIGHT);
            h.until(() -> soundTest.selection()[index] == 7, 120, CdFlowHarness.DOWN);
        }
        h.idle(1);
        h.step(0, 0, true);

        h.until(() -> h.loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE, 300, 0);
        var provider = assertInstanceOf(CdSpecialStageProvider.class, h.loop.getActiveSpecialStageProvider());
        assertEquals(7, provider.getCurrentStage());
        assertTrue(provider.session().stage().flags().secret());
        h.until(() -> !provider.session().fadingIn(), 120, 0);
        return provider;
    }

    private static void setControlledZeroUfoState(CdSpecialStage stage) throws ReflectiveOperationException {
        // The local native fixtures never clear SS8. Set up the terminal UFO state only;
        // the session, exit fade, and menu return all run through the production game loop.
        assertEquals(6, stage.ufoCount());
        var count = CdSpecialStage.class.getDeclaredField("ufoCount");
        var alive = CdSpecialStageUfos.Ufo.class.getDeclaredField("alive");
        count.setAccessible(true);
        alive.setAccessible(true);
        count.setInt(stage, 0);
        for (var ufo : stage.ufos().ufos()) alive.setBoolean(ufo, false);
    }
}
