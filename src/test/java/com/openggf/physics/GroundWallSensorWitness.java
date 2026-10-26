package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.level.ChunkDesc;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Passive test observer for the actual CalcRoomInFront owner, originating in
 * HCZ R8 (runtime86171c6b8). Inputs: live collision owner, playable and copied-data
 * sink. Other shared-probe callers run unchanged and are excluded by their actual
 * lexical call stack. No second scan, gameplay writes or trace hydration.
 */
public final class GroundWallSensorWitness implements AutoCloseable {
    private final CollisionSystem collision;
    private final GroundSensor original, observed;
    private final AbstractPlayableSprite target;
    private int ownerCalls, otherProbeCalls;

    public GroundWallSensorWitness(CollisionSystem collision, AbstractPlayableSprite target,
                                   Consumer<Map<String, Object>> sink) throws Exception {
        this.collision = collision;this.target = target;
        original = (GroundSensor) read(collision, "calcRoomProbe");observed = spy(original);
        for (String name : List.of("reusableResult", "bgScanResult", "bgWallScanResult", "wallResult1", "wallResult2"))
            assertSame(read(original, name), read(observed, name), "Actual pooled scan buffers must be shared");
        doAnswer(call -> {
            if (observed.sprite != target) return call.callRealMethod();
            List<String> callers = StackWalker.getInstance().walk(frames -> frames
                    .filter(f -> f.getClassName().equals(CollisionSystem.class.getName()))
                    .map(StackWalker.StackFrame::getMethodName).toList());
            if (!callers.contains("resolveGroundWallCollision")) {
                otherProbeCalls++;
                return call.callRealMethod();
            }
            ownerCalls++;
            var before = playable(target);
            Direction direction = call.getArgument(0);
            short ox = call.getArgument(1), oy = call.getArgument(2), dx = call.getArgument(3), dy = call.getArgument(4);
            int bit = call.getArgument(5);
            short x = (short)(target.getCentreX() + ox + dx), y = (short)(target.getCentreY() + oy + dy);
            var lm = GameServices.level();var level = lm.getCurrentLevel();
            var provider = GameServices.backgroundPlaneCollisionOrNull();
            var preState = provider == null ? BackgroundPlaneCollisionProvider.State.INACTIVE : provider.state(lm);
            SensorResult returned = (SensorResult) call.callRealMethod();
            var fg = (SensorResult) read(observed, "reusableResult");
            String bgPool = direction == Direction.UP || direction == Direction.DOWN ? "bgScanResult" : "bgWallScanResult";
            var bg = (SensorResult) read(observed, bgPool);
            // Copy every result primitive immediately, before any other terrain query.
            var fgCopy = result(fg);var bgCopy = result(bg);var returnedCopy = result(returned);
            String selected = returned == fg ? "FG" : returned == bg ? "BG" : returned == null ? "NONE" : "OTHER";
            assertEquals(before, playable(target), "Actual scan must not mutate playable state");
            var postState = provider == null ? BackgroundPlaneCollisionProvider.State.INACTIVE : provider.state(lm);
            assertEquals(preState, postState, "Provider must be stable across actual scan");
            short bx = provider == null ? x : (short)provider.backgroundX(postState, x, direction);
            short by = provider == null ? y : (short)provider.backgroundY(postState, y);
            boolean rowPresent = level.hasBackgroundCollisionRowAt(by);
            var item = new LinkedHashMap<String, Object>();
            item.put("ownerCall", ownerCalls);item.put("actualCollisionSystemCallers", callers);item.put("inputs", before);
            item.put("direction", direction.name());item.put("offsetX", ox);item.put("offsetY", oy);
            item.put("projectedDx", dx);item.put("projectedDy", dy);item.put("lrbBitArgument", bit);
            item.put("fgProbeX", (int)x);item.put("fgProbeY", (int)y);item.put("bgProbeX", (int)bx);item.put("bgProbeY", (int)by);
            item.put("providerClass", provider == null ? null : provider.getClass().getName());
            item.put("providerStateBefore", preState);item.put("providerStateAfter", postState);
            item.put("zoneBackgroundYMask", GameServices.zoneRuntimeRegistry().current().backgroundLayoutYMask());
            item.put("backgroundRowPresent", rowPresent);item.put("bgPool", bgPool);
            item.put("backgroundWidthBlocks", level.getLayerWidthBlocks(1));item.put("backgroundHeightBlocks", level.getLayerHeightBlocks(1));
            item.put("actualFG", fgCopy);item.put("actualBG", postState.active() && rowPresent ? bgCopy : null);
            item.put("actualCombined", returnedCopy);item.put("selectedPlaneByReturnedIdentity", selected);
            item.put("foregroundLayer", 0);item.put("backgroundLayer", 1);
            item.put("FGterrain", terrain((byte)0, x, y, bit, direction, target.isLoopLowPlane()));
            item.put("BGterrain", terrain((byte)1, bx, by, bit, direction, false));
            assertEquals(before, playable(target), "Pure lookups must not mutate playable state");
            assertEquals(returnedCopy, result(returned), "Pure lookups must not overwrite returned pool");
            item.put("pooledBuffersShared", true);item.put("playableUnchangedAcrossScanAndLookup", true);
            item.put("returnedResultUnchangedAfterLookup", true);
            sink.accept(item);
            return returned; // Identical actual result object, unmodified.
        }).when(observed).scanWorld(any(Direction.class), anyShort(), anyShort(), anyShort(), anyShort(), anyInt());
        bind(observed);
    }

