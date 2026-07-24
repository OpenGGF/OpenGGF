using System;

namespace OpenGGF.BizHawk.Headless
{
    public interface IGpgxHost : IDisposable
    {
        int CompletedFrame { get; }

        /// <summary>
        /// Whether the most recently completed frame was a lag frame (the
        /// core did not poll input). Port of the Lua recorder's
        /// emu.islagged(); consumed by the S2 special-stage row writer.
        /// </summary>
        bool IsLagged { get; }

        void ClearButtons();
        void SetButton(string name, bool pressed);
        void Advance();
        byte ReadMainRamByte(int offset);
    }
}
