package com.openggf.tests;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.badniks.JawzBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchResponseResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestS3kJawzBadnik {

    @Test
    public void jawzInitializesVelocityTowardPlayerOnFirstVisibleFrame() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);

        JawzBadnikInstance jawz = new JawzBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 80);

        jawz.update(0, player); // Obj_WaitOffscreen restores the saved entry point
        assertEquals(160, jawz.getX(), "Jawz should not move on the initialization frame");

        jawz.update(1, player); // Obj_Jawz initializes velocity
        assertEquals(160, jawz.getX(), "Jawz initialization should not move the object");

        jawz.update(2, player);
        assertEquals(158, jawz.getX(), "Jawz should move toward the player on the next frame");
        assertEquals(1, readMappingFrame(jawz), "Jawz should advance to the second animation frame after moving");
    }

    @Test
    public void jawzTracksRightWhenPlayerIsToTheRight() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);

        JawzBadnikInstance jawz = new JawzBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 240);

        jawz.update(0, player);
        jawz.update(1, player);
        jawz.update(2, player);

        assertEquals(162, jawz.getX(), "Jawz should move right when the player is on the right");
    }

    @Test
    public void jawzUsesNativeSpecialPropertyTouchAccumulation() {
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 160, (short) 100);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 160, (short) 100);
        JawzBadnikInstance jawz = new JawzBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0));
        jawz.setServices(new TestObjectServices() {
            @Override
            public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> sonic, () -> java.util.List.of(tails));
            }
        });
        TouchResponseResult result = new TouchResponseResult(
                0x17, 8, 8, TouchCategory.SPECIAL, 0);

        jawz.onTouchResponse(sonic, result, 1);
        assertEquals(1, jawz.getCollisionProperty());
        jawz.onTouchResponse(tails, result, 1);

        assertEquals(3, jawz.getCollisionProperty(),
                "Touch_Special adds one for P1 and two for P2, selecting P2 when both overlap");
        assertEquals(0xD7, jawz.getCollisionFlags());
        assertEquals(TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY,
                jawz.getTouchResponseProfile().categoryDecodeMode());
        assertTrue(jawz.requiresContinuousTouchCallbacks());
    }

    private static int readMappingFrame(JawzBadnikInstance jawz) {
        try {
            Field field = jawz.getClass().getSuperclass().getDeclaredField("mappingFrame");
            field.setAccessible(true);
            return field.getInt(jawz);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read Jawz mapping frame", e);
        }
    }
}
