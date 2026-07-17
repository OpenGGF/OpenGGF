-- Strict locked-on FBZ visual-reference exporter (evidence schema v2).
--
-- The v1 checkpoint and AniPLC series are preserved as historical evidence.
-- This script writes only versioned replacements. It never writes RAM, VRAM,
-- animation timers, or animation frame indices: all cadence proof is obtained
-- by observing the complete-run BK2 advance naturally.

local PLAN_PATH = assert(os.getenv("OGGF_FBZ_VISUAL_PLAN"), "OGGF_FBZ_VISUAL_PLAN is required")
local OUTPUT_ROOT = assert(os.getenv("OGGF_FBZ_VISUAL_OUTPUT"), "OGGF_FBZ_VISUAL_OUTPUT is required")
local ROM_SHA1 = assert(os.getenv("OGGF_FBZ_ROM_SHA1"), "OGGF_FBZ_ROM_SHA1 is required")
local BK2_SHA256 = assert(os.getenv("OGGF_FBZ_BK2_SHA256"), "OGGF_FBZ_BK2_SHA256 is required")
local HOST_RECEIPT = assert(os.getenv("OGGF_FBZ_HOST_RECEIPT"), "OGGF_FBZ_HOST_RECEIPT is required")

local plan = dofile(PLAN_PATH)
assert(plan.manifest_sha256 == "D13D037BAF52BBD65D28096A71A54ACACB4229B8C4C560C76DCB921E90DC40DD",
    "capture plan does not bind the frozen FBZ manifest")
assert(plan.bk2_frame_offset == 237913, "unexpected FBZ BK2 frame offset")

local ADDR_PLAYER_X = 0xB010
local ADDR_PLAYER_Y = 0xB014
local ADDR_TITLE_CARD = 0xB250 -- Dynamic_object_RAM + (object_size * 5)
local ADDR_TITLE_CARD_ROUTINE = ADDR_TITLE_CARD + 0x05
local ADDR_TITLE_CARD_WAIT_TIMER = ADDR_TITLE_CARD + 0x2E
local ADDR_TITLE_CARD_CHILD_COUNT = ADDR_TITLE_CARD + 0x30
local ADDR_CAMERA_TARGET_MIN_X = 0xEE0C
local ADDR_CAMERA_TARGET_MAX_X = 0xEE0E
local ADDR_CAMERA_TARGET_MIN_Y = 0xEE10
local ADDR_CAMERA_TARGET_MAX_Y = 0xEE12
local ADDR_CAMERA_MIN_X = 0xEE14
local ADDR_CAMERA_MAX_X = 0xEE16
local ADDR_CAMERA_MIN_Y = 0xEE18
local ADDR_CAMERA_MAX_Y = 0xEE1A
local ADDR_PALETTE_FADE_TIMER = 0xEE50
local ADDR_CAMERA_X = 0xEE78
local ADDR_CAMERA_Y = 0xEE7C
local ADDR_CAMERA_X_COPY = 0xEE80
local ADDR_CAMERA_Y_COPY = 0xEE84
local ADDR_EVENTS_ROUTINE_FG = 0xEEC0
local ADDR_EVENTS_ROUTINE_BG = 0xEEC2
local ADDR_SCREEN_SHAKE_FLAG = 0xEECC
local ADDR_SCREEN_SHAKE_OFFSET = 0xEECE
local ADDR_EVENTS_BG = 0xEED2
local ADDR_GAME_MODE = 0xF600
local ADDR_VDP_REG1_COMMAND = 0xF60C
local ADDR_BACKGROUND_COLLISION_FLAG = 0xF664
local ADDR_BOSS_FLAG = 0xF7AA
local ADDR_ANIM_COUNTERS = 0xF7F0
local ADDR_LEVEL_FRAME_COUNTER = 0xFE04
local ADDR_ZONE_AND_ACT = 0xFE10

local LEVEL_GAME_MODE = 0x0C
local START_OUTPUT_ID = "fbz1-start-outdoor-gameplay-v2"
local OBSERVATION_LIMIT_FRAMES = 1024
local CADENCE_FOLLOWUP_FRAMES = 4 -- zero-step + one-step + four more = six images

