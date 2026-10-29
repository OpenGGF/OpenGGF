package com.openggf.editor.project;

import com.openggf.i18n.UiMessages;
import com.openggf.editor.EditorLabels;
import java.util.Map;


import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuDetailsScreen;
import com.openggf.game.MenuChrome;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextFace;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuStyle;
import com.openggf.game.MenuTextEditor;
import com.openggf.graphics.PixelFont;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Modal project commands and forms, sharing the host's keyboard/controller text editor. */
public final class EditorProjectMenu {
    /** Engine-owned presentation intent; display wording is never used to identify a menu. */
    public enum PresentationKind {
        DEFAULT, ADD_ACTOR, ACTOR_PROPERTIES, ADD_OBJECT, OBJECT_PROPERTIES,
        ACTOR_POSITION, ACTOR_VISIBILITY, REVEAL_TIMING, OPEN_SCENE, SAVE_SCENE,
        IMPORT_SCREENPLAY, EXPORT_SCREENPLAY, NATIVE_HOVER, NATIVE_FLY_TO, NATIVE_GLIDE_TO,
        NATIVE_WAIT, NATIVE_RELEASE, NATIVE_CROUCH, NATIVE_ROLL, NATIVE_LOOK_UP, NATIVE_SPINDASH, NATIVE_FACE_LEFT, NATIVE_FACE_RIGHT,
        FLOOR_PLACEMENTS, ROUTE_RESULT, DIRECT_ACTOR, CAMERA_KEYS, OBJECT_LIBRARY,
        LOADED_OBJECTS, OBJECT_PARTS, DIALOGUE_STYLES, DIALOGUE_TYPING, APPEARANCE_LIMITS,
        FLOOR_REFUSAL, PART_KEYS, OBJECT_MOVES, OBJECT_CAST, SCENE_CHANGES,
        CAMERA_SHAKE, CAPTION_LINES, OBJECT_KEYS, PART_OVERRIDES, CHOOSE_TYPER,
        CHOOSE_STYLE, OBJECT_PART
    }
    private PresentationKind presentationKind = PresentationKind.DEFAULT;
    public PresentationKind presentationKind() { return presentationKind; }

