package com.openggf.configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Editable preferences. Cancel discards this object; only apply writes to the service. */
public final class EngineSettingsDraft {
    public enum Category {
        DISPLAY("Display"), AUDIO("Sound"), INPUT("Controls"), GAMEPLAY("Gameplay"),
        FILES("Files"), RECORDING("Capture"),
        /** Per-game launch profiles; edited in Game Setup, not on a Settings tab. */
        LAUNCH("Launch"),
        ADVANCED("Advanced");
        private final String label;
        Category(String label) { this.label = label; }
        public String label() { return label; }
    }

    private static final Map<Category, List<SonicConfiguration>> CATEGORY_KEYS = new EnumMap<>(Category.class);
    static {
        for (Category category : Category.values()) {
            // Stable sort: within Controls, player bindings come before engine shortcuts.
            CATEGORY_KEYS.put(category, ConfigCatalog.emitOrder().stream().filter(k -> category(k) == category && !ConfigCatalog.meta(k).section().startsWith("hub"))
                    .sorted(java.util.Comparator.comparingInt(k -> controlGroupOrder(k)))
                    .toList());
        }
    }
    private final Map<SonicConfiguration, String> textCache = new EnumMap<>(SonicConfiguration.class);
    private final Map<SonicConfiguration, Boolean> changedCache = new EnumMap<>(SonicConfiguration.class);
    private final Map<SonicConfiguration, Boolean> nonDefaultCache = new EnumMap<>(SonicConfiguration.class);
    private long revision;
    private int changedCount;
    private final SonicConfigurationService config;
    private final Map<SonicConfiguration, Object> initial;
    private final Map<SonicConfiguration, Object> values;

    public EngineSettingsDraft(SonicConfigurationService config) {
        this.config = Objects.requireNonNull(config);
        initial = config.settingsSnapshot();
        values = new EnumMap<>(initial);
        values.keySet().forEach(this::refresh);
    }

    public List<SonicConfiguration> keys(Category category) {
        return CATEGORY_KEYS.get(category);
    }

    private static int controlGroupOrder(SonicConfiguration key) {
        String section = ConfigCatalog.meta(key).section();
        if (section.equals("input.player1")) return 0;
        if (section.equals("input.player2")) return 1;
        if (section.equals("input.controller")) return 2;
        return 3;
    }

    public static Category category(SonicConfiguration key) {
        ConfigKeyMeta meta = ConfigCatalog.meta(key);
        if (!meta.persisted()) throw new IllegalArgumentException("Derived setting");
        String section = meta.section();
        if (section.startsWith("launch")) return Category.LAUNCH;
        // Every non-debug hotkey lives with the other controls, wherever its section is.
        if (meta.type() == ConfigType.KEY && !section.startsWith("debug")) return Category.INPUT;
        if (key == SonicConfiguration.LOAD_TIME_SIMULATION
                || section.startsWith("rewind") && key != SonicConfiguration.LIVE_REWIND_ENABLED
                || section.startsWith("hub")) return Category.ADVANCED;
        if (key == SonicConfiguration.DEBUG_VIEW_ENABLED) return Category.GAMEPLAY;
        if (key == SonicConfiguration.DISPLAY_SHADER_LIBRARY_ROOT
                || key == SonicConfiguration.DISPLAY_SHADER_DEFAULT_PHASE
                || key == SonicConfiguration.FPS || key == SonicConfiguration.SCALE) return Category.ADVANCED;
        if (section.equals("ui") || section.startsWith("display")) return Category.DISPLAY;
        if (section.startsWith("audio")) return Category.AUDIO;
        if (section.startsWith("input")) return Category.INPUT;
        if (section.startsWith("roms")) return Category.FILES;
        if (section.startsWith("capture")) return Category.RECORDING;
        // Global characters and cross-game donation only apply to direct launches: the
        // hub's Game Setup owns them per game.
        if (section.startsWith("multiplayer") || section.startsWith("gameplay") || section.startsWith("startup")
                || section.startsWith("rewind")) return Category.GAMEPLAY;
        return Category.ADVANCED;
    }

