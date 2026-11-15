package com.openggf.level.objects;

import com.openggf.game.internal.ReevaluatingSolidRules;
import com.openggf.game.GroundMode;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The re-evaluating {@code SolidObject} family: Sonic CD's {@code SolidObject},
 * {@code TopSolidObject} and {@code BtmSolidObject} (scddisasm Object Solid.asm:159-479), with
 * {@code GetOffObject} (Object Solid.asm:15-55), {@code StandOnObject} (Object Solid.asm:65-146)
 * and {@code CheckWallCrush} (Object Solid.asm:489-514), {@code PushObject} and
 * {@code StopObjPush} (Object Solid.asm:524-607). Selected by the
 * {@link ReevaluatingSolidRules} game service; the shared S1/S2/S3K routine is not used.
 *
 * <p>Against Sonic 1's {@code SolidObject} (s1disasm _incObj/sub SolidObject.asm:18-385) no
 * path is shared, so nothing of that routine is reused here; the controller's standing, pushing
 * and riding latches are reused as the ROM bits they model:
 * <ul>
 *   <li>Object {@code oFlags} bit 3 = the controller's standing latch; player bit 3 =
 *       {@code isOnObject}; {@code oPlayerStandObj} = the riding record.</li>
 *   <li>Bit 5 on both = the pushing latch and {@code getPushing}; {@code oPlayerPushObj} = the
 *       controller's push target.</li>
 * </ul>
 * The rows below name each difference from Sonic 1:
 * <ol>
 *   <li>No standing path: Sonic 1 keeps a rider with {@code obSolid} and the half-width only
 *       (sub SolidObject.asm:19-55); here every call reruns the box test and re-seats the rider
 *       (Object Solid.asm:202-243, 351-353).</li>
 *   <li>Gates before the box: drowning, invisible, routine &ge; 6, debug and off-screen all take
 *       {@code .GetOff}, which releases a rider (Object Solid.asm:188-200, 473-479); Sonic 1 tests
 *       the render flag first and controls-locked, dying and debug after the box
 *       (sub SolidObject.asm:152-192).</li>
 *   <li>Extents come from the object: {@code d1 = oWidth + 9 + 1}, right edge exclusive
 *       ({@code bcc}); half height {@code oYRadius(a0) + oYRadius(a1) + 2} with no +4 bias
 *       (Object Solid.asm:202-243). Sonic 1 takes {@code d1}-{@code d3} from the caller, right edge
 *       inclusive ({@code bhi}), +4 bias (sub SolidObject.asm:158-179).</li>
 *   <li>Side: depth &le; 4 releases with no correction (Object Solid.asm:275-276; Sonic 1 returns a
 *       side contact, sub SolidObject.asm:214-249). The pushed side sets bit 5 only when moving into
 *       the object; standing still or moving away clears it (Object Solid.asm:288-324). Sonic 1
 *       sets it on every grounded side contact. The side returns 0 (Sonic 1: 1).</li>
 *   <li>Top: no 16-pixel or {@code obActWid} landing window and no extra pixel; the rider sits at
 *       {@code oY - (oYRadius(a0) + oYRadius(a1))} and moves by the object's velocity, with wall,
 *       floor and ceiling probes (Object Solid.asm:344-430). Sonic 1: sub SolidObject.asm:270-335.</li>
 *   <li>Bottom: in the air or under a still or rising object the player is pushed below and stopped;
 *       grounded under a falling object they are killed first (Object Solid.asm:434-471). Sonic 1
 *       kills only a still, grounded player and leaves a falling one in place
 *       (sub SolidObject.asm:278-313).</li>
 *   <li>Landing ({@code StandOnObject}): a hurt player returns to routine 2 with 120 frames of
 *       invulnerability; the roll clear runs on the first landing, airborne or not; the ground
 *       speed is not set by an upward spring (Object Solid.asm:65-146). Sonic 1's
 *       {@code Solid_ResetFloor} calls {@code Sonic_ResetOnFloor} only from the air
 *       (sub SolidObject.asm:353-385).</li>
 *   <li>Crushes: {@code CheckWallCrush} and {@code PushObject} kill a player squeezed against a wall
 *       or a second pushed object (Object Solid.asm:489-581); Sonic 1 has neither.</li>
 * </ol>
 *
 * <p>The three player probes of a moving solid ({@code Player_CalcRoomInFront} and
 * {@code Player_CheckFloor}, Collision Check.asm:14-125; {@code Player_GetCeilDist},
 * Collision Check.asm:298-312) use
 * the shared terrain scans without their angle-buffer writes; no hosted object moves by velocity,
 * so no native reference covers them yet. The charge-stop sound of {@code GetOffObject} and the
 * miniature player's radii (Object Solid.asm:32-35, 88-93) are not hosted.
 */
