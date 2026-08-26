using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S3kCompleteAudioCaptureRunnerTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3kCompleteAudioCaptureRunnerTests drain power-on then publish every row from the boundary",
                DrainsPowerOnThenPublishesEveryRowFromBoundary));
            tests.Add(new TestMain.TestCase(
                "S3kCompleteAudioCaptureRunnerTests reject short and overlong movie streams",
                RejectsShortAndOverlongMovieStreams));
            tests.Add(new TestMain.TestCase(
                "S3kCompleteAudioCaptureRunnerTests stop a prefix without consuming the preserved tail",
                StopsPrefixWithoutConsumingPreservedTail));
            if (Environment.GetEnvironmentVariable(
                "OPENGGF_S3K_COMPLETE_AUDIO_REFERENCE") == "1")
            {
                string rom = Environment.GetEnvironmentVariable("S3K_ROM_PATH");
                string movie = Environment.GetEnvironmentVariable("S3K_BK2_PATH");
                if (File.Exists(rom) && File.Exists(movie))
                {
                    tests.Add(new TestMain.TestCase(
                        "S3kCompleteAudioCaptureRunnerTests prove real power-on to row 810 publication",
                        () => ProvesRealPowerOnToRow810Publication(rom, movie),
                        game: "s3k", serial: true,
                        estimatedSeconds: 20.0));
                }
            }
        }

        private static void DrainsPowerOnThenPublishesEveryRowFromBoundary()
        {
            var api = new S3kAudioObserverProfileTests.RecordingTraceApi();
            CompleteRunAudioObserver observer = S3kAudioObserverProfile.CreateObserver(
                ManifestPath(), api);
            var host = new FakeS1Host(null);
            var sink = new RecordingSink(host);
            var rows = new List<Bk2Frame>
            {
                new Bk2Frame { P1Start = true },
                new Bk2Frame { P1A = true },
                new Bk2Frame { P1B = true },
                new Bk2Frame { P1C = true },
                new Bk2Frame { P1Left = true }
            };

            S3kCompleteAudioCaptureRunner.CaptureResult result =
                S3kCompleteAudioCaptureRunner.CaptureIntervalForTesting(
                    rows, host, observer, sink, 2, 5);

            AssertEx.Equal(5, result.ObservedRows);
            AssertEx.Equal(3, result.PublishedRows);
            AssertEx.Equal(2, sink.BoundaryCompletedFrame);
            AssertEx.Equal(3, sink.Rows.Count);
            AssertEx.Equal(2, sink.Rows[0]);
            AssertEx.Equal(3, sink.Rows[1]);
            AssertEx.Equal(4, sink.Rows[2]);
            AssertEx.Equal(1, sink.BoundaryCalls);
            AssertEx.Equal(1, sink.CutoffCalls);
            AssertEx.Equal(1, api.PublicationCalls);
            AssertEx.Equal(5, host.ClearButtonsCount);
            AssertEx.Equal(true, host.ButtonWrites.Contains("P1 Start=True"));
            AssertEx.Equal(true, host.ButtonWrites.Contains("P1 Left=True"));
        }

        private static void RejectsShortAndOverlongMovieStreams()
        {
            AssertStreamRejected(Frames(4), "ended before");
            AssertStreamRejected(Frames(6), "more rows");
        }

        private static void StopsPrefixWithoutConsumingPreservedTail()
        {
            var api = new S3kAudioObserverProfileTests.RecordingTraceApi();
            CompleteRunAudioObserver observer = S3kAudioObserverProfile.CreateObserver(
                ManifestPath(), api);
            var host = new FakeS1Host(null);
            var sink = new RecordingSink(host);
            S3kCompleteAudioCaptureRunner.CaptureResult result =
                S3kCompleteAudioCaptureRunner.CapturePrefixForTesting(
                    Frames(6), host, observer, sink, 2, 5);
            AssertEx.Equal(5, result.ObservedRows);
            AssertEx.Equal(3, result.PublishedRows);
            AssertEx.Equal(5, host.CompletedFrame);
        }

        private static void ProvesRealPowerOnToRow810Publication(
            string romPath, string moviePath)
        {
            var sink = new RecordingSink(null);
            S3kCompleteAudioCaptureRunner.CaptureResult result =
                S3kCompleteAudioCaptureRunner.CaptureBoundaryProofPinned(
                    romPath, moviePath, ManifestPath(), sink);
            AssertEx.Equal(811, result.ObservedRows);
            AssertEx.Equal(1, result.PublishedRows);
            AssertEx.Equal(true, sink.Boundary.IsArmed);
            AssertEx.Equal(0, sink.Boundary.ActiveServices.Count);
            AssertEx.Equal(0, sink.Boundary.PendingServices.Count);
            AssertEx.Equal((byte)0x28, sink.Boundary.YmPort0Address);
            AssertEx.Equal((byte)0xA1, sink.Boundary.YmPort1Address);
            AssertEx.Equal(1L, sink.Boundary.ArmEpoch);
            AssertEx.Equal(1, sink.Frames.Count);
            AssertEx.Equal(34, sink.Frames[0].RawEvents.Count);
            Console.WriteLine("S3K audio row810 boundary: active="
                + sink.Boundary.ActiveServices.Count + " pending="
                + sink.Boundary.PendingServices.Count + " ym="
                + sink.Boundary.YmPort0Address.ToString("x2") + "/"
                + sink.Boundary.YmPort1Address.ToString("x2")
                + " epoch=" + sink.Boundary.ArmEpoch + " row_events="
                + sink.Frames[0].RawEvents.Count);
        }

        private static void AssertStreamRejected(
            IEnumerable<Bk2Frame> rows, string message)
        {
            var api = new S3kAudioObserverProfileTests.RecordingTraceApi();
            CompleteRunAudioObserver observer = S3kAudioObserverProfile.CreateObserver(
                ManifestPath(), api);
            var host = new FakeS1Host(null);
            AssertEx.Throws<InvalidDataException>(
                () => S3kCompleteAudioCaptureRunner.CaptureIntervalForTesting(
                    rows, host, observer, new RecordingSink(host), 2, 5),
                message);
        }

        private static IEnumerable<Bk2Frame> Frames(int count)
        {
            for (int i = 0; i < count; i++) yield return new Bk2Frame();
        }

        private static string ManifestPath()
        {
            return Path.GetFullPath(Path.Combine(EndToEndTests.ToolDirectory,
                "fixtures/gpgx-audio-service-manifests-v1.json"));
        }

        private sealed class RecordingSink : IS3kCompleteAudioCaptureSink
        {
            private readonly IGpgxHost host;

            internal RecordingSink(IGpgxHost host) { this.host = host; }
            internal int BoundaryCalls;
            internal int CutoffCalls;
            internal int BoundaryCompletedFrame = -1;
            internal readonly List<int> Rows = new List<int>();
            internal readonly List<CompleteRunAudioObserver.FrameCapture> Frames =
                new List<CompleteRunAudioObserver.FrameCapture>();
            internal CompleteRunAudioObserver.CutoffFrontier Boundary;

            public void Begin(CompleteRunAudioObserver.CutoffFrontier boundary)
            {
                BoundaryCalls++;
                BoundaryCompletedFrame = host == null ? -1 : host.CompletedFrame;
                Boundary = boundary;
            }

            public void Frame(int row, CompleteRunAudioObserver.FrameCapture frame)
            {
                Rows.Add(row);
                Frames.Add(frame);
            }

            public void Complete(CompleteRunAudioObserver.CutoffFrontier cutoff)
            {
                CutoffCalls++;
            }
        }
    }
}
