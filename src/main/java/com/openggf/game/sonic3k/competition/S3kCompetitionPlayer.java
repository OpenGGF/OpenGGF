package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.physics.TrigLookupTable;
import java.io.IOException;
import java.util.Objects;

/**
 * Native competition actor, independent of the campaign playable controller.
 * Obj_Sonic2P (sonic3k.asm:21452-21886) and
 * Obj_Tails2P (sonic3k.asm:25605-25970).
 * Coordinates retain the full ROM 16:16 pair; velocities are signed 8:8 words.
 */
public final class S3kCompetitionPlayer {
    public static final int UP=1, DOWN=2, LEFT=4, RIGHT=8, JUMP=0x70;
    public enum Character { SONIC, TAILS, KNUCKLES }
    public record Input(int held, int pressed) {
        public Input { held &= 0xFF; pressed &= 0xFF; }
        public static final Input NONE = new Input(0,0);
    }
    public record Speeds(int maxSpeed, int acceleration, int deceleration) { }
    public record Bounds(int xWrapMask, int yWrapMask, boolean wrapY, int cameraMinY) { }
    public record TouchBounds(int left, int top, int width, int height) { }
    /**
     * Actual ROM terrain owner. No floor or no-collision fallback is supplied.
     * Ground resolution includes the forward wall sensor before positional
     * integration and Player_AnglePos afterwards; airborne resolution follows
     * SonicKnux_DoLevelCollision/Tails_DoLevelCollision after integration.
     */
    public interface Terrain {
        boolean canJump(S3kCompetitionPlayer player, int clearance);
        void resolveGroundWall(S3kCompetitionPlayer player);
        void followGround(S3kCompetitionPlayer player);
        void resolveAir(S3kCompetitionPlayer player);
        /** sub_123C2 prepass (also Tails loc_157C8); the session owns saved positions and notification. */
        void resolveDeath(S3kCompetitionPlayer player);
    }
    /** Exact writes through this actor's a6 pointer; the host resolves the current fixed dust SST. */
    public interface NativeDustWrites {
        void setAnimation(int animation);
        void startSkid();
    }
    public record State(int slot, Character character, Speeds baseSpeeds, Speeds speeds,
            int xFixed, int yFixed, int xVelocity, int yVelocity, int groundVelocity,
            int status, int statusSecondary, int routine, int objectControl,
            int moveLock, int animation, int animationFrame, int xRadius, int yRadius,
            int angle, int topSolidBit, int lrbSolidBit, int standingObject,
            int jumping, int spinDashFlag, int spinDashCounter,
            int doubleJumpFlag, int doubleJumpProperty, int flipAngle, int flipType,
            int flipsRemaining, int flipSpeed, int speedShoesTimer,
            int invulnerabilityTimer, boolean stickToConvex, boolean highPriority,
            S3kMiniatureAnimation.State animationState, boolean bodyVisible,
            int nativeRenderFlags, boolean nativeInitialized, int airLeft) { }

    private final int slot;
    private final Character character;
    private final Speeds baseSpeeds;
    private Speeds speeds;
    private int xFixed,yFixed,xVelocity,yVelocity,groundVelocity;
    private int status,statusSecondary,routine=2,objectControl,moveLock,animation=5,animationFrame;
    private int xRadius=5,yRadius=0xB,angle,topSolidBit=0xC,lrbSolidBit=0xD,standingObject=-1;
    private int jumping,spinDashFlag,spinDashCounter,doubleJumpFlag,doubleJumpProperty;
    private int flipAngle,flipType,flipsRemaining,flipSpeed=4,speedShoesTimer,invulnerabilityTimer;
    private boolean stickToConvex,highPriority;
    private boolean bodyVisible=true;
    private int nativeRenderFlags;
    private boolean nativeInitialized;
    private int airLeft=30;
    // Runtime binding, deliberately excluded from captured player state and installed on roster rebind.
    private transient NativeDustWrites nativeDustWrites;
    private S3kMiniatureSprite sprite;
    private S3kMiniatureAnimation animator;
    private S3kMiniatureAnimation.State animationState=S3kMiniatureAnimation.State.INITIAL;

