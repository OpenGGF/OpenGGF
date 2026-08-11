using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S2CompleteAudioCaptureRunnerTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2CompleteAudioCaptureRunnerTests drain power-on then stream every comparison row",
                DrainsPowerOnThenStreamsEveryComparisonRow));
            tests.Add(new TestMain.TestCase(
                "S2CompleteAudioCaptureRunnerTests reject short and overlong movie streams",
                RejectsShortAndOverlongMovieStreams));
            tests.Add(new TestMain.TestCase(
                "S2CompleteAudioCaptureRunnerTests stop a prefix without consuming the preserved tail",
                StopsPrefixWithoutConsumingPreservedTail));
            if (Environment.GetEnvironmentVariable(
                "OPENGGF_S2_COMPLETE_AUDIO_REFERENCE") == "1")
            {
                string rom = Environment.GetEnvironmentVariable("S2_ROM_PATH");
                string movie = Environment.GetEnvironmentVariable("S2_BK2_PATH");
                if (File.Exists(rom) && File.Exists(movie))
                {
                    tests.Add(new TestMain.TestCase(
                        "S2CompleteAudioCaptureRunnerTests prove real power-on to row 769 publication",
                        () => ProvesRealPowerOnToRow769Publication(rom, movie),
                        game: "s2", serial: true,
                        estimatedSeconds: 20.0));
                }
            }
        }

        private static void DrainsPowerOnThenStreamsEveryComparisonRow()
        {
            var api = new S2AudioObserverProfileTests.RecordingTraceApi();
            CompleteRunAudioObserver observer = S2AudioObserverProfile.CreateObserver(
                ManifestPath(), CapabilityPath(), api);
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

            S2CompleteAudioCaptureRunner.CaptureResult result =
                S2CompleteAudioCaptureRunner.CaptureIntervalForTesting(
                    rows, host, observer, sink, 2, 5);

            AssertEx.Equal(5, result.ObservedRows);
            AssertEx.Equal(3, result.PublishedRows);
            AssertEx.Equal(2, sink.BoundaryCompletedFrame);
            AssertEx.Equal(3, sink.FrameCalls);
            AssertEx.Equal(2, sink.FirstRow);
            AssertEx.Equal(4, sink.LastRow);
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
            var api = new S2AudioObserverProfileTests.RecordingTraceApi();
            CompleteRunAudioObserver observer = S2AudioObserverProfile.CreateObserver(
                ManifestPath(), CapabilityPath(), api);
            var host = new FakeS1Host(null);
            var sink = new RecordingSink(host);
            S2CompleteAudioCaptureRunner.CaptureResult result =
                S2CompleteAudioCaptureRunner.CapturePrefixForTesting(
                    Frames(6), host, observer, sink, 2, 5);
            AssertEx.Equal(5, result.ObservedRows);
            AssertEx.Equal(3, result.PublishedRows);
            AssertEx.Equal(5, host.CompletedFrame);
        }

        private static void ProvesRealPowerOnToRow769Publication(
            string romPath, string moviePath)
        {
            var sink = new RecordingSink(null);
            S2CompleteAudioCaptureRunner.CaptureResult result =
                S2CompleteAudioCaptureRunner.CaptureBoundaryProofPinned(
                    romPath, moviePath, ManifestPath(), CapabilityPath(), sink);
            AssertEx.Equal(770, result.ObservedRows);
            AssertEx.Equal(1, result.PublishedRows);
            AssertEx.Equal(true, sink.Boundary.IsArmed);
            AssertEx.Equal(1L, sink.Boundary.ArmEpoch);
            AssertEx.Equal(1, sink.Boundary.ActiveServices.Count);
            AssertEx.Equal((byte)4, sink.Boundary.ActiveServices[0].Kind);
            AssertEx.Equal(0, sink.Boundary.PendingServices.Count);
            AssertEx.Equal((byte)0x2A, sink.Boundary.YmPort0Address);
            AssertEx.Equal((byte)0xA1, sink.Boundary.YmPort1Address);
            AssertEx.Equal(1, sink.FrameCalls);
            AssertEx.Equal(769, sink.FirstRow);
            AssertEx.Equal(769, sink.LastRow);
            AssertEx.Equal(1491, sink.FirstFrame.RawEvents.Count);
            Console.WriteLine("S2 audio row769 boundary: active="
                + sink.Boundary.ActiveServices.Count + " pending="
                + sink.Boundary.PendingServices.Count + " ym="
                + sink.Boundary.YmPort0Address.ToString("x2") + "/"
                + sink.Boundary.YmPort1Address.ToString("x2")
                + " epoch=" + sink.Boundary.ArmEpoch + " row_events="
                + sink.FirstFrame.RawEvents.Count);
        }

        private static void AssertStreamRejected(
            IEnumerable<Bk2Frame> rows, string message)
        {
            var api = new S2AudioObserverProfileTests.RecordingTraceApi();
            CompleteRunAudioObserver observer = S2AudioObserverProfile.CreateObserver(
                ManifestPath(), CapabilityPath(), api);
            var host = new FakeS1Host(null);
            AssertEx.Throws<InvalidDataException>(
                () => S2CompleteAudioCaptureRunner.CaptureIntervalForTesting(
                    rows, host, observer, new RecordingSink(host), 2, 5),
                message);
        }

        private static IEnumerable<Bk2Frame> Frames(int count)
        {
            for (int index = 0; index < count; index++) yield return new Bk2Frame();
        }

        private static string ManifestPath()
        {
            return Path.GetFullPath(Path.Combine(EndToEndTests.ToolDirectory,
                "fixtures/gpgx-audio-service-manifests-v1.json"));
        }

        private static string CapabilityPath()
        {
            return Path.GetFullPath(Path.Combine(EndToEndTests.ToolDirectory,
                "fixtures/gpgx-audio-capability-v1.json"));
        }

        private sealed class RecordingSink : IS2CompleteAudioCaptureSink
        {
            private readonly IGpgxHost host;

            internal RecordingSink(IGpgxHost host) { this.host = host; }
            internal int BoundaryCalls;
            internal int CutoffCalls;
            internal int BoundaryCompletedFrame = -1;
            internal int FrameCalls;
            internal int FirstRow = -1;
            internal int LastRow = -1;
            internal CompleteRunAudioObserver.CutoffFrontier Boundary;
            internal CompleteRunAudioObserver.FrameCapture FirstFrame;

            public void Begin(CompleteRunAudioObserver.CutoffFrontier boundary)
            {
                BoundaryCalls++;
                BoundaryCompletedFrame = host == null ? -1 : host.CompletedFrame;
                Boundary = boundary;
            }

            public void Frame(int row, CompleteRunAudioObserver.FrameCapture frame)
            {
                if (FrameCalls == 0)
                {
                    FirstRow = row;
                    FirstFrame = frame;
                }
                LastRow = row;
                FrameCalls++;
            }

            public void Complete(CompleteRunAudioObserver.CutoffFrontier cutoff)
            {
                CutoffCalls++;
            }
        }
    }
}
