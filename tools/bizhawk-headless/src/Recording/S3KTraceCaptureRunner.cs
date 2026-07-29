using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Result of an S3K standard trace capture: the detected BK2 frame
    /// offset and the number of trace rows recorded.
    /// </summary>
    public sealed class S3KTraceCaptureResult
    {
        public S3KTraceCaptureResult(int bk2FrameOffset, int traceFrameCount)
        {
            Bk2FrameOffset = bk2FrameOffset;
            TraceFrameCount = traceFrameCount;
        }

        public int Bk2FrameOffset { get; private set; }
        public int TraceFrameCount { get; private set; }
    }

    /// <summary>
    /// Native port of the S3K standard Lua trace recorder's frame loop
    /// (tools/bizhawk/s3k_trace_recorder.lua v6.37-s3k; spec
    /// tools/bizhawk-headless/docs/s3k-profiles-and-hooks.md §1/§3).
    /// Supports all three profiles:
    ///
    /// - "gameplay_unlock" (default; any unrecognized profile string gets
    ///   these semantics while still being written verbatim into
    ///   metadata.trace_profile, exactly like the Lua's string-equality
    ///   branches): arm at the first post-advance frame with Game_mode
    ///   0x0C, P1 move_lock word 0, AND Ctrl_1_locked 0 (the S3K delta vs
    ///   S2's two-term predicate); the arm frame is NOT recorded; stop on
    ///   the first frame with Game_mode != 0x0C, never recording it.
    /// - "aiz_end_to_end": arm the moment Game_mode first enters the
    ///   level-load family ((mode &amp; 0x0F) == 0x0C, i.e. 0x0C/0x4C/0x8C);
    ///   the arm frame IS recorded as trace row 0 (the code falls through
    ///   after arming — unique to this profile). Exempt from the
    ///   gameplay-left stop, so it survives the AIZ1→AIZ2 in-place reload
    ///   and the AIZ2→HCZ handoff; it ends only via the movie stops.
    /// - "level_gated_reset_aware": arm like gameplay_unlock (arm frame
    ///   dropped); while recording, Game_mode 0x00/0x04/0x28 DISCARDS the
    ///   whole recording (files deleted in the Lua — buffers cleared
    ///   here) and re-arms, so the shipped output is the LAST armed
    ///   segment; the first frame whose Current_zone differs from the
    ///   start zone finalizes without recording that row (this check runs
    ///   BEFORE the movie-end checks); also exempt from the gameplay-left
    ///   stop, so mid-zone 0x4C/0x8C transitional frames keep recording.
    ///
    /// Frame alignment is the shared post-advance model: every RAM read
    /// happens after the advance, bk2_frame_offset := completed frame
    /// count at arm. Stop predicates are evaluated POST-advance in the
    /// Lua's on_frame_end source order — discard-and-reset, zone-leave,
    /// arm, gameplay-left, BK2-end guard (offset + trace_frame >=
    /// movie length), movie FINISHED — and a stop never records the row
    /// it fires on: the frame fed by the movie's final input row is
    /// emulated but never recorded (the movie-end stop-ordering bug class
    /// found independently in both prior ports). The CSV input column is
    /// BK2[bk2_frame_offset + trace_row] for EVERY profile (v6.30 rule,
    /// no profile-dependent adjustment): because aiz_end_to_end records
    /// its arm frame, that profile's row N input is one AHEAD of the
    /// input that produced row N's state; replay owns compensating.
    ///
    /// Lua behaviors intentionally not modeled: the OGGF_TRACE_STOP_FRAME
    /// / OGGF_BK2_FRAME_COUNT env stops (unset for every gated fixture;
    /// the CLI does not expose them), the periodic metadata rewrites
    /// (only the final bytes ship), and the first-recorded-frame
    /// start_gameplay_frame_counter recapture (its value stopped reaching
    /// metadata in v6.29). If the movie ends while armed the loop keeps
    /// advancing with idle input until a stop fires (the
    /// arm-on-final-row case finalizes with zero recorded rows, exactly
    /// like the Lua's post-movie on_frame_end); if the movie ends
    /// UNARMED the native port throws instead of reproducing the Lua's
    /// unbounded idle loop.
    ///
    /// Only level_gated_reset_aware can throw an armed recording away
    /// mid-capture, so only that profile buffers its physics.csv /
    /// aux_state.jsonl streams in memory; the other two profiles stream
    /// straight to the injected writers like the S1/S2 runners. A failed
    /// capture never publishes either way — the caller stages the writers
    /// and only link(2)s them on success — so withholding the bytes buys
    /// nothing beyond the discard. See <see cref="TraceStreamSink"/> for
    /// both modes and for why the buffered flush never materialises a
    /// second contiguous copy of the stream. metadata.json is always
    /// written at finalize time. Every output line (including the last)
    /// is terminated with a single LF and the injected writers own the
    /// encoding (production uses UTF-8 without BOM).
    /// </summary>
    public static class S3KTraceCaptureRunner
    {
        public const string GameplayUnlockProfile = "gameplay_unlock";
        public const string AizEndToEndProfile = "aiz_end_to_end";
        public const string LevelGatedResetAwareProfile =
            "level_gated_reset_aware";

        /// <summary>
        /// One LF-terminated output stream. A discardable sink (the
        /// level_gated_reset_aware profile, whose soft-reset path throws
        /// the whole armed segment away) accumulates into a
        /// <see cref="StringBuilder"/> and flushes once at finalisation;
        /// a non-discardable sink writes straight through, so the two
        /// profiles that can never discard hold no capture in memory at
        /// all.
        ///
        /// The buffered flush copies the builder in fixed-size char
        /// blocks rather than calling ToString(). ToString() would
        /// allocate a second contiguous copy of the entire stream on top
        /// of the builder's own chunks — the CNZ fixture's aux stream is
        /// 213 MB of ASCII, i.e. ~426 MB of char storage duplicated for
        /// the duration of a single Write call.
        /// </summary>
        private sealed class TraceStreamSink : TextWriter
        {
            private const int FlushBlockChars = 64 * 1024;

            private readonly TextWriter writer;

            // Non-null only when the profile can discard.
            private readonly StringBuilder buffer;

            public TraceStreamSink(TextWriter writer, bool discardable)
            {
                this.writer = writer;
                this.buffer = discardable ? new StringBuilder() : null;
            }

            public override Encoding Encoding
            {
                get { return Encoding.UTF8; }
            }

            public override void Write(char value)
            {
                if (buffer == null)
                {
                    writer.Write(value);
                }
                else
                {
                    buffer.Append(value);
                }
            }

            public override void Write(string value)
            {
                if (buffer == null)
                {
                    writer.Write(value);
                }
                else
                {
                    buffer.Append(value);
                }
            }

            public void AppendLine(string line)
            {
                Write(line);
                Write('\n');
            }

            /// <summary>
            /// Drops everything appended so far. Only the discardable
            /// mode can honor this; calling it on a streaming sink is a
            /// contract violation, not a silent no-op that would ship a
            /// segment the Lua deleted.
            /// </summary>
            public void Discard()
            {
                if (buffer == null)
                {
                    throw new InvalidOperationException(
                        "A streaming trace sink cannot discard already"
                        + " written output; only the"
                        + " level_gated_reset_aware profile discards.");
                }
                buffer.Length = 0;
            }

            public override void Flush()
            {
                if (buffer == null)
                {
                    return;     // Already written line by line.
                }
                var block = new char[FlushBlockChars];
                int total = buffer.Length;
                for (int start = 0; start < total; start += FlushBlockChars)
                {
                    int count = Math.Min(FlushBlockChars, total - start);
                    buffer.CopyTo(start, block, 0, count);
                    writer.Write(block, 0, count);
                }
                buffer.Length = 0;
            }
        }

        public static S3KTraceCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string traceProfile,
            string recordingDate,
            TextWriter physicsCsv,
            TextWriter auxStateJsonl,
            TextWriter metadataJson)
        {
            return Capture(
                movie,
                host,
                traceProfile,
                recordingDate,
                new byte[0],
                physicsCsv,
                auxStateJsonl,
                TextWriter.Null,
                metadataJson);
        }

        public static S3KTraceCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string traceProfile,
            string recordingDate,
            byte[] rom,
            TextWriter physicsCsv,
            TextWriter auxStateJsonl,
            TextWriter hardwareTimingJsonl,
            TextWriter metadataJson,
            bool loadQueueState = false)
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
            if (rom == null)
            {
                throw new ArgumentNullException("rom");
            }
            if (hardwareTimingJsonl == null)
            {
                throw new ArgumentNullException("hardwareTimingJsonl");
            }
            if (metadataJson == null)
            {
                throw new ArgumentNullException("metadataJson");
            }

            bool aiz = traceProfile == AizEndToEndProfile;
            bool levelGated = traceProfile == LevelGatedResetAwareProfile;
            S3KTraceProfile profile = aiz
                ? S3KTraceProfile.AizEndToEnd
                : (levelGated
                    ? S3KTraceProfile.LevelGatedResetAware
                    : S3KTraceProfile.GameplayUnlock);

            // Only the level-gated profile ever discards, so only it pays
            // for holding the capture in memory.
            var physicsSink = new TraceStreamSink(physicsCsv, levelGated);
            var auxSink = new TraceStreamSink(auxStateJsonl, levelGated);
            var hardwareTimingSink =
                new TraceStreamSink(hardwareTimingJsonl, levelGated);

            bool started = false;
            bool preTraceSnapshotsWritten = false;
            int offset = 0;
            int traceFrame = 0;
            int startX = 0;
            int startY = 0;
            int startZoneId = 0;
            int startAct = 0;
            uint startRngSeed = 0;
            bool hardwareCompletionAuthorityArmed = false;
            S3KAuxEventEngine auxEngine = null;
            var hardwareTimingEngine =
                new HardwareTimingEventEngine(rom);

            using (IDisposable directSubmissionObserver =
                host.RegisterExecuteCallback(
                    HardwareTimingEventEngine.ModuleChildSubmissionPc,
                    () => hardwareTimingEngine.ObserveDirectSubmissions(host)))
            using (IEnumerator<Bk2Frame> frames =
                movie.OpenFrameStream().GetEnumerator())
            {
                // One-frame lookahead: `next` always holds the BK2 row at
                // index `advanced` (the next unconsumed row) or null past
                // the movie's end. The aiz_end_to_end input column needs
                // it — row N's input row (offset + N) has not been
                // consumed yet when row N is written.
                Bk2Frame next = frames.MoveNext() ? frames.Current : null;
                int advanced = 0;
                while (true)
                {
                    Bk2Frame current = next;
                    if (current == null)
                    {
                        if (!started)
                        {
                            throw new InvalidDataException(
                                "The " + traceProfile + " recorder never"
                                + " armed a recording that survived: the"
                                + " movie ended after " + advanced
                                + " input rows.");
                        }
                        // Lua parity past the movie's end: the emulator
                        // keeps advancing with idle input until a stop
                        // fires. Reachable only when the arm consumed the
                        // movie's final input row; the BK2-end guard then
                        // fires on the first idle frame.
                        host.ClearButtons();
                    }
                    else
                    {
                        next = frames.MoveNext() ? frames.Current : null;
                        S1TraceCaptureRunner.ApplyFrame(current, host);
                    }
                    host.Advance();
                    advanced++;
                    if (host.CompletedFrame != advanced)
                    {
                        throw new InvalidOperationException(
                            "GPGX CompletedFrame is " + host.CompletedFrame
                            + "; expected " + advanced + ".");
                    }

                    byte gameMode = S3KRam.U8(host, S3KRam.GameMode);
                    if (gameMode == S3KRam.GameModeLevel)
                    {
                        // LoadLevelLoadBlock waits synchronously for its two
                        // initial KosM jobs. Preserve those jobs in the
                        // run-wide ordinal ledger, but do not export
                        // completion authority until the ordinary LevelLoop
                        // becomes observable.
                        hardwareCompletionAuthorityArmed = true;
                    }

                    // (1) Discard-and-reset (level-gated only, spec §3.2
                    // step 6): a pause+A soft reset or power-on loop
                    // surfaces as SEGA/TITLE/LevelSelect. The Lua deletes
                    // the three output files and resets ALL recorder
                    // state; only the last armed segment ships.
                    if (levelGated && started
                        && (gameMode == S3KRam.GameModeSega
                            || gameMode == S3KRam.GameModeTitle
                            || gameMode == S3KRam.GameModeLevelSelect))
                    {
                        started = false;
                        preTraceSnapshotsWritten = false;
                        offset = 0;
                        traceFrame = 0;
                        startX = 0;
                        startY = 0;
                        startZoneId = 0;
                        startAct = 0;
                        startRngSeed = 0;
                        hardwareCompletionAuthorityArmed = false;
                        physicsSink.Discard();
                        auxSink.Discard();
                        hardwareTimingSink.Discard();
                        auxEngine = null;
                        hardwareTimingEngine.Reset();
                        continue;
                    }

                    // (2) Zone-leave (level-gated only, step 7): fires
                    // BEFORE the movie-end checks and the row write; the
                    // post-leave frame is never recorded. Both gated
                    // fixtures end here during a 0x8C transitional frame.
                    if (levelGated && started
                        && S3KRam.U8(host, S3KRam.Zone) != startZoneId)
                    {
                        break;
                    }

                    // (3) Arm (step 8).
                    if (!started)
                    {
                        bool arm;
                        if (aiz)
                        {
                            arm = S3KRam.IsLevelFamilyMode(gameMode);
                        }
                        else
                        {
                            arm = gameMode == S3KRam.GameModeLevel
                                && S3KRam.U16(host,
                                    S3KRam.PlayerBase + S3KRam.OffMoveLock)
                                    == 0
                                && S3KRam.U8(host, S3KRam.Ctrl1Locked) == 0;
                        }
                        if (!arm)
                        {
                            continue;
                        }
                        started = true;
                        offset = host.CompletedFrame;
                        startX = S3KRam.U16(
                            host, S3KRam.PlayerBase + S3KRam.OffXPos);
                        startY = S3KRam.U16(
                            host, S3KRam.PlayerBase + S3KRam.OffYPos);
                        startZoneId = S3KRam.U8(host, S3KRam.Zone);
                        startAct = S3KRam.U8(host, S3KRam.Act);
                        startRngSeed = S3KRam.U32(host, S3KRam.RngSeed);
                        physicsSink.AppendLine(S3KTraceCsvWriter.Header);
                        auxEngine = new S3KAuxEventEngine(profile);
                        if (!aiz)
                        {
                            hardwareTimingEngine.ObserveFrameEnd(
                                0, host, null);
                            continue;   // Arm frame dropped (row 0 = next).
                        }
                        // aiz_end_to_end falls through: the arm frame is
                        // recorded as trace row 0.
                    }

                    // (4) Gameplay-left (step 9): gameplay_unlock only;
                    // aiz and level-gated are exempt so 0x4C/0x8C
                    // transition frames keep recording.
                    if (!aiz && !levelGated
                        && gameMode != S3KRam.GameModeLevel)
                    {
                        break;
                    }

                    // (5) Movie-end stops (step 10), in Lua source order:
                    // the BK2-end guard, then FINISHED — which appears on
                    // the on_frame_end after the advance that consumed
                    // the movie's final input row. Either way the row is
                    // NOT recorded.
                    if ((long)offset + traceFrame >= movie.FrameCount)
                    {
                        break;
                    }
                    if (advanced >= movie.FrameCount)
                    {
                        break;
                    }

                    // (6) First recorded frame only (step 11): pre-trace
                    // snapshots, written to aux before row 0's CSV row.
                    if (!preTraceSnapshotsWritten)
                    {
                        foreach (string line in
                            auxEngine.EmitPreTraceSnapshots(host))
                        {
                            auxSink.AppendLine(line);
                        }
                        preTraceSnapshotsWritten = true;
                    }

                    // (7) CSV row (step 12): input = BK2[offset + N] for
                    // every profile. For aiz that row is the not-yet-
                    // consumed lookahead (`next`, index advanced ==
                    // offset + N, non-null because the BK2-end guard
                    // passed); for the level-gated family it is the row
                    // just consumed (`current`, index advanced - 1 ==
                    // offset + N).
                    Bk2Frame inputRow = aiz ? next : current;
                    physicsSink.AppendLine(S3KTraceCsvWriter.FormatRow(
                        traceFrame,
                        S1InputMask.FromFrame(inputRow),
                        host));

                    // (8) Aux cascade (step 13) in the engine's fixed
                    // order, then the frame counter (step 14).
                    foreach (string line in
                        auxEngine.ProcessFrame(traceFrame, host))
                    {
                        auxSink.AppendLine(line);
                    }
                    hardwareTimingEngine.ObserveFrameEnd(
                        traceFrame,
                        host,
                        hardwareCompletionAuthorityArmed
                            ? hardwareTimingSink
                            : null);
                    if (loadQueueState)
                    {
                        foreach (string line in LoadQueueStateProjector.CaptureS3k(
                            traceFrame, rom, host))
                        {
                            auxSink.AppendLine(line);
                        }
                    }
                    traceFrame++;
                }

                // Finalisation (Lua main loop after finished): the
                // level-gated gameplay_end checkpoint reads zone/act/mode
                // at finalize time with frame == the row count (the
                // engine emits nothing for the other profiles), then any
                // buffered stream flushes and the final metadata bytes
                // are written.
                foreach (string line in
                    auxEngine.EmitFinalization(traceFrame, host))
                {
                    auxSink.AppendLine(line);
                }
                physicsSink.Flush();
                auxSink.Flush();
                hardwareTimingSink.Flush();
                metadataJson.Write(S3KTraceMetadataWriter.Format(
                    startZoneId,
                    startAct,
                    offset,
                    traceFrame,
                    startX,
                    startY,
                    startRngSeed,
                    traceProfile,
                    recordingDate,
                    HardwareTimingEventEngine.CurrentSchema,
                    loadQueueState));
                return new S3KTraceCaptureResult(offset, traceFrame);
            }
        }
    }
}
