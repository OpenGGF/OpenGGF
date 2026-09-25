package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszDeathEggCutscene {
    @Test void cloudTrackingAdmitsOnlyOneOwnerAndReleasesBeforeDeferredDeletion() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        var manager = GameServices.level().getObjectManager();
        var spawn = new ObjectSpawn(0x200, 0xC35, 0, 2, 0, false, 0);
        var cloud = new SszDeathEggChild(spawn);
        cloud.setServices(services); manager.addDynamicObject(cloud); cloud.update(1, null);
        var duplicate = new SszDeathEggChild(spawn);
        duplicate.setServices(services); manager.addDynamicObject(duplicate); duplicate.update(1, null);
        assertTrue(duplicate.isDestroyed());
        for (int pass = 2; pass <= 786; pass++) cloud.update(pass, null);
        assertFalse(cloud.isDestroyed());
        var replacement = new SszDeathEggChild(spawn);
        replacement.setServices(services); manager.addDynamicObject(replacement); replacement.update(786, null);
        assertFalse(replacement.isDestroyed(), "Remove_From_TrackingSlot precedes slot deletion by one pass");
    }

    @Test void initialChildrenHaveNativeOffsetsTimersAndIndependentLifetimes() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        var services = TestEnvironment.objectServices();
        var manager = GameServices.level().getObjectManager();
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) services.zoneRuntimeState();
        state.setBackgroundCameraDelta(0); state.setCloudOscillator(0);
        var egg = new SszDeathEggSmallObjectInstance(new ObjectSpawn(0x200, 0xC68, 0, 0, 0, false, 0));
        egg.setServices(services); manager.addDynamicObject(egg); egg.update(1, null);
        var children = manager.activeObjectsOfType(SszDeathEggChild.class);
        assertEquals(7, children.size());
        var mask = children.get(0); var cloud = children.get(1);
        assertEquals(0xC68, mask.getY()); assertEquals(0xC35, cloud.getY());
        for (var child : children) child.update(1, null);
        assertEquals(0x1E0, children.get(2).getX());
        assertEquals(egg.getY() + 0x1D, children.get(2).getY());
        assertEquals(4, mask.getPriorityBucket()); assertEquals(5, cloud.getPriorityBucket());
        assertEquals(6, children.get(2).getPriorityBucket());
        for (int pass = 2; pass <= 400; pass++) for (var child : children) if (!child.isDestroyed()) child.update(pass, null);
        assertEquals(0xC35, cloud.getY(), "cloud waits $190+1 passes before drifting");
        assertTrue(children.subList(2, 7).stream().noneMatch(SszDeathEggChild::isDestroyed));
        for (int pass = 401; pass <= 416; pass++) for (var child : children) if (!child.isDestroyed()) child.update(pass, null);
        assertTrue(children.subList(2, 7).stream().allMatch(SszDeathEggChild::isDestroyed));
        assertEquals(0xC36, cloud.getY(), "sixteen $10/256-pixel moves accumulate one pixel");
        egg.setDestroyed(true);
        mask.update(417, null);
        assertFalse(mask.isDestroyed(), "mask does not inherit parent lifetime");
        state.setCutsceneFlag(2); mask.update(418, null);
        assertFalse(mask.isDestroyed()); mask.update(419, null); assertTrue(mask.isDestroyed());
        for (int pass = 417; pass <= 785; pass++) cloud.update(pass, null);
        assertFalse(cloud.isDestroyed());
        cloud.update(786, null); assertFalse(cloud.isDestroyed(), "deletion pass still draws the cloud");
        cloud.update(787, null); assertTrue(cloud.isDestroyed());
    }

    @Test void productionCutsceneGraphRecreatesAndReplaysEveryRegistryKey() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var manager = GameServices.level().getObjectManager();
        int passes = 0;
        while (manager.activeObjectsOfType(SszDeathEggChild.class).isEmpty() && passes++ < 600) fixture.stepIdleFrames(1);
        assertEquals(7, manager.activeObjectsOfType(SszDeathEggChild.class).size());
        var registry = fixture.gameplayMode().getRewindRegistry();
        for (int advance : new int[] {30, 315}) {
            fixture.stepIdleFrames(advance);
            var saved = registry.capture();
            fixture.stepIdleFrames(45);
            var forward = registry.capture();
            for (var child : manager.activeObjectsOfType(SszDeathEggChild.class)) child.setDestroyed(true);
            for (var egg : manager.activeObjectsOfType(SszDeathEggSmallObjectInstance.class)) egg.setDestroyed(true);
            fixture.stepIdleFrames(1);
            registry.restore(saved);
            fixture.stepIdleFrames(45);
            var replay = registry.capture();
            for (String key : forward.entries().keySet()) {
                var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                assertTrue(differences.isEmpty(), key + ": " + differences);
            }
        }
    }

    @Test void missileNegativeScatterAndInclusiveVerticalCullMatchRom() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        var camera = GameServices.camera();
        camera.setX((short) 0x100); camera.setY((short) 0xC00);
        for (int outside = 0; outside <= 1; outside++) {
            services.rng().setSeed(2); // return 82: dx=-14, x velocity -1 pixel/pass.
            var missile = new SszDeathEggMissile(new ObjectSpawn(0x200, 0xC68, 0, 0, 0, false, 0));
            missile.setServices(services);
            missile.update(32, null);
            assertEquals(0x1F1, missile.getX());
            // Next pass reaches C69. The unsigned Y distance may equal $200.
            camera.setY((short) (0xC69 - 0x180 - outside));
            missile.update(33, null);
            assertFalse(missile.isDestroyed(), "Go_Delete_Sprite defers freeing the slot");
            camera.setY((short) 0xC00);
            missile.update(34, null);
            assertEquals(outside != 0, missile.isDestroyed());
        }
    }

    @Test void missileUsesOneRandomDrawAndReplaysAfterDeletion() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2); // establish production player auxiliaries before the rewind boundary.
        var services = TestEnvironment.objectServices();
        GameServices.camera().setX((short) 0x100);
        GameServices.camera().setY((short) 0xC00);
        services.rng().setSeed(1); // Random_Number returns 41: dx=9, dy=0, velocity +$100.
        var manager = GameServices.level().getObjectManager();
        var missile = new SszDeathEggMissile(new ObjectSpawn(0x200, 0xC68, 0, 0, 0, false, 0));
        missile.setServices(services); manager.addDynamicObject(missile);
        missile.update(32, null);
        assertEquals(0x20A, missile.getX());
        assertEquals(0xC68, missile.getY(), "first movement is +$F000 in 16.16 coordinates");
        assertEquals(5, missile.getPriorityBucket());
        assertFalse(missile.isHighPriority());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int i = 1; i <= 20; i++) missile.update(32 + i, null);
        var forward = registry.capture();
        missile.setDestroyed(true);
        registry.restore(saved);
        missile = manager.activeObjectsOfType(SszDeathEggMissile.class).getFirst();
        for (int i = 1; i <= 20; i++) missile.update(32 + i, null);
        var replay = registry.capture();
        for (String key : forward.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
            assertTrue(differences.isEmpty(), key + ": " + differences);
        }
        assertEquals(0x00290029L, services.rng().getSeed(), "movement never reseeds or draws another random value");
    }

    @Test void firingUsesVintCadenceAndCopiesPreMovementPosition() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        var manager = GameServices.level().getObjectManager();
        GameServices.camera().setY((short) 0xB00);
        var egg = new SszDeathEggSmallObjectInstance(new ObjectSpawn(0x200, 0xC68, 0, 0, 0, false, 0));
        egg.setServices(services); manager.addDynamicObject(egg);
        for (int i = 0; i <= 256; i++) egg.update(i, null);
        assertTrue(egg.firingForTest());
        assertTrue(manager.activeObjectsOfType(SszDeathEggMissile.class).isEmpty(),
                "timer transition does not fall through to firing even on a multiple of 32");
        egg.update(287, null);
        assertTrue(manager.activeObjectsOfType(SszDeathEggMissile.class).isEmpty());
        int x = egg.getX(), y = egg.getY();
        egg.update(288, null);
        var missile = manager.activeObjectsOfType(SszDeathEggMissile.class).getFirst();
        assertEquals(x, missile.getX()); assertEquals(y, missile.getY());
        assertEquals(0, services.rng().getSeed(), "allocation alone does not run missile initialization");
        // Camera subtraction wraps at the top of the level: BLS is unsigned.
        GameServices.camera().setY((short) 0);
        egg.update(289, null);
        assertTrue(egg.isDestroyed());
    }

    @Test void initUsesFullVintSeedAndNonzeroRomPaletteThenRestoresExactBackupAfterRecreation() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        var services = TestEnvironment.objectServices();
        int[] original = colors();
        var manager = GameServices.level().getObjectManager();
        var egg = new SszDeathEggSmallObjectInstance(new ObjectSpawn(0x200, 0xC68, 0, 0, 0, false, 0));
        egg.setServices(services); manager.addDynamicObject(egg);
        var ownership = services.paletteOwnershipRegistryOrNull();
        ownership.beginFrame();
        egg.update(0x89ABCDEF, fixture.sprite());
        assertEquals(0x89ABCDEFL, services.rng().getSeed());
        for (int i = 0; i < 16; i++) {
            int word = services.rom().read16BitAddr(0x669B2 + i * 2) & 0xFFFF;
            assertEquals(word == 0 ? original[i] : word, colors()[i], "palette color " + i);
        }
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        egg.setDestroyed(true);
        registry.restore(saved);
        var restored = registry.capture();
        for (String key : saved.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, saved.get(key), restored.get(key)).isEmpty(), key);
        }
        egg = manager.activeObjectsOfType(SszDeathEggSmallObjectInstance.class).getFirst();
        long seed = services.rng().getSeed();
        GameServices.camera().setY((short) 0xF00);
        for (int frame = 1; frame < 258; frame++) {
            ownership.beginFrame(); egg.update(frame, fixture.sprite());
        }
        assertTrue(egg.isDestroyed(), "loc_65A4A restores after the $100 countdown and rise gate");
        assertArrayEquals(original, colors(), "restore the backed-up normal line, including original zero-patch cells");
        assertEquals(seed, services.rng().getSeed(), "initialization reseeds only once, including after rewind");
    }

    private static int[] colors() {
        int[] result = new int[16];
        for (int i = 0; i < 16; i++) result[i] = PaletteWriteSupport.segaWordFromColor(
                GameServices.level().getCurrentLevel().getPalette(3).getColor(i));
        return result;
    }
}
