using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Native capture path for the standalone S2 special-stage recorder.
    /// </summary>
    public static class S2SpecialStageCaptureRunner
    {
        public const string TraceProfile = "s2_special_stage";
        private const int GameModeAddress = 0xF600;
        private const int SpecialStageGameMode = 0x10;
        private const int StartTimeoutFrames = 5000;

        public static S2SpecialStageCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
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
                    "Canonical special-stage publication requires native load audit.");
            }
            return CaptureCore(
                movie, host, sourceBk2, recordingDate, physicsCsv,
                auxStateJsonl, metadataJson, requiredDynamicArtRom);
        }

        /// <summary>
        /// Scratch-only compatibility capture. Output from this path lacks
        /// mandatory DPLC audit and must never reach publication.
        /// </summary>
        public static S2SpecialStageCaptureResult CaptureScratchLegacy(
            Bk2Movie movie,
            IGpgxHost host,
            string sourceBk2,
            string recordingDate,
            TextWriter physicsCsv,
            TextWriter auxStateJsonl,
            TextWriter metadataJson)
        {
            return CaptureCore(
                movie, host, sourceBk2, recordingDate, physicsCsv,
                auxStateJsonl, metadataJson, null);
        }

        private static S2SpecialStageCaptureResult CaptureCore(
            Bk2Movie movie,
            IGpgxHost host,
            string sourceBk2,
            string recordingDate,
            TextWriter physicsCsv,
            TextWriter auxStateJsonl,
            TextWriter metadataJson,
            byte[] dynamicArtRom)
        {
            if (movie == null) throw new ArgumentNullException("movie");
            if (host == null) throw new ArgumentNullException("host");
            if (sourceBk2 == null) throw new ArgumentNullException("sourceBk2");
            if (recordingDate == null)
                throw new ArgumentNullException("recordingDate");
            if (physicsCsv == null) throw new ArgumentNullException("physicsCsv");
            if (auxStateJsonl == null)
                throw new ArgumentNullException("auxStateJsonl");
            if (metadataJson == null)
                throw new ArgumentNullException("metadataJson");

            bool started = false;
            int offset = 0;
            int traceFrame = 0;
            S2SpecialStageAuxEventEngine aux = null;
            S2SpecialStageRunObjectsObserver runObjects = null;
            int dynamicArtLogicalFrame = 0;
            S2DynamicArtObserver dynamicArt = dynamicArtRom == null
                ? null
                : new S2DynamicArtObserver(
                    dynamicArtRom, host, () => dynamicArtLogicalFrame);
            DynamicArtCaptureRowBuffer rowBuffer = null;
            try
            {
                using (IEnumerator<Bk2Frame> frames =
                    movie.OpenFrameStream().GetEnumerator())
                using (IEnumerator<Bk2Frame> inputRows =
                    movie.OpenFrameStream().GetEnumerator())
                {
                    while (frames.MoveNext())
                    {
                        if (!inputRows.MoveNext())
                        {
                            if (started)
                            {
                                break;
                            }
                            throw new InvalidOperationException(
                                "Input row stream ended before emulator row"
                                + " stream.");
                        }
                        Bk2Frame frame = frames.Current;
                        dynamicArtLogicalFrame = started
                            ? traceFrame
                            : host.CompletedFrame;
                        S1TraceCaptureRunner.ApplyFrame(frame, host);
                        host.Advance();
                        int gameMode = S2Ram.U8(host, GameModeAddress);
                        if (!started)
                        {
                            if (gameMode != SpecialStageGameMode)
                            {
                                if (host.CompletedFrame >= StartTimeoutFrames)
                                {
                                    throw new InvalidOperationException(
                                        "Game_Mode never reached 0x10 within "
                                        + StartTimeoutFrames + " frames.");
                                }
                                continue;
                            }
                            started = true;
                            offset = host.CompletedFrame;
                            WriteLine(
                                physicsCsv,
                                S2SpecialStageCsvWriter.Header);
                            aux = new S2SpecialStageAuxEventEngine(host);
                            runObjects =
                                new S2SpecialStageRunObjectsObserver(
                                    host, offset, () => traceFrame);
                            WriteLine(
                                auxStateJsonl,
                                aux.FormatPretraceSnapshot(host));
                            if (dynamicArt != null)
                            {
                                dynamicArt.PublishGap();
                                dynamicArt.ArmSegment();
                                rowBuffer = new DynamicArtCaptureRowBuffer(
                                    physicsCsv, auxStateJsonl, "\r\n");
                            }
                            // Entry frame: no row (mirrors the run path's
                            // `continue` at S2RunCaptureRunner Block 1).
                            // `offset` is the emulator frame on which
                            // Game_Mode first read $10, and the observer
                            // computes input_sample_frame =
                            // host.CompletedFrame - offset from INSIDE
                            // host.Advance(), where CompletedFrame is still
                            // the PRE-increment count. So the V-int that
                            // polls the pad during the Advance producing
                            // emulator frame offset+1+k reports
                            // input_sample_frame == k, and row k must
                            // therefore be the state sampled AFTER that same
                            // Advance -- i.e. emulator frame offset+1+k, one
                            // later than the entry frame. Writing a row here
                            // as well made row k the state at offset+k, so
                            // every input_sample_frame pointed at the row
                            // before the frame that actually polled the pad
                            // (SpecialStage_MainLoop's WaitForVint ->
                            // ReadJoypads, s2.asm:6674-6691). Dropping the
                            // extra inputRows.MoveNext() with the row keeps
                            // the two enumerators aligned, so row k still
                            // carries BK2 input index offset+k -- the input
                            // that produced it.
                            continue;
                        }
                        if (gameMode != SpecialStageGameMode
                            || offset + traceFrame >= movie.FrameCount)
                        {
                            break;
                        }
                        string physicsLine =
                            S2SpecialStageCsvWriter.FormatRow(
                                traceFrame,
                                S2SpecialStageCsvWriter.InputMask(
                                    inputRows.Current),
                                0,
                                host.IsLagged,
                                host);
                        var auxLines = new List<string>();
                        foreach (string line in runObjects.PublishForRow(
                            traceFrame, host.IsLagged))
                        {
                            auxLines.Add(line);
                        }
                        foreach (string line in aux.EmitRowEvents(
                            traceFrame, host.IsLagged, host))
                        {
                            auxLines.Add(line);
                            if (line.IndexOf(
                                "\"type\":\"checkpoint\"",
                                StringComparison.Ordinal) >= 0)
                            {
                                foreach (string terminal
                                    in runObjects.PublishTerminal(traceFrame))
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
                                dynamicArt.PublishRow(
                                    traceFrame, host.IsLagged));
                        }
                        else
                        {
                            WriteLine(physicsCsv, physicsLine);
                            for (int index = 0; index < auxLines.Count; index++)
                            {
                                WriteLine(auxStateJsonl, auxLines[index]);
                            }
                        }
                        traceFrame++;
                    }
                }
                if (dynamicArt != null)
                {
                    if (traceFrame > 0)
                    {
                        rowBuffer.FlushTerminal(
                            dynamicArt.PublishTerminal(traceFrame - 1));
                    }
                    dynamicArt.EndSegment();
                }
            }
            finally
            {
                if (runObjects != null)
                {
                    runObjects.Dispose();
                }
                if (dynamicArt != null)
                {
                    dynamicArt.Dispose();
                }
            }
            if (!started)
            {
                throw new InvalidOperationException(
                    "Movie ended before Game_Mode reached 0x10.");
            }
            metadataJson.Write(
                S2SpecialStageMetadataWriter.FormatStandalone(
                    offset, traceFrame, sourceBk2, recordingDate,
                    dynamicArtRom != null)
                .Replace("\n", "\r\n"));
            return new S2SpecialStageCaptureResult(offset, traceFrame);
        }

        private static void WriteLine(TextWriter writer, string value)
        {
            writer.Write(value);
            writer.Write("\r\n");
        }
    }

    public sealed class S2SpecialStageCaptureResult
    {
        public S2SpecialStageCaptureResult(
            int bk2FrameOffset,
            int traceFrameCount)
        {
            Bk2FrameOffset = bk2FrameOffset;
            TraceFrameCount = traceFrameCount;
        }

        public int Bk2FrameOffset { get; private set; }
        public int TraceFrameCount { get; private set; }
    }
}
