using System;
using System.Security.Cryptography;

namespace OpenGGF.BizHawk.Headless
{
    public static class RomIdentity
    {
        public const string Sonic1Rev01Sha1 =
            "69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B";

        public static string ValidateSonic1Rev01(byte[] romBytes)
        {
            if (romBytes == null)
            {
                throw new ArgumentNullException("romBytes");
            }

            string actualSha1;
            using (SHA1 sha1 = SHA1.Create())
            {
                actualSha1 = BitConverter.ToString(sha1.ComputeHash(romBytes))
                    .Replace("-", "");
            }

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
    }
}
