package com.openggf.sprites.managers;

import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.PlayerAnimationRules;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationScript;

/**
 * Updates a playable sprite's mapping frame based on its animation profile.
 */
public class PlayableSpriteAnimation {
    private static final int DEFAULT_RUN_SPEED_THRESHOLD = 0x600;
    private final AbstractPlayableSprite sprite;
    private int lastAnimationId = -1;
    // ROM prev_anim equivalent for the Status_Push frame-end clear: the grounded
    // movement-selected anim byte (WAIT/WALK/BALANCE/...), tracked separately from
    // lastAnimationId because lastAnimationId also carries the engine's push render
    // animation substitution, which the ROM anim byte does not. See
    // ScriptedVelocityAnimationProfile.resolveGroundMovementAnimId.
    private int lastGroundMovementAnimId = -1;
    private int groundMovementAnimSpeedSnapshot = Integer.MIN_VALUE;
    private boolean groundMovementAnimationSuppressed;

    /**
     * Resets the tracked animation ID so the next update sees a mismatch
     * and restarts the current animation script.
     * ROM equivalent: clearing prev_anim to 0 when anim stays the same.
     */
    public void resetLastAnimationId() {
        lastAnimationId = -1;
    }

    /**
     * Publishes a native {@code prev_anim} value without changing the selected
     * animation or its current script state. The next update restarts only when
     * the selected animation differs from this exact ROM-visible value.
     */
    public void publishPreviousAnimationId(int nativePrevAnimId) {
        lastAnimationId = nativePrevAnimId;
    }

    public PlayableSpriteAnimation(AbstractPlayableSprite sprite) {
        this.sprite = sprite;
    }

    private PlayerAnimationRules playerAnimationRulesOrNull() {
        GameRules rules = sprite.getGameRules();
        if (rules != null && rules.playerAnimation() != null) {
            return rules.playerAnimation();
        }
        return null;
    }

    /**
     * Captures the previous-animation tracker. {@link #lastAnimationId} gates
     * {@link #resetScriptState()} on every {@link #update(int)} call: when the
     * sprite's {@code animationId} differs from {@code lastAnimationId}, the
     * script's frame index/tick are reset to 0. Without snapshotting it,
     * a rewound run can have the same {@code animationId} in the sprite
     * snapshot but a stale {@code lastAnimationId} from the live forward run,
     * causing a spurious script reset (or skipping a real one) on the first
     * replay tick. That drift propagates into {@code mappingFrame},
     * {@code animationFrameIndex}, and {@code animationTick} after long
     * forward+rewind cycles (surfaced by TestRewindTorture).
     */
    public RewindState captureRewindState() {
        return new RewindState(lastAnimationId, lastGroundMovementAnimId);
    }

    public void restoreRewindState(RewindState state) {
        if (state == null) {
            lastAnimationId = -1;
            lastGroundMovementAnimId = -1;
            return;
        }
        lastAnimationId = state.lastAnimationId();
        lastGroundMovementAnimId = state.lastGroundMovementAnimId();
    }

    public record RewindState(int lastAnimationId, int lastGroundMovementAnimId) {}

    public void captureGroundMovementAnimSpeed(short speed) {
        groundMovementAnimSpeedSnapshot = speed;
    }

    public void clearGroundMovementAnimSpeed() {
        groundMovementAnimSpeedSnapshot = Integer.MIN_VALUE;
        groundMovementAnimationSuppressed = false;
    }

    public boolean hasGroundMovementAnimSpeed() {
        return groundMovementAnimSpeedSnapshot != Integer.MIN_VALUE;
    }

    public short getGroundMovementAnimSpeed() {
        return hasGroundMovementAnimSpeed() ? (short) groundMovementAnimSpeedSnapshot : sprite.getGSpeed();
    }

    public void suppressGroundMovementAnimationForFrame() {
        groundMovementAnimationSuppressed = true;
    }

    public boolean isGroundMovementAnimationSuppressed() {
        return groundMovementAnimationSuppressed;
    }

