package openggf.timeattack;


import com.openggf.mods.run.MonitorRule;
import com.openggf.mods.run.TrackKind;
import openggf.timeattack.mp.VoteTrackPools;

import java.io.IOException;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pure input/selection logic for {@link TimeAttackMenu}: game &rarr; track
 * &rarr; character columns, a ghost summary for the current selection, and
 * GO/BACK. Kept GL-free so it is unit testable with a stub {@link GhostStore}.
 */
public final class TimeAttackMenuState {
    private final Logger LOGGER = Logger.getLogger(TimeAttackMenuState.class.getName());

    /** Ghosts raced alongside the player, in addition to the auto-loaded best. */
    static final int MAX_GHOSTS_RACED = 7;

    /**
     * Menu rows. RACE_MODE picks Time Attack or Competition for a hosted room; MONITORS, CUTOFF and
     * START_IN are Competition's host settings and WINDOW is Time Attack's, so each mode hides the
     * other's rows.
     */
    public enum Row { GAME, TRACK, CHARACTER, MODE, RACE_MODE, POLICY, WINDOW, MONITORS, CUTOFF, START_IN, POOL }

    /**
     * The saved custom vote pools a hosted room can use, and the one chosen per game (the
     * scene backs it with {@link TimeAttackSettings}).
     */
    public interface PoolChoices {
        /** Saved pool names of {@code gameId}, in order. */
        List<String> names(String gameId);

        /** The chosen pool of {@code gameId}; empty for the game's default pool. */
        String chosen(String gameId);

        /** Records the choice (empty selects the default pool). */
        void choose(String gameId, String name);

        /**
         * The protocol track keys of pool {@code name} of {@code gameId} (empty name: the default
         * pool), which decide the Competition monitor choices the room may offer.
         */
        default List<String> keys(String gameId, String name) {
            return name == null || name.isEmpty() ? VoteTrackPools.forGame(gameId) : List.of();
        }

        /** No saved pools: every room uses its game's default pool. */
        static PoolChoices none() {
            return new PoolChoices() {
                @Override public List<String> names(String gameId) { return List.of(); }
                @Override public String chosen(String gameId) { return ""; }
                @Override public void choose(String gameId, String name) { }
            };
        }
    }
    public enum Mode { SOLO, HOST_LAN, JOIN_LAN, BROWSE }

    private final int[] WINDOW_MINUTES = {1, 2, 5, 10};

    private final List<String> games;
    private final GhostStore ghostStore;
    private final PoolChoices pools;
    private boolean poolEditorRequested;
    private int gameIndex;
    private int trackIndex;
    private int characterIndex;
    private int modeIndex;
    private int policyIndex;
    private int windowIndex = 2;
    private CompetitionRoomSettings.Mode raceMode = CompetitionRoomSettings.Mode.TIME_ATTACK;
    private MonitorRule monitors = MonitorRule.NORMAL;
    private int cutoffIndex = CompetitionRoomSettings.cutoffChoices().indexOf(
            CompetitionRoomSettings.defaults().cutoffSeconds());
    private int startInIndex = CompetitionRoomSettings.startInChoices().indexOf(
            CompetitionRoomSettings.defaults().startInSeconds());
    private Row focusedRow = Row.GAME;
    private boolean closeRequested;
    private TimeAttackLaunchRequest launchRequest;
    private boolean bestExists;
    private int importCount;

    public TimeAttackMenuState(List<String> availableGameIds, String initialGameId, GhostStore ghostStore) {
        this(availableGameIds, initialGameId, ghostStore, PoolChoices.none());
    }

    public TimeAttackMenuState(List<String> availableGameIds, String initialGameId, GhostStore ghostStore,
                               PoolChoices pools) {
        this.pools = Objects.requireNonNull(pools, "pools");
        this.games = List.copyOf(Objects.requireNonNull(availableGameIds, "availableGameIds"));
        if (this.games.isEmpty()) {
            throw new IllegalArgumentException("At least one ROM-available game is required");
        }
        this.ghostStore = Objects.requireNonNull(ghostStore, "ghostStore");
        int initial = this.games.indexOf(initialGameId);
        this.gameIndex = initial >= 0 ? initial : 0;
        refreshGhostSummary();
    }