final class ReevaluatingSolidRoutine {
    private final ObjectSolidContactController latches;

    ReevaluatingSolidRoutine(ObjectSolidContactController latches) {
        this.latches = latches;
    }

    /** {@code SolidObject} (Object Solid.asm:188-479); null is {@code .GetOff}'s d0 = 0. */
    SolidContact resolve(ObjectInstance instance, ReevaluatingSolidObject obj, AbstractPlayableSprite p,
                         ReevaluatingSolidRules rules) {
        ReevaluatingSolidObject.Entry entry = obj.solidEntry();
        // Object Solid.asm:189-200: drowning, invisible (oPlayerCtrl bit 6), routine >= 6,
        // off screen, debug. tst.b oID(a1) is always true for the hosted player.
        if (p.getAnimationId() == rules.drowningAnimation() || p.isHidden() || p.getDead()
                || !obj.solidRenderedOnScreen() || p.isDebugMode()) {
            return getOff(instance, p, rules);
        }
        // Object Solid.asm:202-213: d1 = oWidth + 9 + 1; outside when d0 < 0 or d0 >= 2*d1.
        int d1 = (byte) obj.solidWidth() + rules.widthAllowance();
        int d0 = (short) (p.getCentreX() - instance.getX() + d1);
        if (d0 < 0 || d0 >= 2 * d1) return getOff(instance, p, rules);

        if (p.getAnimationId() == rules.giveUpAnimation()) {
            // Object Solid.asm:215-219: giving up keeps only a stood-on object solid.
            if (!latches.standingLatch(p, instance)) return getOff(instance, p, rules);
        } else if (entry == ReevaluatingSolidObject.Entry.TOP && p.getYSpeed() < 0) {
            return getOff(instance, p, rules);                 // Object Solid.asm:221-226
        }
        // Object Solid.asm:228-243: d3 = oYRadius(a0) + oYRadius(a1) + 2.
        int d3 = (byte) obj.solidYRadius() + (byte) p.getYRadius() + rules.heightAllowance();
        int d2 = (short) (p.getCentreY() - instance.getY() + d3);
        if (d2 < 0 || d2 >= 2 * d3) return getOff(instance, p, rules);

        // Object Solid.asm:245-260: distances to the nearest edges.
        int d4 = d0;
        if (d1 < d0) {
            d0 -= 2 * d1;
            d4 = -d0;
        }
        int d5 = d2;
        if (d3 < d2) {
            d2 -= 2 * d3;
            d5 = -d2;
        }
        if (d5 < d4) return collideVertically(instance, obj, p, rules, entry, d2);   // :263-264

        if (entry == ReevaluatingSolidObject.Entry.TOP) return getOff(instance, p, rules);   // :266-267
        if (obj.solidRole() == ReevaluatingSolidObject.Role.SPRING && p.getAir()) {
            return getOff(instance, p, rules);                 // Object Solid.asm:269-272
        }
        if ((d5 & 0xFF) <= 4) return getOff(instance, p, rules);   // cmpi.b #4,d5; bls (:275-276)
        return collideHorizontally(instance, obj, p, rules, d0);
    }

