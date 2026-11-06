package com.openggf.control;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.memAddress;

class TestTextInputSession {
    private static Composition preedit(String text) {
        return Composition.fromCodepoints(text.codePoints().toArray(), new int[] {text.codePointCount(0, text.length())}, 0, text.codePointCount(0, text.length()));
    }
    @Test void utf32ArraysAreCopiedAndIndicesConvertOnce() {
        try (var stack = MemoryStack.stackPush()) {
            var text = stack.ints('A', 0x1F600, '日', 'e', 0x301);
            var blocks = stack.ints(2, 3);
            var composition = GlfwTextInputAdapter.copyPreedit(5, memAddress(text), 2, memAddress(blocks), 1, 4);
            text.put(0, 'Z'); blocks.put(0, 5);
            assertEquals("A😀日é", composition.text());
            assertEquals(List.of(new Composition.Clause(0, 3), new Composition.Clause(3, 6)), composition.clauses());
            assertEquals(6, composition.caret(), "The caret snaps past a combining sequence");
            assertEquals(1, composition.focusedClause());
            assertThrows(UnsupportedOperationException.class, () -> composition.clauses().clear());
        }
    }
    @Test void malformedNativeMetadataIsBoundedAndClausesNormalize() {
        assertEquals(Composition.EMPTY, GlfwTextInputAdapter.copyPreedit(0, 0, 0, 0, 12, 500));
        for (int count : new int[] {-1, 4097, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> GlfwTextInputAdapter.copyPreedit(count, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> GlfwTextInputAdapter.copyPreedit(1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Composition.fromCodepoints(new int[] {0xD800}, new int[0], 0, 0));
        var normalized = Composition.fromCodepoints("é日".codePoints().toArray(), new int[] {1, 2}, 0, 500);
        assertEquals(List.of(new Composition.Clause(0, 3)), normalized.clauses());
        assertEquals(-1, normalized.focusedClause());
        assertEquals(3, normalized.caret());
        assertEquals(-1, Composition.fromCodepoints(new int[] {'a'}, new int[] {1}, 22, -5).focusedClause());
    }
    @Test void bothPollOrdersSuppressKeysBeforeChromeAndThroughRelease() {
        for (boolean keyFirst : new boolean[] {true, false}) {
            InputHandler input = new InputHandler();
            var session = InputHandlerInternalAccess.textInput(input);
            session.claim(this);
            List<Integer> chrome = new ArrayList<>();
            var adapter = adapter(input, chrome);
            if (keyFirst) adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0);
            adapter.preedit(preedit("日本語"));
            if (!keyFirst) adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0);
            adapter.endEvents();
            assertFalse(input.isKeyPressed(GLFW_KEY_ENTER));
            assertFalse(input.isCapturedKeyDown(GLFW_KEY_ENTER));
            assertTrue(chrome.isEmpty());
            assertTrue(input.capturePhysicalInput().keysDown().isEmpty());
            adapter.preedit(Composition.EMPTY); adapter.endEvents(); input.update();
            adapter.key(GLFW_KEY_ENTER, GLFW_REPEAT, 0); adapter.endEvents();
            assertTrue(chrome.isEmpty(), "A held composition key stays suppressed after clear");
            adapter.key(GLFW_KEY_ENTER, GLFW_RELEASE, 0); adapter.endEvents(); input.update();
            adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0); adapter.endEvents();
            assertTrue(input.isKeyPressed(GLFW_KEY_ENTER));
            assertEquals(List.of(GLFW_KEY_ENTER, GLFW_KEY_ENTER), chrome);
        }
    }
    @Test void existingHeldKeysAndPhysicalSceneEdgesCannotLeakWhenPreeditStarts() {
        var input = new InputHandler(); var session = InputHandlerInternalAccess.textInput(input); session.claim(this);
        input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
        session.preedit(preedit("候補"));
        assertFalse(MenuInput.textDetails(input));
        assertFalse(input.isAnyKeyJustPressed());
        assertTrue(input.capturePhysicalInput().events().isEmpty());
        session.preedit(Composition.EMPTY); session.endEvents(); input.update();
        assertFalse(input.isPhysicalKeyDown(GLFW_KEY_F1));
        input.handleKeyEvent(GLFW_KEY_F1, GLFW_RELEASE); input.update();
        input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
        assertTrue(MenuInput.textDetails(input));
    }
    @Test void resetWithoutNativeAcknowledgmentKeepsOldCommitsDrainingAcrossFramesAndOwners() {
        for (boolean clearFirst : new boolean[] {true, false}) {
            var input = new InputHandler(); var session = InputHandlerInternalAccess.textInput(input);
            var first = session.claim(new Object());
            int[] resets = {0};
            session.attach(new TextInputSession.NativeHost() {
                public void reset() { resets[0]++; }
                public void caretRectangle(int x, int y, int w, int h) { }
            });
            session.preedit(preedit("古い"));
            first.close();
            Object second = new Object(); var next = session.claim(second);
            for (int i = 0; i < 10; i++) {
                session.claim(second); input.update();
                MenuInput.handleCharEvent(input, '旧');
                assertEquals("", input.consumeTypedText());
                assertTrue(next.suppressCommands());
            }
            if (clearFirst) session.preedit(Composition.EMPTY);
            MenuInput.handleCharEvent(input, '古');
            if (!clearFirst) session.preedit(Composition.EMPTY);
            assertEquals("", input.consumeTypedText());
            session.endEvents(); input.update(); session.claim(second);
            MenuInput.handleCharEvent(input, '新');
            assertEquals("新", input.consumeTypedText());
            assertEquals(1, resets[0]);
            assertEquals(Composition.EMPTY, first.composition());
        }
    }
    @Test void focusWithdrawalAndStandaloneHandlersAreIndependent() {
        var first = new InputHandler(); var second = new InputHandler();
        first.captureKeyboardText(); second.captureKeyboardText();
        var a = InputHandlerInternalAccess.textInput(first); var b = InputHandlerInternalAccess.textInput(second);
        a.preedit(preedit("한글"));
        assertEquals("한글", a.legacyComposition().text());
        assertEquals(Composition.EMPTY, b.legacyComposition());
        assertEquals("", first.consumeTypedText(), "Preedit is never public committed text");
        a.focus(false); a.focus(true); first.captureKeyboardText();
        MenuInput.handleCharEvent(first, '旧'); assertEquals("", first.consumeTypedText());
        a.preedit(Composition.EMPTY); a.endEvents(); first.update(); first.captureKeyboardText();
        MenuInput.handleCharEvent(first, '新'); assertEquals("新", first.consumeTypedText());
        second.handleKeyEvent(GLFW_KEY_A, GLFW_PRESS); assertTrue(second.isCapturedKeyDown(GLFW_KEY_A));
    }
    @Test void chromeCanKeepItsOwnPreeditAndCharacterPolicy() {
        var input = new InputHandler(); List<Integer> chrome = new ArrayList<>();
        var adapter = new GlfwTextInputAdapter(input, new GlfwTextInputAdapter.Filters() {
            public boolean key(int key, int action, int mods) { chrome.add(key); return false; }
            public boolean character(int cp) { return false; }
            public boolean preedit(Composition composition) { return false; }
        });
        adapter.preedit(preedit("native frontend")); adapter.character('x'); adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0); adapter.endEvents();
        assertEquals(List.of(GLFW_KEY_ENTER), chrome);
        assertEquals("", input.consumeTypedText());
        assertFalse(input.isKeyDown(GLFW_KEY_ENTER));
    }
    @Test void viewportMappingUsesUnequalDpiAndChromeAdjustedLetterbox() {
        var viewport = new GlfwTextInputAdapter.Viewport(70, 80, 853, 448, 426, 224);
        var rectangle = GlfwTextInputAdapter.toWindowRectangle(viewport, 801, 503, 1001, 755, 304, 40, 1, 18);
        double left = (70 + 304 * 853.0 / 426) * 801 / 1001;
        double top = (755 - 80 - 448 + 40 * 448.0 / 224) * 503 / 755;
        assertTrue(rectangle.x() <= left && rectangle.x() + 1 > left);
        assertTrue(rectangle.y() <= top && rectangle.y() + 1 > top);
        double right = (70 + 305 * 853.0 / 426) * 801 / 1001;
        assertTrue(rectangle.x() + rectangle.width() >= right);
        assertNull(GlfwTextInputAdapter.toWindowRectangle(viewport, 0, 0, 0, 0, 0, 0, 1, 1));
    }
    @Test void hostCaptureUsesExplicitSceneOwnershipAndLeaseRetirement() {
        InputHandler input = new InputHandler();
        Object oldScene = new Object(), newCourse = new Object();
        var first = InputHandlerInternalAccess.captureKeyboardText(input, oldScene);
        var session = InputHandlerInternalAccess.textInput(input);
        session.preedit(preedit("場面"));
        assertEquals("場面", session.legacyComposition().text());
        first.close();
        InputHandlerInternalAccess.captureKeyboardText(input, newCourse);
        MenuInput.handleCharEvent(input, '旧');
        assertEquals("", input.consumeTypedText());
        session.preedit(Composition.EMPTY); session.endEvents(); input.update();
        InputHandlerInternalAccess.captureKeyboardText(input, newCourse);
        session.preedit(preedit("course"));
        assertEquals("course", session.legacyComposition().text());
        first.close();
        assertEquals("course", session.legacyComposition().text(), "Retiring an old lease cannot close a new course");
    }

    @Test void inertNativeCapabilityFallbackKeepsTypingAndFrameExpiryFunctional() {
        var input = new InputHandler();
        input.captureKeyboardText();
        var adapter = adapter(input, new ArrayList<>());
        adapter.character('é'); adapter.key(GLFW_KEY_A, GLFW_PRESS, 0); adapter.endEvents();
        assertEquals("é", input.consumeTypedText());
        assertTrue(input.isCapturedKeyPressed(GLFW_KEY_A));
        input.update(); input.update(); input.update();
        assertEquals(Composition.EMPTY, InputHandlerInternalAccess.textInput(input).legacyComposition());
        assertTrue(input.isPhysicalKeyDown(GLFW_KEY_A));
    }

    @Test void orphanPreeditQuarantinesCommitsWithoutStealingGameplayKeysDuringNoOpReset() {
        var input = new InputHandler();
        var session = InputHandlerInternalAccess.textInput(input);
        int[] resets = {0};
        session.attach(new TextInputSession.NativeHost() {
            public void reset() { resets[0]++; }
            public void caretRectangle(int x, int y, int width, int height) { }
        });
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        session.preedit(preedit("orphan"));
        assertTrue(input.isPhysicalKeyDown(GLFW_KEY_RIGHT));
        for (int i = 0; i < 8; i++) {
            input.update(); input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
            assertTrue(MenuInput.details(input));
            assertTrue(input.isPhysicalKeyDown(GLFW_KEY_RIGHT));
            assertFalse(session.suppressCommands());
            MenuInput.handleCharEvent(input, '旧');
            assertEquals("", input.consumeTypedText());
            input.handleKeyEvent(GLFW_KEY_F1, GLFW_RELEASE);
        }
        assertEquals(1, resets[0]);
        session.preedit(Composition.EMPTY); session.endEvents();
        MenuInput.handleCharEvent(input, '新'); assertEquals("新", input.consumeTypedText());
    }

    @Test void ordinaryChromeKeyAndCharacterCallbacksRetainTheirOrderAndPhysicalReleases() {
        var input = new InputHandler(); List<String> received = new ArrayList<>();
        var adapter = new GlfwTextInputAdapter(input, new GlfwTextInputAdapter.Filters() {
            public boolean key(int key, int action, int mods) { received.add("key:" + action); return true; }
            public boolean character(int cp) { received.add("char:" + (char) cp); return true; }
        });
        adapter.key(GLFW_KEY_A, GLFW_PRESS, 0); adapter.character('a'); adapter.key(GLFW_KEY_A, GLFW_RELEASE, 0);
        adapter.endEvents();
        assertEquals(List.of("key:1", "char:a", "key:0"), received);
        assertEquals("a", input.consumeTypedText());
        assertEquals(List.of(1f, 0f), input.capturePhysicalInput().events().stream().map(PhysicalInputEvent::value).toList());
        var session = InputHandlerInternalAccess.textInput(input); session.claim(this); session.preedit(preedit("compose"));
        adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0); adapter.key(GLFW_KEY_ENTER, GLFW_RELEASE, 0); adapter.endEvents();
        assertTrue(input.capturePhysicalInput().events().isEmpty(), "A suppressed press must not invent a physical release");
        input.update(); session.preedit(Composition.EMPTY); session.endEvents(); input.update();
        input.handleKeyEvent(GLFW_KEY_A, GLFW_PRESS); input.capturePhysicalInput();
        session.claim(this); session.preedit(preedit("next")); adapter.key(GLFW_KEY_A, GLFW_RELEASE, 0); adapter.endEvents();
        assertEquals(List.of(0f), input.capturePhysicalInput().events().stream().map(PhysicalInputEvent::value).toList(), "A previously delivered press still gets its real release");
    }

    @Test void focusLossDiscardsEarlierQueuedEventsEvenIfFocusReturnsInTheSamePoll() {
        var input = new InputHandler(); var adapter = adapter(input, new ArrayList<>());
        adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0); adapter.character('x');
        adapter.focus(false); input.clearKeyState();
        adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0); adapter.character('y');
        adapter.focus(true); adapter.endEvents();
        assertFalse(input.isAnyKeyJustPressed());
        assertEquals("", input.consumeTypedText());
    }

    @Test void releaseBeforeFocusLossStillRearmsTheFirstFreshPress() {
        var input = new InputHandler();
        var session = InputHandlerInternalAccess.textInput(input);
        var adapter = adapter(input, new ArrayList<>());
        session.claim(this);
        adapter.preedit(preedit("候補"));
        adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0);
        adapter.endEvents();
        assertFalse(input.isCapturedKeyDown(GLFW_KEY_ENTER));
        adapter.key(GLFW_KEY_ENTER, GLFW_RELEASE, 0);
        adapter.focus(false);
        input.clearKeyState();
        adapter.preedit(Composition.EMPTY);
        adapter.focus(true);
        adapter.endEvents();
        input.update();
        session.claim(this);
        adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0);
        adapter.endEvents();
        assertTrue(MenuInput.textAccept(input), "The real release before focus loss must rearm Enter");
    }

    @Test void disablingCourseCaptureReleasesOnlyThatCoursesLease() {
        var input = new InputHandler(); var course = new Object(); var other = new Object();
        InputHandlerInternalAccess.captureKeyboardText(input, course);
        var session = InputHandlerInternalAccess.textInput(input); session.preedit(preedit("course"));
        InputHandlerInternalAccess.releaseKeyboardText(input, other);
        assertEquals("course", session.legacyComposition().text());
        InputHandlerInternalAccess.releaseKeyboardText(input, course);
        assertEquals(Composition.EMPTY, session.legacyComposition());
        assertFalse(session.suppressCommands());
        MenuInput.handleCharEvent(input, '旧'); assertEquals("", input.consumeTypedText());
    }

    @Test void preeditRetainsEarlierPhysicalReleasesButDropsUnpublishedPressReleasePairs() {
        var input = new InputHandler(); var session = InputHandlerInternalAccess.textInput(input); session.claim(this);
        input.handleKeyEvent(GLFW_KEY_A, GLFW_PRESS); input.capturePhysicalInput(); input.update();
        input.handleKeyEvent(GLFW_KEY_A, GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS); input.handleKeyEvent(GLFW_KEY_B, GLFW_RELEASE);
        session.preedit(preedit("候補"));
        var events = input.capturePhysicalInput().events();
        assertEquals(1, events.size());
        assertEquals(GLFW_KEY_A, events.getFirst().code());
        assertEquals(0f, events.getFirst().value());
    }

    @Test void activeLegacyChromeKeepsItsNativeTextWhileHostLeasesAndDrainsHavePrecedence() {
        var chrome = org.mockito.Mockito.mock(com.openggf.editor.EditorWindowChrome.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        org.mockito.Mockito.when(chrome.isActive()).thenReturn(true);
        org.mockito.Mockito.when(chrome.character(org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        var input = new InputHandler(); var session = InputHandlerInternalAccess.textInput(input);
        int[] resets = {0};
        session.attach(new TextInputSession.NativeHost() {
            public void reset() { resets[0]++; }
            public void caretRectangle(int x, int y, int width, int height) { }
        });
        var adapter = new GlfwTextInputAdapter(input, new GlfwTextInputAdapter.Filters() {
            public boolean key(int key, int action, int mods) { return chrome.key(key, action, mods); }
            public boolean character(int cp) { return chrome.character(cp); }
            public boolean preedit(Composition composition) { return chrome.preedit(composition); }
        });
        adapter.preedit(preedit("chrome")); adapter.character('外'); adapter.key(GLFW_KEY_A, GLFW_PRESS, 0); adapter.endEvents();
        assertEquals(0, resets[0], "An old active chrome must not have its native composition reset");
        assertTrue(input.isPhysicalKeyDown(GLFW_KEY_A));
        org.mockito.Mockito.verify(chrome).character('外');
        assertEquals("", input.consumeTypedText());
        var host = session.claim(this);
        adapter.preedit(preedit("host"));
        assertEquals("host", host.composition().text(), "A host field wins while chrome is active");
        host.close(); assertEquals(1, resets[0]);
        assertTrue(session.ownsPreedit(), "Pending native reset still owns its acknowledgment");
        adapter.preedit(Composition.EMPTY); adapter.character('旧'); adapter.endEvents();
        assertFalse(session.ownsPreedit());
        org.mockito.Mockito.verify(chrome, org.mockito.Mockito.never()).character('旧');
        adapter.character('新'); adapter.endEvents();
        org.mockito.Mockito.verify(chrome).character('新');
    }

    @Test void emptyPreeditWithoutACompositionLeavesOrdinaryEditingKeysVisible() {
        // Wayland text-input reports an empty preedit alongside plain key events with no IME.
        var input = new InputHandler(); var session = InputHandlerInternalAccess.textInput(input);
        var adapter = adapter(input, new ArrayList<>());
        for (int key : new int[] {GLFW_KEY_BACKSPACE, GLFW_KEY_ENTER, GLFW_KEY_ESCAPE}) {
            session.claim(this);
            adapter.key(key, GLFW_PRESS, 0); adapter.preedit(Composition.EMPTY); adapter.endEvents();
            assertFalse(session.suppressCommands());
            assertTrue(MenuInput.textKeyPressed(input, key), "key " + key);
            input.update();
            adapter.key(key, GLFW_RELEASE, 0); adapter.preedit(Composition.EMPTY); adapter.endEvents(); input.update();
        }
        session.claim(this);
        adapter.preedit(preedit("日本")); adapter.endEvents(); input.update();
        session.claim(this);
        adapter.key(GLFW_KEY_ENTER, GLFW_PRESS, 0); adapter.preedit(Composition.EMPTY); adapter.endEvents();
        assertFalse(MenuInput.textKeyPressed(input, GLFW_KEY_ENTER), "The key that ends a real composition stays hidden");
    }

    private static GlfwTextInputAdapter adapter(InputHandler input, List<Integer> chrome) {
        return new GlfwTextInputAdapter(input, new GlfwTextInputAdapter.Filters() {
            public boolean key(int key, int action, int mods) { chrome.add(key); return true; }
            public boolean character(int cp) { return true; }
        });
    }
}
