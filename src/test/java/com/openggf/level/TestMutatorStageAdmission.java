package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.*;
import com.openggf.game.mutators.*;
import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.objects.Sonic1GiantRingObjectInstance;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.LevelMutatorTestWorld;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestMutatorStageAdmission {
    private static final LevelMutatorPolicy NO_ENTRY = new LevelMutatorPolicy(Set.of(), false, false, true, true);
    @BeforeEach void setup() { TestEnvironment.resetAll(); AbstractObjectInstance.updateCameraBounds(0,0,320,224,0); }
    @AfterEach void cleanup() { com.openggf.game.session.SessionManager.clear(); }

    @Test void sonic1GiantRingDenialPrecedesSoundAndCaptureAndStockStillCollects() {
        var services = new Services(new Sonic1GameModule());
        var player = player();
        var ring = BoundMutatorObjectTestAccess.construct(services,
                () -> new Sonic1GiantRingObjectInstance(new ObjectSpawn(160,112,0x4B,0,0,false,112)));
        ring.update(1, player);
        services.policy(NO_ENTRY);
        ring.onTouchResponse(player, new TouchResponseResult(0,0,0,TouchCategory.SPECIAL), 1);
        ring.update(2, player);
        assertEquals(0, services.sounds);
        verify(player, never()).setHidden(anyBoolean());
        services.policy(LevelMutatorPolicy.STOCK);
        ring.onTouchResponse(player, new TouchResponseResult(0,0,0,TouchCategory.SPECIAL), 2);
        ring.update(3, player);
        assertEquals(1, services.sounds);
        assertEquals(1, services.objectManager().getActiveObjects().stream()
                .filter(o -> o.getClass().getSimpleName().equals("Sonic1RingFlashObjectInstance")).count());
    }

    @Test void sonic3kGiantRingDenialLeavesFormationAndPlayerUntouched() {
        var services = new Services(new Sonic3kGameModule());
        services.policy(NO_ENTRY);
        var player = player();
        var ring = BoundMutatorObjectTestAccess.construct(services,
                () -> new Sonic3kSSEntryRingObjectInstance(new ObjectSpawn(160,112,0x85,0,0,false,112)));
        for (int frame=1; frame<=45; frame++) ring.update(frame, player);
        assertFalse(ring.isDestroyed());
        assertEquals(0, services.sounds);
        verify(player, never()).setHidden(anyBoolean());
        verify(player, never()).applyObjectControlState(any());
        services.policy(LevelMutatorPolicy.STOCK);
        ring.update(46, player);
        verify(player).setHidden(true);
        assertEquals(1, services.sounds);
        services.policy(NO_ENTRY);
        for (int frame=47; frame<180; frame++) {
            for (var object : List.copyOf(services.objectManager().getActiveObjects())) object.update(frame, player);
        }
        assertTrue(services.transitions.isSpecialStageRequested(), "accepted capture survives a late policy change");
        assertEquals(2, services.sounds);
    }

    @Test void existingSonic2SpecialStarsCannotMarkPostUsedUnderNewDenial() {
        var services = new Services(new com.openggf.game.sonic2.Sonic2GameModule());
        var parent = mock(com.openggf.game.sonic2.objects.CheckpointObjectInstance.class);
        when(parent.getCenterX()).thenReturn(160);
        when(parent.getCenterY()).thenReturn(112);
        var star = BoundMutatorObjectTestAccess.construct(services,
                () -> new com.openggf.game.sonic2.objects.CheckpointStarInstance(parent,0));
        var player = player();
        services.policy(NO_ENTRY);
        star.onTouchResponse(player,new TouchResponseResult(0,0,0,TouchCategory.SPECIAL),1);
        star.update(1,player);
        verify(parent,never()).markUsedForSpecialStage();
        assertFalse(services.transitions.isSpecialStageRequested());
        services.policy(LevelMutatorPolicy.STOCK);
        star.onTouchResponse(player,new TouchResponseResult(0,0,0,TouchCategory.SPECIAL),2);
        star.update(2,player);
        verify(parent).markUsedForSpecialStage();
        assertTrue(services.transitions.isSpecialStageRequested());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(Sonic3kStarPostObjectInstance.BonusStarVariant.class)
    void existingSonic3kBonusStarsRemainUnusedAndReenterWhenEnabled(Sonic3kStarPostObjectInstance.BonusStarVariant variant) {
        var services = new Services(new Sonic3kGameModule());
        var parent = mock(Sonic3kStarPostObjectInstance.class);
        when(parent.getCenterX()).thenReturn(160);
        when(parent.getCenterY()).thenReturn(112);
        var star = BoundMutatorObjectTestAccess.construct(services,
                () -> new Sonic3kStarPostBonusStarChild(parent,0,variant));
        for (int i=1;i<=128;i++) star.update(i,null);
        var player = player();
        placePlayerAtBonusStar(player, star);
        services.policy(NO_ENTRY);
        star.update(129,player);
        verify(parent,never()).markUsedForSpecialStage();
        assertNull(services.transitions.peekBonusStageRequest());
        assertFalse(star.isDestroyed());
        services.policy(LevelMutatorPolicy.STOCK);
        placePlayerAtBonusStar(player, star);
        star.update(130,player);
        verify(parent).markUsedForSpecialStage();
        assertEquals(variant.bonusStageType,services.transitions.peekBonusStageRequest());
        assertTrue(star.isDestroyed());
    }

    @Test void hpzDenialPrecedesControlLockAndAcceptedSelectionRewindsAndPublishesAfterLateToggle() {
        var services = new Services(new Sonic3kGameModule());
        var progression = S3kEmeraldProgression.restore(services.gameState, List.of(0,0,2,0,0,0,0), true);
        var sanctuary = new S3kSanctuaryRuntimeState(progression,true);
        var pedestal = BoundMutatorObjectTestAccess.construct(services, () -> new HPZSuperEmeraldObjectInstance(
                new ObjectSpawn(0,0,0xB4,2,0,false,0), progression, sanctuary));
        var player = player();
        services.policy(NO_ENTRY);
        assertFalse(HpzMutatorSelectionTestAccess.beginSelection(pedestal, player));
        verify(player, never()).applyObjectControlState(any());
        services.policy(LevelMutatorPolicy.STOCK);
        assertTrue(HpzMutatorSelectionTestAccess.beginSelection(pedestal, player));
        for (int i=0;i<5;i++) pedestal.updateSelection();
        var objectSnapshot = pedestal.captureRewindState();
        var permits = services.runtime().capture();
        services.policy(NO_ENTRY);
        for (int i=0;i<11;i++) pedestal.updateSelection();
        assertEquals(new SpecialStageEntryRequest(2, EmeraldRewardKind.SUPER_EMERALD),
                services.transitions.consumeSpecialStageEntryRequest());
        services.runtime().restore(permits);
        pedestal.restoreRewindState(objectSnapshot, RewindCaptureContext.none());
        for (int i=0;i<10;i++) pedestal.updateSelection();
        assertFalse(services.transitions.isSpecialStageRequested());
        pedestal.updateSelection();
        assertEquals(new SpecialStageEntryRequest(2, EmeraldRewardKind.SUPER_EMERALD),
                services.transitions.consumeSpecialStageEntryRequest());
    }

    @Test void specialAndBonusDenialsRemainIndependentForEveryCombination() {
        for (boolean noSpecial : new boolean[]{false,true}) {
            for (boolean noBonus : new boolean[]{false,true}) {
                var services = new Services(new Sonic3kGameModule());
                services.policy(new LevelMutatorPolicy(Set.of(),false,false,noSpecial,noBonus));
                services.transitions.requestSpecialStageEntry();
                services.transitions.requestBonusStageEntry(BonusStageType.GUMBALL);
                assertEquals(!noSpecial,services.transitions.isSpecialStageRequested());
                assertEquals(noBonus ? null : BonusStageType.GUMBALL,services.transitions.peekBonusStageRequest());
            }
        }
    }

    @Test void allEmeraldGiantRingRewardUsesRingPolicyIndependentlyOfEntryPolicy() {
        var services = new Services(new Sonic3kGameModule());
        for (int index=0;index<7;index++) services.gameState.markEmeraldCollected(index);
        services.policy(new LevelMutatorPolicy(Set.of(),true,false,true,true));
        var player = player();
        var ring = BoundMutatorObjectTestAccess.construct(services,
                () -> new Sonic3kSSEntryRingObjectInstance(new ObjectSpawn(160,112,0x85,0,0,false,112)));
        for (int frame=1;frame<=45;frame++) ring.update(frame,player);
        verify(player,never()).addRings(anyInt());
        verify(player,never()).setHidden(anyBoolean());
        assertEquals(0,services.sounds);
        services.policy(NO_ENTRY);
        ring.update(46,player);
        verify(player).addRings(50);
        verify(player,never()).setHidden(anyBoolean());
        assertEquals(2,services.sounds);
        assertFalse(services.transitions.isSpecialStageRequested());
    }

    @Test void directTransitionRequestsAreDefendedAndScopedTrustedPermitAllowsOnlyMatchingKind() {
        var services = new Services(new Sonic3kGameModule());
        long permit = services.runtime().tryAdmit(StageEntryKind.BONUS);
        services.policy(NO_ENTRY);
        services.transitions.requestSpecialStageEntry();
        services.transitions.advanceToSpecialStageEntryRoutine();
        services.transitions.requestBonusStageEntry(BonusStageType.GUMBALL);
        assertFalse(services.transitions.isSpecialStageRequested());
        assertNull(services.transitions.consumeBonusStageRequest());
        services.runtime().publish(StageEntryKind.BONUS, permit, () -> {
            services.transitions.requestBonusStageEntry(BonusStageType.GUMBALL);
            services.transitions.requestSpecialStageEntry();
        });
        assertEquals(BonusStageType.GUMBALL, services.transitions.consumeBonusStageRequest());
        assertFalse(services.transitions.isSpecialStageRequested());
    }

    private AbstractPlayableSprite player() {
        var player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short)160);
        when(player.getCentreY()).thenReturn((short)112);
        when(player.getRingCount()).thenReturn(50);
        when(player.getYRadius()).thenReturn((short)19);
        return player;
    }

    private static void placePlayerAtBonusStar(AbstractPlayableSprite player, Sonic3kStarPostBonusStarChild star) {
        var state = GenericFieldCapturer.captureObjectSubclassScalars(star);
        var type = Sonic3kStarPostBonusStarChild.class.getName();
        when(player.getCentreX()).thenReturn(assertInstanceOf(Integer.class,
                state.value(new FieldKey(type, "currentX"))).shortValue());
        when(player.getCentreY()).thenReturn(assertInstanceOf(Integer.class,
                state.value(new FieldKey(type, "currentY"))).shortValue());
    }
    private static class Services extends LevelMutatorTestWorld {
        final GameStateManager gameState = new GameStateManager();
        final LevelGamestate level = new LevelGamestate();
        final Camera camera = mock(Camera.class);
        final LevelTransitionCoordinator transitions;
        final ObjectManager manager;
        int sounds;
        Services(GameModule module) {
            super(module);
            withPlayerQuery(new ObjectPlayerQuery(() -> null, List::of));
            transitions = new LevelTransitionCoordinator(worldSession());
            when(camera.getWidth()).thenReturn((short)320);
            when(camera.getHeight()).thenReturn((short)224);
            manager = new ObjectManager(List.of(),null,0,null,null,null,camera,this);
            manager.reset(0);
        }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public LevelState levelGamestate() { return level; }
        @Override public Camera camera() { return camera; }
        @Override public ObjectManager objectManager() { return manager; }
        @Override public void playSfx(int id) { sounds++; }
        @Override public void requestBonusStageEntry(BonusStageType type) { transitions.requestBonusStageEntry(type); }
        @Override public void requestSpecialStageEntry() { transitions.requestSpecialStageEntry(); }
        @Override public void requestSpecialStageEntry(SpecialStageEntryRequest request) { transitions.requestSpecialStageEntry(request); }
    }
}
