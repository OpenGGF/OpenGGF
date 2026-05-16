package com.openggf.trace;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.replay.TraceReplayFixture;

import java.util.List;

/**
 * Shared trace replay bootstrap helpers.
 *
 * <p>Trace rows are a read-only comparison ledger. This class may align replay
 * cursors and classify execution phases from trace metadata/events, but it must
 * not copy recorded player, sidekick, object, camera, RNG, or CPU state back
 * into the engine.
 */
public final class TraceReplayBootstrap {

    public record ReplayStartState(int startingTraceIndex, int seededTraceIndex) {
        public static final ReplayStartState DEFAULT = new ReplayStartState(0, -1);

        public boolean hasSeededTraceState() {
            return seededTraceIndex >= 0;
        }
    }

    public record ReplayPrimaryState(
            short x,
            short y,
            short xSpeed,
            short ySpeed,
            short gSpeed,
            byte angle,
            boolean air,
            boolean rolling,
            int groundMode,
            int xSub,
            int ySub,
            String source) {

        public static ReplayPrimaryState fromSprite(AbstractPlayableSprite sprite) {
            return new ReplayPrimaryState(
                    sprite.getCentreX(),
                    sprite.getCentreY(),
                    sprite.getXSpeed(),
                    sprite.getYSpeed(),
                    sprite.getGSpeed(),
                    sprite.getAngle(),
                    sprite.getAir(),
                    sprite.getRolling(),
                    sprite.getGroundMode().ordinal(),
                    sprite.getXSubpixelRaw(),
                    sprite.getYSubpixelRaw(),
                    "player");
        }

        public static ReplayPrimaryState fromTraceFrame(TraceFrame frame, String source) {
            return new ReplayPrimaryState(
                    frame.x(),
                    frame.y(),
                    frame.xSpeed(),
                    frame.ySpeed(),
                    frame.gSpeed(),
                    frame.angle(),
                    frame.air(),
                    frame.rolling(),
                    frame.groundMode(),
                    frame.xSub(),
                    frame.ySub(),
                    source);
        }
    }

    private TraceReplayBootstrap() {
    }

    /** Diagnostic system property gating the pre-trace writeback path. */
    private static final String HYDRATE_SWITCH = "oggf.trace.hydrate";

    /**
     * Applies the recorded pre-trace object SST snapshots when (and only when)
     * the {@code oggf.trace.hydrate} diagnostic switch is enabled. On a normal
     * green replay run this method is a no-op: trace data remains a read-only
     * comparison ledger and engine objects keep the state produced by their
     * native level-load / prelude execution.
     *
     * <p>When the switch is on, the call delegates to
     * {@link TraceObjectSnapshotBinder#apply(ObjectManager, java.util.List)}
     * which iterates the recorded slots and copies the SST bytes onto the
     * matching engine instance via
     * {@link com.openggf.level.objects.AbstractObjectInstance#hydrateFromRomSnapshot}.
     * The returned {@link TraceObjectSnapshotBinder.Result} truthfully reports
     * the number of slots successfully addressed so divergence reports can
     * distinguish "engine had no matching slot" from "engine matched but the
     * subclass has no per-slot hydrate override".
     */
    public static TraceObjectSnapshotBinder.Result applyPreTraceState(TraceData trace,
                                                                     TraceReplayFixture fixture) {
        var level = GameServices.levelOrNull();
        ObjectManager objectManager = level != null ? level.getObjectManager() : null;
        List<TraceEvent.ObjectStateSnapshot> snapshots = trace != null
                ? trace.preTraceObjectSnapshots()
                : List.of();
        if (!Boolean.getBoolean(HYDRATE_SWITCH)) {
            // Comparison-only mode — never mutate engine state. Report
            // attempted-count truthfully so callers can log "we observed N
            // snapshots without applying any of them".
            return new TraceObjectSnapshotBinder.Result(snapshots.size(), 0, List.of());
        }
        return TraceObjectSnapshotBinder.apply(objectManager, snapshots);
    }

