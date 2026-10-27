package com.openggf.editor.project;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuDetailsScreen;
import com.openggf.game.MenuChrome;
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
    public void pick(String key) { if (!canPick(key)) throw new IllegalArgumentException("This field has no level picker"); try {positionPick.accept(positions.get(key));}catch(RuntimeException failure){report(failure);} }
    public void suspend() { suspended = true; }
    public void resume() { suspended = false; }
    public void setPrimaryAction(int index) { if (index < 0 || index >= actions.size()) throw new IllegalArgumentException("Unknown action"); primaryAction = index; }
    public void setActionLabel(int index, String label) { actionLabels.put(index, label); }
    public List<Action> actionEntries() { return actions; }
    public List<Field> formFields() { return fields; }
    public void setStatusLine(String text, Tone tone) { statusLine = Objects.requireNonNull(text); this.tone = Objects.requireNonNull(tone); }
    public record ActionRow(String label, int index, boolean primary) { }
    public record ActionView(String title, String hint, String help, List<ActionRow> rows, String status, Tone tone, String error) { }
    public ActionView actionView() {
        if (!isOpen() || submit != null || presenter == null) return null;
        var rows = new java.util.ArrayList<ActionRow>();
        for (int i=0;i<actions.size();i++) rows.add(new ActionRow(actionLabels.getOrDefault(i,actions.get(i).label()),i,i==primaryAction));
        return new ActionView(title,subtitle,help,List.copyOf(rows),statusLine,tone,error);
    }
    public void runAction(int index) { selected=index; activate(); }
    public String detailsText() {
        String text = error != null ? error : subtitle + "\n\n" + help;
        if (submit != null) for (Field field:fields) text += "\n" + originalLabels.getOrDefault(field.key(),field.label()) + ": "
                + (multiline.contains(field.key()) ? decodePreviewText(values.get(field.key())) : values.get(field.key()));
        else for (Action action:actions) text += "\n" + action.label();
        return text;
    }
    public void setDetailsContent(MenuDetailsScreen.Content content) { detailsContent=Objects.requireNonNull(content); }
    public void openDetails() { details = new MenuDetailsScreen(detailsContent==null||error!=null?defaultDetails():detailsContent); }
    private MenuDetailsScreen.Content defaultDetails() {
        var sections=new java.util.ArrayList<MenuDetailsScreen.Section>();
        var form=formView();
        if(form!=null) for(var group:form.groups()) sections.add(new MenuDetailsScreen.Section(
                group.title().isBlank()?"Values":group.title(),group.fields().stream().map(f->new MenuDetailsScreen.Row(
                        f.field().label(),f.display())).toList(),false));
        else if(submit!=null) sections.add(new MenuDetailsScreen.Section("Values",fields.stream().map(f->
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
                || pairs.containsKey(second) || pairs.containsValue(second)) throw new IllegalArgumentException("Repeated coordinate pair");
        pairs.put(first, second); configureField(first, label);
    }
    public void setMultilinePreview(String key) { requireField(key); multiline.add(key); }
    /** Context stays in the shared form so both frontends use the same Who row. */
    public void addContextField(String key, String label, String value, String display) {
        if (values.containsKey(key)) throw new IllegalArgumentException("Repeated context field");
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
        if (!sections.containsKey(section)) throw new IllegalArgumentException("Unknown section");
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
                .orElseThrow(() -> new IllegalArgumentException("Unknown project field: " + key));
    }
    public void setGroup(String title, String... keys) {
        if (submit == null || keys.length == 0 || groups.containsKey(title)) throw new IllegalArgumentException("Invalid form group");
        var owned = new java.util.HashSet<String>(); groups.values().forEach(owned::addAll); sections.values().forEach(owned::addAll);
        for (String key : keys) if (!owned.add(key)) throw new IllegalArgumentException("Repeated group field"); else requireField(key);
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
        return edited ? "Custom settings" : sectionSummaries.getOrDefault(name, "More settings");
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
        reset(title);
        this.actions = List.copyOf(actions);
        if (actions.isEmpty()) throw new IllegalArgumentException("A project menu needs an action");
        if (selected < 0 || selected >= actions.size()) throw new IllegalArgumentException("Invalid selected action");
        this.selected = selected;
        if (presenter != null) presenter.accept(this);
    }

    public void showForm(String title, List<Field> fields, String submitLabel,
                         Consumer<Map<String, String>> submit) {
        reset(title);
        this.fields = List.copyOf(fields);
        this.submitLabel = Objects.requireNonNull(submitLabel);
        this.submit = Objects.requireNonNull(submit);
        for (Field field : fields) {
            originalLabels.put(field.key(),field.label());
            if (values.putIfAbsent(field.key(), field.value()) != null)
                throw new IllegalArgumentException("Duplicate project field: " + field.key());
        }
        if (presenter != null) presenter.accept(this);
    }

    private void reset(String title) {
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
            throw new IllegalArgumentException("A section needs a form, a unique title and fields");
        var grouped = java.util.stream.Stream.concat(sections.values().stream(), groups.values().stream()).flatMap(List::stream).toList();
        var requested = List.of(keys);
        if (requested.stream().distinct().count() != requested.size()
                || requested.stream().anyMatch(key -> !values.containsKey(key) || grouped.contains(key)))
            throw new IllegalArgumentException("Unknown or repeated section field");
        sections.put(Objects.requireNonNull(title), requested);
        selected = Math.min(selected, rowCount() - 1);
    }

    /** Display names are separate from saved identities and editable values. */
    public void setValueLabels(String key, Map<String, String> labels) {
        if (!values.containsKey(key)) throw new IllegalArgumentException("Unknown project field: " + key);
        valueLabels.put(key, Map.copyOf(labels));
    }
    public boolean isOpen() { return open && !suspended; }
    public boolean capturesText() { return open && textEditor != null; }
    public String title() { return title; }
    public String error() { return error; }
    public Map<String, String> values() { return Map.copyOf(values); }
    public void setValue(String key, String value) {
        if (!values.containsKey(key)) throw new IllegalArgumentException("Unknown project field: " + key);
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
                            if (parts.length != 2) throw new IllegalArgumentException("Enter position as X, Y");
                            for (String part : parts) if (!Double.isFinite(Double.parseDouble(part.trim())))
                                throw new IllegalArgumentException("Position needs two finite numbers");
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
        var rows = formRows(); int pointerY = ROW_TOP;
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
        if (submit == null) throw new IllegalStateException("No project form is active");
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
        if (submit != null && index<rows.size() && rows.get(index).heading()!=null) return HEADING_HEIGHT;
        if (submit == null && index==primaryAction) return BUTTON_GAP;
        return 0;
    }
    private int rowHeight(int index, List<Row> rows) {
        return index < rows.size() && rows.get(index).field() != null && multiline.contains(rows.get(index).field().key()) ? 2 * ROW_HEIGHT : ROW_HEIGHT;
    }
    private int rowCount() { return submit == null ? actions.size() : formRows().size() + 1; }
    private int visibleEnd(int first) {
        var rows = formRows(); int y = ROW_TOP, end = first;
        while (end < rowCount()) {
            int height = rowHeight(end, rows) + gapBefore(end,rows);
            if (y + height > (error == null && statusLine.isEmpty() ? ROW_BOTTOM : 191)) break;
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
            case "true" -> "On"; case "false" -> "Off";
            default -> value.matches("[A-Z][A-Z_]*") ? java.util.Arrays.stream(value.split("_"))
                    .map(word -> word.charAt(0) + word.substring(1).toLowerCase(java.util.Locale.ROOT))
                    .collect(java.util.stream.Collectors.joining(" ")) : value;
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
        MenuStyle.text(font, fitted, width - 15 - fitted.length() * 6, y, valueWidth(), r, g, b);
    }
    private void options(PixelFont font, Field field, int y) {
        var labels = field.choices().stream().map(value -> displayValue(field, value)).toList();
        int selectedOption = field.choices().indexOf(values.get(field.key()));
        if (labels.size() == 1) {
            MenuStyle.text(font, labels.getFirst(), valueColumn(), y, valueWidth(), .88f, .92f, .98f); return;
        }
        int start = 0, end = labels.size();
        int total = labels.stream().mapToInt(label -> label.length() * 6 + 10).sum();
        if (total > valueWidth()) { start = Math.max(0, selectedOption); end = Math.min(labels.size(), start + 1); }
        int x = valueColumn();
        for (int i = start; i < end; i++) {
            String label = MenuStyle.fit(labels.get(i), valueWidth() - 10);
            int w = label.length() * 6 + 10;
            if (i == selectedOption) MenuStyle.fill(font, x, y - 1, w, 10, .12f, .31f, .39f, 1);
            MenuStyle.text(font, label, x + 5, y, w - 10, i == selectedOption ? .65f : .4f, i == selectedOption ? .94f : .47f, i == selectedOption ? 1 : .55f);
            x += w;
        }
    }
    private static String ellipsis(String value, int columns) {
        if (columns < 4) return ".".repeat(Math.max(0, columns));
        if (value.length() <= columns - 3) return value.stripTrailing() + "...";
        int end = Math.min(value.length(), columns - 3);
        int space = value.lastIndexOf(' ', end);
        if (space > 0) end = space;
        return value.substring(0, end).stripTrailing() + "...";
    }
    private static List<String> previewLines(String text, int columns) {
        var lines = new java.util.ArrayList<String>();
        for (String paragraph : text.split("\\R", -1)) {
            String line = "";
            for (String word : paragraph.split(" +")) {
                if (!line.isEmpty() && line.length() + word.length() + 1 > columns) { lines.add(line); line = ""; }
                line = line.isEmpty() ? word : line + " " + word;
            }
            lines.add(line);
        }
        var result = new java.util.ArrayList<String>();
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            String line = lines.get(i);
            result.add(line.length() > columns || i == 1 && lines.size() > 2 ? ellipsis(line, columns) : line);
        }
        return result;
    }
    private void drawValue(PixelFont font, Field field, int y) {
        var view = fieldView(field);
        if (view.multiline()) {
            var lines = previewLines(view.display(), Math.max(1, valueWidth() / 6));
            for (int i = 0; i < lines.size(); i++) MenuStyle.text(font, lines.get(i), valueColumn(), y + i * ROW_HEIGHT, valueWidth(), .88f, .92f, .98f);
        } else if (view.partner() != null) {
            MenuStyle.text(font, view.display(), valueColumn(), y, valueWidth(), .88f, .92f, .98f);
        } else if (!field.choices().isEmpty()) options(font, field, y);
        else if (view.value().matches("-?\\d+(\\.\\d+)?")) {
            String unit = view.unit(), value = coordinate(view.value());
            MenuStyle.text(font, value, valueColumn(), y, valueWidth(), .88f, .92f, .98f);
            if (!unit.isEmpty()) MenuStyle.text(font, unit, valueColumn()+value.length()*6+6, y, valueWidth()-value.length()*6-6, .4f, .48f, .58f);
        } else MenuStyle.text(font, view.display(), valueColumn(), y, valueWidth(), .88f, .92f, .98f);
    }

    public void render(PixelFont font, int width) {
        if (!isOpen()) return;
        this.width = Math.max(320, width);
        if (details != null) { details.render(font, this.width); return; }
        if (textEditor != null) { textEditor.render(font, this.width); return; }
        MenuChrome.background(font, this.width);
        MenuChrome.header(font, this.width, MenuChrome.fitLabel(title, this.width - 18), null);
        MenuChrome.compact(font, hint(subtitle, this.width - 24), 12, 32, MenuChrome.MUTED);
        int first = firstVisible(), y = ROW_TOP;
        var rows = formRows();
        for (int i = first; i < visibleEnd(first); i++) {
            var row = i < rows.size() ? rows.get(i) : null;
            if (submit != null && row != null && row.heading() != null) {
                MenuStyle.text(font, row.heading(), 15, y + HEADING_GAP, this.width - 30, 1, .78f, .23f);
            }
            y += gapBefore(i,rows);
            int height = rowHeight(i, rows);
            if (i == selected) {
                MenuChrome.focus(font, 12, y, this.width - 24, ROW_HEIGHT, chromeFrame);
            }
            if (submit == null) {
                String label=actionLabels.getOrDefault(i,actions.get(i).label());
                if (i==primaryAction) {
                    int w=Math.min(this.width-30,label.length()*6+20); MenuStyle.fill(font,15,y,w,height,1,.78f,.23f,1);
                    MenuStyle.text(font,label,25,y+2,w-20,.025f,.05f,.09f);
                } else MenuStyle.text(font,label,15,y+2,this.width-30,.87f,.91f,.96f);
            }
            else if (i == rows.size()) {
                int buttonWidth = Math.min(this.width - 30, submitLabel.length() * 6 + 20);
                MenuStyle.fill(font, 15, y, buttonWidth, height, 1, .78f, .23f, 1);
                MenuStyle.text(font, submitLabel, 25, y + 2, buttonWidth - 20, .025f, .05f, .09f);
            } else if (row.section() != null) {
                String section = row.section();
                MenuStyle.text(font, (expanded.contains(section) ? "- " : "+ ") + section, 15, y + 2, this.width - 30, .6f, .69f, .8f);
                if (!expanded.contains(section)) MenuStyle.text(font, sectionSummary(section), valueColumn(), y + 2, valueWidth(), .4f, .48f, .58f);
            } else {
                if (row.group() != null) MenuStyle.fill(font, 14, y, 1, height, .22f, .19f, .11f, 1);
                int labelX = row.group() == null ? 15 : 21;
                MenuStyle.text(font, row.field().label(), labelX, y + 2, valueColumn() - labelX - 8,
                        i == selected ? .88f : .62f, i == selected ? .94f : .69f, i == selected ? 1 : .78f);
                drawValue(font, row.field(), y + 2);
            }
            y += height;
        }
        if (error == null && !statusLine.isEmpty()) {
            MenuStyle.fill(font,12,197,3,3,tone==Tone.ERROR?1:.6f,tone==Tone.SUCCESS?.9f:.65f,tone==Tone.ERROR?.3f:.4f,1);
            MenuStyle.text(font,statusLine,21,195,this.width-33,.8f,.86f,.94f);
        }
        if (error != null) {
            MenuStyle.fill(font, 12, 197, 3, 3, 1, .35f, .4f, 1);
            MenuStyle.text(font, "Check the values; " + detailsLabel + " explains why.", 21, 195, this.width - 33, .95f, .57f, .6f);
        }
        boolean apply = submit != null && selected == rows.size();
        boolean pick = submit != null && selected < rows.size() && rows.get(selected).field() != null
                && canPick(rows.get(selected).field().key());
        var hints = new java.util.ArrayList<MenuChrome.Hint>();
        hints.add(MenuChrome.upDown(lastInput, pick ? "" : "Move"));
        // Enter opens the same editor as the secondary left/right shortcuts. Keep all primary
        // actions visible at 320 rather than letting the shared footer drop navigation.
        hints.add(MenuChrome.hint(accept, apply ? "Apply" : pick ? "Pick" : submit == null ? "Open" : "Edit"));
        if (pick) hints.add(MenuChrome.hint("F2", "Edit"));
        hints.add(MenuChrome.hint(back, "Close"));
        hints.add(MenuChrome.hint(detailsLabel, "Help"));
        MenuChrome.footer(font, this.width, null, hints, MenuChrome.controller(lastInput));
    }
}
