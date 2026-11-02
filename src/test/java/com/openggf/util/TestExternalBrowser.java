package com.openggf.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Shown addresses open over https through each platform's own opener, and nothing else is launched. */
class TestExternalBrowser {

    @Test void eachPlatformUsesItsOwnOpener() {
        String url = "https://openggf.com/discord";
        assertEquals(List.of("xdg-open", url), ExternalBrowser.command(url, "Linux"));
        assertEquals(List.of("open", url), ExternalBrowser.command(url, "Mac OS X"));
        assertEquals(List.of("rundll32", "url.dll,FileProtocolHandler", url), ExternalBrowser.command(url, "Windows 11"));
    }

    @Test void aShownAddressOpensOverHttps() {
        List<List<String>> started = new ArrayList<>();
        assertTrue(ExternalBrowser.open("openggf.com/report-issue", started::add));
        assertEquals("https://openggf.com/report-issue", started.getFirst().getLast());
    }

    @Test void onlyPlainWebAddressesReachTheOpener() {
        List<List<String>> started = new ArrayList<>();
        for (String address : List.of("openggf.com/x?y=1", "openggf.com/#top", "user@openggf.com/", "bad address", "/no-host")) {
            assertFalse(ExternalBrowser.open(address, started::add), address);
        }
        assertEquals(List.of(), started);
    }

    @Test void aMissingOpenerReportsFailure() {
        assertFalse(ExternalBrowser.open("openggf.com/discord", command -> { throw new IOException("not found"); }));
    }
}