    /**
     * Snaps the live sprite's follower-history rings to the recorded ROM
     * pre-trace snapshot when (and only when) the {@code oggf.trace.hydrate}
     * diagnostic switch is enabled. On a normal green replay run this method
     * is a no-op.
     *
     * <p>When the switch is on, the snapshot's {@code xHistory}, {@code yHistory},
     * {@code inputHistory}, and {@code statusHistory} arrays plus {@code historyPos}
     * are copied verbatim onto the sprite via
     * {@link AbstractPlayableSprite#hydrateRecordedHistory}. The sprite is the
     * Player_1 follower target sampled by Tails_Normal, so hydrating it lines
     * up the sidekick's delayed-input read with the ROM source-of-truth before
     * the first compared frame.
     */
    public static void applyPreTracePlayerHistory(TraceEvent.PlayerHistorySnapshot snapshot,
                                                  AbstractPlayableSprite sprite) {
        if (snapshot == null || sprite == null) {
            return;
        }
        if (!Boolean.getBoolean(HYDRATE_SWITCH)) {
            // Comparison-only mode — never mutate engine state.
            return;
        }
        if (snapshot.xHistory() == null || snapshot.yHistory() == null
                || snapshot.inputHistory() == null || snapshot.statusHistory() == null) {
            return;
        }
        sprite.hydrateRecordedHistory(
                snapshot.xHistory(),
                snapshot.yHistory(),
                snapshot.inputHistory(),
                snapshot.statusHistory(),
                snapshot.historyPos());
    }

    public static ReplayStartState applyReplayStartState(TraceData trace,
                                                         TraceReplayFixture fixture) {
        return applyReplayStartStateForTraceReplay(trace, fixture);
    }

    public static ReplayStartState applyReplayStartStateForTraceReplay(TraceData trace,
                                                                       TraceReplayFixture fixture) {
        if (usesSidekickTitleCardSeedFrame(trace)) {
            if (fixture != null) {
                // The frame-0 row is reproduced by the native sidekick-only
                // prelude, not by a full player physics tick. Consume only the
                // matching BK2 input frame so trace frame 1 uses BK2 input 1
                // and later Ctrl_1_pressed edges stay aligned with the recorded
                // rows. Also append that live controller sample to Sonic's
                // native follow history; Tails_Normal reads the delayed
                // Ctrl_1_Logical stream independently of Sonic physics.
                int seedInput = fixture.consumeRecordingFrameInputOnly();
                recordSeedFrameInputHistory(fixture.sprite(), seedInput);
            }
            return new ReplayStartState(1, 0);
        }
        return new ReplayStartState(replaySeedTraceIndexForTraceReplay(trace), -1);
    }

    /**
     * Compatibility entrypoint for callers that used to request a seeded replay
     * start. It now only returns the unseeded comparison cursor.
     */
    public static ReplayStartState applySeedReplayStartStateForTraceReplay(TraceData trace,
                                                                           TraceReplayFixture fixture) {
        return applyReplayStartStateForTraceReplay(trace, fixture);
    }

    public static int recordingStartFrameForTraceReplay(TraceData trace) {
        if (trace == null) {
            return 0;
        }
        int seedTraceIndex = replaySeedTraceIndexForTraceReplay(trace);
        if (isLegacyS3kAizIntroTrace(trace) && seedTraceIndex == 0) {
            // The AIZ end-to-end recorder writes trace frame 0 in the same
            // callback that arms recording, unlike level-gated traces which
            // return and emit their first row after the next frameadvance().
            // That row is therefore the state produced by the previous BK2
            // input. Start the movie cursor one input frame earlier while still
            // replaying the full trace prefix from trace frame 0.
            return Math.max(0, trace.metadata().bk2FrameOffset() - 1);
        }
        return trace.metadata().bk2FrameOffset() + Math.max(0, seedTraceIndex - 1);
    }

