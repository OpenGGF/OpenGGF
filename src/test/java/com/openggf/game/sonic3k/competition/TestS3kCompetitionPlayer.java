package com.openggf.game.sonic3k.competition;

import com.openggf.data.RomManager;
import com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal native branch regressions; the sensor fixture never certifies an arena route. */
@org.junit.jupiter.api.parallel.Isolated
class TestS3kCompetitionPlayer {
    private static final Bounds BOUNDS=new Bounds(0x3FF,0x3FF,false,0);
    private static final class Sensors implements Terrain {
        boolean jumpAllowed=true;
        boolean land;
        int floorAngle;
        java.util.function.Consumer<S3kCompetitionPlayer> death=p->{};
        final List<String> calls=new ArrayList<>();
        @Override public boolean canJump(S3kCompetitionPlayer p,int clearance) {
            calls.add("clearance:"+clearance); return jumpAllowed;
        }
        @Override public void resolveGroundWall(S3kCompetitionPlayer p) { calls.add("wall"); }
        @Override public void followGround(S3kCompetitionPlayer p) { calls.add("ground"); }
        @Override public void resolveAir(S3kCompetitionPlayer p) {
            calls.add("air"); if(land) p.land(floorAngle);
        }
        @Override public void resolveDeath(S3kCompetitionPlayer p) {
            calls.add("death"); death.accept(p);
        }
    }
    private static S3kCompetitionPlayer player(int slot,Character c) {
        return new S3kCompetitionPlayer(slot,c,0x600,200);
    }
    @Test void independentHumanSlotsUseNativeInitSpeedsAndFullFractionWords() {
        var p1=player(0,Character.SONIC); var p2=player(1,Character.TAILS);
        var terrain=new Sensors();
        p1.tick(new Input(RIGHT,0),terrain,BOUNDS,0);
        p2.tick(new Input(LEFT,0),terrain,BOUNDS,0);
        assertEquals(0x10,p1.xVelocity()); assertEquals(-0x1C,p2.xVelocity());
        assertEquals(0x600,p1.centreX()); assertEquals(0x1000,p1.xFraction());
        assertEquals(0x5FF,p2.centreX()); assertEquals(0xE400,p2.xFraction());
        assertEquals(5,p1.xRadius()); assertEquals(0xB,p1.yRadius());
        assertEquals(List.of("wall","ground","wall","ground"),terrain.calls);
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void exactLockedOnCharacterSpeedBytesAreTheNativeInitTable() throws Exception {
        var rom=RomManager.getInstance().getRom();
        assertEquals(new Speeds(0x600,0x10,0x20),readSpeeds(rom,Character.SONIC));
        assertEquals(new Speeds(0x4C0,0x1C,0x70),readSpeeds(rom,Character.TAILS));
        assertEquals(new Speeds(0x580,0x10,0x200),readSpeeds(rom,Character.KNUCKLES));
    }
    @Test void brakingUsesNativeCharacterDecelerationAndFixBugsZeroSkidRegister() {
        var sonic=player(0,Character.SONIC); var knuckles=player(1,Character.KNUCKLES);
        sonic.setGroundVelocity(0x100); knuckles.setGroundVelocity(0x100);
        sonic.tick(new Input(LEFT,0),new Sensors(),BOUNDS,0);
        knuckles.tick(new Input(LEFT,0),new Sensors(),BOUNDS,0);
        assertEquals(0xE0,sonic.groundVelocity()); assertEquals(-0x80,knuckles.groundVelocity());
        var left=player(0,Character.SONIC); left.setGroundVelocity(-0x3F0);
        var right=player(1,Character.SONIC); right.setGroundVelocity(0x3F0);
        left.tick(new Input(RIGHT,0),new Sensors(),BOUNDS,0);
        right.tick(new Input(LEFT,0),new Sensors(),BOUNDS,0);
        assertEquals(-0x3D0,left.groundVelocity()); assertEquals(0xD,left.animation());
        assertEquals(0x3D0,right.groundVelocity()); assertNotEquals(0xD,right.animation());
    }
    @Test void aboveCapMomentumIsPreservedAndNeutralFrictionIsCharacterAcceleration() {
        var p=player(0,Character.SONIC); p.setGroundVelocity(0x900);
        p.tick(new Input(RIGHT,0),new Sensors(),BOUNDS,0);
        assertEquals(0x900,p.groundVelocity());
        p.tick(Input.NONE,new Sensors(),BOUNDS,1); assertEquals(0x8F0,p.groundVelocity());
        p.setStatusSecondary(0x80);
        p.tick(new Input(LEFT,0),new Sensors(),BOUNDS,2);
        assertEquals(0x8F0,p.groundVelocity(),"slide infinite-inertia bit skips input and friction");
    }
    @Test void nativeJumpHasClearanceGateAndSharedSevenFourteenRadiiWrites() {
        var p=player(0,Character.SONIC); var terrain=new Sensors(); terrain.jumpAllowed=false;
        p.tick(new Input(JUMP,JUMP),terrain,BOUNDS,0); assertFalse(p.airborne());
        terrain.jumpAllowed=true;
        p.tick(new Input(JUMP,JUMP),terrain,BOUNDS,1);
        assertEquals(-0x680,p.yVelocity()); assertEquals(197,p.centreY());
        assertTrue(p.jumping()); assertTrue(p.rolling()); assertFalse(p.rollingJump());
        assertEquals(7,p.xRadius()); assertEquals(14,p.yRadius());
        assertEquals("clearance:6",terrain.calls.getLast());
        p.tick(Input.NONE,terrain,BOUNDS,2);
        assertEquals(193,p.centreY()); assertEquals(-0x3C8,p.yVelocity());
    }
    @Test void jumpingFromRollMarksNoAirSteeringAndDoesNotUseCampaignGlide() {
        var p=player(0,Character.KNUCKLES); p.setRolling(true);p.setRadii(3,7);
        p.setXVelocity(0x200); p.setGroundVelocity(0x200);
        p.tick(new Input(RIGHT|JUMP,JUMP),new Sensors(),BOUNDS,0);
        assertTrue(p.rollingJump()); assertEquals(5,p.xRadius()); assertEquals(11,p.yRadius());
        p.tick(new Input(LEFT|JUMP,0),new Sensors(),BOUNDS,1);
        assertEquals(0x200,p.xVelocity()); assertEquals(-0x648,p.yVelocity());
        assertEquals(0,p.doubleJumpFlag());
    }
    @Test void spindashChargesDecaysAndReleasesUsingNativeHighByteTableIndex() {
        var p=player(0,Character.SONIC); var terrain=new Sensors();
        p.tick(new Input(DOWN,0),terrain,BOUNDS,0); assertEquals(8,p.animation());
        p.tick(new Input(DOWN|JUMP,JUMP),terrain,BOUNDS,1);
        assertEquals(1,p.spinDashFlag()); assertEquals(0,p.spinDashCounter());
        p.tick(new Input(DOWN|JUMP,JUMP),terrain,BOUNDS,2); assertEquals(0x200,p.spinDashCounter());
        p.tick(new Input(DOWN,0),terrain,BOUNDS,3); assertEquals(0x1F0,p.spinDashCounter());
        p.tick(Input.NONE,terrain,BOUNDS,4);
        assertEquals(0x880,p.groundVelocity()); assertEquals(0,p.spinDashFlag());
        assertTrue(p.rolling()); assertEquals(3,p.xRadius()); assertEquals(7,p.yRadius());
        assertEquals(204,p.centreY());
    }
    @Test void peakDragUsesUnsignedComparisonAndDoesNotAffectDownwardFlight() {
        var rising=player(0,Character.SONIC); rising.setAirborne(true); rising.setYVelocity(-0x100);rising.setXVelocity(0x200);
        rising.tick(Input.NONE,new Sensors(),BOUNDS,0); assertEquals(0x1F0,rising.xVelocity());
        var falling=player(1,Character.SONIC); falling.setAirborne(true); falling.setYVelocity(0x100);falling.setXVelocity(0x200);
        falling.tick(Input.NONE,new Sensors(),BOUNDS,0); assertEquals(0x200,falling.xVelocity());
    }
    @Test void tailsDoublePressStartsNativeTimedFlightAndFlapCadence() {
        var p=player(1,Character.TAILS); p.setAirborne(true);p.setJumping(true);p.setRolling(true);p.setRadii(7,14);
        p.setYVelocity(-0x300);
        p.tick(new Input(JUMP,JUMP),new Sensors(),BOUNDS,0);
        assertEquals(1,p.doubleJumpFlag());assertEquals(240,p.doubleJumpProperty());
        assertFalse(p.rolling());assertEquals(11,p.yRadius());
        int before=p.yVelocity();
        p.tick(Input.NONE,new Sensors(),BOUNDS,1);
        assertEquals(239,p.doubleJumpProperty()); assertEquals(before+8,p.yVelocity());
        p.setYVelocity(0);p.tick(new Input(JUMP,JUMP),new Sensors(),BOUNDS,2);
        assertEquals(2,p.doubleJumpFlag());assertEquals(8,p.yVelocity());
        p.tick(Input.NONE,new Sensors(),BOUNDS,3);
        assertEquals(3,p.doubleJumpFlag());assertEquals(-0x18,p.yVelocity());
    }
    @Test void wordWrapAndNativePlacementKeepFractionsAndTouchUsesSeparateMiniatureBox() {
        var p=player(0,Character.SONIC);p.setCentre(0x3FF,0x402);p.setFractions(0xA123,0xB456);
        p.setObjectControl(1);
        p.tick(Input.NONE,new Sensors(),new Bounds(0x3FF,0x3FF,true,-0x100),0);
        assertEquals(0x7FF,p.centreX());assertEquals(2,p.centreY());
        assertEquals(0xA123,p.xFraction());assertEquals(0xB456,p.yFraction());
        assertEquals(new TouchBounds(0x7FB,-6,8,16),p.touchBounds());
        p.setCentreNative(0x444,0x123);assertEquals(0xA123,p.xFraction());
        p.setObjectControl(0x80);assertFalse(p.touchEnabled());
    }
    @Test void hurtLandingAndDeathHaveNativeRoutineStateAndDistinctGravity() {
        var p=player(0,Character.SONIC);p.hurt(0x700);
        assertEquals(4,p.routine());assertEquals(-0x200,p.xVelocity());assertEquals(-0x400,p.yVelocity());
        var sensors=new Sensors();p.tick(Input.NONE,sensors,BOUNDS,0);
        assertEquals(-0x3D0,p.yVelocity());
        sensors.land=true;p.tick(Input.NONE,sensors,BOUNDS,1);
        assertEquals(2,p.routine());assertEquals(120,p.invulnerabilityTimer());
        assertEquals(0,p.xVelocity());assertEquals(0,p.yVelocity());
        p.kill();assertEquals(6,p.routine());assertEquals(-0x700,p.yVelocity());
        p.tick(Input.NONE,sensors,BOUNDS,2);assertEquals(-0x6C8,p.yVelocity());
    }
    @Test void bothActorsRestoreAndRecreateExactlyThenReplayIndependentInputs() {
        var p1=player(0,Character.SONIC); var p2=player(1,Character.TAILS);
        p1.setFractions(0x1234,0x5678);p2.setStatusSecondary(0x84);p2.setSpeedShoesTimer(75);
        p2.setSpeeds(0xC00,0x18,0x80);p2.setFlipAngle(1);p2.setFlipsRemaining(3);
        var first=p1.capture();var second=p2.capture();
        for(int f=0;f<16;f++) {
            p1.tick(new Input(RIGHT,f==6?JUMP:0),new Sensors(),BOUNDS,f);
            p2.tick(new Input(LEFT,f==4?JUMP:0),new Sensors(),BOUNDS,f);
        }
        var expected1=p1.capture();var expected2=p2.capture();
        p1.restore(first);p2=S3kCompetitionPlayer.recreate(second);
        assertEquals(first,p1.capture());assertEquals(second,p2.capture());
        for(int f=0;f<16;f++) {
            p1.tick(new Input(RIGHT,f==6?JUMP:0),new Sensors(),BOUNDS,f);
            p2.tick(new Input(LEFT,f==4?JUMP:0),new Sensors(),BOUNDS,f);
        }
        assertEquals(expected1,p1.capture());assertEquals(expected2,p2.capture());
        assertThrows(IllegalArgumentException.class,()->p1.restore(second));
    }
    private static S3kMiniatureAnimation nativeAnimationOracle() {
        byte[][] sonic=new byte[36][],knuckles=new byte[36][],tails=new byte[33][];
        byte[] idle=java.util.HexFormat.of().parseHex("071b1b1b1b1b1b1b1b1c1c1c1c1c1c1d1e1d1e1d1e1d1e1d1efe10");
        for(int i=0;i<36;i++) {sonic[i]=idle;knuckles[i]=idle;}
        for(int i=0;i<33;i++) tails[i]=idle;
        sonic[0]=knuckles[0]=tails[0]=new byte[]{-1,1,2,3,2,-1};
        sonic[1]=knuckles[1]=tails[1]=new byte[]{-1,13,14,-1,-1,-1};
        sonic[2]=sonic[3]=knuckles[2]=knuckles[3]=new byte[]{-2,41,40,42,40,-1};
        tails[2]=tails[3]=new byte[]{1,40,41,42,-1};
        sonic[4]=tails[4]=new byte[]{-3,37,38,-1,-1,-1};
        knuckles[4]=new byte[]{-3,37,38,39,38,-1};
        sonic[9]=knuckles[9]=tails[9]=new byte[]{0,35,36,-1};
        sonic[13]=knuckles[13]=tails[13]=new byte[]{5,34,34,34,34,-3,0};
        return new S3kMiniatureAnimation(new S3kMiniatureAnimation.Bank(sonic,knuckles,tails));
    }
    @Test void miniatureWalkingSelectsNativeSpeedAndSlopeFramesBeforeTimerExpires() {
        var p=player(0,Character.SONIC);p.setAnimation(0);p.setGroundVelocity(0x300);
        var animator=nativeAnimationOracle();
        var s=animator.tick(p,S3kMiniatureAnimation.State.INITIAL);
        assertEquals(1,s.mappingFrame());assertEquals(1,s.frame());assertEquals(5,s.timer());
        s=animator.tick(p,s);
        assertEquals(2,s.mappingFrame(),"running branch maps the selected frame even while its timer remains positive");
        assertEquals(1,s.frame());assertEquals(4,s.timer());
        p.setGroundVelocity(0x700);p.setAngle(0x40);
        s=animator.tick(p,S3kMiniatureAnimation.State.INITIAL);
        assertEquals(17,s.mappingFrame());assertEquals(1,s.timer());assertEquals(3,s.renderFlips());
    }
    @Test void miniatureAnimationsPreserveByteLoopChangeAndKnucklesPushTiming() {
        var p=player(0,Character.SONIC);var animator=nativeAnimationOracle();
        p.setAnimation(5);
        var s=animator.tick(p,new S3kMiniatureAnimation.State(5,24,0,0,0));
        assertEquals(0x1C,s.mappingFrame());assertEquals(9,s.frame());assertEquals(7,s.timer());
        p.setAnimation(13);
        s=animator.tick(p,new S3kMiniatureAnimation.State(13,4,0,34,0));
        assertEquals(0,p.animation());assertEquals(13,s.previousAnimation());
        var knuckles=player(1,Character.KNUCKLES);knuckles.setAnimation(0);knuckles.setPushing(true);
        s=animator.tick(knuckles,new S3kMiniatureAnimation.State(0,0,0,0,0));
        assertEquals(37,s.mappingFrame());assertEquals(8,s.timer());
        p.setAnimation(0);p.setPushing(true);
        s=animator.tick(p,new S3kMiniatureAnimation.State(0,0,0,0,0));
        assertEquals(32,s.timer());
    }
    @Test void nativeTailsRollScriptRetainsPositiveDelayAndTumbleFlips() {
        var tails=player(1,Character.TAILS);var animator=nativeAnimationOracle();
        tails.setAnimation(2);tails.setGroundVelocity(0x900);
        var s=animator.tick(tails,S3kMiniatureAnimation.State.INITIAL);
        assertEquals(40,s.mappingFrame());assertEquals(1,s.timer());
        s=animator.tick(tails,s);assertEquals(40,s.mappingFrame());
        s=animator.tick(tails,s);assertEquals(41,s.mappingFrame());
        tails.setAnimation(0);tails.setFlipAngle(1);tails.setFacingLeft(true);
        s=animator.tick(tails,S3kMiniatureAnimation.State.INITIAL);
        assertEquals(24,s.mappingFrame());assertEquals(3,s.renderFlips());assertEquals(0,s.timer());
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void miniatureRomScriptsAndCapturedAnimationPhaseReplayExactly() throws Exception {
        var animator=S3kMiniatureAnimation.fromRom(RomManager.getInstance().getRom());
        var p=player(0,Character.SONIC);p.setAnimator(animator);p.setGroundVelocity(0x300);
        for(int f=0;f<7;f++) p.tick(new Input(RIGHT,0),new Sensors(),BOUNDS,f);
        var before=p.capture();
        for(int f=7;f<25;f++) p.tick(new Input(RIGHT,0),new Sensors(),BOUNDS,f);
        var expected=p.capture();
        p=S3kCompetitionPlayer.recreate(before,animator);
        for(int f=7;f<25;f++) p.tick(new Input(RIGHT,0),new Sensors(),BOUNDS,f);
        assertEquals(expected,p.capture());assertTrue(p.mappingFrame()>0);
        var tails=player(1,Character.TAILS);tails.setAnimator(animator);tails.setAnimation(2);
        tails.setObjectControl(1);tails.tick(Input.NONE,new Sensors(),BOUNDS,0);
        assertEquals(0x28,tails.mappingFrame());
    }

    @Test void displayBlinkUsesPredecrementTimerAndHurtRoutineAlwaysDraws() {
        var p=player(0,Character.SONIC);p.setInvulnerabilityTimer(5);
        p.tick(Input.NONE,new Sensors(),BOUNDS,0);assertTrue(p.bodyVisible());
        assertEquals(4,p.invulnerabilityTimer());
        p.tick(Input.NONE,new Sensors(),BOUNDS,1);assertTrue(p.bodyVisible());
        p.tick(Input.NONE,new Sensors(),BOUNDS,2);assertFalse(p.bodyVisible());
        p.setInvulnerabilityTimer(1);p.tick(Input.NONE,new Sensors(),BOUNDS,3);
        assertFalse(p.bodyVisible());assertEquals(0,p.invulnerabilityTimer());
        p.tick(Input.NONE,new Sensors(),BOUNDS,4);assertTrue(p.bodyVisible());
        p.hurt();p.tick(Input.NONE,new Sensors(),BOUNDS,5);assertTrue(p.bodyVisible());
    }

    @Test void spriteBridgeBindsBothNativeIdentitiesAndRestoresActualSubclassPayload() {
        com.openggf.game.session.EngineServices.configure(
                com.openggf.game.session.EngineContext.fromLegacySingletonsForBootstrap());
        com.openggf.game.GameModuleRegistry.setCurrent(new com.openggf.game.sonic3k.Sonic3kGameModule());
        com.openggf.tests.TestEnvironment.activeGameplayMode();
        try {
            var p1=player(0,Character.SONIC);var p2=player(1,Character.TAILS);
            p1.setAnimator(nativeAnimationOracle());p2.setAnimator(nativeAnimationOracle());
            var first=p1.sprite();var second=p2.sprite();
            assertEquals(com.openggf.game.CharacterKey.SONIC,first.characterKey());
            assertEquals(com.openggf.game.CharacterKey.TAILS,second.characterKey());
            assertEquals("s3k-competition-p1",first.getCode());assertEquals("s3k-competition-p2",second.getCode());
            assertEquals(5,first.getXRadius());assertEquals(11,first.getYRadius());
            p1.setFractions(0x1234,0x5678);p1.setGroundVelocity(0x300);
            p1.tick(new Input(RIGHT,0),new Sensors(),BOUNDS,0);
            var nativeBefore=p1.capture();var actualKeyframe=first.captureRewindState();
            p1.launch(0x800,-0x400);p1.setCentre(0x777,77);p1.setFlipAngle(0x42);
            first.restoreRewindState(actualKeyframe);
            assertEquals(nativeBefore,p1.capture());
            assertEquals(p1.centreX(),first.getCentreX());assertEquals(0x5678,first.getYSubpixelRaw());
            var recreated=S3kMiniatureSprite.recreate(p2.capture(),nativeAnimationOracle());
            assertEquals(second.characterKey(),recreated.characterKey());assertEquals(1,recreated.nativeSlot());
            assertEquals(p2.capture(),recreated.nativePlayer().capture());
            // Actual shared terrain writes are accepted explicitly, without normal movement.
            first.setCentreXPreserveSubpixel((short)0x655);first.setXSpeed((short)0x456);
            first.synchronizeToModel();
            assertEquals(0x655,p1.centreX());assertEquals(0x456,p1.xVelocity());
            assertEquals(0x2234,p1.xFraction());
        } finally {
            com.openggf.game.session.SessionManager.clear();com.openggf.game.GameModuleRegistry.reset();
        }
    }

    @Test void additionalSpindashPumpRestartsAnimationThroughNativeAdjacentByteWrite() {
        var p=player(0,Character.SONIC);p.setAnimator(nativeAnimationOracle());
        var terrain=new Sensors();
        p.tick(new Input(DOWN,0),terrain,BOUNDS,0);
        p.tick(new Input(DOWN|JUMP,JUMP),terrain,BOUNDS,1);assertEquals(35,p.mappingFrame());
        p.tick(new Input(DOWN,0),terrain,BOUNDS,2);assertEquals(36,p.mappingFrame());
        p.tick(new Input(DOWN|JUMP,JUMP),terrain,BOUNDS,3);
        assertEquals(35,p.mappingFrame());assertEquals(1,p.animationState().frame());
    }

    @Test void nativeDeathPrepassRunsBeforeMotionThenRespawnStillExecutesGravityForBothSlots() {
        for(int slot=0;slot<2;slot++) {
            int nativeSlot=slot;
            var p=player(slot,slot==0?Character.SONIC:Character.TAILS);
            p.setAnimator(nativeAnimationOracle());p.kill();
            p.setCentreNative(0x700,0x491);p.setFractions(0xA123,0xB456);
            p.setSpinDashFlag(1);p.setJumping(true);p.setMoveLock(12);
            p.setStatusSecondary(0x84);p.setSpeedShoesTimer(75);p.setSpeeds(0xC00,0x18,0x80);
            var sensors=new Sensors();
            sensors.death=actor->{
                assertEquals(nativeSlot,actor.slot());assertEquals(6,actor.routine());
                assertEquals(0x491,actor.centreY(),"prepass observes the unmoved death position");
                assertEquals(-0x700,actor.yVelocity());
                assertEquals(0,actor.spinDashFlag(),"death prepass clears the native byte before the callback");
                actor.respawn(0x3FF,0x123,0xE,0xF);
            };
            p.tick(new Input(RIGHT|JUMP,JUMP),sensors,BOUNDS,slot);
            assertEquals(List.of("death"),sensors.calls,"same-pass respawn skips ordinary terrain and input paths");
            assertEquals(2,p.routine());assertEquals(0x3FF,p.centreX(),"death path does not execute ordinary X wrapping");
            assertEquals(0x123,p.centreY());assertEquals(0xA123,p.xFraction());assertEquals(0xB456,p.yFraction());
            assertEquals(0,p.xVelocity());assertEquals(0,p.groundVelocity());assertEquals(0x38,p.yVelocity());
            assertEquals(2,p.status());assertEquals(0,p.moveLock());assertEquals(0,p.objectControl());
            assertEquals(0xE,p.topSolidBit());assertEquals(0xF,p.lrbSolidBit());
            assertEquals(0,p.speedShoesTimer());
            assertEquals(slot==0?new Speeds(0x600,0x10,0x20):new Speeds(0x4C0,0x1C,0x70),p.speeds());
            assertEquals(0x84,p.statusSecondary(),"native respawn does not rewrite this unrelated byte");
            assertTrue(p.jumping(),"native respawn does not rewrite jumping");
            assertEquals(5,p.animation());assertEquals(0x1B,p.mappingFrame(),"animation executes after same-pass respawn");
        }
    }
    @Test void deathPrepassWithoutRespawnClearsSpinDashAndThenMovesTheDeadActor() {
        var p=player(1,Character.TAILS);p.kill();p.setSpinDashFlag(1);
        p.setCentre(0x600,400);p.setFractions(0x1234,0x5678);
        var sensors=new Sensors();
        sensors.death=actor->{assertEquals(400,actor.centreY());assertEquals(0,actor.spinDashFlag());};
        p.tick(Input.NONE,sensors,BOUNDS,0);
        assertEquals(393,p.centreY());assertEquals(0x5678,p.yFraction());
        assertEquals(-0x6C8,p.yVelocity());assertEquals(6,p.routine());
        assertEquals(List.of("death"),sensors.calls);
    }

}
