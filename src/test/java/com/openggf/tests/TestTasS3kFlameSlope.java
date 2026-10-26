package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.ShieldType;
import com.openggf.game.session.SessionManager;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** A native-compared SOZ2 fire-dash/slide boundary and neutral control.
 * This inlet is an ORDINARY slide in both producers, not evidence of the
 * separately reported stuck-in-slope/bottom-zip exploit (2877S lines60-61).
 * Initial airborne state was independently authored before native capture;
 * subsequent gameplay rows are comparison-only, never state hydration. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kFlameSlope {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void fireDashAfterRisingSlopeApproachLandsAndExitsWithNativeLock(boolean dash) {
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1)
                .withFreshLevelStartLifecycle().build();
        f.stepFrame(false,false,false,false,false);
        var p=f.sprite();var level=GameServices.level();
        assertEquals(0x77,level.getBlockIdAt(0x3440,0x260));
        // Valid clear airborne jump beside the real ROM slope. Neither overlap,
        // a landing, sliding, control lock, nor a flame dash is seeded.
        p.setRolling(true);p.applyRollingRadii(true);p.setCentreX((short)0x3440);p.setCentreY((short)0x24A);
        p.setAir(true);p.setJumping(true);p.setOnObject(false);p.setXSpeed((short)-0x3000);
        p.setYSpeed((short)-0x100);p.setGSpeed((short)-0x3000);p.setAngle((byte)0);p.setGroundMode(GroundMode.GROUND);
        p.setDirection(Direction.LEFT);p.giveShield(ShieldType.FIRE);
        var left=new GroundSensor(p,Direction.DOWN,(byte)-9,(byte)14,true).scan();
        var right=new GroundSensor(p,Direction.DOWN,(byte)9,(byte)14,true).scan();
        assertNotNull(left);assertNotNull(right);assertTrue(left.distance()>=0&&right.distance()>=0);
        assertFalse(p.isSliding());assertEquals(0,p.getMoveLockTimer());assertEquals(0,p.getDoubleJumpFlag());
        var camera=f.camera();camera.setFrozen(false);camera.setMinX((short)0);camera.setMaxX((short)0x3F00);
        camera.setMaxY((short)0x7FF);camera.updatePosition(true);

        f.stepFrame(false,false,false,false,false); // release/approach acquires overlap
        assertTrue(p.getAir());assertEquals(0x3411,p.getCentreX());assertEquals(0x249,p.getCentreY());
        assertEquals(-0x2E80,p.getXSpeed());assertEquals(-0xC8,p.getYSpeed());
        f.stepFrame(false,false,false,false,dash);
        if(dash){
            assertEquals(0x3409,p.getCentreX());assertEquals(0x232,p.getCentreY());
            assertEquals(-0x800,p.getXSpeed());assertEquals(0,p.getYSpeed());assertEquals(0,p.getGSpeed());
            assertFalse(p.getAir());assertTrue(p.isSliding());assertEquals(14,p.getYRadius());
        }else{
            assertEquals(0x33E4,p.getCentreX());assertEquals(0x248,p.getCentreY());
            assertTrue(p.getAir());assertFalse(p.isSliding());
        }
        int firstSlideExit=-1;
        for(int tick=2;tick<=100;tick++){
            boolean sliding=p.isSliding();
            f.stepFrame(false,false,false,false,false); // includes post-camera SOZ terrain owner
            if(sliding&&!p.isSliding()&&firstSlideExit<0){
                firstSlideExit=tick;assertEquals(5,p.getMoveLockTimer(),"loc_734A owns the actual slide exit");
                assertEquals(dash,p.getAir(),"dash route first exits in air; neutral control exits grounded");
            }
        }
        assertEquals(dash?49:86,firstSlideExit);
        assertEquals(dash?0x36AF:0x345A,p.getCentreX());assertEquals(dash?0x336:0x2EC,p.getCentreY());
    }
}
