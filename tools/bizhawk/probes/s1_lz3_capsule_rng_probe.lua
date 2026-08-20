local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

-- Sonic 1 World REV01 RAM (docs/s1disasm/_Variables.asm, sonic.lst symbol table).
local V_GAMEMODE = 0xF600
local V_BOSSSTATUS = 0xF7A7
local V_RANDOM = 0xF636
local V_VBLANK_BYTE = 0xFE0F
local V_ZONE = 0xFE10
local V_ACT = 0xFE11

-- The capsule's own explosion/spawn phase is the window where a draw-count
-- difference reorders the eight burst animals' species. Opening the capsule
-- sets v_bossstatus to 2 (Pri_SpawnAnimals, 3E Prison Capsule.asm:142), so the
-- window is bounded by that flag rather than by a frame number.
local LZ_ZONE = 1
local ACT3 = 2
local MOVIE_FRAME_FLOOR = 148000
local WINDOW_START = 160290
local WINDOW_END = 160460

local draws = 0

local function register(name)
    return emu.getregister(name) or 0
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
            -- Every RandomNumber entry (sonic.lst: 29AC). The return address on
            -- the stack names the caller, which is what separates the capsule's
            -- own explosion and continuous-spawn draws from each animal's
            -- Anml_FromEnemy species draw.
            name = "random_number_entry",
            address = 0x0029AC,
            callback = function(context)
                draws = draws + 1
                local frame = emu.framecount()
                if frame < WINDOW_START or frame > WINDOW_END then return end
                local sp = register("M68K SP") & 0xFFFFFF
                context.log(string.format(
                    "draw n=%d movieFrame=%d caller=%06X seed=%08X boss=%02X vbl=%02X",
                    draws,
                    frame,
                    mainmemory.read_u32_be(sp & 0xFFFF),
                    mainmemory.read_u32_be(V_RANDOM),
                    mainmemory.read_u8(V_BOSSSTATUS),
                    mainmemory.read_u8(V_VBLANK_BYTE)))
            end
        },
        {
            name = "pri_endact_gotthroughact",
            address = 0x01B514,
            callback = function(context)
                context.log(string.format("gotThroughAct movieFrame=%d totalDraws=%d",
                    emu.framecount(), draws))
                context.finish()
            end
        }
    }
})
