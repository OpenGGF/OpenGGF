using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S1 complete-run recorder's special-stage
    /// physics.csv writer (tools/bizhawk/s1_complete_run_recorder.lua,
    /// write_ss_row L671-702; spec s1-run-mode-behavior.md §4). One
    /// 14-column row per $10 continuation frame, ss_csv_version 1:
    /// <c>frame</c> and <c>lag</c> are decimal, everything else is
    /// lowercase unpadded hex (do NOT reuse the level writer's %04X
    /// uppercase padding). x_pos/y_pos are u32 reads (pixel word ++
    /// subpixel word); vel/inertia are UNSIGNED u16 reads, so negative
    /// velocities render as e.g. "ffde". Rows keep being written through
    /// S1's results tally while it still runs under game_mode $10. The
    /// input mask is the level rows' shared bk2_input_mask
    /// (<see cref="S1InputMask"/>): directions plus A|B|C collapsed to
    /// 0x10, no Start bit (an S2-SS delta).
    /// </summary>
    public static class S1SpecialStageCsvWriter
    {
        public const string Header =
            "frame,input,lag,x_pos,y_pos,vel_x,vel_y,inertia,status,"
            + "ss_angle,ss_rotate,bg_anim,rings,emeralds";

        /// <summary>
        /// Formats one special-stage row (no trailing newline) from the
        /// post-advance RAM state: write_ss_row's
        /// "%d,%x,%d,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x".
        /// </summary>
        public static string FormatRow(
            int traceFrame,
            int inputMask,
            bool lagged,
            IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            var row = new StringBuilder(96);
            row.Append(Dec(traceFrame)).Append(',');
            row.Append(Hex(inputMask)).Append(',');
            row.Append(lagged ? '1' : '0').Append(',');
            row.Append(HexU(S1Ram.U32(
                host, S1Ram.PlayerBase + S1Ram.OffXPos))).Append(',');
            row.Append(HexU(S1Ram.U32(
                host, S1Ram.PlayerBase + S1Ram.OffYPos))).Append(',');
            row.Append(Hex(S1Ram.U16(
                host, S1Ram.PlayerBase + S1Ram.OffXVel))).Append(',');
            row.Append(Hex(S1Ram.U16(
                host, S1Ram.PlayerBase + S1Ram.OffYVel))).Append(',');
            row.Append(Hex(S1Ram.U16(
                host, S1Ram.PlayerBase + S1Ram.OffInertia))).Append(',');
            row.Append(Hex(S1Ram.U8(
                host, S1Ram.PlayerBase + S1Ram.OffStatus))).Append(',');
            row.Append(Hex(S1Ram.U16(host, S1Ram.SsAngle))).Append(',');
            row.Append(Hex(S1Ram.U16(host, S1Ram.SsRotate))).Append(',');
            row.Append(Hex(S1Ram.U16(host, S1Ram.SsBgAnim))).Append(',');
            row.Append(Hex(S1Ram.U16(host, S1Ram.RingCount))).Append(',');
            row.Append(Hex(S1Ram.U8(host, S1Ram.Emeralds)));
            return row.ToString();
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        private static string Hex(int value)
        {
            return value.ToString("x", CultureInfo.InvariantCulture);
        }

        private static string HexU(uint value)
        {
            return value.ToString("x", CultureInfo.InvariantCulture);
        }
    }
}
