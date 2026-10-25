package typinghog.play;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.render.LevelFloorScanner;
import com.openggf.level.render.PlaneRasterizer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.physics.Direction;
import com.openggf.physics.NativePlayerTerrainPlacement;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayablePlacementProfile;
import com.openggf.sprites.playable.PlayablePuppet;
import com.openggf.sprites.playable.route.NativeRouteSteering;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import typinghog.rails.SectionPlan;

/**
 * Directed travel over a short, real strip of the loaded act. The production world stays held;
 * an isolated native player owns movement, collision, animation and stopping. Selection is a
 * finite, one-time rehearsal, not a continuous stock route or a parity claim. It reuses the native
 * steering policy shared with Stageplay and rehearses against loaded ROM terrain.
 * No flat terrain, active-world replacement, or trace comparison data participates.
 */
public final class SectionTravel implements AutoCloseable {
    public static final int WIDTH = 400, HEIGHT = 224, FEET_ROW = 196;
    public static final int FIGHTS = 4;
    private static final int DESTINATIONS = FIGHTS + 1;
    private static final int WINDOW = 2048, MAX_WINDOWS = 18, MAX_PROPOSALS = 36;
    private static final int LEG_LIMIT = 360, SETTLE_ROWS = 12, SOAK_ROWS = 30;
    private static final int TICK_BUDGET = 16_000, SENSOR_BUDGET = 2_000_000;
    private final NativeRouteSteering.Settings motion = new NativeRouteSteering.Settings(
            NativeRouteSteering.TravelSpeed.RUN, NativeRouteSteering.EndingAction.HARD_STOP);

    public record Site(int x, int y, int floorY) { }
    public record View(List<Site> sites, int arrived, int nativeTicks, double x, double y,
                       int xSpeed, int groundSpeed, boolean grounded, int settled, boolean closed) { }
    private final PlayablePuppet puppet;
    private final List<Site> sites;
    private final int corridorLeft, corridorWidth;
    private int arrived, frame, legRows, settled, stuck;
    private boolean closed;

    private SectionTravel(PlayablePuppet puppet, List<Site> sites, int corridorLeft, int corridorWidth) {
        this.puppet = puppet;
        this.sites = List.copyOf(sites);
        this.corridorLeft = corridorLeft; this.corridorWidth = corridorWidth;
    }

    /** A private route checkpoint must be restored together with the native course checkpoint. */
    public static final class Snapshot {
        private final SectionTravel owner;
        private final PlayablePuppet.Snapshot puppet;
        private final int arrived, frame, legRows, settled, stuck;
        private Snapshot(SectionTravel owner) {
            this.owner = owner;
            puppet = owner.puppet.capture();
            arrived = owner.arrived; frame = owner.frame; legRows = owner.legRows;
            settled = owner.settled; stuck = owner.stuck;
        }
    }

    /** Uses the actual player's ROM animation identity, including its real mapping-frame count. */
    static SpriteArtSet animationArt(AbstractPlayableSprite player) {
        int count = player.getAnimationFrameCount();
        if (count <= 0 || count > 4096 || player.getAnimationProfile() == null || player.getAnimationSet() == null)
            throw new IllegalStateException("native Sonic animation art is not ready");
        return new SpriteArtSet(new Pattern[0], Collections.nCopies(count, new SpriteMappingFrame(List.of())),
                List.of(), 0, 0, 0, 0, player.getAnimationProfile(), player.getAnimationSet());
    }

    static SectionTravel prepare(SectionPlan plan, AbstractPlayableSprite player) {
        if (plan.mode() != SectionPlan.Mode.GROUND) throw new IllegalArgumentException("Ground section required");
        return prepare(GameServices.level().getCurrentLevel(), GameServices.module(), plan.zone(), plan.act(),
                animationArt(player), player.getCentreX(), player.getCentreY());
    }

