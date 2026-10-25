package com.openggf.game.sonic2.titlescreen;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static com.openggf.sprites.playable.AbstractPlayableSprite.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2NativeTitleControls {
    @Test
    void availableCompetitionUsesBothControllerEdgesAndAllThreeWrappedSelections() throws Exception {
        try (var services = mockStatic(GameServices.class)) {
            var audio = mock(AudioManager.class);
            services.when(GameServices::audio).thenReturn(audio);
            var title = activeTitle(true);
            var input = new InputHandler();
            update(title, input, 0, 0, false, INPUT_UP, 0, false);
            assertEquals(2, title.getNativeMenuSelection());
            update(title, input, INPUT_DOWN, 0, false, 0, 0, false);
            assertEquals(0, title.getNativeMenuSelection());
            update(title, input, 0, 0, false, INPUT_DOWN, 0, false);
            assertEquals(1, title.getNativeMenuSelection());
            update(title, input, INPUT_UP, 0, false, INPUT_DOWN, 0, false);
            assertEquals(1, title.getNativeMenuSelection(), "Up then Down preserve the original selection");
            PlayerInputState held = PlayerInputState.of(INPUT_DOWN, 0, 0, 0, true, false);
            input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(held, held));
            for (int frame = 0; frame < 60; frame++) title.update(input);
            assertEquals(1, title.getNativeMenuSelection());
            assertFalse(title.isExiting());
            verify(audio, times(4)).playSfx(Sonic2Sfx.BLIP.id);
            update(title, input, 0, 0, false, 0, 0, true);
            assertTrue(title.isExiting());
            assertEquals(TitleScreenProvider.TitleScreenAction.TWO_PLAYER, title.consumeExitAction());
            assertEquals(TitleScreenProvider.TitleScreenAction.OTHER, title.consumeExitAction());
        }
    }

    @Test
    void optionsAreReachableWhileCompetitionIsUnavailableAndFaceButtonsDoNotLaunch() throws Exception {
        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::audio).thenReturn(mock(AudioManager.class));
            var title = activeTitle(false);
            var input = new InputHandler();
            update(title, input, INPUT_DOWN, InputActionMasks.ACTION_B, false, 0, 0, false);
            assertEquals(2, title.getNativeMenuSelection());
            assertFalse(title.isExiting());
            update(title, input, 0, 0, false, INPUT_DOWN, 0, false);
            assertEquals(0, title.getNativeMenuSelection());
            update(title, input, 0, 0, false, INPUT_UP, 0, true);
            assertTrue(title.isExiting());
            assertEquals(TitleScreenProvider.TitleScreenAction.OPTIONS, title.consumeExitAction());
        }
    }

    @Test
    void disablingCompetitionCannotLeaveAStaleCompetitionLaunch() throws Exception {
        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::audio).thenReturn(mock(AudioManager.class));
            var title = activeTitle(true);
            var input = new InputHandler();
            update(title, input, INPUT_DOWN, 0, false, 0, 0, false);
            assertEquals(1, title.getNativeMenuSelection());
            title.setNativeMenuAvailability(false);
            assertEquals(0, title.getNativeMenuSelection());
            update(title, input, 0, 0, true, 0, 0, false);
            assertEquals(TitleScreenProvider.TitleScreenAction.ONE_PLAYER, title.consumeExitAction());
        }
    }

    static TitleScreenManager activeTitle(boolean competition) throws Exception {
        var title = new TitleScreenManager();
        title.setNativeMenuAvailability(competition);
        set(title, "state", TitleScreenProvider.State.ACTIVE);
        set(title, "introComplete", true);
        set(title, "musicPlaying", true);
        set(title, "sonicPaletteLoaded", true);
        return title;
    }

    static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void update(TitleScreenManager title, InputHandler input, int d1, int a1,
            boolean s1, int d2, int a2, boolean s2) {
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(d1, d1, a1, a1, s1, s1), PlayerInputState.of(d2, d2, a2, a2, s2, s2)));
        title.update(input);
    }
}
