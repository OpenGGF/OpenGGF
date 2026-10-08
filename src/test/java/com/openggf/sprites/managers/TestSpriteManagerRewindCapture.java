package com.openggf.sprites.managers;

import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot.PlayerRewindExtra;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Isolated
class TestSpriteManagerRewindCapture {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void captureIncludesFollowHistoryOnlyForSpritesWithFollowers() {
        SpriteManager manager = new SpriteManager();
        Sonic sonic = new Sonic("sonic", (short) 0x100, (short) 0x200);
        Tails firstSidekick = cpuTails("tails_p2", (short) 0x0E0, sonic);
        Tails terminalSidekick = cpuTails("tails_p3", (short) 0x0C0, firstSidekick);

        manager.addSprite(sonic);
        manager.addSprite(firstSidekick, "tails");
        manager.addSprite(terminalSidekick, "tails");

        Map<String, PlayerRewindExtra> entries = Arrays.stream(manager.rewindSnapshottable().capture().sprites())
                .collect(Collectors.toMap(entry -> entry.code(), entry -> entry.state().playerExtra()));

        assertHasFollowHistory(entries.get("sonic"));
        assertHasFollowHistory(entries.get("tails_p2"));
        assertHasNoFollowHistory(entries.get("tails_p3"));
    }

    @Test
    void canonicalOwnerCharacterCodeSurvivesProductionSpriteRewindRoundTrip() {
        SpriteManager manager = new SpriteManager();
        Sonic runner = new Sonic("owner:runner", (short) 0x100, (short) 0x200);
        manager.addSprite(runner);
        var rewind = manager.rewindSnapshottable();
        int originalCentreX = runner.getCentreX();

        var snapshot = rewind.capture();
        runner.setCentreX((short) 0x180);
        rewind.restore(snapshot);

        assertEquals("owner:runner", snapshot.sprites()[0].code());
        assertEquals("owner:runner", runner.getCode());
        assertEquals(originalCentreX, runner.getCentreX());
    }

    @Test
    void emptyRestoredInteractSlotDoesNotLatchAnotherObjectOfTheSameType() {
        SpriteManager manager = new SpriteManager();
        Sonic runner = new Sonic("sonic", (short) 0x100, (short) 0x200);
        manager.addSprite(runner);
        ObjectManager objects = new ObjectManager(List.of(), null, 0, null, null);
        objects.reset(0);
        LatchObject unrelated = new LatchObject();
        objects.addDynamicObject(unrelated);
        LatchObject released = new LatchObject();
        released.setSlotIndex(unrelated.getSlotIndex() + 1);
        released.setDestroyed(true);
        runner.setLatchedSolidObject(7, released);
        assertEquals(-1, objects.objectIdInSlot(runner.getInteractSlotIndex()));
        runner.restoreRewindState(runner.captureRewindState());

        manager.refreshLatchedSolidObjectsAfterRewindRestore(objects);

        assertNull(runner.getLatchedSolidObjectInstance(),
                "An empty recorded contact slot must stay released even with a nearby same-type object");
        assertEquals(released.getSlotIndex(), runner.getInteractSlotIndex());
        assertEquals(7, runner.getLatchedSolidObjectId());
        assertTrue(runner.isLatchedSolidObjectReleased());
    }

    @Test
    void releasedContactSurvivesRewindEvenWhenItsSlotHasTheSameObjectTypeAgain() {
        SpriteManager manager = new SpriteManager();
        Sonic runner = new Sonic("sonic", (short) 0x100, (short) 0x200);
        manager.addSprite(runner);
        ObjectManager objects = new ObjectManager(List.of(), null, 0, null, null);
        objects.reset(0);
        LatchObject replacement = new LatchObject();
        objects.addDynamicObject(replacement);
        LatchObject released = new LatchObject();
        released.setSlotIndex(replacement.getSlotIndex());
        released.setDestroyed(true);
        runner.setLatchedSolidObject(7, released);
        var snapshot = runner.captureRewindState();
        assertTrue(snapshot.playerExtra().latchedSolidObjectReleased());

        runner.setLatchedSolidObject(7, replacement);
        runner.restoreRewindState(snapshot);
        manager.refreshLatchedSolidObjectsAfterRewindRestore(objects);

        assertNull(runner.getLatchedSolidObjectInstance());
        assertTrue(runner.isLatchedSolidObjectReleased(),
                "Slot reuse cannot erase the released owner's captured provenance");
        assertTrue(runner.captureRewindState().playerExtra().latchedSolidObjectReleased());
        runner.setLatchedSolidObject(7, replacement);
        assertFalse(runner.isLatchedSolidObjectReleased(), "A genuine new contact clears the release marker");
    }

