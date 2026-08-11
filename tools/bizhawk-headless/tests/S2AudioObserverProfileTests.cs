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
        }

        private static void ConfiguresReviewedServiceGraph()
        {
            var api = new FakeTraceApi();
            CompleteRunAudioObserver observer = S2AudioObserverProfile.CreateObserver(
                Fixture("gpgx-audio-service-manifests-v1.json"),
                Fixture("gpgx-audio-capability-v1.json"), api);

            AssertEx.Equal(true, observer != null);
            AssertEx.Equal(1, api.ConfigureCalls);
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
            AssertEx.Equal((byte)1, FindHook(api.Hooks, 10).Flags);

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
                JObject value = JObject.Parse(File.ReadAllText(
                    Fixture("gpgx-audio-capability-v1.json")));
                value["runs"]["s2"]["complete"]["events"] = 169986418;
                File.WriteAllText(capability, value.ToString());
                AssertEx.Throws<InvalidDataException>(
                    () => S2AudioObserverProfile.LoadCapability(manifest, capability),
                    "capability file identity");

                value = JObject.Parse(File.ReadAllText(
                    Fixture("gpgx-audio-capability-v1.json")));
                value["runs"]["s2"]["movie"]["sha256"] = new string('0', 64);
                File.WriteAllText(capability, value.ToString());
                AssertEx.Throws<InvalidDataException>(
                    () => S2AudioObserverProfile.LoadCapability(manifest, capability),
                    "capability file identity");

                value = JObject.Parse(File.ReadAllText(
                    Fixture("gpgx-audio-capability-v1.json")));
                value["task8_harness_executable_sha256"] = new string('f', 64);
                File.WriteAllText(capability, value.ToString());
                AssertEx.Throws<InvalidDataException>(
                    () => S2AudioObserverProfile.LoadCapability(manifest, capability),
                    "capability file identity");
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
            AssertEx.Equal("bizhawk-2.11-gpgx-audio-observer-v2",
                identity.InstallationId);
            AssertEx.Equal("gpgx-audio-observer-v2", identity.CoreId);
            AssertEx.Equal(2, identity.AbiVersion);
            AssertEx.Equal("b49036a848890682", identity.BuildId);

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

        private static string Fixture(string name)
        {
            return Path.Combine(EndToEndTests.ToolDirectory, "fixtures", name);
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

        private sealed class FakeTraceApi : IGpgxAudioTraceApi
        {
            internal int ConfigureCalls;
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
            public int BeginPublicationEpoch() { return 0; }
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
