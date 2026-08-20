package com.openggf.sprites.playable;

import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.LevelState;
import com.openggf.game.PhysicsProfile;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationProfile;

import java.util.logging.Logger;

public abstract class SuperStateController {
    private static final Logger LOGGER = Logger.getLogger(SuperStateController.class.getName());

    protected final AbstractPlayableSprite player;
    private SuperState state = SuperState.NORMAL;
    private int ringDrainCounter;
    private SpriteAnimationProfile normalAnimProfile;
    private boolean romDataPreLoaded;

    protected SuperStateController(AbstractPlayableSprite player) {
        this.player = player;
        reset();
    }

    public void reset() {
        state = SuperState.NORMAL;
        ringDrainCounter = 0;
    }

    public void update() {
        switch (state) {
            case NORMAL -> checkTransformationTrigger();
            case TRANSFORMING -> updateTransformation();
            case SUPER -> updateSuper();
            default -> {} // REVERTING not used (revert is instant in ROM)
        }
        // Post-revert effects (e.g. palette fade-out) run every frame regardless of state
        updatePostRevertEffects();
    }

    public SuperState getState() { return state; }

    public boolean isSuper() {
        return state == SuperState.SUPER || state == SuperState.TRANSFORMING;
    }

    public void debugActivate() {
        if (state != SuperState.NORMAL) return;
        player.addRings(50);
        startTransformation();
        LOGGER.info("Debug: Super Sonic transformation started");
    }

    /**
     * Starts a monitor-triggered transformation after the monitor has already
     * awarded its rings. S3K subtype 9 monitors bypass the normal jump and
     * emerald checks; this entry point deliberately does not add rings.
     */
    public boolean activateFromMonitor() {
        if (state != SuperState.NORMAL || player.isSuperSonic()
                || player.getDead() || player.isHurt() || player.isDebugMode()) {
            return false;
        }
        startTransformation();
        return true;
    }

    /**
     * Starts the S3K air-ability transformation path. Unlike the normal S2 jump
     * trigger, S3K tests Super/Hyper eligibility inside Sonic_ShieldMoves before
     * dispatching insta-shield.
     */
    public boolean activateFromAirAbility() {
        if (!usesExplicitAirAbilityTrigger()) {
            return false;
        }
        if (!canTransform()) {
            return false;
        }
        startTransformation();
        return true;
    }

    public void debugDeactivate() {
        if (state == SuperState.NORMAL) return;
        revertToNormal();
        LOGGER.info("Debug: Super Sonic deactivated");
    }

    /**
     * Loads game-specific ROM data (palette cycling, etc.).
     * Called once during level initialization. Default is no-op.
     *
     * @param reader ROM byte reader for data access
     */
    public void loadRomData(RomByteReader reader) {
        // Default: no ROM data needed
    }

    public void setRomDataPreLoaded(boolean preLoaded) {
        this.romDataPreLoaded = preLoaded;
    }

    public boolean isRomDataPreLoaded() {
        return romDataPreLoaded;
    }

    /** Complete controller snapshot. Unsupported controllers return {@code null}. */
    public RewindState captureRewindState() {
        return null;
    }

    /** Restores a complete controller snapshot without replaying activation effects. */
    public void restoreRewindState(RewindState rewindState) {
        // Default deliberately unsupported: S2 owns a live stars-object reference.
    }

    protected final RewindState createRewindState(int paletteState, int paletteFrame,
                                                   int paletteTimer, int transformFramesRemaining) {
        return new RewindState(state, ringDrainCounter, paletteState, paletteFrame,
                paletteTimer, transformFramesRemaining);
    }

    protected final void restoreCoreRewindState(RewindState rewindState) {
        state = rewindState.state();
        ringDrainCounter = rewindState.ringDrainCounter();
    }

    protected final void reconcileRewindPhysicsAndAnimationProfile(boolean activeSuper) {
        player.applyExternalPhysicsProfile(activeSuper ? getSuperProfile() : getNormalProfile());
        SpriteAnimationProfile current = player.getAnimationProfile();
        if (normalAnimProfile == null && current instanceof ScriptedVelocityAnimationProfile) {
            normalAnimProfile = current;
        }
        if (normalAnimProfile instanceof ScriptedVelocityAnimationProfile normalVelocityProfile) {
            player.setAnimationProfile(activeSuper
                    ? normalVelocityProfile.withRunSpeedThreshold(getSuperRunSpeedThreshold())
                    : normalAnimProfile);
        }
    }

