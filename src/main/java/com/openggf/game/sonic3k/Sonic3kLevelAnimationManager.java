package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.animation.SeamlessTransitionAnimationClock;
import com.openggf.level.animation.InitialLevelAnimationPass;

import java.nio.ByteBuffer;

/**
 * Combined level animation manager for Sonic 3 &amp; Knuckles.
 * Delegates to the pattern animator and palette cycler, and aggregates
 * snapshot state from both halves so a rewind restores visual state fully.
 */
public final class Sonic3kLevelAnimationManager implements AnimatedPatternManager, AnimatedPaletteManager,
        RewindSnapshottable<PatternAnimatorSnapshot>, AizVineAngleProvider,
        SeamlessTransitionAnimationClock, InitialLevelAnimationPass,
        com.openggf.level.animation.StageRingAnimationFrameProvider,
        com.openggf.game.internal.LevelPaletteFadeOwner, S3kPaletteCycleCounterWriter {

    /** See {@code Sonic2LevelAnimationManager.COMBINED_EXTRA_MAGIC} for rationale. */
    private static final byte COMBINED_EXTRA_MAGIC = (byte) 0xC3;
    private static final byte COMBINED_EXTRA_WITH_GLOBALS_MAGIC = (byte) 0xC4;

    private static final byte COMBINED_EXTRA_WITH_RING_CLOCK_MAGIC = (byte) 0xC5;
    /** Ring clock layout followed by the shared AnPal RAM bank. */
    private static final byte COMBINED_EXTRA_WITH_ANPAL_RAM_MAGIC = (byte) 0xC6;

    private final Sonic3kPatternAnimator patternAnimator;
    private final Sonic3kPaletteCycler paletteCycler;
    private final Sonic3kGlobalAnimationState globalAnimationState;
    private final boolean endingScene;
    /**
     * Animate_Palette branches to Pal_FromWhite instead of Pal_FromBlack for
     * $D01 and $1701 (sonic3k.asm:5023-5026), as loc_64DC's Pal_FillBlack does
     * to Pal_FillWhite (4992-4995). The ending owns its own white reveal; the
     * engine has no white presentation for the $1701 arena, so neither shows
     * the black fade.
     */
    private final boolean whiteLevelFade;
    // Whether this owner last published the level fade to the GraphicsManager;
    // derived from the captured timer, so rewind re-applies it.
    private boolean levelFadePresented;
    // Level_frame_counter of this animator's last Animate_Palette call; transient,
    // read only inside the frame that performs a seamless reload.
    private int lastAnimatePaletteFrame = Integer.MIN_VALUE;

    public Sonic3kLevelAnimationManager(RomByteReader reader, Level level,
                                        int zoneIndex, int actIndex, boolean isSkipIntro) {
        this(reader, level, zoneIndex, actIndex, isSkipIntro,
                new Sonic3kGlobalAnimationState());
    }

    Sonic3kLevelAnimationManager(RomByteReader reader, Level level,
                                 int zoneIndex, int actIndex, boolean isSkipIntro,
                                 Sonic3kGlobalAnimationState globalAnimationState) {
        this.endingScene=zoneIndex==0x0D&&actIndex==1;
        this.whiteLevelFade = endingScene
                || (zoneIndex == Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA && actIndex == 1);
        this.patternAnimator = new Sonic3kPatternAnimator(reader, level,
                zoneIndex, actIndex, isSkipIntro);
        // Resolve the palette ownership registry from the active gameplay mode
        // context. The fallback inside Sonic3kPaletteCycler covers headless tests
        // and the bootstrap window where the mode context is not yet wired; the
        // production path always finds a non-null registry here.
        // The cycler shares the module-owned AnPal bank, which outlives this manager
        // across seamless act reloads exactly as the ROM's Palette_cycle_* RAM does.
        this.paletteCycler = new Sonic3kPaletteCycler(reader, level, zoneIndex, actIndex,
                resolvePaletteRegistry(), null, globalAnimationState);
        this.globalAnimationState = globalAnimationState;
    }

    private static com.openggf.game.palette.PaletteOwnershipRegistry resolvePaletteRegistry() {
        try {
            return GameServices.paletteOwnershipRegistryOrNull();
        } catch (RuntimeException e) {
            return null;
        }
    }

    void publishAniPlcAtVBlank() {
        patternAnimator.publishAniPlcAtVBlank();
    }

    @Override
    public int stageRingAnimationFrame() { return globalAnimationState.ringFrame(); }

    /** ROM {@code Palette_fade_timer} is still counting down the fresh-level fade. */
    public boolean paletteFadeTimerRunning() {
        return globalAnimationState.paletteFadeTimer() != 0;
    }

    void resetFreshLevelRingAnimation() {
        globalAnimationState.resetFreshLevelRingAnimation();
        // The same loc_60DE clears the shared AnPal bank; seamless reloads skip it.
        globalAnimationState.resetFreshLevelPaletteCycles();
        globalAnimationState.armFreshLevelPaletteFade();
        // The same loc_64DC block ran Pal_FillBlack on lines 1-3, so the level
        // shows them black before LevelLoop's first Animate_Palette pass.
        presentLevelFade(false);
    }

    /**
     * {@code Pal_FromBlack} passes lines 1-3 hold at this {@code Palette_fade_timer},
     * or -1 when the level palette is not fading. {@code Level/loc_64DC}
     * (sonic3k.asm:7874-7878) sets {@code Palette_fade_info = $202F} (lines 1-3),
     * runs {@code Pal_FillBlack} and arms the timer with {@code $16};
     * {@code Animate_Palette} (5018-5028) then spends one pass per LevelLoop frame
     * while it decrements the timer, so {@code $16 - timer} passes have run. Line 0
     * (players, HUD, title card) is never faded. A negative timer is an event's
     * {@code ST} block (the {@code BMI} at 5019), which runs neither fade nor AnPal.
     */
    static int levelFadePassesAt(int paletteFadeTimer) {
        if (paletteFadeTimer <= 0
                || paletteFadeTimer > Sonic3kGlobalAnimationState.FRESH_LEVEL_PALETTE_FADE_FRAMES) {
            return -1;
        }
        int passes = Sonic3kGlobalAnimationState.FRESH_LEVEL_PALETTE_FADE_FRAMES - paletteFadeTimer;
        // Seven levels per channel, three channels: every colour has arrived after 21.
        return passes < PaletteFadePresentation.COLOR_STEPS ? passes : -1;
    }

    /**
     * Publishes the level's {@code Pal_FromBlack} state on lines 1-3. The fade
     * belongs to the Level routine, not to the title card: it runs whether or not a
     * card is presented, and a seamless reload never reaches loc_64DC to arm it.
     *
     * @param releaseRestored also release a level fade left by an earlier owner,
     *        for a rewind restore that may replace this animator's history
     */
    private void presentLevelFade(boolean releaseRestored) {
        int passes = whiteLevelFade ? -1 : levelFadePassesAt(globalAnimationState.paletteFadeTimer());
        if (passes < 0 && !levelFadePresented && !releaseRestored) {
            return;
        }
        GraphicsManager graphics = graphicsOrNull();
        if (graphics == null) {
            return;
        }
        if (passes >= 0) {
            graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_BLACK, passes,
                    PaletteFadePresentation.LINES_1_TO_3);
            levelFadePresented = true;
            return;
        }
        PaletteFadePresentation current = graphics.getPaletteFadePresentation();
        if (levelFadePresented || (current.mode() == PaletteFadePresentation.Mode.FROM_BLACK
                && current.lineMask() == PaletteFadePresentation.LINES_1_TO_3)) {
            graphics.clearPaletteFadePresentation();
        }
        levelFadePresented = false;
    }

    @Override
    public boolean presentsLevelFadeFromBlack() {
        return !whiteLevelFade && levelFadePassesAt(globalAnimationState.paletteFadeTimer()) >= 0;
    }

    private static GraphicsManager graphicsOrNull() {
        try {
            return GameServices.graphics();
        } catch (RuntimeException ignored) {
            // Focused tests drive the animators without an engine session.
            return null;
        }
    }

    @Override
    public void update() {
        patternAnimator.update();
        // D01 Animate_Palette belongs before Process_Sprites. Its AnPal entry
        // is empty; retain late object writes here without consuming the timer twice.
        paletteCycler.update(animatePaletteThisFrame());
        presentLevelFade(false);
        applyPoweredFormPaletteUploadVInt();
        // LevelLoop runs Process_Sprites before ChangeRingFrame
        // (sonic3k.asm:7888-7910). LevelFrameRuntimeUpdater calls this combined
        // manager after the object pass, so this is the runtime owner for the
        // independent AIZ_vine_angle word advanced at sonic3k.asm:9680-9694.
        globalAnimationState.advanceChangeRingFrame();
    }

    /**
     * Animate_Palette for this LevelLoop iteration: false while {@code Palette_fade_timer}
     * spends the frame, or when a seamless reload in this iteration already ran it for
     * the outgoing act.
     */
    private boolean animatePaletteThisFrame() {
        int frame = currentLevelFrame();
        lastAnimatePaletteFrame = frame;
        if (globalAnimationState.consumeAnimatePaletteBeforeReload(frame)) {
            return false;
        }
        return !endingScene && !globalAnimationState.consumePaletteFadeFrame();
    }

    /**
     * LevelLoop calls Animate_Palette (sonic3k.asm:7923) before DeformBgLayer and
     * ScreenEvents (7929-7930), whose background events perform a seamless reload such as
     * AIZ1BGE_Finish. That iteration's AnPal call therefore belongs to the outgoing act;
     * the incoming act's AnPal first runs on the next iteration.
     */
    @Override
    public void completePaletteAnimationBeforeSeamlessReload() {
        int frame = currentLevelFrame();
        if (frame == Integer.MIN_VALUE || frame == lastAnimatePaletteFrame) {
            return; // this iteration already animated, or no level clock
        }
        lastAnimatePaletteFrame = frame;
        paletteCycler.update(!endingScene && !globalAnimationState.consumePaletteFadeFrame());
        globalAnimationState.markAnimatePaletteBeforeReload(frame);
    }

    private static int currentLevelFrame() {
        try {
            var level = GameServices.levelOrNull();
            return level != null ? level.getFrameCounter() : Integer.MIN_VALUE;
        } catch (RuntimeException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    /** The module-owned state this animator shares with the next act's animator. */
    Sonic3kGlobalAnimationState globalAnimationState() {
        return globalAnimationState;
    }

    @Override
    public void writePaletteCycleCountersByte(int offset, int value) {
        globalAnimationState.queueObjectPhasePaletteCycleByte(offset, value);
    }

    /** D01's source-ordered Animate_Palette call, using the existing captured global timer. */
    public void advanceEndingInitialPalette(com.openggf.level.objects.ObjectServices services) {
        if(!endingScene)throw new IllegalStateException("Ending palette operation on an ordinary animator");
        if(globalAnimationState.paletteFadeTimer()<0)return; // Animate_Palette BMI: event ST writes $FF00.
        if(globalAnimationState.consumePaletteFadeFrame()) {
            com.openggf.game.sonic3k.ending.S3kEndingPalette.advanceInitialSceneFromWhite(services);
            return;
        }
        advanceEndingPoweredPaletteAtAnPal(services);
    }

    /** ScreenEvents ST/CLR of the existing Palette_fade_timer word, captured by this animator. */
    public void setEndingScreenPaletteFadeBlocked(boolean blocked) {
        if(!endingScene)throw new IllegalStateException("Ending fade flag on an ordinary animator");
        globalAnimationState.restorePaletteFadeTimer(blocked?0xFF00:0);
    }

    /** The SSZ ending shares AnPal_Load without owning D01's fresh white fade. */
    public void advanceEndingPoweredPaletteAtAnPal(com.openggf.level.objects.ObjectServices services) {
        if(globalAnimationState.paletteFadeTimer()!=0)return;
        if(services.playerQuery().mainPlayerOrNull() instanceof com.openggf.sprites.playable.AbstractPlayableSprite player
                && player.getSuperStateController() instanceof Sonic3kSuperStateController controller){
            controller.advanceEndingPaletteAtAnPal();
            // Native Normal_palette changes precede object reads and later writes.
            S3kPaletteWriteSupport.resolvePendingWritesNow(services.paletteOwnershipRegistryOrNull(),
                    services.currentLevel(),services.graphicsManager());
        }
    }

    private static void applyPoweredFormPaletteUploadVInt() {
        try {
            var main = GameServices.sprites().getMainPlayable();
            if (main != null
                    && main.getSuperStateController() instanceof Sonic3kSuperStateController controller) {
                controller.onPaletteUploadVInt();
            }
        } catch (RuntimeException ignored) {
            // Headless/bootstrap animation managers may run without gameplay services.
        }
    }

    /**
     * loc_6468 runs Animate_Tiles once after setup objects, at Level_frame_counter
     * zero. Palette cycling and ChangeRingFrame belong only to LevelLoop.
     */
    @Override
    public void runInitialLevelAnimationPass() {
        // Animate_Init precedes loc_6468 (sonic3k.asm:7766, 7855) with no deform between them.
        patternAnimator.runAnimateInitTransfers();
        // MHZ's runtime constructor already represents its pre-loop cap counter;
        // retain that existing ownership while initializing ROM-backed pattern DMA.
        patternAnimator.updatePatternsPreservingPreseededCounters();
    }

    /**
     * Replay bootstrap for native {@code Animate_Tiles} calls that happened
     * before the first compared row. This deliberately excludes
     * {@code Animate_Palette}; the ROM setup pass calls only the tile animator.
     */
    public void updatePatternsOnlyForReplayBootstrap() {
        patternAnimator.updatePatternsPreservingPreseededCounters();
    }

    Sonic3kPatternAnimator patternAnimatorForTesting() {
        return patternAnimator;
    }

    @Override
    public String key() {
        return patternAnimator.key();
    }

    @Override
    public int aizVineAngleWord() {
        return globalAnimationState.aizVineAngleWord();
    }

    @Override
    public void advanceForSeamlessTransition() {
        globalAnimationState.advanceChangeRingFrame();
    }

    @Override
    public PatternAnimatorSnapshot capture() {
        PatternAnimatorSnapshot inner = patternAnimator.capture();
        byte[] innerExtra = inner.extra() != null ? inner.extra() : new byte[0];
        byte[] cyclerState = paletteCycler.captureCyclerState();
        ByteBuffer wrapped = ByteBuffer.allocate(
                1 + 4 + innerExtra.length + 4 + cyclerState.length + 4 + 2
                        + Sonic3kGlobalAnimationState.PALETTE_CYCLE_SNAPSHOT_BYTES);
        wrapped.put(COMBINED_EXTRA_WITH_ANPAL_RAM_MAGIC);
        wrapped.putInt(innerExtra.length);
        wrapped.put(innerExtra);
        wrapped.putInt(cyclerState.length);
        wrapped.put(cyclerState);
        wrapped.putShort((short) globalAnimationState.aizVineAngleWord());
        wrapped.put((byte) globalAnimationState.ringTimer());
        wrapped.put((byte) globalAnimationState.ringFrame());
        wrapped.putShort((short) globalAnimationState.paletteFadeTimer());
        globalAnimationState.capturePaletteCycles(wrapped);
        return new PatternAnimatorSnapshot(inner.scriptCounters(), inner.handlerCounters(),
                wrapped.array());
    }

    @Override
    public void restore(PatternAnimatorSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        byte[] extra = snapshot.extra();
        if (extra == null || extra.length == 0
                || (extra[0] != COMBINED_EXTRA_MAGIC
                && extra[0] != COMBINED_EXTRA_WITH_GLOBALS_MAGIC
                && extra[0] != COMBINED_EXTRA_WITH_RING_CLOCK_MAGIC
                && extra[0] != COMBINED_EXTRA_WITH_ANPAL_RAM_MAGIC)) {
            patternAnimator.restore(snapshot);
            return;
        }
        boolean hasAnPalRam = extra[0] == COMBINED_EXTRA_WITH_ANPAL_RAM_MAGIC;
        boolean hasRingClock = hasAnPalRam || extra[0] == COMBINED_EXTRA_WITH_RING_CLOCK_MAGIC;
        boolean hasGlobalAnimationState = hasRingClock || extra[0] == COMBINED_EXTRA_WITH_GLOBALS_MAGIC;
        ByteBuffer buf = ByteBuffer.wrap(extra, 1, extra.length - 1);
        int innerSize = buf.getInt();
        if (innerSize < 0 || innerSize > buf.remaining()) {
            patternAnimator.restore(snapshot);
            return;
        }
        byte[] innerExtra = new byte[innerSize];
        buf.get(innerExtra);
        int cyclerSize = hasGlobalAnimationState ? buf.getInt() : buf.remaining();
        if (cyclerSize < 0 || cyclerSize > buf.remaining()) {
            patternAnimator.restore(snapshot);
            return;
        }
        byte[] cyclerState = new byte[cyclerSize];
        buf.get(cyclerState);
        PatternAnimatorSnapshot inner = new PatternAnimatorSnapshot(snapshot.scriptCounters(),
                snapshot.handlerCounters(), innerExtra);
        patternAnimator.restore(inner);
        paletteCycler.restoreCyclerState(cyclerState);
        if (hasGlobalAnimationState && buf.remaining() >= 2) {
            globalAnimationState.restoreAizVineAngleWord(buf.getShort() & 0xFFFF);
        }
        if (hasRingClock && buf.remaining() >= 2) {
            globalAnimationState.restoreRingAnimation(buf.get() & 0xFF, buf.get() & 0xFF);
        }
        if (hasRingClock && buf.remaining() >= 2) {
            globalAnimationState.restorePaletteFadeTimer(buf.getShort());
        }
        if (hasAnPalRam && buf.remaining() >= Sonic3kGlobalAnimationState.PALETTE_CYCLE_SNAPSHOT_BYTES) {
            globalAnimationState.restorePaletteCycles(buf);
        }
        presentLevelFade(true);
    }
}
