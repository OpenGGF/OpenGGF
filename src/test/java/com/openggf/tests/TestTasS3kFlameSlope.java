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

/** SOZ2 fire-dash comparisons: the normal-floor reverse-zip limitation and a
 * separate ordinary special-slide control. Declared inlets precede the action;
 * native gameplay rows are comparison-only, never state hydration. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kFlameSlope {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void currentLimitationFireDashSlopeEntryUsesVerticalCorrectionAndMissesNativeReverseZip(boolean dash) {
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1)
                .withFreshLevelStartLifecycle().build();
        f.stepFrame(false,false,false,false,false);
        var p=f.sprite();var level=GameServices.level();
        // Independently authored full-spindash rolling entry on the ROM's
        // ordinary floor: surface $420, radius14, one-pixel sensor gap.
        // All fractions are zero. No observed gameplay row is imported.
        p.setRolling(true);p.applyRollingRadii(true);
        p.setCentreX((short)0x889);p.setCentreY((short)0x411);p.setSubpixelRaw(0,0);
        p.setAir(false);p.setOnObject(false);p.setXSpeed((short)-0xC00);
        p.setYSpeed((short)0);p.setGSpeed((short)-0xC00);p.setAngle((byte)0);
        p.setGroundMode(GroundMode.GROUND);p.setDirection(Direction.LEFT);p.giveShield(ShieldType.FIRE);
        assertEquals(4,level.getBlockIdAt(0x889,0x420));
        var contact=new GroundSensor(p,Direction.DOWN,(byte)-7,(byte)14,true).scan();
        assertNotNull(contact);assertEquals(0,contact.distance());
        assertFalse(p.isSliding());assertEquals(0,p.getMoveLockTimer());
        assertEquals(0,p.getDoubleJumpFlag());assertFalse(p.getRollingJump());
        var camera=f.camera();camera.setFrozen(false);camera.setMinX((short)0);
        camera.setMaxX((short)0x3F00);camera.setMaxY((short)0x7FF);camera.updatePosition(true);
        int exitVx=0, reverseCorrections=0, firstLock=-1;
        for(int tick=0;tick<45;tick++){
            boolean left=tick<=11;
            boolean right=tick>=12&&tick<=35;
            boolean jump=(tick>=2&&tick<=4)||(dash&&tick==10);
            f.stepFrame(false,false,left,right,jump);
            if(tick==2){
                assertTrue(p.getAir());assertTrue(p.getRollingJump());assertEquals(19,p.getYRadius());
                assertEquals(-2633,p.getXSpeed());assertEquals(-1911,p.getYSpeed());
            }
            if(tick==9){
                assertTrue(p.getAir());assertTrue(p.getRollingJump());assertEquals(19,p.getYRadius());
                assertEquals(0x83B,p.getCentreX());assertEquals(0x9700,p.getXSubpixelRaw());
                assertEquals(0x3ED,p.getCentreY());assertEquals(0x4F00,p.getYSubpixelRaw());
                assertEquals(0,p.getXSpeed());assertEquals(-744,p.getYSpeed());assertEquals(-800,p.getGSpeed());
                assertEquals(0,p.getDoubleJumpFlag());assertEquals(0,p.getMoveLockTimer());
                assertEquals(0,camera.getHorizScrollDelay());
            }
            if(tick==10){
                assertEquals(!dash,p.getRollingJump());assertEquals(19,p.getYRadius());
                assertEquals(dash,camera.getHorizScrollDelay()>0,"actual fire ability publishes camera history delay");
            }
            if(p.getMoveLockTimer()==30&&firstLock<0)firstLock=tick;
            if(dash&&tick==34){
                // Native player_movement_complete at logical tick34 matches
                // these fields; the next pre_ground_wall_probe retains the
                // position and accelerates vx/vy to545 before correction.
                assertEquals(0x856,p.getCentreX());assertEquals(0x7100,p.getXSubpixelRaw());
                assertEquals(0x409,p.getCentreY());assertEquals(0xED00,p.getYSubpixelRaw());
                assertEquals(529,p.getXSpeed());assertEquals(529,p.getYSpeed());assertEquals(749,p.getGSpeed());
                assertEquals(0x20,p.getAngle()&255);assertFalse(p.getAir());assertFalse(p.isOnObject());
                var embedded=new GroundSensor(p,Direction.DOWN,(byte)-9,(byte)19,true).scan();
                assertNotNull(embedded);assertTrue(embedded.distance() < -14,"acquired deep floor entry, never seeded");
            }
            if(dash&&tick==35){
                // Native loc_1137E decrements negative probe angle for SOZ:
                // $20+$C0=$E0 -> $DF; +$20 rounds to horizontal $C0.
                // The engine omits that adjustment and applies the same -2px
                // distance to Y (545-512=33), instead of native X=33/Y=545.
                assertEquals(545,p.getXSpeed());assertEquals(33,p.getYSpeed());assertEquals(771,p.getGSpeed());
            }
            if(tick>=12&&p.getXSpeed()<0)reverseCorrections++;
            if(tick==39){
                exitVx=p.getXSpeed();
                if(dash){
                    assertEquals(0x863,p.getCentreX());assertEquals(0,p.getXSubpixelRaw());
                    assertEquals(0x402,p.getCentreY());assertEquals(0x1C00,p.getYSubpixelRaw());
                    assertFalse(p.getAir(),"native has exited airborne at $834.AE00/$40C.2A00");
                }
            }
            assertFalse(p.isSliding(),"normal terrain must not invoke the separate SOZ special-slide state");
        }
        assertEquals(0,reverseCorrections,"native dash has four increasingly negative reverse corrections after turning right");
        assertEquals(dash?15:32,firstLock,"real Player_SlopeRepel lock phase ran");
        if(dash){
            // Native parity was executed red: expected -4345, observed +707.
            // This passing assertion records that specific current limitation.
            assertEquals(707,exitVx);
            assertEquals(0x871,p.getCentreX());assertEquals(0x3400,p.getXSubpixelRaw());
            assertEquals(0x408,p.getCentreY());assertEquals(0x8000,p.getYSubpixelRaw());
            assertFalse(p.getAir());
        }else{
            assertEquals(0x855,p.getCentreX());assertEquals(0xCC00,p.getXSubpixelRaw());
            assertEquals(0x3F8,p.getCentreY());assertEquals(0x1C00,p.getYSubpixelRaw());
            assertEquals(569,p.getXSpeed());assertEquals(569,p.getYSpeed());assertFalse(p.getAir());
        }
    }

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
