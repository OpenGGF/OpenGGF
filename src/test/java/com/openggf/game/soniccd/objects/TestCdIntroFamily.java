package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdIntroSpikes.Read.*;
import static com.openggf.game.soniccd.objects.CdIntroSpikes.PlayerStore.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal native vectors, genuine CdProgramAssets ingress, no live Frame/World/parity claim. */
class TestCdIntroFamily {
    private static CdR31IntroAssets retained;
    static synchronized CdR31IntroAssets assets() throws Exception {
        if(retained==null) {
            String root=Objects.requireNonNull(System.getProperty("soniccd.media.root"),"Explicit original media root required");
            try(var p=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
                retained=CdR31IntroAssets.fromAssets(CdR31aAssets.fromPrograms(p,CdStagePayload.R31A.stage()));
            }
        }
        return retained;
    }
    static final class Host implements CdIntroSpikes.Ports {
        final EnumMap<CdIntroSpikes.Read,Integer> values=new EnumMap<>(CdIntroSpikes.Read.class);
        final List<String> events=new ArrayList<>();final List<Integer> visited=new ArrayList<>();
        final EnumMap<CdIntroSpikes.PlayerStore,Integer> writes=new EnumMap<>(CdIntroSpikes.PlayerStore.class);
        RuntimeException fault;String faultAt;boolean contact;CdObjectPool pool;int solidFlags=8;Runnable onRead;
        java.util.function.BiConsumer<CdIntroSpikes.Read,CdIntroSpikes.Cut> onReadCut;
        Host(){for(var r:CdIntroSpikes.Read.values())values.put(r,0);}
        void event(String event){events.add(event);if(event.equals(faultAt))throw fault;}
        public int read(CdIntroSpikes.Read f,CdIntroSpikes.Cut c){event("read:"+f);if(onReadCut!=null)onReadCut.accept(f,c);if(onRead!=null)onRead.run();return values.get(f);}
        public void playerStore(CdIntroSpikes.PlayerStore f,int v,CdIntroSpikes.Cut c){event("store:"+f);writes.put(f,v);}
        public boolean solid(CdIntroSpikes.Cut c){event("solid");if(pool!=null)pool.write(c.source().moveByte(CdObjectState.ByteField.FLAGS,solidFlags));return contact;}
        public void getOff(CdIntroSpikes.Cut c){event("getOff");}
        public void hurt(CdIntroSpikes.Cut c){event("hurt");}
        public void fm(int id,CdIntroSpikes.Cut c){event("fm:"+Integer.toHexString(id));}
        public void loadPlc(int id,CdIntroSpikes.Cut c){event("plc:"+id);}
        public void subCpu(int id,CdIntroSpikes.Cut c){event("sub:"+id);}
        public void setAmyCaptured(CdIntroSpikes.Cut c){event("capture");values.put(AMY_CAPTURED,255);}
        public void palette(CdAssetBytes bytes,CdIntroSpikes.Cut c){assertEquals(32,bytes.length());event("palette");}
        public void draw(CdIntroSpikes.Cut c){visited.add(c.source().identity().wholeIndex());event("draw");}
        public void despawn(CdIntroSpikes.Cut c){event("despawn");}
    }
    static CdObjectPool pool(int whole,int id,int routine) {
        var p=new CdObjectPool();p.write(p.state(whole).moveByte(CdObjectState.ByteField.ID,id).moveByte(CdObjectState.ByteField.ROUTINE,routine));return p;
    }
    static void b(CdObjectPool p,int slot,int at,int value){new P5CallUnchecked(p,slot).b(at,value);}
    static void w(CdObjectPool p,int slot,int at,int value){p.write(p.state(slot).moveWord(P5Call.wf(at),value));}
    static void l(CdObjectPool p,int slot,int at,int value){p.write(p.state(slot).moveLong(P5Call.lf(at),value));}
    // Same immutable-byte composition used by the source vocabulary, with independent literal images below.
    record P5CallUnchecked(CdObjectPool p,int slot) {
        void b(int at,int v){if(at==0x30)p.write(p.state(slot).moveWord(P5Call.wf(0x30),((v&255)<<8)|(p.state(slot).word(P5Call.wf(0x30))&255)));else p.write(p.state(slot).moveByte(P5Call.bf(at),v));}
    }
    static void occupied(CdObjectPool p){for(int n=32;n<128;n++)if(p.state(n).id()==0)b(p,n,0,6);}
    static void bytes(byte[] image,int at,int... values){for(int v:values)image[at++]=(byte)v;}

