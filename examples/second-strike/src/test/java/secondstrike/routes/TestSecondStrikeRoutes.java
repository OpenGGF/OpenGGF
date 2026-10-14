package secondstrike.routes;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.AbstractLevel;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.testing.ModTestKit;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-backed authoring checks; gameplay/lifecycle evidence belongs to the native integration tests. */
public final class TestSecondStrikeRoutes {
    @TempDir Path temporary;
    private static byte[] originalRomBytes;
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
        originalRomBytes = bytes;
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
        assertEquals(3, selected.size());
        for (RingSpawn ring : selected) assertSame(forestRings.stream()
                .filter(source -> source.placementId() == ring.placementId()).findFirst().orElseThrow(), ring);
        assertEquals(List.of(1, 35, 74), selected.stream().map(RingSpawn::placementId).toList());
        assertEquals(selected, SecondStrikeRoutes.rings(selected, forestObjects));
        assertThrows(UnsupportedOperationException.class, () -> selected.clear());
    }

    @Test void physicalActTwoKeepsLaterCampaignRoutesUntouched() {
        assertEquals(306, fireRings.size());
        List<RingSpawn> selected = SecondStrikeRoutes.rings(fireRings, fireObjects);
        assertEquals(List.of(12, 68), selected.stream().filter(ring -> ring.x() < 0x1000).map(RingSpawn::placementId).toList());
        assertEquals(fireRings.stream().filter(ring -> ring.x() >= 0x1000).toList(),
                selected.stream().filter(ring -> ring.x() >= 0x1000).toList());
        assertEquals(selected, SecondStrikeRoutes.rings(selected, fireObjects),
                "Repeat native loads and prepared installs must not thin an already selected bank again");
    }

    @Test void unrecognisedLayoutsPassThroughButExtraOpeningRingsCannotEvadeTheBudget() {
        assertEquals(forestRings, SecondStrikeRoutes.rings(forestRings, List.of()));
        RingSpawn additional = new RingSpawn(0x2000, 0x220, 1000);
        List<RingSpawn> extended = java.util.stream.Stream.concat(forestRings.stream(),
                java.util.stream.Stream.of(additional)).toList();
        assertFalse(SecondStrikeRoutes.rings(extended, forestObjects).contains(additional));
        assertEquals(3, SecondStrikeRoutes.rings(extended, forestObjects).size());
    }

    @Test void nativeCheckpointSpecialEntryAndCharacterMechanicsAreNotEnemyBindings() throws Exception {
        try (var kit = ModTestKit.openTrusted(Path.of("target"), temporary.resolve("saves"),
                new LogicalRomResolver(() -> originalRomBytes), SonicConfigurationService.createStandalone(), List.of())) {
        var plan = kit.plan("second-strike");
        var module = kit.launch(new Sonic3kGameModule(), new GameplayLaunchRequest("s3k", "sonic", List.of()));
        assertEquals(6, plan.objectFactories().size());
        for (boolean fire : List.of(false, true)) {
            var originals = fire ? fireObjects : forestObjects;
            var inventory = new NativeInventory(originals, fire ? fireRings : forestRings);
            assertSame(inventory, module.transformDecodedLevel(inventory));
            var bound = inventory.getObjects().stream().filter(spawn -> spawn.ownerModId() != null
                    && !"second-strike:robotnik-monitor".equals(spawn.objectKey())).toList();
            assertEquals(fire ? 6 : 10, bound.size());
            for (var spawn : bound) {
                var original = originals.get(spawn.layoutIndex());
                assertEquals(original.objectId(), spawn.objectId()); assertEquals(original.subtype(), spawn.subtype());
                assertEquals(original.x(), spawn.x()); assertEquals(original.y(), spawn.y());
                assertEquals(original.renderFlags(), spawn.renderFlags()); assertEquals(original.rawYWord(), spawn.rawYWord());
                assertEquals(original.respawnTracked(), spawn.respawnTracked());
                assertTrue(List.of(0x8C, 0x8D, 0x8E, 0x8F).contains(spawn.objectId()));
                assertTrue(plan.objectFactories().containsKey(spawn.objectKey()));
                if (fire) assertTrue(spawn.x() < 0x1000);
            }
            if (fire) {
                assertEquals(List.of(82, 100, 119), bound.stream().filter(s -> "second-strike:caterkiller".equals(s.objectKey()))
                        .map(ObjectSpawn::layoutIndex).toList());
                assertEquals(originals.stream().filter(s -> s.x() >= 0x1000).toList(),
                        inventory.getObjects().stream().filter(s -> s.x() >= 0x1000).toList());
            }
        }
        assertEquals(List.of("sonic", "tails", "knuckles"),
                SecondStrikeRoutes.opportunities().stream().map(SecondStrikeRoutes.Opportunity::character).toList());
        assertEquals(5, SecondStrikeRoutes.rings(forestRings, forestObjects).size()
                + SecondStrikeRoutes.rings(fireRings, fireObjects).stream().filter(ring -> ring.x() < 0x1000).count());
        assertTrue(forestObjects.stream().anyMatch(spawn -> spawn.objectId() == 0x01
                && spawn.subtype() == 0x04 && spawn.x() == 0x1790), "Original shoes stay available");
        assertTrue(kit.disabledOwners().isEmpty(), kit.findings()::toString);
        }
    }

    private static final class NativeInventory extends AbstractLevel {
        NativeInventory(List<ObjectSpawn> placements, List<RingSpawn> ringPlacements) {
            super(0); objects = placements; rings = ringPlacements;
        }
    }
}
