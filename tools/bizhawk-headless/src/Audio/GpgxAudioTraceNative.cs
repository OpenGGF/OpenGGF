namespace OpenGGF.BizHawk.Headless
{
    public sealed class GpgxAudioTraceNative : IGpgxAudioTraceApi
    {
        private readonly GpgxAudioObserverAdapter adapter;
        private readonly GpgxAudioTraceDrainDepartures drainDepartures;
        private GpgxAudioTraceEvent[] reusableNativeEvents;

        internal GpgxAudioTraceNative(GpgxAudioObserverAdapter adapter)
        {
            if (adapter == null) throw new System.ArgumentNullException("adapter");
            this.adapter = adapter;
            drainDepartures = adapter.BindDeparture<GpgxAudioTraceDrainDepartures>();
            AbiVersion = adapter.AbiVersion();
            EventSize = adapter.EventSize();
            Capacity = adapter.Capacity();
            if (AbiVersion != 2 || EventSize != 32 || Capacity != 65536)
                throw new System.InvalidOperationException("GPGX audio observer API identity differs from v2/32/65536.");
        }

        public uint AbiVersion { get; private set; }
        public uint EventSize { get; private set; }
        public uint Capacity { get; private set; }

        public int Configure(ref GpgxAudioObserverAdapter.Config config, byte[] mask,
            GpgxAudioObserverAdapter.ServiceKind[] kinds,
            GpgxAudioObserverAdapter.ServiceHook[] hooks,
            GpgxAudioObserverAdapter.SnapshotRange[] ranges)
        { return adapter.Configure(ref config, mask, kinds, hooks, ranges); }
        public int BeginFrame() { return adapter.BeginFrame(); }
        public int EndFrame() { return adapter.EndFrame(); }
        public int EventCount(out uint count, out uint overflow)
        { return adapter.EventCount(out count, out overflow); }
        public int Drain(GpgxAudioTraceEvent[] events, uint capacity, out uint count)
        {
            if (capacity == 0) return adapter.Drain(null, 0, out count);
            if (events == null || events.LongLength < capacity)
                throw new System.ArgumentException("The event buffer is shorter than the requested drain capacity.", "events");
            return drainDepartures.gpgx_audio_trace_drain(events, capacity, out count);
        }
        internal int DrainNative(uint capacity, out uint count,
            out GpgxAudioTraceEvent[] events)
        {
            if (capacity == 0)
            {
                events = null;
                return adapter.Drain(null, 0, out count);
            }
            if (reusableNativeEvents == null || reusableNativeEvents.LongLength < capacity)
                reusableNativeEvents = new GpgxAudioTraceEvent[checked((int)capacity)];
            events = reusableNativeEvents;
            return drainDepartures.gpgx_audio_trace_drain(reusableNativeEvents, capacity, out count);
        }
        public int AbortFrame() { return adapter.AbortFrame(); }
        public int BeginPublicationEpoch() { return adapter.BeginPublicationEpoch(); }
        public int Disable() { return adapter.Disable(); }

    }

    public abstract class GpgxAudioTraceDrainDepartures
    {
        [global::BizHawk.BizInvoke.BizImport(System.Runtime.InteropServices.CallingConvention.Cdecl)]
        public abstract int gpgx_audio_trace_drain(
            [System.Runtime.InteropServices.Out] GpgxAudioTraceEvent[] events,
            uint capacity, out uint count);
    }
}
