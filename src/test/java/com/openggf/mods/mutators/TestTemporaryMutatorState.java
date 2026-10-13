package com.openggf.mods.mutators;

import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestTemporaryMutatorState {
    private TemporaryMutatorState state(AtomicInteger preparations) {
        var definition = new MutatorDefinition("gravity", "Gravity", "", MutatorScope.LIVE,
                MutatorScope.LIVE, List.of(new MutatorOption.IntegerSlider("percent", "Percent", "",
                MutatorScope.LIVE, 100, 25, 200, 5, "%")), Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                options -> { preparations.incrementAndGet(); return List.of(new MutatorPolicy.DrySonicGravity(options.integer("percent"))); });
        var faults = mock(ModFaultBoundary.class);
        when(faults.isOwnerAvailable(anyString())).thenReturn(true);
        when(faults.call(anyString(), any())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(1)).get());
        var pulse=new MutatorDefinition("pulse","Pulse","",MutatorScope.LIVE,MutatorScope.LIVE,List.of(),
                Set.of(MutatorCapability.PLAYER_MOTION),options -> List.of(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.MOMENTUM_PULSE,0)));
        return new TemporaryMutatorState(7,List.of(new OwnedMutator("alpha",definition),new OwnedMutator("alpha",pulse)),faults);
    }

    @Test void expiryAndReplacePreserveIdentityAndNeverEditUserPreferences() {
        var count = new AtomicInteger(); var state = state(count);
        try (var port = state.open("alpha", Set.of(MutatorCapability.DRY_SONIC_GRAVITY))) {
            var accepted = port.activate("gravity", Map.of("percent", 50), 4);
            assertTrue(accepted.accepted());
            assertTrue(port.replace(accepted.activationId(), Map.of("percent", 75)).accepted());
            assertEquals(accepted.activationId(), port.active().getFirst().activationId());
            assertEquals(4, port.active().getFirst().remainingPresentations());
            assertEquals(new MutatorPolicy.DrySonicGravity(75), state.entries().getFirst().policy());
            state.advancePresentation(); state.advancePresentation(); state.advancePresentation();
            assertEquals(1, port.active().getFirst().remainingPresentations());
            state.advancePresentation(); assertTrue(state.entries().isEmpty());
        }
        assertEquals(2, count.get());
    }

    @Test void oneShotWaitsForNativeAcknowledgementAndRewindsToPending() {
        var state = state(new AtomicInteger());
        try (var port = state.open("alpha", Set.of(MutatorCapability.PLAYER_MOTION))) {
            var accepted=port.activate("pulse",Map.of(),0);assertTrue(accepted.accepted());
            long id=accepted.activationId();
            var before = state.capture();
            for (int i=0; i<20; i++) state.advancePresentation();
            assertTrue(state.entries().getFirst().oneShot());
            assertTrue(state.acknowledge(id)); assertFalse(state.acknowledge(id));
            assertTrue(state.entries().isEmpty());
            state.restore(before);
            assertEquals(id, state.entries().getFirst().activationId());
            assertTrue(state.acknowledge(id));
        }
    }

    @Test void restoreRetainsPreparedPoliciesAndBranchesNeverReuseActivationIdentity() {
        var count=new AtomicInteger();var state=state(count);
        try(var port=state.open("alpha",Set.of(MutatorCapability.DRY_SONIC_GRAVITY))) {
            long first=port.activate("gravity",Map.of("percent",50),4).activationId();
            var snapshot=state.capture();
            port.replace(first,Map.of("percent",75));assertEquals(2,count.get());
            state.restore(snapshot);
            assertEquals(new MutatorPolicy.DrySonicGravity(50),state.entries().getFirst().policy());
            assertEquals(2,count.get(),"restore uses accepted values without invoking factories");
            port.cancel(first);
            long branch=port.activate("gravity",Map.of("percent",100),4).activationId();
            assertNotEquals(first,branch);
        }
    }

    @Test void modalHoldAgesItsOwnLeaseWhileOrdinaryChaosClockAndEffectsFreeze() {
        var faults=mock(ModFaultBoundary.class);
        when(faults.isOwnerAvailable(anyString())).thenReturn(true);
        when(faults.call(anyString(),any())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(1)).get());
        var hold=new MutatorDefinition("hold","Hold","",MutatorScope.LIVE,MutatorScope.LIVE,List.of(),
                Set.of(MutatorCapability.GAMEPLAY_HOLD),options -> List.of(new GameplayHoldPolicy(3)));
        var gravity=new MutatorDefinition("gravity","Gravity","",MutatorScope.LIVE,MutatorScope.LIVE,List.of(),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY),options -> List.of(new MutatorPolicy.DrySonicGravity(50)));
        var state=new TemporaryMutatorState(8,List.of(new OwnedMutator("alpha",hold),new OwnedMutator("alpha",gravity)),faults);
        try(var port=state.open("alpha",Set.of(MutatorCapability.GAMEPLAY_HOLD,MutatorCapability.DRY_SONIC_GRAVITY))) {
            assertFalse(port.activate("hold",Map.of(),0).accepted());
            assertFalse(port.activate("hold",Map.of(),4).accepted());
            long gravityId=port.activate("gravity",Map.of(),5).activationId();
            assertTrue(port.activate("hold",Map.of(),3).accepted());
            state.advancePresentation(true);state.advancePresentation(true);state.advancePresentation(true);
            assertEquals(0,state.clock());assertEquals(1,port.active().size());
            assertEquals(gravityId,port.active().getFirst().activationId());
            assertEquals(5,port.active().getFirst().remainingPresentations());
            state.advancePresentation();assertEquals(1,state.clock());assertEquals(4,port.active().getFirst().remainingPresentations());
        }
    }

    @Test void quarantineRevokesContributionAndHistoricalRestoreCannotReenableItsOwner() {
        var available=new java.util.concurrent.atomic.AtomicBoolean(true);
        var faults=mock(ModFaultBoundary.class);
        when(faults.isOwnerAvailable(anyString())).thenAnswer(call -> available.get());
        when(faults.call(anyString(),any())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(1)).get());
        var definition=new MutatorDefinition("gravity","Gravity","",MutatorScope.LIVE,MutatorScope.LIVE,List.of(),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY),options -> List.of(new MutatorPolicy.DrySonicGravity(50)));
        var state=new TemporaryMutatorState(9,List.of(new OwnedMutator("alpha",definition)),faults);
        try(var port=state.open("alpha",Set.of(MutatorCapability.DRY_SONIC_GRAVITY))) {
            assertTrue(port.activate("gravity",Map.of(),10).accepted());var captured=state.capture();
            available.set(false);assertTrue(state.entries().isEmpty());
            assertThrows(IllegalStateException.class,port::active);
            assertThrows(IllegalStateException.class,()->state.restore(captured));
        }
    }

    @Test void nativeSemanticTimingRejectsMislabelledDurationsAndMixedBundles() {
        var state=state(new AtomicInteger());
        try(var port=state.open("alpha",Set.of(MutatorCapability.PLAYER_MOTION,MutatorCapability.DRY_SONIC_GRAVITY))) {
            assertFalse(port.activate("gravity",Map.of(),0).accepted());
            assertFalse(port.activate("pulse",Map.of(),4).accepted());
            assertTrue(port.activate("pulse",Map.of(),0).accepted());
        }
        var faults=mock(ModFaultBoundary.class);
        when(faults.isOwnerAvailable(anyString())).thenReturn(true);
        when(faults.call(anyString(),any())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(1)).get());
        var mixed=new MutatorDefinition("mixed","Mixed","",MutatorScope.LIVE,MutatorScope.LIVE,List.of(),
                Set.of(MutatorCapability.PLAYER_MOTION,MutatorCapability.DRY_SONIC_GRAVITY),options -> List.of(
                new PlayerMotionPolicy(PlayerMotionPolicy.Mode.MOMENTUM_PULSE,0),new MutatorPolicy.DrySonicGravity(50)));
        var mixedState=new TemporaryMutatorState(10,List.of(new OwnedMutator("alpha",mixed)),faults);
        try(var port=mixedState.open("alpha",mixed.capabilities())) { assertFalse(port.activate("mixed",Map.of(),0).accepted()); }
    }

    @Test void capturedFacadeCannotBeUsedByAnAsynchronousCreatorCallback() throws Exception {
        var state=state(new AtomicInteger());
        try(var port=state.open("alpha",Set.of(MutatorCapability.DRY_SONIC_GRAVITY))) {
            var result=java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { port.active();return false; } catch(IllegalStateException expected) { return true; }
            });
            assertTrue(result.get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(port.nativeArt().isEmpty());
        }
    }

    private TemporaryMutatorState pongState() {
        var faults=mock(ModFaultBoundary.class);
        when(faults.isOwnerAvailable(anyString())).thenReturn(true);
        when(faults.call(anyString(),any())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(1)).get());
        var pong=new MutatorDefinition("pong","Pong","",MutatorScope.LIVE,MutatorScope.LIVE,List.of(),
                Set.of(MutatorCapability.GAMEPLAY_HOLD,MutatorCapability.OVERLAY_CONTENT,MutatorCapability.RING_ECONOMY),
                options -> List.of(new GameplayHoldPolicy(3),new OverlayPolicy(OverlayPolicy.Mode.PONG),
                        new RingEconomyPolicy(RingEconomyPolicy.Kind.COMPLETION_REWARD,5)));
        return new TemporaryMutatorState(11,List.of(new OwnedMutator("alpha",pong)),faults);
    }
    private Set<MutatorCapability> pongCapabilities() {
        return Set.of(MutatorCapability.GAMEPLAY_HOLD,MutatorCapability.OVERLAY_CONTENT,MutatorCapability.RING_ECONOMY);
    }
    @Test void completionIsOwnerBoundOnceOnlyAndRewindRestoresItsNativeRewardJob() {
        var state=pongState();
        try(var port=state.open("alpha",pongCapabilities());var foreign=state.open("beta",pongCapabilities())) {
            long id=port.activate("pong",Map.of(),3).activationId();assertNotEquals(0,id);
            assertFalse(foreign.complete(id,true).accepted());assertFalse(state.entries().isEmpty());
            var before=state.capture();
            assertTrue(port.complete(id,true).accepted());assertTrue(state.entries().isEmpty());
            assertFalse(port.complete(id,true).accepted());
            var completion=state.completions().getFirst();
            assertEquals("alpha",completion.owner());assertEquals(id,completion.activationId());assertEquals(5,completion.ringReward());
            var queued=state.capture();
            assertTrue(state.acknowledgeCompletion(id));assertFalse(state.acknowledgeCompletion(id));
            state.restore(queued);assertEquals(List.of(completion),state.completions());
            assertTrue(state.acknowledgeCompletion(id));
            state.restore(before);assertTrue(state.completions().isEmpty());
            assertTrue(port.complete(id,false).accepted());assertTrue(state.completions().isEmpty());
        }
    }
    @Test void completionBackpressureKeepsTheModalLeaseUntilThereIsNativeCapacity() {
        var state=pongState();
        try(var port=state.open("alpha",pongCapabilities())) {
            for(int i=0;i<32;i++)assertTrue(port.complete(port.activate("pong",Map.of(),3).activationId(),true).accepted());
            long waiting=port.activate("pong",Map.of(),3).activationId();
            assertFalse(port.complete(waiting,true).accepted());assertEquals(waiting,port.active().getFirst().activationId());
            assertEquals(32,state.completions().size());
            state.acknowledgeCompletion(state.completions().getFirst().activationId());
            assertTrue(port.complete(waiting,true).accepted());assertEquals(32,state.completions().size());
            state.clear();assertTrue(state.completions().isEmpty());assertTrue(state.entries().isEmpty());
        }
        assertThrows(IllegalArgumentException.class,()->new RingEconomyPolicy(RingEconomyPolicy.Kind.COMPLETION_REWARD));
        assertThrows(IllegalArgumentException.class,()->new RingEconomyPolicy(RingEconomyPolicy.Kind.COMPLETION_REWARD,11));
        assertThrows(IllegalArgumentException.class,()->new RingEconomyPolicy(RingEconomyPolicy.Kind.RAIN,5));
    }

    @Test void foreignOwnerAndRevokedPortCannotControlAnActivation() {
        var state = state(new AtomicInteger());
        var port = state.open("alpha", Set.of(MutatorCapability.DRY_SONIC_GRAVITY));
        long id = port.activate("gravity", Map.of("percent", 50), 4).activationId();
        try (var foreign = state.open("beta", Set.of(MutatorCapability.DRY_SONIC_GRAVITY))) {
            assertFalse(foreign.cancel(id));
            assertFalse(foreign.replace(id, Map.of("percent", 75)).accepted());
            assertTrue(foreign.available().isEmpty());
        }
        port.close(); assertThrows(IllegalStateException.class, port::active);
        state.retire(); assertTrue(state.entries().isEmpty());
    }
}