    @Test void earlyTimeAttackDeleteDoesNotReadRoutineOrAmyOrDraw() throws Exception {
        var p=pool(32,0x30,255);l(p,32,8,0x12345678);var h=new Host();h.values.put(TIME_ATTACK,1);
        new CdIntroSpikes(assets()).run(p,32,h);assertArrayEquals(new byte[64],p.state(32).copyBytes());
        assertEquals(List.of("read:TIME_ATTACK"),h.events);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void introInitializesAllFourRawSubtypesAndPreservesWholeImage(int subtype) throws Exception {
        var p=pool(32,0x30,0);b(p,32,0x28,subtype);l(p,32,8,0x01001234);l(p,32,0xC,0x0200ABCD);
        b(p,32,0x1A,0x66);b(p,32,0x20,0x55);var expected=p.state(32).copyBytes();
        bytes(expected,1,4,3,0x1E);bytes(expected,4,0,0x23,0xBE,0x60);bytes(expected,0x18,3,subtype<2?0x12:0xC);
        expected[0x16]=(byte)(subtype==1?8:0x20);expected[0x24]=2;
        if(subtype>=2){expected[0x1A]=(byte)(subtype==2?9:14);expected[0x20]=(byte)0x86;}
        var untouched=p.capture().slots().subList(1,96);var h=new Host();
        new CdIntroSpikes(assets()).run(p,32,h);assertArrayEquals(expected,p.state(32).copyBytes());
        assertEquals(untouched,p.capture().slots().subList(1,96));assertTrue(h.events.contains("solid"));
    }
    @Test void shardsUseThreeFirstFreeSlotsInDescendingTypeOrderAndRetainDirtyFractions() throws Exception {
        var p=pool(40,0x30,2);b(p,40,0x19,18);b(p,40,0x16,32);b(p,40,1,0x80);
        w(p,40,8,0x120);w(p,40,0xC,0x200);b(p,41,0,0x31);w(p,41,8,0x120);w(p,41,0xC,0x200);
        for(int n=32;n<35;n++){l(p,n,8,0xAAAA1234);l(p,n,0xC,0xBBBB5678);b(p,n,0x28,0xA5);}
        var prior=p.capture().slots();var h=new Host();new CdIntroSpikes(assets()).run(p,40,h);
        assertEquals(4,p.state(40).routine());assertEquals(4,p.state(40).unsignedByte(CdObjectState.ByteField.MAP_FRAME));
        for(int n=32;n<35;n++) {
            var expected=prior.get(n-32).copyBytes();bytes(expected,0,0x30);bytes(expected,8,1,0x20);bytes(expected,0xC,2,0);
            expected[0x1A]=(byte)(35-n);expected[0x3C]=(byte)(35-n);assertArrayEquals(expected,p.state(n).copyBytes());
        }
        assertEquals(List.of("read:TIME_ATTACK","fm:a3","getOff","draw"),h.events);
    }
    @Test void noFreeShardStillAdvancesParentAndSoundFailureRetainsAllThreeChildren() throws Exception {
        var p=pool(40,0x30,2);b(p,40,0x19,18);b(p,40,0x16,32);b(p,40,1,0x80);b(p,41,0,0x31);
        occupied(p);var h=new Host();h.faultAt="fm:a3";h.fault=new IllegalStateException("sound failure");
        assertSame(h.fault,assertThrows(IllegalStateException.class,()->new CdIntroSpikes(assets()).run(p,40,h)));
        assertEquals(4,p.state(40).routine());assertFalse(h.events.contains("getOff"));assertFalse(h.events.contains("draw"));
    }
    @Test void partialChildPoolRetriesEachOfThreeAllocationsWithoutClearingOrRollingBack() throws Exception {
        var p=pool(40,0x30,2);b(p,40,0x19,18);b(p,40,0x16,32);b(p,40,0x28,2);b(p,41,0,0x31);occupied(p);
        p.objNull(33);l(p,33,8,0x56781234);var h=new Host();new CdIntroSpikes(assets()).run(p,40,h);
        assertEquals(0x30,p.state(33).id());assertEquals(7,p.state(33).unsignedByte(CdObjectState.ByteField.MAP_FRAME));
        assertEquals(3,p.state(33).unsignedByte(P5Call.bf(0x3C)));assertEquals(0x1234,p.state(33).word(CdObjectState.WordField.X_FRACTION));
    }
    @Test void shardInitFallsIntoBallisticsButParentShatterDoesNot() throws Exception {
        var p=pool(32,0x30,0);b(p,32,0x3C,3);l(p,32,8,0x1000ABCD);l(p,32,0xC,0x20005678);
        var h=new Host();new CdIntroSpikes(assets()).run(p,32,h);
        assertEquals(0x1002ABCD,p.state(32).longBits(CdObjectState.LongField.X_POSITION));
        assertEquals(0x1FFC5678,p.state(32).longBits(CdObjectState.LongField.Y_POSITION));
        assertEquals(0xFC60,p.state(32).word(CdObjectState.WordField.Y_VELOCITY));assertEquals(0x77,p.state(32).unsignedByte(P5Call.bf(0x3A)));
    }
    @Test void dirtyShardSelectorFaultRetainsEarlierInitAndClearedCollision() throws Exception {
        var p=pool(32,0x30,0);b(p,32,0x3C,255);var h=new Host();
        assertThrows(IndexOutOfBoundsException.class,()->new CdIntroSpikes(assets()).run(p,32,h));
        assertEquals(4,p.state(32).routine());assertEquals(0,p.state(32).unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));assertFalse(h.events.contains("draw"));
    }
    @Test void hurtRewindsCompleteYBeforeRealHurtAndRetainsThatPrefixOnFailure() throws Exception {
        var p=pool(32,0x30,2);b(p,32,0x28,1);var h=new Host();h.pool=p;h.contact=true;
        h.values.put(PLAYER_Y_LONG,0x20005678);h.values.put(PLAYER_Y_VELOCITY,0xFD80);h.faultAt="hurt";h.fault=new IllegalStateException("hurt");
        assertSame(h.fault,assertThrows(IllegalStateException.class,()->new CdIntroSpikes(assets()).run(p,32,h)));
        assertEquals(0x2002D678,h.writes.get(Y_LONG));assertEquals(8,p.state(32).flags());
        assertEquals(List.of("read:TIME_ATTACK","solid","read:TIME_WARP","read:INVINCIBLE","read:PLAYER_ROUTINE",
                "read:PLAYER_HURT_WORD","read:PLAYER_Y_LONG","read:PLAYER_Y_VELOCITY","store:Y_LONG","hurt"),h.events);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5})
    void hurtGuardsStopAtTheFirstNativePredicate(int gate) throws Exception {
        var p=pool(32,0x30,2);b(p,32,0x28,1);var h=new Host();h.pool=p;h.contact=gate!=0;
        if(gate==1)h.solidFlags=0;if(gate==2)h.values.put(TIME_WARP,1);if(gate==3)h.values.put(INVINCIBLE,1);
        if(gate==4)h.values.put(PLAYER_ROUTINE,4);if(gate==5)h.values.put(PLAYER_HURT_WORD,0x100);
        new CdIntroSpikes(assets()).run(p,32,h);assertTrue(h.writes.isEmpty());assertFalse(h.events.contains("hurt"));
        assertFalse(h.events.contains("read:PLAYER_Y_LONG"));
    }
    @Test void teleporterDeleteAndPeriodNullHaveDifferentCompleteImages() throws Exception {
        for(var programme:List.of(CdNativeFile.R31B,CdNativeFile.R31C,CdNativeFile.R31D))for(int id=0x30;id<=0x34;id++) {
            assertTrue(CdR31IntroAssets.nativeNull(programme,id));var p=pool(32,id,255);l(p,32,8,0x12345678);var expected=p.state(32).copyBytes();expected[0]=0;
            p.objNull(32);assertArrayEquals(expected,p.state(32).copyBytes());
        }
        assertFalse(CdR31IntroAssets.nativeNull(CdNativeFile.R31A,0x31));assertFalse(CdR31IntroAssets.nativeNull(CdNativeFile.R31B,0x35));
        var p=pool(32,0x2B,255);l(p,32,8,0x12345678);CdIntroSpikes.teleporterDelete(p,32);assertArrayEquals(new byte[64],p.state(32).copyBytes());
    }
    @Test void historicalShatterFallsThroughToParentMotionAfterSoundBeforeGetOff() throws Exception {
        var p=pool(40,0x30,2);b(p,40,0x19,18);b(p,40,0x16,32);b(p,40,1,0x80);b(p,41,0,0x31);
        var h=new Host();h.faultAt="getOff";h.fault=new IllegalStateException("getoff");
        assertSame(h.fault,assertThrows(IllegalStateException.class,()->new CdIntroSpikes(assets()).run(p,40,h)));
        assertEquals(0x200,p.state(40).word(CdObjectState.WordField.X_VELOCITY));assertEquals(0xFE00,p.state(40).word(CdObjectState.WordField.Y_VELOCITY));
        assertEquals(0x60,p.state(40).word(P5Call.wf(0x3E)));assertEquals(0x78,p.state(40).unsignedByte(P5Call.bf(0x3A)));
        assertEquals(List.of("read:TIME_ATTACK","fm:a3","getOff"),h.events);
    }
    @Test void forbiddenReadPortPoolMutationIsDetectedWithoutRollingBackReachedBytes() throws Exception {
        var p=pool(32,0x30,0);var h=new Host();h.onRead=()->b(p,33,0,6);
        assertThrows(IllegalStateException.class,()->new CdIntroSpikes(assets()).run(p,32,h));
        assertEquals(6,p.state(33).id());assertEquals(0,p.state(32).routine());
    }
    @Test void portFaultIdentitySurvivesALaterFullPoolInvariantFailure() throws Exception {
        var p=pool(32,0x30,0);var h=new Host();var original=new IllegalStateException("original read failure");
        h.onRead=()->{b(p,33,0,6);throw original;};
        assertSame(original,assertThrows(IllegalStateException.class,()->new CdIntroSpikes(assets()).run(p,32,h)));
        assertEquals(1,original.getSuppressed().length);assertEquals(6,p.state(33).id());
    }

}