    public S3kCompetitionPlayer(int slot, Character character, int centreX, int centreY) {
        this(slot,character,centreX,centreY,nativeSpeeds(character));
    }
    public S3kCompetitionPlayer(int slot, Character character, int centreX, int centreY, Speeds speeds) {
        if (slot<0 || slot>1) throw new IllegalArgumentException("Native player slot must be 0 or 1");
        this.slot=slot; this.character=Objects.requireNonNull(character);
        this.baseSpeeds=Objects.requireNonNull(speeds); this.speeds=speeds;
        setCentre(centreX,centreY);
    }
    public static Speeds readSpeeds(Rom rom, Character character) throws IOException {
        return readSpeeds(rom,character,S3kCompetitionResources.LOCKED_ON);
    }
    public static Speeds readSpeeds(Rom rom, Character character, S3kCompetitionResources resources) throws IOException {
        byte[] bytes=rom.readBytes(resources.at(0x1E3C00)+character.ordinal()*8,6);
        return new Speeds(word(bytes,0),word(bytes,2),word(bytes,4));
    }
    private static int word(byte[] b,int i) { return (b[i]&255)<<8 | b[i+1]&255; }
    private static Speeds nativeSpeeds(Character c) {
        // Character_Speeds (sonic3k.asm:202408-202411), ROM $1E3C00.
        return switch(c) {
            case SONIC -> new Speeds(0x600,0x10,0x20);
            case TAILS -> new Speeds(0x4C0,0x1C,0x70);
            case KNUCKLES -> new Speeds(0x580,0x10,0x200);
        };
    }

    /** One native actor pass; caller supplies each human slot's own controller. */
    public void tick(Input input,Terrain terrain,Bounds bounds,int levelFrameCounter) {
        Objects.requireNonNull(input); Objects.requireNonNull(terrain); Objects.requireNonNull(bounds);
        if (routine==4) {
            bodyVisible=true; integrate(); setYVelocity(yVelocity+(underwater()?0x10:0x30));
            if (bounds.wrapY()) wrapY(bounds.yWrapMask());
            terrain.resolveAir(this);
            if (!airborne()) {
                xVelocity=yVelocity=groundVelocity=objectControl=spinDashFlag=0;
                routine=2; animation=0; invulnerabilityTimer=120;
            }
            advanceAnimation(); publish(); return;
        }
        if (routine==6) {
            bodyVisible=true;
            // sub_123C2 clears this byte even while the actor is still above the
            // respawn threshold. Both slots read P1 Camera_Y_pos (FixBugs=0).
            spinDashFlag=0;
            terrain.resolveDeath(this);
            // loc_123AA still falls through MoveSprite_TestGravity after the
            // callback's native respawn changes routine back to 2. Do not run
            // an ordinary grounded/airborne pass in that same actor execution.
            integrate(); setYVelocity(yVelocity+0x38); advanceAnimation(); publish(); return;
        }
        if (routine!=2) { publish(); return; }
        bodyVisible=invulnerabilityTimer==0 || (invulnerabilityTimer&4)!=0;
        if (invulnerabilityTimer!=0) invulnerabilityTimer=(invulnerabilityTimer-1)&255;
        if ((objectControl&1)==0) {
            if (airborne()) tickAir(input,terrain,bounds,levelFrameCounter);
            else tickGround(input,terrain);
        }
        // loc_1079E/loc_132DE: word writes preserve both subpixel words.
        if (bounds.wrapY()) wrapY(bounds.yWrapMask());
        setCentreNative((centreX()&bounds.xWrapMask())+0x400,centreY());
        if ((objectControl&2)==0) advanceAnimation();
        publish();
    }

