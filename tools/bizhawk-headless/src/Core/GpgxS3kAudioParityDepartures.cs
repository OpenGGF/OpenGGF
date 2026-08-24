using System.Runtime.InteropServices;
using BizHawk.BizInvoke;

namespace OpenGGF.BizHawk.Headless
{
    [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 64)]
    public struct GpgxS3kAudioParityConfig
    {
        [FieldOffset(0)] public uint Magic;
        [FieldOffset(4)] public ushort AbiVersion;
        [FieldOffset(6)] public ushort StructSize;
        [FieldOffset(8)] public ushort DescriptorSize;
        [FieldOffset(10)] public ushort EventSize;
        [FieldOffset(12)] public uint DescriptorCount;
        [FieldOffset(16)] public uint EventCapacity;
        [FieldOffset(20)] public ushort SongTrackFirst;
        [FieldOffset(22)] public ushort SongTrackEnd;
        [FieldOffset(24)] public ushort SfxTrackFirst;
        [FieldOffset(26)] public ushort SfxTrackEnd;
        [FieldOffset(28)] public ushort TrackSize;
        [FieldOffset(30)] public ushort SongBankAddress;
        [FieldOffset(32)] public byte FixedSfxBank;
    }

    [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 20)]
    public struct GpgxS3kAudioParityDescriptor
    {
        [FieldOffset(0)] public ushort DescriptorId;
        [FieldOffset(2)] public ushort BeginPc;
        [FieldOffset(4)] public ushort EndPc;
        [FieldOffset(6)] public byte BeginOpcode;
        [FieldOffset(7)] public byte EndOpcode;
        [FieldOffset(8)] public byte ExpectedServiceKind;
        [FieldOffset(9)] public byte ExpectedTrackType;
    }

    [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 38)]
    public struct GpgxS3kAudioParityEvent
    {
        [FieldOffset(0)] public uint EventOrdinal;
        [FieldOffset(4)] public uint MasterCycle;
        [FieldOffset(8)] public uint VintOrdinal;
        [FieldOffset(12)] public uint ServiceEntryMasterCycle;
        [FieldOffset(16)] public uint TransactionId;
        [FieldOffset(20)] public ushort ServiceOrdinal;
        [FieldOffset(22)] public ushort Generation;
        [FieldOffset(24)] public ushort TrackBase;
        [FieldOffset(26)] public ushort SourcePointer;
        [FieldOffset(28)] public ushort SourcePc;
        [FieldOffset(30)] public byte ServiceKind;
        [FieldOffset(31)] public byte TrackType;
        [FieldOffset(32)] public byte ChannelId;
        [FieldOffset(33)] public byte Bank;
        [FieldOffset(34)] public byte Chip;
        [FieldOffset(35)] public byte Port;
        [FieldOffset(36)] public byte RegisterId;
        [FieldOffset(37)] public byte Value;
    }

    [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 16)]
    public struct GpgxS3kAudioParityFault
    {
        [FieldOffset(0)] public uint Reason;
        [FieldOffset(4)] public uint Pc;
        [FieldOffset(8)] public uint TransactionId;
        [FieldOffset(12)] public ushort TrackBase;
        [FieldOffset(14)] public byte ServiceKind;
    }

    [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 32)]
    public struct GpgxS3kPcmConfig
    {
        [FieldOffset(0)] public uint Magic;
        [FieldOffset(4)] public ushort AbiVersion;
        [FieldOffset(6)] public ushort StructSize;
        [FieldOffset(8)] public ushort EventSize;
        [FieldOffset(12)] public uint EventCapacity;
    }

    [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 28)]
    public struct GpgxS3kPcmEvent
    {
        [FieldOffset(0)] public uint EventOrdinal;
        [FieldOffset(4)] public uint SampleOrdinal;
        [FieldOffset(8)] public ulong MasterCycle;
        [FieldOffset(16)] public int Left;
        [FieldOffset(20)] public int Right;
        [FieldOffset(24)] public byte Tap;
    }

    public abstract class GpgxS3kAudioParityDepartures
    {
        [BizImport(CallingConvention.Cdecl)] public abstract uint gpgx_s3k_audio_parity_abi_version();
        [BizImport(CallingConvention.Cdecl)] public abstract uint gpgx_s3k_audio_parity_event_size();
        [BizImport(CallingConvention.Cdecl)] public abstract uint gpgx_s3k_audio_parity_capacity();
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_audio_parity_configure(
            ref GpgxS3kAudioParityConfig config, [In] GpgxS3kAudioParityDescriptor[] descriptors);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_audio_parity_begin_frame(uint vintOrdinal);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_audio_parity_end_frame();
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_audio_parity_event_count(out uint count, out uint overflow);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_audio_parity_drain(
            [Out] GpgxS3kAudioParityEvent[] events, uint capacity, out uint count);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_audio_parity_first_fault(out GpgxS3kAudioParityFault fault);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_audio_parity_disable();
    }

    public abstract class GpgxS3kPcmDepartures
    {
        [BizImport(CallingConvention.Cdecl)] public abstract uint gpgx_s3k_pcm_abi_version();
        [BizImport(CallingConvention.Cdecl)] public abstract uint gpgx_s3k_pcm_event_size();
        [BizImport(CallingConvention.Cdecl)] public abstract uint gpgx_s3k_pcm_capacity();
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_pcm_configure(ref GpgxS3kPcmConfig config);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_pcm_begin_frame();
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_pcm_end_frame();
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_pcm_event_count(out uint count, out uint overflow);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_pcm_drain(
            [Out] GpgxS3kPcmEvent[] events, uint capacity, out uint count);
        [BizImport(CallingConvention.Cdecl)] public abstract int gpgx_s3k_pcm_disable();
    }
}
