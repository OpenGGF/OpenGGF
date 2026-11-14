package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameServices;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.zones.ttz.TtzWaterDataProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Shared pieces of the Tidal Tempest native checks: the rehost fixture trace ({@code physics.csv}), its
 * screenshots, a module that adds the water provider the module does not wire yet, the production render
 * and the pixel comparison.
 *
 * <p>Only what the checks need from the native run is used, and only as a stand-in for lanes that are not
 * hosted yet: the camera path (so the scroll handler sees the native camera) and the player's position and
 * routine (so the water events see the native player). Nothing else is taken from the trace.
 */
final class TtzNative {
    private TtzNative() { }

    /** One trace row: {@code gameplay_frame_counter} is the level frame; X and Y are centre coordinates. */
    record Row(int counter, int cameraX, int cameraY, int playerX, int playerY, int routine) { }

    /** A native screenshot and the level frame count it was taken at. */
    record Shot(String name, Path file, int levelFrames) { }

    static Path fixture(CdStagePayload payload, String relative) {
        return CdTestMedia.nativeFile("rehost-fixtures/" + payload.name() + "/" + relative);
    }

    static List<Row> rows(CdStagePayload payload) throws IOException {
        List<String> lines = Files.readAllLines(fixture(payload, "trace/physics.csv"));
        String[] header = lines.get(0).split(",");
        int counter = indexOf(header, "gameplay_frame_counter");
        int cameraX = indexOf(header, "camera_x");
        int cameraY = indexOf(header, "camera_y");
        int playerX = indexOf(header, "player_x");
        int playerY = indexOf(header, "player_y");
        int routine = indexOf(header, "player_routine");
        List<Row> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            rows.add(new Row(hex(c[counter]), hex(c[cameraX]), hex(c[cameraY]), hex(c[playerX]), hex(c[playerY]),
                    hex(c[routine])));
        }
        return rows;
    }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) if (header[i].equals(name)) return i;
        throw new IllegalStateException("physics.csv has no column " + name);
    }

    private static int hex(String cell) {
        return Integer.parseInt(cell.trim(), 16);
    }

    static List<Shot> shots(CdStagePayload payload) throws IOException {
        JsonNode screenshots = new ObjectMapper().readTree(
                Files.readString(fixture(payload, "manifest.json"))).get("screenshots");
        List<Shot> shots = new ArrayList<>();
        for (String name : List.of("early", "end")) {
            JsonNode shot = screenshots.get(name);
            shots.add(new Shot(name, fixture(payload, shot.get("file").asText()), shot.get("level_frames").asInt()));
        }
        return shots;
    }

    /**
     * The stock module with a Tidal Tempest water provider that reads the player from the native trace:
     * the water events need the native player while player movement on these routes is still unverified.
     */
    static final class WaterModule extends DelegatingGameModule {
        private final WaterDataProvider provider;

        WaterModule(SonicCdGameModule base, CdDiscDataSource disc, TtzWaterDataProvider provider) {
            super(base, "ttz-water-test");
            this.provider = provider;
        }

        @Override
        public WaterDataProvider getWaterDataProvider() {
            return provider;
        }
    }

    /** What a capture needs from the engine before the boot closes. */
    record Rendered(RgbaImage image, int cameraX, int cameraY, int playerX, int playerY,
                    int enginePlayerX, int enginePlayerY, List<CdLevel.CdPlacement> placements) { }

    static Rendered capture(Row row) {
        GraphicsManager graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        var level = GameServices.level();
        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithSpritePriority(GameServices.sprites(), true);
        graphics.flush();
        if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
        glFinish();
        var sprite = GameServices.camera().getFocusedSprite();
        return new Rendered(ScreenshotCapture.captureFramebuffer(320, 224),
                GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                row.playerX(), row.playerY(),
                sprite == null ? -9999 : sprite.getCentreX(), sprite == null ? -9999 : sprite.getCentreY(),
                ((CdLevel) GameServices.level().getCurrentLevel()).placements());
    }

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    static void maskBox(boolean[][] masked, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(224, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(320, x1); x++) masked[x][y] = true;
        }
    }

    /** HUD, both players, and every placed object's box (the big machine blocks reach 48 px): the things the compared lanes do not host yet. */
    static boolean[][] mask(Rendered rendered) {
        boolean[][] masked = new boolean[320][224];
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                masked[x][y] = (x < 120 && y < 56) || (x < 56 && y >= 192) || (x >= 224 && y < 40);
            }
        }
        int sx = rendered.playerX() - rendered.cameraX();
        int sy = rendered.playerY() - rendered.cameraY();
        maskBox(masked, sx - 48, sy - 112, sx + 48, sy + 48);                 // the native player and the bubbles above it
        int ex = rendered.enginePlayerX() - rendered.cameraX();
        int ey = rendered.enginePlayerY() - rendered.cameraY();
        maskBox(masked, ex - 32, ey - 40, ex + 32, ey + 40);                  // the engine's own player
        for (CdLevel.CdPlacement placement : rendered.placements()) {
            int px = placement.x() - rendered.cameraX();
            int py = (placement.yWord() & 0xFFF) - rendered.cameraY();
            maskBox(masked, px - 48, py - 48, px + 48, py + 48);
        }
        // Every ring of the loaded stage, including ring-group members the placement boxes do not reach.
        // Rings are objects: their spin frame follows the object lanes' clocks, not this background check
        // (in R43C/R43D the engine's ring frame differs from native at level frame 178; lag frames are not
        // modelled yet).
        var level = com.openggf.game.GameServices.level().getCurrentLevel();
        if (level != null && level.getRings() != null) {
            for (var ring : level.getRings()) {
                int rx = ring.x() - rendered.cameraX();
                int ry = ring.y() - rendered.cameraY();
                maskBox(masked, rx - 12, ry - 12, rx + 12, ry + 12);
            }
        }
        return masked;
    }
}
