package com.openggf.mods.code;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.objects.Sonic1AnimalsObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Uses each game's real native animal initializer as the oracle for the mod's two loaded species. */
class TestTypingHedgehogAnimals {
    @TempDir Path work;

    @Test @RequiresRom(SonicGame.SONIC_1)
    void greenHillSpeciesAndEveryPoseMatchNativeAnimals() throws Exception { check(SonicGame.SONIC_1); }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void emeraldHillSpeciesAndEveryPoseMatchNativeAnimals() throws Exception { check(SonicGame.SONIC_2); }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void angelIslandSpeciesAndEveryPoseMatchNativeAnimals() throws Exception { check(SonicGame.SONIC_3K); }

    private void check(SonicGame game) throws Exception {
        var level = SharedLevel.load(game, 0, 0);
        try (var harness = ExampleModHarness.build(Path.of("examples/typing-of-the-hedgehog"), work.resolve("mod"))) {
            var module = GameServices.module();
            SessionManager.clear();
            GameModuleRegistry.setCurrent(module);
            TestEnvironment.activeGameplayMode();
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
            fixture.stepIdleFrames(240);
            var services = GameServices.level().getObjectManager().getObjectServices();
            var frames = harness.loader().loadClass("typinghog.play.AnimalFrames");
            var visualType = harness.loader().loadClass("typinghog.play.Stagehand$Visual");
            var stagehand = harness.loader().loadClass("typinghog.play.Stagehand").getConstructor().newInstance();
            var add = stagehand.getClass().getMethod("add", String.class, int.class, int.class);
            var select = frames.getDeclaredMethod("select", visualType, int.class);
            var animate = frames.getDeclaredMethod("animate", visualType, int.class);
            select.setAccessible(true);
            animate.setAccessible(true);
            Set<Integer> seen = new HashSet<>();
            for (int attempt = 0; attempt < 64 && seen.size() < 2; attempt++) {
                var spawn = new ObjectSpawn(fixture.sprite().getCentreX(), fixture.sprite().getCentreY() - 32,
                        0, 0, 0, false, 0);
                AbstractObjectInstance nativeAnimal = ObjectConstructionContext.construct(services,
                        () -> game == SonicGame.SONIC_1 ? new Sonic1AnimalsObjectInstance(spawn)
                                : new AnimalObjectInstance(spawn, services));
                nativeAnimal.setServices(services);
                nativeAnimal.update(1, fixture.sprite());
                int variant = field(nativeAnimal, "artVariant").getInt(nativeAnimal);
                if (!seen.add(variant)) continue;
                Object visual = add.invoke(stagehand, "animal", 100, 100);
                select.invoke(null, visual, variant);
                var resolve = nativeAnimal.getClass().getDeclaredMethod(
                        game == SonicGame.SONIC_1 ? "resolveFrameIndex" : "getFrameIndex");
                resolve.setAccessible(true);
                for (int pose = 0; pose < 3; pose++) {
                    field(nativeAnimal, "animFrame").setInt(nativeAnimal, pose);
                    animate.invoke(null, visual, pose);
                    int chosen = visualType.getField("frame").getInt(visual);
                    assertEquals(resolve.invoke(nativeAnimal), chosen, game + " variant " + variant + " pose " + pose);
                    assertTrue(chosen < services.renderManager().getArtProvider().getSheet("animal").getFrameCount());
                }
            }
            assertEquals(Set.of(0, 1), seen, "both zone species were exercised");
            assertTrue(harness.findings().isEmpty(), harness.findings().toString());
        } finally {
            level.dispose();
        }
    }

    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
