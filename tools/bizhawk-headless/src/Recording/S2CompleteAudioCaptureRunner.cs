using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    internal interface IS2CompleteAudioCaptureSink
    {
        void Begin(CompleteRunAudioObserver.CutoffFrontier boundary);
        void Frame(int row, CompleteRunAudioObserver.FrameCapture frame);
        void Complete(CompleteRunAudioObserver.CutoffFrontier cutoff);
    }

    /// <summary>
    /// Bounded Sonic 2 complete-audio capture. Native observation starts at
    /// power-on, while publication starts at the reviewed comparison boundary
    /// without resetting chip latches or the native service lifecycle.
    /// </summary>
    internal static class S2CompleteAudioCaptureRunner
    {
        internal sealed class CaptureResult
        {
            internal CaptureResult(int observedRows, int publishedRows)
            {
                ObservedRows = observedRows;
                PublishedRows = publishedRows;
            }

            internal int ObservedRows { get; private set; }
            internal int PublishedRows { get; private set; }
        }

        internal static CaptureResult CapturePinned(
            string romPath, string moviePath, string manifestPath,
            string capabilityPath, IS2CompleteAudioCaptureSink sink)
        {
            return CapturePinnedCore(romPath, moviePath, manifestPath,
                capabilityPath, sink, S2AudioObserverProfile.ExclusiveEnd,
                true);
        }

        internal static CaptureResult CaptureRawPinned(
            string romPath, string moviePath, string manifestPath,
            string capabilityPath, string outputPath)
        {
            CaptureResult result = null;
            PublishRaw(outputPath, output =>
            {
                result = CaptureRawPinnedCore(romPath, moviePath, manifestPath,
                    capabilityPath, output, S2AudioObserverProfile.ExclusiveEnd,
                    true);
            });
            return result;
        }

        internal static CaptureResult CaptureRawBoundaryProofPinnedForTesting(
            string romPath, string moviePath, string manifestPath,
            string capabilityPath, TextWriter output)
        {
            return CaptureRawPinnedCore(romPath, moviePath, manifestPath,
                capabilityPath, output, S2AudioObserverProfile.FirstRow + 1,
                false);
        }

        internal static void PublishRawForTesting(
            string outputPath, Action<TextWriter> capture)
        {
            PublishRaw(outputPath, capture);
        }

        private static void PublishRaw(
            string outputPath, Action<TextWriter> capture)
        {
            if (string.IsNullOrEmpty(outputPath) || !Path.IsPathRooted(outputPath))
                throw new ArgumentException(
                    "The S2 raw staging output path must be absolute.",
                    "outputPath");
            if (capture == null) throw new ArgumentNullException("capture");
            string fullPath = Path.GetFullPath(outputPath);
            string fileName = Path.GetFileName(fullPath);
            if (string.IsNullOrEmpty(fileName))
                throw new ArgumentException(
                    "The S2 raw staging output path must name a file.",
                    "outputPath");
            string directory = Path.GetDirectoryName(fullPath);
            var publisher = new NoReplacePublisher();
            using (NoReplacePublisher.StagedPublicationSet staged =
                publisher.StageAll(directory, new[] { fileName },
                    writers => capture(writers[0])))
            {
                staged.Publish();
            }
        }

        internal static CaptureResult CaptureBoundaryProofPinned(
            string romPath, string moviePath, string manifestPath,
            string capabilityPath, IS2CompleteAudioCaptureSink sink)
        {
            return CapturePinnedCore(romPath, moviePath, manifestPath,
                capabilityPath, sink, S2AudioObserverProfile.FirstRow + 1,
                false);
        }

        private static CaptureResult CapturePinnedCore(
            string romPath, string moviePath, string manifestPath,
            string capabilityPath, IS2CompleteAudioCaptureSink sink,
            int exclusiveEnd, bool requireExactEnd)
        {
            if (sink == null) throw new ArgumentNullException("sink");
            S2AudioObserverProfile.ValidateRom(romPath);
            Bk2Movie movie = S2AudioObserverProfile.OpenMovie(moviePath);
            S2AudioObserverProfile.LoadCapability(manifestPath, capabilityPath);
            S2AudioObserverProfile.VerifyInstallation(
                Environment.GetEnvironmentVariable("BIZHAWK_HOME"));
            using (GpgxHost host = GpgxHost.Open(romPath, movie.SyncSettings))
            {
                CompleteRunAudioObserver observer =
                    S2AudioObserverProfile.CreateObserver(
                        manifestPath, capabilityPath,
                        host.CreateAudioTraceApi());
                return CaptureCore(movie.OpenFrameStream(), host, observer,
                    sink, S2AudioObserverProfile.FirstRow, exclusiveEnd,
                    requireExactEnd);
            }
        }

        private static CaptureResult CaptureRawPinnedCore(
            string romPath, string moviePath, string manifestPath,
            string capabilityPath, TextWriter output, int exclusiveEnd,
            bool requireExactEnd)
        {
            if (output == null) throw new ArgumentNullException("output");
            S2AudioObserverProfile.ValidateRom(romPath);
            Bk2Movie movie = S2AudioObserverProfile.OpenMovie(moviePath);
            S2AudioObserverProfile.LoadCapability(manifestPath, capabilityPath);
            S2AudioObserverProfile.VerifyInstallation(
                Environment.GetEnvironmentVariable("BIZHAWK_HOME"));
            using (GpgxHost host = GpgxHost.Open(romPath, movie.SyncSettings))
            {
                CompleteRunAudioObserver observer =
                    S2AudioObserverProfile.CreateObserver(
                        manifestPath, capabilityPath,
                        host.CreateAudioTraceApi());
                var sink = new S2CompleteAudioRawSink(
                    new GpgxS2CompleteAudioStateSource(host), output);
                return CaptureCore(movie.OpenFrameStream(), host, observer,
                    sink, S2AudioObserverProfile.FirstRow, exclusiveEnd,
                    requireExactEnd);
            }
        }

        /// <summary>Deterministic synthetic seam; production uses CapturePinned.</summary>
        internal static CaptureResult CaptureIntervalForTesting(
            IEnumerable<Bk2Frame> frames, IGpgxHost host,
            CompleteRunAudioObserver observer,
            IS2CompleteAudioCaptureSink sink,
            int firstRow, int exclusiveEnd)
        {
            return CaptureCore(frames, host, observer, sink,
                firstRow, exclusiveEnd, true);
        }

        /// <summary>Deterministic synthetic seam; production uses CapturePinned.</summary>
        internal static CaptureResult CapturePrefixForTesting(
            IEnumerable<Bk2Frame> frames, IGpgxHost host,
            CompleteRunAudioObserver observer,
            IS2CompleteAudioCaptureSink sink,
            int firstRow, int exclusiveEnd)
        {
            return CaptureCore(frames, host, observer, sink,
                firstRow, exclusiveEnd, false);
        }

        private static CaptureResult CaptureCore(
            IEnumerable<Bk2Frame> frames, IGpgxHost host,
            CompleteRunAudioObserver observer,
            IS2CompleteAudioCaptureSink sink,
            int firstRow, int exclusiveEnd, bool requireExactEnd)
        {
            if (frames == null) throw new ArgumentNullException("frames");
            if (host == null) throw new ArgumentNullException("host");
            if (observer == null) throw new ArgumentNullException("observer");
            if (sink == null) throw new ArgumentNullException("sink");
            if (firstRow < 0 || exclusiveEnd <= firstRow)
                throw new ArgumentOutOfRangeException("firstRow");

            int published = 0;
            using (IEnumerator<Bk2Frame> rows = frames.GetEnumerator())
            {
                for (int row = 0; row < exclusiveEnd; row++)
                {
                    if (!rows.MoveNext())
                        throw new InvalidDataException(
                            "The S2 complete audio movie ended before row "
                            + exclusiveEnd + ".");
                    if (row == firstRow)
                        sink.Begin(observer.CaptureBoundaryFrontierAndResetPublication());
                    S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                    CompleteRunAudioObserver.FrameCapture capture =
                        observer.CaptureCanonicalFrame(host.Advance);
                    if (row >= firstRow)
                    {
                        sink.Frame(row, capture);
                        published++;
                    }
                }
                if (requireExactEnd && rows.MoveNext())
                    throw new InvalidDataException(
                        "The S2 complete audio movie contains more rows than declared.");
            }
            sink.Complete(observer.CaptureCutoffFrontier());
            return new CaptureResult(exclusiveEnd, published);
        }
    }
}