    /** {@code .CollideHoriz} (Object Solid.asm:280-324): always returns d0 = 0. */
    private SolidContact collideHorizontally(ObjectInstance instance, ReevaluatingSolidObject obj,
                                             AbstractPlayableSprite p, ReevaluatingSolidRules rules, int d0) {
        pushObject(instance, obj, p);
        getOffObject(instance, p, rules);
        NativePositionOps.addXPosPreserveSubpixel(p, -d0);
        short xVel = p.getXSpeed();
        boolean halt = d0 < 0 ? xVel < 0 : xVel > 0;           // .PlayerRight / .PlayerLeft
        if (halt) {
            // .HaltOnX (:303-315).
            checkWallCrush(obj, p);
            boolean grounded = !p.getAir();
            if (grounded) {
                p.setPushing(true);
                latches.setPushLatch(p, instance);
                p.setGSpeed((short) 0);
            }
            p.setXSpeed((short) 0);
            return SolidContact.side(grounded, d0, true);
        }
        // .StopPush (:317-324).
        stopObjPush(instance, p);
        checkWallCrush(obj, p);
        p.setPushing(false);
        latches.clearPushLatch(p, instance);
        return SolidContact.side(false, d0, false);
    }

    /** {@code .CollideVert} (Object Solid.asm:328-471); d2 is negative when the player is below. */
    private SolidContact collideVertically(ObjectInstance instance, ReevaluatingSolidObject obj,
                                           AbstractPlayableSprite p, ReevaluatingSolidRules rules,
                                           ReevaluatingSolidObject.Entry entry, int d2) {
        ReevaluatingSolidObject.Role role = obj.solidRole();
        if (role == ReevaluatingSolidObject.Role.MONITOR && p.getRolling()) {
            return getOff(instance, p, rules);                 // Object Solid.asm:329-332
        }
        int d1 = (byte) obj.solidYRadius() + (byte) p.getYRadius();   // :335-339
        if (d2 >= 0) {
            // .PlayerAbove (:344-349).
            if (p.getAnimationId() != rules.giveUpAnimation() && p.getYSpeed() < 0) {
                return getOff(instance, p, rules);
            }
            // .MoveOnObject (:351-391): re-seat, then carry by the object's velocity.
            NativePositionOps.writeYPosPreserveSubpixel(p, instance.getY() - d1);
            int xVel = (short) obj.solidXVelocity();
            if (xVel != 0) {
                NativePositionOps.addXPos16_16(p, xVel << 8);
                int room = roomInFront(p, xVel > 0);
                if (room < 0) NativePositionOps.addXPosPreserveSubpixel(p, xVel > 0 ? room : -room);
            }
            int yVel = (short) obj.solidYVelocity();
            if (yVel != 0) NativePositionOps.addYPos16_16(p, yVel << 8);
            if (role != ReevaluatingSolidObject.Role.SPRING) {
                if (yVel >= 0) {
                    // .CheckFloor (:396-407): landing on the floor instead releases the rider.
                    int floor = floorDistance(p);
                    if (floor < 0) {
                        NativePositionOps.addYPosPreserveSubpixel(p, floor);
                        return getOff(instance, p, rules);
                    }
                }
                if (yVel < 0 && ceilingDistance(p) < 0) {
                    p.applyCrushDeath();                       // .CheckCeiling (:409-424)
                    return getOff(instance, p, rules);
                }
            }
            standOnObject(instance, obj, p, rules);            // .StandOn (:426-430)
            return SolidContact.STANDING;
        }
        // .PlayerBelow (:434-460).
        if (entry == ReevaluatingSolidObject.Entry.TOP || role == ReevaluatingSolidObject.Role.PASS_UNDER) {
            return getOff(instance, p, rules);
        }
        if (role == ReevaluatingSolidObject.Role.SPRING) {
            if (entry != ReevaluatingSolidObject.Entry.BOTTOM && !obj.solidVerticallyFlipped()) {
                return getOff(instance, p, rules);
            }
        } else if (!p.getAir() && (short) obj.solidYVelocity() > 0) {
            p.applyCrushDeath();                               // .CheckCrushFloor (:449-460)
        }
        // .HitBottom (:462-471).
        NativePositionOps.addYPosPreserveSubpixel(p, -d2);
        p.setYSpeed((short) 0);
        stopObjPush(instance, p);
        getOffObject(instance, p, rules);
        return SolidContact.CEILING;
    }

