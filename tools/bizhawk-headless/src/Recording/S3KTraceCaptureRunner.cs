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
    /// (tools/bizhawk/s3k_trace_recorder.lua v6.30-s3k; spec
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
    /// Output is buffered in memory so the discard can throw a partial
    /// recording away without filesystem interaction. On success the
    /// buffered physics.csv / aux_state.jsonl bytes and the finalize-time
    /// metadata.json are written to the injected writers; every output
    /// line (including the last) is terminated with a single LF and the
    /// injected writers own the encoding (production uses UTF-8 without
    /// BOM).
    /// </summary>
    public static class S3KTraceCaptureRunner
    {
        public const string GameplayUnlockProfile = "gameplay_unlock";
        public const string AizEndToEndProfile = "aiz_end_to_end";
        public const string LevelGatedResetAwareProfile =
            "level_gated_reset_aware";

        public static S3KTraceCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string traceProfile,
            string recordingDate,
            TextWriter physicsCsv,
            TextWriter auxStateJsonl,
            TextWriter metadataJson)
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

            var physicsBuf = new StringBuilder();
            var auxBuf = new StringBuilder();

            bool started = false;
            bool preTraceSnapshotsWritten = false;
            int offset = 0;
            int traceFrame = 0;
            int startX = 0;
            int startY = 0;
            int startZoneId = 0;
            int startAct = 0;
            uint startRngSeed = 0;
            S3KAuxEventEngine auxEngine = null;

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
                        physicsBuf.Length = 0;
                        auxBuf.Length = 0;
                        auxEngine = null;
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
                        physicsBuf
                            .Append(S3KTraceCsvWriter.Header)
                            .Append('\n');
                        auxEngine = new S3KAuxEventEngine(profile);
                        if (!aiz)
                        {
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
                            auxBuf.Append(line).Append('\n');
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
                    physicsBuf.Append(S3KTraceCsvWriter.FormatRow(
                        traceFrame,
                        S1InputMask.FromFrame(inputRow),
                        host));
                    physicsBuf.Append('\n');

                    // (8) Aux cascade (step 13) in the engine's fixed
                    // order, then the frame counter (step 14).
                    foreach (string line in
                        auxEngine.ProcessFrame(traceFrame, host))
                    {
                        auxBuf.Append(line).Append('\n');
                    }
                    traceFrame++;
                }

                // Finalisation (Lua main loop after finished): the
                // level-gated gameplay_end checkpoint reads zone/act/mode
                // at finalize time with frame == the row count (the
                // engine emits nothing for the other profiles), then the
                // buffered files flush and the final metadata bytes are
                // written.
                foreach (string line in
                    auxEngine.EmitFinalization(traceFrame, host))
                {
                    auxBuf.Append(line).Append('\n');
                }
                physicsCsv.Write(physicsBuf.ToString());
                auxStateJsonl.Write(auxBuf.ToString());
                metadataJson.Write(S3KTraceMetadataWriter.Format(
                    startZoneId,
                    startAct,
                    offset,
                    traceFrame,
                    startX,
                    startY,
                    startRngSeed,
                    traceProfile,
                    recordingDate));
                return new S3KTraceCaptureResult(offset, traceFrame);
            }
        }
    }
}
