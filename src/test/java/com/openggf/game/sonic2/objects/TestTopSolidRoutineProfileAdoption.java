package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTopSolidRoutineProfileAdoption {

    @Test
    void cpzPlatformDeclaresTopSolidRoutineProfile() throws Exception {
        CPZPlatformObjectInstance platform = new CPZPlatformObjectInstance(
                new ObjectSpawn(0x1000, 0x0300, Sonic2ObjectIds.GENERIC_PLATFORM_B, 0x00, 0, false, 0),
                "CPZPlatform");

        assertDeclaredTopSolidProfile(CPZPlatformObjectInstance.class, platform);
    }

    @Test
    void sidewaysPformDeclaresTopSolidRoutineProfile() throws Exception {
        SidewaysPformObjectInstance platform = new SidewaysPformObjectInstance(
                new ObjectSpawn(0x1000, 0x0300, Sonic2ObjectIds.SIDEWAYS_PFORM, 0x00, 0, false, 0),
                "SidewaysPform");

        assertDeclaredTopSolidProfile(SidewaysPformObjectInstance.class, platform);
    }

    @Test
    void swingingPformDeclaresTopSolidRoutineProfile() throws Exception {
        SwingingPformObjectInstance platform = new SwingingPformObjectInstance(
                new ObjectSpawn(0x1000, 0x0300, Sonic2ObjectIds.SWINGING_PFORM, 0x00, 0, false, 0),
                "SwingingPform");

        assertDeclaredTopSolidProfile(SwingingPformObjectInstance.class, platform);
    }

    @Test
    void swingingPlatformDeclaresTopSolidRoutineProfile() throws Exception {
        SwingingPlatformObjectInstance platform = ObjectConstructionContext.construct(
                new StubObjectServices(),
                () -> new SwingingPlatformObjectInstance(
                        new ObjectSpawn(0x1000, 0x0300, Sonic2ObjectIds.SWINGING_PLATFORM, 0x00, 0, false, 0),
                        "SwingingPlatform"));

        assertDeclaredTopSolidProfile(SwingingPlatformObjectInstance.class, platform);
    }

    private static void assertDeclaredTopSolidProfile(
            Class<?> owner,
            SolidObjectProvider provider) throws Exception {
        Method method = owner.getDeclaredMethod("getSolidRoutineProfile");

        assertEquals(SolidRoutineProfile.class, method.getReturnType());
        assertTrue(provider.isTopSolidOnly());
        assertEquals(SolidRoutineProfile.topSolid(provider.usesStickyContactBuffer()),
                provider.getSolidRoutineProfile());
    }
}
