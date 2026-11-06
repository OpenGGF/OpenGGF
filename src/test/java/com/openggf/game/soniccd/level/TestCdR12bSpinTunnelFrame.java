package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original-USA R12B Frame/source-product tests with explicitly constructed component
 * state/art/clock/bank ingress. Not a World, natural route, current-art or rendering claim.
 * Native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Spin Tunnel/Main (Tag).asm:26-32,37-161.
 * Fixture shape follows immutable Jd032c114807f3ee30752bdb9a8f05f745fbea652
 * TestCdR12bPlayerFrameEntry/state/environment/art and TestCdPlayerFrameRuntime's literal Spin
 * first-roll vector. No inherited test or validator is edited. Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@Isolated
class TestCdR12bSpinTunnelFrame {
    static CdR12bTerrainAssets terrain;static CdR12bPlayerAssets player;
    @BeforeAll static void configuredOriginalUsa()throws Exception{
        String root=System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Set soniccd.media.root to original USA media");
        Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(f->f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        // A configured invalid source must fail, never turn into a media skip.
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){terrain=CdR12bTerrainAssets.fromPrograms(source);player=CdR12bPlayerAssets.fromPrograms(source);}
    }
    record Product(CdObjectPlacementRuntime placement,CdSpinTunnelTag body,CdSpinTunnelTag.Result result){}