    /** Edge-triggered menu input for one tick (the scene maps buttons and keys to it). */
    public record MenuKeys(boolean up, boolean down, boolean left, boolean right, boolean accept, boolean back) {
    }

    public void update(MenuKeys keys) {
        Objects.requireNonNull(keys, "keys");
        if (keys.back()) {
            closeRequested = true;
            return;
        }
        if (keys.up()) {
            moveFocus(-1);
        }
        if (keys.down()) {
            moveFocus(1);
        }
        if (keys.left()) {
            adjust(-1);
        }
        if (keys.right()) {
            adjust(1);
        }
        if (keys.accept()) {
            if (focusedRow == Row.POOL) {
                poolEditorRequested = true;
            } else {
                pressGo();
            }
        }
    }

    public void moveFocus(int delta) {
        List<Row> rows = visibleRows();
        int index = wrap(rows.indexOf(focusedRow) + delta, rows.size());
        focusedRow = rows.get(index);
    }

    public List<Row> visibleRows() {
        if (!hosting()) return List.of(Row.GAME, Row.TRACK, Row.CHARACTER, Row.MODE);
        return raceMode() == CompetitionRoomSettings.Mode.COMPETITION
                ? List.of(Row.GAME, Row.TRACK, Row.CHARACTER, Row.MODE, Row.RACE_MODE, Row.POLICY, Row.MONITORS,
                        Row.CUTOFF, Row.START_IN, Row.POOL)
                : List.of(Row.GAME, Row.TRACK, Row.CHARACTER, Row.MODE, Row.RACE_MODE, Row.POLICY, Row.WINDOW,
                        Row.POOL);
    }

    /** True for the modes that open a room, whose host settings the menu shows. */
    private boolean hosting() {
        return mode() == Mode.HOST_LAN || mode() == Mode.BROWSE;
    }

    public void adjust(int delta) {
        switch (focusedRow) {
            case GAME -> {
                gameIndex = wrap(gameIndex + delta, games.size());
                trackIndex = 0;
                characterIndex = 0;
                if (!legalRaceModes().contains(raceMode)) raceMode = CompetitionRoomSettings.Mode.TIME_ATTACK;
                snapMonitors();
                refreshGhostSummary();
            }
            case TRACK -> {
                int trackCount = currentTracks().size();
                if (trackCount == 0) {
                    return;
                }
                trackIndex = wrap(trackIndex + delta, trackCount);
                characterIndex = 0;
                snapMonitors();
                refreshGhostSummary();
            }
            case CHARACTER -> {
                int characterCount = currentCharacters().size();
                if (characterCount == 0) {
                    return;
                }
                characterIndex = wrap(characterIndex + delta, characterCount);
                refreshGhostSummary();
            }
            case MODE -> {
                TimeAttackTrackCatalog.Track before = currentTrack();
                modeIndex = wrap(modeIndex + delta, Mode.values().length);
                keepTrack(before);
            }
            case RACE_MODE -> {
                List<CompetitionRoomSettings.Mode> modes = legalRaceModes();
                TimeAttackTrackCatalog.Track before = currentTrack();
                raceMode = modes.get(wrap(modes.indexOf(raceMode) + delta, modes.size()));
                keepTrack(before);
            }
            case POLICY -> policyIndex = wrap(policyIndex + delta, 2);
            case WINDOW -> windowIndex = wrap(windowIndex + delta, WINDOW_MINUTES.length);
            case MONITORS -> {
                List<MonitorRule> legal = legalMonitors();
                if (!legal.isEmpty()) monitors = legal.get(wrap(legal.indexOf(monitors) + delta, legal.size()));
            }
            case CUTOFF -> cutoffIndex = wrap(cutoffIndex + delta, CompetitionRoomSettings.cutoffChoices().size());
            case START_IN -> startInIndex = wrap(startInIndex + delta, CompetitionRoomSettings.startInChoices().size());
            case POOL -> {
                List<String> names = pools.names(currentGameId());
                int index = names.indexOf(currentPoolName()) + 1; // 0 = the default pool
                int next = wrap(index + delta, names.size() + 1);
                pools.choose(currentGameId(), next == 0 ? "" : names.get(next - 1));
                snapMonitors();
            }
        }
    }

