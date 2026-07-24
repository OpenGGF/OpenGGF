using System;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Typed big-endian reads over <see cref="IGpgxHost.ReadMainRamByte"/>
    /// plus the Sonic 1 REV01 68K work-RAM address map used by the trace
    /// recorder. Addresses are the mainmemory-domain form: the $FF0000 base
    /// is stripped, so 0xF600 here is $FFF600 on hardware.
    /// </summary>
    public static class S1Ram
    {
        // Global variables.
        public const int GameMode = 0xF600;              // u8 v_gamemode
        public const int RingCount = 0xFE20;             // u16be ring count
        public const int CameraX = 0xF700;               // u16be pixel word of v_screenposx
        public const int CameraY = 0xF704;               // u16be pixel word of v_screenposy
        public const int Zone = 0xFE10;                  // u8 v_zone
        public const int Act = 0xFE11;                   // u8 v_act
        public const int Random = 0xF636;                // u32be v_random
        public const int FrameCount = 0xFE04;            // u16be v_framecount
        public const int VblankWord = 0xFE0E;            // u16be VBlank word (NOT 0xFE0C)

        // Special-stage state (s1_complete_run_recorder.lua run mode).
        public const int SsAngle = 0xF780;               // u16be v_ssangle
        public const int SsRotate = 0xF782;              // u16be v_ssrotate
        public const int SsBgAnim = 0xF7A0;              // u16be v_ssbganim
        public const int Emeralds = 0xFE57;              // u8 v_emeralds (count 0-6)
        public const int LastSpecial = 0xFE16;           // u8 v_lastspecial (0-5)

        // ObjPosLoad cursor state.
        public const int OplScreen = 0xF76E;             // u16be v_opl_screen
        public const int OplDataForward = 0xF770;        // u32be v_opl_data forward cursor
        public const int OplDataBackward = 0xF774;       // u32be v_opl_data+4 backward cursor
        public const int ObjStateForwardCounter = 0xFC00;  // u8 v_objstate[0]
        public const int ObjStateBackwardCounter = 0xFC01; // u8 v_objstate[1]

        // Complete-run per-frame diagnostic state (s1_complete_run_recorder.lua).
        public const int ObjState = 0xFC00;              // byte[0xC0] v_objstate respawn-bit array
        public const int ObjStateSize = 0xC0;            // ds.b $C0 (FFFC00..FFFCC0)
        public const int Limitbtm1 = 0xF726;             // u16be v_limitbtm1
        public const int Limitbtm2 = 0xF72E;             // u16be v_limitbtm2
        public const int Lookshift = 0xF73E;             // u16be v_lookshift
        public const int BgScrollVert = 0xF75C;          // u8 f_bgscrollvert
        public const int Oscillate = 0xFE5E;             // u16be v_oscillate + byte[0x40] values
        public const int OscillateSize = 0x42;           // $2 bitfield word + $40 values array

        // Object table (SST): 128 slots of 0x40 bytes; slot 0 is the player.
        public const int PlayerBase = 0xD000;
        public const int ObjectSlotSize = 0x40;
        public const int TotalObjectSlots = 128;
        public const int FirstDynamicSlot = 32;

        // Per-object offsets from the slot base.
        public const int OffRenderFlags = 0x01;          // u8 obRender
        public const int OffXPos = 0x08;                 // u16be centre X pixel
        public const int OffXSub = 0x0A;                 // u16be X subpixel
        public const int OffYPos = 0x0C;                 // u16be centre Y pixel
        public const int OffYSub = 0x0E;                 // u16be Y subpixel
        public const int OffXVel = 0x10;                 // s16be X velocity
        public const int OffYVel = 0x12;                 // s16be Y velocity
        public const int OffInertia = 0x14;              // s16be ground speed
        public const int OffRadiusY = 0x16;              // s8 Y radius
        public const int OffRadiusX = 0x17;              // s8 X radius
        public const int OffMappingFrame = 0x1A;         // u8 displayed mapping frame
        public const int OffAnimId = 0x1C;               // u8 animation ID
        public const int OffStatus = 0x22;               // u8 status flags
        public const int OffRoutine = 0x24;              // u8 obRoutine
        public const int OffRoutine2 = 0x25;             // u8 ob2ndRout (secondary routine)
        public const int OffAngle = 0x26;                // u8 terrain angle
        public const int OffSubtype = 0x28;              // u8 subtype
        public const int OffStandOnObj = 0x3D;           // u8 standonobject (SST index)
        public const int OffCtrlLock = 0x3E;             // u16be obCtrlLock

        // Status flag bits.
        public const int StatusFacingLeft = 0x01;
        public const int StatusInAir = 0x02;
        public const int StatusRolling = 0x04;
        public const int StatusOnObject = 0x08;
        public const int StatusRollJump = 0x10;
        public const int StatusPushing = 0x20;
        public const int StatusUnderwater = 0x40;

        public static int SlotAddress(int slot)
        {
            return PlayerBase + (slot * ObjectSlotSize);
        }

        public static byte U8(IGpgxHost host, int address)
        {
            return host.ReadMainRamByte(address);
        }

        public static sbyte S8(IGpgxHost host, int address)
        {
            return unchecked((sbyte)host.ReadMainRamByte(address));
        }

        public static ushort U16(IGpgxHost host, int address)
        {
            return (ushort)(
                (host.ReadMainRamByte(address) << 8)
                | host.ReadMainRamByte(address + 1));
        }

        public static short S16(IGpgxHost host, int address)
        {
            return unchecked((short)U16(host, address));
        }

        public static uint U32(IGpgxHost host, int address)
        {
            return (uint)(
                ((uint)host.ReadMainRamByte(address) << 24)
                | ((uint)host.ReadMainRamByte(address + 1) << 16)
                | ((uint)host.ReadMainRamByte(address + 2) << 8)
                | host.ReadMainRamByte(address + 3));
        }
    }
}
