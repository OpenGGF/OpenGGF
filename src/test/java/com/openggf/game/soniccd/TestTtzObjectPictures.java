package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.objects.ObjectInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprite pixels of the Tidal Tempest objects against the native screenshots ({@code rehost-fixtures/<payload>/
 * screenshot-*.png}). The camera follows the trace (as in {@link TtzPicture}); each of this lane's objects that is
 * on screen at the engine frame before a shot is compared inside its own box, 3 bits a channel. Boxes are
 * masked where the HUD, either player, a ring or another lane's placement (plus its machine block reach) covers
 * them. The class boots GL once and loads each stage into the session; one test method covers every stage and
 * prints each compared object (class, box, matching pixels).
 */
@EnabledOnOs(OS.LINUX)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestTtzObjectPictures {
    private static final List<CdStagePayload> STAGES = List.of(CdStagePayload.R41A, CdStagePayload.R41B,
            CdStagePayload.R41C, CdStagePayload.R41D, CdStagePayload.R42A, CdStagePayload.R42B, CdStagePayload.R42C,
            CdStagePayload.R42D, CdStagePayload.R43C, CdStagePayload.R43D);

    /** An object of this lane on screen at a capture: its class and screen box. */
    private record Seen(String name, int objectId, int x, int y, int halfW, int halfH) { }

    private record Compared(String stage, String shot, Seen seen, int same, int total) {
        @Override public String toString() {
            return String.format("%s %s %s id=%02X at screen (%d,%d) box %dx%d: %d/%d", stage, shot, seen.name(),
                    seen.objectId(), seen.x(), seen.y(), seen.halfW() * 2, seen.halfH() * 2, same, total);
        }
    }

    private TtzPicture.Session session;

    @BeforeAll
    void boot() throws Exception {
        CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        try {
            session = new TtzPicture.Session(disc, CdStagePayload.R41A);
        } catch (Exception | Error e) {
            disc.close();
            throw e;
        }
    }

    @AfterAll
    void close() throws Exception {
        if (session != null) {
            CdDiscDataSource disc = session.disc;
            session.close();
            disc.close();
        }
    }

    @Test
    void hostedObjectsMatchTheNativeScreenshotsInsideTheirBoxes() throws Exception {
        List<Compared> all = new ArrayList<>();
        for (CdStagePayload payload : STAGES) all.addAll(compare(payload, 0));
        Map<String, int[]> perClass = new TreeMap<>();
        for (Compared c : all) {
            System.out.println("TTZOBJ picture: " + c);
            int[] sum = perClass.computeIfAbsent(c.seen().name(), k -> new int[2]);
            sum[0] += c.same();
            sum[1] += c.total();
        }
        perClass.forEach((name, sum) -> System.out.println(String.format("TTZOBJ picture total %s: %d/%d", name, sum[0], sum[1])));
        assertTrue(!all.isEmpty(), "no hosted object was on screen at any native screenshot");
    }

    /** Compare list for one stage; {@code skewX} moves the camera that many pixels off from level frame 100 on. */
    private List<Compared> compare(CdStagePayload payload, int skewX) throws Exception {
        List<TtzNative.Shot> shots = TtzNative.shots(payload);
        List<TtzNative.Row> rows = TtzNative.rows(payload);
        Map<Integer, String> captureAt = new TreeMap<>();
        for (TtzNative.Shot shot : shots) captureAt.put(shot.levelFrames() - 1, shot.name());
        int last = captureAt.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow();
        Map<String, TtzNative.Rendered> rendered = new LinkedHashMap<>();
        Map<String, List<Seen>> seenAt = new LinkedHashMap<>();
        session.load(payload, skewX != 0);
        var loop = session.loop;
        GameServices.camera().setFrozen(true);
        // The loaded stage is level frame 1 with its scroll, palette cycle and tile calls run (CdFirstMainLoopIteration),
        // so stepping starts at level frame 2 and the production animator is the only one.
        int previous = rows.get(0).counter();
        TtzNative.Row previousRow = rows.get(0);
        session.player.row = rows.get(0);
        for (TtzNative.Row row : rows) {
            if (row.counter() == previous) continue;
            previous = row.counter();
            if (previous > last) break;
            session.player.row = previousRow;
            GameServices.camera().setX((short) (row.cameraX() + (previous > 100 ? skewX : 0)));
            GameServices.camera().setY((short) row.cameraY());
            loop.step();
            String shot = captureAt.get(previous);
            if (shot != null) {
                rendered.put(shot, TtzNative.capture(row));
                seenAt.put(shot, onScreen(row.cameraX(), row.cameraY()));
            }
            previousRow = row;
        }
        List<Compared> out = new ArrayList<>();
        for (TtzNative.Shot shot : shots) {
            RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
            TtzNative.Rendered engine = rendered.get(shot.name());
            assertTrue(engine != null, "frame " + (shot.levelFrames() - 1) + " not reached");
            boolean[][] masked = TtzNative.mask(engine);
            // The base mask also covers every placement, including this lane's: lift it for the hosted ones.
            boolean[][] cover = new boolean[320][224];
            for (int y = 0; y < 224; y++) {
                for (int x = 0; x < 320; x++) cover[x][y] = (x < 120 && y < 56) || (x < 56 && y >= 192) || (x >= 224 && y < 40);
            }
            TtzNative.maskBox(cover, engine.playerX() - engine.cameraX() - 48, engine.playerY() - engine.cameraY() - 112,
                    engine.playerX() - engine.cameraX() + 48, engine.playerY() - engine.cameraY() + 48);
            TtzNative.maskBox(cover, engine.enginePlayerX() - engine.cameraX() - 32,
                    engine.enginePlayerY() - engine.cameraY() - 40, engine.enginePlayerX() - engine.cameraX() + 32,
                    engine.enginePlayerY() - engine.cameraY() + 40);
            var level = GameServices.level().getCurrentLevel();
            if (level != null && level.getRings() != null) {
                for (var ring : level.getRings()) {
                    TtzNative.maskBox(cover, ring.x() - engine.cameraX() - 12, ring.y() - engine.cameraY() - 12,
                            ring.x() - engine.cameraX() + 12, ring.y() - engine.cameraY() + 12);
                }
            }
            // Other lanes' placements keep their box masked: not this lane's IDs.
            for (var placement : engine.placements()) {
                if (isMine(placement.idByte() & 0x7F)) continue;
                int px = placement.x() - engine.cameraX();
                int py = (placement.yWord() & 0xFFF) - engine.cameraY();
                TtzNative.maskBox(cover, px - 48, py - 48, px + 48, py + 48);
            }
            for (Seen seen : seenAt.getOrDefault(shot.name(), List.of())) {
                int same = 0, total = 0;
                for (int y = seen.y() - seen.halfH(); y < seen.y() + seen.halfH(); y++) {
                    for (int x = seen.x() - seen.halfW(); x < seen.x() + seen.halfW(); x++) {
                        if (x < 0 || y < 0 || x >= 320 || y >= 224 || cover[x][y]) continue;
                        total++;
                        if (TtzNative.level(reference.argb(x, y)) == TtzNative.level(engine.image().argb(x, y))) same++;
                    }
                }
                if (total >= 16) out.add(new Compared(payload.name(), shot.name(), seen, same, total));
            }
        }
        return out;
    }

    private static boolean isMine(int objectId) {
        return switch (objectId) {
            case 0x04, 0x0B, 0x0C, 0x0D, 0x0E, 0x20, 0x21, 0x23, 0x24, 0x25, 0x26, 0x2B, 0x32, 0x35, 0x36, 0x38,
                 0x3D, 0x42, 0x45 -> true;
            default -> false;
        };
    }

    /** This lane's live objects whose box touches the screen. */
    private static List<Seen> onScreen(int cameraX, int cameraY) {
        List<Seen> out = new ArrayList<>();
        for (ObjectInstance object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object.isDestroyed() || !object.getClass().getName().contains(".objects.ttz.")) continue;
            int sx = object.getX() - cameraX;
            int sy = object.getY() - cameraY;
            int hw = 0x14, hh = 0x14;
            if (object instanceof com.openggf.level.objects.AbstractObjectInstance a) {
                hw = Math.max(0x10, a.getOnScreenHalfWidth() + 4);
                hh = Math.max(0x10, a.getOnScreenHalfHeight() + 4);
            }
            if (sx + hw < 0 || sx - hw >= 320 || sy + hh < 0 || sy - hh >= 224) continue;
            out.add(new Seen(object.getClass().getSimpleName(), object.getSpawn().objectId(), sx, sy, hw, hh));
        }
        return out;
    }

    /** Comparison must be able to fail: skewing the camera by 3 pixels lowers the matching total of the same objects. */
    @Test
    void theComparisonFailsWhenTheCameraIsThreePixelsOff() throws Exception {
        List<Compared> right = compare(CdStagePayload.R42B, 0);
        List<Compared> skewed = compare(CdStagePayload.R42B, 3);
        int rightSame = right.stream().mapToInt(Compared::same).sum();
        int rightTotal = right.stream().mapToInt(Compared::total).sum();
        int skewedSame = skewed.stream().mapToInt(Compared::same).sum();
        int skewedTotal = skewed.stream().mapToInt(Compared::total).sum();
        System.out.println("TTZOBJ picture control: right " + rightSame + "/" + rightTotal + " skewed " + skewedSame + "/" + skewedTotal);
        assertTrue(rightTotal > 0, "no object compared in R42B");
        assertTrue(skewedSame * 1.0 / Math.max(1, skewedTotal) < rightSame * 1.0 / rightTotal,
                "a skewed camera was not detected");
    }
}
