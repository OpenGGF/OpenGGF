package com.openggf.game;

import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.game.preview.TitlePreviewProvider.Sequence;

import com.openggf.TraceSessionLauncher;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomImageCatalogue;
import com.openggf.data.RomIdentity;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.game.launch.HubProgressStore;
import com.openggf.graphics.PngTextureLoader;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.graphics.SolidColorTexture;
import com.openggf.ExternalFrameOrInputOwnership;
import com.openggf.game.session.EngineServices;
import com.openggf.game.playback.PlaybackBrowser;
import com.openggf.game.playback.PlaybackLibrary;
import com.openggf.game.playback.PlaybackLauncher;
import com.openggf.game.recording.menu.UserRecordingMenu;
import com.openggf.version.AppVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_M;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

/**
 * Master title screen shown on startup for game selection.
 * Runs before gameplay initialization, using host UI and ROM-backed preview textures.
 *
 * <p>The hub fits the native 320x224 viewport. The left pane preserves animated
 * ROM logos and their aspect ratio; the right pane exposes launch and engine actions.
 * Wider viewports expand the panes without changing the navigation model.
 */
public class MasterTitleScreen {

    private static final Logger LOGGER = Logger.getLogger(MasterTitleScreen.class.getName());
    static final int SCREEN_W = 320;
    private static final int SCREEN_H = 224;
    private static final int TITLE_LOGO_BASE_SCALE_NUMERATOR = 35;
    private static final int TITLE_LOGO_BASE_SCALE_DENOMINATOR = 100;
    private static final int TITLE_LOGO_SCALE_NUMERATOR = 9;
    private static final int TITLE_LOGO_SCALE_DENOMINATOR = 10;

    record PreviewLayout(int width, int height, float x, float y) {
    }

    public enum GameEntry {
        SONIC_1("Sonic The Hedgehog", "Sonic 1", "s1", SonicConfiguration.SONIC_1_ROM,
                "s1.gen"),
        SONIC_2("Sonic The Hedgehog 2", "Sonic 2", "s2", SonicConfiguration.SONIC_2_ROM,
                "s2.gen"),
        SONIC_3K("Sonic 3 & Knuckles", "Sonic 3K", "s3k", SonicConfiguration.SONIC_3K_ROM,
                "s3k.gen");

        public final String displayName;
        public final String menuLabel;
        public final String gameId;
        public final SonicConfiguration romConfigKey;
        public final String expectedRomFilename;

        GameEntry(String displayName,
                  String menuLabel,
                  String gameId,
                  SonicConfiguration romConfigKey,
                  String expectedRomFilename) {
            this.displayName = displayName;
            this.menuLabel = menuLabel;
            this.gameId = gameId;
            this.romConfigKey = romConfigKey;
            this.expectedRomFilename = expectedRomFilename;
        }

        public static GameEntry fromGameId(String gameId) {
            for (GameEntry entry : values()) {
                if (entry.gameId.equalsIgnoreCase(gameId)) {
                    return entry;
                }
            }
            throw new IllegalArgumentException("Unknown game id: " + gameId);
        }
    }

    public enum State {
        INACTIVE, FADE_IN, ACTIVE, ERROR_DISPLAY, CONFIRMING, EXITING
    }

    /** Host-owned feedback events emitted before any game ROM is selected. */
    public enum AudioCue {
        NAVIGATE("UI_NAVIGATE"),
        CONFIRM("UI_CONFIRM"),
        CANCEL("UI_CANCEL"),
        ERROR("UI_ERROR");

        private final String sfxName;

        AudioCue(String sfxName) {
            this.sfxName = sfxName;
        }

        public String sfxName() {
            return sfxName;
        }
    }

    @FunctionalInterface
    public interface AudioSink {
        AudioSink NO_OP = cue -> { };

        void play(AudioCue cue);
    }

    // Cloud sprite for parallax animation
    private static class CloudSprite {
        int textureId;
        float x;
        float y;
        float speed;
        int width;
        int height;

        CloudSprite(int textureId, float x, float y, float speed, int width, int height) {
            this.textureId = textureId;
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.width = width;
            this.height = height;
        }

        void update(int vpWidth) {
            x += speed;
            // Wrap when fully off the right edge
            if (x > vpWidth) {
                x = -width;
            }
            // Wrap when fully off the left edge (negative speed)
            if (x + width < 0) {
                x = vpWidth;
            }
        }
    }

    private State state = State.INACTIVE;
    private int selectedIndex = 1; // Default to Sonic 2
    private int frameCounter = 0;
    private String launchErrorTitle;
    private String launchErrorDetail;
    private boolean traceFailureAnnounced;

    private List<MasterTitleEntry> entries;
    private java.util.function.Supplier<List<MasterTitleEntry>> entrySource;
    private final Map<GameEntry, Boolean> stockAvailability = new EnumMap<>(GameEntry.class);
    private Map<GameEntry, List<String>> enabledModsByGame = Map.of();

    // GL resources
    private TexturedQuadRenderer renderer;
    private PixelFont font;
    private PlaybackBrowser playbackBrowser;
    private PlaybackBrowser.SelectionMemory playbackSelection = new PlaybackBrowser.SelectionMemory();
    private java.util.concurrent.Callable<PlaybackLibrary> playbackLibrarySource;
    private UserRecordingMenu.PlaybackStarter userRecordingPlaybackStarter =
            (entry, options) -> LOGGER.info("User recording playback callback not configured.");
    private int bgTextureId;
    private int solidWhiteTextureId; // 1x1 white texture for solid color overlays
    private int titleTextId;
    private int titleTextWidth, titleTextHeight;
    private int cloudLargeTextureId;
    private int cloudLargeWidth, cloudLargeHeight;
    private int cloudSmallTextureId;
    private int cloudSmallWidth, cloudSmallHeight;
    private final Map<GameEntry, RomPreviewState> romPreviews = new EnumMap<>(GameEntry.class);
    private int previewAnimationFrame = 0;

    private final List<CloudSprite> clouds = new ArrayList<>();
    private final SonicConfigurationService configService;
    private final LaunchProfileStore launchProfileStore;
    private final HubProgressStore hubProgress;
    private boolean helpShortcutHeld;
    private final AudioSink audioSink;
    private LaunchConfigPanel launchConfigPanel;
    private boolean programmaticSelection;
    private final TitleHubNavigation navigation = new TitleHubNavigation();
    private InputHandler menuInput;
    private EngineSettingsScreen settingsScreen;
    private float[] currentProjection;
    private boolean toolsOpen;
    private Runnable editorProjectsOpenHandler;
    Runnable contentImportOpenHandler;

    public void setEditorProjectsOpenHandler(Runnable handler) { editorProjectsOpenHandler = handler; }
    private MenuRomLocator romLocator;
    private MenuGameOnboarding onboarding;
    private HubMusicController hubMusic;
    private MenuLoadTask<?> catalogLoad;
    private java.util.function.Consumer<Object> catalogLoaded;
    private String catalogLoadingTitle;
    private final Map<GameEntry, String> previewSources = new EnumMap<>(GameEntry.class);
    /** User-supplied images as last scanned; rebuilt on initialise and after Settings apply. */
    private RomImageCatalogue romCatalogue;
    private int toolIndex;
    private boolean helpOpen;
    private boolean modsShortcutHeld;
    private boolean childInputPending;
    private Runnable modManagerOpenHandler =
            () -> LOGGER.info("Mod manager open callback not configured.");
    private ModManagerScreenFactory modManagerScreenFactory;
    private Runnable modManagerApplyHandler = () -> { };
    private boolean modApplyPending;
    private ModManagerView modManagerScreen;

    /**
     * Projection width supplied by Engine each frame. Defaults to SCREEN_W (320)
     * so the screen behaves identically to native when no explicit width is set.
     * At widescreen (e.g. 400, 528) the background/clouds expand to fill the full
     * width while foreground elements stay centered.
     */
    private int viewportWidth = SCREEN_W;

    private boolean gameSelected = false;
    private MasterTitleEntry.Launch selectedLaunch;
    private boolean standaloneActionOpen;
    private List<String> titleEntries = List.of();
    private java.util.function.ObjIntConsumer<List<String>> titleEntryOpener = (games, index) -> { };
    private boolean extrasOpen;
    private int extrasIndex;
    private int standaloneActionIndex;

    /** The game suspended with a held Escape: first on the shelf until it is resumed or ended. */
    private MasterTitleEntry.Suspended suspended;
    private Image suspendedImage;
    private int suspendedTextureId;
    private Image suspendFrame;
    private int suspendFrameTextureId;
    static final int SUSPEND_ZOOM_TICKS = 32;
    private int suspendZoomTicks = SUSPEND_ZOOM_TICKS;
    private boolean resumeZooming;
    private boolean resumeZoomFinished;
    private Runnable suspendedGameEndHandler = () -> { };
    private boolean resumeRequested;
    /** Asks before anything that would end the suspended game; {@link #endPromptThen} runs after. */
    private boolean endPrompt;
    private boolean endSelected;
    private String endPromptDetail;
    private Runnable endPromptThen;

    private static final class RomPreviewState {
        int textureId;
        int width;
        int height;
        int frameToken = Integer.MIN_VALUE;
        Sequence sequence;
    }

    public MasterTitleScreen() {
        this(GameServices.configuration());
    }

    private static List<MasterTitleEntry> defaultEntries() {
        return java.util.Arrays.stream(GameEntry.values())
                .map(game -> (MasterTitleEntry) new MasterTitleEntry.Stock(game)).toList();
    }

    public MasterTitleScreen(SonicConfigurationService configService) {
        this(configService, new LaunchProfileStore(configService));
    }

    public MasterTitleScreen(SonicConfigurationService configService,
                             LaunchProfileStore launchProfileStore) {
        this(configService, launchProfileStore, defaultEntries(), AudioSink.NO_OP);
    }

    public MasterTitleScreen(SonicConfigurationService configService,
                             LaunchProfileStore launchProfileStore,
                             AudioSink audioSink) {
        this(configService, launchProfileStore, defaultEntries(), audioSink);
    }

    public MasterTitleScreen(SonicConfigurationService configService,
                             LaunchProfileStore launchProfileStore,
                             List<MasterTitleEntry> entries) {
        this(configService, launchProfileStore, entries, AudioSink.NO_OP);
    }

