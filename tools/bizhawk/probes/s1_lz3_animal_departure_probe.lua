local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

-- Sonic 1 World REV01 RAM (docs/s1disasm/_Variables.asm, sonic.lst symbol table).
local V_GAMEMODE = 0xF600
local V_BOSSSTATUS = 0xF7A7
local V_FRAMECOUNT = 0xFE04
local V_VBLANK_BYTE = 0xFE0F
local V_ZONE = 0xFE10
local V_ACT = 0xFE11

local OBJECT_RAM = 0xD000
local OBJECT_SIZE = 0x40
local OB_RENDER = 0x01
local OB_X = 0x08
local OB_ROUTINE = 0x24
local OB_VEL_X = 0x10
local ANIMAL_ID = 0x30

-- LZ3 is the only capsule act whose recorded PLC 16 the engine misses, so the
-- stage gate names it directly. v_act is zero-based, so act 3 reads 2. The
-- movie-frame floor keeps a transition tail from matching on zone/act bytes
-- that are already written while the previous segment is still running; lz3
-- begins at bk2 frame 148410 in this run's manifest.
local LZ_ZONE = 1
local ACT3 = 2
local MOVIE_FRAME_FLOOR = 148000

local function register(name)
    return emu.getregister(name) or 0
end

local function pointer(registerName)
    return register("M68K " .. registerName) & 0xFFFF
end

local function slotFor(ptr)
    local delta = ptr - OBJECT_RAM
    if delta < 0 or delta % OBJECT_SIZE ~= 0 then return -1 end
    return math.floor(delta / OBJECT_SIZE)
end

local function signedWord(value)
    if value >= 0x8000 then return value - 0x10000 end
    return value
end

local function animalSummary(ptr)
    return string.format(
        "slot=%d id=%d routine=%02X x=%d velX=%d render=%02X",
        slotFor(ptr),
        mainmemory.read_u8(ptr + ANIMAL_ID),
        mainmemory.read_u8(ptr + OB_ROUTINE),
        mainmemory.read_u16_be(ptr + OB_X),
        signedWord(mainmemory.read_u16_be(ptr + OB_VEL_X)),
        mainmemory.read_u8(ptr + OB_RENDER))
end

local function clocks()
    return string.format("movieFrame=%d frameCount=%d vblankByte=%02X bit4=%d",
        emu.framecount(),
        mainmemory.read_u16_be(V_FRAMECOUNT),
        mainmemory.read_u8(V_VBLANK_BYTE),
        (mainmemory.read_u8(V_VBLANK_BYTE) >> 4) & 1)
end

-- Logs a delete only on the frame the shared "has the animal gone offscreen?"
-- test actually falls through to DeleteObject, so the per-frame execute hook
-- reports departures rather than every surviving animal.
local function logIfLeaving(context, ptr, where)
    if (mainmemory.read_u8(ptr + OB_RENDER) & 0x80) ~= 0 then return end
    context.log(string.format("depart at=%s %s %s",
        where, animalSummary(ptr), clocks()))
end

ProbeRuntime.run({
    stage = function()
        return emu.framecount() >= MOVIE_FRAME_FLOOR
            and mainmemory.read_u8(V_GAMEMODE) == 0x0C
            and mainmemory.read_u8(V_ZONE) == LZ_ZONE
            and mainmemory.read_u8(V_ACT) == ACT3
    end,
    hooks = {
        {
            -- Anml_ChkFloor, after the landing sets the per-species routine and
            -- reloads animal_speedX, immediately before the prison direction
            -- test (docs/s1disasm/sonic.lst: 97E6 tst.b (v_bossstatus).w).
            name = "anml_chkfloor_landed",
            address = 0x0097E6,
            callback = function(context)
                context.log(string.format("land %s bossStatus=%02X %s",
                    animalSummary(pointer("A0")),
                    mainmemory.read_u8(V_BOSSSTATUS),
                    clocks()))
            end
        },
        {
            -- The btst #4,(v_vblank_byte).w branch was taken, so this animal
            -- reverses its base X-direction (sonic.lst: 97F4 neg.w obVelX(a0)).
            name = "anml_chkfloor_flip_taken",
            address = 0x0097F4,
            callback = function(context)
                context.log(string.format("flip %s %s",
                    animalSummary(pointer("A0")), clocks()))
            end
        },
        {
            -- Anml_ChkFloor's own offscreen test, for an animal that leaves
            -- before it ever lands (sonic.lst: 97A8 tst.b obRender(a0)).
            name = "anml_chkfloor_offscreen_test",
            address = 0x0097A8,
            callback = function(context)
                logIfLeaving(context, pointer("A0"), "chkfloor")
            end
        },
        {
            -- Anml_Type0's offscreen test, which owns both LZ species: LZ's
            -- Anml_VarIndex pair is 2,3 and Anml_ChkFloor routes animal_id*2+4,
            -- so both land on Anml_NormalGravity (sonic.lst: 9832).
            name = "anml_type0_offscreen_test",
            address = 0x009832,
            callback = function(context)
                logIfLeaving(context, pointer("A0"), "type0")
            end
        },
        {
            -- Pri_EndAct found no animal in the slots its FixBugs=0 scan covers
            -- and is calling GotThroughAct (sonic.lst: 1B514). This is the edge
            -- the recorded NEMESIS_PLC_QUEUE ordinal 166 is taken at.
            name = "pri_endact_gotthroughact",
            address = 0x01B514,
            callback = function(context)
                context.log(string.format("gotThroughAct %s", clocks()))
                context.finish()
            end
        }
    }
})