    // --- Palette target resolution for cross-game support ---

    protected record PaletteTarget(Palette palette, int gpuLine) {}

    /**
     * Resolves the correct palette and GPU line for Super Sonic palette cycling.
     * In cross-game mode, uses the donor render context's palette so cycling
     * affects the palette the sprite actually renders from (GPU line 4+).
     * In normal mode, uses the level's palette at the given logical line.
     *
     * @param logicalLine logical palette line (e.g., 0 for Sonic's palette)
     * @return the palette and GPU line to write to, or null if unavailable
     */
    protected PaletteTarget resolvePaletteTarget(int logicalLine) {
        if (CrossGameFeatureProvider.isActive()) {
            CrossGameFeatureProvider crossGame = player.currentCrossGameFeatures();
            RenderContext donor = crossGame.getDonorRenderContext();
            if (donor != null) {
                Palette p = donor.getPalette(logicalLine);
                if (p != null) {
                    return new PaletteTarget(p, donor.getEffectivePaletteLine(logicalLine));
                }
            }
        }
        Level level = player.currentLevelManager().getCurrentLevel();
        if (level == null) return null;
        Palette p = level.getPalette(logicalLine);
        return p != null ? new PaletteTarget(p, logicalLine) : null;
    }

    // --- Template methods for subclasses ---
    protected abstract int getRingDrainInterval();
    protected abstract int getMinRingsToTransform();
    protected abstract PhysicsProfile getSuperProfile();
    protected abstract PhysicsProfile getNormalProfile();
    protected abstract void onTransformationStarted();
    protected abstract boolean updateTransformationAnimation();
    protected abstract void onSuperActivated();
    protected abstract void updateSuperPalette();
    protected abstract void onRevertStarted();

    /** S2 transforms automatically during a jump; S3K overrides for explicit re-press activation. */
    protected boolean usesAutomaticJumpTrigger() {
        return true;
    }

    protected boolean usesExplicitAirAbilityTrigger() {
        return false;
    }

    protected boolean hasTransformationEmeralds() {
        return player.currentGameState().hasAllEmeralds();
    }

    protected boolean passesGameSpecificTransformGates() {
        return true;
    }

    /**
     * Called every frame regardless of state. Override to run post-revert effects
     * (e.g. palette fade-out animation that continues after state returns to NORMAL).
     */
    protected void updatePostRevertEffects() {
        // Default: no-op
    }

    /**
     * Returns the run speed threshold to use in the Super animation profile.
     * Default is 0x800 (ROM: cmpi.w #$800,d2 in SAnim_Super). Subclasses can
     * override for Hyper or other characters if needed.
     */
    protected int getSuperRunSpeedThreshold() {
        return 0x800;
    }

    /**
     * Returns the animation ID to play during the transformation.
     * Default is 0x1F (AniIDSupSonAni_Transform), used by both S2 and S3K.
     * ROM: move.b #$1F,anim(a0) in Sonic_Transform_Super.
     */
    protected int getTransformationAnimationId() {
        return 0x1F;
    }

    // --- Core logic ---
    private void checkTransformationTrigger() {
        if (!usesAutomaticJumpTrigger()) return;
        if (!canTransform()) return;
        // ROM Sonic_JumpHeight ends `tst.b y_vel(a0) / beq.s Sonic_CheckGoSuper`
        // (docs/s2disasm/s2.asm:37432-37434). On a big-endian word `tst.b` reads
        // the HIGH byte, so the gate is "y_vel's high byte is zero" -- the
        // $0000..$00FF window just PAST the apex, already falling slowly. It is
        // not a window around the apex: the whole of the rise, where y_vel is
        // negative, has a high byte of $FF and never passes.
        //
        // The engine tested -$100 <= y_vel <= 0 instead, which fires on the way
        // up. Across the complete-emerald run's ARZ1 segment that transformed
        // Sonic on the recording's frame 4014 with y_vel = $FF10, five frames
        // before the ROM's own transform at 4019 with y_vel = $0028 -- the
        // first frame whose high byte is zero, gravity being +$38 per frame.
        //
        // FixBugs = 0: the `if fixBugs` block at s2.asm:37468-37475 that clears
        // Status_Roll/RollJump and restores the standing radii is NOT assembled
        // into the shipped ROM, so a roll-jump transform keeps its rolling state
        // and its ball radii. Nothing here clears them, which is the accurate
        // path; the fixed branch would reset both.
        if (player.getAir() && player.isJumping()
                && (player.getYSpeed() & 0xFF00) == 0) {
            startTransformation();
        }
    }

