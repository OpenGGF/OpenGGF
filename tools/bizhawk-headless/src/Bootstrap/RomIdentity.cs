using System;
using System.Security.Cryptography;

namespace OpenGGF.BizHawk.Headless
{
    public static class RomIdentity
    {
        public const string Sonic1Rev01Sha1 =
            "69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B";

        public const string Sonic2Rev01Sha1 =
            "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9";

        public static string ValidateSonic1Rev01(byte[] romBytes)
        {
            string actualSha1 = ComputeSha1(romBytes);
            if (!string.Equals(
                Sonic1Rev01Sha1,
                actualSha1,
                StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    "Sonic 1 World REV01 ROM SHA-1 was " + actualSha1
                    + "; expected " + Sonic1Rev01Sha1 + ".");
            }
            return actualSha1;
        }

        public static string ValidateSonic2Rev01(byte[] romBytes)
        {
            string actualSha1 = ComputeSha1(romBytes);
            if (!string.Equals(
                Sonic2Rev01Sha1,
                actualSha1,
                StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    "Sonic 2 World REV01 ROM SHA-1 was " + actualSha1
                    + "; expected " + Sonic2Rev01Sha1 + ".");
            }
            return actualSha1;
        }

        /// <summary>
        /// Detects the trace game from the ROM's SHA-1: "s1" for Sonic 1
        /// World REV01, "s2" for Sonic 2 World REV01. The CLI dispatches on
        /// this instead of a game flag so a supplied ROM always selects the
        /// matching recorder pipeline.
        /// </summary>
        public static string DetectGame(byte[] romBytes)
        {
            string actualSha1 = ComputeSha1(romBytes);
            if (string.Equals(
                Sonic1Rev01Sha1, actualSha1, StringComparison.Ordinal))
            {
                return "s1";
            }
            if (string.Equals(
                Sonic2Rev01Sha1, actualSha1, StringComparison.Ordinal))
            {
                return "s2";
            }
            throw new InvalidOperationException(
                "ROM SHA-1 " + actualSha1 + " is not a supported trace ROM;"
                + " expected Sonic 1 World REV01 (" + Sonic1Rev01Sha1
                + ") or Sonic 2 World REV01 (" + Sonic2Rev01Sha1 + ").");
        }

        public static string ComputeSha1(byte[] romBytes)
        {
            if (romBytes == null)
            {
                throw new ArgumentNullException("romBytes");
            }

            using (SHA1 sha1 = SHA1.Create())
            {
                return BitConverter.ToString(sha1.ComputeHash(romBytes))
                    .Replace("-", "");
            }
        }
    }
}
