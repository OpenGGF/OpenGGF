package infinite;

import com.openggf.data.Rom;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.level.scroll.BgTilemapUpdateMode;
import com.openggf.level.scroll.ZoneScrollHandler;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Keeps the stock Deform_* background continuous across the course's window shifts.
 * <p>
 * Each shift moves the camera 4,096 pixels left. The stock routines derive every band from
 * camera X (GHZ's mountains move 96/256 of each camera step, its water lines interpolate
 * towards camera X, Spring Yard and Star Light read it directly), so the shift jumped every
 * band by a different amount. The stock handlers instead see the course's logical camera X
 * (window origin plus local camera X), which only ever moves forward, so every band keeps
 * its stock speed with no seam. The foreground words are then put back to the real local
 * camera: the stock handlers write FG as -camera X plus any per-line deform, so adding back
 * the origin restores the local scroll and keeps the deform. Off the course the origin is
 * zero and every call is the stock one.
 */
final class CourseScroll implements ScrollHandlerProvider {
    // Display lines whose BG-FG spread the renderer reads, as AbstractZoneScrollHandler tracks.
    private static final int VISIBLE_LINES = 224;
    private final ScrollHandlerProvider base;
    private final LongSupplier origin;
    private final Map<ZoneScrollHandler, ZoneScrollHandler> handlers = new IdentityHashMap<>();

    /** {@code origin} supplies the course's window origin in pixels, or 0 off the course. */
    CourseScroll(ScrollHandlerProvider base, LongSupplier origin) {
        this.base = base;
        this.origin = origin;
    }

    ScrollHandlerProvider base() { return base; }

    @Override public void load(Rom rom) throws IOException { base.load(rom); }
    @Override public ZoneScrollHandler getHandler(int zoneIndex) {
        ZoneScrollHandler stock = base.getHandler(zoneIndex);
        return stock == null ? null : handlers.computeIfAbsent(stock, Logical::new);
    }
    @Override public ZoneConstants getZoneConstants() { return base.getZoneConstants(); }
    @Override public void initForZone(int zoneId, int actId, int cameraX, int cameraY) {
        base.initForZone(zoneId, actId, cameraX, cameraY);
    }
    @Override public void updateDynamicArt(com.openggf.level.Level level, int cameraX) {
        base.updateDynamicArt(level, cameraX);
    }
    @Override public int getTornadoVelocityX() { return base.getTornadoVelocityX(); }
    @Override public int getTornadoVelocityY() { return base.getTornadoVelocityY(); }
    @Override public int getCameraBgXOffset() { return base.getCameraBgXOffset(); }
    @Override public void resetZoneState() { base.resetZoneState(); }
    @Override public boolean updateForEnding(int[] horizScrollBuf, int zoneId, int actId,
                                             int frameCounter, short bgVscroll) {
        return base.updateForEnding(horizScrollBuf, zoneId, actId, frameCounter, bgVscroll);
    }

    /** One stock zone handler driven by the logical camera X. */
    private final class Logical implements ZoneScrollHandler {
        private final ZoneScrollHandler stock;
        private int minScrollOffset;
        private int maxScrollOffset;
        private boolean shifted;

        Logical(ZoneScrollHandler stock) { this.stock = stock; }

        @Override public void update(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
            int offset = (int) origin.getAsLong();
            stock.update(horizScrollBuf, cameraX + offset, cameraY, frameCounter, actId);
            shifted = offset != 0;
            if (!shifted) return;
            minScrollOffset = Integer.MAX_VALUE;
            maxScrollOffset = Integer.MIN_VALUE;
            for (int line = 0; line < horizScrollBuf.length; line++) {
                int packed = horizScrollBuf[line];
                short fg = (short) ((packed >> 16) + offset);
                short bg = (short) packed;
                horizScrollBuf[line] = (fg << 16) | (bg & 0xFFFF);
                if (line >= VISIBLE_LINES) continue;
                minScrollOffset = Math.min(minScrollOffset, bg - fg);
                maxScrollOffset = Math.max(maxScrollOffset, bg - fg);
            }
        }
        @Override public int getMinScrollOffset() { return shifted ? minScrollOffset : stock.getMinScrollOffset(); }
        @Override public int getMaxScrollOffset() { return shifted ? maxScrollOffset : stock.getMaxScrollOffset(); }
        @Override public short getVscrollFactorBG() { return stock.getVscrollFactorBG(); }
        @Override public BgTilemapUpdateMode getBgTilemapUpdateMode() { return stock.getBgTilemapUpdateMode(); }
        @Override public short[] getPerLineVScrollBG() { return stock.getPerLineVScrollBG(); }
        @Override public short[] getPerColumnVScrollBG() { return stock.getPerColumnVScrollBG(); }
        @Override public short[] getPerColumnVScrollFG() { return stock.getPerColumnVScrollFG(); }
        // BG camera X and period are background coordinates, which stay logical.
        @Override public int getBgCameraX() { return stock.getBgCameraX(); }
        @Override public int getBgPeriodWidth() { return stock.getBgPeriodWidth(); }
        @Override public short getVscrollFactorFG() { return stock.getVscrollFactorFG(); }
        @Override public int getShakeOffsetX() { return stock.getShakeOffsetX(); }
        @Override public int getShakeOffsetY() { return stock.getShakeOffsetY(); }
        @Override public void init(int actId, int cameraX, int cameraY) {
            stock.init(actId, cameraX + (int) origin.getAsLong(), cameraY);
        }
        @Override public Object captureRewindState() { return stock.captureRewindState(); }
        @Override public void restoreRewindState(Object state) { stock.restoreRewindState(state); }
    }
}
