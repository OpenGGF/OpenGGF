package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.tests.RomTestUtils;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestLbzCupElevatorInstance {

    @Test
    void registryRoutesS3klSlot18ToLbzCupElevator() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_LBZ);

        ObjectInstance elevator = registry.create(new ObjectSpawn(
                0x1800, 0x0600, Sonic3kObjectIds.LBZ_CUP_ELEVATOR, 0, 0, false, 0));

        assertFalse(elevator instanceof PlaceholderObjectInstance,
                "S3KL slot $18 is Obj_LBZCupElevator and must not remain a placeholder");
        assertEquals("LBZCupElevator", elevator.getName());
        assertInstanceOf(SolidObjectProvider.class, elevator,
                "Obj_LBZCupElevator calls SolidObjectFull2_1P while near upright");
    }

    @Test
    void registryRoutesS3klSlot19ToCupElevatorPole() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_LBZ);

        ObjectInstance pole = registry.create(new ObjectSpawn(
                0x1800, 0x0600, Sonic3kObjectIds.LBZ_CUP_ELEVATOR_POLE, 0x3F, 0, false, 0));

        assertFalse(pole instanceof PlaceholderObjectInstance,
                "S3KL slot $19 is Obj_LBZCupElevatorPole and must render the long pole variant");
        assertEquals("LBZCupElevatorPole", pole.getName());
        assertEquals(0x60, assertInstanceOf(AbstractObjectInstance.class, pole).getOnScreenHalfHeight(),
                "Pole subtype bits 0-5 select mapping_frame 4 and height_pixels=$60");
    }

    @Test
    void subtypeLowNibbleSelectsRomTravelDistanceInMultiplesOf60() {
        LbzCupElevatorInstance elevator = new LbzCupElevatorInstance(new ObjectSpawn(
                0x1800, 0x0600, Sonic3kObjectIds.LBZ_CUP_ELEVATOR, 0x03, 0, false, 0));

        assertEquals(0x120, elevator.getTravelDistanceForTest(),
                "Obj_LBZCupElevator computes (subtype & $F) * $60 into objoff_38");
        assertEquals(0x17C0, elevator.getX(),
                "Right-facing init shifts x_pos left by $40 after saving the anchor x in objoff_30");
        assertEquals(0x0600, elevator.getY());
        assertEquals(0x8080, elevator.getAngleForTest(),
                "Right-facing init sets angle word to $8080");
    }

    @Test
    void movingDownSubtypeStartsAtBottomAndUsesRoutine6() {
        LbzCupElevatorInstance elevator = new LbzCupElevatorInstance(new ObjectSpawn(
                0x1800, 0x0600, Sonic3kObjectIds.LBZ_CUP_ELEVATOR, 0x31, 1, false, 0));

        assertEquals(0x60, elevator.getTravelDistanceForTest());
        assertEquals(0x60, elevator.getTravelProgressForTest());
        assertEquals(0x0660, elevator.getAnchorYForTest(),
                "Subtypes with bits 5-4 set start at the lower anchor: objoff_32 += travel distance");
        assertEquals(6, elevator.getRoutineForTest(),
                "ROM stores routine selector $36=6 for a downward-starting elevator");
        assertEquals(0x80, elevator.getAngleForTest() & 0xFF,
                "Odd subtype bit 0 adds $80 to angle+1 for the starting phase");
    }

    @Test
    void lbzPlanIncludesCupElevatorLevelArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(Sonic3kZoneIds.ZONE_LBZ, 0);

        Sonic3kPlcArtRegistry.LevelArtEntry elevator = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.LBZ_CUP_ELEVATOR))
                .findFirst().orElse(null);

        assertNotNull(elevator, "Obj_LBZCupElevator uses resident LBZ misc art");
        assertEquals(Sonic3kConstants.MAP_LBZ_CUP_ELEVATOR_ADDR, elevator.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_LBZ_MISC + 0x4A, elevator.artTileBase());
        assertEquals(2, elevator.palette());
    }

    @Test
    void lbzCupElevatorMappingsMatchRomShape() throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 3K ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 3K ROM");
            RomByteReader reader = RomByteReader.fromRom(rom);
            var frames = S3kSpriteDataLoader.loadMappingFrames(reader,
                    Sonic3kConstants.MAP_LBZ_CUP_ELEVATOR_ADDR);

            assertEquals(5, frames.size(), "Map_LBZCupElevator has cup, attach, base, short pole, long pole");
            assertEquals(2, frames.get(0).pieces().size());
            assertEquals(1, frames.get(1).pieces().size());
            assertEquals(2, frames.get(2).pieces().size());
            assertEquals(3, frames.get(3).pieces().size());
            assertEquals(6, frames.get(4).pieces().size());
        }
    }

    @Test
    void attachChildUsesRomFrameAndPositionFormula() {
        int anchorX = 0x1800;

        assertEquals(1, LbzCupElevatorInstance.ATTACH_MAPPING_FRAME,
                "Obj_LBZCupElevatorAttach initializes mapping_frame(a1)=1");
        assertEquals(0x1818, LbzCupElevatorInstance.attachXForTest(anchorX, true, 0),
                "Obj_LBZCupElevatorAttach caches original x_pos in $30 before the +/-$18 spawn offset");
        assertEquals(0x17E8, LbzCupElevatorInstance.attachXForTest(anchorX, false, 0x80),
                "Runtime attach x_pos ignores orientation and uses original x + ((cos(angle)*3)>>5)");
    }

    @Test
    void heldPlayerUsesRomCupTwistFrameAndClearsItOnRelease() throws Exception {
        LbzCupElevatorInstance elevator = new LbzCupElevatorInstance(new ObjectSpawn(
                0x1800, 0x0600, Sonic3kObjectIds.LBZ_CUP_ELEVATOR, 0, 0, false, 0));
        setPrivateInt(elevator, "angleWord", 0x4000);
        Sonic player = new Sonic("sonic", (short) 0x1800, (short) 0x0600);

        invokeHoldPlayer(elevator, player);

        assertEquals(0x5B, player.getMappingFrame(),
                "loc_32610 maps angle $40 through PlayerTwistFrames index 3");
        assertFalse(player.getRenderHFlip(),
                "loc_32610 uses PlayerTwistFlip index 3 = no horizontal flip");
        assertTrue(player.isObjectMappingFrameControl(),
                "object_control=3 suppresses normal player animation while the cup writes raw mapping frames");

        Object p1State = getPrivateField(elevator, "p1");
        invokeReleasePlayer(elevator, player, p1State);

        assertFalse(player.isObjectMappingFrameControl(),
                "raw mapping-frame control must reset when the player leaves the cup");
    }

    private static final class ZoneForTestRegistry extends Sonic3kObjectRegistry {
        private final int zoneId;

        private ZoneForTestRegistry(int zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        protected int currentRomZoneId() {
            return zoneId;
        }
    }

    private static void setPrivateInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invokeHoldPlayer(LbzCupElevatorInstance elevator, Sonic player) throws Exception {
        Method method = LbzCupElevatorInstance.class.getDeclaredMethod("holdPlayer",
                com.openggf.sprites.playable.AbstractPlayableSprite.class);
        method.setAccessible(true);
        method.invoke(elevator, player);
    }

    private static void invokeReleasePlayer(LbzCupElevatorInstance elevator, Sonic player, Object state)
            throws Exception {
        Class<?> stateClass = Class.forName("com.openggf.game.sonic3k.objects.LbzCupElevatorInstance$PlayerState");
        Method method = LbzCupElevatorInstance.class.getDeclaredMethod("releasePlayer",
                com.openggf.sprites.playable.AbstractPlayableSprite.class,
                stateClass,
                int.class,
                boolean.class);
        method.setAccessible(true);
        method.invoke(elevator, player, state, 0, true);
    }
}
