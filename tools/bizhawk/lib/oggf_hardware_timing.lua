------------------------------------------------------------------------------
-- S3K authoritative hardware-timing recorder.
--
-- Mirrors the four-entry Kosinski-module FIFO from main RAM, derives each
-- submission's stable descriptor from the user-supplied ROM, and emits only
-- final-module retirement edges. The module is shared by the standard and
-- complete-run recorders so their bytes and FIFO identity rules cannot drift.
------------------------------------------------------------------------------

local M = {}
local MASK = 0xFFFFFFFF
local K = {
    0x428A2F98,0x71374491,0xB5C0FBCF,0xE9B5DBA5,
    0x3956C25B,0x59F111F1,0x923F82A4,0xAB1C5ED5,
    0xD807AA98,0x12835B01,0x243185BE,0x550C7DC3,
    0x72BE5D74,0x80DEB1FE,0x9BDC06A7,0xC19BF174,
    0xE49B69C1,0xEFBE4786,0x0FC19DC6,0x240CA1CC,
    0x2DE92C6F,0x4A7484AA,0x5CB0A9DC,0x76F988DA,
    0x983E5152,0xA831C66D,0xB00327C8,0xBF597FC7,
    0xC6E00BF3,0xD5A79147,0x06CA6351,0x14292967,
    0x27B70A85,0x2E1B2138,0x4D2C6DFC,0x53380D13,
    0x650A7354,0x766A0ABB,0x81C2C92E,0x92722C85,
    0xA2BFE8A1,0xA81A664B,0xC24B8B70,0xC76C51A3,
    0xD192E819,0xD6990624,0xF40E3585,0x106AA070,
    0x19A4C116,0x1E376C08,0x2748774C,0x34B0BCB5,
    0x391C0CB3,0x4ED8AA4A,0x5B9CCA4F,0x682E6FF3,
    0x748F82EE,0x78A5636F,0x84C87814,0x8CC70208,
    0x90BEFFFA,0xA4506CEB,0xBEF9A3F7,0xC67178F2,
}

local function ror(x, n)
    return ((x >> n) | (x << (32 - n))) & MASK
end

local function be32(value)
    value = value & MASK
    return string.char(
        (value >> 24) & 0xFF,
        (value >> 16) & 0xFF,
        (value >> 8) & 0xFF,
        value & 0xFF)
end

