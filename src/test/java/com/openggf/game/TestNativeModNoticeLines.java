package com.openggf.game;

import com.openggf.GameLoop;
import com.openggf.mods.NativeUnsupportedMods;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestNativeModNoticeLines {

    @Test
    void bootNoticeWiringDoesNotExpandThePublicModApi() throws NoSuchMethodException {
        var supplierSetter = GameLoop.class.getDeclaredMethod(
                "setNativeModNoticeScreenSupplier", java.util.function.Supplier.class);
        var exitSetter = GameLoop.class.getDeclaredMethod(
                "setNativeModNoticeExitHandler", Runnable.class);

        assertTrue(!Modifier.isPublic(supplierSetter.getModifiers()));
        assertTrue(!Modifier.isPublic(exitSetter.getModifiers()));
    }

    @Test
    void screenBudgetProducesTruncation() {
        var names = IntStream.range(0, NativeModNoticeScreen.MAX_VISIBLE_MOD_LINES + 3)
                .mapToObj(i -> "mod" + i).toList();

        var lines = NativeUnsupportedMods.noticeLines(
                names, NativeModNoticeScreen.MAX_VISIBLE_MOD_LINES);

        assertEquals(NativeModNoticeScreen.MAX_VISIBLE_MOD_LINES + 1, lines.size());
        assertTrue(lines.getLast().endsWith(" more"));
    }
}
