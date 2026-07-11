package com.openggf.game.timeattack;

import com.openggf.control.InputHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

/**
 * Pure input/selection logic for {@link TimeAttackMenu}: game &rarr; track
 * &rarr; character columns, a ghost summary for the current selection, and
 * GO/BACK. Kept GL-free so it is unit testable with a stub {@link GhostStore}.
 */
@com.openggf.game.ModApi
public final class TimeAttackMenuState {
    private static final Logger LOGGER = Logger.getLogger(TimeAttackMenuState.class.getName());

    /** Ghosts raced alongside the player, in addition to the auto-loaded best. */
    static final int MAX_GHOSTS_RACED = 7;

    @com.openggf.game.ModApi
    public enum Row { GAME, TRACK, CHARACTER, MODE, POLICY, WINDOW }
    @com.openggf.game.ModApi
    public enum Mode { SOLO, HOST_LAN, JOIN_LAN, BROWSE }

    private static final int[] WINDOW_MINUTES = {1, 2, 5, 10};

    private final List<String> games;
    private final GhostStore ghostStore;
    private int gameIndex;
    private int trackIndex;
    private int characterIndex;
    private int modeIndex;
    private int policyIndex;
    private int windowIndex = 2;
    private Row focusedRow = Row.GAME;
    private boolean closeRequested;
    private TimeAttackLaunchRequest launchRequest;
    private boolean bestExists;
    private int importCount;

    public TimeAttackMenuState(List<String> availableGameIds, String initialGameId, GhostStore ghostStore) {
        this.games = List.copyOf(Objects.requireNonNull(availableGameIds, "availableGameIds"));
        if (this.games.isEmpty()) {
            throw new IllegalArgumentException("At least one ROM-available game is required");
        }
        this.ghostStore = Objects.requireNonNull(ghostStore, "ghostStore");
        int initial = this.games.indexOf(initialGameId);
        this.gameIndex = initial >= 0 ? initial : 0;
        refreshGhostSummary();
    }

    public void update(InputHandler input) {
        Objects.requireNonNull(input, "input");
        var logical = input.logical();
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_UP) || logical.menuUp()) {
            moveFocus(-1);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_DOWN) || logical.menuDown()) {
            moveFocus(1);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_LEFT) || logical.menuLeft()) {
            adjust(-1);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_RIGHT) || logical.menuRight()) {
            adjust(1);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_ENTER) || logical.menuAccept()) {
            pressGo();
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_ESCAPE) || logical.menuBack()) {
            closeRequested = true;
        }
    }

    public void moveFocus(int delta) {
        Row[] rows = Row.values();
        int index = wrap(focusedRow.ordinal() + delta, rows.length);
        focusedRow = rows[index];
    }

    public void adjust(int delta) {
        switch (focusedRow) {
            case GAME -> {
                gameIndex = wrap(gameIndex + delta, games.size());
                trackIndex = 0;
                characterIndex = 0;
                refreshGhostSummary();
            }
            case TRACK -> {
                int trackCount = currentTracks().size();
                if (trackCount == 0) {
                    return;
                }
                trackIndex = wrap(trackIndex + delta, trackCount);
                characterIndex = 0;
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
            case MODE -> modeIndex = wrap(modeIndex + delta, Mode.values().length);
            case POLICY -> policyIndex = wrap(policyIndex + delta, 2);
            case WINDOW -> windowIndex = wrap(windowIndex + delta, WINDOW_MINUTES.length);
        }
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
                    .limit(MAX_GHOSTS_RACED)
                    .toList();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed listing ghost imports for " + currentGameId(), e);
            extraGhosts = List.of();
        }
        launchRequest = new TimeAttackLaunchRequest(currentGameId(), track.zone(), track.act(), character, extraGhosts);
    }

    private void refreshGhostSummary() {
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

    public List<TimeAttackTrackCatalog.Track> currentTracks() {
        return TimeAttackTrackCatalog.tracksFor(currentGameId());
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