    public Object value(SonicConfiguration key) { return values.get(key); }
    public long revision() { return revision; }
    public String text(SonicConfiguration key) { return textCache.getOrDefault(key, ""); }
    private String format(SonicConfiguration key) {
        return formatValue(key, value(key));
    }
    private static String formatValue(SonicConfiguration key, Object value) {
        if (ConfigCatalog.meta(key).type() == ConfigType.KEY && value instanceof Number) {
            int code = ((Number) value).intValue();
            return code == -1 ? "" : GlfwKeyNameResolver.nameOf(code);
        }
        return value == null ? "" : value.toString();
    }
    public boolean nonDefault(SonicConfiguration key) {
        return nonDefaultCache.getOrDefault(key, false);
    }
    public boolean changed(SonicConfiguration key) {
        return changedCache.getOrDefault(key, false);
    }
    public boolean dirty() { return changedCount > 0; }
    public int changedCount() { return changedCount; }
    public void reset(SonicConfiguration key) { put(key, config.getDefaultValue(key)); }
    public void reset(Category category) { keys(category).forEach(this::reset); }
    public record Change(SonicConfiguration key, String before, String after) { }
    public List<Change> changes() {
        return ConfigCatalog.emitOrder().stream().filter(this::changed)
                .map(key -> new Change(key, formatValue(key, initial.get(key)), text(key))).toList();
    }
    /** Duplicate chords warn but remain legal: games deliberately share some controls. */
    public List<SonicConfiguration> bindingConflicts(SonicConfiguration key) {
        if (ConfigCatalog.meta(key).type() != ConfigType.KEY) return List.of();
        KeyChord chord = KeyChord.parse(value(key));
        if (!chord.isBound()) return List.of();
        return ConfigCatalog.emitOrder().stream().filter(other -> other != key
                && ConfigCatalog.meta(other).type() == ConfigType.KEY
                && chord.equals(KeyChord.parse(value(other)))).toList();
    }
    private void put(SonicConfiguration key, Object next) {
        if (Objects.equals(values.get(key), next)) return;
        values.put(key, next);
        refresh(key);
        revision++;
    }
    private void refresh(SonicConfiguration key) {
        boolean changed = !equivalent(key, value(key), initial.get(key));
        if (changedCache.getOrDefault(key, false)) changedCount--;
        if (changed) changedCount++;
        changedCache.put(key, changed);
        nonDefaultCache.put(key, !equivalent(key, value(key), config.getDefaultValue(key)));
        textCache.put(key, format(key));
    }

