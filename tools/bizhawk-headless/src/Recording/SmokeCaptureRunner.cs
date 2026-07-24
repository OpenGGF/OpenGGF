using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    public static class SmokeCaptureRunner
    {
        public static void Capture(
            Bk2Movie movie,
            IGpgxHost host,
            int bk2FrameOffset,
            int maxFrames,
            TextWriter output)
        {
            if (movie == null)
            {
                throw new ArgumentNullException("movie");
            }
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            if (output == null)
            {
                throw new ArgumentNullException("output");
            }
            if (bk2FrameOffset < 0)
            {
                throw new ArgumentOutOfRangeException(
                    "bk2FrameOffset",
                    "BK2 frame offset cannot be negative.");
            }
            if (maxFrames < 1 || maxFrames > 1000)
            {
                throw new ArgumentOutOfRangeException(
                    "maxFrames",
                    "Smoke capture frame count must be between 1 and 1000.");
            }

            long requiredFrames = (long)bk2FrameOffset + maxFrames;
            if (requiredFrames > movie.FrameCount)
            {
                throw new InvalidDataException(
                    "Movie contains only " + movie.FrameCount
                    + " frames; capture requires " + requiredFrames + ".");
            }

            using (IEnumerator<Bk2Frame> frames =
                movie.OpenFrameStream().GetEnumerator())
            {
                for (int warmUpFrame = 0;
                    warmUpFrame < bk2FrameOffset;
                    warmUpFrame++)
                {
                    Bk2Frame frame = NextFrame(frames, requiredFrames);
                    ApplyFrame(frame, host);
                    host.Advance();
                }

                output.Write(S1SmokeRecorder.Header);
                output.Write('\n');
                for (int traceFrame = 0;
                    traceFrame < maxFrames;
                    traceFrame++)
                {
                    Bk2Frame frame = NextFrame(frames, requiredFrames);
                    ApplyFrame(frame, host);
                    host.Advance();

                    int expectedCompletedFrame =
                        bk2FrameOffset + traceFrame + 1;
                    if (host.CompletedFrame != expectedCompletedFrame)
                    {
                        throw new InvalidOperationException(
                            "GPGX CompletedFrame is " + host.CompletedFrame
                            + "; expected " + expectedCompletedFrame + ".");
                    }

                    output.Write(S1SmokeRecorder.Record(
                        traceFrame,
                        frame.OpenGgfInputMask,
                        host));
                    output.Write('\n');
                }
            }
        }

        private static Bk2Frame NextFrame(
            IEnumerator<Bk2Frame> frames,
            long requiredFrames)
        {
            if (!frames.MoveNext())
            {
                throw new InvalidDataException(
                    "Movie input stream ended before the required "
                    + requiredFrames + " frames.");
            }
            return frames.Current;
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
