package com.openggf.game.sonic2.credits;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestSonic2EndingWidescreenLayout {

    @Test
    void nativeEndingCoordinatesFollowTheLiveViewport() {
        assertEquals(160, Sonic2CreditsTextRenderer.centerXForWidth(320));
        assertEquals(176, Sonic2CreditsTextRenderer.centerXForWidth(352));
        assertEquals(200, Sonic2CreditsTextRenderer.centerXForWidth(400));
        assertEquals(112, Sonic2EndingCutsceneManager.screenXForWidth(112, 320));
        assertEquals(152, Sonic2EndingCutsceneManager.screenXForWidth(112, 400));
        assertEquals(200, Sonic2LogoFlashManager.centerXForWidth(400));
    }

    @Test
    void providerForwardsViewportWidthToItsActiveEndingManagers() throws Exception {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        Sonic2EndingCutsceneManager cutscene = mock(Sonic2EndingCutsceneManager.class);
        Sonic2CreditsTextRenderer text = mock(Sonic2CreditsTextRenderer.class);
        Sonic2LogoFlashManager logo = mock(Sonic2LogoFlashManager.class);
        setField(provider, "cutsceneManager", cutscene);
        setField(provider, "textRenderer", text);
        setField(provider, "logoFlashManager", logo);

        provider.setViewportWidth(400);

        verify(cutscene).setViewportWidth(400);
        verify(text).setViewportWidth(400);
        verify(logo).setViewportWidth(400);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
