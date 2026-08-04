namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Deliberately narrow opt-in mutation authority for recorder modes that
    /// must perform a documented ROM setup transition. It is intentionally
    /// separate from IGpgxHost, whose contract is observation-only.
    /// </summary>
    public interface IMainRamWriter
    {
        void WriteMainRamByte(int offset, byte value);
    }
}
