using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class BootstrapTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "BizHawk installation accepts pinned distribution",
                AcceptsPinnedDistribution));
            tests.Add(new TestMain.TestCase(
                "BizHawk installation reports missing GPGX core",
                ReportsMissingGpgxCore));
            tests.Add(new TestMain.TestCase(
                "ROM identity accepts Sonic 1 REV01",
                AcceptsSonic1Rev01));
            tests.Add(new TestMain.TestCase(
                "ROM identity reports mutated SHA-1",
                ReportsMutatedSha1));
            // Clears S1_ROM_PATH out of the process environment and puts
            // it back. Any test resolving a ROM in that window — and
            // every capture child process, which inherits the block —
            // would see it missing and skip, so this one runs alone.
            tests.Add(new TestMain.TestCase(
                "ROM identity skips only when S1_ROM_PATH is absent",
                SkipsOnlyWhenSonic1RomPathIsAbsent,
                game: "s1",
                serial: true));
            tests.Add(new TestMain.TestCase(
                "ROM identity accepts Sonic 2 REV01",
                AcceptsSonic2Rev01));
            tests.Add(new TestMain.TestCase(
                "ROM identity reports mutated Sonic 2 SHA-1",
                ReportsMutatedSonic2Sha1));
            tests.Add(new TestMain.TestCase(
                "ROM identity accepts Sonic 3 & Knuckles locked-on",
                AcceptsSonic3kLockOn));
            tests.Add(new TestMain.TestCase(
                "ROM identity reports mutated Sonic 3 & Knuckles SHA-1",
                ReportsMutatedSonic3kSha1));
            // Same hazard as the S1 case above: it unsets S3K_ROM_PATH
            // process-wide for the duration.
            tests.Add(new TestMain.TestCase(
                "ROM identity skips only when S3K_ROM_PATH is absent",
                SkipsOnlyWhenSonic3kRomPathIsAbsent,
                game: "s3k",
                serial: true));
            tests.Add(new TestMain.TestCase(
                "ROM identity detects supported games by SHA-1",
                DetectsSupportedGamesBySha1));
            tests.Add(new TestMain.TestCase(
                "ROM identity detects Sonic 3 & Knuckles by SHA-1",
                DetectsSonic3kBySha1));
            tests.Add(new TestMain.TestCase(
                "ROM identity rejects unknown ROM in game detection",
                RejectsUnknownRomInGameDetection));
            tests.Add(new TestMain.TestCase(
                "S1 credits pre-capture registry selects only safe evidence",
                CreditsPreCaptureRegistrySelectsOnlySafeEvidence));
        }

        private static void AcceptsPinnedDistribution()
        {
            string root = Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            BizHawkInstallation install = BizHawkInstallation.Validate(root);
            AssertEx.Equal(new Version(2, 11, 0, 0), install.ManagedVersion);
            AssertEx.Equal(Path.Combine(root, "dll"), install.DllDirectory);
        }

        private static void ReportsMissingGpgxCore()
        {
            string missingRoot = Path.Combine(
                Path.GetTempPath(),
                "openggf-bizhawk-missing-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(missingRoot);
            try
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => BizHawkInstallation.Validate(missingRoot),
                    "gpgx.wbx.zst");
            }
            finally
            {
                Directory.Delete(missingRoot, true);
            }
        }

        private static void AcceptsSonic1Rev01()
        {
            byte[] rom = ReadSonic1Rom();
            AssertEx.Equal(
                "69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B",
                RomIdentity.ValidateSonic1Rev01(rom));
        }

        private static void ReportsMutatedSha1()
        {
            byte[] rom = ReadSonic1Rom();
            rom[rom.Length / 2] ^= 0x01;
            string actualSha1;
            using (SHA1 sha1 = SHA1.Create())
            {
                actualSha1 = BitConverter.ToString(sha1.ComputeHash(rom)).Replace("-", "");
            }

            AssertEx.Throws<InvalidOperationException>(
                () => RomIdentity.ValidateSonic1Rev01(rom),
                actualSha1);
        }

        private static void SkipsOnlyWhenSonic1RomPathIsAbsent()
        {
            string original = Environment.GetEnvironmentVariable("S1_ROM_PATH");
            string missingPath = Path.Combine(
                Path.GetTempPath(),
                "openggf-missing-rom-" + Guid.NewGuid().ToString("N") + ".gen");
            try
            {
                Environment.SetEnvironmentVariable("S1_ROM_PATH", null);
                AssertEx.Throws<TestMain.SkipTestException>(
                    () => ReadSonic1Rom(),
                    "S1_ROM_PATH is not set");

                Environment.SetEnvironmentVariable("S1_ROM_PATH", missingPath);
                AssertEx.Throws<InvalidOperationException>(
                    () => ReadSonic1Rom(),
                    "does not exist");
            }
            finally
            {
                Environment.SetEnvironmentVariable("S1_ROM_PATH", original);
            }
        }

        private static void AcceptsSonic2Rev01()
        {
            byte[] rom = ReadSonic2Rom();
            AssertEx.Equal(
                "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9",
                RomIdentity.ValidateSonic2Rev01(rom));
        }

        private static void ReportsMutatedSonic2Sha1()
        {
            byte[] rom = ReadSonic2Rom();
            rom[rom.Length / 2] ^= 0x01;
            string actualSha1 = RomIdentity.ComputeSha1(rom);
            AssertEx.Throws<InvalidOperationException>(
                () => RomIdentity.ValidateSonic2Rev01(rom),
                actualSha1);
        }

        private static void AcceptsSonic3kLockOn()
        {
            byte[] rom = ReadSonic3kRom();
            AssertEx.Equal(
                "CFBF98C36C776677290A872547AC47C53D2761D6",
                RomIdentity.ValidateSonic3kLockOn(rom));
        }

        private static void ReportsMutatedSonic3kSha1()
        {
            byte[] rom = ReadSonic3kRom();
            rom[rom.Length / 2] ^= 0x01;
            string actualSha1 = RomIdentity.ComputeSha1(rom);
            AssertEx.Throws<InvalidOperationException>(
                () => RomIdentity.ValidateSonic3kLockOn(rom),
                actualSha1);
        }

        private static void SkipsOnlyWhenSonic3kRomPathIsAbsent()
        {
            string original =
                Environment.GetEnvironmentVariable("S3K_ROM_PATH");
            string missingPath = Path.Combine(
                Path.GetTempPath(),
                "openggf-missing-rom-" + Guid.NewGuid().ToString("N") + ".gen");
            try
            {
                Environment.SetEnvironmentVariable("S3K_ROM_PATH", null);
                AssertEx.Throws<TestMain.SkipTestException>(
                    () => ReadSonic3kRom(),
                    "S3K_ROM_PATH is not set");

                Environment.SetEnvironmentVariable("S3K_ROM_PATH", missingPath);
                AssertEx.Throws<InvalidOperationException>(
                    () => ReadSonic3kRom(),
                    "does not exist");
            }
            finally
            {
                Environment.SetEnvironmentVariable("S3K_ROM_PATH", original);
            }
        }

        private static void DetectsSupportedGamesBySha1()
        {
            AssertEx.Equal("s2", RomIdentity.DetectGame(ReadSonic2Rom()));
            AssertEx.Equal("s1", RomIdentity.DetectGame(ReadSonic1Rom()));
        }

        private static void DetectsSonic3kBySha1()
        {
            AssertEx.Equal("s3k", RomIdentity.DetectGame(ReadSonic3kRom()));
        }

        private static void RejectsUnknownRomInGameDetection()
        {
            byte[] unknown = new byte[] { 0x01, 0x02, 0x03, 0x04 };
            AssertEx.Throws<InvalidOperationException>(
                () => RomIdentity.DetectGame(unknown),
                "not a supported trace ROM");
            AssertEx.Throws<InvalidOperationException>(
                () => RomIdentity.DetectGame(unknown),
                RomIdentity.ComputeSha1(unknown));
        }

        private static void CreditsPreCaptureRegistrySelectsOnlySafeEvidence()
        {
            var credits = new List<TestMain.TestCase>();
            S1CreditsDemoDifferentialTests.RegisterPreCapture(credits);

            AssertEx.Equal(3, credits.Count);
            AssertEx.Equal(
                "S1 credits predecessor evidence keeps eight 20-column fixtures\n"
                + "S1 credits raw-host evidence is independent and hash-bound\n"
                + "S1 credits captures twice with deterministic logical evidence",
                string.Join("\n", credits.ConvertAll(item => item.Name).ToArray()));
            AssertEx.Equal(TestKind.Unit, credits[0].Kind);
            AssertEx.Equal(TestKind.Unit, credits[1].Kind);
            AssertEx.Equal(TestKind.Gate, credits[2].Kind);
            AssertEx.Equal(true, credits[2].Serial);
            foreach (TestMain.TestCase test in credits)
            {
                AssertEx.Equal(false,
                    test.Name.IndexOf("candidate", StringComparison.OrdinalIgnoreCase) >= 0);
                AssertEx.Equal(false,
                    test.Name.IndexOf("diagnostic", StringComparison.OrdinalIgnoreCase) >= 0);
            }

            List<TestMain.TestCase> registry = TestMain.BuildRegistry();
            foreach (TestMain.TestCase expected in credits)
            {
                AssertEx.Equal(1, registry.FindAll(
                    item => item.Name == expected.Name).Count);
            }
            AssertEx.Equal(0, registry.FindAll(item =>
                item.Name == "S1 credits native candidate preserves every predecessor column"
                || item.Name == "S1 credits diagnostic candidate reports literal common-field deltas").Count);
        }

        private static byte[] ReadSonic2Rom()
        {
            string romPath = Environment.GetEnvironmentVariable("S2_ROM_PATH");
            if (string.IsNullOrEmpty(romPath))
            {
                throw new TestMain.SkipTestException(
                    "S2_ROM_PATH is not set.");
            }
            if (!File.Exists(romPath))
            {
                throw new InvalidOperationException(
                    "Supplied S2_ROM_PATH does not exist: " + romPath + ".");
            }
            return File.ReadAllBytes(romPath);
        }

        private static byte[] ReadSonic3kRom()
        {
            string romPath =
                Environment.GetEnvironmentVariable("S3K_ROM_PATH");
            if (string.IsNullOrEmpty(romPath))
            {
                throw new TestMain.SkipTestException(
                    "S3K_ROM_PATH is not set.");
            }
            if (!File.Exists(romPath))
            {
                throw new InvalidOperationException(
                    "Supplied S3K_ROM_PATH does not exist: " + romPath + ".");
            }
            return File.ReadAllBytes(romPath);
        }

        private static byte[] ReadSonic1Rom()
        {
            string romPath = Environment.GetEnvironmentVariable("S1_ROM_PATH");
            if (string.IsNullOrEmpty(romPath))
            {
                throw new TestMain.SkipTestException(
                    "S1_ROM_PATH is not set.");
            }
            if (!File.Exists(romPath))
            {
                throw new InvalidOperationException(
                    "Supplied S1_ROM_PATH does not exist: " + romPath + ".");
            }
            return File.ReadAllBytes(romPath);
        }
    }
}
