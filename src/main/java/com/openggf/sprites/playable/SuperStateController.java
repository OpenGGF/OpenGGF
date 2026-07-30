package com.openggf.sprites.playable;

import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.LevelState;
import com.openggf.game.ModApi;
import com.openggf.game.PhysicsProfile;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationProfile;

import java.util.logging.Logger;

@ModApi
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

    /** Semantic capability query; game-specific controllers override when active. */
    public boolean isHyperFormActive() {
        return false;
    }

    /** Semantic query for a live Super-Tails form and its auxiliary flock. */
    public boolean isSuperTailsFormActive() {
        return false;
    }

    /** Semantic powered-form hook evaluated only after a successful wall latch. */
    public boolean triggerPoweredWallImpact(int preZeroGroundSpeed) {
        return false;
    }

    /** Semantic powered-form hook invoked after a successful airborne dash. */
    public void triggerPoweredAirDashEffects(ObjectManager objectManager) {
    }

    /** Draws any game-owned powered-form trail before the live player frame. */
    public void renderPoweredTrail() {
    }

    public void debugActivate() {
        if (state != SuperState.NORMAL || !transformationSupported()) return;
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
        if (!transformationSupported() || state != SuperState.NORMAL || player.isSuperSonic()
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
                paletteTimer, transformFramesRemaining, -1, -1L, -1L);
    }

    /**
     * Creates a snapshot with a game-owned presentation tier.
     *
     * <p>The generic controller treats the value as opaque. S3K uses it to keep
     * Super/Hyper/Super-Tails presentation stable across rewind.
     */
    protected final RewindState createRewindState(int paletteState, int paletteFrame,
                                                   int paletteTimer, int transformFramesRemaining,
                                                   int presentationTier) {
        return new RewindState(state, ringDrainCounter, paletteState, paletteFrame,
                paletteTimer, transformFramesRemaining, presentationTier, -1L, -1L);
    }

    protected final RewindState createRewindState(int paletteState, int paletteFrame,
                                                   int paletteTimer, int transformFramesRemaining,
                                                   int presentationTier, long savedNormalPalette,
                                                   long savedNormalUnderwaterPalette) {
        return new RewindState(state, ringDrainCounter, paletteState, paletteFrame,
                paletteTimer, transformFramesRemaining, presentationTier,
                savedNormalPalette, savedNormalUnderwaterPalette);
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

    @com.openggf.game.ModApi
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
        var levelManager = player.currentLevelManager();
        if (levelManager == null) return null;
        Level level = levelManager.getCurrentLevel();
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

    /** Character-roster eligibility gate shared by every transformation entry point. */
    private boolean transformationSupported() {
        com.openggf.game.CharacterKey key = player.boundCharacterKey;
        if (key == null) return false;
        if (key.isBuiltin()) return true;
        com.openggf.game.session.WorldSession world =
                com.openggf.game.session.SessionManager.getCurrentWorldSession();
        return world != null && world.getPlayableCharacterRegistry().find(key)
                .map(com.openggf.game.CharacterDefinition::supportsSuperForm)
                .orElse(false);
    }

    // --- Core logic ---
    private void checkTransformationTrigger() {
        if (!usesAutomaticJumpTrigger()) return;
        if (!canTransform()) return;
        if (player.getAir() && player.isJumping() && player.getYSpeed() >= -0x100 && player.getYSpeed() <= 0) {
            startTransformation();
        }
    }

    private boolean canTransform() {
        if (!transformationSupported()) return false;
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
        // ROM: move.b #$81,obj_control(a0) - freeze physics during transformation
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        // ROM: move.b #$1F,anim(a0) - play transformation sparkle animation
        player.setForcedAnimationId(getTransformationAnimationId());
        onTransformationStarted();
    }

    private void updateTransformation() {
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

    @com.openggf.game.ModApi
    public record RewindState(
            SuperState state,
            int ringDrainCounter,
            int paletteState,
            int paletteFrame,
            int paletteTimer,
            int transformFramesRemaining,
            int presentationTier,
            long savedNormalPalette,
            long savedNormalUnderwaterPalette
    ) {}
}
