package com.openggf.i18n;

import com.openggf.graphics.text.HostTextContext;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class TestUiLocaleState {
    @Test
    void savedViewAndTestTeardownRestoreWordingAndFontContextTogether() {
        UiMessages.selectLanguage("en");
        var before = UiLocaleState.capture();
        var view = UiMessages.current();
        var font = HostTextContext.current();
        Locale machineLocale = Locale.getDefault();
        try {
            UiMessages.selectLanguage("ja");
            assertEquals("ja", UiMessages.locale().toLanguageTag());
            assertEquals(Locale.ENGLISH, view.locale(), "Captured views do not change during a later frame");
            assertEquals(UiMessages.locale(), HostTextContext.current().locale());
            assertEquals(machineLocale, Locale.getDefault());
        } finally { before.restore(); }
        assertSame(view, UiMessages.current());
        assertEquals(font, HostTextContext.current());
    }
}
