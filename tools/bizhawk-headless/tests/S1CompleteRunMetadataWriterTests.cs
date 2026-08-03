using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Strict-v5 assertions for the S1 complete-run level-segment metadata
    /// writer. Current in-memory inputs cover the GHZ1/FZ raw-ROM naming
    /// cases, the fixed recorder fields, absence of removed version fields,
    /// and the %q-parity guard on source_bk2 without transforming fixture
    /// data.
    /// </summary>
    internal static class S1CompleteRunMetadataWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunMetadataWriter asserts GHZ1 strict v5 metadata",
                AssertsGhz1StrictV5Metadata));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunMetadataWriter asserts FZ strict v5 metadata"
                + " (ROM sbz act 3)",
                AssertsFzStrictV5Metadata));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunMetadataWriter names SBZ3 as ROM lz act 4",
                NamesSbz3AsRomLzAct4));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunMetadataWriter renders unknown zones lowercase",
                RendersUnknownZonesLowercase));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunMetadataWriter rejects source_bk2 needing"
                + " escaping",
                RejectsSourceBk2NeedingEscaping));
        }

        /// <summary>
        /// GHZ1: zone ghz/0 act raw 0, offset 788, 5598 rows, start
        /// (0x0050, 0x03B0), rng 0. The fixed recording date makes the
        /// strict-v5 writer assertion deterministic.
        /// </summary>
        private static void AssertsGhz1StrictV5Metadata()
        {
            AssertStrictV5Metadata(
                S1CompleteRunMetadataWriter.Format(
                    0, 0, 788, 5598, 0x0050, 0x03B0, 0u,
                    "2026-07-30", "s1-complete-run.bk2", true));
        }

        /// <summary>
        /// Final Zone is ROM-encoded as SBZ act 3 (zone 5, act raw 2): the
        /// fz_completerun fixture carries zone sbz/5/3 with offset 189578
        /// and 4457 rows.
        /// </summary>
        private static void AssertsFzStrictV5Metadata()
        {
            AssertStrictV5Metadata(
                S1CompleteRunMetadataWriter.Format(
                    5, 2, 189578, 4457, 0x2140, 0x05AC, 0u,
                    "2026-07-30", "s1-complete-run.bk2", true));
        }

        /// <summary>
        /// SBZ3 is ROM-encoded as LZ act 4 (zone 1, act raw 3): the
        /// metadata must carry the raw ROM identity, never an sbz3 alias
        /// (the committed sbz3_completerun fixture dir was renamed by hand,
        /// not by the recorder).
        /// </summary>
        private static void NamesSbz3AsRomLzAct4()
        {
            string metadata = S1CompleteRunMetadataWriter.Format(
                1, 3, 181004, 8354, 0x0060, 0x0290, 0u,
                "2026-07-13", "s1-complete-run.bk2");
            AssertContains(metadata, "  \"zone\": \"lz\",\n");
            AssertContains(metadata, "  \"zone_id\": 1,\n");
            AssertContains(metadata, "  \"act\": 4,\n");
        }

        private static void RendersUnknownZonesLowercase()
        {
            string metadata = S1CompleteRunMetadataWriter.Format(
                0x0B, 0, 10, 5, 0, 0, 0u,
                "2026-07-13", "s1-complete-run.bk2");
            AssertContains(metadata, "  \"zone\": \"unknown_0b\",\n");
        }

        private static void RejectsSourceBk2NeedingEscaping()
        {
            try
            {
                S1CompleteRunMetadataWriter.Format(
                    0, 0, 0, 0, 0, 0, 0u, "2026-07-13", "we\"ird.bk2");
                throw new InvalidOperationException(
                    "Expected an ArgumentException for a source_bk2 that %q"
                    + " would escape.");
            }
            catch (ArgumentException)
            {
            }
        }

        /// <summary>
        /// Current strict-v5 output must use LF, carry the required recorder
        /// fields, and omit every removed version field.
        /// </summary>
        private static void AssertStrictV5Metadata(string produced)
        {
            AssertEx.Equal(false, produced.IndexOf('\r') >= 0);
            AssertEx.Equal(true, produced.EndsWith("\n"));
            AssertContains(
                produced,
                "  \"recorder\": \"native-bizhawk-headless\",\n"
                + "  \"recorder_version\": \"3.0\",\n"
                + "  \"trace_schema\": 5,\n");
            AssertEx.Equal(false, produced.Contains("lua_script_version"));
            AssertEx.Equal(false, produced.Contains("csv_version"));
            AssertEx.Equal(false, produced.Contains("hardware_timing_schema"));
        }

        private static void AssertContains(
            string value,
            string expectedFragment)
        {
            if (value.IndexOf(expectedFragment, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected text to contain <" + expectedFragment + ">.");
            }
        }
    }
}
