package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The managed live graph, rather than a parent's append-only history, owns intro children. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAizIntroInventoryRewind {
    enum Spot { WAVES, BEFORE_SCATTER, FALLING_EMERALDS, PARTLY_COLLECTED_EMERALDS }

    @ParameterizedTest(name = "native AIZ intro destructive restore: {0}")
    @EnumSource(Spot.class)
    void liveChildrenRestoreFreshAndReplayThroughTheirRetirement(Spot spot) {
        var config = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try {
            HeadlessTestFixture fixture = fixture();
            ObjectManager manager = GameServices.level().getObjectManager();
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            int frame = 0;
            while (!atSpot(manager, fixture.sprite(), spot) && frame++ < 2400) step(fixture);
            assertTrue(atSpot(manager, fixture.sprite(), spot), "native intro did not reach " + spot);
            assertEquals(320, fixture.camera().getWidth());
            assertPlayerIdentities(manager, fixture);
            assertOwnerLinks(manager, fixture.sprite());

            var registry = fixture.gameplayMode().getRewindRegistry();
            CompositeSnapshot saved = registry.capture();
            Map<ObjectRefId, ObjectInstance> sources = family(manager);
            Observation before = observe(manager, fixture);
            List<Observation> forward = new ArrayList<>();
            int horizon = spot == Spot.WAVES ? 30 : 600;
            boolean moved = false;
            boolean animated = false;
            boolean grounded = false;
            for (int i = 0; i < horizon; i++) {
                step(fixture);
                Observation next = observe(manager, fixture);
                forward.add(next);
                for (var entry : before.objects().entrySet()) {
                    ChildState current = next.objects().get(entry.getKey());
                    if (current != null) {
                        moved |= current.x() != entry.getValue().x() || current.y() != entry.getValue().y();
                        animated |= current.frame() != entry.getValue().frame();
                    }
                }
                grounded |= live(manager, AizEmeraldScatterInstance.class).stream()
                        .anyMatch(e -> e.getPhase() == AizEmeraldScatterInstance.Phase.GROUNDED);
                if (spot != Spot.WAVES && live(manager, AizPlaneIntroInstance.class).isEmpty()
                        && live(manager, AizEmeraldScatterInstance.class).isEmpty()) break;
            }
            assertTrue(moved, "the positive control must exercise forward motion");
            if (spot == Spot.WAVES) {
                assertTrue(animated, "the positive control must exercise wave animation");
                for (var entry : sources.entrySet()) {
                    if (entry.getValue() instanceof AizIntroWaveChild) {
                        assertFalse(forward.getLast().objects().containsKey(entry.getKey()),
                                "each captured wave must retire even while new waves are being spawned");
                    }
                }
            } else {
                assertTrue(live(manager, AizPlaneIntroInstance.class).isEmpty(), "parent must retire");
                assertTrue(live(manager, AizEmeraldScatterInstance.class).isEmpty(), "emerald pickup must finish");
                if (spot != Spot.PARTLY_COLLECTED_EMERALDS) assertTrue(grounded, "emeralds must land before pickup");
                assertFalse(fixture.sprite().isObjectControlled(), "explosion must release player movement");
                assertFalse(fixture.sprite().isObjectMappingFrameControl());
                assertFalse(fixture.sprite().isHidden());
            }

            // Destroy the actual manager graph and leave an unmanaged singleton decoy.
            // Neither reused Java instances nor a stale static owner can satisfy this restore.
            for (ObjectInstance object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
            new AizPlaneIntroInstance(new ObjectSpawn(0x700, 0x100, 0, 0, 0, false, 900));
            registry.restore(saved);
            Map<ObjectRefId, ObjectInstance> restored = family(manager);
            assertEquals(sources.keySet(), restored.keySet(), "exact live identities, no dropped or extra children");
            sources.forEach((id, source) -> assertNotSame(source, restored.get(id), "fresh instance for " + id));
            assertEquals(before, observe(manager, fixture), "immediate restore at " + spot);
            assertPlayerIdentities(manager, fixture);
            assertOwnerLinks(manager, fixture.sprite());
            for (int i = 0; i < forward.size(); i++) {
                step(fixture);
                assertEquals(forward.get(i), observe(manager, fixture), "forward dispatch " + i + " after " + spot);
            }
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(config);
        }
    }

    @Test
    void destructiveRestoreKeepsTheStageplayOwnedPlayerWhenFocusIsAnotherRegisteredPlayer() throws Exception {
        var config = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try {
            HeadlessTestFixture fixture = fixture();
            step(fixture);
            ObjectManager manager = GameServices.level().getObjectManager();
            AizPlaneIntroInstance parent = live(manager, AizPlaneIntroInstance.class).getFirst();
            AbstractPlayableSprite owner = GameServices.sprites().getSidekicks().getFirst();
            assertNotSame(fixture.sprite(), owner);
            owner.setControlLocked(true);
            owner.setHidden(true);
            owner.setObjectMappingFrameControl(true);
            ObjectControlState.engineScriptedPreserveCpuMovementSuppressed().applyTo(owner);
            field(parent, "ownedPlayer").set(parent, owner);
            assertPlayerIdentities(manager, fixture);
            var registry = fixture.gameplayMode().getRewindRegistry();
            CompositeSnapshot saved = registry.capture();
            manager.removeDynamicObject(parent);
            registry.restore(saved);
            AizPlaneIntroInstance restored = live(manager, AizPlaneIntroInstance.class).getFirst();
            assertNotSame(parent, restored);
            assertSame(owner, field(restored, "ownedPlayer").get(restored));
            PlayerState focusedBeforeRelease = playerState(fixture.sprite());
            restored.releasePlayersForVisualDirection();
            assertFalse(owner.isControlLocked());
            assertFalse(owner.isObjectControlled());
            assertFalse(owner.isObjectMappingFrameControl());
            assertFalse(owner.isHidden());
            assertEquals(focusedBeforeRelease, playerState(fixture.sprite()), "release must target its actual owner");
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(config);
        }
    }

    private static HeadlessTestFixture fixture() {
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        GameServices.configuration().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        return HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
    }

    private static void step(HeadlessTestFixture fixture) {
        fixture.stepFrame(false, false, false, false, false);
        assertFalse(fixture.sprite().getDead(), "native neutral intro must keep P1 alive");
    }

    private static boolean atSpot(ObjectManager manager, AbstractPlayableSprite player, Spot spot) {
        List<AizPlaneIntroInstance> parents = live(manager, AizPlaneIntroInstance.class);
        List<AizEmeraldScatterInstance> emeralds = live(manager, AizEmeraldScatterInstance.class);
        return switch (spot) {
            case WAVES -> parents.size() == 1 && parents.getFirst().getRoutine() == 20
                    && live(manager, AizIntroWaveChild.class).size() >= 2;
            case BEFORE_SCATTER -> parents.size() == 1 && parents.getFirst().getRoutine() == 26
                    && player.getCentreX() >= 0x13B0 && player.getCentreX() < 0x13D0;
            case FALLING_EMERALDS -> parents.isEmpty() && emeralds.size() == 7
                    && emeralds.stream().allMatch(e -> e.getPhase() == AizEmeraldScatterInstance.Phase.FALLING);
            case PARTLY_COLLECTED_EMERALDS -> parents.isEmpty() && !emeralds.isEmpty() && emeralds.size() < 7;
        };
    }

    private static void assertPlayerIdentities(ObjectManager manager, HeadlessTestFixture fixture) {
        RewindIdentityTable table = manager.captureIdentityContext().requireIdentityTable();
        assertEquals(PlayerRefId.mainPlayer(), table.encodePlayer(fixture.sprite()));
        assertEquals(PlayerRefId.sidekick(0), table.encodePlayer(GameServices.sprites().getSidekicks().getFirst()));
        manager.validateRewindReferenceClosure();
    }

    private static void assertOwnerLinks(ObjectManager manager, AbstractPlayableSprite player) {
        List<AizPlaneIntroInstance> parents = live(manager, AizPlaneIntroInstance.class);
        for (AizIntroWaveChild wave : live(manager, AizIntroWaveChild.class)) {
            assertEquals(1, parents.size(), "native waves retire before their single intro parent");
            assertSame(parents.getFirst(), read(wave, "parent"));
        }
        for (AizEmeraldScatterInstance emerald : live(manager, AizEmeraldScatterInstance.class)) {
            assertTrue(parents.isEmpty(), "emeralds continue after the intro parent's retirement");
            assertSame(live(manager, CutsceneKnucklesAiz1Instance.class).getFirst(), read(emerald, "knuckles"));
        }
        if (!parents.isEmpty()) {
            assertSame(player, read(parents.getFirst(), "ownedPlayer"));
            assertTrue(player.isObjectControlled());
            assertTrue(player.isControlLocked());
            assertTrue(player.isObjectMappingFrameControl());
            assertTrue(player.isHidden());
        }
    }

    private static Map<ObjectRefId, ObjectInstance> family(ObjectManager manager) {
        var table = manager.captureIdentityContext().requireIdentityTable();
        Map<ObjectRefId, ObjectInstance> result = new LinkedHashMap<>();
        for (ObjectInstance object : manager.getActiveObjects()) {
            if (!object.isDestroyed() && (object instanceof AizPlaneIntroInstance || object instanceof AizIntroWaveChild
                    || object instanceof AizEmeraldScatterInstance || object instanceof CutsceneKnucklesAiz1Instance)) {
                ObjectRefId id = table.idFor(object);
                assertNotNull(id);
                assertNull(result.put(id, object), "identity must be unique");
            }
        }
        return result;
    }

    private static Observation observe(ObjectManager manager, HeadlessTestFixture fixture) {
        var table = manager.captureIdentityContext().requireIdentityTable();
        Map<ObjectRefId, ChildState> states = new LinkedHashMap<>();
        family(manager).forEach((id, object) -> {
            int frame = object instanceof AizIntroWaveChild w ? w.getAnimFrame()
                    : object instanceof AizEmeraldScatterInstance e ? e.getMappingFrame()
                    : (int) read(object, "mappingFrame");
            Object owner = object instanceof AizIntroWaveChild ? read(object, "parent")
                    : object instanceof AizEmeraldScatterInstance || object instanceof AizPlaneIntroInstance
                    ? read(object, "knuckles") : null;
            List<Object> scalars = new ArrayList<>();
            String[] fields = object instanceof AizIntroWaveChild
                    ? new String[]{"animIndex", "animTimer", "deleteRoutinePending"}
                    : object instanceof AizEmeraldScatterInstance
                    ? new String[]{"xSub", "ySub", "xVel", "yVel", "phase"}
                    : object instanceof AizPlaneIntroInstance
                    ? new String[]{"routine", "waveTimer", "scrollSpeed", "eventsFg1", "lastFrameCounter", "ownsPlayerControl"}
                    : new String[]{"routine", "xSub", "ySub", "xVel", "yVel"};
            for (String name : fields) scalars.add(read(object, name));
            states.put(id, new ChildState(object.getClass(), object.getX(), object.getY(),
                    ((AbstractObjectInstance) object).getSlotIndex(), frame,
                    table.idFor((ObjectInstance) owner), scalars));
        });
        return new Observation(states, playerState(fixture.sprite()));
    }

    private record Observation(Map<ObjectRefId, ChildState> objects, PlayerState player) { }
    private record ChildState(Class<?> type, int x, int y, int slot, int frame, ObjectRefId owner,
                              List<Object> scalars) { }
    private record PlayerState(int x, int y, int xSub, int ySub, short xVel, short yVel, int frame, int anim,
                               boolean control, boolean locked, boolean mappingControl, boolean hidden,
                               boolean hurt, boolean air) { }
    private static PlayerState playerState(AbstractPlayableSprite player) {
        return new PlayerState(player.getCentreX(), player.getCentreY(), player.getXSubpixelRaw(),
                player.getYSubpixelRaw(), player.getXSpeed(), player.getYSpeed(), player.getMappingFrame(),
                player.getAnimationId(), player.isObjectControlled(), player.isControlLocked(),
                player.isObjectMappingFrameControl(), player.isHidden(), player.isHurt(), player.getAir());
    }
    private static <T> List<T> live(ObjectManager manager, Class<T> type) {
        return manager.getActiveObjects().stream().filter(o -> !o.isDestroyed()).filter(type::isInstance)
                .map(type::cast).toList();
    }
    private static Object read(Object owner, String name) {
        try { return field(owner, name).get(owner); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Field field(Object owner, String name) throws NoSuchFieldException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
