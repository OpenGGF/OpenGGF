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

        /// <summary>
        /// The cumulative lag-frame count since power-on, read from the
        /// core's own accounting. Port of the Lua recorder's emu.lagcount();
        /// consumed by the S1 complete-run lag_state aux event, whose
        /// fixture values are monotone across the whole pass (boot frames
        /// and inter-segment gaps included).
        /// </summary>
        int LagCount { get; }

        void ClearButtons();
        void SetButton(string name, bool pressed);
        IDisposable RegisterExecuteCallback(uint address, Action callback);
        void Advance();
        byte ReadMainRamByte(int offset);
    }
}