-- Destination display order is intentionally independent of ROM channel order.
-- Anim_Counters pairs are in ROM declaration order: $210,$230,$238,$200,$208.
local CADENCE_SPECS = {
    { plan_name = "aniplc-cadence-200", output_name = "aniplc-cadence-200-v2",
      channel = 3, reset_duration = 7, destination_tile = 0x200, tile_count = 8 },
    { plan_name = "aniplc-cadence-208", output_name = "aniplc-cadence-208-v2",
      channel = 4, reset_duration = 7, destination_tile = 0x208, tile_count = 8 },
    { plan_name = "aniplc-cadence-210", output_name = "aniplc-cadence-210-v2",
      channel = 0, reset_duration = 0x3F, destination_tile = 0x210, tile_count = 0x20 },
    { plan_name = "aniplc-cadence-230", output_name = "aniplc-cadence-230-v2",
      channel = 1, reset_duration = 7, destination_tile = 0x230, tile_count = 8 },
    { plan_name = "aniplc-cadence-238", output_name = "aniplc-cadence-238-v2",
      channel = 2, reset_duration = 1, destination_tile = 0x238, tile_count = 0x10 },
}

local function join_path(a, b)
    local suffix = a:sub(-1)
    if suffix == "/" or suffix == "\\" then return a .. b end
    return a .. "/" .. b
end

local function json_escape(value)
    return tostring(value):gsub("\\", "\\\\"):gsub('"', '\\"'):gsub("\n", "\\n")
end

local function hex8(value) return string.format("0x%02X", value % 0x100) end
local function hex16(value) return string.format("0x%04X", value % 0x10000) end
local function hex32(value) return string.format("0x%08X", value % 0x100000000) end

local function read_u32_be(address)
    return mainmemory.read_u16_be(address) * 0x10000 + mainmemory.read_u16_be(address + 2)
end

local function file_exists(path)
    local file = io.open(path, "rb")
    if file then file:close() return true end
    return false
end

local function ensure_absent(path)
    assert(not file_exists(path), "refusing to overwrite preserved evidence: " .. path)
end

local function write_atomic(path, writer)
    ensure_absent(path)
    local temporary = path .. ".tmp"
    ensure_absent(temporary)
    local file = assert(io.open(temporary, "w"))
    local ok, failure = pcall(writer, file)
    file:close()
    if not ok then
        os.remove(temporary)
        error(failure)
    end
    local renamed, rename_failure = os.rename(temporary, path)
    if not renamed then
        os.remove(temporary)
        error("could not publish " .. path .. ": " .. tostring(rename_failure))
    end
end

local function capture_png_temporary(final_path)
    ensure_absent(final_path)
    local temporary = final_path .. ".candidate.png"
    ensure_absent(temporary)
    local ok, failure = pcall(client.screenshot, temporary)
    if not ok then
        os.remove(temporary)
        return nil, tostring(failure)
    end
    return temporary, nil
end

local function publish_temporary(temporary, final_path)
    ensure_absent(final_path)
    local renamed, failure = os.rename(temporary, final_path)
    assert(renamed, "could not publish screenshot " .. final_path .. ": " .. tostring(failure))
end

-- BizHawk overlays are emulator configuration, not ROM state. Preserve the
-- user's exact configuration, install a separately fetched capture copy, and
-- restore it both on normal completion and on emulator exit.
local OVERLAY_CONFIG_KEYS = {
    "DisplayFps", "DisplayFrameCounter", "DisplayLagCounter", "DisplayInput",
    "DisplayRerecordCount", "DisplayMessages",
}
local original_config = assert(client.getconfig(), "client.getconfig() did not return the original config")
local original_overlay_values = {}
for _, key in ipairs(OVERLAY_CONFIG_KEYS) do original_overlay_values[key] = original_config[key] end
local capture_config = assert(client.getconfig(), "client.getconfig() did not return a capture config copy")
local config_restored = false

local function apply_config(config)
    -- BizHawk 2.11's shipped ClientLuaLibrary exposes getconfig but some builds
    -- do not expose setconfig. In those builds getconfig returns the live config
    -- object, so field assignment is the application operation. Either route is
    -- accepted only after a fresh getconfig read verifies every changed value.
    if client.setconfig then return pcall(client.setconfig, config) end
    return true, "live getconfig object"
end

local function restore_config()
    if config_restored then return true end
    local restore_target = client.getconfig and client.getconfig() or original_config
    if not restore_target then return false end
    for _, key in ipairs(OVERLAY_CONFIG_KEYS) do
        restore_target[key] = original_overlay_values[key]
    end
    local applied = apply_config(restore_target)
    if gui and gui.clearGraphics then pcall(gui.clearGraphics) end
    local read_ok, readback = pcall(client.getconfig)
    if not applied or not read_ok then return false end
    for _, key in ipairs(OVERLAY_CONFIG_KEYS) do
        if readback[key] ~= original_overlay_values[key] then return false end
    end
    config_restored = true
    return true
end

assert(event and event.onexit, "BizHawk event.onexit is required for config restoration")
event.onexit(function() pcall(restore_config) end, "openggf-fbz-visual-v2-restore-config")

