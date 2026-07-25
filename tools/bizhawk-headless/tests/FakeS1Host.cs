using System;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Fake S1 host whose Advance() stamps the completed frame into vfc
    /// (0xFE04), then runs the per-advance script with the host itself
    /// so scripts drive RAM, the lag flag and the cumulative lag count
    /// by completed-frame number. Player position is script-controlled
    /// (never auto-stamped) so aux expectations stay byte-exact. Shared
    /// by the S1 run/complete-run runner tests and the CLI tests.
    /// </summary>
    internal sealed class FakeS1Host : IGpgxHost
    {
        private readonly Action<FakeS1Host, int> onAdvance;

        public FakeS1Host(Action<FakeS1Host, int> onAdvance)
        {
            this.onAdvance = onAdvance;
            Ram = new byte[0x10000];
        }

        public byte[] Ram { get; private set; }
        public int CompletedFrame { get; private set; }
        public bool IsLagged { get; set; }
        public int LagCount { get; set; }

        public void ClearButtons()
        {
        }

        public void SetButton(string name, bool pressed)
        {
        }

        public void Advance()
        {
            CompletedFrame++;
            SetU16(0xFE04, (ushort)CompletedFrame);
            if (onAdvance != null)
            {
                onAdvance(this, CompletedFrame);
            }
        }

        public byte ReadMainRamByte(int offset)
        {
            return Ram[offset];
        }

        public void Dispose()
        {
        }

        public void SetU16(int offset, ushort value)
        {
            Ram[offset] = (byte)(value >> 8);
            Ram[offset + 1] = (byte)value;
        }

        public void SetU32(int offset, uint value)
        {
            Ram[offset] = (byte)(value >> 24);
            Ram[offset + 1] = (byte)(value >> 16);
            Ram[offset + 2] = (byte)(value >> 8);
            Ram[offset + 3] = (byte)value;
        }
    }
}
