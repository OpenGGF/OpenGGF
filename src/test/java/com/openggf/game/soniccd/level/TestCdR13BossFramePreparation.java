package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdR13Boss.Field;
import com.openggf.game.soniccd.objects.CdR13Boss.StoreForm;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Tests authored first; all Java/build/media/native/GL UNRUN. Actual C/D assets with explicitly
* constructed complete State8/Environment4/art/clock component values, never World/boss execution.
* N1ecc5f3a objects/r1/boss.asm30-64,232-250,307-330,474-514,619-696 and CollisionCheck157-179.
* Only public actual Frame captures are used; no private owner/token/snapshot or issued call is forged. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR13BossFramePreparation {
    private List<CdR13FutureAssets> originals;
    record Vector(Field field,int before,int operand,int after,String path){
    }
    private static final List<Vector> VECTORS=List.of(
    new Vector(Field.BOSS_FIGHT,0x78,1,1,"environment.movement.bounds.bossFight"),
    new Vector(Field.BOTTOM_BOUND,0x3456,0xFF,0xFF,"environment.movement.bounds.bottom"),
    new Vector(Field.LEFT_BOUND,0x12,0xAC0,0xAC0,"environment.movement.bounds.left"),
    new Vector(Field.RIGHT_BOUND,0x2345,0xAC0,0xAC0,"environment.movement.bounds.right"),
    new Vector(Field.CAMERA_Y_CENTRE,0x2345,0xC8,0xC8,"environment.movement.globals.cameraYCentre"),
    // ASSIGN angle is a constructed metadata-form control; native recoil uses XOR80 below.
    new Vector(Field.PLAYER_ANGLE,0x56,0xD6,0xD6,"state.motion.angle"),
    new Vector(Field.PLAYER_GROUND_SPEED,0xFCBB,0x400,0x400,"state.motion.groundVelocity"),
    new Vector(Field.PLAYER_X_SPEED,0xFEDD,0xFC00,-0x400,"state.motion.xVelocity"),
    new Vector(Field.PLAYER_Y_SPEED,0x234,0xFC00,-0x400,"state.motion.yVelocity"),
    new Vector(Field.PRIMARY_ANGLE,0x34,0x80,0x80,"environment.movement.globals.primaryAngle"));
    @Test void exactlyOnePublicFourArgumentMethodReusesTheUnchangedPrivateCarrier()throws Exception{
        var method=CdPlayerFrameRuntime.class.getDeclaredMethod("prepareObjectBossScalar",Field.class,StoreForm.class,int.class,int.class);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
        assertEquals(CdPlayerFrameRuntime.ObjectSolidPublication.class,method.getReturnType());
        assertEquals(1,Arrays.stream(CdPlayerFrameRuntime.class.getDeclaredMethods()).filter(m->m.getName().equals("prepareObjectBossScalar")).count());
        assertTrue(Arrays.stream(CdPlayerFrameRuntime.ObjectSolidPublication.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
    }
    @Test void tenFieldAssignmentVectorsPrebuildAllValuesWithoutMutationAndAdoptOnlyPreparedReferences()throws Exception{
        for(var source:actual())for(var v:VECTORS)try(var f=frame(source,state(),environment())){
            var actor=f.captureActor();
            var shared=f.captureShared();
            var stage=f.stageSource();
            var p=f.prepareObjectBossScalar(v.field(),StoreForm.ASSIGN,v.before(),v.operand());
            assertSame(actor.state(),p.expectedState());
            assertSame(shared.environment(),p.expectedEnvironment());
            unchanged(f,actor,shared);
            assertOnlyScalar(actor.state(),shared.environment(),p.finalState(),p.finalEnvironment(),v.path(),v.after());
            preservedReferences(actor.state(),shared.environment(),p.finalState(),p.finalEnvironment(),v.field());
            f.validatePreparedObjectSolidPublication(p);
            unchanged(f,actor,shared);
            f.applyPreparedObjectSolidPublication(p);
            assertSame(p.finalState(),f.state());
            assertSame(p.finalEnvironment(),f.environment());
            assertSame(actor.display(),f.display());
            assertSame(shared.artState(),f.artState());
            assertSame(shared.vIntClock(),f.vIntClock());
            assertSame(stage,f.stageSource());
            assertEquals(shared.paused(),f.paused());
        }
    }
    @Test void nativeAngleXor80PreservesEveryOtherMotionByteFractionsRotationAndEnvironment()throws Exception{
        for(var source:actual())try(var f=frame(source,state(),environment())){
            var a=f.captureActor();
            var e=f.captureShared();
            var p=f.prepareObjectBossScalar(Field.PLAYER_ANGLE,StoreForm.XOR,0x56,0x80);
            assertOnlyScalar(a.state(),e.environment(),p.finalState(),p.finalEnvironment(),"state.motion.angle",0xD6);
            assertSame(e.environment(),p.finalEnvironment());
            preservedReferences(a.state(),e.environment(),p.finalState(),p.finalEnvironment(),Field.PLAYER_ANGLE);
            unchanged(f,a,e);
            f.applyPreparedObjectSolidPublication(p);
            assertEquals(0x01234567,f.state().motion().centreX16_16());
            assertEquals(0x023489AB,f.state().motion().centreY16_16());
            assertEquals(a.state().rotationDistance(),f.state().rotationDistance());
            assertEquals(a.state().rotationAngle(),f.state().rotationAngle());
        }
    }
    @Test void rawWordEdgesBecomeSignedVelocityWordsWithoutChangingPositionOrNormalizingBounds()throws Exception{
        var source=actual().getFirst();
        for(var v:VECTORS)for(int operand:new int[]{
            0,1,0x7FFF,0x8000,0xFFFF
        }){
            if(v.field().bits()!=16)continue;
            try(var f=frame(source,state(),environment())){
                var a=f.captureActor();
                var e=f.captureShared();
                var p=f.prepareObjectBossScalar(v.field(),StoreForm.ASSIGN,v.before(),operand);
                int after=isVelocity(v.field())?(short)operand:operand;
                assertOnlyScalar(a.state(),e.environment(),p.finalState(),p.finalEnvironment(),v.path(),after);
                unchanged(f,a,e);
            }
        }
    }
    @Test void xorUsesTheFullNativeWidthForEachAllowedMetadataFieldWithoutCreatingCallAuthority()throws Exception{
        // Constructed pure metadata controls; only PLAYER_ANGLE/XOR80 is the native Boss site.
        var source=actual().getFirst();
        for(var v:VECTORS)try(var f=frame(source,state(),environment())){
            var a=f.captureActor();
            var e=f.captureShared();
            int mask=v.field().bits()==8?255:65535;
            int raw=v.before()^mask;
            var p=f.prepareObjectBossScalar(v.field(),StoreForm.XOR,v.before(),mask);
            assertOnlyScalar(a.state(),e.environment(),p.finalState(),p.finalEnvironment(),v.path(),isVelocity(v.field())?(short)raw:raw);
            unchanged(f,a,e);
        }
    }
    @Test void everyHostOnlyAndReadOnlyBossFieldRejectsWithoutAnyOwnerChange()throws Exception{
        var allowed=EnumSet.noneOf(Field.class);
        VECTORS.forEach(v->allowed.add(v.field()));
        try(var f=frame(actual().getFirst(),state(),environment())){
            var a=f.captureActor();
            var e=f.captureShared();
            for(var field:Field.values())if(!allowed.contains(field))for(var form:StoreForm.values()){
                assertThrows(IllegalArgumentException.class,()->f.prepareObjectBossScalar(field,form,0,0));
                unchanged(f,a,e);
            }
            assertEquals(10,allowed.size());
        }
    }
    @Test void staleScalarPreimagesAndNoncanonicalRawWidthsRejectBeforePreparation()throws Exception{
        try(var f=frame(actual().getFirst(),state(),environment())){
            var a=f.captureActor();
            var e=f.captureShared();
            for(var v:VECTORS){
                assertThrows(IllegalArgumentException.class,()->f.prepareObjectBossScalar(v.field(),StoreForm.ASSIGN,v.before()^1,v.operand()));
                int outside=v.field().bits()==8?256:65536;
                for(int raw:new int[]{
                    -1,outside
                }){
                    assertThrows(IllegalArgumentException.class,()->f.prepareObjectBossScalar(v.field(),StoreForm.ASSIGN,raw,v.operand()));
                    assertThrows(IllegalArgumentException.class,()->f.prepareObjectBossScalar(v.field(),StoreForm.XOR,v.before(),raw));
                }
                unchanged(f,a,e);
            }
        }
    }
    @Test void preparedCarrierRejectsAStaleActualStateOrEnvironmentOnTheSameFrame()throws Exception{
        try(var f=frame(actual().getFirst(),state(),environment())){
            var a=f.captureActor();
            var e=f.captureShared();
            var ready=f.prepareObjectBossScalar(Field.RIGHT_BOUND,StoreForm.ASSIGN,0x2345,0xAC0);
            f.applyPreparedObjectSolidPublication(f.prepareObjectBossScalar(Field.PLAYER_ANGLE,StoreForm.XOR,0x56,0x80));
            var changed=f.captureActor();
            assertThrows(IllegalArgumentException.class,()->f.validatePreparedObjectSolidPublication(ready));
            assertThrows(IllegalArgumentException.class,()->f.applyPreparedObjectSolidPublication(ready));
            unchanged(f,changed,e);
            f.restoreActor(a);
            f.applyPreparedObjectSolidPublication(f.prepareObjectBossScalar(Field.PRIMARY_ANGLE,StoreForm.ASSIGN,0x34,0x35));
            var changedShared=f.captureShared();
            assertThrows(IllegalArgumentException.class,()->f.applyPreparedObjectSolidPublication(ready));
            unchanged(f,a,changedShared);
            f.restoreShared(e);
            f.applyPreparedObjectSolidPublication(ready);
            assertSame(ready.finalState(),f.state());
            assertSame(ready.finalEnvironment(),f.environment());
        }
    }
    @Test void completeEightStateAndFourEnvironmentComponentsParticipateInExistingValueCas()throws Exception{
        var source=actual().getFirst();
        var s=state();
        var env=environment();
        try(var f=frame(source,s,env)){
            var ready=f.prepareObjectBossScalar(Field.BOSS_FIGHT,StoreForm.ASSIGN,0x78,1);
            var a=f.captureActor();
            var e=f.captureShared();
            for(int part=0;part<8;part++)try(var other=frame(source,changedState(s,part),env)){
                var oa=other.captureActor();
                var oe=other.captureShared();
                assertThrows(IllegalArgumentException.class,()->other.applyPreparedObjectSolidPublication(ready));
                unchanged(other,oa,oe);
            }
            for(int part=0;part<4;part++)try(var other=frame(source,s,changedEnvironment(env,part))){
                var oa=other.captureActor();
                var oe=other.captureShared();
                assertThrows(IllegalArgumentException.class,()->other.validatePreparedObjectSolidPublication(ready));
                unchanged(other,oa,oe);
            }
            unchanged(f,a,e);
        }
    }
    @Test void preparationCannotAuthenticateAnEarlierPairWhenTheExpectedScalarStillMatches()throws Exception{
        try(var f=frame(actual().getFirst(),state(),environment())){
            var earlier=f.captureActor();
            f.applyPreparedObjectSolidPublication(f.prepareObjectBossScalar(Field.PLAYER_ANGLE,StoreForm.XOR,0x56,0x80));
            var current=f.captureActor();
            var e=f.captureShared();
            var ready=f.prepareObjectBossScalar(Field.RIGHT_BOUND,StoreForm.ASSIGN,0x2345,0xAC0);
            assertNotSame(earlier.state(),ready.expectedState());
            assertSame(current.state(),ready.expectedState());
            assertSame(e.environment(),ready.expectedEnvironment());
            unchanged(f,current,e);
        }
    }
    @Test void sameValueAndAbaPublicationsRemainReusableValueCasAndNotInvocationReceipts()throws Exception{
        try(var f=frame(actual().getFirst(),state(),environment())){
            var a=f.captureActor();
            var e=f.captureShared();
            for(var form:StoreForm.values()){
                var noOp=f.prepareObjectBossScalar(Field.PLAYER_ANGLE,form,0x56,form==StoreForm.ASSIGN?0x56:0);
                assertSame(a.state(),noOp.finalState());
                assertSame(e.environment(),noOp.finalEnvironment());
                f.applyPreparedObjectSolidPublication(noOp);
                f.applyPreparedObjectSolidPublication(noOp);
                unchanged(f,a,e);
            }
            var changed=f.prepareObjectBossScalar(Field.PLAYER_ANGLE,StoreForm.XOR,0x56,0x80);
            f.applyPreparedObjectSolidPublication(changed);
            assertThrows(IllegalArgumentException.class,()->f.applyPreparedObjectSolidPublication(changed));
            f.restoreActor(a);
            f.applyPreparedObjectSolidPublication(changed);
            assertEquals(0xD6,f.state().motion().angle());
        }
    }
    @Test void equalValuedDifferentFramesCanUseExistingCarrierSoWorldMustAuthenticateSameFrame()throws Exception{
        var source=actual().getFirst();
        var s=state();
        var e=environment();
        try(var first=frame(source,s,e);var second=frame(source,s,e)){
            var a=first.captureActor();
            var shared=first.captureShared();
            assertNotSame(a.owner(),second.captureActor().owner());
            var ready=first.prepareObjectBossScalar(Field.BOSS_FIGHT,StoreForm.ASSIGN,0x78,1);
            second.applyPreparedObjectSolidPublication(ready);
            assertSame(ready.finalEnvironment(),second.environment());
            unchanged(first,a,shared);
        }
    }
    @Test void nullPendingBusyAndClosedGuardsDoNotPrepareOrConsumeNativeWork()throws Exception{
        try(var f=frame(actual().getFirst(),state(),environment())){
            var a=f.captureActor();
            var e=f.captureShared();
            assertThrows(NullPointerException.class,()->f.prepareObjectBossScalar(null,StoreForm.ASSIGN,0,0));
            assertThrows(NullPointerException.class,()->f.prepareObjectBossScalar(Field.BOSS_FIGHT,null,0,0));
            unchanged(f,a,e);
            f.prepareInput(8);
            assertThrows(IllegalStateException.class,()->f.prepareObjectBossScalar(null,null,0,0));
            f.clearPendingInput();
            unchanged(f,a,e);
            f.serviceLevelLoad(8,()->{
                var s=f.state();var env=f.environment();assertThrows(IllegalStateException.class,()->f.prepareObjectBossScalar(null,null,0,0));assertSame(s,f.state());assertSame(env,f.environment());
            },()->{
            },()->{
            });
            var s=f.state();
            var env=f.environment();
            var art=f.artState();
            var clock=f.vIntClock();
            var display=f.display();
            f.close();
            assertThrows(IllegalStateException.class,()->f.prepareObjectBossScalar(null,null,0,0));
            assertSame(s,f.state());
            assertSame(env,f.environment());
            assertSame(art,f.artState());
            assertSame(clock,f.vIntClock());
            assertSame(display,f.display());
        }
    }
    private static boolean isVelocity(Field f){
        return f==Field.PLAYER_GROUND_SPEED||f==Field.PLAYER_X_SPEED||f==Field.PLAYER_Y_SPEED;
    }
    private static void assertOnlyScalar(CdPlayerBody.State s,CdPlayerBody.Environment e,CdPlayerBody.State next,CdPlayerBody.Environment ne,String path,int expected)throws Exception{
        var before=new TreeMap<String,Object>();
        var after=new TreeMap<String,Object>();
        flatten("state",s,before);
        flatten("environment",e,before);
        flatten("state",next,after);
        flatten("environment",ne,after);
        assertEquals(before.keySet(),after.keySet());
        assertEquals(expected,after.get(path));
        before.remove(path);
        after.remove(path);
        assertEquals(before,after,"every other public State8/Environment4 leaf, including fractions and rotation");
    }
    private static void flatten(String path,Object value,Map<String,Object> values)throws Exception{
        if(value!=null&&value.getClass().isRecord()){
            for(var c:value.getClass().getRecordComponents())flatten(path+"."+c.getName(),c.getAccessor().invoke(value),values);
        }
        else values.put(path,value);
    }
    private static void preservedReferences(CdPlayerBody.State s,CdPlayerBody.Environment e,CdPlayerBody.State n,CdPlayerBody.Environment ne,Field field){
        assertSame(s.animation(),n.animation());
        assertSame(s.header(),n.header());
        assertSame(s.timers(),n.timers());
        assertSame(s.history(),n.history());
        assertSame(s.objectLinks(),n.objectLinks());
        assertSame(e.controls(),ne.controls());
        assertSame(e.globals(),ne.globals());
        assertSame(e.dynamicSlots(),ne.dynamicSlots());
        assertSame(e.movement().physics(),ne.movement().physics());
        assertSame(e.movement().gates(),ne.movement().gates());
        if(field.name().startsWith("PLAYER_"))assertSame(e,ne);
        else assertSame(s,n);
    }
    private static void unchanged(CdPlayerFrameRuntime f,CdPlayerFrameRuntime.ActorState a,CdPlayerFrameRuntime.SharedState e){
        assertEquals(a,f.captureActor());
        assertEquals(e,f.captureShared());
        assertSame(a.state(),f.state());
        assertSame(e.environment(),f.environment());
        assertSame(a.display(),f.display());
        assertSame(e.artState(),f.artState());
        assertSame(e.vIntClock(),f.vIntClock());
        assertFalse(f.closed());
    }
    private static CdPlayerFrameRuntime frame(CdR13FutureAssets a,CdPlayerBody.State state,CdPlayerBody.Environment environment){
        byte[] cpu=new byte[768],resident=new byte[768];
        for(int n=0;n<768;n++){
            cpu[n]=(byte)(3+n*7);
            resident[n]=(byte)(5+n*11);
        }
        var f=CdPlayerFrameRuntime.fromAct3FutureEntry(a,state,environment,new CdPlayerArtRuntime.State(cpu,resident,7,true),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFEDCBA98L),0x1234));
        f.bindArtPublication((request,value)->fail("Scalar preparation never services art"));
        return f;
    }
    private static CdPlayerBody.State state(){
        byte[] history=new byte[256];
        for(int n=0;n<256;n++)history[n]=(byte)(7+n*13);
        return new CdPlayerBody.State(new CdPlayerMotionState(0x01234567,0x023489AB,-0x123,0x234,-0x345,4,0x56,0x42,0x23,7,11,6,7,9,0,-0x1234,0x44,0x10,0x77,0x88),new CdPlayerBody.AnimationCursor(5,6,3),new CdPlayerBody.Header(1,0x12345678,0x2345,3,0x21),new CdPlayerBody.Timers(0x78,0x1234,0x2345,0x3456),new CdPlayerBody.History(CdAssetBytes.copyOf(history),0x42),0x67,new CdPlayerBody.ObjectLinks(0x20,0x7F),0x89);
    }
    private static CdPlayerBody.Environment environment(){
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123,0x23,0x34),new CdPlayerMovement.Bounds(0x12,0x2345,0x3456,0x78),new CdPlayerMovement.Gates(2,2,0x12,0x34,0x56,0x78,0xFF),new CdPlayerMovement.Globals(0x12,0x2345,0x34,0x56,0xFFFF,0x4567,0x78,0x89,0x9A,0xAB)),new CdPlayerBody.Controls(0x24,0x18,8,0x10,0x81),new CdPlayerBody.Globals(0x1234,0x2345,0x12,0x34,0x4567,0xFFFE,0x56,0x78),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
    private static CdPlayerBody.State changedState(CdPlayerBody.State s,int part){
        var h=s.history().bytes().copy();
        h[0]^=1;
        var m=s.motion();
        var motion=part==0?new CdPlayerMotionState(m.centreX16_16()^1,m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle()):m;
        return new CdPlayerBody.State(motion,part==1?new CdPlayerBody.AnimationCursor(4,6,3):s.animation(),part==2?new CdPlayerBody.Header(1,0x12345678,0x2344,3,0x21):s.header(),part==3?new CdPlayerBody.Timers(0x79,0x1234,0x2345,0x3456):s.timers(),part==4?new CdPlayerBody.History(CdAssetBytes.copyOf(h),0x42):s.history(),part==5?s.rotationDistance()^1:s.rotationDistance(),part==6?new CdPlayerBody.ObjectLinks(0x21,0x7F):s.objectLinks(),part==7?s.rotationAngle()^1:s.rotationAngle());
    }
    private static CdPlayerBody.Environment changedEnvironment(CdPlayerBody.Environment e,int part){
        var m=e.movement();
        var g=e.globals();
        return new CdPlayerBody.Environment(part==0?new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x124,0x23,0x34),m.bounds(),m.gates(),m.globals()):m,part==1?new CdPlayerBody.Controls(e.controls().rawHeld()^1,e.controls().rawTap(),e.controls().logicalHeld(),e.controls().logicalTap(),e.controls().controlLocked()):e.controls(),part==2?new CdPlayerBody.Globals(g.levelFrames()^1,g.cameraXCentre(),g.timeAttackMode(),g.debugMode(),g.debugCheat(),g.timeWarpTimer(),g.invincible(),g.bossMusic()):g,part==3?CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96:e.dynamicSlots());
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
