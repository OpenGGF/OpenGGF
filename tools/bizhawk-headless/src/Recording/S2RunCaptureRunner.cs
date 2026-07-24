using System;
using System.Collections.Generic;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// One finished run segment with its buffered output file contents.
    /// DirToken is the per-segment output subdirectory name (seg1_ehz1,
    /// ss, ss_2, ...); the manifest entry carries the run_manifest.json
    /// fields. Special-stage segments carry the v9.13-s2 hook-free SS aux
    /// event stream (spec §11.3; byte-empty before that revision).
    /// </summary>
    public sealed class S2RunSegmentOutput
    {
        public S2RunSegmentOutput(
            RunManifestSegment manifestEntry,
            string physicsCsv,
            string auxStateJsonl,
            string metadataJson)
        {
            ManifestEntry = manifestEntry;
            PhysicsCsv = physicsCsv;
            AuxStateJsonl = auxStateJsonl;
            MetadataJson = metadataJson;
        }

        public RunManifestSegment ManifestEntry { get; private set; }

        public string DirToken
        {
            get { return ManifestEntry.Dir; }
        }

        public string PhysicsCsv { get; private set; }
        public string AuxStateJsonl { get; private set; }
        public string MetadataJson { get; private set; }
    }

    /// <summary>
    /// Result of a run-mode capture: the finished segments in recording
    /// order, the recorded transitions, and the formatted run_manifest.json
    /// bytes (written at the run root).
    /// </summary>
    public sealed class S2RunCaptureResult
    {
        public S2RunCaptureResult(
            IList<S2RunSegmentOutput> segments,
            IList<RunManifestTransition> transitions,
            string runManifestJson)
        {
            Segments = segments;
            Transitions = transitions;
            RunManifestJson = runManifestJson;
        }

        public IList<S2RunSegmentOutput> Segments { get; private set; }
        public IList<RunManifestTransition> Transitions { get; private set; }
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
    /// exactly. All output is buffered in memory; the caller publishes the
    /// per-segment files and the manifest atomically.
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
            int effectiveMovieLength)
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

            var state = new RunState(
                runId, sourceBk2, recordingDate,
                effectiveMovieLength == 0
                    ? movie.FrameCount
                    : effectiveMovieLength);

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

            private readonly List<S2RunSegmentOutput> segments =
                new List<S2RunSegmentOutput>();
            private readonly List<RunManifestTransition> transitions =
                new List<RunManifestTransition>();

            // Armed-segment state (shared between level and ss segments,
            // exactly one of which can be armed at a time).
            private readonly StringBuilder physicsBuf = new StringBuilder();
            private readonly StringBuilder auxBuf = new StringBuilder();
            private int traceFrame;
            private int bk2FrameOffset;
            private string dirToken;

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

            internal RunState(
                string runId,
                string sourceBk2,
                string recordingDate,
                int effectiveMovieLength)
            {
                this.runId = runId;
                this.sourceBk2 = sourceBk2;
                this.recordingDate = recordingDate;
                EffectiveMovieLength = effectiveMovieLength;
            }

            internal int EffectiveMovieLength { get; private set; }
            internal bool Started { get; private set; }
            internal bool DetourActive { get; set; }

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
                    && segments[segments.Count - 1].ManifestEntry.Kind
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

                physicsBuf.Length = 0;
                auxBuf.Length = 0;
                physicsBuf.Append(S2TraceCsvWriter.Header).Append('\n');
                auxEngine = new S2AuxEventEngine();
                foreach (string line in auxEngine.EmitArmEvents(
                    startRomZoneId, startAct, host))
                {
                    auxBuf.Append(line).Append('\n');
                }
            }

            internal void AppendLevelRow(Bk2Frame frame, IGpgxHost host)
            {
                S2TraceCaptureRunner.AppendRecordedRow(
                    physicsBuf, auxBuf, auxEngine, traceFrame, frame, host);
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
                    segments.Count);
                segments.Add(new S2RunSegmentOutput(
                    new RunManifestSegment(
                        dirToken,
                        RunManifestSegment.LevelKind,
                        S2TraceCaptureRunner.GameplayUnlockProfile,
                        bk2FrameOffset,
                        traceFrame,
                        S2Zones.EngineZoneId(startRomZoneId),
                        S2Zones.ApparentAct(startRomZoneId, startAct) + 1,
                        null),
                    physicsBuf.ToString(),
                    auxBuf.ToString(),
                    metadata));
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
                physicsBuf.Length = 0;
                auxBuf.Length = 0;
                physicsBuf.Append(S2SpecialStageCsvWriter.Header)
                    .Append('\n');
                // v9.13-s2 (§11.3): seed the per-detour aux trackers from
                // RAM and emit the frame -1 pre-trace snapshot, sampled on
                // the $10 entry frame (frame -1 = pre-row-0).
                ssAuxEngine = new S2SpecialStageAuxEventEngine(host);
                auxBuf.Append(ssAuxEngine.FormatPretraceSnapshot(host))
                    .Append('\n');
            }

            internal void WriteSsRow(Bk2Frame frame, IGpgxHost host)
            {
                bool lagged = host.IsLagged;
                physicsBuf.Append(S2SpecialStageCsvWriter.FormatRow(
                    traceFrame,
                    S2SpecialStageCsvWriter.InputMask(frame),
                    0,
                    lagged,
                    host));
                physicsBuf.Append('\n');
                // v9.13-s2 (§11.3): SS aux events after the physics row and
                // before the trace_frame increment, in the standalone's
                // record_frame order.
                foreach (string line in ssAuxEngine.EmitRowEvents(
                    traceFrame, lagged, host))
                {
                    auxBuf.Append(line).Append('\n');
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
                string metadata = S2SpecialStageMetadataWriter.Format(
                    currentSsIndex,
                    bk2FrameOffset,
                    traceFrame,
                    sourceBk2,
                    recordingDate,
                    runId,
                    segments.Count);
                segments.Add(new S2RunSegmentOutput(
                    new RunManifestSegment(
                        dirToken,
                        RunManifestSegment.SpecialStageKind,
                        "s2_special_stage",
                        bk2FrameOffset,
                        traceFrame,
                        0,
                        0,
                        currentSsIndex),
                    physicsBuf.ToString(),
                    auxBuf.ToString(),
                    metadata));
                Started = false;
                ssArmed = false;
                traceFrame = 0;
                ssAuxEngine = null;
            }

            /// <summary>
            /// Single end-of-run funnel (§2): `Started` is true during BOTH
            /// an armed level segment and an armed ss segment, so the
            /// detour route must be checked first — running the level
            /// finalize mid-detour would emit a bogus level entry with the
            /// ss segment's buffers. The manifest is written exactly once.
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
                // A reload transition still pending at run end (the run
                // terminated mid-reload, before the completing re-arm) is
                // discarded, never emitted (§11.2): its to_segment index
                // would point past the last manifest segment.
                pendingReload = null;
                if (!manifestWritten)
                {
                    var manifestSegments =
                        new List<RunManifestSegment>(segments.Count);
                    foreach (S2RunSegmentOutput segment in segments)
                    {
                        manifestSegments.Add(segment.ManifestEntry);
                    }
                    runManifestJson = S2RunManifestWriter.Format(
                        runId, sourceBk2, manifestSegments, transitions);
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
                    segments, transitions, runManifestJson);
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