    /** {@code .GetOff} (Object Solid.asm:473-479). */
    private SolidContact getOff(ObjectInstance instance, AbstractPlayableSprite p, ReevaluatingSolidRules rules) {
        stopObjPush(instance, p);
        getOffObject(instance, p, rules);
        return null;
    }

    /** {@code GetOffObject} (Object Solid.asm:15-55). */
    void getOffObject(ObjectInstance instance, AbstractPlayableSprite p, ReevaluatingSolidRules rules) {
        if (!latches.standingLatch(p, instance) || !p.isOnObject() || latches.standObject(p) != instance) return;
        p.setStickToConvex(false);                             // clr.b oPlayerStick
        p.setAir(true);
        p.setOnObject(false);
        latches.clearStandingLatch(p, instance);
        if (!p.isHidden() && p.getAnimationId() != rules.drowningAnimation() && p.isObjectControlled()) {
            p.setObjectControlled(false);                      // bclr #0,oPlayerCtrl (:42-46)
        }
        latches.clearStandObject(p);
        if (p.getAnimationId() == rules.giveUpAnimation()) p.setAir(false);   // :50-52
    }

    /** {@code StandOnObject} (Object Solid.asm:65-146). */
    private void standOnObject(ObjectInstance instance, ReevaluatingSolidObject obj, AbstractPlayableSprite p,
                               ReevaluatingSolidRules rules) {
        if (p.isHurt()) {
            p.setHurt(false);                                  // subq.b #2,oRoutine (:66-69)
            p.setInvulnerableFrames(rules.hurtLandingInvulnerability());
        }
        p.setJumping(false);                                   // clr.b oPlayerJump
        if (!latches.standingLatch(p, instance)) {
            latches.setStandingLatch(p, instance);
            if (p.getAnimationId() == rules.giveUpAnimation()) {
                latches.clearStandingLatch(p, instance);       // :78-81
                getOffObject(instance, p, rules);
                return;
            }
            p.setRollingJump(false);                           // .NotGivingUp (:84-101)
            if (p.getRolling()) {
                int centreX = p.getCentreX();
                int centreY = p.getCentreY();
                p.setRolling(false);
                // ROM: only oYRadius/oXRadius and oY change (subq.w #5,oY(a1), :95-98); the sprite's wall-mode
                // width swap in setRolling would move the centre X by half the height difference.
                NativePositionOps.addXPosPreserveSubpixel(p, centreX - p.getCentreX());
                NativePositionOps.writeYPosPreserveSubpixel(p, centreY - rules.rollLandingRise(p));
                p.setAnimationId(rules.walkAnimation());
            }
        }
        // .StandOn (:103-118).
        boolean wasOnObject = p.isOnObject();
        p.setOnObject(true);
        if (wasOnObject) {
            ObjectInstance previous = latches.standObject(p);
            if (previous == instance) return;
            if (previous != null) latches.clearStandingLatch(p, previous);
        }
        // .SetInteract (:120-143).
        latches.setStandObject(p, instance);
        p.setAngle((byte) 0);
        p.setGroundMode(GroundMode.GROUND);
        p.setYSpeed((short) 0);
        if (!(obj.solidRole() == ReevaluatingSolidObject.Role.SPRING && obj.solidSpringFacesUp())) {
            p.setGSpeed(p.getXSpeed());
        }
        p.setAir(false);
    }

