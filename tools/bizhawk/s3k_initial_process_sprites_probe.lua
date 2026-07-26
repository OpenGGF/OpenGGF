-- Read-only oracle for the fresh S3K Process_Sprites pass.
-- Locked-on World ROM SHA-1: CFBF98C36C776677290A872547AC47C53D2761D6.
-- The ROM bytes at $647E are `4E B9 00 01 AA DA` (jsr Process_Sprites);
-- execution resumes at $6484. This script only reads RAM/registers and logs.

local OUT = os.getenv("OGGF_OUT")
    or "target/initial-process-sprites-oracle/aiz1.jsonl"
local out = assert(io.open(OUT, "w"))
local SLOT_SIZE, P1, P2 = 0x4A, 0xB000, 0xB04A
local PRE_PC, POST_PC = 0x00647E, 0x006484
local GAME_MODE, ZONE, ACT = 0xF600, 0xFE10, 0xFE11
local LEVEL_MODE, LEVEL_MODE_MASK = 0x0C, 0x0F
local TARGET_ZONE, TARGET_ACT = 0x00, 0x00
local PRE_HOOK = "initial_process_sprites_pre"
local POST_HOOK = "initial_process_sprites_post"
local PLAYER_HOOK = "initial_process_sprites_first_player"
local hooks_armed, post_seen, ordinary_hook_installed, done =
    false, false, false, false

-- Match the repository's fast diagnostic template. Rendering and audio
-- dominate the wait to reach the target stage and are irrelevant here.
emu.limitframerate(false)
client.speedmode(6400)
if client.invisibleemulation then client.invisibleemulation(true) end
if client.SetSoundOn then pcall(client.SetSoundOn, false) end

local function u8(a) return mainmemory.read_u8(a) end
local function u16(a) return mainmemory.read_u16_be(a) end
local function u32(a) return mainmemory.read_u32_be(a) end
local function hex(v, n) return string.format("%0" .. n .. "X", v) end
local function player(base)
    return {
        code="0x"..hex(u32(base),8), routine=u8(base+5),
        x="0x"..hex(u16(base+0x10),4), x_sub="0x"..hex(u16(base+0x12),4),
        y="0x"..hex(u16(base+0x14),4), y_sub="0x"..hex(u16(base+0x16),4),
        x_vel="0x"..hex(u16(base+0x18),4), y_vel="0x"..hex(u16(base+0x1A),4),
        ground_vel="0x"..hex(u16(base+0x1C),4),
        anim=u8(base+0x20), prev_anim=u8(base+0x21),
        anim_frame=u8(base+0x23), anim_timer=u8(base+0x24),
        status="0x"..hex(u8(base+0x2A),2),
        status_secondary="0x"..hex(u8(base+0x2B),2),
        air_left=u8(base+0x2C),
        object_control="0x"..hex(u8(base+0x2E),2),
        double_jump_flag=u8(base+0x2F),
        flips_remaining=u8(base+0x30), flip_speed=u8(base+0x31),
        move_lock=u16(base+0x32),
        invulnerability_timer=u8(base+0x34),
        invincibility_timer=u8(base+0x35),
        speed_shoes_timer=u8(base+0x36),
        collision_flags="0x"..hex(u8(base+0x28),2),
        collision_property="0x"..hex(u8(base+0x29),2)
    }
