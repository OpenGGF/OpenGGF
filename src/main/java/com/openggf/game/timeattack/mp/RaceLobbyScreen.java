package com.openggf.game.timeattack.mp;

import com.openggf.control.InputHandler;
import com.openggf.game.timeattack.TimeAttackLaunchRequest;
import com.openggf.graphics.PixelFont;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.protocol.ControlMessage;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/** Master-title sub-screen that owns the room network pump between rounds. */
public final class RaceLobbyScreen {
    private static final float SCALE = 0.5f;
    private static final int LINE_HEIGHT = 12;

    private final MultiplayerRaceCoordinator coordinator;
    private final PixelFont font;
    private final boolean host;
    private final ControlMessage.RoundConfig configuredRound;
    private final String localCharacter;
    private final Consumer<TimeAttackLaunchRequest> roundLauncher;
    private final Runnable leaveHandler;
    private final MenuTextField chat = new MenuTextField(200, " .,:!?'-");
    private boolean launchedCurrentRound;

    public RaceLobbyScreen(MultiplayerRaceCoordinator coordinator, PixelFont font,
                           boolean host, ControlMessage.RoundConfig configuredRound,
                           String localCharacter,
                           Consumer<TimeAttackLaunchRequest> roundLauncher,
                           Runnable leaveHandler) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.font = font;
        this.host = host;
        this.configuredRound = Objects.requireNonNull(configuredRound, "configuredRound");
        this.localCharacter = Objects.requireNonNull(localCharacter, "localCharacter");
        this.roundLauncher = Objects.requireNonNull(roundLauncher, "roundLauncher");
        this.leaveHandler = Objects.requireNonNull(leaveHandler, "leaveHandler");
    }

    public void update(InputHandler input) {
        coordinator.pump();
        if (input.isKeyPressed(GLFW_KEY_ESCAPE) || input.logical().menuBack()
                || coordinator.hudState().connectionLost()
                || coordinator.hudState().kickReason() != null) {
            leaveHandler.run();
            return;
        }
        chat.poll(input);
        if (input.isKeyPressed(GLFW_KEY_ENTER) || input.logical().menuAccept()) {
            if (!chat.text().isBlank()) {
                coordinator.sendChat(chat.text());
                chat.setText("");
            } else if (host && (coordinator.session().phase() == ClientRaceSession.Phase.LOBBY
                    || coordinator.session().phase() == ClientRaceSession.Phase.ROUND_END)) {
                coordinator.sendRoundConfigure(configuredRound);
            }
        }
        ClientRaceSession.Phase phase = coordinator.session().phase();
        if (phase == ClientRaceSession.Phase.LOBBY) {
            launchedCurrentRound = false;
        } else if ((phase == ClientRaceSession.Phase.COUNTDOWN
                || phase == ClientRaceSession.Phase.RUNNING) && !launchedCurrentRound) {
            ControlMessage.RoundConfig round = coordinator.session().roundConfig();
            if (round != null) {
                launchedCurrentRound = true;
                roundLauncher.accept(new TimeAttackLaunchRequest(round.gameId(), round.zone(),
                        round.act(), localCharacter, List.of()));
            }
        }
    }

    public void render() {
        if (font == null) {
            return;
        }
        font.beginMegaBatch();
        try {
            ControlMessage.RoomDescriptor room = coordinator.session().room();
            int y = 8;
            font.drawText("MULTIPLAYER TIME ATTACK", 8, y, SCALE, 1f, 1f, 1f, 1f);
            y += LINE_HEIGHT;
            if (room != null) {
                font.drawText(room.name() + "  " + room.gameId().toUpperCase()
                                + " " + room.zone() + "-" + (room.act() + 1),
                        8, y, SCALE, 0.9f, 0.9f, 0.9f, 1f);
                y += LINE_HEIGHT;
                font.drawText(room.verified() ? "VERIFIED" : "UNVERIFIED TIMES",
                        8, y, SCALE, room.verified() ? 0.5f : 1f,
                        room.verified() ? 1f : 0.75f, 0.5f, 1f);
                y += LINE_HEIGHT;
            }
            int index = 0;
            for (ControlMessage.PlayerInfo player : coordinator.session().players()) {
                String badge = index++ == 0 ? " [HOST]" : "";
                String fingerprintTag = player.fingerprint() == null ? "????"
                        : player.fingerprint().substring(0,
                                Math.min(4, player.fingerprint().length()));
                String newBadge = player.newPlayer() ? " (new)" : "";
                font.drawText(player.displayName() + "#" + fingerprintTag + newBadge + "  "
                                + player.character().toUpperCase() + badge,
                        12, y, SCALE, 1f, 1f, 1f, 1f);
                y += LINE_HEIGHT;
            }
            y += LINE_HEIGHT;
            for (String line : coordinator.session().chatLines()) {
                font.drawText(line, 8, y, SCALE, 0.75f, 0.75f, 0.75f, 1f);
                y += LINE_HEIGHT;
            }
            font.drawText("CHAT: " + chat.text() + "_", 8, 190, SCALE,
                    1f, 1f, 1f, 1f);
            font.drawText(host ? "ENTER: CHAT / START ROUND   ESC: LEAVE"
                            : "ENTER: CHAT   ESC: LEAVE",
                    8, 204, SCALE, 0.65f, 0.65f, 0.65f, 1f);
        } finally {
            font.endMegaBatch();
        }
    }
}
