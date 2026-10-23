package com.openggf.game.sonic3k.competition;

import com.openggf.game.CharacterConstructionScope;
import com.openggf.game.CharacterKey;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.PerObjectRewindSnapshot.PlayableSubclassRewindExtra;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.physics.Direction;
import com.openggf.physics.Sensor;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CharacterPhysicsSpec;
import com.openggf.sprites.playable.PlayableSensorSpec;

/**
 * Stable human roster/view identity for a miniature actor. The competition
 * host ticks {@link S3kCompetitionPlayer}, never the campaign movement owner.
 * ROM art/animation banks are installed by the competition presentation owner.
 */
public final class S3kMiniatureSprite extends AbstractPlayableSprite implements NativeSpriteSubmissionSource {
    @RewindTransient(reason="native model captured explicitly in miniature subclass rewind payload")
    private final S3kCompetitionPlayer player;

    private record NativeState(S3kCompetitionPlayer.State state) implements PlayableSubclassRewindExtra { }

    static S3kMiniatureSprite create(S3kCompetitionPlayer player) {
        CharacterKey key=switch(player.character()) {
            case SONIC -> CharacterKey.SONIC;
            case TAILS -> CharacterKey.TAILS;
            case KNUCKLES -> CharacterKey.KNUCKLES;
        };
        return CharacterConstructionScope.call(key,()->new S3kMiniatureSprite(player));
    }

    private S3kMiniatureSprite(S3kCompetitionPlayer player) {
        super("s3k-competition-p"+(player.slot()+1),(short)0,(short)0,specification(player));
        this.player=player;
        // Obj_Sonic2P/Obj_Tails2P init: word priority=$100, dimensions=$C.
        setPriorityBucket(RenderPriority.fromS3kWord(0x100));
        setRenderFlagWidthPixels(0xC);
        setRenderOffsets((short)0,(short)0);
        synchronizeFromModel();
    }

    private static CharacterPhysicsSpec specification(S3kCompetitionPlayer player) {
        var speeds=player.speeds();
        return new CharacterPhysicsSpec(PhysicsProfile.builder()
                .movement(speeds.acceleration(),speeds.deceleration(),speeds.acceleration(),speeds.maxSpeed(),0x680)
                .shape(5,0xB,3,7).rolling(0x20,0x80,0x80,0x1000).slopes(0x20,0x14,0x50)
                .balance(player.character()==S3kCompetitionPlayer.Character.TAILS,2).build(),
                new PlayableSensorSpec((byte)6,(byte)0));
    }
    public S3kCompetitionPlayer nativePlayer() { return player; }
    public int nativeSlot() { return player.slot(); }
    public static S3kMiniatureSprite recreate(S3kCompetitionPlayer.State state,S3kMiniatureAnimation animator) {
        return S3kCompetitionPlayer.recreate(state,animator).sprite();
    }
    public static S3kMiniatureSprite recreate(S3kCompetitionPlayer.State state) {
        return S3kCompetitionPlayer.recreate(state).sprite();
    }

