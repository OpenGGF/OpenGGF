package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * Object 0x47 - CNZ Cylinder ({@code Obj_CNZCylinder}).
 *
 * <p>This class ports the ROM motion families from {@code sub_321E2} and the
 * rider-control seam from {@code sub_324C0}.</p>
 */
public final class CnzCylinderInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(0x2B, 0x20, 0x21);
    private static final int PLAYER_CAPTURE_PRIORITY = RenderPriority.PLAYER_DEFAULT;
    private static final int PLAYER_TWIST_PRIORITY = RenderPriority.PLAYER_DEFAULT - 1;
    private static final int PRIORITY_THRESHOLD_SOURCE = 0x60;
    private static final int RELEASE_Y_SPEED = -0x680;
    private static final int[] MODE0_SPEED_CAPS = {
            0x04E0, 0x06F0, 0x0870, 0x09C0, 0x0AE0, 0x0C00, 0x0CF0, 0x0DE0
    };
    private static final int[] PLAYER_TWIST_FRAMES = {
            0x55, 0x59, 0x5A, 0x5B, 0x5A, 0x59, 0x55, 0x56, 0x57, 0x58, 0x57, 0x56
    };
    private static final boolean[] PLAYER_TWIST_FLIPS = {
            false, true, true, false, false, false, true, true, true, false, false, false
    };

    private static final int CIRCULAR_HALF_EXTENT = 0x20;

    private static final class RiderSlot {
        private boolean active;
        private boolean contactLatched;
        private int twistAngle;
        private int horizontalDistance;
        private int priorityThresholdSource;
        private AbstractPlayableSprite player;
        private boolean jumpPressedLastFrame;
    }

    private int baseX;
    private int baseY;
    private int motionSelector;
    private int speedCap;
    private boolean circularRoute;
    private int angleStep;
    private final RiderSlot playerOneSlot = new RiderSlot();
    private final RiderSlot playerTwoSlot = new RiderSlot();
    private AbstractPlayableSprite releasedJumpSolidSkipPlayer;

    private int routeQuadrant;
    private int centerX;
    private int centerY;
    private int standingMaskCache;
    private int standingMask;
    private int nextStandingMask;
    private int heldInputMask;
    private int nextHeldInputMask;
    private int mode0Velocity;
    private int mode0YSubpixel;
    private int currentYVelocity;
    private int angle;
    private int mappingFrame;
    private int animFrameTimer = 0;
    private String playerTwoDiagnosticBranch = "none";
    private int playerTwoPreX = -1;
    private int playerTwoPreXSubpixel = -1;
    private int playerTwoPostX = -1;
    private int playerTwoPostXSubpixel = -1;
    private int playerTwoPreStatus = -1;
    private int playerTwoPostStatus = -1;
    private int playerTwoPreObjectControl = -1;
    private int playerTwoPostObjectControl = -1;

    public CnzCylinderInstance(ObjectSpawn spawn) {
        super(spawn, "CNZCylinder");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.centerX = spawn.x();
        this.centerY = spawn.y();
        int subtype = spawn.subtype() & 0xFF;
        this.motionSelector = (subtype << 1) & 0x1E;
        this.speedCap = MODE0_SPEED_CAPS[((subtype >>> 3) & 0x0E) >>> 1];
        this.circularRoute = motionSelector >= 0x12;
        this.routeQuadrant = ((subtype & 0x0F) - 0x0A) & 0x03;
        int step = (subtype & 0xF0) << 2;
        if ((spawn.renderFlags() & 0x01) != 0) {
            step = -step;
        }
        this.angleStep = step;
        this.mode0Velocity = 0;
        this.mode0YSubpixel = 0;
        this.currentYVelocity = 0;
        // Obj_CNZCylinder init falls through directly into loc_32188, so the
        // first sub_321E2 motion pass has already happened by the first full
        // engine update after spawn.
        updateMotion();
        updateDynamicSpawn(centerX, centerY);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Self-contained: all state (including the {@code final} geometry fields) is
     * derived deterministically from the captured spawn, so re-running the constructor
     * reproduces it exactly. Mutable scalar fields are reapplied by the standard
     * scalar-restore pass after recreate. Replaces the former explicit dynamic restore path
     * (Phase-2 codec-deletion batch 2).
     */

    @Override
    public boolean isSkipSolidContactThisFrame() {
        // ROM: Obj_CNZCylinder falls through from init into loc_32188 and calls
        // SolidObjectFull immediately; it does not guard the first frame on obRender bit 7.
        return false;
    }

    @Override
    public void snapshotPreUpdatePosition() {
        super.snapshotPreUpdatePosition();
        releasedJumpSolidSkipPlayer = null;
    }

    @Override
    public int getOutOfRangeReferenceX() {
        // ROM loc_32188 calls Sprite_OnScreen_Test2 with $2E(a0), the saved
        // placement X, after the cylinder has moved away from its current x_pos.
        return baseX;
    }

    @Override
    public int getOnScreenHalfWidth() {
        // ROM Obj_CNZCylinder init writes width_pixels=$20 before loc_32188's
        // SolidObjectFull pass (sonic3k.asm:67634-67641, 67656-67672).
        return 0x20;
    }

    @Override
    public int getOnScreenHalfHeight() {
        // ROM Obj_CNZCylinder init writes height_pixels=$20 before loc_32188's
        // SolidObjectFull pass (sonic3k.asm:67634-67641, 67656-67672).
        return 0x20;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        // ROM sub_324C0 / SolidObjectFull (sonic3k.asm:41006-41008): when a
        // rider is offscreen (`tst.b render_flags(a1); bpl.w locret_1DCB4`)
        // the entire SolidObjectFull pass for that rider is skipped, so the
        // cylinder's per-rider standing bit is NOT cleared. The engine's
        // SolidContacts pass also skips offscreen objects (line 4444 gate),
        // so `nextStandingMask` will be 0 even though the rider is still
        // logically anchored to the cylinder. Preserve the previous frame's
        // bits for any rider whose render_flags bit 7 is currently clear so
        // the alternating capture/release cycle in updateRiderSlot can
        // continue to re-capture the offscreen rider each frame.
        //
        int preservedStanding = 0;
        if (playerOneSlot.player != null
                && !riderRenderFlagOnScreen(playerOneSlot.player)) {
            preservedStanding |= (standingMask & 0x01);
        }
        if (playerTwoSlot.player != null
                && !riderRenderFlagOnScreen(playerTwoSlot.player)) {
            preservedStanding |= (standingMask & 0x02);
        }
        preservedStanding |= activeGroundedHeldStandingMask();

        standingMask = nextStandingMask | preservedStanding;
        heldInputMask = nextHeldInputMask;
        nextStandingMask = 0;
        nextHeldInputMask = 0;

        primeDefaultRiderSlots(playerEntity);
        int previousCenterY = centerY;
        updateMotion();
        currentYVelocity = motionSelector == 0 ? mode0Velocity : ((centerY - previousCenterY) << 8);
        updateRiderSlots(frameCounter);
        advanceAnimation();
    }

    private static boolean riderRenderFlagOnScreen(AbstractPlayableSprite rider) {
        return !rider.hasRenderFlagOnScreenState() || rider.isRenderFlagOnScreen();
    }

    private int activeGroundedHeldStandingMask() {
        // ROM loc_32208 compares status(a0)&standing_mask with $3C(a0)
        // before sub_324C0 and SolidObjectFull run (sonic3k.asm:67709-67718,
        // 67656-67672). While sub_324C0 holds a grounded rider with
        // object_control=$03, that cylinder status bit remains a continuous
        // standing bit; a missing engine-side solid callback must not create a
        // false 0->standing transition and reapply loc_32208's +$400 boost.
        return activeGroundedHeldStandingMask(playerOneSlot, 0x01)
                | activeGroundedHeldStandingMask(playerTwoSlot, 0x02);
    }

    private int activeGroundedHeldStandingMask(RiderSlot slot, int mask) {
        AbstractPlayableSprite player = slot.player;
        if (!slot.active
                || player == null
                || !player.isObjectControlled()
                || player.getAir()) {
            return 0;
        }
        return standingMask & mask;
    }

    private void updateMotion() {
        if (circularRoute) {
            updateCircularRoute();
        } else {
            switch (motionSelector) {
                case 0x00 -> updateMode0VerticalController();
                case 0x02 -> updateHorizontalShift(3);
                case 0x04 -> updateHorizontalShift(2);
                case 0x06 -> updateHorizontalThreeEighths();
                case 0x08 -> updateHorizontalShift(1);
                case 0x0A -> updateVerticalShift(3);
                case 0x0C -> updateVerticalShift(2);
                case 0x0E -> updateVerticalThreeEighths();
                case 0x10 -> updateVerticalShift(1);
                default -> updateMode0VerticalController();
            }
        }

        updateDynamicSpawn(centerX, centerY);
    }

    private void updateMode0VerticalController() {
        int standingMask = currentStandingMask();
        if (standingMask != standingMaskCache) {
            int delta = standingMask - standingMaskCache;
            standingMaskCache = standingMask;
            // ROM sonic3k.asm:67725-67729 (loc_32208): only apply the +0x400
            // player-landing boost when the cylinder is within ±0x40 pixels of
            // its base. The ROM check is `addi.w #$40, d0; cmpi.w #$80, d0; bhs`
            // i.e. the boost only fires when (centerY - baseY) is in [-0x40, 0x40).
            // Without this gate the engine over-injects downward velocity when
            // Tails lands during the cylinder's return upswing, which was the
            // root cause of CNZ trace F1685 tails_y_speed mismatch (the cylinder's
            // upward velocity at jump-release was 0x22F too low).
            // Applied BEFORE moveMode0Sprite2() to match ROM ordering at loc_32254.
            int preMoveOffset = (centerY - baseY) + 0x40;
            boolean withinBoostBand = preMoveOffset >= 0 && preMoveOffset < 0x80;
            if (delta > 0 && Math.abs(mode0Velocity) < 0x200 && withinBoostBand) {
                mode0Velocity += 0x400;
                if (mode0Velocity > speedCap) {
                    mode0Velocity = speedCap;
                }
            }
        }

        centerX = baseX;
        moveMode0Sprite2();

        int offset = centerY - baseY;
        if (offset < 0) {
            if (mode0Velocity < speedCap) {
                mode0Velocity += 0x20;
                if (mode0Velocity < 0) {
                    mode0Velocity += 0x10;
                } else if ((collectHeldInputMask() & AbstractPlayableSprite.INPUT_DOWN) != 0) {
                    mode0Velocity += 0x20;
                }
            }
            return;
        }

        if (offset > 0) {
            int negativeCap = -speedCap;
            if (mode0Velocity > negativeCap) {
                mode0Velocity -= 0x20;
                // ROM loc_322AC/loc_322D2 uses BPL after subtracting $20, so
                // zero is treated as non-negative and takes the fixed -$10
                // deceleration instead of the UP-held -$20 branch
                // (docs/skdisasm/sonic3k.asm:67772-67782).
                if (mode0Velocity >= 0) {
                    mode0Velocity -= 0x10;
                } else if ((collectHeldInputMask() & AbstractPlayableSprite.INPUT_UP) != 0) {
                    mode0Velocity -= 0x20;
                }
            }
            return;
        }

        if (Math.abs(mode0Velocity) < 0x80) {
            mode0Velocity = 0;
        }
    }

    private void moveMode0Sprite2() {
        // ROM loc_32254 calls MoveSprite2, so y_pos carries the low byte of
        // y_vel between frames even though the visible object centre is a word.
        int yTotal = (mode0YSubpixel & 0xFF) + (mode0Velocity & 0xFF);
        centerY += (mode0Velocity >> 8) + (yTotal >> 8);
        mode0YSubpixel = yTotal & 0xFF;
    }

    private void updateHorizontalShift(int shift) {
        int sine = TrigLookupTable.sinHex((angle >> 8) & 0xFF);
        centerX = baseX + (sine >> shift);
        centerY = baseY;
        angle = (angle + angleStep) & 0xFFFF;
    }

    private void updateHorizontalThreeEighths() {
        int sine = TrigLookupTable.sinHex((angle >> 8) & 0xFF);
        int shifted = sine >> 2;
        centerX = baseX + shifted + (shifted >> 1);
        centerY = baseY;
        angle = (angle + angleStep) & 0xFFFF;
    }

    private void updateVerticalShift(int shift) {
        int sine = TrigLookupTable.sinHex((angle >> 8) & 0xFF);
        centerX = baseX;
        centerY = baseY + (sine >> shift);
        angle = (angle + angleStep) & 0xFFFF;
    }

    private void updateVerticalThreeEighths() {
        int sine = TrigLookupTable.sinHex((angle >> 8) & 0xFF);
        int shifted = sine >> 2;
        centerX = baseX;
        centerY = baseY + shifted + (shifted >> 1);
        angle = (angle + angleStep) & 0xFFFF;
    }

    private void updateCircularRoute() {
        angle = (angle + angleStep) & 0xFFFF;
        int angleByte = (angle >> 8) & 0xFF;

        if (angleStep < 0) {
            if (angleByte < 0x80) {
                angleByte = (angleByte & 0x7F) + 0x80;
                angle = (angle & 0x00FF) | (angleByte << 8);
                routeQuadrant = (routeQuadrant - 1) & 0x03;
            }
        } else if (angleStep > 0) {
            if (angleByte < 0x80) {
                angleByte = (angleByte & 0x7F) + 0x80;
                angle = (angle & 0x00FF) | (angleByte << 8);
                routeQuadrant = (routeQuadrant + 1) & 0x03;
            }
        }

        int varying = TrigLookupTable.cosHex(angleByte) >> 3;
        switch (routeQuadrant & 0x03) {
            case 0 -> {
                centerX = baseX + varying;
                centerY = baseY - CIRCULAR_HALF_EXTENT;
            }
            case 1 -> {
                centerX = baseX + CIRCULAR_HALF_EXTENT;
                centerY = baseY + varying;
            }
            case 2 -> {
                centerX = baseX - varying;
                centerY = baseY + CIRCULAR_HALF_EXTENT;
            }
            default -> {
                centerX = baseX - CIRCULAR_HALF_EXTENT;
                centerY = baseY - varying;
            }
        }
    }

    private int currentStandingMask() {
        return standingMask;
    }

    private int collectHeldInputMask() {
        // ROM loc_32254 reads Ctrl_1_held_logical / Ctrl_2_held_logical after
        // MoveSprite2 using the cylinder's current standing bits; it does not
        // reuse a held-input byte latched by the prior SolidObjectFull pass
        // (sonic3k.asm:67736-67752, 67772-67782). The engine still latches
        // standing feedback across the split object/solid phases, but the
        // mode-0 acceleration must see this frame's UP/DOWN transition.
        int mask = 0;
        boolean foundLiveStandingRider = false;
        if ((standingMask & 0x01) != 0 && playerOneSlot.player != null) {
            foundLiveStandingRider = true;
            mask |= heldInputMaskFor(playerOneSlot.player);
        }
        if ((standingMask & 0x02) != 0 && playerTwoSlot.player != null) {
            foundLiveStandingRider = true;
            mask |= heldInputMaskFor(playerTwoSlot.player);
        }
        return foundLiveStandingRider ? mask : heldInputMask;
    }

    private void updateRiderSlots(int frameCounter) {
        updateRiderSlot(playerOneSlot, frameCounter);
        updateRiderSlot(playerTwoSlot, frameCounter);
    }

    private void updateRiderSlot(RiderSlot slot, int frameCounter) {
        AbstractPlayableSprite player = slot.player;
        if (player == null || player.getDead() || player.isHurt()) {
            if (slot.active) {
                beginPlayerTwoDiagnostic(slot, "release_invalid", player);
                releaseSlot(slot, frameCounter, false);
                endPlayerTwoDiagnostic(slot, player);
            }
            slot.contactLatched = false;
            return;
        }

        boolean latchedContact = slot.contactLatched;
        slot.contactLatched = false;

        // ROM sub_324C0 loc_32538 (sonic3k.asm:68019-68022): when the captured
        // rider is offscreen (`tst.b render_flags(a1); bpl.w loc_325F2`), the
        // cylinder takes the release branch every frame. ROM SolidObjectFull
        // (sonic3k.asm:41006-41008) ALSO skips Player_2 when his render_flags
        // bit 7 is clear, so the cylinder's p2_standing_bit stays set from the
        // last on-screen frame. The next frame's sub_324C0 (a2)==0 path then
        // re-captures from that preserved standing bit, producing the
        // alternating Status_InAir 0/1 pattern observed at CNZ1 F4489+ when
        // Tails (X=0x1BB9) is just past the right edge of the screen.
        boolean playerOnScreen = !player.hasRenderFlagOnScreenState()
                || player.isRenderFlagOnScreen();

        boolean standing = latchedContact || hasStandingBit(player);
        if (slot.active) {
            if (player.getAir() && !player.isObjectControlled()) {
                // External launchers can preempt the cylinder hold before this
                // object's pass. CNZ balloon sub_317AE writes y_vel=-$700,
                // sets Status_InAir, and clears object_control (sonic3k.asm:
                // 66804-66810). ROM still applies loc_32538's held X/twist
                // write before that external launch is observed in the final
                // frame state (sonic3k.asm:68026-68038), but loc_32604 only
                // clears the cylinder's rider byte (sonic3k.asm:
                // 68024-68025,68076-68078); it does not zero the player's
                // velocity. Preserve that external launch.
                beginPlayerTwoDiagnostic(slot, "release_external_air", player);
                holdSlotPositionOnly(slot);
                clearStaleCylinderSupport(player);
                clearSlotOnly(slot);
                endPlayerTwoDiagnostic(slot, player);
                return;
            }
            if (!playerOnScreen) {
                // ROM loc_325F2: bset Status_InAir, object_control=0, (a2)=0.
                beginPlayerTwoDiagnostic(slot, "release_offscreen", player);
                releaseSlot(slot, frameCounter, false);
                endPlayerTwoDiagnostic(slot, player);
                return;
            }
            standing = standing || !player.getAir();
            if (!standing) {
                beginPlayerTwoDiagnostic(slot, "release_no_standing", player);
                clearStaleCylinderSupport(player);
                releaseSlot(slot, frameCounter, false);
                endPlayerTwoDiagnostic(slot, player);
                return;
            }
            beginPlayerTwoDiagnostic(slot, "hold", player);
            short preHoldReleaseY = player.getCentreY();
            holdSlot(slot);
            endPlayerTwoDiagnostic(slot, player);
            // Obj_CNZCylinder passes Ctrl_1_logical/Ctrl_2_logical in d5 to
            // sub_324C0, and loc_325B6 branches on the low-byte A/B/C press
            // bits (sonic3k.asm:67656-67672, 68059-68064). Held raw jump or a
            // live raw edge is insufficient here; the low byte of the logical
            // word must carry the A/B/C press bits that Obj_CNZCylinder passed
            // in d5.
            slot.jumpPressedLastFrame = player.isJumpPressed();
            if (player.isLogicalJumpPressActive()) {
                beginPlayerTwoDiagnostic(slot, "release_jump", player);
                releaseSlot(slot, frameCounter, true, preHoldReleaseY);
                endPlayerTwoDiagnostic(slot, player);
                return;
            }
            return;
        }

        // ROM sub_13ECA can write the offscreen CPU marker with
        // object_control=$81 before Obj_CNZCylinder's P2 sub_324C0 pass
        // (sonic3k.asm:26800-26809, 67656-67672). The inactive-cylinder path
        // only tests the preserved standing bit before writing
        // object_control=$03 and clearing Status_InAir (sonic3k.asm:
        // 67985-68005), so do not let the engine's object-control boolean
        // block that same-frame recapture.
        boolean recapturesCpuMarkerFromStandingBit = standing
                && !playerOnScreen
                && player.isCpuControlled()
                && player.isObjectControlled();
        if (!standing || (player.isObjectControlled() && !recapturesCpuMarkerFromStandingBit)) {
            if (!standing) {
                clearStaleCylinderSupport(player);
            }
            return;
        }
        // ROM sub_324C0 (a2)==0 path re-captures immediately - no ROM cooldown.
        // It only tests the cylinder standing bit before writing object_control=3
        // and clearing Status_InAir/x_vel/y_vel/ground_vel (sonic3k.asm:
        // 67985-68005). Tails CPU can write its offscreen despawn marker earlier
        // in the same frame (sub_13ECA, sonic3k.asm:26800-26809), then
        // Obj_CNZCylinder runs its P2 sub_324C0 pass afterward
        // (sonic3k.asm:67656-67672). Let the standing bit recapture immediately
        // for both on-screen and offscreen riders.
        if (playerOnScreen) {
            beginPlayerTwoDiagnostic(slot, "capture", player);
            applyP2CpuNudgeBeforeFirstCapture(slot, player);
        } else {
            beginPlayerTwoDiagnostic(slot, "capture_offscreen", player);
        }
        captureSlot(slot, player, latchedContact);
        endPlayerTwoDiagnostic(slot, player);
    }

    private boolean hasStandingBit(AbstractPlayableSprite player) {
        int bit = standingMaskBitFor(player);
        return bit != 0 && (standingMask & bit) != 0;
    }

    private void applyP2CpuNudgeBeforeFirstCapture(RiderSlot slot, AbstractPlayableSprite player) {
        if (slot != playerTwoSlot || player == null || !player.isCpuControlled()
                || player.getAir() || player.getGSpeed() == 0) {
            return;
        }
        var cpu = player.getCpuController();
        if (cpu == null) {
            return;
        }
        int nudge = cpu.consumePendingGroundedFollowNudge(1);
        if (nudge == 0) {
            return;
        }

        // ROM Tails CPU runs before Obj_CNZCylinder's P2 sub_324C0 pass
        // (sonic3k.asm:26195-26208, 67656-67672). Its FollowLeft/FollowRight
        // branches nudge x_pos by one pixel when the delayed target is on the
        // facing side and ground_vel is nonzero (sonic3k.asm:26717-26724,
        // 26734-26741). The engine discovers this cylinder standing contact
        // after the CPU pass, so apply only the CPU-recorded pending nudge
        // immediately before the first P2 capture consumes the standing bit.
        if (nudge < 0 && player.getDirection() == Direction.LEFT) {
            player.shiftX(-1);
        } else if (nudge > 0 && player.getDirection() == Direction.RIGHT) {
            player.shiftX(1);
        }
    }

    private void primeDefaultRiderSlots(PlayableEntity playerEntity) {
        if (playerOneSlot.player == null && playerEntity instanceof AbstractPlayableSprite sprite) {
            playerOneSlot.player = sprite;
        }

        ObjectServices svc = tryServices();
        AbstractPlayableSprite focused = svc != null && svc.camera() != null
                ? svc.camera().getFocusedSprite()
                : null;
        if (playerOneSlot.player == null && focused != null) {
            playerOneSlot.player = focused;
        }

        if (playerTwoSlot.player == null) {
            AbstractPlayableSprite firstSidekick = getFirstSidekick();
            if (firstSidekick != null && firstSidekick != playerOneSlot.player) {
                playerTwoSlot.player = firstSidekick;
            }
        }
    }

    private AbstractPlayableSprite getFirstSidekick() {
        ObjectServices svc = tryServices();
        if (svc == null) {
            return null;
        }
        return svc.playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite sprite
                ? sprite
                : null;
    }

    private void captureSlot(RiderSlot slot, AbstractPlayableSprite player, boolean latchedContact) {
        slot.player = player;
        slot.active = true;
        int captureCenterX = firstCaptureDistanceAnchorX(player, latchedContact);
        slot.twistAngle = player.getCentreX() < captureCenterX ? 0x80 : 0x00;
        slot.horizontalDistance = Math.min(0xFF, Math.abs(player.getCentreX() - captureCenterX));
        slot.priorityThresholdSource = getPriorityThresholdSource();
        slot.jumpPressedLastFrame = player.isJumpPressed();
        // ROM Obj_CNZCylinder (sonic3k.asm:67668-67672) calls SolidObjectFull
        // every frame, which sets the cylinder's per-rider standing bit on
        // capture. The engine's SolidObject framework blocks the contact pass
        // for object-controlled players (ObjectManager.java:4120-4131
        // `blocksSolidContacts`), so {@link #onSolidContact} never fires once
        // we mark the rider as objectControlled. Set the standing bit
        // explicitly here so the next frame's update() sees it for
        // preservation across both on-screen and off-screen states. Without
        // this, captureSlot setting objectControlled=true creates a
        // "standing-bit cleared, slot.active=true" inconsistency on the very
        // next frame and breaks the cylinder's alternation semantics when the
        // test reseed preserves objectControlled.
        standingMask |= slotMask(slot);

        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null) {
            svc.objectManager().clearRidingObject(player);
        }

        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        // ROM sub_324C0 writes object_control=$03 here (sonic3k.asm:
        // 67999-68005). It does not set Ctrl_locked, so keep the engine's
        // logical input latch open while object-control movement suppression
        // holds the rider in the cylinder.
        player.setControlLocked(false);
        player.setObjectMappingFrameControl(true);
        // ROM sub_324C0 restores default_y_radius/default_x_radius and clears
        // Status_Roll while the player is held in the twist animation.
        player.restoreDefaultRadii();
        player.setRolling(false);
        player.setAir(false);
        player.setPushing(false);
        player.setRollingJump(false);
        player.setJumping(false);
        // ROM Obj_CNZCylinder calls sub_324C0 before SolidObjectFull in the
        // same routine (sonic3k.asm:67656-67672). For an on-screen rider,
        // SolidObjectFull_1P then consumes the standing bit and calls
        // MvSonicOnPtfm, which writes y_pos = cylinder.y - d3 - y_radius
        // after capture restores default_y_radius (sonic3k.asm:41016-41040,
        // 41667-41679, 68002-68004). Offscreen Player_2 is skipped before
        // SolidObjectFull_1P (sonic3k.asm:41006-41010), so keep the CPU
        // despawn-marker position unchanged for that recapture path.
        if (riderRenderFlagOnScreen(player)) {
            player.setCentreYPreserveSubpixel((short) (heldSupportAnchorY() + SOLID_PARAMS.offsetY()
                    - SOLID_PARAMS.groundHalfHeight() - player.getYRadius()));
        }
        player.setAnimationId(0);
        player.setForcedAnimationId(-1);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setPriorityBucket(PLAYER_CAPTURE_PRIORITY);
        applyTwistFrame(player, slot.twistAngle);
    }

    private int firstCaptureDistanceAnchorX(AbstractPlayableSprite player, boolean latchedContact) {
        // ROM Obj_CNZCylinder runs sub_321E2, then immediately lets sub_324C0
        // consume the standing bit that SolidObjectFull set on the prior
        // object pass (sonic3k.asm:67656-67672,67985-67998). In the engine's
        // split pipeline, that first standing callback arrives after object
        // updates; when the next CNZ horizontal oscillator update consumes it,
        // centerX has already advanced one extra sub_321E2 step. Use the
        // frame-entry anchor for only that deferred first-capture distance.
        if (!latchedContact) {
            return centerX;
        }
        int preUpdateX = getPreUpdateX();
        // Circular loc_323EC routes can set the standing bit on one object pass
        // and consume it in sub_324C0 after the engine has stepped the center
        // again. At CNZ f11483/f11484 subtype $4C, ROM captures distance from
        // x_pos(a0)=$1CFE, then the next held frame uses $1CFF; using the
        // current $1CFF during capture stores a distance one pixel too short
        // (sonic3k.asm:67656-67672, 67901-68012).
        if (circularRoute
                && centerX > preUpdateX
                && !player.isCpuControlled()) {
            return preUpdateX;
        }
        if (!isHorizontalOscillator()) {
            return centerX;
        }
        // loc_322F0/loc_3230E-style horizontal steps write x_pos(a0), then the
        // inactive sub_324C0 path stores the rider distance from that object
        // pass's x_pos(a0) (sonic3k.asm:67807-67825, 67985-67998). A deferred
        // non-CPU standing callback in the split engine can be consumed after
        // centerX has advanced one extra step toward the rider; keep the
        // frame-entry anchor for that first distance so the following
        // loc_32538 held write adds the ROM distance to the ROM-visible X
        // (sonic3k.asm:68019-68038).
        int playerX = player.getCentreX();
        boolean centerMovedTowardRider =
                (centerX > preUpdateX && playerX >= centerX)
                        || (centerX < preUpdateX && playerX <= centerX);
        if (!player.isCpuControlled()
                && centerMovedTowardRider) {
            return preUpdateX;
        }
        int currentDistance = Math.abs(playerX - centerX);
        int preUpdateDistance = Math.abs(playerX - preUpdateX);
        return preUpdateDistance < currentDistance ? preUpdateX : centerX;
    }

    private void holdSlot(RiderSlot slot) {
        AbstractPlayableSprite player = slot.player;
        if (player == null) {
            return;
        }

        int sine = TrigLookupTable.sinHex(slot.twistAngle);
        int cosine = TrigLookupTable.cosHex(slot.twistAngle);
        int thresholdByte = ((sine + 0x100) >> 2) & 0xFF;
        // ROM loc_32538 stores the threshold byte at 3(a2), then reads the
        // combined word at 2(a2) as the horizontal distance multiplier.
        int distanceWord = ((slot.horizontalDistance & 0xFF) << 8) | thresholdByte;
        int xOffset = (cosine * distanceWord) >> 16;
        player.setCentreXPreserveSubpixel((short) (heldAnchorX(slot) + xOffset));
        if (!player.getAir()) {
            player.setCentreYPreserveSubpixel((short) (heldSupportAnchorY() + SOLID_PARAMS.offsetY()
                    - SOLID_PARAMS.groundHalfHeight() - player.getYRadius()));
        }
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) cylinderLaunchGroundSpeed(player));
        player.setPushing(false);

        int objectThreshold = slot.priorityThresholdSource & 0xFF;
        player.setPriorityBucket(thresholdByte < objectThreshold
                ? PLAYER_TWIST_PRIORITY
                : PLAYER_CAPTURE_PRIORITY);
        applyTwistFrame(player, slot.twistAngle);
        slot.twistAngle = (slot.twistAngle + 2) & 0xFF;
    }

    private void holdSlotPositionOnly(RiderSlot slot) {
        AbstractPlayableSprite player = slot.player;
        if (player == null) {
            return;
        }

        int sine = TrigLookupTable.sinHex(slot.twistAngle);
        int cosine = TrigLookupTable.cosHex(slot.twistAngle);
        int thresholdByte = ((sine + 0x100) >> 2) & 0xFF;
        int distanceWord = ((slot.horizontalDistance & 0xFF) << 8) | thresholdByte;
        int xOffset = (cosine * distanceWord) >> 16;
        player.setCentreXPreserveSubpixel((short) (heldAnchorX(slot) + xOffset));

        int objectThreshold = slot.priorityThresholdSource & 0xFF;
        player.setPriorityBucket(thresholdByte < objectThreshold
                ? PLAYER_TWIST_PRIORITY
                : PLAYER_CAPTURE_PRIORITY);
        applyTwistFrame(player, slot.twistAngle);
        slot.twistAngle = (slot.twistAngle + 2) & 0xFF;
    }

    private int heldAnchorX(RiderSlot slot) {
        // ROM Obj_CNZCylinder's active rider path uses x_pos(a0) immediately in
        // loc_32538 after the same object pass' sub_321E2 motion
        // (sonic3k.asm:67656-67672, 68019-68038). The engine's split
        // object/solid phases can observe a non-CPU rider's horizontal
        // oscillator step one frame later than the ROM rider-control pass; in
        // that case the frame-entry anchor is the x_pos consumed by loc_32538.
        // CNZ f4320 proves this applies to loc_322F0's post-peak negative
        // step too: ROM still writes the held rider from x_pos(a0)=$1BDF
        // while the engine's split phase has already advanced current center
        // to $1BDE (sonic3k.asm:67656-67672, 67807-67815, 68026-68038).
        // The circular route loc_323EC also needs the
        // frame-entry anchor on the proven positive step after capture: at CNZ
        // f11310 ROM slot 13 is still at $1B93 while the engine's split phase
        // has already advanced the current center to $1B94, and loc_32538 adds
        // the held offset to that ROM-visible $1B93 x_pos(a0)
        // (sonic3k.asm:67985-68038).
        //
        // CPU sidekick holds follow the same horizontal-oscillator object-pass
        // anchor. CNZ f4447 has ROM slot 9 still at $1BA0 while the engine has
        // advanced current center to $1BA1 before the P2 held write; loc_32538
        // still adds the held offset to the ROM-visible $1BA0 x_pos(a0).
        int preUpdateX = getPreUpdateX();
        boolean horizontalPostMotionStep = isHorizontalOscillator() && centerX != preUpdateX;
        boolean circularPositiveStep = circularRoute && centerX > preUpdateX;
        if (horizontalPostMotionStep && slot.player != null) {
            return preUpdateX;
        }
        if (circularPositiveStep
                && slot.player != null
                && !slot.player.isCpuControlled()) {
            return preUpdateX;
        }
        return centerX;
    }

    private int heldSupportAnchorY() {
        // ROM loc_3236E/loc_3238C/loc_323AA/loc_323CE update y_pos, then
        // loc_32188 runs sub_324C0 and SolidObjectFull in the same object pass;
        // SolidObjectFull's standing branch calls MvSonicOnPtfm, which carries
        // the rider from that ROM-visible y_pos(a0) (sonic3k.asm:67656-67672,
        // 67843-67884, 41016-41040, 41667-41679). In the engine split pass at
        // CNZ f13049, subtype $46 has already advanced one upward oscillator
        // pixel before the held-rider/support write; the frame-entry y_pos is
        // the ROM-visible support anchor for that pass.
        if (isVerticalOscillator()
                && centerX == getPreUpdateX()
                && centerY < getPreUpdateY()) {
            return getPreUpdateY();
        }
        return centerY;
    }

    private int cylinderLaunchGroundSpeed(AbstractPlayableSprite player) {
        // ROM sub_324C0 loc_32594 (sonic3k.asm:68045-68056): ground_vel is
        // cleared, then set to $800 only while the rider is grounded and
        // abs(y_vel(a0)) has reached the cylinder launch threshold.
        if (player.getAir() || Math.abs((short) romStoredYVelocity()) < 0x480) {
            return 0;
        }
        return 0x800;
    }

    private int romStoredYVelocity() {
        // ROM loc_32594/loc_325B6 read y_vel(a0), but only mode 0's
        // loc_32208 controller updates that field. Sine/circular routes such
        // as loc_3238C write y_pos(a0) directly and leave y_vel(a0) unchanged
        // (sonic3k.asm:67709-67804, 67865-67872, 68045-68068).
        return motionSelector == 0 ? currentYVelocity : 0;
    }

    private void clearStaleCylinderSupport(AbstractPlayableSprite player) {
        if (!isLatchedToThisCylinder(player)) {
            return;
        }
        // ROM sub_324C0 loc_32538 (sonic3k.asm:68019-68025) exits the
        // rider-control path when the cylinder standing bit is clear. Clear
        // only this cylinder's engine-side latch so the shared SolidObject
        // finalizer cannot preserve stale object support for a released rider.
        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null) {
            svc.objectManager().clearRidingObject(player);
        }
        player.setOnObject(false);
        player.setLatchedSolidObjectId(0);
    }

    private boolean isLatchedToThisCylinder(AbstractPlayableSprite player) {
        if (player == null || player.getLatchedSolidObjectId() != (spawn.objectId() & 0xFF)) {
            return false;
        }
        return player.getLatchedSolidObjectInstance() == this;
    }

    private int getPriorityThresholdSource() {
        return PRIORITY_THRESHOLD_SOURCE;
    }

    private void clearSlotOnly(RiderSlot slot) {
        slot.active = false;
    }

    private void releaseSlot(RiderSlot slot, int frameCounter, boolean jumpedOff) {
        releaseSlot(slot, frameCounter, jumpedOff, (short) 0);
    }

    private void releaseSlot(RiderSlot slot, int frameCounter, boolean jumpedOff, short jumpReleaseY) {
        AbstractPlayableSprite player = slot.player;
        if (player == null) {
            slot.active = false;
            return;
        }

        slot.active = false;
        player.setObjectMappingFrameControl(false);
        player.releaseFromObjectControl(frameCounter);
        player.setControlLocked(false);
        player.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
        player.setForcedAnimationId(-1);
        player.setPushing(false);

        if (jumpedOff) {
            short releaseX = player.getCentreX();
            // ROM loc_325B6 changes y_radius/x_radius for the jump, then
            // falls through to loc_325F2 without writing y_pos (sonic3k.asm:
            // 68059-68076). Use the y_pos from before the engine's local hold
            // rewrite; moving cylinders must not recompute release Y from the
            // current object centre on the jump frame.
            short releaseY = jumpReleaseY;
            clearCylinderReleaseSupport(player);
            // The same Obj_CNZCylinder pass still calls SolidObjectFull after
            // sub_324C0 (sonic3k.asm:67656-67672). Since the cylinder standing
            // bit was set for loc_32538, SolidObjectFull_1P takes loc_1DC98
            // for the now-airborne rider and returns d4=0 without applying
            // loc_1E154's upward-velocity lift (sonic3k.asm:41016-41034).
            releasedJumpSolidSkipPlayer = player;
            player.setAir(true);
            player.setJumping(true);
            player.applyRollingRadii(false);
            player.setRolling(true);
            player.setCentreXPreserveSubpixel(releaseX);
            player.setCentreYPreserveSubpixel(releaseY);
            player.setAnimationId(2);
            player.setYSpeed((short) (romStoredYVelocity() + RELEASE_Y_SPEED));
            player.setXSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.suppressNextJumpPress();
        } else {
            player.setAir(true);
            player.setJumping(false);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
        }
    }

    private void clearCylinderReleaseSupport(AbstractPlayableSprite player) {
        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null) {
            svc.objectManager().clearRidingObject(player);
        }
        player.setOnObject(false);
        player.setLatchedSolidObjectId(0);
    }

    private void applyTwistFrame(AbstractPlayableSprite player, int twistAngle) {
        int frameIndex = ((twistAngle + 0x0B) & 0xFF) / 0x16;
        if (frameIndex < 0 || frameIndex >= PLAYER_TWIST_FRAMES.length) {
            frameIndex = 0;
        }

        player.setMappingFrame(PLAYER_TWIST_FRAMES[frameIndex]);
        boolean flipLeft = PLAYER_TWIST_FLIPS[frameIndex];
        player.setDirection(flipLeft ? Direction.LEFT : Direction.RIGHT);
        player.setRenderFlips(flipLeft, false);
    }

    private void advanceAnimation() {
        animFrameTimer--;
        if (animFrameTimer >= 0) {
            return;
        }
        animFrameTimer = 1;
        mappingFrame = (mappingFrame + 1) & 0x03;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_CYLINDER);
        if (renderer == null) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), hFlip, vFlip);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return player != releasedJumpSolidSkipPlayer;
    }

    @Override
    public boolean allowsObjectControlledSolidContacts() {
        // ROM Obj_CNZCylinder writes object_control=$03 on capture
        // (sonic3k.asm:68002), then still calls SolidObjectFull every frame
        // after sub_324C0 (sonic3k.asm:67656-67672). SolidObjectFull's active
        // rider branch can clear the cylinder standing bit when the rider is
        // airborne or leaves bounds (sonic3k.asm:41016-41033), which sub_324C0
        // consumes on the next active-slot check (sonic3k.asm:68019-68025).
        return true;
    }

    @Override
    public boolean rejectsBit7ObjectControlSideContact(PlayableEntity player) {
        // ROM SolidObject_cont rejects signed object_control values before side
        // separation (`tst.b object_control(a1); bmi.w loc_1E0A2`,
        // sonic3k.asm:41438-41440). This is narrower than the normal
        // object-controlled opt-in above: CNZCylinder's bit-7-clear captured
        // states such as $03 still need SolidObjectFull feedback, while Tails'
        // $81 flight/despawn marker must not be pushed sideways by the cylinder.
        return true;
    }

    @Override
    public boolean rejectsBit7ObjectControlNewSolidContact(PlayableEntity player) {
        // Obj_CNZCylinder reaches SolidObjectFull after sub_324C0
        // (sonic3k.asm:67656-67672). For new contacts, SolidObject_cont tests
        // signed object_control before both side separation and top landing
        // (`tst.b object_control(a1); bmi.w loc_1E0A2`,
        // sonic3k.asm:41394-41440). This must not block bit-7-clear captured
        // riders because their standing-bit branch is consumed before
        // SolidObject_cont.
        return true;
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public boolean usesPreUpdatePositionForSolidContact(PlayableEntity player) {
        // Obj_CNZCylinder runs captured-rider logic before SolidObjectFull
        // (sonic3k.asm:67656-67672, 67985-68038), so object-controlled riders
        // normally keep the current post-motion anchor. Y-only object steps are
        // the narrow exception: SolidObjectFull writes the rider's y_pos from
        // the ROM-visible y_pos(a0) consumed by that object pass
        // (sonic3k.asm:41016-41040, 41667-41679). In the engine split, the
        // deferred solid-contact checkpoint can run after the cylinder body has
        // locally advanced one extra Y step; use the frame-entry anchor for the
        // proven circular down step at CNZ f11503 and vertical-oscillator up
        // step at CNZ f13049.
        if (player instanceof AbstractPlayableSprite sprite
                && sprite.isObjectControlled()) {
            boolean circularVerticalOnlyStep = circularRoute
                    && centerX == getPreUpdateX()
                    && centerY > getPreUpdateY();
            boolean verticalOscillatorUpStep = isVerticalOscillator()
                    && centerX == getPreUpdateX()
                    && centerY < getPreUpdateY();
            if (sprite.isCpuControlled()) {
                // The same frame-entry support anchor applies after P2 capture:
                // sub_324C0 writes object_control=$03, then the same
                // Obj_CNZCylinder pass still calls SolidObjectFull for Player_2
                // while render_flags is on-screen (sonic3k.asm:67656-67672,
                // 41006-41016, 67985-68005). In the split engine pass at CNZ
                // f13062, using the already-stepped vertical-oscillator y_pos
                // overwrites the capture Y one pixel high.
                return verticalOscillatorUpStep;
            }
            return circularVerticalOnlyStep || verticalOscillatorUpStep;
        }
        if (player instanceof AbstractPlayableSprite sprite
                && sprite.isCpuControlled()
                && !sprite.isObjectControlled()) {
            if (isHorizontalOscillator()) {
                // P2 reaches the same Obj_CNZCylinder SolidObjectFull call as
                // P1 after sub_324C0 (sonic3k.asm:67656-67672), and
                // SolidObjectFull passes the live x_pos(a0) in d4 to
                // SolidObject_cont's side separation (sonic3k.asm:41006-41010,
                // 41394-41407, 41488-41495). In the engine's split checkpoint,
                // the current horizontal oscillator position can be one step
                // ahead of the ROM-visible object-pass anchor; CNZ f18259's
                // free Tails side contact must separate from the frame-entry
                // x_pos=$1415, not the already-stepped $1414.
                return true;
            }
            // P2 reaches the same SolidObjectFull call after sub_324C0
            // (sonic3k.asm:67656-67672, 41006-41016). At CNZ f13060 the
            // engine's split phase has advanced subtype $46 from
            // y_pos=$0416 to $0415 one pass ahead of the ROM-visible object
            // anchor; using current y_pos turns Tails' relY=-1 miss into a
            // relY=0 landing and zeroes y_vel one frame early. Keep this to
            // the vertical-oscillator upward step where the frame-entry anchor
            // is proven by loc_3238C/SolidObject_cont geometry
            // (sonic3k.asm:67865-67874, 41394-41440).
            return isVerticalOscillator()
                    && centerX == getPreUpdateX()
                    && centerY < getPreUpdateY();
        }
        // Horizontal oscillator contacts use the frame-entry X anchor in the
        // engine's split inline checkpoint; SolidObject_cont then applies side
        // separation from x_pos (sonic3k.asm:41394-41407, 41488-41495). CNZ
        // trace f10541 exercises subtype $42, where the ROM separates from
        // $19B2 while the engine has already advanced the cylinder body to
        // $19B4.
        if (!(player instanceof AbstractPlayableSprite sprite)
                || sprite.isCpuControlled()
                || sprite.isObjectControlled()) {
            return false;
        }
        if (isHorizontalOscillator()) {
            return true;
        }
        // Vertical oscillator contacts use the same frame-entry object anchor
        // for new non-controlled side separation. Obj_CNZCylinder's loc_3236E/
        // loc_3238C/loc_323AA/loc_323CE update y_pos before SolidObjectFull
        // (sonic3k.asm:67843-67884, 67656-67672), and SolidObject_cont then
        // classifies side-vs-top from x_pos/y_pos before zeroing speed in
        // loc_1E056 (sonic3k.asm:41394-41440, 41473-41495). In the split
        // engine pass, a one-pixel vertical body step can make that side
        // classification one frame early; CNZ f6678 subtype $45 should still
        // classify from the frame-entry y_pos=$04FD, not the just-stepped
        // y_pos=$04FE.
        return isVerticalOscillator() && centerY != getPreUpdateY();
    }

    private boolean isHorizontalOscillator() {
        return motionSelector == 0x02
                || motionSelector == 0x04
                || motionSelector == 0x06
                || motionSelector == 0x08;
    }

    private boolean isVerticalOscillator() {
        return motionSelector == 0x0A
                || motionSelector == 0x0C
                || motionSelector == 0x0E
                || motionSelector == 0x10;
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (!contact.standing() || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }

        RiderSlot slot = resolveContactSlot(sprite);
        if (slot == null) {
            return;
        }

        slot.player = sprite;
        slot.contactLatched = true;

        int mask = slotMask(slot);
        nextStandingMask |= mask;
        nextHeldInputMask |= heldInputMaskFor(sprite);

        // ROM Obj_CNZCylinder positions captured riders in sub_324C0 before
        // calling SolidObjectFull with d4 = current x_pos(a0)
        // (sonic3k.asm:67656-67672, 68026-68038). SolidObjectFull_1P then
        // calls MvSonicOnPtfm with that same current anchor, so the platform
        // X delta is zero for captured riders (sonic3k.asm:41038-41040,
        // 41667-41679). Keep the standing bit feedback, but do not let the
        // engine's previous-X riding tracker apply a second carry delta next
        // frame.
        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null && sprite.isObjectControlled()) {
            svc.objectManager().clearRidingObject(sprite);
        }
        if (sprite.isObjectControlled() && !sprite.getAir()
                && Math.abs((short) currentYVelocity) >= 0x480) {
            sprite.setGSpeed((short) 0x0800);
        }
    }

    private RiderSlot resolveContactSlot(AbstractPlayableSprite sprite) {
        if (playerOneSlot.player == sprite) {
            return playerOneSlot;
        }
        if (playerTwoSlot.player == sprite) {
            return playerTwoSlot;
        }
        ObjectServices svc = tryServices();
        AbstractPlayableSprite focused = svc != null && svc.camera() != null
                ? svc.camera().getFocusedSprite()
                : null;
        if (sprite == focused) {
            return playerOneSlot;
        }
        if (isFirstSidekick(sprite)) {
            return playerTwoSlot;
        }
        if (playerOneSlot.player == null) {
            return playerOneSlot;
        }
        if (playerTwoSlot.player == null) {
            return playerTwoSlot;
        }
        return null;
    }

    private boolean isFirstSidekick(AbstractPlayableSprite sprite) {
        ObjectServices svc = tryServices();
        if (svc == null) {
            return false;
        }
        return svc.playerQuery().nativeP2OrNull() == sprite;
    }

    private int slotMask(RiderSlot slot) {
        return slot == playerOneSlot ? 0x01 : 0x02;
    }

    private int standingMaskBitFor(AbstractPlayableSprite sprite) {
        ObjectServices svc = tryServices();
        AbstractPlayableSprite focused = svc != null && svc.camera() != null
                ? svc.camera().getFocusedSprite()
                : null;
        if (playerOneSlot.player == sprite
                || (sprite == focused && playerTwoSlot.player != sprite)) {
            return 0x01;
        }
        if (playerTwoSlot.player == sprite || isFirstSidekick(sprite)) {
            return 0x02;
        }
        if (svc != null) {
            PlayableEntity main = svc.playerQuery().mainPlayerOrNull();
            for (PlayableEntity candidate : svc.playerQuery().playersFor(
                    ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
                if (candidate != main && candidate == sprite) {
                    return 0x02;
                }
            }
        }
        return 0;
    }

    private int heldInputMaskFor(AbstractPlayableSprite sprite) {
        int mask = 0;
        if (sprite.isUpPressed()) {
            mask |= AbstractPlayableSprite.INPUT_UP;
        }
        if (sprite.isDownPressed()) {
            mask |= AbstractPlayableSprite.INPUT_DOWN;
        }
        return mask;
    }

    private void beginPlayerTwoDiagnostic(RiderSlot slot, String branch,
                                          AbstractPlayableSprite player) {
        if (slot != playerTwoSlot || player == null) {
            return;
        }
        playerTwoDiagnosticBranch = branch;
        playerTwoPreX = player.getCentreX() & 0xFFFF;
        playerTwoPreXSubpixel = player.getXSubpixelRaw() & 0xFFFF;
        playerTwoPreStatus = diagnosticStatusByte(player);
        playerTwoPreObjectControl = player.isObjectControlled() ? 0x03 : 0x00;
        playerTwoPostX = playerTwoPreX;
        playerTwoPostXSubpixel = playerTwoPreXSubpixel;
        playerTwoPostStatus = playerTwoPreStatus;
        playerTwoPostObjectControl = playerTwoPreObjectControl;
    }

    private void endPlayerTwoDiagnostic(RiderSlot slot, AbstractPlayableSprite player) {
        if (slot != playerTwoSlot || player == null) {
            return;
        }
        playerTwoPostX = player.getCentreX() & 0xFFFF;
        playerTwoPostXSubpixel = player.getXSubpixelRaw() & 0xFFFF;
        playerTwoPostStatus = diagnosticStatusByte(player);
        playerTwoPostObjectControl = player.isObjectControlled() ? 0x03 : 0x00;
    }

    private static int diagnosticStatusByte(AbstractPlayableSprite player) {
        int status = 0;
        if (player.getDirection() == Direction.LEFT) {
            status |= 0x01;
        }
        if (player.getAir()) {
            status |= 0x02;
        }
        if (player.getRolling()) {
            status |= 0x04;
        }
        if (player.isOnObject()) {
            status |= 0x08;
        }
        if (player.isInWater()) {
            status |= 0x40;
        }
        return status;
    }

    @Override
    public String traceDebugDetails() {
        return String.format(
                "cyl center=%04X,%04X yv=%04X masks=%02X/%02X p2=%s pre=%04X.%02X,%02X/%02X post=%04X.%02X,%02X/%02X slot=%s,%02X,%02X,%02X",
                centerX & 0xFFFF,
                centerY & 0xFFFF,
                currentYVelocity & 0xFFFF,
                standingMask & 0xFF,
                nextStandingMask & 0xFF,
                playerTwoDiagnosticBranch,
                playerTwoPreX & 0xFFFF,
                playerTwoPreXSubpixel & 0xFF,
                playerTwoPreStatus & 0xFF,
                playerTwoPreObjectControl & 0xFF,
                playerTwoPostX & 0xFFFF,
                playerTwoPostXSubpixel & 0xFF,
                playerTwoPostStatus & 0xFF,
                playerTwoPostObjectControl & 0xFF,
                playerTwoSlot.active,
                playerTwoSlot.twistAngle & 0xFF,
                playerTwoSlot.horizontalDistance & 0xFF,
                playerTwoSlot.priorityThresholdSource & 0xFF);
    }
}
