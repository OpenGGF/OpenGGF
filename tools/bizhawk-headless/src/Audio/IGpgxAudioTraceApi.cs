namespace OpenGGF.BizHawk.Headless
{
    public interface IGpgxAudioTraceApi
    {
        uint AbiVersion { get; }
        uint EventSize { get; }
        uint Capacity { get; }

        int Configure(ref GpgxAudioObserverAdapter.Config config, byte[] mask,
            GpgxAudioObserverAdapter.ServiceKind[] kinds,
            GpgxAudioObserverAdapter.ServiceHook[] hooks,
            GpgxAudioObserverAdapter.SnapshotRange[] ranges);
        int BeginFrame();
        int EndFrame();
        int EventCount(out uint count, out uint overflow);
        int Drain(GpgxAudioTraceEvent[] events, uint capacity, out uint count);
        int GetFirstFault(out GpgxAudioObserverAdapter.FirstFault fault);
        int BeginPublicationEpoch();
        int AbortFrame();
        int Disable();
    }
}
