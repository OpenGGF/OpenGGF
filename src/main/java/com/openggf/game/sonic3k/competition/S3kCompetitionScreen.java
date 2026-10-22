package com.openggf.game.sonic3k.competition;

import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;

import java.util.List;
import java.util.Objects;

/** Native Competition_Menu / PlayerSelect / LevelSelect / Results consumer. */
public final class S3kCompetitionScreen implements NativeMenuScreen {
    public enum Phase { MODE, PLAYER_SELECT, ARENA_SELECT, RACE, RESULTS, TIME_ATTACK_RESULTS }
    public record Launch(S3kCompetitionSession.Config config, List<S3kCompetitionPlayer.Character> characters) {
        public Launch {
            characters = List.copyOf(characters);
            if (characters.size() != config.participantCount()) throw new IllegalArgumentException("Launch roster differs from mode");
        }
    }
    /** ROM render state, including the selection wheel and results scroll positions. */
    public record MenuView(Phase phase, int frame, int modeRow, boolean itemsEnabled,
            S3kCompetitionSession.Mode mode, int arenaRow, int participants,
            List<Integer> characters, List<Integer> wheelAngles, List<Boolean> confirmed,
            int horizontalScroll, int verticalScroll, S3kCompetitionSession.Result result) { }

    /** Implemented by the shared native world/view adapter; no ordinary gameplay bootstrap. */
    public interface Host {
        S3kCompetitionSession openArena(Launch launch);
        void stepArena(LogicalInputSnapshot input, S3kCompetitionSession session);
        void drawArena();
        void drawMenu(MenuView view);
        void setClearColor();
        void closeArena();
        default void playSfx(Sonic3kSfx sound) { }
    }

    private final Host host;
    private Phase phase = Phase.MODE;
    private int frame, modeRow, arenaRow, participants = 1;
    private boolean itemsEnabled = true;
    private S3kCompetitionSession.Mode selectedMode = S3kCompetitionSession.Mode.GRAND_PRIX;
    private final int[] characters = {0, 1}, wheelAngles = {0x40, 0x95}, wheelTargets = {0x40, 0x95};
    private final int[] wheelDirections = new int[2];
    private final boolean[] confirmed = new boolean[2];
    private int horizontalScroll, verticalScroll, scrollRemaining, resultHold;
    private boolean resultSliding;
    private S3kCompetitionSession session;
    private S3kCompetitionSession.Arena pendingArena;
    private S3kCompetitionSession.Result pendingResult, result;
    private Exit exit = Exit.NONE;

    public S3kCompetitionScreen(Host host) { this.host = Objects.requireNonNull(host); }
    @Override public void initialize() {
        phase = Phase.MODE; frame = 0; modeRow = Math.min(modeRow, 2);
        exit = Exit.NONE; result = null; session = null; pendingArena = null; pendingResult = null;
    }
    @Override public GameMode mode() {
        return phase == Phase.RACE ? GameMode.COMPETITION
                : phase == Phase.RESULTS || phase == Phase.TIME_ATTACK_RESULTS ? GameMode.COMPETITION_RESULTS : GameMode.COMPETITION_MENU;
    }
    @Override public void update(InputHandler input) { update(input.logical()); }
    public void update(LogicalInputSnapshot input) {
        Objects.requireNonNull(input);
        frame = frame + 1 & 0xFFFF;
        switch (phase) {
            case MODE -> updateMode(input);
            case PLAYER_SELECT, ARENA_SELECT -> updateSelection(input);
            case RACE -> {
                host.stepArena(input, session);
                if (pendingArena != null) {
                    var next = pendingArena; pendingArena = null;
                    openArena(next);
                } else if (pendingResult != null) {
                    result = pendingResult; pendingResult = null;
                    host.closeArena(); session = null;
                    if (selectedMode == S3kCompetitionSession.Mode.MATCH_RACE) enterSelection();
                    else {
                        phase = selectedMode == S3kCompetitionSession.Mode.TIME_ATTACK ? Phase.TIME_ATTACK_RESULTS : Phase.RESULTS;
                        horizontalScroll = 0; resultHold = 30; resultSliding = false;
                    }
                }
            }
            case RESULTS, TIME_ATTACK_RESULTS -> updateResults(input);
        }
    }

