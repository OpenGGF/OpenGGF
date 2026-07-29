package com.openggf.game.sonic3k;

import com.openggf.audio.GameMusic;
import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.SuperState;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.level.WaterSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * S3K-specific Super Sonic state controller.
 *
 * <p>Palette cycling uses ROM data from {@code PalCycle_SuperSonic} (0x398E).
 * The 60-byte table contains 10 frames of 6 bytes each (3 Mega Drive colors per frame).
 * Colors are written to palette line 0, color indices 2-4 (Sonic's body colors).
 *
 * <p>Palette states:
 * <ul>
 *   <li>0 = off (normal palette)</li>
 *   <li>1 = fading in (transformation) - timer 1, advance 6 bytes, complete at offset $24</li>
 *   <li>-1 = cycling (active Super Sonic) - timer 6, advance 6 bytes, wrap at $36 back to $24</li>
 *   <li>2 = fading out (reverting) - timer 3, retreat 6 bytes, stop at 0</li>
 * </ul>
 */
public class Sonic3kSuperStateController extends SuperStateController {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSuperStateController.class.getName());

    /** Palette fade state: 0=off, 1=fading in, -1=cycling, 2=fading out. */
    private int paletteState;
    /** Current palette frame byte offset into the cycling data (increments by 6). */
    private int paletteFrame;
    /** Countdown timer between palette frame advances. */
    private int paletteTimer;
    /** Frames remaining in the transformation animation. */
    private int transformFramesRemaining;

    /** Raw ROM palette data (60 bytes: 10 frames x 3 colors x 2 bytes). */
    private byte[] paletteData;
    private byte[] hyperPaletteData;
    private byte[] superTailsPaletteData;
    private byte[] superKnucklesPaletteData;
    private byte[] activePaletteData;
    private int activeCycleStart = 0x24;
    private int activeCycleEnd = 0x36;
    private int activeCycleTimer = 6;
    private int activeWrapCycleTimer = 6;
    private byte[] savedNormalPalette;
    private byte[] savedNormalUnderwaterPalette;

    /** Retained for lazy loads of the zone's underwater cycle table. */
    private RomByteReader romReader;
    /** Underwater cycle tables, keyed by ROM address. */
    private final Map<Integer, byte[]> underwaterPaletteData = new HashMap<>();

    /** Super Sonic animation set (loaded from ROM). */
    private SpriteAnimationSet superAnimSet;
    /** Normal animation set (saved on activation, restored on revert). */
    private SpriteAnimationSet normalAnimSet;

    /** Super Sonic sprite renderer (loaded from ROM, uses Map_SuperSonic / PLC_SuperSonic). */
    private PlayerSpriteRenderer superRenderer;
    /** Normal sprite renderer (saved on activation, restored on revert). */
    private PlayerSpriteRenderer normalRenderer;

    /** Form selected when transformation starts; never inferred during rendering. */
    private S3kFormTier activeFormTier = S3kFormTier.NORMAL;

    /** Palette line index where Sonic's colors reside. */
    private static final int SONIC_PALETTE_INDEX = 0;
    /** First color index to write (palette+4 bytes = color index 2). */
    private static final int FIRST_COLOR_INDEX = 2;
    /** Number of colors written per frame (S3K uses 3, vs S2's 4). */
    private static final int COLORS_PER_FRAME = 3;
    /** Bytes per palette frame (3 colors x 2 bytes). */
    private static final int BYTES_PER_FRAME = 6;
    /** Byte offset at which fade-in is complete (6 frames x 6 bytes = $24). */
    private static final int FADE_COMPLETE_OFFSET = 0x24;
    /** Byte offset at which cycling wraps ($36 = 9 frames x 6 bytes). */
    private static final int CYCLE_WRAP_OFFSET = 0x36;

    public Sonic3kSuperStateController(AbstractPlayableSprite player) {
        super(player);
    }

    @Override
    public void reset() {
        if (activeFormTier != null && activeFormTier != S3kFormTier.NORMAL) {
            restoreCharacterPresentationImmediately();
        }
        super.reset();
        paletteState = 0;
        paletteFrame = 0;
        paletteTimer = 0;
        transformFramesRemaining = 0;
        activeFormTier = S3kFormTier.NORMAL;
    }

    @Override
    public void update() {
        if (player.getDead() && isSuper()) {
            debugDeactivate();
            return;
        }
        super.update();
    }

    @Override
    public RewindState captureRewindState() {
        return createRewindState(paletteState, paletteFrame, paletteTimer, transformFramesRemaining,
                activeFormTier.ordinal(), packPalette(savedNormalPalette),
                packPalette(savedNormalUnderwaterPalette));
    }

    @Override
    public void restoreRewindState(RewindState rewindState) {
        if (rewindState == null) {
            return;
        }
        restoreCoreRewindState(rewindState);
        paletteState = rewindState.paletteState();
        paletteTimer = rewindState.paletteTimer();
        transformFramesRemaining = rewindState.transformFramesRemaining();
        savedNormalPalette = unpackPalette(rewindState.savedNormalPalette());
        savedNormalUnderwaterPalette = unpackPalette(rewindState.savedNormalUnderwaterPalette());
        activeFormTier = formTierFromSnapshot(rewindState.presentationTier());
        configurePaletteForActiveTier();
        paletteFrame = rewindState.paletteFrame();
        reconcileRewindPresentation(rewindState.state());
    }

    @Override
    public void loadRomData(RomByteReader reader) {
        int addr = Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ADDR;
        int len = Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ENTRY_COUNT
                * Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ENTRY_SIZE;
        if (addr == 0 || addr + len > reader.size()) {
            LOGGER.warning("S3K Super Sonic palette data not available at ROM address 0x"
                    + Integer.toHexString(addr));
            return;
        }
        paletteData = reader.slice(addr, len);
        hyperPaletteData = reader.slice(
                Sonic3kConstants.PAL_CYCLE_HYPER_SONIC_ADDR,
                Sonic3kConstants.PAL_CYCLE_HYPER_SONIC_FRAME_COUNT * BYTES_PER_FRAME);
        superTailsPaletteData = reader.slice(
                Sonic3kConstants.PAL_CYCLE_SUPER_TAILS_ADDR,
                Sonic3kConstants.PAL_CYCLE_SUPER_TAILS_FRAME_COUNT * BYTES_PER_FRAME);
        superKnucklesPaletteData = reader.slice(
                Sonic3kConstants.PAL_CYCLE_SUPER_KNUCKLES_ADDR,
                Sonic3kConstants.PAL_CYCLE_SUPER_KNUCKLES_FRAME_COUNT * BYTES_PER_FRAME);
        activePaletteData = paletteData;
        romReader = reader;
        underwaterPaletteData.clear();
        LOGGER.fine("Loaded S3K Super Sonic palette data: " + len + " bytes from ROM 0x"
                + Integer.toHexString(addr));

        Sonic3kPlayerArt playerArt = new Sonic3kPlayerArt(reader);
        superAnimSet = playerArt.loadSuperSonicAnimationSet();
        if (superAnimSet != null) {
            LOGGER.fine("Loaded S3K Super Sonic animation set");
        }

        try {
            SpriteArtSet superArtSet = playerArt.loadFormArtSet("sonic", S3kFormTier.SUPER);
            if (superArtSet != null) {
                superRenderer = new PlayerSpriteRenderer(superArtSet);
                if (CrossGameFeatureProvider.isActive()) {
                    superRenderer.setRenderContext(
                            GameServices.crossGameFeatures().getDonorRenderContext());
                }
                LOGGER.fine("Loaded S3K Super Sonic sprite renderer");
            }
        } catch (Exception e) {
            LOGGER.warning("Could not load Super Sonic art set: " + e.getMessage());
        }
    }

    @Override
    protected int getRingDrainInterval() {
        return Sonic3kConstants.SUPER_SONIC_RING_DRAIN_INTERVAL;
    }

    @Override
    protected int getMinRingsToTransform() {
        return Sonic3kConstants.SUPER_SONIC_MIN_RINGS;
    }

    @Override
    protected boolean usesAutomaticJumpTrigger() {
        return false;
    }

    @Override
    protected boolean usesExplicitAirAbilityTrigger() {
        return true;
    }

    @Override
    protected int getTransformationAnimationId() {
        return player instanceof Tails ? 0x29 : super.getTransformationAnimationId();
    }

    @Override
    protected boolean hasTransformationEmeralds() {
        return getEligibleFormTier() != S3kFormTier.NORMAL;
    }

    /**
     * Selects the form permitted by current ROM progression state.
     * This is consulted for activation only; active presentation uses
     * {@link #getActiveFormTier()}.
     */
    public S3kFormTier getEligibleFormTier() {
        var gameState = player.currentGameState();
        if (player instanceof Tails) {
            if (!gameState.hasAllSuperEmeralds()) {
                return S3kFormTier.NORMAL;
            }
            return GameServices.sprites().getMainPlayable() == player
                    ? S3kFormTier.SUPER_TAILS
                    : S3kFormTier.NORMAL;
        }
        if (gameState.hasAllSuperEmeralds()) {
            return S3kFormTier.HYPER;
        }
        if (gameState.hasAllEmeralds() && !gameState.isEmeraldsConverted()) {
            return S3kFormTier.SUPER;
        }
        return S3kFormTier.NORMAL;
    }

    public S3kFormTier getActiveFormTier() {
        return activeFormTier;
    }

    @Override
    public boolean isHyperFormActive() {
        return isSuper() && activeFormTier == S3kFormTier.HYPER;
    }

    @Override
    protected boolean passesGameSpecificTransformGates() {
        if (player instanceof Tails) {
            return GameServices.sprites().getMainPlayable() == player;
        }
        if (player instanceof Knuckles) {
            return true;
        }
        ShieldType shield = player.getShieldType();
        boolean elementalShield = shield == ShieldType.FIRE
                || shield == ShieldType.LIGHTNING
                || shield == ShieldType.BUBBLE;
        return !elementalShield && player.getInvincibleFrames() <= 0;
    }

    @Override
    protected PhysicsProfile getSuperProfile() {
        // S3K Super Tails: max=$800, accel=$18, decel=$C0 (sonic3k.asm:26325-26327)
        // S3K Super Sonic: max=$A00, accel=$30, decel=$100 (sonic3k.asm:22084-22086)
        if (player instanceof Tails) {
            return PhysicsProfile.SONIC_3K_SUPER_TAILS;
        }
        if (player instanceof Knuckles) {
            return PhysicsProfile.SONIC_3K_SUPER_KNUCKLES;
        }
        return PhysicsProfile.SONIC_3K_SUPER_SONIC;
    }

    @Override
    protected PhysicsProfile getNormalProfile() {
        // On revert, use canonical "reset" values — NOT init (Character_Speeds) values.
        // ROM: speed shoes expire code sets $600/$C/$80 for all characters.
        if (player instanceof Tails) {
            return PhysicsProfile.SONIC_2_TAILS;
        }
        if (player instanceof Knuckles) {
            return PhysicsProfile.SONIC_3K_KNUCKLES;
        }
        return PhysicsProfile.SONIC_2_SONIC;
    }

    @Override
    protected void onTransformationStarted() {
        activeFormTier = getEligibleFormTier();
        captureNormalPalette();
        configurePaletteForActiveTier();
        paletteState = usesSonicFormPresentation() ? 1 : -1;
        paletteFrame = 0;
        paletteTimer = paletteState == 1 ? 1 : activeCycleTimer;
        transformFramesRemaining = 30;
        // Play transformation SFX
        try {
            if (CrossGameFeatureProvider.isActive()) {
                GameServices.audio().playDonorSfx(
                        GameServices.crossGameFeatures().getDonorGameId(),
                        Sonic3kSfx.SUPER_TRANSFORM.id);
            } else {
                GameServices.audio().playSfx(Sonic3kSfx.SUPER_TRANSFORM.id);
            }
        } catch (Exception e) {
            LOGGER.fine("Could not play transformation SFX: " + e.getMessage());
        }
    }

    @Override
    protected boolean updateTransformationAnimation() {
        if (!usesSonicFormPresentation()) {
            applyPaletteFrame(paletteFrame);
            advanceActivePaletteFrame();
            return true;
        }
        updatePaletteFade();
        transformFramesRemaining--;
        return transformFramesRemaining <= 0;
    }

    @Override
    protected void onSuperActivated() {
        paletteState = -1;
        paletteTimer = activeCycleTimer;
        if (usesSonicFormPresentation()) {
            paletteFrame = activeCycleStart;
        }
        // Play invincibility music (S3K Super Sonic uses mus_Invincibility)
        try {
            if (CrossGameFeatureProvider.isActive()) {
                GameServices.audio().playDonorMusic(
                        GameServices.crossGameFeatures().getDonorGameId(),
                        GameMusic.SUPER);
            } else {
                GameServices.audio().playMusic(GameMusic.SUPER);
            }
        } catch (Exception e) {
            LOGGER.fine("Could not play Super Sonic music: " + e.getMessage());
        }
        player.setInvincibleFrames(0);
        if (usesSonicFormPresentation() && superAnimSet != null) {
            if (normalAnimSet == null) {
                normalAnimSet = player.getAnimationSet();
            }
            player.setAnimationSet(superAnimSet);
        }
        // Swap to Super Sonic sprite renderer (different mappings/DPLCs)
        if (usesSonicFormPresentation() && superRenderer != null) {
            if (normalRenderer == null) {
                normalRenderer = player.getSpriteRenderer();
            }
            player.setSpriteRenderer(superRenderer);
        }
        player.setShieldVisible(false);
        LOGGER.info("Super Sonic activated (S3K)");
    }

    @Override
    protected void updateSuperPalette() {
        var paletteRegistry = GameServices.paletteOwnershipRegistryOrNull();
        if (paletteRegistry != null && paletteRegistry.isPaletteRotationDisabled()) {
            return;
        }
        if (paletteState != -1) return;

        paletteTimer--;
        if (paletteTimer >= 0) return;

        int frameOffset = paletteFrame;
        applyPaletteFrame(frameOffset);
        paletteTimer = activeCycleTimer;
        advanceActivePaletteFrame();
    }

    @Override
    protected void onRevertStarted() {
        paletteState = activeFormTier == S3kFormTier.SUPER && usesSonicFormPresentation() ? 2 : 0;
        paletteFrame = FADE_COMPLETE_OFFSET - BYTES_PER_FRAME;
        paletteTimer = 3;
        player.setInvincibleFrames(1);
        if (normalAnimSet != null) {
            player.setAnimationSet(normalAnimSet);
        }
        // Restore normal sprite renderer
        if (normalRenderer != null) {
            player.setSpriteRenderer(normalRenderer);
        }
        if (paletteState == 0) {
            restoreNormalPalette();
        }
        player.setShieldVisible(true);
        // Revert to zone music
        try {
            if (CrossGameFeatureProvider.isActive()) {
                GameServices.audio().endDonorMusicOverride(
                        GameServices.crossGameFeatures().getDonorGameId(),
                        GameMusic.SUPER);
            } else {
                GameServices.audio().endMusicOverride(GameMusic.SUPER);
            }
        } catch (Exception e) {
            LOGGER.fine("Could not revert Super Sonic music: " + e.getMessage());
        }
        LOGGER.info("Super Sonic deactivated (S3K)");
        activeFormTier = S3kFormTier.NORMAL;
    }

    private void reconcileRewindPresentation(SuperState restoredState) {
        boolean activeSuper = restoredState == SuperState.SUPER;
        reconcileRewindPhysicsAndAnimationProfile(activeSuper);
        if (activeSuper) {
            if (usesSonicFormPresentation() && superAnimSet != null) {
                player.setAnimationSet(superAnimSet);
            }
            if (usesSonicFormPresentation() && superRenderer != null) {
                player.setSpriteRenderer(superRenderer);
            }
            player.setShieldVisible(false);
        } else {
            if (normalAnimSet != null) {
                player.setAnimationSet(normalAnimSet);
            }
            if (normalRenderer != null) {
                player.setSpriteRenderer(normalRenderer);
            }
            player.setShieldVisible(true);
        }
    }

    private boolean usesSonicFormPresentation() {
        return !(player instanceof Tails) && !(player instanceof Knuckles);
    }

    private void configurePaletteForActiveTier() {
        switch (activeFormTier) {
            case SUPER -> {
                if (player instanceof Knuckles) {
                    activePaletteData = superKnucklesPaletteData;
                    activeCycleStart = 0;
                    activeCycleEnd = Sonic3kConstants.PAL_CYCLE_SUPER_KNUCKLES_FRAME_COUNT
                            * BYTES_PER_FRAME;
                    activeCycleTimer = 2;
                    activeWrapCycleTimer = 0xE;
                } else {
                    activePaletteData = paletteData;
                    activeCycleStart = FADE_COMPLETE_OFFSET;
                    activeCycleEnd = CYCLE_WRAP_OFFSET;
                    activeCycleTimer = 6;
                    activeWrapCycleTimer = 6;
                }
            }
            case HYPER -> {
                if (player instanceof Knuckles) {
                    activePaletteData = superKnucklesPaletteData;
                    activeCycleStart = 0;
                    activeCycleEnd = Sonic3kConstants.PAL_CYCLE_SUPER_KNUCKLES_FRAME_COUNT
                            * BYTES_PER_FRAME;
                    activeCycleTimer = 2;
                    activeWrapCycleTimer = 0xE;
                } else {
                    activePaletteData = hyperPaletteData;
                    activeCycleStart = 0;
                    activeCycleEnd = Sonic3kConstants.PAL_CYCLE_HYPER_SONIC_FRAME_COUNT
                            * BYTES_PER_FRAME;
                    activeCycleTimer = 4;
                    activeWrapCycleTimer = 4;
                }
            }
            case SUPER_TAILS -> {
                activePaletteData = superTailsPaletteData;
                activeCycleStart = 0;
                activeCycleEnd = Sonic3kConstants.PAL_CYCLE_SUPER_TAILS_FRAME_COUNT
                        * BYTES_PER_FRAME;
                activeCycleTimer = 0xB;
                activeWrapCycleTimer = 0xB;
            }
            case NORMAL -> {
                activePaletteData = paletteData;
                activeCycleStart = FADE_COMPLETE_OFFSET;
                activeCycleEnd = CYCLE_WRAP_OFFSET;
                activeCycleTimer = 6;
                activeWrapCycleTimer = 6;
            }
        }
        paletteFrame = activeCycleStart;
    }

    private void advanceActivePaletteFrame() {
        paletteFrame += BYTES_PER_FRAME;
        if (paletteFrame >= activeCycleEnd) {
            paletteFrame = activeCycleStart;
            paletteTimer = activeWrapCycleTimer;
        }
    }

    private static S3kFormTier formTierFromSnapshot(int ordinal) {
        S3kFormTier[] tiers = S3kFormTier.values();
        return ordinal >= 0 && ordinal < tiers.length ? tiers[ordinal] : S3kFormTier.NORMAL;
    }

    /**
     * Begin a palette fade-out from an external trigger (e.g. AIZ intro Knuckles hit).
     * Matches ROM: {@code move.b #2,(Super_palette_status)} + {@code move.w #$1E,(Palette_frame)}.
     *
     * @param startFrame byte offset into PalCycle_SuperSonic to start the backwards fade from
     */
    public void beginPaletteRevert(int startFrame) {
        paletteState = 2;
        paletteFrame = startFrame;
        paletteTimer = 3;
    }

    @Override
    protected void updatePostRevertEffects() {
        if (paletteState == 2) {
            updatePaletteFade();
        }
    }

    private void updatePaletteFade() {
        if (paletteState == 0) return;

        paletteTimer--;
        if (paletteTimer >= 0) return;

        if (paletteState == 1) {
            paletteTimer = 1;
            int frameOffset = paletteFrame;
            paletteFrame += BYTES_PER_FRAME;

            if (paletteFrame >= FADE_COMPLETE_OFFSET) {
                paletteState = -1;
            }

            applyPaletteFrame(frameOffset, true);

        } else if (paletteState == 2) {
            paletteTimer = 3;
            int frameOffset = paletteFrame;
            paletteFrame -= BYTES_PER_FRAME;

            if (paletteFrame < 0) {
                paletteFrame = 0;
                paletteState = 0;
            }

            applyPaletteFrame(frameOffset);
        }
    }

    private void applyPaletteFrame(int frameOffset) {
        applyPaletteFrame(frameOffset, false);
    }

    private void applyPaletteFrame(int frameOffset, boolean sonicFadeFrame) {
        if (activePaletteData == null || activePaletteData.length == 0) return;
        if (frameOffset < 0 || frameOffset + BYTES_PER_FRAME > activePaletteData.length) return;

        PaletteTarget target = resolvePaletteTarget(SONIC_PALETTE_INDEX);
        if (target == null) return;

        LevelManager levelManager = GameServices.levelOrNull();
        Level level = levelManager != null ? levelManager.getCurrentLevel() : null;

        byte[] source = sonicFadeFrame
                ? paletteData : activePaletteData;
        if (source == null || frameOffset + BYTES_PER_FRAME > source.length) {
            return;
        }
        byte[] patch = new byte[BYTES_PER_FRAME];
        System.arraycopy(source, frameOffset, patch, 0, patch.length);
        applySurfacePalettePatch(target, level, patch);

        applyUnderwaterPaletteFrame(frameOffset, target.gpuLine(), levelManager, level, sonicFadeFrame);
    }

    /**
     * Mirrors the cycle frame into the water palette so Super Sonic stays gold
     * below the surface.
     *
     * <p>ROM: {@code SuperHyper_PalCycle_SonicApply} (sonic3k.asm:4666-4681)
     * writes {@code Water_palette+$04} from a zone-specific underwater table
     * whenever {@code Water_flag} is set.
     */
    private void applyUnderwaterPaletteFrame(int frameOffset, int gpuLine,
                                             LevelManager levelManager, Level level,
                                             boolean sonicFadeFrame) {
        // The donor palette in cross-game mode lives above the four level lines and
        // has no underwater counterpart.
        if (gpuLine != SONIC_PALETTE_INDEX || levelManager == null || level == null) {
            return;
        }
        WaterSystem water = GameServices.waterOrNull();
        if (water == null) {
            return;
        }
        int zone = level.getZoneIndex();
        int act = levelManager.getCurrentAct();
        if (!water.hasWater(zone, act)) {
            return;
        }
        byte[] underwaterData = underwaterPaletteTable(zone, act, sonicFadeFrame);
        if (underwaterData == null || frameOffset + BYTES_PER_FRAME > underwaterData.length) {
            return;
        }
        byte[] patch = new byte[BYTES_PER_FRAME];
        System.arraycopy(underwaterData, frameOffset, patch, 0, patch.length);
        int[] indices = activePaletteColorIndices();
        for (int i = 0; i < indices.length; i++) {
            byte[] word = {patch[i * 2], patch[i * 2 + 1]};
            S3kPaletteWriteSupport.applyUnderwaterContiguousPatch(
                    GameServices.paletteOwnershipRegistryOrNull(), level, GameServices.graphics(),
                    S3kPaletteOwners.SUPER_PALETTE, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    SONIC_PALETTE_INDEX, indices[i], word);
        }
    }

    /**
     * Returns the underwater cycle table for the zone/act, falling back to the
     * surface table when the zone declares no underwater variant.
     */
    private byte[] underwaterPaletteTable(int zone, int act, boolean sonicFadeFrame) {
        if (sonicFadeFrame && usesSonicFormPresentation()) {
            // Sonic and Hyper Sonic share the six-frame transformation fade.
        } else if (activeFormTier == S3kFormTier.HYPER) {
            return activePaletteData;
        }
        if (!usesSonicFormPresentation()) {
            return activePaletteData;
        }
        var waterData = GameServices.module().getWaterDataProvider();
        int addr = waterData != null
                ? waterData.getUnderwaterSuperPaletteCycleAddress(zone, act)
                : 0;
        if (addr <= 0 || romReader == null) {
            return paletteData;
        }
        return underwaterPaletteData.computeIfAbsent(addr, address -> {
            int len = Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ENTRY_COUNT
                    * Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ENTRY_SIZE;
            if (address + len > romReader.size()) {
                LOGGER.warning("S3K underwater Super Sonic palette data not available at ROM address 0x"
                        + Integer.toHexString(address));
                return paletteData;
            }
            return romReader.slice(address, len);
        });
    }

    private void applySurfacePalettePatch(PaletteTarget target, Level level, byte[] patch) {
        int[] indices = activePaletteColorIndices();
        for (int i = 0; i < indices.length; i++) {
            byte[] word = {patch[i * 2], patch[i * 2 + 1]};
            S3kPaletteWriteSupport.applyContiguousPatchToPalette(
                    GameServices.paletteOwnershipRegistryOrNull(), level, GameServices.graphics(),
                    target.palette(), target.gpuLine(), S3kPaletteOwners.SUPER_PALETTE,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, indices[i], word);
        }
    }

    private int[] activePaletteColorIndices() {
        return player instanceof Tails ? new int[] {8, 9, 11} : new int[] {2, 3, 4};
    }

    java.util.List<Integer> activePaletteColorIndicesForTest() {
        return java.util.Arrays.stream(activePaletteColorIndices()).boxed().toList();
    }

    int activePaletteReloadForTest() {
        return activeCycleTimer;
    }

    int activePaletteWrapReloadForTest() {
        return activeWrapCycleTimer;
    }

    int activePaletteFrameCountForTest() {
        return (activeCycleEnd - activeCycleStart) / BYTES_PER_FRAME;
    }

    private static long packPalette(byte[] palette) {
        if (palette == null) {
            return -1L;
        }
        long packed = 0;
        for (byte value : palette) {
            packed = (packed << 8) | (value & 0xFFL);
        }
        return packed;
    }

    private static byte[] unpackPalette(long packed) {
        if (packed == -1L) {
            return null;
        }
        byte[] palette = new byte[BYTES_PER_FRAME];
        for (int i = palette.length - 1; i >= 0; i--) {
            palette[i] = (byte) packed;
            packed >>>= 8;
        }
        return palette;
    }

    private void captureNormalPalette() {
        PaletteTarget target = resolvePaletteTarget(SONIC_PALETTE_INDEX);
        if (target == null) {
            return;
        }
        savedNormalPalette = new byte[BYTES_PER_FRAME];
        int[] indices = activePaletteColorIndices();
        for (int i = 0; i < indices.length; i++) {
            int word = com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                    target.palette().getColor(indices[i]));
            savedNormalPalette[i * 2] = (byte) (word >>> 8);
            savedNormalPalette[i * 2 + 1] = (byte) word;
        }
        captureNormalUnderwaterPalette(indices);
    }

    private void restoreNormalPalette() {
        if (savedNormalPalette == null) {
            return;
        }
        PaletteTarget target = resolvePaletteTarget(SONIC_PALETTE_INDEX);
        if (target == null) {
            return;
        }
        LevelManager levelManager = GameServices.levelOrNull();
        Level level = levelManager != null ? levelManager.getCurrentLevel() : null;
        int[] indices = activePaletteColorIndices();
        for (int i = 0; i < indices.length; i++) {
            byte[] word = {savedNormalPalette[i * 2], savedNormalPalette[i * 2 + 1]};
            S3kPaletteWriteSupport.applyContiguousPatchToPalette(
                    GameServices.paletteOwnershipRegistryOrNull(), level, GameServices.graphics(),
                    target.palette(), target.gpuLine(), S3kPaletteOwners.SUPER_PALETTE,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, indices[i], word);
        }
        if (usesSonicFormPresentation()) {
            restoreNormalUnderwaterSonicPalette(levelManager, level);
        } else {
            restoreSavedNormalUnderwaterPalette(levelManager, level, indices);
        }
    }

    private void captureNormalUnderwaterPalette(int[] indices) {
        LevelManager levelManager = GameServices.levelOrNull();
        Level level = levelManager != null ? levelManager.getCurrentLevel() : null;
        WaterSystem water = GameServices.waterOrNull();
        if (level == null || water == null) {
            return;
        }
        Palette[] palettes = water.getUnderwaterPalette(level.getZoneIndex(), levelManager.getCurrentAct());
        if (palettes == null || palettes.length == 0 || palettes[0] == null) {
            return;
        }
        savedNormalUnderwaterPalette = new byte[BYTES_PER_FRAME];
        for (int i = 0; i < indices.length; i++) {
            int word = com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                    palettes[0].getColor(indices[i]));
            savedNormalUnderwaterPalette[i * 2] = (byte) (word >>> 8);
            savedNormalUnderwaterPalette[i * 2 + 1] = (byte) word;
        }
    }

    private void restoreSavedNormalUnderwaterPalette(LevelManager levelManager, Level level, int[] indices) {
        if (savedNormalUnderwaterPalette == null || levelManager == null || level == null) {
            return;
        }
        for (int i = 0; i < indices.length; i++) {
            byte[] word = {
                    savedNormalUnderwaterPalette[i * 2],
                    savedNormalUnderwaterPalette[i * 2 + 1]
            };
            S3kPaletteWriteSupport.applyUnderwaterContiguousPatch(
                    GameServices.paletteOwnershipRegistryOrNull(), level, GameServices.graphics(),
                    S3kPaletteOwners.SUPER_PALETTE, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    SONIC_PALETTE_INDEX, indices[i], word);
        }
    }

    private void restoreNormalUnderwaterSonicPalette(LevelManager levelManager, Level level) {
        if (!usesSonicFormPresentation() || levelManager == null || level == null || romReader == null) {
            return;
        }
        WaterSystem water = GameServices.waterOrNull();
        if (water == null || !water.hasWater(level.getZoneIndex(), levelManager.getCurrentAct())) {
            return;
        }
        var waterData = GameServices.module().getWaterDataProvider();
        int address = waterData != null
                ? waterData.getUnderwaterSuperPaletteCycleAddress(
                        level.getZoneIndex(), levelManager.getCurrentAct())
                : 0;
        byte[] patch = address > 0 && address + BYTES_PER_FRAME <= romReader.size()
                ? romReader.slice(address, BYTES_PER_FRAME)
                : paletteData != null ? java.util.Arrays.copyOf(paletteData, BYTES_PER_FRAME) : null;
        if (patch != null) {
            S3kPaletteWriteSupport.applyUnderwaterContiguousPatch(
                    GameServices.paletteOwnershipRegistryOrNull(), level, GameServices.graphics(),
                    S3kPaletteOwners.SUPER_PALETTE, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    SONIC_PALETTE_INDEX, FIRST_COLOR_INDEX, patch);
        }
    }

    private void restoreCharacterPresentationImmediately() {
        player.setSuperSonic(false);
        com.openggf.sprites.playable.ObjectControlState.none().applyTo(player);
        player.setForcedAnimationId(-1);
        player.applyExternalPhysicsProfile(getNormalProfile());
        if (normalAnimSet != null) {
            player.setAnimationSet(normalAnimSet);
        }
        if (normalRenderer != null) {
            player.setSpriteRenderer(normalRenderer);
        }
        player.setShieldVisible(true);
        restoreNormalPalette();
        try {
            if (CrossGameFeatureProvider.isActive()) {
                GameServices.audio().endDonorMusicOverride(
                        GameServices.crossGameFeatures().getDonorGameId(), GameMusic.SUPER);
            } else {
                GameServices.audio().endMusicOverride(GameMusic.SUPER);
            }
        } catch (Exception e) {
            LOGGER.fine("Could not clear Super music during lifecycle reset: " + e.getMessage());
        }
    }
}