    @Test
    void restoredInteractSlotOverridesStillActiveContactFromFutureTimeline() {
        SpriteManager manager = new SpriteManager();
        Sonic runner = new Sonic("sonic", (short) 0x100, (short) 0x200);
        manager.addSprite(runner);
        ObjectManager objects = new ObjectManager(List.of(), null, 0, null, null);
        objects.reset(0);
        LatchObject restored = new LatchObject();
        LatchObject future = new LatchObject();
        objects.addDynamicObject(restored);
        objects.addDynamicObject(future);
        runner.setLatchedSolidObject(7, restored);
        var snapshot = runner.captureRewindState();
        runner.setLatchedSolidObject(7, future);
        runner.restoreRewindState(snapshot);

        manager.refreshLatchedSolidObjectsAfterRewindRestore(objects);

        assertSame(restored, runner.getLatchedSolidObjectInstance(),
                "An active same-type future contact cannot override the restored interact slot");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 7})
    void liveDynamicContactRemainsLiveAcrossCaptureAndRestore(int objectId) throws Exception {
        SpriteManager manager = new SpriteManager();
        Sonic runner = new Sonic("sonic", (short) 0x100, (short) 0x200);
        manager.addSprite(runner);
        ObjectManager objects = new ObjectManager(List.of(), null, 0, null, null);
        objects.reset(0);
        var level = com.openggf.game.GameServices.level();
        var field = level.getClass().getDeclaredField("objectManager");
        field.setAccessible(true);
        field.set(level, objects);
        LatchObject dynamic = new LatchObject(objectId);
        objects.addDynamicObject(dynamic);
        runner.setLatchedSolidObject(objectId, dynamic);
        assertTrue(objects.getActiveObjects().contains(dynamic));
        assertFalse(objects.isActiveObjectInstance(dynamic),
                "The placed-object map alone does not contain a dynamic contact");

        var snapshot = runner.captureRewindState();
        assertFalse(snapshot.playerExtra().latchedSolidObjectReleased());
        runner.restoreRewindState(snapshot);
        manager.refreshLatchedSolidObjectsAfterRewindRestore(objects);

        assertSame(dynamic, runner.getLatchedSolidObjectInstance());
        assertFalse(runner.isLatchedSolidObjectReleased());
    }

    @Test
    void clearedZeroIdContactDoesNotBindToItsStickySlotAfterRestore() {
        SpriteManager manager = new SpriteManager();
        Sonic runner = new Sonic("sonic", (short) 0x100, (short) 0x200);
        manager.addSprite(runner);
        ObjectManager objects = new ObjectManager(List.of(), null, 0, null, null);
        objects.reset(0);
        LatchObject dynamic = new LatchObject(0);
        objects.addDynamicObject(dynamic);
        runner.setLatchedSolidObject(0, dynamic);
        runner.setLatchedSolidObjectId(0);
        assertEquals(dynamic.getSlotIndex(), runner.getInteractSlotIndex());
        var snapshot = runner.captureRewindState();
        runner.setLatchedSolidObject(0, dynamic);
        runner.restoreRewindState(snapshot);
        manager.refreshLatchedSolidObjectsAfterRewindRestore(objects);
        assertNull(runner.getLatchedSolidObjectInstance());
        assertFalse(runner.hasLatchedSolidObjectBinding());
    }

    private static final class LatchObject extends AbstractObjectInstance {
        LatchObject() { this(7); }
        LatchObject(int objectId) {
            super(new ObjectSpawn(0x100, 0x200, objectId, 0, 0, false, 0), "LatchObject");
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static Tails cpuTails(String code, short x, Sonic leader) {
        return cpuTails(code, x, (com.openggf.sprites.playable.AbstractPlayableSprite) leader);
    }

    private static Tails cpuTails(
            String code,
            short x,
            com.openggf.sprites.playable.AbstractPlayableSprite leader) {
        Tails tails = new Tails(code, x, (short) 0x200);
        tails.setCpuControlled(true);
        tails.setCpuController(new SidekickCpuController(tails, leader));
        return tails;
    }

    private static void assertHasFollowHistory(PlayerRewindExtra extra) {
        assertNotNull(extra.xHistory());
        assertNotNull(extra.yHistory());
        assertNotNull(extra.inputHistory());
        assertNotNull(extra.jumpPressHistory());
        assertNotNull(extra.statusHistory());
    }

    private static void assertHasNoFollowHistory(PlayerRewindExtra extra) {
        assertNull(extra.xHistory());
        assertNull(extra.yHistory());
        assertNull(extra.inputHistory());
        assertNull(extra.jumpPressHistory());
        assertNull(extra.statusHistory());
    }
}
