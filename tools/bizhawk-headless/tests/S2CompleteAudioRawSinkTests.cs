using System;
using System.Collections.Generic;
using System.IO;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S2CompleteAudioRawSinkTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2CompleteAudioRawSinkTests stream an exact bounded raw envelope",
                StreamsExactBoundedRawEnvelope));
            tests.Add(new TestMain.TestCase(
                "S2CompleteAudioRawSinkTests expose only a pinned production capture",
                ExposesOnlyPinnedProductionCapture));
            tests.Add(new TestMain.TestCase(
                "S2CompleteAudioRawSinkTests publish a create-new file transactionally",
                PublishesCreateNewFileTransactionally));
            tests.Add(new TestMain.TestCase(
                "S2CompleteAudioRawSinkTests remove staging after capture failure",
                RemovesStagingAfterCaptureFailure));
            tests.Add(new TestMain.TestCase(
                "S2CompleteAudioRawSinkTests preserve reset service absent begin source",
                PreservesResetServiceAbsentBeginSource));
            if (Environment.GetEnvironmentVariable(
                "OPENGGF_S2_COMPLETE_AUDIO_REFERENCE") == "1")
            {
                string rom = Environment.GetEnvironmentVariable("S2_ROM_PATH");
                string movie = Environment.GetEnvironmentVariable("S2_BK2_PATH");
                if (File.Exists(rom) && File.Exists(movie))
                {
                    tests.Add(new TestMain.TestCase(
                        "S2CompleteAudioRawSinkTests prove the real row 769 raw boundary",
                        () => ProvesRealRow769RawBoundary(rom, movie),
                        game: "s2", serial: true, estimatedSeconds: 20.0));
                }
            }
        }

        private static void ExposesOnlyPinnedProductionCapture()
        {
            AssertEx.Throws<ArgumentException>(
                () => S2CompleteAudioCaptureRunner.CaptureRawPinned(
                    "relative.gen", "relative.bk2", "relative.json",
                    "relative.json", "relative.jsonl"), "absolute");
        }

        private static void PublishesCreateNewFileTransactionally()
        {
            string root = TestScratch.CreateRootPath("s2-raw-transaction");
            Directory.CreateDirectory(root);
            try
            {
                string output = Path.Combine(root, "raw.jsonl");
                S2CompleteAudioCaptureRunner.PublishRawForTesting(
                    output, writer => writer.Write("raw\n"));
                AssertEx.Equal("raw\n", File.ReadAllText(output));
                AssertEx.Equal(1, Directory.GetFiles(root).Length);
                AssertEx.Throws<IOException>(
                    () => S2CompleteAudioCaptureRunner.PublishRawForTesting(
                        output, writer => writer.Write("replace\n")),
                    "already exists");
                AssertEx.Equal("raw\n", File.ReadAllText(output));
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void RemovesStagingAfterCaptureFailure()
        {
            string root = TestScratch.CreateRootPath("s2-raw-failure");
            Directory.CreateDirectory(root);
            try
            {
                string output = Path.Combine(root, "raw.jsonl");
                AssertEx.Throws<InvalidDataException>(
                    () => S2CompleteAudioCaptureRunner.PublishRawForTesting(
                        output, writer =>
                        {
                            writer.Write("partial\n");
                            throw new InvalidDataException("capture failed");
                        }), "capture failed");
                AssertEx.Equal(false, File.Exists(output));
                AssertEx.Equal(0, Directory.GetFiles(root).Length);
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void PreservesResetServiceAbsentBeginSource()
        {
            var state = new FakeStateSource(new byte[0x2000]);
            var output = new StringWriter();
            var sink = new S2CompleteAudioRawSink(state, output);
            sink.Begin(new CompleteRunAudioObserver.CutoffFrontier(
                new List<CompleteRunAudioObserver.ServiceBuilder>(),
                new List<CompleteRunAudioObserver.ServiceBuilder>(),
                0x28, 0xA1, 1, true));
            var reset = new CompleteRunAudioObserver.ServiceBuilder
            {
                Token = 9, Kind = 1, Depth = 0,
                CurrentParentToken = 0, CurrentDepth = 0,
                BeginCoordinate = 1, EndCoordinate = 2,
                BeginPc = 0, EndPc = 0,
                BeginHookToken = 0, BeginSourceCpu = 0,
                EndHookToken = 0
            };
            sink.Complete(new CompleteRunAudioObserver.CutoffFrontier(
                new List<CompleteRunAudioObserver.ServiceBuilder>(),
                new List<CompleteRunAudioObserver.ServiceBuilder> { reset },
                0x28, 0xA1, 1, true));

            string[] lines = output.ToString().Split(
                new[] { '\n' }, StringSplitOptions.RemoveEmptyEntries);
            JObject service = (JObject)((JArray)JObject.Parse(lines[2])
                ["pending_descendants"])[0];
            AssertEx.Equal(1, (int)service["kind"]);
            AssertEx.Equal(0, (int)service["begin_source_cpu"]);
            AssertEx.Equal(0, (int)service["begin_pc"]);
            AssertEx.Equal(0, (int)service["begin_hook_token"]);
        }

        private static void StreamsExactBoundedRawEnvelope()
        {
            byte[] state = new byte[0x2000];
            for (int index = 0; index < state.Length; index++)
                state[index] = (byte)index;
            var source = new FakeStateSource(state);
            var output = new StringWriter();
            var sink = new S2CompleteAudioRawSink(source, output);

            sink.Begin(new CompleteRunAudioObserver.CutoffFrontier(
                new List<CompleteRunAudioObserver.ServiceBuilder>(),
                new List<CompleteRunAudioObserver.ServiceBuilder>(),
                0, 0, 0, false));
            source.Lagged = true;
            var raw = new GpgxAudioTraceEvent
            {
                Ordinal = 0, ServiceToken = 2, ParentToken = 1,
                Pc = 0x1234, Subject = 1, Offset = 2, Kind = 6,
                ServiceKindId = 3, Depth = 1, SourceCpu = 1,
                PayloadLength = 8, Value = 0x2A, Flags = 0,
                Reserved = 0, Payload = ulong.MaxValue
            };
            var frame = new CompleteRunAudioObserver.FrameCapture(
                new[] { raw },
                new List<CompleteRunAudioObserver.ServiceBuilder>(),
                new List<CompleteRunAudioObserver.ResetRecord>(), 0);
            sink.Frame(S2AudioObserverProfile.FirstRow, frame);
            sink.Complete(new CompleteRunAudioObserver.CutoffFrontier(
                new List<CompleteRunAudioObserver.ServiceBuilder>(),
                new List<CompleteRunAudioObserver.ServiceBuilder>(),
                0, 0, 0, false));

            string[] lines = output.ToString().Split(
                new[] { '\n' }, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(4, lines.Length);
            JObject metadata = JObject.Parse(lines[0]);
            AssertEx.Equal("metadata", (string)metadata["type"]);
            AssertEx.Equal("openggf.s2-complete-run-audio-raw.v1",
                (string)metadata["schema"]);
            AssertEx.Equal(S2AudioObserverProfile.RomSha1,
                (string)metadata["rom_sha1"]);
            AssertEx.Equal(S2AudioObserverProfile.MovieSha256,
                (string)metadata["bk2_sha256"]);
            AssertEx.Equal(S2AudioObserverProfile.ServiceManifestSha256,
                (string)metadata["service_manifest_sha256"]);

            JObject baseline = JObject.Parse(lines[1]);
            AssertEx.Equal("baseline", (string)baseline["type"]);
            AssertEx.Equal(769, (int)baseline["row"]);
            AssertEx.Equal(16384, ((string)baseline["state_hex"]).Length);
            AssertEx.Equal(false, (bool)baseline["native_armed"]);

            JObject row = JObject.Parse(lines[2]);
            AssertEx.Equal("frame", (string)row["type"]);
            AssertEx.Equal(769, (int)row["row"]);
            AssertEx.Equal(true, (bool)row["lag"]);
            JArray events = (JArray)row["events"];
            AssertEx.Equal(1, events.Count);
            AssertEx.Equal("18446744073709551615", (string)events[0]["payload"]);
            AssertEx.Equal(8, (int)events[0]["payload_length"]);
            AssertEx.Equal(0, (int)events[0]["reserved"]);

            JObject cutoff = JObject.Parse(lines[3]);
            AssertEx.Equal("cutoff", (string)cutoff["type"]);
            AssertEx.Equal(770, (int)cutoff["exclusive_end"]);
            AssertEx.Equal(3, source.Captures);
        }

        private static void ProvesRealRow769RawBoundary(
            string romPath, string moviePath)
        {
            var output = new StringWriter();
                S2CompleteAudioCaptureRunner.CaptureResult result =
                S2CompleteAudioCaptureRunner.CaptureRawBoundaryProofPinnedForTesting(
                    romPath, moviePath, ManifestPath(), CapabilityPath(), output);
            string[] lines = output.ToString().Split(
                new[] { '\n' }, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(770, result.ObservedRows);
            AssertEx.Equal(1, result.PublishedRows);
            AssertEx.Equal(4, lines.Length);
            JObject baseline = JObject.Parse(lines[1]);
            AssertEx.Equal(769, (int)baseline["row"]);
            AssertEx.Equal(true, (bool)baseline["native_armed"]);
            AssertEx.Equal(16384, ((string)baseline["state_hex"]).Length);
            AssertEx.Equal(0x2A, (int)baseline["ym_port0_latch"]);
            AssertEx.Equal(0xA1, (int)baseline["ym_port1_latch"]);
            AssertEx.Equal(1L, (long)baseline["native_arm_epoch"]);
            JArray active = (JArray)baseline["active_services"];
            AssertEx.Equal(1, active.Count);
            AssertEx.Equal(4, (int)active[0]["kind"]);
            AssertEx.Equal(0, ((JArray)baseline["pending_descendants"]).Count);
            JObject row = JObject.Parse(lines[2]);
            AssertEx.Equal(769, (int)row["row"]);
            AssertEx.Equal(1491, ((JArray)row["events"]).Count);
            AssertEx.Equal(770, (int)JObject.Parse(lines[3])["exclusive_end"]);
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

        private sealed class FakeStateSource : IS2CompleteAudioStateSource
        {
            private readonly byte[] state;
            internal FakeStateSource(byte[] value) { state = value; }
            internal bool Lagged;
            internal int Captures;
            public bool IsLagged { get { return Lagged; } }
            public byte[] CaptureDriverState()
            {
                Captures++;
                return (byte[])state.Clone();
            }
        }
    }
}
