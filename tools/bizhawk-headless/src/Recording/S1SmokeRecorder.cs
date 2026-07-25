using System;

namespace OpenGGF.BizHawk.Headless
{
    public static class S1SmokeRecorder
    {
        public const string Header =
            "frame,input,x,y,x_velocity,y_velocity";

        public static string Record(
            int traceFrame,
            ushort input,
            IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            ushort x = ReadWord(host, 0xD008);
            ushort y = ReadWord(host, 0xD00C);
            short xVelocity = unchecked((short)ReadWord(host, 0xD010));
            short yVelocity = unchecked((short)ReadWord(host, 0xD012));

            return traceFrame.ToString("X4")
                + "," + input.ToString("X4")
                + "," + x.ToString("X4")
                + "," + y.ToString("X4")
                + "," + unchecked((ushort)xVelocity).ToString("X4")
                + "," + unchecked((ushort)yVelocity).ToString("X4");
        }

        private static ushort ReadWord(IGpgxHost host, int offset)
        {
            return (ushort)(
                (host.ReadMainRamByte(offset) << 8)
                | host.ReadMainRamByte(offset + 1));
        }
    }
}
