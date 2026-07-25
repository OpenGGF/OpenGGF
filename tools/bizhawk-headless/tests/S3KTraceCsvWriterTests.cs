using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S3KTraceCsvWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KTraceCsvWriter header matches canonical fixture header",
                HeaderMatchesCanonicalFixtureHeader));
            tests.Add(new TestMain.TestCase(
                "S3KTraceCsvWriter reproduces aiz1_to_hcz_fullrun fixture row 0",
                ReproducesAizFullrunFixtureRowZero));
            tests.Add(new TestMain.TestCase(
                "S3KTraceCsvWriter reproduces the AIZ ADVANCE_ONLY prefix row 0x00A1",
                ReproducesAizAdvanceOnlyPrefixRow));
            tests.Add(new TestMain.TestCase(
                "S3KTraceCsvWriter renders player_present 1 for all-zero player RAM",
                RendersPlayerPresentOneForAllZeroPlayerRam));
            tests.Add(new TestMain.TestCase(
                "S3KTraceCsvWriter renders absent sidekick as constant zero block",
                RendersAbsentSidekickAsConstantZeroBlock));
            tests.Add(new TestMain.TestCase(
                "S3KTraceCsvWriter renders negative speeds for both characters",
                RendersNegativeSpeedsForBothCharacters));
            tests.Add(new TestMain.TestCase(
                "S3KTraceCsvWriter derives sidekick air and ground mode from own state",
                DerivesSidekickAirAndGroundModeFromOwnState));
            tests.Add(new TestMain.TestCase(
                "S3KTraceCsvWriter resolves stand_on_obj only for exact slot addresses",
                ResolvesStandOnObjOnlyForExactSlotAddresses));
            tests.Add(new TestMain.TestCase(
                "S3KTraceCsvWriter reads a real lag counter from Lag_frame_count",
                ReadsRealLagCounterFromLagFrameCount));
            tests.Add(new TestMain.TestCase(
                "S3KRam pins the S3K OST geometry and mode helpers",
                PinsOstGeometryAndModeHelpers));
        }

        /// <summary>
        /// RAM state reproducing data row 0 of the gunzipped
        /// src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/physics.csv:
        /// the aiz_end_to_end ARM frame itself (Game_mode 0x4C), where
        /// player RAM still holds title-handoff values (y_vel 0xFFA5 with
        /// status 0), 100 pre-arm lag frames have accrued (lag 0x0064) and
        /// the Life_count word renders 0x0300 (3 lives).
        /// </summary>
        internal static RamBackedHost BuildAizRowZeroHost()
        {
            var host = new RamBackedHost();
            host.SetWord(S3KRam.VblankWord, 0x0300);
            host.SetWord(S3KRam.LagFrameCount, 0x0064);
            // Player_1 (0xB000) - read unconditionally, no presence check.
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffXPos, 0x0120);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffYPos, 0x00D4);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffYVel, 0xFFA5);
            // Player_2 (0xB04A) - present via nonzero u32be object code.
            host.SetLong(
                S3KRam.SidekickBase + S3KRam.OffObjectCode, 0x00019F72);
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffXPos, 0x00F0);
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffYPos, 0x0140);
            return host;
        }

        private static void HeaderMatchesCanonicalFixtureHeader()
        {
            // LITERAL header line of the gunzipped
            // src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/physics.csv
            // (byte-identical to the S1/S2 v7 header).
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
                S3KTraceCsvWriter.Header);
        }

        private static void ReproducesAizFullrunFixtureRowZero()
        {
            RamBackedHost host = BuildAizRowZeroHost();

            // LITERAL data row 0 of the gunzipped AIZ fixture physics.csv:
            // lag_counter 0064 is a REAL 0xF628 read, vblank_counter 0300
            // is the Life_count word, gameplay_frame_counter 0000 is the
            // dead 0xFE08 read, and the sidekick block is live (present=1,
            // x=0x00F0, y=0x0140).
            AssertEx.Equal(
                "0000,0000,0000,0000,0000,0000,0300,0064,1,0120,00D4,0000,FFA5,"
                + "0000,00,0,0,0,0000,0000,00,00,00,00,00,1,00F0,0140,0000,0000,"
                + "0000,00,0,0,0,0000,0000,00,00,00,00,00",
                S3KTraceCsvWriter.FormatRow(0, 0x0000, host));
        }

        private static void ReproducesAizAdvanceOnlyPrefixRow()
        {
            // LITERAL row 0x00A1 of the gunzipped AIZ fixture physics.csv -
            // the first ADVANCE_ONLY-classified prefix row: its BK2-derived
            // input flips 0000 -> 0004 while every sampled state field and
            // all three counters are byte-identical to row 0x00A0
            // (vfc 0000, vblank 0300, lag 0085). The recorder derives the
            // input column from the BK2 (never RAM) and records counters
            // verbatim; the replay's phase classifier depends on it.
            var host = new RamBackedHost();
            host.SetWord(S3KRam.VblankWord, 0x0300);
            host.SetWord(S3KRam.LagFrameCount, 0x0085);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffXPos, 0x0120);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffYPos, 0x014C);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffXSub, 0x0120);
            host.Ram[S3KRam.PlayerBase + S3KRam.OffMappingFrame] = 0x03;
            host.SetLong(
                S3KRam.SidekickBase + S3KRam.OffObjectCode, 0x00019F72);
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffXPos, 0x0110);
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffYPos, 0x00E2);
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffXSub, 0x0110);
            host.Ram[S3KRam.SidekickBase + S3KRam.OffMappingFrame] = 0x01;

            AssertEx.Equal(
                "00A1,0004,0000,0000,0000,0000,0300,0085,1,0120,014C,0000,0000,"
                + "0000,00,0,0,0,0120,0000,00,00,00,00,03,1,0110,00E2,0000,0000,"
                + "0000,00,0,0,0,0110,0000,00,00,00,00,01",
                S3KTraceCsvWriter.FormatRow(0x00A1, 0x0004, host));
        }

        private static void RendersPlayerPresentOneForAllZeroPlayerRam()
        {
            // The AIZ tail rows show all-zero player fields with
            // player_present still 1: the Lua argument is the literal 1,
            // never derived from RAM.
            var host = new RamBackedHost();
            AssertEx.Equal(
                "0000,0000,0000,0000,0000,0000,0000,0000,"
                + "1,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00,"
                + "0,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00",
                S3KTraceCsvWriter.FormatRow(0, 0, host));
        }

        private static void RendersAbsentSidekickAsConstantZeroBlock()
        {
            RamBackedHost host = BuildAizRowZeroHost();
            // Clear the slot-1 object code but leave junk in every other
            // Player_2 field: the u32be presence check must zero the whole
            // block through the same specifiers.
            host.SetLong(S3KRam.SidekickBase + S3KRam.OffObjectCode, 0);

            AssertEx.Equal(
                "0000,0000,0000,0000,0000,0000,0300,0064,1,0120,00D4,0000,FFA5,"
                + "0000,00,0,0,0,0000,0000,00,00,00,00,00,"
                + "0,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00",
                S3KTraceCsvWriter.FormatRow(0, 0x0000, host));
        }

        private static void RendersNegativeSpeedsForBothCharacters()
        {
            var host = new RamBackedHost();
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffXVel, 0xFF80);    // -128
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffYVel, 0xFFFE);    // -2
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffInertia, 0x8000); // -32768
            host.SetLong(
                S3KRam.SidekickBase + S3KRam.OffObjectCode, 0x00019F72);
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffXVel, 0xFE00);    // -512
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffYVel, 0xFC00);    // -1024
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffInertia, 0xFFFF); // -1

            string row = S3KTraceCsvWriter.FormatRow(1, 0x0000, host);
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
            host.SetLong(
                S3KRam.SidekickBase + S3KRam.OffObjectCode, 0x00019F72);
            // Grounded left-wall angle on the sidekick only (S3K angle
            // offset +0x26, status +0x2A).
            host.Ram[S3KRam.SidekickBase + S3KRam.OffAngle] = 0xC0;
            string[] fields = S3KTraceCsvWriter.FormatRow(0, 0, host).Split(',');
            AssertEx.Equal("0", fields[17]);   // player ground_mode unaffected
            AssertEx.Equal("3", fields[34]);   // sidekick ground_mode from own angle

            // Same angle airborne: sidekick_air=1 forces sidekick gm to 0.
            host.Ram[S3KRam.SidekickBase + S3KRam.OffStatus] =
                (byte)(S3KRam.StatusInAir | S3KRam.StatusRolling);
            fields = S3KTraceCsvWriter.FormatRow(0, 0, host).Split(',');
            AssertEx.Equal("0", fields[15]);   // player_air unaffected
            AssertEx.Equal("1", fields[32]);   // sidekick_air from own status
            AssertEx.Equal("1", fields[33]);   // sidekick_rolling
            AssertEx.Equal("0", fields[34]);
        }

        private static void ResolvesStandOnObjOnlyForExactSlotAddresses()
        {
            // Lua interact_addr_to_slot: slot only on exact
            // 0xB000 + k*0x4A alignment with k < 110; else 0.
            AssertEx.Equal(0, S3KRam.InteractAddrToSlot(0x0000));
            AssertEx.Equal(0, S3KRam.InteractAddrToSlot(0xB000));
            AssertEx.Equal(1, S3KRam.InteractAddrToSlot(0xB04A));
            AssertEx.Equal(5, S3KRam.InteractAddrToSlot(0xB172));
            AssertEx.Equal(94, S3KRam.InteractAddrToSlot(0xCB2C));
            AssertEx.Equal(109, S3KRam.InteractAddrToSlot(0xCF82));
            AssertEx.Equal(0, S3KRam.InteractAddrToSlot(0xB04B));   // misaligned
            AssertEx.Equal(0, S3KRam.InteractAddrToSlot(0xAFFF));   // below table
            AssertEx.Equal(0, S3KRam.InteractAddrToSlot(0xCFCC));   // one past end
            AssertEx.Equal(0, S3KRam.InteractAddrToSlot(0xD000));   // S1 base is not S3K's

            // Through the CSV row: player stands on slot 5, sidekick's
            // interact word holds a misaligned address and renders 00.
            var host = new RamBackedHost();
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffInteract, 0xB172);
            host.SetLong(
                S3KRam.SidekickBase + S3KRam.OffObjectCode, 0x00019F72);
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffInteract, 0xB04B);
            string[] fields = S3KTraceCsvWriter.FormatRow(0, 0, host).Split(',');
            AssertEx.Equal("05", fields[22]);
            AssertEx.Equal("00", fields[39]);
        }

        private static void ReadsRealLagCounterFromLagFrameCount()
        {
            // S3K's lag_counter is a live 0xF628 read (AIZ arms at 0x0064),
            // unlike S2's constant-zero placeholder.
            var host = new RamBackedHost();
            host.SetWord(S3KRam.LagFrameCount, 0x1234);
            string[] fields = S3KTraceCsvWriter.FormatRow(0, 0, host).Split(',');
            AssertEx.Equal("1234", fields[7]);
        }

        private static void PinsOstGeometryAndModeHelpers()
        {
            AssertEx.Equal(0xB000, S3KRam.SlotAddress(0));
            AssertEx.Equal(0xB04A, S3KRam.SidekickBase);
            AssertEx.Equal(S3KRam.SidekickBase, S3KRam.SlotAddress(1));
            AssertEx.Equal(0xCB2C, S3KRam.SlotAddress(S3KRam.BreathingBubblesSlot));
            AssertEx.Equal(0xCB76, S3KRam.SlotAddress(S3KRam.BreathingBubblesP2Slot));
            AssertEx.Equal(110, S3KRam.TotalObjectSlots);
            AssertEx.Equal(0x4A, S3KRam.ObjectSlotSize);

            // Level family accepts $0C/$4C/$8C transitional frames.
            AssertEx.Equal(true, S3KRam.IsLevelFamilyMode(0x0C));
            AssertEx.Equal(true, S3KRam.IsLevelFamilyMode(0x4C));
            AssertEx.Equal(true, S3KRam.IsLevelFamilyMode(0x8C));
            AssertEx.Equal(false, S3KRam.IsLevelFamilyMode(0x00));
            AssertEx.Equal(false, S3KRam.IsLevelFamilyMode(0x04));
            AssertEx.Equal(false, S3KRam.IsLevelFamilyMode(0x28));

            AssertEx.Equal("aiz", S3KRam.ZoneName(0x00));
            AssertEx.Equal("mgz", S3KRam.ZoneName(0x02));
            AssertEx.Equal("cnz", S3KRam.ZoneName(0x03));
            AssertEx.Equal("icz", S3KRam.ZoneName(0x05));
            AssertEx.Equal("hpz", S3KRam.ZoneName(0x0D));
            AssertEx.Equal("unknown_0e", S3KRam.ZoneName(0x0E));
        }
    }
}
