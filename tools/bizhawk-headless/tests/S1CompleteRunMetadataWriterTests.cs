using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Fixture-literal tests for the S1 complete-run level-segment
    /// metadata writer: byte comparison against the committed
    /// ghz1_completerun and fz_completerun fixtures (the fixtures are
    /// stamped lua_script_version 3.14; the current Lua stamps 3.18 and
    /// that line is the ONLY permitted delta beyond recording_date — spec
    /// docs/s1-complete-run-behavior.md section 2), plus the raw-ROM
    /// naming landmines and the %q-parity guard on source_bk2.
    /// </summary>
    internal static class S1CompleteRunMetadataWriterTests
    {
        private const string FixtureVersionLine =
            "  \"lua_script_version\": \"3.14\",";
        private const string ProducedVersionLine =
            "  \"lua_script_version\": \"3.18\",";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunMetadataWriter matches ghz1_completerun fixture"
                + " bytes",
                MatchesGhz1CompleteRunFixtureBytes));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunMetadataWriter matches fz_completerun fixture"
                + " bytes (ROM sbz act 3)",
                MatchesFzCompleteRunFixtureBytes));
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
        /// (0x0050, 0x03B0), rng 0. The fixture's recording_date is passed
        /// verbatim so the version line is the single expected delta.
        /// </summary>
        private static void MatchesGhz1CompleteRunFixtureBytes()
        {
            AssertMatchesFixtureExceptVersion(
                "ghz1_completerun",
                S1CompleteRunMetadataWriter.Format(
                    0, 0, 788, 5598, 0x0050, 0x03B0, 0u,
                    "2026-07-13", "s1-complete-run.bk2"));
        }

        /// <summary>
        /// Final Zone is ROM-encoded as SBZ act 3 (zone 5, act raw 2): the
        /// fz_completerun fixture carries zone sbz/5/3 with offset 189578
        /// and 4457 rows.
        /// </summary>
        private static void MatchesFzCompleteRunFixtureBytes()
        {
            AssertMatchesFixtureExceptVersion(
                "fz_completerun",
                S1CompleteRunMetadataWriter.Format(
                    5, 2, 189578, 4457, 0x2140, 0x05AC, 0u,
                    "2026-07-13", "s1-complete-run.bk2"));
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
        /// Line-by-line byte comparison against a committed fixture's
        /// metadata.json: every line must be identical except the version
        /// line, which must be exactly the 3.14 fixture stamp on one side
        /// and the 3.18 native stamp on the other. No other normalization.
        /// </summary>
        private static void AssertMatchesFixtureExceptVersion(
            string fixtureDirectoryName,
            string produced)
        {
            string fixturePath = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src", "test", "resources", "traces", "s1",
                fixtureDirectoryName,
                "metadata.json");
            string fixtureText = File.ReadAllText(fixturePath);

            AssertEx.Equal(false, fixtureText.IndexOf('\r') >= 0);
            AssertEx.Equal(false, produced.IndexOf('\r') >= 0);
            AssertEx.Equal(true, fixtureText.EndsWith("\n"));
            AssertEx.Equal(true, produced.EndsWith("\n"));

            string[] fixtureLines = fixtureText.Split('\n');
            string[] producedLines = produced.Split('\n');
            AssertEx.Equal(fixtureLines.Length, producedLines.Length);
            var versionLines = 0;
            for (var index = 0; index < fixtureLines.Length; index++)
            {
                if (fixtureLines[index] == FixtureVersionLine)
                {
                    versionLines++;
                    AssertEx.Equal(
                        ProducedVersionLine, producedLines[index]);
                }
                else
                {
                    AssertEx.Equal(fixtureLines[index], producedLines[index]);
                }
            }
            AssertEx.Equal(1, versionLines);
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
