using System;
using System.Reflection;
using System.Runtime.InteropServices;
using BizHawk.BizInvoke;
using BizHawk.Common;

namespace OpenGGF.BizHawk.Headless
{
    public sealed class GpgxAudioObserverAdapter
    {
        [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 64)]
        public struct Config
        {
            [FieldOffset(0)] public uint Magic;
            [FieldOffset(4)] public ushort AbiVersion;
            [FieldOffset(6)] public ushort StructSize;
            [FieldOffset(8)] public ushort HookSize;
            [FieldOffset(10)] public ushort RangeSize;
            [FieldOffset(12)] public ushort EventSize;
            [FieldOffset(14)] public byte MaxDepth;
            [FieldOffset(15)] public byte MaxOpcodeBytes;
            [FieldOffset(16)] public ushort ResetServiceKind;
            [FieldOffset(18)] public ushort MaxContinuationFrames;
            [FieldOffset(20)] public uint Flags;
            [FieldOffset(24)] public uint WatchMaskBytes;
            [FieldOffset(28)] public uint HookCount;
            [FieldOffset(32)] public uint RangeCount;
            [FieldOffset(36)] public uint SnapshotBytesTotal;
            [FieldOffset(40)] public uint EventCapacity;
            [FieldOffset(44)] public uint MaxServiceTokensPerFrame;
            [FieldOffset(48)] public ushort KindSize;
            [FieldOffset(50)] public ushort KindCount;
            [FieldOffset(52)] public uint Reserved0;
            [FieldOffset(56)] public uint Reserved1;
            [FieldOffset(60)] public uint Reserved2;
        }

