-- Which instruction owns status.player.pushing across S2 CPZ act 2 frames
-- 1721-1733 of the complete-emeralds seg10_cpz2 segment.
--
-- The recorded player_status_byte is 0x49 (bit 5, status.player.pushing,
-- s2.constants.asm:215) on every one of those frames, while the Obj74 invisible
-- block Sonic is flush against carries status.npc.p1_pushing. SolidObject_AtEdge
-- sets BOTH bits together (docs/s2disasm/s2.asm:35439-35446), and every
-- Sonic-side clear except Solid_NotPushing is excluded by the recorded state, so
-- either AtEdge is never reached or a clear runs after it. Sonic's integer x_pos
-- is constant at 0x1EF5 across the window and SolidObject compares integer x_pos
-- only (s2.asm:35418-35421), so the branch decision is identical on every frame
-- and no frame-sampled model can separate the two. This probe records which
-- instruction actually executes, in order, per frame.
--
-- Read/log only. Nothing here writes emulated memory, input, registers or
-- savestates, and nothing it produces is an engine input: it is diagnostic
-- tooling, not a trace contract.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

-- Addresses taken from the production S2 recorder (tools/bizhawk/s2_trace_recorder.lua
-- ADDR_GAME_MODE/ADDR_ZONE/ADDR_ACT/PLAYER_BASE/OFF_STATUS), not re-derived here.
local GAME_MODE     = 0xF600
local CURRENT_ZONE  = 0xFE10
local CURRENT_ACT   = 0xFE11
local MAIN_CHARACTER = 0xB000
local OFF_STATUS    = 0x22
local OFF_X_POS     = 0x08
local OFF_X_SUB     = 0x0A

-- Chemical Plant is raw zone 0x0D; act 2 is act byte 1.
local CPZ_ZONE = 0x0D
local CPZ_ACT2 = 0x01

-- The segment's own bk2_frame_offset, from its metadata.json. The window is the
-- divergence span plus a frame either side.
local BK2_FRAME_OFFSET = 82342
local FIRST_FRAME = BK2_FRAME_OFFSET + 1721
local LAST_FRAME  = BK2_FRAME_OFFSET + 1733

-- 68000 address-register values are the full $FFxxxx word-wide RAM address.
local MAIN_CHARACTER_AREG = 0xFFB000

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

-- Log the frame and the PC explicitly rather than inferring either from order
-- or from position in the stream.
local function record(context, label, aregName)
    if not inWindow() then
        return
    end
    local pc = emu.getregister("M68K PC") or 0
    context.log(string.format(
        "frame=%d pc=%06X hook=%s %s=%06X status=%02X x=%04X xsub=%04X",
        emu.framecount(),
        pc & 0xFFFFFF,
        label,
        aregName,
        areg(aregName),
        mainmemory.read_u8(MAIN_CHARACTER + OFF_STATUS),
        mainmemory.read_u16_be(MAIN_CHARACTER + OFF_X_POS),
        mainmemory.read_u16_be(MAIN_CHARACTER + OFF_X_SUB)))
    if emu.framecount() >= LAST_FRAME then
        context.finish()
    end
end

-- SolidObject carries the character in a1; Sonic_Animate carries it in a0.
local function onSolidObjectSite(label)
    return function(context)
        if areg("A1") == MAIN_CHARACTER_AREG then
            record(context, label, "A1")
        end
    end
end

local function onAnimateSite(label)
    return function(context)
        if areg("A0") == MAIN_CHARACTER_AREG then
            record(context, label, "A0")
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
        -- s2.asm:35413 loc_19A6A -- entry to the left/right branch.
        { name = "SolidObject_LeftRight", address = 0x019A6A,
          callback = onSolidObjectSite("SolidObject_LeftRight") },
        -- s2.asm:35438 loc_19A90 -- sets pushing on BOTH object and player.
        { name = "SolidObject_AtEdge", address = 0x019A90,
          callback = onSolidObjectSite("SolidObject_AtEdge") },
        -- s2.asm:35452 loc_19AB6 -- the <=4 vertical-distance / airborne exit.
        { name = "SolidObject_SideAir", address = 0x019AB6,
          callback = onSolidObjectSite("SolidObject_SideAir") },
        -- s2.asm:35484 loc_19ADC -- clears pushing on both.
        { name = "Solid_NotPushing", address = 0x019ADC,
          callback = onSolidObjectSite("Solid_NotPushing") },
        -- s2.asm:38378 loc_1B350 -- Sonic_Animate entry, BEFORE the prologue
        -- clear at :38391, so its status reading is the pre-clear value.
        { name = "Sonic_Animate_entry", address = 0x01B350,
          callback = onAnimateSite("Sonic_Animate_entry") },
        -- s2.asm:38392 loc_1B384 -- SAnim_Do, reached by both the changed and
        -- unchanged paths, so its status reading is the post-clear value. The
        -- pair brackets the bclr without needing that instruction's address.
        { name = "SAnim_Do", address = 0x01B384,
          callback = onAnimateSite("SAnim_Do") }
    }
})
