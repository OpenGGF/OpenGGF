package com.openggf;

import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.camera.Camera;
import com.openggf.game.GameMode;
import com.openggf.game.MasterTitleEntry;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.launch.SuspendedSettings;
import com.openggf.graphics.RgbaImage;

import java.util.Objects;

/**
 * A stock game kept in memory while the master title is shown. Holding Escape fades the game out
 * and parks it here instead of tearing it down; its card resumes it, and starting anything else
 * from the hub ends it through the normal teardown.
 *
 * <p>The live session stays published in {@code SessionManager}; GameLoop stops binding it, so the
 * hub runs on the bootstrap camera and fade. This object holds what the hub would otherwise
 * change: the game mode, the music state (captured before the fade-out), the settings, the camera
 * position the hub resets for screen-space drawing, and the card.
 */
final class SuspendedGame {
    /** Captured when the hold completes, before the fade changes the picture or the music. */
    record Pending(AudioLogicalSnapshot audio, MasterTitleEntry.Suspended card) {
        Pending {
            Objects.requireNonNull(card, "card");
        }
    }

    /** The paused frame at the game's own pixel size, top row first, RGBA. */
    record Thumbnail(int width, int height, byte[] rgba) {
        Thumbnail {
            if (width <= 0 || height <= 0 || rgba == null || rgba.length != width * height * 4) {
                throw new IllegalArgumentException("Thumbnail must be width*height RGBA");
            }
        }
    }

    record CameraPosition(short x, short y, short xCopy, short yCopy) {
        static CameraPosition of(Camera camera) {
            return camera == null ? null
                    : new CameraPosition(camera.getX(), camera.getY(), camera.getXCopy(), camera.getYCopy());
        }

        void restore(Camera camera) {
            if (camera == null) return;
            camera.setX(x);
            camera.setY(y);
            camera.setXCopy(xCopy);
            camera.setYCopy(yCopy);
        }
    }

    private final GameMode mode;
    private final Pending pending;
    private final Thumbnail thumbnail;
    private final SuspendedSettings settings;
    private final CameraPosition camera;

    SuspendedGame(GameMode mode, Pending pending, Thumbnail thumbnail, SuspendedSettings settings,
                  CameraPosition camera) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.thumbnail = thumbnail;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.camera = camera;
    }

    /** The mode the game was parked from, after its fade-out. */
    GameMode mode() { return mode; }
    AudioLogicalSnapshot audio() { return pending.audio(); }
    MasterTitleEntry.Suspended card() { return pending.card(); }
    Thumbnail thumbnail() { return thumbnail; }
    SuspendedSettings settings() { return settings; }
    CameraPosition camera() { return camera; }

    /** Where the player was, for the card: zone and act in a level, otherwise the screen. */
    static String location(GameMode mode, String zoneName, int act) {
        if (mode == null) return "";
        return switch (mode) {
            case LEVEL, TITLE_CARD -> zoneName == null || zoneName.isBlank() ? "" : titleCase(zoneName) + ", Act " + act;
            case SPECIAL_STAGE, SPECIAL_STAGE_RESULTS -> "Special Stage";
            case BONUS_STAGE -> "Bonus Stage";
            case TITLE_SCREEN -> "Title Screen";
            case LEVEL_SELECT -> "Level Select";
            case DATA_SELECT -> "Data Select";
            default -> "";
        };
    }

    /** Zone registries name zones in capitals ("EMERALD HILL"); the card's sentence reads better in title case. */
    private static String titleCase(String name) {
        StringBuilder out = new StringBuilder(name.length());
        boolean wordStart = true;
        for (char c : name.trim().toCharArray()) {
            out.append(wordStart ? Character.toUpperCase(c) : Character.toLowerCase(c));
            wordStart = c == ' ' || c == '-';
        }
        return out.toString();
    }

    static MasterTitleEntry.Suspended card(String gameCode, String location) {
        return new MasterTitleEntry.Suspended(
                new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.fromGameId(gameCode)), location);
    }

    /**
     * Samples the centre of each game pixel from a framebuffer capture of the scaled game viewport,
     * so the card shows the game's own pixels whatever the window size or display scale.
     */
    static Thumbnail thumbnail(RgbaImage viewport, int gameWidth, int gameHeight) {
        Objects.requireNonNull(viewport, "viewport");
        int width = Math.max(1, gameWidth);
        int height = Math.max(1, gameHeight);
        byte[] rgba = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            int sy = Math.min(viewport.height() - 1, (int) ((y + 0.5) * viewport.height() / height));
            for (int x = 0; x < width; x++) {
                int sx = Math.min(viewport.width() - 1, (int) ((x + 0.5) * viewport.width() / width));
                int argb = viewport.argb(sx, sy);
                int i = (y * width + x) * 4;
                rgba[i] = (byte) (argb >>> 16);
                rgba[i + 1] = (byte) (argb >>> 8);
                rgba[i + 2] = (byte) argb;
                rgba[i + 3] = (byte) 0xFF;
            }
        }
        return new Thumbnail(width, height, rgba);
    }
}