    private void updateMode(LogicalInputSnapshot input) {
        PlayerInputState both = input.player1().merge(input.player2());
        // The item arrow object processes left/right before the screen consumes buttons.
        if (modeRow <= 1 && (both.pressedMask() & 0xC) != 0) {
            itemsEnabled = !itemsEnabled; host.playSfx(Sonic3kSfx.SWITCH);
        }
        if (back(both)) { exit = new Exit(Destination.TITLE_SCREEN, null, false); return; }
        if (accept(both) && (modeRow != 2 || accept(input.player1()))) {
            if (modeRow == 3) { exit = new Exit(Destination.TITLE_SCREEN, null, false); return; }
            // loc_AA74 accepts Time Attack only from controller one.
            selectedMode = S3kCompetitionSession.Mode.values()[modeRow];
            participants = selectedMode == S3kCompetitionSession.Mode.TIME_ATTACK ? 1 : accept(input.player2()) ? 2 : 1;
            host.playSfx(Sonic3kSfx.STARPOST); enterSelection(); return;
        }
        if ((both.pressedMask() & 1) != 0) {
            modeRow = modeRow + 3 & 3; host.playSfx(Sonic3kSfx.SWITCH);
        } else if ((both.pressedMask() & 2) != 0) {
            modeRow = modeRow + 1 & 3; host.playSfx(Sonic3kSfx.SWITCH);
        }
    }

    private void enterSelection() {
        phase = selectedMode == S3kCompetitionSession.Mode.GRAND_PRIX ? Phase.PLAYER_SELECT : Phase.ARENA_SELECT;
        confirmed[0] = confirmed[1] = false;
        wheelDirections[0] = wheelDirections[1] = 0;
        for (int slot = 0; slot < 2; slot++) wheelAngles[slot] = wheelTargets[slot] = (0x40 + 0x55 * characters[slot]) & 0xFF;
        horizontalScroll = 0; scrollRemaining = 0;
        verticalScroll = Math.clamp(arenaRow - 1, 0, 2) * 0x48;
    }