    /**
     * One command on an action page. {@code note} is its consequence in a few words, written by the
     * caller ("Starts a new level from a game's layout"); it draws as a quieter second line, so an
     * empty note keeps a one-line row (choice lists such as game names need none).
     */
    public record Action(String label, String note, Runnable callback) {
        public Action { Objects.requireNonNull(label); Objects.requireNonNull(note); Objects.requireNonNull(callback); }
        public Action(String label, Runnable callback) { this(label, "", callback); }
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
            if (!choices.isEmpty() && !choices.contains(value)) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.choice_default_is_unavailable_1emjbko"));
            Objects.requireNonNull(key); Objects.requireNonNull(label);
            Objects.requireNonNull(value); Objects.requireNonNull(mode);
            if (maxLength < 1 || maxLength > 8192) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.field_length_must_be_1_8192_1iuz815"));
        }
    }

    private String title = UiMessages.text("editor.editor_project_menu.editor_projects_stvulp");
    private String subtitle = "";
    private String help = "";
    private final LinkedHashMap<String, List<String>> sections = new LinkedHashMap<>();
    private final java.util.Set<String> expanded = new java.util.HashSet<>();
    private final Map<String, Map<String, String>> valueLabels = new java.util.HashMap<>();
    private record Row(Field field, String section, String heading, String group) { }
    private final Map<String, String> pairs = new java.util.HashMap<>();
    private final java.util.Set<String> multiline = new java.util.HashSet<>();
    private final LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();
    private final Map<String, String> sectionSummaries = new java.util.HashMap<>();
    private final Map<String, String> units = new java.util.HashMap<>();
    private long generation;
    private Consumer<EditorProjectMenu> presenter;
    private boolean helpConfigured, suspended;
    private final Map<Integer,String> actionLabels = new java.util.HashMap<>();
    private int primaryAction = -1;
    public enum Tone { INFO, SUCCESS, WARNING, ERROR }
    private Tone tone = Tone.INFO;
    private String statusLine = "";
    private final Map<String,String> originalLabels = new java.util.HashMap<>();
    public record PositionField(String x, String y, boolean floor, boolean snap) { }
    private final Map<String,PositionField> positions = new java.util.HashMap<>();
    private Consumer<PositionField> positionPick;
    public void onShow(Consumer<EditorProjectMenu> listener) { presenter = listener; }
    public void onPositionPick(Consumer<PositionField> listener) { positionPick = listener; }
    public void setPositionPicker(String x, String y, boolean floor, boolean snap) {
        requireField(x); if (y != null) requireField(y);
        positions.put(x, new PositionField(x, y, floor, snap));
    }
    public boolean canPick(String key) { return positions.containsKey(key) && positionPick != null; }
    public void pick(String key) { if (!canPick(key)) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.this_field_has_no_level_picker_1kvv7rv")); try {positionPick.accept(positions.get(key));}catch(RuntimeException failure){report(failure);} }
    public void suspend() { suspended = true; }
    public void resume() { suspended = false; }
    public void setPrimaryAction(int index) { if (index < 0 || index >= actions.size()) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.unknown_action_ur384s")); primaryAction = index; }
    public void setActionLabel(int index, String label) { actionLabels.put(index, label); }
    public List<Action> actionEntries() { return actions; }
    public List<Field> formFields() { return fields; }
    public void setStatusLine(String text, Tone tone) { statusLine = Objects.requireNonNull(text); this.tone = Objects.requireNonNull(tone); }
    public record ActionRow(String label, String note, int index, boolean primary) {
        public ActionRow(String label, int index, boolean primary) { this(label, "", index, primary); }
    }
    public record ActionView(String title, String hint, String help, List<ActionRow> rows, String status, Tone tone, String error) { }
    public ActionView actionView() {
        if (!isOpen() || submit != null || presenter == null) return null;
        var rows = new java.util.ArrayList<ActionRow>();
        for (int i=0;i<actions.size();i++) rows.add(new ActionRow(actionLabels.getOrDefault(i,actions.get(i).label()),actions.get(i).note(),i,i==primaryAction));
        return new ActionView(title,subtitle,help,List.copyOf(rows),statusLine,tone,error);
    }
    public void runAction(int index) { selected=index; activate(); }
    public String detailsText() {
        String text = error != null ? error : subtitle + "\n\n" + help;
        if (submit != null) for (Field field:fields) text += "\n" + originalLabels.getOrDefault(field.key(),field.label()) + ": "
                + (multiline.contains(field.key()) ? decodePreviewText(values.get(field.key())) : values.get(field.key()));
        else for (Action action:actions) text += "\n" + action.label() + (action.note().isEmpty() ? "" : ": " + action.note());
        return text;
    }
    public void setDetailsContent(MenuDetailsScreen.Content content) { detailsContent=Objects.requireNonNull(content); }
    public void openDetails() { details = new MenuDetailsScreen(detailsContent==null||error!=null?defaultDetails():detailsContent); }
    private MenuDetailsScreen.Content defaultDetails() {
        var sections=new java.util.ArrayList<MenuDetailsScreen.Section>();
        var form=formView();
        if(form!=null) for(var group:form.groups()) sections.add(new MenuDetailsScreen.Section(
                (group.title().isBlank() ? UiMessages.text("editor.editor_project_menu.values_1696zb6") : group.title()),group.fields().stream().map(f->new MenuDetailsScreen.Row(
                        f.field().label(),f.display())).toList(),false));
        else if(submit!=null) sections.add(new MenuDetailsScreen.Section(UiMessages.text("editor.editor_project_menu.values_1696zb6"),fields.stream().map(f->
                new MenuDetailsScreen.Row(f.label(),displayValue(f,values.get(f.key())))).toList(),false));
        var buttons=new java.util.ArrayList<MenuDetailsScreen.Action>();
        if(submit==null && primaryAction>=0) {
            int index=primaryAction;
            buttons.add(new MenuDetailsScreen.Action(actionLabels.getOrDefault(index,actions.get(index).label()),
                    ()->runAction(index),true));
        }
        return new MenuDetailsScreen.Content(title,statusLine,error==null?subtitle:error,
                MenuDetailsScreen.Tone.valueOf(tone.name()),sections,detailsText(),buttons,"");
    }
    public boolean showingDetails() { return details != null; }
    public String helpText() { return help; }

    // The standard HUD is 3x: 36px rows, 18px section gaps and 21px before buttons.
    private static final int ROW_TOP = 44, ROW_HEIGHT = 12, HEADING_GAP = 6, HEADING_HEIGHT = 16,
            BUTTON_GAP = 7, ROW_BOTTOM = 205;
    /** An action with a note: label and quieter consequence line, as the hub's Advanced page reads. */
    private static final int NOTE_ROW_HEIGHT = 22;

    /** Immutable presentation shared with desktop chrome; canonical values stay separate from labels. */
    public record FormField(Field field, String value, String display, List<String> optionLabels, String unit,
                            Field partner, String partnerValue, boolean multiline, boolean pickable) { }
    public record FormGroup(String title, List<FormField> fields, boolean advanced, String summary) { }
    public record FormView(String title, String hint, String help, String apply, List<FormGroup> groups, String error) { }
    public long generation() { return generation; }
    /** One presentation row, with both original coordinate values retained by the form. */
    public void setPair(String first, String second, String label) {
        requireField(first); requireField(second);
        if (first.equals(second) || pairs.containsKey(first) || pairs.containsValue(first)
                || pairs.containsKey(second) || pairs.containsValue(second)) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.repeated_coordinate_pair_19bngu4"));
        pairs.put(first, second); configureField(first, label);
    }
    public void setMultilinePreview(String key) { requireField(key); multiline.add(key); }
    /** Context stays in the shared form so both frontends use the same Who row. */
    public void addContextField(String key, String label, String value, String display) {
        if (values.containsKey(key)) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.repeated_context_field_12a3kn7"));
        var copy = new java.util.ArrayList<>(fields);
        copy.addFirst(new Field(key, label, value, List.of(value)));
        fields = List.copyOf(copy); values.put(key, value); setValueLabels(key, Map.of(value, display));
    }
    public static String coordinate(String value) {
        try { return new java.math.BigDecimal(value).stripTrailingZeros().toPlainString(); }
        catch (NumberFormatException failure) { return value; }
    }
    public static String encodePreviewText(String value) { return value.replace("\\", "\\\\").replace("\n", "\\n"); }
    private static String decodePreviewText(String value) {
        var text = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (next == 'n') { text.append('\n'); i++; continue; }
                if (next == '\\') { text.append('\\'); i++; continue; }
            }
            text.append(c);
        }
        return text.toString();
    }
    public void setTitle(String value) { title = Objects.requireNonNull(value); }
    public void setSubmitLabel(String value) { submitLabel = Objects.requireNonNull(value); }
    public void setUnit(String key, String unit) { requireField(key); units.put(key, Objects.requireNonNull(unit)); }
    public void setSectionSummary(String section, String summary) {
        if (!sections.containsKey(section)) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.unknown_section_aoxtrj"));
        sectionSummaries.put(section, Objects.requireNonNull(summary));
    }
    public void configureField(String key, String label) {
        var field = requireField(key); configureField(key, label, field.choices(), valueLabels.getOrDefault(key, Map.of()));
    }
    public void configureField(String key, String label, List<String> choices, Map<String,String> labels) {
        var old = requireField(key);
        var copy = new java.util.ArrayList<>(fields);
        copy.set(copy.indexOf(old), new Field(key, label, old.value(), old.mode(), old.maxLength(), choices));
        fields = List.copyOf(copy); setValueLabels(key, labels);
    }
    private Field requireField(String key) {
        return fields.stream().filter(f -> f.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.unknown_project_field_u6ow92", Map.of("value0", String.valueOf(key)))));
    }
    public void setGroup(String title, String... keys) {
        if (submit == null || keys.length == 0 || groups.containsKey(title)) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.invalid_form_group_7hej9o"));
        var owned = new java.util.HashSet<String>(); groups.values().forEach(owned::addAll); sections.values().forEach(owned::addAll);
        for (String key : keys) if (!owned.add(key)) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.repeated_group_field_ty1uvn")); else requireField(key);
        groups.put(Objects.requireNonNull(title), List.of(keys));
    }
    public FormView formView() {
        if (!isOpen() || submit == null || presenter == null && groups.isEmpty() && sections.isEmpty()) return null;
        var result = new java.util.ArrayList<FormGroup>();
        var owned = new java.util.HashSet<String>(); groups.values().forEach(owned::addAll); sections.values().forEach(owned::addAll);
        var common = fields.stream().filter(f -> !owned.contains(f.key()) && !pairs.containsValue(f.key())).map(this::fieldView).toList();
        if (!common.isEmpty()) result.add(new FormGroup("", common, false, ""));
        groups.forEach((name, keys) -> result.add(new FormGroup(name, keys.stream().filter(key -> !pairs.containsValue(key)).map(this::requireField).map(this::fieldView).toList(), false, "")));
        sections.forEach((name, keys) -> result.add(new FormGroup(name, keys.stream().filter(key -> !pairs.containsValue(key)).map(this::requireField).map(this::fieldView).toList(), true, sectionSummary(name))));
        return new FormView(title, subtitle, help, submitLabel, List.copyOf(result), error);
    }
    private FormField fieldView(Field field) {
        String second = pairs.get(field.key());
        String display = second == null ? displayValue(field, values.get(field.key()))
                : coordinate(values.get(field.key())) + ", " + coordinate(values.get(second));
        if (multiline.contains(field.key())) display = decodePreviewText(values.get(field.key()));
        return new FormField(field, values.get(field.key()), display,
                field.choices().stream().map(value -> displayValue(field, value)).toList(), units.getOrDefault(field.key(), ""),
                second == null ? null : requireField(second), second == null ? "" : values.get(second), multiline.contains(field.key()), canPick(field.key()));
    }
    private String sectionSummary(String name) {
        boolean edited = sections.get(name).stream().anyMatch(key -> !values.get(key).equals(requireField(key).value()));
        return (edited ? UiMessages.text("editor.editor_project_menu.custom_settings_1lw21g2") : sectionSummaries.getOrDefault(name, UiMessages.text("editor.project.more_settings")));
    }
    /** Native chrome can keep the existing path browser as a reachable field editor. */
    public void editField(String key) {
        var field = requireField(key); editingKey = key;
        String value = pairs.containsKey(key) ? fieldView(field).display() : values.get(key);
        textEditor = new MenuTextEditor(field.label(), value, field.maxLength(), null, field.mode());
    }
    private List<Action> actions = List.of();
    private List<Field> fields = List.of();
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();
    private Consumer<Map<String, String>> submit;
    private java.util.function.BiConsumer<String, String> valueChanged;
    private String submitLabel;
    private MenuTextEditor textEditor;
    private String editingKey;
    private MenuDetailsScreen details;
    private MenuDetailsScreen.Content detailsContent;
    private String error;
    private int selected;
    private int width = 320;
    private boolean open;
    private String accept = "Enter", back = "Esc", detailsLabel = "F1";
    private InputHandler lastInput;
    private int chromeFrame;

    public void showActions(String title, List<Action> actions) {
        showActions(title, actions, 0);
    }

    /** Keeps display order while initially focusing the most useful action. */
    public void showActions(String title, List<Action> actions, int selected) {
        showActions(PresentationKind.DEFAULT, title, actions, selected);
    }

    public void showActions(PresentationKind kind, String title, List<Action> actions) {
        showActions(kind, title, actions, 0);
    }

    public void showActions(PresentationKind kind, String title, List<Action> actions, int selected) {
        reset(title);
        presentationKind = Objects.requireNonNull(kind, "kind");
        this.actions = List.copyOf(actions);
        if (actions.isEmpty()) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.a_project_menu_needs_an_action_2k8gff"));
        if (selected < 0 || selected >= actions.size()) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.invalid_selected_action_roxzpu"));
        this.selected = selected;
        if (presenter != null) presenter.accept(this);
    }

    public void showForm(String title, List<Field> fields, String submitLabel,
                         Consumer<Map<String, String>> submit) {
        showForm(PresentationKind.DEFAULT, title, fields, submitLabel, submit);
    }

    public void showForm(PresentationKind kind, String title, List<Field> fields, String submitLabel,
                         Consumer<Map<String, String>> submit) {
        reset(title);
        presentationKind = Objects.requireNonNull(kind, "kind");
        this.fields = List.copyOf(fields);
        this.submitLabel = Objects.requireNonNull(submitLabel);
        this.submit = Objects.requireNonNull(submit);
        for (Field field : fields) {
            originalLabels.put(field.key(),field.label());
            if (values.putIfAbsent(field.key(), field.value()) != null)
                throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.duplicate_project_field_1maai9z", Map.of("value0", String.valueOf(field.key()))));
        }
        if (presenter != null) presenter.accept(this);
    }

    private void reset(String title) {
        presentationKind = PresentationKind.DEFAULT;
        this.title = Objects.requireNonNull(title);
        subtitle = ""; help = ""; sections.clear(); expanded.clear(); valueLabels.clear();
        helpConfigured=false; suspended=false; originalLabels.clear(); actionLabels.clear(); primaryAction=-1; statusLine=""; tone=Tone.INFO; positions.clear();
        groups.clear(); sectionSummaries.clear(); units.clear(); pairs.clear(); multiline.clear(); generation++;
        actions = List.of(); fields = List.of(); values.clear();
        submit = null; valueChanged = null; submitLabel = null; textEditor = null; editingKey = null;
        details = null; detailsContent=null; error = null; selected = 0; open = true;
        chromeFrame = 0; lastInput = null;
    }

    public void setSubtitle(String subtitle) {
        Objects.requireNonNull(subtitle);
        if (presenter != null && helpConfigured) help += "\n" + subtitle;
        else { this.subtitle=subtitle; help=subtitle; }
    }

    /** A short hint on the form; the full explanation remains scrollable under Details. */
    public void setHelp(String hint, String detail) {
        subtitle = Objects.requireNonNull(hint); help = Objects.requireNonNull(detail); helpConfigured=true;
    }

    /** Optional fields keep their values and submit together with the main form. */
    public void setSection(String title, String... keys) {
        if (submit == null || keys.length == 0 || sections.containsKey(title))
            throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.a_section_needs_a_form_a_unique_title_and_fields_1zvmiu"));
        var grouped = java.util.stream.Stream.concat(sections.values().stream(), groups.values().stream()).flatMap(List::stream).toList();
        var requested = List.of(keys);
        if (requested.stream().distinct().count() != requested.size()
                || requested.stream().anyMatch(key -> !values.containsKey(key) || grouped.contains(key)))
            throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.unknown_or_repeated_section_field_1o149xc"));
        sections.put(Objects.requireNonNull(title), requested);
        selected = Math.min(selected, rowCount() - 1);
    }

    /** Display names are separate from saved identities and editable values. */
    public void setValueLabels(String key, Map<String, String> labels) {
        if (!values.containsKey(key)) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.unknown_project_field_u6ow92", Map.of("value0", String.valueOf(key))));
        valueLabels.put(key, Map.copyOf(labels));
    }
    public boolean isOpen() { return open && !suspended; }
    public boolean capturesText() { return open && textEditor != null; }
    public String title() { return title; }
    public String error() { return error; }
    public Map<String, String> values() { return Map.copyOf(values); }
    public void setValue(String key, String value) {
        if (!values.containsKey(key)) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.unknown_project_field_u6ow92", Map.of("value0", String.valueOf(key))));
        values.put(key, Objects.requireNonNull(value));
        if (valueChanged != null) valueChanged.accept(key, value);
    }
    public void onValueChanged(java.util.function.BiConsumer<String, String> listener) { valueChanged = listener; }
    public void close() { open = false; suspended=false; textEditor = null; details = null; }

    /** Returns true whenever this frame is owned by the modal, including its closing frame. */
    public boolean update(InputHandler input) {
        if (!isOpen()) return false;
        lastInput = input; chromeFrame++;
        accept = MenuInput.confirmLabel(input); back = MenuInput.backLabel(input); detailsLabel = MenuInput.detailsLabel(input);
        if (details != null) {
            if (details.update(input)) details = null;
            return true;
        }
        if (textEditor != null) {
            textEditor.update(input);
            switch (textEditor.consumeResult()) {
                case ACCEPTED -> {
                    String value = textEditor.value(); textEditor = null;
                    try {
                        if (pairs.containsKey(editingKey)) {
                            String[] parts = value.split(",", -1);
                            if (parts.length != 2) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.enter_position_as_x_y_mgfmwc"));
                            for (String part : parts) if (!Double.isFinite(Double.parseDouble(part.trim())))
                                throw new IllegalArgumentException(UiMessages.text("editor.editor_project_menu.position_needs_two_finite_numbers_11hy007"));
                            setValue(editingKey, parts[0].trim()); setValue(pairs.get(editingKey), parts[1].trim());
                        } else setValue(editingKey, value);
                        error = null;
                    } catch (RuntimeException failure) { report(failure); }
                }
                case CANCELLED -> textEditor = null;
                case NONE -> { }
            }
            return true;
        }
        // Typing outside a field never becomes a later field's input.
        MenuInput.consumeText(input);
        if (MenuInput.back(input)) { close(); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return true; }
        if (MenuInput.details(input)) { openDetails(); return true; }
        if (input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F2)) {
            var rows=formRows();
            if (submit != null && selected<rows.size() && rows.get(selected).field()!=null) editField(rows.get(selected).field().key());
            return true;
        }
        int previous = selected, count = rowCount();
        if (MenuInput.up(input)) selected = Math.floorMod(selected - 1, count);
        if (MenuInput.down(input)) selected = (selected + 1) % count;
        if (MenuInput.left(input)) changeChoice(-1);
        if (MenuInput.right(input)) changeChoice(1);
        var pointer = MenuInput.pointer(input, width, 224);
        boolean clicked = false;
        int first = firstVisible();
        var rows = formRows(); int pointerY = rowsTop();
        for (int i = first; i < visibleEnd(first); i++) {
            pointerY += gapBefore(i,rows);
            if (pointer.over(9, pointerY, width - 18, rowHeight(i, rows)) && pointer.leftPressed()) {
                selected = i; clicked = true;
            }
            pointerY += rowHeight(i, rows);
        }
        if (previous != selected) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        if (MenuInput.accept(input) || clicked) activate();
        return true;
    }

    private void activate() {
        var rows = formRows();
        if (submit != null && selected < rows.size() && rows.get(selected).section() != null) {
            String section = rows.get(selected).section();
            if (!expanded.remove(section)) expanded.add(section);
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        } else if (submit != null && selected < rows.size()) {
            Field field = rows.get(selected).field();
            if (!field.choices().isEmpty()) {
                int next = (field.choices().indexOf(values.get(field.key()))+1)%field.choices().size();
                setValue(field.key(),field.choices().get(next));
                MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
                return;
            }
            if (canPick(field.key())) pick(field.key()); else editField(field.key());
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
        if (submit == null) throw new IllegalStateException(UiMessages.text("editor.editor_project_menu.no_project_form_is_active_163m1d2"));
        var callback = submit;
        var submitted = values();
        error = null;
        close();
        try { callback.accept(submitted); MenuFeedback.emit(MenuFeedback.Cue.CONFIRM); }
        catch (RuntimeException failure) { open = true; report(failure); }
    }

    public void report(Throwable failure) {
        error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        MenuFeedback.emit(MenuFeedback.Cue.ERROR);
    }

    private List<Row> formRows() {
        var rows = new java.util.ArrayList<Row>();
        var owned = new java.util.HashSet<String>(); groups.values().forEach(owned::addAll); sections.values().forEach(owned::addAll);
        for (Field field : fields) if (!owned.contains(field.key()) && !pairs.containsValue(field.key())) rows.add(new Row(field, null, null, null));
        groups.forEach((title, keys) -> {
            var visible = keys.stream().filter(key -> !pairs.containsValue(key)).toList();
            for (int i = 0; i < visible.size(); i++) rows.add(new Row(requireField(visible.get(i)), null, i == 0 ? title : null, title));
        });
        sections.forEach((title, keys) -> {
            rows.add(new Row(null, title, null, null));
            if (expanded.contains(title)) for (String key : keys) if (!pairs.containsValue(key)) rows.add(new Row(requireField(key), null, null, title));
        });
        return rows;
    }
    private boolean changeChoice(int direction) {
        var rows = formRows();
        if (submit == null || selected >= rows.size() || rows.get(selected).field() == null) return false;
        var field = rows.get(selected).field();
        if (field.choices().isEmpty()) return false;
        int at = field.choices().indexOf(values.get(field.key()));
        setValue(field.key(), field.choices().get(Math.floorMod(at + direction, field.choices().size())));
        MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE); return true;
    }
    private int gapBefore(int index, List<Row> rows) {
        if (submit != null && index==rows.size()) return BUTTON_GAP;
        if (submit != null && index<rows.size() && rows.get(index).section()!=null) return HEADING_GAP;
        if (submit != null && index<rows.size() && rows.get(index).heading()!=null) return Math.max(HEADING_HEIGHT, rowPitch() + 4);
        if (submit == null && index==primaryAction) return BUTTON_GAP;
        return 0;
    }
    private static int headerRule() { return Math.max(25, 14 + MenuChrome.labelLineHeight()); }
    private static int subtitleY() { return headerRule() + 7; }
    private static int rowsTop() { return Math.max(ROW_TOP, subtitleY() + MenuChrome.compactLineHeight() + 4); }
    private static int footerTop() { return Math.min(207, 224 - MenuChrome.compactLineHeight() - 6); }
    private static int statusY() { return footerTop() - MenuChrome.compactLineHeight() - 4; }
    private static int rowPitch() { return Math.max(ROW_HEIGHT, MenuChrome.compactLineHeight() + 4); }
    private int rowHeight(int index, List<Row> rows) {
        if (submit == null) return index < actions.size() && index != primaryAction
                && !actions.get(index).note().isEmpty() ? Math.max(NOTE_ROW_HEIGHT, 2 * rowPitch() - 2) : rowPitch();
        return index < rows.size() && rows.get(index).field() != null && multiline.contains(rows.get(index).field().key()) ? 2 * rowPitch() : rowPitch();
    }
    private int rowCount() { return submit == null ? actions.size() : formRows().size() + 1; }
    private int visibleEnd(int first) {
        var rows = formRows(); int y = rowsTop(), end = first;
        while (end < rowCount()) {
            int height = rowHeight(end, rows) + gapBefore(end,rows);
            if (y + height > (error == null && statusLine.isEmpty() ? footerTop() - 2 : statusY() - 4)) break;
            y += height; end++;
        }
        return end;
    }
    private int firstVisible() {
        int first = 0;
        while (selected >= visibleEnd(first) && first < selected) first++;
        return first;
    }
    private String displayValue(Field field, String value) {
        var labels = valueLabels.get(field.key());
        if (labels != null && labels.containsKey(value)) return labels.get(value);
        if (!field.choices().isEmpty()) return switch (value) {
            case "true" -> UiMessages.text("editor.editor_project_menu.on_1z3"); case "false" -> UiMessages.text("editor.editor_project_menu.off_1p3j");
            default -> EditorLabels.value(value);
        };
        return value.isEmpty() ? "-" : value;
    }
    private static String hint(String text, int width) {
        String line = text.split("\\R", 2)[0];
        return MenuStyle.fit(line, width);
    }
    private int valueColumn() { return Math.round(width * .45f); }
    private int valueWidth() { return width - valueColumn() - 15; }
    private void right(PixelFont font, String value, int y, float r, float g, float b) {
        String fitted = MenuStyle.fit(value, valueWidth());
        MenuStyle.text(font, fitted, width - 15 - MenuChrome.compactWidth(fitted), y, valueWidth(), r, g, b);
    }
    private void options(PixelFont font, Field field, int y) {
        var labels = field.choices().stream().map(value -> displayValue(field, value)).toList();
        int selectedOption = field.choices().indexOf(values.get(field.key()));
        if (labels.size() == 1) {
            MenuStyle.text(font, labels.getFirst(), valueColumn(), y, valueWidth(), .88f, .92f, .98f); return;
        }
        int start = 0, end = labels.size();
        int total = labels.stream().mapToInt(label -> MenuChrome.compactWidth(label) + 10).sum();
        if (total > valueWidth()) { start = Math.max(0, selectedOption); end = Math.min(labels.size(), start + 1); }
        int x = valueColumn();
        for (int i = start; i < end; i++) {
            String label = MenuStyle.fit(labels.get(i), valueWidth() - 10);
            int w = MenuChrome.compactWidth(label) + 10;
            if (i == selectedOption) MenuStyle.fill(font, x, y - 1, w, rowPitch() - 2, .12f, .31f, .39f, 1);
            MenuStyle.text(font, label, x + 5, y, w - 10, i == selectedOption ? .65f : .4f, i == selectedOption ? .94f : .47f, i == selectedOption ? 1 : .55f);
            x += w;
        }
    }
    private static List<String> previewLines(String text, int pixels) {
        var lines = HostText.shared().wrap(text, Math.max(1, pixels), TextFace.COMPACT, HostTextContext.current());
        if (lines.size() <= 2) return lines;
        return List.of(lines.getFirst(), MenuStyle.fit(lines.get(1) + "...", pixels));
    }
    private void drawValue(PixelFont font, Field field, int y) {
        var view = fieldView(field);
        if (view.multiline()) {
            var lines = previewLines(view.display(), Math.max(1, valueWidth()));
            for (int i = 0; i < lines.size(); i++) MenuStyle.text(font, lines.get(i), valueColumn(), y + i * rowPitch(), valueWidth(), .88f, .92f, .98f);
        } else if (view.partner() != null) {
            MenuStyle.text(font, view.display(), valueColumn(), y, valueWidth(), .88f, .92f, .98f);
        } else if (!field.choices().isEmpty()) options(font, field, y);
        else if (view.value().matches("-?\\d+(\\.\\d+)?")) {
            String unit = view.unit(), value = coordinate(view.value());
            MenuStyle.text(font, value, valueColumn(), y, valueWidth(), .88f, .92f, .98f);
            if (!unit.isEmpty()) MenuStyle.text(font, unit, valueColumn()+MenuChrome.compactWidth(value)+6, y, valueWidth()-MenuChrome.compactWidth(value)-6, .4f, .48f, .58f);
        } else MenuStyle.text(font, view.display(), valueColumn(), y, valueWidth(), .88f, .92f, .98f);
    }

    public void render(PixelFont font, int width) {
        if (!isOpen()) return;
        this.width = Math.max(320, width);
        if (details != null) { details.render(font, this.width); return; }
        if (textEditor != null) { textEditor.render(font, this.width); return; }
        MenuChrome.background(font, this.width);
        int first = firstVisible(), end = visibleEnd(first);
        boolean scrolling = first > 0 || end < rowCount();
        String position = scrolling ? (selected + 1) + " / " + rowCount() : null;
        int titleRoom = this.width - 18 - (position == null ? 0 : MenuChrome.tagWidth(position) + 10);
        MenuChrome.header(font, this.width, MenuChrome.fitLabel(title.toUpperCase(java.util.Locale.ROOT), titleRoom), position);
        MenuChrome.compact(font, hint(subtitle, this.width - 24), 12, subtitleY(), MenuChrome.MUTED);
        int y = rowsTop();
        var rows = formRows();
        for (int i = first; i < end; i++) {
            var row = i < rows.size() ? rows.get(i) : null;
            if (submit != null && row != null && row.heading() != null) {
                MenuChrome.section(font, row.heading().toUpperCase(java.util.Locale.ROOT), 15, y + HEADING_GAP, this.width - 30);
            }
            y += gapBefore(i,rows);
            int height = rowHeight(i, rows);
            // A button row shows focus on the button itself rather than a second frame around it.
            boolean buttonRow = submit == null ? i == primaryAction : i == rows.size();
            if (i == selected && !buttonRow) {
                MenuChrome.focus(font, 12, y, this.width - 24, height, chromeFrame);
            }
            if (submit == null) {
                String label=actionLabels.getOrDefault(i,actions.get(i).label());
                // Actions read as a list; the primary one keeps the shared gold-edged button.
                if (i==primaryAction) {
                    int w=Math.min(this.width-30,MenuChrome.compactWidth(label)+20);
                    MenuChrome.button(font,15,y,w,height,label,i==selected,true,chromeFrame);
                } else {
                    // A chevron marks rows that open another page, like folders in the path browser.
                    MenuChrome.compact(font,MenuChrome.fitCompact(label,this.width-44),18,y+2,i==selected?MenuChrome.WHITE:MenuChrome.TEXT);
                    String note=actions.get(i).note();
                    if (!note.isEmpty()) MenuChrome.compact(font,MenuChrome.fitCompact(note,this.width-44),18,y+rowPitch()+1,
                            i==selected?MenuChrome.MUTED:MenuChrome.DIM);
                    MenuChrome.triangle(font,this.width-24,y+(height-6)/2,3,'R',i==selected?MenuChrome.GOLD:MenuChrome.DIM,1f);
                }
            }
            else if (i == rows.size()) {
                int buttonWidth = Math.min(this.width - 30, MenuChrome.compactWidth(submitLabel) + 20);
                MenuChrome.button(font, 15, y, buttonWidth, height, submitLabel, i == selected, true, chromeFrame);
            } else if (row.section() != null) {
                String section = row.section();
                MenuChrome.compact(font, MenuChrome.fitCompact((expanded.contains(section) ? "- " : "+ ") + section, valueColumn() - 26),
                        18, y + 2, MenuChrome.CYAN);
                if (!expanded.contains(section)) MenuChrome.compact(font, MenuChrome.fitCompact(sectionSummary(section), valueWidth()), valueColumn(), y + 2, MenuChrome.DIM);
            } else {
                if (row.group() != null) MenuChrome.fill(font, 15, y, 1, height, MenuChrome.EDGE);
                int labelX = row.group() == null ? 18 : 22;
                MenuChrome.compact(font, MenuChrome.fitCompact(row.field().label(), valueColumn() - labelX - 8), labelX, y + 2,
                        i == selected ? MenuChrome.WHITE : MenuChrome.MUTED);
                drawValue(font, row.field(), y + 2);
            }
            y += height;
        }
        if (scrolling) MenuChrome.scrollbar(font, this.width - 10, rowsTop(),
                (error == null && statusLine.isEmpty() ? footerTop() - 2 : statusY() - 4) - rowsTop(),
                first, end - first, rowCount());
        // Status and errors use the shared tone icons and colours above the footer.
        if (error == null && !statusLine.isEmpty()) {
            var color = switch (tone) { case ERROR -> MenuChrome.RED; case WARNING -> MenuChrome.AMBER;
                case SUCCESS -> MenuChrome.GREEN; case INFO -> MenuChrome.CYAN; };
            MenuChrome.icon(font, switch (tone) { case SUCCESS -> MenuChrome.Icon.CHECK; case INFO -> MenuChrome.Icon.INFO;
                default -> MenuChrome.Icon.WARNING; }, 12, statusY(), color);
            MenuChrome.compact(font, MenuChrome.fitCompact(statusLine, this.width - 36), 23, statusY(), MenuChrome.TEXT);
        }
        if (error != null) {
            // Name the problem itself; the key cap at the right opens the full explanation.
            var why = java.util.List.of(MenuChrome.hint(detailsLabel, UiMessages.text("editor.menu.editor_project_menu.why")));
            int helpWidth = MenuChrome.hintsWidth(why);
            MenuChrome.icon(font, MenuChrome.Icon.WARNING, 12, statusY(), MenuChrome.RED);
            String shown = error.isBlank() ? UiMessages.text("editor.menu.editor_project_menu.check_the_values") : MenuDetailsScreen.readable(error.lines().findFirst().orElse(error));
            MenuChrome.compact(font, MenuChrome.fitCompact(shown, this.width - 44 - helpWidth), 23, statusY(), MenuChrome.RED);
            MenuChrome.hints(font, this.width - 12 - helpWidth, statusY() - 1, why, MenuChrome.controller(lastInput), MenuChrome.MUTED);
        }
        boolean apply = submit != null && selected == rows.size();
        boolean pick = submit != null && selected < rows.size() && rows.get(selected).field() != null
                && canPick(rows.get(selected).field().key());
        var hints = new java.util.ArrayList<MenuChrome.Hint>();
        hints.add(MenuChrome.upDown(lastInput, pick ? "" : UiMessages.text("editor.menu.editor_project_menu.move")));
        // Enter opens the same editor as the secondary left/right shortcuts. Keep all primary
        // actions visible at 320 rather than letting the shared footer drop navigation.
        hints.add(MenuChrome.hint(accept, apply ? UiMessages.text("editor.menu.editor_project_menu.apply") : pick ? UiMessages.text("editor.menu.editor_project_menu.pick") : submit == null ? UiMessages.text("menu.path.open_caps") : UiMessages.text("settings.screen.edit_caps")));
        if (pick) hints.add(MenuChrome.hint("F2", UiMessages.text("settings.screen.edit_caps")));
        // Help before the way out, as on every other page: the back key is always the last cap.
        hints.add(MenuChrome.hint(detailsLabel, UiMessages.text("hub.ui.help_caps")));
        hints.add(MenuChrome.hint(back, UiMessages.text("editor.menu.editor_project_menu.close")));
        MenuChrome.footer(font, this.width, null, hints, MenuChrome.controller(lastInput));
    }
}
