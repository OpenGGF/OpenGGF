package com.openggf.game.sonic1.titlescreen;

import com.openggf.audio.AudioManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Input branches from Sega_WaitEnd and GM_Title, independent of power-on timing. */
class TestSonic1IntroInputPhases {
    record SegaInput(String name, PlayerInputState p1, PlayerInputState p2,
                     boolean keyboardJump, boolean menuAccept, boolean exits) {
        @Override public String toString() { return name; }
    }

    static Stream<SegaInput> segaInputs() {
        PlayerInputState neutral = PlayerInputState.neutral();
        PlayerInputState start = PlayerInputState.of(0, 0, 0, 0, true, true);
        return Stream.of(
                new SegaInput("P1 Start edge", start, neutral, false, false, true),
                new SegaInput("held P1 Start", PlayerInputState.of(0, 0, 0, 0, true, false),
                        neutral, false, false, false),
                new SegaInput("P2 Start edge", neutral, start, false, false, false),
                action("A", InputActionMasks.ACTION_A),
                action("B", InputActionMasks.ACTION_B),
                action("C", InputActionMasks.ACTION_C),
                new SegaInput("keyboard Jump", neutral, neutral, true, false, false),
                new SegaInput("host menu accept", neutral, neutral, false, true, false));
    }

    private static SegaInput action(String name, int mask) {
        return new SegaInput(name, PlayerInputState.of(0, 0, mask, mask, false, false),
                PlayerInputState.neutral(), false, false, false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("segaInputs")
    void segaPostWaitReadsOnlyPlayerOneStart(SegaInput sample) throws Exception {
        Sonic1TitleScreenManager manager = manager(TitleScreenProvider.State.SEGA_LOGO);
        // Inside the existing post-wait window, before automatic exit. This is
        // not an assertion that the preceding fixed 98 updates model native CPU work.
        set(manager, "segaLogoTimer", 190);
        set(manager, "segaPcmStarted", true);
        AudioManager audio = mock(AudioManager.class);
        try (var services = mockStatic(GameServices.class, CALLS_REAL_METHODS)) {
            services.when(GameServices::audio).thenReturn(audio);
            manager.update(input(sample));
            assertEquals(sample.exits ? "FADING_OUT" : "ACTIVE",
                    field(manager, "segaLogoFadePhase").toString());
            verify(audio, sample.exits ? org.mockito.Mockito.times(1) : never()).stopSegaPcm();
            verify(audio, never()).playMusic(anyInt());
        }
    }

    @ParameterizedTest
    @EnumSource(value = TitleScreenProvider.State.class,
            names = {"INTRO_TEXT_FADE_IN", "INTRO_TEXT_HOLD", "INTRO_TEXT_FADE_OUT"})
    void sonicTeamFadesAndAssetLoadHaveNoInputExit(TitleScreenProvider.State phase) throws Exception {
        Sonic1TitleScreenManager manager = manager(phase);
        set(manager, "introTextTimer", 2);
        SegaInput all = new SegaInput("all buttons", PlayerInputState.of(0, 0,
                InputActionMasks.ACTION_A | InputActionMasks.ACTION_B | InputActionMasks.ACTION_C,
                InputActionMasks.ACTION_A | InputActionMasks.ACTION_B | InputActionMasks.ACTION_C,
                true, true), PlayerInputState.neutral(), true, true, false);
        AudioManager audio = mock(AudioManager.class);
        try (var services = mockStatic(GameServices.class, CALLS_REAL_METHODS)) {
            services.when(GameServices::audio).thenReturn(audio);
            manager.update(input(all));
            assertEquals(phase, manager.getState(), "GM_Title has no controller branch in STP");
            assertEquals(3, field(manager, "introTextTimer"));
            verify(audio, never()).playMusic(anyInt());
        }
    }

    @ParameterizedTest
    @MethodSource("segaInputs")
    void inputCannotExitDuringPaletteScanOrChantWait(SegaInput sample) throws Exception {
        Sonic1TitleScreenManager manager = manager(TitleScreenProvider.State.SEGA_LOGO);
        set(manager, "segaLogoTimer", 100);
        set(manager, "segaPcmStarted", true);
        manager.update(input(sample));
        assertEquals("ACTIVE", field(manager, "segaLogoFadePhase").toString());
    }

    private static InputHandler input(SegaInput sample) {
        InputHandler input = mock(InputHandler.class);
        LogicalInputSnapshot logical = LogicalInputSnapshot.ofPlayers(sample.p1, sample.p2);
        if (sample.menuAccept) logical = logical.withMenuPolicy(true, false);
        when(input.logical()).thenReturn(logical);
        when(input.isKeyPressed(anyInt())).thenReturn(sample.keyboardJump);
        return input;
    }

    private static Sonic1TitleScreenManager manager(TitleScreenProvider.State state) throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.JUMP, "SPACE");
        Sonic1TitleScreenManager manager = new Sonic1TitleScreenManager(config);
        set(manager, "state", state);
        return manager;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
