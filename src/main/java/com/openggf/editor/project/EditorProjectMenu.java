package com.openggf.editor.project;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuDetailsScreen;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuStyle;
import com.openggf.game.MenuTextEditor;
import com.openggf.graphics.PixelFont;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Modal project commands and forms, sharing the host's keyboard/controller text editor. */
public final class EditorProjectMenu {
    private static final int VISIBLE_ROWS = 8;
    public record Action(String label, Runnable callback) {
        public Action { Objects.requireNonNull(label); Objects.requireNonNull(callback); }
    }
    public record Field(String key, String label, String value, MenuTextEditor.Mode mode, int maxLength, List<String> choices) {
        public Field(String key, String label, String value, MenuTextEditor.Mode mode, int maxLength) {
            this(key,label,value,mode,maxLength,List.of());
        }
        public Field(String key, String label, String value, List<String> choices) {
            this(key,label,value,MenuTextEditor.Mode.TEXT,256,choices);
        }
        public Field(String key, String label, String value, MenuTextEditor.Mode mode) {
            this(key, label, value, mode, mode == MenuTextEditor.Mode.PATH ? 4096 : 256);
        }
        public Field {
            choices = List.copyOf(choices);
            if (!choices.isEmpty() && !choices.contains(value)) throw new IllegalArgumentException("Choice default is unavailable");
            Objects.requireNonNull(key); Objects.requireNonNull(label);
            Objects.requireNonNull(value); Objects.requireNonNull(mode);
            if (maxLength < 1 || maxLength > 8192) throw new IllegalArgumentException("Field length must be 1..8192");
        }
    }

    private String title = "Editor projects";
    private String subtitle = "";
    private List<Action> actions = List.of();
    private List<Field> fields = List.of();
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();
    private Consumer<Map<String, String>> submit;
    private String submitLabel;
    private MenuTextEditor textEditor;
    private String editingKey;
    private MenuDetailsScreen details;
    private String error;
    private int selected;
    private int width = 320;
    private boolean open;
    private String directions = "Arrows", accept = "Enter", back = "Esc";

    public void showActions(String title, List<Action> actions) {
        reset(title);
        this.actions = List.copyOf(actions);
        if (actions.isEmpty()) throw new IllegalArgumentException("A project menu needs an action");
    }

    public void showForm(String title, List<Field> fields, String submitLabel,
                         Consumer<Map<String, String>> submit) {
        reset(title);
        this.fields = List.copyOf(fields);
        this.submitLabel = Objects.requireNonNull(submitLabel);
        this.submit = Objects.requireNonNull(submit);
        for (Field field : fields) {
            if (values.putIfAbsent(field.key(), field.value()) != null)
                throw new IllegalArgumentException("Duplicate project field: " + field.key());
        }
    }

    private void reset(String title) {
        this.title = Objects.requireNonNull(title);
        subtitle = ""; actions = List.of(); fields = List.of(); values.clear();
        submit = null; submitLabel = null; textEditor = null; editingKey = null;
        details = null; error = null; selected = 0; open = true;
    }

    public void setSubtitle(String subtitle) { this.subtitle = Objects.requireNonNull(subtitle); }
    public boolean isOpen() { return open; }
    public boolean capturesText() { return open && textEditor != null; }
    public String title() { return title; }
    public String error() { return error; }
    public Map<String, String> values() { return Map.copyOf(values); }
    public void setValue(String key, String value) {
        if (!values.containsKey(key)) throw new IllegalArgumentException("Unknown project field: " + key);
        values.put(key, Objects.requireNonNull(value));
    }
    public void close() { open = false; textEditor = null; details = null; }