    private boolean canTransform() {
        if (player.isSuperSonic()) return false;
        if (!hasTransformationEmeralds()) return false;
        if (player.getRingCount() < getMinRingsToTransform()) return false;
        if (player.getDead() || player.isHurt() || player.isDebugMode()) return false;
        if (player.isObjectControlled()) return false;
        LevelState levelState = player.currentLevelState();
        if (levelState != null && levelState.isTimerPaused()) return false;
        if (!passesGameSpecificTransformGates()) return false;
        return true;
    }

    private void startTransformation() {
        state = SuperState.TRANSFORMING;
        player.setSuperSonic(true);
        // ROM: move.b #$1F,anim(a0) - play transformation sparkle animation
        player.setForcedAnimationId(getTransformationAnimationId());
        onTransformationStarted();
        // ROM writes obj_control = $81 here (s2.asm:37479) but Obj01 reads that
        // byte at the TOP of its routine dispatch, so the write does not stop
        // the rest of this frame. Sonic_CheckGoSuper is reached from
        // Sonic_JumpHeight, the first call in Obj01_MdJump (s2.asm:37432), and
        // Sonic_ChgJumpDir and ObjectMoveAndFall still run behind it on the
        // same frame -- with the Super speeds this routine just installed. On
        // the complete-emerald run's ARZ1 transform that frame carries x_vel
        // $181 -> $121, a full doubled Super acceleration step of $60
        // (s2.asm:37483 sets acceleration $30), and y_vel $28 -> $60 of
        // gravity. Freezing here instead kept both at their pre-transform
        // values. The freeze is applied at the next tick, below.
    }

    private void updateTransformation() {
        // The frame after the write, which is where Obj01's dispatch reads it.
        // Idempotent: TRANSFORMING is entered once per transformation.
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        if (updateTransformationAnimation()) {
            state = SuperState.SUPER;
            player.applyExternalPhysicsProfile(getSuperProfile());
            swapToSuperAnimProfile();
            ringDrainCounter = getRingDrainInterval();
            onSuperActivated();
            // ROM: clr.b obj_control(a0) - unfreeze after transformation complete
            ObjectControlState.none().applyTo(player);
            player.setForcedAnimationId(-1);
        }
    }

    private void updateSuper() {
        // ROM: Sonic_Super checks Update_HUD_timer == 0 every frame.
        // When the signpost/egg prison clears the timer, Super Sonic reverts.
        // Do NOT use player.isObjectControlled() - many objects (CPZ pipes, grabbers)
        // set that flag temporarily, causing false detransformation.
        LevelState levelState = player.currentLevelState();
        if (levelState != null && levelState.isTimerPaused()) {
            revertToNormal();
            return;
        }
        updateSuperPalette();
        ringDrainCounter--;
        if (ringDrainCounter <= 0) {
            ringDrainCounter = getRingDrainInterval();
            player.addRings(-1);
            if (player.getRingCount() <= 0) {
                revertToNormal();
                return;
            }
        }
    }

    private void revertToNormal() {
        player.setSuperSonic(false);
        // Clear transformation freeze in case revert happens during transformation
        ObjectControlState.none().applyTo(player);
        player.setForcedAnimationId(-1);
        player.applyExternalPhysicsProfile(getNormalProfile());
        restoreNormalAnimProfile();
        onRevertStarted();
        state = SuperState.NORMAL;
    }

    private void swapToSuperAnimProfile() {
        SpriteAnimationProfile current = player.getAnimationProfile();
        if (current instanceof ScriptedVelocityAnimationProfile velocityProfile) {
            if (normalAnimProfile == null) {
                normalAnimProfile = current;
            }
            ScriptedVelocityAnimationProfile normalVelocityProfile =
                    (ScriptedVelocityAnimationProfile) normalAnimProfile;
            player.setAnimationProfile(normalVelocityProfile.withRunSpeedThreshold(getSuperRunSpeedThreshold()));
        }
    }

    private void restoreNormalAnimProfile() {
        if (normalAnimProfile != null) {
            player.setAnimationProfile(normalAnimProfile);
        }
    }

    public record RewindState(
            SuperState state,
            int ringDrainCounter,
            int paletteState,
            int paletteFrame,
            int paletteTimer,
            int transformFramesRemaining
    ) {}
}
