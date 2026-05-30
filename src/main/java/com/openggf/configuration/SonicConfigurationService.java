package com.openggf.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.*;

public class SonicConfigurationService {
	private static final Logger LOGGER = Logger.getLogger(SonicConfigurationService.class.getName());
	private static SonicConfigurationService sonicConfigurationService;

	private Map<String, Object> config;
	private Map<String, Object> defaults = new HashMap<>();
	private boolean loadedFromExistingFile;
	private boolean defaultInsertedSinceLastApply;
	// Derived (non-persisted) display values; read before `config`, never saved.
	private final Map<String, Object> transientResolved = new HashMap<>();

	private SonicConfigurationService() {
		ObjectMapper mapper = new ObjectMapper();
		TypeReference<Map<String, Object>> type = new TypeReference<>(){};

		if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
			// Native image: look for config.json next to the executable binary
			File execConfig = findConfigNextToExecutable();
			if (execConfig != null && execConfig.exists()) {
				try {
					config = mapper.readValue(execConfig, type);
					loadedFromExistingFile = true;
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Failed to load config.json from executable directory", e);
				}
			}
		} else {
			// JAR mode: look for config.json in the working directory
			File file = resolveRelativeFile("config.json");
			if (file.exists()) {
				try {
					config = mapper.readValue(file, type);
					loadedFromExistingFile = true;
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Failed to load config.json from working directory", e);
				}
			}
		}

		if (config == null) {
			try (InputStream is = getClass().getResourceAsStream("/config.json")) {
				if (is != null) {
					config = mapper.readValue(is, type);
				} else {
					LOGGER.log(Level.WARNING, "Could not find config.json, using defaults.");
					config = new HashMap<>();
				}
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Failed to load config.json from classpath", e);
				config = new HashMap<>();
			}
		}

		// Migrate deprecated key encodings/defaults before applying defaults.
		ConfigMigrationService migrationService = new ConfigMigrationService();
		boolean configChanged = false;
		if (migrationService.detectAwtKeyCodes(config)) {
			migrationService.migrateConfig(config);
			configChanged = true;
		}
		if (migrationService.migrateDeprecatedS1PreviewCoordLogKey(config)) {
			configChanged = true;
		}
		if (migrationService.migrateDeprecatedDisplayColorProfileToggleKey(config)) {
			configChanged = true;
		}

		boolean defaultsInserted = applyDefaults();

