package com.openggf.sprites.playable;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameStateManager;
import com.openggf.audio.GameSound;
import com.openggf.sprites.NativePositionOps;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.FrameCollisionPlan;
import com.openggf.game.rules.GameRules;

import java.util.Objects;

/** ROM-accurate shared owner for manual and scripted Tails carry state. */
public final class TailsCarryController {
    public enum CarryContext { NONE, MANUAL, CNZ, MGZ_BOSS }

    public record Snapshot(short latchX, short latchY, boolean carrying,
                           boolean parentagePending, int cooldown, CarryContext context) { }

    private final AbstractPlayableSprite carrier;
    private short latchX;
    private short latchY;
    private boolean carrying;
    private boolean parentagePending;
    private int cooldown;
    private CarryContext context = CarryContext.NONE;

    public TailsCarryController(AbstractPlayableSprite carrier) {
        this.carrier = Objects.requireNonNull(carrier, "carrier");
    }

    public boolean isCarryingMainCharacter() { return carrying; }

    public boolean tryGrabMainCharacter() {
        AbstractPlayableSprite main = resolveMain();
        GameRules rules = carrier.getGameRules();
        SidekickCpuController cpu = carrier.getCpuController();
        boolean playerTwoOwnsCarrier = carrier.isCpuControlled()
                && cpu != null && cpu.isUnderManualControl();
        if (carrier.getSecondaryAbility() != SecondaryAbility.FLY
                || rules == null || rules.playerCapability() == null
                || !rules.playerCapability().tailsFlightEnabled()
                || carrier.getTailsFlightController() == null
                || !carrier.getTailsFlightController().isActive()
                || !playerTwoOwnsCarrier
                || main == null || main == carrier || carrying || cooldown != 0 || !isInContactWindow(main)
                || main.isObjectControlled() || main.isHurt() || main.getDead()
                || main.isDebugMode() || main.getSpindash()) {
            return false;
        }
        attach(main, CarryContext.MANUAL, true);
        return true;
    }

    private boolean isInContactWindow(AbstractPlayableSprite main) {
        int xWindow = (main.getCentreX() - carrier.getCentreX() + 0x10) & 0xFFFF;
        int yWindow = main.getCentreY() - carrier.getCentreY() - 0x20;
        if (isReverseGravity()) yWindow += 0x50;
        return xWindow < 0x20 && (yWindow & 0xFFFF) < 0x10;
    }

    private boolean isReverseGravity() {
        GameStateManager gameState = carrier.currentGameStateOrNull();
        return gameState != null && gameState.isReverseGravityActive();
    }

    private void attach(AbstractPlayableSprite main, CarryContext newContext, boolean playSfx) {
        main.setXSpeed((short) 0);
        main.setYSpeed((short) 0);
        main.setGSpeed((short) 0);
        main.setAngle((byte) 0);
        NativePositionOps.writeXPosPreserveSubpixel(main, carrier.getCentreX());
        int yOffset = isReverseGravity() ? -0x1C : 0x1C;
        NativePositionOps.writeYPosPreserveSubpixel(main, carrier.getCentreY() + yOffset);
        main.setDirection(carrier.getDirection());
        main.setForcedAnimationId(main.resolveAnimationId(CanonicalAnimation.TAILS_CARRIED));
        ObjectControlState.nativeBit7FullControl().applyTo(main);
        main.setAir(true);
        main.setRollingJump(false);
        main.setSpindash(false);
        main.setSpindashCounter((short) 0);
        main.setJumping(false);
        main.setXSpeed(carrier.getXSpeed());
        main.setYSpeed(carrier.getYSpeed());
        latchX = main.getXSpeed();
        latchY = main.getYSpeed();
        carrying = true;
        parentagePending = true;
        cooldown = 0;
        context = newContext;
        if (playSfx && carrier.currentAudioManager() != null) {
            carrier.currentAudioManager().playSfx(GameSound.GRAB);
        }
    }

    public void updateAfterTailsCollision(int mainHeldInput) {
        AbstractPlayableSprite main = resolveMain();
        if (!carrying) {
            if (cooldown > 0) cooldown--;
            if (cooldown == 0) tryGrabMainCharacter();
            return;
        }
        if (main == null) {
            if (parentagePending) {
                return;
            }
            release(0x3C);
            return;
        }
        if (carrier.isHurt() || carrier.getDead() || carrier.isObjectControlled()
                || main.isHurt() || main.getDead()
                || !main.isObjectControlled() || main.isObjectControlAllowsCpu()) {
            release(0x3C);
            return;
        }
        if (!main.getAir()) {
            carrier.getTailsFlightController().clear();
            main.setYSpeed((short) -0x0100);
            release(0);
            return;
        }
        if (main.getXSpeed() != latchX || main.getYSpeed() != latchY) {
            release(0x3C);
            return;
        }
        if ((mainHeldInput & AbstractPlayableSprite.INPUT_JUMP) != 0) {
            performJumpRelease(main, mainHeldInput);
            return;
        }
        followAndCollide(main);
    }

