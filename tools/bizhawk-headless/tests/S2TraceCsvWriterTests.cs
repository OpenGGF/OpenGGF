using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S2TraceCsvWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2TraceCsvWriter emits the current header contract",
                HeaderMatchesCurrentContract));
            tests.Add(new TestMain.TestCase(
                "S2TraceCsvWriter emits the current EHZ1 row-zero contract",
                EmitsEhz1RowZeroContract));
            tests.Add(new TestMain.TestCase(
                "S2TraceCsvWriter renders absent sidekick as constant zero block",
                RendersAbsentSidekickAsConstantZeroBlock));
            tests.Add(new TestMain.TestCase(
                "S2TraceCsvWriter renders negative speeds for both characters",
                RendersNegativeSpeedsForBothCharacters));
            tests.Add(new TestMain.TestCase(
                "S2TraceCsvWriter derives sidekick air and ground mode from own state",
                DerivesSidekickAirAndGroundModeFromOwnState));
        }

        /// <summary>
        /// RAM state reproducing data row 0 of
        /// src/test/resources/traces/s2/ehz1_fullrun/physics.csv.
        /// </summary>
        internal static RamBackedHost BuildEhz1RowZeroHost()
        {
            var host = new RamBackedHost();
            host.SetWord(S2Ram.CameraY, 0x0230);
            host.SetWord(S2Ram.FrameCount, 0x0001);
            host.SetWord(S2Ram.VblankWord, 0x02AC);
            // Sonic (slot 0).
            host.Ram[S2Ram.PlayerBase + S2Ram.OffId] = 0x01;
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffXPos, 0x0060);
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffYPos, 0x0290);
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffXVel, 0x000C);
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffInertia, 0x000C);
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffXSub, 0x0C00);
            host.Ram[S2Ram.PlayerBase + S2Ram.OffRoutine] = 0x02;
            host.Ram[S2Ram.PlayerBase + S2Ram.OffMappingFrame] = 0x0F;
            // Tails (slot 1) — the S2 sidekick block is live symmetric reads.
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x02;
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffXPos, 0x004D);
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffYPos, 0x0294);
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffXVel, 0x0084);
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffInertia, 0x0084);
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffXSub, 0x1800);
            host.Ram[S2Ram.SidekickBase + S2Ram.OffRoutine] = 0x02;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffMappingFrame] = 0x11;
            return host;
        }

        private static void HeaderMatchesCurrentContract()
        {
            // LITERAL header line of
            // src/test/resources/traces/s2/ehz1_fullrun/physics.csv
            // (byte-identical to the S1 v7 header).
            AssertEx.Equal(
                "frame,input,camera_x,camera_y,rings,gameplay_frame_counter,"
                + "vblank_counter,lag_counter,player_present,player_x,player_y,"
                + "player_x_speed,player_y_speed,player_g_speed,player_angle,"
                + "player_air,player_rolling,player_ground_mode,player_x_sub,"
                + "player_y_sub,player_routine,player_status_byte,"
                + "player_stand_on_obj,player_animation_id,player_mapping_frame,"
                + "sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,"
                + "sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,"
                + "sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,"
                + "sidekick_y_sub,sidekick_routine,sidekick_status_byte,"
                + "sidekick_stand_on_obj,sidekick_animation_id,"
                + "sidekick_mapping_frame",
                S2TraceCsvWriter.Header);
        }

        private static void EmitsEhz1RowZeroContract()
        {
            RamBackedHost host = BuildEhz1RowZeroHost();

            // LITERAL data row 0 of
            // src/test/resources/traces/s2/ehz1_fullrun/physics.csv,
            // including the live Tails block (present=1, x=0x004D, ...).
            AssertEx.Equal(
                "0000,0008,0000,0230,0000,0001,02AC,0000,1,0060,0290,000C,0000,"
                + "000C,00,0,0,0,0C00,0000,02,00,00,00,0F,1,004D,0294,0084,0000,"
                + "0084,00,0,0,0,1800,0000,02,00,00,00,11",
                S2TraceCsvWriter.FormatRow(0, 0x0008, host));
        }

        private static void RendersAbsentSidekickAsConstantZeroBlock()
        {
            RamBackedHost host = BuildEhz1RowZeroHost();
            // Clear the slot-1 id byte but leave junk in every other Tails
            // field: the presence check must zero the whole block, rendering
            // the same bytes as S1's constant absent-sidekick block.
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x00;

            AssertEx.Equal(
                "0000,0008,0000,0230,0000,0001,02AC,0000,1,0060,0290,000C,0000,"
                + "000C,00,0,0,0,0C00,0000,02,00,00,00,0F,"
                + "0,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00",
                S2TraceCsvWriter.FormatRow(0, 0x0008, host));
        }

        private static void RendersNegativeSpeedsForBothCharacters()
        {
            var host = new RamBackedHost();
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffXVel, 0xFF80);    // -128
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffYVel, 0xFFFE);    // -2
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffInertia, 0x8000); // -32768
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x02;
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffXVel, 0xFE00);    // -512
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffYVel, 0xFC00);    // -1024
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffInertia, 0xFFFF); // -1

            string row = S2TraceCsvWriter.FormatRow(1, 0x0000, host);
            string[] fields = row.Split(',');
            AssertEx.Equal("FF80", fields[11]);
            AssertEx.Equal("FFFE", fields[12]);
            AssertEx.Equal("8000", fields[13]);
            AssertEx.Equal("FE00", fields[28]);
            AssertEx.Equal("FC00", fields[29]);
            AssertEx.Equal("FFFF", fields[30]);
        }

        private static void DerivesSidekickAirAndGroundModeFromOwnState()
        {
            var host = new RamBackedHost();
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x02;
            // Grounded left-wall angle on the sidekick only.
            host.Ram[S2Ram.SidekickBase + S2Ram.OffAngle] = 0xC0;
            string[] fields = S2TraceCsvWriter.FormatRow(0, 0, host).Split(',');
            AssertEx.Equal("0", fields[17]);   // player ground_mode unaffected
            AssertEx.Equal("3", fields[34]);   // sidekick ground_mode from own angle

            // Same angle airborne: sidekick_air=1 forces sidekick gm to 0.
            host.Ram[S2Ram.SidekickBase + S2Ram.OffStatus] =
                (byte)(S2Ram.StatusInAir | S2Ram.StatusRolling);
            fields = S2TraceCsvWriter.FormatRow(0, 0, host).Split(',');
            AssertEx.Equal("0", fields[15]);   // player_air unaffected
            AssertEx.Equal("1", fields[32]);   // sidekick_air from own status
            AssertEx.Equal("1", fields[33]);   // sidekick_rolling
            AssertEx.Equal("0", fields[34]);
        }
    }
}
