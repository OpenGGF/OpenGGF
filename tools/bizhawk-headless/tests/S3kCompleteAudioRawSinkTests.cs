using System;
using System.Collections.Generic;
using System.IO;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S3kCompleteAudioRawSinkTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3kCompleteAudioRawSinkTests stream an exact bounded raw envelope",
                StreamsExactBoundedRawEnvelope));
            tests.Add(new TestMain.TestCase(
                "S3kCompleteAudioRawSinkTests expose only a pinned production capture",
                ExposesOnlyPinnedProductionCapture));
            tests.Add(new TestMain.TestCase(
                "S3kCompleteAudioRawSinkTests publish a create-new file transactionally",
                PublishesCreateNewFileTransactionally));
            tests.Add(new TestMain.TestCase(
                "S3kCompleteAudioRawSinkTests remove staging after capture failure",
                RemovesStagingAfterCaptureFailure));
            tests.Add(new TestMain.TestCase(
                "S3kCompleteAudioRawSinkTests preserve reset service absent begin source",
                PreservesResetServiceAbsentBeginSource));
            if (Environment.GetEnvironmentVariable(
                "OPENGGF_S3K_COMPLETE_AUDIO_REFERENCE") == "1")
            {
                string rom = Environment.GetEnvironmentVariable("S3K_ROM_PATH");
                string movie = Environment.GetEnvironmentVariable("S3K_BK2_PATH");
                if (File.Exists(rom) && File.Exists(movie))
                {
                    tests.Add(new TestMain.TestCase(
                        "S3kCompleteAudioRawSinkTests prove the real row 810 raw boundary",
                        () => ProvesRealRow810RawBoundary(rom, movie),
                        game: "s3k", serial: true, estimatedSeconds: 20.0));
                }
            }
        }

        private static void ExposesOnlyPinnedProductionCapture()
        {
            AssertEx.Throws<ArgumentException>(
                () => S3kCompleteAudioCaptureRunner.CaptureRawPinned(
                    "relative.gen", "relative.bk2", "relative.json",
                    "relative.jsonl"), "absolute");
        }

        private static void PublishesCreateNewFileTransactionally()
        {
            string root = TestScratch.CreateRootPath("s3k-raw-transaction");
            Directory.CreateDirectory(root);
            try
            {
                string output = Path.Combine(root, "raw.jsonl");
                S3kCompleteAudioCaptureRunner.PublishRawForTesting(
                    output, writer => writer.Write("raw\n"));
                AssertEx.Equal("raw\n", File.ReadAllText(output));
                AssertEx.Equal(1, Directory.GetFiles(root).Length);
                AssertEx.Throws<IOException>(
                    () => S3kCompleteAudioCaptureRunner.PublishRawForTesting(
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
            string root = TestScratch.CreateRootPath("s3k-raw-failure");
            Directory.CreateDirectory(root);
            try
            {
                string output = Path.Combine(root, "raw.jsonl");
                AssertEx.Throws<InvalidDataException>(
                    () => S3kCompleteAudioCaptureRunner.PublishRawForTesting(
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
            var state = new FakeStateSource(new byte[0x400]);
            var output = new StringWriter();
            var sink = new S3kCompleteAudioRawSink(state, output);
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
            var api = new S3kAudioObserverProfileTests.RecordingTraceApi();
            CompleteRunAudioObserver observer = S3kAudioObserverProfile.CreateObserver(
                ManifestPath(), api);
            byte[] state = new byte[0x400];
            for (int index = 0; index < state.Length; index++)
                state[index] = (byte)index;
            var source = new FakeStateSource(state);
            var output = new StringWriter();
            var sink = new S3kCompleteAudioRawSink(source, output);

            sink.Begin(observer.CaptureBoundaryFrontierAndResetPublication());
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
            sink.Frame(S3kAudioObserverProfile.FirstRow, frame);
            sink.Complete(observer.CaptureCutoffFrontier());

            string[] lines = output.ToString().Split(
                new[] { '\n' }, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(4, lines.Length);
            JObject metadata = JObject.Parse(lines[0]);
            AssertEx.Equal("metadata", (string)metadata["type"]);
            AssertEx.Equal("openggf.s3k-complete-run-audio-raw.v1",
                (string)metadata["schema"]);
            AssertEx.Equal(S3kAudioObserverProfile.RomSha1,
                (string)metadata["rom_sha1"]);
            AssertEx.Equal(S3kAudioObserverProfile.MovieSha256,
                (string)metadata["bk2_sha256"]);
            AssertEx.Equal(S3kAudioObserverProfile.ManifestSha256,
                (string)metadata["service_manifest_sha256"]);

            JObject baseline = JObject.Parse(lines[1]);
            AssertEx.Equal("baseline", (string)baseline["type"]);
            AssertEx.Equal(810, (int)baseline["row"]);
            AssertEx.Equal(2048, ((string)baseline["state_hex"]).Length);
            AssertEx.Equal(false, (bool)baseline["native_armed"]);

            JObject row = JObject.Parse(lines[2]);
            AssertEx.Equal("frame", (string)row["type"]);
            AssertEx.Equal(810, (int)row["row"]);
            AssertEx.Equal(true, (bool)row["lag"]);
            JArray events = (JArray)row["events"];
            AssertEx.Equal(1, events.Count);
            AssertEx.Equal("18446744073709551615", (string)events[0]["payload"]);
            AssertEx.Equal(8, (int)events[0]["payload_length"]);
            AssertEx.Equal(0, (int)events[0]["reserved"]);

            JObject cutoff = JObject.Parse(lines[3]);
            AssertEx.Equal("cutoff", (string)cutoff["type"]);
            AssertEx.Equal(811, (int)cutoff["exclusive_end"]);
            AssertEx.Equal(3, source.Captures);
        }

        private static void ProvesRealRow810RawBoundary(
            string romPath, string moviePath)
        {
            var output = new StringWriter();
                S3kCompleteAudioCaptureRunner.CaptureResult result =
                S3kCompleteAudioCaptureRunner.CaptureRawBoundaryProofPinnedForTesting(
                    romPath, moviePath, ManifestPath(), output);
            string[] lines = output.ToString().Split(
                new[] { '\n' }, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(811, result.ObservedRows);
            AssertEx.Equal(1, result.PublishedRows);
            AssertEx.Equal(4, lines.Length);
            JObject baseline = JObject.Parse(lines[1]);
            AssertEx.Equal(810, (int)baseline["row"]);
            AssertEx.Equal(true, (bool)baseline["native_armed"]);
            AssertEx.Equal(2048, ((string)baseline["state_hex"]).Length);
            AssertEx.Equal(0x28, (int)baseline["ym_port0_latch"]);
            AssertEx.Equal(0xA1, (int)baseline["ym_port1_latch"]);
            AssertEx.Equal(1L, (long)baseline["native_arm_epoch"]);
            AssertEx.Equal(0, ((JArray)baseline["active_services"]).Count);
            AssertEx.Equal(0, ((JArray)baseline["pending_descendants"]).Count);
            JObject row = JObject.Parse(lines[2]);
            AssertEx.Equal(810, (int)row["row"]);
            AssertEx.Equal(34, ((JArray)row["events"]).Count);
            AssertEx.Equal(811, (int)JObject.Parse(lines[3])["exclusive_end"]);
        }

        private static string ManifestPath()
        {
            return Path.GetFullPath(Path.Combine(EndToEndTests.ToolDirectory,
                "fixtures/gpgx-audio-service-manifests-v1.json"));
        }

        private sealed class FakeStateSource : IS3kCompleteAudioStateSource
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