    public void update(int frameCounter) {
        if (sprite == null) {
            return;
        }
        if (sprite.getSpindashDustController() != null) {
            sprite.getSpindashDustController().update();
        }
        if (sprite.getTailsTailsController() != null) {
            sprite.getTailsTailsController().update();
        }

        SpriteAnimationProfile profile = sprite.getAnimationProfile();
        if (sprite.getAnimationSet() != null && !sprite.getAnimationSet().getAllScripts().isEmpty()) {
            int forced = sprite.getForcedAnimationId();
            if (forced < 0 && profile instanceof ScriptedVelocityAnimationProfile velocityProfile) {
                clearPushForAnimationChange(velocityProfile, frameCounter,
                        sprite.getAnimationSet().getScriptCount());
            }
            // Both branches must be Integer (not int) so null from resolveAnimationId
            // doesn't trigger auto-unboxing NPE via JLS ternary type inference.
            Integer desiredAnimId = forced >= 0
                    ? Integer.valueOf(forced)
                    : resolveDesiredAnimationId(profile, frameCounter);
            if (desiredAnimId != null && desiredAnimId != sprite.getAnimationId()) {
                sprite.setAnimationId(desiredAnimId);
            }
            restoreWaterTunnelPreviousAnimation(profile);
            if (sprite.isObjectMappingFrameControl()) {
                applyDefaultFacingRenderFlips();
                return;
            }
            updateScriptedAnimation(frameCounter);
            return;
        }

        applyDefaultFacingRenderFlips();
        if (profile == null) {
            return;
        }
        if (sprite.isObjectMappingFrameControl()) {
            return;
        }
        int frameCount = sprite.getAnimationFrameCount();
        if (frameCount <= 0) {
            return;
        }
        int frame = profile.resolveFrame(sprite, frameCounter, frameCount);
        sprite.setMappingFrame(frame);
    }

    /**
     * S1's player slot repairs the wind-tunnel animation immediately before
     * Animate: while {@code f_wtunnelmode} is set, a movement-written Walk byte
     * is replaced with {@code prev_anim}. This matters while Obj64 temporarily
     * disables the tunnel push but leaves the mode flag active. The provider
     * predicate keeps the shared animation code driven by native runtime state,
     * without a game or zone branch.
     */
    private void restoreWaterTunnelPreviousAnimation(SpriteAnimationProfile profile) {
        if (!(profile instanceof ScriptedVelocityAnimationProfile velocityProfile)
                || sprite.getAnimationId() != velocityProfile.getWalkAnimId()
                || lastAnimationId < 0) {
            return;
        }
        var levelManager = sprite.currentLevelManagerIfAvailable();
        ZoneFeatureProvider zoneFeatures = levelManager != null
                ? levelManager.getZoneFeatureProvider()
                : null;
        if (zoneFeatures != null && zoneFeatures.isWaterTunnelActive()) {
            sprite.setAnimationId(lastAnimationId);
        }
    }

    private void updateScriptedAnimation(int frameCounter) {
        var animationSet = sprite.getAnimationSet();
        if (animationSet == null) {
            return;
        }
        if (sprite.getAnimationId() != lastAnimationId) {
            SpriteAnimationProfile profile = sprite.getAnimationProfile();
            if (pushUsesWalkSpecialHandler()
                    && profile instanceof ScriptedVelocityAnimationProfile velocityProfile) {
                // Animate_Sonic/Tails stores the raw anim byte into prev_anim
                // even when the movement resolver is intentionally null (for
                // example an object-landing Walk write on an airborne-start
                // frame). Keep the walk-special tracker in step with the
                // native comparison without collapsing the raw state.
                PlayerAnimationRules rules = playerAnimationRulesOrNull();
                if (lastAnimationId >= 0
                        && rules != null
                        && rules.animationChangeClearsPush()) {
                    // The raw anim/prev_anim comparison is still authoritative
                    // when the movement resolver intentionally returns null.
                    // A post-player object landing can publish Walk after the
                    // previous Animate pass; on the next player tick, a fresh
                    // wall push must be cleared before Walk selects mappings.
                    sprite.setPushing(false);
                }
                lastGroundMovementAnimId = groundMoveAnimByte(
                        velocityProfile, sprite.getAnimationId());
            }
            resetScriptState();
        }
        var script = animationSet.getScript(sprite.getAnimationId());
        if (script == null || script.frames().isEmpty()) {
            applyDefaultFacingRenderFlips();
            return;
        }

        if (!walkRunDelayLatchesRenderOrientation(script)) {
            applyDefaultFacingRenderFlips();
        }

        // Native walk/run handlers do not all put their timer gate in the same
        // place. S1 Sonic and S2 Tails return before selecting a new mapping;
        // S2 Sonic and the S3K characters publish the current mapping first.
        // Keep that ordering on the per-character animation profile.
        int remaining = sprite.getAnimationTick() - 1;
        int delayOrFlag = script.delay() & 0xFF;
        if (remaining >= 0 && !walkRunPublishesFrameBeforeTimerAdvance(delayOrFlag)) {
            sprite.setAnimationTick(remaining);
            return;
        }

        if (delayOrFlag >= 0x80) {
            updateSpecialScript(delayOrFlag, script, remaining);
            return;
        }

        updateScriptWithDelay(script, delayOrFlag, 0);
    }