    /**
     * After the track list changes (mode or race mode), stays on the same track when the new list
     * has it; an arena left behind by Time Attack falls back to the first track.
     */
    private void keepTrack(TimeAttackTrackCatalog.Track before) {
        List<TimeAttackTrackCatalog.Track> tracks = currentTracks();
        int index = before == null ? -1 : tracks.indexOf(before);
        if (index < 0) {
            trackIndex = 0;
            characterIndex = 0;
        } else {
            trackIndex = index;
        }
        snapMonitors();
        refreshGhostSummary();
    }

    /** Keeps the monitor choice legal for the race mode, track and pool (D13, D21). */
    private void snapMonitors() {
        List<MonitorRule> legal = legalMonitors();
        if (!legal.contains(monitors)) monitors = legal.isEmpty() ? MonitorRule.NORMAL : legal.getFirst();
    }

    public void pressGo() {
        TimeAttackTrackCatalog.Track track = currentTrack();
        List<String> characters = currentCharacters();
        if (track == null || characters.isEmpty()) {
            return;
        }
        String character = characters.get(characterIndex);
        List<Path> extraGhosts;
        try {
            extraGhosts = ghostStore.listImports(currentGameId()).stream()
                    .filter(path -> matchesTrack(path, track))
                    .limit(MAX_GHOSTS_RACED)
                    .toList();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed listing ghost imports for " + currentGameId(), e);
            extraGhosts = List.of();
        }
        launchRequest = new TimeAttackLaunchRequest(currentGameId(), track.zone(), track.act(), character, extraGhosts);
    }

    private boolean matchesTrack(Path path, TimeAttackTrackCatalog.Track track) {
        try {
            var header = openggf.timeattack.ghost.GhostFileCodec.read(path).header();
            return header.gameId().equals(currentGameId())
                    && header.zone() == track.zone() && header.act() == track.act();
        } catch (IOException invalid) {
            LOGGER.log(Level.WARNING, "Skipping unreadable ghost import " + path, invalid);
            return false;
        }
    }

    public void refreshGhostSummary() {
        TimeAttackTrackCatalog.Track track = currentTrack();
        List<String> characters = currentCharacters();
        if (track == null || characters.isEmpty()) {
            bestExists = false;
            importCount = 0;
            return;
        }
        String character = characters.get(Math.min(characterIndex, characters.size() - 1));
        try {
            bestExists = ghostStore.loadBest(currentGameId(), track.zone(), track.act(), character).isPresent();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed loading best ghost for menu summary", e);
            bestExists = false;
        }
        try {
            importCount = ghostStore.listImports(currentGameId()).size();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed listing ghost imports for menu summary", e);
            importCount = 0;
        }
    }

    private static int wrap(int value, int size) {
        return ((value % size) + size) % size;
    }

    public List<String> games() {
        return games;
    }

    public String currentGameId() {
        return games.get(gameIndex);
    }

    /**
     * The track row's choices: the curated acts, and in a Competition room also the acts' ARENAS
     * group (S3K's five Competition arenas, which race in no other mode). Acts that do not race
     * in the room's mode (Sonic 1 in Competition) are left out.
     */
    public List<TimeAttackTrackCatalog.Track> currentTracks() {
        CompetitionRoomSettings.Mode mode = raceMode();
        List<TimeAttackTrackCatalog.Track> tracks = new ArrayList<>();
        for (TimeAttackTrackCatalog.Track track : TimeAttackTrackCatalog.tracksFor(currentGameId())) {
            if (track.modes().contains(mode)) tracks.add(track);
        }
        if (mode == CompetitionRoomSettings.Mode.COMPETITION && "s3k".equals(currentGameId())) {
            tracks.addAll(TimeAttackTrackCatalog.arenas());
        }
        return List.copyOf(tracks);
    }