    private void followAndCollide(AbstractPlayableSprite main) {
        NativePositionOps.writeXPosPreserveSubpixel(main, carrier.getCentreX());
        int yOffset = isReverseGravity() ? -0x1C : 0x1C;
        NativePositionOps.writeYPosPreserveSubpixel(main, carrier.getCentreY() + yOffset);
        main.setDirection(carrier.getDirection());
        main.setXSpeed(carrier.getXSpeed());
        main.setYSpeed(carrier.getYSpeed());
        CollisionSystem collision = main.currentCollisionSystemOrNull();
        if (collision != null) {
            collision.resolveAirCollision(FrameCollisionPlan.terrainOnly(), main, sprite -> {
                if (sprite.getRolling()) {
                    int radiusDelta = sprite.getYRadius() - sprite.getStandYRadius();
                    sprite.setRollingFlagPreserveRadii(false);
                    NativePositionOps.writeYPosPreserveSubpixel(sprite, sprite.getCentreY() + radiusDelta);
                }
                sprite.applyStandingRadii(false);
                sprite.setYSpeed((short) 0);
                sprite.setGSpeed(sprite.getXSpeed());
                sprite.setAir(false);
                sprite.setPushing(false);
                sprite.setRollingJump(false);
                sprite.setJumping(false);
            });
        }
        latchX = main.getXSpeed();
        latchY = main.getYSpeed();
        parentagePending = false;
    }

    private void performJumpRelease(AbstractPlayableSprite main, int input) {
        boolean left = (input & AbstractPlayableSprite.INPUT_LEFT) != 0;
        boolean right = (input & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        if (left) main.setXSpeed((short) -0x0200);
        if (right) main.setXSpeed((short) 0x0200);
        main.setYSpeed((short) -0x0380);
        main.setAir(true);
        main.setJumping(true);
        main.applyRollingRadii(false);
        main.setRollingFlagPreserveRadii(true);
        main.setRollingJump(false);
        main.setAnimationId(2);
        release(left || right || (input & (AbstractPlayableSprite.INPUT_UP | AbstractPlayableSprite.INPUT_DOWN)) != 0
                ? 0x3C : 0x12);
    }
    public void forceScriptedCarry(CarryContext context) {
        Objects.requireNonNull(context, "context");
        if (context == CarryContext.NONE || context == CarryContext.MANUAL) {
            throw new IllegalArgumentException("Scripted carry requires CNZ or MGZ_BOSS context");
        }
        AbstractPlayableSprite main = resolveMain();
        if (main == null) {
            return;
        }
        attach(main, context, false);
    }
    public void clearAndReleaseMain() {
        release(0x3C);
    }

    void releaseWithCooldown(int releaseCooldown) {
        release(releaseCooldown);
    }

    private void release(int releaseCooldown) {
        AbstractPlayableSprite main = resolveMain();
        if (main != null && main != carrier) {
            ObjectControlState.none().applyTo(main);
            main.setControlLocked(false);
            main.setForcedAnimationId(-1);
        }
        carrying = false;
        parentagePending = false;
        cooldown = releaseCooldown;
        context = CarryContext.NONE;
    }

    private AbstractPlayableSprite resolveMain() {
        var sprites = carrier.currentSpriteManagerOrNull();
        AbstractPlayableSprite main = sprites != null ? sprites.getMainPlayable() : null;
        return main == carrier ? null : main;
    }

    public Snapshot capture() {
        return new Snapshot(latchX, latchY, carrying, parentagePending, cooldown, context);
    }

    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        latchX = snapshot.latchX();
        latchY = snapshot.latchY();
        carrying = snapshot.carrying();
        parentagePending = snapshot.parentagePending();
        cooldown = snapshot.cooldown();
        context = snapshot.context();
    }

    void setParentagePending(boolean value) { parentagePending = value; }
    void setCarrying(boolean value) { carrying = value; }
    void setCooldown(int value) { cooldown = value; }
    int cooldown() { return cooldown; }
    int decrementCooldown() { if (cooldown > 0) cooldown--; return cooldown; }
    boolean parentagePending() { return parentagePending; }
    boolean velocityLatchMatches(AbstractPlayableSprite main) {
        return main != null && main.getXSpeed() == latchX && main.getYSpeed() == latchY;
    }
    void latchVelocity(AbstractPlayableSprite main) {
        latchX = main.getXSpeed();
        latchY = main.getYSpeed();
    }
    void clearState() {
        latchX = 0;
        latchY = 0;
        carrying = false;
        parentagePending = false;
        cooldown = 0;
        context = CarryContext.NONE;
    }
}
