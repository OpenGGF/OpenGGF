package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidRoutineKind;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTopSolidRoutineProfileAdoption {

    @Test
    void floatingPlatformDeclaresTopSolidRoutineProfile() throws Exception {
        FloatingPlatformObjectInstance platform = ObjectConstructionContext.construct(
                new StubObjectServices(),
                () -> new FloatingPlatformObjectInstance(
                        new ObjectSpawn(0x1000, 0x0300, 0x51, 0x00, 0, false, 0)));

        Method method = FloatingPlatformObjectInstance.class.getDeclaredMethod("getSolidRoutineProfile");

        assertEquals(SolidRoutineProfile.class, method.getReturnType());
        assertTrue(platform.isTopSolidOnly());

        // Independent expectation: the floating platform is a top-solid object that
        // inherits the default sticky-contact buffer (it does not override
        // usesStickyContactBuffer()), so its profile must equal topSolid(true) built
        // without reference to the SUT's own accessor.
        SolidRoutineProfile profile = platform.getSolidRoutineProfile();
        assertEquals(SolidRoutineProfile.topSolid(true), profile);

        // Concrete observable fields of the top-solid routine.
        assertEquals(SolidRoutineKind.TOP_SOLID_ONLY, profile.kind());
        assertTrue(profile.topSolidOnly());
        assertTrue(profile.stickyContactBuffer());
        assertFalse(profile.monitorSolidity());
        assertFalse(profile.usesCollisionHalfWidthForTopLanding());
    }
}
