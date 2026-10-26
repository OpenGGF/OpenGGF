package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Production update-path coverage of locked-on loc_620AA and loc_620D8. */
class TestHcz2CutsceneFloorContacts {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(
            0x3A46, 0x066C, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 8, 0, false, 0);
    private static final ChunkDesc SOLID = new ChunkDesc(7 | 0x1000);

    @Test
    void firstPenetrationUsesInjectedTerrainWithoutSnappingOrTurning() throws Exception {
        LevelManager level = flatTiles(12, true);
        CutsceneKnucklesHcz2Instance knuckles = jumping(level, true);
        setInt(knuckles, "currentY", 0x0600);
        setInt(knuckles, "xSub", 0x1234);
        setInt(knuckles, "ySub", 0x5678);
        setBoolean(knuckles, "facingRight", true);

        knuckles.update(1, null);

        verify(level).getChunkDescAt((byte) 0, 0x3A63, 0x0614);
        verify(level).getSolidTileForChunkDesc(SOLID, 0x0C, true);
        assertEquals(0x0601, knuckles.getY(),
                "loc_620AA retains first-contact penetration even when terrain is far above the spawn Y");
        assertEquals(0x3A63, knuckles.getX());
        assertEquals(0x1234, getInt(knuckles, "xSub"));
        assertEquals(0x5678, getInt(knuckles, "ySub"));
        assertEquals(0x0100, getInt(knuckles, "xVel"));
        assertEquals(-0x0138, getInt(knuckles, "yVel"));
        assertTrue(getBoolean(knuckles, "bounced"));
        assertTrue(getBoolean(knuckles, "facingRight"),
                "loc_620AA has no render_flags direction write on first contact");
        assertEquals(10, getInt(knuckles, "routine"));
    }