    /** Explicit terrain/module inputs also let ROM qualification exercise the production selector. */
    public static SectionTravel prepare(Level terrain, GameModule module, int zone, int act, SpriteArtSet art,
            int nearX, int nearY) {
        if (terrain == null) throw new IllegalStateException("section has no loaded native terrain");
        int size = terrain.getBlockPixelSize();
        int right = Math.min(Short.MAX_VALUE - WIDTH, Math.min(terrain.getMaxX() + 320,
                terrain.getLayerWidthBlocks(PlaneRasterizer.FOREGROUND) * size));
        int bottom = Math.min(Short.MAX_VALUE - HEIGHT, Math.min(terrain.getMaxY() + HEIGHT,
                terrain.getLayerHeightBlocks(PlaneRasterizer.FOREGROUND) * size));
        int left = Math.max(0, terrain.getMinX()), top = Math.max(0, terrain.getMinY());
        if (right - left < WIDTH || bottom - top < FEET_ROW)
            throw new IllegalStateException("native terrain is too small for a section corridor");
        var collision = GameplaySessionFactory.createCollisionOnlyLevel(module, terrain, zone, act, EngineServices.current());
        var profile = PlayablePlacementProfile.capture(module, collision, "sonic", 0);
        int[] budget = {0, 0};
        var placement = new NativePlayerTerrainPlacement(collision, () -> {
            if (++budget[0] > SENSOR_BUDGET) throw new IllegalStateException("section native support search budget exhausted");
        });
        var footing = new Footing(placement, profile);
        var windows = new LinkedHashSet<List<Integer>>();
        var xs = new LinkedHashSet<Integer>();
        xs.add(clamp(nearX - WIDTH / 2, left, Math.max(left, right - WINDOW)));
        // Overlap by one complete view: a 400px floor crossing a window edge must
        // remain discoverable. Quarter-act samples left unexamined gaps between windows.
        for (int x = left; x < right; x += WINDOW - WIDTH)
            xs.add(Math.min(x, Math.max(left, right - WINDOW)));
        int[] ys = {clamp(nearY - FEET_ROW - 128, top, Math.max(top, bottom - WINDOW)),
                top + Math.max(0, bottom - top - WINDOW) / 2, Math.max(top, bottom - WINDOW)};
        for (int y : ys) for (int x : xs) windows.add(List.of(x, y));
        int proposals = 0, scanned = 0;
        String refusal = "no continuous native floor with room for typing";
        for (List<Integer> window : windows) {
            if (++scanned > MAX_WINDOWS) break;
            for (int width : new int[] {1280, 960, 640, 400}) {
                var stages = LevelFloorScanner.stages(terrain, window.get(0), window.get(1),
                        Math.min(right, window.get(0) + WINDOW), Math.min(bottom, window.get(1) + WINDOW),
                        width, 120, 48);
                // Bound rehearsal independently of how many strips the decoder discovers.
                for (int index : sampleIndices(stages.size())) {
                    if (++proposals > MAX_PROPOSALS)
                        throw new IllegalStateException("section corridor search exhausted: " + refusal);
                    var stage = stages.get(index);
                    var sites = sites(terrain, stage, footing, profile);
                    if (sites.isEmpty()) { refusal = "native support or clear typing view rejected the corridor"; continue; }
                    var first = sites.getFirst();
                    var puppet = PlayablePuppet.createNativeWorldProbe(terrain, module, zone, act, module,
                            "sonic", art, first.x(), first.y(), 0, true, false);
                    var route = new SectionTravel(puppet, sites, stage.x(), stage.width());
                    boolean accepted = false;
                    try {
                        for (int i = 0; i < SOAK_ROWS; i++) { chargeTick(budget); puppet.tick(false, false, false, route.frame++); }
                        if (!settled(puppet.sample(), first)) { refusal = "native entry did not settle"; continue; }
                        Snapshot start = route.capture();
                        boolean good = true;
                        for (int goal = 1; goal <= DESTINATIONS && good; goal++) {
                            boolean reached = false;
                            for (int tick = 0; tick < LEG_LIMIT && !reached; tick++) {
                                chargeTick(budget); reached = route.advance();
                            }
                            if (!reached) { refusal = "native travel did not reach destination " + goal; good = false; break; }
                            for (int tick = 0; tick < SOAK_ROWS; tick++) {
                                chargeTick(budget); puppet.tick(false, false, false, route.frame++);
                                if (!settled(puppet.sample(), sites.get(goal))) { good = false; refusal = "native destination drifted"; break; }
                            }
                        }
                        if (!good) continue;
                        route.restore(start);
                        accepted = true;
                        return route;
                    } catch (IllegalStateException rejected) {
                        refusal = rejected.getMessage();
                        if (budget[1] >= TICK_BUDGET) throw rejected;
                    } finally {
                        if (!accepted) route.close();
                    }
                }
            }
        }
        throw new IllegalStateException("no qualified section corridor in " + scanned + " bounded windows: " + refusal);
    }

