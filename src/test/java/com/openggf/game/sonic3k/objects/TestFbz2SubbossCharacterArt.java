package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestFbz2SubbossCharacterArt {
    @Test void onlyTheMainKnucklesBranchSelectsEggRobo() {
        assertEquals(Sonic3kObjectArtKeys.FBZ_ROBOTNIK_STAND,
                Fbz2SubbossCharacterChild.standArtKey(PlayerCharacter.SONIC_ALONE));
        assertEquals(Sonic3kObjectArtKeys.FBZ_ROBOTNIK_STAND,
                Fbz2SubbossCharacterChild.standArtKey(PlayerCharacter.TAILS_ALONE));
        assertEquals(Sonic3kObjectArtKeys.FBZ_EGGROBO_STAND,
                Fbz2SubbossCharacterChild.standArtKey(PlayerCharacter.KNUCKLES));
        assertEquals(Sonic3kObjectArtKeys.FBZ_EGGROBO_RUN,
                Fbz2SubbossCharacterChild.runArtKey(PlayerCharacter.KNUCKLES));
    }

    @Test void pilotStandingAndRunningRawScriptsUseNativeFramesAndDelays() {
        Fbz2SubbossInstance root = new Fbz2SubbossInstance(new com.openggf.level.objects.ObjectSpawn(
                0x2B40, 0x5F0, 0xAB, 0, 0, true, 417));
        Fbz2SubbossCharacterChild pilot = new Fbz2SubbossCharacterChild(root, PlayerCharacter.SONIC_ALONE);

        pilot.update(0, null);
        assertEquals(1, pilot.frameForTest(), "byte_703F4 first pair is frame 1, delay $17");
        for (int i = 0; i < 24; i++) pilot.update(i + 1, null);
        assertEquals(0, pilot.frameForTest());

        root.setControlBit(Fbz2SubbossInstance.CONTROL_CHARACTER_ESCAPE);
        pilot.update(25, null);
        assertEquals(0, pilot.frameForTest());
        for (int expected : new int[] {1, 2, 1, 0}) {
            for (int i = 0; i < 6; i++) pilot.update(26 + i, null);
            assertEquals(expected, pilot.frameForTest());
        }
    }

}
