package com.openggf.mods.code;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.game.sonic3k.difficulty.ClamerRecipe;
import com.openggf.game.sonic3k.difficulty.IczStalagtiteRecipe;
import com.openggf.game.sonic3k.difficulty.LbzAlarmRecipe;
import com.openggf.game.sonic3k.objects.ClamerObjectInstance;
import com.openggf.game.sonic3k.objects.IczStalagtiteObjectInstance;
import com.openggf.game.sonic3k.objects.LbzAlarmObjectInstance;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.AbstractLevel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * C1-east creator recipes over the original CNZ/ICZ/LBZ inventories. Binding by
 * id family is authored-intent evidence only; it is not a complete-zone proof.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeEastCampaign {
    @TempDir static Path temporary;
    static URLClassLoader loader;
    static RomByteReader rom;
    static final Set<String> CAMPAIGNS = Set.of("CarnivalNightCampaign", "IceCapCampaign", "LaunchBaseCampaign");

    @BeforeAll static void compile() throws Exception {
        String romPath = System.getProperty("s3k.rom.path");
        assertNotNull(romPath, "Use the original absolute S3K ROM; these checks must not skip");
        rom = new RomByteReader(Files.readAllBytes(Path.of(romPath)));
        loader = SecondStrikeSourceFixture.compile(temporary.resolve("classes"), SecondStrikeSourceFixture.MAIN);
    }
    @AfterAll static void close() throws Exception { if (loader != null) loader.close(); }

    private static ModRegistrationPlan registration(boolean withAizMod) throws Exception {
        var context = new ModContext("second-strike", "s3k", ModAssetRoot.forTests("second-strike"));
        if (withAizMod) {
            ((GgfMod) loader.loadClass("secondstrike.SecondStrikeMod").getConstructor().newInstance()).register(context);
        }
        for (String name : CAMPAIGNS) {
            loader.loadClass("secondstrike.campaign." + name).getMethod("register", ModContext.class)
                    .invoke(null, context);
        }
        return context.freeze();
    }

    private static GameModule services(ModRegistrationPlan plan) {
        return OwnedModServices.decorate(mock(GameModule.class, CALLS_REAL_METHODS), plan,
                new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                        ignored -> new ModStateSaveResult.Saved(), ignored -> { }));
    }

    private static final class NativeInventory extends AbstractLevel {
        NativeInventory(int zone, int act) {
            super(zone);
            objects = new Sonic3kObjectPlacement(rom).load(zone, act);
            rings = new Sonic3kRingPlacement(rom).load(zone, act);
        }
    }

    private static Set<Integer> ringIds(NativeInventory level) {
        return level.getRings().stream().map(RingSpawn::placementId).collect(Collectors.toSet());
    }

    private static long bound(NativeInventory level, String key) {
        return level.getObjects().stream().filter(s -> ("second-strike:" + key).equals(s.objectKey())).count();
    }

    private static Object recipeOf(Object owner) throws Exception {
        Field field = owner.getClass().getDeclaredField("recipe");
        field.setAccessible(true);
        return field.get(owner);
    }

    @Test void threeCampaignsRegisterTogetherWithTheAizModWithoutKeyCollisions() throws Exception {
        var plan = registration(true);
        // AIZ: six factories, two patches. East: clamer x2, monitor, stalactite, monitor, alarm, monitor.
        assertEquals(6 + 7, plan.objectFactories().size());
        assertEquals(2 + 6, plan.decodedLevelPatches().size());
    }

    @Test void carnivalNightBindsEveryOriginalClamerAndOnlyTheSecondOfEachAdjacentPairIsStaggered() throws Exception {
        var module = services(registration(false));
        var plan = registration(false);
        for (int act = 0; act < 2; act++) {
            var level = new NativeInventory(3, act);
            var original = level.getObjects();
            assertSame(level, module.transformDecodedLevel(level));
            var clamers = original.stream().filter(s -> s.objectId() == 0xA3).toList();
            assertEquals(act == 0 ? 16 : 11, clamers.size());
            long staggered = bound(level, "cnz-staggered-clamer");
            assertEquals(act == 0 ? 0 : 2, staggered);
            assertEquals(clamers.size(), staggered + bound(level, "cnz-cadence-clamer"));
            for (ObjectSpawn spawn : clamers) {
                var after = level.getObjects().get(spawn.layoutIndex());
                assertEquals(spawn.x(), after.x()); assertEquals(spawn.y(), after.y());
                assertEquals(spawn.renderFlags(), after.renderFlags()); assertEquals(spawn.subtype(), after.subtype());
                assertEquals(spawn.rawYWord(), after.rawYWord()); assertEquals(0xA3, after.objectId());
                var owner = plan.objectFactories().get(after.objectKey()).create(after, null);
                assertInstanceOf(ClamerObjectInstance.class, owner);
                boolean stagger = act == 1 && (spawn.layoutIndex() == 180 || spawn.layoutIndex() == 456);
                assertSame(stagger ? ClamerRecipe.STAGGERED_CADENCE : ClamerRecipe.CADENCE, recipeOf(owner));
            }
            assertEquals(act == 0 ? Set.of(77, 123, 308) : Set.of(12, 117, 239), ringIds(level));
            // Idempotent reload keeps the same bindings and rings.
            var objects = level.getObjects(); var rings = level.getRings();
            assertSame(level, module.transformDecodedLevel(level));
            assertEquals(objects, level.getObjects()); assertEquals(rings, level.getRings());
        }
    }

    @Test void iceCapBindsEveryOriginalStalactiteInActOneAndLeavesActTwoEnemiesNative() throws Exception {
        var plan = registration(false); var module = services(plan);
        var one = new NativeInventory(5, 0);
        assertSame(one, module.transformDecodedLevel(one));
        assertEquals(9, bound(one, "icz-warned-stalagtite"));
        for (ObjectSpawn s : one.getObjects()) {
            if (!"second-strike:icz-warned-stalagtite".equals(s.objectKey())) continue;
            assertEquals(0xB5, s.objectId());
            var owner = plan.objectFactories().get(s.objectKey()).create(s, null);
            assertInstanceOf(IczStalagtiteObjectInstance.class, owner);
            assertSame(IczStalagtiteRecipe.WARNED, recipeOf(owner));
        }
        assertEquals(Set.of(54, 135, 131), ringIds(one));
        var two = new NativeInventory(5, 1);
        var originalTwo = two.getObjects();
        assertSame(two, module.transformDecodedLevel(two));
        assertEquals(originalTwo.stream().map(s -> s.objectId()).toList(),
                two.getObjects().stream().map(s -> s.objectId()).toList());
        assertTrue(two.getObjects().stream().noneMatch(s -> s.ownerModId() != null && s.objectId() != 0x01),
                "ICZ2 has no Stalagtite record: only ordinary-monitor conversion is possible, and none is authored");
        assertEquals(Set.of(1, 7, 84, 164, 341), ringIds(two));
        var carried = two.getRings().stream().filter(r -> r.x() < 0x0800).map(RingSpawn::placementId)
                .collect(Collectors.toSet());
        var trueTwo = two.getRings().stream().filter(r -> r.x() >= 0x0930).map(RingSpawn::placementId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(1, 7), carried, "carried ICZ1 approach keeps its two original rings");
        assertEquals(Set.of(84, 164, 341), trueTwo, "true ICZ2 (x >= $930) keeps its own three");
        assertEquals(two.getRings().size(), carried.size() + trueTwo.size(), "nothing in the miniboss arena gap");
    }

    @Test void launchBaseBindsEveryOriginalAlarmInActOneOnly() throws Exception {
        var plan = registration(false); var module = services(plan);
        var one = new NativeInventory(6, 0);
        assertSame(one, module.transformDecodedLevel(one));
        assertEquals(9, bound(one, "lbz-bounded-alarm"));
        for (ObjectSpawn s : one.getObjects()) {
            if (!"second-strike:lbz-bounded-alarm".equals(s.objectKey())) continue;
            assertEquals(0x22, s.objectId());
            var owner = plan.objectFactories().get(s.objectKey()).create(s, null);
            assertInstanceOf(LbzAlarmObjectInstance.class, owner);
            assertSame(LbzAlarmRecipe.BOUNDED, recipeOf(owner));
        }
        assertEquals(Set.of(109, 255, 171, 293), ringIds(one));
        var two = new NativeInventory(6, 1);
        assertSame(two, module.transformDecodedLevel(two));
        assertEquals(0, bound(two, "lbz-bounded-alarm"));
        assertEquals(Set.of(61, 210, 362), ringIds(two));
    }

    @Test void monitorCurationNeverTouchesSpeedShoesHiddenOrBossRewardBoxes() throws Exception {
        var plan = registration(false); var module = services(plan);
        int converted = 0;
        for (int[] za : new int[][]{{3, 0}, {3, 1}, {5, 0}, {5, 1}, {6, 0}, {6, 1}}) {
            var level = new NativeInventory(za[0], za[1]);
            var original = level.getObjects();
            module.transformDecodedLevel(level);
            for (ObjectSpawn s : level.getObjects()) {
                if (s.objectKey() == null || !s.objectKey().endsWith("-robotnik-monitor")) continue;
                var nativeSpawn = original.get(s.layoutIndex());
                assertEquals(0x01, nativeSpawn.objectId(), "only ordinary monitors are converted");
                assertNotEquals(4, nativeSpawn.subtype() & 15, "never replace speed shoes");
                assertEquals(nativeSpawn.x(), s.x()); assertEquals(nativeSpawn.y(), s.y());
                converted++;
            }
            for (ObjectSpawn s : original) {
                if (s.objectId() == 0x80) {
                    assertNull(level.getObjects().get(s.layoutIndex()).objectKey(), "hidden reward boxes stay native");
                }
            }
        }
        assertTrue(converted > 0);
    }

    @Test void everyRingBudgetIsOneToFiveOriginalRecordsInOriginalOrder() throws Exception {
        var module = services(registration(false));
        for (int[] za : new int[][]{{3, 0}, {3, 1}, {5, 0}, {5, 1}, {6, 0}, {6, 1}}) {
            var level = new NativeInventory(za[0], za[1]);
            var original = level.getRings();
            module.transformDecodedLevel(level);
            int n = level.getRings().size();
            assertTrue(n >= 1 && n <= 5, "zone " + za[0] + " act " + (za[1] + 1) + " keeps " + n);
            int cursor = 0;
            for (RingSpawn kept : level.getRings()) {
                while (cursor < original.size() && original.get(cursor) != kept) cursor++;
                assertTrue(cursor < original.size(), "kept ring is an original record in original order");
                cursor++;
            }
        }
    }
}
