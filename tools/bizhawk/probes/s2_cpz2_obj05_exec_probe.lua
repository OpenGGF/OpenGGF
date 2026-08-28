-- Does Obj05 (Tails' tails) execute on every frame of CPZ2 seg10 rows 5535-5600?
--
-- The recorded dynamic-art stream shows a 19-frame gap between tails-tails
-- submissions at rows 5546 and 5565, longer than any Obj05 script allows
-- (Obj05Ani_Swish holds 8 frames, Flick 4, Pushing 10 --
-- docs/s2disasm/s2.asm:41815-41830). Two explanations survive: ExecuteObjects
-- did not reach Obj05 on some of those frames, or it ran and the mapping frame
-- changed only into DPLC entries that emit nothing --  LoadTailsTailsDynPLC
-- writes TailsTails_LastLoadedDPLC at :41641 BEFORE the empty-DPLC early return
-- at :41646-41647, so the stream under-reports changes by construction. Frame
-- sampling cannot separate them; instruction execution can.
--
-- Read/log only. Nothing here writes emulated memory, input, registers or
-- savestates, and nothing it produces is an engine input.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

-- From the production S2 recorder, not re-derived here.
local GAME_MODE    = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT  = 0xFE11
local CPZ_ZONE = 0x0D
local CPZ_ACT2 = 0x01

-- Obj05 is a FIXED slot, not a dynamically allocated one: Tails_Tails is
-- loaded at $FFFFD000 (docs/s2disasm/s2.asm:38944), so the gate is exact
-- rather than a heuristic on object id.
local OBJ05_AREG = 0xFFD000
local OBJ05_RAM  = 0xD000

-- s2.constants.asm:15-35.
local OFF_ID            = 0x00
local OFF_MAPPING_FRAME = 0x1A
local OFF_ANIM_FRAME    = 0x1B
local OFF_ANIM          = 0x1C
local OFF_PREV_ANIM     = 0x1D
local OFF_ANIM_DURATION = 0x1E

-- seg10_cpz2 metadata.json.
local BK2_FRAME_OFFSET = 82342
local FIRST_FRAME = BK2_FRAME_OFFSET + 5535
local LAST_FRAME  = BK2_FRAME_OFFSET + 5600

local function inWindow()
    local f = emu.framecount()
    return f >= FIRST_FRAME and f <= LAST_FRAME
end

local function areg(name)
    local v = emu.getregister("M68K " .. name)
    if v == nil then
        return -1
    end
    return v & 0xFFFFFF
end

-- Frame and PC are recorded explicitly rather than inferred from order or from
-- position in the stream.
local function record(context, label)
    if not inWindow() then
        return
    end
    local pc = emu.getregister("M68K PC") or 0
    context.log(string.format(
        "frame=%d row=%d pc=%06X hook=%s a0=%06X id=%02X anim=%02X prev=%02X "
            .. "frame_idx=%02X duration=%02X map=%02X",
        emu.framecount(),
        emu.framecount() - BK2_FRAME_OFFSET,
        pc & 0xFFFFFF,
        label,
        areg("A0"),
        mainmemory.read_u8(OBJ05_RAM + OFF_ID),
        mainmemory.read_u8(OBJ05_RAM + OFF_ANIM),
        mainmemory.read_u8(OBJ05_RAM + OFF_PREV_ANIM),
        mainmemory.read_u8(OBJ05_RAM + OFF_ANIM_FRAME),
        mainmemory.read_u8(OBJ05_RAM + OFF_ANIM_DURATION),
        mainmemory.read_u8(OBJ05_RAM + OFF_MAPPING_FRAME)))
    if emu.framecount() >= LAST_FRAME then
        context.finish()
    end
end

-- Tails_Animate_Part2 animates whichever object is in a0; Tails_Animate enters
-- it with the Sidekick, Obj05_Main with the tails object. Gate on a0 so the
-- body's own animation is never counted as the tails'.
local function onObj05Site(label)
    return function(context)
        if areg("A0") == OBJ05_AREG then
            record(context, label)
        end
    end
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x7F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == CPZ_ZONE
            and mainmemory.read_u8(CURRENT_ACT) == CPZ_ACT2
    end,
    hooks = {
        -- s2.asm:41270 loc_1CDCA -- entry. One hit per frame here is the whole
        -- question: it proves ExecuteObjects reached Obj05 that frame.
        { name = "Tails_Animate_Part2", address = 0x01CDCA,
          callback = onObj05Site("ANIMATE_ENTRY") },
        -- s2.asm:41293 loc_1CE12 -- past the anim_frame_duration early-out, so
        -- reaching it means the script advanced this frame.
        { name = "TAnim_Do2", address = 0x01CE12,
          callback = onObj05Site("ADVANCE") },
        -- s2.asm:41300 loc_1CE22 -- the mapping_frame write itself. Sampled
        -- BEFORE the store, so `map` here is the outgoing value.
        { name = "TAnim_Next", address = 0x01CE22,
          callback = onObj05Site("WRITE_MAP") },
        -- s2.asm:41636 -- the DPLC dedup. Distinguishes "no change" from
        -- "changed into an empty DPLC", which the recorded stream cannot.
        { name = "LoadTailsTailsDynPLC", address = 0x01D184,
          callback = onObj05Site("DPLC_ENTRY") }
    }
})
