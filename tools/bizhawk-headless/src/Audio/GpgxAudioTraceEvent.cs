using System.Runtime.InteropServices;

namespace OpenGGF.BizHawk.Headless
{
    [StructLayout(LayoutKind.Explicit, Pack = 1, Size = 32)]
    public struct GpgxAudioTraceEvent
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
}
