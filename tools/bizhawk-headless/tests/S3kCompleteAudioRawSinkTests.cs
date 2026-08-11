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
                    new StringWriter()), "absolute");
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
            CompleteRunAudioObserver.FrameCapture frame =
                observer.CaptureCanonicalFrame(() => { });
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
            AssertEx.Equal(0, ((JArray)row["events"]).Count);

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
                S3kCompleteAudioCaptureRunner.CaptureRawBoundaryProofPinned(
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
