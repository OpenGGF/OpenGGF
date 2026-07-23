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

        private static byte[] ReadSonic1Rom()
        {
            string romPath = Environment.GetEnvironmentVariable("S1_ROM_PATH");
            if (string.IsNullOrEmpty(romPath) || !File.Exists(romPath))
            {
                throw new InvalidOperationException(
                    "S1_ROM_PATH must identify the Sonic 1 World REV01 ROM.");
            }
            return File.ReadAllBytes(romPath);
        }
    }
}