    private void tickGround(Input input,Terrain terrain) {
        if (!rolling() && spinDash(input,terrain)) return;
        if (spinDashFlag==0 && (input.pressed()&JUMP)!=0 && terrain.canJump(this,6)) {
            jump(); return; // addq.l #4,sp skips rest of the grounded movement path.
        }
        slopeForce(rolling());
        if (rolling()) rollSpeed(input); else groundSpeed(input);
        terrain.resolveGroundWall(this);
        if (!rolling() && (statusSecondary&0x80)==0 && Math.abs(groundVelocity)>=0x80
                && (input.held()&(LEFT|RIGHT))==0 && (input.held()&DOWN)!=0) {
            setRolling(true); setRadii(3,7); animation=2;
            setCentreNative(centreX(),centreY()+4);
        }
        integrate(); terrain.followGround(this); slopeRepel();
    }

    private void groundSpeed(Input input) {
        if ((statusSecondary&0x80)==0) {
            if (moveLock==0) {
                if ((input.held()&LEFT)!=0) accelerate(-1);
                if ((input.held()&RIGHT)!=0) accelerate(1);
                if (((angle+0x20)&0xC0)==0 && groundVelocity==0) {
                    setPushing(false); animation=(input.held()&DOWN)!=0?8:(input.held()&UP)!=0?7:5;
                }
            }
            if ((input.held()&(LEFT|RIGHT))==0)
                groundVelocity=approachZero(groundVelocity,speeds.acceleration());
        }
        groundToVelocity(false);
    }

    private void accelerate(int direction) {
        int value=groundVelocity;
        if (value*direction>=0) {
            setFacingLeft(direction<0); setPushing(false);
            groundVelocity=accelerated(value,direction*speeds.acceleration(),speeds.maxSpeed());
            animation=0; return;
        }
        int next=signed(value+direction*speeds.deceleration());
        if (value>0 && next<0 || value<0 && next>=0) next=direction*0x80;
        groundVelocity=next;
        // FixBugs=0: sub_113F6/sub_11482 overwrite d0's LOW BYTE with angle
        // before the skid-speed compare. The fixed branch uses a separate d1.
        int nativeCompare=signed((next&0xFF00)|((angle+0x20)&0xC0));
        if (((angle+0x20)&0xC0)==0 && (direction<0?nativeCompare>=0x400:nativeCompare<=-0x400)
                && (flipType&0x80)==0) {
            animation=0xD; setFacingLeft(direction>0);
            if(airLeft>=12 && nativeDustWrites!=null)nativeDustWrites.startSkid();
        }
    }

    private void rollSpeed(Input input) {
        if ((spinDashFlag&0x80)==0 && (statusSecondary&0x80)==0) {
            if (moveLock==0) {
                if ((input.held()&LEFT)!=0) {
                    if (groundVelocity<=0) setFacingLeft(true);
                    else groundVelocity=groundVelocity<0x20?-0x80:groundVelocity-0x20;
                }
                if ((input.held()&RIGHT)!=0) {
                    if (groundVelocity>=0) setFacingLeft(false);
                    else groundVelocity=groundVelocity>=-0x20?0x80:groundVelocity+0x20;
                }
            }
            groundVelocity=approachZero(groundVelocity,speeds.acceleration()>>1);
            if (Math.abs(groundVelocity)<0x80) {
                if (spinDashFlag==0) {
                    int oldRadius=yRadius;
                    setRolling(false); setRadii(5,0xB); animation=5;
                    setCentreNative(centreX(),centreY()+oldRadius-0xB);
                } else groundVelocity=facingLeft()?-0x400:0x400;
            }
        }
        groundToVelocity(true);
    }

