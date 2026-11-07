package com.openggf.game.sonic2.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.*;
import com.openggf.game.solid.*;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPointPokeyCompetitionDualCapture {
    @BeforeEach void reset() { TestEnvironment.resetAll(); }

    @Test void bothCaptureBanksAdvanceAndReleaseIndependentlyAndRewind() throws Exception {
        var f = new Fixture();
        f.capture(0);
        for (int i = 0; i < 8; i++) f.cage.update(i, f.world.p1);
        f.capture(1);
        assertTrue(f.world.p1.isObjectControlled());
        assertTrue(f.world.p2.isObjectControlled());
        assertEquals(112, field(f.cage, "countdown"));
        assertEquals(120, field(f.cage, "secondCountdown"));
        var saved = f.cage.captureRewindState();
        for (int i = 8; i < 16; i++) f.cage.update(i, f.world.p1);
        assertEquals(100, f.world.games[0].getScore());
        assertEquals(100, f.world.games[1].getScore());
        f.cage.restoreRewindState(saved);
        assertEquals(112, field(f.cage, "countdown"));
        assertEquals(120, field(f.cage, "secondCountdown"));
        for (int i = 8; i < 121; i++) f.cage.update(i, f.world.p1);
        assertFalse(f.world.p1.isObjectControlled());
        assertTrue(f.world.p2.isObjectControlled());
        assertEquals(0x400, f.world.p1.getYSpeed());
        assertEquals(4, field(f.cage, "playerState"));
        assertEquals(1, field(f.cage, "secondPlayerState"));
        for (int i = 121; i < 129; i++) f.cage.update(i, f.world.p1);
        assertFalse(f.world.p2.isObjectControlled());
        for (int i = 129; i < 152; i++) f.cage.update(i, f.world.p1);
        assertEquals(0, field(f.cage, "playerState"));
        assertEquals(4, field(f.cage, "secondPlayerState"));
    }

    @Test void prizesUseCapturedParentSlotEvenWhenUpdatedWithOtherPlayer() {
        for (int slot = 0; slot < 2; slot++) {
            var f = new Fixture(); f.capture(slot);
            f.world.banks[0].setRings(10); f.world.banks[1].setRings(20);
            var counter = f.cage.prizeCounterForChild(); counter[0] = 2;
            var ring = new RingPrizeObjectInstance(0x100, 0x100, 0x100, 0x100, 1, counter, f.cage);
            var bomb = new BombPrizeObjectInstance(0x100, 0x100, 0x100, 0x100, 1, counter, f.cage);
            ring.setServices(f.services); bomb.setServices(f.services);
            ring.update(1, f.world.player(1-slot));
            assertEquals(10 + (slot == 0 ? 1 : 0), f.world.banks[0].getRings());
            assertEquals(20 + (slot == 1 ? 1 : 0), f.world.banks[1].getRings());
            assertEquals(1, counter[0]);
            bomb.update(1, f.world.player(1-slot));
            assertEquals(10, f.world.banks[0].getRings());
            assertEquals(20, f.world.banks[1].getRings());
            assertEquals(0, counter[0]);
        }
    }

    @Test void ownershipSwapExchangesOnlyPerPlayerCaptureBanks() throws Exception {
        var f = new Fixture(); f.capture(0);
        f.cage.swapPlayerOwnership();
        assertEquals(0, field(f.cage, "playerState"));
        assertEquals(1, field(f.cage, "secondPlayerState"));
        // swap_loop_objects changes $30/$34 but leaves $3E untouched (s2.asm:26081-26085).
        assertEquals(0, f.cage.prizeOwnerNativeSlot());
        f.cage.swapPlayerOwnership();
        assertEquals(1, field(f.cage, "playerState"));
        assertEquals(0, field(f.cage, "secondPlayerState"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false","false,true","true,false","true,true"})
    void recreatedPrizeRelinksExactRecreatedCageCounterRatherThanNearbyCage(boolean bomb, boolean retained) {
        var f = new Fixture();
        var parent = new PointPokeyObjectInstance(f.cage.getSpawn(),"original");
        parent.setServices(f.services);
        parent.onSolidContact(f.world.p2,new SolidContact(true,false,false,true,false),0);
        f.objects = new ObjectManager(List.of(), new Sonic2ObjectRegistry(),0,null,null,null,
                f.world.cameras[0],f.services);
        f.objects.addDynamicObject(parent);
        int parentSlot = parent.getSlotIndex();
        parent.prizeCounterForChild()[0] = 4;
        AbstractObjectInstance original = bomb
                ? f.objects.createDynamicObject(() -> new BombPrizeObjectInstance(
                    256,256,256,256,1,parent.prizeCounterForChild(),parent))
                : f.objects.createDynamicObject(() -> new RingPrizeObjectInstance(
                    256,256,256,256,1,parent.prizeCounterForChild(),parent));
        var parentState = parent.captureRewindState();
        var childState = original.captureRewindState();
        var decoy = f.objects.createDynamicObject(() -> new PointPokeyObjectInstance(
                new ObjectSpawn(257,256,0xD6,0,0,false,0), "decoy"));
        decoy.prizeCounterForChild()[0] = 99;
        f.objects.removeDynamicObject(parent);
        var restoredParent = new PointPokeyObjectInstance(parent.getSpawn(), "restored");
        restoredParent.setServices(f.services);
        restoredParent.restoreRewindState(parentState);
        f.objects.addDynamicObjectAtSlot(restoredParent,parentSlot);
        AbstractObjectInstance restoredPrize = retained ? original : bomb
                ? new BombPrizeObjectInstance(new ObjectSpawn(256,256,0xD3,0,0,false,0))
                : new RingPrizeObjectInstance(256,256,0,false);
        restoredPrize.setServices(f.services);
        restoredPrize.restoreRewindState(childState);
        f.world.banks[1].setRings(20);
        restoredPrize.update(1,f.world.p1);
        assertEquals(bomb ? 19 : 21,f.world.banks[1].getRings());
        assertEquals(3,restoredParent.prizeCounterForChild()[0]);
        assertEquals(4,parent.prizeCounterForChild()[0]);
        assertEquals(99,decoy.prizeCounterForChild()[0]);
    }

    @Test void failedNativePrizeAllocationDoesNotSpendRewardCounterOrAngle() throws Exception {
        for (int reward : new int[]{1,-1}) {
            var f = new Fixture(); f.capture(1);
            f.objects = new ObjectManager(List.of(),new Sonic2ObjectRegistry(),0,null,null,null,
                    f.world.cameras[0],f.services);
            f.objects.reserveAllButNFreeSlots(0);
            for (var entry : Map.of("slotReward",reward,"prizesToSpawn",5,"prizeAngle",0x24,
                    "playerState",3).entrySet()) {
                var field = PointPokeyObjectInstance.class.getDeclaredField(entry.getKey());
                field.setAccessible(true); field.setInt(f.cage,entry.getValue());
            }
            write(f.machine,"reward",reward > 0 ? 5 : -1);
            write(f.cage,"nativeBombRemaining",5);
            var pass = PointPokeyObjectInstance.class.getDeclaredMethod("runPayoutPass",
                    AbstractPlayableSprite.class,int.class);
            pass.setAccessible(true);
            when(f.world.level.getFrameCounter()).thenReturn(1);
            pass.invoke(f.cage,f.world.p2,2); // V-int even, native level clock odd.
            assertEquals(reward > 0 ? 5 : -1,f.machine.getReward());
            assertEquals(5,field(f.cage,"prizesToSpawn"));
            assertEquals(0x24,field(f.cage,"prizeAngle"));
            assertEquals(0,f.cage.prizeCounterForChild()[0]);
            f.objects.reset(0);
            f.objects.reserveAllButNFreeSlots(1);
            when(f.world.level.getFrameCounter()).thenReturn(3);
            pass.invoke(f.cage,f.world.p2,4); // Admission follows level clock, never V-int.
            assertEquals(reward > 0 ? 4 : -1,f.machine.getReward());
            assertEquals(4,field(f.cage,"prizesToSpawn"));
            assertEquals((0x24+(reward<0 ? 0x90 : 0x89))&255,field(f.cage,"prizeAngle"));
            assertEquals(1,f.cage.prizeCounterForChild()[0]);
        }
    }

    @Test void nativeAnimationSecondCaptureRetainsTheExistingScriptPhase() throws Exception {
        var f = new Fixture(); f.capture(0);
        f.cage.update(0,f.world.p1);
        assertEquals(1,field(f.cage,"mappingFrame"));
        f.cage.update(1,f.world.p1);
        assertEquals(1,field(f.cage,"mappingFrame"));
        f.capture(1);
        var snapshot=f.cage.captureRewindState();
        f.cage.update(2,f.world.p1);
        assertEquals(0,field(f.cage,"mappingFrame"));
        f.cage.restoreRewindState(snapshot);
        f.cage.update(2,f.world.p1);
        assertEquals(0,field(f.cage,"mappingFrame"), "restored native script phase resumes exactly");
    }

    @Test void releaseThenCaptureInOnePassRetainsTheSameFinalAnimation() throws Exception {
        var f = new Fixture(); f.capture(0);
        f.cage.update(0,f.world.p1); f.cage.update(1,f.world.p1);
        var timer=PointPokeyObjectInstance.class.getDeclaredField("countdown");
        timer.setAccessible(true); timer.setInt(f.cage,0);
        var top=mock(PlayerSolidContactResult.class); when(top.kind()).thenReturn(ContactKind.TOP);
        when(f.solid.resolveSolidNowOnly(f.world.p2)).thenReturn(top);
        f.cage.update(2,f.world.p1);
        assertFalse(f.world.p1.isObjectControlled());
        assertTrue(f.world.p2.isObjectControlled());
        assertEquals(0,field(f.cage,"mappingFrame"));
    }

    @Test void nativeSolidCallsInterleaveEachCaptureBeforeTheNextActor() throws Exception {
        var f=new Fixture();
        var top=mock(PlayerSolidContactResult.class); when(top.kind()).thenReturn(ContactKind.TOP);
        when(f.solid.resolveSolidNowOnly(f.world.p1)).thenReturn(top);
        when(f.solid.resolveSolidNowOnly(f.world.p2)).thenAnswer(call -> {
            assertTrue(f.world.p1.isObjectControlled(), "P1 capture must precede P2 solid");
            return top;
        });
        f.cage.update(0,f.world.p1);
        assertTrue(f.world.p2.isObjectControlled());
        assertEquals(120,field(f.cage,"countdown"));
        assertEquals(120,field(f.cage,"secondCountdown"));
        var order=inOrder(f.solid);
        order.verify(f.solid).resolveSolidNowOnly(f.world.p1);
        order.verify(f.solid).resolveSolidNowOnly(f.world.p2);
        verify(f.solid,never()).resolveSolidNowAll();
    }

    @Test void linkedP1CaptureRejectsP2BeforeRunningItsSolidCall() throws Exception {
        var f=new Fixture(); var cage=f.linkedCage();
        for(int i=0;i<6;i++) f.machine.update(i);
        var top=mock(PlayerSolidContactResult.class); when(top.kind()).thenReturn(ContactKind.TOP);
        when(f.solid.resolveSolidNowOnly(f.world.p1)).thenReturn(top);
        cage.update(6,f.world.p1);
        assertTrue(f.machine.isInUse());
        assertTrue(f.world.p1.isObjectControlled());
        assertFalse(f.world.p2.isObjectControlled());
        assertFalse(cage.isSolidFor(f.world.p2));
        verify(f.solid,never()).resolveSolidNowOnly(f.world.p2);
    }

    @Test void initialReelRollAllowsBothCapturesWithoutSettingInUse() {
        var f=new Fixture(); var cage=f.linkedCage();
        var top=mock(PlayerSolidContactResult.class); when(top.kind()).thenReturn(ContactKind.TOP);
        when(f.solid.resolveSolidNowOnly(any())).thenReturn(top);
        assertFalse(f.machine.isAvailable());
        cage.update(0,f.world.p1);
        assertTrue(f.world.p1.isObjectControlled());
        assertTrue(f.world.p2.isObjectControlled());
        assertFalse(f.machine.isInUse());
        for(int i=0;i<6;i++) f.machine.update(i);
        assertFalse(f.machine.isRunning());
        cage.update(7,f.world.p1);
        assertFalse(f.world.p1.isObjectControlled());
        assertFalse(f.world.p2.isObjectControlled());
    }

    @Test void nativeCaptureSoundWaitsForTheCountdownBoundary() {
        var f=new Fixture(); f.capture(0);
        assertEquals(0,f.casinoSounds);
        for(int i=0;i<8;i++) f.cage.update(i,f.world.p1);
        assertEquals(1,f.casinoSounds);
        assertEquals(100,f.world.games[0].getScore());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false","false,true","true,false","true,true"})
    void nativeReleaseReadsTheRetainedRenderFlagAndOrdinaryModeKeepsItsCurrentBounds(boolean nativeMode,
            boolean retainedVisible) throws Exception {
        var f=new Fixture(); f.nativeMode=nativeMode; f.capture(0);
        if(nativeMode) f.capture(1);
        f.visible=!retainedVisible;
        retainedVisibility(f.cage,retainedVisible);
        var saved=f.cage.captureRewindState();
        retainedVisibility(f.cage,!retainedVisible);
        f.cage.restoreRewindState(saved);
        f.cage.update(0,f.world.p1);
        assertEquals(nativeMode ? retainedVisible : !retainedVisible,f.world.p1.isObjectControlled(),
                "loc_2BDF8 reads the PREVIOUS sprite pass, independent of the current bounds");
        if(nativeMode) assertEquals(retainedVisible,f.world.p2.isObjectControlled());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void nativeRenderWidthUsesObjD6InitWhileOrdinaryWidthIsUnchanged(boolean nativeMode) {
        var f=new Fixture(); f.nativeMode=nativeMode;
        assertEquals(nativeMode ? 0x18 : 0x10,f.cage.getOnScreenHalfWidth(),
                "ObjD6_Init (s2.asm:59141-59149) writes width_pixels=$18");
    }

    @Test void ordinaryRingPrizeRestoreKeepsItsExistingCounterAliasAndForwardWrite() {
        var f=new Fixture(); f.nativeMode=false;
        int[] counter={4};
        var ring=new RingPrizeObjectInstance(256,256,256,256,1,counter);
        ring.setServices(f.services);
        var saved=ring.captureRewindState();
        counter[0]=1;
        ring.restoreRewindState(saved);
        assertEquals(4,counter[0],"ordinary final-array restore still writes into its existing owner");
        ring.update(1,f.world.p1);
        assertEquals(3,counter[0],"the forward collection still decrements that same counter");
        ring.restoreRewindState(saved);
        assertEquals(4,counter[0]);
        ring.update(1,f.world.p1);
        assertEquals(3,counter[0]);
    }

    @Test void nativeCagesSpendOneSharedRingBudgetAndRewind() throws Exception {
        var f=new Fixture(); nativeAllocator(f);
        var first=f.linkedCage();var second=f.linkedCage();
        first.onSolidContact(f.world.p1,new SolidContact(true,false,false,true,false),0);
        second.onSolidContact(f.world.p2,new SolidContact(true,false,false,true,false),0);
        write(f.machine,"routine",0x18);write(f.machine,"reward",1);
        var machine=f.machine.rewindAdapter();var machineSaved=machine.capture();
        var firstSaved=first.captureRewindState();var secondSaved=second.captureRewindState();
        var p1=f.world.p1.captureRewindState();var p2=f.world.p2.captureRewindState();
        for(int replay=0;replay<2;replay++) {
            first.update(2,f.world.p1);second.update(2,f.world.p1);
            assertEquals(0,f.machine.getReward(),"loc_2BD4E consumes the GLOBAL word exactly once");
            assertEquals(1,f.objects.getActiveObjects().stream().filter(RingPrizeObjectInstance.class::isInstance).count());
            assertTrue(f.world.p1.isObjectControlled());assertFalse(f.world.p2.isObjectControlled());
            f.objects.reset(0);machine.restore(machineSaved);
            first.restoreRewindState(firstSaved);second.restoreRewindState(secondSaved);
            f.world.p1.restoreRewindState(p1);f.world.p2.restoreRewindState(p2);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,128,0","64,0,128","128,-128,0","192,0,-128"})
    void nativePrizeUsesCalcSineAxesAndRetainsTheAngleWord(int angle,int dx,int dy) throws Exception {
        var f=new Fixture();nativeAllocator(f);f.capture(1);
        write(f.machine,"reward",2);write(f.cage,"prizeAngle",angle);write(f.cage,"playerState",3);
        var pass=PointPokeyObjectInstance.class.getDeclaredMethod("runPayoutPass",AbstractPlayableSprite.class,int.class);
        pass.setAccessible(true);pass.invoke(f.cage,f.world.p2,2);
        var prize=f.objects.getActiveObjects().stream().filter(RingPrizeObjectInstance.class::isInstance).findFirst().orElseThrow();
        assertEquals(256+dx,prize.getX());assertEquals(256+dy,prize.getY());
        assertEquals(angle+0x89,field(f.cage,"prizeAngle"));
        var saved=f.cage.captureRewindState();write(f.cage,"prizeAngle",0);
        f.cage.restoreRewindState(saved);assertEquals(angle+0x89,field(f.cage,"prizeAngle"));
    }

    @Test void nativeReleaseRetainsPrivateWordsUntilANewSpinActuallyStarts() throws Exception {
        var f=new Fixture();var cage=f.linkedCage();
        cage.onSolidContact(f.world.p1,new SolidContact(true,false,false,true,false),0);
        write(cage,"prizeAngle",0x1200);write(cage,"nativeBombRemaining",68);
        var eject=PointPokeyObjectInstance.class.getDeclaredMethod("ejectPlayer",AbstractPlayableSprite.class);
        eject.setAccessible(true);eject.invoke(cage,f.world.p1);
        var retained=cage.captureRewindState();
        write(cage,"prizeAngle",0);write(cage,"nativeBombRemaining",0);
        cage.restoreRewindState(retained);
        assertEquals(0x1200,field(cage,"prizeAngle"));assertEquals(68,field(cage,"nativeBombRemaining"));
        for(int i=0;i<31;i++)cage.update(i,f.world.p1);
        cage.onSolidContact(f.world.p1,new SolidContact(true,false,false,true,false),32);
        assertEquals(0x1200,field(cage,"prizeAngle"),"capturing during an existing roll does not clear the word");
        eject.invoke(cage,f.world.p1);for(int i=0;i<31;i++)cage.update(i,f.world.p1);
        write(f.machine,"routine",0x18);
        cage.onSolidContact(f.world.p1,new SolidContact(true,false,false,true,false),64);
        assertEquals(0,field(cage,"prizeAngle"));assertEquals(0xFFFF,field(cage,"nativeBombRemaining"));
        assertTrue(f.machine.isInUse());
    }

    @Test void nativePayoutStillChecksTheRetainedOffscreenFlag() throws Exception {
        var f=new Fixture();nativeAllocator(f);var cage=f.linkedCage();
        cage.onSolidContact(f.world.p1,new SolidContact(true,false,false,true,false),0);
        write(cage,"playerState",3);write(f.machine,"routine",0x18);write(f.machine,"reward",5);
        retainedVisibility(cage,false);cage.update(2,f.world.p1);
        assertFalse(f.world.p1.isObjectControlled());assertEquals(5,f.machine.getReward());
        assertTrue(f.objects.getActiveObjects().isEmpty());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void nativePrizeCounterUnderflowIsAWord(boolean bomb) {
        var f=new Fixture();f.capture(0);int[] counter=f.cage.prizeCounterForChild();
        AbstractObjectInstance child=bomb
                ?new BombPrizeObjectInstance(256,256,256,256,1,counter,f.cage)
                :new RingPrizeObjectInstance(256,256,256,256,1,counter,f.cage);
        child.setServices(f.services);child.update(1,f.world.p2);
        assertEquals(0xFFFF,counter[0]);
    }

    private static void nativeAllocator(Fixture f) {
        f.objects=new ObjectManager(List.of(),new Sonic2ObjectRegistry(),0,null,null,null,f.world.cameras[0],f.services);
        when(f.world.level.getFrameCounter()).thenReturn(1);
    }
    private static void write(Object target,String name,int value) throws Exception {
        var type=target instanceof com.openggf.game.sonic2.slotmachine.CNZSlotMachineManager
                ? com.openggf.game.sonic2.slotmachine.CNZSlotMachineManager.class : PointPokeyObjectInstance.class;
        var field=type.getDeclaredField(name);field.setAccessible(true);field.setInt(target,value);
    }

    private static void retainedVisibility(PointPokeyObjectInstance cage,boolean visible) throws Exception {
        var seen=AbstractObjectInstance.class.getDeclaredField("renderFlagPassSeen");
        var flag=AbstractObjectInstance.class.getDeclaredField("renderFlagOnScreen");
        seen.setAccessible(true); flag.setAccessible(true);
        seen.setBoolean(cage,true); flag.setBoolean(cage,visible);
    }

    private static int field(Object value, String name) throws Exception {
        var field = PointPokeyObjectInstance.class.getDeclaredField(name);
        field.setAccessible(true); return field.getInt(value);
    }

    private static final class Fixture {
        final NativeCompetitionRuntimeFixture world = new NativeCompetitionRuntimeFixture(new com.openggf.timer.TimerManager());
        ObjectManager objects;
        int casinoSounds;
        boolean nativeMode=true;
        boolean visible=true;
        final com.openggf.game.sonic2.slotmachine.CNZSlotMachineManager machine =
                new com.openggf.game.sonic2.slotmachine.CNZSlotMachineManager();
        final com.openggf.game.sonic2.Sonic2ZoneFeatureProvider features =
                mock(com.openggf.game.sonic2.Sonic2ZoneFeatureProvider.class);
        final ObjectSolidExecutionContext solid = mock(ObjectSolidExecutionContext.class);
        final ObjectServices services = new StubObjectServices() {
            @Override public com.openggf.game.session.WorldSession worldSession() { return nativeMode ? world.world : null; }
            @Override public GameModule gameModule() { return world.world.getGameModule(); }
            @Override public GameStateManager gameState() { return world.ordinaryGame; }
            @Override public com.openggf.level.LevelManager levelManager() { return world.level; }
            @Override public ObjectSolidExecutionContext solidExecution() { return solid; }
            @Override public ObjectManager objectManager() { return objects; }
            @Override public ZoneFeatureProvider zoneFeatureProvider() { return features; }
            @Override public void playSfx(com.openggf.audio.GameSound sound) {
                if(sound == com.openggf.audio.GameSound.CASINO_BONUS) casinoSounds++;
            }
            @Override public ObjectRenderManager renderManager() { return mock(ObjectRenderManager.class); }
            @Override public AudioManager audioManager() { return mock(AudioManager.class); }
        };
        final PointPokeyObjectInstance cage = new PointPokeyObjectInstance(
                new ObjectSpawn(0x100,0x100,0xD6,0,0,false,0), "Pokey") {
            @Override protected boolean isWithinOnScreenRenderBounds() { return visible; }
        };
        Fixture() {
            when(features.getSlotMachineManager()).thenReturn(machine);
            cage.setServices(services);
            when(solid.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(cage, Map.of()));
        }
        PointPokeyObjectInstance linkedCage() {
            var linked=new PointPokeyObjectInstance(new ObjectSpawn(256,256,0xD6,1,0,false,0),"linked") {
                @Override protected boolean isWithinOnScreenRenderBounds() { return true; }
            };
            linked.setServices(services); return linked;
        }
        void capture(int slot) {
            cage.onSolidContact(world.player(slot), new SolidContact(true,false,false,true,false), 0);
        }
    }
}