    @Test void preparationHasExactlyOnePublicThreeArgumentSignatureAndReusesThePrivateCarrier()throws Exception{
        var method=CdPlayerFrameRuntime.class.getDeclaredMethod("prepareObjectSpinTunnelResult",CdPlayerBody.State.class,CdPlayerBody.Environment.class,CdSpinTunnelTag.PlayerResult.class);
        assertTrue(Modifier.isPublic(method.getModifiers()));assertFalse(Modifier.isStatic(method.getModifiers()));assertEquals(CdPlayerFrameRuntime.ObjectSolidPublication.class,method.getReturnType());
        assertEquals(1,Arrays.stream(CdPlayerFrameRuntime.class.getDeclaredMethods()).filter(m->m.getName().equals("prepareObjectSpinTunnelResult")).count());
        assertTrue(Arrays.stream(CdPlayerFrameRuntime.ObjectSolidPublication.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
    }
    @Test void actualSourceProductPrebuildsFirstRollWithoutMutationAndAdoptsOnlyPreparedReferences(){
        try(var f=frame(state(),environment(0x0001,8))){var actor=f.captureActor();var shared=f.captureShared();var product=product(f,0);var before=product.placement.capture();var result=product.result.player();
            var expected=new CdPlayerMotionState(0xFFFC1234,0x0002BEEF,0xA00,-0x234,-0xA00,4,0x60,0xED,0xD5,7,14,2,0x35,0x76,0x11,-0x1234,0x85,0xA5,0x44,0x88);
            assertEquals(expected,result.finalMotion());assertEquals(Set.of(CdSpinTunnelTag.PlayerField.CENTRE_Y_WORD,CdSpinTunnelTag.PlayerField.X_VELOCITY,CdSpinTunnelTag.PlayerField.GROUND_VELOCITY,CdSpinTunnelTag.PlayerField.FLAGS,CdSpinTunnelTag.PlayerField.Y_RADIUS,CdSpinTunnelTag.PlayerField.X_RADIUS,CdSpinTunnelTag.PlayerField.ANIMATION),result.executedWrites());
            var ready=f.prepareObjectSpinTunnelResult(actor.state(),shared.environment(),result);assertSame(actor.state(),ready.expectedState());assertSame(shared.environment(),ready.expectedEnvironment());assertSame(shared.environment(),ready.finalEnvironment());assertSame(result.finalMotion(),ready.finalState().motion());
            assertEquals(new CdPlayerBody.State(expected,actor.state().animation(),actor.state().header(),actor.state().timers(),actor.state().history(),actor.state().rotationDistance(),actor.state().objectLinks(),actor.state().rotationAngle()),ready.finalState());
            assertUnchanged(f,actor,shared);f.validatePreparedObjectSolidPublication(ready);assertUnchanged(f,actor,shared);f.applyPreparedObjectSolidPublication(ready);
            assertSame(ready.finalState(),f.state());assertSame(ready.finalEnvironment(),f.environment());assertPreserved(actor.state(),f.state());assertSame(actor.display(),f.display());assertEquals(shared,f.captureShared());
            assertEquals(before.pool().slots(),product.placement.pool().capture().slots());assertArrayEquals(before.savedFlags().copy(),product.placement.capture().savedFlags().copy());assertSame(actor.state(),ready.expectedState());assertFalse(f.closed());
        }
    }
    @Test void pureNoWriteProductPreparesTheSameOldStateFormulaAndLeavesAllOwnersUntouched(){
        try(var f=frame(state(),environment(0x0100,8))){var actor=f.captureActor();var shared=f.captureShared();var result=product(f,1).result.player();assertTrue(result.executedWrites().isEmpty());assertSame(actor.state().motion(),result.finalMotion());
            var ready=f.prepareObjectSpinTunnelResult(actor.state(),shared.environment(),result);assertEquals(actor.state(),ready.finalState());assertNotSame(actor.state(),ready.finalState(),"Keep the old apply's State8 reconstruction even for a skip");assertUnchanged(f,actor,shared);
            f.applyPreparedObjectSolidPublication(ready);assertSame(ready.finalState(),f.state());assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());
        }
    }
    @Test void preparationRejectsEveryStaleNonMotionStateComponentBeforeAnyWrite(){
        try(var f=frame(state(),environment(1,8))){var actor=f.captureActor();var shared=f.captureShared();var result=product(f,0).result.player();
            for(int component=0;component<7;component++){var stale=changedState(actor.state(),component);assertSame(actor.state().motion(),stale.motion());assertNotEquals(actor.state(),stale);
                assertThrows(IllegalArgumentException.class,()->f.prepareObjectSpinTunnelResult(stale,shared.environment(),result));assertUnchanged(f,actor,shared);}
        }
    }
    @Test void preparationRejectsStaleCompleteEnvironmentEvenWhenExpectedMotionStillMatches(){
        try(var f=frame(state(),environment(1,8))){var actor=f.captureActor();var shared=f.captureShared();var result=product(f,0).result.player();
            for(int component=0;component<4;component++){var stale=changedEnvironment(shared.environment(),component);assertNotEquals(shared.environment(),stale);
                assertThrows(IllegalArgumentException.class,()->f.prepareObjectSpinTunnelResult(actor.state(),stale,result));assertUnchanged(f,actor,shared);}
        }
    }
    @Test void adoptionRechecksNonMotionStateAndEnvironmentThenAcceptsTheRestoredExactPair(){
        try(var f=frame(state(),environment(1,8))){var actor=f.captureActor();var shared=f.captureShared();var ready=f.prepareObjectSpinTunnelResult(actor.state(),shared.environment(),product(f,0).result.player());
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(),changedState(actor.state(),3),actor.display()));var altered=f.captureActor();
            assertThrows(IllegalArgumentException.class,()->f.validatePreparedObjectSolidPublication(ready));assertThrows(IllegalArgumentException.class,()->f.applyPreparedObjectSolidPublication(ready));assertUnchanged(f,altered,shared);
            f.restoreActor(actor);f.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(),changedEnvironment(shared.environment(),0),shared.artState(),shared.paused(),shared.vIntClock()));var alteredShared=f.captureShared();
            assertThrows(IllegalArgumentException.class,()->f.validatePreparedObjectSolidPublication(ready));assertThrows(IllegalArgumentException.class,()->f.applyPreparedObjectSolidPublication(ready));assertUnchanged(f,actor,alteredShared);
            f.restoreShared(shared);f.applyPreparedObjectSolidPublication(ready);assertSame(ready.finalState(),f.state());assertSame(ready.finalEnvironment(),f.environment());assertEquals(shared,f.captureShared());
        }
    }
    @Test void legacyApplyProducesTheIdenticalOldStateEightFormulaAndRetainsAllNonwrites(){
        try(var f=frame(state(),environment(1,8))){var actor=f.captureActor();var shared=f.captureShared();var result=product(f,0).result.player();var ready=f.prepareObjectSpinTunnelResult(actor.state(),shared.environment(),result);
            f.applyObjectSpinTunnelResult(result);assertEquals(ready.finalState(),f.state());assertSame(result.finalMotion(),f.state().motion());assertSame(shared.environment(),f.environment());assertPreserved(actor.state(),f.state());assertSame(actor.display(),f.display());assertEquals(shared,f.captureShared());
            var after=f.captureActor();assertThrows(IllegalArgumentException.class,()->f.applyObjectSpinTunnelResult(result));assertUnchanged(f,after,shared);
        }
    }
    @Test void unchangedValidatorRejectsUndeclaredSpeedAndUnownedFractionOrFlagBits(){
        try(var f=frame(state(),environment(1,8))){var actor=f.captureActor();var shared=f.captureShared();var m=actor.state().motion();
            var values=List.of(new CdSpinTunnelTag.PlayerResult(m,motion(m,m.centreY16_16(),m.xVelocity()+1,m.flags()),Set.of()),
                    new CdSpinTunnelTag.PlayerResult(m,motion(m,m.centreY16_16()^1,m.xVelocity(),m.flags()),EnumSet.allOf(CdSpinTunnelTag.PlayerField.class)),
                    new CdSpinTunnelTag.PlayerResult(m,motion(m,m.centreY16_16(),m.xVelocity(),m.flags()^1),EnumSet.allOf(CdSpinTunnelTag.PlayerField.class)));
            // Constructed malformed DTOs exercise the value barrier; none represents a body invocation.
            for(var value:values){assertThrows(IllegalArgumentException.class,()->f.prepareObjectSpinTunnelResult(actor.state(),shared.environment(),value));assertThrows(IllegalArgumentException.class,()->f.applyObjectSpinTunnelResult(value));assertUnchanged(f,actor,shared);}
        }
    }
    @Test void nullPendingBusyAndClosedCutsRejectWithoutConsumptionOrOwnerWrites(){
        try(var f=frame(state(),environment(1,8))){var actor=f.captureActor();var shared=f.captureShared();var value=product(f,0).result.player();
            assertThrows(NullPointerException.class,()->f.prepareObjectSpinTunnelResult(null,shared.environment(),value));assertThrows(NullPointerException.class,()->f.prepareObjectSpinTunnelResult(actor.state(),null,value));assertThrows(NullPointerException.class,()->f.prepareObjectSpinTunnelResult(actor.state(),shared.environment(),null));assertUnchanged(f,actor,shared);
            f.prepareInput(8);assertThrows(IllegalStateException.class,()->f.prepareObjectSpinTunnelResult(null,null,null));assertThrows(IllegalStateException.class,()->f.applyObjectSpinTunnelResult(value));f.clearPendingInput();assertUnchanged(f,actor,shared);
            f.serviceLevelLoad(8,()->{assertThrows(IllegalStateException.class,()->f.prepareObjectSpinTunnelResult(null,null,null));assertThrows(IllegalStateException.class,()->f.applyObjectSpinTunnelResult(null));},()->{},()->{});
            var s=f.state();var e=f.environment();var display=f.display();var art=f.artState();var clock=f.vIntClock();f.close();assertThrows(IllegalStateException.class,()->f.prepareObjectSpinTunnelResult(null,null,null));assertThrows(IllegalStateException.class,()->f.applyObjectSpinTunnelResult(null));assertSame(s,f.state());assertSame(e,f.environment());assertSame(display,f.display());assertSame(art,f.artState());assertSame(clock,f.vIntClock());
        }
    }
    @Test void equalValuedCarrierRemainsAValuePortAndDoesNotMutateItsPreparingFrame(){
        var state=state();var environment=environment(1,8);try(var first=frame(state,environment);var second=frame(state,environment)){var actor=first.captureActor();var shared=first.captureShared();var ready=first.prepareObjectSpinTunnelResult(state,environment,product(first,0).result.player());
            assertNotSame(first.captureActor().owner(),second.captureActor().owner());second.applyPreparedObjectSolidPublication(ready);assertSame(ready.finalState(),second.state());assertUnchanged(first,actor,shared);
            // The carrier owns no phase, source, pool or native invocation. World supplies private origin.
        }
    }
    static CdPlayerFrameRuntime frame(CdPlayerBody.State state,CdPlayerBody.Environment environment){
        byte[] cpu=new byte[0x300],resident=new byte[0x300];for(int n=0;n<cpu.length;n++){cpu[n]=(byte)(n*7+3);resident[n]=(byte)(n*11+5);}
        var f=CdPlayerFrameRuntime.fromAct2PastEntry(terrain,player,state,environment,new CdPlayerArtRuntime.State(cpu,resident,7,true),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFEDCBA98L),0x1234));
        assertTrue(f.stageSource().ownsAct2Past(terrain,player));f.bindArtPublication((request,value)->fail("Spin value preparation/adoption must not service art"));return f;
    }
    static Product product(CdPlayerFrameRuntime f,int subtype){var placement=CdObjectPlacementRuntime.fromAssets(terrain,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));var m=f.state().motion();
        var source=placement.pool().state(32).moveByte(ID,0x34).moveByte(SUBTYPE,subtype).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16);placement.pool().write(source);
        var body=CdSpinTunnelTag.fromAct2PastAssets(placement,terrain);var e=f.environment();var result=body.step(source,m,new CdSpinTunnelTag.Gates(e.movement().gates().zoneAct()>>>8,e.globals().debugMode(),e.globals().debugCheat(),e.controls().rawHeld()));return new Product(placement,body,result);
    }
    static CdPlayerBody.State state(){byte[] history=new byte[0x100];for(int n=0;n<history.length;n++)history[n]=(byte)(0x33+17*n);
        return new CdPlayerBody.State(new CdPlayerMotionState(0xFFFC1234,0xFFFDBEEF,0x100,-0x234,0x345,4,0x60,0xE9,0xD5,9,19,0x1A,0x35,0x76,0x11,-0x1234,0x85,0xA5,0x44,0x88),new CdPlayerBody.AnimationCursor(5,7,0x23),new CdPlayerBody.Header(1,0x230F60,0x6780,5,0x27),new CdPlayerBody.Timers(0x1357,0x2468,0x369A,0x48BC),new CdPlayerBody.History(CdAssetBytes.copyOf(history),0xBC),0xAB,new CdPlayerBody.ObjectLinks(0x7B,0x9D),0xD7);}
    static CdPlayerBody.Environment environment(int debug,int raw){return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123,0x23,0x34),new CdPlayerMovement.Bounds(0x12,0x2345,0x3456,0x78),new CdPlayerMovement.Gates(1,0,0x12,0x34,0x56,0x78,0xFF),new CdPlayerMovement.Globals(0xA5,0x3456,0x43,0x87,0x2345,0x7654,0x91,0x92,0x93,0x94)),new CdPlayerBody.Controls(raw,0x24,8,0x10,0x81),new CdPlayerBody.Globals(0x3456,0x4567,1,debug,0x0123,0x5678,1,1),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);}
    static CdPlayerMotionState motion(CdPlayerMotionState m,int y,int xv,int flags){return new CdPlayerMotionState(m.centreX16_16(),y,xv,m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),flags,m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());}
    static CdPlayerBody.State changedState(CdPlayerBody.State s,int part){var history=s.history().bytes().copy();history[0]^=1;return new CdPlayerBody.State(s.motion(),part==0?new CdPlayerBody.AnimationCursor(6,7,0x23):s.animation(),part==1?new CdPlayerBody.Header(1,0x230F60,0x6781,5,0x27):s.header(),part==2?new CdPlayerBody.Timers(0x1358,0x2468,0x369A,0x48BC):s.timers(),part==3?new CdPlayerBody.History(CdAssetBytes.copyOf(history),0xBC):s.history(),part==4?s.rotationDistance()^1:s.rotationDistance(),part==5?new CdPlayerBody.ObjectLinks(0x7A,0x9D):s.objectLinks(),part==6?s.rotationAngle()^1:s.rotationAngle());}
    static CdPlayerBody.Environment changedEnvironment(CdPlayerBody.Environment e,int part){var m=e.movement();var g=e.globals();return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(part==1?new CdPlayerMovement.Physics(0x124,0x23,0x34):m.physics(),m.bounds(),part==2?new CdPlayerMovement.Gates(1,0,0x13,0x34,0x56,0x78,0xFF):m.gates(),m.globals()),part==0?new CdPlayerBody.Controls(e.controls().rawHeld()^1,0x24,8,0x10,0x81):e.controls(),part==3?new CdPlayerBody.Globals(g.levelFrames()+1,g.cameraXCentre(),g.timeAttackMode(),g.debugMode(),g.debugCheat(),g.timeWarpTimer(),g.invincible(),g.bossMusic()):g,e.dynamicSlots());}
    static void assertUnchanged(CdPlayerFrameRuntime f,CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared){assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());assertSame(actor.state(),f.state());assertSame(shared.environment(),f.environment());assertSame(actor.display(),f.display());assertSame(shared.artState(),f.artState());assertSame(shared.vIntClock(),f.vIntClock());assertFalse(f.closed());}
    static void assertPreserved(CdPlayerBody.State before,CdPlayerBody.State after){assertSame(before.animation(),after.animation());assertSame(before.header(),after.header());assertSame(before.timers(),after.timers());assertSame(before.history(),after.history());assertSame(before.objectLinks(),after.objectLinks());assertEquals(before.rotationDistance(),after.rotationDistance());assertEquals(before.rotationAngle(),after.rotationAngle());}
}