    private void slopeForce(boolean roll) {
        // Player_SlopeResist (sonic3k.asm:23861-23900) and
        // Player_RollRepel (sonic3k.asm:23907-23940).
        if (((angle+0x60)&255)>=0xC0) return;
        int force=TrigLookupTable.sinHex(angle)*(roll?0x50:0x20)>>8;
        if (roll) {
            if (groundVelocity>=0?force<0:force>=0) force>>=2;
            groundVelocity=signed(groundVelocity+force);
        } else if (groundVelocity!=0 || Math.abs(force)>=0xD)
            groundVelocity=signed(groundVelocity+force);
    }
    private void slopeRepel() {
        if (stickToConvex) return;
        if (moveLock!=0) { moveLock=(moveLock-1)&0xFFFF; return; }
        if (((angle+0x18)&255)<0x30 || Math.abs(groundVelocity)>=0x280) return;
        moveLock=30;
        int quadrant=(angle+0x30)&255;
        if (quadrant>=0x60) setAirborne(true);
        else groundVelocity=signed(groundVelocity+(quadrant<0x30?-0x80:0x80));
    }
    private void groundToVelocity(boolean roll) {
        xVelocity=signed(TrigLookupTable.cosHex(angle)*groundVelocity>>8);
        yVelocity=signed(TrigLookupTable.sinHex(angle)*groundVelocity>>8);
        if (roll) xVelocity=Math.clamp(xVelocity,-0x1000,0x1000);
    }
    private boolean spinDash(Input input,Terrain terrain) {
        if (spinDashFlag==0) {
            if (animation!=8 || (input.pressed()&JUMP)==0) return false;
            animation=9; spinDashFlag=1; spinDashCounter=0;
            if(airLeft>=12 && nativeDustWrites!=null)nativeDustWrites.setAnimation(2);
            terrain.followGround(this); return true;
        }
        if ((input.held()&DOWN)==0) {
            setRolling(true); setRadii(3,7); animation=2;
            setCentreNative(centreX(),centreY()+4); spinDashFlag=0;
            groundVelocity=(0x800+((spinDashCounter>>>8)*0x80))*(facingLeft()?-1:1);
            if(nativeDustWrites!=null)nativeDustWrites.setAnimation(0);
        } else {
            spinDashCounter-=spinDashCounter>>>5;
            if ((input.pressed()&JUMP)!=0) {
                animation=9;
                // MOVE.W #9<<8,anim also zeroes adjacent prev_anim (loc_10A50/loc_13618).
                animationState=new S3kMiniatureAnimation.State(0,animationState.frame(),
                        animationState.timer(),animationState.mappingFrame(),animationState.renderFlips());
                spinDashCounter=Math.min(0x800,spinDashCounter+0x200);
            }
        }
        terrain.followGround(this); return true;
    }
    private void jump() {
        int impulse=underwater()?0x380:0x680;
        xVelocity=signed(xVelocity+(TrigLookupTable.cosHex(angle-0x40)*impulse>>8));
        yVelocity=signed(yVelocity+(TrigLookupTable.sinHex(angle-0x40)*impulse>>8));
        setAirborne(true); setPushing(false); jumping=1; stickToConvex=false;
        // Shared Sonic_Jump/Tails_Jump deliberately write $7/$E even for mini
        // actors (sonic3k.asm:23334-23360,28565-28596). Do not scale these writes.
        setRadii(5,0xB);
        if (rolling()) setRollingJump(true);
        else { setRadii(7,0xE); setRolling(true); animation=2; setCentreNative(centreX(),centreY()-3); }
    }
    private void tickAir(Input input,Terrain terrain,Bounds bounds,int levelFrameCounter) {
        boolean flight=character==Character.TAILS && !rolling() && doubleJumpFlag!=0;
        if (flight) flight(input,bounds,levelFrameCounter);
        else jumpHeight(input);
        if (!rollingJump()) {
            if ((input.held()&LEFT)!=0) {
                setFacingLeft(true); xVelocity=accelerated(xVelocity,-speeds.acceleration()*2,speeds.maxSpeed());
            }
            if ((input.held()&RIGHT)!=0) {
                setFacingLeft(false); xVelocity=accelerated(xVelocity,speeds.acceleration()*2,speeds.maxSpeed());
            }
        }
        // CMP.W #-400 / BLO is UNSIGNED: positive falling velocities skip drag.
        if ((yVelocity&0xFFFF)>=0xFC00) xVelocity=signed(xVelocity-(xVelocity>>5));
        integrate();
        if (!flight) yVelocity=signed(yVelocity+(underwater()?0x10:0x38));
        relaxJumpAngle(); terrain.resolveAir(this);
    }
    private void jumpHeight(Input input) {
        int cap=underwater()?-0x200:-0x400;
        if (jumping!=0) {
            if (yVelocity<cap) {
                if ((input.held()&JUMP)==0) yVelocity=cap;
            } else if (doubleJumpFlag==0 && (input.pressed()&JUMP)!=0) {
                setRollingJump(false);
                if (character==Character.TAILS) {
                    if (rolling()) { int old=yRadius; setRolling(false); setRadii(5,0xB);
                        setCentreNative(centreX(),centreY()+old-0xB); }
                    doubleJumpFlag=1; doubleJumpProperty=240; animation=0x20;
                } else doubleJumpFlag=1; // Insta-shield state; no campaign/glide controller.
            }
        } else if (spinDashFlag==0 && yVelocity< -0xFC0) yVelocity=-0xFC0;
    }
    private void flight(Input input,Bounds bounds,int levelFrameCounter) {
        // sub_133AE (sonic3k.asm:25786-25880), byte timer every odd level frame.
        if ((levelFrameCounter&1)!=0 && doubleJumpProperty!=0) doubleJumpProperty--;
        if (doubleJumpFlag!=1) {
            if (yVelocity< -0x100) doubleJumpFlag=1;
            else { yVelocity=signed(yVelocity-0x20); doubleJumpFlag=(doubleJumpFlag+1)&255;
                if (doubleJumpFlag==0x20) doubleJumpFlag=1; }
        } else {
            if ((input.pressed()&JUMP)!=0 && yVelocity>=-0x100 && doubleJumpProperty!=0)
                doubleJumpFlag=2;
            yVelocity=signed(yVelocity+8);
        }
        if (centreY()<=bounds.cameraMinY()+0x10 && yVelocity<0) yVelocity=0;
    }
    private void relaxJumpAngle() {
        if ((byte)angle<0) angle=(byte)(angle+2)>0?0:(angle+2)&255;
        else angle=Math.max(0,angle-2);
        if (flipAngle==0) return;
        boolean reverse=groundVelocity<0 && (flipType&0x80)==0;
        int next=flipAngle+(reverse?-flipSpeed:flipSpeed);
        if (next<0 || next>255) {
            if (flipsRemaining==0) { flipAngle=0; return; }
            flipsRemaining=(flipsRemaining-1)&255;
        }
        flipAngle=next&255;
    }
    private static int accelerated(int value,int delta,int limit) {
        int next=signed(value+delta);
        if (delta<0 && next<=-limit) return value<=-limit?value:-limit;
        if (delta>0 && next>=limit) return value>=limit?value:limit;
        return next;
    }
    private static int approachZero(int value,int amount) {
        return value>0?Math.max(0,value-amount):Math.min(0,value+amount);
    }
    private static int signed(int value) { return (short)value; }
    private boolean underwater() { return (status&0x40)!=0; }
    private void integrate() { xFixed+=xVelocity<<8; yFixed+=yVelocity<<8; }
    private void wrapY(int mask) { setCentreNative(centreX(),centreY()&mask); }
    private void flag(int mask,boolean enabled) { status=enabled?status|mask:status&~mask; publish(); }
    private void publish() { if (sprite!=null) sprite.synchronizeFromModel(); }
    private void advanceAnimation() {
        if (animator!=null) {
            animationState=animator.tick(this,animationState); animationFrame=animationState.frame();
            nativeRenderFlags=(nativeRenderFlags&0xFC)|(animationState.renderFlips()&3);
        }
    }
    public void setAnimator(S3kMiniatureAnimation animator) { this.animator=Objects.requireNonNull(animator); }
    public boolean bodyVisible() { return bodyVisible; }
    public int mappingFrame() { return animationState.mappingFrame(); }
    public int renderFlips() { return nativeRenderFlags&3; }
    public S3kMiniatureAnimation.State animationState() { return animationState; }