local function sha256(value)
    local bit_length = #value * 8
    value = value .. string.char(0x80)
    value = value .. string.rep("\0", (56 - (#value % 64)) % 64)
    value = value .. be32(0) .. be32(bit_length)
    local h = {
        0x6A09E667,0xBB67AE85,0x3C6EF372,0xA54FF53A,
        0x510E527F,0x9B05688C,0x1F83D9AB,0x5BE0CD19,
    }
    for block = 1, #value, 64 do
        local w = {}
        for i = 0, 15 do
            local p = block + (i * 4)
            w[i] = ((value:byte(p) << 24)
                | (value:byte(p + 1) << 16)
                | (value:byte(p + 2) << 8)
                | value:byte(p + 3)) & MASK
        end
        for i = 16, 63 do
            local a = w[i - 15]
            local b = w[i - 2]
            local s0 = ror(a, 7) ~ ror(a, 18) ~ (a >> 3)
            local s1 = ror(b, 17) ~ ror(b, 19) ~ (b >> 10)
            w[i] = (w[i - 16] + s0 + w[i - 7] + s1) & MASK
        end
        local a,b,c,d,e,f,g,hh =
            h[1],h[2],h[3],h[4],h[5],h[6],h[7],h[8]
        for i = 0, 63 do
            local s1 = ror(e, 6) ~ ror(e, 11) ~ ror(e, 25)
            local ch = (e & f) ~ ((~e) & g)
            local t1 = (hh + s1 + ch + K[i + 1] + w[i]) & MASK
            local s0 = ror(a, 2) ~ ror(a, 13) ~ ror(a, 22)
            local maj = (a & b) ~ (a & c) ~ (b & c)
            local t2 = (s0 + maj) & MASK
            hh,g,f,e,d,c,b,a =
                g,f,e,(d + t1) & MASK,c,b,a,(t1 + t2) & MASK
        end
        h[1]=(h[1]+a)&MASK; h[2]=(h[2]+b)&MASK
        h[3]=(h[3]+c)&MASK; h[4]=(h[4]+d)&MASK
        h[5]=(h[5]+e)&MASK; h[6]=(h[6]+f)&MASK
        h[7]=(h[7]+g)&MASK; h[8]=(h[8]+hh)&MASK
    end
    local parts = {}
    for i = 1, 8 do parts[i] = string.format("%08x", h[i]) end
    return table.concat(parts)
end

local function fingerprint(kind, source, compressed_length, destination,
        destination_length, variant, module_count)
    local payload = be32(#kind) .. kind
        .. be32(source) .. be32(compressed_length) .. be32(destination)
        .. be32(destination_length)
        .. be32(#variant) .. variant .. be32(module_count)
    return "sha256:" .. sha256(payload)
end
M.fingerprint = fingerprint

local function rom_u8(address)
    return memory.read_u8(address, "MD CART")
end

local function pop_bit(state)
    if state.bits == 0 then
        state.descriptor = rom_u8(state.position)
            | (rom_u8(state.position + 1) << 8)
        state.position = state.position + 2
        state.bits = 16
    end
    local bit = state.descriptor & 1
    state.descriptor = state.descriptor >> 1
    state.bits = state.bits - 1
    if state.bits == 0 then
        -- Kos_Decomp_Loop / Kos_Decomp_Match reload d5 immediately when
        -- dbf consumes descriptor bit 16, before the selected command reads
        -- its literal or match payload (sonic3k.asm:2572-2600).
        state.descriptor = rom_u8(state.position)
            | (rom_u8(state.position + 1) << 8)
        state.position = state.position + 2
        state.bits = 16
    end
    return bit
end

local function scan_module(position)
    local state = {position=position, descriptor=0, bits=0}
    while true do
        if pop_bit(state) ~= 0 then
            state.position = state.position + 1
        elseif pop_bit(state) == 0 then
            pop_bit(state)
            pop_bit(state)
            state.position = state.position + 1
        else
            local high = rom_u8(state.position + 1)
            state.position = state.position + 2
            if (high & 7) == 0 then
                local terminator = rom_u8(state.position)
                state.position = state.position + 1
                if terminator == 0 then return state.position end
            end
        end
    end
end

local function inspect(source)
    local destination_length = (rom_u8(source) << 8) | rom_u8(source + 1)
    if destination_length == 0xA000 then destination_length = 0x8000 end
    local modules = math.floor((destination_length + 0xFFF) / 0x1000)
    local position = source + 2
    for module = 1, modules do
        position = scan_module(position)
        if module < modules then
            local relative = position - (source + 2)
            position = position + ((16 - (relative & 15)) & 15)
        end
    end
    return position - source, destination_length, modules
end

local RAM = {
    modules_left=0xFF60,
    queue=0xFF64,
    entry_size=6,
    capacity=4,
    nem_decomp_queue=0xF680,
    level_frame_counter=0xFE04,
    object_ram=0xB000,
    object_size=0x4A,
    title_card_parent_slot=8,
    title_card_code=0x0002D690,
    title_card_wait_offset=0x48,
}

function M.new_tracker()
    return {
        queue={},
        next_ordinal=0,
        prior_modules_left=0,
        prior_level_frame_counter=nil,
        title_card_load_loop_active=false,
    }
end

local function physical_count()
    local count = 0
    for index = 0, RAM.capacity - 1 do
        if mainmemory.read_u32_be(RAM.queue + index * RAM.entry_size) == 0 then
            break
        end
        count = count + 1
    end
    return count
end

local function update_title_card_load_loop(tracker, level_frame_counter)
    local parent = RAM.object_ram
        + RAM.title_card_parent_slot * RAM.object_size
    local parent_wait =
        mainmemory.read_u16_be(parent + RAM.title_card_wait_offset)
    local arm_now = level_frame_counter == 0
        and mainmemory.read_u32_be(parent) == RAM.title_card_code
        and parent_wait ~= 0
    local admitted = level_frame_counter == 0
        and (tracker.title_card_load_loop_active or arm_now)
    -- loc_62CC is a frame-zero Process_Sprites loop. Its exact parent state
    -- arms the lifecycle. Once armed, its raw wait word or the Nemesis queue
    -- retains it even if the object code has been deleted. The exit sample
    -- is still admitted because Process_Sprites ran before both tests clear;
    -- the clear applies to the next sample.
    if level_frame_counter ~= 0 then
        tracker.title_card_load_loop_active = false
    elseif not tracker.title_card_load_loop_active then
        tracker.title_card_load_loop_active = arm_now
    elseif parent_wait == 0
        and mainmemory.read_u32_be(RAM.nem_decomp_queue) == 0
    then
        tracker.title_card_load_loop_active = false
    end
    return admitted
end

local function make_job(tracker, source, destination)
    local compressed_length, destination_length, modules = inspect(source)
    local job = {
        ordinal=tracker.next_ordinal,
        source=source,
        destination=destination,
        fingerprint=fingerprint(
            "KOS_MODULE_QUEUE", source, compressed_length, destination,
            destination_length, "kosinski_moduled", modules),
    }
    tracker.next_ordinal = tracker.next_ordinal + 1
    return job
end

function M.observe(tracker, raw_frame, output_file)
    local modules_left = mainmemory.read_u8(RAM.modules_left)
    local count = physical_count()
    local level_frame_counter =
        mainmemory.read_u16_be(RAM.level_frame_counter)
    local title_card_loop_admitted =
        update_title_card_load_loop(tracker, level_frame_counter)
    local boundary = tracker.prior_level_frame_counter ~= nil
        and tracker.prior_level_frame_counter == level_frame_counter
        and not title_card_loop_admitted
        and "vint_service" or "post_objects"
    local retired = false
    if tracker.prior_modules_left == 0x81 and #tracker.queue > 0 then
        retired = modules_left == 0 or count == 0
        if not retired and #tracker.queue > 1 then
            local next_job = tracker.queue[2]
            retired =
                mainmemory.read_u32_be(RAM.queue) == next_job.source + 2
                and mainmemory.read_u16_be(RAM.queue + 4)
                    == next_job.destination
        end
    end
    if retired then
        local completed = table.remove(tracker.queue, 1)
        if output_file then
            output_file:write(string.format(
                '{"event":"hardware_work_completed","raw_frame":%d,'
                    .. '"boundary":"%s","kind":"kos_module_queue",'
                    .. '"ordinal":%d,"submission_fingerprint":"%s"}\n',
                raw_frame, boundary,
                completed.ordinal, completed.fingerprint))
        end
    end

    local retained = math.min(#tracker.queue, count)
    for index = 1, retained - 1 do
        local address = RAM.queue + index * RAM.entry_size
        local existing = tracker.queue[index + 1]
        if mainmemory.read_u32_be(address) ~= existing.source
            or mainmemory.read_u16_be(address + 4) ~= existing.destination
        then
            error("Kos module FIFO changed without retiring its mirrored head")
        end
    end
    if count < #tracker.queue then
        error("Kos module FIFO lost work without final-module retirement")
    end
    for index = #tracker.queue, count - 1 do
        local address = RAM.queue + index * RAM.entry_size
        local source = mainmemory.read_u32_be(address)
        if index == 0 then source = source - 2 end
        tracker.queue[#tracker.queue + 1] = make_job(
            tracker, source, mainmemory.read_u16_be(address + 4))
    end
    tracker.prior_modules_left = modules_left
    tracker.prior_level_frame_counter = level_frame_counter
end

return M
