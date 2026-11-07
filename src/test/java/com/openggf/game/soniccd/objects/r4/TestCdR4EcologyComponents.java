package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.objects.CdObjectState.ByteField;
import com.openggf.game.soniccd.objects.CdObjectState.WordField;
import com.openggf.game.soniccd.player.CdPlayerMath;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.*;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Read.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed component controls; no authenticated TT ingress, World, route, native execution or pixels. */
class TestCdR4EcologyComponents {
    private static CdObjectPlacements emptyLayout(){
        byte[] bytes=new byte[26];bytes[1]=12;bytes[3]=20;
        for(int at:new int[]{4,12,20}){bytes[at]=-1;bytes[at+1]=-1;}
        return CdObjectPlacements.decodeLayout(bytes);
    }
    static class Fixture implements CdR4ObjectPorts, Guard {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(emptyLayout());
        final CdR4ObjectScope c=new CdR4ObjectScope(placement,this,this);
        final EnumMap<Read,Integer> globals=new EnumMap<>(Read.class);
        final List<Kind> calls=new ArrayList<>();final List<Operation> operations=new ArrayList<>();
        int sine,cosine=256,floor=8,wall=8;boolean solid;RuntimeException failure;Kind failAt;
        java.util.function.Consumer<Operation> before=op->{};
        java.util.function.Consumer<Operation> after=op->{};
        Fixture(){for(var f:Read.values())globals.put(f,0);}
        CdObjectState s(int n){return placement.pool().state(n);}
        void seed(int n,int id,int routine,int subtype){placement.pool().write(s(n).moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype));}
        void word(int n,WordField f,int value){placement.pool().write(s(n).moveWord(f,value));}
        void bytes(int n,ByteField f,int value){placement.pool().write(s(n).moveByte(f,value));}
        void longAt(int n,int offset,int value){
            var out=s(n);for(int k=0;k<2;k++){int o=offset+2*k;var field=Arrays.stream(WordField.values()).filter(f->f.byteOffset()==o).findFirst().orElseThrow();out=out.moveWord(field,k==0?value>>>16:value);}placement.pool().write(out);
        }
        int w(int n,int offset){byte[] b=s(n).copyBytes();return (b[offset]&255)<<8|(b[offset+1]&255);}
        int l(int n,int offset){return w(n,offset)<<16|w(n,offset+2);}
        void fullExcept(int...free){for(int n=32;n<128;n++)seed(n,0x7e,0,0);for(int n:free)seed(n,0,0,0);}
        public void before(CdR4ObjectScope producer,Operation op){assertSame(c,producer);assertTrue(c.isCurrent(op));operations.add(op);before.accept(op);}
        public void after(CdR4ObjectScope producer,Operation op){assertTrue(c.isCurrent(op));after.accept(op);}
        void effect(Kind kind){calls.add(kind);if(kind==failAt)throw failure;}
        public int read(Read field){effect(Kind.READ);return globals.get(field);}
        public int pointer(Data field){effect(Kind.POINTER);return 0x210000+field.ordinal()*0x100;}
        public int animalTile(int offset){effect(Kind.ANIMAL_TILE);assertEquals(0,offset);return 0x450;}
        public CdPlayerMath.SineCosine sine(int phase){effect(Kind.SINE);return new CdPlayerMath.SineCosine(sine,cosine);}
        public void animate(int n,Data table,int raw){effect(Kind.ANIMATE);}
        public void draw(int n){effect(Kind.DRAW);bytes(n,SPRITE_FLAGS,s(n).spriteFlags()|128);}
        public void despawn(int n,Integer anchor){effect(anchor==null?Kind.DESPAWN_CURRENT:Kind.DESPAWN_ANCHOR);}
        public int floor(int n){effect(Kind.FLOOR);return floor;}
        public int wall(int n,boolean right,int width){effect(right?Kind.WALL_RIGHT:Kind.WALL_LEFT);return wall;}
        public boolean solid(int n){effect(Kind.SOLID);return solid;}
        public void hurtWithYRewind(int n){effect(Kind.HURT_WITH_Y_REWIND);}
        public void fm(int sound){effect(Kind.FM);}
        public void testSpawnPoints(int n){effect(Kind.TEST_SPAWN_POINTS);}
        void unchangedOthers(CdObjectPlacementRuntime.Snapshot old,int... changed){
            var allow=new HashSet<Integer>();for(int n:changed)allow.add(n);
            assertArrayEquals(old.savedFlags().copy(),placement.capture().savedFlags().copy());
            assertEquals(old.status(),placement.status());
            for(var s:old.pool().slots())if(!allow.contains(s.identity().wholeIndex()))assertEquals(s,s(s.identity().wholeIndex()));
        }
    }
    @Test void tonboDirtyPhaseLiteralLongMathAndPostDrawReload(){
        var f=new Fixture();f.seed(64,0x2b,0,0);f.word(64,X,0x123);f.word(64,Y,0x234);f.word(64,X_FRACTION,0xabcd);f.word(64,Y_FRACTION,0x7654);
        f.word(64,ANTON_ANCHOR_X,0x55);f.sine=-256;var old=f.placement.capture();
        var out=CdR4Tonbo.run(f.c,64);
        assertEquals(0x122abcd,f.l(64,8));assertEquals(0x2247654,f.l(64,12));assertEquals(0x59,f.w(64,0x2e));
        assertEquals(0x7f,f.w(64,0x36));assertEquals(0x84,out.spriteFlags());assertEquals(0x123,f.w(64,0x3e));
        f.unchangedOthers(old,64);assertTrue(f.operations.stream().noneMatch(f.c::isCurrent));
    }
    @Test void goodFutureConversionRetainsOnlyIntegerCoordinatesAndStops(){
        var f=new Fixture();f.seed(40,0x2b,0,1);f.globals.put(GOOD_FUTURE,1);f.globals.put(TIME_ZONE,1);
        f.word(40,X,0x1234);f.word(40,Y,0x5678);f.bytes(40,SPRITE_FLAGS,255);f.word(40,X_FRACTION,65535);
        CdR4Tonbo.run(f.c,40);byte[] expected=new byte[64];expected[0]=0x18;expected[8]=0x12;expected[9]=0x34;expected[12]=0x56;expected[13]=0x78;
        assertArrayEquals(expected,f.s(40).copyBytes());assertFalse(f.calls.contains(Kind.FM));assertFalse(f.calls.contains(Kind.DRAW));
    }
    @Test void zeroFutureDoesNotReadUnknownTimeZone(){
        var f=new Fixture();f.seed(32,0x2b,2,0);f.before=op->{if(op.operand()==TIME_ZONE)fail("Unreached timeZone");};CdR4Tonbo.run(f.c,32);
    }
    @Test void tagaPartialAllocationRetainsDirtyChildrenAndDrawsClearedParent(){
        var f=new Fixture();f.fullExcept(33,34);f.seed(80,0x2d,0,0);f.bytes(33,ROUTINE,0x46);f.bytes(34,ANIMATION_TIME,0xa5);var old=f.placement.capture();
        CdR4TagaTaga.run(f.c,80);assertEquals(0,f.s(80).id());assertEquals(128,f.s(80).spriteFlags());
        assertEquals(0x2d,f.s(33).id());assertEquals(0xff,f.s(34).subtype());assertEquals(0x46,f.s(33).routine());assertEquals(0xa5,f.s(34).unsignedByte(ANIMATION_TIME));
        assertEquals(0xe400,f.w(33,0x3e));assertEquals(List.of(Kind.READ,Kind.POINTER,Kind.DRAW,Kind.DESPAWN_CURRENT),f.calls);
        f.unchangedOthers(old,80,33,34);
    }
    @Test void tagaFullPoolKeepsNoAllocationPrefix(){
        var f=new Fixture();f.fullExcept();f.seed(90,0x2d,0,0);var old=f.placement.capture();CdR4TagaTaga.run(f.c,90);f.unchangedOthers(old,90);assertEquals(0,f.s(90).id());
    }
    @Test void tagaMissileChecksRawIdAndSavedIdBeforeRoutine(){
        var f=new Fixture();f.seed(50,0x2d,0xff,255);f.word(50,EXPLOSION_POINTS_FRAME,0xd800);f.seed(32,0x2d,0,0);f.bytes(32,SAVED_FLAGS_ID,3);
        CdR4TagaTaga.run(f.c,50);assertArrayEquals(new byte[64],f.s(50).copyBytes());assertTrue(f.calls.isEmpty());
    }
    @Test void amenboProjectileOmitsFutureWaterAndParentReads(){
        var f=new Fixture();f.seed(40,0x2c,77,255);f.longAt(40,0x2c,-0x20000);f.longAt(40,0x38,0x212000);f.word(40,X,20);
        f.before=op->{if(op.kind()==Kind.READ)fail("Missile must not read globals");};CdR4Amenbo.run(f.c,40);
        assertEquals(18,f.s(40).centreX());assertEquals(77,f.s(40).routine());assertEquals(List.of(Kind.ANIMATE,Kind.DRAW,Kind.DESPAWN_CURRENT),f.calls);
    }
    @Test void amenboZeroCrossingAndWaterPositionKeepFractions(){
        var f=new Fixture();f.seed(40,0x2c,4,1);f.longAt(40,0x2c,-0x20);f.longAt(40,0x34,0x20);f.word(40,Y_FRACTION,0xaaaa);f.globals.put(WATER_Y,0x300);
        CdR4Amenbo.run(f.c,40);assertEquals(6,f.s(40).routine());assertEquals(0,f.l(40,0x2c));assertEquals(-0x20,f.l(40,0x34));assertEquals(0x2f8aaaa,f.l(40,12));assertFalse(f.calls.contains(Kind.ANIMATE));
    }
    @Test void yagoVariantLandingAndAttackGravityAreDifferent(){
        for(int subtype=0;subtype<2;subtype++){
            var f=new Fixture();f.seed(40,0x2e,2,subtype);f.word(40,Y,100);f.floor=2;
            CdR4Yago.run(f.c,40);assertEquals(subtype==0?101:98,f.s(40).centreY());assertEquals(4,f.s(40).routine());
            f.seed(40,0x2e,14,subtype);f.longAt(40,0x34,0);f.calls.clear();CdR4Yago.run(f.c,40);
            assertEquals(subtype==0?0:0x1000,f.l(40,0x34));
            assertEquals(subtype==0?1:0,Collections.frequency(f.calls,Kind.DESPAWN_CURRENT));
        }
    }
    @Test void yagoTerrainFailureRetainsEarlierWritesAndOriginalThrowable(){
        var f=new Fixture();f.seed(40,0x2e,4,0);f.longAt(40,0x2a,0x500000);f.longAt(40,0x2e,-0x4000);
        f.failure=new IllegalStateException("terrain");f.failAt=Kind.FLOOR;var old=f.placement.capture();
        assertSame(f.failure,assertThrows(IllegalStateException.class,()->CdR4Yago.run(f.c,40)));
        assertEquals(0x4fc000,f.l(40,0x2a));assertEquals(-0x4000,f.l(40,8));assertFalse(f.calls.contains(Kind.DRAW));f.unchangedOthers(old,40);
    }
    @Test void animalSuppressionReadsOnlyReachedPredicate(){
        var f=new Fixture();f.seed(40,0x2f,0,0);var old=f.placement.capture();CdR4Animal.run(f.c,40);assertEquals(old.pool().slots(),f.placement.capture().pool().slots());
        assertEquals(List.of(Kind.READ,Kind.READ,Kind.DESPAWN_CURRENT),f.calls);
        f.globals.put(TIME_ZONE,2);f.calls.clear();CdR4Animal.run(f.c,40);assertArrayEquals(new byte[64],f.s(40).copyBytes());
    }
    @Test void animalHologramInitRetainsDirtyPhaseAndHasNoDraw(){
        var f=new Fixture();f.seed(40,0x2f,0,0x80);f.longAt(40,0x30,0x12345678);CdR4Animal.run(f.c,40);
        assertEquals(254,f.s(40).routine());assertEquals(0x12345678,f.l(40,0x30));assertEquals(0x200,f.l(40,0x34));assertFalse(f.calls.contains(Kind.DRAW));
    }
    @Test void maceFailedSixthAllocationRetainsFiveUninitializedChildren(){
        var f=new Fixture();f.fullExcept(32,33,34,35,36);f.seed(100,0x36,0,0);var old=f.placement.capture();CdR4Mace.run(f.c,100);
        assertEquals(0,f.s(100).id());for(int n=32;n<37;n++){assertEquals(0x36,f.s(n).id());assertEquals(255,f.s(n).subtype());assertEquals(0xe900,f.w(n,0x3e));}
        f.unchangedOthers(old,100,32,33,34,35,36);assertFalse(f.calls.contains(Kind.SINE));
    }
    @Test void circleChildrenPhysicalPhasesAndInitNoDraw(){
        var f=new Fixture();f.seed(80,0x38,0,2);CdR4CircleSpikeBall.run(f.c,80);
        assertEquals(0xe800,f.w(32,0x2e));assertEquals(0xf000,f.w(33,0x2e));assertEquals(0xf800,f.w(34,0x2e));
        assertEquals(4,f.s(32).unsignedByte(PRIORITY));assertEquals(2,f.s(34).unsignedByte(PRIORITY));assertFalse(f.calls.contains(Kind.DRAW));
        CdR4CircleSpikeBall.run(f.c,80);assertEquals(64,f.s(80).centreX());
    }
    @Test void circlePartialFailureRetainsOriginalFirstChild(){
        var f=new Fixture();f.fullExcept(34);f.seed(90,0x38,0,2);CdR4CircleSpikeBall.run(f.c,90);
        assertEquals(0,f.s(90).id());assertEquals(0x38,f.s(34).id());assertEquals(2,f.s(34).routine());assertFalse(f.calls.contains(Kind.DRAW));
    }
    @Test void spikesDrawThenReloadRawParentAndOffsets(){
        var f=new Fixture();f.seed(40,0x22,2,1);f.seed(50,9,0,0);f.word(40,SPRING_PARENT,0xdc80);f.word(40,PLATFORM_ANCHOR_X,0xfe03);
        f.word(50,X,0x200);f.word(50,Y,0x300);CdR4Spikes.run(f.c,40);
        assertEquals(0x1fe,f.s(40).centreX());assertEquals(0x303,f.s(40).centreY());assertTrue((f.s(40).spriteFlags()&128)!=0);
        assertEquals(List.of(Kind.SOLID,Kind.DRAW,Kind.DESPAWN_ANCHOR),f.calls);
    }
    @Test void spikesSolidGatesHurtLazily(){
        var f=new Fixture();f.seed(40,0x22,2,1);f.bytes(40,FLAGS,8);f.solid=true;f.globals.put(INVINCIBLE,1);CdR4Spikes.run(f.c,40);
        assertFalse(f.calls.contains(Kind.HURT_WITH_Y_REWIND));assertFalse(f.operations.stream().anyMatch(op->op.operand()==PLAYER_ROUTINE));
        f.globals.put(INVINCIBLE,0);CdR4Spikes.run(f.c,40);assertTrue(f.calls.contains(Kind.HURT_WITH_Y_REWIND));
    }
    @Test void fallSpikesAllocatesOnlyAfterCurrentAndKeepsDirtyLong(){
        var f=new Fixture();f.seed(80,0x32,0,0);f.longAt(80,0x2a,0x1234);f.globals.put(STATIC_WATER_Y,0x200);f.word(80,Y,0x300);
        CdR4FallSpikes.run(f.c,80);assertEquals(0x32,f.s(81).id());assertEquals(0,f.s(32).id());assertEquals(0x1000,f.l(80,0x34));assertEquals(0x1234,f.l(80,0x2a));assertEquals(0x330,f.s(81).centreY());
    }
    @Test void offscreenFallSpikesDoesNotDispatchInvalidRoutineOrSolid(){
        var f=new Fixture();f.seed(40,0x32,255,0);CdR4FallSpikes.run(f.c,40);assertEquals(List.of(Kind.DRAW,Kind.DESPAWN_CURRENT),f.calls);
    }
    @Test void testObjectUsesSpawnPointsEntryWithoutDeletingIdSix(){
        var f=new Fixture();f.seed(40,6,4,0);var before=f.s(40);CdR4SimpleHazards.testObject(f.c,40);assertEquals(before,f.s(40));assertEquals(List.of(Kind.TEST_SPAWN_POINTS),f.calls);
    }
    @Test void callbackMutationRejectedWithoutRestoreAndThrowablePreserved(){
        var f=new Fixture();f.seed(40,0x2b,2,0);var original=new IllegalStateException("read");f.before=op->{if(op.kind()==Kind.READ){f.seed(90,5,0,0);throw original;}};
        assertSame(original,assertThrows(IllegalStateException.class,()->CdR4Tonbo.run(f.c,40)));
        assertEquals(5,f.s(90).id());assertEquals(1,original.getSuppressed().length);assertTrue(f.operations.stream().noneMatch(f.c::isCurrent));
    }
    @Test void reentryAndForgedEqualOperationAreNeverCurrent(){
        var f=new Fixture();f.seed(40,0x2b,2,0);f.before=op->{assertFalse(f.c.isCurrent(new Operation(op.kind(),op.actor(),op.target(),op.cut(),op.output(),op.operand())));assertThrows(IllegalStateException.class,()->CdR4Tonbo.run(f.c,40));};CdR4Tonbo.run(f.c,40);
    }
    @Test void allTenProgrammeDispatchControlsPreserveNativeNull(){
        int[] ids={6,0x22,0x2b,0x2c,0x2d,0x2e,0x2f,0x32,0x36,0x37,0x38};
        // Independently read historical ObjectIndex targets at N, in exact normal programme order.
        String[][] expected={
            {"R41A","TEST_SPAWN_POINTS","SPIKES","TONBO","AMENBO","TAGA_TAGA","YAGO","ANIMAL","FALL_SPIKES","MACE","NATIVE_NULL","NATIVE_NULL"},
            {"R41B","TEST_SPAWN_POINTS","SPIKES","TONBO","AMENBO","TAGA_TAGA","YAGO","ANIMAL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL"},
            {"R41C","TEST_SPAWN_POINTS","SPIKES","TONBO","AMENBO","TAGA_TAGA","YAGO","ANIMAL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL","CIRCLE_SPIKE_BALL"},
            {"R41D","TEST_SPAWN_POINTS","SPIKES","TONBO","AMENBO","TAGA_TAGA","YAGO","ANIMAL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL","CIRCLE_SPIKE_BALL"},
            {"R42A","TEST_SPAWN_POINTS","SPIKES","TONBO","AMENBO","TAGA_TAGA","YAGO","ANIMAL","NATIVE_NULL","MACE","SPIKE_BALL","NATIVE_NULL"},
            {"R42B","TEST_SPAWN_POINTS","SPIKES","TONBO","AMENBO","TAGA_TAGA","YAGO","ANIMAL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL"},
            {"R42C","TEST_SPAWN_POINTS","SPIKES","TONBO","AMENBO","TAGA_TAGA","YAGO","ANIMAL","NATIVE_NULL","MACE","SPIKE_BALL","CIRCLE_SPIKE_BALL"},
            {"R42D","TEST_SPAWN_POINTS","SPIKES","TONBO","AMENBO","TAGA_TAGA","YAGO","ANIMAL","NATIVE_NULL","MACE","SPIKE_BALL","CIRCLE_SPIKE_BALL"},
            {"R43C","TEST_SPAWN_POINTS","NATIVE_NULL","TONBO","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL","ANIMAL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL"},
            {"R43D","TEST_SPAWN_POINTS","NATIVE_NULL","TONBO","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL","ANIMAL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL","NATIVE_NULL"},
        };
        assertEquals(10,CdR4EcologyManifest.PROGRAMMES.size());
        for(String[] row:expected)for(int k=0;k<ids.length;k++)
            assertEquals(row[k+1],CdR4EcologyManifest.handler(CdNativeFile.valueOf(row[0]),ids[k]).name(),row[0]+" ID"+ids[k]);
        assertThrows(IllegalArgumentException.class,()->CdR4EcologyManifest.handler(CdNativeFile.R11A,0x2b));
        assertThrows(IllegalArgumentException.class,()->CdR4EcologyManifest.handler(CdNativeFile.R41A,0x40));
    }

    @Test void yagoNearUsesBmiOfWrappedWordSubtraction(){
        var f=new Fixture();f.seed(40,0x2e,2,0);f.floor=-32768;
        CdR4Yago.run(f.c,40);assertEquals(2,f.s(40).routine()); // $8000 - 5 = $7FFB, N clear.
        f.floor=-32763;CdR4Yago.run(f.c,40);assertEquals(4,f.s(40).routine()); // $8005 - 5 = $8000.
    }
    @Test void rawSelfParentLinkIsReadWithoutRepair(){
        var f=new Fixture();f.seed(40,0x22,2,1);f.word(40,SPRING_PARENT,0xda00);
        f.word(40,X,100);f.word(40,Y,200);f.word(40,PLATFORM_ANCHOR_X,0x02ff);
        CdR4Spikes.run(f.c,40);assertEquals(102,f.s(40).centreX());assertEquals(199,f.s(40).centreY());
    }
    @Test void missingRawLinkRetainsReachedFallPrefix(){
        var f=new Fixture();f.seed(40,0x32,4,0);f.bytes(40,SPRITE_FLAGS,128);f.longAt(40,0x2a,0x2000);
        assertThrows(UnsupportedOperationException.class,()->CdR4FallSpikes.run(f.c,40));
        assertEquals(0x2000,f.l(40,12));assertFalse(f.calls.contains(Kind.SOLID));
    }
    @Test void animationOperationCarriesExactRetainedPointerAndFaultPrefix(){
        var f=new Fixture();f.seed(40,0x2c,3,255);f.longAt(40,0x38,0x211234);
        var original=new IllegalStateException("selected animation byte");
        f.before=op->{if(op.kind()==Kind.ANIMATE){
            assertEquals(new AnimationOperand(Data.AMENBO_RETAINED_ANIM,0x211234),op.operand());
            f.bytes(40,ANIMATION_TIME,255);throw original;
        }};
        assertSame(original,assertThrows(IllegalStateException.class,()->CdR4Amenbo.run(f.c,40)));
        assertEquals(255,f.s(40).unsignedByte(ANIMATION_TIME));assertFalse(f.calls.contains(Kind.DRAW));
    }

}
