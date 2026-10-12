package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.*;
import com.openggf.game.mutators.*;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.RingRules;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.LevelMutatorTestWorld;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestMutatorReturnRingDomain {
    @Test void bigRingAndCheckpointRestoreCannotUseInstalledInteriorExemption() {
        var provider = mock(BonusStageProvider.class);
        when(provider.getActiveType()).thenReturn(BonusStageType.GUMBALL);
        var services = new LevelMutatorTestWorld(new Sonic3kGameModule()) {
            @Override public BonusStageProvider bonusStageProviderOrNull() { return provider; }
        };
        services.policy(new LevelMutatorPolicy(Set.of(),true,false,false,false));
        assertTrue(LevelMutatorPolicyAccess.ringsAllowed(services), "interior puzzle remains playable");
        var objects = new ObjectManager(List.of(),null,0,null,null,null,null,services);
        var state = new LevelGamestate();
        var level = mock(LevelManager.class);
        when(level.getObjectManager()).thenReturn(objects);
        when(level.getLevelGamestate()).thenReturn(state);
        var player = mock(AbstractPlayableSprite.class);
        when(player.currentLevelManagerIfAvailable()).thenReturn(level);
        var saved = new BigRingReturnState(0x600,0x200,0x550,0x180,120,
                (byte)12,(byte)13,0x400,0,0,300L,2,0,0,false,-1,-1,-1,-1,0,0,0,0);
        saved.restoreToPlayer(player,mock(Camera.class),state);
        assertEquals(0,state.getRings());
        assertEquals(0,state.getRingExtraLifeFlags());
        verify(player).setCentreX((short)0x600);
        var checkpoint = new CheckpointState();
        checkpoint.restoreFromSaved(0x600,0x200,0x550,0x180,1);
        checkpoint.saveRingState(120,2);
        var module = mock(GameModule.class);
        var rules = mock(GameRules.class);
        var ringRules = mock(RingRules.class);
        when(module.getRules()).thenReturn(rules);
        when(rules.ring()).thenReturn(ringRules);
        when(ringRules.checkpointRestoresSavedRings()).thenReturn(true);
        try (var globals = mockStatic(GameServices.class)) {
            globals.when(GameServices::module).thenReturn(module);
            globals.when(GameServices::levelOrNull).thenReturn(level);
            checkpoint.restoreToPlayer(player,null);
        }
        verify(player).setRingCount(0);
        assertEquals(0,state.getRingExtraLifeFlags());
    }
}