    private void updateSelection(LogicalInputSnapshot input) {
        if (horizontalScroll == 0 && selectedMode != S3kCompetitionSession.Mode.TIME_ATTACK
                && participants == 1 && input.player2().startPressed()) participants = 2;
        if (horizontalScroll == 0) {
            for (int slot = 0; slot < participants; slot++) {
                var buttons = slot == 0 ? input.player1() : input.player2();
                if (confirmed[slot]) { if (back(buttons)) confirmed[slot] = false; continue; }
                if (wheelDirections[slot] != 0) { rotateWheel(slot); continue; }
                int direction = (buttons.pressedMask() & 4) != 0 ? -1 : (buttons.pressedMask() & 8) != 0 ? 1 : 0;
                if (direction != 0) {
                    characters[slot] = (characters[slot] + direction + 3) % 3;
                    int target = characters[slot] * 0x55;
                    if (direction > 0 && target == 0) target = 0x100;
                    if (direction < 0 && target >= 0x80) target = (byte) target;
                    wheelAngles[slot] &= 0xFF; wheelTargets[slot] = target + 0x40;
                    wheelDirections[slot] = direction; rotateWheel(slot);
                    host.playSfx(Sonic3kSfx.GRAVITY_TUNNEL);
                } else if (back(buttons)) { phase = Phase.MODE; return; }
                else if (accept(buttons)) { confirmed[slot] = true; host.playSfx(Sonic3kSfx.STARPOST); }
            }
            if (!allConfirmed()) return;
            if (phase == Phase.PLAYER_SELECT) { openArena(S3kCompetitionSession.Arena.AZURE_LAKE); return; }
        }
        if (!allConfirmed()) { horizontalScroll = Math.min(0, horizontalScroll + 8); return; }
        if (horizontalScroll > -0x58) { horizontalScroll -= 8; return; }
        if (scrollRemaining != 0) {
            int step = Integer.signum(scrollRemaining) * 8;
            verticalScroll += step; scrollRemaining -= step; return;
        }
        PlayerInputState both = participants == 2 ? input.player1().merge(input.player2()) : input.player1();
        if (back(input.player1())) confirmed[0] = false;
        if (participants == 2 && back(input.player2())) confirmed[1] = false;
        int previous = arenaRow;
        if ((both.pressedMask() & 1) != 0) arenaRow = Math.max(0, arenaRow - 1);
        else if ((both.pressedMask() & 2) != 0) arenaRow = Math.min(4, arenaRow + 1);
        if (arenaRow != previous) {
            scrollRemaining = Math.clamp(arenaRow - 1, 0, 2) * 0x48 - verticalScroll;
            host.playSfx(Sonic3kSfx.SWITCH); return;
        }
        if (accept(both) && allConfirmed()) openArena(S3kCompetitionSession.GRAND_PRIX_ORDER.get(arenaRow));
    }
    private void rotateWheel(int slot) {
        int next = wheelAngles[slot] + 5 * wheelDirections[slot];
        if (wheelDirections[slot] > 0 ? next >= wheelTargets[slot] : next <= wheelTargets[slot]) {
            next = wheelTargets[slot]; wheelDirections[slot] = 0;
        }
        wheelAngles[slot] = next;
    }
    private boolean allConfirmed() { return confirmed[0] && (participants == 1 || confirmed[1]); }
    private void openArena(S3kCompetitionSession.Arena arena) {
        var chars = java.util.stream.IntStream.range(0, participants)
                .mapToObj(slot -> S3kCompetitionPlayer.Character.values()[characters[slot]]).toList();
        var launch = new Launch(new S3kCompetitionSession.Config(selectedMode, arena,
                itemsEnabled && selectedMode != S3kCompetitionSession.Mode.TIME_ATTACK, participants), chars);
        session = Objects.requireNonNull(host.openArena(launch), "Native world must supply its real S3K session");
        phase = Phase.RACE;
    }
    private void updateResults(LogicalInputSnapshot input) {
        if (resultHold != 0) { resultHold--; return; }
        if (phase == Phase.TIME_ATTACK_RESULTS) {
            if (accept(input.player1())) enterSelection();
            return;
        }
        if (resultSliding) {
            horizontalScroll -= 8;
            if (horizontalScroll == -0x88) resultSliding = false;
            return;
        }
        var buttons = participants == 2 ? input.player1().merge(input.player2()) : input.player1();
        if (!accept(buttons)) return;
        if (horizontalScroll == 0) { resultSliding = true; horizontalScroll = -8; }
        else phase = Phase.MODE;
    }
    public void requestArena(S3kCompetitionSession.Arena arena) { pendingArena = Objects.requireNonNull(arena); }
    public void requestResults(S3kCompetitionSession.Result result) { pendingResult = Objects.requireNonNull(result); }
    public S3kCompetitionSession session() { return session; }
    public MenuView view() {
        return new MenuView(phase, frame, modeRow, itemsEnabled, selectedMode, arenaRow, participants,
                List.of(characters[0], characters[1]), List.of(wheelAngles[0] & 0xFF, wheelAngles[1] & 0xFF),
                List.of(confirmed[0], confirmed[1]), horizontalScroll, verticalScroll, result);
    }
    @Override public void draw() { if (phase == Phase.RACE) host.drawArena(); else host.drawMenu(view()); }
    @Override public void setClearColor() { host.setClearColor(); }
    @Override public Exit consumeExit() { Exit value = exit; exit = Exit.NONE; return value; }
    @Override public void reset() { host.closeArena(); initialize(); }
    private static boolean back(PlayerInputState input) { return (input.actionPressedMask() & InputActionMasks.ACTION_B) != 0; }
    private static boolean accept(PlayerInputState input) { return input.startPressed()
            || (input.actionPressedMask() & (InputActionMasks.ACTION_A | InputActionMasks.ACTION_C)) != 0; }
}