    private boolean walkRunDelayLatchesRenderOrientation(SpriteAnimationScript script) {
        PlayerAnimationRules rules = playerAnimationRulesOrNull();
        return rules != null
                && rules.walkRunDelayLatchesRenderOrientation()
                && (script.delay() & 0xFF) == 0xFF
                && sprite.getAnimationTick() > 0;
    }

    private boolean walkRunPublishesFrameBeforeTimerAdvance(int delayOrFlag) {
        SpriteAnimationProfile profile = sprite.getAnimationProfile();
        return delayOrFlag == 0xFF
                && profile instanceof ScriptedVelocityAnimationProfile velocityProfile
                && velocityProfile.isWalkRunPublishesFrameBeforeTimerAdvance();
    }

    private void applyDefaultFacingRenderFlips() {
        boolean facingLeft = Direction.LEFT.equals(sprite.getDirection());
        sprite.setRenderFlips(facingLeft, false);
    }

    private void updateSpecialScript(int startFlag, SpriteAnimationScript script, int remaining) {
        switch (startFlag & 0xFF) {
            case 0xFF -> updateWalkRun(script, remaining);
            case 0xFE -> updateRoll(script, remaining);
            case 0xFD -> updatePush(script, remaining);
            default -> updateScriptWithDelay(script, 0, 0);
        }
    }

    private void updateWalkRun(SpriteAnimationScript baseScript, int remaining) {
        int flipAngle = sprite.getFlipAngle();
        if (flipAngle != 0) {
            updateTumble(flipAngle);
            return;
        }
        ScriptedVelocityAnimationProfile profile = resolveVelocityProfile();
        int speed = Math.abs(sprite.getGSpeed());
        if (profile != null
                && profile.isDoubleWalkRunAnimationSpeedWhenSliding()
                && sprite.isSliding()) {
            speed = Math.min(0xFFFF, speed << 1);
        }
        int runThreshold = resolveRunThreshold(profile);

        SpriteAnimationScript walkScript = resolveScript(profile != null ? profile.getWalkAnimId() : -1, baseScript);
        SpriteAnimationScript runScript = resolveScript(profile != null ? profile.getRunAnimId() : -1, baseScript);
        boolean highSpeedTier = profile != null
                && profile.getHighSpeedWalkRunAnimId() >= 0
                && profile.getHighSpeedWalkRunThreshold() > 0
                && speed >= profile.getHighSpeedWalkRunThreshold();
        boolean runTier = !highSpeedTier && speed >= runThreshold;
        SpriteAnimationScript active;
        int slopeStride;
        if (highSpeedTier) {
            active = resolveScript(profile.getHighSpeedWalkRunAnimId(), baseScript);
            slopeStride = profile.getHighSpeedSlopeFrameStride();
        } else if (runTier) {
            active = runScript;
            slopeStride = profile != null ? profile.getRunSlopeFrameStride() : 0;
        } else {
            active = walkScript;
            slopeStride = profile != null ? profile.getWalkSlopeFrameStride() : 0;
        }
        if (active == null) {
            active = baseScript;
        }

        // S1 Sonic_Animate keeps obAnim at id_Walk while Status_Push selects
        // SonAni_Push inside the $FF walk/run special handler. Crucially, the
        // handler decrements obTimeFrame and returns while it is non-negative;
        // the push bit is only tested when the animation step expires
        // (01 Sonic.asm:2253-2282,2353-2376). Do not replace the raw animation
        // id or reset the shared special-animation frame index when push begins.
        int slopeOffset = resolveSlopeOffset(active, slopeStride);
        int delay = computeSpeedDelay(speed, 0x800, 8);
        if (pushUsesWalkSpecialHandler() && sprite.getPushing()) {
            // S2 Sonic publishes the already-selected walk/run mapping before
            // its timer gate. A push that begins while that timer is live does
            // not select SAnim_Push until the step expires (s2.asm:38449-38474,
            // 38627-38649). Tails and S1 return at the earlier outer timer gate.
            if (walkRunPublishesFrameBeforeTimerAdvance(baseScript.delay() & 0xFF)
                    && remaining >= 0) {
                sprite.setAnimationTick(remaining);
                return;
            }
            SpriteAnimationScript pushScript = resolveScript(
                    profile != null ? profile.getPushAnimId() : -1, baseScript);
            int pushDelay = computeSpeedDelay(speed, 0x800, 6);
            updateScriptWithDelay(pushScript, pushDelay, 0);
            return;
        }

        if (walkRunPublishesFrameBeforeTimerAdvance(baseScript.delay() & 0xFF)) {
            updateWalkRunBeforeTimerAdvance(active, delay, slopeOffset, remaining);
            return;
        }
        updateScriptWithDelay(active, delay, slopeOffset);
    }

