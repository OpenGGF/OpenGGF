using System;
using System.Collections.Generic;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// One finished run segment with its buffered output file contents.
    /// ManifestEntry carries the run_manifest.json fields; DirToken is the
    /// per-segment output subdirectory name (ghz1, ss, ghz2, ghz1_2, ...).
    /// Special-stage segments always have a byte-empty aux file.
    /// </summary>
    public sealed class S1RunSegmentOutput
    {
        public S1RunSegmentOutput(
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
    /// Result of a detour-aware complete-run capture: the finished
    /// segments' manifest entries in recording order (the file contents
    /// were streamed to the caller's sink as each segment finalized), the
    /// recorded transitions, and the formatted run_manifest.json bytes —
    /// null when the Lua's emission gate suppressed it (no transitions and
    /// no run id), which keeps a stage-free pass output-identical to the
    /// legacy layout.
    /// </summary>
    public sealed class S1RunCaptureResult
    {
        public S1RunCaptureResult(
            IList<RunManifestSegment> segments,
            IList<RunManifestTransition> transitions,
            string runManifestJson)
        {
            Segments = segments;
            Transitions = transitions;
            RunManifestJson = runManifestJson;
        }

        public IList<RunManifestSegment> Segments { get; private set; }
        public IList<RunManifestTransition> Transitions { get; private set; }
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
    /// exactly the plain complete-run layout with a null manifest
    /// (<see cref="S1CompleteRunCaptureRunner"/> is the equivalent
    /// stage-free engine).
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
    /// </summary>
    public static class S1RunCaptureRunner
    {
        private const byte LevelGameMode = 0x0C;
        private const byte SpecialStageGameMode = 0x10;

        public const string LevelTraceProfile = "complete_run";
        public const string SpecialStageTraceProfile = "s1_special_stage";

        /// <summary>
        /// Captures a complete detour-aware pass, delivering each finalized
        /// segment to <paramref name="segmentSink"/> in recording order.
        /// <paramref name="runId"/> is null when OGGF_TRACE_RUN_ID is
        /// unset. <paramref name="luaScriptVersion"/> is the session's
        /// version stamp (production:
        /// <see cref="S1CompleteRunMetadataWriter.LuaScriptVersion"/>; the
        /// canonical run fixtures: "3.15" — see
        /// <see cref="S1RunManifestWriter"/>). <paramref name="stopAtFrame"/>
        /// models S1_STOP_AT_FRAME (0 = off); the movie-done guard folds
        /// the Lua's frame-count and FINISHED checks into "completed frames
        /// >= movie length", evaluated after each advance before any
        /// recording.
        /// </summary>
        public static S1RunCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            string sourceBk2,
            string recordingDate,
            string luaScriptVersion,
            int stopAtFrame,
            Action<S1RunSegmentOutput> segmentSink)
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

            var state = new RunState(
                runId, sourceBk2, recordingDate, luaScriptVersion,
                segmentSink);

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
                        state.FinalizeRunEnd();
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

                    // Top-of-function stop guard (spec §2 item 1): movie
                    // done or the S1_STOP_AT_FRAME hard stop, before any
                    // detour/row processing and not gated on an armed
                    // segment, so this frame is never recorded and a movie
                    // ending mid-$10 stops the ss tail promptly. No
                    // OGGF_BK2_FRAME_COUNT-style override exists in S1 —
                    // raw movie length only (S2 delta).
                    if (frameNow >= movie.FrameCount
                        || (stopAtFrame > 0 && frameNow >= stopAtFrame))
                    {
                        state.FinalizeRunEnd();
                        break;
                    }

                    byte gameMode = S1Ram.U8(host, S1Ram.GameMode);

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

            return state.BuildResult();
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
            private readonly Action<S1RunSegmentOutput> segmentSink;

            private readonly List<RunManifestSegment> segments =
                new List<RunManifestSegment>();
            private readonly List<RunManifestTransition> transitions =
                new List<RunManifestTransition>();

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
            // exactly one of which can be armed at a time).
            private readonly StringBuilder physicsBuf = new StringBuilder();
            private readonly StringBuilder auxBuf = new StringBuilder();
            private int traceFrame;
            private int bk2FrameOffset;
            private string dirToken;

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
                Action<S1RunSegmentOutput> segmentSink)
            {
                this.runId = runId;
                this.sourceBk2 = sourceBk2;
                this.recordingDate = recordingDate;
                this.luaScriptVersion = luaScriptVersion;
                this.segmentSink = segmentSink;
            }

            internal bool Started { get; private set; }
            internal bool DetourActive { get; set; }

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

                physicsBuf.Length = 0;
                auxBuf.Length = 0;
                physicsBuf.Append(S1TraceCsvWriter.Header).Append('\n');
            }

            internal void AppendLevelRow(Bk2Frame frame, IGpgxHost host)
            {
                physicsBuf.Append(S1TraceCsvWriter.FormatRow(
                    traceFrame,
                    S1InputMask.FromFrame(frame),
                    host));
                physicsBuf.Append('\n');
                foreach (string line in auxEngine.ProcessFrame(
                    traceFrame, host, host.IsLagged, host.LagCount))
                {
                    auxBuf.Append(line).Append('\n');
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
                    luaScriptVersion);
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
                segmentSink(new S1RunSegmentOutput(
                    entry,
                    physicsBuf.ToString(),
                    auxBuf.ToString(),
                    metadata));
                Started = false;
                traceFrame = 0;
                physicsBuf.Length = 0;
                auxBuf.Length = 0;
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
                physicsBuf.Length = 0;
                auxBuf.Length = 0;    // The ss aux file stays byte-empty.
                physicsBuf.Append(S1SpecialStageCsvWriter.Header)
                    .Append('\n');
            }

            internal void WriteSsRow(Bk2Frame frame, IGpgxHost host)
            {
                physicsBuf.Append(S1SpecialStageCsvWriter.FormatRow(
                    traceFrame,
                    S1InputMask.FromFrame(frame),
                    host.IsLagged,
                    host));
                physicsBuf.Append('\n');
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
                string metadata = S1SpecialStageMetadataWriter.Format(
                    currentSsIndex,
                    bk2FrameOffset,
                    traceFrame,
                    sourceBk2,
                    luaScriptVersion,
                    recordingDate,
                    runId,
                    segments.Count);
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
                segmentSink(new S1RunSegmentOutput(
                    entry,
                    physicsBuf.ToString(),
                    auxBuf.ToString(),
                    metadata));
                Started = false;
                ssArmed = false;
                traceFrame = 0;
                physicsBuf.Length = 0;
                auxBuf.Length = 0;
            }

            /// <summary>
            /// Single end-of-run funnel (Lua finalize_run_end L757-769):
            /// `Started` is true during BOTH an armed level segment and an
            /// armed ss segment, so the detour route must be checked first
            /// — running the level finalize mid-detour would emit a bogus
            /// level entry with the ss segment's buffers. The manifest is
            /// then attempted exactly once, gated per spec §1: emitted iff
            /// at least one transition occurred OR a run id was supplied
            /// (an empty run id string still counts — the caller maps env
            /// presence to non-null).
            /// </summary>
            internal void FinalizeRunEnd()
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
                if (!manifestWritten)
                {
                    if (transitions.Count > 0 || runId != null)
                    {
                        runManifestJson = S1RunManifestWriter.Format(
                            runId,
                            sourceBk2,
                            luaScriptVersion,
                            segments,
                            transitions);
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
                    segments, transitions, runManifestJson);
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
