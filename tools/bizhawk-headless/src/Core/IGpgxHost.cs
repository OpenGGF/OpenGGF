using System;

namespace OpenGGF.BizHawk.Headless
{
    public interface IGpgxHost : IDisposable
    {
        int CompletedFrame { get; }
        void ClearButtons();
        void SetButton(string name, bool pressed);
        void Advance();
        byte ReadMainRamByte(int offset);
    }
}
