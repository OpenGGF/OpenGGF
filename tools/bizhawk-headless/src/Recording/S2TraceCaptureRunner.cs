using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Result of an S2 full-trace capture: the detected BK2 frame offset,
    /// the number of trace rows recorded, and the gameplay segment index the
    /// recording was attributed to (the number of COUNTED skipped segments).
    /// </summary>
    public sealed class S2TraceCaptureResult
    {
        public S2TraceCaptureResult(
            int bk2FrameOffset,
            int traceFrameCount,
            int gameplaySegment)
        {
            Bk2FrameOffset = bk2FrameOffset;
            TraceFrameCount = traceFrameCount;
            GameplaySegment = gameplaySegment;
        }

        public int Bk2FrameOffset { get; private set; }
        public int TraceFrameCount { get; private set; }
        public int GameplaySegment { get; private set; }
    }

    /// <summary>
    /// Native port of the S2 Lua trace recorder's level-gameplay frame loop
    /// (tools/bizhawk/s2_trace_recorder.lua v9.12-s2; spec
    /// tools/bizhawk-headless/docs/s2-trace-recorder-behavior.md §2-§3).
    /// Supports both profiles: plain "gameplay_unlock" (arm at the first
    /// frame with game_mode 0x0C and Sonic's move_lock 0), and
    /// "level_gated_reset_aware" (skip earlier gameplay segments up to the
    /// target index — EHZ debug/menu bootstrap segments are skipped WITHOUT
    /// counting — and discard-and-re-arm an in-progress recording whose
    /// start zone was "ehz" when the level mode is left).
    ///
    /// The frame-alignment model is the S1 runner's: post-advance
    /// inspection, bk2_frame_offset := completed frame count at detection,
    /// detection frame not recorded, trace row N = state after applying BK2
    /// input row (offset + N). Stop conditions run post-advance in the
    /// Lua's on_frame_end source order (spec §3.5): (1) game_mode leaves
    /// 0x0C — reset-aware EHZ discard or finalize, (2) BK2-end guard,
    /// (3) movie FINISHED. The frame fed by the movie's final input row IS
    /// emulated — the mode-exit check and the finalize-time live sidekick
    /// read both observe its RAM state — but is never recorded, and the
    /// pre-arm FINISHED check fires before the arm predicate.
    ///
    /// Output is buffered in memory so the reset-aware discard can throw a
    /// partial recording away without filesystem interaction (the Lua
    /// deletes the opened files; only final bytes matter). On success the
    /// buffered physics.csv / aux_state.jsonl bytes and the finalize-time
    /// metadata.json are written to the injected writers; every output line
    /// (including the last) is terminated with a single LF and the injected
    /// writers own the encoding (production uses UTF-8 without BOM).
    /// </summary>
    public static class S2TraceCaptureRunner
    {
        public const string GameplayUnlockProfile = "gameplay_unlock";
        public const string LevelGatedResetAwareProfile =
            "level_gated_reset_aware";

        private const byte LevelGameMode = 0x0C;
        private const string EhzZoneName = "ehz";

        public static S2TraceCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string traceProfile,
            int targetGameplaySegment,
            string sourceBk2,
            string recordingDate,
            TextWriter physicsCsv,
            TextWriter auxStateJsonl,
            TextWriter metadataJson,
            byte[] requiredDynamicArtRom)
        {
            if (requiredDynamicArtRom == null)
            {
                throw new ArgumentNullException(
                    "requiredDynamicArtRom",
                    "Canonical trace publication requires native load audit.");
            }
            return CaptureCore(
                movie, host, traceProfile, targetGameplaySegment, sourceBk2,
                recordingDate, physicsCsv, auxStateJsonl, metadataJson,
                requiredDynamicArtRom);
        }

        /// <summary>
        /// Scratch-only compatibility capture. Output from this path lacks
        /// mandatory native load audit and must never reach publication.
        /// </summary>
        public static S2TraceCaptureResult CaptureScratchLegacy(
            Bk2Movie movie,
            IGpgxHost host,
            string traceProfile,
            int targetGameplaySegment,
            string sourceBk2,
            string recordingDate,
            TextWriter physicsCsv,
            TextWriter auxStateJsonl,
            TextWriter metadataJson)
        {
            return CaptureCore(
                movie, host, traceProfile, targetGameplaySegment, sourceBk2,
                recordingDate, physicsCsv, auxStateJsonl, metadataJson, null);
        }

        private static S2TraceCaptureResult CaptureCore(
            Bk2Movie movie,
            IGpgxHost host,
            string traceProfile,
            int targetGameplaySegment,
            string sourceBk2,
            string recordingDate,
            TextWriter physicsCsv,
            TextWriter auxStateJsonl,
            TextWriter metadataJson,
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
            if (traceProfile == null)
            {
                throw new ArgumentNullException("traceProfile");
            }
            if (targetGameplaySegment < 0)
            {
                throw new ArgumentOutOfRangeException(
                    "targetGameplaySegment",
                    "targetGameplaySegment must be >= 0.");
            }
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }
            if (physicsCsv == null)
            {
                throw new ArgumentNullException("physicsCsv");
            }
            if (auxStateJsonl == null)
            {
                throw new ArgumentNullException("auxStateJsonl");
            }
            if (metadataJson == null)
            {
                throw new ArgumentNullException("metadataJson");
            }

            // The Lua keys every reset-aware branch on string equality with
            // the profile env value; any other string behaves as plain
            // gameplay_unlock semantics while still being written verbatim
            // into metadata.trace_profile.
            bool resetAware = traceProfile == LevelGatedResetAwareProfile;

            // Plain trace mode keeps its two output streams in memory
            // because the reset-aware profile genuinely discards an armed
            // recording (the EHZ bootstrap segment below); the run-mode
            // runners, which never discard, stream instead. The writers are
            // views over the builders, so the shared row appender can be
            // stream-shaped for both.
            var physicsBuf = new StringBuilder();
            var auxBuf = new StringBuilder();
            var physicsBufWriter = new StringWriter(physicsBuf);
            var auxBufWriter = new StringWriter(auxBuf);

            // Segment bookkeeping (spec §3.2). gameplay_segment_index
            // increments ONLY when a skipped segment ends and is counted;
            // recorded_sidekick_present is sticky across the reset-aware
            // discard (spec §3.4 "NOT reset").
            int gameplaySegmentIndex = 0;
            bool skippingSegment = false;
            string skippedSegmentZoneName = null;
            bool recordedSidekickPresent = false;

            bool started = false;
            int offset = 0;
            int traceFrame = 0;
            int startX = 0;
            int startY = 0;
            int startRomZoneId = 0;
            int startAct = 0;
            uint startRngSeed = 0;
            string startZoneName = "unknown";
            S2AuxEventEngine auxEngine = null;
            int dynamicArtLogicalFrame = 0;
            S2DynamicArtObserver dynamicArt = loadQueueRom == null
                ? null
                : new S2DynamicArtObserver(
                    loadQueueRom, host, () => dynamicArtLogicalFrame);
            DynamicArtCaptureRowBuffer rowBuffer = null;
            bool dynamicArtArmed = false;

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
                        if (started)
                        {
                            throw new InvalidDataException(
                                "Movie input stream ended before row "
                                + (offset + traceFrame) + " of "
                                + movie.FrameCount + ".");
                        }
                        throw new InvalidDataException(
                            "Target gameplay segment " + targetGameplaySegment
                            + " never became recordable: the movie ended"
                            + " after " + rowsConsumed + " input rows with"
                            + " gameplay_segment_index " + gameplaySegmentIndex
                            + (skippingSegment
                                ? " while still skipping a segment."
                                : "."));
                    }
                    Bk2Frame frame = frames.Current;
                    dynamicArtLogicalFrame = started
                        ? traceFrame
                        : rowsConsumed;
                    S1TraceCaptureRunner.ApplyFrame(frame, host);
                    host.Advance();
                    rowsConsumed++;
                    if (host.CompletedFrame != rowsConsumed)
                    {
                        throw new InvalidOperationException(
                            "GPGX CompletedFrame is " + host.CompletedFrame
                            + "; expected " + rowsConsumed + ".");
                    }

                    byte gameMode = S2Ram.U8(host, S2Ram.GameMode);

                    if (skippingSegment)
                    {
                        // While skipping, the only check is the skipped
                        // segment's end (mode leaves level). Reset-aware EHZ
                        // bootstrap segments end WITHOUT counting.
                        if (gameMode != LevelGameMode)
                        {
                            if (!(resetAware
                                && skippedSegmentZoneName == EhzZoneName))
                            {
                                gameplaySegmentIndex++;
                            }
                            skippedSegmentZoneName = null;
                            skippingSegment = false;
                        }
                        continue;
                    }

                    if (!started)
                    {
                        // Pre-arm FINISHED check (§3.5, Lua on_frame_end
                        // order): the movie is flagged FINISHED on the
                        // frame that consumed its final input row, and the
                        // Lua evaluates that check BEFORE the arm
                        // predicate — a movie whose final row's frame is
                        // the first armable frame still ends without ever
                        // becoming recordable.
                        if (rowsConsumed >= movie.FrameCount)
                        {
                            throw new InvalidDataException(
                                "Target gameplay segment "
                                + targetGameplaySegment
                                + " never became recordable: the movie"
                                + " finished after " + rowsConsumed
                                + " input rows with gameplay_segment_index "
                                + gameplaySegmentIndex + ".");
                        }

                        // Arm predicate (§3.1): game_mode 0x0C AND Sonic's
                        // move_lock word (0xB02E — the S2 offset, not S1's
                        // +0x3E) is 0.
                        int moveLock = S2Ram.U16(
                            host, S2Ram.PlayerBase + S2Ram.OffMoveLock);
                        if (gameMode == LevelGameMode && moveLock == 0)
                        {
                            if (gameplaySegmentIndex < targetGameplaySegment)
                            {
                                skippedSegmentZoneName = S2Zones.ZoneName(
                                    S2Ram.U8(host, S2Ram.Zone));
                                skippingSegment = true;
                            }
                            else
                            {
                                // Arm (§3.3): capture start context from the
                                // detection frame's RAM, open (buffer) the
                                // outputs, emit the pre-trace aux events.
                                started = true;
                                offset = host.CompletedFrame;
                                traceFrame = 0;
                                startX = S2Ram.U16(
                                    host, S2Ram.PlayerBase + S2Ram.OffXPos);
                                startY = S2Ram.U16(
                                    host, S2Ram.PlayerBase + S2Ram.OffYPos);
                                startRngSeed = S2Ram.U32(host, S2Ram.RngSeed);
                                startRomZoneId = S2Ram.U8(host, S2Ram.Zone);
                                startAct = S2Ram.U8(host, S2Ram.Act);
                                startZoneName =
                                    S2Zones.ZoneName(startRomZoneId);
                                physicsBuf
                                    .Append(S2TraceCsvWriter.Header)
                                    .Append('\n');
                                auxEngine = new S2AuxEventEngine();
                                foreach (string line in auxEngine.EmitArmEvents(
                                    startRomZoneId, startAct, host))
                                {
                                    auxBuf.Append(line).Append('\n');
                                }
                                if (dynamicArt != null)
                                {
                                    dynamicArt.PublishGap();
                                    dynamicArt.ArmSegment();
                                    dynamicArtArmed = true;
                                    rowBuffer =
                                        new DynamicArtCaptureRowBuffer(
                                            physicsBufWriter,
                                            auxBufWriter,
                                            "\n");
                                }
                            }
                        }
                        continue;   // Detection frame is never recorded.
                    }

                    if (gameMode != LevelGameMode)
                    {
                        if (resetAware && startZoneName == EhzZoneName)
                        {
                            // Reset-aware discard (§3.4): the in-progress
                            // recording was the EHZ debug/menu bootstrap.
                            // Throw it away (buffers + tracker engine) and
                            // re-arm; segment bookkeeping and the sticky
                            // recorded_sidekick_present survive.
                            if (dynamicArt != null)
                            {
                                FlushDynamicArtSegment(
                                    dynamicArt, rowBuffer, traceFrame);
                                dynamicArtArmed = false;
                                rowBuffer = null;
                            }
                            started = false;
                            traceFrame = 0;
                            offset = 0;
                            startZoneName = "unknown";
                            physicsBuf.Length = 0;
                            auxBuf.Length = 0;
                            auxEngine = null;
                            continue;
                        }
                        break;      // Left level gameplay; no partial row.
                    }

                    // Movie-end stops (§3.5 conditions 2+3), evaluated
                    // AFTER the mode-exit check above, exactly the Lua
                    // source order — the frame fed by the movie's final
                    // input row is emulated (so the mode-exit branch and
                    // the finalize-time live sidekick read observe it) but
                    // never recorded. Condition 2 (BK2-end guard): the
                    // just-consumed input row is offset + traceFrame;
                    // finalize without recording when it sits at or past
                    // the movie length. Natively unreachable — the frame
                    // stream never yields rows past the movie — but kept
                    // in Lua shape. Condition 3 (FINISHED): fires on the
                    // frame that consumed the final input row.
                    if ((long)offset + traceFrame >= movie.FrameCount)
                    {
                        break;      // BK2 end; row N is not recorded.
                    }
                    if (rowsConsumed >= movie.FrameCount)
                    {
                        break;      // Movie FINISHED; row N is not recorded.
                    }

                    // Recorded row (§7 order): zone_act_state/checkpoint
                    // before the CSV row, then the frame-shared aux events.
                    if (dynamicArt != null)
                    {
                        string physicsLine;
                        IList<string> auxLines;
                        BuildRecordedRow(
                            auxEngine, traceFrame, frame, host, loadQueueRom,
                            out physicsLine, out auxLines);
                        rowBuffer.Queue(
                            physicsLine,
                            auxLines,
                            dynamicArt.PublishRow(
                                traceFrame, host.IsLagged));
                    }
                    else
                    {
                        AppendRecordedRow(
                            physicsBufWriter, auxBufWriter, auxEngine,
                            traceFrame, frame, host, loadQueueRom);
                    }
                    if (S2Ram.U8(host, S2Ram.SidekickBase + S2Ram.OffId) != 0)
                    {
                        recordedSidekickPresent = true;
                    }
                    traceFrame++;
                }

                if (dynamicArtArmed)
                {
                    FlushDynamicArtSegment(
                        dynamicArt, rowBuffer, traceFrame);
                    dynamicArtArmed = false;
                }
                // Finalize: flush the buffered files and write the final
                // metadata bytes. Sidekick presence is the recorder's rule:
                // sticky recorded_sidekick_present OR a live id at 0xB040 at
                // write time.
                bool sidekickPresent = recordedSidekickPresent
                    || S2Ram.U8(host, S2Ram.SidekickBase + S2Ram.OffId) != 0;
                physicsCsv.Write(physicsBuf.ToString());
                auxStateJsonl.Write(auxBuf.ToString());
                metadataJson.Write(S2TraceMetadataWriter.Format(
                    startRomZoneId,
                    startAct,
                    gameplaySegmentIndex,
                    offset,
                    traceFrame,
                    startX,
                    startY,
                    sidekickPresent,
                    startRngSeed,
                    traceProfile,
                    sourceBk2,
                    recordingDate,
                    loadQueueRom != null));
                return new S2TraceCaptureResult(
                    offset, traceFrame, gameplaySegmentIndex);
                }
            }
            finally
            {
                if (dynamicArt != null)
                {
                    dynamicArt.Dispose();
                }
            }
        }

        /// <summary>
        /// Appends one recorded level trace row in the recorder's exact
        /// per-frame order (spec §7): step-1 zone_act_state/checkpoint aux
        /// events, then the CSV v7 row (BK2-derived input mask), then the
        /// frame-shared aux events. Shared with the run-mode capture runner
        /// so run level segments are produced by exactly the plain-mode
        /// code (s2-run-mode-behavior.md §8).
        /// </summary>
        internal static void AppendRecordedRow(
            TextWriter physicsCsv,
            TextWriter auxStateJsonl,
            S2AuxEventEngine auxEngine,
            int traceFrame,
            Bk2Frame frame,
            IGpgxHost host,
            byte[] loadQueueRom = null)
        {
            string physicsLine;
            IList<string> auxLines;
            BuildRecordedRow(
                auxEngine, traceFrame, frame, host, loadQueueRom,
                out physicsLine, out auxLines);
            WriteLine(physicsCsv, physicsLine);
            for (int index = 0; index < auxLines.Count; index++)
            {
                WriteLine(auxStateJsonl, auxLines[index]);
            }
        }

        internal static void BuildRecordedRow(
            S2AuxEventEngine auxEngine,
            int traceFrame,
            Bk2Frame frame,
            IGpgxHost host,
            byte[] loadQueueRom,
            out string physicsLine,
            out IList<string> auxLines)
        {
            var lines = new List<string>();
            foreach (string line in
                auxEngine.ProcessFrameStart(traceFrame, host))
            {
                lines.Add(line);
            }
            physicsLine = S2TraceCsvWriter.FormatRow(
                traceFrame, S1InputMask.FromFrame(frame), host);
            foreach (string line in auxEngine.ProcessFrame(traceFrame, host))
            {
                lines.Add(line);
            }
            if (loadQueueRom != null)
            {
                lines.Add(LoadQueueStateProjector.CaptureS2(
                    traceFrame, loadQueueRom, host));
            }
            auxLines = lines;
        }

        private static void FlushDynamicArtSegment(
            S2DynamicArtObserver observer,
            DynamicArtCaptureRowBuffer rowBuffer,
            int traceFrameCount)
        {
            if (traceFrameCount > 0)
            {
                rowBuffer.FlushTerminal(
                    observer.PublishTerminal(traceFrameCount - 1));
            }
            observer.EndSegment();
        }

        private static void WriteLine(TextWriter writer, string line)
        {
            writer.Write(line);
            writer.Write('\n');
        }
    }
}
