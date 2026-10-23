package com.openggf.game.sonic3k.competition;

import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.RenderPriority;
import com.openggf.physics.TrigLookupTable;

import java.util.Arrays;
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
            int horizontalScroll, int verticalScroll, S3kCompetitionSession.Result result,
            List<Integer> wheelQueuePriorityWords, boolean mainLoopStarted) {
        public MenuView {
            characters = List.copyOf(characters); wheelAngles = List.copyOf(wheelAngles);
            confirmed = List.copyOf(confirmed); wheelQueuePriorityWords = List.copyOf(wheelQueuePriorityWords);
            if (!wheelQueuePriorityWords.isEmpty() && wheelQueuePriorityWords.size() != 6)
                throw new IllegalArgumentException("Native wheel queue contains six priority words");
            wheelQueuePriorityWords.forEach(RenderPriority::fromS3kWord);
        }
        public MenuView(Phase phase, int frame, int modeRow, boolean itemsEnabled,
                S3kCompetitionSession.Mode mode, int arenaRow, int participants,
                List<Integer> characters, List<Integer> wheelAngles, List<Boolean> confirmed,
                int horizontalScroll, int verticalScroll, S3kCompetitionSession.Result result,
                List<Integer> wheelQueuePriorityWords) {
            this(phase,frame,modeRow,itemsEnabled,mode,arenaRow,participants,characters,wheelAngles,
                    confirmed,horizontalScroll,verticalScroll,result,wheelQueuePriorityWords,true);
        }
        /** Synthetic views without a prior native actor pass use current depth ordering. */
        public MenuView(Phase phase, int frame, int modeRow, boolean itemsEnabled,
                S3kCompetitionSession.Mode mode, int arenaRow, int participants,
                List<Integer> characters, List<Integer> wheelAngles, List<Boolean> confirmed,
                int horizontalScroll, int verticalScroll, S3kCompetitionSession.Result result) {
            this(phase,frame,modeRow,itemsEnabled,mode,arenaRow,participants,characters,wheelAngles,
                    confirmed,horizontalScroll,verticalScroll,result,List.of(),true);
        }
    }

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
    // Native actor words and the queue already admitted by this frame's Process_Sprites.
    // Flat player*3 + source object parameter; mapping-frame order is 0,2,1.
    private final int[] wheelPriorityWords = new int[6], wheelQueuePriorityWords = new int[6];
    private final boolean[] confirmed = new boolean[2];
    private int horizontalScroll, verticalScroll, scrollRemaining, resultHold;
    private boolean resultSliding;
    private boolean mainLoopStarted;
    private S3kCompetitionSession session;
    private S3kCompetitionSession.Arena pendingArena;
    private Launch pendingLaunch;
    private S3kCompetitionSession.Result pendingResult, result;
    private boolean pendingTimeAttackSelection, pendingTitleScreen;
    private Exit exit = Exit.NONE;

    public S3kCompetitionScreen(Host host) { this.host = Objects.requireNonNull(host); }
    @Override public void initialize() {
        enterMode(); Arrays.fill(wheelPriorityWords,0); Arrays.fill(wheelQueuePriorityWords,0);
        exit = Exit.NONE; result = null; session = null; pendingArena = null; pendingResult = null; pendingLaunch = null;
        pendingTimeAttackSelection = pendingTitleScreen = false;
    }
    @Override public GameMode mode() {
        return phase == Phase.RACE ? GameMode.COMPETITION
                : phase == Phase.RESULTS || phase == Phase.TIME_ATTACK_RESULTS ? GameMode.COMPETITION_RESULTS : GameMode.COMPETITION_MENU;
    }
    @Override public void update(InputHandler input) { update(input.logical()); }
    public void update(LogicalInputSnapshot input) {
        pendingLaunch = null;
        Objects.requireNonNull(input);
        frame = frame + 1 & 0xFFFF;
        mainLoopStarted = true;
        switch (phase) {
            case MODE -> updateMode(input);
            case PLAYER_SELECT, ARENA_SELECT -> {
                // loc_ADF2/loc_B4A8: Process_Sprites queues old priority words;
                // sub_C0AE changes the actor words after their position update.
                System.arraycopy(wheelPriorityWords,0,wheelQueuePriorityWords,0,6);
                updateSelection(input);
                if (phase == Phase.PLAYER_SELECT || phase == Phase.ARENA_SELECT) updateWheelDepth();
            }
            case RACE -> {
                host.stepArena(input, session);
                if (pendingTitleScreen || pendingTimeAttackSelection) {
                    // Pause_Game returns through loc_6678's ordinary wait/body.
                    // Its mode write is consumed only after that body returns.
                    boolean title = pendingTitleScreen;
                    pendingTitleScreen = pendingTimeAttackSelection = false;
                    pendingArena = null; pendingResult = null; result = null;
                    host.closeArena(); session = null;
                    if (title) {
                        enterMode(); exit = new Exit(Destination.TITLE_SCREEN, null, false);
                    } else enterSelection();
                } else if (pendingArena != null) {
                    var next = pendingArena; pendingArena = null;
                    stageArena(next);
                } else if (pendingResult != null) {
                    result = pendingResult; pendingResult = null;
                    host.closeArena(); session = null;
                    if (selectedMode == S3kCompetitionSession.Mode.MATCH_RACE) enterSelection();
                    else {
                        phase = selectedMode == S3kCompetitionSession.Mode.TIME_ATTACK ? Phase.TIME_ATTACK_RESULTS : Phase.RESULTS;
                        frame = 0; mainLoopStarted = false; horizontalScroll = 0; verticalScroll = 0;
                        resultHold = 30; resultSliding = false;
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
        frame = 0; mainLoopStarted = false;
        confirmed[0] = confirmed[1] = false;
        wheelDirections[0] = wheelDirections[1] = 0;
        for (int slot = 0; slot < 2; slot++) wheelAngles[slot] = wheelTargets[slot] = (0x40 + 0x55 * characters[slot]) & 0xFF;
        horizontalScroll = 0; scrollRemaining = 0;
        verticalScroll = Math.clamp(arenaRow - 1, 0, 2) * 0x48;
        Arrays.fill(wheelPriorityWords,0); Arrays.fill(wheelQueuePriorityWords,0);
        // Native entry performs Process/Render, sub_C0AE, Process/Render before
        // fading in. The zero-initialized first queue is consumed before the
        // final initial view, which has the computed words in its second queue.
        updateWheelDepth();
        System.arraycopy(wheelPriorityWords,0,wheelQueuePriorityWords,0,6);
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
                } else if (back(buttons)) { enterMode(); return; }
                else if (accept(buttons)) { confirmed[slot] = true; host.playSfx(Sonic3kSfx.STARPOST); }
            }
            if (!allConfirmed()) return;
            if (phase == Phase.PLAYER_SELECT) { stageArena(S3kCompetitionSession.Arena.AZURE_LAKE); return; }
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
        if (accept(both) && allConfirmed()) stageArena(S3kCompetitionSession.GRAND_PRIX_ORDER.get(arenaRow));
    }
    private void rotateWheel(int slot) {
        int next = wheelAngles[slot] + 5 * wheelDirections[slot];
        if (wheelDirections[slot] > 0 ? next >= wheelTargets[slot] : next <= wheelTargets[slot]) {
            next = wheelTargets[slot]; wheelDirections[slot] = 0;
        }
        wheelAngles[slot] = next;
    }
    /** sub_C0B8's two-pass pointer sort includes the native equal-Y swaps. */
    private void updateWheelDepth() {
        for (int player = 0; player < 2; player++) {
            int[] y = new int[3];
            for (int parameter = 0; parameter < 3; parameter++)
                y[parameter] = player == 1 && participants == 1 ? 0
                        : TrigLookupTable.sinHex(wheelAngles[player] + parameter * 0x55) >> 5;
            int a = 0, b = 1, c = 2;
            for (int pass = 0; pass < 2; pass++) {
                if (y[a] <= y[b]) { int t = a; a = b; b = t; }
                if (y[b] <= y[c]) { int t = b; b = c; c = t; }
            }
            wheelPriorityWords[player*3+a] = 0x80;
            wheelPriorityWords[player*3+b] = 0x100;
            wheelPriorityWords[player*3+c] = 0x180;
        }
    }
    private void enterMode() {
        // Competition_Menu clears display scroll and Level_frame_counter on entry.
        phase = Phase.MODE; frame = 0; mainLoopStarted = false; if (modeRow >= 3) modeRow = 0;
        horizontalScroll = 0; verticalScroll = 0; scrollRemaining = 0;
    }
    private boolean allConfirmed() { return confirmed[0] && (participants == 1 || confirmed[1]); }
    private void stageArena(S3kCompetitionSession.Arena arena) {
        var chars = java.util.stream.IntStream.range(0, participants)
                .mapToObj(slot -> S3kCompetitionPlayer.Character.values()[characters[slot]]).toList();
        pendingLaunch = new Launch(new S3kCompetitionSession.Config(selectedMode, arena,
                itemsEnabled && selectedMode != S3kCompetitionSession.Mode.TIME_ATTACK, participants), chars);
    }
    @Override public void afterFrame() {
        var launch = pendingLaunch;
        pendingLaunch = null;
        if (launch == null) return;
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
        else enterMode();
    }
    public void requestArena(S3kCompetitionSession.Arena arena) { pendingArena = Objects.requireNonNull(arena); }
    public void requestResults(S3kCompetitionSession.Result result) { pendingResult = Objects.requireNonNull(result); }
    /** Queued after the native pause continuation's final ordinary arena body. */
    public void requestTimeAttackSelection() { pendingTimeAttackSelection = true; }
    /** Slow-motion A's native Game_mode=4 write; existing progress remains owned by the host. */
    public void requestTitleScreen() { pendingTitleScreen = true; }
    public S3kCompetitionSession session() { return session; }
    public MenuView view() {
        return new MenuView(phase, frame, modeRow, itemsEnabled, selectedMode, arenaRow, participants,
                List.of(characters[0], characters[1]), List.of(wheelAngles[0] & 0xFF, wheelAngles[1] & 0xFF),
                List.of(confirmed[0], confirmed[1]), horizontalScroll, verticalScroll, result,
                Arrays.stream(wheelQueuePriorityWords).boxed().toList(),mainLoopStarted);
    }
    @Override public void draw() { if (phase == Phase.RACE) host.drawArena(); else host.drawMenu(view()); }
    @Override public void setClearColor() { host.setClearColor(); }
    @Override public Exit consumeExit() { Exit value = exit; exit = Exit.NONE; return value; }
    @Override public void reset() { host.closeArena(); initialize(); }
    private static boolean back(PlayerInputState input) { return (input.actionPressedMask() & InputActionMasks.ACTION_B) != 0; }
    private static boolean accept(PlayerInputState input) { return input.startPressed()
            || (input.actionPressedMask() & (InputActionMasks.ACTION_A | InputActionMasks.ACTION_C)) != 0; }
}
