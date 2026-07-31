using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class LoadQueueStateEventTests
    {
        public static void Register(List<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "LoadQueueState fingerprints match Java golden vectors",
                FingerprintsMatchGoldenVectors));
            tests.Add(new TestMain.TestCase(
                "LoadQueueState formats canonical JSON",
                FormatsCanonicalJson));
            tests.Add(new TestMain.TestCase(
                "LoadQueueState rejects version 1 service observations",
                RejectsServiceObservations));
            tests.Add(new TestMain.TestCase(
                "LoadQueueState masks prepared S1 identity and fingerprints waiting work",
                MasksPreparedS1Identity));
            tests.Add(new TestMain.TestCase(
                "LoadQueueState masks prepared KosM cursor identity",
                MasksPreparedKosModuleIdentity));
            tests.Add(new TestMain.TestCase(
                "LoadQueueState keeps newly queued direct Kos unprepared",
                KeepsNewDirectKosUnprepared));
            tests.Add(new TestMain.TestCase(
                "LoadQueueState metadata capability is explicit opt in",
                MetadataCapabilityIsExplicitOptIn));
        }

        private static void FingerprintsMatchGoldenVectors()
        {
            AssertEqual(
                "e1cb5d156a023180550e107c0fa41e1de038683d4cbe8c6176b3395bb4dae2fa",
                LoadQueueStateEvent.Fingerprint(1, 0x123456, 0x345, 17));
            AssertEqual(
                "61fd12622bb980712702efbb2b9a3f0fd8daf5c85c78c2c51b8129ba3ef5907b",
                LoadQueueStateEvent.Fingerprint(2, 0x1000, 0x400, 32));
            AssertEqual(
                "1d3688513bf473d8934c182419593716b71a92a8c6d75bb48ae7b7f1dedbfa92",
                LoadQueueStateEvent.Fingerprint(3, 0x12233, 0xFF8000, null));
            AssertEqual(
                "a283ef4143ceb64b27d9190da9cf3d3739166307bc7ad8fff658545391bd7133",
                LoadQueueStateEvent.Fingerprint(4, 0x20000, 0x10000, 8));
        }

        private static void FormatsCanonicalJson()
        {
            string actual = LoadQueueStateEvent.Format(
                7, "s1_nemesis_plc", true, true, -1, -1, -1, 9,
                new string[0],
                new LoadQueueStateEvent.Observation[0]);
            AssertEqual(
                "{\"frame\":7,\"event\":\"load_queue_state\","
                + "\"kind\":\"s1_nemesis_plc\",\"busy\":true,"
                + "\"prepared\":true,\"active_source\":-1,"
                + "\"active_destination\":-1,\"total_work\":-1,"
                + "\"remaining_work\":9,\"queued_fingerprints\":[],"
                + "\"service_observations\":[]}",
                actual);
        }

        private static void RejectsServiceObservations()
        {
            try
            {
                LoadQueueStateEvent.Format(
                    7, "s1_nemesis_plc", true, true, -1, -1, -1, 9,
                    new string[0],
                    new[] {
                        new LoadQueueStateEvent.Observation("LEVEL_MAIN", 3)
                    });
                throw new InvalidOperationException(
                    "Non-empty version 1 observations were accepted");
            }
            catch (ArgumentException)
            {
                // Expected.
            }
        }

        private static void MasksPreparedS1Identity()
        {
            var rom = new byte[0x3000];
            rom[0x2000] = 0x00;
            rom[0x2001] = 0x11;
            var host = new RamBackedHost();
            host.SetWord(S1Ram.PlcPatternsLeft, 9);
            host.SetLong(S1Ram.PlcBuffer, 0x00001234);
            host.SetWord(S1Ram.PlcBuffer + 4, 0x0500);
            host.SetLong(S1Ram.PlcBuffer + S1Ram.PlcEntrySize, 0x00002000);
            host.SetWord(S1Ram.PlcBuffer + S1Ram.PlcEntrySize + 4, 0x0680);

            string actual = LoadQueueStateProjector.CaptureS1(3, rom, host);

            if (actual.IndexOf(
                    "\"prepared\":true,\"active_source\":-1,"
                    + "\"active_destination\":-1,\"total_work\":-1,"
                    + "\"remaining_work\":9",
                    StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Prepared identity was not masked: " + actual);
            }
            string expectedFingerprint = LoadQueueStateEvent.Fingerprint(
                1, 0x2000, 0x34, 17);
            if (actual.IndexOf(expectedFingerprint, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Waiting descriptor fingerprint was absent: " + actual);
            }
        }

        private static void MasksPreparedKosModuleIdentity()
        {
            var rom = new byte[0x4000];
            rom[0x3000] = 0x20;
            rom[0x3001] = 0x00;
            var host = new RamBackedHost();
            host.Ram[S3KRam.KosModulesLeft] = 0x02;
            host.SetLong(S3KRam.KosModuleQueue, 0x00001234);
            host.SetWord(S3KRam.KosModuleQueue + 4, 0x0500);
            host.SetLong(
                S3KRam.KosModuleQueue + S3KRam.KosModuleQueueEntrySize,
                0x00003000);
            host.SetWord(
                S3KRam.KosModuleQueue + S3KRam.KosModuleQueueEntrySize + 4,
                0x0600);

            IList<string> lines = LoadQueueStateProjector.CaptureS3k(
                4, rom, host);
            string module = lines[1];

            if (module.IndexOf(
                    "\"prepared\":true,\"active_source\":-1,"
                    + "\"active_destination\":-1,\"total_work\":-1,"
                    + "\"remaining_work\":2",
                    StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "KosM prepared identity was not masked: " + module);
            }
            if (module.IndexOf("\"prepared\":true", StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Installed KosM parent was mistaken for an unprepared slot: "
                    + module);
            }
            // The queued word IS the VRAM byte address (Queue_Kos_Module
            // stores tiles_to_bytes(toVRAMaddr) verbatim), so 0x0600 carries
            // through unscaled; it previously read 0xC000 = 0x0600 * 32.
            string waiting = LoadQueueStateEvent.Fingerprint(
                4, 0x3000, 0x0600, 2);
            if (module.IndexOf(waiting, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "KosM waiting fingerprint was absent: " + module);
            }
        }

        private static void KeepsNewDirectKosUnprepared()
        {
            var host = new RamBackedHost();
            host.SetWord(S3KRam.KosDecompQueueCount, 1);
            host.SetLong(S3KRam.KosDecompQueue, 0x00001234);
            host.SetLong(S3KRam.KosDecompQueue + 4, 0x00FF8000);

            string direct = LoadQueueStateProjector.CaptureS3k(
                4, new byte[2], host)[0];

            if (direct.IndexOf(
                    "\"busy\":true,\"prepared\":false",
                    StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "New POST_OBJECTS direct descriptor was armed early: "
                    + direct);
            }
        }

        private static void MetadataCapabilityIsExplicitOptIn()
        {
            string legacy = S1TraceMetadataWriter.Format(
                0, 0, 1, 2, 0x50, 0x3B0, 0, "2026-07-29");
            string enabled = S1TraceMetadataWriter.Format(
                0, 0, 1, 2, 0x50, 0x3B0, 0, "2026-07-29", true);
            if (legacy.IndexOf(
                    "load_queue_state_per_frame", StringComparison.Ordinal) >= 0)
            {
                throw new InvalidOperationException(
                    "Legacy metadata unexpectedly enabled queue state.");
            }
            if (enabled.IndexOf(
                    "load_queue_state_per_frame", StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Opt-in metadata omitted queue capability.");
            }
        }

        private static void AssertEqual(string expected, string actual)
        {
            if (!String.Equals(expected, actual, StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    "Expected <" + expected + "> but was <" + actual + ">.");
            }
        }
    }
}
