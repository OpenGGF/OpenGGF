package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.objects.CdR13Capsule.*;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdR13Capsule.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Tests first, all Java UNRUN. Actual C/D data; explicitly constructed public-pool/scalar/queue
* component ingress. No Frame/World/physics/native trace authority or natural completion claim. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR13Capsule {
    private List<CdR13FutureAssets> originals;
    @Test void bothActualProfilesRequireTheirExactRetainedPlacementSource()throws Exception{
        var all=actual();
        for(var a:all){
            var h=new H(a);
            assertSame(a.identity(),h.body.assets().identity());
        }
        var p=placement(all.getFirst());
        assertThrows(IllegalArgumentException.class,()->CdR13Capsule.fromAct3FutureAssets(p,all.getLast(),new Ports(){
        }));
    }
    @Test void initFallsIntoAnimationConditionalHitAndOriginalDrawWithUnwrittenFractionsAndFields()throws Exception{
        for(var a:actual()){
            var h=new H(a);
            h.put(90,0);
            h.pool.write(h.pool.state(90).moveWord(X_FRACTION,0xBEEF).moveByte(SAVED_FLAGS_ID,51).moveByte(FLAGS,0xA5).moveByte(SPRITE_FLAGS,0x83));
            var r=h.body.run(90);
            var s=r.state();
            assertEquals(2,s.routine());
            assertEquals(0x2481,s.word(TILE));
            assertEquals(h.body.assets().mappingAddress(),s.longBits(MAPPINGS));
            assertEquals(0xBEEF,s.word(X_FRACTION));
            assertEquals(51,s.savedFlagsId());
            assertEquals(0xA5,s.flags());
            assertEquals(32,s.unsignedByte(X_RADIUS));
            assertEquals(32,s.unsignedByte(WIDTH));
            assertEquals(24,s.unsignedByte(Y_RADIUS));
            assertTrue(h.events.contains("read:HIT_FLAGS"));
            assertFalse(h.events.contains("read:HIT_X_RADIUS"));
            assertTrue(r.effects().getLast() instanceof DrawReturned);
        }
    }
    @Test void absentReadOwnerStopsAfterRealInitAndSharedAnimationPrefix()throws Exception{
        var a=actual().getFirst();
        var p=placement(a);
        p.pool().write(p.pool().state(90).moveByte(ID,0x15));
        var b=CdR13Capsule.fromAct3FutureAssets(p,a,new Ports(){
        });
        var f=assertThrows(UnownedCapsule.class,()->b.run(90));
        assertEquals(Site.HIT_FLAGS,f.site());
        assertEquals(2,p.pool().state(90).routine());
        assertEquals(1,p.pool().state(90).unsignedByte(ANIMATION_TIME));
    }
    @Test void hitUsesBallFlagAndSignedRadiiWithExactStrictWidthAndHeightBounds()throws Exception{
        for(int dx:new int[]{
            -41,-40,0,40,41
        }){
            var h=new H(actual().getFirst());
            h.put(90,2);
            h.values.put(Field.PLAYER_FLAGS,4);
            h.values.put(Field.PLAYER_X,0xC10+dx);
            h.body.run(90);
            assertEquals(dx>=-41&&dx<41?4:2,h.pool.state(90).routine());
        }
        var h=new H(actual().getFirst());
        h.put(90,2);
        h.values.put(Field.PLAYER_FLAGS,4);
        h.values.put(Field.PLAYER_X_RADIUS,0x80);
        h.body.run(90);
        assertFalse(h.events.contains("read:HIT_Y_RADIUS"));
    }
    @Test void breakingStoresHudAndSourceBeforeFreshBounceReadsAndPreservesOtherVelocity()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,2);
        h.values.put(Field.PLAYER_FLAGS,4);
        h.onRead=c->{
            if(c.site()==Site.BOUNCE_X)h.values.put(Field.PLAYER_X,0xC80);
        };
        h.body.run(90);
        assertEquals(0,h.values.get(Field.UPDATE_HUD_TIME));
        assertEquals(4,h.pool.state(90).routine());
        assertEquals(2,h.pool.state(90).unsignedByte(MAP_FRAME));
        assertEquals(120,h.pool.state(90).unsignedByte(LEVEL_END_TIMER));
        assertEquals((-0x400>>2)&65535,h.values.get(Field.PLAYER_X_VELOCITY));
        assertEquals(0x200,h.values.get(Field.PLAYER_Y_VELOCITY));
        assertTrue(h.events.indexOf("store:HUD_STOP")<h.events.indexOf("read:BOUNCE_X"));
    }
    @Test void bounceNegatesWordBeforeSignedArithmeticShiftIncluding8000Overflow()throws Exception{
        for(int v:new int[]{
            0,1,0x7FFF,0x8000,0xFFFF
        }){
            var h=new H(actual().getFirst());
            h.put(90,2);
            h.values.put(Field.PLAYER_FLAGS,4);
            h.values.put(Field.PLAYER_Y_VELOCITY,v);
            h.body.run(90);
            assertEquals(((short)-((short)v)>>2)&65535,h.values.get(Field.PLAYER_Y_VELOCITY));
        }
    }
    @Test void allEightExplosionOffsetsUseFmBeforeTheFirstChildIdStore()throws Exception{
        for(int pair=0;pair<8;pair++){
            var h=new H(actual().getFirst());
            h.put(90,4);
            h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,4*pair+1));
            h.onFm=c->assertEquals(0,h.pool.state(32).id());
            h.body.run(90);
            var e=h.pool.state(32);
            var raw=h.body.assets().view(CdR13CapsuleAssets.View.EXPLOSION_POSITIONS);
            assertEquals(0x18,e.id());
            assertEquals(1,e.unsignedByte(ROUTINE_2));
            assertEquals((0xC10+raw.signedByte(pair*2))&65535,e.centreX());
            assertEquals((0x100+raw.signedByte(pair*2+1))&65535,e.centreY());
            assertEquals(1,h.events.stream().filter(x->x.equals("fm")).count());
        }
    }
    @Test void fmFailureKeepsTimerAndOriginalUnallocatedDirtySlotWithoutChildWrites()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,4);
        h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,1));
        h.pool.write(h.pool.state(32).moveWord(TILE,0xABCD));
        var before=h.pool.state(32);
        var original=new IllegalStateException("original FM owner");
        h.onFm=c->{
            throw original;
        };
        assertSame(original,assertThrows(IllegalStateException.class,()->h.body.run(90)));
        assertEquals(0,h.pool.state(90).unsignedByte(LEVEL_END_TIMER));
        assertEquals(before,h.pool.state(32));
    }
    @Test void fullPoolExplosionReturnsWithoutSoundOrFabricatedChild()throws Exception{
        var h=new H(actual().getFirst());
        h.full();
        h.put(90,4);
        h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,1));
        h.body.run(90);
        assertFalse(h.events.contains("fm"));
        assertTrue(h.events.contains("draw"));
    }
    @Test void seedFinishRestoresActualLevelPaletteThenAllocatesSevenOriginalDirtySlots()throws Exception{
        for(var a:actual()){
            var h=new H(a);
            for(int n=32;n<39;n++)h.pool.write(h.pool.state(n).moveByte(SPRITE_FLAGS,0x83).moveByte(PRIORITY,7).moveByte(SAVED_FLAGS_ID,50+n-32).moveWord(X_FRACTION,0x5A5A).moveByte(ANIMATION_TIME,0x77));
            h.put(90,4);
            h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,0));
            h.onPalette=c->{
                assertEquals(0,h.pool.state(32).id());
                assertSame(h.body.assets().restoredPalette(),c.binding());
            };
            h.body.run(90);
            int[] xv={
                0,-128,128,-256,256,-384,384
            };
            for(int i=0;i<7;i++){
                var s=h.pool.state(32+i);
                assertEquals(0x15,s.id());
                assertEquals(10,s.routine());
                assertEquals(xv[i],s.signedWord(X_VELOCITY));
                assertEquals(-0x600,s.signedWord(Y_VELOCITY));
                assertEquals(0x87,s.spriteFlags());
                assertEquals(7,s.unsignedByte(PRIORITY));
                assertEquals(0x5A5A,s.word(X_FRACTION));
                assertEquals(0x77,s.unsignedByte(ANIMATION_TIME));
                assertEquals(h.body.assets().mappingAddress(),s.longBits(MAPPINGS));
            }
            assertEquals(6,h.pool.state(90).routine());
            assertEquals(60,h.pool.state(90).unsignedByte(LEVEL_END_TIMER));
            assertFalse(h.events.contains("draw"));
        }
    }
    @Test void everySeedAllocationFailureRetainsItsFinitePrefixAndParentStillStartsResultsDelay()throws Exception{
        for(int count=0;count<7;count++){
            var h=new H(actual().getFirst());
            h.full();
            for(int n=32;n<32+count;n++)h.pool.objNull(n);
            h.put(90,4);
            h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,0));
            h.body.run(90);
            assertEquals(count,h.pool.capture().slots().stream().filter(s->s.id()==0x15&&s.routine()==10).count());
            assertEquals(6,h.pool.state(90).routine());
            assertEquals(60,h.pool.state(90).unsignedByte(LEVEL_END_TIMER));
        }
    }
    @Test void restorePaletteFaultPreservesNegativeTimerBeforeSeedsAndRoutineAdvance()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,4);
        h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,0));
        var f=new IllegalStateException("original palette publication");
        h.onPalette=c->{
            throw f;
        };
        assertSame(f,assertThrows(IllegalStateException.class,()->h.body.run(90)));
        assertEquals(255,h.pool.state(90).unsignedByte(LEVEL_END_TIMER));
        assertEquals(4,h.pool.state(90).routine());
        assertEquals(0,h.pool.state(32).id());
    }
    @Test void seedMotionUsesSharedRawGravityBeforeOriginalFloorAndPreservesFractionsAndFlags()throws Exception{
        for(int ctrl:new int[]{
            0,2,4,8
        }){
            var h=new H(actual().getFirst());
            h.put(90,10);
            h.pool.write(h.pool.state(90).moveByte(ANIMATION,1).moveByte(RAW_CONTROL_2C,ctrl).moveByte(FLAGS,8).moveByte(STAND_LINK_3D,7).moveWord(X_VELOCITY,0xFF80).moveWord(Y_VELOCITY,0xF900).moveWord(X_FRACTION,0x1234));
            var s=h.pool.state(90);
            var expected=CdPlayerMovement.moveObjectWithGravity(new CdPlayerMovement.RawObjectMotion(s.longBits(X_POSITION),s.longBits(Y_POSITION),s.signedWord(X_VELOCITY),s.signedWord(Y_VELOCITY),s.flags(),ctrl,7));
            h.body.run(90);
            assertEquals(expected.centreX16_16(),h.pool.state(90).longBits(X_POSITION));
            assertEquals(expected.centreY16_16(),h.pool.state(90).longBits(Y_POSITION));
            assertEquals(expected.yVelocity(),h.pool.state(90).signedWord(Y_VELOCITY));
            assertEquals(8,h.pool.state(90).flags());
            assertTrue(h.events.contains("store:FLOOR_ANGLE"));
        }
    }
    @Test void negativeFloorConvertsSeedIntoRealFlowerRequestWithoutSameDispatchFlowerOrDraw()throws Exception{
        for(var a:actual()){
            var h=new H(a);
            h.put(90,10);
            h.pool.write(h.pool.state(90).moveByte(ANIMATION,1).moveByte(RAW_CONTROL_2C,8).moveWord(X_VELOCITY,0).moveWord(Y_VELOCITY,0));
            var terrain=CdPlayerTerrain.fromStageTerrain(a.terrain());
            int y=-1;
            for(int candidate=0;candidate<=0x300;candidate++){
                var s=h.pool.state(90).moveWord(Y,candidate);
                if(CdObjectCollisionCheck.objGetFloorDist(terrain,s).distance()<0){
                    y=candidate;
                    break;
                }
            }
            assertTrue(y>=0,"source terrain contact vector");
            h.pool.write(h.pool.state(90).moveWord(Y,y));
            h.body.run(90);
            assertEquals(0x1F,h.pool.state(90).id());
            assertEquals(1,h.pool.state(90).subtype());
            assertEquals(0,h.pool.state(90).routine());
            assertFalse(h.events.contains("draw"));
        }
    }
    @Test void floorAngleFaultRetainsActualSharedMotionPrefixAndOriginalException()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,10);
        h.pool.write(h.pool.state(90).moveByte(ANIMATION,1).moveWord(X_VELOCITY,0x100).moveWord(Y_VELOCITY,0x200));
        var original=new IllegalArgumentException("actual angle owner");
        h.onStore=c->{
            if(c.site()==Site.FLOOR_ANGLE)throw original;
        };
        assertSame(original,assertThrows(IllegalArgumentException.class,()->h.body.run(90)));
        assertEquals(0xC11,h.pool.state(90).centreX());
        assertEquals(0x102,h.pool.state(90).centreY());
        assertEquals(0x238,h.pool.state(90).word(Y_VELOCITY));
    }
    @Test void startResultsPerformsExactMusicControlAllocationAndRealBonusTablePrefix()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,6);
        h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,1));
        h.pool.write(h.pool.state(32).moveWord(ROBOT_GENERATOR_BASE_Y,0xABCD).moveWord(MOVING_SPRING_BASE_Y,0xA55A));
        h.values.put(Field.CONTROL_LOCK,0xA0);
        h.values.put(Field.TIME_SECONDS,30);
        h.values.put(Field.RINGS,65535);
        h.body.run(90);
        assertEquals(List.of(CpuCommand.RESULTS_MUSIC),h.cpu);
        assertEquals(0xA1,h.values.get(Field.CONTROL_LOCK));
        assertEquals(0x808,h.values.get(Field.PLAYER_CONTROL_HOLD));
        assertEquals(8,h.pool.state(90).routine());
        assertEquals(180,h.pool.state(90).unsignedByte(LEVEL_END_TIMER));
        assertEquals(0x3A,h.pool.state(32).id());
        assertEquals(16,h.pool.state(32).unsignedByte(RESULTS_TIMER));
        assertEquals(0x5A,h.pool.state(32).word(MOVING_SPRING_BASE_Y)&255);
        assertEquals(10000,h.values.get(Field.TIME_BONUS));
        assertEquals(65436,h.values.get(Field.RING_BONUS));
        assertTrue(h.events.indexOf("store:HUD_BONUS")<h.events.indexOf("read:TIME_MINUTES"));
        assertFalse(h.events.contains("draw"));
    }
    @Test void startResultsConditionalPastAndSszReadsRetainNativeExtraStores()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,6);
        h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,1));
        h.values.put(Field.TIME_ZONE,0);
        h.values.put(Field.ZONE_ACT,0x502);
        h.body.run(90);
        assertEquals(List.of(CpuCommand.FADE_PCM,CpuCommand.RESULTS_MUSIC),h.cpu);
        assertEquals(0,h.values.get(Field.PLAYER_CONTROL_HOLD));
    }
    @Test void resultsAllocationExhaustionStopsAtFirstUnownedStoreAfterRealSourceControlAndMusicPrefix()throws Exception{
        var h=new H(actual().getFirst());
        h.full();
        h.put(90,6);
        h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,1));
        var f=assertThrows(UnownedCapsule.class,()->h.body.run(90));
        assertEquals(Boundary.RESULTS_ALLOCATION,f.boundary());
        assertEquals(8,h.pool.state(90).routine());
        assertEquals(180,h.pool.state(90).unsignedByte(LEVEL_END_TIMER));
        assertEquals(0x808,h.values.get(Field.PLAYER_CONTROL_HOLD));
        assertFalse(h.events.contains("store:HUD_BONUS"));
    }
    @Test void resultsTimerWrapAndActiveRtsRetainWholePoolAndAllStatusBankBytes()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,6);
        var before=h.placement.capture();
        h.body.run(90);
        assertEquals(255,h.pool.state(90).unsignedByte(LEVEL_END_TIMER));
        assertTrue(h.events.isEmpty());
        h.pool.write(h.pool.state(90).moveByte(ROUTINE,8));
        before=h.placement.capture();
        h.body.run(90);
        assertEquals(before.pool().slots(),h.pool.capture().slots());
        assertEquals(before.status(),h.placement.status());
        assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
    }
    @Test void malformedAnimationPreservesRealSpeedAndStopsBeforePhysicsReadsOrDraw()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,10);
        h.pool.write(h.pool.state(90).moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_FRAME,255));
        var old=h.pool.state(90);
        assertThrows(UnsupportedOperationException.class,()->h.body.run(90));
        assertEquals(2,h.pool.state(90).unsignedByte(ANIMATION_TIME));
        assertEquals(old.longBits(X_POSITION),h.pool.state(90).longBits(X_POSITION));
        assertTrue(h.events.isEmpty());
    }
    @Test void liveCallReceiptReentryRetirementAndPoolFenceKeepOriginalFailure()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,2);
        h.onRead=c->{
            assertTrue(h.body.isCurrentCall(c));
            assertThrows(IllegalStateException.class,()->h.body.run(90));
        };
        h.body.run(90);
        var c=(ReadCall)h.calls.getFirst();
        assertFalse(h.body.isCurrentCall(c));
        assertThrows(IllegalStateException.class,()->c.complete(0));
        var k=new H(actual().getFirst());
        k.put(90,2);
        var original=new IllegalStateException("read fault");
        k.onRead=q->{
            k.pool.write(k.pool.state(60).moveByte(ID,0x99));
            throw original;
        };
        assertSame(original,assertThrows(IllegalStateException.class,()->k.body.run(90)));
        assertEquals(1,original.getSuppressed().length);
        assertEquals(0x99,k.pool.state(60).id());
    }
    @Test void invalidRoutineAndForeignSourceDoNotInventDispatchOrNativeWork()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,1);
        var before=h.pool.capture();
        assertEquals(Boundary.ROUTINE_DIRECTORY,assertThrows(UnownedCapsule.class,()->h.body.run(90)).boundary());
        assertEquals(before.slots(),h.pool.capture().slots());
        h.pool.write(h.pool.state(90).moveByte(ID,0x1F));
        assertEquals(Boundary.OBJECT_ID,assertThrows(UnownedCapsule.class,()->h.body.run(90)).boundary());
    }
    @Test void actualSeedChildrenRespectCallerPhysicalOrderWithoutAnInternalBackedgeDispatch()throws Exception{
        var a=actual().getFirst();
        var earlier=new H(a);
        earlier.put(90,4);
        earlier.body.run(90);
        assertEquals(0x100,earlier.pool.state(32).centreY());
        earlier.body.run(32);
        assertEquals(0xFA,earlier.pool.state(32).centreY());
        var later=new H(a);
        later.put(32,4);
        for(int w=32;w<128;w++)if(later.pool.state(w).id()==0x15)later.body.run(w);
        assertEquals(0xFA,later.pool.state(33).centreY());
    }
    @Test void nativeBonusClampUsesAllByteInputsWithoutDefaultOrClockAndWrappedExplodeStillFinishes()throws Exception{
        var h=new H(actual().getFirst());
        h.put(90,6);
        h.pool.write(h.pool.state(90).moveByte(LEVEL_END_TIMER,1));
        h.values.put(Field.TIME_MINUTES,255);
        h.values.put(Field.TIME_SECONDS,255);
        h.values.put(Field.RINGS,1);
        h.body.run(90);
        assertEquals(0,h.values.get(Field.TIME_BONUS));
        assertEquals(100,h.values.get(Field.RING_BONUS));
        var k=new H(actual().getFirst());
        k.put(90,4);
        k.pool.write(k.pool.state(90).moveByte(LEVEL_END_TIMER,129));
        k.body.run(90);
        assertEquals(6,k.pool.state(90).routine());
    }
    private static CdObjectPlacementRuntime placement(CdR13FutureAssets a){
        return CdObjectPlacementRuntime.fromAct3FutureAssets(a,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));
    }
    private final class H implements Ports {
        final CdObjectPlacementRuntime placement;
        final CdObjectPool pool;
        final CdR13Capsule body;
        final EnumMap<Field,Integer> values=new EnumMap<>(Field.class);
        final List<String> events=new ArrayList<>();
        final List<Call> calls=new ArrayList<>();
        final List<CpuCommand> cpu=new ArrayList<>();
        final CdObjectDrawQueue queue;
        Consumer<ReadCall> onRead;
        Consumer<StoreCall> onStore;
        Consumer<FmCall> onFm;
        Consumer<PaletteCall> onPalette;
        H(CdR13FutureAssets a){
            placement=placement(a);
            pool=placement.pool();
            for(var f:Field.values())values.put(f,0);
            values.put(Field.PLAYER_X,0xC10);
            values.put(Field.PLAYER_Y,0x100);
            values.put(Field.PLAYER_X_RADIUS,9);
            values.put(Field.PLAYER_Y_RADIUS,19);
            values.put(Field.PLAYER_X_VELOCITY,0x400);
            values.put(Field.PLAYER_Y_VELOCITY,0x200);
            values.put(Field.UPDATE_HUD_TIME,1);
            values.put(Field.TIME_ZONE,2);
            values.put(Field.ZONE_ACT,2);
            queue=new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots(){
                public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef r){
                    throw new AssertionError("No renderer");
                }
                public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef r,boolean bit){
                    var s=pool.state(r.wholeIndex());pool.write(s.moveByte(SPRITE_FLAGS,bit?s.spriteFlags()|128:s.spriteFlags()&127));
                }
            },(x,y,z)->{
                throw new AssertionError("No mapping drain");
            },new byte[640]);
            queue.beginPass();
            body=CdR13Capsule.fromAct3FutureAssets(placement,a,this);
        }
        void put(int whole,int routine){
            pool.write(pool.state(whole).moveByte(ID,0x15).moveByte(ROUTINE,routine).moveWord(X,0xC10).moveWord(Y,0x100));
        }
        void full(){
            for(int w=32;w<128;w++)pool.write(pool.state(w).moveByte(ID,1));
        }
        void call(Call c){
            assertTrue(body.isCurrentCall(c));
            calls.add(c);
            assertEquals(pool.capture().slots(),c.actualCut().pool().slots());
            assertSame(pool.state(c.expectedSource().identity().wholeIndex()),c.expectedSource());
        }
        public ReadReceipt read(ReadCall c){
            call(c);
            events.add("read:"+c.site());
            if(onRead!=null)onRead.accept(c);
            var r=c.complete(values.get(c.field()));
            assertFalse(body.isCurrentCall(c));
            assertThrows(IllegalStateException.class,()->c.complete(r.value()));
            return r;
        }
        public StoreReceipt store(StoreCall c){
            call(c);
            events.add("store:"+c.site());
            int before=values.get(c.field()),after=c.form()==StoreForm.BSET0?before|1:c.value();
            values.put(c.field(),after);
            if(onStore!=null)onStore.accept(c);
            return c.complete(before,after);
        }
        public Completion palette(PaletteCall c){
            call(c);
            events.add("palette");
            if(onPalette!=null)onPalette.accept(c);
            return c.complete();
        }
        public Completion fm(FmCall c){
            call(c);
            events.add("fm");
            if(onFm!=null)onFm.accept(c);
            return c.complete();
        }
        public Completion subCpu(CpuCall c){
            call(c);
            cpu.add(c.command());
            return c.complete();
        }
        public DrawReceipt draw(DrawCall c){
            call(c);
            events.add("draw");
            var s=c.expectedSource();
            return c.complete(queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(s.identity().wholeIndex()),s.spriteFlags(),s.centreX(),s.centreY(),s.unsignedByte(Y_RADIUS),s.unsignedByte(WIDTH),s.unsignedByte(PRIORITY)),new CdObjectDrawQueue.CameraWords(0xB00,0)));
        }
    }
    private List<CdR13FutureAssets> actual()throws Exception{
        if(originals!=null)return originals;
        String root=System.getProperty("soniccd.media.root");
        assumeTrue(root!=null&&!root.isBlank(),"Optional original USA media");
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size());
            cue=cues.getFirst();
        }
        try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            originals=List.of(CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()));
        }
        return originals;
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
