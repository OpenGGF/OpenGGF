package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameMusic;
import com.openggf.data.Rom;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.save.SaveReason;
import com.openggf.level.objects.AbstractResultsScreen;
import com.openggf.game.sonic3k.Sonic3kObjectArt;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.S3kTransitionWriteSupport;
import com.openggf.tools.NemesisReader;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * S3K results screen -- displays "{CHARACTER} GOT THROUGH ACT {N}" with
 * time bonus and ring bonus tally after the signpost lands.
 *
 * <p>ROM: Obj_LevelResults (sonic3k.asm lines 62499-63003).
 *
 * <p>Key differences from S2:
 * <ul>
 *   <li>No fade-to-black -- signals flags and lets level events handle transitions</li>
 *   <li>360-frame pre-tally delay (S2 uses 180)</li>
 *   <li>90-frame post-tally wait (S2 uses 180)</li>
 *   <li>No perfect bonus</li>
 *   <li>Act 1 exit shows act 2 title card; act 2 exit sets End_of_level_flag</li>
 * </ul>
 */
public class S3kResultsScreenObjectInstance extends AbstractResultsScreen implements RewindRecreatable {
    enum CarriedTitlePhase {
        RESULTS,
        TITLE_CARD_WAIT,
        TITLE_CARD_WAIT2,
        DONE
    }
    private static final Logger LOG = Logger.getLogger(S3kResultsScreenObjectInstance.class.getName());

    // ROM-accurate timing
    private static final int S3K_PRE_TALLY_DELAY = 360;  // 6*60 frames (ROM line 62580)
    private static final int S3K_WAIT_DURATION = 90;      // ROM line 62676
    private static final int MUSIC_TRIGGER_FRAME = 71;    // 360 - 289 = 71 (ROM line 62626)

    // Time bonus table (ROM lines 62910-62918)
    private static final int[] TIME_BONUSES = {5000, 5000, 1000, 500, 400, 300, 100, 10};
    private static final int SPECIAL_TIME_BONUS = 10000;  // 9:59 override (ROM line 62559)
    private static final int MAX_TIMER_SECONDS = 599;     // 9:59 = 9*60 + 59

    // Pattern caching
    private static final int PATTERN_BASE = PatternAtlasRange.RESULTS_SCREENS.base();

    // Digit rendering constants
    private static final int DIGIT_OFFSET_X = -0x38;  // Digits start 0x38 pixels left of element X
    private static final int DIGIT_SPACING = 8;        // 8px per digit
    private static final int DIGIT_COUNT = 7;           // 7-digit display
    private static final int[] DIVISORS = {1000000, 100000, 10000, 1000, 100, 10, 1};

    // State
    // Un-finaled for rewind: character/act are constructor args not carried by any
    // ObjectSpawn, so recreate uses placeholders and GenericFieldCapturer (which
    // skips final scalars) reapplies the captured values
    // after recreate. Covers this class and its Mgz2 subclass.
    private PlayerCharacter character;
    private int act;  // 0-indexed: 0=Act 1, 1=Act 2

    // Tally values
    private int timeBonus;
    private int ringBonus;
    private int totalBonusCountUp;

    // Art
    private Pattern[] combinedPatterns;
    private List<SpriteMappingFrame> mappingFrames;
    private boolean artLoaded;
    private boolean artCached;

    // Rendering
    private ObjectSpriteSheet spriteSheet;
    private PatternSpriteRenderer renderer;

    // Music flag
    private boolean musicPlayed;

    // Player reference for control restoration on exit
    private AbstractPlayableSprite playerRef;

    // Native child-SST creation/exit queue state.
    private int exitQueueCounter;
    // ROM $30 is initialized to 12 before child allocation. A later
    // CreateNewSprite4 failure does not repair this count, so a partial prefix
    // intentionally leaves Wait2 stalled with the residual count.
    private int childrenRemaining = S3kResultsElementObjectInstance.ENTRY_COUNT;
    private boolean actTransitionSignaled;
    private CarriedTitlePhase carriedTitlePhase = CarriedTitlePhase.RESULTS;
    private int carriedTitleWaitTimer;

    public S3kResultsScreenObjectInstance(PlayerCharacter character, int act) {
        this(character, act, true);
    }

    /** Side-effect-free shell used only by generic rewind recreation. */
    protected S3kResultsScreenObjectInstance(boolean rewindShell) {
        this(PlayerCharacter.SONIC_AND_TAILS, 0, !rewindShell);
    }

