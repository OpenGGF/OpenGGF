package com.openggf.game.sonic1.credits;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestSonic1EndingWidescreenLayout {

    @Test
    void nativeCreditCenterFollowsTheLiveViewport() {
        assertEquals(160, Sonic1CreditsTextRenderer.centerXForWidth(320));
        assertEquals(176, Sonic1CreditsTextRenderer.centerXForWidth(352));
        assertEquals(200, Sonic1CreditsTextRenderer.centerXForWidth(400));
        assertEquals(200, TryAgainEndManager.centerXForWidth(400));
    }

    @Test
    void providerForwardsViewportWidthToItsActiveEndingManagers() throws Exception {
        Sonic1EndingProvider provider = new Sonic1EndingProvider();
        Sonic1CreditsManager credits = mock(Sonic1CreditsManager.class);
        TryAgainEndManager tryAgain = mock(TryAgainEndManager.class);
        setField(provider, "creditsManager", credits);
        setField(provider, "tryAgainEndManager", tryAgain);

        provider.setViewportWidth(400);

        verify(credits).setViewportWidth(400);
        verify(tryAgain).setViewportWidth(400);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