    /**
     * S2 Sonic and the S3K character walk/run handlers publish
     * {@code mapping_frame} from the current
     * {@code anim_frame} before decrementing the timer. Only an expired timer
     * advances {@code anim_frame}, so the newly selected mapping becomes
     * visible on the following object tick. S1 gates the mapping write before
     * this point and therefore keeps the already displayed frame latched.
     *
     * <p>ROM: S2 Sonic's {@code SAnim_WalkRun} writes the mapping at
     * s2.asm:38494-38495, then decrements/advances at 38496-38505. S2 Tails
     * instead gates the whole special handler first at 41330-41336, then
     * publishes at 41386-41396. S3K's {@code Animate_Sonic} publishes at
     * sonic3k.asm:24859-24868 before its 24869-24879 timer/advance. S1 returns
     * on the timer gate before selecting a walk/run mapping
     * ({@code _incObj/01 Sonic.asm:2145-2149,2198-2209}).
     */
    private void updateWalkRunBeforeTimerAdvance(
            SpriteAnimationScript script,
            int delay,
            int frameOffset,
            int remaining
    ) {
        if (script == null || script.frames().isEmpty()) {
            return;
        }

        int frameIndex = sprite.getAnimationFrameIndex();
        if (frameIndex < 0) {
            frameIndex = 0;
            sprite.setAnimationFrameIndex(0);
        }
        if (frameIndex >= script.frames().size()) {
            if (!processEndAction(script)) {
                return;
            }
            frameIndex = sprite.getAnimationFrameIndex();
            if (frameIndex < 0 || frameIndex >= script.frames().size()) {
                frameIndex = 0;
                sprite.setAnimationFrameIndex(0);
            }
        }

        sprite.setMappingFrame(script.frames().get(frameIndex) + frameOffset);
        if (remaining >= 0) {
            sprite.setAnimationTick(remaining);
            return;
        }

        sprite.setAnimationTick(delay);
        sprite.setAnimationFrameIndex(frameIndex + 1);
    }

    private void updateTumble(int flipAngle) {
        // ROM: Anim_Tumble — S2 uses base 0x5F (s2.asm:38216), S3K uses 0x31 (sonic3k.asm:24955)
        ScriptedVelocityAnimationProfile profile = resolveVelocityProfile();
        int base = profile != null ? profile.getTumbleFrameBase() : 0x5F;

        int d0 = flipAngle & 0xFF;
        boolean facingLeft = Direction.LEFT.equals(sprite.getDirection());
        if (!facingLeft) {
            sprite.setRenderFlips(false, false);
            int frame = ((d0 + 0x0B) & 0xFF) / 0x16;
            sprite.setMappingFrame(frame + base);
            sprite.setAnimationTick(0);
            return;
        }

        boolean flipTurned = sprite.isFlipTurned();
        int adjusted;
        boolean hFlip = true;
        boolean vFlip;
        if (flipTurned) {
            vFlip = false;
            adjusted = (d0 + 0x0B) & 0xFF;
        } else {
            vFlip = true;
            adjusted = (0x100 - d0) & 0xFF;
            adjusted = (adjusted + 0x8F) & 0xFF;
        }
        int frame = (adjusted / 0x16) + base;
        sprite.setRenderFlips(hFlip, vFlip);
        sprite.setMappingFrame(frame);
        sprite.setAnimationTick(0);
    }

