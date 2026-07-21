package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SingletonResetExtension.class)
@FullReset
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
    void implementedFbzFactoriesFollowS3klPointerTableInCustomZones() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        Set<Integer> fbzOnlyIds = Set.of(
                Sonic3kObjectIds.FBZ_WIRE_CAGE,
                Sonic3kObjectIds.FBZ_WIRE_CAGE_STATIONARY,
                Sonic3kObjectIds.FBZ_FLOATING_PLATFORM,
                Sonic3kObjectIds.FBZ_CHAIN_LINK,
                Sonic3kObjectIds.FBZ_MAGNETIC_SPIKE_BALL,
                Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM,
                Sonic3kObjectIds.FBZ_SNAKE_PLATFORM,
                Sonic3kObjectIds.FBZ_BENT_PIPE,
                Sonic3kObjectIds.FBZ_ROTATING_PLATFORM,
                Sonic3kObjectIds.FBZ_DISAPPEARING_PLATFORM,
                Sonic3kObjectIds.FBZ_SCREW_DOOR,
                Sonic3kObjectIds.FBZ_SPINNING_POLE,
                Sonic3kObjectIds.FBZ_PROPELLER,
                Sonic3kObjectIds.FBZ_PISTON,
                Sonic3kObjectIds.FBZ_PLATFORM_BLOCKS,
                Sonic3kObjectIds.FBZ_MISSILE_LAUNCHER,
                Sonic3kObjectIds.FBZ_WALL_MISSILE,
                Sonic3kObjectIds.FBZ_MINE,
                Sonic3kObjectIds.FBZ_ELEVATOR,
                Sonic3kObjectIds.FBZ_TRAP_SPRING,
                Sonic3kObjectIds.FBZ_FLAMETHROWER,
                Sonic3kObjectIds.FBZ_SPIDER_CRANE,
                Sonic3kObjectIds.FBZ_MAGNETIC_PENDULUM);

        for (int objectId : fbzOnlyIds) {
            assertTrue(registry.canCreateInCustomZone(S3kZoneSet.S3KL, objectId),
                    () -> "S3KL factory missing for " + registry.getPrimaryName(objectId, S3kZoneSet.S3KL));
            assertFalse(registry.canCreateInCustomZone(S3kZoneSet.SKL, objectId),
                    () -> "SKL must retain " + registry.getPrimaryName(objectId, S3kZoneSet.SKL));
        }
    }

    @Test
    void sharedFbzDezLauncherFollowsBothPointerTablesInCustomZones() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        assertEquals("FBZDEZPlayerLauncher", registry.getPrimaryName(
                Sonic3kObjectIds.FBZ_DEZ_PLAYER_LAUNCHER, S3kZoneSet.S3KL));
        assertEquals("FBZDEZPlayerLauncher", registry.getPrimaryName(
                Sonic3kObjectIds.FBZ_DEZ_PLAYER_LAUNCHER, S3kZoneSet.SKL));
        assertTrue(registry.canCreateInCustomZone(S3kZoneSet.S3KL,
                Sonic3kObjectIds.FBZ_DEZ_PLAYER_LAUNCHER));
        assertTrue(registry.canCreateInCustomZone(S3kZoneSet.SKL,
                Sonic3kObjectIds.FBZ_DEZ_PLAYER_LAUNCHER));
    }

    @Test
    void bareRegistryPreservesLegacyFallbackForStockZoneBoundFactory() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        var spawn = new com.openggf.level.objects.ObjectSpawn(
                0x11F0, 0x289, Sonic3kObjectIds.AIZ_MINIBOSS,
                0, 0, false, 0);

        assertInstanceOf(AizMinibossInstance.class, registry.create(spawn));
    }

    @Test
    void mutableStockSnapshotPreservesRealRomZoneIdentity() {
        com.openggf.game.sonic3k.Sonic3kLevel stock =
                mock(com.openggf.game.sonic3k.Sonic3kLevel.class);
        when(stock.hasStockRomZoneIdentity()).thenReturn(true);
        when(stock.getObjectZoneSet()).thenReturn(S3kZoneSet.SKL);
        when(stock.getZoneIndex()).thenReturn(
                com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_MHZ);
        for (int line = 0; line < 4; line++) {
            when(stock.getPalette(line)).thenReturn(new com.openggf.level.Palette());
        }
        when(stock.getMap()).thenReturn(new com.openggf.level.Map(1, 1, 1, new byte[1]));
        when(stock.getObjects()).thenReturn(java.util.List.of());
        when(stock.getRings()).thenReturn(java.util.List.of());
        com.openggf.level.MutableLevel snapshot = com.openggf.level.MutableLevel.snapshot(stock);

        assertSame(stock, com.openggf.level.LevelOrigin.original(snapshot));
        assertInstanceOf(MhzMushroomPlatformObjectInstance.class,
                new LevelBackedRegistry(snapshot).create(
                        new com.openggf.level.objects.ObjectSpawn(
                                10, 20, Sonic3kObjectIds.MHZ_MUSHROOM_PLATFORM,
                                0, 0, false, 1)));
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
    void stockZoneDependencyInventoryRemainsExplicitFactoryMetadata() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        for (int objectId : registry.stockZoneBoundFactoryIds()) {
            assertFalse(registry.canCreateInCustomZone(S3kZoneSet.S3KL, objectId));
            assertFalse(registry.canCreateInCustomZone(S3kZoneSet.SKL, objectId));
        }
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

    private static final class LevelBackedRegistry extends Sonic3kObjectRegistry {
        private final com.openggf.level.Level level;

        private LevelBackedRegistry(com.openggf.level.Level level) {
            this.level = level;
        }

        @Override
        protected com.openggf.level.Level currentLevel() {
            return level;
        }
    }
}
