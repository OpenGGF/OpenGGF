package com.openggf.game.mutators;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.objects.Sonic1MonitorObjectInstance;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.objects.MonitorObjectInstance;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kRingAwardService;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.level.rings.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.LevelMutatorTestWorld;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeRingMutatorEffects {
    private static final LevelMutatorPolicy NO_RINGS = new LevelMutatorPolicy(Set.of(), true, false, false, false);
    @BeforeEach void setup() { TestEnvironment.resetAll(); }
    @AfterEach void cleanup() { SessionManager.clear(); }

    @Test void allNativeMonitorRewardsDenyGrantAndSoundButStockStillAwardsTen() {
        for (int game = 1; game <= 3; game++) {
            var services = new CountingServices(switch (game) {
                case 1 -> new Sonic1GameModule(); case 2 -> new Sonic2GameModule(); default -> new Sonic3kGameModule();
            });
            var player = mock(AbstractPlayableSprite.class);
            int subtype = switch (game) { case 1 -> 6; case 2 -> 4; default -> 3; };
            final int nativeGame = game;
            var monitor = BoundMutatorObjectTestAccess.construct(services, () -> switch (nativeGame) {
                case 1 -> new S1Reward(subtype); case 2 -> new S2Reward(subtype); default -> new S3Reward(subtype);
            });
            services.policy(NO_RINGS);
            monitor.reward(player);
            verify(player, never()).addRings(anyInt());
            assertEquals(0, services.sounds);
            services.policy(LevelMutatorPolicy.STOCK);
            monitor.reward(player);
            verify(player).addRings(10);
            assertEquals(1, services.sounds);
        }
    }

    @Test void s3kGiveRingCannotAwardThresholdLifeOrMutateCountersWhileDenied() {
        var services = new CountingServices(new Sonic3kGameModule());
        services.state.setRings(99);
        services.policy(NO_RINGS);
        Sonic3kRingAwardService.giveOne(services, mock(AbstractPlayableSprite.class));
        assertEquals(99, services.state.getRings());
        assertEquals(0, services.state.getRingExtraLifeFlags());
        assertEquals(0, services.sounds);
        int lives = services.gameState.getLives();
        services.policy(LevelMutatorPolicy.STOCK);
        Sonic3kRingAwardService.giveOne(services, mock(AbstractPlayableSprite.class));
        assertEquals(100, services.state.getRings());
        assertEquals(2, services.state.getRingExtraLifeFlags());
        assertEquals(lives + 1, services.gameState.getLives());
        assertEquals(1, services.sounds);
    }

    @Test void ringTableKeepsSourceAndRewindIndicesWithoutCollectionSoundOrAttractionSlots() {
        var services = new CountingServices(new Sonic3kGameModule());
        services.policy(NO_RINGS);
        var objectManager = new ObjectManager(List.of(), null, 0, null, null, null, null, services);
        var level = mock(LevelManager.class);
        when(level.getObjectManager()).thenReturn(objectManager);
        when(level.getLevelGamestate()).thenReturn(services.state);
        var audio = mock(AudioManager.class);
        var first = new RingSpawn(160, 112);
        var duplicate = new RingSpawn(160, 112);
        var manager = new RingManager(List.of(first, duplicate), null, level, null, audio);
        manager.reset(0);
        var player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 160);
        when(player.getCentreY()).thenReturn((short) 112);
        when(player.getShieldType()).thenReturn(ShieldType.LIGHTNING);
        var before = manager.capture();
        manager.collectStageRings(player, 1);
        manager.attractStageRings(player);
        assertFalse(manager.collectPlacedRing(first, player, 1));
        assertFalse(manager.isCollected(first));
        assertFalse(manager.isCollected(duplicate));
        assertTrue(manager.getActiveSpawns().isEmpty());
        assertSame(first, manager.resolveCanonicalSpawn(160, 112));
        verifyNoInteractions(audio);
        verify(player, never()).addRings(anyInt());
        services.policy(LevelMutatorPolicy.STOCK);
        manager.restore(before);
        assertEquals(2, manager.getActiveSpawns().size());
        assertTrue(manager.collectPlacedRing(first, player, 2));
        assertTrue(manager.isCollected(first));
        assertFalse(manager.isCollected(duplicate));
    }

    @Test void interiorRewardsRemainPlayableButMainReturnInventoryIsZero() {
        var services = new CountingServices(new Sonic3kGameModule()) {
            private final BonusStageProvider interior = interiorProvider();
            @Override public BonusStageProvider bonusStageProviderOrNull() { return interior; }
        };
        services.policy(NO_RINGS);
        assertEquals(RingAcquisitionDomain.STAGE_INTERIOR, LevelMutatorPolicyAccess.domain(services));
        var player = mock(AbstractPlayableSprite.class);
        Sonic3kRingAwardService.giveOne(services, player);
        assertEquals(1, services.state.getRings());
        assertEquals(1, services.sounds);
        assertEquals(0, LevelMutatorPolicyAccess.mainLevelRingRestore(services, 50));
        var main = new CountingServices(new Sonic3kGameModule());
        main.policy(NO_RINGS);
        Sonic3kRingAwardService.giveOne(main, player);
        assertEquals(0, main.state.getRings());
    }

    private static BonusStageProvider interiorProvider() {
        var provider = mock(BonusStageProvider.class);
        when(provider.getActiveType()).thenReturn(BonusStageType.GUMBALL);
        return provider;
    }

    @Test void installedInertBonusProviderCannotExemptMainLevelRings() {
        var services = new CountingServices(new Sonic3kGameModule()) {
            @Override public BonusStageProvider bonusStageProviderOrNull() { return NoOpBonusStageProvider.INSTANCE; }
        };
        services.policy(NO_RINGS);
        assertEquals(RingAcquisitionDomain.MAIN_LEVEL, LevelMutatorPolicyAccess.domain(services));
        Sonic3kRingAwardService.giveOne(services, mock(AbstractPlayableSprite.class));
        assertEquals(0, services.state.getRings());
        assertEquals(0, services.sounds);
    }

    @Test void deniedNativeSlotPrizeRetiresOnceAndReleasesMachineCounter() {
        var services = new CountingServices(new Sonic2GameModule());
        services.policy(NO_RINGS);
        int[] pending = {1};
        var prize = BoundMutatorObjectTestAccess.construct(services,
                () -> new com.openggf.game.sonic2.objects.RingPrizeObjectInstance(160,112,160,112,0,pending));
        var player = mock(AbstractPlayableSprite.class);
        prize.update(1, player);
        prize.update(2, player);
        assertTrue(prize.isDestroyed());
        assertEquals(0, pending[0]);
        verify(player, never()).addRings(anyInt());
        assertEquals(0, services.sounds);
    }

    private interface Reward { void reward(PlayableEntity player); }
    private static class S1Reward extends Sonic1MonitorObjectInstance implements Reward {
        S1Reward(int subtype) { super(new ObjectSpawn(160,112,0x26,subtype,0,false,112)); }
        public void reward(PlayableEntity player) { applyPowerup(player); }
    }
    private static class S2Reward extends MonitorObjectInstance implements Reward {
        S2Reward(int subtype) { super(new ObjectSpawn(160,112,0x26,subtype,0,false,112), "Monitor"); }
        public void reward(PlayableEntity player) { applyPowerup(player); }
    }
    private static class S3Reward extends Sonic3kMonitorObjectInstance implements Reward {
        S3Reward(int subtype) { super(new ObjectSpawn(160,112,0x01,subtype,0,false,112)); }
        public void reward(PlayableEntity player) { applyPowerup(player); }
    }
    private static class CountingServices extends LevelMutatorTestWorld {
        final LevelGamestate state = new LevelGamestate();
        final GameStateManager gameState = new GameStateManager();
        int sounds;
        CountingServices(GameModule module) { super(module); }
        @Override public LevelState levelGamestate() { return state; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public void playSfx(GameSound sound) { sounds++; }
        @Override public void playSfx(int sound) { sounds++; }
        @Override public void playMusic(int sound) { sounds++; }
        @Override public void playMusicMailboxNativeRequest(int sound) { sounds++; }
    }
}