    private void updateRoll(SpriteAnimationScript baseScript, int remaining) {
        // S3K's walk/run handler publishes the current mapping before its timer
        // gate, but loc_12A2A gates Roll before selecting a script frame. The
        // profile flag therefore cannot be applied to every negative-delay
        // script merely because they share the same outer dispatcher.
        if (remaining >= 0) {
            sprite.setAnimationTick(remaining);
            return;
        }
        int speed = Math.abs(sprite.getGSpeed());
        ScriptedVelocityAnimationProfile profile = resolveVelocityProfile();
        int runThreshold = resolveRunThreshold(profile);

        int rollId = profile != null ? profile.getRollAnimId() : -1;
        int roll2Id = profile != null ? profile.getRoll2AnimId() : -1;
        int activeId = (speed >= runThreshold && roll2Id >= 0) ? roll2Id : rollId;
        SpriteAnimationScript active = resolveScript(activeId, baseScript);
        if (active == null) {
            active = baseScript;
        }

        int delay = computeSpeedDelay(speed, 0x400, 8);
        updateScriptWithDelay(active, delay, 0);
    }

    private void updatePush(SpriteAnimationScript baseScript, int remaining) {
        // Native loc_12A72 uses the same timer-first ordering for Push.
        if (remaining >= 0) {
            sprite.setAnimationTick(remaining);
            return;
        }
        int speed = Math.abs(sprite.getGSpeed());
        ScriptedVelocityAnimationProfile profile = resolveVelocityProfile();

        int pushId = profile != null ? profile.getPushAnimId() : -1;
        SpriteAnimationScript active = resolveScript(pushId, baseScript);
        if (active == null) {
            active = baseScript;
        }

        int delay = computeSpeedDelay(speed, 0x800, 6);
        updateScriptWithDelay(active, delay, 0);
    }

    private void updateScriptWithDelay(
            SpriteAnimationScript script,
            int delay,
            int frameOffset
    ) {
        if (script == null || script.frames().isEmpty()) {
            return;
        }

        sprite.setAnimationTick(delay);

        int frameIndex = sprite.getAnimationFrameIndex();
        if (frameIndex < 0) {
            frameIndex = 0;
            sprite.setAnimationFrameIndex(0);
        }
        if (frameIndex >= script.frames().size()) {
            if (!processEndAction(script)) {
                return;
            }
            frameIndex = sprite.getAnimationFrameIndex();
            if (frameIndex < 0 || frameIndex >= script.frames().size()) {
                frameIndex = 0;
                sprite.setAnimationFrameIndex(0);
            }
        }
        int mappingFrame = script.frames().get(frameIndex) + frameOffset;
        sprite.setMappingFrame(mappingFrame);
        sprite.setAnimationFrameIndex(frameIndex + 1);
    }

    private boolean processEndAction(SpriteAnimationScript script) {
        switch (script.endAction()) {
            case HOLD -> {
                sprite.setAnimationFrameIndex(script.frames().size() - 1);
                return true;
            }
            case LOOP_BACK -> {
                sprite.setAnimationFrameIndex(resolveLoopBackIndex(script));
                return true;
            }
            case SWITCH -> {
                int nextAnimId = script.endParam();
                if (nextAnimId == sprite.getAnimationId()) {
                    sprite.setAnimationFrameIndex(0);
                    return true;
                }
                // $FD executes after the movement routine in the same player
                // slot, so it always publishes the target raw anim byte even
                // if braking refreshed Stop earlier in that slot. Native code
                // does not update prev_anim, anim_frame, or the skid condition
                // here: a continuing brake writes Stop again next frame and
                // resumes at this same $FD command instead of restarting it.
                SpriteAnimationProfile profile = sprite.getAnimationProfile();
                boolean switchingFromSkid = profile instanceof ScriptedVelocityAnimationProfile velocityProfile
                        && velocityProfile.getSkidAnimId() == sprite.getAnimationId();
                boolean skidRefreshed = switchingFromSkid
                        && sprite.getMovementManager() instanceof PlayableSpriteMovement movement
                        && movement.isSkidAnimationRefreshedThisFrame();
                sprite.setAnimationId(nextAnimId);
                if (skidRefreshed) {
                    return false;
                }
                if (switchingFromSkid) {
                    // Engine-only latch: once braking no longer selects Stop,
                    // the native raw-animation switch ends the skid state.
                    sprite.setSkidding(false);
                }
                resetScriptState();
                return false;
            }
            case LOOP -> {
                sprite.setAnimationFrameIndex(0);
                return true;
            }
            default -> {
                sprite.setAnimationFrameIndex(0);
                return true;
            }
        }
    }

