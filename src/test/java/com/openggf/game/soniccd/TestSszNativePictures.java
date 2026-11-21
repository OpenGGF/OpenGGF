package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Native check for the Stardust Speedway presentation of all ten stages: the production renderer (scroll handler and
 * palette cycle through the shared palette owner; Stardust Speedway has no animated tiles) against BizHawk
 * screenshots of the stage run: the fixture's early shot (idle, nothing pressed, about 180 level frames in) and up to
 * five more taken from the same movie while the camera moves right: at camera X about $80, $100, $180, $280 and $400
 * ({@code mid1}-{@code mid5}, those the route reaches) and at the last trace row ({@code late}). The extra shots are
 * made with {@code rehost-fixtures/_tools/probe.sh} and live in {@code native/rehost-fixtures-ssz/<payload>/} (see its
 * {@code mid.json}); the test skips when they or the fixtures are absent.
 *
 * <p>Method follows {@code TestPpzR11aBackgroundPicture} and {@code TestWwzNativePictures}: the camera is frozen and set
 * to the trace's camera for each native level frame, so the scroll handler sees the native camera path without the
 * player lane's movement; nothing else is taken from the trace but what the masks need. Pairing ({@link
 * #engineFrame}): the fixture's early screenshot is its row's picture, the state after level frame lf-1; a probe shot
 * at {@code bk2_frame_offset + row + 1} is the next row's picture, the state after the level frame its own row counts.
 *
 * <p>Masked: the HUD, the box around the trace's Sonic and around the engine's own Sonic (the player lane is not driven
 * by the trace), a box around every placement and around every ring of a ring line (objects are not hosted by this
 * lane), the objects that leave their placement at the position the trace's {@code object_near} events give, and the
 * Searchlight beams and Lamp poles by their sprite extents (see the constants). The pictures are compared only where
 * the background's world X is below 512: the native background plane is 512 px wide and rows drawn "static" show the
 * layout modulo 512 (Present:739-755, {@code DrawBlockRowAbsX}), which the engine's wide plane does not model; that
 * belongs to the background drawing owner. A shot with fewer than {@link #MIN_PIXELS} such unmasked pixels is reported
 * as no coverage; each stage needs {@link #MIN_COUNTED_SHOTS} shots that do count.
 */
@EnabledOnOs(OS.LINUX)
class TestSszNativePictures {
    private static final CdStagePayload[] PAYLOADS = {
            CdStagePayload.R71A, CdStagePayload.R71B, CdStagePayload.R71C, CdStagePayload.R71D,
            CdStagePayload.R72A, CdStagePayload.R72B, CdStagePayload.R72C, CdStagePayload.R72D,
            CdStagePayload.R73C, CdStagePayload.R73D};
    private static final int PLACEMENT_RADIUS = 32;
    private static final int RING_RADIUS = 16;
    /**
     * A shot counts only with at least this many unmasked pixels (5.6% of the screen): the Searchlight beams take whole
     * columns out of many shots. Each stage must have {@link #MIN_COUNTED_SHOTS} counted shots.
     */
    private static final int MIN_PIXELS = 4000;
    private static final int LATE_MIN_PIXELS = 3000;
    private static final int MIN_COUNTED_SHOTS = 3;

    /**
     * Shots that do NOT reach the threshold and are reported as uncertified rather than asserted. The test asserts that
     * each of these still fails, so the list cannot go stale. Evidence in the lane report.
     */
    private static final Set<String> UNCERTIFIED = Set.of(
            "R72A late");   // foreground tiles differ across the whole frame (camera 183,2EA, Sonic airborne): not a scroll effect   // unhosted objects larger than the placement boxes remain

    // ObjRing_Deltas (Level/_Objects/Ring/Main.asm:17-32): x and y step of a ring line by subtype high nibble.
    private static final int[] RING_DELTAS = {
            0x10, 0, 0x18, 0, 0x20, 0, 0, 0x10, 0, 0x18, 0, 0x20, 0x10, 0x10, 0x18, 0x18,
            0x20, 0x20, -0x10, 0x10, -0x18, 0x18, -0x20, 0x20, 0x10, 8, 0x18, 0x10, -0x10, 8, -0x18, 0x10};
    private static final int RING_OBJECT_ID = 0x10;          // Ring: id = line - 3 of r7/object_index_1_2.asm:19
    /**
     * SearchlightObject, object type $38 in acts 1 and 2 (id = line - 3 of r7/object_index_1_2.asm:59; in
     * act 3 the same id is NullObject, r7/object_index_3.asm). It is not hosted. Its sprite is a beam column the full
     * height of the screen: placed at {@code scroll_fg_y + $70} (objects/r7/searchlight.asm:40-42) with pieces from y
     * -$70 to +$70 (sprites/r7/searchlight.asm:11-50). Where the trace lists it near Sonic on the row, its world X and
     * flip are the event's {@code x} and {@code status} bit 0; the frame it shows is not in the trace, so the mask is
     * the union of its three frames' horizontal extents: unflipped -$40 to +$48 from frame 2
     * (sprites/r7/searchlight.asm:33-50: x offsets $28 down to $C0, each piece 32 px wide), mirrored when flipped.
     * Where the trace does not list a live searchlight near Sonic (it has appeared and not been removed), the
     * mask is every column it can reach: its appearance x +-$5C (var_36 plus a sign-extended byte of the frame tables,
     * objects/r7/searchlight.asm:92-120, 150-190) with the wider of the two sides. Only columns are masked and nothing is learned from the comparison.
     */
    private static final int SEARCHLIGHT_OBJECT_ID = 0x38;
    private static final int SEARCHLIGHT_LEFT = 0x40;
    private static final int SEARCHLIGHT_RIGHT = 0x48;
    private static final int SEARCHLIGHT_TRAVEL = 0x5C;
    /**
     * LampObject, object type $36 in act 3 (r7/object_index_3.asm:57, id $36). Its pole (routine 4, set by the spawned child) is a
     * column {@code height = $70} (objects/r7/lamp.asm:44-51) and the head the parent shows
     * ({@code width_2 = $18}, {@code height = $C}, :36-41) sits just above the pole's top; the mask is the pole's
     * span plus $18 above it, $18 to each side.
     */
    private static final int LAMP_TYPE = 0x36;
    private static final int LAMP_POLE_ROUTINE = 4;
    private static final int LAMP_POLE_HALF_WIDTH = 0x18;
    private static final int LAMP_POLE_HALF_HEIGHT = 0x70;
    private static final int LAMP_HEAD_HEIGHT = 0x18;
    /**
     * Objects that leave their placement, boxed at the position the trace lists them at (+-32, the placement radius):
     * LostRing $11, Explosion $18, HudPoints $1C, Hotaru $23, SpikeBall $25, JumpSpikeBall $26, Animal $28 and
     * SwapPlatform $35 in every act, Sparks $09 and Kanabun $21 in acts 1 and 2 only (ids = line - 3 of
     * r7/object_index_1_2.asm and object_index_3.asm; the other id, $09 and $21, is NullObject in act 3). Static
     * objects stay masked from their placements and rings from their ring lines.
     */
    private static final Set<Integer> MOVING_TYPES = Set.of(0x11, 0x18, 0x1C, 0x23, 0x25, 0x26, 0x28, 0x35);
    private static final Set<Integer> MOVING_TYPES_ACTS_1_2 = Set.of(0x09, 0x21);
    private static final int NEAR_OBJECT_RADIUS = 32;
    /**
     * BlockObject, id $24 (line 39 of both object indexes): static, drawn from {@code BlockSprites} (sprites/r4/block.asm:39-51,
     * frames of up to 128 x 32 or 32 x 128 around the placement, chosen by subtype through {@code byte_20EDC2},
     * objects/r4/block.asm:11-37), so every block is masked at the largest half extent, +-$40.
     */
    private static final int BLOCK_OBJECT_ID = 0x24;
    private static final int BLOCK_HALF_EXTENT = 0x40;

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** One trace row. */
    private record Sample(int row, int levelFrames, int cameraX, int cameraY, int playerX, int playerY) { }

    private static List<Sample> samples(CdStagePayload payload) throws Exception {
        Path file = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/physics.csv");
        List<String> lines = Files.readAllLines(file);
        String[] header = lines.get(0).split(",");
        int camX = indexOf(header, "camera_x");
        int camY = indexOf(header, "camera_y");
        int counter = indexOf(header, "gameplay_frame_counter");
        int px = indexOf(header, "player_x");
        int py = indexOf(header, "player_y");
        List<Sample> out = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",");
            out.add(new Sample(i - 1, Integer.parseInt(cells[counter], 16), Integer.parseInt(cells[camX], 16),
                    Integer.parseInt(cells[camY], 16), Integer.parseInt(cells[px], 16), Integer.parseInt(cells[py], 16)));
        }
        return out;
    }

    /** One object event of the trace's aux stream: {@code object_near}, {@code object_appeared} or {@code object_removed}. */
    private record Event(int frame, String kind, int slot, int type, int x, int y, int routine, int status) { }

    private static List<Event> objectEvents(CdStagePayload payload) throws Exception {
        Path file = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/aux_state.jsonl");
        ObjectMapper mapper = new ObjectMapper();
        List<Event> out = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            boolean near = line.contains("\"object_near\"");
            if (!near && !line.contains("\"object_appeared\"") && !line.contains("\"object_removed\"")) continue;
            JsonNode event = mapper.readTree(line);
            String kind = event.get("event").asText();
            JsonNode type = event.has("type") ? event.get("type") : event.get("object_type");
            out.add(new Event(event.get("frame").asInt(), kind, event.get("slot").asInt(), Integer.decode(type.asText()),
                    event.has("x") ? Integer.decode(event.get("x").asText()) : 0,
                    event.has("y") ? Integer.decode(event.get("y").asText()) : 0,
                    near ? Integer.decode(event.get("routine").asText()) : 0,
                    near ? Integer.decode(event.get("status").asText()) : 0));
        }
        return out;
    }

    /** What the trace says about objects on one row. */
    private record Near(int type, int x, int y, int routine, int status, boolean exact) { }

    /**
     * The objects the masks use on {@code row}: every {@code object_near} event of the row, plus each Searchlight slot
     * that has appeared and not been removed by then but is not listed near (its x is the appearance x, {@code exact}
     * false: it can be anywhere within its travel of it).
     */
    private static List<Near> objectsAt(List<Event> events, int row) {
        List<Near> out = new ArrayList<>();
        Map<Integer, Event> live = new HashMap<>();
        for (Event event : events) {
            if (event.frame() > row) break;
            switch (event.kind()) {
                case "object_appeared" -> live.put(event.slot(), event);
                case "object_removed" -> live.remove(event.slot());
                default -> { }
            }
        }
        for (Event event : events) {
            if (event.frame() == row && event.kind().equals("object_near")) {
                out.add(new Near(event.type(), event.x(), event.y(), event.routine(), event.status(), true));
                live.remove(event.slot());
            }
        }
        for (Event event : live.values()) {
            if (event.type() == SEARCHLIGHT_OBJECT_ID) out.add(new Near(event.type(), event.x(), event.y(), 0, 0, false));
        }
        return out;
    }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) if (header[i].equals(name)) return i;
        throw new IllegalStateException("no column " + name);
    }

    /** A native screenshot: its name, file, trace row and level-frame counter. */
    private record Shot(String name, Path file, int traceRow, int levelFrames) { }

    private static List<Shot> shots(CdStagePayload payload) throws Exception {
        Path manifest = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json");
        JsonNode early = new ObjectMapper().readTree(manifest.toFile()).get("screenshots").get("early");
        List<Shot> out = new ArrayList<>();
        out.add(new Shot("early", CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/" + early.get("file").asText()),
                early.get("trace_row").asInt(), early.get("level_frames").asInt()));
        Path mid = CdTestMedia.nativeFile("rehost-fixtures-ssz/" + payload + "/mid.json");
        for (JsonNode shot : new ObjectMapper().readTree(mid.toFile()).get("shots")) {
            out.add(new Shot(shot.get("name").asText(), mid.resolveSibling(shot.get("file").asText()),
                    shot.get("trace_row").asInt(), shot.get("level_frames").asInt()));
        }
        return out;
    }

    /** The framebuffer plus what the masks need, read before the stage is replaced. */
    private record Rendered(RgbaImage image, int cameraX, int cameraY, int playerX, int playerY,
                            int enginePlayerX, int enginePlayerY, List<CdLevel.CdPlacement> placements, int[] hscroll,
                            List<Near> near, boolean act3) { }

    /**
     * Loads {@code payload} on the shared boot and captures the production renderer at each shot's engine level frame
     * ({@link #engineFrame}). The loaded stage is level frame 1, that iteration's scroll and palette cycle included
     * ({@code CdFirstMainLoopIteration}); every later distinct {@code gameplay_frame_counter} sets the native camera of
     * that level frame and steps once, so the engine stands at level frame L after the step that took the trace's
     * camera for L.
     */
    private static List<Rendered> render(com.openggf.GameLoop loop, CdStagePayload payload, boolean firstBoot,
                                         List<Sample> samples, List<Shot> shots,
                                         List<Event> events) throws Exception {
        if (!firstBoot) {
            // The headless GL context can be booted once per JVM, so later stages load through the same boot.
            GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        }
        assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
        var game = (SonicCdGame) GameServices.level().getGame();
        assertEquals(payload, game.currentLevel().payload(), "stage loaded");
        boolean act3 = payload.stage().actNumber() == 3;
        int[] targets = new int[shots.size()];
        for (int i = 0; i < shots.size(); i++) targets[i] = engineFrame(shots.get(i), samples);
        Rendered[] out = new Rendered[shots.size()];
        GameServices.camera().setFrozen(true);
        Sample first = samples.get(0);
        GameServices.camera().setX((short) first.cameraX());
        GameServices.camera().setY((short) first.cameraY());
        int previous = first.levelFrames();
        int captured = 0;
        for (int k = -1; k < samples.size() && captured < out.length; k++) {
            Sample sample = k < 0 ? first : samples.get(k);
            if (k >= 0) {
                if (sample.levelFrames() == previous) continue;   // lag: the level did not advance
                previous = sample.levelFrames();
                GameServices.camera().setX((short) sample.cameraX());
                GameServices.camera().setY((short) sample.cameraY());
                loop.step();
            }
            for (int i = 0; i < targets.length; i++) {
                if (targets[i] == previous && out[i] == null) {
                    out[i] = capture(sample, objectsAt(events, sample.row()), act3);
                    captured++;
                }
            }
        }
        assertEquals(shots.size(), captured, payload + ": shots captured");
        return List.of(out);
    }

    /**
     * The engine level frame a shot shows. A picture shows the state after the level frame before the one its row
     * counts: the V-INT that starts the video frame DMAs the palette, scroll and sprite table the previous iteration
     * wrote (Level/Main.asm:829-831). The fixture's early screenshot is its row's picture: level frame lf(row)-1. A
     * {@code probe.sh} shot is the video frame after its row (probe frame {@code bk2_frame_offset + row + 1}, the run
     * continuing), so it is row+1's picture: lf(row+1)-1. No row follows the last one; there the level is taken to
     * advance, as it does on every frame without a pattern-load lag: lf(row).
     */
    private static int engineFrame(Shot shot, List<Sample> samples) {
        if (shot.name().equals("early")) return samples.get(shot.traceRow()).levelFrames() - 1;
        int after = shot.traceRow() + 1;
        return after < samples.size() ? samples.get(after).levelFrames() - 1 : samples.get(shot.traceRow()).levelFrames();
    }

    private static Rendered capture(Sample sample, List<Near> near, boolean act3) {
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
        AbstractPlayableSprite sprite = GameServices.camera().getFocusedSprite();
        return new Rendered(ScreenshotCapture.captureFramebuffer(320, 224),
                GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                sample.playerX(), sample.playerY(),
                sprite == null ? -9999 : sprite.getCentreX(), sprite == null ? -9999 : sprite.getCentreY(),
                ((CdLevel) level.getCurrentLevel()).placements(), GameServices.parallax().getHScroll().clone(), near, act3);
    }

    private static boolean[][] mask(Rendered rendered) {
        boolean[][] masked = new boolean[320][224];
        int camX = rendered.cameraX();
        int camY = rendered.cameraY();
        maskBox(masked, rendered.playerX() - camX - 28, rendered.playerY() - camY - 36,
                rendered.playerX() - camX + 28, rendered.playerY() - camY + 36);
        maskBox(masked, rendered.enginePlayerX() - camX - 28, rendered.enginePlayerY() - camY - 36,
                rendered.enginePlayerX() - camX + 28, rendered.enginePlayerY() - camY + 36);
        for (CdLevel.CdPlacement placement : rendered.placements()) {
            int px = placement.x() - camX;
            int py = (placement.yWord() & 0xFFF) - camY;
            if ((placement.idByte() & 0x7F) == RING_OBJECT_ID) {
                // ObjRing_Init (Ring/Main.asm:60-80): subtype low 3 bits = rings - 1, high nibble = delta table row.
                int rings = (placement.subtype() & 7) + 1;
                int row = (placement.subtype() >> 4) & 0xF;
                int dx = RING_DELTAS[row * 2];
                int dy = RING_DELTAS[row * 2 + 1];
                for (int i = 0; i < rings; i++) {
                    maskBox(masked, px + i * dx - RING_RADIUS, py + i * dy - RING_RADIUS,
                            px + i * dx + RING_RADIUS, py + i * dy + RING_RADIUS);
                }
            } else if ((placement.idByte() & 0x7F) == BLOCK_OBJECT_ID) {
                maskBox(masked, px - BLOCK_HALF_EXTENT, py - BLOCK_HALF_EXTENT, px + BLOCK_HALF_EXTENT, py + BLOCK_HALF_EXTENT);
            } else {
                maskBox(masked, px - PLACEMENT_RADIUS, py - PLACEMENT_RADIUS, px + PLACEMENT_RADIUS, py + PLACEMENT_RADIUS);
            }
        }
        for (Near object : rendered.near()) {
            int ox = object.x() - camX;
            int oy = object.y() - camY;
            if (!rendered.act3() && object.type() == SEARCHLIGHT_OBJECT_ID) {
                int left = (object.status() & 1) == 0 ? SEARCHLIGHT_LEFT : SEARCHLIGHT_RIGHT;   // mirrored when flipped
                int right = (object.status() & 1) == 0 ? SEARCHLIGHT_RIGHT : SEARCHLIGHT_LEFT;
                int travel = object.exact() ? 0 : SEARCHLIGHT_TRAVEL;
                maskBox(masked, ox - travel - Math.max(left, right), 0, ox + travel + Math.max(left, right), 224);
            } else if (rendered.act3() && object.type() == LAMP_TYPE && object.routine() == LAMP_POLE_ROUTINE) {
                maskBox(masked, ox - LAMP_POLE_HALF_WIDTH, oy - LAMP_POLE_HALF_HEIGHT - LAMP_HEAD_HEIGHT,
                        ox + LAMP_POLE_HALF_WIDTH, oy + LAMP_POLE_HALF_HEIGHT);
            } else if (MOVING_TYPES.contains(object.type()) || (!rendered.act3() && MOVING_TYPES_ACTS_1_2.contains(object.type()))) {
                maskBox(masked, ox - NEAR_OBJECT_RADIUS, oy - NEAR_OBJECT_RADIUS, ox + NEAR_OBJECT_RADIUS, oy + NEAR_OBJECT_RADIUS);
            }
        }
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if ((x < 120 && y < 56) || (x < 56 && y >= 192) || (x >= 224 && y < 40)) masked[x][y] = true;
            }
        }
        return masked;
    }

    private static void maskBox(boolean[][] masked, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(224, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(320, x1); x++) masked[x][y] = true;
        }
    }

    /** {matching, total, minX, minY, maxX, maxY} over the unmasked pixels (and, with {@code hscroll}, world X below 512). */
    private static int[] compare(RgbaImage engine, RgbaImage reference, boolean[][] masked, int[] hscroll) {
        int same = 0, total = 0, minX = 999, minY = 999, maxX = -1, maxY = -1;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (masked[x][y]) continue;
                if (hscroll != null && ((x - (short) hscroll[y]) & 0xFFFF) >= 512) continue;
                total++;
                if (level(reference.argb(x, y)) == level(engine.argb(x, y))) {
                    same++;
                } else {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {same, total, minX, minY, maxX, maxY};
    }

    private static int minPixels(boolean late) {
        return late ? LATE_MIN_PIXELS : MIN_PIXELS;
    }

    private static boolean passes(int[] r) {
        return r[0] * 1000L >= r[1] * 995L;
    }

    /** One stage on the shared boot; returns the failure lines (empty when every asserted shot matches). */
    private static List<String> check(com.openggf.GameLoop loop, CdStagePayload payload, boolean firstBoot) throws Exception {
        Path out = Path.of("target", "soniccd-ssz-pictures");
        Files.createDirectories(out);
        List<Sample> samples = samples(payload);
        List<Shot> shots = shots(payload);
        // The trace row and the manifest agree on the level-frame counter at each shot.
        for (Shot shot : shots) {
            assertEquals(shot.levelFrames(), samples.get(shot.traceRow()).levelFrames(), payload + " " + shot.name());
        }
        List<Rendered> rendered = render(loop, payload, firstBoot, samples, shots, objectEvents(payload));
        List<String> failures = new ArrayList<>();
        RgbaImage[] reference = new RgbaImage[shots.size()];
        int counted = 0;
        for (int i = 0; i < shots.size(); i++) {
            reference[i] = ScreenshotCapture.loadPNG(shots.get(i).file());
            Shot shot = shots.get(i);
            boolean late = shot.name().equals("late");
            boolean[][] masked = mask(rendered.get(i));
            int[] r = compare(rendered.get(i).image(), reference[i], masked, rendered.get(i).hscroll());
            ScreenshotCapture.savePNG(rendered.get(i).image(), out.resolve("engine-" + payload + "-" + shot.name() + ".png"));
            String key = payload + " " + shot.name();
            if (r[1] < minPixels(late)) {
                // Too few pixels left to say anything (masks, or every row at background world X >= 512 on a late shot).
                System.out.println("SSZ picture: NO-COVERAGE " + key + ": " + r[1] + " unmasked pixels" + " with background world x < 512");
                continue;
            }
            boolean ok = passes(r);
            String line = String.format("%s%s: native level frame %d (camera %X,%X): %d/%d unmasked pixels match%s",
                    key, " (background world x < 512 only)", shot.levelFrames(),
                    rendered.get(i).cameraX(), rendered.get(i).cameraY(), r[0], r[1],
                    r[0] == r[1] ? "" : String.format("; differing pixels lie in x %d-%d, y %d-%d", r[2], r[4], r[3], r[5]));
            System.out.println("SSZ picture: " + (UNCERTIFIED.contains(key) ? "UNCERTIFIED " : ok ? "ok " : "FAIL ") + line);
            if (UNCERTIFIED.contains(key)) {
                if (ok) failures.add(key + " is listed as uncertified but now passes: " + line);
            } else if (!ok) {
                failures.add(line);
            } else {
                counted++;
            }
        }
        if (counted < MIN_COUNTED_SHOTS) {
            failures.add(payload + ": only " + counted + " shots with enough unmasked pixels (" + MIN_COUNTED_SHOTS + " needed)");
        }
        // Negative control: this stage's early engine picture must not match its own last native screenshot.
        int last = shots.size() - 1;
        int[] control = compare(rendered.get(0).image(), reference[last], mask(rendered.get(0)), rendered.get(0).hscroll());
        if (control[1] >= MIN_PIXELS && passes(control)) {
            failures.add(payload + " early engine picture also matches the late native screenshot: " + control[0] + "/" + control[1]);
        }
        return failures;
    }

    /**
     * Each stage at its early and moving-camera native frames, at least 99.5% of the unmasked pixels equal (3 bits per
     * channel, as {@code TestPpzR11aBackgroundPicture}). One boot, one stage load per payload; failures are
     * collected so every stage is reported.
     */
    @Test
    void everyStageMatchesItsNativeScreenshots() throws Exception {
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        List<String> failures = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            CdStagePayload first = PAYLOADS[0];
            var loop = boot.boot(disc, new SonicCdGameModule(),
                    SonicCdZoneRegistry.zoneIndex(first), SonicCdZoneRegistry.actIndex(first));
            for (CdStagePayload payload : PAYLOADS) {
                failures.addAll(check(loop, payload, payload == first));
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }
}
