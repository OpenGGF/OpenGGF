package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R51 independent native-player deletion contract, SOURCE candidate only.
 * Uses an unmodified ROM placement, the real registry/allocator and SolidObjectTop.
 * No movie/aux/native pose input, private state seeding, assigned slot, direct unload,
 * CPU/latch/cache/timer setter or replacement controller is used.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestHczHandLauncherNativeUnload {
    @Test
    void ordinaryRangeDeletionRetainsNativePlayersAcrossBirthAndHeldWorldRecreation() {
        var fixture = fixture();
        var objects = GameServices.level().getObjectManager();
        var spawn = unadmittedRomHand(fixture);
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforeBirth = registry.capture();
        var first = captureAndWarm(fixture, spawn);
        var firstArm = arm(first);
        var held = registry.capture();
        var heldPlayers = players(fixture).stream().map(Player::read).toList();
        var firstDeleted = cull(fixture, spawn);
        var afterCull = registry.capture();

        for (int replay = 0; replay < 2; replay++) {
            // Replay the actual placement birth, including the arm child.
            registry.restore(beforeBirth);
            assertWorld(beforeBirth, registry.capture());
            assertNull(objects.getActiveObjectForRewind(spawn));
            var bornAgain = captureAndWarm(fixture, spawn);
            assertNotSame(first, bornAgain, "ordinary ROM placement must construct a new parent");
            var bornAgainArm = arm(bornAgain);
            assertNotSame(firstArm, bornAgainArm, "ordinary placement must construct its real arm child");
            assertWorld(held, registry.capture());
            assertEquals(heldPlayers, players(fixture).stream().map(Player::read).toList());
            assertEquals(firstDeleted, cull(fixture, spawn));
            assertWorld(afterCull, registry.capture());

            // Independently force recreation of the held parent/child and real rides.
            registry.restore(held);
            var restored = hand(spawn);
            assertNotSame(bornAgain, restored, "force parent recreation, not in-place rewind");
            assertNotSame(bornAgainArm, arm(restored), "force recreation and actual child-parent relink");
            assertWorld(held, registry.capture());
            assertHeld(fixture, restored);
            assertEquals(firstDeleted, cull(fixture, spawn));
            assertWorld(afterCull, registry.capture());
        }

        // Native loc_30C76 -> Sprite_OnScreen_Test -> Delete_Referenced_Sprite
        // zeros ONLY this SST (sonic3k.asm:65914,37302,36148). No native player
        // control/status/interact write occurs. Keep these native oracles AFTER
        // both forced birth and held-world recreation/forward cycles.
        assertAll("both native players after every forced-world control",
                () -> assertNativeDeletionRetains(1, heldPlayers.get(0), firstDeleted.players().get(0)),
                () -> assertNativeDeletionRetains(2, heldPlayers.get(1), firstDeleted.players().get(1)));
        // Deliberately no post-deletion restore or following-CPU-marker oracle:
        // retired Java ride refs and any newly loaded SST replacement require
        // separate source qualification. No end pose/state is forced here.
    }

    @Test
    void normalVisibleLaunchStillReleasesBothNativePlayers() {
        var fixture = fixture();
        var spawn = unadmittedRomHand(fixture);
        var launcher = captureAndWarm(fixture, spawn);
        var captured = players(fixture).stream().map(Player::read).toList();
        // Source-derived finite deadline: prehold19 + prelaunch59 + two
        // full $50/8 arm traversals and dispatch edges, never a timer seed.
        int limit = 19 + 59 + 2 * (0x50 / 8) + 4;
        for (int dispatch = 0; dispatch < limit
                && players(fixture).stream().anyMatch(AbstractPlayableSprite::isObjectControlled); dispatch++) {
            tickObjects(fixture);
        }
        assertSame(launcher, hand(spawn), "normal release must not be an unload");
        assertTrue(launcher.getYOffset() > 0x18);
        int direction = (spawn.renderFlags() & 1) == 0 ? 1 : -1;
        for (int nativePlayer = 0; nativePlayer < 2; nativePlayer++) {
            var actual = Player.read(players(fixture).get(nativePlayer));
            var before = captured.get(nativePlayer);
            assertAll("sub_30C8C normal held-player launch",
                    () -> assertFalse(actual.controlled()),
                    () -> assertFalse(actual.onObject()),
                    () -> assertEquals(direction * 0x1000, actual.pose().vx()),
                    () -> assertEquals(direction * 0x1000, actual.pose().ground()),
                    () -> assertEquals(0, actual.pose().vy()),
                    () -> assertEquals(before.pose().xSub(), actual.pose().xSub()),
                    () -> assertEquals(before.pose().ySub(), actual.pose().ySub()));
        }
    }

    private static void assertNativeDeletionRetains(int nativePlayer, Player before, Player deleted) {
        assertAll("native P" + nativePlayer + " survives the deleted hand's own SST clear",
                () -> assertTrue(deleted.onObject(), "Delete_Referenced_Sprite does not clear Status_OnObj"),
                () -> assertTrue(deleted.controlled(), "native deletion preserves object_control=1"),
                () -> assertTrue(deleted.cpuAllowed()),
                () -> assertTrue(deleted.movementSuppressed()),
                () -> assertFalse(deleted.air()),
                () -> assertEquals(before.pose(), deleted.pose(), "prelaunch hold has no pose/velocity write"),
                () -> assertEquals(before.interactSlot(), deleted.interactSlot(), "interact pointer remains sticky"),
                () -> assertEquals(before.latchedId(), deleted.latchedId()));
    }

    private static HeadlessTestFixture fixture() {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1).withSkippedZoneIntro().build();
        fixture.stepIdleFrames(1); // ordinary initial CPU/loader slot, same existing component inlet
        assertEquals(320, fixture.camera().getWidth(), "declared native viewport");
        assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        assertNotNull(p2().getCpuController(), "actual registered P2 controller");
        assertTrue(p2().isCpuControlled(), "actual registered CPU sidekick");
        return fixture;
    }

    private static ObjectSpawn unadmittedRomHand(HeadlessTestFixture fixture) {
        var objects = GameServices.level().getObjectManager();
        // Actual ROM asset data, not a synthetic placement/trace row. A placed
        // parent is required: its arm checks isActiveObjectInstance(parent).
        return objects.getAllSpawns().stream()
                .filter(s -> s.objectId() == Sonic3kObjectIds.HCZ_HAND_LAUNCHER && s.x() >= 0x400)
                .filter(s -> objects.getActiveObjectForRewind(s) == null)
                .findFirst().orElseThrow(() -> new AssertionError("prerequisite: an unadmitted ROM hand placement"));
    }

    private static HCZHandLauncherObjectInstance captureAndWarm(HeadlessTestFixture fixture, ObjectSpawn spawn) {
        fixture.camera().setFrozen(true);
        fixture.camera().setX((short) (spawn.x() - 0x80));
        fixture.camera().setY((short) (spawn.y() + 0x48 - 0x80));
        var nativePlayers = players(fixture);
        for (int i = 0; i < nativePlayers.size(); i++) {
            var player = nativePlayers.get(i);
            assertFalse(player.isOnObject(), "prerequisite: no manufactured/reused ride");
            assertFalse(player.isObjectControlled(), "prerequisite: no earlier object takeover");
            assertFalse(player.getRolling());
            assertFalse(player.getDead());
            assertFalse(player.isHurt());
            player.setCentreX((short) spawn.x());
            // First hand dispatch moves $50 -> $48. loc_1E45A computes
            // d0 = handY-$11-(playerY+y_radius+4); declare d0=-8.
            player.setCentreY((short) (spawn.y() + 0x48 - 0x11 - player.getYRadius() + 4));
            player.setSubpixelRaw(0x1357 + i, 0x2468 + i); // declared independent fractions
            player.setAir(true);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0x100);
            player.setGSpeed((short) 0);
        }
        publishVisibility(fixture);
        tickObjects(fixture); // S3K Load_Sprites -> ordinary registry/factory -> SST/TopSolid
        var launcher = hand(spawn);
        assertTrue(launcher.getSlotIndex() >= 0, "ordinary allocator assigned the managed SST");
        // Finite source-derived lowering/capture deadline; no frame/field is seeded.
        int limit = 0x50 / 8 + 2;
        for (int dispatch = 0; dispatch < limit
                && nativePlayers.stream().anyMatch(player -> !player.isObjectControlled()); dispatch++) {
            tickObjects(fixture);
        }
        assertEquals(0x18, launcher.getYOffset(), "prerequisite: genuine normal capture at grab offset");
        assertHeld(fixture, launcher);
        arm(launcher); // exact parent binding, not a global count of neighboring ROM hands
        var cpu = p2().getCpuController();
        publishVisibility(fixture);
        int lf = GameServices.level().getFrameCounter() & 0xFFFF;
        fixture.stepFrame(false, false, false, false, false); // ONE actual visible CPU slot
        assertEquals((lf + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF);
        assertSame(nativePlayers.get(1), p2());
        assertSame(cpu, p2().getCpuController());
        var state = cpu.getState();
        assertTrue(state == SidekickCpuController.State.NORMAL || state == SidekickCpuController.State.PANIC,
                () -> "prerequisite: unexpected CPU takeover " + state);
        assertHeld(fixture, launcher);
        assertTrue(p2().hasRenderFlagOnScreenState() && p2().isRenderFlagOnScreen());
        assertEquals(3, cpu.getDiagnosticInteractId(), "actual visible CPU latched the native code high word");
        return launcher;
    }

    private static Deleted cull(HeadlessTestFixture fixture, ObjectSpawn spawn) {
        var objects = GameServices.level().getObjectManager();
        var launcher = hand(spawn);
        assertHeld(fixture, launcher);
        int beforeY = launcher.getY();
        // Native aligned distance is now $300, strictly beyond the $280
        // Sprite_OnScreen_Test window. No destroy/unload/slot API is called.
        fixture.camera().setX((short) ((spawn.x() & 0xFF80) - 0x280));
        publishVisibility(fixture);
        assertFalse(p2().isRenderFlagOnScreen(), "actual copied-camera/body offscreen publication");
        tickObjects(fixture);
        assertNull(objects.getActiveObjectForRewind(spawn), "prerequisite: ordinary range unload retired the parent");
        assertEquals(beforeY, launcher.getY(), "prerequisite: cull was during the stationary prelaunch hold");
        return new Deleted(players(fixture).stream().map(Player::read).toList());
    }

    private static void assertHeld(HeadlessTestFixture fixture, HCZHandLauncherObjectInstance hand) {
        var objects = GameServices.level().getObjectManager();
        for (var player : players(fixture)) {
            assertTrue(player.isObjectControlled(), "prerequisite: ordinary hand grab");
            assertTrue(player.isObjectControlAllowsCpu());
            assertTrue(player.isObjectControlSuppressesMovement());
            assertTrue(player.isOnObject());
            assertFalse(player.getAir());
            assertSame(hand, objects.getRidingObject(player), "real ride relink");
            assertSame(hand, player.getLatchedSolidObjectInstance(), "real sticky interact relink");
            assertTrue(objects.hasObjectStandingBit(player, hand), "production standing latch");
        }
    }

    private static void publishVisibility(HeadlessTestFixture fixture) {
        fixture.camera().setXCopy(fixture.camera().getX());
        fixture.camera().setYCopy(fixture.camera().getY());
        GameServices.sprites().refreshPlayableRenderFlags(fixture.camera());
    }

    private static void tickObjects(HeadlessTestFixture fixture) {
        var objects = GameServices.level().getObjectManager();
        objects.snapshotTouchResponseState();
        objects.update(fixture.camera().getX(), fixture.sprite(), List.of(p2()),
                GameServices.level().getFrameCounter(), false, true, false);
    }

    private static HCZHandLauncherObjectInstance hand(ObjectSpawn spawn) {
        return assertInstanceOf(HCZHandLauncherObjectInstance.class,
                GameServices.level().getObjectManager().getActiveObjectForRewind(spawn),
                "prerequisite: original ROM placement admitted through its real registry");
    }

    private static HCZHandLauncherObjectInstance.HandLauncherArmChild arm(HCZHandLauncherObjectInstance hand) {
        try {
            // Existing graph-rewind test uses this exact final reference. Read
            // only: no private grab/standing/cache/timer or child/slot writes.
            var parent = HCZHandLauncherObjectInstance.HandLauncherArmChild.class.getDeclaredField("parent");
            parent.setAccessible(true);
            HCZHandLauncherObjectInstance.HandLauncherArmChild found = null;
            for (var child : GameServices.level().getObjectManager()
                    .activeObjectsOfType(HCZHandLauncherObjectInstance.HandLauncherArmChild.class)) {
                if (parent.get(child) != hand) continue;
                assertNull(found, "prerequisite: exactly one arm belonging to this actual parent");
                found = child;
            }
            assertNotNull(found, "prerequisite: real arm with the actual parent/recreated parent reference");
            return found;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("prerequisite: existing hand-arm parent projection", failure);
        }
    }

    private static AbstractPlayableSprite p2() { return GameServices.sprites().getRegisteredSidekicks().getFirst(); }
    private static List<AbstractPlayableSprite> players(HeadlessTestFixture fixture) { return List.of(fixture.sprite(), p2()); }
    private record Pose(short x, short y, int xSub, int ySub, short vx, short vy, short ground) {
        static Pose read(AbstractPlayableSprite player) {
            return new Pose(player.getCentreX(), player.getCentreY(), player.getXSubpixelRaw(), player.getYSubpixelRaw(),
                    player.getXSpeed(), player.getYSpeed(), player.getGSpeed());
        }
    }
    private record Player(boolean air, boolean onObject, boolean controlled, boolean cpuAllowed,
                          boolean movementSuppressed, int latchedId, int interactSlot, Pose pose) {
        static Player read(AbstractPlayableSprite player) {
            return new Player(player.getAir(), player.isOnObject(), player.isObjectControlled(),
                    player.isObjectControlAllowsCpu(), player.isObjectControlSuppressesMovement(),
                    player.getLatchedSolidObjectId(), player.getInteractSlotIndex(), Pose.read(player));
        }
    }
    private record Deleted(List<Player> players) { }
    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