    /**
     * Returns the first trace frame where a ZoneActState or Checkpoint event
     * reports {@code game_mode=0x0C} (LEVEL). Headless replay fixtures start
     * the engine directly in gamemode 0x0C, but the recorder usually runs
     * for some number of frames in SEGA/title/level-load gamemodes before
     * the ROM reaches LevelLoop for the first time. Many ROM systems
     * (OscillateNumDo, sprite placement cursor, random lookups) only tick
     * inside LevelLoop, so replay drivers need to know how many leading
     * frames to neutralise from the engine-side so the ROM and engine
     * stay phase-aligned over long traces.
     */
    public static int preLevelFrameCountForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return 0;
        }
        return findFirstLevelGameplayFrame(trace);
    }

    public static int replaySeedTraceIndexForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return 0;
        }
        if (isLegacyS3kAizIntroTrace(trace)) {
            // The AIZ full-run fixture records the intro/cutscene timeline from its
            // own frame 0. Replaying from the first in-level frame skips hundreds of
            // recorded intro frames and loses global state that the seed frame alone
            // cannot reconstruct (timers, title-card state, zone-event evolution).
            return 0;
        }
        int firstLevelFrame = findFirstLevelGameplayFrame(trace);
        return Math.max(firstLevelFrame, 0);
    }

    public static int initialVblankCounterForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return 0;
        }
        return trace.initialVblankCounter() + 1;
    }

    public static int preTraceOscillationFramesForTraceReplay(TraceData trace,
                                                              int override) {
        if (override >= 0) {
            return override;
        }
        if (trace == null || trace.frameCount() == 0
                || shouldUseLegacyS3kAizIntroWarmup(trace)) {
            return 0;
        }
        int seedTraceIndex = replaySeedTraceIndexForTraceReplay(trace);
        if (seedTraceIndex < 0 || seedTraceIndex >= trace.frameCount()) {
            return 0;
        }
        if (usesSidekickTitleCardSeedFrame(trace)) {
            // The S3K Sonic+Tails seed row is not driven through a full engine
            // frame, but the ROM row has already passed LevelLoop's
            // OscillateNumDo after Process_Sprites (sonic3k.asm:7884-7910).
            // Apply the metadata's one-time pre-trace oscillator tick so
            // later CNZ objects read the same previous-frame oscillation phase.
            return trace.metadata().preTraceOscillationFrames();
        }
        int firstComparedGameplayFrame =
                trace.getFrame(seedTraceIndex).gameplayFrameCounter();
        // The replay loop steps the seed trace row before comparing it. A row
        // with gameplay_frame_counter=1 has already observed the ROM's first
        // LevelLoop tick, but the headless fixture will produce that same tick
        // natively when it steps the row. Only pre-advance ticks that completed
        // before the first compared row.
        return Math.max(0, firstComparedGameplayFrame - 1);
    }

    /**
     * The legacy S3K AIZ end-to-end trace includes the title/intro prefix, so
     * frame 289 must still execute a native gameplay tick to keep Sonic and
     * sidekick cadence aligned. Its sampled oscillator-dependent object state,
     * however, is still the state read during Process_Sprites before the first
     * LevelLoop OscillateNumDo pass: see docs/skdisasm/sonic3k.asm:7884-7909.
     * Obj_FloatingPlatform reads Oscillating_table+$0A before SolidObjectTop
     * carries the rider (docs/skdisasm/sonic3k.asm:50244-50248,
     * docs/skdisasm/sonic3k.asm:50826-50841), so suppress exactly the first
     * replay-local oscillator advance rather than hydrating trace data.
     */
    public static int initialOscillationSuppressionFramesForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return 0;
        }
        return isLegacyS3kAizIntroTrace(trace) ? 1 : 0;
    }

    /**
     * Number of native sidekick-only object ticks that occur after level load
     * but before the first gameplay comparison frame. Sonic 2's title-card
     * path runs Obj02/Tails CPU for ~104 frames while Sonic's own level-frame
     * physics is held with input locked; the first recorded gameplay row then
     * observes Sonic's first input-driven movement frame and Tails' 105th
     * follower tick. This is derived from execution timing, not from recorded
     * Tails fields.
     *
     * <p>The production {@link com.openggf.GameLoop} title-card branch runs
     * objects + locked-input physics natively during {@code TITLE_CARD} mode,
     * but headless trace fixtures skip the title-card entirely and arrive at
     * frame 0 with virgin state ({@code history_pos=0}, Tails at spawn with
     * zero velocity). For S2 traces recorded with the v9.2-s2 native-prelude
     * recorder where a sidekick is present, replay must reproduce those ~104
     * prelude frames by ticking objects and sidekick CPU (with Sonic input
     * locked) so frame-0 engine state matches the ROM's recorded
     * {@code player_history_snapshot}, {@code cpu_state_snapshot}, and
     * {@code object_state_snapshot}.
     */
    public static int sidekickTitleCardPreludeFramesForTraceReplay(TraceData trace) {
        int s2Frames = resolveS2TitleCardPreludeFrames(trace);
        if (s2Frames > 0) {
            return s2Frames;
        }
        // S3K Sonic+Tails CNZ Act 1 seed-frame mode: trace frame 0 has
        // Level_frame_counter=1, i.e. the row was sampled AFTER ROM's first
        // LevelLoop iteration (sonic3k.asm:7884-7910). Tails_CPU_Control's
        // loc_13A5A (sonic3k.asm:26405-26415) repositions Tails to
        // (0x18, 0x600) with status_InAir set during that iteration, and
        // Tails_Modes' in-air gravity (MoveSprite_TestGravity) then writes
        // y_vel = 0x38 before the LevelLoop ends. Run one sidekick-only
        // prelude tick during bootstrap so the seed-frame compare sees the
        // post-loc_13A5A + post-gravity state instead of the raw post-spawn
        // state ((tails_x ~0x0A clamped, air=false, y_vel=0)).
        return resolveS3kSidekickSeedFramePreludeFrames(trace);
    }

    /**
     * Sonic 2 runs level objects during the title-card sequence before
     * {@code Level_started_flag} is set and before {@code Level_frame_counter}
     * begins ticking in {@code Level_MainLoop}: see s2.asm:5004-5008,
     * s2.asm:5060-5066, and s2.asm:5077-5092. Headless replay starts directly
     * at gameplay frame 1, so it must reproduce that native object prelude
     * without copying pre-trace SST snapshots back into the engine.
     *
     * <p>Returns 104 when the trace's metadata declares native-prelude mode
     * (recorder >= v9.2-s2), the game is S2, and the recording team has at
     * least one sidekick. Returns 0 otherwise.
     */
    public static int levelObjectTitleCardPreludeFramesForTraceReplay(TraceData trace) {
        return resolveS2TitleCardPreludeFrames(trace);
    }

    /**
     * Frame count of the ROM title-card object prelude that S2 traces sampled
     * at {@code frame -1}. The recorded {@code player_history_snapshot}
     * {@code history_pos=0x68 = 104} is the raw byte index of
     * {@code Sonic_Pos_Record_Index} (s2.asm:36043-36048). Each
     * {@code Sonic_RecordPos} call advances the index by 4 (one 4-byte
     * Pos_table entry), so {@code history_pos = 4 * frames_recorded}.
     * Therefore the actual prelude length is {@code 104 / 4 = 26} frames of
     * the ROM title-card {@code RunObjects} loop running Obj01_Control after
     * Obj01_Init has completed its 64-entry pre-fill.
     */
    private static final int S2_TITLE_CARD_PRELUDE_FRAMES = 26;

    /**
     * Frame count of the S3K pre-LevelLoop sidekick prelude. Returns 1 only
     * when seed-frame mode applies (S3K Sonic+Tails trace whose frame 0 row
     * has Level_frame_counter=1 and Sonic primary movement still zero). The
     * single tick fires Tails' carry-trigger init (CNZ loc_13A5A) and
     * applies the in-air gravity that the ROM observes during that first
     * iteration of LevelLoop's Process_Sprites pass.
     */
    private static int resolveS3kSidekickSeedFramePreludeFrames(TraceData trace) {
        if (trace == null) {
            return 0;
        }
        if (!usesSidekickTitleCardSeedFrame(trace)) {
            return 0;
        }
        return 1;
    }

    private static int resolveS2TitleCardPreludeFrames(TraceData trace) {
        if (trace == null) {
            return 0;
        }
        TraceMetadata meta = trace.metadata();
        if (meta == null || !"s2".equals(meta.game())) {
            return 0;
        }
        if (!meta.nativePreludeMode()) {
            return 0;
        }
        if (meta.recordedSidekicks().isEmpty()) {
            return 0;
        }
        return S2_TITLE_CARD_PRELUDE_FRAMES;
    }

    /**
     * Returns false because trace start state is comparison data only. Kept as
     * a named policy gate for callers that need to avoid legacy hydration paths.
     */
    public static boolean shouldUseTraceStartBootstrapForTraceReplay(TraceData trace) {
        return false;
    }

    public static boolean shouldSeedFrameZeroForTraceReplay(TraceData trace) {
        return false;
    }

    public static boolean shouldSeedReplayStartStateForTraceReplay(TraceData trace,
                                                                   int requestedSeedTraceIndex) {
        return false;
    }

    public static boolean requiresFreshLevelLoadForTraceReplay(TraceData trace) {
        return isLegacyS3kAizIntroTrace(trace)
                && replaySeedTraceIndexForTraceReplay(trace) == 0;
    }

    public static boolean shouldUseLegacyS3kAizIntroWarmup(TraceData trace) {
        return false;
    }

    public static boolean shouldApplyMetadataStartPositionForTraceReplay(TraceData trace) {
        return replaySeedTraceIndexForTraceReplay(trace) == 0
                && !isLegacyS3kAizIntroTrace(trace);
    }

    public static boolean usesS2TornadoRideStartForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return false;
        }
        TraceMetadata metadata = trace.metadata();
        if (!"s2".equals(metadata.game())
                || replaySeedTraceIndexForTraceReplay(trace) != 0) {
            return false;
        }
        String zone = metadata.zone();
        return "level_gated_reset_aware".equals(metadata.traceProfile())
                && ("scz".equals(zone) || "wfz".equals(zone));
    }

    public static int strictStartTraceIndexForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return 0;
        }
        if (isLegacyS3kAizIntroTrace(trace)) {
            return findFirstLevelGameplayFrame(trace);
        }
        return replaySeedTraceIndexForTraceReplay(trace);
    }

    public static ReplayPrimaryState capturePrimaryReplayStateForComparison(TraceData trace,
                                                                            TraceFrame current,
                                                                            AbstractPlayableSprite sprite) {
        if (sprite == null) {
            throw new IllegalArgumentException("sprite must not be null");
        }
        if (isLegacyS3kAizIntroTrace(trace)
                && current != null
                && current.frame() < findFirstLevelGameplayFrame(trace)) {
            // Pre-level AIZ full-run rows sample title/intro Player_1 RAM,
            // not the loaded-level Sonic object. Keep this comparison-side
            // only; never copy these fields back into engine state.
            return ReplayPrimaryState.fromTraceFrame(current, "trace-vblank");
        }
        return ReplayPrimaryState.fromSprite(sprite);
    }

    public static TraceExecutionPhase phaseForReplay(TraceData trace,
                                                     TraceFrame previous,
                                                     TraceFrame current) {
        if (shouldUseLegacyS3kAizIntroHeuristic(trace, current)) {
            int firstLevelFrame = findFirstLevelGameplayFrame(trace);
            if (current.frame() < firstLevelFrame) {
                // The AIZ end-to-end trace starts while Game_Mode is $4C
                // (Level with transition bit set). Player_1/Player_2 RAM still
                // contains title-screen objects such as Obj_TitleBanner and
                // Obj_TitleSelection (sonic3k.asm:5995, 6168), not gameplay
                // Sonic/Tails. Advance the BK2/VBlank cursor for these frames,
                // but do not tick the loaded AIZ level until the first real
                // Level frame at the Obj_AIZPlaneIntro spawn point.
                return TraceExecutionPhase.VBLANK_ONLY;
            }
            if (current.frame() == firstLevelFrame) {
                // The first Level-mode frame (Game_Mode just transitioned from
                // $8C to $0C) is the boundary between ROM's synchronous setup
                // block (loc_62FE..loc_7882 -- Get_LevelSizeStart +
                // setup-DeformBgLayer + SpawnLevelMainSprites + Pal_FillBlack)
                // and the first LevelLoop iteration. ROM has already snapped
                // Camera_Y_pos via setup-DeformBgLayer (sonic3k.asm:7760), but
                // LevelLoop's Wait_VSync (sonic3k.asm:7888) -> DeformBgLayer
                // (sonic3k.asm:7897) doesn't run until the NEXT BK2 frame.
                //
                // The headless replay collapses ROM's two-phase setup into
                // initCameraForLevel + first LevelFrameStep, which already ran
                // by the time the comparator first checks this row. Treat this
                // boundary frame as VBLANK_ONLY so the engine's first physics
                // tick aligns with ROM's first LevelLoop iteration on the next
                // trace frame instead of double-counting the setup work.
                return TraceExecutionPhase.VBLANK_ONLY;
            }
            return deriveLegacyPhase(previous, current);
        }
        return TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
    }

    public static boolean shouldCompareGameplayStateForReplay(TraceExecutionPhase phase) {
        return phase == TraceExecutionPhase.FULL_LEVEL_FRAME;
    }

    private static void recordSeedFrameInputHistory(AbstractPlayableSprite sprite, int inputMask) {
        if (sprite == null) {
            return;
        }
        sprite.setLogicalInputState(
                (inputMask & AbstractPlayableSprite.INPUT_UP) != 0,
                (inputMask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                (inputMask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                (inputMask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                (inputMask & AbstractPlayableSprite.INPUT_JUMP) != 0);
        sprite.endOfTick();
    }

    private static boolean shouldUseLegacyS3kAizIntroHeuristic(TraceData trace,
                                                                TraceFrame current) {
        if (trace == null || current == null || !isLegacyS3kAizIntroTrace(trace)) {
            return false;
        }
        int gameplayStartFrame = findCheckpointFrame(trace, "gameplay_start");
        return gameplayStartFrame >= 0 && current.frame() <= gameplayStartFrame;
    }

    private static boolean usesSidekickTitleCardSeedFrame(TraceData trace) {
        if (!hasS3kSidekickTitleCardPrelude(trace)) {
            return false;
        }
        TraceFrame firstFrame = trace.getFrame(0);
        // S3K Sonic+Tails starts spawn the sidekick from Player_1 before the
        // first LevelLoop (docs/skdisasm/sonic3k.asm:8357-8369), and LevelLoop
        // then increments Level_frame_counter before Process_Sprites
        // (docs/skdisasm/sonic3k.asm:7888-7894). Some level-select traces
        // expose a strict frame-0 seed row before Sonic's own movement has
        // advanced. MGZ frame 0 is different: Sonic has already run
        // Sonic_Control/Sonic_MdAir and MoveSprite_TestGravity
        // (docs/skdisasm/sonic3k.asm:21967-21985, 22350-22361,
        // 36068-36077), so its nonzero primary speed/subpixel state must be
        // produced by driving the first BK2 input natively rather than
        // seed-compared against post-load state. This is timing classification
        // only; no recorded player or sidekick values are hydrated.
        return !firstFramePrimaryMovementAdvanced(firstFrame);
    }

    private static boolean hasS3kSidekickTitleCardPrelude(TraceData trace) {
        if (trace == null || trace.frameCount() < 2
                || !"s3k".equals(trace.metadata().game())
                || trace.metadata().recordedSidekicks().isEmpty()
                || isLegacyS3kAizIntroTrace(trace)) {
            return false;
        }
        if (replaySeedTraceIndexForTraceReplay(trace) != 0) {
            return false;
        }
        TraceFrame firstFrame = trace.getFrame(0);
        // Level setup runs SpawnLevelMainSprites, then Load_Sprites and a
        // pre-LevelLoop Process_Sprites pass before controls unlock
        // (docs/skdisasm/sonic3k.asm:7848-7859). Headless fixtures load the
        // team but do not run that setup sprite pass, so a trace whose first
        // gameplay row has Level_frame_counter=1 needs one native sidekick
        // prelude to advance Tails from Obj_Tails routine 0 to routine 2
        // (docs/skdisasm/sonic3k.asm:26085-26156) before the first driven row.
        return firstFrame.gameplayFrameCounter() == 1;
    }

    private static boolean firstFramePrimaryMovementAdvanced(TraceFrame firstFrame) {
        return firstFrame.xSpeed() != 0
                || firstFrame.ySpeed() != 0
                || firstFrame.gSpeed() != 0
                || firstFrame.xSub() != 0
                || firstFrame.ySub() != 0;
    }

    /**
     * Legacy S3K AIZ intro traces keep gameplay_frame_counter pinned during the
     * opening cutscene, so the normal execution model misclassifies many real
     * gameplay frames as VBlank-only. Use the old state-change heuristic for
     * the intro window instead of forcing every frame to full execution.
     */
    private static TraceExecutionPhase deriveLegacyPhase(TraceFrame previous,
                                                         TraceFrame current) {
        if (previous == null || current == null) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        if (!current.stateEquals(previous)) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        // Pre-level frames (SEGA/title/level-load) leave stale non-zero speed
        // fields in the Player_1 RAM block that the recorder samples. When the
        // gameplay_frame_counter stays pinned across two consecutive frames,
        // treat the state as "game is still initializing / intro cutscene
        // running" rather than trusting the stale speed fields, which would
        // otherwise misclassify frozen-state frames as VBLANK_ONLY.
        if (previous.gameplayFrameCounter() >= 0
                && current.gameplayFrameCounter() >= 0
                && previous.gameplayFrameCounter() == current.gameplayFrameCounter()) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        return current.xSpeed() != 0 || current.ySpeed() != 0
                || current.gSpeed() != 0 || current.air()
                ? TraceExecutionPhase.VBLANK_ONLY
                : TraceExecutionPhase.FULL_LEVEL_FRAME;
    }

    private static boolean isLegacyS3kAizIntroTrace(TraceData trace) {
        if (trace == null) {
            return false;
        }
        TraceMetadata metadata = trace.metadata();
        if (!"s3k".equals(metadata.game())) {
            return false;
        }
        if (metadata.zoneId() == null || metadata.zoneId() != 0 || metadata.act() != 1) {
            return false;
        }
        return trace.getEventsForFrame(0).stream()
                .filter(TraceEvent.Checkpoint.class::isInstance)
                .map(TraceEvent.Checkpoint.class::cast)
                .anyMatch(checkpoint -> "intro_begin".equals(checkpoint.name()));
    }


    private static int findCheckpointFrame(TraceData trace, String checkpointName) {
        for (int frame = 0; frame < trace.frameCount(); frame++) {
            for (TraceEvent event : trace.getEventsForFrame(frame)) {
                if (event instanceof TraceEvent.Checkpoint checkpoint
                        && checkpointName.equals(checkpoint.name())) {
                    return frame;
                }
            }
        }
        return -1;
    }

    private static int findFirstLevelGameplayFrame(TraceData trace) {
        for (int frame = 0; frame < trace.frameCount(); frame++) {
            for (TraceEvent event : trace.getEventsForFrame(frame)) {
                if (event instanceof TraceEvent.ZoneActState state
                        && state.gameMode() != null
                        && state.gameMode() == 12) {
                    return frame;
                }
                if (event instanceof TraceEvent.Checkpoint checkpoint
                        && checkpoint.gameMode() != null
                        && checkpoint.gameMode() == 12) {
                    return frame;
                }
            }
        }
        return 0;
    }

}
