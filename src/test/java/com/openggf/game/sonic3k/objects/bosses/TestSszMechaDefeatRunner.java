package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszMechaDefeatRunner {
    @ParameterizedTest @ValueSource(ints = {0, 2, 4})
    void nativeSpawnMovementAndRetirementReplay(int subtype) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var manager = GameServices.level().getObjectManager();
        var camera = GameServices.camera();
        camera.setX((short) 0x240); camera.setY((short) 0x400);
        var runner = new SszMechaDefeatRunner(new ObjectSpawn(0x340, 0x4A0, 0, subtype, 0, false, 0));
        runner.setServices(TestEnvironment.objectServices()); manager.addDynamicObject(runner);
        runner.update(0, fixture.sprite());
        assertEquals(subtype == 0 ? 0x2E0 : 0x340, runner.getX()); assertEquals(0x4D0, runner.getY());
        assertEquals(1, manager.activeObjectsOfType(SszBossExplosionController.class).size());
        if (subtype == 0) {
            assertTrue(runner.isDestroyed(), "stationary burst deletes its creator immediately");
            var worker = manager.activeObjectsOfType(SszBossExplosionController.class).getFirst();
            worker.update(0, fixture.sprite()); worker.update(1, fixture.sprite());
            assertFalse(worker.isDestroyed(), "finite subtype$C does not poll creator liveness");
            return;
        }
        assertFalse(runner.isDestroyed());
        int direction = subtype == 2 ? -2 : 2;
        for (int i = 0; i < 5; i++) runner.update(i, fixture.sprite());
        assertEquals(0x340 + direction * 5, runner.getX());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                registry.restore(saved);
                runner = manager.activeObjectsOfType(SszMechaDefeatRunner.class).getFirst();
            }
            for (int i = 0; i < 160 && !runner.isDestroyed(); i++) runner.update(i, fixture.sprite());
            assertTrue(runner.isDestroyed());
            assertEquals(subtype == 2 ? 0x22E : 0x392, runner.getX(), "strict outside-boundary deletion");
        }
    }
}
