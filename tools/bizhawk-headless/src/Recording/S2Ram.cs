using System;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Sonic 2 REV01 68K work-RAM address map used by the S2 trace recorder
    /// port (tools/bizhawk/s2_trace_recorder.lua v9.12-s2). Addresses are
    /// the mainmemory-domain form: the $FF0000 base is stripped, so 0xF600
    /// here is $FFF600 on hardware. Typed big-endian reads delegate to the
    /// game-independent helpers on <see cref="S1Ram"/>.
    /// </summary>
    public static class S2Ram
    {
        // Global variables.
        public const int GameMode = 0xF600;              // u8 Game_Mode (0x0C = level)
        public const int Ctrl1Logical = 0xF602;          // u8 held byte / u16be word Ctrl_1_Held_Logical
        public const int Ctrl1Raw = 0xF604;              // u8 Ctrl_1_Held (raw)
        public const int Ctrl2Raw = 0xF606;              // u8 Ctrl_2_Held (raw)
        public const int Ctrl2Held = 0xF66A;             // u8 Ctrl_2_Held_Logical held byte
        public const int Ctrl2Pressed = 0xF66B;          // u8 Ctrl_2_Held_Logical+1 pressed byte
        public const int RingCount = 0xFE20;             // u16be Ring_count
        public const int CameraX = 0xEE00;               // u16be pixel word of Camera_X_pos
        public const int CameraY = 0xEE04;               // u16be pixel word of Camera_Y_pos
        public const int Zone = 0xFE10;                  // u8 Current_Zone
        public const int Act = 0xFE11;                   // u8 Current_Act
        public const int RngSeed = 0xF636;               // u32be RNG_seed
        public const int FrameCount = 0xFE04;            // u16be Level_frame_counter
        public const int VblankWord = 0xFE0E;            // u16be VBlank word (NOT the 0xFE0C longword)

        // Sonic history buffers (Tails CPU delayed input).
        public const int SonicStatRecordBuf = 0xE400;    // 64 x 4-byte entries: input u16be, status u8, pad
        public const int SonicPosRecordBuf = 0xE500;     // 64 x 4-byte entries: x u16be, y u16be
        public const int SonicPosRecordIndex = 0xEED2;   // u16be; only the low byte is used

        // Tails CPU state.
        public const int TailsControlCounter = 0xF702;   // u16be Tails_control_counter
        public const int TailsRespawnCounter = 0xF704;   // u16be Tails_respawn_counter
        public const int TailsCpuRoutine = 0xF708;       // u16be Tails_CPU_routine
        public const int TailsCpuTargetX = 0xF70A;       // u16be
        public const int TailsCpuTargetY = 0xF70C;       // u16be
        public const int TailsInteractId = 0xF70E;       // u8
        public const int TailsCpuJumping = 0xF70F;       // u8

        // ObjPosLoad cursor state.
        public const int OplScreen = 0xF76E;             // u16be v_opl_screen
        public const int OplDataForward = 0xF770;        // u32be v_opl_data forward cursor
        public const int OplDataBackward = 0xF774;       // u32be v_opl_data+4 backward cursor
        public const int ObjStateForwardCounter = 0xFC00;  // u8 v_objstate[0]
        public const int ObjStateBackwardCounter = 0xFC01; // u8 v_objstate[1]

        // CNZ slot machine state (start_rom_zone_id == 0x0C recordings only).
        // Note the gaps: nothing is read at 0xFF50 or 0xFF53.
        public const int SlotMachineInUse = 0xFF4C;      // u16be
        public const int SlotMachineRoutine = 0xFF4E;    // u8
        public const int SlotMachineTimer = 0xFF4F;      // u8
        public const int SlotMachineIndex = 0xFF51;      // u8
        public const int SlotMachineReward = 0xFF52;     // u16be
        public const int SlotMachineSlot1Pos = 0xFF54;   // u16be
        public const int SlotMachineSlot1Speed = 0xFF56; // u8
        public const int SlotMachineSlot1Routine = 0xFF57; // u8
        public const int SlotMachineSlot2Pos = 0xFF58;   // u16be
        public const int SlotMachineSlot2Speed = 0xFF5A; // u8
        public const int SlotMachineSlot2Routine = 0xFF5B; // u8
        public const int SlotMachineSlot3Pos = 0xFF5C;   // u16be
        public const int SlotMachineSlot3Speed = 0xFF5E; // u8
        public const int SlotMachineSlot3Routine = 0xFF5F; // u8

        // Object table (SST): 128 slots of 0x40 bytes at 0xB000 (NOT S1's
        // 0xD000). Slot 0 = Sonic, slot 1 = Tails/sidekick; dynamic slots
        // start at 16 (Dynamic_Object_RAM; S1's start at 32).
        public const int PlayerBase = 0xB000;
        public const int SidekickBase = 0xB040;
        public const int ObjectSlotSize = 0x40;
        public const int TotalObjectSlots = 128;
        public const int FirstDynamicSlot = 16;

        // Per-object offsets from the slot base. Sonic's and Tails' blocks
        // are symmetric: both characters use the same offsets.
        public const int OffId = 0x00;                   // u8 object id (0 = slot empty)
        public const int OffRenderFlags = 0x01;          // u8 render_flags
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
        public const int OffAnimFrame = 0x1B;            // u8 anim frame
        public const int OffAnimId = 0x1C;               // u8 animation id
        public const int OffAnimFrameTimer = 0x1E;       // u8 anim frame timer
        public const int OffStatus = 0x22;               // u8 status flags
        public const int OffRoutine = 0x24;              // u8 routine
        public const int OffRoutineSecondary = 0x25;     // u8 routine_secondary
        public const int OffAngle = 0x26;                // u8 terrain angle
        public const int OffSubtype = 0x28;              // u8 subtype
        public const int OffMoveLock = 0x2E;             // u16be move_lock (S2 delta: S1's is +0x3E)
        public const int OffStandOnObj = 0x3D;           // u8 standonobject (SST index, 0 = none)
        public const int OffTopSolidBit = 0x46;          // u8 top_solid_bit ($0C/$0E)
        public const int OffLrbSolidBit = 0x47;          // u8 lrb_solid_bit ($0D/$0F)

        // Status flag bits (same layout as S1).
        public const int StatusFacingLeft = 0x01;
        public const int StatusInAir = 0x02;
        public const int StatusRolling = 0x04;
        public const int StatusOnObject = 0x08;
        public const int StatusRollJump = 0x10;
        public const int StatusPushing = 0x20;
        public const int StatusUnderwater = 0x40;

        // Player routine values (0x00 init, 0x02 control).
        public const int RoutineHurt = 0x04;
        public const int RoutineDeath = 0x06;

        // ObjB2 (Tornado, SCZ/WFZ route) triggers the s2_tornado_state
        // diagnostic in the object scan.
        public const int TornadoObjectId = 0xB2;

        public static int SlotAddress(int slot)
        {
            return PlayerBase + (slot * ObjectSlotSize);
        }

        public static byte U8(IGpgxHost host, int address)
        {
            return S1Ram.U8(host, address);
        }

        public static sbyte S8(IGpgxHost host, int address)
        {
            return S1Ram.S8(host, address);
        }

        public static ushort U16(IGpgxHost host, int address)
        {
            return S1Ram.U16(host, address);
        }

        public static short S16(IGpgxHost host, int address)
        {
            return S1Ram.S16(host, address);
        }

        public static uint U32(IGpgxHost host, int address)
        {
            return S1Ram.U32(host, address);
        }
    }
}
