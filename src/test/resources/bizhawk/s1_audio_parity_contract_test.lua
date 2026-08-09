local contractPath = assert(arg[1], "expected S1 audio contract path")
local vectorPath = assert(arg[2], "expected normalization vector path")
local Contract = dofile(contractPath)

local function check(condition, message)
    if not condition then error(message, 2) end
end

local function equals(actual, expected, message)
    check(actual == expected, message .. "\nexpected: " .. tostring(expected)
        .. "\nactual:   " .. tostring(actual))
end

-- A deliberately small JSON reader belongs in this harness, rather than the
-- capture contract: BizHawk does not supply a JSON library and capture never
-- needs to parse fixture files.
local function decodeJson(text)
    local position = 1
    local function skipWhitespace()
        local _, finish = text:find("^[ \t\r\n]*", position)
        position = finish + 1
    end
    local decodeValue
    local function decodeString()
        position = position + 1
        local parts = {}
        while true do
            local character = text:sub(position, position)
            check(character ~= "", "unterminated JSON string")
            position = position + 1
            if character == '"' then return table.concat(parts) end
            if character == "\\" then
                local escaped = text:sub(position, position)
                position = position + 1
                local replacements = {['"'] = '"', ['\\'] = '\\', ['/'] = '/', b = '\b', f = '\f', n = '\n', r = '\r', t = '\t'}
                if escaped == "u" then
                    local hex = text:sub(position, position + 3)
                    check(hex:match("^%x%x%x%x$"), "invalid JSON unicode escape")
                    position = position + 4
                    local codepoint = tonumber(hex, 16)
                    if codepoint < 128 then
                        parts[#parts + 1] = string.char(codepoint)
                    elseif codepoint < 2048 then
                        parts[#parts + 1] = string.char(192 + math.floor(codepoint / 64), 128 + codepoint % 64)
                    else
                        parts[#parts + 1] = string.char(224 + math.floor(codepoint / 4096), 128 + math.floor(codepoint / 64) % 64, 128 + codepoint % 64)
                    end
                else
                    check(replacements[escaped] ~= nil, "invalid JSON escape")
                    parts[#parts + 1] = replacements[escaped]
                end
            else
                parts[#parts + 1] = character
            end
        end
    end
    local function decodeArray()
        position = position + 1
        local result = {}
        skipWhitespace()
        if text:sub(position, position) == "]" then position = position + 1 return result end
        while true do
            result[#result + 1] = decodeValue()
            skipWhitespace()
            local separator = text:sub(position, position)
            check(separator == "," or separator == "]", "expected JSON array separator")
            position = position + 1
            if separator == "]" then return result end
        end
    end
    local function decodeObject()
        position = position + 1
        local result = {}
        skipWhitespace()
        if text:sub(position, position) == "}" then position = position + 1 return result end
        while true do
            skipWhitespace()
            check(text:sub(position, position) == '"', "expected JSON object key")
            local key = decodeString()
            skipWhitespace()
            check(text:sub(position, position) == ":", "expected JSON object colon")
            position = position + 1
            result[key] = decodeValue()
            skipWhitespace()
            local separator = text:sub(position, position)
            check(separator == "," or separator == "}", "expected JSON object separator")
            position = position + 1
            if separator == "}" then return result end
        end
    end
    decodeValue = function()
        skipWhitespace()
        local character = text:sub(position, position)
        if character == '"' then return decodeString() end
        if character == "{" then return decodeObject() end
        if character == "[" then return decodeArray() end
        for literal, value in pairs({["true"] = true, ["false"] = false, ["null"] = nil}) do
            if text:sub(position, position + #literal - 1) == literal then
                position = position + #literal
                return value
            end
        end
        local number = text:match("^-?%d+%.?%d*[eE]?[%+%-]?%d*", position)
        check(number ~= nil and number ~= "", "expected JSON value")
        position = position + #number
        return tonumber(number)
    end
    local result = decodeValue()
    skipWhitespace()
    check(position > #text, "unexpected trailing JSON")
    return result
end

local function readFile(path)
    local file = assert(io.open(path, "rb"))
    local text = assert(file:read("*a"))
    file:close()
    return text
end

local function runCanonicalJson()
    -- Break caught: changing sorted-key order or JSON escaping changes emitted parity bytes.
    equals(Contract.canonicalJson({z = "line\n\"\\\b\f\r\t", a = 1, array = {true, false}}),
        "{\"a\":1,\"array\":[true,false],\"z\":\"line\\n\\\"\\\\\\b\\f\\r\\t\"}",
        "canonical JSON did not sort keys and escape control characters")
end

local function runIntegerNormalization()
    -- Break caught: a wrapped RAM byte/word would compare as the wrong signed value.
    equals(Contract.u8(-1), 255, "u8 did not wrap")
    equals(Contract.u16(-1), 65535, "u16 did not wrap")
    equals(Contract.s8(254), -2, "s8 did not sign-extend")
    equals(Contract.s16(65533), -3, "s16 did not sign-extend")
end

local function runYmPairing()
    -- Break caught: address/data writes on different YM ports would be paired or reordered.
    local decoder = Contract.newYmDecoder()
    check(decoder:feed({kind = "address", port = 0, value = 34}) == nil, "YM address emitted an event")
    local first = decoder:feed({kind = "data", port = 0, value = 17})
    equals(Contract.canonicalJson(first),
        "{\"chip\":\"ym2612\",\"port\":0,\"register\":34,\"value\":17}",
        "port-zero YM pair was decoded incorrectly")
    decoder:feed({kind = "address", port = 1, value = 42})
    local second = decoder:feed({kind = "data", port = 1, value = 128})
    equals(Contract.canonicalJson(second),
        "{\"chip\":\"ym2612\",\"port\":1,\"register\":42,\"value\":128}",
        "port-one YM pair was decoded incorrectly")
    equals(Contract.canonicalJson(decoder:feed({kind = "psg", value = 159})),
        "{\"chip\":\"psg\",\"value\":159}", "PSG event was decoded incorrectly")
end

local function runYmRejections()
    -- Break caught: malformed bus observations would silently create a misleading parity stream.
    local decoder = Contract.newYmDecoder()
    local ok = pcall(function() decoder:feed({kind = "data", port = 0, value = 1}) end)
    check(not ok, "orphan YM data was accepted")
    ok = pcall(function() decoder:feed({kind = "address", port = 2, value = 1}) end)
    check(not ok, "unsupported YM port was accepted")
end

local function runHashes()
    -- Break caught: a changed byte or event order produces the same recurrence signature.
    equals(Contract.hashState({tempo = 3, track = {active = true, pos = 12}}), "5b5988cb",
        "state hash is not deterministic")
    equals(Contract.hashEvents({{chip = "psg", value = 159}, {chip = "ym2612", port = 0, register = 34, value = 17}}),
        "b6dd9bcc", "event hash is not deterministic")
end

local function runCycleProof()
    -- Break caught: a single repeated state ends capture without proving its following period.
    local detector = Contract.newCycleDetector()
    local stream = {
        {"a", "0"}, {"b", "1"}, {"c", "2"}, {"a", "0"}, {"b", "1"}, {"x", "x"},
        {"a", "0"}, {"b", "1"}, {"c", "2"}, {"a", "0"}, {"b", "1"}, {"c", "2"}, {"a", "0"}
    }
    local proof
    for _, pair in ipairs(stream) do proof = detector:observe(pair[1], pair[2]) or proof end
    equals(proof.startOrdinal, 6, "cycle detector accepted the rejected candidate")
    equals(proof.period, 3, "cycle detector proved the wrong period")
    equals(proof.terminalRecordCount, 13, "cycle detector stopped before the third boundary")
end

local function runCycleLimit()
    -- Break caught: an unbounded capture can hang when music never recurs.
    local detector = Contract.newCycleDetector()
    for ordinal = 0, 35999 do detector:observe("state-" .. ordinal, "events") end
    local ok = pcall(function() detector:observe("state-over-limit", "events") end)
    check(not ok, "cycle detector accepted invocation 36,001")
end

local function runGoldenVector()
    -- Break caught: ROM- and OpenGGF-shaped state normalize to divergent bytes or include stale capacity.
    local vector = decodeJson(readFile(vectorPath))
    local rom = Contract.normalizeRom(vector.rawRom, vector.activeLoopIndices)
    local engine = Contract.normalizeOpenGgf(vector.openGgf, vector.activeLoopIndices)
    equals(Contract.canonicalJson(rom), Contract.canonicalJson(engine),
        "ROM and OpenGGF normalization disagreed")
    local decoder = Contract.newYmDecoder()
    local events = {}
    for _, busEvent in ipairs(vector.busEvents) do
        local event = decoder:feed(busEvent)
        if event then events[#events + 1] = event end
    end
    equals(Contract.canonicalJson({state = rom, events = events}), vector.expectedCanonicalJson,
        "shared normalization golden vector bytes changed")
end

runCanonicalJson()
runIntegerNormalization()
runYmPairing()
runYmRejections()
runHashes()
runCycleProof()
runCycleLimit()
runGoldenVector()
print("S1_AUDIO_PARITY_CONTRACT_OK")
