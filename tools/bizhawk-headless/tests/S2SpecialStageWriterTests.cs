using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Literal-byte tests for the run-mode special-stage writers: the
    /// 48-column ss physics.csv row format (frame/lag decimal, everything
    /// else lowercase unpadded hex, zeroed blocks for absent characters,
    /// Start included in the input mask) and the distinct ss metadata.json
    /// shape (hardcoded characters, raw run_id, segment_index last).
    /// </summary>
    internal static class S2SpecialStageWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2SpecialStage csv header matches the recorder's 48 columns",
                CsvHeaderMatchesRecorder));
            tests.Add(new TestMain.TestCase(
                "S2SpecialStage csv row uses lowercase hex and zeroed absentees",
                CsvRowUsesLowercaseHexAndZeroedAbsentees));
            tests.Add(new TestMain.TestCase(
                "S2SpecialStage input mask collapses ABC and includes Start",
                InputMaskCollapsesAbcAndIncludesStart));
            tests.Add(new TestMain.TestCase(
                "S2SpecialStage metadata matches the ss fixture byte layout",
                MetadataMatchesSsFixtureByteLayout));
        }

        private static void CsvHeaderMatchesRecorder()
        {
            AssertEx.Equal(
                "frame,input,input_p2,lag,speed_factor,track_anim,"
                + "track_anim_frame,track_drawing_index,track_orientation,"
                + "track_duration_timer,current_segment,"
                + "player_anim_frame_timer,rings_togo_bcd,check_rings_flag,"
                + "tails_control_counter,swap_positions_flag,sonic_present,"
                + "sonic_ss_x,sonic_ss_x_sub,sonic_ss_y,sonic_ss_y_sub,"
                + "sonic_ss_z,sonic_angle,sonic_routine,"
                + "sonic_routine_secondary,sonic_status,sonic_anim,"
                + "sonic_anim_frame,sonic_rings_bcd,sonic_hurt_timer,"
                + "sonic_slide_timer,sonic_flip_timer,tails_present,"
                + "tails_ss_x,tails_ss_x_sub,tails_ss_y,tails_ss_y_sub,"
                + "tails_ss_z,tails_angle,tails_routine,"
                + "tails_routine_secondary,tails_status,tails_anim,"
                + "tails_anim_frame,tails_rings_bcd,tails_hurt_timer,"
                + "tails_slide_timer,tails_flip_timer",
                S2SpecialStageCsvWriter.Header);
            AssertEx.Equal(
                48,
                S2SpecialStageCsvWriter.Header.Split(',').Length);
        }

        private static void CsvRowUsesLowercaseHexAndZeroedAbsentees()
        {
            var host = new RamBackedHost();
            // Shared track state.
            host.SetWord(0xDB16, 0x000C);       // speed_factor -> "c"
            host.Ram[0xDB08] = 0x03;            // track_anim
            host.Ram[0xDB0B] = 0x0D;            // track_anim_frame -> "d"
            host.Ram[0xDB0D] = 0x02;            // track_drawing_index
            host.Ram[0xDB0E] = 0x00;            // track_orientation
            host.Ram[0xDB1F] = 0x2B;            // track_duration_timer
            host.Ram[0xDB0A] = 0x04;            // current_segment
            host.Ram[0xDB21] = 0x00;            // player_anim_frame_timer
            host.SetWord(0xDBA4, 0x0000);       // rings_togo_bcd
            host.Ram[0xDB86] = 0xFF;            // check_rings_flag -> "ff"
            host.SetWord(0xF702, 0x0000);       // tails_control_counter
            host.Ram[0xF742] = 0x00;            // swap_positions_flag

            // Sonic present (id 0x09) with the fixture row 0's values.
            host.Ram[0xB000] = 0x09;
            host.SetWord(0xB000 + 0x2A, 0x0000);    // ss_x
            host.SetWord(0xB000 + 0x2C, 0x0004);    // ss_x_sub
            host.SetWord(0xB000 + 0x2E, 0x0000);    // ss_y
            host.SetWord(0xB000 + 0x30, 0x0000);    // ss_y_sub
            host.SetWord(0xB000 + 0x34, 0x0000);    // ss_z
            host.Ram[0xB000 + 0x26] = 0x00;         // angle
            host.Ram[0xB000 + 0x24] = 0x02;         // routine
            host.Ram[0xB000 + 0x25] = 0x00;         // routine_secondary
            host.Ram[0xB000 + 0x22] = 0x06;         // status
            host.Ram[0xB000 + 0x1C] = 0x02;         // anim
            host.Ram[0xB000 + 0x1B] = 0x02;         // anim_frame
            host.Ram[0xB000 + 0x3C] = 0x01;         // rings hundreds
            host.Ram[0xB000 + 0x3D] = 0x18;         // rings tens
            host.Ram[0xB000 + 0x3E] = 0x0C;         // rings units
            host.Ram[0xB000 + 0x36] = 0xF8;         // hurt_timer -> "f8"
            host.Ram[0xB000 + 0x37] = 0x08;         // slide_timer
            host.Ram[0xB000 + 0x33] = 0x00;         // flip_timer
            // Tails slot left empty (id 0): the whole block renders zeroes.

            AssertEx.Equal(
                "7,10,0,1,c,3,d,2,0,2b,4,0,0,ff,0,0,"
                + "1,0,4,0,0,0,0,2,0,6,2,2,1180c,f8,8,0,"
                + "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
                S2SpecialStageCsvWriter.FormatRow(7, 0x10, 0, true, host));
        }

        private static void InputMaskCollapsesAbcAndIncludesStart()
        {
            var frame = new Bk2Frame
            {
                P1Up = true,
                P1Right = true,
                P1B = true,
                P1Start = true
            };
            AssertEx.Equal(
                0x01 | 0x08 | 0x10 | 0x80,
                S2SpecialStageCsvWriter.InputMask(frame));

            // Start alone contributes only 0x80; no A/B/C means no 0x10
            // (unlike the level writer, which drops Start entirely).
            AssertEx.Equal(
                0x80,
                S2SpecialStageCsvWriter.InputMask(
                    new Bk2Frame { P1Start = true }));
        }

        private static void MetadataMatchesSsFixtureByteLayout()
        {
            // Values from the canonical run fixture's ss/metadata.json.
            AssertEx.Equal(
                "{\n"
                + "  \"game\": \"s2\",\n"
                + "  \"trace_profile\": \"s2_special_stage\",\n"
                + "  \"special_stage_index\": 0,\n"
                + "  \"ss_csv_version\": 1,\n"
                + "  \"characters\": [\"sonic\", \"tails\"],\n"
                + "  \"main_character\": \"sonic\",\n"
                + "  \"sidekicks\": [\"tails\"],\n"
                + "  \"bk2_frame_offset\": 3795,\n"
                + "  \"trace_frame_count\": 5733,\n"
                + "  \"source_bk2\": \"s2-ehz-halfpipe-roundtrip.bk2\",\n"
                + "  \"lua_script_version\": \"9.13-s2\",\n"
                + "  \"recording_date\": \"2026-07-19\",\n"
                + "  \"run_id\": \"s2-ehz-halfpipe-roundtrip\",\n"
                + "  \"fresh_load\": false,\n"
                + "  \"segment_index\": 1\n"
                + "}\n",
                S2SpecialStageMetadataWriter.Format(
                    0,
                    3795,
                    5733,
                    "s2-ehz-halfpipe-roundtrip.bk2",
                    "2026-07-19",
                    "s2-ehz-halfpipe-roundtrip",
                    1));
        }
    }
}