    /** Query caches live only for this preparation and this immutable collision/profile pair. */
    private static final class Footing {
        private final NativePlayerTerrainPlacement placement;
        private final PlayablePlacementProfile profile;
        private final Map<Long, NativePlayerTerrainPlacement.Result> near = new HashMap<>();
        private final Map<Long, NativePlayerTerrainPlacement.Result> exact = new HashMap<>();
        Footing(NativePlayerTerrainPlacement placement, PlayablePlacementProfile profile) {
            this.placement = placement; this.profile = profile;
        }
        private static long key(int x, int y) { return (long) x << 32 | y & 0xffffffffL; }
        NativePlayerTerrainPlacement.Result near(int x, int y) {
            return near.computeIfAbsent(key(x, y), ignored -> placement.near(profile, x, y));
        }
        NativePlayerTerrainPlacement.Result exact(int x, int y) {
            return exact.computeIfAbsent(key(x, y), ignored -> placement.exact(profile, x, y));
        }
    }

    private static List<Site> sites(Level terrain, LevelFloorScanner.Stage stage,
            Footing footing, PlayablePlacementProfile profile) {
        // Keep the entry and boss away from corridor edges. A short strip retains a
        // fixed full view while six real standing centres occupy its inner portion.
        int margin = stage.width() >= 640 ? WIDTH / 2 : 128;
        int span = stage.width() - 2 * margin;
        var sites = new ArrayList<Site>();
        for (int i = 0; i <= DESTINATIONS; i++) {
            int x = stage.x() + margin + span * i / DESTINATIONS;
            int floor = stage.floor()[x - stage.x()];
            var seed = footing.near(x, floor - profile.yRadius());
            if (seed.proposal() == null) return List.of();
            // exact() inspects the whole standing body, not just the two feet. Do
            // this at each actual stop; repeating that envelope at every 4px strip
            // sample exhausted the sensor budget before reaching later candidates.
            if (footing.exact(x, seed.proposal().y()).proposal() == null) return List.of();
            int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
            for (int dx = -40; dx <= 88; dx += 4) {
                var support = footing.near(x + dx, stage.floor()[x + dx - stage.x()] - profile.yRadius());
                if (support.proposal() == null
                        || Math.min(support.proposal().angle(), 256 - support.proposal().angle()) > 8) return List.of();
                low = Math.min(low, support.proposal().surfaceY()); high = Math.max(high, support.proposal().surfaceY());
            }
            if (high - low > 8 || seed.proposal().surfaceY() < FEET_ROW
                    || !clearView(terrain, clamp(x - WIDTH / 2, stage.x(), stage.x() + stage.width() - WIDTH),
                            seed.proposal().surfaceY() - FEET_ROW)) return List.of();
            sites.add(new Site(x, seed.proposal().y(), seed.proposal().surfaceY()));
        }
        return sites;
    }

    /** Static foreground transparency at both priorities; no fabricated background or floor. */
    private static boolean clearView(Level terrain, int x, int y) {
        int[] pixels = PlaneRasterizer.rasterize(terrain,
                (layer, pattern, layoutX) -> pattern >= 0 && pattern < terrain.getPatternCount() ? terrain.getPattern(pattern) : null,
                PlaneRasterizer.FOREGROUND, x, y, WIDTH, FEET_ROW - 8, 0);
        int clear = 0, total = 0;
        for (int row = 0; row < FEET_ROW - 8; row += 8) for (int col = 0; col < WIDTH; col += 8) {
            total++;
            if ((pixels[row * WIDTH + col] >>> 24) == 0) clear++;
        }
        return clear * 2 >= total;
    }

