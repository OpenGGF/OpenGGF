package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchResponseProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBumperObjectInstance {

    @Test
    void touchProfileNamesSonic2SpecialLatchPolicy() {
        BumperObjectInstance bumper = new BumperObjectInstance(
                new ObjectSpawn(0x0F00, 0x0400, 0x44, 0, 0, false, 0),
                "Bumper");

        TouchResponseProfile profile = bumper.getTouchResponseProfile();

        assertEquals(TouchCategoryDecodeMode.SONIC2_SPECIAL_PROPERTY, profile.categoryDecodeMode());
        assertTrue(profile.continuousCallbacks());
        assertFalse(profile.requiresRenderFlagForTouch());
    }
}
