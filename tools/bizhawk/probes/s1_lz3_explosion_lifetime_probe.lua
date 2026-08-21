local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local V_GAMEMODE = 0xF600
local V_ZONE = 0xFE10
local V_ACT = 0xFE11

local OBJECT_RAM = 0xD000
local OBJECT_SIZE = 0x40
local OB_FRAME = 0x1A
local OB_TIME_FRAME = 0x1E

local LZ_ZONE = 1
local ACT3 = 2
local MOVIE_FRAME_FLOOR = 148000
-- The capsule's explosion phase runs for 1*60 frames before Pri_SpawnAnimals
-- (3E Prison Capsule.asm:102,138). A wide window covers it plus the whole burst.
local WINDOW_START = 160240
local WINDOW_END = 160360

local function register(name)
    return emu.getregister(name) or 0
end

local function slotFor(ptr)
    local delta = (ptr & 0xFFFF) - OBJECT_RAM
    if delta < 0 or delta % OBJECT_SIZE ~= 0 then return -1 end
    return math.floor(delta / OBJECT_SIZE)
end

local function inWindow()
    local frame = emu.framecount()
    return frame >= WINDOW_START and frame <= WINDOW_END
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
            -- Expl_Main, Obj3F's init (sonic.lst: 9486). Unlike Obj27's
            -- ExItem_Main, which ends `jsr (QueueSound2).l` at 9450 and falls
            -- through into ExItem_Animate at 9456, Expl_Main ends
            -- `jmp (QueueSound2).l` at 94C0 and returns, so Obj3F skips the
            -- predecrement on its own spawn frame. This hook records the spawn
            -- frame and slot so the resulting lifetime can be counted.
            name = "expl_main_init",
            address = 0x009486,
            callback = function(context)
                if not inWindow() then return end
                context.log(string.format("explSpawn movieFrame=%d slot=%d",
                    emu.framecount(), slotFor(register("M68K A0"))))
            end
        },
        {
            -- ExItem_Animate's final-frame test (sonic.lst: 9466). obFrame == 5
            -- here means the beq.w DeleteObject on the next instruction is
            -- taken, so this is the explosion's last frame.
            name = "exitem_animate_final_test",
            address = 0x009466,
            callback = function(context)
                if not inWindow() then return end
                local ptr = register("M68K A0") & 0xFFFF
                if mainmemory.read_u8(ptr + OB_FRAME) ~= 5 then return end
                context.log(string.format("explDelete movieFrame=%d slot=%d timeFrame=%d",
                    emu.framecount(), slotFor(ptr),
                    mainmemory.read_u8(ptr + OB_TIME_FRAME)))
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
