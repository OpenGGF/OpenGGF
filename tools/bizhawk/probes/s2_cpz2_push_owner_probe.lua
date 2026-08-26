-- Which OBJECT owns each pushing-bit transition across S2 CPZ act 2, and where
-- the Walk/Run animation-restart word write comes from.
--
-- Extends s2_cpz2_push_order_probe.lua with (a) the object pointer a0 at every
-- SolidObject site, so each pass is attributable to a slot and object id, and
-- (b) the two SolidObject_TestClearPush sites -- the entry at loc_19AC4 and the
-- `move.w #(Walk<<8)|Run,anim(a1)` restart write immediately before
-- Solid_NotPushing (docs/s2disasm/s2.asm:35462-35483). The recorded fixture
-- shows player_mapping_frame restarting the walk animation on seg10 rows 1724
-- and 1733 and again around 2262, which only that write can produce, so the
-- question is which object reaches it and with whose pushing bit set.
--
-- Read/log only. Nothing here writes emulated memory, input, registers or
-- savestates, and nothing it produces is an engine input.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE     = 0xF600
local CURRENT_ZONE  = 0xFE10
local CURRENT_ACT   = 0xFE11
local MAIN_CHARACTER = 0xB000
local OFF_ID        = 0x00
local OFF_STATUS    = 0x22
local OFF_X_POS     = 0x08
local OFF_ANIM      = 0x1C
local OFF_MAPFRAME  = 0x1A

local CPZ_ZONE = 0x0D
local CPZ_ACT2 = 0x01

local BK2_FRAME_OFFSET = 82342
-- Two windows: the divergence and its successor restart (rows 1718-1745), and
-- the second restart (rows 2255-2275). Rows 440-505 and 4410-4425 are the other
-- pair this probe was run over -- the Obj41 horizontal spring launches, where
-- loc_18BAA clears the object's own pushing bits and no restart follows.
local W1_FIRST = BK2_FRAME_OFFSET + 1718
local W1_LAST  = BK2_FRAME_OFFSET + 1745
local W2_FIRST = BK2_FRAME_OFFSET + 2255
local W2_LAST  = BK2_FRAME_OFFSET + 2275

local MAIN_CHARACTER_AREG = 0xFFB000
local OBJECT_RAM_BASE = 0xFFB000
local OBJECT_SIZE = 0x40

local function inWindow()
    local f = emu.framecount()
    return (f >= W1_FIRST and f <= W1_LAST) or (f >= W2_FIRST and f <= W2_LAST)
end

local function areg(name)
    local v = emu.getregister("M68K " .. name)
    if v == nil then
        return -1
    end
    return v & 0xFFFFFF
end

local function record(context, label)
    if not inWindow() then
        return
    end
    local a0 = areg("A0")
    local slot = -1
    local objId = -1
    local objStatus = -1
    if a0 >= OBJECT_RAM_BASE then
        slot = (a0 - OBJECT_RAM_BASE) // OBJECT_SIZE
        objId = mainmemory.read_u8((a0 & 0xFFFF) + OFF_ID)
        objStatus = mainmemory.read_u8((a0 & 0xFFFF) + OFF_STATUS)
    end
    context.log(string.format(
        "frame=%d row=%d hook=%s a0=%06X slot=%d objid=%02X objstatus=%02X "
            .. "pstatus=%02X panim=%02X pmap=%02X px=%04X",
        emu.framecount(),
        emu.framecount() - BK2_FRAME_OFFSET,
        label,
        a0, slot, objId, objStatus,
        mainmemory.read_u8(MAIN_CHARACTER + OFF_STATUS),
        mainmemory.read_u8(MAIN_CHARACTER + OFF_ANIM),
        mainmemory.read_u8(MAIN_CHARACTER + OFF_MAPFRAME),
        mainmemory.read_u16_be(MAIN_CHARACTER + OFF_X_POS)))
    if emu.framecount() >= W2_LAST then
        context.finish()
    end
end

-- SolidObject carries the character in a1 and the object in a0.
local function onSolidObjectSite(label)
    return function(context)
        if areg("A1") == MAIN_CHARACTER_AREG then
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
        -- s2.asm:35413 loc_19A6A -- entry to the left/right branch.
        { name = "SolidObject_LeftRight", address = 0x019A6A,
          callback = onSolidObjectSite("SolidObject_LeftRight") },
        -- s2.asm:35438 loc_19A90 -- sets pushing on BOTH object and player.
        { name = "SolidObject_AtEdge", address = 0x019A90,
          callback = onSolidObjectSite("SolidObject_AtEdge") },
        -- s2.asm:35452 loc_19AB6 -- the <=4 vertical-distance / airborne exit.
        { name = "SolidObject_SideAir", address = 0x019AB6,
          callback = onSolidObjectSite("SolidObject_SideAir") },
        -- s2.asm:35462 loc_19AC4 -- no-side-contact entry; tests the OBJECT's
        -- own pushing bit before deciding whether to restart the animation.
        { name = "SolidObject_TestClearPush", address = 0x019AC4,
          callback = onSolidObjectSite("SolidObject_TestClearPush") },
        -- s2.asm:35483 -- the `move.w #(Walk<<8)|Run,anim(a1)` restart write,
        -- six bytes before loc_19ADC.
        { name = "SolidObject_WalkRunWrite", address = 0x019AD6,
          callback = onSolidObjectSite("SolidObject_WalkRunWrite") },
        -- s2.asm:35484 loc_19ADC -- clears pushing on both.
        { name = "Solid_NotPushing", address = 0x019ADC,
          callback = onSolidObjectSite("Solid_NotPushing") }
    }
})
