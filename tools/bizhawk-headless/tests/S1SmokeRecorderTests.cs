using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S1SmokeRecorderTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1SmokeRecorder decodes big-endian RAM words as uppercase hex",
                DecodesBigEndianRamWordsAsUppercaseHex));
        }

        private static void DecodesBigEndianRamWordsAsUppercaseHex()
        {
            var host = new RamHost();
            host.Ram[0xD008] = 0x09;
            host.Ram[0xD009] = 0xA5;
            host.Ram[0xD00C] = 0x02;
            host.Ram[0xD00D] = 0xAA;
            host.Ram[0xD010] = 0x02;
            host.Ram[0xD011] = 0x72;
            host.Ram[0xD012] = 0xFF;
            host.Ram[0xD013] = 0x80;

            AssertEx.Equal(
                "frame,input,x,y,x_velocity,y_velocity",
                S1SmokeRecorder.Header);
            AssertEx.Equal(
                "03E7,0008,09A5,02AA,0272,FF80",
                S1SmokeRecorder.Record(999, 0x0008, host));
        }

        private sealed class RamHost : IGpgxHost
        {
            public RamHost()
            {
                Ram = new byte[65536];
            }

            public byte[] Ram { get; private set; }

            public int CompletedFrame
            {
                get { return 0; }
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
    }
}
