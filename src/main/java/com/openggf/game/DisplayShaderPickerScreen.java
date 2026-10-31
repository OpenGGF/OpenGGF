package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.shaderlib.DisplayShaderPickerController;
import com.openggf.graphics.shaderlib.DisplayShaderPresetRef;
import com.openggf.graphics.shaderlib.DisplayShaderSelectionModel;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.TextFace;
import com.openggf.i18n.UiMessages;

/** Host presentation and read-only full-text access for the shader preview picker. */
public final class DisplayShaderPickerScreen {
    private MenuDetailsScreen details;
    private int ticks;
    private String detailsKey = "F1";

    public DisplayShaderPickerController.Action update(InputHandler input, DisplayShaderPickerController picker,
            DisplayShaderPresetRef current, String root, String status, int viewportWidth) {
        ticks++;
        if (!picker.isOpen()) details = null;
        if (input != null) detailsKey = MenuInput.detailsLabel(input);
        if (details != null) {
            var frame = MenuContentFrame.fit(viewportWidth);
            if (details.update(input, frame.local(MenuInput.pointer(input, frame.surfaceWidth(), 224)))) details = null;
            // Closing, scrolling and confirming the reader never reach the picker in this frame.
            return none();
        }
        if (picker.isOpen() && input != null && MenuInput.textDetails(input)) {
            details = new MenuDetailsScreen(title(), fullText(picker, root, status));
            return none();
        }
        if (picker.isOpen() && input != null) {
            var frame = MenuContentFrame.fit(viewportWidth);
            MenuInput.Pointer pointer = MenuInput.pointer(input, frame.surfaceWidth(), 224);
            if (pointer.inside()) picker.scroll(pointer.wheel());
        }
        return picker.update(input, current);
    }

    public boolean reading() { return details != null; }

    public void render(PixelFont font, int viewportWidth, DisplayShaderPickerController picker,
            String root, String status, boolean downloading) {
        var frame = MenuContentFrame.fit(viewportWidth);
        Runnable draw = () -> renderContent(font, frame.width(), picker, root, status, downloading);
        if (font instanceof MenuPixelFont menu) menu.withHorizontalOffset(frame.left(), draw);
        else draw.run();
    }

    void renderContent(PixelFont font, int width, DisplayShaderPickerController picker,
            String root, String status, boolean downloading) {
        if (details != null) { details.render(font, width); return; }
        var language = MenuStyle.language(font);
        var metrics = font instanceof MenuPixelFont menu ? menu.lineMetrics(MenuStyle.COMPACT)
                : HostText.shared().lineMetrics(TextFace.COMPACT, language);
        int pitch = metrics.lineHeight() + 3;
        int y = 5 + metrics.above();
        int footerTop = 224 - pitch - 3;
        line(font, title(), 6, y, width - 12, .5f, .9f, 1); y += pitch;
        line(font, folderText(picker, root), 6, y, width - 12, .5f, .9f, 1); y += pitch;
        line(font, picker.query(), 6, y, width - 12, 1, 1, 1); y += pitch;
        line(font, DisplayShaderPickerController.downloadHintText(), 6, y, width - 12, .78f, .83f, .9f); y += pitch;
        if (status != null && !status.isBlank()) {
            line(font, status, 6, y, width - 12, downloading ? .5f : .78f, .9f, 1); y += pitch;
        }
        var items = picker.visibleItems();
        int selected = items.indexOf(picker.selectedItem());
        int count = Math.max(1, (footerTop - y) / pitch);
        int start = Math.max(0, Math.min(selected - count / 2, items.size() - count));
        int end = Math.min(items.size(), start + count);
        for (int i = start; i < end; i++) {
            boolean focus = i == selected;
            font.drawText(focus ? ">" : "", 6, y, MenuStyle.COMPACT, 1, .8f, .3f, 1);
            line(font, items.get(i).displayPath(), 18, y, width - 24, focus ? 1 : .78f, focus ? .8f : .83f, focus ? .3f : .9f);
            y += pitch;
        }
        // A dedicated row keeps the complete-reader affordance visible beside every preview.
        line(font, detailsKey + " " + UiMessages.text("menu.path.details_caps"), 6,
                footerTop + metrics.above(), width - 12, .5f, .9f, 1);
    }

    private void line(PixelFont font, String value, int x, int y, int width, float r, float g, float b) {
        String readable = MenuDetailsScreen.readable(value);
        String fragment = MenuStyle.pageText(MenuStyle.language(font), readable, width, MenuStyle.COMPACT, ticks);
        font.drawText(fragment, x, y, MenuStyle.COMPACT, r, g, b, 1);
    }

    static String fullText(DisplayShaderPickerController picker, String root, String status) {
        return String.join("\n\n", folderText(picker, root), picker.query(),
                selectedPath(picker.selectedItem(), root), DisplayShaderPickerController.downloadHintText(),
                status == null ? "" : status);
    }

    static String folderText(DisplayShaderPickerController picker, String root) {
        return join(normalizedRoot(root), picker.currentFolder());
    }

    private static String selectedPath(DisplayShaderSelectionModel.SelectionItem item, String root) {
        if (item == null) return "";
        if (item.directory()) return join(normalizedRoot(root), item.folderPath());
        if (item.ref().kind() == DisplayShaderPresetRef.Kind.OFF) return item.displayPath();
        return join(normalizedRoot(root), item.ref().relativePath());
    }

    private static String normalizedRoot(String root) {
        String result = root == null || root.isBlank() ? "shaders" : root.trim().replace('\\', '/');
        while (result.length() > 1 && result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String join(String root, String relative) {
        if (relative == null || relative.isBlank()) return root;
        return root + (root.endsWith("/") ? "" : "/") + relative.replace('\\', '/');
    }

    private static String title() { return UiMessages.text("settings.key.display_shader_selection.label"); }
    private static DisplayShaderPickerController.Action none() {
        return new DisplayShaderPickerController.Action(DisplayShaderPickerController.ActionType.NONE, null);
    }
}
