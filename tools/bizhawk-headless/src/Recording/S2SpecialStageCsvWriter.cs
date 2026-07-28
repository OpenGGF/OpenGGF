using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S2 Lua trace recorder's run-mode special-stage
    /// physics.csv writer (tools/bizhawk/s2_trace_recorder.lua v9.12-s2,
    /// read_ss_character/read_ss_state/write_ss_row, L765-931; spec
    /// tools/bizhawk-headless/docs/s2-run-mode-behavior.md §4). One 48-column
    /// row per $10 frame, ss_csv_version 1: <c>frame</c> and <c>lag</c> are
    /// decimal, everything else is lowercase unpadded hex (the SS
    /// convention — NOT the level writer's zero-padded %04X helpers).
    /// When a character slot's id byte is 0 the whole block renders as
    /// zeroes but the row keeps being written (post-clear results tail).
    /// The caller supplies the BK2-derived input masks; unlike the level
    /// writer's bk2_input_mask, the SS mask includes Start (0x80).
    /// </summary>
    public static class S2SpecialStageCsvWriter
    {
        public const int InputStart = 0x80;

        public const string Header =
            "frame,input,input_p2,lag,speed_factor,track_anim,"
            + "track_anim_frame,track_drawing_index,track_orientation,"
            + "track_duration_timer,current_segment,player_anim_frame_timer,"
            + "rings_togo_bcd,check_rings_flag,tails_control_counter,"
            + "swap_positions_flag,sonic_present,sonic_ss_x,sonic_ss_x_sub,"
            + "sonic_ss_y,sonic_ss_y_sub,sonic_ss_z,sonic_angle,"
            + "sonic_routine,sonic_routine_secondary,sonic_status,"
            + "sonic_anim,sonic_anim_frame,sonic_rings_bcd,sonic_hurt_timer,"
            + "sonic_slide_timer,sonic_flip_timer,tails_present,tails_ss_x,"
            + "tails_ss_x_sub,tails_ss_y,tails_ss_y_sub,tails_ss_z,"
            + "tails_angle,tails_routine,tails_routine_secondary,"
            + "tails_status,tails_anim,tails_anim_frame,tails_rings_bcd,"
            + "tails_hurt_timer,tails_slide_timer,tails_flip_timer";

        // SS-specific SST offsets (same slot layout as the level side;
        // duplicated here under the recorder's SS_ names for traceability).
        private const int OffSsX = 0x2A;
        private const int OffSsXSub = 0x2C;
        private const int OffSsY = 0x2E;
        private const int OffSsYSub = 0x30;
        private const int OffFlipTimer = 0x33;
        private const int OffSsZ = 0x34;
        private const int OffHurtTimer = 0x36;
        private const int OffSlideTimer = 0x37;
        private const int OffRingsHundreds = 0x3C;
        private const int OffRingsTens = 0x3D;
        private const int OffRingsUnits = 0x3E;

        // Shared special-stage track state (mainmemory addresses).
        private const int AddrTrackAnim = 0xDB08;
        private const int AddrCurrentSegment = 0xDB0A;
        private const int AddrTrackAnimFrame = 0xDB0B;
        private const int AddrTrackDrawingIndex = 0xDB0D;
        private const int AddrTrackOrientation = 0xDB0E;
        private const int AddrCurSpeedFactor = 0xDB16;
        private const int AddrTrackDurationTimer = 0xDB1F;
        private const int AddrPlayerAnimFrameTimer = 0xDB21;
        private const int AddrCheckRingsFlag = 0xDB86;
        private const int AddrRingsTogoBcd = 0xDBA4;
        private const int AddrTailsControlCounter = 0xF702;
        private const int AddrSwapPositionsFlag = 0xF742;

        /// <summary>
        /// Collapses a BK2 movie row to the SS engine input mask
        /// (joypad_mask_from_frame): directions 0x01/0x02/0x04/0x08, any of
        /// A/B/C 0x10, Start 0x80. Player 2 has no recorded buttons in any
        /// movie the strict BK2 reader accepts (it rejects P2 activity), so
        /// the input_p2 column is always 0.
        /// </summary>
        public static int InputMask(Bk2Frame frame)
        {
            if (frame == null)
            {
                throw new ArgumentNullException("frame");
            }
            int mask = S1InputMask.FromFrame(frame);
            if (frame.P1Start)
            {
                mask |= InputStart;
            }
            return mask;
        }

        /// <summary>
        /// Formats one special-stage row (no trailing newline) from the
        /// post-advance RAM state: write_ss_row's "%d,%x,%x,%d," then 44
        /// lowercase unpadded hex fields.
        /// </summary>
        public static string FormatRow(
            int traceFrame,
            int inputMask,
            int inputP2Mask,
            bool lagged,
            IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            var row = new StringBuilder(192);
            row.Append(Dec(traceFrame)).Append(',');
            row.Append(Hex(inputMask)).Append(',');
            row.Append(Hex(inputP2Mask)).Append(',');
            row.Append(lagged ? '1' : '0').Append(',');
            row.Append(Hex(S2Ram.U16(host, AddrCurSpeedFactor))).Append(',');
            row.Append(Hex(S2Ram.U8(host, AddrTrackAnim))).Append(',');
            row.Append(Hex(S2Ram.U8(host, AddrTrackAnimFrame))).Append(',');
            row.Append(Hex(S2Ram.U8(host, AddrTrackDrawingIndex))).Append(',');
            row.Append(Hex(S2Ram.U8(host, AddrTrackOrientation))).Append(',');
            row.Append(Hex(S2Ram.U8(host, AddrTrackDurationTimer)))
                .Append(',');
            row.Append(Hex(S2Ram.U8(host, AddrCurrentSegment))).Append(',');
            row.Append(Hex(S2Ram.U8(host, AddrPlayerAnimFrameTimer)))
                .Append(',');
            row.Append(Hex(S2Ram.U16(host, AddrRingsTogoBcd))).Append(',');
            row.Append(Hex(S2Ram.U8(host, AddrCheckRingsFlag))).Append(',');
            row.Append(Hex(S2Ram.U16(host, AddrTailsControlCounter)))
                .Append(',');
            row.Append(Hex(S2Ram.U8(host, AddrSwapPositionsFlag)));
            AppendCharacter(row, S2Ram.PlayerBase, host);
            AppendCharacter(row, S2Ram.SidekickBase, host);
            return row.ToString();
        }

        /// <summary>
        /// read_ss_character: present iff the slot id byte is non-zero
        /// (Sonic 0x09 / Tails 0x10 in the halfpipe); an absent character
        /// renders present 0 and every field 0.
        /// </summary>
        private static void AppendCharacter(
            StringBuilder row,
            int slotBase,
            IGpgxHost host)
        {
            bool present = S2Ram.U8(host, slotBase + S2Ram.OffId) != 0;
            if (!present)
            {
                row.Append(",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0");
                return;
            }

            int ringsBcd =
                (S2Ram.U8(host, slotBase + OffRingsHundreds) << 16)
                | (S2Ram.U8(host, slotBase + OffRingsTens) << 8)
                | S2Ram.U8(host, slotBase + OffRingsUnits);
            row.Append(",1,");
            row.Append(Hex(S2Ram.U16(host, slotBase + OffSsX))).Append(',');
            row.Append(Hex(S2Ram.U16(host, slotBase + OffSsXSub))).Append(',');
            row.Append(Hex(S2Ram.U16(host, slotBase + OffSsY))).Append(',');
            row.Append(Hex(S2Ram.U16(host, slotBase + OffSsYSub))).Append(',');
            row.Append(Hex(S2Ram.U16(host, slotBase + OffSsZ))).Append(',');
            row.Append(Hex(S2Ram.U8(host, slotBase + S2Ram.OffAngle)))
                .Append(',');
            row.Append(Hex(S2Ram.U8(host, slotBase + S2Ram.OffRoutine)))
                .Append(',');
            row.Append(Hex(S2Ram.U8(
                host, slotBase + S2Ram.OffRoutineSecondary))).Append(',');
            row.Append(Hex(S2Ram.U8(host, slotBase + S2Ram.OffStatus)))
                .Append(',');
            row.Append(Hex(S2Ram.U8(host, slotBase + S2Ram.OffAnimId)))
                .Append(',');
            row.Append(Hex(S2Ram.U8(host, slotBase + S2Ram.OffAnimFrame)))
                .Append(',');
            row.Append(Hex(ringsBcd)).Append(',');
            row.Append(Hex(S2Ram.U8(host, slotBase + OffHurtTimer)))
                .Append(',');
            row.Append(Hex(S2Ram.U8(host, slotBase + OffSlideTimer)))
                .Append(',');
            row.Append(Hex(S2Ram.U8(host, slotBase + OffFlipTimer)));
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        private static string Hex(int value)
        {
            return value.ToString("x", CultureInfo.InvariantCulture);
        }
    }
}
