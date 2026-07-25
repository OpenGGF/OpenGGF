using System;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Where a ROM-backed gate puts the capture it is about to compare.
    ///
    /// Not <see cref="Path.GetTempPath"/>. A single capture of the S1
    /// complete-run movie materializes about 1.6 GB of decompressed
    /// fixture and capture bytes, and <c>/tmp</c> is frequently a RAM-backed
    /// tmpfs — on the development box a 16 GiB one shared with everything
    /// else on the machine. Running four gates at once there produced
    /// ENOSPC inside three separate captures, which the gates correctly
    /// reported as failures: a full disk is indistinguishable from a
    /// recorder that stopped early, and the gate must not guess.
    ///
    /// The two S3K complete-run gates already reached this conclusion
    /// independently (2.84 GB of scratch each); this is that decision
    /// applied to every gate rather than to the two largest. The directory
    /// sits beside the existing <c>bin/</c> and <c>obj/</c> build scratch,
    /// is covered by the repository's <c>tools/*</c> ignore rule, and each
    /// gate deletes its own root in a finally block, so peak usage is what
    /// is concurrently running rather than what the process has ever run.
    /// </summary>
    internal static class TestScratch
    {
        /// <summary>
        /// A fresh, unique, not-yet-created scratch root for one test.
        /// The caller owns it and must delete it in a finally block.
        /// </summary>
        internal static string CreateRootPath(string prefix)
        {
            string scratch =
                Path.Combine(EndToEndTests.ToolDirectory, ".scratch");
            // The container, never the root itself: several gates pass
            // their root's child as --output, which the CLI requires not
            // to exist.
            Directory.CreateDirectory(scratch);
            return Path.Combine(
                scratch,
                prefix + "-" + Guid.NewGuid().ToString("N"));
        }
    }
}