    private int computeSpeedDelay(int speedSubpixels, int base, int shift) {
        int value = base - speedSubpixels;
        if (value < 0) {
            value = 0;
        }
        return value >> shift;
    }

    private ScriptedVelocityAnimationProfile resolveVelocityProfile() {
        SpriteAnimationProfile profile = sprite.getAnimationProfile();
        if (profile instanceof ScriptedVelocityAnimationProfile velocityProfile) {
            return velocityProfile;
        }
        return null;
    }

    private Integer resolveDesiredAnimationId(SpriteAnimationProfile profile, int frameCounter) {
        if (profile == null) {
            return null;
        }
        int scriptCount = sprite.getAnimationSet().getScriptCount();
        if (pushUsesWalkSpecialHandler()
                && profile instanceof ScriptedVelocityAnimationProfile velocityProfile) {
            return velocityProfile.resolveAnimationId(sprite, frameCounter, scriptCount, false);
        }
        return profile.resolveAnimationId(sprite, frameCounter, scriptCount);
    }

    private boolean pushUsesWalkSpecialHandler() {
        ScriptedVelocityAnimationProfile profile = resolveVelocityProfile();
        return profile != null && profile.isPushUsesWalkSpecialHandler();
    }

    private int resolveRunThreshold(ScriptedVelocityAnimationProfile profile) {
        if (profile == null) {
            return DEFAULT_RUN_SPEED_THRESHOLD;
        }
        int threshold = profile.getRunSpeedThreshold();
        return threshold > 0 ? threshold : DEFAULT_RUN_SPEED_THRESHOLD;
    }

    private SpriteAnimationScript resolveScript(int scriptId, SpriteAnimationScript fallback) {
        if (scriptId < 0 || sprite.getAnimationSet() == null) {
            return fallback;
        }
        SpriteAnimationScript script = sprite.getAnimationSet().getScript(scriptId);
        return script != null ? script : fallback;
    }

    /**
     * Computes the slope-based frame offset for walk/run animations.
     *
     * <p>ROM reference (S2: s2.asm:38077-38111, S1: 01 Sonic.asm:1699-1734):
     * <ul>
     *   <li>S2: angle pre-adjusted by -1 for positive values, walk offset = d0*4, run = d0*2</li>
     *   <li>S1: no angle pre-adjust, walk offset = d0*3 (d0+d0/2 then *2), run = d0*2</li>
     * </ul>
     * <p>Rather than hardcoding the multiplier, we derive it from the animation script's
     * frame count: {@code offset = d0 * (framesPerSet / 2)} where framesPerSet is the
     * number of frames in the base angle set (the script's frame list size).
     */
    private int resolveSlopeOffset(SpriteAnimationScript activeScript, int configuredStride) {
        int d0 = sprite.getAngle() & 0xFF;

        // S2 only: subtract 1 from positive non-zero angles (s2.asm:38078-38080)
        ScriptedVelocityAnimationProfile velocityProfile = resolveVelocityProfile();
        if (velocityProfile != null && velocityProfile.isAnglePreAdjust()) {
            if (d0 > 0 && d0 < 0x80) {
                d0 -= 1;
            }
        }

        boolean facingLeft = Direction.LEFT.equals(sprite.getDirection());
        if (!facingLeft) {
            d0 = (~d0) & 0xFF;
        }
        d0 = (d0 + 0x10) & 0xFF;
        if ((d0 & 0x80) != 0) {
            sprite.setRenderFlips(!facingLeft, true);
        } else {
            sprite.setRenderFlips(facingLeft, false);
        }
        d0 = (d0 >> 4) & 0x6;

        if (configuredStride > 0) {
            return d0 * configuredStride;
        }

        // Derive the offset multiplier from the animation script's frame count.
        // Walk: S2 has 8 frames/set → d0*(8/2)=d0*4, S1 has 6 frames/set → d0*(6/2)=d0*3
        // Run:  Both have 4 frames/set → d0*(4/2)=d0*2
        // Super Run (2 frames/set):
        //   S2: lsr.b #1,d0 = d0/2 (compact mapping layout, s2.asm:38159)
        //   S3K: add.b d0,d0 = d0*2 (standard run spacing, s3.asm:22323)
        int framesPerSet = (activeScript != null && !activeScript.frames().isEmpty())
                ? activeScript.frames().size()
                : 4;
        if (framesPerSet < 4) {
            if (velocityProfile != null && velocityProfile.isCompactSuperRunSlope()) {
                return d0 >> 1;
            }
            return d0 * 2;
        }
        return d0 * (framesPerSet / 2);
    }

