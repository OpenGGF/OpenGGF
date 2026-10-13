package secondstrike.routes;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-backed authoring checks; gameplay/lifecycle evidence belongs to the native integration tests. */
public final class TestSecondStrikeRoutes {
    private static List<ObjectSpawn> forestObjects;
    private static List<ObjectSpawn> fireObjects;
    private static List<RingSpawn> forestRings;
    private static List<RingSpawn> fireRings;

    @BeforeAll static void loadOriginalPlacements() throws Exception {
        String path = System.getProperty("s3k.rom.path");
        assertNotNull(path, "Run with the original absolute -Ds3k.rom.path; do not create a ROM link");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        CRC32 crc = new CRC32();
        crc.update(bytes);
        assertEquals(0x63522553L, crc.getValue(), "Authoring indices require the locked-on World ROM");
        RomByteReader rom = new RomByteReader(bytes);
        Sonic3kObjectPlacement objects = new Sonic3kObjectPlacement(rom);
        Sonic3kRingPlacement rings = new Sonic3kRingPlacement(rom);
        forestObjects = objects.load(0, 0);
        fireObjects = objects.load(0, 1);
        forestRings = rings.load(0, 0);
        fireRings = rings.load(0, 1);
    }

    @Test void nativeForestBankKeepsAuthoredChoicesWithoutMovingRings() {
        assertEquals(104, forestRings.size());
        List<RingSpawn> selected = SecondStrikeRoutes.rings(forestRings, forestObjects);
        assertEquals(68, selected.size());
        for (RingSpawn ring : selected) assertSame(forestRings.stream()
                .filter(source -> source.placementId() == ring.placementId()).findFirst().orElseThrow(), ring);
        assertTrue(selected.stream().filter(ring -> ring.x() == 0x1EB0 && ring.y() == 0x4C4).count() == 1,
                "One native duplicate is deliberately retained as a recovery ring");
        assertThrows(UnsupportedOperationException.class, () -> selected.clear());
    }

    @Test void physicalActTwoKeepsLaterCampaignRoutesUntouched() {
        assertEquals(306, fireRings.size());
        List<RingSpawn> selected = SecondStrikeRoutes.rings(fireRings, fireObjects);
        assertEquals(45, selected.stream().filter(ring -> ring.x() < 0x1000).count());
        assertEquals(fireRings.stream().filter(ring -> ring.x() >= 0x1000).toList(),
                selected.stream().filter(ring -> ring.x() >= 0x1000).toList());
        assertEquals(selected, SecondStrikeRoutes.rings(selected, fireObjects),
                "Repeat native loads and prepared installs must not thin an already selected bank again");
    }

    @Test void unrecognisedLayoutsAndAddedRingIdentitiesArePreserved() {
        assertEquals(forestRings, SecondStrikeRoutes.rings(forestRings, List.of()));
        RingSpawn additional = new RingSpawn(0x2000, 0x220, 1000);
        List<RingSpawn> extended = java.util.stream.Stream.concat(forestRings.stream(),
                java.util.stream.Stream.of(additional)).toList();
        assertTrue(SecondStrikeRoutes.rings(extended, forestObjects).contains(additional));
    }

    @Test void nativeCheckpointSpecialEntryAndCharacterMechanicsAreNotEnemyBindings() {
        var forest = SecondStrikeRoutes.enemyBindings(forestObjects);
        assertEquals(10, forest.size());
        assertTrue(forest.stream().allMatch(binding -> List.of(0x8C, 0x8D, 0x8E)
                .contains(binding.spawn().objectId())));
        assertEquals(3, SecondStrikeRoutes.enemyBindings(fireObjects).size());
        assertTrue(SecondStrikeRoutes.enemyBindings(fireObjects).stream()
                .allMatch(binding -> binding.spawn().x() < 0x1000));
        assertEquals(List.of("sonic", "tails", "knuckles"),
                SecondStrikeRoutes.opportunities().stream().map(SecondStrikeRoutes.Opportunity::character).toList());
        // Retained native upper monitor #18 gives ten rings. With the eleven
        // remaining opening rings, the first physical post still has a possible
        // native twenty-ring bonus entry. This is a budget, not traversal proof.
        assertTrue(forestObjects.stream().anyMatch(spawn -> spawn.objectId() == 0x01
                && spawn.subtype() == 0x03 && spawn.x() == 0x1A90));
        assertEquals(11, SecondStrikeRoutes.rings(forestRings, forestObjects).stream()
                .filter(ring -> ring.x() < 0x1BC8).count());
    }
}
