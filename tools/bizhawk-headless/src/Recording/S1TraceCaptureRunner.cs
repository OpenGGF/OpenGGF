using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Result of a full-trace capture: the detected BK2 frame offset and the
    /// number of trace rows recorded.
    /// </summary>
    public sealed class S1TraceCaptureResult
    {
        public S1TraceCaptureResult(int bk2FrameOffset, int traceFrameCount)
        {
            Bk2FrameOffset = bk2FrameOffset;
            TraceFrameCount = traceFrameCount;
        }

        public int Bk2FrameOffset { get; private set; }
        public int TraceFrameCount { get; private set; }
    }

    /// <summary>
    /// Full-trace capture runner: the native port of the Lua trace recorder's
    /// frame loop (tools/bizhawk-headless/docs/s1-trace-recorder-behavior.md
    /// sections 2 and 6). Auto-detects the recording start (game_mode 0x0C
    /// with control lock 0), then emits one physics.csv row plus aux events
    /// per trace frame until the level is left or the movie runs out. Every
    /// output line (including the last) is terminated with a single LF; the
    /// injected writers own the encoding (production uses UTF-8 without BOM).
    /// </summary>
    public static class S1TraceCaptureRunner
    {
        private const byte LevelGameMode = 0x0C;

        public static S1TraceCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
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

            using (IEnumerator<Bk2Frame> frames =
                movie.OpenFrameStream().GetEnumerator())
            {
                // Detection phase: apply BK2 rows in order; after each
                // advance, when game_mode == 0x0C and the control lock timer
                // is 0, the number of completed frames is the BK2 frame
                // offset. The detection frame itself is NOT recorded.
                int offset = -1;
                int rowsConsumed = 0;
                int startX = 0;
                int startY = 0;
                int zoneId = 0;
                int actRaw = 0;
                uint rngSeed = 0;
                while (offset < 0)
                {
                    if (!frames.MoveNext())
                    {
                        throw new InvalidDataException(
                            "Start detection never fired: the movie ended"
                            + " after " + rowsConsumed + " input rows without"
                            + " reaching game_mode 0x0C with control lock 0.");
                    }
                    ApplyFrame(frames.Current, host);
                    host.Advance();
                    rowsConsumed++;
                    if (host.CompletedFrame != rowsConsumed)
                    {
                        throw new InvalidOperationException(
                            "GPGX CompletedFrame is " + host.CompletedFrame
                            + "; expected " + rowsConsumed + ".");
                    }

                    byte gameMode = S1Ram.U8(host, S1Ram.GameMode);
                    int ctrlLock = S1Ram.U16(
                        host, S1Ram.PlayerBase + S1Ram.OffCtrlLock);
                    if (gameMode == LevelGameMode && ctrlLock == 0)
                    {
                        offset = host.CompletedFrame;
                        startX = S1Ram.U16(
                            host, S1Ram.PlayerBase + S1Ram.OffXPos);
                        startY = S1Ram.U16(
                            host, S1Ram.PlayerBase + S1Ram.OffYPos);
                        rngSeed = S1Ram.U32(host, S1Ram.Random);
                        zoneId = S1Ram.U8(host, S1Ram.Zone);
                        actRaw = S1Ram.U8(host, S1Ram.Act);
                    }
                }

                physicsCsv.Write(S1TraceCsvWriter.Header);
                physicsCsv.Write('\n');

                // Recording phase: trace row N = state after applying BK2
                // input row (offset + N) and advancing one frame.
                var auxEngine = new S1AuxEventEngine();
                int traceFrame = 0;
                while (true)
                {
                    // Lua stop parity for movie end: BizHawk 2.11 flags the
                    // movie FINISHED on the frame that consumes the last
                    // input row (MovieSession.HandleFrameAfter fires between
                    // FrameAdvance and the Lua resume), so the Lua recorder's
                    // movie.mode() == "FINISHED" check finalizes WITHOUT
                    // recording that row — one iteration before its row-count
                    // guard would fire. Fold both Lua checks into a single
                    // pre-advance predicate: the last recordable trace row is
                    // the one fed by input row (FrameCount - 2).
                    if ((long)offset + traceFrame + 1 >= movie.FrameCount)
                    {
                        break;      // Movie end; row N is not recorded.
                    }
                    if (!frames.MoveNext())
                    {
                        throw new InvalidDataException(
                            "Movie input stream ended before row "
                            + (offset + traceFrame) + " of "
                            + movie.FrameCount + ".");
                    }
                    Bk2Frame frame = frames.Current;
                    ApplyFrame(frame, host);
                    host.Advance();
                    int expectedCompletedFrame = offset + traceFrame + 1;
                    if (host.CompletedFrame != expectedCompletedFrame)
                    {
                        throw new InvalidOperationException(
                            "GPGX CompletedFrame is " + host.CompletedFrame
                            + "; expected " + expectedCompletedFrame + ".");
                    }

                    if (S1Ram.U8(host, S1Ram.GameMode) != LevelGameMode)
                    {
                        break;      // Left level gameplay; no partial row.
                    }

                    physicsCsv.Write(S1TraceCsvWriter.FormatRow(
                        traceFrame,
                        S1InputMask.FromFrame(frame),
                        host));
                    physicsCsv.Write('\n');
                    foreach (string line in
                        auxEngine.ProcessFrame(traceFrame, host))
                    {
                        auxStateJsonl.Write(line);
                        auxStateJsonl.Write('\n');
                    }
                    traceFrame++;
                }

                metadataJson.Write(S1TraceMetadataWriter.Format(
                    zoneId,
                    actRaw,
                    offset,
                    traceFrame,
                    startX,
                    startY,
                    rngSeed,
                    recordingDate));
                return new S1TraceCaptureResult(offset, traceFrame);
            }
        }

        private static void ApplyFrame(Bk2Frame frame, IGpgxHost host)
        {
            host.ClearButtons();
            SetIfPressed(host, "Power", frame.Power);
            SetIfPressed(host, "Reset", frame.Reset);
            SetIfPressed(host, "P1 Up", frame.P1Up);
            SetIfPressed(host, "P1 Down", frame.P1Down);
            SetIfPressed(host, "P1 Left", frame.P1Left);
            SetIfPressed(host, "P1 Right", frame.P1Right);
            SetIfPressed(host, "P1 A", frame.P1A);
            SetIfPressed(host, "P1 B", frame.P1B);
            SetIfPressed(host, "P1 C", frame.P1C);
            SetIfPressed(host, "P1 Start", frame.P1Start);
        }

        private static void SetIfPressed(
            IGpgxHost host,
            string name,
            bool pressed)
        {
            if (pressed)
            {
                host.SetButton(name, true);
            }
        }
    }
}
