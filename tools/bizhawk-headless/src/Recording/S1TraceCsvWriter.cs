using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the Lua trace recorder's physics.csv v7 header and
    /// row formatting (tools/bizhawk/s1_trace_recorder.lua). Rows are
    /// returned WITHOUT the trailing '\n'; the caller terminates every line
    /// (including the last) with a single LF.
    /// </summary>
    public static class S1TraceCsvWriter
    {
        public const string Header =
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
            + "sidekick_mapping_frame";

        /// <summary>
        /// The constant absent-sidekick block: 17 zeros rendered through the
        /// Lua row format's tail specifiers.
        /// </summary>
        public const string SidekickBlock =
            "0,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00";

        /// <summary>
        /// Formats trace row <paramref name="traceFrame"/> from the
        /// just-completed frame's RAM. <paramref name="inputMask"/> comes
        /// from the BK2 movie row applied for this frame (see
        /// <see cref="S1InputMask"/>), never from RAM.
        /// </summary>
        public static string FormatRow(int traceFrame, int inputMask, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            ushort cameraX = S1Ram.U16(host, S1Ram.CameraX);
            ushort cameraY = S1Ram.U16(host, S1Ram.CameraY);
            ushort rings = S1Ram.U16(host, S1Ram.RingCount);
            ushort gameplayFrameCounter = S1Ram.U16(host, S1Ram.FrameCount);
            ushort vblankCounter = S1Ram.U16(host, S1Ram.VblankWord);

            ushort x = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffXPos);
            ushort y = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffYPos);
            ushort xSub = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffXSub);
            ushort ySub = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffYSub);
            short xSpeed = S1Ram.S16(host, S1Ram.PlayerBase + S1Ram.OffXVel);
            short ySpeed = S1Ram.S16(host, S1Ram.PlayerBase + S1Ram.OffYVel);
            short gSpeed = S1Ram.S16(host, S1Ram.PlayerBase + S1Ram.OffInertia);
            byte angle = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffAngle);
            byte status = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffStatus);
            byte routine = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffRoutine);
            byte standOnObj = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffStandOnObj);
            byte animationId = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffAnimId);
            byte mappingFrame = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffMappingFrame);

            bool air = (status & S1Ram.StatusInAir) != 0;
            bool rolling = (status & S1Ram.StatusRolling) != 0;
            int groundMode = DeriveGroundMode(air, angle);

            var row = new StringBuilder(192);
            row.Append(Hex4(traceFrame));
            row.Append(',').Append(Hex4(inputMask));
            row.Append(',').Append(Hex4(cameraX));
            row.Append(',').Append(Hex4(cameraY));
            row.Append(',').Append(Hex4(rings));
            row.Append(',').Append(Hex4(gameplayFrameCounter));
            row.Append(',').Append(Hex4(vblankCounter));
            row.Append(',').Append(Hex4(0));          // lag_counter placeholder
            row.Append(',').Append('1');              // player_present constant
            row.Append(',').Append(Hex4(x));
            row.Append(',').Append(Hex4(y));
            row.Append(',').Append(Hex4(UHex(xSpeed)));
            row.Append(',').Append(Hex4(UHex(ySpeed)));
            row.Append(',').Append(Hex4(UHex(gSpeed)));
            row.Append(',').Append(Hex2(angle));
            row.Append(',').Append(air ? '1' : '0');
            row.Append(',').Append(rolling ? '1' : '0');
            row.Append(',').Append(
                groundMode.ToString(CultureInfo.InvariantCulture));
            row.Append(',').Append(Hex4(xSub));
            row.Append(',').Append(Hex4(ySub));
            row.Append(',').Append(Hex2(routine));
            row.Append(',').Append(Hex2(status));
            row.Append(',').Append(Hex2(standOnObj));
            row.Append(',').Append(Hex2(animationId));
            row.Append(',').Append(Hex2(mappingFrame));
            row.Append(',').Append(SidekickBlock);
            return row.ToString();
        }

        /// <summary>
        /// Two's-complement rendering of a signed 16-bit speed, matching the
        /// Lua uhex() helper: -2 renders as 0xFFFE.
        /// </summary>
        public static int UHex(short value)
        {
            return unchecked((ushort)value);
        }

        /// <summary>
        /// Ground-mode derivation (VERBATIM Lua thresholds): 0 while airborne;
        /// otherwise 0 floor (angle &lt;= 0x1F or &gt;= 0xE0), 1 right wall
        /// (0x20..0x5F), 2 ceiling (0x60..0x9F), 3 left wall (0xA0..0xDF).
        /// </summary>
        public static int DeriveGroundMode(bool air, byte angle)
        {
            if (air)
            {
                return 0;
            }
            if (angle <= 0x1F || angle >= 0xE0)
            {
                return 0;
            }
            if (angle <= 0x5F)
            {
                return 1;
            }
            if (angle <= 0x9F)
            {
                return 2;
            }
            return 3;
        }

        private static string Hex4(int value)
        {
            return value.ToString("X4", CultureInfo.InvariantCulture);
        }

        private static string Hex2(int value)
        {
            return value.ToString("X2", CultureInfo.InvariantCulture);
        }
    }
}
