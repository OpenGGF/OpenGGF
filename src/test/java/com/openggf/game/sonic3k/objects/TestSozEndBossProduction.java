package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import static org.junit.jupiter.api.Assertions.*;

/** Production services, player touch, runtime art and full composite rewind. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozEndBossProduction {
    private HeadlessTestFixture fixture(){
        com.openggf.game.session.SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(8,1)
                .startPosition((short)0x51C0,(short)0x620).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        fixture.sprite().refreshPersistentInstaShieldRegistration();
        fixture.stepIdleFrames(3);return fixture;
    }
    private SozEndBossInstance spawn(){var manager=GameServices.level().getObjectManager();
        var existing=manager.activeObjectsOfType(SozEndBossInstance.class);if(!existing.isEmpty())return existing.getFirst();
        return manager.createDynamicObject(()->new SozEndBossInstance(new ObjectSpawn(0x5260,0x690,0x98,0,0,false,0)));
    }
    @AfterEach void resetConfiguration(){SonicConfigurationService.getInstance().clearSessionOverrides();}
    @ParameterizedTest
    @CsvSource({"320,sonic,none", "352,tails,none", "400,sonic,tails", "528,knuckles,none", "800,sonic,'tails,sonic'", "512,sonic,none", "640,tails,sonic"})
    void productionGraphAndChargePeakRestoreAndReplayWithRomArt(int width,String character,String followers) {
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,followers.equals("none")?"":followers);
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        var f=fixture();var boss=spawn();var player=f.sprite();
        assertEquals(width,f.camera().getWidth()&65535);
        assertEquals(followers.equals("none")?0:followers.split(",").length,
                GameServices.level().getObjectManager().getObjectServices().playerQuery().sidekicks().size());
        player.setObjectControlled(true);player.setObjectControlSuppressesMovement(true);
        for(var sidekick:GameServices.level().getObjectManager().getObjectServices().playerQuery().sidekicks())
            if(sidekick instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite){
                sprite.setObjectControlled(true);sprite.setObjectControlSuppressesMovement(true);
            }
        var manager=GameServices.level().getObjectManager();
        var registry=f.gameplayMode().getRewindRegistry();
        boolean steady=false,charge=false;
        for(int i=0;i<500;i++){
            f.stepIdleFrames(1);
            int size=manager.activeObjectsOfType(SozEndBossChild.class).size();
            if(!steady&&size==12){
                assertSame(boss,manager.activeObjectsOfType(SozEndBossChild.class).stream().map(c->TestSozEndBoss.get(c,"boss")).findFirst().orElseThrow());
                var before=registry.capture();f.stepIdleFrames(1);replay(f,before,registry.capture());steady=true;
                boss=manager.activeObjectsOfType(SozEndBossInstance.class).getFirst();
            }
            if(size==33){
                // Thirteen steady SSTs + one charge + twenty particles = 34.
                // Earlier particles retire before the terminal particle creates the two beam SSTs.
                assertEquals(20,manager.activeObjectsOfType(SozEndBossChild.class).stream().filter(c->TestSozEndBoss.integer(c,"role")==13).count());
                var before=registry.capture();f.stepIdleFrames(1);var after=registry.capture();
                manager.setRewindInPlaceRestoreEnabledForTest(false);
                manager.activeObjectsOfType(SozEndBossChild.class).forEach(manager::removeDynamicObject);
                manager.removeDynamicObject(boss);
                replay(f,before,after);charge=true;break;
            }
        }
        assertTrue(steady);assertTrue(charge,"walking charge must allocate its complete native particle table");
        for(String key:new String[]{Sonic3kObjectArtKeys.SOZ_END_BOSS,Sonic3kObjectArtKeys.SOZ_END_BOSS_BODY,
                Sonic3kObjectArtKeys.ROBOTNIK_SHIP,Sonic3kObjectArtKeys.EGG_CAPSULE}){
            var renderer=GameServices.level().getObjectRenderManager().getRenderer(key);assertNotNull(renderer,key);assertTrue(renderer.isReady(),key);
        }
    }
    @Test void realPlayerTouchDamagesOnlyAfterShellContactAndReplaysKillingHit() {
        var f=fixture();var boss=spawn();f.stepIdleFrames(2);var p=f.sprite();
        var manager=GameServices.level().getObjectManager();var runtime=(SozZoneRuntimeState)manager.getObjectServices().zoneRuntimeState();
        // Contact the shell from its left. This writes the wall-event hit word, without damaging the pilot.
        NativePositionOps.writeXPosResetSubpixel(p,boss.getX()-38);NativePositionOps.writeYPosResetSubpixel(p,boss.getY()-20);
        p.setAir(false);p.setXSpeed((short)0);p.setYSpeed((short)0);p.setGSpeed((short)0);p.setAnimationId(0);
        f.stepIdleFrames(1);assertNotEquals(0,runtime.events().bossWallHitY());assertEquals(8,boss.getCollisionProperty());
        for(int hit=0;hit<8;hit++){
            boolean damaged=false;
            for(int i=0;i<40;i++){
                boss=manager.activeObjectsOfType(SozEndBossInstance.class).getFirst();
                if(runtime.events().bossWallHitY()==0){
                    NativePositionOps.writeXPosResetSubpixel(p,boss.getX()-38);NativePositionOps.writeYPosResetSubpixel(p,boss.getY()-20);
                    p.setAir(false);p.setRollingFlagPreserveRadii(false);p.setAnimationId(0);p.setXSpeed((short)0);p.setYSpeed((short)0);p.setGSpeed((short)0);
                    f.stepIdleFrames(1);continue;
                }
                if(boss.getCollisionFlags()==0){
                    NativePositionOps.writeXPosResetSubpixel(p,boss.getX()-100);NativePositionOps.writeYPosResetSubpixel(p,boss.getY()-60);
                    p.setAir(true);p.setXSpeed((short)0);p.setYSpeed((short)0);f.stepIdleFrames(1);continue;
                }
                NativePositionOps.writeXPosResetSubpixel(p,boss.getX());NativePositionOps.writeYPosResetSubpixel(p,boss.getY());
                p.setAir(true);p.setRollingFlagPreserveRadii(true);p.applyCustomRadii(7,14);p.setAnimationId(2);
                p.setXSpeed((short)0);p.setYSpeed((short)0);
                var before=f.gameplayMode().getRewindRegistry().capture();f.stepIdleFrames(1);
                if(boss.getCollisionProperty()==7-hit){
                    replay(f,before,f.gameplayMode().getRewindRegistry().capture());damaged=true;break;
                }
            }
            assertTrue(damaged,"pilot hit "+(hit+1)+" hp="+boss.getCollisionProperty()+" routine="+TestSozEndBoss.integer(boss,"routine")+" wall="+runtime.events().bossWallHitY()+" dead="+p.getDead()+" x="+p.getCentreX()+" y="+p.getCentreY());
        }
        var before=f.gameplayMode().getRewindRegistry().capture();f.stepIdleFrames(1);
        replay(f,before,f.gameplayMode().getRewindRegistry().capture());
        boss=manager.activeObjectsOfType(SozEndBossInstance.class).getFirst();assertTrue(boss.ownsPostResultsTransition());
        assertEquals(1,manager.activeObjectsOfType(SozEndBossExplosion.class).size());
        p.setObjectControlled(true);p.setObjectControlSuppressesMovement(true);
        for(int i=0;i<500&&TestSozEndBoss.integer(boss,"escapePhase")<5;i++)f.stepIdleFrames(1);
        assertEquals(5,TestSozEndBoss.integer(boss,"escapePhase"));
        var capsule=manager.activeObjectsOfType(SozEndBossEggCapsule.class).getFirst();
        assertEquals(0x5360,capsule.getX());assertEquals(0x720,capsule.getY());
        p.setObjectControlled(false);p.setObjectControlSuppressesMovement(false);p.setRollingFlagPreserveRadii(false);p.restoreDefaultRadii();
        for(int i=0;i<20&&!capsule.isOpened();i++){
            NativePositionOps.writeXPosResetSubpixel(p,capsule.getX());
            NativePositionOps.writeYPosResetSubpixel(p,capsule.getY()-36-4-p.getYRadius());
            p.setAir(true);p.setXSpeed((short)0);p.setYSpeed((short)0x100);p.setGSpeed((short)0);
            f.stepIdleFrames(1);
        }
        assertTrue(capsule.isOpened(),"native top button opens capsule through production solid contact");
        for(int i=0;i<100&&!capsule.isResultsStarted();i++)f.stepIdleFrames(1);
        assertTrue(capsule.isResultsStarted());
        assertTrue(manager.activeObjectsOfType(S3kResultsScreenObjectInstance.class).stream()
                .anyMatch(r->r.getClass().getSimpleName().equals("SozEndBossResults")));
        boolean boundary=false;
        for(int i=0;i<6500&&TestSozEndBoss.integer(boss,"escapePhase")==5;i++){
            var results=manager.activeObjectsOfType(S3kResultsScreenObjectInstance.class);
            if(!results.isEmpty()&&results.getFirst().getState()==4){
                var resultBefore=f.gameplayMode().getRewindRegistry().capture();f.stepIdleFrames(1);
                if(!manager.getObjectServices().gameState().isEndOfLevelActive()){
                    replay(f,resultBefore,f.gameplayMode().getRewindRegistry().capture());boundary=true;
                }
            } else f.stepIdleFrames(1);
        }
        assertTrue(boundary,"results retirement: rootPhase="+TestSozEndBoss.integer(boss,"escapePhase")+" results="+manager.activeObjectsOfType(S3kResultsScreenObjectInstance.class).stream().map(r->r.getState()).toList());
        boss=manager.activeObjectsOfType(SozEndBossInstance.class).getFirst();
        assertEquals(6,TestSozEndBoss.integer(boss,"escapePhase"));
        assertTrue(p.isControlLocked());assertFalse(p.isObjectControlled());
        var postResults=f.gameplayMode().getRewindRegistry().capture();f.stepIdleFrames(1);
        replay(f,postResults,f.gameplayMode().getRewindRegistry().capture());
        boss=manager.activeObjectsOfType(SozEndBossInstance.class).getFirst();
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED,true);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        var input=new com.openggf.control.InputHandler();
        var neutral=new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,"");
        input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral,neutral));
        var loop=new com.openggf.GameLoop(input);loop.setGameplayMode(f.gameplayMode());
        loop.setGameMode(com.openggf.game.GameMode.LEVEL);
        int largestOldFrame=0;boolean loaded=false;
        try {
            for(int i=0;i<1500;i++) {
                f.gameplayMode().getFadeManager().update();
                loop.step();
                var controller=f.gameplayMode().getRewindController();
                if(GameServices.level().getCurrentZone()==9) {
                    assertEquals(0,GameServices.level().getCurrentAct());
                    assertTrue(f.gameplayMode().isGameplayRuntimeReady());
                    assertNotSame(manager,GameServices.level().getObjectManager());
                    assertTrue(boss.isDestroyed());
                    assertTrue(largestOldFrame>10,"outgoing gameplay must have rewind history");
                    assertTrue(controller==null || controller.currentFrame()<largestOldFrame,
                            "level load must clear the outgoing rewind timeline");
                    loaded=true;break;
                }
                if(controller!=null)largestOldFrame=Math.max(largestOldFrame,controller.currentFrame());
            }
            assertTrue(loaded,"GameLoop consumes the native LRZ request; phase="
                    +TestSozEndBoss.integer(boss,"escapePhase")+" x="+p.getCentreX()+" y="+p.getCentreY());
            // Let the destination's real title owner retire; do not raw-consume its request.
            boolean destinationReady=false;
            for(int i=0;i<500;i++) {
                f.gameplayMode().getFadeManager().update();loop.step();
                var title=GameServices.module().getTitleCardProvider();
                if(loop.getCurrentGameMode()==com.openggf.game.GameMode.LEVEL
                        && (title==null || title.isComplete())
                        && !f.gameplayMode().getFadeManager().isActive()
                        && !GameServices.camera().getFocusedSprite().isControlLocked()) {
                    destinationReady=true;break;
                }
            }
            assertTrue(destinationReady,"LRZ title/fade must retire and release playable controls");
            assertEquals(com.openggf.game.GameMode.LEVEL,loop.getCurrentGameMode());
            assertEquals(9,GameServices.level().getCurrentZone());assertEquals(0,GameServices.level().getCurrentAct());
            assertFalse(GameServices.camera().getFocusedSprite().getDead());
        } finally {loop.closePresence();}

    }
    private static void replay(HeadlessTestFixture f,CompositeSnapshot before,CompositeSnapshot after){
        var registry=f.gameplayMode().getRewindRegistry();registry.restore(before);var restored=registry.capture();
        for(var key:before.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,before.get(key),restored.get(key)).isEmpty(),
                ()->key+": "+RewindSnapshotDiff.diffKey(key,before.get(key),restored.get(key)));
        var capsules=GameServices.level().getObjectManager().activeObjectsOfType(SozEndBossEggCapsule.class);
        if(!capsules.isEmpty() && capsules.getFirst().isResultsStarted()) {
            var player=f.sprite();
            assertTrue(!player.isObjectControlled() || player.isMgzTopPlatformCarryOwnedBy(capsules.getFirst()),
                    "restored result capsule owner: declared="+capsules.getFirst().ownsCarriedPlayerForRewind(player));
        }
        f.stepIdleFrames(1);var replayed=registry.capture();
        var differences=new java.util.ArrayList<String>();
        for(var key:after.entries().keySet()){
            var diff=RewindSnapshotDiff.diffKey(key,after.get(key),replayed.get(key));
            if(!diff.isEmpty())differences.add(key+": "+diff.stream().limit(6).toList());
        }
        if(!differences.isEmpty()){
            var expectedObjects=(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot)after.get("object-manager");
            differences.add("expectedRider="+expectedObjects.solidContactRiding());
            var beforeObjects=(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot)before.get("object-manager");
            differences.add("beforeRider="+beforeObjects.solidContactRiding());
        }
        assertTrue(differences.isEmpty(),differences.toString());
    }
}