end
local function encode(v)
    if type(v) == "table" then
        local keys = {}
        for k in pairs(v) do keys[#keys+1] = k end
        table.sort(keys, function(a,b) return tostring(a) < tostring(b) end)
        local p = {}
        for _,k in ipairs(keys) do
            p[#p+1] = string.format("%q", tostring(k))..":"..encode(v[k])
        end
        return "{"..table.concat(p,",").."}"
    elseif type(v) == "string" then return string.format("%q", v)
    elseif type(v) == "boolean" or type(v) == "number" then return tostring(v)
    else return "null" end
end
local function snapshot(label)
    local idx = u16(0xEE26) % 0x100
    local touched = (idx - 4) % 0x100
    local fixed = {}
    for slot=93,109 do
        local b = P1 + slot*SLOT_SIZE
        fixed[tostring(slot)] = {
            code="0x"..hex(u32(b),8), routine=u8(b+5),
            anim=u8(b+0x20), frame=u8(b+0x23), timer=u8(b+0x24)
        }
    end
    local r = {
        label=label, emu_frame=emu.framecount(),
        pc="0x"..hex(emu.getregister("M68K PC") or 0,8),
        p1=player(P1), p2=player(P2),
        pos_table_index=idx,
        history_entry={
            offset="0x"..hex(touched,2),
            x="0x"..hex(u16(0xE500+touched),4),
            y="0x"..hex(u16(0xE500+touched+2),4),
            logical="0x"..hex(u16(0xE400+touched),4),
            status="0x"..hex(u8(0xE400+touched+2),2)
        },
        tails_cpu={
            interact="0x"..hex(u16(0xF700),4), idle_timer=u16(0xF702),
            flight_timer=u16(0xF704), routine=u16(0xF708),
            target_x="0x"..hex(u16(0xF70A),4),
            target_y="0x"..hex(u16(0xF70C),4),
            auto_fly_timer=u8(0xF70E), auto_jump_flag=u8(0xF70F)
        },
        controllers={
            ctrl1="0x"..hex(u16(0xF604),4),
            ctrl2="0x"..hex(u16(0xF606),4),
            ctrl1_logical="0x"..hex(u16(0xF602),4),
            ctrl2_logical="0x"..hex(u16(0xF66A),4),
            ctrl1_locked=u8(0xF7CA), ctrl2_locked=u8(0xF7CB)
        },
        counters={
            level=u16(0xFE04), vint=u32(0xFE0C),
            oscillation_control="0x"..hex(u16(0xFE6E),4)
        },
        water={flag=u8(0xF730)},
        collision_list_byte_count=u16(0xE380),
        absolute_dynamic_slot3_code="0x"..hex(u32(P1+3*SLOT_SIZE),8),
        fixed=fixed
    }
    local line = encode(r)
    out:write(line.."\n"); out:flush(); print(line)
end

local function unregister_hooks()
    event.unregisterbyname(PRE_HOOK)
    event.unregisterbyname(POST_HOOK)
    event.unregisterbyname(PLAYER_HOOK)
end

local function arm_target_hooks()
    if hooks_armed then return end
    hooks_armed = true
    event.onmemoryexecute(function()
        snapshot("ADJACENT_MINUS_ONE_PRE_SETUP")
        local p1pc = u32(P1)
        if p1pc ~= 0 and not ordinary_hook_installed then
            ordinary_hook_installed = true
            event.onmemoryexecute(function()
                if post_seen and not done then
                    done = true
                    snapshot("FIRST_LEVEL_LOOP_PLAYER_ENTRY")
                    unregister_hooks()
                    out:close()
                    client.exit()
                end
            end, p1pc, PLAYER_HOOK)
        end
    end, PRE_PC, PRE_HOOK)

    event.onmemoryexecute(function()
        snapshot("POST_INITIAL_PROCESS_SPRITES")
        post_seen = true
    end, POST_PC, POST_HOOK)
end

local function is_target_stage()
    local game_mode = u8(GAME_MODE)
    return (game_mode & LEVEL_MODE_MASK) == LEVEL_MODE
        and u8(ZONE) == TARGET_ZONE
        and u8(ACT) == TARGET_ACT
end

while not done do
    -- Until AIZ1, poll only three cheap bytes. BizHawk's execution callback
    -- boundary is deliberately absent during boot, title, and other stages.
    if not hooks_armed and is_target_stage() then
        arm_target_hooks()
    end
    if movie.isloaded() and movie.mode() == "FINISHED" then
        unregister_hooks()
        out:close()
        client.exit()
        break
    end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