        [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 16)]
        public struct ServiceKind
        {
            [FieldOffset(0)] public byte KindId;
            [FieldOffset(1)] public byte Flags;
            [FieldOffset(2)] public ushort CancellationRangeFirst;
            [FieldOffset(4)] public ushort CancellationRangeCount;
            [FieldOffset(6)] public byte ContinuationFrameLimit;
            [FieldOffset(7)] public byte Reserved0;
            [FieldOffset(8)] public uint Reserved1;
            [FieldOffset(12)] public uint Reserved2;
        }

        [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 32)]
        public struct ServiceHook
        {
            [FieldOffset(0)] public ushort HookToken;
            [FieldOffset(2)] public byte Action;
            [FieldOffset(3)] public byte Cpu;
            [FieldOffset(4)] public uint Pc;
            [FieldOffset(8)] public byte ServiceKindId;
            [FieldOffset(9)] public byte ExpectedActiveKind;
            [FieldOffset(10)] public byte Flags;
            [FieldOffset(11)] public byte OpcodeLength;
            [FieldOffset(12)] public ushort RangeFirst;
            [FieldOffset(14)] public ushort RangeCount;
            [FieldOffset(16)] public ulong Opcode;
            [FieldOffset(24)] public ulong Reserved;
        }

        [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 16)]
        public struct SnapshotRange
        {
            [FieldOffset(0)] public ushort RangeId;
            [FieldOffset(2)] public ushort Start;
            [FieldOffset(4)] public ushort Length;
            [FieldOffset(6)] public ushort Flags;
            [FieldOffset(8)] public uint Reserved0;
            [FieldOffset(12)] public uint Reserved1;
        }

        [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 32)]
        public struct Event
        {
            [FieldOffset(0)] public uint Ordinal;
            [FieldOffset(4)] public ushort ServiceToken;
            [FieldOffset(6)] public ushort ParentToken;
            [FieldOffset(8)] public uint Pc;
            [FieldOffset(12)] public ushort Subject;
            [FieldOffset(14)] public ushort Offset;
            [FieldOffset(16)] public byte Kind;
            [FieldOffset(17)] public byte ServiceKindId;
            [FieldOffset(18)] public byte Depth;
            [FieldOffset(19)] public byte SourceCpu;
            [FieldOffset(20)] public byte PayloadLength;
            [FieldOffset(21)] public byte Value;
            [FieldOffset(22)] public byte Flags;
            [FieldOffset(23)] public byte Reserved;
            [FieldOffset(24)] public ulong Payload;
        }

        private readonly GpgxAudioObserverDepartures departures;
        private readonly IImportResolver resolver;
        private readonly IMonitor monitor;
        private readonly ICallbackAdjuster adjuster;

        internal GpgxAudioObserverAdapter(object gpgx)
        {
            FieldInfo field = gpgx.GetType().GetField("_elf", BindingFlags.Instance | BindingFlags.NonPublic);
            if (field == null) throw new InvalidOperationException("Pinned GPGX Waterbox host field is absent.");
            object waterbox = field.GetValue(gpgx);
            resolver = waterbox as IImportResolver;
            monitor = waterbox as IMonitor;
            adjuster = waterbox as ICallbackAdjuster;
            if (resolver == null || monitor == null || adjuster == null)
                throw new InvalidOperationException("Pinned GPGX Waterbox reflection contracts are absent.");
            departures = BizInvoker.GetInvoker<GpgxAudioObserverDepartures>(resolver, monitor,
                CallingConventionAdapters.MakeWaterboxDepartureOnly(adjuster));
        }

        internal T BindDeparture<T>() where T : class
        {
            return BizInvoker.GetInvoker<T>(resolver, monitor,
                CallingConventionAdapters.MakeWaterboxDepartureOnly(adjuster));
        }

        internal uint AbiVersion() { return departures.gpgx_audio_trace_abi_version(); }
        internal uint EventSize() { return departures.gpgx_audio_trace_event_size(); }
        internal uint Capacity() { return departures.gpgx_audio_trace_capacity(); }
        internal int Configure(ref Config config, byte[] mask, ServiceKind[] kinds,
            ServiceHook[] hooks, SnapshotRange[] ranges)
        {
            RequireExactLength(mask, config.WatchMaskBytes, "watch mask");
            RequireExactLength(kinds, config.KindCount, "service kind table");
            RequireExactLength(hooks, config.HookCount, "service hook table");
            RequireExactLength(ranges, config.RangeCount, "snapshot range table");
            return departures.gpgx_audio_trace_configure(ref config, mask, kinds, hooks, ranges);
        }
        internal int BeginFrame() { return departures.gpgx_audio_trace_begin_frame(); }
        internal int EndFrame() { return departures.gpgx_audio_trace_end_frame(); }
        internal int EventCount(out uint count, out uint overflow)
        { return departures.gpgx_audio_trace_event_count(out count, out overflow); }
        internal int Drain(Event[] events, uint capacity, out uint count)
        {
            if (capacity == 0)
            {
                if (events != null && events.Length != 0)
                    throw new ArgumentException("A zero-capacity drain requires a null or empty event buffer.", "events");
                events = null;
            }
            else if (events == null || (ulong)events.LongLength < capacity)
            {
                throw new ArgumentException("The event buffer is shorter than the requested drain capacity.", "events");
            }
            return departures.gpgx_audio_trace_drain(events, capacity, out count);
        }
        internal int AbortFrame() { return departures.gpgx_audio_trace_abort_frame(); }
        internal int BeginPublicationEpoch() { return departures.gpgx_audio_trace_begin_publication_epoch(); }
        internal int Disable() { return departures.gpgx_audio_trace_disable(); }

        private static void RequireExactLength(Array value, uint expected, string name)
        {
            if (value == null || (ulong)value.LongLength != expected)
                throw new ArgumentException(name + " length must exactly match the frozen configuration count.", name);
        }
    }

    public abstract class GpgxAudioObserverDepartures
    {
        [BizImport(CallingConvention.Cdecl)] public abstract uint gpgx_audio_trace_abi_version();
        [BizImport(CallingConvention.Cdecl)] public abstract uint gpgx_audio_trace_event_size();
        [BizImport(CallingConvention.Cdecl)] public abstract uint gpgx_audio_trace_capacity();
        [BizImport(CallingConvention.Cdecl)]
        public abstract int gpgx_audio_trace_configure(ref GpgxAudioObserverAdapter.Config config,
            [In] byte[] mask, [In] GpgxAudioObserverAdapter.ServiceKind[] kinds,
            [In] GpgxAudioObserverAdapter.ServiceHook[] hooks,
            [In] GpgxAudioObserverAdapter.SnapshotRange[] ranges);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_audio_trace_begin_frame();
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_audio_trace_end_frame();
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_audio_trace_event_count(out uint count, out uint overflow);
        [BizImport(CallingConvention.Cdecl)]
        public abstract int gpgx_audio_trace_drain([Out] GpgxAudioObserverAdapter.Event[] events,
            uint capacity, out uint count);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_audio_trace_abort_frame();
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_audio_trace_begin_publication_epoch();
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_audio_trace_disable();
    }
}