    public int ownerCalls() { return ownerCalls; }
    public int otherProbeCalls() { return otherProbeCalls; }

    private List<Map<String, Object>> terrain(byte layer, int x, int y, int bit, Direction direction, boolean loop) throws Exception {
        var list = new ArrayList<Map<String, Object>>();var lm = GameServices.level();var level = lm.getCurrentLevel();
        for (int qx : new int[]{x, x - 16, x + 16}) {
            var m = new LinkedHashMap<String, Object>();m.put("layer", (int)layer);m.put("x", qx);m.put("y", y);
            ChunkDesc desc = lm.getChunkDescAt(layer, qx, y, loop);
            if (desc == null) { m.put("descriptor", null);list.add(m);continue; }
            m.put("descriptor", desc.get());m.put("chunkIndex", desc.getChunkIndex());m.put("hFlip", desc.getHFlip());m.put("vFlip", desc.getVFlip());
            m.put("solidAtLrb", desc.isSolidityBitSet(bit));var chunk = level.getChunk(desc.getChunkIndex());
            if (chunk != null) {
                m.put("primaryCollisionIndex", chunk.getSolidTileIndex());m.put("secondaryCollisionIndex", chunk.getSolidTileAltIndex());
                m.put("selectedCollisionIndexFromLrb", bit >= 0xE ? chunk.getSolidTileAltIndex() : chunk.getSolidTileIndex());
            }
            var tile = lm.getSolidTileForChunkDesc(desc, bit);
            if (tile != null) {
                m.put("solidTileId", tile.getIndex());m.put("baseAngle", tile.getAngle() & 0xFF);
                m.put("transformedAngle", tile.getAngle(desc.getHFlip(), desc.getVFlip()) & 0xFF);
                if (direction == Direction.LEFT || direction == Direction.RIGHT) {
                    Method metric = GroundSensor.class.getDeclaredMethod("getWallMetric", com.openggf.level.SolidTile.class, ChunkDesc.class, int.class, Direction.class);
                    metric.setAccessible(true);m.put("signedMetric", metric.invoke(observed, tile, desc, y, direction));
                }
                m.put("widths", tile.widths.clone());m.put("heights", tile.heights.clone());
            }
            list.add(m);
        }
        return list;
    }

    public static Map<String, Object> playable(AbstractPlayableSprite p) {
        var m = new LinkedHashMap<String, Object>();
        m.put("x", p.getCentreX() & 0xFFFF);m.put("xSub", p.getXSubpixelRaw());m.put("y", p.getCentreY() & 0xFFFF);m.put("ySub", p.getYSubpixelRaw());
        m.put("vx", p.getXSpeed() & 0xFFFF);m.put("vy", p.getYSpeed() & 0xFFFF);m.put("g", p.getGSpeed() & 0xFFFF);
        m.put("angle", p.getAngle() & 0xFF);m.put("air", p.getAir());m.put("push", p.getPushing());m.put("inWater", p.isInWater());
        m.put("topSolidBit", (int)p.getTopSolidBit());m.put("lrbSolidBit", (int)p.getLrbSolidBit());m.put("xRadius", (int)p.getXRadius());m.put("yRadius", (int)p.getYRadius());
        m.put("objectControlled", p.isObjectControlled());m.put("moveLock", p.getMoveLockTimer());m.put("rolling", p.getRolling());m.put("sliding", p.isSliding());return m;
    }

    private static Map<String, Object> result(SensorResult r) {
        if (r == null) return null;
        var m = new LinkedHashMap<String, Object>();m.put("distance", (int)r.distance());m.put("tileId", r.tileId());m.put("angle", r.angle() & 0xFF);
        m.put("direction", r.direction() == null ? null : r.direction().name());m.put("foregroundAngleWritten", r.foregroundAngleWritten());m.put("foregroundAngle", r.foregroundAngle() & 0xFF);
        m.put("backgroundScanExecuted", r.backgroundScanExecuted());m.put("backgroundAngleWritten", r.backgroundAngleWritten());m.put("backgroundAngle", r.backgroundAngle() & 0xFF);
        m.put("restoreForegroundAngle", r.restoreForegroundAngleState());return m;
    }
    private static Object read(Object owner, String name) throws Exception {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name);f.setAccessible(true);return f.get(owner); }
            catch (NoSuchFieldException e) { }
        }
        throw new NoSuchFieldException(name);
    }
    private void bind(GroundSensor probe) throws Exception {
        Field f = CollisionSystem.class.getDeclaredField("calcRoomProbe");f.setAccessible(true);f.set(collision, probe);
    }
    @Override public void close() throws Exception { bind(original); }
}
