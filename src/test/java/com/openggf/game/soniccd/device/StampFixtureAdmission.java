package com.openggf.game.soniccd.device;

import com.fasterxml.jackson.databind.JsonNode;

/** Admission of the observed title continuation contract, never a runtime data source. */
final class StampFixtureAdmission {
    private StampFixtureAdmission() { }

    static CdStampRenderState admit(JsonNode fixture) {
        require("partial-device-continuation".equals(fixture.path("observation_kind").asText()), "partial boundary");
        require("68000-address-order-big-endian".equals(fixture.path("wordram_encoding").asText()), "RAM encoding");
        equal(262144, integer(fixture, "wordram_bytes"), "RAM extent");
        equal(0x1d000, integer(fixture, "image_base"), "image base");
        equal(0x3000, integer(fixture, "image_bytes"), "image extent");
        equal(0xa12003, integer(fixture, "callback_address"), "ownership poll address");
        equal(1, integer(fixture, "callback_value"), "ownership poll result");
        equal(4096, integer(fixture, "callback_flags"), "ownership read callback");
        require(fixture.path("ingress_boundary").asText().contains("partial progress"), "ingress boundary");
        require(fixture.path("egress_boundary").asText().contains("before image read"), "egress boundary");

        JsonNode request = fixture.path("request");
        JsonNode in = fixture.path("ingress_devices");
        JsonNode out = fixture.path("egress_devices");
        int inMode = register(in, "regs.02");
        int outMode = register(out, "regs.02");
        // These observations certify 2M Word RAM and normal priority only.
        equal(2, inMode & 7, "ingress 2M sub-CPU ownership");
        equal(1, outMode & 7, "egress 2M main-CPU ownership");
        equal(0, inMode & 0x18, "ingress normal priority");
        equal(inMode & 0x1c, outMode & 0x1c, "stable RAM mode and priority");
        equal(1, integer(in, "dmna"), "ingress ownership latch");
        equal(0, integer(out, "dmna"), "egress ownership latch");
        equal(0x8007, register(in, "regs.58"), "busy mode-7 ingress");
        equal(7, register(out, "regs.58"), "idle matching mode-7 egress");
        equal(inMode, integer(request, "wordram_mode_register"), "request memory mode");
        equal(7, integer(request, "stamp_size_register"), "request stamp mode");

        for (String name : new String[]{"regs.5a", "regs.5c", "regs.5e", "regs.60", "regs.62", "regs.66"}) {
            equal(register(in, name), register(out, name), "stable " + name);
        }
        var semantic = CdStampRenderRequest.fromRegisterWords(register(in, "regs.58"), inMode,
                register(in, "regs.5a"), register(in, "regs.5c"), register(in, "regs.62"));
        equal(256, semantic.widthPixels(), "observed width");
        equal(12, semantic.imageColumnStrideCells(), "observed column stride");
        equal(0, register(in, "regs.60"), "observed image offset");
        equal(semantic.widthPixels(), integer(request, "width"), "request width");
        equal(register(in, "regs.5e"), integer(request, "image_start_register"), "request image base");
        equal(semantic.mapByteAddress(), integer(request, "map_offset"), "request map base");
        equal(0x20000, semantic.mapByteAddress(), "observed map base");
        equal(0x1d000, (register(in, "regs.5e") << 2) & 0x3ffe0, "register image base");

        int completed = integer(request, "completed_lines");
        int remaining = integer(request, "remaining_lines");
        equal(96, integer(request, "reconstructed_total_lines"), "observed total height");
        equal(96, completed + remaining, "partial progress total");
        require(completed > 0 && remaining > 0, "partial progress must be explicit");
        equal(remaining, register(in, "regs.64"), "remaining height register");
        equal(0, register(out, "regs.64"), "completed height register");
        int traceBase = (register(in, "regs.66") << 2) & 0x3fff8;
        equal(traceBase, integer(request, "trace_base"), "request trace base");
        int traceCursor = integer(in, "gfx_hw.tracePtr");
        long imageCursor = number(in, "gfx_hw.bufferStart");
        equal(traceCursor, integer(request, "cursor"), "request trace cursor");
        equal((traceBase + completed * 8) & 0x3ffff, traceCursor, "captured trace progress");
        var start = CdStampRenderState.fromStartRegisterWords(semantic, register(in, "regs.66"),
                register(in, "regs.5e"), register(in, "regs.60"), completed + remaining);
        equal((start.imageNibbleCursor() + completed * 8L) & 0xffffffffL, imageCursor,
                "captured nibble progress");

        equal(0x7fffff, integer(in, "gfx_hw.dotMask"), "derived source mask");
        equal(16, integer(in, "gfx_hw.stampShift"), "derived stamp shift");
        equal(7, integer(in, "gfx_hw.mapShift"), "derived map shift");
        equal(761, integer(in, "gfx_hw.bufferOffset"), "derived column step in nibbles");
        equal(semantic.mapByteAddress(), integer(in, "gfx_hw.mapPtr"), "derived map cursor");
        for (String name : new String[]{"gfx_hw.dotMask", "gfx_hw.stampShift", "gfx_hw.mapShift",
                "gfx_hw.bufferOffset", "gfx_hw.mapPtr"}) {
            equal(integer(in, name), integer(out, name), "stable " + name);
        }
        equal((traceCursor + remaining * 8) & 0x3ffff, integer(out, "gfx_hw.tracePtr"), "egress trace cursor");
        equal((imageCursor + remaining * 8L) & 0xffffffffL, number(out, "gfx_hw.bufferStart"),
                "egress nibble cursor");
        return new CdStampRenderState(semantic, traceCursor, imageCursor, completed, remaining);
    }

    static int integer(JsonNode object, String field) {
        long value = number(object, field);
        require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE, "integer field " + field);
        return (int) value;
    }

    private static int register(JsonNode object, String field) {
        int value = integer(object, field);
        require(value >= 0 && value <= 0xffff, "register width " + field);
        return value;
    }

    private static long number(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToLong(), "missing/nonnumeric " + field);
        return value.longValue();
    }

    private static void equal(long expected, long actual, String field) {
        require(expected == actual, field + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalArgumentException(message); }
    }
}
