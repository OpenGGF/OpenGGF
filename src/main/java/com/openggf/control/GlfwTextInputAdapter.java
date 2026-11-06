package com.openggf.control;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWPreeditCallback;
import org.lwjgl.system.MemoryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import static org.lwjgl.glfw.GLFW.*;

/** Window-thread adapter for optional LWJGL experimental preedit support. */
public final class GlfwTextInputAdapter implements AutoCloseable {
    public interface Filters {
        boolean key(int key, int action, int mods);
        boolean character(int codepoint);
        default boolean preedit(Composition composition) { return true; }
    }
    public record Viewport(int x, int y, int width, int height, int logicalWidth, int logicalHeight) { }
    public record Rectangle(int x, int y, int width, int height) { }
    private sealed interface Event permits Key, CharacterEvent { }
    private record Key(int code, int action, int mods) implements Event { }
    private record CharacterEvent(int codepoint) implements Event { }
    private final InputHandler input;
    private final TextInputSession session;
    private final Filters filters;
    private final List<Event> events = new ArrayList<>();
    private long window;
    private GLFWPreeditCallback preeditCallback;

    public GlfwTextInputAdapter(InputHandler input, Filters filters) {
        this.input = Objects.requireNonNull(input);
        this.session = InputHandlerInternalAccess.textInput(input);
        this.filters = Objects.requireNonNull(filters);
    }

    /** X11's default over-the-spot mode does not deliver application preedit callbacks. */
    public static void configureBeforeInit() {
        if (GLFW.Functions.SetPreeditCallback != 0) {
            glfwInitHint(GLFW_X11_ONTHESPOT, GLFW_TRUE);
            glfwInitHint(GLFW_MANAGE_PREEDIT_CANDIDATE, GLFW_FALSE);
        }
    }

    public void install(long window, Supplier<Viewport> viewport) {
        if (this.window != 0) throw new IllegalStateException("Text input already attached");
        this.window = window;
        glfwSetKeyCallback(window, (handle, key, scan, action, mods) -> key(key, action, mods));
        glfwSetCharCallback(window, (handle, cp) -> character(cp));
        if (GLFW.Functions.SetPreeditCallback != 0) {
            preeditCallback = GLFWPreeditCallback.create((handle, count, text, blocks, sizes, focused, caret) -> {
                try { preedit(copyPreedit(count, text, blocks, sizes, focused, caret)); }
                catch (IllegalArgumentException malformed) {
                    // Malformed nonempty native data cannot acknowledge a cancelled composition.
                    session.malformedPreedit();
                }
            });
            glfwSetPreeditCallback(window, preeditCallback);
        }
        session.attach(new TextInputSession.NativeHost() {
            public void reset() { if (GLFW.Functions.ResetPreeditText != 0) glfwResetPreeditText(window); }
            public void caretRectangle(int x, int y, int width, int height) {
                if (GLFW.Functions.SetPreeditCursorRectangle == 0) return;
                int[] ww = new int[1], wh = new int[1], fw = new int[1], fh = new int[1];
                glfwGetWindowSize(window, ww, wh);
                glfwGetFramebufferSize(window, fw, fh);
                Rectangle mapped = toWindowRectangle(viewport.get(), ww[0], wh[0], fw[0], fh[0], x, y, width, height);
                if (mapped != null) glfwSetPreeditCursorRectangle(window, mapped.x(), mapped.y(), mapped.width(), mapped.height());
            }
        });
    }

    public void key(int code, int action, int mods) {
        if (action == GLFW_RELEASE || session.windowFocused()) events.add(new Key(code, action, mods));
    }
    public void character(int cp) {
        if (session.acceptsCommit()) events.add(new CharacterEvent(cp));
    }
    public void preedit(Composition composition) {
        if (session.ownsPreedit() || filters.preedit(composition)) session.preedit(composition);
    }
    public void focus(boolean focused) {
        // GLFW does not synthesize a second release if the first arrived before focus loss.
        if (!focused) events.removeIf(event -> !(event instanceof Key key && key.action() == GLFW_RELEASE));
        session.focus(focused);
    }
    public void pollEvents() {
        try { glfwPollEvents(); } finally { endEvents(); }
    }
    public void endEvents() {
        List<Event> batch = List.copyOf(events);
        events.clear();
        try {
            for (Event event : batch) {
                switch (event) {
                    case Key key -> {
                        if (session.key(key.code(), key.action())) {
                            boolean allowed = filters.key(key.code(), key.action(), key.mods());
                            if (allowed || key.action() == GLFW_RELEASE) input.handleKeyEvent(key.code(), key.action());
                        }
                    }
                    case CharacterEvent character -> {
                        if (session.acceptsCommit() && filters.character(character.codepoint()))
                            MenuInput.handleCharEvent(input, character.codepoint());
                    }
                }
            }
        } finally { events.clear(); session.endEvents(); }
    }

    /** Copies GLFW-owned UTF-32 arrays before the callback returns. Invalid pointers are never dereferenced for empty data. */
    public static Composition copyPreedit(int count, long text, int blocks, long sizes, int focused, int caret) {
        if (count < 0 || count > Composition.MAX_CODEPOINTS || blocks < 0 || blocks > Composition.MAX_CODEPOINTS)
            throw new IllegalArgumentException("Invalid native preedit counts");
        if (count == 0) return Composition.EMPTY;
        if (text == 0 || (text & 3) != 0 || blocks > 0 && (sizes == 0 || (sizes & 3) != 0))
            throw new IllegalArgumentException("Missing native preedit arrays");
        int[] codepoints = new int[count], blockSizes = new int[blocks];
        MemoryUtil.memIntBuffer(text, count).get(codepoints);
        if (blocks > 0) MemoryUtil.memIntBuffer(sizes, blocks).get(blockSizes);
        return Composition.fromCodepoints(codepoints, blockSizes, focused, caret);
    }

    /** Inverse of the live framebuffer viewport, with top-left window coordinates and outward-rounded edges. */
    public static Rectangle toWindowRectangle(Viewport viewport, int windowWidth, int windowHeight,
            int framebufferWidth, int framebufferHeight, int x, int y, int width, int height) {
        if (viewport == null || windowWidth <= 0 || windowHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0
                || viewport.width() <= 0 || viewport.height() <= 0 || viewport.logicalWidth() <= 0 || viewport.logicalHeight() <= 0)
            return null;
        double sx = windowWidth / (double) framebufferWidth, sy = windowHeight / (double) framebufferHeight;
        double left = viewport.x() + x * (double) viewport.width() / viewport.logicalWidth();
        double top = framebufferHeight - viewport.y() - viewport.height() + y * (double) viewport.height() / viewport.logicalHeight();
        double right = left + Math.max(1, width) * (double) viewport.width() / viewport.logicalWidth();
        double bottom = top + Math.max(1, height) * (double) viewport.height() / viewport.logicalHeight();
        int wx = (int) Math.floor(left * sx), wy = (int) Math.floor(top * sy);
        return new Rectangle(wx, wy, Math.max(1, (int) Math.ceil(right * sx) - wx), Math.max(1, (int) Math.ceil(bottom * sy) - wy));
    }

    @Override public void close() {
        session.detach();
        events.clear();
        if (window != 0 && preeditCallback != null) {
            glfwSetPreeditCallback(window, null);
            preeditCallback.free();
            preeditCallback = null;
        }
        window = 0;
    }
}