    /** The race modes the current game offers: Sonic 1 races Time Attack only (D2). */
    public List<CompetitionRoomSettings.Mode> legalRaceModes() {
        List<CompetitionRoomSettings.Mode> modes = new ArrayList<>();
        for (CompetitionRoomSettings.Mode mode : CompetitionRoomSettings.Mode.values()) {
            boolean raced = TimeAttackTrackCatalog.tracksFor(currentGameId()).stream()
                    .anyMatch(track -> track.modes().contains(mode))
                    || mode == CompetitionRoomSettings.Mode.COMPETITION && "s3k".equals(currentGameId());
            if (raced) modes.add(mode);
        }
        return modes.isEmpty() ? List.of(CompetitionRoomSettings.Mode.TIME_ATTACK) : List.copyOf(modes);
    }

    /** The room's race mode: the chosen one while hosting; solo runs and joins are Time Attack. */
    public CompetitionRoomSettings.Mode raceMode() {
        return hosting() ? raceMode : CompetitionRoomSettings.Mode.TIME_ATTACK;
    }

    /**
     * The tracks the room can race: the chosen pool's tracks and the menu's track. The monitor
     * choices are the ones every one of them permits.
     */
    private List<TimeAttackTrackCatalog.Track> roomTracks() {
        List<TimeAttackTrackCatalog.Track> tracks = new ArrayList<>();
        for (String key : pools.keys(currentGameId(), currentPoolName())) {
            TimeAttackTrackCatalog.forKey(key).filter(track -> !tracks.contains(track)).ifPresent(tracks::add);
        }
        TimeAttackTrackCatalog.Track track = currentTrack();
        if (track != null && !tracks.contains(track)) tracks.add(track);
        return tracks;
    }

    /**
     * The monitor settings the room may use (design 7): only the values every track of the room
     * permits in its mode; empty when the pool cannot race in this mode at all.
     */
    public List<MonitorRule> legalMonitors() {
        return CompetitionRoomSettings.legalMonitors(raceMode(), roomTracks());
    }

    /** The chosen pool holds a track that does not race in the room's mode (arenas in Time Attack). */
    public boolean poolIllegalForMode() {
        return legalMonitors().isEmpty();
    }

    /**
     * The room's Competition settings as the host chose them. Time Attack always races normal
     * monitors; a choice the room's tracks no longer permit falls back to the first legal one.
     */
    public CompetitionRoomSettings competitionSettings() {
        CompetitionRoomSettings.Mode mode = raceMode();
        MonitorRule rule = mode == CompetitionRoomSettings.Mode.TIME_ATTACK ? MonitorRule.NORMAL : monitors;
        return new CompetitionRoomSettings(mode, rule, cutoffSeconds(), startInSeconds());
    }

    public MonitorRule monitors() {
        return competitionSettings().monitors();
    }

    public int cutoffSeconds() {
        return CompetitionRoomSettings.cutoffChoices().get(cutoffIndex);
    }

    public int startInSeconds() {
        return CompetitionRoomSettings.startInChoices().get(startInIndex);
    }

    /** The label of a host-settings row this class owns ({@code RACE_MODE}, {@code MONITORS}, ...). */
    public static String settingLabel(Row row) {
        return switch (row) {
            case RACE_MODE -> "Race mode";
            case MONITORS -> "Monitors";
            case CUTOFF -> "Cutoff";
            case START_IN -> "Start in";
            default -> row.name();
        };
    }

