using System;
using System.Collections.Generic;
using System.IO;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S2AudioObserverProfileTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2AudioObserverProfileTests configure the reviewed S2 service graph",
                ConfiguresReviewedServiceGraph,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "S2AudioObserverProfileTests reject changed S2 reference evidence",
                RejectsChangedReferenceEvidence,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "S2AudioObserverProfileTests verify the final observer installation",
                VerifiesFinalObserverInstallation,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "S2AudioObserverProfileTests pin the complete movie and comparison boundary",
                PinsCompleteMovieAndComparisonBoundary));
            tests.Add(new TestMain.TestCase(
                "S2AudioObserverProfileTests reject changed service manifest bytes",
                RejectsChangedServiceManifestBytes));
            tests.Add(new TestMain.TestCase(
                "S2AudioObserverProfileTests reject a wrong ROM before host construction",
                RejectsWrongRomBeforeHostConstruction));
            if (File.Exists(MoviePath()))
            {
                tests.Add(new TestMain.TestCase(
                    "S2AudioObserverProfileTests authenticate the tracked complete movie",
                    AuthenticatesTrackedCompleteMovie));
                tests.Add(new TestMain.TestCase(
                    "S2AudioObserverProfileTests reject changed complete movie bytes",
                    RejectsChangedMovieBytes));
            }
        }

        private static void ConfiguresReviewedServiceGraph()
        {
            var api = new RecordingTraceApi();
            CompleteRunAudioObserver observer = S2AudioObserverProfile.CreateObserver(
                Fixture("gpgx-audio-service-manifests-v1.json"),
                Fixture("gpgx-audio-capability-v1.json"), api);

            AssertEx.Equal(true, observer != null);
            AssertEx.Equal(1, api.ConfigureCalls);
            AssertEx.Equal((ushort)2, api.Config.AbiVersion);
            AssertEx.Equal(1u, api.Config.Flags);
            AssertEx.Equal((byte)2, FindHook(api.Hooks, 9).Flags);
            AssertEx.Equal((byte)3, FindHook(api.Hooks, 10).Flags);
            foreach (GpgxAudioObserverAdapter.ServiceHook hook in api.Hooks)
            {
                if (hook.HookToken != 9 && hook.HookToken != 10)
                    AssertEx.Equal((byte)0, hook.Flags);
            }
            AssertEx.Equal(9, api.Kinds.Length);
            AssertEx.Equal(23, api.Hooks.Length);
            AssertEx.Equal(2, api.Ranges.Length);
            AssertEx.Equal(8192, api.Mask.Length);
            AssertEx.Equal(true, Watched(api.Mask, 0x0038));
            AssertEx.Equal(true, Watched(api.Mask, 0x0110));
            AssertEx.Equal(true, Watched(api.Mask, 0x017A));
            AssertEx.Equal(false, Watched(api.Mask, 0x0178));
            AssertEx.Equal(0xEC000u, FindHook(api.Hooks, 9).Pc);
            AssertEx.Equal(0xEC036u, FindHook(api.Hooks, 10).Pc);

            S2AudioObserverProfile.Capability capability =
                S2AudioObserverProfile.LoadCapability(
                    Fixture("gpgx-audio-service-manifests-v1.json"),
                    Fixture("gpgx-audio-capability-v1.json"));
            AssertEx.Equal(259590, capability.Frames);
            AssertEx.Equal(169986419L, capability.Events);
            AssertEx.Equal(1825, capability.MaximumFrameOccupancy);
            AssertEx.Equal(0, capability.OpenServicesAtCutoff);
            AssertEx.Equal(0, capability.PendingServicesAtCutoff);
            AssertEx.Equal(
                "c2b2f82374aaa16144b6bf121df051dcd5b4ba095431c16cf6224adc633de41d",
                capability.EventDigestSha256);
        }

        private static void RejectsChangedReferenceEvidence()
        {
            string root = TestScratch.CreateRootPath("s2-audio-profile");
            Directory.CreateDirectory(root);
            try
            {
                string manifest = Path.Combine(root, "manifest.json");
                string capability = Path.Combine(root, "capability.json");
                File.Copy(Fixture("gpgx-audio-service-manifests-v1.json"), manifest);
                string original = File.ReadAllText(
                    Fixture("gpgx-audio-capability-v1.json"));
                string executable = (string)JObject.Parse(original)
                    ["task8_harness_executable_sha256"];
                string alternateExecutable = new string('0',64);
                File.WriteAllText(capability,original.Replace(
                    executable,alternateExecutable));
                AssertEx.Equal(
                    S2AudioObserverProfile.CapabilityTemplateSha256(
                        Fixture("gpgx-audio-capability-v1.json")),
                    S2AudioObserverProfile.CapabilityTemplateSha256(capability));

                JObject value = JObject.Parse(File.ReadAllText(
                    Fixture("gpgx-audio-capability-v1.json")));
                value["runs"]["s2"]["complete"]["events"] = 169986418;
                File.WriteAllText(capability, value.ToString());
                AssertEx.Throws<InvalidDataException>(
                    () => S2AudioObserverProfile.LoadCapability(manifest, capability),
                    "capability template identity");

                File.WriteAllText(capability,original.Replace(
                    "{", "{\n  \"task8_harness_executable_sha256\": \""
                        +new string('0',64)+"\","));
                AssertEx.Throws<InvalidDataException>(() =>
                    S2AudioObserverProfile.CapabilityTemplateSha256(capability),
                    "exactly one");

                File.WriteAllText(capability,original.Replace(
                    executable,new string('F',64)));
                AssertEx.Throws<InvalidDataException>(() =>
                    S2AudioObserverProfile.CapabilityTemplateSha256(capability),
                    "lowercase");

                value = JObject.Parse(File.ReadAllText(
                    Fixture("gpgx-audio-capability-v1.json")));
                value["runs"]["s2"]["movie"]["sha256"] = new string('0', 64);
                File.WriteAllText(capability, value.ToString());
                AssertEx.Throws<InvalidDataException>(
                    () => S2AudioObserverProfile.LoadCapability(manifest, capability),
                    "capability template identity");

                File.WriteAllText(capability,original.Replace(
                    executable,new string('f',64)));
                AssertEx.Throws<InvalidDataException>(
                    () => S2AudioObserverProfile.LoadCapability(manifest, capability),
                    "capability executable identity");
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void VerifiesFinalObserverInstallation()
        {
            string home = Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            if (string.IsNullOrEmpty(home))
                throw new InvalidOperationException(
                    "BIZHAWK_HOME must name the final observer installation.");
            S2AudioObserverProfile.InstallationIdentity identity =
                S2AudioObserverProfile.VerifyInstallation(home);
            AssertEx.Equal("bizhawk-2.11-gpgx-audio-observer-v3",
                identity.InstallationId);
            AssertEx.Equal("gpgx-audio-observer-v3", identity.CoreId);
            AssertEx.Equal(4, identity.AbiVersion);
            AssertEx.Equal("cba4d8c88cf968a9", identity.BuildId);

            string root = TestScratch.CreateRootPath("s2-audio-install");
            Directory.CreateDirectory(Path.Combine(root,
                "gpgx-audio-observer-source"));
            try
            {
                string target = Path.Combine(home,
                    "gpgx-audio-observer-source", "identity.json");
                string link = Path.Combine(root,
                    "gpgx-audio-observer-source", "identity.json");
                if (Symlink(target, link) != 0)
                    throw new InvalidOperationException(
                        "Unable to create observer identity symlink test input.");
                AssertEx.Throws<InvalidDataException>(
                    () => S2AudioObserverProfile.VerifyInstallation(root),
                    "symbolic link");
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void PinsCompleteMovieAndComparisonBoundary()
        {
            AssertEx.Equal(769, S2AudioObserverProfile.FirstRow);
            AssertEx.Equal(259590, S2AudioObserverProfile.ExclusiveEnd);
            AssertEx.Equal("9FEEB724052C39982D432A7851C98D3E",
                S2AudioObserverProfile.MovieHeaderHash);
            AssertEx.Equal(RomIdentity.Sonic2Rev01Sha1.ToLowerInvariant(),
                S2AudioObserverProfile.RomSha1);
        }

        private static void AuthenticatesTrackedCompleteMovie()
        {
            Bk2Movie movie = S2AudioObserverProfile.OpenMovie(MoviePath());
            AssertEx.Equal(259590, movie.FrameCount);
            AssertEx.Equal("9FEEB724052C39982D432A7851C98D3E", movie.Sha1);
        }

        private static void RejectsWrongRomBeforeHostConstruction()
        {
            string root = TestScratch.CreateRootPath("s2-audio-rom");
            try
            {
                Directory.CreateDirectory(root);
                string changed = Path.Combine(root, "wrong.gen");
                File.WriteAllBytes(changed, new byte[] { 1, 2, 3 });
                AssertEx.Throws<InvalidDataException>(
                    () => S2CompleteAudioCaptureRunner.CapturePinned(
                        changed, MoviePath(),
                        Fixture("gpgx-audio-service-manifests-v1.json"),
                        Fixture("gpgx-audio-capability-v1.json"),
                        new NeverCalledSink()), "ROM");
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void RejectsChangedMovieBytes()
        {
            string root = TestScratch.CreateRootPath("s2-audio-inputs");
            try
            {
                Directory.CreateDirectory(root);
                string movie = Path.Combine(root, "changed.bk2");
                byte[] movieBytes = File.ReadAllBytes(MoviePath());
                movieBytes[movieBytes.Length - 1] ^= 1;
                File.WriteAllBytes(movie, movieBytes);
                AssertEx.Throws<InvalidDataException>(
                    () => S2AudioObserverProfile.OpenMovie(movie),
                    "movie identity");
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void RejectsChangedServiceManifestBytes()
        {
            string root = TestScratch.CreateRootPath("s2-audio-manifest");
            try
            {
                Directory.CreateDirectory(root);
                string manifest = Path.Combine(root, "changed.json");
                File.WriteAllText(manifest,
                    File.ReadAllText(Fixture(
                        "gpgx-audio-service-manifests-v1.json")) + "\n");
                AssertEx.Throws<InvalidDataException>(
                    () => S2AudioObserverProfile.CreateObserver(
                        manifest,
                        Fixture("gpgx-audio-capability-v1.json"),
                        new RecordingTraceApi()),
                    "service manifest identity");
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static string Fixture(string name)
        {
            return Path.Combine(EndToEndTests.ToolDirectory, "fixtures", name);
        }

        private static string MoviePath()
        {
            return Path.Combine(EndToEndTests.RepositoryRoot,
                "src", "test", "resources", "traces", "s2", "runs",
                "s2-sonic-tails-complete-emeralds",
                "sonic-2-sonic-tails-complete-emeralds.bk2");
        }

        private static bool Watched(byte[] mask, int pc)
        {
            return (mask[pc >> 3] & (1 << (pc & 7))) != 0;
        }

        private static GpgxAudioObserverAdapter.ServiceHook FindHook(
            GpgxAudioObserverAdapter.ServiceHook[] hooks, ushort token)
        {
            foreach (GpgxAudioObserverAdapter.ServiceHook hook in hooks)
                if (hook.HookToken == token) return hook;
            throw new InvalidOperationException("Missing hook token.");
        }

        private sealed class NeverCalledSink : IS2CompleteAudioCaptureSink
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
            internal int ConfigureCalls;
            internal int PublicationCalls;
            internal GpgxAudioObserverAdapter.Config Config;
            internal byte[] Mask;
            internal GpgxAudioObserverAdapter.ServiceKind[] Kinds;
            internal GpgxAudioObserverAdapter.ServiceHook[] Hooks;
            internal GpgxAudioObserverAdapter.SnapshotRange[] Ranges;
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
                Mask = (byte[])mask.Clone();
                Kinds = (GpgxAudioObserverAdapter.ServiceKind[])kinds.Clone();
                Hooks = (GpgxAudioObserverAdapter.ServiceHook[])hooks.Clone();
                Ranges = (GpgxAudioObserverAdapter.SnapshotRange[])ranges.Clone();
                return 0;
            }
            public int BeginFrame() { return 0; }
            public int EndFrame() { return 0; }
            public int EventCount(out uint count, out uint overflow)
            { count = 0; overflow = 0; return 0; }
            public int Drain(GpgxAudioTraceEvent[] events, uint capacity,
                out uint count) { count = 0; return 0; }
            public int GetFirstFault(out GpgxAudioObserverAdapter.FirstFault fault)
            { fault = new GpgxAudioObserverAdapter.FirstFault(); return 0; }
            public int BeginPublicationEpoch() { PublicationCalls++; return 0; }
            public int AbortFrame() { return 0; }
            public int Disable() { return 0; }
        }

        [System.Runtime.InteropServices.DllImport(
            "libc", EntryPoint = "symlink", CharSet =
                System.Runtime.InteropServices.CharSet.Ansi,
            SetLastError = true)]
        private static extern int Symlink(string target, string linkPath);
    }
}