local OVERLAYS_DISABLED = false
local function install_capture_config()
    for _, key in ipairs(OVERLAY_CONFIG_KEYS) do capture_config[key] = false end
    local set_config_ok = apply_config(capture_config)
    local clear_graphics_ok = gui and gui.clearGraphics and pcall(gui.clearGraphics) or false
    local readback_ok, readback_config = pcall(client.getconfig)
    OVERLAYS_DISABLED = set_config_ok and clear_graphics_ok and readback_ok
    if OVERLAYS_DISABLED then
        for _, key in ipairs(OVERLAY_CONFIG_KEYS) do
            if readback_config[key] ~= false then OVERLAYS_DISABLED = false end
        end
    end
    assert(OVERLAYS_DISABLED,
        "fail-closed: BizHawk FPS/input/message overlays could not be disabled and verified")
end

emu.limitframerate(false)
client.speedmode(6400)
client.invisibleemulation(true)
if client.SetSoundOn then pcall(client.SetSoundOn, false) end

local function read_ram_snapshot()
    local counters = {}
    for index = 0, 15 do counters[#counters + 1] = mainmemory.read_u8(ADDR_ANIM_COUNTERS + index) end
    local title_code = read_u32_be(ADDR_TITLE_CARD)
    local title_children = mainmemory.read_u16_be(ADDR_TITLE_CARD_CHILD_COUNT)
    local vdp_reg1_command = mainmemory.read_u16_be(ADDR_VDP_REG1_COMMAND)
    local title_active = title_code ~= 0
    return {
        player_x = mainmemory.read_u16_be(ADDR_PLAYER_X),
        player_y = mainmemory.read_u16_be(ADDR_PLAYER_Y),
        camera_x = mainmemory.read_u16_be(ADDR_CAMERA_X),
        camera_y = mainmemory.read_u16_be(ADDR_CAMERA_Y),
        camera_x_copy = mainmemory.read_u16_be(ADDR_CAMERA_X_COPY),
        camera_y_copy = mainmemory.read_u16_be(ADDR_CAMERA_Y_COPY),
        camera_target_min_x = mainmemory.read_u16_be(ADDR_CAMERA_TARGET_MIN_X),
        camera_target_max_x = mainmemory.read_u16_be(ADDR_CAMERA_TARGET_MAX_X),
        camera_target_min_y = mainmemory.read_u16_be(ADDR_CAMERA_TARGET_MIN_Y),
        camera_target_max_y = mainmemory.read_u16_be(ADDR_CAMERA_TARGET_MAX_Y),
        camera_min_x = mainmemory.read_u16_be(ADDR_CAMERA_MIN_X),
        camera_max_x = mainmemory.read_u16_be(ADDR_CAMERA_MAX_X),
        camera_min_y = mainmemory.read_u16_be(ADDR_CAMERA_MIN_Y),
        camera_max_y = mainmemory.read_u16_be(ADDR_CAMERA_MAX_Y),
        zone_and_act = mainmemory.read_u16_be(ADDR_ZONE_AND_ACT),
        events_routine_fg = mainmemory.read_u16_be(ADDR_EVENTS_ROUTINE_FG),
        events_routine_bg = mainmemory.read_u16_be(ADDR_EVENTS_ROUTINE_BG),
        events_bg_00 = mainmemory.read_u16_be(ADDR_EVENTS_BG),
        events_bg_02 = mainmemory.read_u16_be(ADDR_EVENTS_BG + 2),
        events_bg_04 = mainmemory.read_u16_be(ADDR_EVENTS_BG + 4),
        events_bg_06 = mainmemory.read_u16_be(ADDR_EVENTS_BG + 6),
        screen_shake_flag = mainmemory.read_u16_be(ADDR_SCREEN_SHAKE_FLAG),
        screen_shake_offset = mainmemory.read_u16_be(ADDR_SCREEN_SHAKE_OFFSET),
        background_collision_flag = mainmemory.read_u8(ADDR_BACKGROUND_COLLISION_FLAG),
        boss_flag = mainmemory.read_u8(ADDR_BOSS_FLAG),
        level_frame_counter = mainmemory.read_u16_be(ADDR_LEVEL_FRAME_COUNTER),
        game_mode = mainmemory.read_u8(ADDR_GAME_MODE),
        palette_fade_timer = mainmemory.read_u16_be(ADDR_PALETTE_FADE_TIMER),
        vdp_reg1_command = vdp_reg1_command,
        display_enabled = (vdp_reg1_command % 0x80) >= 0x40,
        title_card_code = title_code,
        title_card_routine = mainmemory.read_u8(ADDR_TITLE_CARD_ROUTINE),
        title_card_wait_timer = mainmemory.read_u16_be(ADDR_TITLE_CARD_WAIT_TIMER),
        title_card_child_count = title_children,
        title_card_active = title_active,
        title_card_complete = not title_active and title_children == 0,
        overlays_disabled = OVERLAYS_DISABLED,
        anim_counters = counters,
    }
end

local function append_failure(failures, condition, message)
    if not condition then failures[#failures + 1] = message end
end

local function visibility_failures(snapshot)
    local failures = {}
    append_failure(failures, snapshot.zone_and_act == 0x0400, "Current_zone_and_act != $0400")
    append_failure(failures, snapshot.game_mode == LEVEL_GAME_MODE, "game_mode != level ($0C)")
    append_failure(failures, snapshot.title_card_complete, "title card is active or has live children")
    append_failure(failures, snapshot.palette_fade_timer == 0, "palette_fade_timer != 0")
    append_failure(failures, snapshot.display_enabled, "VDP display-enable bit is clear")
    append_failure(failures, snapshot.overlays_disabled, "BizHawk overlays are not disabled")
    return failures
end

local function native_start_intent_failures(snapshot)
    local failures = visibility_failures(snapshot)
    append_failure(failures, snapshot.player_x == 0x0060, "native start x_pos != $0060")
    append_failure(failures, snapshot.player_y == 0x076C, "native start y_pos != $076C")
    append_failure(failures, snapshot.camera_x == 0x0000, "native start camera x != $0000")
    append_failure(failures, snapshot.events_routine_bg == 0x0000,
        "native start Events_routine_bg != 0")
    append_failure(failures, snapshot.events_bg_00 == 0x0018,
        "native start Events_bg+$00 != $0018")
    append_failure(failures, snapshot.events_bg_02 == 0xFF00,
        "native start Events_bg+$02 != $FF00")
    append_failure(failures, snapshot.events_bg_04 == 0xFF00,
        "native start Events_bg+$04 != $FF00")
    return failures
end

local function snapshot_fingerprint(snapshot)
    local values = {
        snapshot.player_x, snapshot.player_y, snapshot.camera_x, snapshot.camera_y,
        snapshot.camera_x_copy, snapshot.camera_y_copy,
        snapshot.camera_target_min_x, snapshot.camera_target_max_x,
        snapshot.camera_target_min_y, snapshot.camera_target_max_y,
        snapshot.camera_min_x, snapshot.camera_max_x, snapshot.camera_min_y, snapshot.camera_max_y,
        snapshot.zone_and_act, snapshot.events_routine_fg, snapshot.events_routine_bg,
        snapshot.events_bg_00, snapshot.events_bg_02, snapshot.events_bg_04, snapshot.events_bg_06,
        snapshot.screen_shake_flag, snapshot.screen_shake_offset,
        snapshot.background_collision_flag, snapshot.boss_flag, snapshot.level_frame_counter,
        snapshot.game_mode, snapshot.palette_fade_timer, snapshot.vdp_reg1_command,
        snapshot.title_card_code, snapshot.title_card_routine,
        snapshot.title_card_wait_timer, snapshot.title_card_child_count,
        tostring(snapshot.display_enabled), tostring(snapshot.title_card_active),
        tostring(snapshot.title_card_complete), tostring(snapshot.overlays_disabled),
    }
    for _, value in ipairs(snapshot.anim_counters) do values[#values + 1] = value end
    for index, value in ipairs(values) do values[index] = tostring(value) end
    return table.concat(values, ":")
end

local function write_snapshot_json(file, snapshot, indent)
    indent = indent or "    "
    local function field(name, value, comma)
        file:write(indent .. '"' .. name .. '": "' .. value .. '"' .. (comma == false and "" or ",") .. '\n')
    end
    field("player_x", hex16(snapshot.player_x))
    field("player_y", hex16(snapshot.player_y))
    field("camera_x", hex16(snapshot.camera_x))
    field("camera_y", hex16(snapshot.camera_y))
    field("camera_x_copy", hex16(snapshot.camera_x_copy))
    field("camera_y_copy", hex16(snapshot.camera_y_copy))
    field("camera_target_min_x", hex16(snapshot.camera_target_min_x))
    field("camera_target_max_x", hex16(snapshot.camera_target_max_x))
    field("camera_target_min_y", hex16(snapshot.camera_target_min_y))
    field("camera_target_max_y", hex16(snapshot.camera_target_max_y))
    field("camera_min_x", hex16(snapshot.camera_min_x))
    field("camera_max_x", hex16(snapshot.camera_max_x))
    field("camera_min_y", hex16(snapshot.camera_min_y))
    field("camera_max_y", hex16(snapshot.camera_max_y))
    field("zone_and_act", hex16(snapshot.zone_and_act))
    field("events_routine_fg", hex16(snapshot.events_routine_fg))
    field("events_routine_bg", hex16(snapshot.events_routine_bg))
    field("events_bg_00_word", hex16(snapshot.events_bg_00))
    field("events_bg_02_word", hex16(snapshot.events_bg_02))
    field("events_bg_04_word", hex16(snapshot.events_bg_04))
    field("events_bg_06_word", hex16(snapshot.events_bg_06))
    field("screen_shake_flag", hex16(snapshot.screen_shake_flag))
    field("screen_shake_offset", hex16(snapshot.screen_shake_offset))
    field("background_collision_flag", hex8(snapshot.background_collision_flag))
    field("boss_flag", hex8(snapshot.boss_flag))
    field("level_frame_counter", hex16(snapshot.level_frame_counter))
    field("game_mode", hex8(snapshot.game_mode))
    field("palette_fade_timer", hex16(snapshot.palette_fade_timer))
    field("vdp_reg1_command", hex16(snapshot.vdp_reg1_command))
    file:write(indent .. '"display_enabled": ' .. tostring(snapshot.display_enabled) .. ',\n')
    field("title_card_code", hex32(snapshot.title_card_code))
    field("title_card_routine", hex8(snapshot.title_card_routine))
    field("title_card_wait_timer", hex16(snapshot.title_card_wait_timer))
    field("title_card_child_count", hex16(snapshot.title_card_child_count))
    file:write(indent .. '"title_card_active": ' .. tostring(snapshot.title_card_active) .. ',\n')
    file:write(indent .. '"title_card_complete": ' .. tostring(snapshot.title_card_complete) .. ',\n')
    file:write(indent .. '"overlays_disabled": ' .. tostring(snapshot.overlays_disabled) .. ',\n')
    file:write(indent .. '"anim_counters": [')
    for index, value in ipairs(snapshot.anim_counters) do
        if index > 1 then file:write(', ') end
        file:write('"' .. hex8(value) .. '"')
    end
    file:write(']\n')
end

local vram_domain = nil
local function resolve_vram_domain()
    if vram_domain then return vram_domain end
    assert(memory and memory.getmemorydomainlist and memory.usememorydomain and memory.hash_region,
        "BizHawk memory-domain SHA256 API is required")
    for _, domain in ipairs(memory.getmemorydomainlist()) do
        if tostring(domain):lower() == "vram" then vram_domain = domain break end
    end
    assert(vram_domain, "BizHawk Genplus-gx VRAM memory domain is unavailable")
    return vram_domain
end

local function hash_vram(spec)
    local previous = memory.getcurrentmemorydomain and memory.getcurrentmemorydomain() or nil
    assert(previous, "cannot preserve the current BizHawk memory domain")
    local selected, select_failure = pcall(memory.usememorydomain, resolve_vram_domain())
    assert(selected, "cannot select VRAM memory domain: " .. tostring(select_failure))
    local ok, digest = pcall(memory.hash_region,
        spec.destination_tile * 32, spec.tile_count * 32, "SHA256")
    local restored, restore_failure = pcall(memory.usememorydomain, previous)
    assert(restored, "cannot restore memory domain: " .. tostring(restore_failure))
    assert(ok, "cannot hash VRAM destination: " .. tostring(digest))
    digest = tostring(digest):gsub("^0x", ""):upper()
    assert(#digest == 64 and digest:match("^[0-9A-F]+$"),
        "VRAM hash is not a SHA256 digest: " .. digest)
    return digest
end

local function trace_frame(bk2_frame)
    return bk2_frame - plan.bk2_frame_offset - 1
end

local function write_common_provenance(file, frame)
    file:write('  "host_receipt_required": "' .. json_escape(HOST_RECEIPT) .. '",\n')
    file:write('  "manifest_sha256": "' .. plan.manifest_sha256 .. '",\n')
    file:write('  "rom_sha1": "' .. ROM_SHA1 .. '",\n')
    file:write('  "bk2_sha256": "' .. BK2_SHA256 .. '",\n')
    file:write('  "bizhawk_version": "2.11",\n')
    file:write('  "genesis_core": "Genplus-gx",\n')
    file:write('  "bk2_frame": ' .. frame .. ',\n')
    file:write('  "trace_frame": ' .. trace_frame(frame) .. ',\n')
end

local start_frame = plan.bk2_frame_offset + 1
for _, checkpoint in ipairs(plan.checkpoints or {}) do
    if checkpoint.id == "fbz1-start-outdoor" then start_frame = checkpoint.bk2_frame break end
end

local observation_start = start_frame
for _, spec in ipairs(CADENCE_SPECS) do
    local frames = plan.cadence_series and plan.cadence_series[spec.plan_name] or nil
    assert(frames and #frames > 0, "capture plan is missing " .. spec.plan_name)
    spec.monitor_start = frames[1]
    if spec.monitor_start < observation_start then observation_start = spec.monitor_start end
    spec.phase = "waiting"
    spec.previous = nil
    spec.created_paths = {}
end
local observation_deadline = observation_start + OBSERVATION_LIMIT_FRAMES

local start_png = join_path(OUTPUT_ROOT, "raw/" .. START_OUTPUT_ID .. "-348x240.png")
local start_sidecar = join_path(OUTPUT_ROOT, "provenance/" .. START_OUTPUT_ID .. ".bizhawk.json")
local summary_path = join_path(OUTPUT_ROOT, "provenance/export-summary-v2.json")
ensure_absent(start_png)
ensure_absent(start_sidecar)
ensure_absent(summary_path)
for _, spec in ipairs(CADENCE_SPECS) do
    for index = 0, CADENCE_FOLLOWUP_FRAMES + 1 do
        local label = index == 0 and "zero-step" or (index == 1 and "one-step" or "followup")
        local stem = string.format("%s-%s-%02d", spec.output_name, label, index)
        ensure_absent(join_path(OUTPUT_ROOT, "raw/time-series/" .. stem .. ".png"))
        ensure_absent(join_path(OUTPUT_ROOT, "time-series/provenance/" .. stem .. ".json"))
    end
end

local start_captured = false
local start_capture_frame = nil
local start_failure = nil
local cadence_captures = 0

local function capture_start(snapshot, frame)
    local failures = native_start_intent_failures(snapshot)
    if #failures ~= 0 then return false end
    local temporary, screenshot_failure = capture_png_temporary(start_png)
    if not temporary then start_failure = screenshot_failure return false end
    publish_temporary(temporary, start_png)
    write_atomic(start_sidecar, function(file)
        file:write('{\n')
        file:write('  "schema_version": 2,\n')
        file:write('  "kind": "locked-on-bizhawk-first-visible-gameplay-reference",\n')
        file:write('  "checkpoint": "' .. START_OUTPUT_ID .. '",\n')
        file:write('  "source_checkpoint": "fbz1-start-outdoor",\n')
        file:write('  "capture_policy": "first fully visible observed gameplay frame",\n')
        file:write('  "authoritative_emulator_state": true,\n')
        file:write('  "visibility_gate": "PASS",\n')
        write_common_provenance(file, frame)
        file:write('  "ram": {\n')
        write_snapshot_json(file, snapshot)
        file:write('  }\n')
        file:write('}\n')
    end)
    start_captured = true
    start_capture_frame = frame
    print("FBZ first fully visible gameplay frame captured: " .. frame)
    return true
end

local function cadence_paths(spec, index)
    local label = index == 0 and "zero-step" or (index == 1 and "one-step" or "followup")
    local stem = string.format("%s-%s-%02d", spec.output_name, label, index)
    return label,
        join_path(OUTPUT_ROOT, "raw/time-series/" .. stem .. ".png"),
        join_path(OUTPUT_ROOT, "time-series/provenance/" .. stem .. ".json")
end

local function remember_created(spec, path)
    spec.created_paths[#spec.created_paths + 1] = path
end

local function fail_series(spec, message)
    if spec.zero and spec.zero.temporary_png then os.remove(spec.zero.temporary_png) end
    for _, path in ipairs(spec.created_paths) do os.remove(path) end
    spec.created_paths = {}
    spec.phase = "failed"
    spec.failure = message
    print("FBZ cadence rejected: " .. spec.output_name .. ": " .. message)
end

local function write_cadence_sidecar(spec, index, label, snapshot, frame, digest,
        zero_frame, one_frame, zero_repeat_digest, zero_repeat_stable)
    local _, _, sidecar_path = cadence_paths(spec, index)
    write_atomic(sidecar_path, function(file)
        file:write('{\n')
        file:write('  "schema_version": 2,\n')
        file:write('  "kind": "locked-on-bizhawk-natural-aniplc-cadence",\n')
        file:write('  "series": "' .. spec.output_name .. '",\n')
        file:write('  "source_plan_series": "' .. spec.plan_name .. '",\n')
        file:write('  "capture_index": ' .. index .. ',\n')
        file:write('  "step_kind": "' .. label .. '",\n')
        file:write('  "natural_expiry_observed": true,\n')
        file:write('  "semantic_review_status": "pending-independent-visible-region-review",\n')
        file:write('  "visibility_gate": "PASS",\n')
        file:write('  "channel": ' .. spec.channel .. ',\n')
        file:write('  "destination_tile": "' .. hex16(spec.destination_tile) .. '",\n')
        file:write('  "destination_byte_offset": "' .. hex16(spec.destination_tile * 32) .. '",\n')
        file:write('  "destination_byte_length": ' .. (spec.tile_count * 32) .. ',\n')
        file:write('  "reset_duration": ' .. spec.reset_duration .. ',\n')
        file:write('  "zero_step_bk2_frame": ' .. zero_frame .. ',\n')
        file:write('  "one_step_bk2_frame": ' .. one_frame .. ',\n')
        file:write('  "vram_sha256": "' .. digest .. '",\n')
        if zero_repeat_digest then
            file:write('  "zero_step_repeat_vram_sha256": "' .. zero_repeat_digest .. '",\n')
            file:write('  "zero_step_repeat_state_equal": ' .. tostring(zero_repeat_stable) .. ',\n')
        end
        write_common_provenance(file, frame)
        file:write('  "ram": {\n')
        write_snapshot_json(file, snapshot)
        file:write('  }\n')
        file:write('}\n')
    end)
    remember_created(spec, sidecar_path)
end

local function capture_published_cadence_frame(spec, index, snapshot, frame, digest)
    local failures = visibility_failures(snapshot)
    if #failures ~= 0 then
        fail_series(spec, "visibility gate failed during cadence")
        return false
    end
    local label, png_path = cadence_paths(spec, index)
    local temporary, screenshot_failure = capture_png_temporary(png_path)
    if not temporary then
        fail_series(spec, "screenshot failed: " .. tostring(screenshot_failure))
        return false
    end
    publish_temporary(temporary, png_path)
    remember_created(spec, png_path)
    write_cadence_sidecar(spec, index, label, snapshot, frame, digest,
        spec.zero.frame, spec.one_frame, nil, nil)
    cadence_captures = cadence_captures + 1
    return true
end

local function observe_series(spec, snapshot, frame)
    if spec.phase == "done" or spec.phase == "failed" or frame < spec.monitor_start then return end
    local failures = visibility_failures(snapshot)
    if #failures ~= 0 then
        spec.previous = nil
        if spec.zero then
            os.remove(spec.zero.temporary_png)
            spec.zero = nil
            spec.phase = "waiting"
        end
        return
    end

    local timer = snapshot.anim_counters[spec.channel * 2 + 1]
    local frame_index = snapshot.anim_counters[spec.channel * 2 + 2]

    if spec.phase == "waiting" then
        -- Capture a same-emulator-frame control only after the counter reaches
        -- zero naturally. A second RAM/VRAM read proves that the capture path
        -- itself performed a zero-step and did not hydrate or mutate state.
        if timer == 0 then
            local digest = hash_vram(spec)
            local repeat_snapshot = read_ram_snapshot()
            local repeat_digest = hash_vram(spec)
            local stable = snapshot_fingerprint(snapshot) == snapshot_fingerprint(repeat_snapshot)
                and digest == repeat_digest and emu.framecount() == frame
            if not stable then
                fail_series(spec, "zero-step RAM/VRAM control was not stable")
                return
            end
            local _, zero_png = cadence_paths(spec, 0)
            local temporary, screenshot_failure = capture_png_temporary(zero_png)
            if not temporary then
                fail_series(spec, "zero-step screenshot failed: " .. tostring(screenshot_failure))
                return
            end
            spec.zero = {
                frame = frame, frame_index = frame_index, snapshot = snapshot,
                digest = digest, repeat_digest = repeat_digest,
                stable = stable, temporary_png = temporary,
            }
            spec.phase = "awaiting-one-step"
        end
    elseif spec.phase == "awaiting-one-step" then
        if frame ~= spec.zero.frame + 1 then
            fail_series(spec, "one-step control was not the next emulated frame")
            return
        end
        if frame_index == spec.zero.frame_index or timer ~= spec.reset_duration then
            -- The observed zero did not expire on this tick. Discard the
            -- unpublished candidate and continue looking; never force it.
            os.remove(spec.zero.temporary_png)
            spec.zero = nil
            spec.phase = "waiting"
            spec.previous = snapshot
            return
        end

        local one_digest = hash_vram(spec)
        local _, zero_png = cadence_paths(spec, 0)
        publish_temporary(spec.zero.temporary_png, zero_png)
        spec.zero.temporary_png = nil
        remember_created(spec, zero_png)
        write_cadence_sidecar(spec, 0, "zero-step", spec.zero.snapshot,
            spec.zero.frame, spec.zero.digest, spec.zero.frame, frame,
            spec.zero.repeat_digest, spec.zero.stable)
        cadence_captures = cadence_captures + 1

        spec.one_frame = frame
        if not capture_published_cadence_frame(spec, 1, snapshot, frame, one_digest) then return end
        spec.last_frame = frame
        spec.next_index = 2
        spec.remaining = CADENCE_FOLLOWUP_FRAMES
        spec.phase = "followup"
    elseif spec.phase == "followup" then
        if frame ~= spec.last_frame + 1 then
            fail_series(spec, "cadence frames are not consecutive")
            return
        end
        local digest = hash_vram(spec)
        if not capture_published_cadence_frame(spec, spec.next_index, snapshot, frame, digest) then return end
        spec.last_frame = frame
        spec.next_index = spec.next_index + 1
        spec.remaining = spec.remaining - 1
        if spec.remaining == 0 then
            spec.phase = "done"
            print("FBZ natural AniPLC cadence captured: " .. spec.output_name)
        end
    end
    spec.previous = snapshot
end

local function all_series_terminal()
    for _, spec in ipairs(CADENCE_SPECS) do
        if spec.phase ~= "done" and spec.phase ~= "failed" then return false end
    end
    return true
end

local function run_export()
    while true do
        local frame = emu.framecount()
        if frame >= observation_start then
            local snapshot = read_ram_snapshot()
            if not start_captured and frame >= start_frame then capture_start(snapshot, frame) end
            for _, spec in ipairs(CADENCE_SPECS) do observe_series(spec, snapshot, frame) end
        end

        if start_captured and all_series_terminal() then break end
        if frame >= observation_deadline then break end
        if movie.isloaded() and movie.mode() == "FINISHED" then
            error("movie finished before the FBZ v2 observation window completed")
        end
        if client.ispaused() then client.unpause() end
        -- Rendering begins one frame before the observation window so every
        -- screenshot is sourced from the exact visible client surface.
        local next_frame = frame + 1
        client.invisibleemulation(next_frame < observation_start or next_frame > observation_deadline)
        emu.frameadvance()
    end

    if not start_captured then
        start_failure = start_failure or "no fully visible gameplay frame passed every gate"
    end
    for _, spec in ipairs(CADENCE_SPECS) do
        if spec.phase ~= "done" and spec.phase ~= "failed" then
            fail_series(spec, "no natural timer-zero to frame-advance transition was captured before deadline")
        end
    end

    local series_passes = 0
    local series_failures = 0
    for _, spec in ipairs(CADENCE_SPECS) do
        if spec.phase == "done" then series_passes = series_passes + 1 else series_failures = series_failures + 1 end
    end
    write_atomic(summary_path, function(file)
        file:write('{\n')
        file:write('  "schema_version": 2,\n')
        file:write('  "manifest_sha256": "' .. plan.manifest_sha256 .. '",\n')
        file:write('  "preserved_v1_evidence": true,\n')
        file:write('  "start_checkpoint": "' .. START_OUTPUT_ID .. '",\n')
        file:write('  "start_capture_pass": ' .. tostring(start_captured) .. ',\n')
        if start_capture_frame then file:write('  "start_bk2_frame": ' .. start_capture_frame .. ',\n') end
        if start_failure then file:write('  "start_failure": "' .. json_escape(start_failure) .. '",\n') end
        file:write('  "cadence_series_passes": ' .. series_passes .. ',\n')
        file:write('  "cadence_series_failures": ' .. series_failures .. ',\n')
        file:write('  "cadence_captures": ' .. cadence_captures .. ',\n')
        file:write('  "final_bk2_frame": ' .. emu.framecount() .. ',\n')
        file:write('  "series": {\n')
        for index, spec in ipairs(CADENCE_SPECS) do
            file:write('    "' .. spec.output_name .. '": {"status":"' .. spec.phase .. '"')
            if spec.failure then file:write(',"failure":"' .. json_escape(spec.failure) .. '"') end
            file:write('}' .. (index == #CADENCE_SPECS and '\n' or ',\n'))
        end
        file:write('  }\n')
        file:write('}\n')
    end)
    print(string.format(
        "FBZ visual v2 export complete: start=%s, cadence pass=%d fail=%d images=%d",
        tostring(start_captured), series_passes, series_failures, cadence_captures))
end

local ok, failure = xpcall(function()
    install_capture_config()
    run_export()
end, debug.traceback)
local restored = restore_config()
if not restored then error("fail-closed: BizHawk overlay configuration could not be restored") end
if not ok then error(failure) end

for _ = 1, 8 do
    client.exit()
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
client.pause()
