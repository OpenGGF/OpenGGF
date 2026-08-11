using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S3kAudioObserverProfileTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3kAudioObserverProfileTests pin the reviewed locked-on prepublication profile",
                PinsReviewedLockedOnPrepublicationProfile));
            tests.Add(new TestMain.TestCase(
                "S3kAudioObserverProfileTests reject any service-manifest byte change",
                RejectsAnyServiceManifestByteChange));
            tests.Add(new TestMain.TestCase(
                "S3kAudioObserverProfileTests reject an unpinned complete-run movie",
                RejectsUnpinnedCompleteRunMovie));
            tests.Add(new TestMain.TestCase(
                "S3kAudioObserverProfileTests reject a wrong ROM before host construction",
                RejectsWrongRomBeforeHostConstruction));
        }

        private static void PinsReviewedLockedOnPrepublicationProfile()
        {
            var api = new RecordingTraceApi();
            CompleteRunAudioObserver observer = S3kAudioObserverProfile.CreateObserver(
                ManifestPath(), api);

            AssertEx.Equal(810, S3kAudioObserverProfile.FirstRow);
            AssertEx.Equal(434417, S3kAudioObserverProfile.ExclusiveEnd);
            AssertEx.Equal(0x1C00, S3kAudioObserverProfile.DriverStateStart);
            AssertEx.Equal(0x2000, S3kAudioObserverProfile.DriverStateExclusiveEnd);
            AssertEx.Equal(RomIdentity.Sonic3kLockOnSha1.ToLowerInvariant(),
                S3kAudioObserverProfile.RomSha1);
            AssertEx.Equal("C5B1C655C19F462ADE0AC4E17A844D10",
                S3kAudioObserverProfile.MovieHeaderHash);
            AssertEx.Equal(1, api.ConfigureCalls);
            AssertEx.Equal((ushort)2, api.Config.AbiVersion);
            AssertEx.Equal(1u, api.Config.Flags);
            AssertEx.Equal((ushort)4, api.Config.MaxContinuationFrames);
            AssertEx.Equal((byte)2, Hook(api.Hooks, 7).Flags);
            AssertEx.Equal((byte)3, Hook(api.Hooks, 8).Flags);
            AssertEx.Equal(true, api.Hooks.Where(value => value.HookToken != 7
                && value.HookToken != 8).All(value => value.Flags == 0));
            AssertEx.Equal(false, observer.IsArmed);
        }

        private static void RejectsAnyServiceManifestByteChange()
        {
            AssertEx.Throws<ArgumentException>(
                () => S3kAudioObserverProfile.CreateObserver(
                    "fixtures/gpgx-audio-service-manifests-v1.json",
                    new RecordingTraceApi()), "absolute");

            string text = File.ReadAllText(ManifestPath());
            string scratch = TestScratch.CreateRootPath("s3k-audio-profile");
            try
            {
                Directory.CreateDirectory(scratch);
                string changed = Path.Combine(scratch, "manifest.json");
                File.WriteAllText(changed, text + "\n");
                AssertEx.Throws<InvalidDataException>(
                    () => S3kAudioObserverProfile.CreateObserver(
                        changed, new RecordingTraceApi()), "SHA-256");
            }
            finally
            {
                if (Directory.Exists(scratch)) Directory.Delete(scratch, true);
            }
        }

        private static void RejectsUnpinnedCompleteRunMovie()
        {
            AssertEx.Throws<ArgumentException>(
                () => S3kAudioObserverProfile.OpenMovie("movie.bk2"),
                "absolute");
            string scratch = TestScratch.CreateRootPath("s3k-audio-movie");
            try
            {
                Directory.CreateDirectory(scratch);
                string changed = Path.Combine(scratch, "movie.bk2");
                using (FileStream output = File.Create(changed))
                    output.SetLength(new FileInfo(MoviePath()).Length);
                AssertEx.Throws<InvalidDataException>(
                    () => S3kAudioObserverProfile.OpenMovie(changed),
                    "SHA-256");
            }
            finally
            {
                if (Directory.Exists(scratch)) Directory.Delete(scratch, true);
            }
        }

        private static void RejectsWrongRomBeforeHostConstruction()
        {
            string scratch = TestScratch.CreateRootPath("s3k-audio-rom");
            try
            {
                Directory.CreateDirectory(scratch);
                string changed = Path.Combine(scratch, "wrong.gen");
                File.WriteAllBytes(changed, new byte[] { 1, 2, 3 });
                AssertEx.Throws<InvalidDataException>(
                    () => S3kCompleteAudioCaptureRunner.CapturePinned(
                        changed, MoviePath(), ManifestPath(),
                        new NeverCalledSink()), "ROM");
            }
            finally
            {
                if (Directory.Exists(scratch)) Directory.Delete(scratch, true);
            }
        }

        private static GpgxAudioObserverAdapter.ServiceHook Hook(
            GpgxAudioObserverAdapter.ServiceHook[] hooks, ushort token)
        {
            return hooks.Single(value => value.HookToken == token);
        }

        private static string ManifestPath()
        {
            return Path.GetFullPath(Path.Combine(EndToEndTests.ToolDirectory,
                "fixtures/gpgx-audio-service-manifests-v1.json"));
        }

        private static string MoviePath()
        {
            return Path.Combine(EndToEndTests.RepositoryRoot,
                "src", "test", "resources", "traces", "s3k", "_movies",
                "s3k-knuckles-complete-superemeralds.bk2");
        }

        private sealed class NeverCalledSink : IS3kCompleteAudioCaptureSink
        {
            public void Begin(CompleteRunAudioObserver.CutoffFrontier boundary)
            { throw new InvalidOperationException("sink must not be called"); }
            public void Frame(int row, CompleteRunAudioObserver.FrameCapture frame)
            { throw new InvalidOperationException("sink must not be called"); }
            public void Complete(CompleteRunAudioObserver.CutoffFrontier cutoff)
            { throw new InvalidOperationException("sink must not be called"); }
        }

        internal sealed class RecordingTraceApi : IGpgxAudioTraceApi
        {
            public int ConfigureCalls;
            public int PublicationCalls;
            public GpgxAudioObserverAdapter.Config Config;
            public GpgxAudioObserverAdapter.ServiceHook[] Hooks;
            public uint AbiVersion { get { return 2; } }
            public uint EventSize { get { return 32; } }
            public uint Capacity { get { return 65536; } }

            public int Configure(ref GpgxAudioObserverAdapter.Config config,
                byte[] mask, GpgxAudioObserverAdapter.ServiceKind[] kinds,
                GpgxAudioObserverAdapter.ServiceHook[] hooks,
                GpgxAudioObserverAdapter.SnapshotRange[] ranges)
            {
                ConfigureCalls++;
                Config = config;
                Hooks = (GpgxAudioObserverAdapter.ServiceHook[])hooks.Clone();
                return 0;
            }

            public int BeginFrame() { return 0; }
            public int EndFrame() { return 0; }
            public int EventCount(out uint count, out uint overflow)
            { count = 0; overflow = 0; return 0; }
            public int Drain(GpgxAudioTraceEvent[] events, uint capacity,
                out uint count) { count = 0; return 0; }
            public int GetFirstFault(out GpgxAudioObserverAdapter.FirstFault fault)
            { fault = default(GpgxAudioObserverAdapter.FirstFault); return 0; }
            public int BeginPublicationEpoch() { PublicationCalls++; return 0; }
            public int AbortFrame() { return 0; }
            public int Disable() { return 0; }
        }
    }
}