    private int resolveLoopBackIndex(SpriteAnimationScript script) {
        int loopBack = script.endParam();
        if (loopBack <= 0) {
            return 0;
        }
        int target = script.frames().size() - loopBack;
        if (target < 0) {
            return 0;
        }
        return target;
    }

    private void resetScriptState() {
        sprite.setAnimationFrameIndex(0);
        sprite.setAnimationTick(0);
        lastAnimationId = sprite.getAnimationId();
    }

    private void clearPushForAnimationChange(ScriptedVelocityAnimationProfile profile,
                                             int frameCounter, int scriptCount) {
        // ROM Animate_Sonic/Animate_Tails clear Status_Push whenever the anim byte
        // differs from prev_anim, then store anim into prev_anim
        // (s2.asm:38033-38038,40879-40884; sonic3k.asm:29359-29364,29681-29686).
        // The byte that drives this is the real ROM anim byte the movement/state
        // code writes (roll/air/walk/wait/balance/...), NOT the engine's push
        // render substitution: ROM shows the push frames inside the walk script's
        // special handler while the anim byte stays at the movement value
        // (Animate_Sonic loc_12A72, sonic3k.asm:24832). Resolve the anim id with
        // the push render substitution disabled and compare against the previous
        // frame's. Track every grounded scripted frame so prev_anim stays current
        // even when no push is set (push-clear is then a no-op, as in ROM).
        // S1 leaves the clear behind FixBugs.
        Integer resolved = profile.resolveAnimationId(sprite, frameCounter, scriptCount, false);
        if (resolved == null) {
            // Object-controlled / move-locked: ROM movement routine does not run,
            // so prev_anim is not updated here. Leave the tracker untouched.
            return;
        }
        int animByteId = groundMoveAnimByte(profile, resolved.intValue());
        int prevAnimByteId = lastGroundMovementAnimId;
        lastGroundMovementAnimId = animByteId;

        if (!sprite.getPushing()) {
            return;
        }
        PlayerAnimationRules animationRules = playerAnimationRulesOrNull();
        if (animationRules == null || !animationRules.animationChangeClearsPush()) {
            return;
        }
        if (prevAnimByteId < 0 || animByteId == prevAnimByteId) {
            return;
        }

        sprite.setPushing(false);
    }

    /**
     * Collapses the engine's distinct Run animation id onto the Walk id for the
     * purpose of the ROM {@code anim != prev_anim} push-clear comparison.
     *
     * <p>In both S2 and S3K the ground directional-movement routines write the
     * Walk animation id into the {@code anim} byte regardless of speed — S2
     * {@code Sonic_MoveRight}/{@code Sonic_MoveLeft} (s2.asm:36956,36891
     * {@code move.b #AniIDSonAni_Walk,anim(a0)}), S3K {@code sub_14CAC}/
     * {@code sub_14C20} and {@code SonicKnux_Move} (sonic3k.asm:28122,28056,
     * 22811,22877 {@code move.b #0,anim(a0)}). The Run frames are a render-time
     * selection inside that same {@code AniXXX00} script (S2 SonAni_Walk vs
     * SonAni_Run pointers are dispatched by speed in the animation routine; S3K
     * {@code Animate_Sonic}/{@code Animate_Tails} loc_15A14 selects Run frames
     * by {@code ground_vel} within the Walk script). The engine instead models
     * Run as its own animation id, so a Run->Walk transition that ROM never
     * records as an {@code anim}-byte change would otherwise trip
     * {@code Animate}'s {@code anim != prev_anim} push-clear. Treating Run as the
     * Walk byte here matches the ROM comparison: a CPU sidekick decelerating
     * from a run into a wall keeps Status_Push across the speed step
     * (sonic3k.asm:29360-29364,28122 — {@code anim} stays Walk so push is not
     * cleared).
     */
    private static int groundMoveAnimByte(ScriptedVelocityAnimationProfile profile, int animId) {
        return animId == profile.getRunAnimId() ? profile.getWalkAnimId() : animId;
    }

}
