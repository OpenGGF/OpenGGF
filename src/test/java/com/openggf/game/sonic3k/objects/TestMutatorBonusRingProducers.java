package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameSound;
import com.openggf.game.*;
import com.openggf.game.mutators.LevelMutatorPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.LevelMutatorTestWorld;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestMutatorBonusRingProducers {
    private static final LevelMutatorPolicy NO_RINGS = new LevelMutatorPolicy(Set.of(),true,false,false,false);
    @BeforeEach void setup() { TestEnvironment.resetAll(); }
    @AfterEach void cleanup() { SessionManager.clear(); }

    @Test void mainLevelSlotRewardRetiresWithoutGrantSparkleSoundOrPendingCount() {
        var services = new Services(BonusStageType.NONE);
        services.policy(NO_RINGS);
        var controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.onRewardSpawned();
        var reward = new S3kSlotRingRewardObjectInstance(spawn(0),controller);
        reward.setServices(services);
        reward.activate();
        var player = player();
        for (int frame=0;frame<40;frame++) reward.tickSlotRuntime(frame,player);
        assertEquals(0,controller.activeRewardObjects());
        assertEquals(0,controller.rewardCount());
        assertTrue(reward.isDestroyed());
        assertFalse(reward.isActive());
        assertFalse(reward.isInSparkle());
        assertNoGrant(services,player);
    }

    @Test void activeSlotInteriorPreservesNativeGrantAndPendingCountTiming() {
        var services = new Services(BonusStageType.SLOT_MACHINE);
        services.policy(NO_RINGS);
        var controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.onRewardSpawned();
        var reward = new S3kSlotRingRewardObjectInstance(spawn(0),controller);
        reward.setServices(services);
        reward.activate();
        var player = player();
        for (int frame=0;frame<0x1A;frame++) reward.tickSlotRuntime(frame,player);
        assertEquals(0,controller.activeRewardObjects());
        assertEquals(1,controller.rewardCount());
        assertTrue(reward.isInSparkle());
        assertFalse(reward.isDestroyed());
        verify(player).addRings(1);
        assertEquals(1,services.savedRings);
        assertEquals(1,services.sounds);
        for (int frame=0x1A;frame<40;frame++) reward.tickSlotRuntime(frame,player);
        assertEquals(0,controller.activeRewardObjects());
        assertTrue(reward.isDestroyed());
    }

    @Test void deniedGumballTouchDoesNotConsumeThenReenablingAwardsNativeSavedAndHudAmounts() {
        var services = new Services(BonusStageType.NONE);
        services.policy(NO_RINGS);
        var item = new GumballItemObjectInstance(spawn(2));
        item.setServices(services);
        var player = player();
        assertEquals(0,item.getCollisionFlags());
        assertFalse(item.publishesTouchResponseListEntryThisFrame());
        item.onTouchResponse(player,null,0);
        item.update(0,player);
        assertFalse(item.isDestroyed());
        assertNoGrant(services,player);
        services.policy(LevelMutatorPolicy.STOCK);
        assertNotEquals(0,item.getCollisionFlags());
        assertTrue(item.publishesTouchResponseListEntryThisFrame());
        item.onTouchResponse(player,null,1);
        verify(player).addRings(10);
        assertEquals(20,services.savedRings);
        assertEquals(1,services.sounds);
        item.update(2,player);
        assertTrue(item.isDestroyed());
    }

    @Test void deniedEjectedGumballCannotGrantThroughManualProximityPoll() {
        var services = new Services(BonusStageType.NONE);
        services.policy(NO_RINGS);
        var item = new GumballItemObjectInstance(spawn(2),0,true);
        item.setServices(services);
        var player = player();
        item.update(0,player);
        assertNoGrant(services,player);
        services.policy(LevelMutatorPolicy.STOCK);
        item.update(1,player);
        verify(player).addRings(10);
        assertEquals(20,services.savedRings);
        assertEquals(1,services.sounds);
    }

    @Test void pachinkoDeferredTouchRechecksPolicyBeforeGrantAndCollection() {
        var services = new Services(BonusStageType.NONE);
        var item = GumballItemObjectInstance.createPachinkoItem(spawn(3));
        item.setServices(services);
        var player = player();
        item.onTouchResponse(player,null,0);
        services.policy(NO_RINGS);
        item.update(1,player);
        item.onTouchResponse(player,null,1);
        item.update(2,player);
        assertFalse(item.isDestroyed());
        assertEquals(0,item.getCollisionFlags());
        assertFalse(item.publishesTouchResponseListEntryThisFrame());
        assertNoGrant(services,player);
        services.policy(LevelMutatorPolicy.STOCK);
        item.onTouchResponse(player,null,2);
        item.update(3,player);
        verify(player).addRings(50); // Native table: high byte of y_pos $01 -> $32.
        assertEquals(50,services.savedRings);
        assertEquals(1,services.sounds);
        item.update(4,player);
        assertTrue(item.isDestroyed());
    }

    @Test void activeGumballAndPachinkoInteriorsKeepTheirNativeRingRewards() {
        var gumballServices = new Services(BonusStageType.GUMBALL);
        gumballServices.policy(NO_RINGS);
        var gumball = new GumballItemObjectInstance(spawn(2));
        gumball.setServices(gumballServices);
        var gumballPlayer = player();
        gumball.onTouchResponse(gumballPlayer,null,0);
        verify(gumballPlayer).addRings(10);
        assertEquals(20,gumballServices.savedRings);
        assertEquals(1,gumballServices.sounds);
        var pachinkoServices = new Services(BonusStageType.GLOWING_SPHERE);
        pachinkoServices.policy(NO_RINGS);
        var pachinko = GumballItemObjectInstance.createPachinkoItem(spawn(3));
        pachinko.setServices(pachinkoServices);
        var pachinkoPlayer = player();
        pachinko.onTouchResponse(pachinkoPlayer,null,0);
        pachinko.update(1,pachinkoPlayer);
        verify(pachinkoPlayer).addRings(50);
        assertEquals(50,pachinkoServices.savedRings);
        assertEquals(1,pachinkoServices.sounds);
    }

    @Test void noRingsDoesNotSuppressOtherGumballRewards() {
        var services = new Services(BonusStageType.NONE);
        services.policy(NO_RINGS);
        int lives = services.state.getLives();
        var item = new GumballItemObjectInstance(spawn(0));
        item.setServices(services);
        assertNotEquals(0,item.getCollisionFlags());
        assertTrue(item.publishesTouchResponseListEntryThisFrame());
        item.onTouchResponse(player(),null,0);
        assertEquals(lives+1,services.state.getLives());
    }

    private static ObjectSpawn spawn(int subtype) { return new ObjectSpawn(0x100,0x180,0xEB,subtype,0,false,0x180); }
    private static AbstractPlayableSprite player() {
        var player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short)0x100);
        when(player.getCentreY()).thenReturn((short)0x180);
        return player;
    }
    private static void assertNoGrant(Services services,AbstractPlayableSprite player) {
        verify(player,never()).addRings(anyInt());
        assertEquals(0,services.savedRings);
        assertEquals(0,services.sounds);
    }
    private static class Services extends LevelMutatorTestWorld {
        final GameStateManager state = new GameStateManager();
        final BonusStageProvider provider;
        int savedRings;
        int sounds;
        Services(BonusStageType type) {
            super(new Sonic3kGameModule());
            provider = type == BonusStageType.NONE ? NoOpBonusStageProvider.INSTANCE : mock(BonusStageProvider.class);
            if (type != BonusStageType.NONE) when(provider.getActiveType()).thenReturn(type);
        }
        @Override public BonusStageProvider bonusStageProviderOrNull() { return provider; }
        @Override public GameStateManager gameState() { return state; }
        @Override public void addBonusStageRings(int count) { savedRings += count; }
        @Override public void playSfx(GameSound sound) { sounds++; }
        @Override public void playSfx(int sound) { sounds++; }
    }
}
