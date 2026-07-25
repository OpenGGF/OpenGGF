using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// The two output streams of one armed segment, handed to the runner by
    /// its sink at every arm. A special-stage segment gets an
    /// <see cref="AuxStateJsonl"/> writer too even though nothing is ever
    /// written to it: the Lua opens the file on the SS path and never
    /// writes a line, so every SS fixture ships a 0-byte aux_state.jsonl
    /// and the port must PUBLISH the empty file rather than omit it
    /// (spec s3k-run-publication.md §2.2).
    /// </summary>
    public sealed class S3KSegmentStreams
    {
        public S3KSegmentStreams(
            TextWriter physicsCsv, TextWriter auxStateJsonl)
        {
            if (physicsCsv == null)
            {
                throw new ArgumentNullException("physicsCsv");
            }
            if (auxStateJsonl == null)
            {
                throw new ArgumentNullException("auxStateJsonl");
            }
            PhysicsCsv = physicsCsv;
            AuxStateJsonl = auxStateJsonl;
        }

        public TextWriter PhysicsCsv { get; private set; }
        public TextWriter AuxStateJsonl { get; private set; }
    }

    /// <summary>
    /// Where a complete-run capture's segments go. The runner never buffers
    /// a segment: it asks the sink for writers at the arm, streams rows
    /// into them frame by frame, and hands back the finished manifest entry
    /// plus the final metadata.json bytes at the finalize. That is what
    /// keeps peak memory at one OS write buffer rather than at the 266 MB
    /// of a single lbz_completerun aux stream (spec §9 invariant 12).
    /// </summary>
    public interface IS3KCompleteRunSegmentSink
    {
        /// <summary>
        /// Called once per arm, BEFORE the segment's CSV header is written.
        /// </summary>
        S3KSegmentStreams BeginSegment(S3KSegmentArm arm);

        /// <summary>
        /// Called once per finalize, after the last row. metadata.json is
        /// materialised exactly once, here — the Lua rewrites it at arm,
        /// every 300 rows and at finalize, and only the finalize write
        /// survives (spec §2.3).
        /// </summary>
        void EndSegment(RunManifestSegment entry, string metadataJson);
    }

    /// <summary>
    /// Result of a complete-run capture: the finished manifest entries in
    /// recording order, the recorded transitions, and the formatted
    /// run_manifest.json bytes — null when the Lua's emission gate says no
    /// manifest (no transition AND no run id), which is exactly why
    /// identity (A)'s seven published dirs have none.
    /// </summary>
    public sealed class S3KCompleteRunCaptureResult
    {
        public S3KCompleteRunCaptureResult(
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
    /// Native port of the S3K complete-run recorder's DRIVER
    /// (tools/bizhawk/s3k_complete_run_recorder.lua v6.32-s3k-completerun
    /// main loop L5850-5900; spec
    /// tools/bizhawk-headless/docs/s3k-run-publication.md). It owns only
    /// the loop and the wiring; every decision it makes is delegated:
    ///
    /// - segmentation (arm / finalize / stop, dir tokens, transitions) to
    ///   <see cref="S3KCompleteRunSegmenter"/>,
    /// - the 42-column level/bonus row to <see cref="S3KTraceCsvWriter"/>
    ///   with the complete-run ADDR_FRAMECOUNT
    ///   (<see cref="S3KRam.LevelFrameCounter"/>, 0xFE04 — NOT the standard
    ///   recorder's dead-zero 0xFE08),
    /// - the aux cascade to <see cref="S3KAuxEventEngine"/> under
    ///   <see cref="S3KTraceProfile.CompleteRun"/>,
    /// - the 20-column special-stage row to
    ///   <see cref="S3KSpecialStageCsvWriter"/>,
    /// - metadata.json to <see cref="S3KCompleteRunMetadataWriter"/> and
    ///   run_manifest.json to <see cref="S3KRunManifestWriter"/>.
    ///
    /// Loop shape is the Lua's literally (L5850): on_frame_end, then the
    /// FRAME_CAP backstop, then — only if not finished — the advance. The
    /// first on_frame_end therefore runs at CompletedFrame 0, BEFORE any
    /// advance; it is a no-op for every real capture because nothing is
    /// armed and the arm gate needs Game_mode 0x0C. Reordering any of this
    /// is the bug class both the S1 and S2 ports independently hit.
    ///
    /// Frame alignment (spec §5.6 of the segmentation spec): the `input`
    /// column of row N is BK2 input ROW <c>bk2_frame_offset + N</c>, full
    /// stop. That is what the Lua indexes —
    /// <c>bk2_input_mask(raw_input, trace_frame)</c> (L5573) forwards to
    /// the shared helper with the recorder's own <c>bk2_frame_offset</c>
    /// upvalue (L988) and calls
    /// <c>movie.getinput(bk2_frame_offset + trace_row, 1)</c>
    /// (lib/oggf_trace_common.lua L67-79); <c>ss_input_mask</c> (L5079)
    /// does the same for both players. The index is the ROW index, never
    /// the emulator frame.
    ///
    /// In a frame-CONTIGUOUS segment row N happens to be written while
    /// observing framecount bk2_frame_offset + 1 + N, so the row consumed
    /// by the immediately preceding advance is also
    /// bk2_frame_offset + N — and every canonical fixture segment is
    /// contiguous, so the two agree there. They diverge the moment an
    /// armed segment SKIPS a frame, which segmenter step (10) exists to
    /// allow: a non-level-family Game_mode (0x00/0x04/0x08 — game-over /
    /// continue, a pause+A soft reset back to the title, an
    /// ending/credits excursion) suppresses the row WITHOUT closing the
    /// segment, and the one-time-per-zone arm gate re-enters the SAME
    /// open segment afterwards. From there on "the last applied row" is
    /// ahead of "row bk2_frame_offset + N" by the excursion length, for
    /// every remaining row of that segment. So the runner indexes the BK2
    /// stream by row, keeping every applied row in <c>appliedRows</c>
    /// (index i == BK2 row i); <c>lastApplied</c> now only feeds the
    /// emulator. The buffer is bounded by the movie length — ~466k rows
    /// for the complete S3K movie, tens of MB, negligible next to the
    /// per-segment stream buffers <see cref="IS3KCompleteRunSegmentSink"/>
    /// exists to avoid.
    ///
    /// There is no finalization aux event.
    /// <see cref="S3KAuxEventEngine.EmitFinalization"/> emits the
    /// gameplay_end checkpoint for the level_gated_reset_aware profile
    /// only, and the complete-run recorder's TRACE_PROFILE is a hard-pinned
    /// "complete_run"; the Lua's finalize path likewise writes metadata and
    /// closes files without touching aux. Calling it would be a no-op, so
    /// it is not called.
    ///
    /// Every output line, including the last, is terminated with a single
    /// LF. S3K publishes LF in BOTH plain and run mode — do not apply the
    /// S1/S2 run-mode CRLF expansion on any S3K path (spec §6).
    /// </summary>
    public static class S3KCompleteRunCaptureRunner
    {
        /// <summary>
        /// Captures one full movie pass.
        /// <paramref name="runId"/> is null when --run-id was not supplied
        /// (identity (A)); it drives the run_id metadata line, the manifest
        /// run_id line, and the manifest emission gate.
        /// <paramref name="effectiveMovieLength"/> models the capture
        /// session's movie-length signal, 0 meaning "the movie's own frame
        /// count" (the normal case); it feeds the segmenter's unconditional
        /// movie-input-end guard, and is the modeled equivalent of the
        /// REFUSED OGGF_BK2_FRAME_COUNT env var, exactly as in the S1/S2
        /// run ports.
        /// </summary>
        public static S3KCompleteRunCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            string sourceBk2,
            string recordingDate,
            int effectiveMovieLength,
            IS3KCompleteRunSegmentSink sink)
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
            if (sink == null)
            {
                throw new ArgumentNullException("sink");
            }
            if (effectiveMovieLength < 0)
            {
                throw new ArgumentOutOfRangeException(
                    "effectiveMovieLength",
                    "effectiveMovieLength must be >= 0 (0 = movie length).");
            }

            int movieLength = effectiveMovieLength == 0
                ? movie.FrameCount
                : effectiveMovieLength;
            var state = new CaptureState(
                host, sink, runId, sourceBk2, recordingDate);
            var segmenter = new S3KCompleteRunSegmenter(
                true, movieLength, 0, 0);
            segmenter.SegmentOpened = state.OpenSegment;
            segmenter.SegmentClosed = state.CloseSegment;

            using (IEnumerator<Bk2Frame> frames =
                movie.OpenFrameStream().GetEnumerator())
            {
                Bk2Frame next = frames.MoveNext() ? frames.Current : null;
                Bk2Frame lastApplied = null;
                // BK2 row i lives at appliedRows[i]. Rows are applied in
                // order from row 0, so the list index IS the BK2 frame
                // index, which is what the Lua's movie.getinput() takes.
                var appliedRows = new List<Bk2Frame>();
                var advanced = 0;
                while (true)
                {
                    S3KCompleteRunFrameAction action = segmenter.Step(host);
                    if (action == S3KCompleteRunFrameAction.LevelRow)
                    {
                        state.WriteLevelRow(
                            segmenter.RowIndex,
                            segmenter.EmittedPreTraceSnapshots,
                            InputRow(appliedRows, segmenter));
                    }
                    else if (action
                        == S3KCompleteRunFrameAction.SpecialStageRow)
                    {
                        state.WriteSpecialStageRow(
                            segmenter.RowIndex,
                            InputRow(appliedRows, segmenter));
                    }

                    // The Lua's runaway backstop, evaluated by the main loop
                    // between on_frame_end and the advance — never inside
                    // on_frame_end. It fires in no fixture capture.
                    segmenter.ApplyFrameCap(host.CompletedFrame);
                    if (segmenter.Finished)
                    {
                        break;
                    }

                    if (next == null)
                    {
                        // Unreachable with a loaded movie: the segmenter's
                        // unconditional movie-input-end guard fires at
                        // CompletedFrame == movieLength, which is at most
                        // the number of rows the stream can supply. Loud
                        // rather than silently short.
                        throw new InvalidDataException(
                            "The S3K complete-run recorder ran out of BK2"
                            + " input after " + advanced
                            + " rows without any stop predicate firing.");
                    }
                    lastApplied = next;
                    next = frames.MoveNext() ? frames.Current : null;
                    appliedRows.Add(lastApplied);
                    S1TraceCaptureRunner.ApplyFrame(lastApplied, host);
                    host.Advance();
                    advanced++;
                    if (host.CompletedFrame != advanced)
                    {
                        throw new InvalidOperationException(
                            "GPGX CompletedFrame is " + host.CompletedFrame
                            + "; expected " + advanced + ".");
                    }
                }
            }

            // End-of-run finalize, then — last of all — the manifest, so it
            // can never describe a segment whose files were not written.
            segmenter.FinalizeRunEnd();

            string manifestJson = null;
            if (S3KRunManifestWriter.ShouldEmit(segmenter.Transitions, runId))
            {
                manifestJson = S3KRunManifestWriter.Format(
                    runId,
                    sourceBk2,
                    state.ManifestSegments,
                    segmenter.Transitions);
            }
            return new S3KCompleteRunCaptureResult(
                state.ManifestSegments,
                segmenter.Transitions,
                manifestJson);
        }

        /// <summary>
        /// The BK2 input row the Lua would read for the row the segmenter
        /// just asked for: <c>bk2_frame_offset + trace_row</c>.
        ///
        /// The index can never run past what has been applied. Row N is
        /// written at framecount C; the arm frame F == Bk2FrameOffset is
        /// itself recorded by no segment, so rows occupy frames F+1..C and
        /// N &lt;= C - F - 1, i.e. F + N &lt;= C - 1 == appliedRows.Count - 1.
        /// Equality is the contiguous case; a strict inequality is exactly
        /// the skipped-frame case this indexing exists to get right.
        /// </summary>
        private static Bk2Frame InputRow(
            IList<Bk2Frame> appliedRows, S3KCompleteRunSegmenter segmenter)
        {
            int index = segmenter.Bk2FrameOffset + segmenter.RowIndex;
            if (index < 0 || index >= appliedRows.Count)
            {
                throw new InvalidOperationException(
                    "BK2 input row " + index + " (bk2_frame_offset "
                    + segmenter.Bk2FrameOffset + " + trace row "
                    + segmenter.RowIndex + ") has not been applied yet;"
                    + " only " + appliedRows.Count + " rows have.");
            }
            return appliedRows[index];
        }

        /// <summary>
        /// The per-segment writer wiring: which streams are live, which aux
        /// engine belongs to the armed segment, and the finished manifest
        /// entries.
        /// </summary>
        private sealed class CaptureState
        {
            private readonly IGpgxHost host;
            private readonly IS3KCompleteRunSegmentSink sink;
            private readonly string runId;
            private readonly string sourceBk2;
            private readonly string recordingDate;

            private S3KSegmentArm arm;
            private TextWriter physics;
            private TextWriter aux;

            // Per-segment: a fresh engine at every arm, so each segment
            // re-emits its own pre-trace snapshot and re-latches its own
            // prev_* state. Null for a special-stage segment, which emits
            // no aux events at all.
            private S3KAuxEventEngine auxEngine;

            internal CaptureState(
                IGpgxHost host,
                IS3KCompleteRunSegmentSink sink,
                string runId,
                string sourceBk2,
                string recordingDate)
            {
                this.host = host;
                this.sink = sink;
                this.runId = runId;
                this.sourceBk2 = sourceBk2;
                this.recordingDate = recordingDate;
                ManifestSegments = new List<RunManifestSegment>();
            }

            internal IList<RunManifestSegment> ManifestSegments
            {
                get;
                private set;
            }

            /// <summary>
            /// open_files (L1236) / start_ss_segment's file block (L5154):
            /// both files are created at arm and the CSV header is written
            /// and flushed immediately. The two headers are different
            /// schemas — 42 columns versus 20 — and an SS segment's aux file
            /// stays byte-empty.
            /// </summary>
            internal void OpenSegment(S3KSegmentArm opened)
            {
                arm = opened;
                S3KSegmentStreams streams = sink.BeginSegment(opened);
                physics = streams.PhysicsCsv;
                aux = streams.AuxStateJsonl;
                if (opened.Kind == RunManifestSegment.SpecialStageKind)
                {
                    WriteLine(physics, S3KSpecialStageCsvWriter.Header);
                    auxEngine = null;
                    return;
                }
                WriteLine(physics, S3KTraceCsvWriter.Header);
                auxEngine = new S3KAuxEventEngine(
                    S3KTraceProfile.CompleteRun);
            }

            internal void WriteLevelRow(
                int rowIndex, bool emitPreTraceSnapshots, Bk2Frame inputRow)
            {
                RequireArmed(rowIndex, inputRow);
                if (emitPreTraceSnapshots)
                {
                    foreach (string line in
                        auxEngine.EmitPreTraceSnapshots(host))
                    {
                        WriteLine(aux, line);
                    }
                }
                WriteLine(physics, S3KTraceCsvWriter.FormatRow(
                    rowIndex,
                    S1InputMask.FromFrame(inputRow),
                    host,
                    S3KRam.LevelFrameCounter));
                foreach (string line in auxEngine.ProcessFrame(rowIndex, host))
                {
                    WriteLine(aux, line);
                }
            }

            /// <summary>
            /// write_ss_row (L5174). input_p2 is a hard 0: the Lua reads it
            /// from movie.getinput(..., 2) while the native BK2 reader
            /// models no per-button P2 state (it rejects movies with P2
            /// activity), and every canonical fixture row carries 0. `lag`
            /// is a real per-frame emu.islagged() read — the canonical
            /// special_stage fixture has 113 lag rows out of 4630.
            /// </summary>
            internal void WriteSpecialStageRow(
                int rowIndex, Bk2Frame inputRow)
            {
                RequireArmed(rowIndex, inputRow);
                WriteLine(physics, S3KSpecialStageCsvWriter.FormatRow(
                    rowIndex,
                    S3KSpecialStageCsvWriter.InputMask(inputRow),
                    0,
                    host.IsLagged,
                    host));
            }

            /// <summary>
            /// finalize_segment (L4995) / finalize_ss_segment (L5240): the
            /// metadata write happens BEFORE the segments_done append, which
            /// is why segment_index is this segment's own 0-based index —
            /// already captured into the arm record. Player_mode and the
            /// recording date are FINALIZE-time samples.
            /// </summary>
            internal void CloseSegment(S3KCompleteRunSegment segment)
            {
                bool specialStage =
                    segment.Kind == RunManifestSegment.SpecialStageKind;
                int playerMode = S3KRam.U16(host, S3KRam.PlayerMode);
                string metadata = specialStage
                    ? S3KCompleteRunMetadataWriter.FormatSpecialStage(
                        arm,
                        segment.TraceFrameCount,
                        sourceBk2,
                        recordingDate,
                        runId,
                        playerMode)
                    : S3KCompleteRunMetadataWriter.Format(
                        arm,
                        segment.TraceFrameCount,
                        sourceBk2,
                        recordingDate,
                        runId,
                        playerMode);
                var entry = new RunManifestSegment(
                    segment.Dir,
                    segment.Kind,
                    segment.TraceProfile,
                    segment.Bk2FrameOffset,
                    segment.TraceFrameCount,
                    segment.ZoneId,
                    segment.Act,
                    segment.SpecialStageIndex,
                    segment.BonusStageType);
                ManifestSegments.Add(entry);
                sink.EndSegment(entry, metadata);
                arm = null;
                physics = null;
                aux = null;
                auxEngine = null;
            }

            private void RequireArmed(int rowIndex, Bk2Frame inputRow)
            {
                if (physics == null)
                {
                    throw new InvalidOperationException(
                        "The segmenter asked for row " + rowIndex
                        + " with no segment armed.");
                }
                if (inputRow == null)
                {
                    throw new InvalidOperationException(
                        "Row " + rowIndex + " has no BK2 input row: a row"
                        + " can only be written after at least one"
                        + " advance.");
                }
            }

            private static void WriteLine(TextWriter writer, string line)
            {
                writer.Write(line);
                writer.Write('\n');
            }
        }
    }
}
