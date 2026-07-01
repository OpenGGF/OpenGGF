package com.openggf.game.sonic2.titlescreen;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.TitleScreenProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSonic2SegaScreenFadeTiming {
    private static final int SEGA_ACTIVE_FRAMES = 106 + 180;
    private static final int FADE_FRAMES = 22;

    @Test
    void segaLogoTimerWaitsUntilFadeFromBlackCompletes() throws Exception {
        TitleScreenManager manager = managerInSegaState();
        InputHandler input = new InputHandler();

        for (int i = 0; i < FADE_FRAMES - 1; i++) {
            manager.update(input);
            assertEquals(0, intField(manager, "segaLogoTimer"),
                    "S2 SEGA animation must not advance during fade-from-black");
            assertEquals(TitleScreenProvider.State.SEGA_LOGO, manager.getState());
        }

        manager.update(input);

        assertEquals(1, intField(manager, "segaLogoTimer"),
                "S2 SEGA animation starts on the first frame after fade-from-black");
    }

    @Test
    void segaLogoFadesToBlackBeforeIntroText() throws Exception {
        TitleScreenManager manager = managerInSegaState();
        setIntField(manager, "segaLogoTimer", SEGA_ACTIVE_FRAMES - 1);
        setBooleanField(manager, "segaPcmStarted", true);
        setEnumField(manager, "segaLogoFadePhase", "ACTIVE");
        InputHandler input = new InputHandler();

        manager.update(input);

        assertEquals(TitleScreenProvider.State.SEGA_LOGO, manager.getState(),
                "S2 must hold the SEGA screen while it fades to black");
        assertEquals(SEGA_ACTIVE_FRAMES, intField(manager, "segaLogoTimer"));

        for (int i = 0; i < FADE_FRAMES - 1; i++) {
            manager.update(input);
            assertEquals(TitleScreenProvider.State.SEGA_LOGO, manager.getState());
        }

        manager.update(input);

        assertEquals(TitleScreenProvider.State.INTRO_TEXT_FADE_IN, manager.getState(),
                "S2 intro text starts only after the SEGA fade-to-black finishes");
    }

    @Test
    void giantSonicIsHiddenUntilSegaLogoFadeInCompletes() throws Exception {
        TitleScreenManager manager = managerInSegaState();

        assertNull(manager.resolveSegaGiantSonicPose(),
                "ObjB0 runner should not display during Pal_FadeFromBlack");

        setEnumField(manager, "segaLogoFadePhase", "ACTIVE");
        setIntField(manager, "segaLogoTimer", 1);

        TitleScreenManager.SegaGiantSonicPose pose = manager.resolveSegaGiantSonicPose();
        assertEquals(0, pose.localFrame());
        assertEquals(328, pose.centerX(),
                "first active frame has already applied the ROM -$20 leftward step from screen_width+$28");
        assertEquals(true, pose.hFlip());
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