    @Test
    void secondPenetrationAddsMeasuredDistanceAndRetainsNativeVelocityWords() throws Exception {
        CutsceneKnucklesHcz2Instance knuckles = jumping(flatTiles(12, false), false);
        setInt(knuckles, "currentY", 0x0601);
        setInt(knuckles, "ySub", 0x5678);
        setBoolean(knuckles, "bounced", true);
        setBoolean(knuckles, "facingRight", true);

        knuckles.update(1, null);

        assertEquals(0x0600, knuckles.getY(),
                "loc_620D8 adds the -2 pixel floor distance rather than restoring the spawn Y");
        assertEquals(0x5678, getInt(knuckles, "ySub"));
        assertEquals(-0x0100, getInt(knuckles, "xVel"));
        assertEquals(0x0138, getInt(knuckles, "yVel"),
                "loc_620D8 starts the laugh without clearing the velocity words");
        assertTrue(getBoolean(knuckles, "facingRight"));
        assertEquals(12, getInt(knuckles, "routine"));
        assertEquals(0x1C, getInt(knuckles, "mappingFrame"));
        assertEquals(0, getInt(knuckles, "animationTick"));
        assertEquals(0, getInt(knuckles, "animationIndex"));
        assertFalse(getBoolean(knuckles, "laughIntroFinished"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 11})
    void missingPositiveAndZeroDistanceDoNotCountAsContact(int tileHeight) throws Exception {
        for (boolean alreadyBounced : new boolean[] {false, true}) {
            LevelManager level = flatTiles(tileHeight, false);
            CutsceneKnucklesHcz2Instance knuckles = jumping(level, false);
            setInt(knuckles, "currentY", 0x0600);
            setBoolean(knuckles, "bounced", alreadyBounced);

            knuckles.update(1, null);

            verify(level).getChunkDescAt((byte) 0, 0x3A63, 0x0614);
            assertEquals(alreadyBounced, getBoolean(knuckles, "bounced"),
                    "loc_620AA's bpl branch rejects no surface, positive distance and exact zero");
            assertEquals(10, getInt(knuckles, "routine"),
                    "non-negative floor distance also cannot terminate an existing bounce");
            assertEquals(-0x0100, getInt(knuckles, "xVel"));
            assertEquals(0x0138, getInt(knuckles, "yVel"));
        }
    }

    @Test
    void risingDispatchSkipsTerrainButTheApexUsesTheUpdatedSignedVelocity() throws Exception {
        LevelManager level = flatTiles(12, false);
        CutsceneKnucklesHcz2Instance rising = jumping(level, false);
        setInt(rising, "yVel", -0x0100);

        rising.update(1, null);

        verifyNoInteractions(level);
        assertEquals(-0x00C8, getInt(rising, "yVel"));

        CutsceneKnucklesHcz2Instance apex = jumping(level, false);
        setInt(apex, "currentY", 0x0602);
        setInt(apex, "yVel", -0x0020);

        apex.update(2, null);

        verify(level).getChunkDescAt((byte) 0, 0x3A63, 0x0614);
        assertTrue(getBoolean(apex, "bounced"),
                "tst.w y_vel follows MoveSprite's +$38, even though this dispatch moved upward");
        assertEquals(-0x0018, getInt(apex, "yVel"));
    }

    @Test
    void gravityAndFirstContactNegationUseSignedWords() throws Exception {
        LevelManager level = flatTiles(12, false);
        CutsceneKnucklesHcz2Instance overflow = jumping(level, false);
        setInt(overflow, "yVel", 0x7FF0);

        overflow.update(1, null);

        assertEquals((short) 0x8028, getInt(overflow, "yVel"));
        verifyNoInteractions(level);

        CutsceneKnucklesHcz2Instance contact = jumping(level, false);
        setInt(contact, "currentY", 0x0600);
        setInt(contact, "xVel", Short.MIN_VALUE);

        contact.update(2, null);

        assertTrue(getBoolean(contact, "bounced"));
        assertEquals(Short.MIN_VALUE, getInt(contact, "xVel"),
                "neg.w $8000 retains $8000 instead of widening to positive 32768");
    }

    @Test
    void optionalBackgroundFloorUsesTheInjectedProviderAndTranslatedPoint() throws Exception {
        LevelManager level = mock(LevelManager.class);
        SolidTile floor = tile(12);
        when(level.getChunkDescAt((byte) 1, 0x3963, 0x0614)).thenReturn(SOLID);
        when(level.getSolidTileForChunkDesc(SOLID, 0x0C, false)).thenReturn(floor);
        BackgroundPlaneCollisionProvider provider = () ->
                new BackgroundPlaneCollisionProvider.State(true, 0x100, 0);
        CutsceneKnucklesHcz2Instance knuckles = jumping(level, provider, false);
        setInt(knuckles, "currentY", 0x0600);

        knuckles.update(1, null);

        verify(level).getChunkDescAt((byte) 1, 0x3963, 0x0614);
        assertTrue(getBoolean(knuckles, "bounced"),
                "ObjCheckFloorDist delegates optional background collision to FindFloor");
    }

    @Test
    void nativeStockContactPositionsAndRewindForwardReplayKeepThePenetration() throws Exception {
        LevelManager level = mock(LevelManager.class);
        SolidTile fullTile = tile(16);
        when(level.getChunkDescAt(eq((byte) 0), anyInt(), anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(2) >= 0x0680 ? SOLID : new ChunkDesc(0));
        when(level.getSolidTileForChunkDesc(SOLID, 0x0C, false)).thenReturn(fullTile);
        CutsceneKnucklesHcz2Instance knuckles = jumping(level, false);
        setInt(knuckles, "yVel", -0x0400);

        // Independent native object_state observations: committed Sonic+Tails
        // hcz_4 rows 846 (launch), 884/885 (first contact), 924/925 (second).
        // This fixture supplies a static terrain plane, never per-row gameplay state.
        for (int frame = 1; frame <= 37; frame++) {
            knuckles.update(frame, null);
        }
        var beforeFirstContact = knuckles.captureRewindState();
        List<MotionState> expected = new ArrayList<>();
        for (int frame = 38; frame <= 79; frame++) {
            knuckles.update(frame, null);
            expected.add(state(knuckles));
            switch (frame) {
                case 38 -> assertPosition(knuckles, 0x3A3E, 0x066D, 10);
                case 39 -> assertPosition(knuckles, 0x3A3F, 0x0669, 10);
                case 78 -> assertPosition(knuckles, 0x3A66, 0x066B, 10);
                case 79 -> assertPosition(knuckles, 0x3A67, 0x066C, 12);
                default -> { }
            }
        }

        CutsceneKnucklesHcz2Instance restored = jumping(level, false);
        restored.restoreRewindState(beforeFirstContact);
        List<MotionState> replayed = new ArrayList<>();
        for (int frame = 38; frame <= 79; frame++) {
            restored.update(frame, null);
            replayed.add(state(restored));
        }
        assertEquals(expected, replayed,
                "restoring into a fresh instance before first contact must replay both native branches");
        verify(level, never()).getChunkDescAt(eq((byte) 1), anyInt(), anyInt());
    }

    private static void assertPosition(CutsceneKnucklesHcz2Instance knuckles, int x, int y, int routine)
            throws Exception {
        assertEquals(x, knuckles.getX());
        assertEquals(y, knuckles.getY());
        assertEquals(routine, getInt(knuckles, "routine"));
    }

    private static LevelManager flatTiles(int height, boolean secondary) {
        LevelManager level = mock(LevelManager.class);
        when(level.getChunkDescAt(eq((byte) 0), anyInt(), anyInt())).thenReturn(SOLID);
        when(level.getSolidTileForChunkDesc(SOLID, 0x0C, secondary)).thenReturn(tile(height));
        return level;
    }

    private static SolidTile tile(int height) {
        byte[] heights = new byte[16];
        Arrays.fill(heights, (byte) height);
        return new SolidTile(7, heights, heights.clone(), (byte) 0);
    }

    private static CutsceneKnucklesHcz2Instance jumping(LevelManager level, boolean secondary)
            throws Exception {
        return jumping(level, BackgroundPlaneCollisionProvider.FOREGROUND_ONLY, secondary);
    }

    private static CutsceneKnucklesHcz2Instance jumping(LevelManager level,
            BackgroundPlaneCollisionProvider provider, boolean secondary) throws Exception {
        CutsceneKnucklesHcz2Instance knuckles = new CutsceneKnucklesHcz2Instance(SPAWN);
        Camera camera = new Camera();
        knuckles.setServices(new StubObjectServices() {
            @Override public Camera camera() { return camera; }
            @Override public LevelManager levelManager() { return level; }
            @Override public BackgroundPlaneCollisionProvider backgroundPlaneCollisionProvider() { return provider; }
            @Override public boolean useSecondaryTerrainCollisionPath() { return secondary; }
        });
        // Enter the isolated jump routine without running unrelated palette/art/music setup.
        setBoolean(knuckles, "initialized", true);
        setInt(knuckles, "routine", 10);
        setInt(knuckles, "currentX", 0x3A64);
        setInt(knuckles, "xVel", -0x0100);
        setInt(knuckles, "yVel", 0x0100);
        return knuckles;
    }

    private record MotionState(int x, int y, int xSub, int ySub, int xVel, int yVel,
            int routine, int mappingFrame, int animationTick, int animationIndex,
            boolean bounced, boolean facingRight, boolean laughIntroFinished) { }

    private static MotionState state(CutsceneKnucklesHcz2Instance knuckles) throws Exception {
        return new MotionState(knuckles.getX(), knuckles.getY(), getInt(knuckles, "xSub"),
                getInt(knuckles, "ySub"), getInt(knuckles, "xVel"), getInt(knuckles, "yVel"),
                getInt(knuckles, "routine"), getInt(knuckles, "mappingFrame"),
                getInt(knuckles, "animationTick"), getInt(knuckles, "animationIndex"),
                getBoolean(knuckles, "bounced"), getBoolean(knuckles, "facingRight"),
                getBoolean(knuckles, "laughIntroFinished"));
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = CutsceneKnucklesHcz2Instance.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static int getInt(Object target, String name) throws Exception {
        return field(name).getInt(target);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        return field(name).getBoolean(target);
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        field(name).setInt(target, value);
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        field(name).setBoolean(target, value);
    }
}
