package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameMode;
import com.openggf.game.session.SessionManager;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.graphics.SpritePresentation;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.difficulty.MarbleGardenBossRecipe;
import com.openggf.game.sonic3k.objects.MgzDrillingRobotnikInstance;
import com.openggf.game.sonic3k.objects.MgzEndBossInstance;
import com.openggf.game.sonic3k.objects.MgzEndBossKnuxInstance;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Positioned approach, ordinary native post touch, declared full reload, then real A2 input.
 * Exact artifact-backed creator witness; no physical death, cold/full route or other-character certificate.
 * Opt in only with the immutable 53676 engine/SDK/mod paths and hashes, the original ROM,
 * artifact-only production CodeSources and a fresh absolute OPENGGF_M4_CAPTURE_ROOT.
 * Default smoke skips this historical witness; enabling it does not qualify current builds.
 * Origin: Second Strike M4, 2026-10-16; execution 76734 used body blob 74c72d8a.
 */
@EnabledIfSystemProperty(named = "secondstrike.mgz.packaged.arena", matches = "true")
class TestWestMgzBossNativeArena {
    @Test @Timeout(120)
    void packagedCreatorKnucklesHasAnOrdinaryDamageFreeSideResponse() throws Exception {
        Path out=Path.of(System.getenv().getOrDefault("OPENGGF_M4_CAPTURE_ROOT","target/m4-placed-a2"));
        Files.createDirectories(out);
        // Declared sole position seed: one native tile left of original post333.
        // Never change camera, player physics/health, boss phase or preset fields.
        verifyArtifactAuthority(out);
        var settings=new GameplayCaptureSession.Settings(320,"knuckles","","off",null,0x3BD0,0x80,
                null,false,false,null,null,false,0,false);
        var evidence=new Evidence();
        try(var session=new GameplayCaptureSession(settings);
            var state=Files.newBufferedWriter(out.resolve("state.csv"));
            var authority=Files.newBufferedWriter(out.resolve("authority.csv"));
            var visibility=Files.newBufferedWriter(out.resolve("visibility.csv"));
            var touches=Files.newBufferedWriter(out.resolve("touches.csv"));
            var ability=Files.newBufferedWriter(out.resolve("ability.csv"))) {
            session.applyMod(Path.of(System.getProperty("secondstrike.mod.path")));
            session.boot(Path.of(System.getProperty("s3k.rom.path")),2,1,settings);
            assertCreatorInventory();
            state.write(GameplayCaptureSession.stateHeader());state.newLine();
            authority.write("frame,phase,checkpoint,committed_roots,root_count,root_state");authority.newLine();
            evidence.visibility=visibility;evidence.touches=touches;evidence.ability=ability;
            ability.write("frame,p1mask,recovery_stage,"+abilityHeader("before_")+","+abilityHeader("after_")
                    +",floor_left_distance,floor_left_tile,floor_right_distance,floor_right_tile,touch_debug_enabled,touch_debug_hits");ability.newLine();
            touches.write("frame,p1mask,before_player_x,before_player_y,before_rings,before_hurt,before_invulnerable,before_animation,before_rolling,before_boss_x,before_boss_y,before_boss_hp,before_boss_invulnerable,before_regions,after_player_x,after_player_y,after_rings,after_hurt,after_invulnerable,after_alive,after_boss_hp,after_boss_invulnerable,after_regions");touches.newLine();
            visibility.write("frame,p1mask,"+NativeVisibility.header("live_")+","+NativeVisibility.header("published_")
                    +",published_body_tiles,boss_x,boss_y,boss_routine,boss_timer,boss_xvel,boss_yvel,boss_flags,boss_hp,boss_invulnerable");visibility.newLine();
            try {
                var post=GameServices.level().getCurrentLevel().getObjects().stream()
                        .filter(o->o.layoutIndex()==333).findFirst().orElseThrow();
                assertEquals(0x34,post.objectId());assertEquals(0x08,post.subtype());
                assertEquals(0x3BE0,post.x());assertEquals(0x80,post.y());
                assertEquals(0,session.player().getRingCount());
                int x=session.player().getCentreX(),y=session.player().getCentreY();
                var left=ObjectTerrainUtils.checkFloorDist(x-session.player().getXRadius(),y,session.player().getYRadius());
                var right=ObjectTerrainUtils.checkFloorDist(x+session.player().getXRadius(),y,session.player().getYRadius());
                Files.writeString(out.resolve("entry-authority.txt"),"positionedSeed=3BD0,0080, one tile before original "+post
                        +"; zero rings; left="+left+"; right="+right+"\n"
                        +"Ordinary post touch must bank index8/3BE0,0080. Public respawnPlayer requests FULL_DEATH_RELOAD; physical death is NOT claimed.\n"
                        +"Native Get_LevelSizeStart camera recompute is X-A0/Y-60, not saved camera retention. No --star-post shortcut.\n");
                assertTrue((left.foundSurface()||right.foundSurface())&&Math.min(left.distance(),right.distance())>=-2
                        &&Math.min(left.distance(),right.distance())<=32,"declared pre-post seed must have native floor support");
                var checkpoint=(CheckpointState)GameServices.level().getCheckpointState();
                for(int n=0;n<90&&!checkpoint.isActive();n++) {
                    step(session,evidence,AbstractPlayableSprite.INPUT_RIGHT,state,authority,"earn-post");
                    assertFalse(session.player().getDead(),"ordinary checkpoint approach died");
                }
                assertEquals(8,checkpoint.getLastCheckpointIndex(),"the actual post must save subtype8 through touch");
                assertEquals(post.x(),checkpoint.getSavedX());assertEquals(post.y(),checkpoint.getSavedY());
                assertEquals(0,evidence.committedRoots,"pre-arena approach must not commit a recipe");
                capture(session,evidence,out,"earned-post",true,null);
                Files.writeString(out.resolve("checkpoint-bank.txt"),"index="+checkpoint.getLastCheckpointIndex()
                        +" saved="+checkpoint.getSavedX()+","+checkpoint.getSavedY()+" savedCamera="+checkpoint.getSavedCameraX()
                        +","+checkpoint.getSavedCameraY()+" observedFrame="+evidence.frame+"\n");
                // Declared load request uses the real full death-reload path, without
                // forcing dead/velocity/camera/checkpoint/boss fields or claiming a death.
                GameServices.level().respawnPlayer();
                assertSame(session.player(),GameServices.camera().getFocusedSprite(),"capture must keep the actual reloaded native playable");
                assertEquals(post.x(),session.player().getCentreX()&65535);assertEquals(post.y(),session.player().getCentreY()&65535);
                assertEquals(0,session.player().getRingCount());assertFalse(session.player().getAir());
                assertEquals(0x3B40,GameServices.camera().getX()&65535);assertEquals(0x20,GameServices.camera().getY()&65535);
                assertEquals(8,GameServices.level().getCheckpointState().getLastCheckpointIndex());
                assertEquals(0,evidence.committedRoots);
                assertCreatorInventory();
                evidence.reload=true;
                awaitPublishedOrdinaryReload(session,evidence,state,authority,out);
                capture(session,evidence,out,"full-reload",true,null);
                var originalA2=GameServices.level().getCurrentLevel().getObjects().stream()
                        .filter(o->o.layoutIndex()==341).findFirst().orElseThrow();
                assertEquals(0x3E78,originalA2.x());assertEquals(0,originalA2.y());
                assertEquals(0xA2,originalA2.objectId());assertEquals(0,originalA2.subtype());
                MgzEndBossKnuxInstance observedRoot=null;int observedSlot=-1;
                boolean takeoff=false,glide=false,punishHolding=false,settled=false;
                int released=-1,lastRoutine=-1,punishDirection=0;
                for(int n=0;n<1800;n++) {
                    var boss=roots().stream().filter(MgzEndBossKnuxInstance.class::isInstance)
                            .map(MgzEndBossKnuxInstance.class::cast).findFirst().orElse(null);
                    int phase=boss==null?0:(int)read(boss,"admissionPhase");
                    int mask=0;boolean jump=false;
                    // Continue the legitimate approach until the native camera gate
                    // reaches its destination; stopping at initial admission would stall it.
                    if(phase<2 || (GameServices.camera().getX()&65535)<0x3D78) {
                        mask=AbstractPlayableSprite.INPUT_RIGHT;
                        if(!takeoff&&!session.player().getAir()&&(session.player().getCentreX()&65535)>=post.x()+0x10)takeoff=true;
                        if(takeoff&&!glide){
                            if(session.player().getAir()&&session.player().getYSpeed()>=-0x200){
                                if(released<0)released=evidence.frame;else if(evidence.frame>released){jump=true;glide=true;}
                            }else jump=true;
                        }else if(glide)jump=true;
                    }else if(boss!=null) {
                        int routine=boss.getNativeRoutineForTesting();
                        if(!settled){
                            assertTrue(routine<8,"must settle on the observed supported floor before the first drop");
                            assertFalse(session.player().getAir(),"ordinary floor positioning must remain grounded");
                            int distance=session.player().getCentreX()-0x3E38;
                            int velocity=session.player().getXSpeed();
                            if(Math.abs(distance)<=8&&Math.abs(velocity)<=session.player().getRunAccel()){
                                assertSupportedFloor(session.player());settled=true;
                            }else{
                                int direction=distance>0?-1:1;
                                int stopping=nativeStoppingPixels(velocity,session.player().getRunDecel());
                                if(Math.abs(distance)<=8||Integer.signum(velocity)==direction&&stopping>=Math.max(0,Math.abs(distance)-8))
                                    direction=-Integer.signum(velocity);
                                mask=direction<0?AbstractPlayableSprite.INPUT_LEFT:direction>0?AbstractPlayableSprite.INPUT_RIGHT:0;
                            }
                        }else{
                            assertSupportedFloorUnlessAirborne(session.player());
                            if(evidence.firstHit>=0&&!evidence.recoveryComplete){
                                int flag=session.player().getDoubleJumpFlag();
                                if(evidence.recoveryStage==0){
                                    assertTrue(session.player().isJumping(),"native rebound must retain the natural jumping latch");
                                    assertEquals(0,flag,"no pre-existing glide ability before recovery release");
                                    mask=AbstractPlayableSprite.INPUT_LEFT;evidence.recoveryStage=1;
                                }else if(evidence.recoveryStage==1){
                                    assertTrue(session.player().isJumping(),"fresh glide press requires the natural jumping latch");
                                    assertEquals(0,flag,"fresh glide press must use an unused native ability");
                                    mask=AbstractPlayableSprite.INPUT_LEFT;jump=true;evidence.recoveryStage=2;
                                }else{
                                    assertTrue(flag==1||flag==3||flag==0&&!session.player().getAir(),"missing natural glide/slide recovery");
                                    if(flag==1)evidence.glideObserved=true;
                                    if(flag==3){evidence.slideObserved=true;assertSupportedFloor(session.player());}
                                    if(flag==0&&!session.player().getAir()){
                                        assertTrue(evidence.glideObserved,"native glide must actually activate");
                                        assertSupportedFloor(session.player());evidence.recoveryComplete=true;evidence.recoveryStage=3;
                                    }else{mask=AbstractPlayableSprite.INPUT_LEFT;jump=true;}
                                }
                            }else if(!evidence.recoveryComplete){
                                if(punishHolding&&(!session.player().getAir()||session.player().getYSpeed()>=0))punishHolding=false;
                                if(punishHolding){mask=punishDirection;jump=true;}
                                else if(evidence.attempts==0&&routine==12&&!session.player().getAir()
                                        &&boss.getNativeXVelocityForTesting()<0&&boss.getNativeYVelocityForTesting()<0
                                        &&boss.getCollisionFlags()==0x0F){
                                    int dx=boss.getX()-session.player().getCentreX();
                                    // ROM body half-width24 + native player half-width8,
                                    // plus one full player touch width16 of jump clearance.
                                    if(dx>32&&dx<=48){
                                        mask=AbstractPlayableSprite.INPUT_RIGHT;jump=true;
                                        punishHolding=true;punishDirection=mask;evidence.attempts++;
                                    }
                                }
                            }
                        }
                    }
                    if(jump)mask|=AbstractPlayableSprite.INPUT_JUMP;
                    step(session,evidence,mask,state,authority,"arena");
                    var live=roots();assertTrue(live.size()<=1,"duplicate layout/event root: "+describe(live));
                    var events=((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getMgzEvents();
                    assertFalse(events.isBossSpawned(),"upper Knuckles approach must not create an event-owned root");
                    boss=live.stream().filter(MgzEndBossKnuxInstance.class::isInstance).map(MgzEndBossKnuxInstance.class::cast).findFirst().orElse(null);
                    if(boss!=null){
                        phase=(int)read(boss,"admissionPhase");evidence.peak=Math.max(evidence.peak,live.size());
                        if(observedRoot==null){
                            assertEquals(originalA2,boss.getSpawn(),"all native fields of original A2 at layout byte7FE");
                            assertEquals(0,phase,"first native layout observation must precede admission");
                            observedRoot=boss;observedSlot=boss.getSlotIndex();
                            assertTrue(observedSlot>=0,"placed root must hold a native slot");
                        }else{
                            // AbstractBossInstance publishes a positioned seven-field
                            // spawn after motion; placement metadata is not live identity.
                            assertSame(observedRoot,boss,"native placed actor must remain the same root");
                            assertEquals(observedSlot,boss.getSlotIndex(),"native placed root must retain its slot");
                        }
                        if(phase==0){evidence.dormant++;assertEquals(0,boss.getCollisionFlags());}
                        if(phase>0)assertEquals(true,read(boss,"counterCadence"),"native admission must commit the offered immutable preset");
                        if(phase>0&&evidence.admitted<0){evidence.admitted=evidence.frame;capture(session,evidence,out,"admission",true,null);}
                        int routine=boss.getNativeRoutineForTesting();
                        if(phase==2&&!boss.isHidden())evidence.fight++;
                        if(routine==8)evidence.firstDrop++;if(routine==12)evidence.secondDrop++;
                        evidence.hp=Math.min(evidence.hp,boss.getState().hitCount);
                        if(routine!=lastRoutine||evidence.frame%120==0||session.player().getDead()){
                            boolean visible=phase==2&&!boss.isHidden()&&boss.getY()-GameServices.camera().getY()>=12
                                    &&boss.getY()-GameServices.camera().getY()<200
                                    &&boss.getX()-GameServices.camera().getX()>=24&&boss.getX()-GameServices.camera().getX()<296;
                            capture(session,evidence,out,"frame-"+evidence.frame,true,visible?boss:null);
                            if(visible)evidence.cuePixels=true;
                        }
                        lastRoutine=routine;
                    }
                    if(session.player().getDead()){capture(session,evidence,out,"death-"+evidence.frame,false,null);break;}
                    if(evidence.hp<8&&evidence.firstDrop>=136&&evidence.secondDrop>=160&&evidence.recoveryComplete)break;
                }
                assertTrue(evidence.reload,"normal saved checkpoint load was not reached");
                assertTrue(evidence.admitted>=0,"actual placed-A2 native admission was not reached");
                assertEquals(1,evidence.peak);assertEquals(1,evidence.committedRoots);
                assertFalse(session.player().getDead(),"ordinary damage-free response died: "+evidence);
                assertTrue(evidence.cuePixels,"native boss movement/presentation cue must have real visible pixels");
                assertTrue(evidence.firstDrop>=136&&evidence.secondDrop>=160,"must observe whole native paired drops: "+evidence);
                assertTrue(evidence.hp<8,"an ordinary side/back punish opportunity must cause a real native hit: "+evidence);
                assertTrue(evidence.recoveryComplete,"ordinary glide recovery must finish on supported native floor: "+evidence);
            } finally {
                Files.writeString(out.resolve("outcome.txt"),evidence+" committedRoots="+evidence.committedRoots+" player="
                        +session.player().getCentreX()+","+session.player().getCentreY()+" dead="+session.player().getDead()
                        +" camera="+GameServices.camera().getX()+","+GameServices.camera().getY()+" roots="+describe(roots())+"\n");
            }
        }
    }
    private static void verifyArtifactAuthority(Path out)throws Exception{
        var engine=Path.of(System.getProperty("secondstrike.engine.path")).toRealPath();
        var sdk=Path.of(System.getProperty("secondstrike.sdk.path")).toRealPath();
        var mod=Path.of(System.getProperty("secondstrike.mod.path")).toRealPath();
        assertEquals("76ab99e288605412635a7930791b3c146bb9602bfdba27f05a111e468ff5bd38",sha256(engine));
        assertEquals("f41bc45670fdd3f5e8eabb218f30b6cee84570a4ea2a2d991ddb852c052f7a3e",sha256(sdk));
        assertEquals("0dd53e4d032cd07ea3db5f864f4616a08ba0f5d607158b1858c3806ccc7bbbeb",sha256(mod));
        var rom=Path.of(System.getProperty("s3k.rom.path"));
        assertEquals("cfbf98c36c776677290a872547ac47c53d2761d6",java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(rom))));
        var origins=new StringBuilder();
        for(var type:List.of(GameplayCaptureSession.class,com.openggf.GameLoop.class,com.openggf.mods.code.ModContext.class,
                MgzEndBossKnuxInstance.class,MarbleGardenBossRecipe.class)){
            var origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            assertTrue(origin.equals(engine)||origin.equals(sdk),"production class must resolve from the actual artifact pair: "+type+" "+origin);
            origins.append(type.getName()).append("=").append(origin).append("\n");
        }
        Files.writeString(out.resolve("artifact-authority.txt"),origins+"mod="+mod+" SHA256="+sha256(mod)+"\n");
    }
    private static String sha256(Path path)throws Exception{return java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static void assertCreatorInventory()throws Exception{
        var level=GameServices.level().getCurrentLevel();
        assertEquals(java.util.Set.of(1,88,146,233,349),level.getRings().stream()
                .map(com.openggf.level.rings.RingSpawn::placementId).collect(java.util.stream.Collectors.toSet()),"actual creator ring retention");
        var monitors=level.getObjects().stream().filter(o->o.objectId()==1&&(o.subtype()&15)!=4).toList();
        assertEquals(18,monitors.size());
        Files.writeString(Path.of(System.getenv("OPENGGF_M4_CAPTURE_ROOT")).resolve("creator-inventory.txt"),
                "rings="+level.getRings()+" non-shoes monitors="+monitors.size()+"\n",
                java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
        for(var monitor:monitors)assertEquals("second-strike:robotnik-monitor",monitor.objectKey());
        for(int index:new int[]{333,341}){
            var original=level.getObjects().stream().filter(o->o.layoutIndex()==index).findFirst().orElseThrow();
            assertNull(original.ownerModId());assertNull(original.objectKey(),"placed checkpoint and A2 stay native");
        }
    }
    private static int nativeStoppingPixels(int velocity,int deceleration){
        assertTrue(deceleration>0);int distance=0,speed=Math.abs(velocity);
        while(speed>0){speed=Math.max(0,speed-deceleration);distance+=speed;}
        return (distance+255)>>8;
    }
    private static void assertSupportedFloorUnlessAirborne(AbstractPlayableSprite player){if(!player.getAir())assertSupportedFloor(player);}
    private static void assertSupportedFloor(AbstractPlayableSprite player){
        int x=player.getCentreX(),y=player.getCentreY();
        var left=ObjectTerrainUtils.checkFloorDist(x-player.getXRadius(),y,player.getYRadius());
        var right=ObjectTerrainUtils.checkFloorDist(x+player.getXRadius(),y,player.getYRadius());
        assertTrue((left.foundSurface()||right.foundSurface())&&Math.min(left.distance(),right.distance())>=-2
                &&Math.min(left.distance(),right.distance())<=32,"ordinary anchor must have actual native floor support");
    }
    /** Observe native setup/ordinary/publication boundaries; never choose a fitted wait. */
    private static void awaitPublishedOrdinaryReload(GameplayCaptureSession session,Evidence evidence,
            java.io.BufferedWriter state,java.io.BufferedWriter authority,Path out)throws Exception {
        Readiness baseline=readiness();
        try(var trace=Files.newBufferedWriter(out.resolve("reload-readiness.csv"))){
            trace.write("call,phase,level_frame,pending_setup,player_tiles,hud_tiles,hud_counter_tiles,new_sat,new_counters");trace.newLine();
            trace.write("0,reloaded,"+baseline.row(baseline));trace.newLine();
            assertTrue(baseline.pending(),"S3K full reload must expose its native one-shot setup");
            step(session,evidence,0,state,authority,"reload-setup-only");
            Readiness setup=readiness();trace.write("1,setup-only,"+setup.row(baseline));trace.newLine();
            assertFalse(setup.pending(),"one observed setup pass must consume LOAD_THEN_PROCESS_ONCE");
            assertEquals(baseline.frame(),setup.frame(),"setup-only must not advance Level_frame_counter");
            // Native bound: first ordinary VBlank publishes the setup CPU SAT,
            // its completed loop builds the playable/HUD table, and the second
            // ordinary VBlank publishes that table. A held call is a blocker;
            // do not add retries, arbitrary settling or direct publication.
            for(int ordinary=1;ordinary<=2;ordinary++){
                int before=GameServices.level().getFrameCounter();
                step(session,evidence,0,state,authority,"reload-ordinary-"+ordinary);
                Readiness current=readiness();trace.write((ordinary+1)+",ordinary,"+current.row(baseline));trace.newLine();
                assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
                assertFalse(GameServices.level().isLevelInactiveForTransition());
                assertEquals(before+1,current.frame(),"held/nonadvancing reload call is not completed ordinary gameplay");
                assertFalse(current.pending());
                if(current.sat()!=baseline.sat()&&current.counters()!=baseline.counters()
                        &&current.playerTiles()>0&&current.hudTiles()>0&&current.counterTiles()>0)return;
            }
            fail("two completed ordinary iterations did not publish native playable SAT and HUD counters; see reload-readiness.csv");
        }
    }
    private record Readiness(int frame,boolean pending,int playerTiles,int hudTiles,int counterTiles,
                             SpritePresentation.Frame sat,SpritePresentation.Frame counters){
        String row(Readiness baseline){return frame+","+pending+","+playerTiles+","+hudTiles+","+counterTiles
                +","+(sat!=baseline.sat)+","+(counters!=baseline.counters);}
    }
    private static Readiness readiness()throws Exception{
        var level=GameServices.level();var sat=LevelSpritePresentation.published(level);
        // Existing registered production snapshot, read only. No captureScene
        // projection, live sprite draw, publication call, or state restoration.
        Object displayed=SessionManager.getCurrentGameplayMode().getRewindRegistry().capture().get("level-sprite-presentation");
        assertNotNull(displayed,"native presentation rewind adapter must be registered");
        var accessor=displayed.getClass().getDeclaredMethod("counters");accessor.setAccessible(true);
        var counters=(SpritePresentation.Frame)accessor.invoke(displayed);
        return new Readiness(level.getFrameCounter(),level.hasPendingInitialProcessSpritesPass(),
                (int)sat.tiles().stream().filter(t->t.layer()==SpritePresentation.Layer.PLAYER).count(),
                (int)sat.tiles().stream().filter(t->t.layer()==SpritePresentation.Layer.HUD).count(),
                (int)counters.tiles().stream().filter(t->t.layer()==SpritePresentation.Layer.HUD_COUNTERS).count(),sat,counters);
    }
    private static final class Evidence {
        int frame,admitted=-1,dormant,fight,firstDrop,secondDrop,hp=8,peak,attempts;boolean reload,cuePixels;
        java.io.BufferedWriter visibility,touches,ability;NativeVisibility publishedVisibility;
        int firstHit=-1,committedRoots,recoveryStage;
        boolean recoveryComplete,glideObserved,slideObserved;
        public String toString(){return "frame="+frame+" reload="+reload+" admitted="+admitted+" dormant="+dormant
                +" fight="+fight+" firstDrop="+firstDrop+" secondDrop="+secondDrop+" hp="+hp+" peak="+peak+" committedRoots="+committedRoots+" attempts="+attempts+" firstHit="+firstHit+" recoveryStage="+recoveryStage+" recovered="+recoveryComplete
                +" glide="+glideObserved+" slide="+slideObserved+" cuePixels="+cuePixels;}
    }
    private static void step(GameplayCaptureSession session,Evidence evidence,int mask,java.io.BufferedWriter state,
                             java.io.BufferedWriter authority,String phase)throws Exception {
        boolean jump=(mask&AbstractPlayableSprite.INPUT_JUMP)!=0;
        var input=new Bk2FrameInput(evidence.frame++,mask,jump?1:0,false,"P1mask="+mask);
        // The next VBlank displays the CPU table prepared by the preceding loop.
        // Pair its actual identity with the native visibility inputs that built it.
        var prepared=presentationFrame("prepared");
        var preparedVisibility=nativeVisibility(session.player());
        int before=GameServices.level().getFrameCounter();
        var beforeBoss=roots().stream().filter(MgzEndBossKnuxInstance.class::isInstance)
                .map(MgzEndBossKnuxInstance.class::cast).findFirst().orElse(null);
        int beforeRings=session.player().getRingCount();
        String beforePlayer=touchPlayer(session.player());
        String beforeAbility=abilityState(session.player());
        int beforeHp=beforeBoss==null?-1:beforeBoss.getState().hitCount;
        String beforeBossState=beforeBoss==null?",,,":beforeBoss.getX()+","+beforeBoss.getY()+","+beforeHp+","+beforeBoss.getState().invulnerable;
        String beforeRegions=touchRegions(beforeBoss);
        session.step(input);session.renderFrame();
        var afterBoss=roots().stream().filter(MgzEndBossKnuxInstance.class::isInstance)
                .map(MgzEndBossKnuxInstance.class::cast).findFirst().orElse(null);
        evidence.committedRoots=(int)roots().stream().filter(b->b instanceof MgzEndBossKnuxInstance
                &&(int)read(b,"admissionPhase")>0&&(boolean)read(b,"counterCadence")).count();
        if(beforeBoss!=null&&afterBoss==beforeBoss&&afterBoss.getState().hitCount<beforeHp){
            assertEquals(beforeHp-1,afterBoss.getState().hitCount,"one native hit per ordinary touch pass");
            assertTrue(afterBoss.getState().invulnerable,"native accepted hit must start boss invulnerability");
            assertEquals(0,afterBoss.getCollisionFlags(),"native accepted hit must suppress boss body collision");
            if(evidence.firstHit<0)evidence.firstHit=input.frameIndex();
        }
        if(evidence.touches!=null){
            evidence.touches.write(input.frameIndex()+","+mask+","+beforePlayer+","+beforeBossState+",\""+beforeRegions+"\","
                    +session.player().getCentreX()+","+session.player().getCentreY()+","+session.player().getRingCount()+","+session.player().isHurt()
                    +","+session.player().getInvulnerableFrames()+","+!session.player().getDead()+","
                    +(afterBoss==null?",":afterBoss.getState().hitCount+","+afterBoss.getState().invulnerable)+",\""+touchRegions(afterBoss)+"\"");
            evidence.touches.newLine();
        }
        if(evidence.ability!=null){
            var player=session.player();int x=player.getCentreX(),y=player.getCentreY();
            var left=ObjectTerrainUtils.checkFloorDist(x-player.getXRadius(),y,player.getYRadius());
            var right=ObjectTerrainUtils.checkFloorDist(x+player.getXRadius(),y,player.getYRadius());
            var debug=GameServices.level().getObjectManager().getTouchResponseDebugState();
            // Read the existing diagnostic only. Never enable it or add instrumentation.
            String hits=debug==null?"unavailable":debug.getHits().toString();
            evidence.ability.write(input.frameIndex()+","+mask+","+evidence.recoveryStage+","+beforeAbility+","+abilityState(player)
                    +","+left.distance()+","+left.tileIndex()+","+right.distance()+","+right.tileIndex()+","+(debug!=null&&debug.isEnabled())
                    +",\""+hits.replace("\"","\"\"")+"\"");evidence.ability.newLine();
        }
        var published=LevelSpritePresentation.published(GameServices.level());
        boolean matched=published==prepared;
        if(GameServices.level().getFrameCounter()>before)
            assertTrue(matched,"ordinary VBlank must publish the observed previous CPU SAT");
        evidence.publishedVisibility=matched?preparedVisibility:null;
        if(evidence.visibility!=null){
            var boss=roots().stream().filter(MgzEndBossKnuxInstance.class::isInstance)
                    .map(MgzEndBossKnuxInstance.class::cast).findFirst().orElse(null);
            evidence.visibility.write(input.frameIndex()+","+mask+","+nativeVisibility(session.player()).row()+","
                    +(matched?preparedVisibility.row():"unknown,unknown,unknown,unknown,unknown,unknown,unknown,unknown,unknown")
                    +","+publishedBodyTiles()+","+(boss==null?",,,,,,,,":boss.getX()+","+boss.getY()+","+boss.getNativeRoutineForTesting()
                    +","+boss.getNativeTimerForTesting()+","+boss.getNativeXVelocityForTesting()+","+boss.getNativeYVelocityForTesting()
                    +","+boss.getCollisionFlags()+","+boss.getState().hitCount+","+boss.getState().invulnerable));
            evidence.visibility.newLine();
        }
        state.write(session.stateLine(input.frameIndex(),input));state.newLine();
        authority.write(input.frameIndex()+","+phase+","+GameServices.level().getCheckpointState().getLastCheckpointIndex()
                +","+evidence.committedRoots+","+roots().size()+","+describe(roots()));authority.newLine();
        if(session.player().isHurt()||session.player().getDead()||session.player().getInvulnerableFrames()>0){
            capture(session,evidence,Path.of(System.getenv("OPENGGF_M4_CAPTURE_ROOT")),"safety-stop-"+input.frameIndex(),false,null);
        }
        assertTrue(session.player().getRingCount()>=beforeRings,"any ring loss stops the damage-free witness");
        assertFalse(session.player().isHurt(),"any native hurt stops the damage-free witness at input"+input.frameIndex());
        assertFalse(session.player().getDead(),"any death stops the damage-free witness");
        assertEquals(0,session.player().getInvulnerableFrames(),"no earned post-hurt invulnerability is allowed");
    }
    private static String abilityHeader(String prefix){return java.util.Arrays.stream(new String[]{"jumping","double_jump","direction","x_radius","y_radius",
            "x_velocity","y_velocity","ground_speed","air","x","y"}).map(n->prefix+n).collect(java.util.stream.Collectors.joining(","));}
    private static String abilityState(AbstractPlayableSprite player){return player.isJumping()+","+player.getDoubleJumpFlag()+","+player.getDirection()
            +","+player.getXRadius()+","+player.getYRadius()+","+player.getXSpeed()+","+player.getYSpeed()+","+player.getGSpeed()
            +","+player.getAir()+","+player.getCentreX()+","+player.getCentreY();}
    private static String touchPlayer(AbstractPlayableSprite player){return player.getCentreX()+","+player.getCentreY()+","+player.getRingCount()
            +","+player.isHurt()+","+player.getInvulnerableFrames()+","+player.getAnimationId()+","+player.getRolling();}
    private static String touchRegions(MgzEndBossKnuxInstance boss){return boss==null?"none":java.util.Arrays.toString(boss.getMultiTouchRegions());}
    private static List<MgzDrillingRobotnikInstance> roots(){return GameServices.level().getObjectManager().getActiveObjects().stream()
            .filter(o->!o.isDestroyed()&&(o instanceof MgzEndBossKnuxInstance||o instanceof MgzEndBossInstance))
            .map(MgzDrillingRobotnikInstance.class::cast).toList();}
    private static String describe(List<MgzDrillingRobotnikInstance> roots){return roots.stream().map(b->b.getClass().getSimpleName()
            +"[layout="+b.getSpawn().layoutIndex()+" slot="+b.getSlotIndex()+" x="+b.getX()+" y="+b.getY()+" hp="+b.getState().hitCount
            +" phase="+(b instanceof MgzEndBossKnuxInstance?read(b,"admissionPhase"):"lower-event-A1")+" routine="+b.getState().routine+" flags="+b.getCollisionFlags()+"]").toList().toString();}
    private record NativeVisibility(int x,int y,int invulnerable,boolean decremented,boolean hurt,
                                    boolean hurtOwner,boolean hidden,boolean visible,int displayTimer){
        static String header(String prefix){return java.util.Arrays.stream(new String[]{"x","y","invulnerable","decremented",
                "hurt","hurt_owner","hidden","visible","display_timer"}).map(n->prefix+n).collect(java.util.stream.Collectors.joining(","));}
        String row(){return x+","+y+","+invulnerable+","+decremented+","+hurt+","+hurtOwner+","+hidden+","+visible+","+displayTimer;}
    }
    private static NativeVisibility nativeVisibility(AbstractPlayableSprite player){
        int timer=player.getInvulnerableFrames();
        boolean decremented=(boolean)read(player,"invulnerabilityDisplayTimerDecrementedThisFrame");
        return new NativeVisibility(player.getCentreX(),player.getCentreY(),timer,decremented,player.isHurt(),
                (boolean)read(player,"hurtRoutineOwnedDisplayThisFrame"),player.isHidden(),
                player.shouldRefreshRenderFlagThisFrame(),timer+(decremented?1:0));
    }
    private static SpritePresentation.Frame presentationFrame(String name)throws Exception{
        Object state=SessionManager.getCurrentGameplayMode().getRewindRegistry().capture().get("level-sprite-presentation");
        assertNotNull(state,"registered native presentation state");
        var accessor=state.getClass().getDeclaredMethod(name);accessor.setAccessible(true);
        return (SpritePresentation.Frame)accessor.invoke(state);
    }
    private static long publishedBodyTiles(){return LevelSpritePresentation.published(GameServices.level()).tiles().stream()
            .filter(t->t.layer()==SpritePresentation.Layer.PLAYER&&t.subject().part()==SpritePresentation.Part.BODY).count();}
    private static void capture(GameplayCaptureSession session,Evidence evidence,Path out,String label,boolean playerPixels,MgzEndBossKnuxInstance boss)throws Exception {
        RgbaImage image=session.render();ScreenshotCapture.savePNG(image,out.resolve(label+".png"));
        assertEquals(320,image.width());assertEquals(224,image.height());
        if(!playerPixels&&boss==null)return;
        RgbaImage tiles=session.render(false);
        if(playerPixels){
            var nativeDisplay=evidence.publishedVisibility;
            assertNotNull(nativeDisplay,"pixel oracle requires exact prepared-to-published visibility inputs at "+label);
            if(nativeDisplay.visible()){
                assertTrue(difference(image,tiles,session.player().getRenderCentreX()-GameServices.camera().getX(),
                        session.player().getRenderCentreY()-GameServices.camera().getY(),24)>20,"visible native playable pixels at "+label);
            }else{
                // Allow only the actual native blink decision for this published SAT.
                // A missing body for hidden/hurt/unknown reasons still fails.
                assertFalse(nativeDisplay.hidden(),"native blink must not hide the actor");
                assertFalse(nativeDisplay.hurt());assertFalse(nativeDisplay.hurtOwner());
                assertTrue(nativeDisplay.displayTimer()>0);
                assertEquals(0,nativeDisplay.displayTimer()&4,"native pre-decrement blink bit must be clear");
                assertEquals(0,publishedBodyTiles(),"native blink must omit playable BODY tiles at "+label);
            }
        }
        if(boss!=null)assertTrue(difference(image,tiles,boss.getX()-GameServices.camera().getX(),boss.getY()-GameServices.camera().getY(),24)>20,
                "visible ROM boss cue pixels at "+label);
    }
    private static int difference(RgbaImage image,RgbaImage tiles,int sx,int sy,int radius){int count=0;
        for(int y=Math.max(0,sy-radius);y<Math.min(224,sy+radius);y++)for(int x=Math.max(0,sx-radius);x<Math.min(320,sx+radius);x++)
            if(image.argb(x,y)!=tiles.argb(x,y))count++;return count;}
    private static Object read(Object object,String name){
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}catch(IllegalAccessException failure){throw new AssertionError(failure);}
        throw new AssertionError(name);
    }
}