    public MasterTitleScreen(SonicConfigurationService configService,
                             LaunchProfileStore launchProfileStore,
                             List<MasterTitleEntry> entries,
                             AudioSink audioSink) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.launchProfileStore = Objects.requireNonNull(launchProfileStore, "launchProfileStore");
        this.hubProgress = new HubProgressStore(configService);
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        this.audioSink = Objects.requireNonNull(audioSink, "audioSink");
        this.selectedIndex = rememberedSelectionIndex(this.entries);
        for (GameEntry game : GameEntry.values()) stockAvailability.put(game, false);
    }

    public void initialize() {
        refreshStockAvailability();

        try {
            // Initialize renderer and font
            renderer = new TexturedQuadRenderer();
            renderer.init();

            font = new com.openggf.graphics.MenuPixelFont();
            font.init("pixel-font.png", renderer);

            // Load background
            bgTextureId = PngTextureLoader.loadTexture("titlescreen/bg.png");

            // Create 1x1 solid white texture for overlays
            solidWhiteTextureId = SolidColorTexture.createWhite();

            // Load title text
            titleTextId = PngTextureLoader.loadTexture("titlescreen/titletext.png");
            titleTextWidth = PngTextureLoader.getLastWidth();
            titleTextHeight = PngTextureLoader.getLastHeight();

            // Load cloud textures
            cloudLargeTextureId = PngTextureLoader.loadTexture("titlescreen/cloud-l.png");
            cloudLargeWidth = PngTextureLoader.getLastWidth();
            cloudLargeHeight = PngTextureLoader.getLastHeight();

            cloudSmallTextureId = PngTextureLoader.loadTexture("titlescreen/cloud-s.png");
            cloudSmallWidth = PngTextureLoader.getLastWidth();
            cloudSmallHeight = PngTextureLoader.getLastHeight();

            // Randomly generate clouds with varied positions and speeds
            Random rng = new Random();
            int cloudCount = 5 + rng.nextInt(3); // 5-7 clouds
            for (int i = 0; i < cloudCount; i++) {
                boolean large = rng.nextBoolean();
                int texId = large ? cloudLargeTextureId : cloudSmallTextureId;
                int cw = large ? cloudLargeWidth : cloudSmallWidth;
                int ch = large ? cloudLargeHeight : cloudSmallHeight;
                float x = rng.nextFloat() * (SCREEN_W + cw) - cw;
                float y = 55 + rng.nextFloat() * 100; // y range 55-155
                float speed = 0.08f + rng.nextFloat() * 0.4f; // speed 0.08-0.48
                clouds.add(new CloudSprite(texId, x, y, speed, cw, ch));
            }

            loadRomPreviews();
            resetRomPreviewTextureFrames();
            hubMusic = new HubMusicController(configService);
            if (stockAvailability.values().stream().noneMatch(Boolean.TRUE::equals))
                onboarding = new MenuGameOnboarding(configService);

            state = State.FADE_IN;
            LOGGER.info("Master title screen initialized");

        } catch (IOException e) {
            LOGGER.severe("Failed to initialize master title screen: " + e.getMessage());
            throw new RuntimeException("Failed to initialize master title screen", e);
        }
    }

    /**
     * Updates the title screen state. Called once per frame from GameLoop.
     */
    public void update(InputHandler inputHandler) {
        MenuFeedback.withSink(cue -> audioSink.play(AudioCue.valueOf(cue.name())),
                () -> updateMenu(inputHandler));
        hubProgress.update();
    }

    private void updateMenu(InputHandler inputHandler) {
        menuInput = inputHandler;
        navigation.capture(inputHandler);
        pointer = readPointer(inputHandler);
        frameCounter++;
        if (hubMusic != null) hubMusic.update(getSelectedGameId(), settingsScreen != null || helpOpen || toolsOpen
                || romLocator != null || onboarding != null || quitPrompt || endPrompt || state != State.ACTIVE);
        advancePreviewAnimationFrame();
        advanceHubAnimation();
        if (resumeZooming) {
            if (suspendZoomTicks > 0) suspendZoomTicks--;
            else if (!resumeZoomFinished) {
                // Hold the full-size picture for a rendered frame before handing back control.
                resumeZoomFinished = true;
                resumeRequested = true;
            }
            return;
        }
        if (suspendZoomTicks < SUSPEND_ZOOM_TICKS) {
            suspendZoomTicks++;
            childInputPending = true;
            if (suspendZoomTicks == SUSPEND_ZOOM_TICKS) releaseSuspendTexture();
            return;
        }

        // Update cloud animation
        for (CloudSprite cloud : clouds) {
            cloud.update(viewportWidth);
        }

        if (state == State.FADE_IN) {
            // Transition to active after a brief delay
            if (frameCounter > 10) {
                state = State.ACTIVE;
            }
            return;
        }

        if (state == State.ERROR_DISPLAY) {
            if (navigation.back() || navigation.accept() || clicked() || backClicked()) {
                state = State.ACTIVE;
                playCancelSound();
            }
            return;
        }

        if (state == State.CONFIRMING || state == State.EXITING) {
            return; // Waiting for fade
        }

        if (state != State.ACTIVE) {
            return;
        }

        if (childInputPending) {
            if (MenuInput.accept(inputHandler) || MenuInput.back(inputHandler) || MenuInput.up(inputHandler)
                    || MenuInput.down(inputHandler) || MenuInput.left(inputHandler) || MenuInput.right(inputHandler)) return;
            childInputPending = false;
        }
        if (catalogLoad != null) { updateCatalogLoad(); return; }
        if (onboarding != null) {
            if (pointer != null) onboarding.pointer(pointer.x(), pointer.y(), pointer.moved(), pointer.click(),
                    pointer.backClick(), pointer.wheel());
            onboarding.update(inputHandler);
            if (onboarding.completed()) { refreshRomPreviews(); onboarding = null; childInputPending = true; }
            else if (onboarding.cancelled()) { onboarding = null; childInputPending = true; }
            return;
        }
        if (romLocator != null) {
            if (pointer != null) romLocator.pointer(pointer.x(), pointer.y(), pointer.moved(),
                    pointer.click(), pointer.backClick(), pointer.wheel());
            romLocator.update(inputHandler);
            if (romLocator.completed()) { refreshRomPreviews(); romLocator = null; }
            else if (romLocator.cancelled()) romLocator = null;
            return;
        }
        if (quitPrompt) { updateQuitPrompt(); return; }
        if (endPrompt) { updateEndPrompt(); return; }
        if (settingsScreen != null) {
            settingsScreen.update(inputHandler);
            if (settingsScreen.consumeApplied()) refreshRomPreviews();
            if (settingsScreen.consumeCloseRequested()) settingsScreen = null;
            return;
        }
        if (helpOpen) {
            if (navigation.back() || backClicked() || clicked()) { helpOpen = false; playCancelSound(); }
            return;
        }
        if (toolsOpen) {
            updateTools();
            return;
        }
        if (modManagerScreen != null) {
            ModManagerView menu = modManagerScreen;
            // The manager draws as a centred 320-pixel page (drawNativePage).
            int margin = Math.max(0, (viewportWidth - SCREEN_W) / 2);
            if (pointer != null) {
                menu.pointer(pointer.x() - margin, pointer.y(), pointer.moved(), pointer.click(),
                        pointer.backClick(), pointer.wheel());
            }
            menu.update(inputHandler);
            if (menu.consumeActivationRequested()) {
                modApplyPending = true;
                return;
            }
            if (modManagerScreen == menu && menu.consumeCloseRequested()) {
                modManagerScreen = null;
                if (entrySource != null) replaceEntries(entrySource.get());
                if (launchConfigPanel != null && selectedEntry() instanceof MasterTitleEntry.Stock stock) {
                    launchConfigPanel.setEnabledMods(enabledModsByGame.getOrDefault(stock.game(), List.of()));
                    launchConfigPanel.resumeAfterMods();
                    childInputPending = true;
                }
            }
            return;
        }

        if (playbackBrowser != null) {
            PlaybackBrowser browser = playbackBrowser;
            browser.update(inputHandler);
            playbackSelection.remember(browser);
            switch (browser.consumeResult()) {
                case BACK -> {
                    configService.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
                    playbackBrowser = null;
                    childInputPending = true;
                }
                case LAUNCHED -> playbackBrowser = null;
                case NONE -> { }
            }
            return;
        }
        if (configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)) {
            openPlaybackBrowser("", PlaybackBrowser.Filter.TRACES, true);
            return;
        }

        if (launchConfigPanel != null) {
            launchConfigPanel.update(inputHandler);
            LaunchConfigPanel.Result result = launchConfigPanel.consumeResult();
            if (result == LaunchConfigPanel.Result.CANCELLED) {
                launchConfigPanel = null;
            } else if (result == LaunchConfigPanel.Result.OPEN_MODS) {
                // Game Setup stays open underneath; it resumes when the mod manager closes.
                openModManager(selectedEntry().gameId());
                // A host without an in-title manager screen opens it elsewhere: resume at once.
                if (modManagerScreen == null) launchConfigPanel.resumeAfterMods();
            } else if (result == LaunchConfigPanel.Result.CLOSED) {
                MasterTitleEntry.Stock stock = stockOf(selectedEntry());
                try {
                    launchProfileStore.save(stock.game(), launchConfigPanel.currentProfile());
                    launchConfigPanel = null;
                    playConfirmSound();
                } catch (java.io.UncheckedIOException failure) {
                    LOGGER.warning("Could not save launch profile: " + failure.getMessage());
                    launchConfigPanel.saveFailed();
                    playErrorSound();
                }
            }
            return;
        }

        if (standaloneActionOpen) {
            updateStandaloneActionChooser(inputHandler);
            return;
        }
        if (extrasOpen) {
            updateExtrasChooser();
            return;
        }

        boolean helpShortcut = MenuInput.details(inputHandler);
        boolean openHelp = helpShortcut && !helpShortcutHeld;
        helpShortcutHeld = helpShortcut;
        if (openHelp) { helpOpen = true; childInputPending = true; playConfirmSound(); return; }

        // Shortcuts remain optional; every action is also in the visible menu.
        boolean modsShortcut = inputHandler.isKeyPressedWithoutModifiers(GLFW_KEY_M);
        boolean openMods = modsShortcut && !modsShortcutHeld;
        modsShortcutHeld = modsShortcut;
        if (openMods) {
            openModManager(navigation.zone() == TitleHubNavigation.Zone.TABS ? null : getSelectedGameId());
            return;
        }
        int recordKey = configService.getInt(SonicConfiguration.RECORDING_RECORD_KEY);
        boolean recordingRequest = inputHandler.isKeyPressed(recordKey) && inputHandler.isShiftDown();
        if (recordingRequest) {
            openRecordings();
            return;
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_TAB) || inputHandler.isGamepadBackButtonPressed()) {
            if (!openLaunchOptions(null)) showActionError("Game setup");
            return;
        }

        refreshHubRows();
        if (updateHubPointer()) return;
        TitleHubNavigation.Zone zone = navigation.zone();
        if (navigation.up()) { if (navigation.moveRow(-1)) playNavigateSound(); return; }
        if (navigation.down()) { if (navigation.moveRow(1)) playNavigateSound(); return; }
        if (navigation.back()) {
            if (zone == TitleHubNavigation.Zone.CARD) openQuitPrompt();
            else { navigation.focus(TitleHubNavigation.Zone.CARD); playCancelSound(); }
            return;
        }
        if (navigation.left() || navigation.right()) {
            int delta = navigation.left() ? -1 : 1;
            boolean moved = zone == TitleHubNavigation.Zone.CARD ? cycleSelectedEntry(delta) : navigation.moveWithin(delta);
            if (moved) playNavigateSound();
            return;
        }
        if (!navigation.accept()) return;
        switch (zone) {
            case CARD -> startSelectedEntry();
            case ACTIONS -> runHubAction(focusedAction());
            case TABS -> runHubTab(navigation.tab());
        }
    }

    private void runHubAction(TitleHubNavigation.Action action) {
        switch (action) {
            case START -> startSelectedEntry();
            case SETUP -> { if (!openLaunchOptions(null)) showActionError("Game setup"); }
            case RECORDINGS -> openRecordings();
            case RESUME -> requestResume();
            case END -> askToEndSuspended("Its progress will be lost", () -> { });
        }
    }

    /** The action under focus, from the selected card's own list (a paused game has its own). */
    private TitleHubNavigation.Action focusedAction() {
        List<TitleHubNavigation.Action> actions = hubActions();
        return actions.get(Math.clamp(navigation.actionIndex(), 0, actions.size() - 1));
    }

    /** Playback from Recordings starts a session, so a paused game is ended first. */
    private void openRecordings() {
        if (askToEndSuspended("Opening playback ends it", this::openRecordings)) return;
        if (!tryOpenUserRecordingMenuForSelectedGame()) showActionError("Playback");
    }

    private void runHubTab(TitleHubNavigation.Tab tab) {
        switch (tab) {
            case MODS -> openModManager();
            case SETTINGS -> openSettings();
            case EXTRAS -> openExtras();
            case ADVANCED -> { toolsOpen = true; toolIndex = 0; playConfirmSound(); }
            case QUIT -> openQuitPrompt();
        }
    }

    /**
     * Mod-contributed master-title entries (from enabled mods' latest registration pass) and
     * how to open one; the opener receives the games whose ROMs are present.
     */
    public void setTitleEntries(List<String> labels, java.util.function.ObjIntConsumer<List<String>> opener) {
        titleEntries = List.copyOf(labels);
        titleEntryOpener = Objects.requireNonNull(opener, "opener");
        navigation.setExtrasVisible(!titleEntries.isEmpty());
    }

    private void openExtras() {
        if (titleEntries.isEmpty()) {
            showActionError("Extras");
        } else {
            extrasOpen = true;
            extrasIndex = 0;
            playConfirmSound();
        }
    }

    private void openTitleEntry(int index) {
        if (askToEndSuspended("Opening extras ends it", () -> openTitleEntry(index))) return;
        List<String> games = entries.stream()
                .filter(MasterTitleEntry.Stock.class::isInstance)
                .map(MasterTitleEntry.Stock.class::cast)
                .filter(this::isEntryAvailable)
                .map(stock -> stock.game().gameId)
                .toList();
        playConfirmSound();
        titleEntryOpener.accept(games, index);
    }

    private void updateExtrasChooser() {
        if (navigation.back() || backClicked()) { extrasOpen = false; playCancelSound(); return; }
        int previous = extrasIndex;
        if (navigation.up()) extrasIndex = Math.max(0, extrasIndex - 1);
        if (navigation.down()) extrasIndex = Math.min(titleEntries.size() - 1, extrasIndex + 1);
        boolean clicked = false;
        for (int i = 0; pointer != null && i < titleEntries.size(); i++) {
            if (pointer.over(viewportWidth / 2 - 90, 76 + i * 28, 180, 23) && (pointer.moved() || pointer.click())) {
                extrasIndex = i;
                clicked |= pointer.click();
            }
        }
        if (extrasIndex != previous) playNavigateSound();
        if (navigation.accept() || clicked) {
            extrasOpen = false;
            openTitleEntry(extrasIndex);
        }
    }

    /** This tick's mouse in menu pixels; null without a window or before the mouse is used. */
    private MasterTitlePointer pointer;
    private int pointerX = Integer.MIN_VALUE;
    private int pointerY = Integer.MIN_VALUE;
    /** Window point to {@code {x, y, inside}} menu pixels, or null; tests substitute their own. */
    private java.util.function.BiFunction<Double, Double, int[]> pointerMapper = (wx, wy) ->
            com.openggf.graphics.LogicalMouse.map(org.lwjgl.glfw.GLFW.glfwGetCurrentContext(),
                    GameServices.graphics(), wx, wy, viewportWidth, SCREEN_H);

    void setPointerMapperForTest(java.util.function.BiFunction<Double, Double, int[]> mapper) {
        pointerMapper = mapper;
    }

    boolean isRomLocatorOpenForTest() { return romLocator != null; }

    boolean isQuitPromptOpenForTest() {
        return quitPrompt;
    }

    boolean isEndPromptOpenForTest() {
        return endPrompt;
    }

    List<TitleHubNavigation.Action> hubActionsForTest() {
        return hubActions();
    }

    int selectedIndexForTest() {
        return selectedIndex;
    }

    private MasterTitlePointer readPointer(InputHandler input) {
        int wheel = com.openggf.control.MouseWheel.of(input).takeNotches();
        if (!input.hasMouseInputSeen()) return null;
        int[] p = pointerMapper.apply(input.getMouseX(), input.getMouseY());
        if (p == null || p[2] == 0) return null;
        boolean moved = p[0] != pointerX || p[1] != pointerY;
        pointerX = p[0];
        pointerY = p[1];
        return new MasterTitlePointer(p[0], p[1], moved, input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT),
                input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT), wheel);
    }

    private boolean clicked() {
        return pointer != null && pointer.click();
    }

    private boolean backClicked() {
        return pointer != null && pointer.backClick();
    }

    /**
     * Mouse on the hub: hovering a tab or action focuses it and clicking runs it (the setup
     * chips are status only);
     * side cards change game, the centre card starts it, the page dots open the game
     * browser, the wheel changes game and the right button goes back. Rectangles come
     * from {@link #hubLayout()}, the same geometry drawHub uses.
     */
    private boolean updateHubPointer() {
        if (pointer == null) return false;
        Rect help = helpHintBounds();
        if (pointer.click() && help != null && help.contains(pointer)) {
            helpOpen = true; childInputPending = true; playConfirmSound(); return true;
        }
        if (pointer.backClick()) {
            if (navigation.zone() != TitleHubNavigation.Zone.CARD) {
                navigation.focus(TitleHubNavigation.Zone.CARD);
                playCancelSound();
            } else openQuitPrompt();
            return true;
        }
        if (pointer.wheel() != 0) {
            if (cycleSelectedEntry(pointer.wheel() > 0 ? -1 : 1)) playNavigateSound();
            return true;
        }
        boolean hover = pointer.moved() || pointer.click();
        if (!hover) return false;
        HubLayout layout = hubLayout();
        for (int i = 0; i < layout.tabs().size(); i++) {
            if (!layout.tabs().get(i).contains(pointer)) continue;
            if (navigation.zone() != TitleHubNavigation.Zone.TABS || navigation.tabIndex() != i) {
                navigation.selectTab(i);
                playNavigateSound();
            }
            if (pointer.click()) runHubTab(navigation.tab());
            return pointer.click();
        }
        for (int i = 0; i < layout.actions().size(); i++) {
            if (!layout.actions().get(i).contains(pointer)) continue;
            if (navigation.zone() != TitleHubNavigation.Zone.ACTIONS || navigation.actionIndex() != i) {
                navigation.selectAction(i);
                playNavigateSound();
            }
            if (pointer.click()) runHubAction(focusedAction());
            return pointer.click();
        }
        for (CardSlot card : layout.cards()) {
            if (!card.rect().contains(pointer)) continue;
            if (card.offset() == 0) {
                if (navigation.zone() != TitleHubNavigation.Zone.CARD) {
                    navigation.focus(TitleHubNavigation.Zone.CARD);
                    playNavigateSound();
                }
                if (pointer.click()) startSelectedEntry();
                return pointer.click();
            }
            if (pointer.click()) {
                if (cycleSelectedEntry(card.offset())) playNavigateSound();
                return true;
            }
            return false;
        }
        return false;
    }

    private void openPlaybackBrowser(String gameId, PlaybackBrowser.Filter filter, boolean traceOnly) {
        var identity = AppVersion.identity();
        var source = playbackLibrarySource != null ? playbackLibrarySource
                : (java.util.concurrent.Callable<PlaybackLibrary>) () -> {
                    Path root = Path.of(System.getProperty("user.dir", "."));
                    Path traces = root.resolve(configService.getString(SonicConfiguration.TRACE_CATALOG_DIR)).normalize();
                    String movie = configService.getString(SonicConfiguration.PLAYBACK_MOVIE_PATH);
                    Path moviePath = movie == null || movie.isBlank() ? null : root.resolve(movie).normalize();
                    return PlaybackLibrary.scan(root, traces, moviePath, identity);
                };
        loadCatalog("PLAYBACK", source, library -> {
            if (traceOnly && !library.issues().isEmpty()) {
                configService.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
            }
            var launcher = new PlaybackLauncher(userRecordingPlaybackStarter, entry -> {
                boolean previousMode = configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED);
                boolean launched = false;
                try {
                    if (traceOnly) configService.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
                    launched = TraceSessionLauncher.launch(entry);
                    return launched;
                } finally {
                    if (!launched) configService.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, previousMode);
                }
            }, () -> ExternalFrameOrInputOwnership.active(EngineServices.current()),
                    () -> configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
            playbackBrowser = new PlaybackBrowser(library, gameId, filter, traceOnly, font, launcher);
            playbackSelection.restore(playbackBrowser);
            if (traceFailureAnnounced) {
                playbackBrowser.markFailureFeedbackAnnounced();
                traceFailureAnnounced = false;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T> void loadCatalog(String title, java.util.concurrent.Callable<T> read,
                                 java.util.function.Consumer<T> loaded) {
        cancelCatalogLoad();
        catalogLoadingTitle = title;
        catalogLoaded = value -> loaded.accept((T) value);
        catalogLoad = new MenuLoadTask<>(read);
    }

    private void cancelCatalogLoad() {
        if (catalogLoad != null) catalogLoad.cancel();
        catalogLoad = null;
        catalogLoaded = null;
    }

    private void updateCatalogLoad() {
        if (navigation.back()) {
            cancelCatalogLoad();
            configService.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
            playCancelSound();
            return;
        }
        if (!catalogLoad.done()) return;
        var completed = catalogLoad;
        var loaded = catalogLoaded;
        catalogLoad = null;
        catalogLoaded = null;
        try { loaded.accept(completed.result()); childInputPending = true; }
        catch (Exception failure) {
            configService.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            showUnavailableAction("Could not load catalog: " + cause.getMessage());
        }
    }

    private boolean quitPrompt;
    private boolean quitSelected;
    private boolean quitRequested;

    private void openQuitPrompt() {
        quitPrompt = true;
        quitSelected = false;
        playConfirmSound();
    }

    private void updateQuitPrompt() {
        if (navigation.back() || backClicked()) { quitPrompt = false; playCancelSound(); return; }
        if (pointer != null) {
            // "Stay in OpenGGF" and "Quit to desktop" rows as drawQuitPrompt lays them out.
            for (int option = 0; option < 2; option++) {
                if (!pointer.over(viewportWidth / 2 - 80, 92 + option * 26, 160, 20)) continue;
                boolean quit = option == 1;
                if (pointer.moved() && quitSelected != quit) { quitSelected = quit; playNavigateSound(); }
                if (pointer.click()) { quitSelected = quit; confirmQuitPrompt(); return; }
            }
        }
        if (navigation.left() || navigation.right() || navigation.up() || navigation.down()) {
            quitSelected = !quitSelected;
            playNavigateSound();
        } else if (navigation.accept()) {
            confirmQuitPrompt();
        }
    }

    private void confirmQuitPrompt() {
        quitPrompt = false;
        if (quitSelected) {
            hubProgress.flush();
            quitRequested = true;
            state = State.EXITING;
            playConfirmSound();
        } else playCancelSound();
    }

    boolean consumeQuitRequest() {
        boolean requested = quitRequested;
        quitRequested = false;
        return requested;
    }

    private void showActionError(String action) {
        if (selectedEntry() == null) { showUnavailableAction("No games are available. Check Mods and Settings."); return; }
        if (selectedEntry() instanceof MasterTitleEntry.Suspended paused) {
            showUnavailableAction(action + " is on the " + paused.menuLabel() + " card");
        } else if (selectedEntry() instanceof MasterTitleEntry.Stock stock && !isEntryAvailable(stock)) {
            launchErrorTitle = null;
            launchErrorDetail = null;
            state = State.ERROR_DISPLAY;
            playErrorSound();
        } else showUnavailableAction(selectedEntry() instanceof MasterTitleEntry.Standalone
                ? action + " is not available for standalone games"
                : "Could not open " + action.toLowerCase(java.util.Locale.ROOT));
    }

    /** Settings saved since start-up that only take effect after a restart. */
    private boolean restartPending() {
        return !com.openggf.configuration.PendingRestart.current(configService).isEmpty();
    }

    /** The stock game a card sets up: its own, or the one a paused card holds. */
    private static MasterTitleEntry.Stock stockOf(MasterTitleEntry entry) {
        return entry instanceof MasterTitleEntry.Suspended paused ? paused.game()
                : entry instanceof MasterTitleEntry.Stock stock ? stock : null;
    }

    /** Settings say which saved changes reach the paused game on resume. */
    private void markPausedGame(EngineSettingsScreen screen) {
        if (suspended != null) {
            boolean inLevel = suspended.inLevel();
            screen.setPausedGame(key -> com.openggf.game.launch.LiveSettings.appliesOnResume(key, inLevel));
        }
    }

    private void openSettings() {
        settingsScreen = new EngineSettingsScreen(configService, font);
        markPausedGame(settingsScreen);
        playConfirmSound();
        childInputPending = true;
    }

    private void updateTools() {
        if (navigation.back() || backClicked()) { toolsOpen = false; playCancelSound(); return; }
        int previous = toolIndex;
        if (navigation.up()) toolIndex = Math.max(0, toolIndex - 1);
        var tools = toolActions();
        int count = tools.size();
        if (navigation.down()) toolIndex = Math.min(count - 1, toolIndex + 1);
        boolean clickedTool = false;
        for (int i = 0; pointer != null && i < count; i++) {
            if (pointer.over(10, TOOL_TOP + i * TOOL_ROW, viewportWidth - 20, TOOL_ROW - 2) && (pointer.moved() || pointer.click())) {
                toolIndex = i;
                clickedTool |= pointer.click();
            }
        }
        if (toolIndex != previous) playNavigateSound();
        if (!navigation.accept() && !clickedTool) return;
        toolsOpen = false;
        tools.get(toolIndex).callback().run();
        playConfirmSound();
    }

    private record ToolAction(String label, String note, MenuChrome.Icon icon, Runnable callback) { }

    /** Advanced rows: icon, name and its one-line note, so the page reads without a floating caption. */
    private static final int TOOL_TOP = 42, TOOL_ROW = 24;

    private List<ToolAction> toolActions() {
        var actions = new ArrayList<ToolAction>();
        actions.add(new ToolAction("Playback browser", "Recordings, BK2 movies and ROM trace replays", MenuChrome.Icon.FILM,
                () -> openPlaybackBrowser("", PlaybackBrowser.Filter.ALL, false)));
        actions.replaceAll(this::endingSuspendedFirst);
        actions.add(new ToolAction("Advanced settings", "Debug, Discord and other expert settings", MenuChrome.Icon.GEAR, () -> {
            settingsScreen = new EngineSettingsScreen(configService, font, EngineSettingsScreen.Scope.ADVANCED);
            markPausedGame(settingsScreen);
            childInputPending = true;
        }));
        actions.add(new ToolAction("Controls / help", "How the menus and shortcuts work", MenuChrome.Icon.INFO, () -> helpOpen = true));
        if (editorProjectsOpenHandler != null) actions.add(endingSuspendedFirst(new ToolAction("Editor projects", "Create, open and edit level projects", MenuChrome.Icon.FOLDER, editorProjectsOpenHandler)));
        if (contentImportOpenHandler != null) actions.add(endingSuspendedFirst(new ToolAction("Import content", "Review and import projects or mod packages", MenuChrome.Icon.DOWNLOAD, contentImportOpenHandler)));
        return actions;
    }

    private ToolAction endingSuspendedFirst(ToolAction tool) {
        return new ToolAction(tool.label(), tool.note(), tool.icon(), () -> {
            if (!askToEndSuspended("Opening " + tool.label().toLowerCase(java.util.Locale.ROOT) + " ends it",
                    tool.callback())) tool.callback().run();
        });
    }

    /** Opens Game Setup for the selected stock game, optionally focused on one row. */
    private boolean openLaunchOptions(com.openggf.game.launch.LaunchProfile.Row focusRow) {
        MasterTitleEntry.Stock stock = stockOf(selectedEntry());
        if (stock == null || !isEntryAvailable(stock) && !(selectedEntry() instanceof MasterTitleEntry.Suspended)) return false;
        launchConfigPanel = new LaunchConfigPanel(stock.game(), launchProfileStore.load(stock.game()),
                launchProfileStore, configService, font, renderer);
        launchConfigPanel.setEnabledMods(enabledModsByGame.getOrDefault(stock.game(), List.of()));
        if (selectedEntry() instanceof MasterTitleEntry.Suspended paused) launchConfigPanel.setPausedGame(paused.inLevel());
        if (focusRow != null) launchConfigPanel.focus(focusRow);
        playConfirmSound();
        childInputPending = true;
        return true;
    }

    private void startSelectedEntry() {
        MasterTitleEntry entry = selectedEntry();
        if (entry == null) { showActionError("Start game"); return; }
        if (entry instanceof MasterTitleEntry.Suspended) { requestResume(); return; }
        if (isEntryAvailable(entry) && askToEndSuspended("Starting " + entry.menuLabel() + " ends it",
                this::startSelectedEntry)) return;
        if (!isEntryAvailable(entry) && entry instanceof MasterTitleEntry.Stock stock) {
            romLocator = new MenuRomLocator(stock.game(), configService);
            childInputPending = true;
            playConfirmSound();
        } else if (!isEntryAvailable(entry)) {
            launchErrorTitle = null;
            launchErrorDetail = null;
            state = State.ERROR_DISPLAY;
            playErrorSound();
        } else if (entry instanceof MasterTitleEntry.Standalone) {
            standaloneActionOpen = true;
            standaloneActionIndex = 0;
            playConfirmSound();
        } else confirmLaunch(new MasterTitleEntry.Launch(entry, MasterTitleEntry.Action.NEW_GAME), false);
    }

    private void showUnavailableAction(String detail) {
        launchErrorTitle = "ACTION UNAVAILABLE";
        launchErrorDetail = detail;
        state = State.ERROR_DISPLAY;
        playErrorSound();
    }

    /**
     * Rescans the user's images. Availability comes from the ROM catalogue
     * (size and header, never filename), so a game whose only image is a
     * lock-on dump or a pair of separate cartridges is offered like any other.
     */
    private void refreshStockAvailability() {
        romCatalogue = RomImageCatalogue.forCurrentWorkingDirectory(configService);
        for (GameEntry game : GameEntry.values()) {
            stockAvailability.put(game, romCatalogue.isAvailable(logicalRomFor(game)));
        }
    }

    private static RomIdentity logicalRomFor(GameEntry game) {
        return GameId.fromCode(game.gameId).romGame().identity();
    }

    private void refreshRomPreviews() {
        refreshStockAvailability();
        for (GameEntry game : GameEntry.values()) {
            Optional<RomImageCatalogue.Resolution> resolution = romCatalogue.resolve(logicalRomFor(game));
            boolean listed = entries.stream().anyMatch(entry -> entry instanceof MasterTitleEntry.Stock stock && stock.game() == game);
            String source = resolution.isPresent() && listed ? resolution.get().changeToken() : null;
            if (Objects.equals(source, previewSources.get(game))) continue;
            RomPreviewState removed = romPreviews.remove(game);
            if (removed != null) {
                PngTextureLoader.deleteTexture(removed.textureId);
            }
            previewSources.remove(game);
            if (renderer != null && source != null) loadRomPreview(game, resolution.get(), source);
        }
    }

    private void updateStandaloneActionChooser(InputHandler input) {
        List<MasterTitleEntry.Action> actions = standaloneActionsForTest();
        if (navigation.back() || backClicked()) { standaloneActionOpen = false; playCancelSound(); return; }
        int previous = standaloneActionIndex;
        if (navigation.up()) standaloneActionIndex = Math.max(0, standaloneActionIndex - 1);
        if (navigation.down()) standaloneActionIndex = Math.min(actions.size() - 1, standaloneActionIndex + 1);
        boolean clickedAction = false;
        for (int i = 0; pointer != null && i < actions.size(); i++) {
            if (pointer.over(viewportWidth / 2 - 90, 76 + i * 28, 180, 23) && (pointer.moved() || pointer.click())) {
                standaloneActionIndex = i;
                clickedAction |= pointer.click();
            }
        }
        if (standaloneActionIndex != previous) playNavigateSound();
        if (navigation.accept() || clickedAction) {
            confirmLaunch(new MasterTitleEntry.Launch(selectedEntry(), actions.get(standaloneActionIndex)), false);
            standaloneActionOpen = false;
        }
    }

    private void confirmLaunch(MasterTitleEntry.Launch launch, boolean programmatic) {
        if (!programmatic) hubProgress.rememberGame(launch.entry().gameId());
        hubProgress.flush();
        selectedLaunch = launch;
        state = State.CONFIRMING;
        if (!programmatic) playConfirmSound();
        programmaticSelection = programmatic;
        gameSelected = true;
    }

    /**
     * Draws the title screen. Called once per frame from Engine.draw().
     */
    public void draw() {
        if (modManagerScreen != null) {
            drawNativePage(modManagerScreen::render);
            return;
        }
        if (renderer == null) return;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        drawContent();
        drawSuspendZoom();
    }

    /**
     * Runs the title draw body without touching OpenGL blend state.
     * Package-private for command-recording tests that use recording renderers
     * without a live context.
     */
    void drawForRecording() {
        if (renderer != null) {
            drawContent();
        }
    }

    private void drawNativePage(Runnable page) {
        if (renderer == null || font == null) { page.run(); return; }
        int margin = Math.max(0, (viewportWidth - SCREEN_W) / 2);
        if (margin > 0) MenuChrome.widePageFrame(font, viewportWidth, margin);
        if (margin == 0 || currentProjection == null) { page.run(); return; }
        float[] centered = new org.joml.Matrix4f()
                .ortho2D(-margin, viewportWidth - margin, 0, SCREEN_H).get(new float[16]);
        renderer.setProjectionMatrix(centered);
        try { page.run(); }
        finally { renderer.setProjectionMatrix(currentProjection); }
    }

    private void drawContent() {
        if (catalogLoad != null) {
            MenuStyle.page(font, viewportWidth, catalogLoadingTitle, "Reading catalog...");
            MenuStyle.label(font, "Loading" + ".".repeat((frameCounter / 20) % 4), 14, 84, viewportWidth - 28, 1, 1, 1);
            MenuChrome.footer(font, viewportWidth, "READING FILES", List.of(MenuChrome.hint(MenuChrome.backKey(menuInput), "CANCEL")),
                    MenuChrome.controller(menuInput));
            return;
        }
        if (onboarding != null) { onboarding.render(font, viewportWidth); return; }
        if (romLocator != null) { romLocator.render(font, viewportWidth); return; }

        if (playbackBrowser != null) {
            drawNativePage(playbackBrowser::render);
            return;
        }

        if (settingsScreen != null) {
            settingsScreen.render(viewportWidth);
            return;
        }
        drawHub();
        if (quitPrompt) {
            drawQuitPrompt();
            return;
        }
        if (endPrompt) {
            drawChoicePrompt("END " + suspended.menuLabel().toUpperCase(java.util.Locale.ROOT) + " GAME?",
                    endPromptDetail, new String[] { "Keep it paused", "End game" }, endSelected, "BACK");
            return;
        }

        if (launchConfigPanel != null) {
            renderer.drawTexture(solidWhiteTextureId, 0, 0, viewportWidth, SCREEN_H,
                    0.02f, 0.07f, 0.23f, 1f);
            launchConfigPanel.render(viewportWidth);
            return;
        }

        if (state == State.ERROR_DISPLAY) {
            font.beginMegaBatch();
            MasterTitleEntry entry = selectedEntry();
            MenuStyle.page(font, viewportWidth, "UNABLE TO START", entry == null ? "No game selected" : entry.displayName());
            MenuStyle.panel(font, 9, 57, viewportWidth - 18, 132);
            MenuStyle.label(font, launchErrorTitle == null ? "ROM NOT FOUND" : launchErrorTitle,
                    17, 68, viewportWidth - 34, 1f, .35f, .35f);
            String detail = launchErrorDetail;
            if (detail == null && entry instanceof MasterTitleEntry.Stock stock)
                detail = configService.getString(stock.game().romConfigKey);
            if (detail == null || detail.isBlank()) detail = "No ROM path configured";
            // Paths have no spaces: break them wherever the measured width runs out.
            String rest = detail;
            for (int row = 0; !rest.isEmpty() && row < 5; row++) {
                int end = Math.max(MenuStyle.fittingPrefix(rest, viewportWidth - 34, MenuStyle.COMPACT),
                        Character.charCount(rest.codePointAt(0)));
                MenuStyle.text(font, rest.substring(0, end), 17, 96 + row * 12, viewportWidth - 34, .85f, .9f, 1);
                rest = rest.substring(end);
            }
            if (launchErrorTitle == null && entry instanceof MasterTitleEntry.Stock)
                MenuStyle.text(font, "Check Files in Engine Settings", 17, 172, viewportWidth - 34, 1, .78f, .3f);
            MenuChrome.footer(font, viewportWidth, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), "BACK TO GAMES")),
                    MenuChrome.controller(menuInput));
            font.endMegaBatch();
        }
    }

    private void drawQuitPrompt() {
        drawChoicePrompt("QUIT OPENGGF?", suspended == null ? "OpenGGF will close" : "Your paused game will end",
                new String[] { "Stay in OpenGGF", "Quit to desktop" }, quitSelected, "STAY");
    }

    /** Two-row confirmation over the dimmed hub; the second row is the consequential one. */
    private void drawChoicePrompt(String title, String detail, String[] options, boolean secondSelected, String backVerb) {
        int w = viewportWidth;
        font.beginMegaBatch();
        MenuChrome.fill(font, 0, 0, w, SCREEN_H, new float[] {0, 0, 0}, .62f);
        int bx = w / 2 - 96;
        MenuChrome.fill(font, bx, 56, 192, 92, MenuChrome.NAVY, .98f);
        MenuChrome.outline(font, bx, 56, 192, 92, MenuChrome.GOLD, 1f, 1);
        MenuChrome.label(font, title, (w - MenuChrome.labelWidth(title)) / 2, 66, MenuChrome.GOLD);
        detail = MenuChrome.fitCompact(detail, 184);
        MenuChrome.compact(font, detail, (w - MenuChrome.compactWidth(detail)) / 2, 80, MenuChrome.MUTED);
        for (int i = 0; i < 2; i++) {
            int y = 92 + i * 26;
            boolean sel = secondSelected == (i == 1);
            if (sel) MenuChrome.focus(font, w / 2 - 80, y, 160, 20, frameCounter);
            else MenuChrome.panel(font, w / 2 - 80, y, 160, 20);
            // The consequential choice keeps amber lettering; both use the shared button face.
            MenuChrome.compact(font, options[i], (w - MenuChrome.compactWidth(options[i])) / 2, y + 7,
                    i == 1 ? MenuChrome.AMBER : sel ? MenuChrome.WHITE : MenuChrome.TEXT);
        }
        MenuChrome.footer(font, w, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), "SELECT"),
                MenuChrome.hint(MenuChrome.backKey(menuInput), backVerb)), MenuChrome.controller(menuInput));
        font.endMegaBatch();
    }

    private String confirmHint() { return menuInput == null ? "Enter" : MenuInput.confirmLabel(menuInput); }
    private String backHint() { return menuInput == null ? "Esc" : MenuInput.backLabel(menuInput); }

    // -------------------------------------------------------------------------
    // Cartridge-shelf hub
    // -------------------------------------------------------------------------

    private static final int HUB_HEADER_H = 24;
    private static final int CARD_SPACING = 118;
    private static final int CARD_CENTER_Y = 72;
    private static final int CARD_W = 132, CARD_H = 84, SIDE_W = 100, SIDE_H = 64;
    private static final int NAME_Y = 118, CHIP_Y = 131, DOTS_Y = 148, ACTION_Y = 157, SUMMARY_Y = 183, LEGEND_Y = 193;

    /** Hit-test rectangle in menu pixels (top-left origin). */
    record Rect(int x, int y, int w, int h) {
        boolean contains(MasterTitlePointer p) {
            return p != null && p.over(x, y, w, h);
        }
    }

    /** A card on the shelf: its offset from the selected game and its eased geometry. */
    record CardSlot(int offset, int index, Rect rect, float distance) { }

    record HubLayout(List<Rect> tabs, List<CardSlot> cards, List<Rect> chips, List<Rect> actions, Rect dots) { }

    /** Sub-pixel carousel offset: eases to zero after each change of game. */
    private float slide;
    private int confirmTicks;

    /** Native-pixel layout; preview art keeps its source aspect ratio inside a card. */
    static PreviewLayout cardLogoLayout(int sourceWidth, int sourceHeight, int boxX, int boxY, int boxW, int boxH) {
        double scale = Math.min((double) boxW / Math.max(1, sourceWidth), (double) boxH / Math.max(1, sourceHeight));
        int width = Math.max(1, (int) Math.floor(sourceWidth * scale));
        int height = Math.max(1, (int) Math.floor(sourceHeight * scale));
        return new PreviewLayout(width, height, boxX + (boxW - width) / 2f,
                SCREEN_H - (boxY + (boxH - height) / 2f) - height);
    }

    private List<TitleHubNavigation.Action> hubActions() {
        if (selectedEntry() instanceof MasterTitleEntry.Suspended)
            return List.of(TitleHubNavigation.Action.RESUME, TitleHubNavigation.Action.SETUP, TitleHubNavigation.Action.END);
        return selectedEntry() instanceof MasterTitleEntry.Standalone
                ? List.of(TitleHubNavigation.Action.START)
                : List.of(TitleHubNavigation.Action.START, TitleHubNavigation.Action.SETUP,
                        TitleHubNavigation.Action.RECORDINGS);
    }

    /** Chips for the selected game, read from the saved profile so they never go stale. */
    List<HubSetupChips.Chip> hubChips() {
        MasterTitleEntry entry = selectedEntry();
        if (!(entry instanceof MasterTitleEntry.Stock stock) || !isEntryAvailable(stock)) return List.of();
        return HubSetupChips.forStock(stock.game(), launchProfileStore.load(stock.game()), launchProfileStore,
                configService, enabledModsByGame.getOrDefault(stock.game(), List.of()).size());
    }

    private void refreshHubRows() {
        navigation.setActionCount(hubActions().size());
    }

    List<HubSetupChips.Chip> visibleHubChips() {
        return HubSetupChips.fit(hubChips(), viewportWidth - 16);
    }

    private String tabLabel(TitleHubNavigation.Tab tab) {
        // Keep at least 64 pixels for branding when optional tabs expand the header.
        if (navigation.tabs().length > 4 && viewportWidth < 380) {
            if (tab == TitleHubNavigation.Tab.ADVANCED) return "MORE";
            if (tab == TitleHubNavigation.Tab.MODS) return "MODS";
        }
        if (tab == TitleHubNavigation.Tab.MODS) {
            long count = enabledModsByGame.values().stream().flatMap(List::stream).distinct().count();
            return count == 0 ? tab.label : tab.label + " " + count;
        }
        return tab.label;
    }

    private String actionLabel(TitleHubNavigation.Action action) {
        if (action == TitleHubNavigation.Action.START && selectedEntry() instanceof MasterTitleEntry.Stock stock) {
            if (!isEntryAvailable(stock)) return "LOCATE ROM";
        }
        return action.label;
    }

    private int actionWidth(TitleHubNavigation.Action action) {
        return primary(action) && !compactActions()
                ? MenuChrome.labelWidth(actionLabel(action)) + 16 + PRIMARY_ICON_ROOM : MenuChrome.compactWidth(actionLabel(action)) + 12;
    }

    private boolean compactActions() { return hubActions().size() > 3; }

    /** The full-size gold button leads with its verb's glyph: play to start or resume, a folder to locate. */
    private static final int PRIMARY_ICON_ROOM = MenuChrome.ICON_SIZE + 4;

    /** Start and Resume are the gold buttons. */
    private static boolean primary(TitleHubNavigation.Action action) {
        return action == TitleHubNavigation.Action.START || action == TitleHubNavigation.Action.RESUME;
    }

    HubLayout hubLayout() {
        int w = viewportWidth;
        List<Rect> tabs = new ArrayList<>();
        int x = w - 8;
        TitleHubNavigation.Tab[] visibleTabs = navigation.tabs();
        int[] tabX = new int[visibleTabs.length];
        for (int i = visibleTabs.length - 1; i >= 0; i--) {
            int tw = MenuChrome.compactWidth(tabLabel(visibleTabs[i])) + 10;
            x -= tw;
            tabX[i] = x;
            x -= 4;
        }
        for (int i = 0; i < visibleTabs.length; i++) {
            tabs.add(new Rect(tabX[i], 5, MenuChrome.compactWidth(tabLabel(visibleTabs[i])) + 10, 14));
        }

        List<CardSlot> cards = new ArrayList<>();
        int n = entries.size();
        if (n > 0) {
            int left = Math.min(2, (n - 1) / 2);
            int right = Math.min(2, n - 1 - (n - 1) / 2);
            // Draw far cards first so the selected card overlaps its neighbours.
            List<Integer> offsets = new ArrayList<>();
            for (int k = -left; k <= right; k++) offsets.add(k);
            offsets.sort((a, b) -> Float.compare(Math.abs(b * CARD_SPACING + slide), Math.abs(a * CARD_SPACING + slide)));
            for (int k : offsets) {
                float centre = w / 2f + k * CARD_SPACING + slide;
                float t = Math.min(1f, Math.abs(k * CARD_SPACING + slide) / CARD_SPACING);
                int cw = Math.round(CARD_W + (SIDE_W - CARD_W) * t);
                int ch = Math.round(CARD_H + (SIDE_H - CARD_H) * t);
                Rect rect = new Rect(Math.round(centre - cw / 2f), CARD_CENTER_Y - ch / 2, cw, ch);
                cards.add(new CardSlot(k, Math.floorMod(selectedIndex + k, n), rect, t));
            }
        }

        List<Rect> chips = new ArrayList<>();
        List<HubSetupChips.Chip> chipModels = visibleHubChips();
        int total = 0;
        for (HubSetupChips.Chip chip : chipModels) total += MenuChrome.chipWidth(chip.label()) + 4;
        int cx = (w - Math.max(0, total - 4)) / 2;
        for (HubSetupChips.Chip chip : chipModels) {
            int cwid = MenuChrome.chipWidth(chip.label());
            chips.add(new Rect(cx, CHIP_Y, cwid, 12));
            cx += cwid + 4;
        }

        List<Rect> actions = new ArrayList<>();
        List<TitleHubNavigation.Action> actionModels = hubActions();
        int atotal = 0;
        for (TitleHubNavigation.Action action : actionModels) atotal += actionWidth(action) + 4;
        int ax = (w - (atotal - 4)) / 2;
        for (TitleHubNavigation.Action action : actionModels) {
            actions.add(new Rect(ax, ACTION_Y, actionWidth(action), 20));
            ax += actionWidth(action) + 4;
        }
        int dotsWidth = Math.min(n, 12) * 8;
        Rect dots = new Rect((w - dotsWidth) / 2 - 4, DOTS_Y - 3, dotsWidth + 8, 10);
        return new HubLayout(List.copyOf(tabs), List.copyOf(cards), List.copyOf(chips), List.copyOf(actions), dots);
    }

    private void advanceHubAnimation() {
        slide *= 0.78f;
        if (Math.abs(slide) < 0.5f) slide = 0;
        if (state == State.CONFIRMING) confirmTicks++;
        else confirmTicks = 0;
    }

    private void drawHub() {
        int w = viewportWidth;
        renderer.drawTexture(bgTextureId, 0, 0, w, SCREEN_H);
        for (CloudSprite cloud : clouds) {
            renderer.drawTexture(cloud.textureId, cloud.x, SCREEN_H - cloud.y - cloud.height,
                    cloud.width, cloud.height, 1f, 1f, 1f, 0.65f);
        }
        if (toolsOpen || helpOpen) {
            drawToolsAndHelp();
            return;
        }
        if (extrasOpen) {
            drawChooser("EXTRAS", "Modes added by your mods", titleEntries.stream()
                    .map(e -> e.toUpperCase(java.util.Locale.ROOT)).toList(), extrasIndex, "OPEN");
            return;
        }
        if (standaloneActionOpen) {
            drawChooser("START GAME", selectedEntry().menuLabel(), standaloneActionsForTest().stream()
                    .map(a -> a == MasterTitleEntry.Action.NEW_GAME ? "NEW GAME" : "CONTINUE").toList(),
                    standaloneActionIndex, "START");
            return;
        }
        // Darken the sky slightly so cards and text keep their contrast over bright clouds.
        box(0, 0, w, SCREEN_H, 0.02f, 0.07f, 0.23f, 0.34f);
        drawTwinkles(w);
        HubLayout layout = hubLayout();
        drawHubHeader(w, layout);
        for (CardSlot card : layout.cards()) drawCard(card);
        drawHubDetails(w, layout);
    }

    private void drawHubHeader(int w, HubLayout layout) {
        font.beginMegaBatch();
        box(0, 0, w, HUB_HEADER_H, 0.04f, 0.14f, 0.36f, 1f);
        box(0, HUB_HEADER_H - 2, w, 1, 1f, 0.84f, 0.3f, 1f);
        box(0, HUB_HEADER_H - 1, w, 1, 0.4f, 0.27f, 0.02f, 1f);
        font.endMegaBatch();
        // Keep OpenGGF branding and ROM-decoded game logos, never mockup artwork.
        int room = layout.tabs().isEmpty() ? w - 16 : layout.tabs().getFirst().x() - 16;
        int logoH = 17;
        int logoW = titleTextHeight == 0 ? 1 : titleTextWidth * logoH / titleTextHeight;
        if (logoW > room) { logoW = Math.max(1, room); logoH = Math.max(1, titleTextHeight * logoW / Math.max(1, titleTextWidth)); }
        renderer.drawTexture(titleTextId, 8, SCREEN_H - 3 - logoH, logoW, logoH);
        font.beginMegaBatch();
        TitleHubNavigation.Tab[] tabs = navigation.tabs();
        for (int i = 0; i < tabs.length; i++) {
            Rect r = layout.tabs().get(i);
            boolean focused = navigation.zone() == TitleHubNavigation.Zone.TABS && navigation.tabIndex() == i;
            if (focused) MenuChrome.focus(font, r.x(), r.y(), r.w(), r.h(), frameCounter);
            else MenuChrome.panel(font, r.x(), r.y(), r.w(), r.h());
            String label = tabLabel(tabs[i]);
            boolean mods = tabs[i] == TitleHubNavigation.Tab.MODS && !label.equals(tabs[i].label);
            MenuChrome.compact(font, label, r.x() + 5, r.y() + 3, focused ? MenuChrome.WHITE
                    : mods ? MenuChrome.CYAN : MenuChrome.TEXT);
        }
        font.endMegaBatch();
    }

    private void drawTwinkles(int w) {
        font.beginMegaBatch();
        for (int i = 0; i < 9; i++) {
            int phase = Math.floorMod(frameCounter + i * 53, 150);
            if (phase >= 30) continue;
            float a = phase < 15 ? phase / 15f : (30 - phase) / 15f;
            int cycle = Math.floorDiv(frameCounter + i * 53, 150);
            int x = Math.floorMod(i * 97 + cycle * 61 + 23, Math.max(1, w - 16)) + 8;
            int y = HUB_HEADER_H + 6 + Math.floorMod(i * 41 + cycle * 29, 96);
            MenuChrome.fill(font, x, y, 1, 1, MenuChrome.WHITE, a);
            if (a > .5f) {
                MenuChrome.fill(font, x - 1, y, 3, 1, MenuChrome.WHITE, a * .6f);
                MenuChrome.fill(font, x, y - 1, 1, 3, MenuChrome.WHITE, a * .6f);
            }
            if (a > .85f) {
                MenuChrome.fill(font, x - 2, y, 5, 1, MenuChrome.WHITE, a * .25f);
                MenuChrome.fill(font, x, y - 2, 1, 5, MenuChrome.WHITE, a * .25f);
            }
        }
        font.endMegaBatch();
    }

    private void drawCard(CardSlot card) {
        Rect r = card.rect();
        MasterTitleEntry entry = entries.get(card.index());
        boolean selected = card.offset() == 0;
        boolean available = isEntryAvailable(entry);
        // Card body with a one-pixel drop shadow.
        font.beginMegaBatch();
        box(r.x() + 2, r.y() + 2, r.w(), r.h(), 0f, 0.01f, 0.06f, 0.55f);
        box(r.x(), r.y(), r.w(), r.h(), 0.02f, 0.06f, 0.2f, 0.78f);
        font.endMegaBatch();
        RomPreviewState preview = entry instanceof MasterTitleEntry.Stock stock ? romPreviews.get(stock.game()) : null;
        if (preview != null && preview.textureId != 0 && preview.sequence != null && available) {
            if (selected) updateSelectedRomPreviewTexture();
            // Keep the original ROM texture: the final viewport samples its texels directly.
            PreviewLayout layout = cardLogoLayout(preview.width, preview.height, r.x() + 4, r.y() + 4, r.w() - 8, r.h() - 8);
            renderer.drawTexture(preview.textureId, layout.x(), layout.y(), layout.width(), layout.height(),
                    1, 1, 1, 1);
        }
        if (entry instanceof MasterTitleEntry.Suspended && suspendedImage != null) {
            if (suspendedTextureId == 0) suspendedTextureId = MasterTitleRomPreview.uploadTexture(suspendedImage);
            PreviewLayout layout = cardLogoLayout(suspendedImage.width(), suspendedImage.height(),
                    r.x() + 3, r.y() + 3, r.w() - 6, r.h() - 6);
            renderer.drawTexture(suspendedTextureId, layout.x(), layout.y(), layout.width(), layout.height());
        }
        font.beginMegaBatch();
        if (!available && entry instanceof MasterTitleEntry.Stock stock) {
            String title = "ROM MISSING";
            MenuChrome.icon(font, MenuChrome.Icon.WARNING, r.x() + (r.w() - MenuChrome.ICON_SIZE) / 2, r.y() + r.h() / 2 - 26,
                    MenuChrome.AMBER);
            MenuChrome.compact(font, title, r.x() + (r.w() - MenuChrome.compactWidth(title)) / 2, r.y() + r.h() / 2 - 14, MenuChrome.AMBER);
            String file = stock.game().menuLabel;
            MenuChrome.compact(font, file, r.x() + (r.w() - MenuChrome.compactWidth(file)) / 2, r.y() + r.h() / 2 - 4, MenuChrome.TEXT);
            String hint = "Locate ROM below";
            MenuChrome.compact(font, MenuChrome.fitCompact(hint, r.w() - 6),
                    r.x() + Math.max(3, (r.w() - MenuChrome.compactWidth(hint)) / 2), r.y() + r.h() / 2 + 6, MenuChrome.DIM);
        } else if (entry instanceof MasterTitleEntry.Standalone standalone) {
            List<String> lines = MenuDetailsScreen.wrapText(standalone.displayName().toUpperCase(java.util.Locale.ROOT),
                    r.w() - 10);
            int top = r.y() + r.h() / 2 - lines.size() * 5 - 4;
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                String line = lines.get(i);
                MenuChrome.compact(font, line, r.x() + (r.w() - MenuChrome.compactWidth(line)) / 2, top + i * 10, MenuChrome.WHITE);
            }
            String badge = "MOD GAME";
            int bx = r.x() + (r.w() - MenuChrome.compactWidth(badge) - 8) / 2;
            int by = top + Math.min(3, lines.size()) * 10 + 4;
            MenuChrome.fill(font, bx, by, MenuChrome.compactWidth(badge) + 8, 11, MenuChrome.NAVY);
            MenuChrome.outline(font, bx, by, MenuChrome.compactWidth(badge) + 8, 11, MenuChrome.CYAN, 1f, 1);
            MenuChrome.compact(font, badge, bx + 4, by + 2, MenuChrome.CYAN);
        } else if (entry instanceof MasterTitleEntry.Suspended) {
            if (suspendedImage == null) {
                String name = entry.menuLabel().toUpperCase(java.util.Locale.ROOT);
                MenuChrome.label(font, MenuChrome.fitLabel(name, r.w() - 8),
                        r.x() + Math.max(4, (r.w() - MenuChrome.labelWidth(name)) / 2), r.y() + r.h() / 2 - 12, MenuChrome.WHITE);
            }
            String badge = "PAUSED";
            int bw = MenuChrome.compactWidth(badge) + 14;
            int bx = r.x() + (r.w() - bw) / 2;
            int by = r.y() + r.h() - 16;
            MenuChrome.fill(font, bx, by, bw, 11, MenuChrome.NAVY);
            MenuChrome.outline(font, bx, by, bw, 11, MenuChrome.GOLD, 1f, 1);
            MenuChrome.fill(font, bx + 4, by + 3, 1, 5, MenuChrome.GOLD);
            MenuChrome.fill(font, bx + 6, by + 3, 1, 5, MenuChrome.GOLD);
            MenuChrome.compact(font, badge, bx + 10, by + 2, MenuChrome.GOLD);
        } else if (preview == null && entry instanceof MasterTitleEntry.Stock) {
            String name = entry.menuLabel().toUpperCase(java.util.Locale.ROOT);
            MenuChrome.label(font, MenuChrome.fitLabel(name, r.w() - 8), r.x() + Math.max(4, (r.w() - MenuChrome.labelWidth(name)) / 2),
                    r.y() + r.h() / 2 - 5, MenuChrome.WHITE);
        }
        // Neighbours recede: darker the further they sit from the centre.
        float dim = Math.min(1f, card.distance());
        if (dim > 0f) box(r.x(), r.y(), r.w(), r.h(), 0.01f, 0.03f, 0.12f, 0.5f * dim);
        boolean focused = selected && card.distance() < 0.5f && navigation.zone() == TitleHubNavigation.Zone.CARD;
        if (focused) {
            float glow = MenuChrome.pulse(frameCounter);
            float[] c = MenuChrome.mix(MenuChrome.GOLD, MenuChrome.WHITE, glow * .35f);
            // The shared focus border, with open outer corners, around the card art.
            MenuChrome.fill(font, r.x() - 1, r.y() - 2, r.w() + 2, 2, c);
            MenuChrome.fill(font, r.x() - 1, r.y() + r.h(), r.w() + 2, 2, c);
            MenuChrome.fill(font, r.x() - 2, r.y() - 1, 2, r.h() + 2, c);
            MenuChrome.fill(font, r.x() + r.w(), r.y() - 1, 2, r.h() + 2, c);
        } else if (selected && card.distance() < 0.5f) {
            outlineBox(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, new float[] {.62f, .52f, .2f}, 1);
        } else {
            outlineBox(r.x(), r.y(), r.w(), r.h(), MenuChrome.EDGE, 1);
        }
        font.endMegaBatch();
    }

    private void outlineBox(int x, int y, int w, int h, float[] c, int t) {
        box(x, y, w, t, c[0], c[1], c[2], 1f);
        box(x, y + h - t, w, t, c[0], c[1], c[2], 1f);
        box(x, y + t, t, h - 2 * t, c[0], c[1], c[2], 1f);
        box(x + w - t, y + t, t, h - 2 * t, c[0], c[1], c[2], 1f);
    }

    private void drawHubDetails(int w, HubLayout layout) {
        MasterTitleEntry entry = selectedEntry();
        font.beginMegaBatch();
        String name = entry == null ? "NO GAMES AVAILABLE" : entry.displayName().toUpperCase(java.util.Locale.ROOT);
        name = carouselName(name, w - 16);
        MenuChrome.label(font, name, (w - MenuChrome.labelWidth(name)) / 2, NAME_Y,
                isEntryAvailable(entry) ? MenuChrome.WHITE : MenuChrome.DIM);

        List<HubSetupChips.Chip> chips = visibleHubChips();
        boolean anyAmber = hubChips().stream().anyMatch(HubSetupChips.Chip::thisGameOnly);
        for (int i = 0; i < chips.size(); i++) {
            Rect r = layout.chips().get(i);
            HubSetupChips.Chip chip = chips.get(i);
            anyAmber |= chip.thisGameOnly();
            MenuChrome.chip(font, r.x(), r.y(), chip.label(), chip.thisGameOnly());
        }
        if (chips.isEmpty() && entry instanceof MasterTitleEntry.Stock stock && !isEntryAvailable(stock)) {
            String help = "Choose your " + stock.game().menuLabel + " ROM file to play";
            MenuChrome.compact(font, help, (w - MenuChrome.compactWidth(help)) / 2, CHIP_Y + 2, MenuChrome.AMBER);
        } else if (chips.isEmpty() && entry instanceof MasterTitleEntry.Standalone standalone) {
            String help = standalone.continueAvailable() ? "A saved game is ready to continue" : "A standalone game from your mods";
            MenuChrome.compact(font, help, (w - MenuChrome.compactWidth(help)) / 2, CHIP_Y + 2, MenuChrome.CYAN);
        } else if (entry instanceof MasterTitleEntry.Suspended paused) {
            String where = MenuChrome.fitCompact(paused.location().isBlank() ? "Paused where you left it"
                    : "Paused in " + paused.location(), w - 16);
            MenuChrome.compact(font, where, (w - MenuChrome.compactWidth(where)) / 2, CHIP_Y + 2, MenuChrome.CYAN);
        }

        int n = entries.size();
        if (n > 1 && n <= 12) {
            int dx = (w - n * 8) / 2 + 2;
            for (int i = 0; i < n; i++) {
                boolean sel = i == selectedIndex;
                MenuChrome.fill(font, dx + i * 8, DOTS_Y, 4, 4, sel ? MenuChrome.GOLD : new float[] {.29f, .35f, .6f});
                if (sel) MenuChrome.fill(font, dx + i * 8 + 1, DOTS_Y + 1, 2, 1, MenuChrome.WHITE, .8f);
            }
        } else if (n > 12) {
            String pos = (selectedIndex + 1) + " / " + n;
            MenuChrome.compact(font, pos, (w - MenuChrome.compactWidth(pos)) / 2, DOTS_Y - 2, MenuChrome.MUTED);
        }

        List<TitleHubNavigation.Action> actions = hubActions();
        boolean available = isEntryAvailable(entry);
        for (int i = 0; i < actions.size(); i++) {
            Rect r = layout.actions().get(i);
            TitleHubNavigation.Action action = actions.get(i);
            boolean focused = navigation.zone() == TitleHubNavigation.Zone.ACTIONS && navigation.actionIndex() == i;
            if (primary(action)) {
                if (focused) MenuChrome.focus(font, r.x(), r.y(), r.w(), r.h(), frameCounter);
                float[] gold = MenuChrome.GOLD;
                MenuChrome.fill(font, r.x() + (focused ? 2 : 0), r.y() + (focused ? 2 : 0),
                        r.w() - (focused ? 4 : 0), r.h() - (focused ? 4 : 0), gold);
                MenuChrome.fill(font, r.x() + (focused ? 2 : 0), r.y() + r.h() - (focused ? 3 : 1),
                        r.w() - (focused ? 4 : 0), 1, new float[] {.6f, .42f, .05f});
                if (available) drawShimmer(r);
                if (compactActions()) MenuChrome.compact(font, actionLabel(action), r.x() + 6, r.y() + 6, MenuChrome.GOLD_INK);
                else {
                    MenuChrome.icon(font, available ? MenuChrome.Icon.PLAY : MenuChrome.Icon.FOLDER, r.x() + 8, r.y() + 6,
                            MenuChrome.GOLD_INK);
                    MenuChrome.label(font, actionLabel(action), r.x() + 8 + PRIMARY_ICON_ROOM, r.y() + 5, MenuChrome.GOLD_INK);
                }
            } else {
                if (focused) MenuChrome.focus(font, r.x(), r.y(), r.w(), r.h(), frameCounter);
                else MenuChrome.panel(font, r.x(), r.y(), r.w(), r.h());
                MenuChrome.compact(font, action.label, r.x() + 6, r.y() + 6,
                        available ? (focused ? MenuChrome.WHITE : MenuChrome.TEXT) : MenuChrome.DIM);
            }
        }

        List<List<HubSetupChips.Segment>> status = HubSetupChips.lines(hubStatus(), w - 16);
        if (status.isEmpty()) {
            String summary = hubSummary(entry, chips);
            MenuChrome.compact(font, MenuChrome.fitCompact(summary, w - 16),
                    (w - Math.min(w - 16, MenuChrome.compactWidth(summary))) / 2, SUMMARY_Y, MenuChrome.MUTED);
        } else {
            for (int line = 0; line < status.size(); line++) drawStatusLine(status.get(line), w, SUMMARY_Y + line * 10);
            anyAmber &= status.size() == 1; // a second status line takes the legend's row; markers remain
        }
        boolean setupFocused = navigation.zone() == TitleHubNavigation.Zone.ACTIONS
                && focusedAction() == TitleHubNavigation.Action.SETUP; // its summary already says this
        if (entry instanceof MasterTitleEntry.Suspended paused && !setupFocused) {
            String legend = MenuChrome.fitCompact(paused.inLevel() ? "Aspect, rewind, debug and controls apply on resume"
                    : "Rewind, debug and controls apply on resume", w - 16);
            MenuChrome.compact(font, legend, (w - MenuChrome.compactWidth(legend)) / 2, LEGEND_Y, MenuChrome.DIM);
        }
        if (anyAmber && entry != null) {
            String legend = "SET FOR " + entry.menuLabel().toUpperCase(java.util.Locale.ROOT) + " ONLY";
            int lx = (w - MenuChrome.compactWidth(legend) - 8) / 2;
            MenuChrome.changedMarker(font, lx, LEGEND_Y + 2);
            MenuChrome.compact(font, legend, lx + 8, LEGEND_Y, MenuChrome.AMBER);
        }
        MenuChrome.footer(font, w, restartPending() ? EngineSettingsScreen.RESTART_STATUS : null, hubHints(),
                MenuChrome.controller(menuInput));
        if (state == State.CONFIRMING && confirmTicks < 14) {
            // A quick white flash as the game is chosen, before GameLoop's fade.
            box(0, 0, w, SCREEN_H, 1f, 1f, 1f, 0.55f * (1f - confirmTicks / 14f));
        }
        font.endMegaBatch();
    }

    /** A soft light sweep across Start every few seconds. */
    private void drawShimmer(Rect r) {
        int period = 180;
        int t = Math.floorMod(frameCounter, period);
        if (t > 26) return;
        int x = r.x() - 6 + (r.w() + 12) * t / 26;
        for (int i = 0; i < 4; i++) {
            int px = x - i;
            if (px < r.x() + 1 || px >= r.x() + r.w() - 1) continue;
            MenuChrome.fill(font, px, r.y() + 1, 1, r.h() - 2, MenuChrome.WHITE, .45f - i * .1f);
        }
    }

    /**
     * The status line on the card and on Start: what Start uses that the chips do not show
     * (the save-screen team, and every chip cut to "+N MORE"). Empty elsewhere and when the
     * chips already say everything.
     */
    List<HubSetupChips.Segment> hubStatus() {
        MasterTitleEntry entry = selectedEntry();
        if (!(entry instanceof MasterTitleEntry.Stock stock) || !isEntryAvailable(stock)) return List.of();
        boolean card = navigation.zone() == TitleHubNavigation.Zone.CARD;
        boolean start = navigation.zone() == TitleHubNavigation.Zone.ACTIONS
                && focusedAction() == TitleHubNavigation.Action.START;
        if (!card && !start) return List.of();
        return HubSetupChips.status(hubChips(), visibleHubChips(),
                launchProfileStore.load(stock.game()).usesS3kDataSelectCharacters());
    }

    /** One centred status line; runs set for this game only are amber after the legend's marker. */
    private void drawStatusLine(List<HubSetupChips.Segment> line, int w, int y) {
        int x = (w - HubSetupChips.statusWidth(line)) / 2;
        for (HubSetupChips.Segment segment : line) {
            if (segment.thisGameOnly()) {
                MenuChrome.changedMarker(font, x, y + 2);
                x += HubSetupChips.MARKER_ROOM;
            }
            MenuChrome.compact(font, segment.text(), x, y, segment.thisGameOnly() ? MenuChrome.AMBER : MenuChrome.MUTED);
            x += MenuChrome.compactWidth(segment.text());
        }
    }

    private String hubSummary(MasterTitleEntry entry, List<HubSetupChips.Chip> chips) {
        if (entry == null) return "Add a game in Settings > Files or Mods";
        String game = entry.menuLabel();
        if (entry instanceof MasterTitleEntry.Stock && !isEntryAvailable(entry)) return "Locate your game file; any filename is accepted";
        if (entry instanceof MasterTitleEntry.Suspended paused && navigation.zone() != TitleHubNavigation.Zone.TABS) {
            TitleHubNavigation.Action action = navigation.zone() == TitleHubNavigation.Zone.ACTIONS ? focusedAction() : null;
            if (action == TitleHubNavigation.Action.END) return "End your paused " + game + " game";
            if (action == TitleHubNavigation.Action.SETUP)
                return (paused.inLevel() ? "Aspect, rewind and debug" : "Rewind and debug") + " apply on resume";
            return "Carry on with " + game + " where you left it";
        }
        return switch (navigation.zone()) {
            case CARD -> entry instanceof MasterTitleEntry.Stock && isEntryAvailable(entry)
                    ? "Game Setup changes players, screen and extras" : "Start " + game;
            case ACTIONS -> switch (focusedAction()) {
                case START -> "Start " + game;
                case SETUP -> "Players, screen and extras for " + game + " only";
                case RECORDINGS -> "Browse " + game + " recordings and trace replays";
                case RESUME, END -> "";
            };
            case TABS -> switch (navigation.tab()) {
                case MODS -> "Turn mods on or off for all games";
                case SETTINGS -> restartPending() ? "Restart OpenGGF to finish applying saved settings"
                        : suspended != null ? "For all games; some reach your paused game"
                        : "Display, sound, controls and more for all games";
                case EXTRAS -> "Extra modes added by your mods";
                case ADVANCED -> "Playback browser, advanced settings and tools";
                case QUIT -> "Close OpenGGF";
            };
        };
    }

    private List<MenuChrome.Hint> hubHints() {
        String ok = MenuChrome.confirmKey(menuInput);
        String back = MenuChrome.backKey(menuInput);
        return switch (navigation.zone()) {
            case CARD -> List.of(MenuChrome.leftRight(menuInput, "GAME"), MenuChrome.hint(ok,
                    selectedEntry() instanceof MasterTitleEntry.Suspended ? "RESUME" : actionLabel(TitleHubNavigation.Action.START)), MenuChrome.hint(MenuChrome.detailsKey(menuInput), "HELP"), MenuChrome.hint(back, "QUIT"));
            case ACTIONS -> List.of(MenuChrome.leftRight(menuInput, "BUTTON"), MenuChrome.hint(ok, switch (focusedAction()) {
                case START -> actionLabel(TitleHubNavigation.Action.START);
                case SETUP -> "OPEN SETUP";
                case RECORDINGS -> "OPEN PLAYBACK";
                case RESUME -> "RESUME";
                case END -> "END GAME";
            }), MenuChrome.hint(MenuChrome.detailsKey(menuInput), "HELP"), MenuChrome.hint(back, "GAMES"));
            case TABS -> List.of(MenuChrome.leftRight(menuInput, "TAB"), MenuChrome.hint(ok, "OPEN " + navigation.tab().label),
                    MenuChrome.hint(MenuChrome.detailsKey(menuInput), "HELP"), MenuChrome.hint(back, "GAMES"));
        };
    }

    Rect helpHintBounds() {
        List<MenuChrome.Hint> shown = hubHints();
        int left = 6;
        if (restartPending()) left += MenuChrome.compactWidth(EngineSettingsScreen.RESTART_STATUS) + 18;
        while (!shown.isEmpty() && MenuChrome.hintsWidth(shown) > viewportWidth - 8 - left)
            shown = shown.subList(1, shown.size());
        int x = viewportWidth - 8 - MenuChrome.hintsWidth(shown);
        for (var hint : shown) {
            int width = MenuChrome.hintsWidth(List.of(hint));
            if ("HELP".equals(hint.label())) return new Rect(x - 2, MenuChrome.FOOTER_Y + 2, width + 4, 15);
            x += width + 12;
        }
        return null;
    }

    private String carouselName(String name, int maxWidth) {
        return MenuStyle.marquee(name, maxWidth, 1f, previewAnimationFrame / 8);
    }

    /** Shared list page for Extras and the standalone New Game / Continue chooser. */
    private void drawChooser(String title, String subtitle, List<String> rows, int selected, String verb) {
        int w = viewportWidth;
        font.beginMegaBatch();
        MenuChrome.background(font, w);
        MenuChrome.header(font, w, title, null);
        MenuChrome.compact(font, MenuChrome.fitCompact(subtitle, w - 18), 9, 36, MenuChrome.MUTED);
        for (int i = 0; i < rows.size(); i++) {
            int y = 76 + i * 28;
            if (i == selected) MenuChrome.focus(font, w / 2 - 90, y, 180, 23, frameCounter);
            else MenuChrome.panel(font, w / 2 - 90, y, 180, 23);
            String label = MenuChrome.fitLabel(rows.get(i), 170);
            MenuChrome.label(font, label, (w - MenuChrome.labelWidth(label)) / 2, y + 7,
                    i == selected ? MenuChrome.WHITE : MenuChrome.TEXT);
        }
        MenuChrome.footer(font, w, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), verb),
                MenuChrome.hint(MenuChrome.backKey(menuInput), "BACK")), MenuChrome.controller(menuInput));
        font.endMegaBatch();
    }

    private void drawToolsAndHelp() {
        int w = viewportWidth;
        font.beginMegaBatch();
        MenuChrome.background(font, w);
        MenuChrome.header(font, w, toolsOpen ? "ADVANCED" : "CONTROLS AND HELP", toolsOpen ? "ALL GAMES" : null);
        if (toolsOpen) {
            var tools = toolActions();
            for (int i = 0; i < tools.size(); i++) {
                int y = TOOL_TOP + i * TOOL_ROW;
                boolean focused = i == toolIndex;
                if (focused) MenuChrome.focus(font, 10, y, w - 20, TOOL_ROW - 2, frameCounter);
                else MenuChrome.fill(font, 10, y + TOOL_ROW - 2, w - 20, 1, MenuChrome.EDGE, .45f);
                MenuChrome.icon(font, tools.get(i).icon(), 17, y + 7, focused ? MenuChrome.GOLD : MenuChrome.CYAN);
                MenuChrome.compact(font, tools.get(i).label(), 31, y + 3, focused ? MenuChrome.WHITE : MenuChrome.TEXT);
                MenuChrome.compact(font, MenuChrome.fitCompact(tools.get(i).note(), w - 46), 31, y + 12,
                        focused ? MenuChrome.MUTED : MenuChrome.DIM);
            }
            MenuChrome.footer(font, w, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), "OPEN"),
                    MenuChrome.hint(MenuChrome.backKey(menuInput), "BACK")), MenuChrome.controller(menuInput));
        } else {
            String back = MenuChrome.backKey(menuInput);
            boolean pad = MenuChrome.controller(menuInput);
            List<HelpGroup> groups = helpGroups();
            int descriptionX = 18 + groups.stream().flatMap(group -> group.rows().stream())
                    .mapToInt(row -> MenuChrome.keyCapWidth(row[0])).max().orElse(36) + 10;
            int y = 35;
            for (HelpGroup group : groups) {
                MenuChrome.section(font, group.title(), 12, y, w - 24);
                y += 12;
                for (String[] row : group.rows()) {
                    MenuChrome.hints(font, 18, y, List.of(MenuChrome.hint(row[0], "")), pad, MenuChrome.TEXT);
                    MenuChrome.compact(font, MenuChrome.fitCompact(row[1], w - descriptionX - 12), descriptionX, y + 2,
                            MenuChrome.TEXT);
                    // Quieter notes follow on their own lines, wrapped by measured width; never cut short.
                    for (int note = 2; note < row.length; note++) {
                        for (String line : MenuStyle.wrap(row[note], w - descriptionX - 12, MenuStyle.COMPACT)) {
                            y += 10;
                            MenuChrome.compact(font, line, descriptionX, y + 2, MenuChrome.MUTED);
                        }
                    }
                    y += HELP_ROW;
                }
                y += 4;
            }
            // The legend shows the marker it explains, as the hub's own legend does.
            MenuChrome.changedMarker(font, 18, y + 3);
            MenuChrome.compact(font, "Amber values are set for one game only", 26, y + 1, MenuChrome.AMBER);
            MenuChrome.footer(font, w, null, List.of(MenuChrome.hint(back, "BACK")), MenuChrome.controller(menuInput));
        }
        font.endMegaBatch();
    }

    private static final int HELP_ROW = 15;

    /** One captioned group of help rows: {key, what it does, quieter note lines...}. */
    record HelpGroup(String title, List<String[]> rows) { }

    /**
     * The help page in reading order: moving, then selecting, then shortcuts. Move rows say where
     * the focus goes; only Select rows act. A pad has no Tab or M key, so its one Up/Down row names
     * where Game Setup and Mods sit instead of repeating the same D-pad cap for each destination;
     * its help button is its one shortcut, as F1 is on a keyboard.
     */
    List<HelpGroup> helpGroups() {
        boolean pad = MenuChrome.controller(menuInput);
        String ok = MenuChrome.confirmKey(menuInput);
        String back = MenuChrome.backKey(menuInput);
        List<HelpGroup> groups = new ArrayList<>();
        String[] vertical = pad
                ? new String[] { MenuChrome.verticalKey(menuInput), "Move between tabs, game and buttons",
                        "Mods: in the top tabs", "Game Setup: below the game" }
                : new String[] { MenuChrome.verticalKey(menuInput), "Move between tabs, game and buttons" };
        groups.add(new HelpGroup("MOVE", List.of(
                new String[] { MenuChrome.horizontalKey(menuInput), "Next or previous game, button or tab" }, vertical)));
        groups.add(new HelpGroup("SELECT", List.of(
                new String[] { ok, "Start the game or open the selection" },
                new String[] { back, "Go back, or quit from the game card" })));
        groups.add(new HelpGroup("SHORTCUTS", pad
                ? List.<String[]>of(new String[] { MenuChrome.detailsKey(menuInput), "This help, from any hub row" })
                : List.of(new String[] { "Tab", "Game Setup for the selected game" },
                        new String[] { "M", "Mod manager" },
                        new String[] { MenuChrome.detailsKey(menuInput), "This help" })));
        return groups;
    }

    /** Where the help page draws: group captions, key rows and note lines, and the legend's top. */
    record HelpLayout(List<Integer> captionY, List<Integer> rowY, int legendY, int descriptionX) { }

    /** The help page's measured geometry, the same walk {@link #drawToolsAndHelp} draws. */
    HelpLayout helpLayout() {
        int w = viewportWidth;
        List<HelpGroup> groups = helpGroups();
        int descriptionX = 18 + groups.stream().flatMap(group -> group.rows().stream())
                .mapToInt(row -> MenuChrome.keyCapWidth(row[0])).max().orElse(36) + 10;
        List<Integer> captions = new ArrayList<>(), rows = new ArrayList<>();
        int y = 35;
        for (HelpGroup group : groups) {
            captions.add(y);
            y += 12;
            for (String[] row : group.rows()) {
                rows.add(y);
                for (int note = 2; note < row.length; note++)
                    y += 10 * MenuStyle.wrap(row[note], w - descriptionX - 12, MenuStyle.COMPACT).size();
                y += HELP_ROW;
            }
            y += 4;
        }
        return new HelpLayout(List.copyOf(captions), List.copyOf(rows), y, descriptionX);
    }

    private void textFitted(String text, int x, int y, int maxWidth, float preferredScale,
                            float r, float g, float b) {
        if (preferredScale >= 1f && MenuChrome.labelWidth(text) <= maxWidth) {
            font.drawText(text, x, y, 1f, r, g, b, 1f);
        } else {
            MenuStyle.text(font, text, x, y, maxWidth, r, g, b);
        }
    }

    private void box(int x, int y, int width, int height, float r, float g, float b, float alpha) {
        MenuStyle.fill(font, x, y, width, height, r, g, b, alpha);
    }


    private void loadRomPreviews() {
        if (romCatalogue == null) refreshStockAvailability();
        for (MasterTitleEntry entry : entries) {
            if (!(entry instanceof MasterTitleEntry.Stock stock) || !isEntryAvailable(entry)) continue;
            GameEntry game = stock.game();
            romCatalogue.resolve(logicalRomFor(game))
                    .ifPresent(resolution -> loadRomPreview(game, resolution, resolution.changeToken()));
        }
    }

    private void loadRomPreview(GameEntry game, RomImageCatalogue.Resolution resolution, String source) {
        // Remember even a failed decode: unrelated Apply must not retry disk work.
        previewSources.put(game, source);
        Optional<Path> wholeImage = resolution.wholeImagePath();
        Optional<Sequence> decoded;
        if (wholeImage.isPresent()) {
            decoded = MasterTitleRomPreview.loadSequenceFor(game, wholeImage.get());
        } else {
            // A window of a lock-on dump or a composite has no file of its own:
            // decode from the catalogue's in-memory view instead.
            try {
                decoded = MasterTitleRomPreview.loadSequenceFor(game,
                        Rom.fromReader(romCatalogue.open(resolution.rom()), resolution.describe()));
            } catch (IOException e) {
                LOGGER.warning("Could not open " + resolution.describe() + " for its title preview: " + e.getMessage());
                decoded = Optional.empty();
            }
        }
        decoded.ifPresent(sequence -> {
            RomPreviewState preview = new RomPreviewState();
            preview.sequence = sequence;
            Image firstFrame = sequence.imageAt(0);
            preview.textureId = MasterTitleRomPreview.uploadTexture(firstFrame);
            preview.width = firstFrame.width();
            preview.height = firstFrame.height();
            romPreviews.put(game, preview);
        });
    }

    private void updateSelectedRomPreviewTexture() {
        RomPreviewState preview = selectedPreview();
        if (preview == null) return;
        int token = preview.sequence.frameTokenAt(previewAnimationFrame);
        if (preview.frameToken == token) return;
        Image frame = preview.sequence.imageAt(previewAnimationFrame);
        MasterTitleRomPreview.updateTexture(preview.textureId, frame);
        preview.frameToken = token;
    }

    private void playNavigateSound() { audioSink.play(AudioCue.NAVIGATE); }
    private void playConfirmSound()  { audioSink.play(AudioCue.CONFIRM); }
    private void playCancelSound()   { audioSink.play(AudioCue.CANCEL); }
    private void playErrorSound()    { audioSink.play(AudioCue.ERROR); }

    /**
     * Returns true when the user has selected a game and confirmed.
     */
    public boolean isGameSelected() {
        return gameSelected;
    }

    /**
     * Programmatic selection used by {@link com.openggf.TraceSessionLauncher}
     * to force a game without user input. Must be called while state is
     * {@code ACTIVE}. Seeds the internal "selected" state so
     * {@link #isGameSelected()} returns true on the next tick.
     */
    public void selectEntry(GameEntry entry) {
        Objects.requireNonNull(entry, "entry");
        int index = indexOfStock(entry);
        if (index < 0) throw new IllegalArgumentException("Stock entry is not present: " + entry.gameId);
        setSelectedIndex(index, false);
        if (!isEntryAvailable(selectedEntry())) {
            throw new IllegalStateException("Selected ROM is unavailable: " + entry.gameId);
        }
        confirmLaunch(new MasterTitleEntry.Launch(selectedEntry(), MasterTitleEntry.Action.NEW_GAME), true);
    }

    public void showRomLoadError(String gameId) {
        launchErrorTitle = null;
        launchErrorDetail = null;
        selectErrorGame(gameId);
    }

    public void showModLaunchError(String gameId) {
        launchErrorTitle = "MOD LAUNCH FAILED";
        launchErrorDetail = "DISABLED FOR NEXT LAUNCH";
        selectErrorGame(gameId);
    }

    private void selectErrorGame(String gameId) {
        if (gameId != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).gameId().equalsIgnoreCase(gameId)) {
                    setSelectedIndex(i);
                    break;
                }
            }
        }
        if (this.state != State.ERROR_DISPLAY) {
            playErrorSound();
            traceFailureAnnounced = configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED);
            if (playbackBrowser != null) playbackBrowser.markFailureFeedbackAnnounced();
        }
        this.state = State.ERROR_DISPLAY;
        this.gameSelected = false;
    }

    /**
     * Sets the projection (viewport) width for widescreen-aware layout.
     * Must be called each frame before {@link #draw()} when the engine is
     * running at a width other than native 320. At native width (320) this is
     * a no-op because the default already equals SCREEN_W.
     *
     * @param width projection width in pixels (e.g. 320, 400, 528)
     */
    public void setViewportWidth(int width) {
        this.viewportWidth = Math.max(SCREEN_W, width);
    }

    static int titleLogoScaledWidth(int sourceWidth) {
        return scaledTitleLogoDimension(sourceWidth);
    }

    private static int scaledTitleLogoDimension(int sourceDimension) {
        int scaled = sourceDimension
                * TITLE_LOGO_BASE_SCALE_NUMERATOR
                * TITLE_LOGO_SCALE_NUMERATOR
                / TITLE_LOGO_BASE_SCALE_DENOMINATOR
                / TITLE_LOGO_SCALE_DENOMINATOR;
        return Math.max(1, scaled);
    }

    static String expectedRomFilename(GameEntry entry) {
        return entry.expectedRomFilename;
    }

    private MasterTitleEntry selectedEntry() {
        return entries.isEmpty() ? null : entries.get(selectedIndex);
    }

    private boolean isEntryAvailable(MasterTitleEntry entry) {
        return entry instanceof MasterTitleEntry.Standalone || entry instanceof MasterTitleEntry.Suspended
                || entry instanceof MasterTitleEntry.Stock stock
                && stockAvailability.getOrDefault(stock.game(), false);
    }

    private RomPreviewState selectedPreview() {
        if (!(selectedEntry() instanceof MasterTitleEntry.Stock stock)) return null;
        RomPreviewState preview = romPreviews.get(stock.game());
        return preview == null || preview.textureId == 0 || preview.sequence == null ? null : preview;
    }

    private int indexOfStock(GameEntry game) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof MasterTitleEntry.Stock stock && stock.game() == game) return i;
        }
        return -1;
    }

    private int rememberedSelectionIndex(List<MasterTitleEntry> entries) {
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i).gameId().equals(hubProgress.lastGame())) return i;
        return defaultSelectionIndex(entries);
    }

    private static int defaultSelectionIndex(List<MasterTitleEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof MasterTitleEntry.Stock stock
                    && stock.game() == GameEntry.SONIC_2) return i;
        }
        return 0;
    }

    private boolean cycleSelectedEntry(int direction) {
        boolean changed = entries.size() > 1 && setSelectedIndex(Math.floorMod(selectedIndex + direction, entries.size()));
        // The newly selected card starts where it sat on the shelf and eases into the centre.
        if (changed) slide += direction * CARD_SPACING;
        return changed;
    }

    void bindEntrySource(java.util.function.Supplier<List<MasterTitleEntry>> source) {
        entrySource = Objects.requireNonNull(source, "source");
    }

    /** Reconcile a host catalog snapshot by game identity, never by an obsolete slot. */
    void replaceEntries(List<MasterTitleEntry> replacement) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (MasterTitleEntry entry : replacement) {
            if (!ids.add(entry.gameId())) throw new IllegalArgumentException("Duplicate title game: " + entry.gameId());
        }
        List<MasterTitleEntry> next = withSuspendedFirst(replacement);
        if (entries.equals(next)) return;
        MasterTitleEntry selected = selectedEntry();
        String selectedId = getSelectedGameId();
        int previousIndex = selectedIndex;
        entries = next;
        selectedIndex = Math.max(0, Math.min(previousIndex, entries.size() - 1));
        int match = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).equals(selected)) { match = i; break; }
            if (match < 0 && entries.get(i).gameId().equals(selectedId)) match = i;
        }
        if (match >= 0) selectedIndex = match;
        previewAnimationFrame = 0;
        if (renderer != null) refreshRomPreviews();
    }

    boolean setSelectedIndex(int newIndex) { return setSelectedIndex(newIndex, true); }

    private boolean setSelectedIndex(int newIndex, boolean remember) {
        int clamped = Math.max(0, Math.min(entries.size() - 1, newIndex));
        if (clamped == selectedIndex) {
            return false;
        }
        selectedIndex = clamped;
        previewAnimationFrame = 0;
        if (remember && !(selectedEntry() instanceof MasterTitleEntry.Suspended)) hubProgress.rememberGame(getSelectedGameId());
        return true;
    }

    void advancePreviewAnimationFrame() {
        if (previewAnimationFrame < Integer.MAX_VALUE) {
            previewAnimationFrame++;
        }
    }

    int previewAnimationFrameForTest() {
        return previewAnimationFrame;
    }

    public void setSelectedIndexForTest(int newIndex) {
        setSelectedIndex(newIndex);
    }

    public void setStateForTest(State state) {
        this.state = state;
    }

    public boolean isLaunchConfigPanelOpenForTest() {
        return launchConfigPanel != null;
    }

    LaunchProfile currentLaunchProfileForTest() {
        if (launchConfigPanel != null) {
            return launchConfigPanel.currentProfile();
        }
        if (selectedEntry() instanceof MasterTitleEntry.Stock stock) {
            return launchProfileStore.load(stock.game());
        }
        return null;
    }

    com.openggf.game.launch.LaunchProfile.Row launchPanelRowForTest() {
        return launchConfigPanel == null ? null : launchConfigPanel.selectedRowForTest();
    }

    boolean helpOpenForTest() { return helpOpen; }

    TitleHubNavigation.Zone hubZoneForTest() {
        return navigation.zone();
    }

    public void setRomAvailableForTest(GameEntry entry, boolean available) {
        stockAvailability.put(entry, available);
    }

    List<MasterTitleEntry> entriesForTest() { return entries; }

    List<MasterTitleEntry.Action> standaloneActionsForTest() {
        if (!(selectedEntry() instanceof MasterTitleEntry.Standalone standalone)) return List.of();
        return standalone.continueAvailable()
                ? List.of(MasterTitleEntry.Action.NEW_GAME, MasterTitleEntry.Action.CONTINUE)
                : List.of(MasterTitleEntry.Action.NEW_GAME);
    }

    public MasterTitleEntry.Launch getSelectedLaunch() { return selectedLaunch; }

    /**
     * Shows a suspended game first on the shelf and selects it. {@code rgba} is its last frame,
     * top row first, or null for a card without a picture.
     */
    public void setSuspendedGame(MasterTitleEntry.Suspended card, int width, int height, byte[] rgba) {
        suspended = Objects.requireNonNull(card, "card");
        releaseSuspendFrame();
        suspendFrame = rgba == null ? null : new Image(width, height, rgba);
        suspendedImage = suspendFrame == null ? null : fitThumbnail(suspendFrame, CARD_W - 6, CARD_H - 6);
        deleteSuspendedTexture();
        entries = withSuspendedFirst(entries);
        selectedIndex = 0;
        navigation.focus(TitleHubNavigation.Zone.CARD);
    }

    /** Called only on entry from gameplay; reopening the hub must not replay the zoom. */
    public void animateSuspendedGame() {
        if (suspendFrame == null) return;
        suspendZoomTicks = 0;
        childInputPending = true;
    }

    PreviewLayout suspendZoomLayout() {
        PreviewLayout start = cardLogoLayout(suspendFrame.width(), suspendFrame.height(),
                0, 0, viewportWidth, SCREEN_H);
        Rect card = hubLayout().cards().stream().filter(slot -> slot.offset() == 0).findFirst().orElseThrow().rect();
        PreviewLayout end = cardLogoLayout(suspendedImage.width(), suspendedImage.height(),
                card.x() + 3, card.y() + 3, card.w() - 6, card.h() - 6);
        float t = suspendZoomTicks / (float) SUSPEND_ZOOM_TICKS;
        float eased = t * t * (3 - 2 * t);
        return new PreviewLayout(Math.round(start.width() + (end.width() - start.width()) * eased),
                Math.round(start.height() + (end.height() - start.height()) * eased),
                start.x() + (end.x() - start.x()) * eased, start.y() + (end.y() - start.y()) * eased);
    }

    private void drawSuspendZoom() {
        if ((!resumeZooming && suspendZoomTicks >= SUSPEND_ZOOM_TICKS) || suspendFrame == null) return;
        if (suspendFrameTextureId == 0) {
            suspendFrameTextureId = MasterTitleRomPreview.uploadTexture(suspendFrame);
            glBindTexture(GL_TEXTURE_2D, suspendFrameTextureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            org.lwjgl.opengl.GL30.glGenerateMipmap(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        float t = suspendZoomTicks / (float) SUSPEND_ZOOM_TICKS;
        renderer.drawTexture(solidWhiteTextureId, 0, 0, viewportWidth, SCREEN_H, 0, 0, 0, 1 - t);
        PreviewLayout r = suspendZoomLayout();
        renderer.drawTexture(suspendFrameTextureId, r.x(), r.y(), r.width(), r.height());
    }

    private void releaseSuspendTexture() {
        if (suspendFrameTextureId != 0) PngTextureLoader.deleteTexture(suspendFrameTextureId);
        suspendFrameTextureId = 0;
    }

    private void releaseSuspendFrame() {
        releaseSuspendTexture();
        suspendFrame = null;
        suspendZoomTicks = SUSPEND_ZOOM_TICKS;
        resumeZooming = false;
        resumeZoomFinished = false;
    }

    /** Ends the suspended game in the host; the card is then removed. */
    public void setSuspendedGameEndHandler(Runnable handler) {
        suspendedGameEndHandler = Objects.requireNonNull(handler, "handler");
    }

    public boolean hasSuspendedGame() {
        return suspended != null;
    }

    /** True once after the resume zoom finishes; the host restores gameplay between frames. */
    public boolean consumeResumeRequest() {
        boolean requested = resumeRequested;
        resumeRequested = false;
        return requested;
    }

    private void requestResume() {
        hubProgress.flush();
        if (suspendFrame == null) resumeRequested = true;
        else {
            suspendZoomTicks = SUSPEND_ZOOM_TICKS;
            resumeZooming = true;
            resumeZoomFinished = false;
        }
        state = State.CONFIRMING;
        playConfirmSound();
    }

    /**
     * Asks before an action that would end the suspended game, then runs {@code then} once it has
     * ended. Returns false (and asks nothing) when no game is suspended, so the caller proceeds.
     */
    private boolean askToEndSuspended(String detail, Runnable then) {
        if (suspended == null) return false;
        endPrompt = true;
        endSelected = false;
        endPromptDetail = detail;
        endPromptThen = then;
        playConfirmSound();
        return true;
    }

    private void updateEndPrompt() {
        if (navigation.back() || backClicked()) { endPrompt = false; playCancelSound(); return; }
        if (pointer != null) {
            for (int option = 0; option < 2; option++) {
                if (!pointer.over(viewportWidth / 2 - 80, 92 + option * 26, 160, 20)) continue;
                boolean end = option == 1;
                if (pointer.moved() && endSelected != end) { endSelected = end; playNavigateSound(); }
                if (pointer.click()) { endSelected = end; confirmEndPrompt(); return; }
            }
        }
        if (navigation.left() || navigation.right() || navigation.up() || navigation.down()) {
            endSelected = !endSelected;
            playNavigateSound();
        } else if (navigation.accept()) {
            confirmEndPrompt();
        }
    }

    private void confirmEndPrompt() {
        endPrompt = false;
        if (!endSelected) { playCancelSound(); return; }
        Runnable then = endPromptThen;
        endPromptThen = null;
        suspendedGameEndHandler.run();
        clearSuspendedGame();
        playConfirmSound();
        then.run();
    }

    /** Removes the suspended card once its game has ended, selecting the same stock game. */
    void clearSuspendedGame() {
        if (suspended == null) return;
        MasterTitleEntry.Stock game = suspended.game();
        boolean wasSelected = selectedEntry() instanceof MasterTitleEntry.Suspended;
        suspended = null;
        releaseSuspendFrame();
        suspendedImage = null;
        deleteSuspendedTexture();
        entries = entries.stream().filter(entry -> !(entry instanceof MasterTitleEntry.Suspended)).toList();
        int stock = entries.indexOf(game);
        selectedIndex = wasSelected && stock >= 0 ? stock
                : Math.clamp(selectedIndex - 1, 0, Math.max(0, entries.size() - 1));
        navigation.focus(TitleHubNavigation.Zone.CARD);
    }

    private List<MasterTitleEntry> withSuspendedFirst(List<MasterTitleEntry> catalog) {
        List<MasterTitleEntry> next = new ArrayList<>();
        if (suspended != null) next.add(suspended);
        catalog.stream().filter(entry -> !(entry instanceof MasterTitleEntry.Suspended)).forEach(next::add);
        return List.copyOf(next);
    }

    private void deleteSuspendedTexture() {
        if (suspendedTextureId != 0) PngTextureLoader.deleteTexture(suspendedTextureId);
        suspendedTextureId = 0;
    }

    /** Area-averages the frame down to the card so the shelf shows a clean miniature. */
    static Image fitThumbnail(Image source, int maxWidth, int maxHeight) {
        double scale = Math.min(1.0, Math.min((double) maxWidth / source.width(), (double) maxHeight / source.height()));
        int width = Math.max(1, (int) Math.floor(source.width() * scale));
        int height = Math.max(1, (int) Math.floor(source.height() * scale));
        if (width == source.width() && height == source.height()) return source;
        byte[] out = new byte[width * height * 4];
        byte[] in = source.rgba();
        for (int y = 0; y < height; y++) {
            int y0 = y * source.height() / height, y1 = Math.max(y0 + 1, (y + 1) * source.height() / height);
            for (int x = 0; x < width; x++) {
                int x0 = x * source.width() / width, x1 = Math.max(x0 + 1, (x + 1) * source.width() / width);
                long[] sum = new long[4];
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int i = (sy * source.width() + sx) * 4;
                        for (int c = 0; c < 4; c++) sum[c] += in[i + c] & 0xFF;
                    }
                }
                int count = (y1 - y0) * (x1 - x0);
                int o = (y * width + x) * 4;
                for (int c = 0; c < 4; c++) out[o + c] = (byte) (sum[c] / count);
            }
        }
        return new Image(width, height, out);
    }

    public void setModManagerOpenHandler(Runnable handler) {
        modManagerOpenHandler = Objects.requireNonNull(handler, "handler");
    }

    public void setModManagerScreenFactory(ModManagerScreenFactory factory) {
        modManagerScreenFactory = Objects.requireNonNull(factory, "factory");
    }

    public void setModManagerApplyHandler(Runnable handler) {
        modManagerApplyHandler = Objects.requireNonNull(handler, "handler");
    }

    public void setEnabledModsByGame(Map<GameEntry, List<String>> namesByGame) {
        Objects.requireNonNull(namesByGame, "namesByGame");
        Map<GameEntry, List<String>> copy = new EnumMap<>(GameEntry.class);
        namesByGame.forEach((game, names) -> copy.put(Objects.requireNonNull(game),
                List.copyOf(Objects.requireNonNull(names))));
        enabledModsByGame = Map.copyOf(copy);
    }

    /** Called after GameLoop finishes using this title instance for the frame. */
    public void finishPendingModApply() {
        if (!modApplyPending) return;
        modApplyPending = false;
        try {
            modManagerApplyHandler.run();
        } catch (RuntimeException failure) {
            if (modManagerScreen != null) modManagerScreen.activationFailed(
                    failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }

    void openModManager() {
        openModManager(null);
    }

    private void openModManager(String gameId) {
        if (askToEndSuspended("Changing mods ends it", () -> openModManager(gameId))) return;
        if (modManagerScreenFactory != null) {
            modManagerScreen = Objects.requireNonNull(
                    modManagerScreenFactory.create(font), "mod manager screen");
            modManagerScreen.setGameFilter(gameId);
            modManagerScreen.suppressInputUntilNeutral();
        } else {
            modManagerOpenHandler.run();
        }
        playConfirmSound();
    }

    boolean isModsFocusedForTest() {
        return navigation.zone() == TitleHubNavigation.Zone.TABS && navigation.tab() == TitleHubNavigation.Tab.MODS;
    }

    boolean isModManagerOpenForTest() {
        return modManagerScreen != null;
    }

    public void setUserRecordingPlaybackStarter(UserRecordingMenu.PlaybackStarter playbackStarter) {
        this.userRecordingPlaybackStarter = Objects.requireNonNull(playbackStarter, "playbackStarter");
    }

    public void setPlaybackLibrarySourceForTest(java.util.concurrent.Callable<PlaybackLibrary> source) {
        this.playbackLibrarySource = Objects.requireNonNull(source, "source");
    }

    public void setPlaybackSelectionMemory(PlaybackBrowser.SelectionMemory memory) {
        playbackSelection = Objects.requireNonNull(memory, "memory");
    }

    public PlaybackBrowser playbackBrowserForTest() {
        return playbackBrowser;
    }

    public PixelFont pixelFont() {
        return font;
    }

    private void resetRomPreviewTextureFrames() {
        romPreviews.values().forEach(preview -> preview.frameToken = Integer.MIN_VALUE);
    }

    public void setProjectionMatrix(float[] projectionMatrix) {
        if (renderer != null && projectionMatrix != null) {
            currentProjection = projectionMatrix.clone();
            renderer.setProjectionMatrix(projectionMatrix);
        }
    }

    /**
     * Returns the stable selected game ID, or null when the effective catalog is empty.
     */
    public String getSelectedGameId() {
        return selectedEntry() == null ? null : selectedEntry().gameId();
    }

    public boolean isProgrammaticSelection() {
        return programmaticSelection;
    }

    public boolean tryOpenUserRecordingMenuForSelectedGame() {
        if (state != State.ACTIVE
                || configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)
                || launchConfigPanel != null) {
            return false;
        }
        if (!(selectedEntry() instanceof MasterTitleEntry.Stock stock)
                || !isEntryAvailable(stock)) return false;
        openPlaybackBrowser(stock.game().gameId, PlaybackBrowser.Filter.ALL, false);
        playConfirmSound();
        return true;
    }

    public boolean handleUserRecordingMenuRequest(boolean recordingMenuRequested) {
        if (!recordingMenuRequested) {
            return false;
        }
        return tryOpenUserRecordingMenuForSelectedGame();
    }

    /** Cleans up all GL resources. */
    public void cleanup() {
        if (settingsScreen != null) settingsScreen.cancelDisplayTrial();
        hubProgress.flush();
        cancelCatalogLoad();
        if (font != null) font.cleanup();
        PngTextureLoader.deleteTexture(bgTextureId);
        PngTextureLoader.deleteTexture(solidWhiteTextureId);
        PngTextureLoader.deleteTexture(titleTextId);
        PngTextureLoader.deleteTexture(cloudLargeTextureId);
        PngTextureLoader.deleteTexture(cloudSmallTextureId);
        romPreviews.values().forEach(preview -> {
            PngTextureLoader.deleteTexture(preview.textureId);
        });
        deleteSuspendedTexture();
        releaseSuspendFrame();
        if (renderer != null) renderer.cleanup();
        playbackBrowser = null;
        if (romLocator != null) romLocator.cancel();
        romLocator = null;
        if (onboarding != null) onboarding.cancel();
        onboarding = null;
        if (hubMusic != null) hubMusic.close();
        hubMusic = null;
        settingsScreen = null;
        toolsOpen = false;
        helpOpen = false;
        modManagerScreen = null;
        state = State.INACTIVE;
        LOGGER.info("Master title screen cleaned up");
    }

    @FunctionalInterface
    public interface ModManagerScreenFactory {
        ModManagerView create(PixelFont font);
    }

    /** Menu ownership is intentionally separate from the exported title-screen API. */
    boolean blocksGlobalShortcuts() {
        return state != State.ACTIVE || navigation.zone() != TitleHubNavigation.Zone.CARD || quitPrompt || childInputPending
                || settingsScreen != null || launchConfigPanel != null || modManagerScreen != null
                || playbackBrowser != null || standaloneActionOpen || extrasOpen
                || toolsOpen || helpOpen || romLocator != null || onboarding != null || catalogLoad != null || configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED);
    }

    boolean acceptsContentDrop() {
        return state == State.ACTIVE && !quitPrompt && !childInputPending
                && romLocator == null && onboarding == null && settingsScreen == null && launchConfigPanel == null && modManagerScreen == null
                && playbackBrowser == null && catalogLoad == null;
    }

    public interface ModManagerView {
        default void setGameFilter(String gameId) { }
        void update(InputHandler input);
        void render();
        boolean consumeCloseRequested();
        void suppressInputUntilNeutral();
        /** This frame's mouse in the manager's 320-pixel page, before {@link #update}. */
        default void pointer(int x, int y, boolean moved, boolean click, boolean backClick, int wheel) { }
        default boolean consumeActivationRequested() { return false; }
        default void activationFailed(String message) { }
    }
}
