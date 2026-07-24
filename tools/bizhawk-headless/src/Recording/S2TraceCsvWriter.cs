using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S2 Lua trace recorder's physics.csv v7 row
    /// formatting (tools/bizhawk/s2_trace_recorder.lua v9.12-s2). The header
    /// text, 42-field format shape, uhex two's-complement rendering, and
    /// ground-mode thresholds are byte-shared with the S1 port; the
    /// fundamental S2 delta is the live symmetric sidekick block read from
    /// Tails' SST slot (S1 writes constants). Rows are returned WITHOUT the
    /// trailing '\n'; the caller terminates every line (including the last)
    /// with a single LF.
    /// </summary>
    public static class S2TraceCsvWriter
    {
        /// <summary>CSV v7 header, byte-identical to the S1 v7 header.</summary>
        public const string Header = S1TraceCsvWriter.Header;

        /// <summary>
        /// One character block of the CSV row (Lua
        /// read_character_trace_state): presence flag plus 16 fields, all
        /// zero when the slot's id byte is 0.
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
        /// Presence-checked character read used for the sidekick block: when
        /// the id byte at <paramref name="baseAddress"/> is 0, present=0 and
        /// every field is 0 — rendering exactly the same bytes as S1's
        /// constant absent-sidekick block.
        /// </summary>
        public static CharacterState ReadCharacter(IGpgxHost host, int baseAddress)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            if (S2Ram.U8(host, baseAddress + S2Ram.OffId) == 0)
            {
                return new CharacterState();
            }
            return ReadCharacterUnconditional(host, baseAddress);
        }

        /// <summary>
        /// Unconditional character read used for the player block: the Lua
        /// reads Sonic's slot 0 with no presence check and player_present is
        /// the constant 1. Speeds are read signed (s16be) and rendered
        /// through uhex at format time.
        /// </summary>
        public static CharacterState ReadCharacterUnconditional(
            IGpgxHost host, int baseAddress)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            byte status = S2Ram.U8(host, baseAddress + S2Ram.OffStatus);
            byte angle = S2Ram.U8(host, baseAddress + S2Ram.OffAngle);
            bool air = (status & S2Ram.StatusInAir) != 0;
            bool rolling = (status & S2Ram.StatusRolling) != 0;

            var state = new CharacterState();
            state.Present = 1;
            state.X = S2Ram.U16(host, baseAddress + S2Ram.OffXPos);
            state.Y = S2Ram.U16(host, baseAddress + S2Ram.OffYPos);
            state.XSpeed = S2Ram.S16(host, baseAddress + S2Ram.OffXVel);
            state.YSpeed = S2Ram.S16(host, baseAddress + S2Ram.OffYVel);
            state.GSpeed = S2Ram.S16(host, baseAddress + S2Ram.OffInertia);
            state.Angle = angle;
            state.Air = air ? 1 : 0;
            state.Rolling = rolling ? 1 : 0;
            state.GroundMode = S1TraceCsvWriter.DeriveGroundMode(air, angle);
            state.XSub = S2Ram.U16(host, baseAddress + S2Ram.OffXSub);
            state.YSub = S2Ram.U16(host, baseAddress + S2Ram.OffYSub);
            state.Routine = S2Ram.U8(host, baseAddress + S2Ram.OffRoutine);
            state.Status = status;
            state.StandOnObj = S2Ram.U8(host, baseAddress + S2Ram.OffStandOnObj);
            state.AnimationId = S2Ram.U8(host, baseAddress + S2Ram.OffAnimId);
            state.MappingFrame = S2Ram.U8(host, baseAddress + S2Ram.OffMappingFrame);
            return state;
        }

        /// <summary>
        /// Formats trace row <paramref name="traceFrame"/> from the
        /// just-completed frame's RAM. <paramref name="inputMask"/> comes
        /// from the BK2 movie row applied for this frame (shared
        /// <see cref="S1InputMask"/> derivation), never from RAM.
        /// </summary>
        public static string FormatRow(int traceFrame, int inputMask, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            ushort cameraX = S2Ram.U16(host, S2Ram.CameraX);
            ushort cameraY = S2Ram.U16(host, S2Ram.CameraY);
            ushort rings = S2Ram.U16(host, S2Ram.RingCount);
            ushort gameplayFrameCounter = S2Ram.U16(host, S2Ram.FrameCount);
            ushort vblankCounter = S2Ram.U16(host, S2Ram.VblankWord);

            CharacterState player =
                ReadCharacterUnconditional(host, S2Ram.PlayerBase);
            CharacterState sidekick = ReadCharacter(host, S2Ram.SidekickBase);

            var row = new StringBuilder(192);
            row.Append(Hex4(traceFrame));
            row.Append(',').Append(Hex4(inputMask));
            row.Append(',').Append(Hex4(cameraX));
            row.Append(',').Append(Hex4(cameraY));
            row.Append(',').Append(Hex4(rings));
            row.Append(',').Append(Hex4(gameplayFrameCounter));
            row.Append(',').Append(Hex4(vblankCounter));
            row.Append(',').Append(Hex4(0));          // lag_counter placeholder
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
