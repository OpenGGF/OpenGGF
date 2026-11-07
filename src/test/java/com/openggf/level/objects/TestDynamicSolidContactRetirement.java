package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ROM-free witnesses for contact-key lifetime at the production dynamic-retirement boundary. */
class TestDynamicSolidContactRetirement {
    @BeforeEach
    void openGameplayServices() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void closeGameplayServices() {
        SessionManager.clear();
    }

    @ParameterizedTest
    @EnumSource(Retirement.class)
    void soleSpawnKeyedDynamicRetirementExpiresObjectBitsWithoutChangingPlayers(Retirement retirement) {
        var manager = manager(List.of());
        var solid = new SpawnSolid(spawn());
        manager.addDynamicObject(solid);
        var contacts = contactPair(manager, solid, "sole");
        var key = new ObjectManagerSnapshot.SolidContactLatchKey(solid.getSpawn(), -1, false);
        var before = manager.solidContacts().captureRewindState();
        assertEquals(List.of(key), keysFor(before.standingBits(), contacts.rider()));
        assertEquals(List.of(key), keysFor(before.pushingBits(), contacts.pusher()));
        assertFalse(manager.usesOccurrenceSolidStateKey(solid));
        var riderState = PlayerState.capture(contacts.rider());
        var pusherState = PlayerState.capture(contacts.pusher());

        retirement.retire(manager, solid);

        var after = manager.solidContacts().captureRewindState();
        // DeleteObject/DeleteObject2 (docs/s2disasm/s2.asm:30329-30345) clear
        // the retired object's slot, including standing/pushing status bits.
        // They do not write either player's slot or release its interaction.
        assertAll(
                () -> assertTrue(after.standingBits().isEmpty(), "retired dynamic standing bits must expire"),
                () -> assertTrue(after.pushingBits().isEmpty(), "retired dynamic pushing bits must expire"),
                () -> assertSame(solid, manager.getRidingObject(contacts.rider())),
                () -> assertEquals(riderState, PlayerState.capture(contacts.rider())),
                () -> assertEquals(pusherState, PlayerState.capture(contacts.pusher())),
                () -> assertTrue(manager.objectQuery().identityOf(solid).isEmpty()));
    }

    @ParameterizedTest
    @EnumSource(Retirement.class)
    void equalSpawnReplacementCannotInheritBitsAfterReconstructedDynamicRetires(Retirement retirement) {
        var manager = manager(List.of());
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var original = new SpawnSolid(spawn());
        manager.addDynamicObject(original);
        var contacts = contactPair(manager, original, "reconstructed");
        var adapter = manager.rewindSnapshottable();
        var captured = adapter.capture();

        adapter.restore(captured);

        var restored = manager.activeObjectsOfType(SpawnSolid.class).stream().findFirst().orElseThrow();
        assertNotSame(original, restored, "exercise reconstruction rather than only in-place restore");
        assertSame(restored, manager.getRidingObject(contacts.rider()));
        assertTrue(manager.hasObjectStandingBit(contacts.rider(), restored));
        assertTrue(manager.hasObjectPushingBit(contacts.pusher()));
        int retiredSlot = restored.getSlotIndex();

        retirement.retire(manager, restored);
        var replacementSpawn = spawn();
        assertEquals(restored.getSpawn(), replacementSpawn);
        assertNotSame(restored.getSpawn(), replacementSpawn, "a value-equal spawn is a legitimate fresh actor");
        var replacement = new SpawnSolid(replacementSpawn);
        manager.addDynamicObject(replacement);

        assertEquals(retiredSlot, replacement.getSlotIndex(), "normal allocation reuses the freed SST slot");
        assertAll(
                () -> assertFalse(manager.hasObjectStandingBit(contacts.rider(), replacement),
                        "the new actor must not observe its predecessor's spawn-keyed standing bit"),
                () -> assertFalse(manager.hasObjectPushingBit(contacts.pusher()),
                        "the predecessor's pushing bit must not survive replacement admission"),
                () -> assertSame(restored, manager.getRidingObject(contacts.rider()),
                        "admitting an actor does not itself rewrite the player's retained ride"));
    }