    /** Returns true whenever this frame is owned by the modal, including its closing frame. */
    public boolean update(InputHandler input) {
        if (!open) return false;
        directions = MenuInput.directionLabel(input);
        accept = MenuInput.confirmLabel(input); back = MenuInput.backLabel(input);
        if (details != null) {
            if (details.update(input)) details = null;
            return true;
        }
        if (textEditor != null) {
            textEditor.update(input);
            switch (textEditor.consumeResult()) {
                case ACCEPTED -> { values.put(editingKey, textEditor.value()); textEditor = null; error = null; }
                case CANCELLED -> textEditor = null;
                case NONE -> { }
            }
            return true;
        }
        // Typing outside a field never becomes a later field's input.
        MenuInput.consumeText(input);
        if (MenuInput.back(input)) { close(); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return true; }
        if (MenuInput.details(input)) {
            details = new MenuDetailsScreen(title, error != null ? error : subtitle + "\n"
                    + "Choose a row to edit. Path fields can browse local files. "
                    + "Changes are applied only by the final action; Escape cancels this form.");
            return true;
        }
        int previous = selected, count = rowCount();
        if (MenuInput.up(input)) selected = Math.floorMod(selected - 1, count);
        if (MenuInput.down(input)) selected = (selected + 1) % count;
        if (MenuInput.left(input)) selected = Math.max(0, selected - VISIBLE_ROWS);
        if (MenuInput.right(input)) selected = Math.min(count - 1, selected + VISIBLE_ROWS);
        var pointer = MenuInput.pointer(input, width, 224);
        boolean clicked = false;
        int first = firstVisible();
        for (int i = first; i < Math.min(count, first + VISIBLE_ROWS); i++) {
            if (pointer.over(9, 48 + (i - first) * 18, width - 18, 17) && pointer.leftPressed()) {
                selected = i; clicked = true;
            }
        }
        if (previous != selected) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        if (MenuInput.accept(input) || clicked) activate();
        return true;
    }

    private void activate() {
        if (submit != null && selected < fields.size()) {
            Field field = fields.get(selected);
            if (!field.choices().isEmpty()) {
                int next = (field.choices().indexOf(values.get(field.key()))+1)%field.choices().size();
                values.put(field.key(),field.choices().get(next));
                MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
                return;
            }
            editingKey = field.key();
            textEditor = new MenuTextEditor(field.label(), values.get(field.key()),
                    field.maxLength(), null, field.mode());
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        } else if (submit != null) submit();
        else {
            Action action = actions.get(selected);
            close();
            try { action.callback().run(); MenuFeedback.emit(MenuFeedback.Cue.CONFIRM); }
            catch (RuntimeException failure) { open = true; report(failure); }
        }
    }

    public void submit() {
        if (submit == null) throw new IllegalStateException("No project form is active");
        var callback = submit;
        var submitted = values();
        close();
        try { callback.accept(submitted); MenuFeedback.emit(MenuFeedback.Cue.CONFIRM); }
        catch (RuntimeException failure) { open = true; report(failure); }
    }

    public void report(Throwable failure) {
        error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        MenuFeedback.emit(MenuFeedback.Cue.ERROR);
    }

    private int rowCount() { return submit == null ? actions.size() : fields.size() + 1; }
    private int firstVisible() { return Math.max(0, Math.min(selected - VISIBLE_ROWS / 2, rowCount() - VISIBLE_ROWS)); }

    public void render(PixelFont font, int width) {
        if (!open) return;
        this.width = Math.max(320, width);
        if (details != null) { details.render(font, this.width); return; }
        if (textEditor != null) { textEditor.render(font, this.width); return; }
        MenuStyle.page(font, this.width, title, subtitle);
        int first = firstVisible();
        for (int i = first; i < Math.min(rowCount(), first + VISIBLE_ROWS); i++) {
            int y = 48 + (i - first) * 18;
            if (i == selected) MenuStyle.focus(font, 9, y, this.width - 18, 17);
            String label = submit == null ? actions.get(i).label()
                    : i == fields.size() ? submitLabel
                    : fields.get(i).label() + ": " + values.get(fields.get(i).key());
            MenuStyle.text(font, label, 15, y + 4, this.width - 30, 1, 1, 1);
        }
        MenuStyle.footer(font, this.width,
                error == null ? directions + " select  " + (selected + 1) + "/" + rowCount() : "F1 Full error: " + error,
                accept + " Open / apply  " + back + " Cancel  F1 Details");
    }
}