    /** The value of a host-settings row, in sentence case as the menu shows it. */
    public String settingValue(Row row) {
        return switch (row) {
            case RACE_MODE -> legalRaceModes().size() < 2 ? raceModeName(raceMode()) + " only"
                    : raceModeName(raceMode());
            case MONITORS -> poolIllegalForMode() ? "Pool not raceable" : monitorName(monitors());
            case CUTOFF -> cutoffSeconds() + " s after the winner";
            case START_IN -> startInSeconds() + " s";
            default -> "";
        };
    }

    public static String raceModeName(CompetitionRoomSettings.Mode mode) {
        return mode == CompetitionRoomSettings.Mode.COMPETITION ? "Competition" : "Time Attack";
    }

    public static String monitorName(MonitorRule rule) {
        return switch (rule) {
            case NORMAL -> "Normal";
            case RANDOMISED -> "Randomised";
            case COMPETITION_ITEMS -> "Competition items";
            case OFF -> "Off";
        };
    }

    /**
     * Tags the menu draws on the focused track row only: its kind and the modes it races in
     * ({@code ARENA}, {@code COMPETITION}). Every other row has none.
     */
    public List<String> focusBadges(Row row) {
        TimeAttackTrackCatalog.Track track = currentTrack();
        if (row != Row.TRACK || track == null || !hosting()) return List.of();
        String kind = track.kind() == TrackKind.ARENA ? track.lapCount() + " LAPS" : "ACT";
        String modes = track.modes().size() > 1 ? "BOTH MODES"
                : track.modes().contains(CompetitionRoomSettings.Mode.COMPETITION) ? "COMPETITION" : "TIME ATTACK";
        return List.of(kind, modes);
    }

    /** The track's group as the pool editor captions it. */
    public static String groupName(TimeAttackTrackCatalog.Group group) {
        return group == TimeAttackTrackCatalog.Group.ARENAS ? "Arenas" : "Acts";
    }

    public TimeAttackTrackCatalog.Track currentTrack() {
        List<TimeAttackTrackCatalog.Track> tracks = currentTracks();
        return trackIndex < tracks.size() ? tracks.get(trackIndex) : null;
    }

    public List<String> currentCharacters() {
        TimeAttackTrackCatalog.Track track = currentTrack();
        return track == null ? List.of() : track.characters();
    }

    public String currentCharacter() {
        List<String> characters = currentCharacters();
        return characterIndex < characters.size() ? characters.get(characterIndex) : null;
    }

    public int gameIndex() {
        return gameIndex;
    }

    public int trackIndex() {
        return trackIndex;
    }

    public int characterIndex() {
        return characterIndex;
    }

    public Row focusedRow() {
        return focusedRow;
    }

    public Mode mode() {
        return Mode.values()[modeIndex];
    }

    public String characterPolicy() {
        return policyIndex == 0 ? "OPEN" : "LOCKED";
    }

    public String lockedCharacter() {
        return policyIndex == 0 ? null : currentCharacter();
    }

    /** The custom pool hosted rooms of the current game vote from; empty for the default pool. */
    public String currentPoolName() {
        String chosen = pools.chosen(currentGameId());
        return pools.names(currentGameId()).contains(chosen) ? chosen : "";
    }

    /** True once after the player confirms the pool row (the scene opens the pool editor). */
    public boolean consumePoolEditorRequested() {
        boolean result = poolEditorRequested;
        poolEditorRequested = false;
        return result;
    }

    public int windowSeconds() {
        return WINDOW_MINUTES[windowIndex] * 60;
    }

    public boolean bestExists() {
        return bestExists;
    }

    public int importCount() {
        return importCount;
    }

    public boolean consumeCloseRequested() {
        boolean result = closeRequested;
        closeRequested = false;
        return result;
    }

    public TimeAttackLaunchRequest consumeLaunchRequest() {
        TimeAttackLaunchRequest result = launchRequest;
        launchRequest = null;
        return result;
    }

    /** Test-only: force a pending launch request as if GO had been confirmed. */
    public void forceLaunchRequestForTest(TimeAttackLaunchRequest request) {
        this.launchRequest = request;
    }
}
