using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S3K complete-run recorder's
    /// <c>s3k_special_stage</c> physics.csv writer
    /// (tools/bizhawk/s3k_complete_run_recorder.lua v6.33-s3k-completerun,
    /// write_ss_row L5174; spec
    /// tools/bizhawk-headless/docs/s3k-completerun-profiles.md §2.2).
    ///
    /// This is a COMPLETELY separate writer from
    /// <see cref="S3KTraceCsvWriter"/>, not a variant of it: 20 columns
    /// instead of 42, a different format string, and a different numeric
    /// convention — <c>frame</c> and <c>lag</c> are DECIMAL while every
    /// other column is LOWERCASE UNPADDED hex (the S2 special-stage
    /// convention, and the opposite of the level writer's zero-padded
    /// uppercase %04X/%02X). An SS segment also emits no aux events at all
    /// (spec §4.4), so there is no aux counterpart to this class.
    ///
    /// Column order is the blue-spheres plan's TABLE order, not address
    /// order: <c>rate_timer</c> (0xE43E) is emitted AFTER <c>rate</c>
    /// (0xE444), matching the engine-side S3kSpecialStageTraceFrame parser.
    /// Reordering the two to "tidy" the addresses would silently corrupt
    /// every replay.
    ///
    /// Rows are returned WITHOUT the trailing newline; the caller
    /// terminates every line (including the last) itself.
    /// </summary>
    public static class S3KSpecialStageCsvWriter
    {
        /// <summary>
        /// LITERAL header line of the gunzipped
        /// src/test/resources/traces/s3k/special_stage/physics.csv, shared
        /// by every ss* segment of runs/s3-knux-multibonus-ss.
        /// </summary>
        public const string Header =
            "frame,input,input_p2,lag,anim_frame,x_pos,y_pos,angle,velocity,"
            + "turning,jumping,fade_timer,spheres_left,ring_count,rings_left,"
            + "rate,rate_timer,clear_timer,clear_routine,started";

        /// <summary>
        /// ss_input_mask (Lua 5075) for one player. Unlike the S2 special
        /// stage's mask this does NOT include Start: the Lua folds only the
        /// four directions plus A|B|C into the mask, exactly like the level
        /// writer's bk2_input_mask. Reuses <see cref="S1InputMask"/>
        /// because the fold is identical.
        /// </summary>
        public static int InputMask(Bk2Frame frame)
        {
            if (frame == null)
            {
                throw new ArgumentNullException("frame");
            }
            return S1InputMask.FromFrame(frame);
        }

        /// <summary>
        /// Formats one 20-column special-stage row from the post-advance
        /// RAM state.
        ///
        /// <paramref name="inputP2Mask"/> is a caller-supplied parameter
        /// rather than a movie read because the Lua takes it from
        /// movie.getinput(..., 2) while the native BK2 reader models no
        /// per-button P2 state (it rejects movies with P2 activity). Every
        /// canonical fixture row therefore carries input_p2 = 0, which is
        /// what the runner passes.
        ///
        /// <paramref name="lagged"/> is emu.islagged() for the just-completed
        /// frame (<see cref="IGpgxHost.IsLagged"/>) — a real per-frame value,
        /// not a placeholder: the canonical special_stage fixture has 113
        /// lag rows out of 4630.
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

            var row = new StringBuilder(96);
            row.Append(Dec(traceFrame)).Append(',');
            row.Append(Hex(inputMask)).Append(',');
            row.Append(Hex(inputP2Mask)).Append(',');
            row.Append(lagged ? '1' : '0').Append(',');
            row.Append(Hex(S3KRam.U16(host, S3KRam.SsAnimFrame))).Append(',');
            row.Append(Hex(S3KRam.U16(host, S3KRam.SsXPos))).Append(',');
            row.Append(Hex(S3KRam.U16(host, S3KRam.SsYPos))).Append(',');
            row.Append(Hex(S3KRam.U8(host, S3KRam.SsAngle))).Append(',');
            // velocity is read SIGNED and wrapped by the Lua's local
            // "v < 0 -> v + 0x10000", so -0x400 renders "fc00" — the same
            // uhex convention as the level writer, but lowercase/unpadded.
            row.Append(Hex(UHex(S3KRam.S16(host, S3KRam.SsVelocity))))
                .Append(',');
            row.Append(Hex(S3KRam.U8(host, S3KRam.SsTurning))).Append(',');
            row.Append(Hex(S3KRam.U8(host, S3KRam.SsJumping))).Append(',');
            row.Append(Hex(S3KRam.U8(host, S3KRam.SsFadeTimer))).Append(',');
            row.Append(Hex(S3KRam.U16(host, S3KRam.SsSpheresLeft)))
                .Append(',');
            row.Append(Hex(S3KRam.U16(host, S3KRam.SsRingCount))).Append(',');
            row.Append(Hex(S3KRam.U16(host, S3KRam.SsRingsLeft))).Append(',');
            row.Append(Hex(S3KRam.U16(host, S3KRam.SsRate))).Append(',');
            row.Append(Hex(S3KRam.U16(host, S3KRam.SsRateTimer))).Append(',');
            row.Append(Hex(S3KRam.U16(host, S3KRam.SsClearTimer))).Append(',');
            row.Append(Hex(S3KRam.U8(host, S3KRam.SsClearRoutine)))
                .Append(',');
            // started is Special_stage_started ~= 0 rendered as 0/1, not
            // the raw byte.
            row.Append(S3KRam.U8(host, S3KRam.SsStarted) != 0 ? '1' : '0');
            return row.ToString();
        }

        private static int UHex(short value)
        {
            return value < 0 ? value + 0x10000 : value;
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