    /** Reject invalid typed edits before they enter the draft. */
    public void set(SonicConfiguration key, String text) {
        if (!values.containsKey(key)) throw new IllegalArgumentException("Derived setting");
        ConfigKeyMeta meta = ConfigCatalog.meta(key);
        Object parsed;
        try {
            parsed = switch (meta.type()) {
                case BOOL -> {
                    if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false"))
                        throw new IllegalArgumentException("Choose true or false");
                    yield Boolean.parseBoolean(text);
                }
                case INT -> Integer.parseInt(text.trim());
                case DOUBLE -> {
                    double number = Double.parseDouble(text.trim());
                    if (!Double.isFinite(number)) throw new IllegalArgumentException("Use a finite number");
                    yield number;
                }
                case ENUM -> {
                    if (!meta.allowedValues().contains(text))
                        throw new IllegalArgumentException("Choose a listed value");
                    yield text;
                }
                case KEY -> {
                    if (!text.isEmpty() && !text.equals("-1") && !KeyChord.parse(text).isBound())
                        throw new IllegalArgumentException("Use a key name or chord");
                    yield text;
                }
                case STRING -> key == SonicConfiguration.UI_LANGUAGE ? UiLanguagePreference.normalize(text) : text;
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(meta.type() == ConfigType.INT ? "Use a whole number" : "Use a number");
        }
        validateRange(key, parsed);
        put(key, parsed);
    }

    public void step(SonicConfiguration key, int direction) {
        ConfigKeyMeta meta = ConfigCatalog.meta(key);
        if (key == SonicConfiguration.AUDIO_HUB_MUSIC_VOLUME) {
            set(key, Integer.toString(Math.clamp(Integer.parseInt(text(key)) + direction * 5, 0, 100)));
            return;
        }
        switch (meta.type()) {
            case BOOL -> set(key, Boolean.toString(!Boolean.parseBoolean(text(key))));
            case ENUM -> {
                List<String> options = new ArrayList<>(meta.allowedValues());
                options.sort(String::compareTo);
                set(key, options.get(Math.floorMod(options.indexOf(text(key)) + direction, options.size())));
            }
            case INT -> set(key, Long.toString(Long.parseLong(text(key)) + direction));
            case DOUBLE -> set(key, java.math.BigDecimal.valueOf(Double.parseDouble(text(key)))
                    .add(java.math.BigDecimal.valueOf(direction * 0.05)).stripTrailingZeros().toPlainString());
            default -> { }
        }
    }

    /** Save failure leaves both live preferences and draft edits intact for retry. */
    public void apply() throws IOException {
        Map<SonicConfiguration, Object> changes = new EnumMap<>(SonicConfiguration.class);
        for (SonicConfiguration key : values.keySet()) {
            if (changed(key)) {
                validateRange(key, value(key));
                changes.put(key, value(key));
            }
        }
        if (changes.isEmpty()) return;
        double min = Double.parseDouble(text(SonicConfiguration.LIVE_REWIND_TAPE_COAST_MIN_STEPS));
        double max = Double.parseDouble(text(SonicConfiguration.LIVE_REWIND_TAPE_COAST_MAX_STEPS));
        if ((changes.containsKey(SonicConfiguration.LIVE_REWIND_TAPE_COAST_MIN_STEPS)
                || changes.containsKey(SonicConfiguration.LIVE_REWIND_TAPE_COAST_MAX_STEPS)) && min > max)
            throw new IllegalArgumentException("Rewind minimum exceeds maximum");
        config.applySettings(changes);
        PendingRestart.noteSaved(changes.keySet());
        initial.putAll(changes);
        changes.keySet().forEach(this::refresh);
        revision++;
    }

    private static void validateRange(SonicConfiguration key, Object value) {
        if (!(value instanceof Number number)) return;
        double n = number.doubleValue();
        if (!Double.isFinite(n)) throw new IllegalArgumentException("Use a finite number");
        switch (key) {
            case AUDIO_HUB_MUSIC_VOLUME -> {
                if (n < 0 || n > 100) throw new IllegalArgumentException("Volume: 0 to 100");
            }
            case CONTROLLER_DEADZONE -> {
                if (n < 0 || n >= 1) throw new IllegalArgumentException("Deadzone: 0 to below 1");
            }
            case FPS, SCREEN_WIDTH, SCREEN_HEIGHT, SCALE, CAPTURE_SCALE, CAPTURE_FPS,
                    CAPTURE_QUEUE_BUDGET_MB, REWIND_HISTORY_SECONDS, REWIND_AUDIO_HISTORY_SECONDS,
                    REWIND_AUDIO_HISTORY_SIZE_MB, LIVE_REWIND_TAPE_COAST_MIN_STEPS,
                    LIVE_REWIND_TAPE_COAST_MAX_STEPS -> {
                if (n <= 0) throw new IllegalArgumentException("Must be greater than zero");
            }
            case CAPTURE_ENCODER_THREADS, PLAYBACK_START_OFFSET_FRAME,
                    LIVE_REWIND_TAPE_COAST_ACCELERATION, LIVE_REWIND_TAPE_COAST_DECELERATION -> {
                if (n < 0) throw new IllegalArgumentException("Must be zero or greater");
            }
            default -> { }
        }
    }

    private static boolean equivalent(SonicConfiguration key, Object a, Object b) {
        if (ConfigCatalog.meta(key).type() == ConfigType.KEY) return KeyChord.parse(a).equals(KeyChord.parse(b));
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        return Objects.equals(a, b);
    }
}
