using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S3K Lua trace recorder's physics.csv v7 row
    /// formatting (tools/bizhawk/s3k_trace_recorder.lua v6.31-s3k). The
    /// header text, 42-field format shape, uhex two's-complement
    /// rendering, and ground-mode thresholds are byte-shared with the
    /// S1/S2 ports; the S3K deltas are the RAM sources: player/sidekick
    /// blocks at 0xB000/0xB04A with 0x4A stride and S3K field offsets
    /// (status +0x2A), stand_on_obj resolved from the u16be interact
    /// address at +0x42, lag_counter as a REAL read of Lag_frame_count
    /// (0xF628), gameplay_frame_counter from Level_frame_counter (0xFE04)
    /// and vblank_counter from the Life_count word 0xFE12 (see
    /// <see cref="S3KRam"/>). The player block is read UNCONDITIONALLY
    /// with player_present as the literal 1 (the AIZ prefix/tail rows
    /// render all-zero player fields with present=1); the sidekick block
    /// is presence-gated on the u32be object code at 0xB04A. Rows are
    /// returned WITHOUT the trailing '\n'; the caller terminates every
    /// line (including the last) with a single LF.
    /// </summary>
    public static class S3KTraceCsvWriter
    {
        /// <summary>CSV v7 header, byte-identical to the S1/S2 v7 header.</summary>
        public const string Header = S1TraceCsvWriter.Header;

        /// <summary>
        /// One character block of the CSV row (Lua
        /// read_character_trace_state): presence flag plus 16 fields.
        /// </summary>
        public sealed class CharacterState
        {
            public int Present;
            public int X;
            public int Y;
            public short XSpeed;
            public short YSpeed;
            public short GSpeed;
            public int Angle;
            public int Air;
            public int Rolling;
            public int GroundMode;
            public int XSub;
            public int YSub;
            public int Routine;
            public int Status;
            public int StandOnObj;
            public int AnimationId;
            public int MappingFrame;
        }

        /// <summary>
        /// Presence-checked character read used for the sidekick block:
        /// when the u32be object code at <paramref name="baseAddress"/> is
        /// 0, present=0 and every field is 0 - rendering the constant
        /// absent-sidekick block through the same specifiers.
        /// </summary>
        public static CharacterState ReadCharacter(IGpgxHost host, int baseAddress)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            if (S3KRam.U32(host, baseAddress + S3KRam.OffObjectCode) == 0)
            {
                return new CharacterState();
            }
            return ReadCharacterUnconditional(host, baseAddress);
        }

        /// <summary>
        /// Unconditional character read used for the player block: the Lua
        /// reads Player_1's slot with no presence check and player_present
        /// is the constant 1. Speeds are read signed (s16be) and rendered
        /// through uhex at format time.
        /// </summary>
        public static CharacterState ReadCharacterUnconditional(
            IGpgxHost host, int baseAddress)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            byte status = S3KRam.U8(host, baseAddress + S3KRam.OffStatus);
            byte angle = S3KRam.U8(host, baseAddress + S3KRam.OffAngle);
            bool air = (status & S3KRam.StatusInAir) != 0;
            bool rolling = (status & S3KRam.StatusRolling) != 0;

            var state = new CharacterState();
            state.Present = 1;
            state.X = S3KRam.U16(host, baseAddress + S3KRam.OffXPos);
            state.Y = S3KRam.U16(host, baseAddress + S3KRam.OffYPos);
            state.XSpeed = S3KRam.S16(host, baseAddress + S3KRam.OffXVel);
            state.YSpeed = S3KRam.S16(host, baseAddress + S3KRam.OffYVel);
            state.GSpeed = S3KRam.S16(host, baseAddress + S3KRam.OffInertia);
            state.Angle = angle;
            state.Air = air ? 1 : 0;
            state.Rolling = rolling ? 1 : 0;
            state.GroundMode = S1TraceCsvWriter.DeriveGroundMode(air, angle);
            state.XSub = S3KRam.U16(host, baseAddress + S3KRam.OffXSub);
            state.YSub = S3KRam.U16(host, baseAddress + S3KRam.OffYSub);
            state.Routine = S3KRam.U8(host, baseAddress + S3KRam.OffRoutine);
            state.Status = status;
            state.StandOnObj = S3KRam.ReadStandOnSlot(host, baseAddress);
            state.AnimationId = S3KRam.U8(host, baseAddress + S3KRam.OffAnimId);
            state.MappingFrame =
                S3KRam.U8(host, baseAddress + S3KRam.OffMappingFrame);
            return state;
        }

        /// <summary>
        /// Formats trace row <paramref name="traceFrame"/> from the
        /// just-completed frame's RAM. <paramref name="inputMask"/> comes
        /// from the BK2 movie row at bk2_frame_offset + traceFrame (shared
        /// <see cref="S1InputMask"/> derivation, v6.30 rule: no
        /// profile-dependent adjustment), never from RAM - the RAM pad
        /// latch lags the BK2 on lag frames, and the pre-level prefix
        /// rows' BK2-derived input edges are exactly what the replay's
        /// ADVANCE_ONLY phase classifier consumes.
        ///
        /// gameplay_frame_counter is <see cref="S3KRam.LevelFrameCounter"/>
        /// (0xFE04) for EVERY S3K profile. This used to be a fork: the
        /// standard recorder read the dead-zero Debug_placement_mode at
        /// 0xFE08 while the complete-run recorder read 0xFE04, so a second
        /// overload took the address as a recorder-identity parameter. Lua
        /// v6.31-s3k moved the standard recorder to 0xFE04 and its three
        /// canonical fixtures were regenerated, so the two recorders now
        /// agree in all 42 columns and the overload is deleted.
        /// </summary>
        public static string FormatRow(int traceFrame, int inputMask, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            ushort cameraX = S3KRam.U16(host, S3KRam.CameraX);
            ushort cameraY = S3KRam.U16(host, S3KRam.CameraY);
            ushort rings = S3KRam.U16(host, S3KRam.RingCount);
            ushort gameplayFrameCounter =
                S3KRam.U16(host, S3KRam.LevelFrameCounter);
            ushort vblankCounter = S3KRam.U16(host, S3KRam.VblankWord);
            ushort lagCounter = S3KRam.U16(host, S3KRam.LagFrameCount);

            CharacterState player =
                ReadCharacterUnconditional(host, S3KRam.PlayerBase);
            CharacterState sidekick = ReadCharacter(host, S3KRam.SidekickBase);

            var row = new StringBuilder(192);
            row.Append(Hex4(traceFrame));
            row.Append(',').Append(Hex4(inputMask));
            row.Append(',').Append(Hex4(cameraX));
            row.Append(',').Append(Hex4(cameraY));
            row.Append(',').Append(Hex4(rings));
            row.Append(',').Append(Hex4(gameplayFrameCounter));
            row.Append(',').Append(Hex4(vblankCounter));
            row.Append(',').Append(Hex4(lagCounter));
            AppendCharacterBlock(row, player);
            AppendCharacterBlock(row, sidekick);
            return row.ToString();
        }

        /// <summary>
        /// Appends one 17-field character block:
        /// %d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X.
        /// </summary>
        private static void AppendCharacterBlock(
            StringBuilder row, CharacterState state)
        {
            row.Append(',').Append(Dec(state.Present));
            row.Append(',').Append(Hex4(state.X));
            row.Append(',').Append(Hex4(state.Y));
            row.Append(',').Append(Hex4(S1TraceCsvWriter.UHex(state.XSpeed)));
            row.Append(',').Append(Hex4(S1TraceCsvWriter.UHex(state.YSpeed)));
            row.Append(',').Append(Hex4(S1TraceCsvWriter.UHex(state.GSpeed)));
            row.Append(',').Append(Hex2(state.Angle));
            row.Append(',').Append(Dec(state.Air));
            row.Append(',').Append(Dec(state.Rolling));
            row.Append(',').Append(Dec(state.GroundMode));
            row.Append(',').Append(Hex4(state.XSub));
            row.Append(',').Append(Hex4(state.YSub));
            row.Append(',').Append(Hex2(state.Routine));
            row.Append(',').Append(Hex2(state.Status));
            row.Append(',').Append(Hex2(state.StandOnObj));
            row.Append(',').Append(Hex2(state.AnimationId));
            row.Append(',').Append(Hex2(state.MappingFrame));
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
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
