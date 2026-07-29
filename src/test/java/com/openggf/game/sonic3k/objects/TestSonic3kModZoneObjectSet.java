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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                Sonic3kObjectIds.FBZ_SPIDER_CRANE);

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
    void fbzRuntimeStateConsumersRemainStockZoneBound() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        Set<Integer> runtimeStateBoundIds = Set.of(
                Sonic3kObjectIds.FBZ_MAGNETIC_SPIKE_BALL,
                Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM,
                Sonic3kObjectIds.FBZ_MAGNETIC_PENDULUM);

        assertTrue(registry.stockZoneBoundFactoryIds().containsAll(runtimeStateBoundIds));
        for (int objectId : runtimeStateBoundIds) {
            assertFalse(registry.canCreateInCustomZone(S3kZoneSet.S3KL, objectId));
            assertFalse(registry.canCreateInCustomZone(S3kZoneSet.SKL, objectId));
        }
    }

    @Test
    void stockFbzCreatesRuntimeStateBoundMagneticFactories() {
        Sonic3kObjectRegistry registry = new LevelBackedRegistry(stockLevel(
                com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_FBZ));

        assertInstanceOf(FbzMagneticSpikeBallObjectInstance.class,
                registry.create(spawn(Sonic3kObjectIds.FBZ_MAGNETIC_SPIKE_BALL)));
        assertInstanceOf(FbzMagneticPlatformObjectInstance.class,
                registry.create(spawn(Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM)));
        assertInstanceOf(FbzMagneticPendulumObjectInstance.class,
                registry.create(spawn(Sonic3kObjectIds.FBZ_MAGNETIC_PENDULUM)));
    }

    @Test
    void stockSklDoesNotCreateFbzMagneticFactoriesForRemappedIds() {
        Sonic3kObjectRegistry registry = new LevelBackedRegistry(stockLevel(
                com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_MHZ));

        assertInstanceOf(com.openggf.level.objects.PlaceholderObjectInstance.class,
                registry.create(spawn(Sonic3kObjectIds.FBZ_MAGNETIC_SPIKE_BALL)));
        assertInstanceOf(com.openggf.level.objects.PlaceholderObjectInstance.class,
                registry.create(spawn(Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM)));
        assertInstanceOf(com.openggf.level.objects.PlaceholderObjectInstance.class,
                registry.create(spawn(Sonic3kObjectIds.FBZ_MAGNETIC_PENDULUM)));
    }

    @Test
    void nonFbzStockS3klDoesNotCreateRuntimeStateBoundMagneticFactories() {
        Sonic3kObjectRegistry registry = new LevelBackedRegistry(stockLevel(
                com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_AIZ));

        assertInstanceOf(com.openggf.level.objects.PlaceholderObjectInstance.class,
                registry.create(spawn(Sonic3kObjectIds.FBZ_MAGNETIC_SPIKE_BALL)));
        assertInstanceOf(com.openggf.level.objects.PlaceholderObjectInstance.class,
                registry.create(spawn(Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM)));
        assertInstanceOf(com.openggf.level.objects.PlaceholderObjectInstance.class,
                registry.create(spawn(Sonic3kObjectIds.FBZ_MAGNETIC_PENDULUM)));
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
    void customCreationContextWinsBeforeLegacyRomZoneFallback() {
        com.openggf.game.sonic3k.Sonic3kLevel custom =
                mock(com.openggf.game.sonic3k.Sonic3kLevel.class);
        when(custom.hasStockRomZoneIdentity()).thenReturn(false);
        when(custom.getObjectZoneSet()).thenReturn(S3kZoneSet.S3KL);

        assertInstanceOf(LbzPlayerLauncherInstance.class,
                new LevelBackedRegistry(custom).create(
                        new com.openggf.level.objects.ObjectSpawn(
                                10, 20, Sonic3kObjectIds.LBZ_PLAYER_LAUNCHER,
                                0, 0, false, 1)));
    }

    @Test
    void everyStockZoneDependentFactoryIsExplicitlyInventoried() {
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
                Sonic3kObjectIds.FBZ_MAGNETIC_PENDULUM,
                Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM,
                Sonic3kObjectIds.FBZ_MAGNETIC_SPIKE_BALL,
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
                Sonic3kObjectIds.HPZ_MASTER_EMERALD,
                Sonic3kObjectIds.HPZ_SUPER_EMERALD,
                Sonic3kObjectIds.HPZ_SS_ENTRY_CONTROL,
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
    void customCompatibleFactoriesCannotReadStockZoneIdentity() throws Exception {
        Path source = Path.of("src/main/java/com/openggf/game/sonic3k/objects/"
                + "Sonic3kObjectRegistry.java");
        Set<Integer> violations = customCompatibleStockZoneReadIds(Files.readAllLines(source));

        assertEquals(Set.of(), violations);
    }

    @Test
    void sourceAuditAttributesCurrentRomZoneReadToCustomCompatibleFactory() throws Exception {
        Set<Integer> violations = customCompatibleStockZoneReadIds(List.of(
                "registerStockZoneBound(Sonic3kObjectIds.AIZ_HOLLOW_TREE,",
                "        (spawn, registry) -> new AizHollowTreeObjectInstance(spawn));",
                "registerZoneSetBound(Sonic3kObjectIds.FBZ_WIRE_CAGE, S3kZoneSet.S3KL,",
                "        (spawn, registry) -> currentRomZoneId() == 4",
                "                ? new FbzWireCageObjectInstance(spawn) : null);",
                "factories.forEach(this::registerSetOnly);"));

        assertEquals(Set.of(Sonic3kObjectIds.FBZ_WIRE_CAGE), violations);
    }

    @Test
    void sourceAuditFollowsHelperIndirectStockZoneRead() throws Exception {
        Set<Integer> violations = customCompatibleStockZoneReadIds(List.of(
                "factories.put(Sonic3kObjectIds.FBZ_WIRE_CAGE,",
                "        (spawn, registry) -> isFbzS3kl()",
                "                ? new FbzWireCageObjectInstance(spawn) : null);",
                "factories.forEach(this::registerSetOnly);",
                "private boolean isFbzS3kl() {",
                "    return getCurrentZoneSet() == S3kZoneSet.S3KL",
                "            && currentRomZoneId() == Sonic3kZoneIds.ZONE_FBZ;",
                "}"));

        assertEquals(Set.of(Sonic3kObjectIds.FBZ_WIRE_CAGE), violations);
    }

    private static Set<Integer> customCompatibleStockZoneReadIds(List<String> sourceLines) throws Exception {
        Set<String> stockZoneReadingHelpers = stockZoneReadingHelpers(sourceLines);
        Pattern registration = Pattern.compile(
                "(factories\\.put|registerStockZoneBound|registerStockRomZoneBound|registerZoneSetBound)"
                        + "\\(Sonic3kObjectIds\\.([A-Z0-9_]+),");
        Set<Integer> violations = new HashSet<>();
        String activeConstant = null;
        boolean activeCustomCompatible = false;
        for (String line : sourceLines) {
            var matcher = registration.matcher(line);
            if (matcher.find()) {
                activeCustomCompatible = matcher.group(1).equals("factories.put")
                        || matcher.group(1).equals("registerZoneSetBound");
                activeConstant = matcher.group(2);
            } else if (line.contains("factories.forEach")) {
                activeConstant = null;
            }
            boolean readsStockZone = line.contains("currentRomZoneId()")
                    || stockZoneReadingHelpers.stream().anyMatch(helper ->
                            Pattern.compile("\\b" + Pattern.quote(helper) + "\\s*\\(")
                                    .matcher(line).find());
            if (activeCustomCompatible && activeConstant != null && readsStockZone) {
                violations.add(Sonic3kObjectIds.class
                        .getField(activeConstant).getInt(null));
            }
        }
        return violations;
    }

    private static Set<String> stockZoneReadingHelpers(List<String> sourceLines) {
        Pattern methodStart = Pattern.compile(
                "^\\s*(?:private|protected|public)\\s+(?:static\\s+)?"
                        + "[\\w<>, ?\\[\\]]+\\s+(\\w+)\\s*\\([^;]*\\)\\s*\\{\\s*$");
        Map<String, String> methodBodies = new LinkedHashMap<>();
        for (int index = 0; index < sourceLines.size(); index++) {
            var matcher = methodStart.matcher(sourceLines.get(index));
            if (!matcher.matches()) continue;
            StringBuilder body = new StringBuilder();
            int depth = 0;
            for (; index < sourceLines.size(); index++) {
                String line = sourceLines.get(index);
                body.append(line).append('\n');
                depth += count(line, '{') - count(line, '}');
                if (depth == 0) break;
            }
            methodBodies.put(matcher.group(1), body.toString());
        }

        Set<String> readers = new HashSet<>();
        methodBodies.forEach((name, body) -> {
            if (body.contains("currentRomZoneId()")) readers.add(name);
        });
        // These context resolvers consult the ROM zone only after checking the
        // active creation context. Custom factory execution always installs that
        // context first, so callers receive the declared pointer-table identity.
        readers.removeAll(Set.of(
                "currentCreationContext", "currentRomZoneId", "getCurrentZoneSet"));
        boolean changed;
        do {
            changed = false;
            for (var method : methodBodies.entrySet()) {
                if (readers.contains(method.getKey())) continue;
                for (String reader : readers) {
                    if (Pattern.compile("\\b" + Pattern.quote(reader) + "\\s*\\(")
                            .matcher(method.getValue()).find()) {
                        changed = readers.add(method.getKey());
                        break;
                    }
                }
            }
        } while (changed);
        return readers;
    }

    private static int count(String value, char target) {
        int matches = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == target) matches++;
        }
        return matches;
    }

    private static com.openggf.game.sonic3k.Sonic3kLevel stockLevel(int zoneId) {
        com.openggf.game.sonic3k.Sonic3kLevel level =
                mock(com.openggf.game.sonic3k.Sonic3kLevel.class);
        when(level.hasStockRomZoneIdentity()).thenReturn(true);
        when(level.getObjectZoneSet()).thenReturn(S3kZoneSet.forZone(zoneId));
        when(level.getZoneIndex()).thenReturn(zoneId);
        return level;
    }

    private static com.openggf.level.objects.ObjectSpawn spawn(int objectId) {
        return new com.openggf.level.objects.ObjectSpawn(
                10, 20, objectId, 0, 0, false, 1);
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
