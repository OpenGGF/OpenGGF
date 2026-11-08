package com.openggf.sprites.playable;

import com.openggf.game.*;

/** Package-owned actor transfer and display timers; no identity or world ownership changes. */
final class PlayableNativeActorState {
    private PlayableNativeActorState() { }
    static NativeTeleportActorState captureNativeTeleportState(AbstractPlayableSprite sprite){
                return new NativeTeleportActorState(
                        sprite.getCentreX(),
                        sprite.getCentreY(),
                        (short) sprite.getXSubpixelRaw(),
                        (short) sprite.getYSubpixelRaw(),
                        sprite.xSpeed,
                        sprite.ySpeed,
                        sprite.gSpeed,
                        sprite.xRadius,
                        sprite.yRadius,
                        sprite.priorityBucket,
                        sprite.renderFlagWidthPixels,
                        sprite.mappingFrame,
                        sprite.animationId,
                        sprite.animationFrameIndex,
                        sprite.animationTick,
                        sprite.getDirection(),
                        sprite.air,
                        sprite.rolling,
                        sprite.onObject,
                        sprite.rollingJump,
                        sprite.pushing,
                        sprite.inWater,
                        sprite.waterPhysicsActive,
                        sprite.getLayer(),
                        sprite.preventTailsRespawn,
                        sprite.hurt,
                        sprite.objectRoutineOverride,
                        sprite.angle,
                        sprite.flipAngle,
                        sprite.flipTurned,
                        sprite.controller.getDrowning().getRemainingAir(),
                        sprite.objectControlled,
                        sprite.objectControlAllowsCpu,
                        sprite.objectControlSuppressesMovement,
                        sprite.objectMappingFrameControl,
                        sprite.shield,
                        sprite.shieldType,
                        sprite.speedShoes,
                        sprite.sliding,
                        sprite.flipsRemaining,
                        sprite.flipSpeed,
                        sprite.moveLockTimer,
                        sprite.invulnerableFrames,
                        sprite.invincibleFrames,
                        sprite.currentSpeedShoesRemainingTicks(),
                        sprite.stickToConvex,
                        sprite.spindash,
                        sprite.pinballMode,
                        sprite.pinballSpeedLock,
                        sprite.spindashCounter,
                        sprite.jumping,
                        sprite.interactSlotIndex,
                        sprite.latchedSolidObjectId,
                        sprite.latchedSolidObjectReleased,
                        sprite.latchedSolidObjectBound,
                        sprite.topSolidBit,
                        sprite.lrbSolidBit,
                        sprite.runningMode,
                        sprite.xHistory,
                        sprite.yHistory,
                        sprite.historyPos);
            }
    static void applyNativeTeleportEffects(AbstractPlayableSprite sprite, PowerUpObject shield, PowerUpObject stars){
                sprite.shieldObject = shield;
                sprite.invincibilityObject = stars;
            }
    static void applyNativeTeleportState(AbstractPlayableSprite sprite, NativeTeleportActorState s){
                sprite.xSpeed = s.xSpeed();
                sprite.ySpeed = s.ySpeed();
                sprite.gSpeed = s.inertia();
                sprite.xRadius = s.xRadius();
                sprite.yRadius = s.yRadius();
                sprite.priorityBucket = s.priority();
                sprite.renderFlagWidthPixels = s.displayWidth();
                sprite.mappingFrame = s.mappingFrame();
                sprite.animationId = s.animation();
                sprite.animationFrameIndex = s.animationFrame();
                sprite.animationTick = s.animationDuration();
                sprite.setDirection(s.direction());
                sprite.air = s.air();
                sprite.rolling = s.rolling();
                sprite.onObject = s.onObject();
                sprite.rollingJump = s.rollingJump();
                sprite.pushing = s.pushing();
                sprite.inWater = s.inWater();
                sprite.waterPhysicsActive = s.waterPhysicsActive();
                sprite.setLayer(s.layer());
                sprite.preventTailsRespawn = s.preventRespawn();
                sprite.hurt = s.hurt();
                sprite.objectRoutineOverride = s.routineOverride();
                sprite.angle = s.angle();
                sprite.flipAngle = s.flipAngle();
                sprite.flipTurned = s.flipTurned();
                sprite.objectControlled = s.objectControlled();
                sprite.objectControlAllowsCpu = s.allowsCpu();
                sprite.objectControlSuppressesMovement = s.suppressesMovement();
                sprite.objectMappingFrameControl = s.mappingControl();
                sprite.shield = s.shield();
                sprite.shieldType = s.shieldType();
                sprite.speedShoes = s.shoes();
                sprite.sliding = s.sliding();
                sprite.flipsRemaining = s.flipsRemaining();
                sprite.flipSpeed = s.flipSpeed();
                sprite.moveLockTimer = s.moveLock();
                sprite.invulnerableFrames = s.invulnerability();
                sprite.invincibleFrames = s.invincibility();
                sprite.stickToConvex = s.stickToConvex();
                sprite.spindash = s.spindash();
                sprite.pinballMode = s.pinball();
                sprite.pinballSpeedLock = s.pinballLock();
                sprite.spindashCounter = s.spindashCounter();
                sprite.jumping = s.jumping();
                sprite.interactSlotIndex = s.interact();
                sprite.latchedSolidObjectId = s.latchedId();
                sprite.latchedSolidObjectReleased = s.latchedReleased();
                sprite.latchedSolidObjectBound = s.latchedBound();
                sprite.topSolidBit = s.topSolidBit();
                sprite.lrbSolidBit = s.lrbSolidBit();
                sprite.runningMode = s.groundMode();
                sprite.controller.getDrowning().setRemainingAirFromFixedCountdown(s.airLeft());
                System.arraycopy(s.xHistory(), 0, sprite.xHistory, 0, sprite.xHistory.length);
                System.arraycopy(s.yHistory(), 0, sprite.yHistory, 0, sprite.yHistory.length);
                sprite.historyPos = s.historyIndex();
                // Radius changes do not alter the transferred native centre words.
                com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(sprite, s.x());
                com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(sprite, s.y());
                sprite.setSubpixelRaw(s.xSub(), s.ySub());
                sprite.updateSensorOffsetsFromRadii();
            }
    static void tickInvincibilityDisplayTimer(AbstractPlayableSprite sprite){
                // Obj01_ChkInvin (s2.asm:36291-36296), Obj02_ChkInvinc (s2.asm:39027-39032).
                // A clear bit freezes even a nonzero timer; a set bit with time zero remains set.
                if (Boolean.FALSE.equals(sprite.nativeSecondaryInvincibility())) return;
                if (sprite.invincibleFrames > 0) {
                        sprite.invincibleFrames--;
                        if (sprite.invincibleFrames == 0) {
                                // Obj01_RmvInvin (s2.asm:36304-36305), Obj02_RmvInvin (s2.asm:39040-39041).
                                sprite.writeNativeSecondaryInvincibility(false);
                                if (sprite.invincibilityObject != null) {
                                        sprite.invincibilityObject.destroy();
                                        sprite.invincibilityObject = null;
                                }
                                if (sprite.shieldObject != null) {
                                        sprite.shieldObject.setVisible(!sprite.nativeInvincibilityOr(sprite.superSonic));
                                }
                                sprite.restoreLevelMusicAfterInvincibility();
                        }
                }
            }
    static void tickInvulnerabilityDisplayTimer(AbstractPlayableSprite sprite){
                if (sprite.invulnerabilityDisplayTimerTickedThisFrame) {
                        return;
                }
                sprite.invulnerabilityDisplayTimerTickedThisFrame = true;
                // ROM: invulnerable_time only decrements in Sonic_Display (routine 2).
                // During hurt routine (routine 4), DisplaySprite is called directly,
                // so the timer stays frozen until Sonic lands. Routine 6 also draws
                // directly and leaves this timer frozen throughout death.
                if (sprite.invulnerableFrames > 0 && !sprite.hurt && !sprite.dead) {
                        if (sprite.suppressNextInvulnerabilityDecrement) {
                                sprite.suppressNextInvulnerabilityDecrement = false;
                        } else {
                                sprite.invulnerableFrames--;
                                sprite.invulnerabilityDisplayTimerDecrementedThisFrame = true;
                        }
                }
            }
    static boolean getInvulnerable(AbstractPlayableSprite sprite){
                Boolean raw = sprite.nativeSecondaryInvincibility();
                if (raw != null) {
                        // Touch_ChkHurt2 (s2.asm:29494-29501) reads status, timer and routine independently.
                        return sprite.dead || sprite.debugMode || raw || sprite.invulnerableFrames > 0 || sprite.hurt
                                || com.openggf.game.mutators.HomingAttackRuntime.invulnerable(sprite.mutatorPolicySource, sprite);
                }
                // Dead players cannot enter object-owned hurt/ring-loss paths.
                // Debug mode and Super Sonic also make the player invulnerable
                return sprite.dead || sprite.debugMode || sprite.superSonic || sprite.invulnerableFrames > 0 || sprite.invincibleFrames > 0 || sprite.hurt || com.openggf.game.mutators.HomingAttackRuntime.invulnerable(sprite.mutatorPolicySource, sprite);
            }
    static boolean isDamageBlocked(AbstractPlayableSprite sprite, boolean ignoreIFrames) {
                Boolean raw = sprite.nativeSecondaryInvincibility();
                if (raw != null) {
                        // Touch_ChkHurt (s2.asm:85579-85595); HurtCharacter has no global Super test.
                        return sprite.dead || sprite.debugMode || raw || (!ignoreIFrames && sprite.invulnerableFrames > 0);
                }
                if (sprite.dead || sprite.debugMode || sprite.invincibleFrames > 0 || sprite.isSuperSonic()) {
                        return true;
                }
                // ROM Touch_Hurt (sonic3k.asm:21086-21089, s2.asm Touch_Hurt) gates
                // purely on a NONZERO invulnerability_timer: `tst.b
                // invulnerability_timer(a0); bne.s Touch_ChkHurt_Return`. The timer is
                // decremented earlier in the same object slot by *_Display
                // (Sonic_Display sonic3k.asm:22074-22077, Tails_Display
                // sonic3k.asm:26319-26322), which the engine mirrors via
                // tickInvulnerabilityDisplayTimerBeforeTouchResponse() before
                // applyTouchResponses (SpriteManager.java:1414). So invulnerableFrames at
                // touch time already holds the post-decrement value, and any nonzero
                // value blocks the hit -- matching the ROM `bne` exactly. This applies
                // identically to the CPU sidekick, whose Tails_Display performs the same
                // pre-touch decrement.
                return !ignoreIFrames && sprite.invulnerableFrames > 0;
            }
    static boolean nativeInvincibilityOr(AbstractPlayableSprite sprite, boolean inferred){
                Boolean raw = sprite.nativeSecondaryInvincibility();
                return raw != null ? raw : inferred;
            }
    static void setAir(AbstractPlayableSprite sprite, boolean air, boolean preserveAbilityProperty) {
                boolean changed = air != sprite.air;
                boolean landed = !air && sprite.air;
                // HurtCharacter/HurtStop do not write art_tile. Preserve any priority bit
                // owned by a path switcher or scripted sequence (notably AIZ2's waterfall
                // arena) when the hurt routine lands.
                // (invulnerableFrames already set in applyHurt() per ROM behavior)
                if (!air && sprite.air && sprite.hurt) {
                        sprite.hurt = false;
                        PlayableHurtRecovery.clearObjectControlAfterLanding(sprite);
                        sprite.forcedAnimationId = -1;
                        // HurtStop's direct draw path delays decrementing the reset timer by one frame.
                        sprite.invulnerableFrames = 0x78;
                        sprite.suppressNextInvulnerabilityDecrement = true;
                        sprite.hurtRoutineOwnedDisplayThisFrame = true;
                }
                // Reset rolling jump flag when landing
                if (!air && sprite.air) {
                        sprite.rollingJump = false;
                }
                // Clear jumping flag and double-jump flag when landing
                if (!air && sprite.air) {
                        sprite.jumping = false;
                        // Restore standing radii if coming out of a glide state
                        // (glide uses custom 10x10 radii that won't be restored by
                        // setRolling(false) since rolling was already false)
                        if (sprite.doubleJumpFlag > 0 && !sprite.rolling) {
                                sprite.applyStandingRadii(false);
                                sprite.objectMappingFrameControl = false;
                                sprite.abilityMappingFrameControl = false;
                                sprite.forcedAnimationId = -1;
                        }
                        sprite.doubleJumpFlag = 0;
                        if (!preserveAbilityProperty) sprite.doubleJumpProperty = 0;
                        // S1 ROM parity (Sonic_ResetOnFloor):
                        // move.w #0,(v_itembonus).w ; clear enemy/block score chain.
                        sprite.currentGameState().resetItemBonus();
                }
                sprite.air = air;
                if (landed) {
                        sprite.controller.publishLandingAnimationWrite();
                }
                // SPG: Push sensor Y offset changes based on air state
                sprite.updatePushSensorYOffset();
                if (air) {
                        sprite.setGroundMode(com.openggf.game.GroundMode.GROUND);
                        // SPG: Angle should gradually return to 0 while airborne,
                        // NOT immediately reset. See returnAngleToZero() called during air updates.
                } else {
                        // S2 resets its shared word only at explicit ResetOnFloor owners.
                        // Other hosts retain their existing setter behavior.
                        if (PlayableNativeFloorReset.chain(sprite) == null) sprite.resetBadnikChain();
                        else sprite.badnikChainCounter = 0;
                }
                sprite.publishGroundTransition(changed, landed);
            }
}