    private S3kResultsScreenObjectInstance(PlayerCharacter character, int act,
                                           boolean initializeRuntimeState) {
        super("S3kResults");
        setRomWorldPositioned(false);
        this.character = character;
        this.act = act;

        if (!initializeRuntimeState) {
            return;
        }

        // Calculate bonuses from current game state (ROM lines 62550-62578)
        calculateBonuses();

        // Fade out current music immediately (ROM line 62513)
        fadeOutMusic();

        // The Java renderer may load immediately, but the ROM-visible KosM queue
        // remains authoritative for Obj_LevelResultsCreate readiness.
        enqueueResultsKosModules();
        loadArt();

        LOG.fine(() -> String.format("S3K results init: character=%s act=%d timeBonus=%d ringBonus=%d",
                character, act, timeBonus, ringBonus));
    }

    private S3kResultsScreenObjectInstance() {
        this(true);
    }

    @Override
    public AbstractResultsScreen recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new S3kResultsScreenObjectInstance(true));
    }

    @Override
    protected void afterRewindRestoreSettled() {
        // Rebuild only derived art after restored character/act scalars settle.
        // The gameplay KosM queue snapshot remains the sole readiness owner.
        combinedPatterns = null;
        mappingFrames = null;
        spriteSheet = null;
        renderer = null;
        artLoaded = false;
        artCached = false;
        loadArt();
    }

    @Override
    public String traceDebugDetails() {
        return String.format(
                "state=%02X timer=%04X total=%04X act=%d kos=%02X children=%d sig=%b time=%d ring=%d total=%d music=%b complete=%b",
                state,
                stateTimer & 0xFFFF,
                totalFrames & 0xFFFF,
                act,
                services().kosinskiModuleQueue().modulesLeftRaw() & 0xFF,
                childrenRemaining,
                actTransitionSignaled,
                timeBonus,
                ringBonus,
                totalBonusCountUp,
                musicPlayed,
                complete);
    }

    /**
     * Returns the mapping frame index for the character name.
     * ROM: character-specific name art frames in Map_LevelResults.
     */
    private int getCharNameFrame() {
        return switch (character) {
            case TAILS_ALONE -> 0x13 + 2;
            case KNUCKLES -> 0x13 + 3;
            default -> 0x13;
        };
    }

    private void enqueueResultsKosModules() {
        Rom rom;
        try {
            rom = services().rom();
        } catch (Exception e) {
            LOG.fine("Results KosM queue unavailable in construction fixture: " + e.getMessage());
            return;
        }
        if (rom == null) {
            LOG.fine("Results KosM queue skipped because no ROM is attached");
            return;
        }

        int zone = services().romZoneId();
        int numberSource = act == 0 && zone != 0x16
                ? Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR
                : Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM2_ADDR;
        int characterSource = switch (character) {
            case KNUCKLES -> Sonic3kConstants.ART_KOSM_RESULTS_KNUCKLES_ADDR;
            case TAILS_ALONE -> Sonic3kConstants.ART_KOSM_RESULTS_TAILS_ADDR;
            default -> Sonic3kConstants.ART_KOSM_RESULTS_SONIC_ADDR;
        };
        int characterDestination = (act == 0
                ? Sonic3kConstants.VRAM_RESULTS_CHAR_NAME_ACT1
                : Sonic3kConstants.VRAM_RESULTS_CHAR_NAME_ACT2) * Pattern.PATTERN_SIZE_IN_ROM;

        enqueueRequiredKosArchive(rom, Sonic3kConstants.ART_KOSM_RESULTS_GENERAL_ADDR,
                Sonic3kConstants.VRAM_RESULTS_BASE * Pattern.PATTERN_SIZE_IN_ROM);
        enqueueRequiredKosArchive(rom, numberSource,
                Sonic3kConstants.VRAM_RESULTS_NUMBERS * Pattern.PATTERN_SIZE_IN_ROM);
        enqueueRequiredKosArchive(rom, characterSource, characterDestination);
    }

    private void enqueueRequiredKosArchive(Rom rom, int sourceAddress, int destinationVramBytes) {
        try {
            if (!services().kosinskiModuleQueue().enqueue(rom, sourceAddress, destinationVramBytes)) {
                throw new IllegalStateException("S3K KosM queue capacity exhausted while queuing results art");
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(String.format(
                    "Could not read results KosM header at $%06X", sourceAddress), e);
        }
    }

    /**
     * ROM {@code Obj_LevelResultsCreate}: wait for {@code Kos_modules_left==0},
     * then allocate the 12 entries from {@code ObjArray_LevResults}. A failed
     * first AllocateObjectAfterCurrent leaves this routine active for a retry;
     * later CreateNewSprite4 failures leave the already-created prefix intact.
     */
    private void createResultChildSsts() {
        if (services().objectManager() == null) {
            return;
        }
        for (int entryIndex = 0; entryIndex < S3kResultsElementObjectInstance.ENTRY_COUNT; entryIndex++) {
            int index = entryIndex;
            S3kResultsElementObjectInstance child = spawnAfterCurrentSibling(
                    () -> new S3kResultsElementObjectInstance(this, index, character));
            if (child.isDestroyed() || child.getSlotIndex() < 0) {
                if (entryIndex == 0) {
                    return;
                }
                break;
            }
        }
        signalActTransitionIfNeeded();
        actTransitionSignaled = true;
        // Create advances directly to Obj_LevelResultsWait and returns. The
        // first 360-frame countdown decrement belongs to the next dispatch.
        state = STATE_PRE_TALLY_DELAY;
        stateTimer = 0;
    }

    // ---- Bonus calculation ----

    private void calculateBonuses() {
        var levelGamestate = services().levelGamestate();
        int elapsedSeconds = (levelGamestate != null) ? levelGamestate.getElapsedSeconds() : 0;

        // Pause the timer (ROM line 62550)
        if (levelGamestate != null) {
            levelGamestate.pauseTimer();
        }

        // Special case: 9:59 -> 10000 (ROM lines 62557-62559)
        if (elapsedSeconds == MAX_TIMER_SECONDS) {
            timeBonus = SPECIAL_TIME_BONUS;
        } else {
            int index = Math.min(elapsedSeconds / 30, TIME_BONUSES.length - 1);
            timeBonus = TIME_BONUSES[index];
        }

        // Ring bonus: rings x 10 (ROM lines 62576-62578)
        int ringCount = (levelGamestate != null) ? levelGamestate.getRings() : 0;
        ringBonus = ringCount * 10;

        totalBonusCountUp = 0;
    }

    // ---- Timing overrides ----

    @Override
    protected int getSlideDuration() {
        return 0;  // Children handle their own sliding; skip SLIDE_IN entirely
    }

    @Override
    protected int getPreTallyDelay() {
        return S3K_PRE_TALLY_DELAY;
    }

    @Override
    protected int getWaitDuration() {
        return S3K_WAIT_DURATION;
    }

    // ---- Update with element sliding ----

    /**
     * Fully overrides base update() because S3K has a slide-out exit phase
     * that the base class doesn't support (base STATE_EXIT immediately sets
     * complete=true, but S3K needs exit queue animation first).
     *
     * ROM flow: Obj_LevelResultsWait2 counts down 90 frames, THEN runs exit
     * queue each frame until all children are off-screen, THEN transitions.
     */
    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        this.playerRef = player;
        if (carriedTitlePhase != CarriedTitlePhase.RESULTS) {
            updateCarriedTitleCard();
            return;
        }
        this.frameCounter = frameCounter;
        if (!actTransitionSignaled) {
            // Obj_LevelResultsCreate is a distinct routine. Waiting on KosM,
            // retrying the first allocation, and the successful create/publish
            // dispatch all return without consuming Obj_LevelResultsWait's $2E.
            updateCreateGate();
            return;
        }
        stateTimer++;
        totalFrames++;

        switch (state) {
            case STATE_SLIDE_IN -> updateSlideIn();
            case STATE_PRE_TALLY_DELAY -> updatePreTallyDelay();
            case STATE_TALLY -> updateTally();
            case STATE_WAIT -> updateWait();
            case STATE_EXIT -> updateExitQueue();
            // Do NOT set complete=true here — exitQueue handles it
        }
    }

    /**
     * Override: transition to STATE_EXIT without calling onExitReady().
     * ROM: Obj_LevelResultsWait2 first counts down 90 frames, then the
     * exit queue runs. onExitReady() fires only after ALL children are gone.
     */
    @Override
    protected void updateWait() {
        if (stateTimer >= getWaitDuration()) {
            state = STATE_EXIT;
            stateTimer = 0;
            // Do NOT call onExitReady() here — wait for exit queue to finish
        }
    }

    private void updateCreateGate() {
        if (actTransitionSignaled) {
            return;
        }
        if (!services().kosinskiModuleQueue().isIdle()) {
            return;
        }
        createResultChildSsts();
    }

    /**
     * ROM: Obj_LevelResultsWait2 after 90-frame wait (sonic3k.asm lines 62686-62690).
     * Increments exit queue counter each frame. Children start sliding out when
     * the counter reaches their priority. When all are gone, fire onExitReady().
     */
    private void updateExitQueue() {
        if (childrenRemaining <= 0) {
            onExitReady();
            complete = carriedTitlePhase == CarriedTitlePhase.RESULTS;
            return;
        }
        exitQueueCounter++;
    }

    boolean shouldExitElement(int priority) {
        return state == STATE_EXIT && exitQueueCounter >= priority;
    }

    void childExited(S3kResultsElementObjectInstance child) {
        if (childrenRemaining > 0) {
            childrenRemaining--;
        }
    }

    int nativeChildrenRemaining() {
        return childrenRemaining;
    }

    int activeResultsFrames() {
        return totalFrames;
    }

    boolean hasPlayedResultsMusic() {
        return musicPlayed;
    }

    PlayerCharacter resultsCharacter() {
        return character;
    }

    int resultsAct() {
        return act;
    }

    boolean hasLoadedResultsArt() {
        return artLoaded;
    }

    // ---- Pre-tally delay with music trigger ----

    @Override
    protected void updatePreTallyDelay() {
        // Trigger music at frame 71 of the 360-frame countdown
        // ROM: checks counter == 289 (360 - 71) at line 62626
        if (!musicPlayed && stateTimer == MUSIC_TRIGGER_FRAME) {
            musicPlayed = true;
            for (PlayableEntity candidate : playerQuery()
                    .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
                if (candidate instanceof AbstractPlayableSprite sprite
                        && sprite.getDrowningController() != null) {
                    // Native writes air_left=30 for Player_1 and Player_2.
                    // The extension applies the same state to every configured
                    // engine player without restarting the old zone music.
                    sprite.getDrowningController().restoreAirForExternalMusicOverride();
                }
            }
            try {
                services().playMusic(GameMusic.ACT_CLEAR);
            } catch (Exception e) {
                // Ignore audio errors
            }
        }

        super.updatePreTallyDelay();
    }

    // ---- Tally logic ----

    @Override
    protected TallyResult performTallyStep() {
        int totalIncrement = 0;

        // Decrement time bonus by 10 (ROM line 62639)
        int[] timeResult = decrementBonus(timeBonus);
        timeBonus = timeResult[0];
        totalIncrement += timeResult[1];

        // Decrement ring bonus by 10 (ROM line 62645)
        int[] ringResult = decrementBonus(ringBonus);
        ringBonus = ringResult[0];
        totalIncrement += ringResult[1];

        // Track running total (ROM line 62648)
        totalBonusCountUp += totalIncrement;

        boolean anyRemaining = (timeBonus > 0 || ringBonus > 0);
        return tallyResult(anyRemaining, totalIncrement);
    }

    @Override
    protected void updateTally() {
        TallyResult result = performTallyStep();

        if (result.totalIncrement() > 0) {
            services().gameState().addScore(result.totalIncrement());
            // ROM tests the amount removed on this dispatch, not whether any
            // counter remains. The final decrement can therefore still tick.
            if ((this.frameCounter & 3) == 0) {
                playTickSound();
            }
            return;
        }

        // Only the following zero-increment dispatch completes the tally.
        playTallyEndSound();
        int zone = services().romZoneId();
        if ((act != 0) || (zone == 0x0A)) {
            services().requestSessionSave(SaveReason.PROGRESSION_SAVE);
        }
        state = STATE_WAIT;
        // Native falls through from tally completion into Wait2 and
        // immediately decrements 90 to 89 in this same dispatch.
        stateTimer = 1;
    }

    // ---- Audio overrides ----

    @Override
    protected void playTickSound() {
        try {
            services().playSfx(Sonic3kSfx.SWITCH.id);
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    @Override
    protected void playTallyEndSound() {
        try {
            services().playSfx(Sonic3kSfx.REGISTER.id);
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    private void fadeOutMusic() {
        try {
            services().fadeOutMusic();
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    /**
     * ROM: Obj_LevelResultsCreate (sonic3k.asm line 62610-62616).
     * Sets Events_fg_5 for Act 1 zones (except AIZ zone 0 and ICZ zone 5)
     * to trigger the background event handler's seamless act transition.
     *
     * <p>ROM Obj_LevelResultsInit queues the Kosinski module loads first; the
     * create gate above models Obj_LevelResultsCreate polling Kos_modules_left
     * before this flag is written.
     */
    private void signalActTransitionIfNeeded() {
        if (act != 0) return;  // Act 2 — no transition
        try {
            int zone = services().romZoneId();
            if (zone == 0x00) return;  // AIZ — handled by fire transition
            if (zone == 0x05) return;  // ICZ — different transition mechanism
            S3kTransitionWriteSupport.signalActTransition(services());
        } catch (Exception e) {
            LOG.fine("Could not signal act transition: " + e.getMessage());
        }
    }

    // ---- Exit behavior ----

    @Override
    protected void onExitReady() {
        int zone = services().romZoneId();
        // Zones whose Act 1 → Act 2 boundary is a seamless level reload:
        //   HCZ (zone $01): HCZ1BGE_DoTransition
        //   MGZ (zone $02): MGZ1BGE_Transition
        boolean hasSeamlessTransition = (act == 0) && (zone == 0x01 || zone == 0x02 || zone == 0x04);
        boolean fbzCarriedTitleOwner = act == 0 && zone == 0x04;

        // Restore player controls (locked by signpost in Set_PlayerEndingPose).
        // For zones with seamless transitions (HCZ), defer unlocking — the player
        // must remain in the victory pose (objectControlled) while the terrain
        // changes underneath. The seamless transition handler in executeActTransition
        // resets the player state after the layout reload, so they fall naturally.
        boolean lbzAct2PostBossHandoff = zone == 0x06 && act == 1;
        if (!hasSeamlessTransition && !lbzAct2PostBossHandoff && shouldRestorePlayerControlsOnExit()) {
            for (PlayableEntity candidate : playerQuery()
                    .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
                if (candidate instanceof AbstractPlayableSprite sprite) {
                    sprite.setControlLocked(false);
                    ObjectControlState.none().applyTo(sprite);
                    sprite.setForcedAnimationId(-1);
                }
            }
        }
        boolean aizAct1MinibossTitleHandoff = zone == 0x00 && act == 0;

        // Restore camera. AIZ Act 1 is excluded because ROM Obj_LevelResults
        // changes into the in-level Act 2 title card without touching camera
        // bounds; Obj_EndSignControlDoStart waits for that title card to set
        // End_of_level_flag before Change_Act2Sizes creates the gradual
        // level-size objects (sonic3k.asm:62708-62720,62276-62279,
        // 180415-180419,180575-180609).
        // When the AIZ2 cutscene override is active, the
        // Aiz2BossEndSequenceController manages camera bounds for the walk-right
        // sequence. Restoring full level bounds here would snap the camera back
        // to the pre-boss area (ROM: loc_694D4 uses Obj_IncLevEndXGradual).
        boolean iczAct2EndBossHandoff = zone == 0x05 && act == 1;
        var cam = services().camera();
        if (!lbzAct2PostBossHandoff) {
            cam.setFrozen(false);
        }
        if (shouldRestoreCameraBoundsOnExit(zone, act)
                && !Aiz2BossEndSequenceState.isCutsceneOverrideObjectsActive()) {
            var level = services().currentLevel();
            if (level != null) {
                if (!iczAct2EndBossHandoff) {
                    cam.setMinX((short) level.getMinX());
                }
                cam.setMaxX((short) level.getMaxX());
                cam.setMinY((short) level.getMinY());
                cam.setMaxY((short) level.getMaxY());
            }
        }

        // Act 2, Sky Sanctuary ($A), or LRZ boss ($16): set End_of_level_flag
        // ROM lines 62694-62705
        boolean isAct2OrSpecial = (act != 0) || (zone == 0x0A) || (zone == 0x16);

        services().gameState().setEndOfLevelActive(false);

        if (isAct2OrSpecial || (hasSeamlessTransition && !fbzCarriedTitleOwner)) {
            // ROM loc_2DCF8 sets End_of_level_flag directly for Act 2/Sky
            // Sanctuary/LRZ boss results (sonic3k.asm:62693-62705).
            // For HCZ/MGZ Act 1, the engine's BG event handlers use the same
            // flag as their post-results gate before executeActTransition()
            // resets gameplay state for the reloaded act.
            services().gameState().setEndOfLevelFlag(true);
        }

        if (!isAct2OrSpecial) {
            // Act 1: transition to act 2 (ROM lines 62708-62720)
            // ROM loc_2DD06 mutates the results object into Obj_TitleCard
            // without setting End_of_level_flag. The in-level title-card wait
            // path sets it after its children are gone (sonic3k.asm:62708-62720,
            // 62244-62279).
            // ROM: move.b #1,(Apparent_act).w — update display act so
            // death/restart title cards show "Act 2" from this point on.
            services().setApparentAct(1);
            if (fbzCarriedTitleOwner && services().levelManager() != null) {
                services().levelManager().clearCheckpointAndBonusReturnForActTitle();
            }
            // The level data continues seamlessly (S3K acts share the same level).

            // Play act 2 music
            var zoneRegistry = services().gameModule().getZoneRegistry();
            int act2MusicId = zoneRegistry.getMusicId(zone, 1);
            if (act2MusicId >= 0 && !fbzCarriedTitleOwner) {
                try { services().playMusic(act2MusicId); } catch (Exception e) { /* ignore */ }
            }

            // Show act 2 title card (except SOZ zone $8, DEZ zone $B, and zones
            // with seamless transitions like HCZ — the seamless transition will
            // show its own title card after the level reload).
            // ROM lines 62713-62720
            boolean skipTitleCard = (zone == 0x08) || (zone == 0x0B);
            if (!skipTitleCard && (!hasSeamlessTransition || fbzCarriedTitleOwner)) {
                var titleCardProvider = services().titleCardProvider();
                titleCardProvider.initializeInLevel(zone, 1);
                if (titleCardProvider instanceof Sonic3kTitleCardManager s3kTitleCard) {
                    if (fbzCarriedTitleOwner) {
                        s3kTitleCard.useExternalInLevelGameplayOwner();
                    } else if (aizAct1MinibossTitleHandoff) {
                        s3kTitleCard.requestLevelGamestateResetAtInLevelDisplay();
                    }
                }
                if (fbzCarriedTitleOwner) {
                    carriedTitlePhase = CarriedTitlePhase.TITLE_CARD_WAIT;
                    carriedTitleWaitTimer = 0;
                }
            }

            // ROM: Timer and ring count reset on act transition. For zones with
            // seamless transitions (HCZ), the level reload in executeActTransition
            // creates a fresh LevelGamestate. For non-seamless S3K act transitions
            // (where acts share level data), the results screen must reset the
            // gamestate directly since no level reload occurs. AIZ's miniboss
            // handoff carries its Timer/Ring_count reset through the title-card
            // request above, where it becomes visible after the title children
            // reach their display positions (sonic3k.asm:62708-62720,
            // 62214-62235).
            if (!hasSeamlessTransition && !aizAct1MinibossTitleHandoff) {
                resetLevelGamestateForActTransition();
            }
        }

        if (!fbzCarriedTitleOwner) {
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
        LOG.fine(() -> String.format("S3K results exit: zone=%X act=%d isAct2OrSpecial=%b",
                zone, act, isAct2OrSpecial));
    }

    static boolean shouldRestoreLevelCameraBoundsOnExit(int zone, int act) {
        boolean actOneInLevelTitleHandoff = act == 0
                && (zone == 0x00 || zone == 0x01 || zone == 0x02 || zone == 0x04);
        boolean lbzActTwoPostBossHandoff = zone == 0x06 && act == 1;
        return !actOneInLevelTitleHandoff && !lbzActTwoPostBossHandoff;
    }

    protected boolean shouldRestoreCameraBoundsOnExit(int zone, int act) {
        return shouldRestoreLevelCameraBoundsOnExit(zone, act);
    }

    private void updateCarriedTitleCard() {
        switch (carriedTitlePhase) {
            case TITLE_CARD_WAIT -> {
                // ROM mutates this same SST owner into Obj_TitleCardWait, then
                // clears Timer/Ring_count, restores air and music, and advances
                // it to Wait2. Visual children are managed by the shared title
                // renderer; this carried object owns every gameplay mutation.
                resetLevelGamestateForActTransition();
                for (PlayableEntity candidate : playerQuery()
                        .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
                    if (candidate instanceof AbstractPlayableSprite sprite
                            && sprite.getDrowningController() != null) {
                        sprite.getDrowningController().resetAirTimerFromFixedCountdownDeath();
                    }
                }
                int musicId = services().gameModule().getZoneRegistry()
                        .getMusicId(services().romZoneId(), 1);
                if (musicId >= 0) {
                    services().playMusic(musicId);
                }
                carriedTitleWaitTimer = 90;
                carriedTitlePhase = CarriedTitlePhase.TITLE_CARD_WAIT2;
            }
            case TITLE_CARD_WAIT2 -> {
                if (carriedTitleWaitTimer > 0) {
                    carriedTitleWaitTimer--;
                    return; // Obj_TitleCardWait2 returns; child polling starts next dispatch.
                }
                if (services().titleCardProvider().isComplete()) {
                    services().gameState().setEndOfLevelFlag(true);
                    carriedTitlePhase = CarriedTitlePhase.DONE;
                    complete = true;
                    ObjectLifetimeOps.deleteNoRespawn(this);
                }
            }
            case RESULTS, DONE -> {
                // RESULTS is handled by the ordinary results state machine;
                // DONE is retained only for rewind-visible routine identity.
            }
        }
    }

    CarriedTitlePhase carriedTitlePhase() {
        return carriedTitlePhase;
    }

    int carriedTitleWaitTimer() {
        return carriedTitleWaitTimer;
    }

    protected boolean shouldRestorePlayerControlsOnExit() {
        return true;
    }

    private ObjectPlayerQuery playerQuery() {
        ObjectPlayerQuery query = services().playerQuery();
        return new ObjectPlayerQuery(() -> playerRef, query::sidekicks);
    }

    /**
     * Resets the LevelGamestate (timer + rings) for a non-seamless act transition.
     * ROM: Timer and ring count reset to zero when entering a new act.
     * Score carries over.
     */
    private void resetLevelGamestateForActTransition() {
        var levelManager = services().levelManager();
        if (levelManager != null) {
            var gameModule = services().gameModule();
            if (gameModule != null) {
                levelManager.resetLevelGamestate(gameModule.createLevelState());
            }
        }
    }

    // ---- Persistence ----

    @Override
    public boolean isPersistent() {
        return true;  // Survives screen boundary checks
    }

    // ---- Art loading ----

    private void loadArt() {
        try {
            var rom = services().rom();
            var reader = services().romReader();
            Sonic3kObjectArt objectArt = new Sonic3kObjectArt(null, reader);

            combinedPatterns = objectArt.loadResultsArt(character, act);
            List<SpriteMappingFrame> rawMappings = objectArt.loadResultsMappings();

            if (combinedPatterns != null && !rawMappings.isEmpty()) {
                // Some mapping frames (TIME/RING/BONUS labels) reference HUD text tiles
                // at VRAM $6CA+. These are from ArtNem_RingHUDText loaded to ArtTile_Ring ($6BC).
                // First 14 tiles are ring art, tiles 14+ are HUD text (S,C,O,R,E,R,I,N,G,S,T,I,M,E).
                loadHudTextIntoPatterns(rom, combinedPatterns);

                // Adjust tile indices: ROM mappings use absolute VRAM tile indices (e.g. 0x520),
                // but our patterns array is 0-based (patterns[0] = VRAM $520).
                // Subtract VRAM_RESULTS_BASE so piece.tileIndex() maps to array indices.
                mappingFrames = Sonic3kObjectArt.adjustTileIndices(rawMappings, -Sonic3kConstants.VRAM_RESULTS_BASE);

                // ROM: The character name child object's art_tile is 0 (act 1) or $28 (act 2).
                // This offset is added at render time by Draw_Sprite. Since we don't have
                // per-element art_tile, bake the act 2 offset into the mapping tile indices.
                // $28 = VRAM_RESULTS_CHAR_NAME_ACT2 - VRAM_RESULTS_CHAR_NAME_ACT1 = $5A0 - $578
                if (act != 0) {
                    int charNameTileOffset = Sonic3kConstants.VRAM_RESULTS_CHAR_NAME_ACT2
                            - Sonic3kConstants.VRAM_RESULTS_CHAR_NAME_ACT1;
                    adjustCharNameFrameTiles(charNameTileOffset);
                }

                // Create sprite sheet and renderer
                spriteSheet = new ObjectSpriteSheet(combinedPatterns, mappingFrames, 0, 1);
                renderer = new PatternSpriteRenderer(spriteSheet);
                artLoaded = true;
            } else {
                artLoaded = false;
                LOG.warning("Failed to load results screen art");
            }

            // Note: The level results screen uses the existing level palette.
            // Pal_Results in the ROM is for the special stage results screen, not here.
            // (S2 results screen also does not load a palette.)
        } catch (Exception e) {
            artLoaded = false;
            LOG.warning("Failed to load results screen art: " + e.getMessage());
        }
    }

    /**
     * Loads HUD text tiles from ArtNem_RingHUDText and places them at the correct
     * VRAM offsets in the combined pattern array.
     *
     * ROM: ArtNem_RingHUDText loads to ArtTile_Ring ($6BC). First 14 tiles are
     * ring art ($6BC-$6C9), tiles 14+ are HUD text starting at $6CA.
     * The results mapping frames reference these tiles for TIME/RING/BONUS labels.
     */
    private void loadHudTextIntoPatterns(Rom rom, Pattern[] patterns) {
        try {
            FileChannel channel = rom.getFileChannel();
            channel.position(Sonic3kConstants.ART_NEM_RING_HUD_TEXT_ADDR);
            byte[] data = NemesisReader.decompress(channel);

            int totalTiles = data.length / Pattern.PATTERN_SIZE_IN_ROM;
            // ArtTile_Ring = $6BC. Place ALL tiles starting at array index $6BC - $520 = $19C
            int vramBase = 0x6BC;
            for (int i = 0; i < totalTiles; i++) {
                int arrayIdx = (vramBase + i) - Sonic3kConstants.VRAM_RESULTS_BASE;
                if (arrayIdx >= 0 && arrayIdx < patterns.length) {
                    byte[] tileData = Arrays.copyOfRange(data,
                            i * Pattern.PATTERN_SIZE_IN_ROM,
                            (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                    Pattern pat = new Pattern();
                    pat.fromSegaFormat(tileData);
                    patterns[arrayIdx] = pat;
                }
            }
            LOG.fine("Loaded " + totalTiles + " ring/HUD text tiles from ROM");
        } catch (Exception e) {
            LOG.warning("Failed to load HUD text tiles: " + e.getMessage());
        }
    }

    /**
     * Adjusts tile indices for character name mapping frames to account for the
     * act-dependent VRAM destination. Character name frames are $13 (Sonic),
     * $14 (Sonic & Tails label), $15 (Tails), $16 (Knuckles).
     *
     * <p>ROM: Character name child object art_tile = 0 (act 1) or $28 (act 2).
     * Since we use a single combined pattern array without per-element art_tile,
     * we must offset the character name frame tile indices by this amount.
     */
    private void adjustCharNameFrameTiles(int tileOffset) {
        if (mappingFrames == null || !(mappingFrames instanceof ArrayList)) {
            mappingFrames = new ArrayList<>(mappingFrames);
        }
        // Adjust all possible character name frames ($13 through $16)
        for (int frameIdx = 0x13; frameIdx <= 0x16; frameIdx++) {
            if (frameIdx >= mappingFrames.size()) continue;
            SpriteMappingFrame frame = mappingFrames.get(frameIdx);
            if (frame.pieces().isEmpty()) continue;
            List<SpriteMappingPiece> adjusted = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece piece : frame.pieces()) {
                adjusted.add(new SpriteMappingPiece(
                        piece.xOffset(), piece.yOffset(),
                        piece.widthTiles(), piece.heightTiles(),
                        piece.tileIndex() + tileOffset,
                        piece.hFlip(), piece.vFlip(),
                        piece.paletteIndex(), piece.priority()));
            }
            mappingFrames.set(frameIdx, new SpriteMappingFrame(adjusted));
        }
    }

    // ---- Pattern caching ----

    private void ensureArtCached() {
        if (artCached || !artLoaded || renderer == null) return;
        var gm = services().graphicsManager();
        if (gm == null) return;
        renderer.ensurePatternsCached(gm, PATTERN_BASE);
        artCached = true;
    }

    // ---- Rendering ----

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Obj_LevelResults is the tally/controller SST. Each visual entry is a
        // real child SST and issues its own DisplaySprite-equivalent draw.
    }

    void appendElementRender(S3kResultsElementObjectInstance element,
                             List<GLCommand> commands) {
        if (!artLoaded || renderer == null) return;
        ensureArtCached();
        if (!renderer.isReady()) return;

        var camera = services().camera();
        if (camera == null) return;

        // xOffset() is (viewportWidth - 320) / 2; 0 at native 320 (byte-identical).
        int baseX = camera.getX() + xOffset();
        int baseY = camera.getY();

        int worldX = baseX + element.currentScreenX();
        int worldY = baseY + element.screenY();
        switch (element.role()) {
            case TIME_BONUS -> renderBonusDigits(worldX, worldY, timeBonus);
            case RING_BONUS -> renderBonusDigits(worldX, worldY, ringBonus);
            case TOTAL -> renderBonusDigits(worldX, worldY, totalBonusCountUp);
            default -> renderMappingFrame(element.mappingFrame(), worldX, worldY);
        }
    }

    /**
     * Renders a mapping frame at the given world position using the PatternSpriteRenderer.
     */
    private void renderMappingFrame(int frameIndex, int worldX, int worldY) {
        if (frameIndex < 0 || frameIndex >= spriteSheet.getFrameCount()) return;
        renderer.drawFrameIndex(frameIndex, worldX, worldY, false, false);
    }

    /**
     * Renders a 7-digit BCD value with leading zero suppression.
     * ROM: LevResults_DisplayScore (sonic3k.asm lines 62789-62815).
     *
     * Each digit uses a mapping frame from Map_Results:
     *   Frame 0 = blank (suppressed leading zero)
     *   Frame 1 = "0", Frame 2 = "1", ... Frame 10 = "9"
     * These frames reference tiles from ArtKosM_ResultsGeneral ($520+),
     * NOT the act number art at $568.
     */
    private void renderBonusDigits(int worldX, int worldY, int value) {
        int x = worldX + DIGIT_OFFSET_X;
        boolean hasNonZero = false;
        int remaining = value;

        for (int i = 0; i < DIGIT_COUNT; i++) {
            int digit = remaining / DIVISORS[i];
            remaining %= DIVISORS[i];
            if (digit != 0) hasNonZero = true;

            // ROM: mapping_frame = (hasNonZero) ? digit + 1 : 0
            // Frame 0 = blank, Frame 1 = "0", Frame 2 = "1", ..., Frame 10 = "9"
            int frameIdx;
            if (hasNonZero || i == DIGIT_COUNT - 1) {
                frameIdx = digit + 1;
            } else {
                frameIdx = 0; // Suppress leading zero
            }

            if (frameIdx > 0) {
                renderMappingFrame(frameIdx, x + i * DIGIT_SPACING, worldY);
            }
        }
    }
}