    @ParameterizedTest
    @EnumSource(Retirement.class)
    void retiringNonSolidDynamicChildWithParentSpawnPreservesLiveParentBits(Retirement retirement) {
        var parentSpawn = spawn();
        var manager = manager(List.of(parentSpawn));
        var parent = (SpawnSolid) manager.getActiveObjectForRewind(parentSpawn);
        var contacts = contactPair(manager, parent, "parent");
        // A child's construction provenance may be its parent's layout record.
        // This child never owns a solid bit: its removal must not erase the
        // still-live parent's spawn-keyed status merely because getSpawn agrees.
        var child = new SharedSpawnChild(parentSpawn);
        manager.addDynamicObject(child);
        assertSame(parent.getSpawn(), child.getSpawn());
        var before = manager.solidContacts().captureRewindState();

        retirement.retire(manager, child);

        assertEquals(before, manager.solidContacts().captureRewindState(),
                "retiring a non-solid child cannot retire its live parent's contact key");
        assertTrue(manager.hasObjectStandingBit(contacts.rider(), parent));
        assertTrue(manager.hasObjectPushingBit(contacts.pusher()));
        assertSame(parent, manager.getRidingObject(contacts.rider()));
        assertTrue(manager.objectQuery().identityOf(parent).isPresent());
    }

    @ParameterizedTest
    @EnumSource(Retirement.class)
    void instanceKeyedDynamicSiblingRetiresOnlyItsOwnBitsWhenSpawnIsShared(Retirement retirement) {
        var parentSpawn = spawn();
        var manager = manager(List.of(parentSpawn));
        var parent = (SpawnSolid) manager.getActiveObjectForRewind(parentSpawn);
        var parentContacts = contactPair(manager, parent, "instance-parent");
        var child = new InstanceSolid(parentSpawn);
        manager.addDynamicObject(child);
        var childContacts = contactPair(manager, child, "instance-child");
        var before = manager.solidContacts().captureRewindState();
        assertTrue(keysFor(before.standingBits(), childContacts.rider()).getFirst().instanceKey());
        assertFalse(keysFor(before.standingBits(), parentContacts.rider()).getFirst().instanceKey());

        retirement.retire(manager, child);

        var after = manager.solidContacts().captureRewindState();
        assertAll(
                () -> assertTrue(keysFor(after.standingBits(), childContacts.rider()).isEmpty()),
                () -> assertTrue(keysFor(after.pushingBits(), childContacts.pusher()).isEmpty()),
                () -> assertEquals(keysFor(before.standingBits(), parentContacts.rider()),
                        keysFor(after.standingBits(), parentContacts.rider())),
                () -> assertEquals(keysFor(before.pushingBits(), parentContacts.pusher()),
                        keysFor(after.pushingBits(), parentContacts.pusher())),
                () -> assertSame(child, manager.getRidingObject(childContacts.rider())),
                () -> assertSame(parent, manager.getRidingObject(parentContacts.rider())));
    }

    private enum Retirement {
        EXPLICIT {
            @Override void retire(ObjectManager manager, AbstractObjectInstance object) {
                manager.removeDynamicObject(object);
            }
        },
        DESTROYED_CLEANUP {
            @Override void retire(ObjectManager manager, AbstractObjectInstance object) {
                object.setDestroyed(true);
                manager.cleanupDestroyedDynamicObjects();
            }
        };

        abstract void retire(ObjectManager manager, AbstractObjectInstance object);
    }

    private record Contacts(ContactSonic rider, ContactSonic pusher) { }

    private record PlayerState(int x, int y, int xSpeed, int ySpeed, int groundSpeed,
            boolean air, boolean onObject, boolean pushing, int animation) {
        static PlayerState capture(Sonic player) {
            return new PlayerState(player.getCentreX(), player.getCentreY(), player.getXSpeed(),
                    player.getYSpeed(), player.getGSpeed(), player.getAir(), player.isOnObject(),
                    player.getPushing(), player.getAnimationId());
        }
    }

    private static Contacts contactPair(ObjectManager manager, SpawnSolid solid, String code) {
        solid.snapshotPreUpdatePosition();
        var rider = new ContactSonic(code + "-rider");
        rider.setCentreX((short) solid.getX());
        int maxTop = solid.getSolidParams().groundHalfHeight() + rider.getYRadius();
        rider.setCentreY((short) (solid.getY() - 4 - maxTop + 8));
        rider.setAir(true);
        rider.setYSpeed((short) 0x100);
        var pusher = new ContactSonic(code + "-pusher");
        pusher.applyCustomRadii(10, 10);
        pusher.setCentreX((short) (solid.getX() - 15));
        pusher.setCentreY((short) solid.getY());
        pusher.setAir(false);
        pusher.setXSpeed((short) 0x100);
        manager.solidContacts().beginInlineFrame(rider, List.of(pusher), false);
        SolidObjectPassTestSupport.participantCheckpoint(manager, solid, rider, false);
        SolidObjectPassTestSupport.participantCheckpoint(manager, solid, pusher, false);
        assertSame(solid, manager.getRidingObject(rider), "a real top contact establishes the ride");
        assertTrue(manager.hasObjectStandingBit(rider, solid));
        assertTrue(pusher.getPushing(), "a real grounded side contact establishes pushing");
        assertTrue(manager.hasObjectPushingBit(pusher));
        return new Contacts(rider, pusher);
    }

