package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.TestCdR12bEffectsBindings.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real source CalcSine/floor/reward helper, explicit scalar ports, never a World/Frame fixture. Java UNRUN. */
class TestCdR12bLostRingBinding {
    @BeforeAll static void source()throws Exception{load();}
    @Test void actualSourceIngressKeepsLegacySixPortConstructorAndNativeAssets() {
        var h=new H();var before=h.p.capture();assertEquals(6,CdLostRing.class.getConstructors()[0].getParameterCount());assertEquals(3,CdLostRing.Assets.class.getRecordComponents().length);assertEquals(0x209EB0,h.body.assets().mappingsAddress());assertEquals(0xA7AE,h.body.assets().baseTileWord());nonwrites(before,h.p,Set.of());assertTrue(h.order.isEmpty());
    }
    @Test void legacyConstructorKeepsDeferredSoundAndOriginalRunPath() {
        var p=placement();seed(p,33,0x11);p.pool().write(p.pool().state(33).moveByte(ROUTINE,2).moveWord(X_VELOCITY,0).moveWord(Y_VELOCITY,-0x100));int[] draws={0};
        var old=new CdLostRing(p,CdLostRing.Assets.fromAct2PastAssets(terrain.objects()),new CdPlayerMath(player.player()),probes(),(pool,id)->draws[0]++,CdLostRing::collectRing);
        var g=new CdRing.GlobalsPort(){public CdRingGlobals read(){return new CdRingGlobals(1,3,0,0,0,0,7,2);}public void write(CdRingGlobals s){fail("Main cannot write rings");}};
        var spill=new CdLostRing.SpillGlobalsPort(){public CdRingSpillGlobals read(){return new CdRingSpillGlobals(9,0x4567,2);}public void write(CdRingSpillGlobals s){fail("Main cannot write spill");}};
        old.run(33,new CdLostRing.Inputs(0,94,0,0x800),new CdLostRing.Ports(g,spill));assertEquals(1,draws[0]);assertFalse(old.isCurrentCall(p.pool().state(33)));
    }
    @Test void foreignExactPairFailsBeforeAnyComponentOrMetadataUse() {
        for(var p:List.of(new CdObjectPlacementRuntime(terrain.placements()),CdObjectPlacementRuntime.fromAssets(equalTerrain,bank())))assertThrows(IllegalArgumentException.class,()->CdLostRing.fromAct2PastAssets(p,terrain,null,null,null,null,null,null));
    }
    @Test void initUsesRealSineFirstFreeDirtyChildAndSynchronousFmBeforeMain() {
        var h=new H();var child=h.p.pool().state(32);var before=h.p.capture();h.rings=new CdRingGlobals(2,3,0xA0,5,6,0x1234,7,2);var result=h.run();
        assertEquals(0x209EB0,h.slot().longBits(MAPPINGS));assertEquals(0xA7AE,h.slot().word(TILE));var c=h.p.pool().state(32);assertEquals(0x11,c.id());assertEquals((child.routine()+2)&255,c.routine());assertEquals(child.longBits(X_POSITION)&65535,c.longBits(X_POSITION)&65535);assertEquals(0,h.rings.rings());assertEquals(0,h.rings.livesFlags());assertEquals(0x80,h.rings.updateHudRings());assertEquals(0xFF,h.spill.timerByte());assertEquals(0x4567,h.spill.accumWord());
        assertEquals(2,result.effects().stream().filter(CdLostRing.RingInitialized.class::isInstance).count());assertEquals(1,h.sounds.size());assertInstanceOf(CdLostRing.SoundRequest.class,h.sounds.getFirst().request());assertTrue(h.order.indexOf("sound")<h.order.indexOf("draw"));nonwrites(before,h.p,Set.of(32,33));h.retired();
    }
    @Test void scalarAndSoundExceptionsRetainNativeInitPrefixAndRetireCalls() {
        var h=new H();var fault=new Fault();h.onSound=c->{throw fault;};var before=h.p.capture();assertSame(fault,assertThrows(Fault.class,h::run));assertEquals(0,h.rings.rings());assertEquals(2,h.slot().routine());assertEquals(0xFF,h.spill.timerByte());assertEquals(0,h.draws);nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void genuineFloorPublishesInitialZeroAndOriginalAnglesBeforeDraw() {
        var h=new H();h.main();h.p.pool().write(h.slot().moveWord(Y_VELOCITY,0));h.inputs=new CdLostRing.Inputs(2,94,0,0x800);var before=h.p.capture();h.run();assertFalse(h.angles.isEmpty());assertEquals(0,h.angles.getFirst().value());assertEquals(1,h.draws);nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void collectUsesUnchangedSharedRewardExactlyOnceThenSparklesAndDraws() {
        var h=new H();h.main();h.p.pool().write(h.slot().moveByte(ROUTINE,4));h.rings=new CdRingGlobals(99,3,0,0,0,0xABCD,7,2);var before=h.p.capture();h.run();assertEquals(100,h.rings.rings());assertEquals(4,h.rings.lives());assertEquals(1,h.sounds.size());assertInstanceOf(CdLostRing.RewardSubCpuRequest.class,h.sounds.getFirst().request());assertEquals(1,h.collects);assertEquals(1,h.draws);assertTrue(h.order.indexOf("sound")<h.order.indexOf("draw"));nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void malformedCollectCannotInventSuccessAndRetainsActualCollectPrefix() {
        var h=new H(true);h.main();h.p.pool().write(h.slot().moveByte(ROUTINE,4));var before=h.p.capture();assertThrows(IllegalStateException.class,h::run);assertEquals(6,h.slot().routine());assertEquals(0,h.slot().unsignedByte(COLLISION_TYPE));assertEquals(1,h.slot().unsignedByte(PRIORITY));assertEquals(0,h.draws);nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void sourceCallbacksRejectReentryAndEqualCopiesAndRetireAfterError() {
        var h=new H();var error=new AssertionError("original");h.onSound=c->{assertTrue(h.body.isCurrentCall(c));assertFalse(h.body.isCurrentCall(cloneRecord(c)));assertThrows(IllegalStateException.class,h::run);throw error;};assertSame(error,assertThrows(AssertionError.class,h::run));h.retired();h.onSound=c->{};h.run();assertEquals(1,h.draws);
    }
    @Test void foreignPoolWriteBySoundIsRejectedWithoutRewindingCompletedNativePrefix() {
        var h=new H();h.onSound=c->h.p.pool().write(h.p.pool().state(70).moveByte(ID,0x55));assertThrows(IllegalStateException.class,h::run);assertEquals(0,h.rings.rings());assertEquals(0x55,h.p.pool().state(70).id());assertEquals(0,h.draws);h.retired();
    }
    @Test void timerDeleteAndWrongPhysicalCounterDoNotCallDrawOrInventRewind() {
        var h=new H();h.main();var before=h.p.capture();h.inputs=new CdLostRing.Inputs(0,93,0,0x800);assertThrows(IllegalArgumentException.class,h::run);nonwrites(before,h.p,Set.of());h.inputs=new CdLostRing.Inputs(0,94,0,0x800);h.spill=new CdRingSpillGlobals(0,0x4567,2);h.run();assertArrayEquals(new byte[64],h.slot().copyBytes());assertEquals(0,h.draws);nonwrites(before,h.p,Set.of(33));
    }
    static final class H {
        final CdObjectPlacementRuntime p=placement();CdLostRing body;CdRingGlobals rings=new CdRingGlobals(1,3,0,0,0,0x1234,7,2);CdRingSpillGlobals spill=new CdRingSpillGlobals(9,0x4567,2);CdLostRing.Inputs inputs=new CdLostRing.Inputs(0,94,0,0x800);final CdLostRing.Ports ports;int draws,collects;final List<String> order=new ArrayList<>();final List<Object> calls=new ArrayList<>();final List<CdLostRing.SoundCall> sounds=new ArrayList<>();final List<CdLostRing.PrimaryAngleCall> angles=new ArrayList<>();Consumer<CdLostRing.SoundCall> onSound=c->{};
        H(){this(false);}H(boolean malformed){seed(p,33,0x11);ports=new CdLostRing.Ports(new CdRing.GlobalsPort(){public CdRingGlobals read(){assertTrue(body.isCurrentInvocation(33,inputs,ports));return rings;}public void write(CdRingGlobals s){assertTrue(body.isCurrentCall(s));assertFalse(body.isCurrentCall(new CdRingGlobals(s.rings(),s.lives(),s.livesFlags(),s.updateHudRings(),s.updateHudLives(),s.timeStopTimer(),s.ringAnimationTimer(),s.ringAnimationFrame())));calls.add(s);rings=s;order.add("rings");}},new CdLostRing.SpillGlobalsPort(){public CdRingSpillGlobals read(){return spill;}public void write(CdRingSpillGlobals s){assertTrue(body.isCurrentCall(s));calls.add(s);spill=s;order.add("spill");}});
            body=CdLostRing.fromAct2PastAssets(p,terrain,new CdPlayerMath(player.player()),probes(),c->{assertTrue(body.isCurrentCall(c));assertFalse(body.isCurrentCall(cloneRecord(c)));angles.add(c);calls.add(c);},c->{assertTrue(body.isCurrentCall(c));assertSame(c.cut(),body.currentSource(c));sounds.add(c);calls.add(c);order.add("sound");onSound.accept(c);},(pool,id)->{assertTrue(body.isCurrentDrawCall(pool,id));assertFalse(body.isCurrentDrawCall(pool,new CdObjectState.Identity(id.wholeIndex())));draws++;order.add("draw");var s=pool.state(id.wholeIndex());pool.write(s.moveByte(SPRITE_FLAGS,s.spriteFlags()&127));},(g,c)->{assertTrue(body.isCurrentCollectCall(g,c));collects++;if(!malformed)CdLostRing.collectRing(g,c);});}
        CdObjectState slot(){return p.pool().state(33);}CdLostRing.Result run(){return body.run(33,inputs,ports);}void main(){p.pool().write(slot().moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(FLAGS,0).moveWord(X_VELOCITY,0).moveWord(Y_VELOCITY,-0x100).moveLong(MAPPINGS,0x209EB0).moveByte(MAP_FRAME,0));}void retired(){for(var c:calls)assertFalse(body.isCurrentCall(c));}
    }
}