    /** Publish native state without invoking campaign movement or resizing motion. */
    public void synchronizeFromModel() {
        if (player==null) return;
        setCollisionRadii((short)player.xRadius(),(short)player.yRadius(),false);
        NativePositionOps.writeXPosPreserveSubpixel(this,player.centreX());
        NativePositionOps.writeYPosPreserveSubpixel(this,player.centreY());
        setSubpixelRaw(player.xFraction(),player.yFraction());
        xSpeed=(short)player.xVelocity(); ySpeed=(short)player.yVelocity(); gSpeed=(short)player.groundVelocity();
        angle=(byte)player.angle(); topSolidBit=(byte)player.topSolidBit(); lrbSolidBit=(byte)player.lrbSolidBit();
        air=player.airborne(); rolling=player.rolling(); rollingJump=player.rollingJump();
        jumping=player.jumping(); pushing=player.pushing(); onObject=(player.status()&8)!=0;
        dead=player.dead(); hurt=player.routine()==4; inWater=(player.status()&0x40)!=0;
        moveLockTimer=player.moveLock(); doubleJumpFlag=player.doubleJumpFlag();
        doubleJumpProperty=(byte)player.doubleJumpProperty(); spindash=player.spinDashFlag()!=0;
        spindashCounter=(short)player.spinDashCounter(); invulnerableFrames=player.invulnerabilityTimer();
        speedShoes=(player.statusSecondary()&4)!=0;
        objectControlled=(player.objectControl()&1)!=0;
        objectControlAllowsCpu=(player.objectControl()&0x80)==0;
        setDirection(player.facingLeft()?Direction.LEFT:Direction.RIGHT);
        setAnimationId(player.animation()); setHighPriority(player.highPriority());
        setMappingFrame(player.mappingFrame());
        setRenderFlips((player.renderFlips()&1)!=0,(player.renderFlips()&2)!=0);
        setFlipAngle(player.flipAngle()); setFlipType(player.flipType());
        setFlipsRemaining(player.flipsRemaining()); setFlipSpeed(player.flipSpeed());
        runAccel=(short)player.speeds().acceleration(); runDecel=(short)player.speeds().deceleration();
        friction=runAccel; max=(short)player.speeds().maxSpeed();
    }

    /**
     * Explicitly accept native terrain/object writes made through the shared
     * sprite port. Call once before actor execution, after those writes finish.
     */
    public void synchronizeToModel() {
        int cx=getCentreX(),cy=getCentreY(),xf=getXSubpixelRaw(),yf=getYSubpixelRaw();
        int vx=xSpeed,vy=ySpeed,gv=gSpeed,a=angle&255,top=topSolidBit&255,lrb=lrbSolidBit&255;
        int flags=(getDirection()==Direction.LEFT?1:0)|(air?2:0)|(rolling?4:0)|(onObject?8:0)
                |(rollingJump?0x10:0)|(pushing?0x20:0)|(inWater?0x40:0)|(player.status()&0x80);
        int anim=getAnimationId(),lock=moveLockTimer,radiusX=getXRadius(),radiusY=getYRadius();
        boolean jumpState=jumping,priority=isHighPriority();
        int dj=doubleJumpFlag,djp=doubleJumpProperty&255,fa=getFlipAngle(),ft=getFlipType();
        int flips=getFlipsRemaining(),fs=getFlipSpeed();
        player.setCentreNative(cx,cy); player.setFractions(xf,yf); player.setXVelocity(vx);
        player.setYVelocity(vy); player.setGroundVelocity(gv); player.setStatus(flags);
        player.setAngle(a); player.setSolidBits(top,lrb); player.setAnimation(anim); player.setMoveLock(lock);
        player.setRadii(radiusX,radiusY); player.setJumping(jumpState); player.setHighPriority(priority);
        player.setDoubleJumpFlag(dj); player.setDoubleJumpProperty(djp); player.setFlipAngle(fa);
        player.setFlipType(ft); player.setFlipsRemaining(flips); player.setFlipSpeed(fs);
    }

    @Override public void draw() { if (player.bodyVisible()) drawStandardArt(); }
    @Override protected void createSensorLines() {
        // The instance-owned CharacterPhysicsSpec creates actual miniature sensors.
        groundSensors=new Sensor[0]; ceilingSensors=new Sensor[0]; pushSensors=new Sensor[0];
    }
    @Override protected PlayableSubclassRewindExtra captureSubclassRewindState() {
        return player==null?null:new NativeState(player.capture());
    }
    @Override protected void restoreSubclassRewindState(PlayableSubclassRewindExtra state) {
        if (state==null || player==null) return;
        if (!(state instanceof NativeState nativeState))
            throw new IllegalArgumentException("Incorrect miniature player snapshot");
        player.restore(nativeState.state());
    }
}
