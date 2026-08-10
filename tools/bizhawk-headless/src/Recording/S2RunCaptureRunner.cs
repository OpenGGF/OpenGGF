using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Result of a run-mode capture: the finished segments' manifest
    /// entries in recording order (the file contents went straight into the
    /// caller's sink as the capture produced them), the recorded
    /// transitions, and the formatted run_manifest.json bytes (written at
    /// the run root).
    /// </summary>
    public sealed class S2RunCaptureResult
    {
        public S2RunCaptureResult(
            IList<RunManifestSegment> segments,
            IList<RunManifestTransition> transitions,
            IList<DynamicArtGapTransition> dynamicArtGapTransitions,
            string runManifestJson)
        {
            Segments = segments;
            Transitions = transitions;
            DynamicArtGapTransitions = dynamicArtGapTransitions;
            RunManifestJson = runManifestJson;
        }

        public IList<RunManifestSegment> Segments { get; private set; }
        public IList<RunManifestTransition> Transitions { get; private set; }
        public IList<DynamicArtGapTransition> DynamicArtGapTransitions
        {
            get;
            private set;
        }
        public string RunManifestJson { get; private set; }
    }

    /// <summary>
    /// Native port of the S2 Lua trace recorder's run mode
    /// (OGGF_TRACE_RUN_ID; tools/bizhawk/s2_trace_recorder.lua v9.13-s2;
    /// spec tools/bizhawk-headless/docs/s2-run-mode-behavior.md incl. §11):
    /// the stage-detour state machine for giant-ring special-stage round
    /// trips (level -> ss -> level), in-level reload survival across the
    /// Game_Mode $8C title-card family (Block 1.5: death/star-post restarts,
    /// time overs, act and zone transitions, the ObjB2 SCZ->WFZ->DEZ routes),
    /// the minimal special-stage segment writer with the §11.3 hook-free SS
    /// aux event stream, and
    /// run_manifest.json. Level segments are produced by exactly the plain
    /// gameplay_unlock recorder (same arm gate, CSV v7 writer, aux event
    /// pipeline) with the run-mode metadata additions; run mode never takes
    /// the segment-skip or reset-aware branches (the capture procedure does
    /// not set OGGF_S2_TRACE_PROFILE / OGGF_TRACE_GAMEPLAY_SEGMENT, and the
    /// CLI enforces the same exclusivity).
    ///
    /// The frame-alignment model is the plain runner's: post-advance
    /// inspection, bk2_frame_offset := completed frame count at detection
    /// (level arm) or at the first $10 frame (ss arm), the detection/entry
    /// frame is never recorded, and row N is the state after applying BK2
    /// input row (offset + N). The Lua's top-of-function movie-done guard
    /// (§2 item 4b) folds natively to "completed frames >= effective movie
    /// length", checked before any other per-frame processing; with the
    /// default effective length (the BK2's own frame count) a level
    /// segment's tail matches the plain runner's folded movie-end predicate
    /// exactly.
    ///
    /// Segments STREAM: the runner takes writers from its
    /// <see cref="IRunSegmentSink"/> at each arm and writes rows straight
    /// through, because no armed segment is ever thrown away — every arm
    /// reaches exactly one finalize (the level/ss finalizes, the §11.2 $8C
    /// reload boundary, or the run-end funnel). The caller stages those
    /// writers and the manifest and publishes them atomically.
    /// </summary>
    public static class S2RunCaptureRunner
    {
        private const byte LevelGameMode = 0x0C;
        private const byte SpecialStageGameMode = 0x10;

        // v9.13-s2 (§11): Game_Mode $8C = GameModeID_Level with
        // GameModeFlag_TitleCard (bit 7) set — the in-$0C reload family
        // (death/star-post restart, time over, act 1->2, zone->zone, ObjB2
        // SCZ->WFZ->DEZ routes). Every member funnels through
        // Level_Inactive_flag -> Level: (bset #GameModeFlag_TitleCard,
        // s2.asm:4758) -> Level_StartGame (bclr, s2.asm:5082); the base mode
        // never changes across a reload, only bit 7 toggles. Exact-match on
        // purpose: $88 (Demo|TitleCard) is out of scope.
        private const byte LevelTitleCardGameMode = 0x8C;

        // Detour transition RAM fields (mainmemory addresses).
        private const int AddrBigringFlag = 0xF7CD;
        private const int AddrLastStarPostHit = 0xFE30;
        private const int AddrSavedXPos = 0xFE32;
        private const int AddrSavedYPos = 0xFE34;
        private const int AddrEmeralds = 0xFFB1;

        // v_lastspecial-equivalent index, sampled at ss arm time.
        private const int AddrSpecialStageIndex = 0xFE16;

        /// <summary>
        /// Captures a complete run. <paramref name="effectiveMovieLength"/>
        /// models the capture session's movie-length signal for the 4b
        /// movie-done guard: 0 uses the movie's own frame count (the normal
        /// case). The canonical fixture's final level segment was terminated
        /// by a capture-time effective length shorter than the committed
        /// BK2's row count (spec §2 caveat), so differential reproduction
        /// must inject that session value explicitly.
        /// </summary>
        public static S2RunCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            string sourceBk2,
            string recordingDate,
            int effectiveMovieLength,
            IRunSegmentSink segmentSink,
            byte[] requiredDynamicArtRom)
        {
            if (requiredDynamicArtRom == null)
            {
                throw new ArgumentNullException(
                    "requiredDynamicArtRom",
                    "Canonical run publication requires native load audit.");
            }
            return CaptureCore(
                movie, host, runId, sourceBk2, recordingDate,
                effectiveMovieLength, segmentSink, requiredDynamicArtRom);
        }

        /// <summary>
        /// Scratch-only compatibility capture. Its segments intentionally
        /// omit mandatory native load audit and are not publishable.
        /// </summary>
        public static S2RunCaptureResult CaptureScratchLegacy(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            string sourceBk2,
            string recordingDate,
            int effectiveMovieLength,
            IRunSegmentSink segmentSink)
        {
            return CaptureCore(
                movie, host, runId, sourceBk2, recordingDate,
                effectiveMovieLength, segmentSink, null);
        }

        private static S2RunCaptureResult CaptureCore(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            string sourceBk2,
            string recordingDate,
            int effectiveMovieLength,
            IRunSegmentSink segmentSink,
            byte[] loadQueueRom)
        {
            if (movie == null)
            {
                throw new ArgumentNullException("movie");
            }
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            if (runId == null)
            {
                throw new ArgumentNullException("runId");
            }
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }
            if (effectiveMovieLength < 0)
            {
                throw new ArgumentOutOfRangeException(
                    "effectiveMovieLength",
                    "effectiveMovieLength must be >= 0 (0 = movie length).");
            }
            if (segmentSink == null)
            {
                throw new ArgumentNullException("segmentSink");
            }

            RunState state = null;
            S2DynamicArtObserver dynamicArt = loadQueueRom == null
                ? null
                : new S2DynamicArtObserver(
                    loadQueueRom, host,
                    () => state.DynamicArtLogicalFrame);
            state = new RunState(
                runId, sourceBk2, recordingDate,
                effectiveMovieLength == 0
                    ? movie.FrameCount
                    : effectiveMovieLength,
                segmentSink,
                loadQueueRom,
                dynamicArt);

            try
            {
                using (IEnumerator<Bk2Frame> frames =
                    movie.OpenFrameStream().GetEnumerator())
                {
                int rowsConsumed = 0;
                while (true)
                {
                    if (!frames.MoveNext())
                    {
                        // Movie input exhausted before the effective-length
                        // guard fired (the Lua's movie.mode() == "FINISHED"
                        // signal): finalize whatever is armed and stop.
                        state.FinalizeRunEnd(host);
                        break;
                    }
                    Bk2Frame frame = frames.Current;
                    state.PrepareDynamicArtCursor(rowsConsumed);
                    S1TraceCaptureRunner.ApplyFrame(frame, host);
                    host.Advance();
                    rowsConsumed++;
                    if (host.CompletedFrame != rowsConsumed)
                    {
                        throw new InvalidOperationException(
                            "GPGX CompletedFrame is " + host.CompletedFrame
                            + "; expected " + rowsConsumed + ".");
                    }
                    int frameNow = rowsConsumed;

                    // 4b. Top-of-function movie-done guard (spec §2): fires
                    // before any row/detour processing on this frame, so a
                    // movie ending mid-$10 stops the ss tail promptly and a
                    // level segment records exactly (length - offset - 1)
                    // rows — one fewer than the in-loop BK2-end check, which
                    // it therefore shadows entirely.
                    if (frameNow >= state.EffectiveMovieLength)
                    {
                        state.FinalizeRunEnd(host);
                        break;
                    }

                    byte gameMode = S2Ram.U8(host, S2Ram.GameMode);

                    // Block 1: SS entry/continuation. Gated on `started` so
                    // a movie reaching $10 with no armed level segment can
                    // never create an ss segment or a from_segment=-1
                    // transition.
                    if (state.Started && gameMode == SpecialStageGameMode)
                    {
                        if (!state.DetourActive)
                        {
                            state.FinalizeLevelSegment(host);
                            state.PushStarpostSpecialTransition(
                                frameNow, host);
                            state.StartSsSegment(frameNow, host);
                            state.DetourActive = true;
                            continue;   // Entry frame: no ss row (§4).
                        }
                        state.WriteSsRow(frame, host);
                        continue;
                    }

                    // Block 2: SS exit — first non-$10 frame after the
                    // detour. Finalize the ss segment, reset the level
                    // trackers (fresh aux engine at the next arm; no files
                    // are deleted — everything is buffered per segment),
                    // then fall through to the arm gate on the SAME frame.
                    if (state.DetourActive)
                    {
                        state.FinalizeSsSegment();
                        state.DetourActive = false;
                    }

                    // Block 1.5 (v9.13-s2, §11.2): in-level reload survival.
                    // First frame Game_Mode reads $8C while a LEVEL segment
                    // is armed: finalize that segment exactly like the
                    // SS-entry sequence, capture a PENDING reload transition
                    // (pushed only at the completing re-arm, where the
                    // *_after fields are read), then fall through — $8C
                    // frames are manifest-only until the next $0C +
                    // move_lock==0 frame re-arms via the unchanged arm gate
                    // below. `Started` is false on subsequent $8C frames so
                    // this fires once per reload. An armed SS segment can
                    // never reach here ($10 frames are consumed by Block 1;
                    // the post-SS $8C reload happens unarmed, and a $10->$8C
                    // jump lands after the ss finalize above with Started
                    // already false). v9.12 answered this shape by
                    // truncating the whole run at the first reload.
                    if (state.Started
                        && gameMode == LevelTitleCardGameMode)
                    {
                        state.HandleReloadBoundary(frameNow, host);
                    }

                    if (!state.Started)
                    {
                        // Level arm gate: game_mode 0x0C and Sonic's
                        // move_lock word 0. Run mode takes neither the
                        // segment-skip nor the reset-aware branch (see class
                        // doc), so the gate is the plain arm predicate.
                        int moveLock = S2Ram.U16(
                            host, S2Ram.PlayerBase + S2Ram.OffMoveLock);
                        if (gameMode == LevelGameMode && moveLock == 0)
                        {
                            state.ArmLevelSegment(frameNow, host);
                        }
                        continue;   // Detection frame is never recorded.
                    }

                    if (gameMode != LevelGameMode)
                    {
                        // $10 was intercepted by Block 1 and $8C (the
                        // in-level reload family) by Block 1.5, so this is a
                        // genuinely terminal mode — $20 ending (the graceful
                        // complete-run end), $14 continue screen, $00 game
                        // over/sega, $18 2P results, ...: a real stop,
                        // funneled through the run-end finalize so the
                        // manifest still lands. A pending reload transition
                        // is discarded there, never emitted.
                        state.FinalizeRunEnd(host);
                        break;
                    }

                    state.AppendLevelRow(frame, host);
                }
                }
            }
            finally
            {
                if (dynamicArt != null)
                {
                    dynamicArt.Dispose();
                }
            }

            return state.BuildResult();
        }

        /// <summary>
        /// All mutable run-mode recording state (the Lua's run globals plus
        /// the armed-segment locals), with the finalize funnels.
        /// </summary>
        private sealed class RunState
        {
            private readonly string runId;
            private readonly string sourceBk2;
            private readonly string recordingDate;
            private readonly IRunSegmentSink segmentSink;

            private readonly List<RunManifestSegment> segments =
                new List<RunManifestSegment>();
            private readonly List<RunManifestTransition> transitions =
                new List<RunManifestTransition>();
            private readonly List<DynamicArtGapTransition>
                dynamicArtGapTransitions =
                    new List<DynamicArtGapTransition>();

            // Armed-segment state (shared between level and ss segments,
            // exactly one of which can be armed at a time). The two writers
            // belong to the sink and are live only between the arm and its
            // finalize; nothing here holds the segment's bytes.
            private TextWriter physicsWriter;
            private TextWriter auxWriter;
            private int traceFrame;
            private int bk2FrameOffset;
            private string dirToken;
            private DynamicArtCaptureRowBuffer rowBuffer;
            private bool dynamicArtAdvanceMarked;
            private IList<DynamicArtTransferDescriptor>
                dynamicArtInitialLedger;

            // Level-segment arm context.
            private S2AuxEventEngine auxEngine;
            private int startRomZoneId;
            private int startAct;
            private int startX;
            private int startY;
            private uint startRngSeed;

            // SS-segment arm context. The aux engine is per-detour
            // (v9.13-s2 §11.3): constructed at ss arm so ss_2+ segments
            // re-emit their own frame -1 snapshot and first-row
            // control_state.
            private int currentSsIndex;
            private bool ssArmed;
            private S2SpecialStageAuxEventEngine ssAuxEngine;
            // run_objects_end pass records, ported from the standalone
            // capture path (S2SpecialStageCaptureRunner). The Lua run port's
            // "no execute hooks" rule does not bind the native harness --
            // S2DynamicArtObserver already registers execute callbacks in
            // run mode -- and without these records the replay side has no
            // RunObjects passes to pace a special-stage interior with.
            private S2SpecialStageRunObjectsObserver ssRunObjects;

            // Run counters. Level tokens number by level arms only; the ss
            // token is bare "ss" for the first detour, "ss_2"+ afterwards.
            private int levelSegmentCount;
            private int ssSegmentCount;

            // Sticky across the whole run (never reset by the post-SS
            // tracker reset — the Lua only ever sets it).
            private bool recordedSidekickPresent;

            // v9.13-s2 (§11.2): transition record captured at a $8C reload
            // boundary (Block 1.5) but NOT yet pushed — the completing level
            // re-arm fills in the *_after fields and pushes it. If the run
            // terminates first, the pending record is discarded (never
            // emitted), so run_manifest.json always satisfies
            // TraceRunManifest.validate (to_segment < segments.size()).
            private PendingReloadTransition pendingReload;

            private bool manifestWritten;
            private string runManifestJson;
            private readonly byte[] loadQueueRom;
            private readonly S2DynamicArtObserver dynamicArt;

            internal RunState(
                string runId,
                string sourceBk2,
                string recordingDate,
                int effectiveMovieLength,
                IRunSegmentSink segmentSink,
                byte[] loadQueueRom,
                S2DynamicArtObserver dynamicArt)
            {
                this.runId = runId;
                this.sourceBk2 = sourceBk2;
                this.recordingDate = recordingDate;
                this.segmentSink = segmentSink;
                EffectiveMovieLength = effectiveMovieLength;
                this.loadQueueRom = loadQueueRom;
                this.dynamicArt = dynamicArt;
            }

            internal int EffectiveMovieLength { get; private set; }
            internal bool Started { get; private set; }
            internal bool DetourActive { get; set; }
            internal int DynamicArtLogicalFrame { get; private set; }

            internal void PrepareDynamicArtCursor(int movieLogicalFrame)
            {
                DynamicArtLogicalFrame = Started
                    ? traceFrame
                    : movieLogicalFrame;
                if (dynamicArt != null && Started)
                {
                    dynamicArt.MarkAdvanceBoundary(movieLogicalFrame);
                    dynamicArtAdvanceMarked = true;
                }
            }

            internal void ArmLevelSegment(int frameNow, IGpgxHost host)
            {
                Started = true;
                bk2FrameOffset = frameNow;
                traceFrame = 0;
                startX = S2Ram.U16(host, S2Ram.PlayerBase + S2Ram.OffXPos);
                startY = S2Ram.U16(host, S2Ram.PlayerBase + S2Ram.OffYPos);
                startRngSeed = S2Ram.U32(host, S2Ram.RngSeed);
                startRomZoneId = S2Ram.U8(host, S2Ram.Zone);
                startAct = S2Ram.U8(host, S2Ram.Act);

                levelSegmentCount++;
                dirToken = "seg" + Dec(levelSegmentCount) + "_"
                    + S2Zones.ZoneName(startRomZoneId)
                    + Dec(S2Zones.ApparentAct(startRomZoneId, startAct) + 1);

                // Post-SS re-arm: when the previous finished segment was
                // the special stage, this arm is that stage's exit
                // boundary. At this point segments == [..., level, ss], so
                // the indices are exact without adjustment; the ROM zeroes
                // ring tracking on the post-SS reload, so rings_after
                // genuinely records 0 (§3).
                if (segments.Count > 0
                    && segments[segments.Count - 1].Kind
                        == RunManifestSegment.SpecialStageKind)
                {
                    var exit = new RunManifestTransition(
                        segments.Count - 1,
                        segments.Count,
                        RunManifestTransition.StageExitKind,
                        frameNow);
                    exit.RingsAfter = S2Ram.U16(host, S2Ram.RingCount);
                    exit.EmeraldsAfter = S2Ram.U8(host, AddrEmeralds);
                    transitions.Add(exit);
                }

                // v9.13-s2 (§11.2): complete + push the pending reload
                // transition captured at the last $8C boundary (Block 1.5).
                // Mutually exclusive with the stage_exit push above at any
                // one arm: the previous finished segment is either the ss
                // (stage_exit) or a level (pending reload). Indices are
                // exact here for the same reason stage_exit's are — the
                // boundary finalize already appended the from-segment, and
                // this arm has not yet appended the new level segment.
                // *_after fields are read on this arm frame (same convention
                // as stage_exit; the ROM zeroes ring tracking on a death
                // reload — record the truth, 0 included).
                if (pendingReload != null)
                {
                    var reload = new RunManifestTransition(
                        segments.Count - 1,
                        segments.Count,
                        pendingReload.EntryKind,
                        pendingReload.ModeChangeBk2Frame);
                    reload.SavedXPos = pendingReload.SavedXPos;
                    reload.SavedYPos = pendingReload.SavedYPos;
                    reload.LastStarPostHit = pendingReload.LastStarPostHit;
                    reload.RingsBefore = pendingReload.RingsBefore;
                    reload.EmeraldsBefore = pendingReload.EmeraldsBefore;
                    reload.RingsAfter = S2Ram.U16(host, S2Ram.RingCount);
                    reload.EmeraldsAfter = S2Ram.U8(host, AddrEmeralds);
                    transitions.Add(reload);
                    pendingReload = null;
                }

                OpenSegmentStreams();
                WriteLine(physicsWriter, S2TraceCsvWriter.Header);
                auxEngine = new S2AuxEventEngine();
                foreach (string line in auxEngine.EmitArmEvents(
                    startRomZoneId, startAct, host))
                {
                    WriteLine(auxWriter, line);
                }
                ArmDynamicArtSegment();
            }

            internal void AppendLevelRow(Bk2Frame frame, IGpgxHost host)
            {
                if (dynamicArt != null)
                {
                    string physicsLine;
                    IList<string> auxLines;
                    S2TraceCaptureRunner.BuildRecordedRow(
                        auxEngine, traceFrame, frame, host, loadQueueRom,
                        out physicsLine, out auxLines);
                    rowBuffer.Queue(
                        physicsLine,
                        auxLines,
                        dynamicArt.PublishRow(
                            traceFrame, host.IsLagged));
                    dynamicArtAdvanceMarked = false;
                }
                else
                {
                    S2TraceCaptureRunner.AppendRecordedRow(
                        physicsWriter, auxWriter, auxEngine, traceFrame, frame,
                        host, loadQueueRom);
                }
                if (S2Ram.U8(host, S2Ram.SidekickBase + S2Ram.OffId) != 0)
                {
                    recordedSidekickPresent = true;
                }
                traceFrame++;
            }

            /// <summary>
            /// Level finalize order (§2): metadata (segment_index counts the
            /// segments finished BEFORE this one), then the segments_done
            /// append. The sidekick rule is the recorder's: sticky
            /// recorded_sidekick_present OR a live id at 0xB040 at write
            /// time (the finalize frame).
            /// </summary>
            internal void FinalizeLevelSegment(IGpgxHost host)
            {
                FinalizeDynamicArtSegment();
                bool sidekickPresent = recordedSidekickPresent
                    || S2Ram.U8(host, S2Ram.SidekickBase + S2Ram.OffId) != 0;
                string metadata = S2TraceMetadataWriter.Format(
                    startRomZoneId,
                    startAct,
                    0,          // gameplay_segment stays 0 for run segments.
                    bk2FrameOffset,
                    traceFrame,
                    startX,
                    startY,
                    sidekickPresent,
                    startRngSeed,
                    S2TraceCaptureRunner.GameplayUnlockProfile,
                    sourceBk2,
                    recordingDate,
                    runId,
                    segments.Count,
                    loadQueueRom != null,
                    traceFrame > 0);
                var entry = new RunManifestSegment(
                    dirToken,
                    RunManifestSegment.LevelKind,
                    S2TraceCaptureRunner.GameplayUnlockProfile,
                    bk2FrameOffset,
                    traceFrame,
                    S2Zones.EngineZoneId(startRomZoneId),
                    S2Zones.ApparentAct(startRomZoneId, startAct) + 1,
                    null);
                AttachDynamicArtInitialLedger(entry);
                segments.Add(entry);
                CloseSegmentStreams(entry, metadata);
                Started = false;
                traceFrame = 0;
                auxEngine = null;
            }

            /// <summary>
            /// Block 1.5 boundary handling (v9.13-s2, §11.2): finalize the
            /// armed level segment exactly like the SS-entry sequence, then
            /// capture the pending reload transition with every boundary
            /// field read on this first-$8C frame. Kind decision: compare
            /// the Current_ZoneAndAct word ($FFFE10) on this boundary frame
            /// against the finished segment's start zone/act — differs ->
            /// "level_advance" (act->act, zone->zone, ObjB2 routes), equal
            /// -> "death_restart" (death, star-post respawn, time over).
            /// Obj3A writes the destination into Current_ZoneAndAct on $0C
            /// tail frames BEFORE Level_Inactive lands, so comparing against
            /// the segment-START values keeps those pre-boundary tail frames
            /// from affecting classification. saved_x/y_pos and
            /// last_star_post_hit are recorded for death_restart only (the
            /// LevelOrder path clears Last_star_pole_hit); no
            /// special_bonus_entry_flag on either kind.
            /// </summary>
            internal void HandleReloadBoundary(int frameNow, IGpgxHost host)
            {
                FinalizeLevelSegment(host);
                int finishedZoneAct = (startRomZoneId << 8) | startAct;
                int boundaryZoneAct = S2Ram.U16(host, S2Ram.Zone);
                var pending = new PendingReloadTransition();
                pending.EntryKind = boundaryZoneAct != finishedZoneAct
                    ? RunManifestTransition.LevelAdvanceKind
                    : RunManifestTransition.DeathRestartKind;
                pending.ModeChangeBk2Frame = frameNow;
                pending.RingsBefore = S2Ram.U16(host, S2Ram.RingCount);
                pending.EmeraldsBefore = S2Ram.U8(host, AddrEmeralds);
                if (pending.EntryKind
                    == RunManifestTransition.DeathRestartKind)
                {
                    // The values the reload will consume.
                    pending.SavedXPos = S2Ram.U16(host, AddrSavedXPos);
                    pending.SavedYPos = S2Ram.U16(host, AddrSavedYPos);
                    pending.LastStarPostHit =
                        S2Ram.U8(host, AddrLastStarPostHit);
                }
                pendingReload = pending;
            }

            /// <summary>
            /// starpost_special transition, pushed at ss entry AFTER the
            /// level append so the indices already count the just-finished
            /// level. All RAM fields read on the entry frame (§3).
            /// </summary>
            internal void PushStarpostSpecialTransition(
                int frameNow, IGpgxHost host)
            {
                var entry = new RunManifestTransition(
                    segments.Count - 1,
                    segments.Count,
                    RunManifestTransition.StarpostSpecialKind,
                    frameNow);
                entry.SpecialBonusEntryFlag = S2Ram.U8(host, AddrBigringFlag);
                entry.SavedXPos = S2Ram.U16(host, AddrSavedXPos);
                entry.SavedYPos = S2Ram.U16(host, AddrSavedYPos);
                entry.LastStarPostHit = S2Ram.U8(host, AddrLastStarPostHit);
                entry.RingsBefore = S2Ram.U16(host, S2Ram.RingCount);
                entry.EmeraldsBefore = S2Ram.U8(host, AddrEmeralds);
                transitions.Add(entry);
            }

            internal void StartSsSegment(int frameNow, IGpgxHost host)
            {
                ssSegmentCount++;
                dirToken = ssSegmentCount == 1
                    ? "ss"
                    : "ss_" + Dec(ssSegmentCount);
                Started = true;
                ssArmed = true;
                bk2FrameOffset = frameNow;
                traceFrame = 0;
                currentSsIndex = S2Ram.U8(host, AddrSpecialStageIndex);
                OpenSegmentStreams();
                WriteLine(physicsWriter, S2SpecialStageCsvWriter.Header);
                // v9.13-s2 (§11.3): seed the per-detour aux trackers from
                // RAM and emit the frame -1 pre-trace snapshot, sampled on
                // the $10 entry frame (frame -1 = pre-row-0).
                ssAuxEngine = new S2SpecialStageAuxEventEngine(host);
                WriteLine(
                    auxWriter, ssAuxEngine.FormatPretraceSnapshot(host));
                // The observer computes trace_frame = host.CompletedFrame -
                // bk2Offset, so bk2Offset must be the emu frame carrying this
                // detour's trace_frame 0 -- the same quantity the segment
                // publishes as bk2_frame_offset, which is frameNow. Verified
                // against the BK2 input log: each segment's per-row `input`
                // column reproduces the P1 mask exactly from bk2_frame_offset
                // (ss_2 6361/6361, ss_5 6690/6690) and not from +1 (6015,
                // 6386); the standalone capture path, which passes `offset`
                // unmodified, matches at +0 for all 2991 of its passes.
                // frameNow + 1 reported an input_sample_frame one lower than
                // the trace frame the ROM's Vint_S2SS joypad read belongs to,
                // which SpecialStageRunObjectsPassBinder rejects as a BK2
                // identity mismatch at sequence 0.
                ssRunObjects = new S2SpecialStageRunObjectsObserver(
                    host, frameNow, () => traceFrame);
                ArmDynamicArtSegment();
            }

            internal void WriteSsRow(Bk2Frame frame, IGpgxHost host)
            {
                bool lagged = host.IsLagged;
                string physicsLine = S2SpecialStageCsvWriter.FormatRow(
                    traceFrame,
                    S2SpecialStageCsvWriter.InputMask(frame),
                    0,
                    lagged,
                    host);
                var auxLines = new List<string>();
                // Standalone order (S2SpecialStageCaptureRunner): the row's
                // completed RunObjects passes first, then the state-sampled
                // events, with the terminal pass published immediately after
                // the checkpoint edge.
                foreach (string line in ssRunObjects.PublishForRow(
                    traceFrame, lagged))
                {
                    auxLines.Add(line);
                }
                // v9.13-s2 (§11.3): SS aux events after the physics row and
                // before the trace_frame increment, in the standalone's
                // record_frame order.
                foreach (string line in ssAuxEngine.EmitRowEvents(
                    traceFrame, lagged, host))
                {
                    auxLines.Add(line);
                    if (line.IndexOf(
                        "\"type\":\"checkpoint\"",
                        StringComparison.Ordinal) >= 0)
                    {
                        foreach (string terminal
                            in ssRunObjects.PublishTerminal(traceFrame))
                        {
                            auxLines.Add(terminal);
                        }
                    }
                }
                if (dynamicArt != null)
                {
                    rowBuffer.Queue(
                        physicsLine,
                        auxLines,
                        dynamicArt.PublishRow(traceFrame, lagged));
                    dynamicArtAdvanceMarked = false;
                }
                else
                {
                    WriteLine(physicsWriter, physicsLine);
                    for (int index = 0; index < auxLines.Count; index++)
                    {
                        WriteLine(auxWriter, auxLines[index]);
                    }
                }
                traceFrame++;
            }

            /// <summary>
            /// SS finalize (§4): idempotent via the armed flag; appends the
            /// kind "special_stage" entry with the arm-time
            /// special_stage_index and hardcoded zone_id/act 0.
            /// </summary>
            internal void FinalizeSsSegment()
            {
                if (!ssArmed)
                {
                    return;
                }
                FinalizeDynamicArtSegment();
                string metadata = S2SpecialStageMetadataWriter.Format(
                    currentSsIndex,
                    bk2FrameOffset,
                    traceFrame,
                    sourceBk2,
                    recordingDate,
                    runId,
                    segments.Count,
                    dynamicArt != null);
                var entry = new RunManifestSegment(
                    dirToken,
                    RunManifestSegment.SpecialStageKind,
                    "s2_special_stage",
                    bk2FrameOffset,
                    traceFrame,
                    0,
                    0,
                    currentSsIndex);
                AttachDynamicArtInitialLedger(entry);
                segments.Add(entry);
                CloseSegmentStreams(entry, metadata);
                Started = false;
                ssArmed = false;
                traceFrame = 0;
                ssAuxEngine = null;
                if (ssRunObjects != null)
                {
                    ssRunObjects.Dispose();
                    ssRunObjects = null;
                }
            }

            /// <summary>
            /// Single end-of-run funnel (§2): `Started` is true during BOTH
            /// an armed level segment and an armed ss segment, so the
            /// detour route must be checked first — running the level
            /// finalize mid-detour would emit a bogus level entry with the
            /// ss segment's streams. The manifest is written exactly once.
            /// </summary>
            internal void FinalizeRunEnd(IGpgxHost host)
            {
                if (DetourActive)
                {
                    FinalizeSsSegment();
                    DetourActive = false;
                }
                else if (Started)
                {
                    FinalizeLevelSegment(host);
                }
                PublishDynamicArtGap();
                // A reload transition still pending at run end (the run
                // terminated mid-reload, before the completing re-arm) is
                // discarded, never emitted (§11.2): its to_segment index
                // would point past the last manifest segment.
                pendingReload = null;
                if (!manifestWritten)
                {
                    runManifestJson = S2RunManifestWriter.Format(
                        runId, sourceBk2, segments, transitions,
                        dynamicArt == null
                            ? null
                            : dynamicArtGapTransitions);
                    manifestWritten = true;
                }
            }

            internal S2RunCaptureResult BuildResult()
            {
                if (!manifestWritten)
                {
                    throw new InvalidOperationException(
                        "Run capture ended without a run-end finalize.");
                }
                return new S2RunCaptureResult(
                    segments, transitions, dynamicArtGapTransitions,
                    runManifestJson);
            }

            private void ArmDynamicArtSegment()
            {
                if (dynamicArt == null)
                {
                    return;
                }
                PublishDynamicArtGap();
                dynamicArtInitialLedger = dynamicArt.ArmRunSegment();
                rowBuffer = new DynamicArtCaptureRowBuffer(
                    physicsWriter, auxWriter, "\n");
            }

            private void AttachDynamicArtInitialLedger(
                RunManifestSegment entry)
            {
                if (dynamicArtInitialLedger == null)
                {
                    return;
                }
                entry.DynamicArtInitialLedger = dynamicArtInitialLedger;
                entry.DynamicArtInitialLedgerFingerprint =
                    DynamicArtTransferState.ComputeLedgerHash(
                        dynamicArtInitialLedger);
                dynamicArtInitialLedger = null;
            }

            private void FinalizeDynamicArtSegment()
            {
                if (dynamicArt == null)
                {
                    return;
                }
                if (traceFrame > 0)
                {
                    DynamicArtTransferEnvelope terminal =
                        dynamicArtAdvanceMarked
                            ? dynamicArt.PublishBoundaryTerminal(
                                traceFrame - 1)
                            : dynamicArt.PublishTerminal(traceFrame - 1);
                    rowBuffer.FlushTerminal(terminal);
                }
                else if (dynamicArtAdvanceMarked)
                {
                    DynamicArtTransferEnvelope empty =
                        dynamicArt.PublishBoundaryTerminal(0);
                    if (empty.Edges.Count != 0
                        || empty.OutstandingTransferIds.Count != 0)
                    {
                        throw new InvalidOperationException(
                            "zero-row segment cannot terminal-forward dynamic-art state");
                    }
                }
                dynamicArt.EndSegment();
                dynamicArtAdvanceMarked = false;
                rowBuffer = null;
            }

            private void PublishDynamicArtGap()
            {
                if (dynamicArt == null)
                {
                    return;
                }
                foreach (DynamicArtGapTransition transition
                    in dynamicArt.PublishGap())
                {
                    dynamicArtGapTransitions.Add(transition);
                }
            }

            /// <summary>
            /// Asks the sink for this segment's two writers. Called after
            /// the dir token is allocated and before the CSV header, so the
            /// sink sees segments in exactly the recording order.
            /// </summary>
            private void OpenSegmentStreams()
            {
                RunSegmentStreams streams = segmentSink.BeginSegment(
                    dirToken);
                physicsWriter = streams.PhysicsCsv;
                auxWriter = streams.AuxStateJsonl;
            }

            private void CloseSegmentStreams(
                RunManifestSegment entry, string metadata)
            {
                physicsWriter = null;
                auxWriter = null;
                segmentSink.EndSegment(entry, metadata);
            }

            private static void WriteLine(TextWriter writer, string line)
            {
                writer.Write(line);
                writer.Write('\n');
            }

            private static string Dec(int value)
            {
                return value.ToString(
                    System.Globalization.CultureInfo.InvariantCulture);
            }

            /// <summary>
            /// Boundary-frame capture of a $8C reload transition (the Lua's
            /// pending_reload_transition table): everything except the
            /// from/to indices and the *_after fields, which the completing
            /// re-arm supplies.
            /// </summary>
            private sealed class PendingReloadTransition
            {
                internal string EntryKind;
                internal int ModeChangeBk2Frame;
                internal int? SavedXPos;
                internal int? SavedYPos;
                internal int? LastStarPostHit;
                internal int RingsBefore;
                internal int EmeraldsBefore;
            }
        }
    }
}