    /** {@code PushObject} (Object Solid.asm:524-581). */
    private void pushObject(ObjectInstance instance, ReevaluatingSolidObject obj, AbstractPlayableSprite p) {
        if (obj.solidRole() == ReevaluatingSolidObject.Role.SPRING) {
            latches.clearPushTarget(p);
            return;
        }
        ObjectInstance other = latches.pushTarget(p);
        if (other == null) {
            latches.setPushTarget(p, instance);                // .SetPushObj
            return;
        }
        if (other == instance) return;
        int otherXVel = other instanceof ReevaluatingSolidObject solid ? (short) solid.solidXVelocity() : 0;
        if ((short) obj.solidXVelocity() == 0 && otherXVel == 0) return;
        int px = p.getCentreX() & 0xFFFF;
        boolean crush = px >= (instance.getX() & 0xFFFF)
                ? px < (other.getX() & 0xFFFF)                 // .CheckRight
                : px >= (other.getX() & 0xFFFF);               // .CheckLeft
        if (crush && obj.solidRole() != ReevaluatingSolidObject.Role.CRUSH_EXEMPT) p.applyCrushDeath();
    }

    /** {@code StopObjPush} (Object Solid.asm:591-607). */
    private void stopObjPush(ObjectInstance instance, AbstractPlayableSprite p) {
        if (latches.pushTarget(p) == instance) latches.clearPushTarget(p);
    }

    /** {@code CheckWallCrush} (Object Solid.asm:489-514). */
    private void checkWallCrush(ReevaluatingSolidObject obj, AbstractPlayableSprite p) {
        int xVel = (short) obj.solidXVelocity();
        if (xVel == 0 || obj.solidRole() == ReevaluatingSolidObject.Role.SPRING) return;
        if (roomInFront(p, xVel > 0) < 0) p.applyCrushDeath();
    }

    /**
     * {@code Player_CalcRoomInFront} with angle $C0 (right) or $40 (left): the wall probe at the
     * player's next position, 8 pixels down (Collision Check.asm:14-61).
     */
    private static int roomInFront(AbstractPlayableSprite p, boolean right) {
        int x = (((p.getCentreX() << 16) | p.getXSubpixelRaw()) + (p.getXSpeed() << 8)) >> 16;
        int y = ((((p.getCentreY() << 16) | p.getYSubpixelRaw()) + (p.getYSpeed() << 8)) >> 16) + 8;
        TerrainCheckResult wall = right ? ObjectTerrainUtils.checkRightWallDist(x + 10, y)
                : ObjectTerrainUtils.checkLeftWallDist(x - 10, y);
        return wall.hasCollision() ? wall.distance() : 0;
    }

    /** {@code Player_CheckFloor}: both feet, the nearer result (Collision Check.asm:79-125). */
    private static int floorDistance(AbstractPlayableSprite p) {
        int y = p.getCentreY() + p.getYRadius();
        TerrainCheckResult right = ObjectTerrainUtils.checkPlayerFloorDistWithFlipAwareAngle(
                p.getCentreX() + p.getXRadius(), y, p.getTopSolidBit(), false);
        TerrainCheckResult left = ObjectTerrainUtils.checkPlayerFloorDistWithFlipAwareAngle(
                p.getCentreX() - p.getXRadius(), y, p.getTopSolidBit(), false);
        int r = right.hasCollision() ? right.distance() : 0;
        int l = left.hasCollision() ? left.distance() : 0;
        return Math.min(r, l);
    }

    /**
     * {@code Player_GetCeilDist}: one probe at the player's own X, {@code $A} pixels above its
     * centre ({@code subi.w #$A,d2}), with neither radius (Collision Check.asm:298-312). It is
     * not {@code Player_CheckCeiling}'s two-foot probe at {@code oY - oYRadius}
     * (Collision Check.asm:259-294).
     */
    private static int ceilingDistance(AbstractPlayableSprite p) {
        TerrainCheckResult ceiling = ObjectTerrainUtils.checkPlayerCeilingDistWithFlipAwareAngle(
                p.getCentreX(), p.getCentreY(), CEILING_PROBE_RISE, p.getLrbSolidBit(), false);
        return ceiling.hasCollision() ? ceiling.distance() : 0;
    }

    /** {@code Player_GetCeilDist_Part2}: {@code subi.w #$A,d2} (Collision Check.asm:305-306). */
    private static final int CEILING_PROBE_RISE = 0x0A;
}