    @ParameterizedTest
    @EnumSource(Retirement.class)
    void retirementDoesNotRebuildCachesOrReadUnrelatedSlotPolicies(Retirement retirement) {
        var manager = manager(List.of());
        var solid = new SpawnSolid(spawn());
        var unrelated = new SharedSpawnChild(spawn()) {
            boolean armed;
            @Override public int getSlotIndex() {
                if (armed) throw new AssertionError("unrelated slot getter during contact expiry");
                return super.getSlotIndex();
            }
        };
        manager.addDynamicObject(solid);
        manager.addDynamicObject(unrelated);
        contactPair(manager, solid, "no-cache-rebuild");
        unrelated.armed = true;
        retirement.retire(manager, solid);
        unrelated.armed = false;
        assertTrue(manager.solidContacts().captureRewindState().standingBits().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Retirement.class)
    void unloadKeyMutationObservesTheExistingRetirementBoundary(Retirement retirement) {
        var manager = manager(List.of());
        var sibling = new SpawnSolid(new ObjectSpawn(300, 100, 1, 0, 0, false, 0));
        var solid = new SpawnSolid(spawn()) {
            boolean unloadObserved;
            @Override public void onUnload() {
                unloadObserved = true;
                // Expiry has already run for explicit removal, but not cleanup.
                assertEquals(retirement == Retirement.DESTROYED_CLEANUP,
                        manager.hasObjectStandingBit(observedRider, this));
                sibling.updateDynamicSpawn(100, 100);
            }
            PlayableEntity observedRider;
        };
        manager.addDynamicObject(solid);
        manager.addDynamicObject(sibling);
        var contacts = contactPair(manager, solid, "unload-key");
        solid.observedRider = contacts.rider();
        retirement.retire(manager, solid);
        // This pins callback timing, not whether value-key aliases should inherit bits.
        // Both observations belong to the retired incarnation's existing boundary.
        assertTrue(solid.unloadObserved);
        assertSame(solid, manager.getRidingObject(contacts.rider()));
    }

    @ParameterizedTest
    @EnumSource(Retirement.class)
    void equalSpawnSolidSiblingRetainsSharedKeyUntilLastOwnerRetires(Retirement retirement) {
        var manager = manager(List.of());
        var first = new SpawnSolid(spawn());
        var sibling = new SpawnSolid(spawn());
        manager.addDynamicObject(first);
        manager.addDynamicObject(sibling);
        var contacts = contactPair(manager, first, "shared-solid");
        retirement.retire(manager, first);
        assertTrue(manager.hasObjectStandingBit(contacts.rider(), sibling));
        assertFalse(manager.solidContacts().captureRewindState().pushingBits().isEmpty());
        retirement.retire(manager, sibling);
        assertTrue(manager.solidContacts().captureRewindState().standingBits().isEmpty());
        assertTrue(manager.solidContacts().captureRewindState().pushingBits().isEmpty());
        assertSame(first, manager.getRidingObject(contacts.rider()));
    }

    private static List<ObjectManagerSnapshot.SolidContactLatchKey> keysFor(
            List<ObjectManagerSnapshot.SolidContactLatchEntry> entries, PlayableEntity player) {
        return entries.stream().filter(entry -> entry.player() == player)
                .flatMap(entry -> entry.keys().stream()).toList();
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(100, 100, 1, 0, 0, false, 0);
    }

    private static ObjectManager manager(List<ObjectSpawn> spawns) {
        var registry = new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) { return new SpawnSolid(spawn); }
            @Override public void reportCoverage(List<ObjectSpawn> ignored) { }
            @Override public String getPrimaryName(int objectId) { return "contact-retirement-solid"; }
            @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_2; }
        };
        var manager = new ObjectManager(spawns, registry, 0, null, null);
        manager.reset(0);
        return manager;
    }

    private static final class ContactSonic extends Sonic {
        ContactSonic(String code) {
            super(code, (short) 0, (short) 0);
            setGameRulesForTest(GameRules.SONIC_3K);
        }
    }

    private static class SpawnSolid extends AbstractObjectInstance
            implements SolidObjectProvider, SpawnRewindRecreatable {
        SpawnSolid(ObjectSpawn spawn) { super(spawn, "contact-retirement-solid"); }
        @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(16, 8, 8); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class InstanceSolid extends SpawnSolid {
        InstanceSolid(ObjectSpawn spawn) { super(spawn); }
        @Override public int getX() { return getSpawn().x() + 64; }
        @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    }

    private static class SharedSpawnChild extends AbstractObjectInstance {
        SharedSpawnChild(ObjectSpawn spawn) { super(spawn, "non-solid-shared-spawn-child"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
