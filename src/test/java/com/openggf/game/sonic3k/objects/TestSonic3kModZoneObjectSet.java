package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic3kModZoneObjectSet {

    @Test
    void zoneIdBoundMhzAndLbzFactoriesRejectCustomZones() {
        InspectableRegistry registry = new InspectableRegistry();

        assertFalse(registry.canCreateInCustomZone(S3kZoneSet.SKL,
                Sonic3kObjectIds.MHZ_MUSHROOM_PLATFORM));
        assertFalse(registry.canCreateInCustomZone(S3kZoneSet.S3KL,
                Sonic3kObjectIds.LBZ_PIPE_PLUG));
        assertTrue(registry.inheritedFactorySubstrateContains(
                Sonic3kObjectIds.MHZ_MUSHROOM_PLATFORM));
        assertTrue(registry.inheritedFactorySubstrateContains(
                Sonic3kObjectIds.LBZ_PIPE_PLUG));
    }

    @Test
    void setOnlyFactoryRemainsAvailableToCustomZones() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        assertTrue(registry.canCreateInCustomZone(S3kZoneSet.S3KL,
                Sonic3kObjectIds.MONITOR));
        assertTrue(registry.canCreateInCustomZone(S3kZoneSet.SKL,
                Sonic3kObjectIds.MONITOR));
    }

    @Test
    void everyCurrentRomZoneDependentFactoryIsExplicitlyInventoried() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        assertEquals(Set.of(
                Sonic3kObjectIds.AIZ_END_BOSS,
                Sonic3kObjectIds.AIZ_GIANT_RIDE_VINE,
                Sonic3kObjectIds.AIZ_HOLLOW_TREE,
                Sonic3kObjectIds.AIZ_MINIBOSS,
                Sonic3kObjectIds.AIZ_RIDE_VINE,
                Sonic3kObjectIds.AIZ1_TREE,
                Sonic3kObjectIds.AIZ1_ZIPLINE_PEG,
                Sonic3kObjectIds.BUMPER,
                Sonic3kObjectIds.CNZ_TRIANGLE_BUMPER,
                Sonic3kObjectIds.CUTSCENE_BUTTON,
                Sonic3kObjectIds.JAWZ,
                Sonic3kObjectIds.LBZ_END_BOSS,
                Sonic3kObjectIds.LBZ_FINAL_BOSS_1,
                Sonic3kObjectIds.LBZ_FINAL_BOSS_2,
                Sonic3kObjectIds.LBZ_GATE_LASER,
                Sonic3kObjectIds.LBZ_KNUX_PILLAR,
                Sonic3kObjectIds.LBZ_LOWERING_GRAPPLE,
                Sonic3kObjectIds.LBZ_MINIBOSS,
                Sonic3kObjectIds.LBZ_MINIBOSS_BOX,
                Sonic3kObjectIds.LBZ_MINIBOSS_BOX_KNUX,
                Sonic3kObjectIds.LBZ_PIPE_PLUG,
                Sonic3kObjectIds.LBZ_SPIN_LAUNCHER,
                Sonic3kObjectIds.LBZ_TUBE_ELEVATOR,
                Sonic3kObjectIds.LBZ1_ROBOTNIK,
                Sonic3kObjectIds.LBZ2_ROBOTNIK_SHIP,
                Sonic3kObjectIds.MHZ_MUSHROOM_CAP,
                Sonic3kObjectIds.MHZ_MUSHROOM_CATAPULT,
                Sonic3kObjectIds.MHZ_MUSHROOM_PARACHUTE,
                Sonic3kObjectIds.MHZ_MUSHROOM_PLATFORM,
                Sonic3kObjectIds.MHZ_SWING_BAR_HORIZONTAL,
                Sonic3kObjectIds.PACHINKO_ENERGY_TRAP,
                Sonic3kObjectIds.PACHINKO_FLIPPER,
                Sonic3kObjectIds.PACHINKO_ITEM_ORB,
                Sonic3kObjectIds.PACHINKO_MAGNET_ORB,
                Sonic3kObjectIds.PACHINKO_PLATFORM,
                Sonic3kObjectIds.PACHINKO_TRIANGLE_BUMPER,
                Sonic3kObjectIds.UPDRAFT),
                registry.stockZoneBoundFactoryIds());
    }

    @Test
    void sourceBranchesReadingRomZoneIdCannotSilentlyUseSetOnlyRegistration() throws Exception {
        Path source = Path.of("src/main/java/com/openggf/game/sonic3k/objects/"
                + "Sonic3kObjectRegistry.java");
        Pattern registration = Pattern.compile(
                "(?:factories\\.put|registerStockZoneBound)\\(Sonic3kObjectIds\\.([A-Z0-9_]+),");
        Set<Integer> sourceDependentIds = new HashSet<>();
        String activeConstant = null;
        for (String line : Files.readAllLines(source)) {
            var matcher = registration.matcher(line);
            if (matcher.find()) {
                activeConstant = matcher.group(1);
            } else if (line.contains("factories.forEach")) {
                activeConstant = null;
            }
            if (activeConstant != null && line.contains("currentRomZoneId()")) {
                sourceDependentIds.add(Sonic3kObjectIds.class
                        .getField(activeConstant).getInt(null));
            }
        }

        assertEquals(new Sonic3kObjectRegistry().stockZoneBoundFactoryIds(),
                sourceDependentIds);
    }

    private static final class InspectableRegistry extends Sonic3kObjectRegistry {
        boolean inheritedFactorySubstrateContains(int objectId) {
            ensureLoaded();
            return factories.containsKey(objectId);
        }
    }
}
