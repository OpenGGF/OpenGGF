package com.openggf.tests;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.badniks.PenguinatorBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class TestS3kPenguinatorBadnik {

    @Test
    void registryCreatesPenguinatorInstance() {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.PENGUINATOR, 0, 0, false, 0));

        assertInstanceOf(PenguinatorBadnikInstance.class, instance);
    }

    @Test
    void exposesRomCollisionFlagsAndPriority() {
        PenguinatorBadnikInstance penguinator = create(0);

        assertEquals(0x1A, penguinator.getCollisionFlags());
        assertEquals(5, penguinator.getPriorityBucket());
    }

    @Test
    void firstPatrolTickAcceleratesInSpawnFacingDirection() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        PenguinatorBadnikInstance penguinator = create(0);
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 1));

            penguinator.update(0, player);
            assertEquals(0, readInt(penguinator, "xVelocity"),
                    "ROM init frame sets $40 but does not move yet");

            penguinator.update(1, player);
        }

        assertEquals(-2, readInt(penguinator, "xVelocity"),
                "render_flags bit 0 clear initializes $40 to -2");
        assertEquals(1, readInt(penguinator, "mappingFrame"),
                "byte_8BE0A first Animate_RawGetFaster tick selects frame 1");
    }

    @Test
    void flippedSpawnAcceleratesRightOnFirstPatrolTick() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        PenguinatorBadnikInstance penguinator = create(1);
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 1));

            penguinator.update(0, player);
            penguinator.update(1, player);
        }

        assertEquals(2, readInt(penguinator, "xVelocity"),
                "render_flags bit 0 set initializes $40 to +2");
    }

    @Test
    void slideWaitExpiresIntoSlideRecoveryInsteadOfRestartingPatrol() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        PenguinatorBadnikInstance penguinator = create(0);
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);

        setEnum(penguinator, "state", "SLIDE_WAIT");
        setInt(penguinator, "routineTimer", 0);
        setInt(penguinator, "yRadius", 0x0B);
        setInt(penguinator, "mappingFrame", 8);
        setInt(penguinator, "xVelocity", -0x200);

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 1));

            penguinator.update(0, player);
        }

        assertEquals("SLIDE_RECOVER", readEnumName(penguinator, "state"),
                "loc_8BC6C Obj_Wait callback must run loc_8BC94, not loc_8BB24");
        assertEquals(4, readInt(penguinator, "animFrame"),
                "sub_8BD9C updates mapping_frame before loc_8BC94 seeds anim_frame to 8 - mapping_frame");
    }

    private static PenguinatorBadnikInstance create(int renderFlags) {
        return new PenguinatorBadnikInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.PENGUINATOR, 0, renderFlags, false, 0));
    }

    private static int readInt(PenguinatorBadnikInstance penguinator, String fieldName) {
        try {
            Class<?> type = penguinator.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.getInt(penguinator);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            throw new AssertionError("Missing field " + fieldName);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Failed to read " + fieldName, e);
        }
    }

    private static String readEnumName(PenguinatorBadnikInstance penguinator, String fieldName) {
        Object value = readField(penguinator, fieldName);
        return ((Enum<?>) value).name();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(PenguinatorBadnikInstance penguinator, String fieldName, String valueName) {
        Field field = findField(penguinator, fieldName);
        try {
            field.setAccessible(true);
            field.set(penguinator, Enum.valueOf((Class<Enum>) field.getType(), valueName));
        } catch (IllegalAccessException e) {
            throw new AssertionError("Failed to write " + fieldName, e);
        }
    }

    private static void setInt(PenguinatorBadnikInstance penguinator, String fieldName, int value) {
        Field field = findField(penguinator, fieldName);
        try {
            field.setAccessible(true);
            field.setInt(penguinator, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Failed to write " + fieldName, e);
        }
    }

    private static Object readField(PenguinatorBadnikInstance penguinator, String fieldName) {
        Field field = findField(penguinator, fieldName);
        try {
            field.setAccessible(true);
            return field.get(penguinator);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Failed to read " + fieldName, e);
        }
    }

    private static Field findField(PenguinatorBadnikInstance penguinator, String fieldName) {
        Class<?> type = penguinator.getClass();
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
