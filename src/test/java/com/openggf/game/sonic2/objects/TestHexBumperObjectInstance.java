package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHexBumperObjectInstance {

    @Test
    void movingBumperUsesRangeBoundsForOutOfRangeDelete() {
        HexBumperObjectInstance bumper = new HexBumperObjectInstance(
                new ObjectSpawn(0x1FF8, 0x028C, 0xD7, 0x01, 0, false, 0x828C),
                "HexBumper");

        assertTrue(bumper.usesCustomOutOfRangeCheck());
        assertFalse(bumper.isCustomOutOfRange(0x1F80),
                "ROM ObjD7 keeps moving bumpers alive while either objoff_30 or objoff_32 is in range");
        assertTrue(bumper.isCustomOutOfRange(0x2500),
                "ROM ObjD7 deletes only after both horizontal movement bounds leave the camera window");
    }

    @Test
    void stationaryBumperKeepsSharedOutOfRangePath() {
        HexBumperObjectInstance bumper = new HexBumperObjectInstance(
                new ObjectSpawn(0x2034, 0x04F2, 0xD7, 0x00, 0, false, 0x84F2),
                "HexBumper");

        assertFalse(bumper.usesCustomOutOfRangeCheck());
    }
}