		if (configChanged || (loadedFromExistingFile && defaultsInserted)) {
			saveConfig();
		}
		resolveDisplayAspect();
	}

	public int getInt(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if (value instanceof Integer) {
			return ((Integer) value);
		} else {
			String str = getString(sonicConfiguration);
			// Step 1: try numeric parse
			try {
				return Integer.parseInt(str);
			} catch (NumberFormatException ignored) {
			}

			// Step 2: try GLFW key name resolution
			OptionalInt resolved = GlfwKeyNameResolver.resolve(str);
			if (resolved.isPresent()) {
				return resolved.getAsInt();
			}

			// Step 3: fall back to default with warning
			if (!str.isEmpty()) {
				int intDefault = resolveKeyCode(defaults.get(sonicConfiguration.name()));
				if (intDefault > 0) {
					LOGGER.warning("'" + str + "' could not be interpreted as a valid input for "
							+ sonicConfiguration.name() + ". Defaulting to '"
							+ GlfwKeyNameResolver.nameOf(intDefault) + "'");
					return intDefault;
				} else {
					LOGGER.warning("'" + str + "' could not be interpreted as a valid input for "
							+ sonicConfiguration.name() + ". Defaulting to unbound");
				}
			}
			return -1;
		}
	}

	/**
	 * Creates an independent configuration service with the same loading rules as
	 * the process singleton. Intended for standalone tools that are not wired
	 * through {@code EngineContext}.
	 */
	public static SonicConfigurationService createStandalone() {
		return new SonicConfigurationService();
	}

	public short getShort(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if (value instanceof Short) {
			return ((Short) value).shortValue();
		} else if (value instanceof Integer) {
			return (short) getInt(sonicConfiguration);
		} else {
			try {
				return Short.parseShort(getString(sonicConfiguration));
			} catch (NumberFormatException e) {
				return -1;
			}
		}
	}

	public String getString(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if (value != null) {
			return value.toString();
		} else {
			return StringUtils.EMPTY;
		}
	}

	public double getDouble(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if (value instanceof Double) {
			return ((Double) value);
		} else {
			try {
				return Double.parseDouble(getString(sonicConfiguration));
			} catch (NumberFormatException e) {
				return -1.00d;
			}
		}
	}

	public boolean getBoolean(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if(value instanceof Boolean) {
			return ((Boolean) value);
		} else if (value instanceof Number) {
			return ((Number) value).intValue() != 0;
		} else {
			return Boolean.parseBoolean(getString(sonicConfiguration));
		}
	}

	public Object getConfigValue(SonicConfiguration sonicConfiguration) {
		Object overlay = transientResolved.get(sonicConfiguration.name());
		if (overlay != null) {
			return overlay;
		}
		if (config != null && config.containsKey(sonicConfiguration.name())) {
			return config.get(sonicConfiguration.name());
		}
		return null;
	}

	/**
	 * Returns the default value for a configuration key, or {@code null} if
	 * no default is registered. Package-private for testing.
	 */
	Object getDefaultValue(SonicConfiguration key) {
		return defaults.get(key.name());
	}

	/**
	 * Resolves DISPLAY_ASPECT into the derived SCREEN_WIDTH_PIXELS (and, when
	 * DISPLAY_WINDOW_AUTOSIZE is true with a widescreen preset,
	 * SCREEN_WIDTH/SCREEN_HEIGHT). Derived values are stored in an in-memory
	 * overlay only and are NEVER written to config.json. SCREEN_WIDTH_PIXELS is
	 * therefore a derived value here, not a user setting; a manual
	 * SCREEN_WIDTH_PIXELS in config.json is superseded by the preset. Idempotent.
	 * Height pixels stay 224.
	 *
	 * <p>When {@code TEST_MODE_ENABLED} is {@code true} the aspect is always
	 * forced to {@code NATIVE_4_3} regardless of the persisted value.
	 * Trace replay tests and the test-mode trace picker are parity-critical and
	 * only valid at 320×224; a developer's widescreen {@code DISPLAY_ASPECT}
	 * must never leak into those runs.
	 */
	public void resolveDisplayAspect() {
		WidescreenAspect aspect = WidescreenAspect.parse(getString(SonicConfiguration.DISPLAY_ASPECT));
		if (getBoolean(SonicConfiguration.TEST_MODE_ENABLED)) {
			if (aspect != WidescreenAspect.NATIVE_4_3) {
				LOGGER.info("TEST_MODE_ENABLED: forcing DISPLAY_ASPECT to NATIVE_4_3 (320x224) for this run.");
			}
			aspect = WidescreenAspect.NATIVE_4_3;
		}
		boolean autosize = getBoolean(SonicConfiguration.DISPLAY_WINDOW_AUTOSIZE);
		int currentWindowW = persistedInt(SonicConfiguration.SCREEN_WIDTH, 640);
		int currentWindowH = persistedInt(SonicConfiguration.SCREEN_HEIGHT, 448);
		DisplayWindowPolicy.Resolved resolved =
				DisplayWindowPolicy.resolve(aspect, autosize, currentWindowW, currentWindowH);
		// Pixel dimensions are always derived; height pixels are always 224 (the
		// aspect system never changes vertical resolution, so any persisted
		// SCREEN_HEIGHT_PIXELS is intentionally superseded).
		transientResolved.put(SonicConfiguration.SCREEN_WIDTH_PIXELS.name(), resolved.pixelWidth());
		transientResolved.put(SonicConfiguration.SCREEN_HEIGHT_PIXELS.name(), 224);
		if (resolved.windowWidth() != currentWindowW || resolved.windowHeight() != currentWindowH) {
			// Widescreen preset with autosize derived a new window; overlay it.
			transientResolved.put(SonicConfiguration.SCREEN_WIDTH.name(), resolved.windowWidth());
			transientResolved.put(SonicConfiguration.SCREEN_HEIGHT.name(), resolved.windowHeight());
			LOGGER.info("Display aspect " + aspect + " -> " + resolved.pixelWidth() + "x224, window "
					+ resolved.windowWidth() + "x" + resolved.windowHeight()
					+ " (in-memory only); set DISPLAY_WINDOW_AUTOSIZE=false to keep a custom window.");
		} else {
			// Window unchanged (NATIVE, autosize off, or already matching): clear any
			// stale derived window so reads fall through to the persisted config.
			transientResolved.remove(SonicConfiguration.SCREEN_WIDTH.name());
			transientResolved.remove(SonicConfiguration.SCREEN_HEIGHT.name());
			if (aspect != WidescreenAspect.NATIVE_4_3) {
				LOGGER.info("Display aspect " + aspect + " -> " + resolved.pixelWidth()
						+ "x224 (window preserved).");
			}
		}
	}

	/** Reads an int from the persisted {@code config} map only, bypassing the transient overlay. */
	private int persistedInt(SonicConfiguration key, int fallback) {
		Object v = (config != null) ? config.get(key.name()) : null;
		if (v instanceof Number n) {
			return n.intValue();
		}
		if (v != null) {
			try {
				return Integer.parseInt(v.toString());
			} catch (NumberFormatException ignored) {
				// fall through
			}
		}
		return fallback;
	}

	public void setConfigValue(SonicConfiguration key, Object value) {
		if (config == null) {
			config = new HashMap<>();
		}
		config.put(key.name(), value);
	}

	public void saveConfig() {
		ObjectMapper mapper = new ObjectMapper();
		File target = resolveConfigFile();
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(target, config);
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to save config.json", e);
		}
	}

	public void ensureConfigFileExists() {
		File target = resolveConfigFile();
		if (!target.exists()) {
			saveConfig();
		}
	}

	public synchronized static SonicConfigurationService getInstance() {
		if (sonicConfigurationService == null) {
			sonicConfigurationService = new SonicConfigurationService();
		}
		return sonicConfigurationService;
	}

	/**
	 * Resets the singleton instance. Used by tests that need a fresh
	 * configuration with defaults re-applied.
	 */
	static void resetStaticInstance() {
		sonicConfigurationService = null;
	}

	public void resetToDefaults() {
		config = new HashMap<>();
		defaults = new HashMap<>();
		applyDefaults();
		// Re-derive SCREEN_WIDTH_PIXELS (and related) from the freshly-set
		// DISPLAY_ASPECT=NATIVE_4_3 default so any widescreen value left in
		// transientResolved from the singleton constructor is discarded.
		// Without this call a developer's ULTRA_21_9 config.json would leave
		// SCREEN_WIDTH_PIXELS=528 in the overlay even after the test harness
		// calls resetToDefaults(), silently widening trace and headless test runs.
		resolveDisplayAspect();
	}

	private boolean applyDefaults() {
		if (config == null) {
			config = new HashMap<>();
		}
		defaultInsertedSinceLastApply = false;
		// Fill in core defaults if missing to keep tests and headless runs stable.
		putDefault(SonicConfiguration.SCREEN_WIDTH, 640);
		putDefault(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
		putDefault(SonicConfiguration.SCREEN_HEIGHT, 448);
		putDefault(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
		putDefault(SonicConfiguration.SCALE, 1.0);
		// Debug view now eagerly initialized in Engine.init() to avoid macOS freeze
		putDefault(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
		putDefault(SonicConfiguration.EDITOR_ENABLED, false);
		putDefault(SonicConfiguration.DEBUG_COLLISION_VIEW_ENABLED, false);
		putDefault(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
		putDefaultKey(SonicConfiguration.DISPLAY_COLOR_PROFILE_TOGGLE_KEY, GLFW_KEY_V);
		putDefault(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
		putDefault(SonicConfiguration.WIDESCREEN_DEADZONE_MODE, "PROPORTIONAL");
		putDefault(SonicConfiguration.DISPLAY_WINDOW_AUTOSIZE, true);
		putDefault(SonicConfiguration.DAC_INTERPOLATE, true);
		putDefault(SonicConfiguration.FM6_DAC_OFF, true); // Default true for Sonic 2 parity
		putDefault(SonicConfiguration.AUDIO_ENABLED, true);
		putDefault(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT, false);
		putDefault(SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE, true);
		putDefault(SonicConfiguration.REGION, "NTSC");
		putDefaultKey(SonicConfiguration.UP, GLFW_KEY_UP);
		putDefaultKey(SonicConfiguration.DOWN, GLFW_KEY_DOWN);
		putDefaultKey(SonicConfiguration.LEFT, GLFW_KEY_LEFT);
		putDefaultKey(SonicConfiguration.RIGHT, GLFW_KEY_RIGHT);
		putDefaultKey(SonicConfiguration.JUMP, GLFW_KEY_SPACE);
		putDefaultKey(SonicConfiguration.P2_UP, GLFW_KEY_I);
		putDefaultKey(SonicConfiguration.P2_DOWN, GLFW_KEY_K);
		putDefaultKey(SonicConfiguration.P2_LEFT, GLFW_KEY_J);
		putDefaultKey(SonicConfiguration.P2_RIGHT, GLFW_KEY_L);
		putDefaultKey(SonicConfiguration.P2_JUMP, GLFW_KEY_RIGHT_SHIFT);
		putDefaultKey(SonicConfiguration.P2_START, GLFW_KEY_ENTER);
		putDefaultKey(SonicConfiguration.TEST, GLFW_KEY_T);
		putDefaultKey(SonicConfiguration.NEXT_ACT, GLFW_KEY_PAGE_UP);
		putDefaultKey(SonicConfiguration.NEXT_ZONE, GLFW_KEY_PAGE_DOWN);
		putDefaultKey(SonicConfiguration.DEBUG_MODE_KEY, GLFW_KEY_D);
		putDefault(SonicConfiguration.FPS, 60);
		putDefaultKey(SonicConfiguration.SPECIAL_STAGE_KEY, GLFW_KEY_TAB);
		putDefaultKey(SonicConfiguration.SPECIAL_STAGE_COMPLETE_KEY, GLFW_KEY_END);
		putDefaultKey(SonicConfiguration.SPECIAL_STAGE_FAIL_KEY, GLFW_KEY_DELETE);
		putDefaultKey(SonicConfiguration.SPECIAL_STAGE_SPRITE_DEBUG_KEY, GLFW_KEY_F12);
		putDefaultKey(SonicConfiguration.SPECIAL_STAGE_PLANE_DEBUG_KEY, GLFW_KEY_F3);
		putDefaultKey(SonicConfiguration.PAUSE_KEY, GLFW_KEY_ENTER);
		putDefaultKey(SonicConfiguration.FRAME_STEP_KEY, GLFW_KEY_Q);
		putDefault(SonicConfiguration.PLAYBACK_MOVIE_PATH, "");
		putDefaultKey(SonicConfiguration.PLAYBACK_TOGGLE_KEY, GLFW_KEY_B);
		putDefaultKey(SonicConfiguration.PLAYBACK_LOAD_KEY, GLFW_KEY_N);
		putDefaultKey(SonicConfiguration.PLAYBACK_PLAY_PAUSE_KEY, GLFW_KEY_M);
		putDefaultKey(SonicConfiguration.PLAYBACK_STEP_BACK_KEY, GLFW_KEY_COMMA);
		putDefaultKey(SonicConfiguration.PLAYBACK_STEP_FORWARD_KEY, GLFW_KEY_PERIOD);
		putDefaultKey(SonicConfiguration.PLAYBACK_JUMP_BACK_KEY, GLFW_KEY_LEFT_BRACKET);
		putDefaultKey(SonicConfiguration.PLAYBACK_JUMP_FORWARD_KEY, GLFW_KEY_RIGHT_BRACKET);
		putDefaultKey(SonicConfiguration.PLAYBACK_FAST_RATE_KEY, GLFW_KEY_SLASH);
		putDefaultKey(SonicConfiguration.PLAYBACK_RESET_TO_START_KEY, GLFW_KEY_BACKSLASH);
		putDefault(SonicConfiguration.PLAYBACK_START_OFFSET_FRAME, 0);
		putDefaultKey(SonicConfiguration.TRACE_REWIND_KEY, GLFW_KEY_R);
		putDefault(SonicConfiguration.LIVE_REWIND_ENABLED, false);
		putDefaultKey(SonicConfiguration.LIVE_REWIND_KEY, GLFW_KEY_R);
		putDefault(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED, false);
		putDefault(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ACCELERATION, 0.25);
		putDefault(SonicConfiguration.LIVE_REWIND_TAPE_COAST_DECELERATION, 0.5);
		putDefault(SonicConfiguration.LIVE_REWIND_TAPE_COAST_MAX_STEPS, 4.0);
		putDefault(SonicConfiguration.LIVE_REWIND_TAPE_COAST_MIN_STEPS, 0.25);
		putDefault(SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE, "time");
		putDefault(SonicConfiguration.REWIND_AUDIO_HISTORY_SECONDS, 60);
		putDefault(SonicConfiguration.REWIND_AUDIO_HISTORY_SIZE_MB, 10);
		putDefaultKey(SonicConfiguration.DEBUG_LAST_CHECKPOINT_KEY, GLFW_KEY_C);
		putDefaultKey(SonicConfiguration.LEVEL_SELECT_KEY, GLFW_KEY_F9);
		putDefault(SonicConfiguration.TITLE_SCREEN_ON_STARTUP, true);
		putDefault(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
		putDefault(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
		putDefault(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
		putDefault(SonicConfiguration.DATA_SELECT_EXTRA_PLAYER_COMBOS, "");
		putDefault(SonicConfiguration.SONIC_1_ROM, "Sonic The Hedgehog (W) (REV01) [!].gen");
		putDefault(SonicConfiguration.SONIC_2_ROM, "Sonic The Hedgehog 2 (W) (REV01) [!].gen");
		putDefault(SonicConfiguration.SONIC_3K_ROM, "Sonic and Knuckles & Sonic 3 (W) [!].gen");
		// Migrate renamed config key: S3K_SKIP_AIZ1_INTRO → S3K_SKIP_INTROS
		if (config.containsKey("S3K_SKIP_AIZ1_INTRO")) {
			if (!config.containsKey(SonicConfiguration.S3K_SKIP_INTROS.name())) {
				config.put(SonicConfiguration.S3K_SKIP_INTROS.name(), config.get("S3K_SKIP_AIZ1_INTRO"));
			}
			config.remove("S3K_SKIP_AIZ1_INTRO");
			defaultInsertedSinceLastApply = true;
		}
		putDefault(SonicConfiguration.S3K_SKIP_INTROS, false);
		putDefault(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
		putDefault(SonicConfiguration.DEFAULT_ROM, "s2");
		putDefaultKey(SonicConfiguration.SUPER_SONIC_DEBUG_KEY, GLFW_KEY_U);
		putDefaultKey(SonicConfiguration.GIVE_EMERALDS_KEY, GLFW_KEY_E);
		putDefault(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP, true);
		putDefault(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP, true);
		putDefault(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
		putDefault(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_OVERRIDE, false);
		putDefault(SonicConfiguration.CROSS_GAME_S2_DATA_SELECT_IMAGE_GEN_OVERRIDE, false);
		putDefaultKey(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY, GLFW_KEY_APOSTROPHE);
		putDefault(SonicConfiguration.CROSS_GAME_SOURCE, "s2");
		putDefault(SonicConfiguration.TEST_MODE_ENABLED, false);
		putDefault(SonicConfiguration.TRACE_CATALOG_DIR, "src/test/resources/traces");
		putDefault(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
		putDefault(SonicConfiguration.DISCORD_RICH_PRESENCE_SHOW_TIMER, true);
		putDefault(SonicConfiguration.DISCORD_RICH_PRESENCE_SHOW_ZONE, true);
		return defaultInsertedSinceLastApply;
	}

	private void putDefault(SonicConfiguration key, Object value) {
		if (config == null) {
			config = new HashMap<>();
		}
		if (!config.containsKey(key.name())) {
			config.put(key.name(), value);
			defaultInsertedSinceLastApply = true;
		}
		defaults.put(key.name(), value);
	}

	private void putDefaultKey(SonicConfiguration key, int glfwKeyCode) {
		putDefault(key, GlfwKeyNameResolver.nameOf(glfwKeyCode));
	}

	/**
	 * Finds config.json next to the native image executable binary.
	 * Uses ProcessHandle to determine the executable path.
	 */
	private static File findConfigNextToExecutable() {
		try {
			String cmd = ProcessHandle.current().info().command().orElse("");
			if (!cmd.isEmpty()) {
				File execDir = new File(cmd).getParentFile();
				if (execDir != null) {
					return new File(execDir, "config.json");
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	/**
	 * Resolves a relative filename against user.dir. In GraalVM native images
	 * launched from macOS Finder, getcwd() is broken so File("relative") may
	 * resolve against the wrong directory. This ensures consistent behavior.
	 */
	private static File resolveRelativeFile(String name) {
		File f = new File(name);
		if (!f.isAbsolute()) {
			String userDir = System.getProperty("user.dir");
			if (userDir != null) {
				return new File(userDir, name);
			}
		}
		return f;
	}

	private static File resolveConfigFile() {
		if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
			File execConfig = findConfigNextToExecutable();
			return (execConfig != null) ? execConfig : resolveRelativeFile("config.json");
		}
		return resolveRelativeFile("config.json");
	}

	private static int resolveKeyCode(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value != null) {
			String str = value.toString();
			try {
				return Integer.parseInt(str);
			} catch (NumberFormatException ignored) {
			}
			OptionalInt resolved = GlfwKeyNameResolver.resolve(str);
			if (resolved.isPresent()) {
				return resolved.getAsInt();
			}
		}
		return -1;
	}
}
