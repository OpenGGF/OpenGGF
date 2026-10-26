package com.openggf.tools.audio.completerun.s1;

/** Source-format vectors, independent of a ROM, movie, native installation or captured route. */
final class S1RawContractVectors {
    private S1RawContractVectors() { }

    static String metadata() {
        return "{\"type\":\"metadata\",\"schema\":\"openggf.s1-complete-run-audio-raw.v1\","
                + "\"rom_sha1\":\"69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B\","
                + "\"bk2_sha256\":\"f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b\","
                + "\"first_row\":860,\"exclusive_end\":225101,\"native_abi\":5,"
                + "\"native_event_size\":32,\"native_capacity\":65536}\n";
    }

    static String baseline() {
        return "{\"type\":\"baseline\",\"row\":860,\"state_start\":61440,\"state_hex\":\""
                + "00".repeat(1472) + "\",\"ym_port0_latch\":42,\"ym_port1_latch\":161,"
                + "\"active_services\":[],\"pending_descendants\":[],\"native_arm_epoch\":1,"
                + "\"native_armed\":true}\n";
    }

    static String terminal(int end) {
        return "{\"type\":\"terminal\",\"exclusive_end\":" + end + ",\"rows\":" + (end - 860)
                + ",\"orphan_closes\":0,\"opcode_mismatches\":0,\"overflows\":0}\n";
    }

    static String frame(int row, String body) {
        return "{\"type\":\"frame_begin\",\"row\":" + row + "}\n" + body
                + "{\"type\":\"frame_end\",\"row\":" + row + "}\n";
    }

    static String event(int row, int ordinal, int token, int parent, int kind, int service,
            int depth, int cpu, int pc, int subject, int offset, int length, int value, int flags,
            String payload) {
        return "{\"type\":\"native_event\",\"row\":" + row + ",\"ordinal\":" + ordinal
                + ",\"service_token\":" + token + ",\"parent_token\":" + parent + ",\"pc\":" + pc
                + ",\"subject\":" + subject + ",\"offset\":" + offset + ",\"kind\":" + kind
                + ",\"service_kind\":" + service + ",\"depth\":" + depth + ",\"source_cpu\":" + cpu
                + ",\"payload_length\":" + length + ",\"value\":" + value + ",\"flags\":" + flags
                + ",\"payload\":" + payload + "}\n";
    }

    static String music(int row, int ordinal, int kind, int subject, int value) {
        int pc = kind == 1 ? 0x71b4c : kind == 2 ? 0x71c4c : 0x72752;
        return event(row, ordinal, 1, 0, kind, 4, 0, 2, pc, subject, 0, 0, value, 0, "0");
    }

    static String prefix() {
        return metadata() + baseline()
                + frame(860, music(860, 0, 1, 112, 0)
                        + music(860, 1, 3, 1, 127) // baseline port0 latch is $2A
                        + music(860, 2, 3, 2, 180)
                        + music(860, 3, 3, 3, 192))
                + frame(861, music(861, 0, 4, 0, 159)
                        + music(861, 1, 3, 3, 64)
                        + musicSnapshot(861, 2)
                        + music(861, 5, 2, 131, 0))
                + terminal(862);
    }

    static String musicSnapshot(int row, int ordinal) {
        return snapshot(row, ordinal, 1, 0, 4, 0, 2, 0x71c4c, 10);
    }

    static String snapshot(int row, int ordinal, int token, int parent, int service,
            int depth, int cpu, int pc, int range) {
        return event(row, ordinal, token, parent, 5, service, depth, cpu, pc, range, 0, 0, 0, 0, "0")
                + event(row, ordinal + 1, token, parent, 6, service, depth, cpu, pc, range, 0, 1, 0, 0, "0")
                + event(row, ordinal + 2, token, parent, 7, service, depth, cpu, pc, range, 1, 0, 0, 0, "0");
    }
}
