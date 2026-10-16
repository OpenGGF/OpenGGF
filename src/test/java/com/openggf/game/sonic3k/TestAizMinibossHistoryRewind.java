package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizMinibossInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A completed AIZ miniboss is historical event state, not a permanently live
 * actor. These bounded native-manager fixtures do not claim a played boss clear
 * or cold route. The completion fixture models the scalar state after native
 * results/title handoff and retirement of the retained EndSignControl owner.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestAizMinibossHistoryRewind {
    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void completedMinibossHistoryWithoutALiveActorRestoresTheEntireRegisteredWorld() {
        var world = world();
        // Synthetic equivalent of the reachable completed-fight history:
        // SonicResize2 committed its spawn and advanced to SonicResize3; results
        // changed Apparent_act to 1, then EndSignControl deleted its native SST.
        // No gameplay actor/physics/snapshot bytes are supplied by a trace.
        world.events.setAiz2ResizeRoutine(6);
        world.events.setMinibossSpawned(true);
        world.events.setBossFlag(false);
        GameServices.level().setApparentAct(1);
        assertTrue(world.events.isMinibossSpawned());
        assertEquals(0, liveMinibosses(world.objects).size());
        var before = world.rewind.capture();

        world.events.setMinibossSpawned(false);
        world.rewind.restore(before);

        assertWorldEquals(before, world.rewind.capture());
        assertTrue(world.events.isMinibossSpawned(), "completion retains its one-shot spawn history");
        assertEquals(0, liveMinibosses(world.objects).size(), "completed history must not recreate a boss");
    }

    @Test
    void uncommittedMinibossHistoryRemainsFalseAfterWholeWorldRestore() {
        var world = world();
        assertFalse(world.events.isMinibossSpawned());
        assertEquals(0, liveMinibosses(world.objects).size());
        var before = world.rewind.capture();

        world.events.setMinibossSpawned(true);
        world.rewind.restore(before);

        assertWorldEquals(before, world.rewind.capture());
        assertFalse(world.events.isMinibossSpawned());
        assertEquals(0, liveMinibosses(world.objects).size());
    }

    @Test
    void committedNativeMinibossRecreatesOnceAndKeepsItsHistory() {
        var world = world();
        // Bounded spawn-dispatch fixture: run the genuine SonicResize2 case,
        // rather than constructing a replacement boss or importing its state.
        GameServices.level().setApparentAct(0);
        world.events.setAiz2ResizeRoutine(4);
        world.fixture.camera().setX((short) 0x0F50);
        world.events.update(1, 0);
        var original = liveMinibosses(world.objects).getFirst();
        assertEquals(1, liveMinibosses(world.objects).size());
        assertTrue(world.events.isMinibossSpawned());
        assertEquals(6, world.events.getAiz2ResizeRoutine());
        var before = world.rewind.capture();

        world.objects.removeDynamicObject(original);
        world.events.setMinibossSpawned(false);
        world.rewind.restore(before);

        assertWorldEquals(before, world.rewind.capture());
        assertEquals(1, liveMinibosses(world.objects).size());
        assertNotSame(original, liveMinibosses(world.objects).getFirst());
        assertTrue(world.events.isMinibossSpawned());
        world.events.update(1, 1);
        assertEquals(1, liveMinibosses(world.objects).size(), "the committed resize case cannot double-spawn");
    }

    private static World world() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return new World(fixture, manager.getAizEvents(), GameServices.level().getObjectManager(),
                fixture.gameplayMode().getRewindRegistry());
    }

    private static List<AizMinibossInstance> liveMinibosses(ObjectManager objects) {
        return objects.getActiveObjects().stream().filter(AizMinibossInstance.class::isInstance)
                .map(AizMinibossInstance.class::cast).filter(boss -> !boss.isDestroyed()).toList();
    }

    private static void assertWorldEquals(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        var differences = new ArrayList<String>();
        for (String key : expected.entries().keySet()) {
            differences.addAll(RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
        assertTrue(differences.isEmpty(), () -> String.join("\n", differences));
    }

    private record World(HeadlessTestFixture fixture, Sonic3kAIZEvents events, ObjectManager objects,
                         RewindRegistry rewind) { }
}