    /**
     * Obj_Sonic2P/Obj_Tails2P entry, once per actual actor SST dispatch, before movement.
     * Matching characters toggle P1 bit3/P2 bit4; init then overwrites that first toggle
     * (sonic3k.asm:21416-21466,25565-25616). True tells the host to install Tails' fixed tail.
     */
    public boolean beginNativeActorPass(int partnerCharacterId) {
        if (partnerCharacterId==character.ordinal()) nativeRenderFlags^=slot==0?8:0x10;
        boolean first=!nativeInitialized;
        if (first) {
            nativeInitialized=true;
            airLeft=30;
            setNativeRenderFlags(character==Character.TAILS?0x84:4);
        } else publish();
        return first;
    }
    public int nativeRenderFlags() { return nativeRenderFlags; }
    public boolean nativeInitialized() { return nativeInitialized; }
    public void setNativeDustWrites(NativeDustWrites writes) { nativeDustWrites=Objects.requireNonNull(writes); }
    public int airLeft() { return airLeft; }
    public void setAirLeft(int value) { airLeft=value&0xFF; publish(); }
    /** BuildSprites publishes the union of both views; skipped body submissions retain their bit. */
    public void refreshNativeVisibility(boolean visible) {
        nativeRenderFlags=(nativeRenderFlags&0x7F)|(visible?0x80:0); publish();
    }
    public void setNativeRenderFlags(int flags) {
        nativeRenderFlags=flags&0xFF;
        animationState=new S3kMiniatureAnimation.State(animationState.previousAnimation(),
                animationState.frame(),animationState.timer(),animationState.mappingFrame(),nativeRenderFlags&3);
        publish();
    }
    /** Both miniature actor routines initialize this word and never change it. */
    public int nativePriorityWord() { return 0x100; }

