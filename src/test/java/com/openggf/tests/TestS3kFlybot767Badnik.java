package com.openggf.tests;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.CompressionType;
import com.openggf.tools.Sonic3kObjectProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestS3kFlybot767Badnik {

    @Test
    void registryCreatesFlybot767AndProfileMarksS3klSlotImplemented() {
        ObjectInstance instance = createFlybot();

        assertEquals("Flybot767BadnikInstance", instance.getClass().getSimpleName());
        assertTrue(new Sonic3kObjectProfile().getImplementedIds().contains(Sonic3kObjectIds.FLYBOT_767));
    }

    @Test
    void chaseStateSwitchesToAttackWindupWhenPlayerIsBelowWithinSixtyPixels() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        AbstractObjectInstance flybot = createFlybot();
        AbstractPlayableSprite player = playerAt(0x0230, 0x0140);
        setBoolean(flybot, "waitingForOnscreen", false);

        flybot.update(0, player);
        flybot.update(1, player);

        assertEquals("ATTACK_WINDUP", readEnumName(flybot, "state"));
        assertEquals(0, readInt(flybot, "yVelocity"),
                "loc_8C9EC clears y_vel before the windup animation");
        assertEquals(flybot.getY(), readInt(flybot, "originY"),
                "loc_8C9EC snapshots y_pos in objoff_44 for the rebound limit");
    }

    @Test
    void reboundAboveOriginStopsAndWaitsBeforeReturningToChase() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        AbstractObjectInstance flybot = createFlybot();
        AbstractPlayableSprite player = playerAt(0x0200, 0x0140);
        setEnum(flybot, "state", "REBOUND");
        setInt(flybot, "originY", 0x0120);
        setInt(flybot, "yVelocity", -0x200);
        setInt(flybot, "currentY", 0x0120);
        setBoolean(flybot, "waitingForOnscreen", false);

        flybot.update(0, player);

        assertEquals("WAIT_RETURN", readEnumName(flybot, "state"));
        assertEquals(0, readInt(flybot, "xVelocity"));
        assertEquals(0, readInt(flybot, "yVelocity"));
        assertEquals(0x1F, readInt(flybot, "waitTimer"));
    }

    @Test
    void traceFrame410KeepsFlybotOnePixelColumnBeforeTouchOverlap() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        AbstractObjectInstance flybot = createFlybotAt(0x0406, 0x05CC);
        AbstractPlayableSprite player = playerAt(0x0346, 0x062C);

        for (int frame = 308; frame <= 410; frame++) {
            flybot.update(frame, player);
        }

        assertEquals("DIVE", readEnumName(flybot, "state"),
                "ROM Obj_Flybot767 is in routine $06 at LBZ1 trace frame 410.");
        assertEquals(0x035B, flybot.getX(),
                "Obj_WaitOffscreen plus Animate_Raw byte_8CB36/byte_8CB3F keep x_pos at the ROM frame-410 value.");
        assertEquals(0x0617, flybot.getY(),
                "Animate_Raw skips the first script entry after clr.b anim_frame, so y_pos must match ROM frame 410.");
    }

    @Test
    void firstOnscreenWaitPassDoesNotPublishTouchResponseListEntry() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        AbstractObjectInstance flybot = createFlybot();
        AbstractPlayableSprite player = playerAt(0x0200, 0x0140);

        flybot.update(0, player);

        assertEquals("INIT", readEnumName(flybot, "state"),
                "Obj_WaitOffscreen returns before Obj_Flybot767 dispatches its routine.");
        assertFalse(flybot.publishesTouchResponseListEntryThisFrame(),
                "The wait helper returns before Sprite_CheckDeleteTouchSlotted can add Flybot to the S3K touch list.");
    }

    @Test
    void lbzArtPlanRegistersUncompressedDplcFlybotSheet() {
        Sonic3kPlcArtRegistry.StandaloneArtEntry entry =
                Sonic3kPlcArtRegistry.getPlan(0x06, 0).standaloneArt().stream()
                        .filter(e -> Sonic3kObjectArtKeys.FLYBOT_767.equals(e.key()))
                        .findFirst()
                        .orElseThrow();

        assertEquals(Sonic3kConstants.ART_UNC_FLYBOT_767_ADDR, entry.artAddr());
        assertEquals(Sonic3kConstants.ART_UNC_FLYBOT_767_SIZE, entry.artSize());
        assertEquals(Sonic3kConstants.MAP_FLYBOT_767_ADDR, entry.mappingAddr());
        assertEquals(Sonic3kConstants.DPLC_FLYBOT_767_ADDR, entry.dplcAddr());
        assertEquals(CompressionType.UNCOMPRESSED, entry.compression());
        assertEquals(1, entry.palette());
    }

    private static AbstractObjectInstance createFlybot() {
        return createFlybotAt(0x0200, 0x0100);
    }

    private static AbstractObjectInstance createFlybotAt(int x, int y) {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(
                new ObjectSpawn(x, y, Sonic3kObjectIds.FLYBOT_767, 0, 0, false, 0));
        assertTrue(instance instanceof AbstractObjectInstance,
                "Flybot767 registry entry should create an object instance");
        return (AbstractObjectInstance) instance;
    }

    private static AbstractPlayableSprite playerAt(int x, int y) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn(Short.valueOf((short) x));
        when(player.getCentreY()).thenReturn(Short.valueOf((short) y));
        when(player.getDead()).thenReturn(false);
        return player;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(Object target, String fieldName, String valueName) {
        Field field = findField(target, fieldName);
        try {
            field.setAccessible(true);
            field.set(target, Enum.valueOf((Class<Enum>) field.getType(), valueName));
        } catch (IllegalAccessException e) {
            throw new AssertionError("Failed to write " + fieldName, e);
        }
    }

    private static void setInt(Object target, String fieldName, int value) {
        Field field = findField(target, fieldName);
        try {
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Failed to write " + fieldName, e);
        }
    }

    private static void setBoolean(Object target, String fieldName, boolean value) {
        Field field = findField(target, fieldName);
        try {
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Failed to write " + fieldName, e);
        }
    }

    private static int readInt(Object target, String fieldName) {
        Field field = findField(target, fieldName);
        try {
            field.setAccessible(true);
            return field.getInt(target);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Failed to read " + fieldName, e);
        }
    }

    private static String readEnumName(Object target, String fieldName) {
        Field field = findField(target, fieldName);
        try {
            field.setAccessible(true);
            return ((Enum<?>) field.get(target)).name();
        } catch (IllegalAccessException e) {
            throw new AssertionError("Failed to read " + fieldName, e);
        }
    }

    private static Field findField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                return type.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new AssertionError("Missing field " + fieldName);
    }
}
