local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

-- Sonic 1 World REV01 RAM (docs/s1disasm/_Variables.asm, sonic.lst symbol table).
local V_GAMEMODE = 0xF600
local V_ZONE = 0xFE10
local V_ACT = 0xFE11

local OBJECT_RAM = 0xD000
local OBJECT_SIZE = 0x40
local OB_ID = 0x00
local OB_RENDER = 0x01
local OB_ROUTINE = 0x24
local OB_SUBTYPE = 0x28
local OB_X = 0x08
local OB_Y = 0x0C

-- FindFreeObj scans the dynamic space only (v_lvlobjspace = slot 32 through
-- v_lvlobjend = slot 128, _incObj/sub FindFreeObj.asm:11-12), so the census
-- covers the low end of that space where the burst lands.
local FIRST_DYNAMIC_SLOT = 32
local LAST_CENSUS_SLOT = 72

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

ProbeRuntime.run({
    stage = function()
        return emu.framecount() >= MOVIE_FRAME_FLOOR
            and mainmemory.read_u8(V_GAMEMODE) == 0x0C
            and mainmemory.read_u8(V_ZONE) == LZ_ZONE
            and mainmemory.read_u8(V_ACT) == ACT3
    end,
    hooks = {
        {
            -- Pri_SpawnAnimals, after v_bossstatus is set and immediately
            -- before the eight-animal loop begins (sonic.lst: 1B47C moveq
            -- #7,d6). This is the exact instant FindFreeObj will be asked for
            -- the first burst slot, so the census is the occupancy that decides
            -- the assignment.
            name = "pri_spawnanimals_pre_loop",
            address = 0x01B47C,
            callback = function(context)
                context.log(string.format("census movieFrame=%d switchSlot=%d",
                    emu.framecount(), slotFor(pointer("A0"))))
                for slot = FIRST_DYNAMIC_SLOT, LAST_CENSUS_SLOT do
                    local ptr = OBJECT_RAM + slot * OBJECT_SIZE
                    local id = mainmemory.read_u8(ptr + OB_ID)
                    if id ~= 0 then
                        context.log(string.format(
                            "occupied slot=%d id=%02X routine=%02X subtype=%02X x=%d y=%d render=%02X",
                            slot, id,
                            mainmemory.read_u8(ptr + OB_ROUTINE),
                            mainmemory.read_u8(ptr + OB_SUBTYPE),
                            mainmemory.read_u16_be(ptr + OB_X),
                            mainmemory.read_u16_be(ptr + OB_Y),
                            mainmemory.read_u8(ptr + OB_RENDER)))
                    end
                end
            end
        },
        {
            -- One iteration of the burst loop has just claimed a slot, so a1
            -- names the assignment FindFreeObj made (sonic.lst: 1B492, the
            -- instruction after obID is written).
            name = "pri_spawnanimals_slot_claimed",
            address = 0x01B492,
            callback = function(context)
                context.log(string.format("burstSlot movieFrame=%d slot=%d",
                    emu.framecount(), slotFor(pointer("A1"))))
            end
        },
        {
            name = "pri_endact_gotthroughact",
            address = 0x01B514,
            callback = function(context)
                context.log(string.format("gotThroughAct movieFrame=%d",
                    emu.framecount()))
                context.finish()
            end
        }
    }
})
