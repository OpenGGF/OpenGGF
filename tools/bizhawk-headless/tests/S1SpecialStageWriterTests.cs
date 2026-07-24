using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Literal-byte tests for the S1 run-mode special-stage writers: the
    /// 14-column ss physics.csv row format (frame/lag decimal, everything
    /// else lowercase unpadded hex, u32 x/y, unsigned u16 velocities) and
    /// the distinct ss metadata.json shape (solo-Sonic hardcoded
    /// characters, %q source_bk2, raw optional run_id, segment_index
    /// last). The metadata test reproduces the canonical run fixture's
    /// ss/metadata.json bytes with the session values injected, and pins
    /// the standalone special_stage/ fixture as a byte-identical copy of
    /// the same segment (spec s1-run-mode-behavior.md §11 — there is no
    /// separate standalone writer).
    /// </summary>
    internal static class S1SpecialStageWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1SpecialStage csv header matches the recorder's 14 columns",
                CsvHeaderMatchesRecorder));
            tests.Add(new TestMain.TestCase(
                "S1SpecialStage csv row reproduces the ss fixture row 0",
                CsvRowReproducesSsFixtureRowZero));
            tests.Add(new TestMain.TestCase(
                "S1SpecialStage metadata reproduces the ss fixture bytes",
                MetadataReproducesSsFixtureBytes));
            tests.Add(new TestMain.TestCase(
                "S1SpecialStage metadata omits run_id when no run id was set",
                MetadataOmitsRunIdWhenNoRunIdWasSet));
        }

        private static void CsvHeaderMatchesRecorder()
        {
            AssertEx.Equal(
                "frame,input,lag,x_pos,y_pos,vel_x,vel_y,inertia,status,"
                + "ss_angle,ss_rotate,bg_anim,rings,emeralds",
                S1SpecialStageCsvWriter.Header);
            AssertEx.Equal(
                14,
                S1SpecialStageCsvWriter.Header.Split(',').Length);
        }

        private static void CsvRowReproducesSsFixtureRowZero()
        {
            // Canonical ss fixture row 0:
            // 0,0,0,25ab0300,44d8300,ffde,48,13c,7,0,0,0,55,0
            var host = new RamBackedHost();
            host.SetLong(0xD008, 0x25AB0300);   // x_pos u32 (pixel ++ sub)
            host.SetLong(0xD00C, 0x044D8300);   // y_pos u32 -> "44d8300"
            host.SetWord(0xD010, 0xFFDE);       // vel_x unsigned -> "ffde"
            host.SetWord(0xD012, 0x0048);       // vel_y
            host.SetWord(0xD014, 0x013C);       // inertia
            host.Ram[0xD022] = 0x07;            // status
            host.SetWord(0xF780, 0x0000);       // ss_angle
            host.SetWord(0xF782, 0x0000);       // ss_rotate
            host.SetWord(0xF7A0, 0x0000);       // bg_anim
            host.SetWord(0xFE20, 0x0055);       // rings (85 -> hex "55")
            host.Ram[0xFE57] = 0x00;            // emeralds

            AssertEx.Equal(
                "0,0,0,25ab0300,44d8300,ffde,48,13c,7,0,0,0,55,0",
                S1SpecialStageCsvWriter.FormatRow(0, 0, false, host));

            // frame decimal, input lowercase hex, lag decimal.
            host.SetWord(0xFE20, 0x0056);
            AssertEx.Equal(
                "12,18,1,25ab0300,44d8300,ffde,48,13c,7,0,0,0,56,0",
                S1SpecialStageCsvWriter.FormatRow(12, 0x18, true, host));
        }

        private static void MetadataReproducesSsFixtureBytes()
        {
            string produced = S1SpecialStageMetadataWriter.Format(
                0,
                4957,
                3091,
                "s1-ghz-maze-roundtrip.bk2",
                "3.15",
                "2026-07-19",
                "s1-ghz-maze-roundtrip",
                1);

            string runsDir = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src", "test", "resources", "traces", "s1");
            string ssFixture = File.ReadAllText(Path.Combine(
                runsDir, "runs", "s1-ghz-maze-roundtrip", "ss",
                "metadata.json"));
            AssertEx.Equal(ssFixture.Replace("\r\n", "\n"), produced);

            // The standalone special_stage/ fixture is a published copy of
            // the same run segment — identical bytes, same writer.
            string standaloneFixture = File.ReadAllText(Path.Combine(
                runsDir, "special_stage", "metadata.json"));
            AssertEx.Equal(ssFixture, standaloneFixture);
        }

        private static void MetadataOmitsRunIdWhenNoRunIdWasSet()
        {
            string produced = S1SpecialStageMetadataWriter.Format(
                3, 100, 200, "movie.bk2", "3.17", "2026-07-24", null, 2);
            AssertEx.Equal(false, produced.Contains("run_id"));
            AssertEx.Equal(
                true,
                produced.Contains(
                    "  \"recording_date\": \"2026-07-24\",\n"
                    + "  \"fresh_load\": false,\n"
                    + "  \"segment_index\": 2\n"
                    + "}\n"));
            AssertEx.Equal(
                true,
                produced.Contains("  \"characters\": [\"sonic\"],\n"));
            AssertEx.Equal(
                true,
                produced.Contains("  \"sidekicks\": [],\n"));
        }
    }
}
