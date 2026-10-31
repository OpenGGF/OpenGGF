package com.openggf.level;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.graphics.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLevelMotionPair {
    private static final Object KEY = new LevelMotionOwners.ObjectKey(ObjectRefId.dynamic(3, 0, 7));
    private record Picture(LevelMotionPair.Sample sample, SpritePresentation.Tile tile) { }
    private static LevelScrollPresentation scroll(int x, int y, int bg, int period, short[] lines, short[] columns) {
        return new LevelScrollPresentation(new LevelScrollPresentation.Registers(x, y, x + 3,
                (short) y, (short) bg, bg, period, true, true, false,
                false, (short) 0, false, (short) 0, new ArenaMaskState(0, 320, 9)),
                new int[]{(-x << 16) | ((-bg) & 0xffff), ((-x - 2) << 16) | ((-bg - 2) & 0xffff)},
                lines, columns, new short[]{7, 11}, null);
    }
    private static Picture picture(int cameraX, int cameraY, int x, int y, int cap, MotionPacket.Anchoring anchoring) {
        var tile = new SpritePresentation.Tile(SpritePresentation.Layer.PLAYER, 1, 0, false, false, false,
                x - cameraX, y - cameraY, 8, 8, true, 15, false, 1);
        var owner = new MotionPacket.Owner(KEY, x, y, anchoring);
        var motion = mock(MotionPacket.class);
        when(motion.owners()).thenReturn(Map.of(KEY, owner));
        when(motion.owner(tile)).thenReturn(owner);
        return new Picture(new LevelMotionPair.Sample(motion, scroll(cameraX, cameraY, 0, 512, null, null),
                new LevelMotionPair.Metadata(cameraX, cameraY, 320, 224, cap, false), Map.of()), tile);
    }
    private static LevelMotionPair.Sample meta(Picture picture, int width, int height, boolean wrap, Set<MotionCut> cuts) {
        var sample = picture.sample(); var old = sample.metadata();
        return new LevelMotionPair.Sample(sample.motion(), sample.scroll(),
                new LevelMotionPair.Metadata(old.cameraBaseX(), old.cameraBaseY(), width, height, old.fastScrollCap(), wrap), cuts.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(cut -> cut, cut -> 1L)));
    }
    private static PictureSelection.Between phase(double alpha) { return (PictureSelection.Between) PictureSelection.between(alpha); }

    @Test void selectionRejectsInvalidPhasesAndAnEndpointAlwaysMeansExact() {
        assertSame(PictureSelection.LATEST, PictureSelection.between(1));
        assertSame(PictureSelection.LATEST, PictureSelection.between(2));
        for (double value : new double[]{-1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> PictureSelection.between(value));
        assertThrows(IllegalArgumentException.class, () -> new PictureSelection.Between(1));
        assertEquals(0, phase(0).alpha());
    }
    @ParameterizedTest @CsvSource({"16,15,true", "16,16,true", "16,17,false", "16,-16,true", "16,-17,false",
            "24,23,true", "24,24,true", "24,25,false", "24,-24,true", "24,-25,false"})
    void cameraBoundsApplyOnBothAxes(int cap, int step, boolean paired) {
        var old = picture(step, step, 100, 100, cap, MotionPacket.Anchoring.FOLLOW);
        var now = picture(0, 0, 100, 100, cap, MotionPacket.Anchoring.FOLLOW);
        assertEquals(paired, LevelMotionPair.between(old.sample(), now.sample()) != null);
    }
    @ParameterizedTest @CsvSource({"16,31", "16,32", "16,33", "16,-32", "16,-33", "24,47", "24,48", "24,49", "24,-48", "24,-49"})
    void theOwnerToleranceIsSeparateFromTheCameraBound(int cap, int step) {
        var old = picture(0, 0, 100 + step, 100 + step, cap, MotionPacket.Anchoring.FOLLOW);
        var now = picture(0, 0, 100, 100, cap, MotionPacket.Anchoring.FOLLOW);
        var blend = LevelMotionPair.between(old.sample(), now.sample()).at(phase(.5));
        int expected = Math.abs(step) > 2 * cap ? 0 : (int) Math.rint(step * .5);
        assertEquals(new SpriteMotionProjection.Offset(expected, expected), blend.sprites().offset(now.tile()));
    }
    @ParameterizedTest @CsvSource({"1,0", "3,2", "-1,0", "-3,-2", "5,2", "-5,-2"})
    void nativePixelRoundingIsSymmetricAtTies(int step, int expected) {
        assertEquals(new SpriteMotionProjection.Offset(expected, expected),
                new SpriteMotionProjection.Offset(step, step).weighted(.5));
    }
    @Test void aFollowedPlayerStaysFixedAndScreenAnchorsCancelTheSceneCamera() {
        var old = picture(4, 6, 104, 106, 16, MotionPacket.Anchoring.FOLLOW);
        var now = picture(12, 16, 112, 116, 16, MotionPacket.Anchoring.FOLLOW);
        for (double alpha : new double[]{0, .125, .5, .875, Math.nextDown(1d)}) {
            var blend = LevelMotionPair.between(old.sample(), now.sample()).at(phase(alpha));
            assertEquals(blend.camera(), blend.sprites().offset(now.tile()));
            var hud = new SpritePresentation.Tile(SpritePresentation.Layer.HUD, 2, 0, false, false, false,
                    20, 20, 8, 8, false, 0, false, 1);
            assertEquals(blend.camera(), blend.sprites().offset(hud));
            var screen = picture(12, 16, 112, 116, 16, MotionPacket.Anchoring.SCREEN);
            var screenBlend = LevelMotionPair.between(old.sample(), screen.sample()).at(phase(alpha));
            assertEquals(screenBlend.camera(), screenBlend.sprites().offset(screen.tile()));
            var world = picture(12, 16, 112, 116, 16, MotionPacket.Anchoring.WORLD);
            assertEquals(SpriteMotionProjection.Offset.ZERO,
                    LevelMotionPair.between(old.sample(), world.sample()).at(phase(alpha)).sprites().offset(world.tile()));
        }
    }
    @Test void wrapViewportAndCameraCutsRejectTheWholePairAndSmallOwnerCutsSnapOnlyThatOwner() {
        var old = picture(10, 10, 110, 110, 16, MotionPacket.Anchoring.FOLLOW);
        var now = picture(12, 12, 112, 112, 16, MotionPacket.Anchoring.FOLLOW);
        for (var current : new LevelMotionPair.Sample[]{meta(now, 321, 224, false, Set.of()),
                meta(now, 320, 225, false, Set.of()), meta(now, 320, 224, true, Set.of()),
                meta(now, 320, 224, false, Set.of(MotionCut.CAMERA))})
            assertNull(LevelMotionPair.between(old.sample(), current));
        assertNull(LevelMotionPair.between(meta(old, 320, 224, true, Set.of()), now.sample()));
        var cut = MotionCut.object(((LevelMotionOwners.ObjectKey) KEY).id());
        var blend = LevelMotionPair.between(old.sample(), meta(now, 320, 224, false, Set.of(cut))).at(phase(.5));
        assertEquals(new SpriteMotionProjection.Offset(-1, -1), blend.camera());
        assertEquals(SpriteMotionProjection.Offset.ZERO, blend.sprites().offset(now.tile()));
        assertEquals(new SpriteMotionProjection.Offset(-4, 4), LevelMotionPair.step(65534, 2, 2, 65534));
    }
    @Test void newOwnersAndNewPlayerIncarnationsCannotBorrowAMatchingSlotOrRosterRole() {
        var old = picture(0, 0, 100, 100, 16, MotionPacket.Anchoring.FOLLOW);
        var now = picture(0, 0, 110, 110, 16, MotionPacket.Anchoring.FOLLOW);
        when(old.sample().motion().owners()).thenReturn(Map.of());
        assertEquals(SpriteMotionProjection.Offset.ZERO,
                LevelMotionPair.between(old.sample(), now.sample()).at(phase(0)).sprites().offset(now.tile()));
        var first = new Object(); var second = new Object();
        var id = com.openggf.game.rewind.identity.PlayerRefId.mainPlayer();
        var firstKey = new LevelMotionOwners.PlayerKey(id, first); var secondKey = new LevelMotionOwners.PlayerKey(id, second);
        assertFalse(MotionCut.player(first).matches(secondKey));
        assertTrue(MotionCut.player(first).matches(firstKey));
        var oldOwner = new MotionPacket.Owner(firstKey, 100, 100, MotionPacket.Anchoring.FOLLOW);
        var newOwner = new MotionPacket.Owner(secondKey, 110, 110, MotionPacket.Anchoring.FOLLOW);
        when(old.sample().motion().owners()).thenReturn(Map.of(firstKey, oldOwner));
        when(now.sample().motion().owners()).thenReturn(Map.of(secondKey, newOwner));
        when(now.sample().motion().owner(now.tile())).thenReturn(newOwner);
        assertEquals(SpriteMotionProjection.Offset.ZERO,
                LevelMotionPair.between(old.sample(), now.sample()).at(phase(0)).sprites().offset(now.tile()));
    }
    @Test void scrollBlendsBackgroundLinesAndColumnsWhileKeepingCurrentForegroundDeformationAndShake() {
        var old = scroll(16, 36, 96, 512, new short[]{2, 4}, new short[]{6, 8});
        var now = scroll(20, 40, 100, 512, new short[]{4, 6}, new short[]{8, 10});
        var projected = now.forMotion(-2, -2, 320, now.backgroundMotionSince(old, 16, 320), .5);
        assertArrayEquals(new int[]{(-18 << 16) | (-98 & 0xffff), (-20 << 16) | (-100 & 0xffff)}, projected.horizontal());
        assertArrayEquals(new short[]{3, 5}, projected.backgroundLines());
        assertArrayEquals(new short[]{7, 9}, projected.backgroundColumns());
        assertArrayEquals(new short[]{7, 11}, projected.foregroundColumns());
        assertEquals(38, projected.registers().foregroundY());
        assertEquals(98, projected.registers().backgroundY());
        assertEquals(98, projected.registers().backgroundX());
        assertEquals(21, projected.registers().cameraXWithShake(), "Current shake remains +3");
        assertArrayEquals(new short[]{4, 6}, now.backgroundLines(), "A picture cannot mutate its source");
    }
    @Test void zeroDeltasRetainEveryDiscreteFieldIncludingTheCurrentMaskFadeSample() {
        var picture = picture(20, 40, 100, 120, 16, MotionPacket.Anchoring.FOLLOW);
        var pair = LevelMotionPair.between(picture.sample(), picture.sample());
        assertSame(picture.sample().scroll(), pair.at(phase(0)).scroll());
        assertSame(picture.sample().scroll(), pair.at(phase(.75)).scroll());
        assertEquals(SpriteMotionProjection.Offset.ZERO, pair.at(phase(0)).sprites().offset(picture.tile()));
    }
    @Test void oneDiscontinuousBackgroundWordSnapsEveryBackgroundQuantity() {
        var prior = scroll(16, 36, 96, 512, new short[]{2, 4}, new short[]{6, 8});
        var words = prior.horizontal();
        words[1] = (words[1] & 0xffff0000) | (-85 & 0xffff);
        var old = new LevelScrollPresentation(prior.registers(), words, prior.backgroundLines(),
                prior.backgroundColumns(), prior.foregroundColumns(), null);
        var now = scroll(20, 40, 100, 512, new short[]{4, 6}, new short[]{8, 10});
        assertNull(now.backgroundMotionSince(old, 16, 320));
        var projected = now.forMotion(-2, -2, 320, null, .5);
        assertEquals(-100, (short) projected.horizontal()[0]);
        assertArrayEquals(now.backgroundLines(), projected.backgroundLines());
        assertArrayEquals(now.backgroundColumns(), projected.backgroundColumns());
        assertEquals(now.registers().backgroundY(), projected.registers().backgroundY());
        assertEquals(now.registers().backgroundX(), projected.registers().backgroundX());
    }
    @Test void periodOrLineTopologyChangesSnapTheWholeBackground() {
        var now = scroll(20, 40, 100, 512, new short[]{4, 6}, new short[]{8, 10});
        assertNull(now.backgroundMotionSince(scroll(16, 36, 96, 256, new short[]{2, 4}, new short[]{6, 8}), 16, 320));
        assertNull(now.backgroundMotionSince(scroll(16, 36, 96, 512, null, new short[]{6, 8}), 16, 320));
        assertNull(now.backgroundMotionSince(scroll(16, 36, 96, 512, new short[]{2, 4}, new short[]{6}), 16, 320));
    }
    @ParameterizedTest @CsvSource({"15,true", "16,true", "17,false", "-15,true", "-16,true", "-17,false"})
    void backgroundStepBoundAppliesToTheCompleteLayer(int step, boolean paired) {
        var old = scroll(0, 0, 100 + step, 512, null, null);
        var now = scroll(0, 0, 100, 512, null, null);
        assertEquals(paired, now.backgroundMotionSince(old, 16, 320) != null);
    }
    @Test void descriptorResidencyAndNametableRebaseChangesSnapTheBackground() throws Exception {
        var old = scroll(16, 36, 96, 512, null, null);
        var now = scroll(20, 40, 100, 512, null, null);
        var field = LevelScrollPresentation.class.getDeclaredField("backgroundBuild"); field.setAccessible(true);
        field.set(old, build(1, 0)); field.set(now, build(1, 0));
        assertNotNull(now.backgroundMotionSince(old, 16, 320));
        field.set(now, build(2, 0));
        assertNull(now.backgroundMotionSince(old, 16, 320));
        field.set(now, build(1, 1));
        assertNull(now.backgroundMotionSince(old, 16, 320));
    }
    private static BackgroundBuildParameters build(long revision, int base) {
        return new BackgroundBuildParameters(false, false, false, false, false, false, -1, false,
                new com.openggf.game.internal.BackgroundBuildProjection(revision, (x, y) -> 0, ignored -> base));
    }
    @Test void cameraPairingUsesTheUnshakenRenderCopyWhileTheCurrentShakeStaysDiscrete() {
        var old = shaken(picture(16, 36, 116, 136, 16, MotionPacket.Anchoring.FOLLOW), 7, 5);
        var now = shaken(picture(20, 40, 120, 140, 16, MotionPacket.Anchoring.FOLLOW), -3, -9);
        var blend = LevelMotionPair.between(old.sample(), now.sample()).at(phase(.5));
        assertEquals(new SpriteMotionProjection.Offset(-2, -2), blend.camera());
        assertEquals(blend.camera(), blend.sprites().offset(now.tile()));
        assertEquals(15, blend.scroll().registers().cameraXWithShake());
        assertEquals(29, blend.scroll().registers().foregroundY());
    }
    private static Picture shaken(Picture picture, int x, int y) {
        var sample = picture.sample(); var scroll = sample.scroll(); var r = scroll.registers();
        var registers = new LevelScrollPresentation.Registers(r.cameraX(), r.cameraY(), r.cameraX() + x,
                (short) (r.foregroundY() + y), r.backgroundY(), r.backgroundX(), r.backgroundPeriod(),
                r.heatHaze(), r.perLineForeground(), r.reversePlanes(), r.overrideForegroundY(),
                r.overriddenForegroundY(), r.overrideBackgroundY(), r.overriddenBackgroundY(), r.arenaMask());
        var shaken = new LevelScrollPresentation(registers, scroll.horizontal(), scroll.backgroundLines(),
                scroll.backgroundColumns(), scroll.foregroundColumns(), null);
        var owner = sample.motion().owners().get(KEY);
        var tile = new SpritePresentation.Tile(SpritePresentation.Layer.PLAYER, 1, 0, false, false, false,
                owner.x() - r.cameraX() - x, owner.y() - r.cameraY() - y, 8, 8, true, 15, false, 1);
        when(sample.motion().owner(tile)).thenReturn(owner);
        when(sample.motion().cameraX()).thenReturn(r.cameraX() + x);
        when(sample.motion().cameraY()).thenReturn(r.cameraY() + y);
        return new Picture(new LevelMotionPair.Sample(sample.motion(), shaken, sample.metadata(), sample.cuts()), tile);
    }
    @Test void persistentPlaneResidencyBoundHasWitnessesOnBothSides() throws Exception {
        var old = scroll(16, 36, 96, 512, null, null);
        var now = scroll(20, 40, 100, 512, null, null);
        var mode = LevelScrollPresentation.class.getDeclaredField("tilemapMode"); mode.setAccessible(true);
        mode.set(old, com.openggf.level.scroll.BgTilemapUpdateMode.PERSISTENT_NAMETABLE_64X32);
        assertNull(now.backgroundMotionSince(old, 16, 320));
        mode.set(now, com.openggf.level.scroll.BgTilemapUpdateMode.PERSISTENT_NAMETABLE_64X32);
        assertNotNull(now.backgroundMotionSince(old, 16, 508));
        assertNull(now.backgroundMotionSince(old, 16, 509));
    }
}
