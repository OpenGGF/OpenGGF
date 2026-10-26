package com.openggf.game.sonic3k.competition;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionItemObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kCompetitionDispatch {
    @Test void unsignedNativeZoneByteSelectsBothDisjointS3klRanges() {
        for (int zone = 0; zone <= 0xFF; zone++) {
            S3kZoneSet expected = zone < 7 || zone >= 0xE && zone <= 0x15
                    ? S3kZoneSet.S3KL : S3kZoneSet.SKL;
            assertEquals(expected, S3kZoneSet.forZone(zone), "zone $" + Integer.toHexString(zone));
        }
        assertEquals(S3kZoneSet.S3KL, S3kZoneSet.forZone(0x10E), "Current_zone is a byte");
        assertEquals(S3kZoneSet.SKL, S3kZoneSet.forZone(-1));
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void everyRealArenaPlacementResolvesToItsNativeMiniatureConsumer() throws Exception {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        int[] addresses = {0x1FC560, 0x1FC5A2, 0x1FC60E, 0x1FC686, 0x1FC6F2};
        int[] counts = {8, 15, 17, 15, 15};
        for (int arena = 0; arena < addresses.length; arena++) {
            var registry = new ArenaRegistry(0xE + arena);
            int base = addresses[arena];
            for (int i = 0; i < counts[arena]; i++) {
                int address = base + i * 6;
                int rawY = rom.readU16BE(address + 2);
                int id = rom.readU8(address + 4);
                var spawn = new ObjectSpawn(rom.readU16BE(address), rawY & 0xFFF,
                        id, rom.readU8(address + 5), (rawY >>> 13) & 3,
                        (rawY & 0x8000) != 0, rawY, i);
                var object = registry.create(spawn);
                String identity = "arena $" + Integer.toHexString(0xE + arena) + " object " + i + " id $" + Integer.toHexString(id);
                if (id == 0x64) assertInstanceOf(CompetitionItemObjectInstance.class, object, identity);
                else if (id == 0x65) assertInstanceOf(CompetitionGoalObjectInstance.class, object, identity);
                else assertInstanceOf(CompetitionArenaObjectInstance.class, object, identity);
            }
            assertEquals(0xFFFF, rom.readU16BE(base + counts[arena] * 6), "native placement terminator");
        }
    }

    @Test void campaignIdentitiesAndNonArenaConsumersRemainScoped() {
        var campaign = new ArenaRegistry(0);
        var deathEgg = new ArenaRegistry(0xB);
        var arena = new ArenaRegistry(0xE);
        for (int id : new int[]{2, 5, 7, 0x28, 0x33, 0x38, 0x4A, 0x4F}) {
            var spawn = new ObjectSpawn(0x440, 0x188, id, id == 5 ? 0x31 : 0, 0, false, 0);
            assertFalse(campaign.create(spawn) instanceof CompetitionArenaObjectInstance, "campaign $" + Integer.toHexString(id));
            assertFalse(deathEgg.create(spawn) instanceof CompetitionArenaObjectInstance, "SKL $" + Integer.toHexString(id));
            assertInstanceOf(CompetitionArenaObjectInstance.class, arena.create(spawn));
        }
        for (int id = 0x5D; id <= 0x66; id++) {
            var object = deathEgg.create(new ObjectSpawn(0, 0, id, 0, 0, false, 0));
            assertFalse(object instanceof CompetitionArenaObjectInstance, "SKL $" + Integer.toHexString(id));
            assertFalse(object instanceof CompetitionGoalObjectInstance);
            assertFalse(object instanceof CompetitionItemObjectInstance);
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void dispatchComparisonImmediatesComeFromLockedOnNativeCode() throws Exception {
        var rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        // loc_1B6A8: three unsigned byte comparisons select exactly $16, $E and 7.
        assertEquals(0x16, rom.readU16BE(0x1B6B4));
        assertEquals(0xE, rom.readU16BE(0x1B6BA));
        assertEquals(7, rom.readU16BE(0x1B6C0));
        assertEquals(0x952A2, rom.readU32BE(0x1B6AA)); // Sprite_ListingK
        assertEquals(0x94EA2, rom.readU32BE(0x1B6C6)); // Sprite_Listing3
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void arenaBootReadsNativeStartAndUpfrontPlacementPointersForBothHumanSlots() throws Exception {
        int[] startsX = {0x430, 0x440, 0x758, 0x454, 0x430};
        int[] startsY = {0x194, 0x264, 0x144, 0xB4, 0x194};
        int[] counts = {8, 15, 17, 15, 15};
        var rom = RomManager.getInstance().getRom();
        for (int index = 0; index < 5; index++) {
            var definition = S3kCompetitionArenaDefinition.load(rom, S3kCompetitionSession.Arena.forZone(0xE + index));
            assertEquals(startsX[index], definition.startX()); assertEquals(startsY[index], definition.startY());
            assertEquals(counts[index], definition.placements().size()); assertEquals(0x12C0, definition.cameraMaxX());
            var pair = definition.createPlayers(rom, java.util.List.of(S3kCompetitionPlayer.Character.TAILS, S3kCompetitionPlayer.Character.KNUCKLES));
            assertEquals(pair.get(0).centreX(), pair.get(1).centreX());
            assertEquals(pair.get(0).centreY(), pair.get(1).centreY());
            assertEquals(0x4C0, pair.get(0).speeds().maxSpeed()); assertEquals(0x580, pair.get(1).speeds().maxSpeed());
            assertEquals(index == 4, pair.get(1).highPriority());
        }
    }

    private static final class ArenaRegistry extends Sonic3kObjectRegistry {
        private final int zone;
        private ArenaRegistry(int zone) { this.zone = zone; }
        @Override protected int currentRomZoneId() { return zone; }
    }
}