    private static void chargeTick(int[] budget) {
        if (++budget[1] > TICK_BUDGET) throw new IllegalStateException("section native rehearsal tick budget exhausted");
    }
    private static int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }
    private static int[] sampleIndices(int size) {
        return size == 0 ? new int[0] : java.util.stream.IntStream.of(0, size / 2, size - 1).distinct().toArray();
    }

    /** A fight destination requires standing support, low speed and a matching native height. */
    private static boolean settled(PlayablePuppet.Observation sample, Site goal) {
        return sample.onGround() && Math.abs(sample.x() - goal.x()) <= 6 && Math.abs(sample.y() - goal.y()) <= 4
                && Math.abs(sample.xSpeed()) <= 64 && Math.abs(sample.groundSpeed()) <= 64;
    }

    /** Advances exactly one private native row; holds, pauses and typing never call this. */
    public boolean advance() {
        requireOpen();
        if (arrived >= DESTINATIONS) throw new IllegalStateException("section route is already complete");
        Site goal = sites.get(arrived + 1);
        var before = puppet.sample();
        int direction = NativeRouteSteering.direction(before.x(), before.xSpeed(), before.groundSpeed(), before.runAccel(),
                before.runDecel(), before.friction(), before.maxSpeed(), before.slopeImpulse(), before.airborne(),
                goal.x(), 1, motion, false, 1);
        // A bounded fresh jump can clear a narrow native lip missed by the coarse strip scan.
        // Native collision and ordinary jump physics decide its result; it never counts as arrival.
        boolean jump = stuck == 20 && before.onGround() && Math.abs(before.x() - goal.x()) > 12;
        puppet.tick(direction < 0, direction > 0, jump, frame++);
        var after = puppet.sample();
        stuck = Math.abs(after.x() - before.x()) < .125 && Math.abs(after.x() - goal.x()) > 12 ? stuck + 1 : 0;
        if (stuck > 48) throw new IllegalStateException("native section travel is blocked");
        if (++legRows > LEG_LIMIT || after.y() > goal.floorY() + HEIGHT
                || after.x() < sites.getFirst().x() - 64 || after.x() > sites.getLast().x() + 64)
            throw new IllegalStateException("native section travel exceeded its qualified corridor");
        settled = settled(after, goal) ? settled + 1 : 0;
        if (settled < SETTLE_ROWS) return false;
        arrived++;
        legRows = settled = stuck = 0;
        return true;
    }

    public Snapshot capture() { requireOpen(); return new Snapshot(this); }
    public void restore(Snapshot snapshot) {
        requireOpen();
        if (snapshot == null || snapshot.owner != this) throw new IllegalArgumentException("Section checkpoint belongs to another route");
        puppet.restore(snapshot.puppet);
        arrived = snapshot.arrived; frame = snapshot.frame; legRows = snapshot.legRows;
        settled = snapshot.settled; stuck = snapshot.stuck;
    }

    public View view() {
        var sample = puppet.sample();
        return new View(sites, arrived, frame, sample.x(), sample.y(), sample.xSpeed(), sample.groundSpeed(),
                sample.onGround(), settled, closed);
    }

    /** Applies sampled native movement/pose to the actual stock player, with a 400x224 view. */
    void present(AbstractPlayableSprite player) {
        requireOpen();
        var sample = puppet.sample();
        player.setRolling(false);
        player.setAir(sample.airborne());
        player.setOnObject(false);
        player.setObjectControlled(true);
        player.setObjectMappingFrameControl(true);
        NativePositionOps.writeXPosResetSubpixel(player, (int) Math.round(sample.x()));
        NativePositionOps.writeYPosResetSubpixel(player, (int) Math.round(sample.y()));
        player.setXSpeed((short) 0); player.setYSpeed((short) 0); player.setGSpeed((short) 0);
        player.setAngle((byte) sample.angle());
        player.setDirection(sample.flipX() ? Direction.LEFT : Direction.RIGHT);
        player.setAnimationId(sample.animation());
        player.setMappingFrame(sample.mappingFrame());
        player.setRenderFlips(sample.flipX(), sample.flipY());
        player.setHidden(false);
        player.setRenderFlagOnScreen(true);
        player.updateSensors(player.getX(), player.getY());
        var camera = GameServices.camera();
        camera.setX((short) clamp((int) Math.round(sample.x()) - WIDTH / 2,
                corridorLeft, corridorLeft + corridorWidth - WIDTH));
        camera.setY((short) ((int) Math.round(sample.y()) + sample.yRadius() - FEET_ROW));
        camera.setFrozen(true);
    }

    private void requireOpen() { if (closed) throw new IllegalStateException("Section route is closed"); }
    @Override public void close() { if (!closed) { closed = true; puppet.close(); } }
}
