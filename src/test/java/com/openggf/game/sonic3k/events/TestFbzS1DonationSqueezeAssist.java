package com.openggf.game.sonic3k.events;

import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GroundMode;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic3k.objects.FbzElevatorObjectInstance;
import com.openggf.game.sonic3k.objects.FbzMovingSqueezeTraversal;
import com.openggf.game.sonic3k.objects.Sonic3kInvisibleBlockObjectInstance;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.S1DonationSqueezeAssistState;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzS1DonationSqueezeAssist {

    @Test
    void exactRomGapBoundaryRequiresTwentyNinePixelsForRollingSonic() {
        assertEquals(false,
                FbzS1DonationSqueezeAssist.isFullSolidGapSafe(28, 19, 14));
        assertEquals(true,
                FbzS1DonationSqueezeAssist.isFullSolidGapSafe(29, 19, 14));
    }

    @Test
    void s1OrdinaryRollReceivesTraceGroundedFloorOnlyForExactSafeEpisode() {
        var decision = FbzS1DonationSqueezeAssist.resolve(
                S1DonationSqueezeAssistState.ARMED,
                true, false,
                true, true, true,
                false, true,
                0x0318);

        assertEquals(0x0800, decision.groundSpeed());
        assertEquals(S1DonationSqueezeAssistState.CONSUMED,
                decision.nextState());
    }

    @Test
    void ablationLeavesUnsafeS1EpisodeUnassistedWhenExactPairIsAbsent() {
        var decision = FbzS1DonationSqueezeAssist.resolve(
                S1DonationSqueezeAssistState.ARMED,
                true, false,
                false, true, true,
                false, true,
                0x0318);

        assertEquals(0x0318, decision.groundSpeed());
        assertEquals(S1DonationSqueezeAssistState.ARMED,
                decision.nextState());
    }

    @Test
    void nativeAndSpindashCapableDonationNeverConsumeS1Assist() {
        for (boolean donationActive : new boolean[]{false, true}) {
            var decision = FbzS1DonationSqueezeAssist.resolve(
                    S1DonationSqueezeAssistState.ARMED,
                    donationActive, true,
                    true, true, true,
                    false, true,
                    0x0318);
            assertEquals(0x0318, decision.groundSpeed());
            assertEquals(S1DonationSqueezeAssistState.ARMED,
                    decision.nextState());
        }
    }

    @Test
    void consumedEpisodeRearmsOnlyAfterExactPairEnds() {
        var retained = FbzS1DonationSqueezeAssist.resolve(
                S1DonationSqueezeAssistState.CONSUMED,
                true, false,
                true, true, true,
                false, true,
                0x0800);
        assertEquals(S1DonationSqueezeAssistState.CONSUMED,
                retained.nextState());

        var ended = FbzS1DonationSqueezeAssist.resolve(
                S1DonationSqueezeAssistState.CONSUMED,
                true, false,
                false, false, false,
                false, false,
                0x0700);
        assertEquals(S1DonationSqueezeAssistState.ARMED,
                ended.nextState());
    }
    @Test
    void armedNativeAndSpindashCapablePlayNeverEnterGeometryDiscovery() {
        for (boolean donation : new boolean[]{false, true}) {
            for (GameRules rules : new GameRules[]{GameRules.SONIC_1, GameRules.SONIC_3K}) {
                if (donation && rules == GameRules.SONIC_1) continue;
                var player = rollingPlayer(rules, 0x318);
                var runtime = runtime(S1DonationSqueezeAssistState.ARMED);
                var objects = mock(ObjectManager.class);
                try (var provider = mockStatic(CrossGameFeatureProvider.class);
                     var geometry = mockStatic(FbzMovingSqueezeTraversal.class, CALLS_REAL_METHODS)) {
                    provider.when(CrossGameFeatureProvider::isActive).thenReturn(donation);
                    FbzS1DonationSqueezeAssist.applyToMainPlayer(player, runtime, objects);
                    geometry.verifyNoInteractions();
                    verifyNoInteractions(objects);
                    verify(player, never()).setGSpeed(anyShort());
                    verify(runtime, never()).setS1DonationSqueezeAssistState(any());
                }
            }
        }
    }

    @Test
    void armedIncompatibleInputAndSpeedBoundariesAvoidDiscovery() {
        for (int variant = 0; variant < 9; variant++) {
            int speed = switch (variant) { case 0 -> -1; case 1 -> 0; case 2 -> 0x800;
                case 3 -> 0x801; default -> 0x7FF; };
            var player = rollingPlayer(GameRules.SONIC_1, speed);
            if (variant == 4) when(player.getAir()).thenReturn(true);
            if (variant == 5) when(player.getRolling()).thenReturn(false);
            if (variant == 6) when(player.getSpindash()).thenReturn(true);
            if (variant == 7) when(player.isRightPressed()).thenReturn(false);
            if (variant == 8) when(player.isDownPressed()).thenReturn(false);
            var runtime = runtime(S1DonationSqueezeAssistState.ARMED);
            var objects = mock(ObjectManager.class);
            try (var provider = mockStatic(CrossGameFeatureProvider.class);
                 var geometry = mockStatic(FbzMovingSqueezeTraversal.class, CALLS_REAL_METHODS)) {
                provider.when(CrossGameFeatureProvider::isActive).thenReturn(true);
                FbzS1DonationSqueezeAssist.applyToMainPlayer(player, runtime, objects);
                geometry.verifyNoInteractions();
                verify(player, never()).setGSpeed(anyShort());
            }
        }
    }

    @Test
    void eligibleArmedProductionPathReusesBothWinningProjections() {
        for (int speed : new int[]{1, 0x318, 0x7FF}) {
            var player = rollingPlayer(GameRules.SONIC_1, speed);
            var runtime = runtime(S1DonationSqueezeAssistState.ARMED);
            var objects = mock(ObjectManager.class);
            var block = new Sonic3kInvisibleBlockObjectInstance(new ObjectSpawn(80, 0, 0x28, 0, 0, false, 0));
            block.setSlotIndex(1);
            block.snapshotPreUpdatePosition();
            assertTrue(FbzMovingSqueezeTraversal.isLiveNormalInvisibleSolid(block, player));
            var car = new FbzElevatorObjectInstance.Car(new ObjectSpawn(80, 100, 0, 10, 0, false, 0));
            car.setSlotIndex(2);
            var episode = new FbzMovingSqueezeTraversal.Episode(block, car);
            when(objects.activeObjectsOfType(Sonic3kInvisibleBlockObjectInstance.class)).thenReturn(List.of(block));
            when(objects.activeObjectsOfType(FbzElevatorObjectInstance.Car.class)).thenReturn(List.of(car));
            var nativeFloor = new FbzMovingSqueezeTraversal.Projection(true, true, true, true, false, 70, 29, 0x800, 150);
            var current = new FbzMovingSqueezeTraversal.Projection(false, false, true, true, true, 70, 28, speed, -1);
            try (var provider = mockStatic(CrossGameFeatureProvider.class);
                 var geometry = mockStatic(FbzMovingSqueezeTraversal.class, invocation -> {
                     if (invocation.getMethod().getName().equals("project")) {
                         assertEquals(episode, invocation.getArgument(0));
                         return (int) invocation.getArgument(2) == 0x800 ? nativeFloor : current;
                     }
                     return invocation.callRealMethod();
                 })) {
                provider.when(CrossGameFeatureProvider::isActive).thenReturn(true);
                FbzS1DonationSqueezeAssist.applyToMainPlayer(player, runtime, objects);
                geometry.verify(() -> FbzMovingSqueezeTraversal.project(episode, player, speed), times(1));
                geometry.verify(() -> FbzMovingSqueezeTraversal.project(episode, player, 0x800), times(1));
                verify(player).setGSpeed((short) 0x800);
                verify(runtime).setS1DonationSqueezeAssistState(S1DonationSqueezeAssistState.CONSUMED);
            }
        }
    }

    @Test
    void consumedProductionPathStillUsesExactDiscoveryAcrossDonorAndCapabilityChanges() {
        for (boolean donation : new boolean[]{false, true}) {
            for (GameRules rules : new GameRules[]{GameRules.SONIC_1, GameRules.SONIC_3K}) {
                for (boolean active : new boolean[]{false, true}) {
                    var player = rollingPlayer(rules, 0x800);
                    when(player.isDownPressed()).thenReturn(false);
                    var runtime = runtime(S1DonationSqueezeAssistState.CONSUMED);
                    var objects = mock(ObjectManager.class);
                    var episode = new FbzMovingSqueezeTraversal.Episode(
                            mock(Sonic3kInvisibleBlockObjectInstance.class), mock(FbzElevatorObjectInstance.Car.class));
                    try (var provider = mockStatic(CrossGameFeatureProvider.class);
                         var geometry = mockStatic(FbzMovingSqueezeTraversal.class, invocation -> {
                             if (invocation.getMethod().getName().equals("findEpisode")) {
                                 return active ? Optional.of(episode) : Optional.empty();
                             }
                             return invocation.callRealMethod();
                         })) {
                        provider.when(CrossGameFeatureProvider::isActive).thenReturn(donation);
                        FbzS1DonationSqueezeAssist.applyToMainPlayer(player, runtime, objects);
                        geometry.verify(() -> FbzMovingSqueezeTraversal.findEpisode(objects, player));
                        verify(runtime, active ? never() : times(1))
                                .setS1DonationSqueezeAssistState(S1DonationSqueezeAssistState.ARMED);
                        verify(player, never()).setGSpeed(anyShort());
                    }
                }
            }
        }
    }

    @Test
    void missingCapabilitiesPreserveConsumedStateWithoutGeometryWork() {
        for (GameRules rules : new GameRules[]{null, mock(GameRules.class)}) {
            var player = rollingPlayer(rules, 0x318);
            var runtime = runtime(S1DonationSqueezeAssistState.CONSUMED);
            var objects = mock(ObjectManager.class);
            try (var geometry = mockStatic(FbzMovingSqueezeTraversal.class, CALLS_REAL_METHODS)) {
                FbzS1DonationSqueezeAssist.applyToMainPlayer(player, runtime, objects);
                geometry.verifyNoInteractions();
                verify(runtime, never()).setS1DonationSqueezeAssistState(any());
            }
        }
    }

    @Test
    void staleConsumedEpisodeRearmsThroughRealDiscoveryAtSpeedBoundaries() {
        for (int speed : new int[]{-1, 0, 0x800, 0x801}) {
            var player = rollingPlayer(GameRules.SONIC_3K, speed);
            when(player.isDownPressed()).thenReturn(false);
            var runtime = runtime(S1DonationSqueezeAssistState.CONSUMED);
            var objects = mock(ObjectManager.class);
            when(objects.activeObjectsOfType(Sonic3kInvisibleBlockObjectInstance.class)).thenReturn(List.of());
            FbzS1DonationSqueezeAssist.applyToMainPlayer(player, runtime, objects);
            verify(runtime).setS1DonationSqueezeAssistState(S1DonationSqueezeAssistState.ARMED);
            verify(objects, speed > 0 ? times(1) : never())
                    .activeObjectsOfType(Sonic3kInvisibleBlockObjectInstance.class);
            verify(player, never()).setGSpeed(anyShort());
        }
    }

    private static FbzZoneRuntimeState runtime(S1DonationSqueezeAssistState state) {
        var runtime = mock(FbzZoneRuntimeState.class);
        when(runtime.s1DonationSqueezeAssistState()).thenReturn(state);
        return runtime;
    }

    private static AbstractPlayableSprite rollingPlayer(GameRules rules, int speed) {
        var player = mock(AbstractPlayableSprite.class);
        when(player.getGameRules()).thenReturn(rules);
        when(player.getGSpeed()).thenReturn((short) speed);
        when(player.getGroundMode()).thenReturn(GroundMode.GROUND);
        when(player.getRolling()).thenReturn(true);
        when(player.isRightPressed()).thenReturn(true);
        when(player.isDownPressed()).thenReturn(true);
        when(player.getCentreY()).thenReturn((short) 86);
        when(player.getYRadius()).thenReturn((short) 14);
        when(player.getRollYRadius()).thenReturn((short) 14);
        when(player.getStandYRadius()).thenReturn((short) 19);
        return player;
    }

}
