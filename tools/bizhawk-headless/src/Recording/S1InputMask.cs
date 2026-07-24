using System;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Collapses a BK2 movie row's Player 1 buttons to the recorder's engine
    /// input mask (lib/oggf_trace_common.lua bk2_input_mask): directions map
    /// to 0x01/0x02/0x04/0x08, any of A/B/C sets 0x10 ("JUMP"), and Start
    /// never contributes. The mask always comes from the movie row, never
    /// from ROM-side v_jpadhold1.
    /// </summary>
    public static class S1InputMask
    {
        public const int Up = 0x01;
        public const int Down = 0x02;
        public const int Left = 0x04;
        public const int Right = 0x08;
        public const int Jump = 0x10;

        public static int FromFrame(Bk2Frame frame)
        {
            if (frame == null)
            {
                throw new ArgumentNullException("frame");
            }

            return FromButtons(
                frame.P1Up,
                frame.P1Down,
                frame.P1Left,
                frame.P1Right,
                frame.P1A || frame.P1B || frame.P1C);
        }

        public static int FromButtons(
            bool up,
            bool down,
            bool left,
            bool right,
            bool jump)
        {
            int mask = 0;
            if (up)
            {
                mask |= Up;
            }
            if (down)
            {
                mask |= Down;
            }
            if (left)
            {
                mask |= Left;
            }
            if (right)
            {
                mask |= Right;
            }
            if (jump)
            {
                mask |= Jump;
            }
            return mask;
        }
    }
}
