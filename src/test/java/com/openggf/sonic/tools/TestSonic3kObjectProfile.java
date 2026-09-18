package com.openggf.sonic.tools;

import org.junit.jupiter.api.Test;
import com.openggf.level.LevelData;
import com.openggf.tools.ObjectDiscoveryTool.LevelConfig;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.tools.Sonic3kObjectProfile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSonic3kObjectProfile {

    @Test
    public void aizMinibossIsMarkedImplementedForS3klLevelsOnly() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        List<LevelConfig> levels = profile.getLevels();

        LevelConfig aiz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_ANGEL_ISLAND_1)
                .findFirst()
                .orElseThrow();
        LevelConfig mhz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_MUSHROOM_HILL_1)
                .findFirst()
                .orElseThrow();

        assertTrue(profile.getImplementedIds().contains(0x91));
        assertTrue(profile.getImplementedIds(aiz1).contains(0x91));
        // Slot $91 is Obj_MHZMinibossTree in SK Set 2: implemented for MHZ under that
        // name, but not as the AIZ miniboss, and absent from every other SKL zone.
        LevelConfig soz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_SANDOPOLIS_1)
                .findFirst()
                .orElseThrow();
        assertEquals("MHZMinibossTree", new Sonic3kObjectRegistry().getPrimaryName(0x91, S3kZoneSet.SKL));
        assertTrue(profile.getImplementedIds(mhz1).contains(0x91));
        assertFalse(profile.getImplementedIds(soz1).contains(0x91));
    }

    @Test
    public void hczMinibossIsMarkedImplementedForS3klLevelsOnly() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        List<LevelConfig> levels = profile.getLevels();

        LevelConfig hcz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_HYDROCITY_1)
                .findFirst()
                .orElseThrow();
        LevelConfig mhz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_MUSHROOM_HILL_1)
                .findFirst()
                .orElseThrow();

        assertTrue(profile.getImplementedIds().contains(0x99));
        assertTrue(profile.getImplementedIds(hcz1).contains(0x99));
        assertFalse(profile.getImplementedIds(mhz1).contains(0x99));
    }

    @Test
    public void iczSegmentColumnIsMarkedImplementedForS3klLevelsOnly() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        List<LevelConfig> levels = profile.getLevels();

        LevelConfig icz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_ICECAP_1)
                .findFirst()
                .orElseThrow();
        LevelConfig mhz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_MUSHROOM_HILL_1)
                .findFirst()
                .orElseThrow();

        assertTrue(profile.getImplementedIds().contains(0xB3));
        assertTrue(profile.getImplementedIds(icz1).contains(0xB3));
        assertFalse(profile.getImplementedIds(mhz1).contains(0xB3));
    }

    @Test
    public void cnzPlacedActorsKeepPointerTableImplementationBoundaries() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        List<LevelConfig> levels = profile.getLevels();

        LevelConfig cnz2 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_CARNIVAL_NIGHT_2)
                .findFirst()
                .orElseThrow();
        LevelConfig mhz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_MUSHROOM_HILL_1)
                .findFirst()
                .orElseThrow();

        int[] implementedCnzIds = {
                0x41, 0x43, 0x47, 0x48,
                0x4A, 0x4B, 0x4C, 0x4D, 0x4E,
                0x82, 0x83, 0x88, 0x89,
                0xA3, 0xA4, 0xA5, 0xA6, 0xA7
        };
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        var sklOwners = java.util.Map.of(0x41, "SOZLightSwitch", 0x43, "SOZSwingingPlatform",
                0x47, "SOZSandCork", 0x48, "SOZRapelWire");
        for (int objectId : implementedCnzIds) {
            assertTrue(profile.getImplementedIds(cnz2).contains(objectId),
                    "CNZ object $" + Integer.toHexString(objectId) + " should be reported as implemented");
            boolean sameOwnerInBothSets = registry.getPrimaryName(objectId, S3kZoneSet.S3KL)
                    .equals(registry.getPrimaryName(objectId, S3kZoneSet.SKL));
            if (sameOwnerInBothSets) {
                // CutsceneKnuckles, CutsceneButton and the CNZ water-level pair keep one
                // owner in both pointer tables, so they are legitimately shared.
                assertTrue(Sonic3kObjectProfile.SHARED_IMPLEMENTED_IDS.contains(objectId),
                        "object $" + Integer.toHexString(objectId) + " has one owner in both sets");
            } else if (sklOwners.containsKey(objectId)) {
                assertEquals(sklOwners.get(objectId), registry.getPrimaryName(objectId, S3kZoneSet.SKL));
                assertTrue(profile.getImplementedIds(mhz1).contains(objectId),
                        "implemented SOZ owner shares the numeric slot with CNZ");
            } else {
                assertFalse(profile.getImplementedIds(mhz1).contains(objectId),
                        "CNZ object $" + Integer.toHexString(objectId) + " must stay out of the SKL set");
            }
        }
    }

    @Test
    public void newSozSlotsReportBothImplementedOwners() {
        var registry=new Sonic3kObjectRegistry();
        assertEquals("SOZLoopFallthrough",registry.getPrimaryName(0x3B,S3kZoneSet.SKL));
        assertEquals("SOZSolidSprites",registry.getPrimaryName(0x49,S3kZoneSet.SKL));
        assertEquals("HCZWaterWall",registry.getPrimaryName(0x3B,S3kZoneSet.S3KL));
        assertEquals("CNZGiantWheel",registry.getPrimaryName(0x49,S3kZoneSet.S3KL));
        assertTrue(Sonic3kObjectProfile.SHARED_IMPLEMENTED_IDS.containsAll(java.util.List.of(0x3B,0x49)));
    }

    @Test
    public void slot44ReportsBothImplementedOwners() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        assertEquals("CNZTrapDoor", registry.getPrimaryName(0x44, S3kZoneSet.S3KL));
        assertEquals("SOZBreakableSandRock", registry.getPrimaryName(0x44, S3kZoneSet.SKL));
        assertTrue(Sonic3kObjectProfile.SHARED_IMPLEMENTED_IDS.contains(0x44));
        for (LevelData levelData : List.of(LevelData.S3K_CARNIVAL_NIGHT_1,
                LevelData.S3K_CARNIVAL_NIGHT_2, LevelData.S3K_SANDOPOLIS_1,
                LevelData.S3K_SANDOPOLIS_2)) {
            LevelConfig level = profile.getLevels().stream()
                    .filter(candidate -> candidate.levelData() == levelData)
                    .findFirst().orElseThrow();
            assertTrue(profile.getImplementedIds(level).contains(0x44),
                    levelData + " must report its own implemented $44 actor");
        }
    }

    @Test
    public void lbz2EndSequenceActorsAreMarkedImplementedForS3klLevelsOnly() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        List<LevelConfig> levels = profile.getLevels();

        LevelConfig lbz2 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_LAUNCH_BASE_2)
                .findFirst()
                .orElseThrow();
        LevelConfig mhz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_MUSHROOM_HILL_1)
                .findFirst()
                .orElseThrow();

        int[] implementedLbz2Ids = {0xC6, 0xC8, 0xCA, 0xCB};
        for (int objectId : implementedLbz2Ids) {
            assertTrue(profile.getImplementedIds(lbz2).contains(objectId),
                    "LBZ2 object $" + Integer.toHexString(objectId) + " should be reported as implemented");
            assertFalse(profile.getImplementedIds(mhz1).contains(objectId),
                    "LBZ2 object $" + Integer.toHexString(objectId) + " must stay out of the SKL set");
        }
    }
}
