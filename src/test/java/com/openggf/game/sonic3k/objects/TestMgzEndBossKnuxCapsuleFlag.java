package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameServices;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Native damage/capsule/animation component contract; no physical route claim. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMgzEndBossKnuxCapsuleFlag {
    @AfterEach void cleanup(){SessionManager.clear();GameServices.configuration().clearSessionOverrides();}

    @ParameterizedTest @ValueSource(ints={0,2})
    void capsuleAttemptClearsBossFlagAndResumesRealMgzScriptsEvenWithoutASlot(int freeSlots)throws Exception {
        SessionManager.clear();
        GameServices.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"knuckles");
        GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,true);
        HeadlessTestFixture.builder().withZoneAndAct(2,1)
                .startPosition((short)0x3E18,(short)0x80).startPositionIsCentre().build();
        var level=GameServices.level();var manager=level.getObjectManager();
        var boss=manager.activeObjectsOfType(MgzEndBossKnuxInstance.class).stream()
                .filter(b->b.getSpawn().layoutIndex()==341).findFirst().orElseThrow();
        var events=(AbstractLevelEventManager)GameServices.module().getLevelEventProvider();
        var script0=channel("s3k.mgz.script.0");var script1=channel("s3k.mgz.script.1");
        assertFalse(events.isBossActive());assertTrue(script0.guard().allows());assertTrue(script1.guard().allows());
        // Component camera inputs enter the real native gate, not a forced actor
        // phase. No gameplay/capture acceptance is inferred from these inputs.
        GameServices.camera().setX((short)0x3D78);GameServices.camera().setY((short)0x28);
        int frame=0;
        for(int i=0;i<123;i++)boss.update(frame++,null);
        assertTrue(events.isBossActive());assertEquals(0xA2,GameServices.gameState().getCurrentBossId());
        assertEquals(2,boss.getNativeRoutineForTesting());assertFalse(boss.isHidden());
        assertFalse(script0.guard().allows());assertFalse(script1.guard().allows());
        var publication=new PlcFrameLifecycleCoordinator((PlcLifecycleService)null);
        // Native VInt retires any pre-lock submission. A paused script must
        // stop NEW work, not cancel a payload already queued before the flag.
        publishOrdinaryVBlank(publication);
        byte[] paused222=pattern(0x222),paused252=pattern(0x252);
        for(int i=0;i<24;i++)animationFrame(publication);
        assertArrayEquals(paused222,pattern(0x222));assertArrayEquals(paused252,pattern(0x252));
        // Accepted native callbacks exercise actual damage/invulnerability and
        // native defeat timers; no HP/defeat/capsule fields are written by test.
        for(int hit=0;hit<8;hit++){
            boss.onPlayerAttack(null,null);assertEquals(7-hit,boss.getCollisionProperty());
            if(hit<7)for(int i=0;i<32;i++)boss.update(frame++,null);
        }
        assertTrue(boss.getState().defeated);assertTrue(events.isBossActive());
        boolean reserved=false;
        for(int i=0;i<400&&!(boolean)read(boss,"capsuleSpawned");i++){
            assertTrue(events.isBossActive(),"native flag stays set through the preceding defeat waits");
            assertEquals(0xA2,GameServices.gameState().getCurrentBossId());
            if((boolean)read(boss,"defeatLocEntered")&&(int)read(boss,"defeatTimer")==0){
                manager.reserveAllButNFreeSlots(freeSlots);reserved=true;
            }
            boss.update(frame++,null);
        }
        assertTrue(reserved,"exercise the exact capsule allocation boundary");
        assertEquals(true,read(boss,"capsuleSpawned"));assertFalse(events.isBossActive());
        assertEquals(0,GameServices.gameState().getCurrentBossId());
        int expectedCapsules=freeSlots>0?1:0;
        assertEquals(expectedCapsules,manager.activeObjectsOfType(MgzEndBossKnuxEggCapsuleInstance.class).size());
        assertTrue(script0.guard().allows());assertTrue(script1.guard().allows());
        byte[] resumed222=pattern(0x222),resumed252=pattern(0x252);boolean changed222=false,changed252=false;
        for(int i=0;i<24;i++){
            animationFrame(publication);
            changed222|=!Arrays.equals(resumed222,pattern(0x222));changed252|=!Arrays.equals(resumed252,pattern(0x252));
        }
        assertTrue(changed222,"real ROM MGZ tile222 resumes after native flag clear");
        assertTrue(changed252,"real ROM MGZ tile252 resumes after native flag clear");
        for(int i=0;i<3;i++)boss.update(frame++,null);
        assertFalse(events.isBossActive());
        assertEquals(expectedCapsules,manager.activeObjectsOfType(MgzEndBossKnuxEggCapsuleInstance.class).size(),"one native capsule attempt; no retry");
    }
    private static void animationFrame(PlcFrameLifecycleCoordinator coordinator){
        // Ordinary native order: VInt publishes the preceding Animate_Tiles
        // submission, then this iteration queues the next ROM payload.
        publishOrdinaryVBlank(coordinator);
        GameServices.level().getAnimatedPatternManager().update();
    }
    private static void publishOrdinaryVBlank(PlcFrameLifecycleCoordinator coordinator){
        var token=coordinator.latchBeforeFadeUpdate();token.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
        try{LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(SessionManager.getCurrentGameplayMode()),token);}
        finally{token.finish();}
    }
    private static AnimatedTileChannel channel(String id){return GameServices.animatedTileChannelGraph().channels().stream()
            .filter(c->c.channelId().equals(id)).findFirst().orElseThrow();}
    private static byte[] pattern(int tile){Pattern p=GameServices.level().getCurrentLevel().getPattern(tile);
        byte[] values=new byte[Pattern.PATTERN_SIZE_IN_MEM];int n=0;
        for(int y=0;y<Pattern.PATTERN_HEIGHT;y++)for(int x=0;x<Pattern.PATTERN_WIDTH;x++)values[n++]=p.getPixel(x,y);
        return values;}
    private static Object read(Object object,String name)throws Exception{Field f=MgzEndBossKnuxInstance.class.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
}
