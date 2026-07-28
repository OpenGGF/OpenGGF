package com.openggf.graphics;

import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;


import static org.lwjgl.glfw.GLFW.GLFW_WAYLAND_APP_ID;
import static org.lwjgl.glfw.GLFW.GLFW_X11_CLASS_NAME;
import static org.lwjgl.glfw.GLFW.GLFW_X11_INSTANCE_NAME;
import static org.lwjgl.glfw.GLFW.glfwSetWindowIcon;
import static org.lwjgl.glfw.GLFW.glfwWindowHintString;

/**
 * Applies the application icon to a GLFW window.
 * Decodes the packaged PNG icon set with STBImage (no AWT dependency) and hands the
 * RGBA buffers to GLFW, which picks whichever size the window manager asks for.
 */
public final class WindowIconLoader {

    private static final String[] ICON_RESOURCES = {
            "icon/openggf-16.png",
            "icon/openggf-24.png",
            "icon/openggf-32.png",
            "icon/openggf-48.png",
            "icon/openggf-64.png",
            "icon/openggf-128.png",
            "icon/openggf-256.png"
    };

    /** Matches the {@code StartupWMClass} in {@code packaging/linux/openggf.desktop}. */
    private static final String APP_ID = "openggf";

    private WindowIconLoader() {
    }

    /**
     * Declares the window class GLFW reports to the desktop shell. Wayland has no
     * client-side window icon protocol, so the compositor pairs the window with an
     * installed {@code .desktop} entry by app id instead; X11 uses the class hint the
     * same way. Must run before the window is created.
     */
    public static void applyWindowClassHints() {
        glfwWindowHintString(GLFW_WAYLAND_APP_ID, APP_ID);
        glfwWindowHintString(GLFW_X11_CLASS_NAME, APP_ID);
        glfwWindowHintString(GLFW_X11_INSTANCE_NAME, APP_ID);
    }

    /**
     * Sets the window icon, silently leaving the platform default in place if the icon
     * resources cannot be decoded.
     *
     * @param window GLFW window handle
     */
    public static void apply(long window) {
        List<ByteBuffer> pixelBuffers = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer icons = GLFWImage.malloc(ICON_RESOURCES.length, stack);
            int count = 0;

            for (String resource : ICON_RESOURCES) {
                byte[] bytes = readResource(resource);
                if (bytes == null) {
                    continue;
                }

                ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
                encoded.put(bytes).flip();

                IntBuffer pWidth = stack.mallocInt(1);
                IntBuffer pHeight = stack.mallocInt(1);
                IntBuffer pChannels = stack.mallocInt(1);

                // GLFW expects top-left origin RGBA, unlike the GL texture path
                STBImage.stbi_set_flip_vertically_on_load(false);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, pWidth, pHeight, pChannels, 4);
                MemoryUtil.memFree(encoded);
                if (pixels == null) {
                    continue;
                }

                pixelBuffers.add(pixels);
                icons.get(count++)
                        .width(pWidth.get(0))
                        .height(pHeight.get(0))
                        .pixels(pixels);
            }

            if (count == 0) {
                return;
            }
            icons.limit(count);
            glfwSetWindowIcon(window, icons);
        } finally {
            // GLFW copies the pixel data, so the decoded buffers can go straight back
            for (ByteBuffer pixels : pixelBuffers) {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    private static byte[] readResource(String resourcePath) {
        try (InputStream is = WindowIconLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            return is == null ? null : is.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }
}
