package com.openggf.level;

import com.openggf.audio.AudioManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.animation.StageRingAnimationFrameProvider;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.level.rings.RingFrame;
import com.openggf.level.rings.RingFramePiece;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingManagerInternalAccess;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises the canonical ring phase without loading a level or ordinary player. */
class TestSharedSpillAnimationPolicy {
    @AfterEach void closeWorld() { SessionManager.clear(); }

    @Test void absentProviderRetainsTheOrdinarySharedClock() {
        var fixture = fixture();
        fixture.level.animatedPatternManager = () -> { };
        fixture.rings.getSpillAnimationState().restore(new int[]{3, 0x1FF, 0});
        fixture.level.update();
        assertArrayEquals(new int[]{2, 0x202, 1}, fixture.rings.getSpillAnimationState().snapshot());
    }

    @Test void providerDefaultTrueRetainsOneClockStepPerLevelUpdate() {
        var fixture = fixture();
        var patterns = new DefaultClock();
        assertTrue(patterns.advancesSharedSpillAnimation());
        fixture.level.animatedPatternManager = patterns;
        fixture.rings.getSpillAnimationState().restore(new int[]{3, 0x1FF, 0});
        fixture.level.update();
        assertEquals(1, patterns.updates);
        assertArrayEquals(new int[]{2, 0x202, 1}, fixture.rings.getSpillAnimationState().snapshot());
    }

    @Test void stoppedClockRetainsAllThreeWordsWhileOtherAnimationStillRuns() {
        var fixture = fixture();
        var patterns = new StoppedClock();
        fixture.level.animatedPatternManager = patterns;
        int[] initial = {0xFF, 0x5678, 3};
        fixture.rings.getSpillAnimationState().restore(initial);
        for (int i = 0; i < 17; i++) fixture.level.update();
        assertEquals(17, patterns.updates);
        assertArrayEquals(initial, fixture.rings.getSpillAnimationState().snapshot());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bothBranchesRetireOnlyStaleMirrorsAndKeepAReusedSlotReserved(boolean advances) {
        var fixture = fixture();
        fixture.level.animatedPatternManager = advances ? new DefaultClock() : new StoppedClock();
        var player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 0x100);
        when(player.getCentreY()).thenReturn((short) 0x100);
        fixture.rings.spawnLostRings(player, 2, 0);
        var twins = fixture.objects.activeObjectsOfType(LostRingObjectInstance.class);
        assertEquals(2, twins.size());
        assertEquals(2, fixture.rings.getActiveLostRings().size());
        int reusedSlot = twins.getFirst().getSlotIndex();
        int liveSlot = twins.get(1).getSlotIndex();
        fixture.objects.removeDynamicObject(twins.getFirst());
        assertEquals(reusedSlot, fixture.objects.allocateDynamicSlot());
        var replacement = ObjectConstructionContext.with(fixture.objects.getObjectServices(), reusedSlot, InertObject::new);
        fixture.objects.addDynamicObjectAtSlot(replacement, reusedSlot);
        fixture.rings.getSpillAnimationState().restore(new int[]{7, 0x400, 2});

        fixture.level.update();

        var mirrors = fixture.rings.getActiveLostRings();
        assertEquals(1, mirrors.size());
        assertEquals(liveSlot, mirrors.getFirst().getSlotIndex());
        assertTrue(fixture.objects.hasLiveLostRingAtSlot(liveSlot));
        assertTrue(fixture.objects.getActiveObjects().contains(replacement));
        assertNotEquals(reusedSlot, fixture.objects.allocateDynamicSlot(), "Housekeeping cannot free a reused live slot");
        assertArrayEquals(advances ? new int[]{6, 0x407, 2} : new int[]{7, 0x400, 2},
                fixture.rings.getSpillAnimationState().snapshot());
    }

    @Test void housekeepingBridgeDoesNotAdvanceTheClockWithoutAnObjectManager() {
        fixture();
        var rings = new RingManager(List.of(), null, null, null, mock(AudioManager.class));
        int[] initial = {0xFF, 0x2345, 2};
        rings.getSpillAnimationState().restore(initial);
        RingManagerInternalAccess.updateLostRingHousekeeping(rings);
        assertArrayEquals(initial, rings.getSpillAnimationState().snapshot());
        assertTrue(rings.getActiveLostRings().isEmpty());
    }

    @Test void restoredClockReplaysWithRecreatedOwnersAndHonorsTheCurrentPolicy() {
        var fixture = fixture();
        fixture.level.animatedPatternManager = new DefaultClock();
        fixture.rings.getSpillAnimationState().restore(new int[]{0x80, 0x1234, 2});
        var checkpoint = fixture.rings.capture();
        for (int i = 0; i < 17; i++) fixture.level.update();
        // Sum of the 17 successive counter values $80..$70 is $7F8.
        int[] expected = {0x6F, 0x1A2C, 1};
        assertArrayEquals(expected, fixture.rings.getSpillAnimationState().snapshot());

        var replacement = new RingManager(List.of(), null, fixture.level, null, mock(AudioManager.class));
        fixture.level.ringManager = replacement;
        replacement.restore(checkpoint);
        fixture.level.animatedPatternManager = new StoppedClock();
        for (int i = 0; i < 17; i++) fixture.level.update();
        assertArrayEquals(new int[]{0x80, 0x1234, 2}, replacement.getSpillAnimationState().snapshot());
        fixture.level.animatedPatternManager = new DefaultClock();
        for (int i = 0; i < 17; i++) fixture.level.update();
        assertArrayEquals(expected, replacement.getSpillAnimationState().snapshot());
    }

    private static Fixture fixture() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var context = SessionManager.openGameplaySession(new com.openggf.game.sonic3k.Sonic3kGameModule());
        GameplaySessionFactory.attachManagers(context, EngineServices.current());
        var level = context.getLevelManager();
        // This fixture owns no loaded terrain, camera deformation or palette pass.
        level.parallaxManager = null;
        level.animatedPaletteManager = null;
        level.levelGamestate = null;
        level.zoneFeatureProvider = null;
        var registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_3K);
        var services = new StubObjectServices() {
            @Override public WorldSession worldSession() { return level.getWorldSession(); }
            @Override public LevelManager levelManager() { return level; }
            @Override public ObjectManager objectManager() { return level.getObjectManager(); }
            @Override public RingManager ringManager() { return level.getRingManager(); }
        };
        var objects = new ObjectManager(List.of(), registry, 0, null, null, null, context.getCamera(), services);
        level.objectManager = objects;
        objects.reset(0);
        var frame = new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 0, false, false, 0)));
        var art = new RingSpriteSheet(new Pattern[]{new Pattern()}, List.of(frame, frame, frame), 1, 1, 1, 2);
        var rings = new RingManager(List.of(), art, level, null, mock(AudioManager.class));
        level.ringManager = rings;
        return new Fixture(context, level, rings, objects);
    }

    private record Fixture(GameplayModeContext context, LevelManager level, RingManager rings, ObjectManager objects) { }

    private static class DefaultClock implements AnimatedPatternManager, StageRingAnimationFrameProvider {
        int updates;
        @Override public int stageRingAnimationFrame() { return 3; }
        @Override public void update() { updates++; }
    }

    private static final class StoppedClock extends DefaultClock {
        @Override public boolean advancesSharedSpillAnimation() { return false; }
    }

    private static final class InertObject extends AbstractObjectInstance {
        private InertObject() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "Retained slot owner"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }
}
