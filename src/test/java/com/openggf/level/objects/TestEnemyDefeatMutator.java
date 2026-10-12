package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.tests.MutatorPhysicsWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestEnemyDefeatMutator {
    @ParameterizedTest
    @CsvSource({"-1024,90,-1152", "1024,90,-1536", "1024,110,1152", "-256,90,0",
            "30000,90,-3072", "-32768,90,-3072"})
    void scalesResolvedNativeWordOnceAndCapsOnlyVerticalVelocity(int before, int playerY, int expected) {
        var world = MutatorPhysicsWorld.create(() -> new GameplayMutatorPolicy(100, 0, 150, 0xC00, 100, false));
        var player = mock(PlayableEntity.class);
        when(player.getYSpeed()).thenReturn((short) before);
        when(player.getCentreY()).thenReturn((short) playerY);
        EnemyDefeatBounce.apply(player, 100, world);
        verify(player, times(1)).setYSpeed((short) expected);
        verify(player, never()).setXSpeed(anyShort());
        verify(player, never()).setGSpeed(anyShort());
        verify(player, never()).setAir(anyBoolean());
    }

    @Test void worldsAndStockHelperRemainIndependentIncludingNativeOverflow() {
        var left = MutatorPhysicsWorld.create(() -> new GameplayMutatorPolicy(100, 0, 150, 0xC00, 100, false));
        var right = MutatorPhysicsWorld.create(() -> new GameplayMutatorPolicy(100, 0, 300, 0xC00, 100, false));
        var stock = MutatorPhysicsWorld.create(() -> GameplayMutatorPolicy.STOCK);
        var player = mock(PlayableEntity.class);
        when(player.getYSpeed()).thenReturn((short) 0x100);
        when(player.getCentreY()).thenReturn((short) 90);
        EnemyDefeatBounce.apply(player, 100, left);
        EnemyDefeatBounce.apply(player, 100, right);
        verify(player).setYSpeed((short) -0x180);
        verify(player).setYSpeed((short) -0x300);
        clearInvocations(player);
        when(player.getYSpeed()).thenReturn(Short.MIN_VALUE);
        EnemyDefeatBounce.apply(player, 100);
        EnemyDefeatBounce.apply(player, 100, stock);
        verify(player, times(2)).setYSpeed((short) (Short.MIN_VALUE + 0x100));
    }
}
