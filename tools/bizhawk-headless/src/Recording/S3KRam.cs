using System;
using System.Globalization;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Sonic 3 &amp; Knuckles (locked-on combined image) 68K work-RAM
    /// address map used by the S3K trace recorder port
    /// (tools/bizhawk/s3k_trace_recorder.lua v6.32-s3k). Addresses are the
    /// mainmemory-domain form: the $FF0000 base is stripped, so 0xF600
    /// here is $FFF600 on hardware. Typed big-endian reads delegate to
    /// the game-independent helpers on <see cref="S1Ram"/>.
    ///
    /// Both of the mislabeled reads this class used to carry are gone,
    /// fixed at the source in the Lua rather than mirrored here.
    ///
    /// The first was ADDR_FRAMECOUNT, on which the two S3K recorders used
    /// to disagree - the standard recorder read 0xFE08
    /// (Debug_placement_mode, dead-zero outside debug mode) while the
    /// complete-run recorder read 0xFE04 - so this class carried both
    /// addresses and the writers forked on recorder identity. Lua
    /// v6.31-s3k moved the standard recorder to 0xFE04 as well, so
    /// <see cref="LevelFrameCounter"/> is now the single ADDR_FRAMECOUNT
    /// for every S3K profile and the fork is deleted.
    ///
    /// The second was ADDR_VBLA_WORD, which both recorders pointed at
    /// 0xFE12 = skdisasm Life_count, so the CSV vblank_counter column
    /// carried lives &lt;&lt; 8 - a value that changes only on a 1UP, not a
    /// V-int counter at all. Lua v6.32-s3k / v6.33-s3k-completerun moved it
    /// to <see cref="VblankWord"/> 0xFE0E, the low word of the ds.l
    /// V_int_run_count at 0xFE0C, which is the same address S1Ram and
    /// S2Ram have always read. Every S3K fixture was regenerated against
    /// the corrected read, so nothing here is reproduced verbatim-wrong any
    /// more.
    /// </summary>
    public static class S3KRam
    {
        // Global variables (core capture path).
        public const int GameMode = 0xF600;              // u8 Game_mode; level family = (mode & 0x0F) == 0x0C
        public const int Ctrl1Logical = 0xF602;          // u16be Ctrl_1_logical (held+pressed bytes)
        public const int Ctrl1Raw = 0xF604;              // u8 Ctrl_1_held (raw; CSV-input fallback only)
        public const int Ctrl1Locked = 0xF7CA;           // u8 Ctrl_1_locked
        public const int Ctrl2Locked = 0xF7CB;           // u8 Ctrl_2_locked
        public const int Ctrl2Logical = 0xF66A;          // u16be Ctrl_2_logical word read
        public const int Ctrl2Held = 0xF66A;             // u8 Ctrl_2_held_logical
        public const int Ctrl2Pressed = 0xF66B;          // u8 Ctrl_2_pressed_logical
        public const int RingCount = 0xFE20;             // u16be Ring_count
        public const int CameraX = 0xEE78;               // u16be pixel word of Camera_X_pos
        public const int CameraY = 0xEE7C;               // u16be pixel word of Camera_Y_pos
        public const int CameraMinX = 0xEE14;            // u16be Camera_min_X_pos
        public const int CameraMaxX = 0xEE16;            // u16be Camera_max_X_pos
        public const int CameraXCopy = 0xEE80;           // u16be Camera_X_pos_copy pixel word
        public const int CameraYCopy = 0xEE84;           // u16be Camera_Y_pos_copy pixel word
        public const int CameraYBgCopy = 0xEE90;         // u32be Camera_Y_pos_BG_copy (16.16)
        public const int CameraYBgRounded = 0xEE96;      // u16be Camera_Y_pos_BG_rounded
        public const int CameraMaxY = 0xEE1A;            // u16be Camera_max_Y_pos (cnz_event_ram only)
        public const int CameraTargetMaxY = 0xEE12;      // u16be Camera_max_Y_pos target (cnz_event_ram only)
        public const int Zone = 0xFE10;                  // u8 Current_zone (also u16be zone<<8|act read)
        public const int Act = 0xFE11;                   // u8 Current_act
        public const int ApparentAct = 0xEE4F;           // u8 apparent act
        public const int PlayerMode = 0xFF08;            // u16be Player_mode
        public const int EventsRoutineBg = 0xEEC2;       // u16be Events_routine_bg
        public const int EventsFg5 = 0xEEC6;             // u16be Events_fg_5
        public const int EventsBg00 = 0xEED2;            // u16be Events_bg+$00
        public const int EventsBg02 = 0xEED4;            // u16be Events_bg+$02
        public const int BackgroundCollisionFlag = 0xF664; // u8 (cnz_event_ram only)
        public const int LevelStartedFlag = 0xF711;      // u8 level-started flag
        public const int LevelFrameCounter = 0xFE04;     // u16be Level_frame_counter - ADDR_FRAMECOUNT for BOTH S3K recorders: CSV gameplay_frame_counter, every aux "vfc", oscillation_state.level_frame_counter
        public const int VblankWord = 0xFE0E;            // u16be low word of V_int_run_count (the ds.l at 0xFE0C) - CSV vblank_counter; was 0xFE12 = Life_count until Lua v6.32-s3k (see class doc)
        public const int LagFrameCount = 0xF628;         // u16be Lag_frame_count - CSV lag_counter (a REAL read, unlike S2's constant 0)
        public const int RngSeed = 0xF636;               // u32be RNG_seed
        public const int OscTable = 0xFE6E;              // Oscillating_table: control word + 16 (value,delta) pairs
        public const int OscTableSize = 0x42;            // 66 bytes total
        public const int PosTableIndex = 0xEE26;         // u16be Pos_table_index

        // Collision response list (count word = payload BYTE count, then
        // word OST addresses from 0xE382).
        public const int CollisionResponseList = 0xE380;
        public const int CollisionResponseEntries = 0xE382;
        public const int CollisionResponseCountClamp = 0x7E;

        // Tails CPU delay buffers (hook-only paths; never read in
        // lightweight mode).
        public const int StatTable = 0xE400;
        public const int PosTable = 0xE500;

        // Tails CPU global block (cpu_state / cpu_state_snapshot).
        public const int TailsCpuInteract = 0xF700;      // u16be RAM addr of object Tails stood on
        public const int TailsCpuIdleTimer = 0xF702;     // u16be (legacy snapshot name control_counter)
        public const int TailsCpuFlightTimer = 0xF704;   // u16be (legacy respawn_counter)
        public const int TailsCpuRoutine = 0xF708;       // u16be
        public const int TailsCpuTargetX = 0xF70A;       // u16be
        public const int TailsCpuTargetY = 0xF70C;       // u16be
        public const int TailsCpuAutoFlyTimer = 0xF70E;  // u8 (legacy snapshot name interact_id)
        public const int TailsCpuAutoJumpFlag = 0xF70F;  // u8 (legacy jumping)

        // aiz_handoff_terrain_state poll extras.
        public const int DrawDelayedPosition = 0xEEC8;   // u16be
        public const int DrawDelayedRowCount = 0xEECA;   // u16be
        public const int DynamicResizeRoutine = 0xEE33;  // u8
        public const int ObjectLoadRoutine = 0xF76C;     // u8
        public const int RingsManagerRoutine = 0xF710;   // u8
        // Kosinski queue owners. These are adjacent in the phased
        // $FFFF0000 RAM namespace declared by sonic3k.constants.asm.
        // See Kos_decomp_queue_count at lines 866 and
        // Kos_modules_left/Kos_module_queue at lines 896-909.
        public const int KosDecompQueueCount = 0xFF0E;   // u16be: bit 15 busy, low 15 queued direct streams
        public const int KosModulesLeft = 0xFF60;        // u8: bit 7 busy, low 7 modules remaining
        public const int KosLastModuleSize = 0xFF62;     // u16be words
        public const int KosModuleQueue = 0xFF64;        // four source:u32be,destination:u16be entries
        public const int KosModuleSource = 0xFF64;       // active entry source alias
        public const int KosModuleDestination = 0xFF68;  // active entry destination alias
        public const int KosModuleQueueEntrySize = 6;
        public const int KosModuleQueueCapacity = 4;
        // Schema-6 aiz_handoff_terrain_state accidentally sampled this
        // unrelated byte. Keep that published diagnostic byte stable while
        // the authoritative schema-7 timing recorder uses KosModulesLeft.
        public const int LegacyAizHandoffKosModulesLeft = 0xFF04;

        // Object table (OST): 110 slots of 0x4A bytes at 0xB000
        // (Player_1). NOT S2's 0x40 stride. Slot 1 = Player_2 (0xB04A);
        // dynamic objects at slots 3..92; fixed Breathing_bubbles slots
        // 94 (0xCB2C) and 95 (0xCB76).
        public const int PlayerBase = 0xB000;
        public const int SidekickBase = 0xB04A;
        public const int ObjectSlotSize = 0x4A;
        public const int TotalObjectSlots = 110;
        public const int FirstDynamicSlot = 3;
        public const int DynamicSlotCount = 90;
        public const int BreathingBubblesSlot = 94;
        public const int BreathingBubblesP2Slot = 95;

        // Per-object offsets from the slot base - the S3K layout (do NOT
        // reuse S2 offsets: S2 status is +0x22; S3K's is +0x2A).
        // Positions are 32-bit: high word pixel, low word subpixel.
        public const int OffObjectCode = 0x00;           // u32be routine pointer (0 = slot empty)
        public const int OffRenderFlags = 0x04;          // u8 render_flags
        public const int OffRoutine = 0x05;              // u8 routine byte
        public const int OffHeightPixels = 0x06;         // u8 height_pixels
        public const int OffWidthPixels = 0x07;          // u8 width_pixels
        public const int OffXPos = 0x10;                 // u16be x_pos pixel word
        public const int OffXSub = 0x12;                 // u16be x_sub
        public const int OffYPos = 0x14;                 // u16be y_pos pixel word
        public const int OffYSub = 0x16;                 // u16be y_sub
        public const int OffXVel = 0x18;                 // s16be x_vel
        public const int OffYVel = 0x1A;                 // s16be y_vel
        public const int OffInertia = 0x1C;              // s16be ground_vel
        public const int OffRadiusY = 0x1E;              // u8/s8 y_radius
        public const int OffRadiusX = 0x1F;              // u8/s8 x_radius
        public const int OffAnimId = 0x20;               // u8 anim
        public const int OffMappingFrame = 0x22;         // u8 mapping_frame
        public const int OffAnimFrame = 0x23;            // u8 anim_frame
        public const int OffAnimFrameTimer = 0x24;       // u8 anim_frame_timer
        public const int OffAngle = 0x26;                // u8 angle
        public const int OffCollisionFlags = 0x28;       // u8 collision_flags
        public const int OffCollisionProperty = 0x29;    // u8 collision_property
        public const int OffStatus = 0x2A;               // u8 status (S3K delta: S1/S2 use +0x22)
        public const int OffStatusSecondary = 0x2B;      // u8 status_secondary
        public const int OffSubtype = 0x2C;              // u8 subtype (objects) / air_left (players)
        public const int OffObjectControl = 0x2E;        // u8 object_control
        public const int OffMoveLock = 0x32;             // u16be move_lock (CNZ balloon reuses as base_y)
        public const int OffInvulnerabilityTimer = 0x34; // u8
        public const int OffParentPtr = 0x40;            // u32be parent/owner pointer
        public const int OffInteract = 0x42;             // u16be interact: RAM addr of stood-on object
        public const int OffTopSolidBit = 0x46;          // u8 top_solid_bit
        public const int OffLrbSolidBit = 0x47;          // u8 lrb_solid_bit

        // Status flag bits (player blocks): bit 3 = P1 standing on
        // objects, bit 4 = roll-jump / P2 standing on objects.
        public const int StatusFacingLeft = 0x01;
        public const int StatusInAir = 0x02;
        public const int StatusRolling = 0x04;
        public const int StatusOnObject = 0x08;
        public const int StatusRollJump = 0x10;
        public const int StatusPushing = 0x20;
        public const int StatusUnderwater = 0x40;

        // Player routine values that trigger extra state_snapshot events.
        public const int RoutineHurt = 0x04;
        public const int RoutineDeath = 0x06;

        // Object codes referenced by the core (lightweight) capture path.
        public const uint ObjCnzBalloon = 0x00031754;         // pre-trace snapshot id 0x41
        public const uint ObjAirCountdown = 0x00018164;       // Obj_AirCountdown child
        public const uint ObjCnzWireCageInit = 0x00033836;    // cage_state (either entry matches)
        public const uint ObjCnzWireCagePerFrame = 0x0003385E;
        public const uint ObjCnzCylinder = 0x00032188;
        public const uint ObjClamerSpringFire = 0x000890AA;   // loc_890AA_fire
        public const uint ObjClamerSpringCooldown = 0x000890C8; // loc_890C8_cooldown
        public const uint ObjClamerSpringReset = 0x000890D0;  // loc_890D0_reset
        public const int CnzBalloonSnapshotId = 0x41;

        // Complete-run recorder additions (s3k_complete_run_recorder.lua
        // v6.32; spec docs/s3k-complete-run-behavior.md §5). The STANDARD
        // recorder reads none of these. ADDR_FRAMECOUNT is NOT among them
        // any more: it is LevelFrameCounter (0xFE04) in the global block
        // above and is now shared by both recorders.
        public const int VIntRunCount = 0xFE0C;          // u32be free-running V-int counter; bonus-segment metadata only. VblankWord (0xFE0E) is this longword's LOW WORD
        public const int CurrentSpecialStage = 0xFE16;   // u8 Current_special_stage -> special_stage_index

        // Game_paused is declared ds.w 1 (sonic3k.constants.asm:564) and
        // the ROM writes move.w #1,(Game_paused).w, so the complete-run
        // recorder's game_paused_state family reads a WORD here: a byte
        // read of the high half would render the 0x0001 paused state as 0.
        public const int GamePaused = 0xF63A;            // u16be Game_paused

        // Raw (pre-logical) pad-2 byte. NOT Ctrl2Held/Ctrl2Logical
        // (0xF66A): the SS row writer's input_p2 fallback reads the raw
        // Ctrl_2_held byte, matching the Lua's ADDR_CTRL2.
        public const int Ctrl2Raw = 0xF606;              // u8 Ctrl_2_held (SS input_p2 fallback)

        // Blue-spheres special-stage state block. Read ONLY by the
        // complete-run recorder's 20-column s3k_special_stage row writer
        // (write_ss_row, Lua 5174); the level/bonus schema touches none of
        // it. Column order in the CSV is the blue-spheres plan's TABLE
        // order, not address order — SsRateTimer (0xE43E) is emitted AFTER
        // SsRate (0xE444).
        public const int SsAnimFrame = 0xE420;           // u16be Special_stage_anim_frame
        public const int SsXPos = 0xE422;                // u16be Special_stage_X_pos
        public const int SsYPos = 0xE424;                // u16be Special_stage_Y_pos
        public const int SsAngle = 0xE426;               // u8 Special_stage_angle
        public const int SsVelocity = 0xE428;            // s16be Special_stage_velocity
        public const int SsTurning = 0xE42A;             // u8 Special_stage_turning
        public const int SsJumping = 0xE432;             // u8 Special_stage_jumping
        public const int SsFadeTimer = 0xE433;           // u8 Special_stage_fade_timer
        public const int SsSpheresLeft = 0xE438;         // u16be Special_stage_spheres_left
        public const int SsRingCount = 0xE43A;           // u16be Special_stage_ring_count
        public const int SsRateTimer = 0xE43E;           // u16be Special_stage_rate_timer
        public const int SsRingsLeft = 0xE442;           // u16be Special_stage_rings_left
        public const int SsRate = 0xE444;                // u16be Special_stage_rate
        public const int SsClearTimer = 0xE44A;          // u16be Special_stage_clear_timer
        public const int SsClearRoutine = 0xE44C;        // u8 Special_stage_clear_routine
        public const int SsStarted = 0xE450;             // u8 Special_stage_started (rendered 0/1)
        public const int LastStarPostHit = 0xFE2A;       // u8 transition field
        public const int SavedXPos = 0xFE2E;             // u16be transition field
        public const int SavedYPos = 0xFE30;             // u16be transition field
        public const int SpecialBonusEntryFlag = 0xFE48; // u8 transition field
        public const int EmeraldCount = 0xFFB0;          // u8 emeralds_before/emeralds_after

        // Game modes. The engine ORs $40/$80 during level-load handoff, so
        // the level family accepts $0C/$4C/$8C via the masked check.
        public const int GameModeSega = 0x00;
        public const int GameModeTitle = 0x04;
        public const int GameModeLevelSelect = 0x28;
        public const int GameModeLevel = 0x0C;
        public const int GameModeSpecialStage = 0x34;    // blue-spheres SS; its own CSV schema
        public const int GameModeSpecialStageResults = 0x48;
        public const int GameModeMask = 0x0F;

        public static bool IsLevelFamilyMode(int gameMode)
        {
            return (gameMode & GameModeMask) == GameModeLevel;
        }

        /// <summary>
        /// Zone-id to metadata zone-name mapping (Lua zone_name table);
        /// unknown ids render as unknown_%02x with LOWERCASE hex.
        /// </summary>
        public static string ZoneName(int zoneId)
        {
            switch (zoneId)
            {
                case 0x00: return "aiz";
                case 0x01: return "hcz";
                case 0x02: return "mgz";
                case 0x03: return "cnz";
                case 0x04: return "fbz";
                case 0x05: return "icz";
                case 0x06: return "lbz";
                case 0x07: return "mhz";
                case 0x08: return "soz";
                case 0x09: return "lrz";
                case 0x0A: return "ssz";
                case 0x0B: return "dez";
                case 0x0C: return "ddz";
                case 0x0D: return "hpz";
                default:
                    return "unknown_" + (zoneId & 0xFF).ToString(
                        "x2", CultureInfo.InvariantCulture);
            }
        }

        public static int SlotAddress(int slot)
        {
            return PlayerBase + (slot * ObjectSlotSize);
        }

        /// <summary>
        /// Lua interact_addr_to_slot: an interact word maps to an OST slot
        /// index only when it is EXACTLY 0xB000 + k*0x4A for k &lt; 110;
        /// zero, out-of-range, and misaligned addresses all resolve to 0.
        /// </summary>
        public static int InteractAddrToSlot(int addr)
        {
            if (addr == 0)
            {
                return 0;
            }
            int maxAddr = PlayerBase + (TotalObjectSlots * ObjectSlotSize);
            if (addr < PlayerBase || addr >= maxAddr)
            {
                return 0;
            }
            int delta = addr - PlayerBase;
            if ((delta % ObjectSlotSize) != 0)
            {
                return 0;
            }
            return delta / ObjectSlotSize;
        }

        /// <summary>
        /// Reads the interact word at <paramref name="baseAddress"/>+0x42
        /// and resolves it to an OST slot index (CSV *_stand_on_obj).
        /// </summary>
        public static int ReadStandOnSlot(IGpgxHost host, int baseAddress)
        {
            return InteractAddrToSlot(
                U16(host, baseAddress + OffInteract));
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
