using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// 64KiB-RAM-backed fake host shared by the S1 trace formatting tests.
    /// </summary>
    internal sealed class RamBackedHost : IGpgxHost
    {
        public RamBackedHost()
        {
            Ram = new byte[65536];
        }

        public byte[] Ram { get; private set; }

        public int CompletedFrame
        {
            get { return 0; }
        }

        public void SetWord(int address, int value)
        {
            Ram[address] = (byte)((value >> 8) & 0xFF);
            Ram[address + 1] = (byte)(value & 0xFF);
        }

        public void SetLong(int address, long value)
        {
            Ram[address] = (byte)((value >> 24) & 0xFF);
            Ram[address + 1] = (byte)((value >> 16) & 0xFF);
            Ram[address + 2] = (byte)((value >> 8) & 0xFF);
            Ram[address + 3] = (byte)(value & 0xFF);
        }

        public void ClearButtons()
        {
        }

        public void SetButton(string name, bool pressed)
        {
        }

        public void Advance()
        {
        }

        public byte ReadMainRamByte(int offset)
        {
            return Ram[offset];
        }

        public void Dispose()
        {
        }
    }

    internal static class S1TraceCsvWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1TraceCsvWriter header matches canonical fixture header",
                HeaderMatchesCanonicalFixtureHeader));
            tests.Add(new TestMain.TestCase(
                "S1TraceCsvWriter reproduces ghz1_fullrun fixture row 0",
                ReproducesGhz1FullrunFixtureRowZero));
            tests.Add(new TestMain.TestCase(
                "S1TraceCsvWriter renders negative speeds as two's complement",
                RendersNegativeSpeedsAsTwosComplement));
            tests.Add(new TestMain.TestCase(
                "S1TraceCsvWriter derives ground mode with angle wrap",
                DerivesGroundModeWithAngleWrap));
            tests.Add(new TestMain.TestCase(
                "S1InputMask collapses BK2 buttons and excludes Start",
                InputMaskCollapsesBk2ButtonsAndExcludesStart));
        }

        private static void HeaderMatchesCanonicalFixtureHeader()
        {
            // LITERAL header line of
            // src/test/resources/traces/s1/ghz1_fullrun/physics.csv.
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
                S1TraceCsvWriter.Header);
        }

        private static void ReproducesGhz1FullrunFixtureRowZero()
        {
            var host = new RamBackedHost();
            host.SetWord(S1Ram.CameraX, 0x0000);
            host.SetWord(S1Ram.CameraY, 0x0300);
            host.SetWord(S1Ram.RingCount, 0x0000);
            host.SetWord(S1Ram.FrameCount, 0x0001);
            host.SetWord(S1Ram.VblankWord, 0x020E);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffXPos, 0x0050);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffYPos, 0x03B0);
            host.Ram[S1Ram.PlayerBase + S1Ram.OffAngle] = 0xFC;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRoutine] = 0x02;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffAnimId] = 0x05;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffMappingFrame] = 0x01;

            // LITERAL data row 0 of
            // src/test/resources/traces/s1/ghz1_fullrun/physics.csv.
            AssertEx.Equal(
                "0000,0000,0000,0300,0000,0001,020E,0000,1,0050,03B0,0000,0000,"
                + "0000,FC,0,0,0,0000,0000,02,00,00,05,01,0,0000,0000,0000,0000,"
                + "0000,00,0,0,0,0000,0000,00,00,00,00,00",
                S1TraceCsvWriter.FormatRow(0, 0x0000, host));
        }

        private static void RendersNegativeSpeedsAsTwosComplement()
        {
            var host = new RamBackedHost();
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffXVel, 0xFF80);   // -128
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffYVel, 0xFFFE);   // -2
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffInertia, 0x8000); // -32768

            string row = S1TraceCsvWriter.FormatRow(1, 0x0000, host);
            string[] fields = row.Split(',');
            AssertEx.Equal("FF80", fields[11]);
            AssertEx.Equal("FFFE", fields[12]);
            AssertEx.Equal("8000", fields[13]);
        }

        private static void DerivesGroundModeWithAngleWrap()
        {
            // Floor wraps across 0x00: 0x00..0x1F and 0xE0..0xFF.
            AssertEx.Equal(0, S1TraceCsvWriter.DeriveGroundMode(false, 0x00));
            AssertEx.Equal(0, S1TraceCsvWriter.DeriveGroundMode(false, 0x1F));
            AssertEx.Equal(0, S1TraceCsvWriter.DeriveGroundMode(false, 0xE0));
            AssertEx.Equal(0, S1TraceCsvWriter.DeriveGroundMode(false, 0xFF));
            // Right wall 0x20..0x5F.
            AssertEx.Equal(1, S1TraceCsvWriter.DeriveGroundMode(false, 0x20));
            AssertEx.Equal(1, S1TraceCsvWriter.DeriveGroundMode(false, 0x5F));
            // Ceiling 0x60..0x9F.
            AssertEx.Equal(2, S1TraceCsvWriter.DeriveGroundMode(false, 0x60));
            AssertEx.Equal(2, S1TraceCsvWriter.DeriveGroundMode(false, 0x9F));
            // Left wall 0xA0..0xDF.
            AssertEx.Equal(3, S1TraceCsvWriter.DeriveGroundMode(false, 0xA0));
            AssertEx.Equal(3, S1TraceCsvWriter.DeriveGroundMode(false, 0xDF));
            // Airborne forces 0 regardless of angle.
            AssertEx.Equal(0, S1TraceCsvWriter.DeriveGroundMode(true, 0x80));

            // End-to-end: grounded left-wall angle renders ground_mode 3.
            var host = new RamBackedHost();
            host.Ram[S1Ram.PlayerBase + S1Ram.OffAngle] = 0xC0;
            string row = S1TraceCsvWriter.FormatRow(0, 0, host);
            AssertEx.Equal("3", row.Split(',')[17]);

            // Same angle airborne renders 0 (and player_air 1).
            host.Ram[S1Ram.PlayerBase + S1Ram.OffStatus] = 0x02;
            row = S1TraceCsvWriter.FormatRow(0, 0, host);
            AssertEx.Equal("1", row.Split(',')[15]);
            AssertEx.Equal("0", row.Split(',')[17]);
        }

        private static void InputMaskCollapsesBk2ButtonsAndExcludesStart()
        {
            AssertEx.Equal(0x00, S1InputMask.FromButtons(
                false, false, false, false, false));
            AssertEx.Equal(0x01, S1InputMask.FromButtons(
                true, false, false, false, false));
            AssertEx.Equal(0x02, S1InputMask.FromButtons(
                false, true, false, false, false));
            AssertEx.Equal(0x04, S1InputMask.FromButtons(
                false, false, true, false, false));
            AssertEx.Equal(0x08, S1InputMask.FromButtons(
                false, false, false, true, false));
            AssertEx.Equal(0x18, S1InputMask.FromButtons(
                false, false, false, true, true));

            // A, B, and C each collapse to the single 0x10 jump bit.
            AssertEx.Equal(0x10, S1InputMask.FromFrame(
                new Bk2Frame { P1A = true }));
            AssertEx.Equal(0x10, S1InputMask.FromFrame(
                new Bk2Frame { P1B = true }));
            AssertEx.Equal(0x10, S1InputMask.FromFrame(
                new Bk2Frame { P1C = true }));
            AssertEx.Equal(0x10, S1InputMask.FromFrame(
                new Bk2Frame { P1A = true, P1B = true, P1C = true }));

            // Start never contributes to the mask.
            AssertEx.Equal(0x00, S1InputMask.FromFrame(
                new Bk2Frame { P1Start = true }));
            AssertEx.Equal(0x06, S1InputMask.FromFrame(
                new Bk2Frame
                {
                    P1Down = true,
                    P1Left = true,
                    P1Start = true
                }));
        }
    }
}
