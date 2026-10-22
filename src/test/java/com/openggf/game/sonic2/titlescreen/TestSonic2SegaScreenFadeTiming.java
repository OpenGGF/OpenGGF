package com.openggf.game.sonic2.titlescreen;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.TitleScreenProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

class TestSonic2SegaScreenFadeTiming {
    private static final int FADE_FRAMES = 22;
    private static final int INTRO_TEXT_FADE_FRAMES = 22;
    private static final int INTRO_TEXT_HOLD_FRAMES = 96;

    @Test
    void segaLogoTimerStartsWithTheDirectActivePaletteEntry() throws Exception {
        TitleScreenManager manager = managerInSegaState();
        InputHandler input = new InputHandler();

        manager.update(input);

        assertEquals(1, intField(manager, "segaLogoTimer"),
                "PalLoad_Now enters the SEGA animation without a generic fade-in");
        assertEquals(TitleScreenProvider.State.SEGA_LOGO, manager.getState());
    }

    @Test
    void segaLogoFadesToBlackBeforeIntroText() throws Exception {
        TitleScreenManager manager = managerInSegaState();
        setIntField(manager, "segaLogoTimer", 106);
        setBooleanField(manager, "segaPcmStarted", true);
        setEnumField(manager, "segaLogoPhase", "END_WAIT");
        setIntField(manager, "segaEndWaitTimer", 1);
        setEnumField(manager, "segaLogoFadePhase", "ACTIVE");
        InputHandler input = new InputHandler();

        manager.update(input);

        assertEquals(TitleScreenProvider.State.SEGA_LOGO, manager.getState(),
                "S2 must hold the SEGA screen while it fades to black");
        assertEquals(106, intField(manager, "segaLogoTimer"),
                "the end wait owns its timer separately from the visual sequence");

        for (int i = 0; i < FADE_FRAMES - 1; i++) {
            manager.update(input);
            assertEquals(TitleScreenProvider.State.SEGA_LOGO, manager.getState());
        }

        manager.update(input);

        assertEquals(TitleScreenProvider.State.INTRO_TEXT_FADE_IN, manager.getState(),
                "S2 intro text starts only after the SEGA fade-to-black finishes");
    }

    @Test
    void giantSonicIsVisibleOnTheFirstActiveVisualUpdate() throws Exception {
        TitleScreenManager manager = managerInSegaState();

        assertNull(manager.resolveSegaGiantSonicPose(),
                "the runner has not executed its first object update");

        manager.update(new InputHandler());

        TitleScreenManager.SegaGiantSonicPose pose = manager.resolveSegaGiantSonicPose();
        assertNotNull(pose);
        assertEquals(0, pose.localFrame());
        assertEquals(328, pose.centerX(),
                "first active frame has already applied the ROM -$20 leftward step from screen_width+$28");
        assertEquals(true, pose.hFlip());
    }

    /**
     * ROM: TitleScreen fades the "SONIC AND MILES 'TAILS' PROWER IN" text in
     * and out silently, then spawns the intro object and runs it for one frame
     * before Pal_FadeFromBlack; Obj0E_Sonic_Init plays SndID_Sparkle on that
     * frame. The twinkle therefore lands on the first title fade-in frame.
     */
    @Test
    void sparkleWaitsForTitleFadeInNotIntroText() throws Exception {
        TitleScreenManager manager = managerInSegaState();
        setIntField(manager, "segaLogoTimer", FADE_FRAMES - 1);
        setBooleanField(manager, "segaPcmStarted", true);
        setEnumField(manager, "segaLogoFadePhase", "FADING_OUT");
        InputHandler input = new InputHandler();

        for (int i = 0; i < FADE_FRAMES && manager.getState() == TitleScreenProvider.State.SEGA_LOGO; i++) {
            manager.update(input);
        }
        assertEquals(TitleScreenProvider.State.INTRO_TEXT_FADE_IN, manager.getState());
        assertEquals(false, sparklePlayed(manager, 0),
                "no twinkle when the intro text screen begins");

        int introTextFrames = INTRO_TEXT_FADE_FRAMES + INTRO_TEXT_HOLD_FRAMES + INTRO_TEXT_FADE_FRAMES;
        for (int i = 0; i < introTextFrames - 1; i++) {
            manager.update(input);
            assertEquals(false, sparklePlayed(manager, 0),
                    "no twinkle while the intro text is on screen (frame " + i + ")");
        }
        assertEquals(TitleScreenProvider.State.INTRO_TEXT_FADE_OUT, manager.getState());

        manager.update(input);

        assertEquals(TitleScreenProvider.State.FADE_IN, manager.getState());
        assertEquals(true, sparklePlayed(manager, 0),
                "Obj0E_Sonic_Init twinkle fires as the title starts fading from black");
    }

    @Test
    void introTextInputDoesNotSkipToTheInitSparkle() throws Exception {
        TitleScreenManager manager = managerInSegaState();
        setField(manager, "state", TitleScreenProvider.State.INTRO_TEXT_HOLD);
        InputHandler input = new InputHandler();
        input.handleKeyEvent(SonicConfigurationService.getInstance().getInt(SonicConfiguration.JUMP), GLFW_PRESS);

        manager.update(input);

        assertEquals(TitleScreenProvider.State.INTRO_TEXT_HOLD, manager.getState());
        assertEquals(1, intField(manager, "introTextTimer"));
        assertEquals(false, sparklePlayed(manager, 0),
                "input cannot bypass the text's loading owner or start the title twinkle");
    }

    private static boolean sparklePlayed(TitleScreenManager manager, int index) throws Exception {
        Field field = TitleScreenManager.class.getDeclaredField("sparklePlayedAt");
        field.setAccessible(true);
        return ((boolean[]) field.get(manager))[index];
    }

    @Test
    void segaLogoFadeDoesNotUseBlackOverlayHelpers() {
        assertThrows(NoSuchMethodException.class,
                () -> method("drawSegaLogoFade", com.openggf.graphics.GraphicsManager.class));
        assertThrows(NoSuchMethodException.class,
                () -> method("segaLogoFadeAmount"));
    }

    private static TitleScreenManager managerInSegaState() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.JUMP, "SPACE");
        TitleScreenManager manager = new TitleScreenManager(config);
        setField(manager, "state", TitleScreenProvider.State.SEGA_LOGO);
        setIntField(manager, "segaLogoTimer", 0);
        setBooleanField(manager, "segaPcmStarted", false);
        return manager;
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return TitleScreenManager.class.getDeclaredMethod(name, parameterTypes);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumField(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, Enum.valueOf((Class<Enum>) field.getType(), value));
    }
}
