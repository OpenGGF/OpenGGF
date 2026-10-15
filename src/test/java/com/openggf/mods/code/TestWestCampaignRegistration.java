package com.openggf.mods.code;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorFactory;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.AbstractLevel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

/** Static creator registration/original-ROM bindings; no packed-owner service or route certification. */
@RequiresRom(SonicGame.SONIC_3K)
class TestWestCampaignRegistration {
    @TempDir static Path temporary;
    static URLClassLoader loader;
    static RomByteReader rom;
    @BeforeAll static void compileCreatorsAndReadOriginal() throws Exception {
        String path = System.getProperty("s3k.rom.path");
        assertNotNull(path, "Use the existing absolute original ROM path");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        CRC32 crc = new CRC32(); crc.update(bytes); assertEquals(0x63522553L, crc.getValue());
        rom = new RomByteReader(bytes);
        loader = SecondStrikeSourceFixture.compile(temporary.resolve("classes"),
                SecondStrikeSourceFixture.MAIN.resolve("secondstrike/campaign/HydrocityCampaign.java"),
                SecondStrikeSourceFixture.MAIN.resolve("secondstrike/campaign/MarbleGardenCampaign.java"));
    }
    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }
    private static ModRegistrationPlan registration() throws Exception {
        var context = new ModContext("second-strike", "s3k", ModAssetRoot.forTests("second-strike"));
        context.registerObject("robotnik-monitor", (spawn, registry) -> Sonic3kMonitorFactory.eggman(spawn));
        for (String type : List.of("HydrocityCampaign", "MarbleGardenCampaign"))
            loader.loadClass("secondstrike.campaign." + type).getMethod("register", ModContext.class).invoke(null, context);
        return context.freeze();
    }
    private static GameModule module(ModRegistrationPlan plan) {
        return OwnedModServices.decorate(new Sonic3kGameModule(), plan, new ModFaultBoundary(
                Map.of(), new ModRuntimeFindingStore(), ignored -> new ModStateSaveResult.Saved(), ignored -> { }));
    }
    private static final class Inventory extends AbstractLevel {
        Inventory(int zone, int act) throws Exception { this(zone, act, true); }
        Inventory(int zone, int act, boolean recognized) throws Exception {
            super(zone); objects = new Sonic3kObjectPlacement(rom).load(zone, act);
            rings = new Sonic3kRingPlacement(rom).load(zone, act);
            if (!recognized) objects = objects.stream().filter(s -> s.objectId() != 0x99 && s.objectId() != 0x9F).toList();
        }
    }
    @Test void bothActsKeepExactNativeRecordsAndCuratedRings() throws Exception {
        var plan = registration(); var module = module(plan);
        for (int zone : new int[]{1, 2}) for (int act : new int[]{0, 1}) {
            var level = new Inventory(zone, act); var source = level.getObjects(); var rings = level.getRings();
            assertSame(level, module.transformDecodedLevel(level));
            assertEquals(source.size(), level.getObjects().size()); assertEquals(5, level.getRings().size());
            Set<Integer> retained = switch (zone * 2 + act) {
                case 2 -> Set.of(1,109,145,217,433);
                case 3 -> Set.of(1,64,127,232,253);
                case 4 -> Set.of(1,85,169,337,505);
                case 5 -> Set.of(1,88,146,233,349);
                default -> throw new AssertionError();
            };
            assertEquals(retained, level.getRings().stream().map(RingSpawn::placementId).collect(Collectors.toSet()));
            for (RingSpawn ring : level.getRings()) assertSame(rings.stream()
                    .filter(original -> original.placementId() == ring.placementId()).findFirst().orElseThrow(), ring);
            for (ObjectSpawn result : level.getObjects()) {
                ObjectSpawn original = source.get(result.layoutIndex());
                assertEquals(original.layoutIndex(), result.layoutIndex());
                assertEquals(original.x(), result.x()); assertEquals(original.y(), result.y());
                assertEquals(original.objectId(), result.objectId()); assertEquals(original.subtype(), result.subtype());
                assertEquals(original.renderFlags(), result.renderFlags()); assertEquals(original.rawYWord(), result.rawYWord());
                assertEquals(original.respawnTracked(), result.respawnTracked());
                if (result.objectKey() != null) assertTrue(plan.objectFactories().containsKey(result.objectKey()));
                if (result.objectId() == 0x54 || result.objectId() == 0x34 || result.objectId() == 0x85
                        || result.objectId() == 0x5B || result.objectId() == 0x5C)
                    assertSame(original, result, "air, checkpoint, giant-ring and top owners remain native");
                if (result.objectId() == 1 && (result.subtype() & 15) == 4)
                    assertSame(original, result, "speed shoes remain a visible native choice");
                if (result.objectId() == 0x80 || result.objectId() == 0xA2 || result.objectId() == 0x0F
                        && ((result.subtype() & 0x80) != 0 || zone == 2 && (result.subtype() & 0xF0) == 0x20))
                    assertSame(original, result, "boss rewards and trigger/stomp supports stay native");
                if (result.objectId() == 1 && (result.subtype() & 15) != 4)
                    assertEquals("second-strike:robotnik-monitor", result.objectKey());
                if (result.ownerModId() == null) assertSame(original, result);
            }
            Map<String,Long> bound = level.getObjects().stream().filter(s -> s.objectKey() != null)
                    .collect(Collectors.groupingBy(s -> s.objectKey().substring("second-strike:".length()), Collectors.counting()));
            Map<String,Long> expected = switch (zone * 2 + act) {
                case 2 -> Map.of("hcz-brittle",2L,"hcz-alert",4L,"hcz-miniboss",1L,"robotnik-monitor",22L);
                case 3 -> Map.of("hcz-alert",8L,"hcz-endboss",2L,"robotnik-monitor",21L);
                case 4 -> Map.of("mgz-brittle",5L,"mgz-spiker",4L,"mgz-miniboss",1L,"robotnik-monitor",29L);
                case 5 -> Map.of("mgz-brittle",5L,"mgz-spiker",4L,"mgz-mantis",6L,"robotnik-monitor",18L);
                default -> throw new AssertionError();
            };
            assertEquals(expected, bound, "every authored recipe must bind its inspected original records");
            var admittedObjects = level.getObjects(); var admittedRings = level.getRings();
            assertSame(level, module.transformDecodedLevel(level));
            assertEquals(admittedObjects, level.getObjects()); assertEquals(admittedRings, level.getRings());
        }
    }
    @Test void unknownNativeBanksKeepTheirOriginalInventoryAndResources() throws Exception {
        for (int zone : new int[]{1,2}) {
            var level = new Inventory(zone, 0, false); var objects = level.getObjects(); var rings = level.getRings();
            assertSame(level, module(registration()).transformDecodedLevel(level));
            assertEquals(objects, level.getObjects()); assertEquals(rings, level.getRings());
        }
    }
    @Test void immutableBossRecipeIsAuthoredOnceWithoutClaimingPackedHostAdmission() throws Exception {
        var plan = registration();
        assertEquals(Set.of("mgz-encounter"), plan.serviceBundles().keySet());
        var services = plan.serviceBundles().get("mgz-encounter").get().services();
        assertEquals(Map.of(com.openggf.game.sonic3k.difficulty.MarbleGardenBossRecipe.class,
                com.openggf.game.sonic3k.difficulty.MarbleGardenBossRecipe.COUNTER_CADENCE), services);
    }
    @Test void otherZonesKeepTheirOriginalInventoryAndResources() throws Exception {
        var level = new Inventory(0, 0); var objects = level.getObjects(); var rings = level.getRings();
        assertSame(level, module(registration()).transformDecodedLevel(level));
        assertEquals(objects, level.getObjects()); assertEquals(rings, level.getRings());
    }
}
