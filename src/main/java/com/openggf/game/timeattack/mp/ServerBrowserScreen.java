package com.openggf.game.timeattack.mp;

import com.openggf.control.InputHandler;
import com.openggf.graphics.PixelFont;
import com.openggf.net.client.MasterClient;
import com.openggf.net.protocol.ControlMessage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

/** Master-title room browser backed by an admitted master-server connection. */
@com.openggf.game.ModApi
public final class ServerBrowserScreen {
    private static final float SCALE = 0.5f;
    private static final int LINE_HEIGHT = 12;
    private static final long REFRESH_INTERVAL_MILLIS = 2000;

    @com.openggf.game.ModApi
    public interface Actions {
        void join(ControlMessage.RoomSummary room);
        void create(String routing);
        void back();
    }

    private final MasterClient client;
    private final String gameId;
    private final PixelFont font;
    private final Actions actions;
    private volatile List<ControlMessage.RoomSummary> rooms = List.of();
    private volatile String status = "Loading rooms...";
    private volatile boolean refreshInFlight;
    private int selected;
    private int page;
    private int totalPages;
    private String createRouting = "RELAY";
    private long lastRefreshAt = Long.MIN_VALUE;

    public ServerBrowserScreen(MasterClient client, String gameId,
                               PixelFont font, Actions actions) {
        this.client = Objects.requireNonNull(client, "client");
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        this.font = font;
        this.actions = Objects.requireNonNull(actions, "actions");
        refresh(System.currentTimeMillis());
    }

    public void update(InputHandler input) {
        long now = System.currentTimeMillis();
        if (!client.isOpen()) {
            status = "Master connection lost";
        } else if (lastRefreshAt == Long.MIN_VALUE
                || now - lastRefreshAt >= REFRESH_INTERVAL_MILLIS) {
            refresh(now);
        }
        if (input.isKeyPressed(GLFW_KEY_ESCAPE) || input.logical().menuBack()) {
            actions.back();
            return;
        }
        if (input.isKeyPressed(GLFW_KEY_UP) || input.logical().menuUp()) {
            selected = Math.max(0, selected - 1);
        }
        if (input.isKeyPressed(GLFW_KEY_DOWN) || input.logical().menuDown()) {
            selected = Math.min(Math.max(0, rooms.size() - 1), selected + 1);
        }
        if (input.isKeyPressed(GLFW_KEY_LEFT) || input.logical().menuLeft()) {
            if (page > 0) {
                page--;
                refresh(now);
            } else {
                createRouting = createRouting.equals("RELAY") ? "DIRECT" : "RELAY";
            }
        }
        if (input.isKeyPressed(GLFW_KEY_RIGHT) || input.logical().menuRight()) {
            if (page + 1 < totalPages) {
                page++;
                refresh(now);
            } else {
                createRouting = createRouting.equals("RELAY") ? "DIRECT" : "RELAY";
            }
        }
        if (input.isKeyPressed(GLFW_KEY_R)) {
            refresh(now);
        }
        if (input.isKeyPressed(GLFW_KEY_C)) {
            actions.create(createRouting);
        }
        if ((input.isKeyPressed(GLFW_KEY_ENTER) || input.logical().menuAccept())
                && selected < rooms.size()) {
            actions.join(rooms.get(selected));
        }
    }

    public void render() {
        if (font == null) {
            return;
        }
        font.beginMegaBatch();
        try {
            int y = 8;
            font.drawText("TIME ATTACK ROOMS - " + gameId.toUpperCase(),
                    8, y, SCALE, 1f, 1f, 1f, 1f);
            y += LINE_HEIGHT;
            for (int index = 0; index < rooms.size() && y < 170; index++) {
                ControlMessage.RoomSummary room = rooms.get(index);
                String prefix = index == selected ? "> " : "  ";
                String trust = room.verified() ? "VERIFIED" : "UNVERIFIED";
                String line = prefix + room.name() + "  " + room.playerCount() + "/"
                        + room.maxPlayers() + "  " + room.routing() + "  " + trust;
                float brightness = index == selected ? 1f : 0.68f;
                font.drawText(line, 8, y, SCALE, brightness, brightness, brightness, 1f);
                y += LINE_HEIGHT;
            }
            font.drawText(status, 8, 178, SCALE, 0.75f, 0.75f, 0.75f, 1f);
            font.drawText("ENTER JOIN  C CREATE " + createRouting
                            + "  LEFT/RIGHT PAGE/MODE  R REFRESH  ESC BACK",
                    8, 202, SCALE, 0.65f, 0.65f, 0.65f, 1f);
        } finally {
            font.endMegaBatch();
        }
    }

    private void refresh(long now) {
        if (refreshInFlight || !client.isOpen()) {
            return;
        }
        refreshInFlight = true;
        lastRefreshAt = now;
        client.listRooms(gameId, page).whenComplete((result, error) -> {
            refreshInFlight = false;
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                status = "Refresh failed: " + cause.getMessage();
                return;
            }
            rooms = result.rooms();
            totalPages = result.totalPages();
            selected = Math.min(selected, Math.max(0, rooms.size() - 1));
            status = rooms.isEmpty() ? "No rooms found"
                    : "Page " + (result.page() + 1) + "/" + Math.max(1, totalPages);
        });
    }
}
