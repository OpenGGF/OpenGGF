using System;
using System.Collections.Generic;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// One finished complete-run level segment with its buffered output file
    /// contents. DirToken is the recorder's per-segment output subdirectory
    /// name (zone name + 1-based act, with a _n suffix when the same
    /// zone/act arms again: ghz1, lz4, ghz1_2, ...). ZoneId and ActRaw are
    /// the raw arm-frame ROM bytes (SBZ3 arrives as lz act raw 3, Final
    /// Zone as sbz act raw 2).
    /// </summary>
    public sealed class S1CompleteRunSegmentOutput
    {
        public S1CompleteRunSegmentOutput(
            string dirToken,
            int zoneId,
            int actRaw,
            int bk2FrameOffset,
            int traceFrameCount,
            string physicsCsv,
            string auxStateJsonl,
            string metadataJson)
        {
            DirToken = dirToken;
            ZoneId = zoneId;
            ActRaw = actRaw;
            Bk2FrameOffset = bk2FrameOffset;
            TraceFrameCount = traceFrameCount;
            PhysicsCsv = physicsCsv;
            AuxStateJsonl = auxStateJsonl;
            MetadataJson = metadataJson;
        }

        public string DirToken { get; private set; }
        public int ZoneId { get; private set; }
        public int ActRaw { get; private set; }
        public int Bk2FrameOffset { get; private set; }
        public int TraceFrameCount { get; private set; }
        public string PhysicsCsv { get; private set; }
        public string AuxStateJsonl { get; private set; }
        public string MetadataJson { get; private set; }
    }

    /// <summary>
    /// Result of a complete-run capture: the finished segments' directory
    /// tokens in recording order (the segment contents were streamed to the
    /// caller's sink as each segment finalized, so a 19-segment pass never
    /// holds more than one segment's buffers).
    /// </summary>
    public sealed class S1CompleteRunCaptureResult
    {
        public S1CompleteRunCaptureResult(IList<string> segmentDirTokens)
        {
            SegmentDirTokens = segmentDirTokens;
        }

        public IList<string> SegmentDirTokens { get; private set; }

        public int SegmentCount
        {
            get { return SegmentDirTokens.Count; }
        }
    }

    /// <summary>
    /// Native port of the S1 complete-run recorder's level-segment state
    /// machine (tools/bizhawk/s1_complete_run_recorder.lua; spec
    /// tools/bizhawk-headless/docs/s1-complete-run-behavior.md), the
    /// stage-free path: one movie pass over a full playthrough BK2 arms a
    /// segment whenever game_mode reads 0x0C with the player control lock 0,
    /// records CSV v7 rows plus complete-run aux events per frame, and on
    /// every mode exit finalizes the segment and re-arms for the next —
    /// deaths and in-mode restarts never split a segment because only the
    /// mode byte is modeled. Nothing arms after the ending flips the mode
    /// away from 0x0C for good, so no ending/credits segments exist (the
    /// committed credits_* fixtures come from a different pipeline).
    /// Special-stage detours and the run manifest (OGGF_TRACE_RUN_ID) are
    /// the sibling run-mode spec — this runner covers the layout where
    /// write_run_manifest is a no-op.
    ///
    /// The frame-alignment model is the standard S1 runner's, re-run per
    /// segment: post-advance inspection, bk2_frame_offset := completed frame
    /// count at that segment's arm, the detection frame and the mode-exit
    /// frame are never recorded, and segment row N is the state after
    /// applying BK2 input row (offset + N). Cross-segment tracker carry-over
    /// is preserved by sharing ONE aux engine across all segments (spec
    /// section 5): frame 0 of a later segment diffs objects and cursor state
    /// against the previous segment's last recorded frame, and only the very
    /// first segment sees the routine_change from 0x00.
    /// </summary>
    public static class S1CompleteRunCaptureRunner
    {
        private const byte LevelGameMode = 0x0C;

        /// <summary>
        /// Captures every level segment of one movie pass, delivering each
        /// finalized segment to <paramref name="segmentSink"/> in recording
        /// order. <paramref name="stopAtFrame"/> models the Lua's
        /// S1_STOP_AT_FRAME hard stop (0 = off): when the completed frame
        /// count reaches it, the pass finalizes whatever is armed (a
        /// truncated but well-formed segment) and stops. The movie-done
        /// guard folds the Lua's frame-count and FINISHED checks into
        /// "completed frames >= movie length", evaluated after each advance
        /// before any recording, so the frame that consumed the movie's last
        /// input row is never recorded.
        /// </summary>
        public static S1CompleteRunCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string sourceBk2,
            string recordingDate,
            int stopAtFrame,
            Action<S1CompleteRunSegmentOutput> segmentSink)
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

            var state = new RunState(sourceBk2, recordingDate, segmentSink);

            using (IEnumerator<Bk2Frame> frames =
                movie.OpenFrameStream().GetEnumerator())
            {
                int rowsConsumed = 0;
                while (true)
                {
                    if (!frames.MoveNext())
                    {
                        // Movie input exhausted before the frame-count guard
                        // fired (the Lua's movie.mode() == "FINISHED"
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

                    // Top-of-function stop guard (spec section 4.5): movie
                    // done or the S1_STOP_AT_FRAME hard stop, evaluated
                    // before anything else and not gated on an armed
                    // segment, so this frame is never recorded. On the
                    // canonical run the final (FZ) segment was already
                    // finalized by the mode-exit branch, so the guard
                    // contributes zero output bytes.
                    if (frameNow >= movie.FrameCount
                        || (stopAtFrame > 0 && frameNow >= stopAtFrame))
                    {
                        state.FinalizeRunEnd();
                        break;
                    }

                    byte gameMode = S1Ram.U8(host, S1Ram.GameMode);

                    if (!state.Started)
                    {
                        // Arm gate (spec section 4.1): purely game_mode and
                        // ctrl_lock driven; zone/act are only read for
                        // naming and metadata. The detection frame is never
                        // recorded (dead-frame rule).
                        int ctrlLock = S1Ram.U16(
                            host, S1Ram.PlayerBase + S1Ram.OffCtrlLock);
                        if (gameMode == LevelGameMode && ctrlLock == 0)
                        {
                            state.ArmSegment(frameNow, host);
                        }
                        continue;
                    }

                    if (gameMode != LevelGameMode)
                    {
                        // Mode exit (spec section 4.2): finalize and re-arm
                        // posture — NOT a run end. The mode-exit frame is
                        // never recorded; the recorder idles until the arm
                        // gate fires again (never, after the ending).
                        state.FinalizeSegment();
                        continue;
                    }

                    state.AppendRow(frame, host);
                }
            }

            return state.BuildResult();
        }

        /// <summary>
        /// All mutable capture state: the per-segment arm context plus the
        /// whole-pass carry-overs (the shared aux engine and the dir-token
        /// counts, neither of which ever resets between segments).
        /// </summary>
        private sealed class RunState
        {
            private readonly string sourceBk2;
            private readonly string recordingDate;
            private readonly Action<S1CompleteRunSegmentOutput> segmentSink;

            // Whole-pass carry-over (spec section 5): one aux engine for
            // every segment, and the per-base-token arm counts driving the
            // _n dir suffixes.
            private readonly S1AuxEventEngine auxEngine =
                new S1AuxEventEngine(true);
            private readonly Dictionary<string, int> dirTokenCounts =
                new Dictionary<string, int>();
            private readonly List<string> segmentDirTokens =
                new List<string>();

            // Armed-segment state.
            private readonly StringBuilder physicsBuf = new StringBuilder();
            private readonly StringBuilder auxBuf = new StringBuilder();
            private int traceFrame;
            private int bk2FrameOffset;
            private string dirToken;
            private int startZoneId;
            private int startActRaw;
            private int startX;
            private int startY;
            private uint startRngSeed;

            internal RunState(
                string sourceBk2,
                string recordingDate,
                Action<S1CompleteRunSegmentOutput> segmentSink)
            {
                this.sourceBk2 = sourceBk2;
                this.recordingDate = recordingDate;
                this.segmentSink = segmentSink;
            }

            internal bool Started { get; private set; }

            internal void ArmSegment(int frameNow, IGpgxHost host)
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

                physicsBuf.Length = 0;
                auxBuf.Length = 0;
                physicsBuf.Append(S1TraceCsvWriter.Header).Append('\n');
            }

            internal void AppendRow(Bk2Frame frame, IGpgxHost host)
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

            internal void FinalizeSegment()
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
                    sourceBk2);
                segmentSink(new S1CompleteRunSegmentOutput(
                    dirToken,
                    startZoneId,
                    startActRaw,
                    bk2FrameOffset,
                    traceFrame,
                    physicsBuf.ToString(),
                    auxBuf.ToString(),
                    metadata));
                segmentDirTokens.Add(dirToken);
                Started = false;
                traceFrame = 0;
                physicsBuf.Length = 0;
                auxBuf.Length = 0;
            }

            internal void FinalizeRunEnd()
            {
                // finalize_run_end on the stage-free path: close an armed
                // segment; write_run_manifest is a no-op without a detour
                // or run id (spec section 4.5).
                if (Started)
                {
                    FinalizeSegment();
                }
            }

            internal S1CompleteRunCaptureResult BuildResult()
            {
                return new S1CompleteRunCaptureResult(segmentDirTokens);
            }

            /// <summary>
            /// next_segment_dir_token: the first arm of a base token yields
            /// the bare token; the n-th (n > 1) yields token_n. In the
            /// canonical run every base token is unique, so no suffix ever
            /// appears; suffixes only arise when a run re-enters the same
            /// zone/act.
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