    public TouchBounds touchBounds() {
        int h=(yRadius-3)&255;
        return new TouchBounds((short)(centreX()-4),(short)(centreY()-h),8,h*2);
    }
    public boolean touchEnabled() { return routine==2 && (objectControl&0xA0)==0; }
    public void launch(int vx,int vy) {
        setXVelocity(vx); setYVelocity(vy); setAirborne(true); setStandingObject(-1);
    }
    public void land(int terrainAngle) {
        angle=terrainAngle&255;
        int oldRadius=yRadius;
        if (rolling()) {
            setCentreNative(centreX(),centreY()+(((angle+0x40)&0x80)!=0?-(oldRadius-0xB):oldRadius-0xB));
        }
        status&=~(2|4|0x10|0x20); jumping=doubleJumpFlag=flipAngle=flipType=flipsRemaining=0;
        setRadii(5,0xB); animation=0; publish();
    }
    /** Ring/shield allocation decisions stay in the session damage owner. */
    public void hurt() { hurt(centreX()); }
    public void hurt(int sourceX) {
        if (routine>=4 || invulnerabilityTimer!=0) return;
        land(angle); routine=4; statusSecondary&=0x8E; setAirborne(true);
        yVelocity=underwater()?-0x200:-0x400;
        xVelocity=(centreX()<sourceX?-1:1)*(underwater()?0x100:0x200);
        groundVelocity=0; animation=0x1A; invulnerabilityTimer=120; publish();
    }
    public void kill() {
        land(angle); statusSecondary=0; routine=6; setAirborne(true);
        yVelocity=-0x700; xVelocity=groundVelocity=0; animation=0x18; publish();
    }
    public void respawn(int x,int y,int top,int lrb) {
        setCentreNative(x,y); setSolidBits(top,lrb); routine=2; objectControl=0;
        xVelocity=yVelocity=groundVelocity=moveLock=spinDashCounter=speedShoesTimer=0;
        status=2; animation=5; resetSpeeds(); publish();
    }
    public S3kMiniatureSprite sprite() {
        if (sprite==null) sprite=S3kMiniatureSprite.create(this);
        sprite.synchronizeFromModel(); return sprite;
    }
    public static S3kCompetitionPlayer recreate(State state,S3kMiniatureAnimation animator) {
        S3kCompetitionPlayer player=recreate(state);player.setAnimator(animator);return player;
    }
    public static S3kCompetitionPlayer recreate(State state) {
        var player=new S3kCompetitionPlayer(state.slot(),state.character(),0,0,state.baseSpeeds());
        player.restore(state); return player;
    }
    public State capture() {
        return new State(slot,character,baseSpeeds,speeds,xFixed,yFixed,xVelocity,yVelocity,groundVelocity,
                status,statusSecondary,routine,objectControl,moveLock,animation,animationFrame,xRadius,yRadius,
                angle,topSolidBit,lrbSolidBit,standingObject,jumping,spinDashFlag,spinDashCounter,
                doubleJumpFlag,doubleJumpProperty,flipAngle,flipType,flipsRemaining,flipSpeed,
                speedShoesTimer,invulnerabilityTimer,stickToConvex,highPriority,animationState,bodyVisible,
                nativeRenderFlags,nativeInitialized,airLeft);
    }
    public void restore(State s) {
        if (s.slot()!=slot || s.character()!=character || !s.baseSpeeds().equals(baseSpeeds))
            throw new IllegalArgumentException("Competition actor identity differs from snapshot");
        speeds=s.speeds(); xFixed=s.xFixed(); yFixed=s.yFixed(); xVelocity=s.xVelocity();
        yVelocity=s.yVelocity(); groundVelocity=s.groundVelocity(); status=s.status(); statusSecondary=s.statusSecondary();
        routine=s.routine(); objectControl=s.objectControl(); moveLock=s.moveLock(); animation=s.animation();
        animationFrame=s.animationFrame(); xRadius=s.xRadius(); yRadius=s.yRadius(); angle=s.angle();
        topSolidBit=s.topSolidBit(); lrbSolidBit=s.lrbSolidBit(); standingObject=s.standingObject();
        jumping=s.jumping(); spinDashFlag=s.spinDashFlag(); spinDashCounter=s.spinDashCounter();
        doubleJumpFlag=s.doubleJumpFlag(); doubleJumpProperty=s.doubleJumpProperty(); flipAngle=s.flipAngle();
        flipType=s.flipType(); flipsRemaining=s.flipsRemaining(); flipSpeed=s.flipSpeed();
        speedShoesTimer=s.speedShoesTimer(); invulnerabilityTimer=s.invulnerabilityTimer();
        stickToConvex=s.stickToConvex(); highPriority=s.highPriority(); animationState=s.animationState(); bodyVisible=s.bodyVisible();
        nativeRenderFlags=s.nativeRenderFlags(); nativeInitialized=s.nativeInitialized(); airLeft=s.airLeft(); publish();
    }
    public int slot(){return slot;} public Character character(){return character;}
    public int centreX(){return xFixed>>16;} public int centreY(){return yFixed>>16;}
    public int xFraction(){return xFixed&65535;} public int yFraction(){return yFixed&65535;}
    public int xVelocity(){return xVelocity;} public int yVelocity(){return yVelocity;}
    public int groundVelocity(){return groundVelocity;} public int status(){return status;}
    public int statusSecondary(){return statusSecondary;} public int routine(){return routine;}
    public int objectControl(){return objectControl;} public int moveLock(){return moveLock;}
    public int animation(){return animation;} public int animationFrame(){return animationFrame;}
    public boolean stickToConvex(){return stickToConvex;}
    public int xRadius(){return xRadius;} public int yRadius(){return yRadius;}
    public int angle(){return angle;} public int topSolidBit(){return topSolidBit;}
    public int lrbSolidBit(){return lrbSolidBit;} public int standingObject(){return standingObject;}
    public boolean airborne(){return (status&2)!=0;} public boolean rolling(){return (status&4)!=0;}
    public boolean rollingJump(){return (status&0x10)!=0;} public boolean pushing(){return (status&0x20)!=0;}
    public boolean facingLeft(){return (status&1)!=0;} public boolean jumping(){return jumping!=0;}
    public boolean dead(){return routine>=6;} public boolean highPriority(){return highPriority;}
    public int flipAngle(){return flipAngle;} public int flipType(){return flipType;}
    public int flipsRemaining(){return flipsRemaining;} public int flipSpeed(){return flipSpeed;}
    public int doubleJumpFlag(){return doubleJumpFlag;} public int doubleJumpProperty(){return doubleJumpProperty;}
    public int spinDashFlag(){return spinDashFlag;} public int spinDashCounter(){return spinDashCounter;}
    public int speedShoesTimer(){return speedShoesTimer;} public int invulnerabilityTimer(){return invulnerabilityTimer;}
    public Speeds speeds(){return speeds;}
    /** Explicit placement resets fractional words; native MOVE.W uses setCentreNative. */
    public void setCentre(int x,int y){xFixed=x<<16;yFixed=y<<16;publish();}
    public void setCentreNative(int x,int y){xFixed=(x<<16)|(xFixed&65535);yFixed=(y<<16)|(yFixed&65535);publish();}
    public void setFractions(int x,int y){xFixed=(xFixed&0xFFFF0000)|(x&65535);yFixed=(yFixed&0xFFFF0000)|(y&65535);publish();}
    public void setXVelocity(int v){xVelocity=signed(v);publish();} public void setYVelocity(int v){yVelocity=signed(v);publish();}
    public void setGroundVelocity(int v){groundVelocity=signed(v);publish();}
    public void setStatus(int v){status=v&255;publish();} public void setStatusSecondary(int v){statusSecondary=v&255;publish();}
    public void setRoutine(int v){routine=v&255;publish();} public void setObjectControl(int v){objectControl=v&255;publish();}
    public void setMoveLock(int v){moveLock=v&65535;publish();} public void setAnimation(int v){animation=v&255;publish();}
    public void setAnimationFrame(int v){animationFrame=v&255;publish();}
    public void setPreviousAnimation(int v){
        animationState=new S3kMiniatureAnimation.State(v&255,animationState.frame(),
                animationState.timer(),animationState.mappingFrame(),animationState.renderFlips());
        publish();
    }
    public void setRadii(int x,int y){xRadius=x&255;yRadius=y&255;publish();}
    public void setAngle(int v){angle=v&255;publish();} public void setTopSolidBit(int v){topSolidBit=v&255;publish();}
    public void setLrbSolidBit(int v){lrbSolidBit=v&255;publish();}
    public void setSolidBits(int top,int lrb){topSolidBit=top&255;lrbSolidBit=lrb&255;publish();}
    public void setStandingObject(int v){standingObject=v;flag(8,v>=0);}
    public void setAirborne(boolean v){flag(2,v);} public void setRolling(boolean v){flag(4,v);}
    public void setRollingJump(boolean v){flag(0x10,v);} public void setRollJump(boolean v){setRollingJump(v);}
    public void setPushing(boolean v){flag(0x20,v);} public void setFacingLeft(boolean v){flag(1,v);}
    public void setJumping(boolean v){jumping=v?1:0;publish();}
    public void setDead(boolean v){if(v)kill();else {routine=2;publish();}}
    public void setHighPriority(boolean v){highPriority=v;publish();}
    public void setDoubleJumpFlag(int v){doubleJumpFlag=v&255;publish();}
    public void setDoubleJumpProperty(int v){doubleJumpProperty=v&255;publish();}
    public void setFlipAngle(int v){flipAngle=v&255;publish();} public void setFlipType(int v){flipType=v&255;publish();}
    public void setFlipsRemaining(int v){flipsRemaining=v&255;publish();} public void setFlipSpeed(int v){flipSpeed=v&255;publish();}
    public void setSpinDashFlag(int v){spinDashFlag=v&255;publish();}
    public void setSpeedShoesTimer(int v){speedShoesTimer=v&255;publish();}
    public void setInvulnerabilityTimer(int v){invulnerabilityTimer=v&255;publish();}
    public void setSpeeds(Speeds v){speeds=Objects.requireNonNull(v);publish();}
    public void setSpeeds(int max,int accel,int decel){setSpeeds(new Speeds(max,accel,decel));}
    public void resetSpeeds(){speeds=baseSpeeds;publish();}
}
