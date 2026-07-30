using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Result of a detour-aware complete-run capture: the finished
    /// segments' manifest entries in recording order (the file contents
    /// went straight into the caller's sink as the capture produced them),
    /// the recorded transitions, and the formatted run_manifest.json bytes —
    /// null when the Lua's emission gate suppressed it (no transitions and
    /// no run id), which keeps a stage-free pass output-identical to the
    /// legacy layout.
    /// </summary>
    public sealed class S1RunCaptureResult
    {
        public S1RunCaptureResult(
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
    /// Native port of the S1 complete-run recorder's detour-aware run
    /// machinery (tools/bizhawk/s1_complete_run_recorder.lua; spec
    /// tools/bizhawk-headless/docs/s1-run-mode-behavior.md): the
    /// giant-ring special-stage detour state machine (level -> ss ->
    /// level), the 14-column s1_special_stage segment writer, transition
    /// records with their exact RAM-read moments, and run_manifest.json.
    ///
    /// Unlike S2 (where run mode is gated on OGGF_TRACE_RUN_ID), the S1
    /// detour machine is ALWAYS on — this runner is the full recorder and
    /// <paramref name="runId"/> only (a) forces manifest emission for a
    /// detour-free pass and (b) adds the run_id lines to ss metadata and
    /// the manifest. A movie that never reads game_mode $10 produces
    /// exactly the plain complete-run layout with a null manifest (the
    /// stage-free semantics are gated by S1RunCaptureRunnerStageFreeTests
    /// and the ROM-backed complete-run differential gate).
    ///
    /// Frame alignment is the complete-run model per segment: post-advance
    /// inspection, bk2_frame_offset := completed frame count at detection
    /// (level arm) or at the first $10 frame (ss entry), the detection /
    /// entry frame is never recorded, and row N is the state after applying
    /// BK2 input row (offset + N). Cross-segment tracker carry-over is
    /// preserved by sharing ONE aux engine across every level segment
    /// (spec §8) — during a detour none of its trackers update, so the
    /// return level's frame 0 diffs against the pre-detour level's final
    /// state (the fixture's ghz2 opens with a routine_change 0x02 -> 0x04
    /// for precisely this reason). Non-level modes other than $10 finalize
    /// the armed segment and RE-ARM (S1 records the whole playthrough; the
    /// S2 runner's same branch is a run stop).
    ///
    /// Segments STREAM: the runner takes writers from its
    /// <see cref="IRunSegmentSink"/> at each arm and writes rows straight
    /// through, because no armed segment is ever thrown away — every arm
    /// reaches exactly one finalize (the level/ss finalizes or the run-end
    /// funnel).
    /// </summary>
    public static class S1RunCaptureRunner
    {
        private const byte TitleScreenGameMode = 0x04;
        private const byte LevelGameMode = 0x0C;
        private const byte SpecialStageGameMode = 0x10;
        private const byte GameModeBaseMask = 0x7F;

        public const string LevelTraceProfile = "complete_run";
        public const string SpecialStageTraceProfile = "s1_special_stage";

        /// <summary>
        /// Captures a complete detour-aware pass, streaming each segment's
        /// rows into writers obtained from <paramref name="segmentSink"/>
        /// and finalizing them in recording order.
        /// <paramref name="runId"/> is null when OGGF_TRACE_RUN_ID is
        /// unset. <paramref name="luaScriptVersion"/> is the session's
        /// version stamp (production:
        /// <see cref="S1CompleteRunMetadataWriter.LuaScriptVersion"/>; the
        /// canonical run fixtures: "3.15" — see
        /// <see cref="S1RunManifestWriter"/>). <paramref name="stopAtFrame"/>
        /// models S1_STOP_AT_FRAME (0 = off); the movie-done guard folds
        /// the Lua's frame-count and FINISHED checks into "completed frames
        /// >= movie length", evaluated after each advance before any
        /// recording. Only true movie completion maps the observed final
        /// game mode into expected_movie_end_mode; a configured hard stop
        /// owns a same-frame tie and omits that field.
        /// </summary>
        public static S1RunCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            string sourceBk2,
            string recordingDate,
            string luaScriptVersion,
            int stopAtFrame,
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
                luaScriptVersion, stopAtFrame, segmentSink,
                requiredDynamicArtRom);
        }

        /// <summary>
        /// Scratch-only compatibility capture. Its segments intentionally
        /// omit mandatory native load audit and are not publishable.
        /// </summary>
        public static S1RunCaptureResult CaptureScratchLegacy(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            string sourceBk2,
            string recordingDate,
            string luaScriptVersion,
            int stopAtFrame,
            IRunSegmentSink segmentSink)
        {
            return CaptureCore(
                movie, host, runId, sourceBk2, recordingDate,
                luaScriptVersion, stopAtFrame, segmentSink, null);
        }

        private static S1RunCaptureResult CaptureCore(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            string sourceBk2,
            string recordingDate,
            string luaScriptVersion,
            int stopAtFrame,
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
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }
            if (luaScriptVersion == null)
            {
                throw new ArgumentNullException("luaScriptVersion");
            }
            if (stopAtFrame < 0)
            {
                throw new ArgumentOutOfRangeException(
                    "stopAtFrame",
                    "stopAtFrame must be >= 0 (0 = no hard stop).");
            }
            if (segmentSink == null)
            {
                throw new ArgumentNullException("segmentSink");
            }

            RunState state = null;
            S1DynamicArtObserver dynamicArt = loadQueueRom == null
                ? null
                : new S1DynamicArtObserver(
                    loadQueueRom, host,
                    () => state.DynamicArtLogicalFrame);
            state = new RunState(
                runId, sourceBk2, recordingDate, luaScriptVersion,
                segmentSink, loadQueueRom, dynamicArt);

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
                        // Movie input exhausted before the frame-count
                        // guard fired (the Lua's movie.mode() == "FINISHED"
                        // signal): finalize whatever is armed and stop.
                        state.FinalizeRunEnd(MapExpectedMovieEndMode(
                            S1Ram.U8(host, S1Ram.GameMode)));
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
                    byte gameMode = S1Ram.U8(host, S1Ram.GameMode);

                    // Top-of-function stop guard (spec §2 item 1): movie
                    // done or the S1_STOP_AT_FRAME hard stop, before any
                    // detour/row processing and not gated on an armed
                    // segment, so this frame is never recorded and a movie
                    // ending mid-$10 stops the ss tail promptly. No
                    // OGGF_BK2_FRAME_COUNT-style override exists in S1 —
                    // raw movie length only (S2 delta).
                    bool movieCompleted = frameNow >= movie.FrameCount;
                    bool hardStop =
                        stopAtFrame > 0 && frameNow >= stopAtFrame;
                    if (movieCompleted || hardStop)
                    {
                        // S1_STOP_AT_FRAME owns a same-frame tie with movie
                        // completion, matching the Lua: a configured hard
                        // stop is never authoritative endpoint metadata.
                        state.FinalizeRunEnd(
                            hardStop
                                ? null
                                : MapExpectedMovieEndMode(gameMode));
                        break;
                    }

                    // Block 1: SS entry/continuation (spec §2 item 2).
                    // Gated on `started` so a movie beginning inside $10
                    // with nothing armed can never create an ss segment or
                    // a from_segment=-1 transition.
                    if (state.Started && gameMode == SpecialStageGameMode)
                    {
                        if (!state.DetourActive)
                        {
                            state.FinalizeLevelSegment();
                            state.PushGiantRingTransition(frameNow, host);
                            state.StartSsSegment(frameNow, host);
                            state.DetourActive = true;
                            continue;   // Entry frame: no ss row (§4).
                        }
                        state.WriteSsRow(frame, host);
                        continue;
                    }

                    // Block 2: SS exit (spec §2 item 3) — first non-$10
                    // frame after the detour. Finalize the ss segment, then
                    // fall through to the arm gate on the SAME frame. No
                    // tracker reset happens here (§8) — the shared aux
                    // engine simply resumes at the next level arm.
                    if (state.DetourActive)
                    {
                        state.FinalizeSsSegment();
                        state.DetourActive = false;
                    }

                    if (!state.Started)
                    {
                        // Level arm gate (spec §2 item 4): game_mode $0C
                        // and the player obCtrlLock word 0. Arming may push
                        // a stage_exit transition; the detection frame is
                        // never recorded.
                        int ctrlLock = S1Ram.U16(
                            host, S1Ram.PlayerBase + S1Ram.OffCtrlLock);
                        if (gameMode == LevelGameMode && ctrlLock == 0)
                        {
                            state.ArmLevelSegment(frameNow, host);
                        }
                        continue;
                    }

                    if (gameMode != LevelGameMode)
                    {
                        // Level finalize + re-arm posture (spec §2 item 5):
                        // $10 was intercepted by Block 1, so this is a
                        // non-detour mode exit (got-through card, death
                        // reload, ending) — finalize and keep scanning, NOT
                        // a run end (S2 delta).
                        state.FinalizeLevelSegment();
                        continue;
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

        private static string MapExpectedMovieEndMode(byte gameMode)
        {
            // Bit 7 is S1's PreLevel form of GM_Level ($8C); the ROM clears
            // it back to $0C when pre-level work finishes. No other mode is
            // normalized.
            if ((gameMode & GameModeBaseMask) == LevelGameMode)
            {
                return "level";
            }
            if (gameMode == TitleScreenGameMode)
            {
                return "title_screen";
            }
            return null;
        }

        /// <summary>
        /// All mutable run state: the Lua's run globals (segments_done /
        /// transitions_done / segment_dir_counts / detour_active) plus the
        /// armed-segment locals and the whole-pass carry-overs (the shared
        /// aux engine and the dir-token counts, neither of which ever
        /// resets between segments).
        /// </summary>
        private sealed class RunState
        {
            private readonly string runId;
            private readonly string sourceBk2;
            private readonly string recordingDate;
            private readonly string luaScriptVersion;
            private readonly IRunSegmentSink segmentSink;
            private readonly byte[] loadQueueRom;
            private readonly S1DynamicArtObserver dynamicArt;

            private readonly List<RunManifestSegment> segments =
                new List<RunManifestSegment>();
            private readonly List<RunManifestTransition> transitions =
                new List<RunManifestTransition>();
            private readonly List<DynamicArtGapTransition>
                dynamicArtGapTransitions =
                    new List<DynamicArtGapTransition>();

            // Whole-pass carry-over (spec §8): one aux engine for every
            // level segment, and the per-base-token arm counts driving the
            // _n dir suffixes. Level and ss bases share ONE counter table,
            // exactly like the Lua's segment_dir_counts (the bare "ss"
            // detour token never collides with the zone-7 "ss1".."ss4"
            // level namespace).
            private readonly S1AuxEventEngine auxEngine =
                new S1AuxEventEngine(true);
            private readonly Dictionary<string, int> dirTokenCounts =
                new Dictionary<string, int>();

            // Armed-segment state (shared between level and ss segments,
            // exactly one of which can be armed at a time). The two writers
            // belong to the sink and are live only between the arm and its
            // finalize; nothing here holds the segment's bytes, because no
            // armed S1 segment is ever thrown away — every arm reaches
            // exactly one finalize, through the level/ss finalizes or the
            // run-end funnel.
            private TextWriter physicsWriter;
            private TextWriter auxWriter;
            private int traceFrame;
            private int bk2FrameOffset;
            private string dirToken;
            private DynamicArtCaptureRowBuffer rowBuffer;
            private bool dynamicArtAdvanceMarked;

            // Level-segment arm context.
            private int startZoneId;
            private int startActRaw;
            private int startX;
            private int startY;
            private uint startRngSeed;

            // SS-segment arm context.
            private int currentSsIndex;
            private bool ssArmed;

            private bool manifestWritten;
            private string runManifestJson;

            internal RunState(
                string runId,
                string sourceBk2,
                string recordingDate,
                string luaScriptVersion,
                IRunSegmentSink segmentSink,
                byte[] loadQueueRom,
                S1DynamicArtObserver dynamicArt)
            {
                this.runId = runId;
                this.sourceBk2 = sourceBk2;
                this.recordingDate = recordingDate;
                this.luaScriptVersion = luaScriptVersion;
                this.segmentSink = segmentSink;
                this.loadQueueRom = loadQueueRom;
                this.dynamicArt = dynamicArt;
            }

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
                startX = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffXPos);
                startY = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffYPos);
                startRngSeed = S1Ram.U32(host, S1Ram.Random);
                startZoneId = S1Ram.U8(host, S1Ram.Zone);
                startActRaw = S1Ram.U8(host, S1Ram.Act);
                dirToken = NextDirToken(
                    S1TraceMetadataWriter.ZoneName(startZoneId)
                    + Dec(startActRaw + 1));

                // Post-SS re-arm: when the previous finished segment was a
                // special stage, this arm is that stage's exit boundary
                // (spec §3). Fields are read on THIS arm frame, not the
                // first non-$10 frame; S1 carries the ring count through
                // the SS round trip, so rings_after is genuinely non-zero
                // (S2 delta: its ROM reload zeroes it).
                if (segments.Count > 0
                    && segments[segments.Count - 1].Kind
                        == RunManifestSegment.SpecialStageKind)
                {
                    var exit = new RunManifestTransition(
                        segments.Count - 1,
                        segments.Count,
                        RunManifestTransition.StageExitKind,
                        frameNow);
                    exit.RingsAfter = S1Ram.U16(host, S1Ram.RingCount);
                    exit.EmeraldsAfter = S1Ram.U8(host, S1Ram.Emeralds);
                    transitions.Add(exit);
                }

                OpenSegmentStreams();
                WriteLine(physicsWriter, S1TraceCsvWriter.Header);
                ArmDynamicArtSegment();
            }

            internal void AppendLevelRow(Bk2Frame frame, IGpgxHost host)
            {
                string physicsLine = S1TraceCsvWriter.FormatRow(
                    traceFrame, S1InputMask.FromFrame(frame), host);
                var auxLines = new List<string>();
                foreach (string line in auxEngine.ProcessFrame(
                    traceFrame, host, host.IsLagged, host.LagCount))
                {
                    auxLines.Add(line);
                }
                if (loadQueueRom != null)
                {
                    auxLines.Add(LoadQueueStateProjector.CaptureS1(
                        traceFrame, loadQueueRom, host));
                }
                if (dynamicArt != null)
                {
                    rowBuffer.Queue(
                        physicsLine,
                        auxLines,
                        dynamicArt.PublishRow(
                            traceFrame, host.IsLagged));
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
            /// Level finalize order (Lua L1487-1500 / SS-entry inline
            /// finalize L1377-1384): metadata, then the segments_done
            /// append. S1 level metadata is byte-identical in and out of
            /// run context — no run_id / segment_index lines (spec §7).
            /// </summary>
            internal void FinalizeLevelSegment()
            {
                FinalizeDynamicArtSegment();
                string metadata = S1CompleteRunMetadataWriter.Format(
                    startZoneId,
                    startActRaw,
                    bk2FrameOffset,
                    traceFrame,
                    startX,
                    startY,
                    startRngSeed,
                    recordingDate,
                    sourceBk2,
                    luaScriptVersion,
                    loadQueueRom != null);
                var entry = new RunManifestSegment(
                    dirToken,
                    RunManifestSegment.LevelKind,
                    LevelTraceProfile,
                    bk2FrameOffset,
                    traceFrame,
                    startZoneId,
                    startActRaw + 1,
                    null);
                segments.Add(entry);
                CloseSegmentStreams(entry, metadata);
                Started = false;
                traceFrame = 0;
            }

            /// <summary>
            /// giant_ring transition, pushed at ss entry AFTER the level
            /// append so the indices already count the just-finished level.
            /// Both RAM fields are read on the entry frame (spec §3);
            /// emeralds_before 0 still renders (presence by kind, not
            /// value).
            /// </summary>
            internal void PushGiantRingTransition(
                int frameNow, IGpgxHost host)
            {
                var entry = new RunManifestTransition(
                    segments.Count - 1,
                    segments.Count,
                    RunManifestTransition.GiantRingKind,
                    frameNow);
                entry.RingsBefore = S1Ram.U16(host, S1Ram.RingCount);
                entry.EmeraldsBefore = S1Ram.U8(host, S1Ram.Emeralds);
                transitions.Add(entry);
            }

            internal void StartSsSegment(int frameNow, IGpgxHost host)
            {
                dirToken = NextDirToken("ss");
                Started = true;
                ssArmed = true;
                bk2FrameOffset = frameNow;
                traceFrame = 0;
                // v_lastspecial, sampled at arm time BEFORE SS_Load runs
                // (spec §4 sampling-window caveat; the Lua's finalize-time
                // re-read is a stdout self-check with no output-file
                // effect, so it is not ported).
                currentSsIndex = S1Ram.U8(host, S1Ram.LastSpecial);
                // The ss aux writer is opened and never written to: the ss
                // aux file stays byte-empty and must still be published.
                OpenSegmentStreams();
                WriteLine(physicsWriter, S1SpecialStageCsvWriter.Header);
                ArmDynamicArtSegment();
            }

            internal void WriteSsRow(Bk2Frame frame, IGpgxHost host)
            {
                string physicsLine = S1SpecialStageCsvWriter.FormatRow(
                    traceFrame,
                    S1InputMask.FromFrame(frame),
                    host.IsLagged,
                    host);
                if (dynamicArt != null)
                {
                    rowBuffer.Queue(
                        physicsLine,
                        new List<string>(),
                        dynamicArt.PublishRow(
                            traceFrame, host.IsLagged));
                    dynamicArtAdvanceMarked = false;
                }
                else
                {
                    WriteLine(physicsWriter, physicsLine);
                }
                traceFrame++;
            }

            /// <summary>
            /// SS finalize (spec §4): idempotent via the armed flag;
            /// appends the kind "special_stage" entry with the arm-time
            /// special_stage_index and hardcoded zone_id/act 0.
            /// segment_index in the ss metadata counts the segments
            /// finished BEFORE this one.
            /// </summary>
            internal void FinalizeSsSegment()
            {
                if (!ssArmed)
                {
                    return;
                }
                FinalizeDynamicArtSegment();
                string metadata = S1SpecialStageMetadataWriter.Format(
                    currentSsIndex,
                    bk2FrameOffset,
                    traceFrame,
                    sourceBk2,
                    luaScriptVersion,
                    recordingDate,
                    runId,
                    segments.Count,
                    dynamicArt != null);
                var entry = new RunManifestSegment(
                    dirToken,
                    RunManifestSegment.SpecialStageKind,
                    SpecialStageTraceProfile,
                    bk2FrameOffset,
                    traceFrame,
                    0,
                    0,
                    currentSsIndex);
                segments.Add(entry);
                CloseSegmentStreams(entry, metadata);
                Started = false;
                ssArmed = false;
                traceFrame = 0;
            }

            /// <summary>
            /// Single end-of-run funnel (Lua finalize_run_end L757-769):
            /// `Started` is true during BOTH an armed level segment and an
            /// armed ss segment, so the detour route must be checked first
            /// — running the level finalize mid-detour would emit a bogus
            /// level entry over the ss segment's streams. The manifest is
            /// then attempted exactly once with the caller's nullable
            /// authoritative endpoint, gated per spec §1: emitted iff
            /// at least one transition occurred OR a run id was supplied
            /// (an empty run id string still counts — the caller maps env
            /// presence to non-null).
            /// </summary>
            internal void FinalizeRunEnd(string expectedMovieEndMode)
            {
                if (DetourActive)
                {
                    FinalizeSsSegment();
                    DetourActive = false;
                }
                else if (Started)
                {
                    FinalizeLevelSegment();
                }
                PublishDynamicArtGap();
                if (!manifestWritten)
                {
                    if (transitions.Count > 0 || runId != null
                        || dynamicArtGapTransitions.Count > 0)
                    {
                        runManifestJson = S1RunManifestWriter.Format(
                            runId,
                            sourceBk2,
                            luaScriptVersion,
                            expectedMovieEndMode,
                            segments,
                            transitions,
                            dynamicArt == null
                                ? null
                                : dynamicArtGapTransitions);
                    }
                    manifestWritten = true;
                }
            }

            internal S1RunCaptureResult BuildResult()
            {
                if (!manifestWritten)
                {
                    throw new InvalidOperationException(
                        "Run capture ended without a run-end finalize.");
                }
                return new S1RunCaptureResult(
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
                dynamicArt.ArmSegment();
                rowBuffer = new DynamicArtCaptureRowBuffer(
                    physicsWriter, auxWriter, "\n");
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

            /// <summary>
            /// next_segment_dir_token: the first arm of a base token yields
            /// the bare token; the n-th (n > 1) yields token_n.
            /// </summary>
            private string NextDirToken(string baseToken)
            {
                int count;
                dirTokenCounts.TryGetValue(baseToken, out count);
                count++;
                dirTokenCounts[baseToken] = count;
                return count == 1 ? baseToken : baseToken + "_" + Dec(count);
            }

            private static string Dec(int value)
            {
                return value.ToString(
                    System.Globalization.CultureInfo.InvariantCulture);
            }
        }
    }
}
