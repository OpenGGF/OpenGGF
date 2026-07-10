package com.openggf.game.timeattack;

import com.openggf.control.InputHandler;
import com.openggf.graphics.PixelFont;
import com.openggf.game.timeattack.mp.MenuTextField;

import java.util.List;
import java.util.Objects;

/**
 * Master-title sub-mode for launching a solo Time Attack run. Structural
 * template copied from {@code UserRecordingMenu}'s
 * {@code MasterTitleScreen} integration: {@link #update(InputHandler)} drives
 * a GL-free state object, {@link #render()} mega-batches the font draws, and
 * {@link #consumeCloseRequested()} tells the host when to drop the menu.
 */
public final class TimeAttackMenu {
    private static final float SCALE = 0.5f;
    private static final int LINE_HEIGHT = 12;
    private static final int TOP_Y = 20;

    private final TimeAttackMenuState state;
    private final PixelFont font;
    private final LaunchStarter launchStarter;
    private final MenuTextField joinAddress = new MenuTextField(64, ".:-");
    private NetworkStarter networkStarter = NetworkStarter.NONE;

    public TimeAttackMenu(List<String> availableGameIds, String initialGameId,
            GhostStore ghostStore, PixelFont font, LaunchStarter launchStarter) {
        this.state = new TimeAttackMenuState(availableGameIds, initialGameId, ghostStore);
        this.font = font;
        this.launchStarter = Objects.requireNonNull(launchStarter, "launchStarter");
    }

    public void update(InputHandler input) {
        if (state.mode() == TimeAttackMenuState.Mode.JOIN_LAN) {
            joinAddress.poll(input);
        }
        state.update(input);
        TimeAttackLaunchRequest request = state.consumeLaunchRequest();
        if (request != null) {
            switch (state.mode()) {
                case SOLO -> launchStarter.launch(request);
                case HOST_LAN -> networkStarter.host(request,
                        state.characterPolicy(), state.lockedCharacter(), state.windowSeconds());
                case JOIN_LAN -> networkStarter.join(request, joinAddress.text());
                case BROWSE -> networkStarter.browse(request,
                        state.characterPolicy(), state.lockedCharacter(), state.windowSeconds());
            }
        }
    }

    public void render() {
        if (font == null) {
            return;
        }
        font.beginMegaBatch();
        try {
            renderContents();
        } finally {
            font.endMegaBatch();
        }
    }

    private void renderContents() {
        font.drawText("TIME ATTACK", 8, 6, SCALE, 1f, 1f, 1f, 1f);

        int y = TOP_Y;
        y = drawRow("Game", state.currentGameId().toUpperCase(), TimeAttackMenuState.Row.GAME, y);

        TimeAttackTrackCatalog.Track track = state.currentTrack();
        String trackLabel = track == null ? "(no tracks)" : track.label();
        y = drawRow("Track", trackLabel, TimeAttackMenuState.Row.TRACK, y);

        String character = state.currentCharacter();
        String characterLabel = character == null ? "(none)" : character.toUpperCase();
        y = drawRow("Character", characterLabel, TimeAttackMenuState.Row.CHARACTER, y);
        y = drawRow("Mode", state.mode().name().replace('_', ' '),
                TimeAttackMenuState.Row.MODE, y);
        if (state.mode() == TimeAttackMenuState.Mode.HOST_LAN
                || state.mode() == TimeAttackMenuState.Mode.BROWSE) {
            String policy = state.characterPolicy().equals("OPEN") ? "OPEN"
                    : "LOCKED " + characterLabel;
            y = drawRow("Policy", policy, TimeAttackMenuState.Row.POLICY, y);
            y = drawRow("Window", (state.windowSeconds() / 60) + " min",
                    TimeAttackMenuState.Row.WINDOW, y);
        } else if (state.mode() == TimeAttackMenuState.Mode.JOIN_LAN) {
            font.drawText("  Join: " + joinAddress.text() + "_", 12, y, SCALE,
                    1f, 1f, 1f, 1f);
            y += LINE_HEIGHT;
        }

        y += LINE_HEIGHT;
        String bestLine = state.bestExists() ? "Best: saved" : "Best: none yet";
        font.drawText(bestLine, 12, y, SCALE, 0.85f, 0.85f, 0.85f, 1f);
        y += LINE_HEIGHT;
        font.drawText(state.importCount() + " imported ghost(s) available", 12, y, SCALE,
                0.85f, 0.85f, 0.85f, 1f);

        y += LINE_HEIGHT;
        font.drawText("Enter GO   Esc Back   Up/Down Select   Left/Right Change", 8, y, SCALE,
                0.65f, 0.65f, 0.65f, 1f);
    }

    private int drawRow(String rowName, String value, TimeAttackMenuState.Row row, int y) {
        boolean focused = state.focusedRow() == row;
        float brightness = focused ? 1f : 0.62f;
        String prefix = focused ? "> " : "  ";
        font.drawText(prefix + rowName + ": " + value, 12, y, SCALE, brightness, brightness, brightness, 1f);
        return y + LINE_HEIGHT;
    }

    public boolean consumeCloseRequested() {
        return state.consumeCloseRequested();
    }

    public TimeAttackMenuState state() {
        return state;
    }

    public void setNetworkStarter(NetworkStarter networkStarter) {
        this.networkStarter = Objects.requireNonNull(networkStarter, "networkStarter");
    }

    public void setJoinAddress(String address) {
        joinAddress.setText(address == null ? "" : address);
    }

    @FunctionalInterface
    public interface LaunchStarter {
        void launch(TimeAttackLaunchRequest request);
    }

    public interface NetworkStarter {
        NetworkStarter NONE = new NetworkStarter() {
            @Override public void host(TimeAttackLaunchRequest request, String policy,
                                       String lockedCharacter, int windowSeconds) { }
            @Override public void join(TimeAttackLaunchRequest request, String address) { }
        };

        void host(TimeAttackLaunchRequest request, String policy,
                  String lockedCharacter, int windowSeconds);

        void join(TimeAttackLaunchRequest request, String address);

        default void browse(TimeAttackLaunchRequest request, String policy,
                            String lockedCharacter, int windowSeconds) { }
    }
}
