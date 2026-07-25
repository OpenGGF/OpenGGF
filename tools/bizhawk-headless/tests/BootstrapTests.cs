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
            tests.Add(new TestMain.TestCase(
                "ROM identity skips only when S1_ROM_PATH is absent",
                SkipsOnlyWhenSonic1RomPathIsAbsent));
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
            tests.Add(new TestMain.TestCase(
                "ROM identity skips only when S3K_ROM_PATH is absent",
                SkipsOnlyWhenSonic3kRomPathIsAbsent));
            tests.Add(new TestMain.TestCase(
                "ROM identity detects supported games by SHA-1",
                DetectsSupportedGamesBySha1));
            tests.Add(new TestMain.TestCase(
                "ROM identity detects Sonic 3 & Knuckles by SHA-1",
                DetectsSonic3kBySha1));
            tests.Add(new TestMain.TestCase(
                "ROM identity rejects unknown ROM in game detection",
                RejectsUnknownRomInGameDetection));
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
