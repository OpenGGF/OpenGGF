package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Constructed component ingress with literal complete native R12B6/5 tables.
 * N1ecc5f3: Springboard/Main.asm:13-373; Object Tile ID Set.asm:7-17;
 * Object Spawner.asm:385-446. These are instruction/branch controls, not live World/route proof.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdSpringboard {
    private static final int WHOLE = 47, MAP = 0x20BE7C;
    private static final byte[] MAPS = hex("000c001800240030003c004802fc010000f8fc080002000002fc050005f800050009080002fc05000df8f0050011080002fc01080000fc080802e80002fc050805f800050809e80002fc05080df8f0050811e800");
    private static final byte[] SCRIPTS = hex("000a001a0024002e0032020001000200010003040305030403ff0200010002000100ff000203040305030403ff000000ff000003ff00");
    private static final byte[] BOXES = hex("10f010f010f004fc09f7381000e804fc00e80c00180004fc18000c0020e020e0");
    private static final byte[] TILES = hex("002400260028002a002c002e00380032003600340030003a003c003e004000420044004623ea23ea2399241e23af434c0330041e400040000372035e83400000033804900000046c");

    @Test void literalSixFramesFiveScriptsAndEightSignedBoxesAreComplete() {
        var a = assets();
        assertEquals(84, a.visual().mappings().length());
        assertEquals(54, a.visual().animationTable().length());
        assertEquals(6, a.visual().mappingFrames().size());
        assertEquals(5, a.visual().animations().size());
        assertEquals(32, a.hitboxes().length());
        assertEquals(72, a.objectTileIds().length());
        assertEquals(List.of(0, 1, 0, 2, 0, 1, 0), a.visual().animations().get(1).frameBytes());
        assertEquals(List.of(3, 4, 3, 5, 3, 4, 3), a.visual().animations().get(2).frameBytes());
        assertArrayEquals(hex("00e804fc00e80c00180004fc18000c00"), Arrays.copyOfRange(BOXES, 12, 28));
    }

    @Test void initReturnsWithoutAnimationOrDrawAndPreservesEveryOtherByte() {
        var h = new Harness();
        h.edit(s -> dirty(s).moveByte(ID, 0x28).moveByte(ROUTINE, 0).moveByte(SPRITE_FLAGS, 0xE2)
                .moveByte(SUBTYPE, 0).moveByte(SUBTYPE2, 0).moveByte(SAVED_FLAGS_ID, 0).moveWord(X, 0x1000));
        var before = h.slot(); var primary = h.primary; var env = h.environment;
        var out = h.run();
        byte[] expected = before.copyBytes();
        putLong(expected, 4, MAP); expected[1] |= 4; expected[0x18] = 3; expected[0x19] = 0x10;
        expected[0x17] = 0x18; expected[0x16] = 4; putWord(expected, 2, 0x041E);
        expected[0x1C] = 3; expected[0x24] = 2;
        assertArrayEquals(expected, out.state().copyBytes());
        assertSame(primary, h.primary); assertSame(env, h.environment);
        assertTrue(h.draws.isEmpty()); assertTrue(h.primaryCalls.isEmpty());
        assertEquals(9, h.publications.size());
        assertTrue(out.effects().getLast() instanceof CdSpringboard.DespawnChecked);
    }

    @Test void initFlipUsesRawSubtypeOrDirtySpriteBitAndClearsOnlyTheTwoFlipBits() {
        for (int[] c : new int[][]{{1, 0}, {0xFF, 0x80}, {0, 1}}) {
            var h = new Harness(); h.edit(s -> s.moveByte(SUBTYPE, c[0]).moveByte(SPRITE_FLAGS, c[1])
                    .moveByte(FLAGS, 0xFF)); h.run();
            assertEquals(4, h.slot().routine()); assertEquals(4, h.slot().unsignedByte(ANIMATION));
            assertEquals((c[1] | 4) & 0xFE, h.slot().spriteFlags()); assertEquals(0xFE, h.slot().flags());
            assertEquals(11, h.publications.size()); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void initRealOuterDespawnRunsEvenThoughDrawDoesNot() {
        var h = new Harness(); h.run(new CdSpringboard.Globals(0x80, 0, 0x1080));
        assertArrayEquals(new byte[64], h.slot().copyBytes()); assertTrue(h.draws.isEmpty());
    }

    @Test void reachedTileDirectoryFaultPreservesExactlySixInitStores() {
        for (int length : new int[]{0, 14, 15}) {
            var h = new Harness(withTiles(Arrays.copyOf(TILES, length)));
            var before = h.slot();
            var failure = assertThrows(CdSpringboard.UnsupportedSpringboard.class, h::run);
            assertEquals(CdSpringboard.UnsupportedBoundary.TILE_ADDRESS, failure.boundary());
            assertEquals(14, failure.value());
            byte[] expected = before.copyBytes(); putLong(expected, 4, MAP); expected[1] |= 4;
            expected[0x18] = 3; expected[0x19] = 0x10; expected[0x17] = 0x18; expected[0x16] = 4;
            assertArrayEquals(expected, h.slot().copyBytes()); assertEquals(6, h.publications.size());
            assertTrue(h.draws.isEmpty());
        }
    }

    @Test void tileSecondaryReadsActualAdjacentWordsButCannotExtendTheFinite72ByteView() {
        int[] words = {0x041E, 0x4000, 0x4000, 0x0372, 0x035E, 0x8340, 0, 0x0338, 0x0490, 0, 0x046C};
        for (int secondary = 0; secondary < words.length; secondary++) {
            var h = new Harness(); final int value = secondary;
            h.edit(s -> s.moveByte(SUBTYPE2, value)); h.run(); assertEquals(words[secondary], h.slot().word(TILE));
        }
        var h = new Harness(withTiles(Arrays.copyOf(TILES, 74))); h.edit(s -> s.moveByte(SUBTYPE2, 11));
        var e = assertThrows(CdSpringboard.UnsupportedSpringboard.class, h::run);
        assertEquals(72, e.value()); assertEquals(6, h.publications.size());
    }

    @Test void tileDirectoryUsesSignedRelativeThenSeparateUnsignedSecondaryAddressing() {
        for(int[] c:new int[][]{{0x7FFF,255,33277},{0x8000,255,-32258},{0xFFFF,0,-1}}){
            byte[] tiles=TILES.clone();putWord(tiles,14,c[0]);var h=new Harness(withTiles(tiles));
            h.edit(s->s.moveByte(SUBTYPE2,c[1]));
            var e=assertThrows(CdSpringboard.UnsupportedSpringboard.class,h::run);
            assertEquals(c[2],e.value());assertEquals(6,h.publications.size());assertTrue(h.draws.isEmpty());
        }
    }

    @Test void normalAndFlipMainCollisionAlignsTheEntireYLongBeforeParentRoutineStores() {
        for (int routine : new int[]{2, 4}) {
            var h = new Harness(); h.main(routine);
            h.edit(s -> s.moveLong(Y_POSITION, 0x02001234));
            var before = h.primary; h.run();
            assertEquals(0x01ED1234, h.primary.motion().centreY16_16());
            assertEquals(routine == 2 ? 10 : 12, h.slot().routine());
            assertEquals(routine == 2 ? 3 : 4, h.slot().unsignedByte(ANIMATION));
            assertEquals(CdSpringboard.PrimaryField.CENTRE_Y_LONG, h.primaryCalls.getFirst().field());
            assertEquals(0x020055AA, h.primaryCalls.getFirst().expectedState().motion().centreY16_16());
            assertSame(before, h.primaryCalls.getFirst().expectedState());
            assertEquals(List.of("object", "primary:CENTRE_Y_LONG", "object", "object", "object", "draw"), h.events);
        }
    }

    @Test void debugHighByteDeathAndUpwardsGatesDoNotReadEvenAnEmptyHitboxView() {
        for (int[] c : new int[][]{{0x0100, 2, 0}, {0xFF00, 2, 0}, {0, 6, 0}, {0, 255, 0}, {0, 2, -1}}) {
            var h = new Harness(withBoxes(new byte[0])); h.main(2); h.environment = environment(c[0]);
            h.primary = player(0x1000, 0x200, c[2], c[1], 0, 9, 19);
            h.edit(s -> s.moveByte(FLAGS, 0xFF)); h.run();
            assertEquals(0xF7, h.slot().flags()); assertTrue(h.primaryCalls.isEmpty()); assertEquals(1, h.draws.size());
        }
    }

    @Test void lowDebugByteIsIgnoredAndFirstReachedHitboxByteFaultIsAfterNoObjectStores() {
        var h = new Harness(withBoxes(new byte[0])); h.main(2); h.environment = environment(0x00FF);
        var before = h.slot();
        var e = assertThrows(CdSpringboard.UnsupportedSpringboard.class, h::run);
        assertEquals(CdSpringboard.UnsupportedBoundary.HITBOX_ADDRESS, e.boundary()); assertEquals(20, e.value());
        assertArrayEquals(before.copyBytes(), h.slot().copyBytes()); assertTrue(h.draws.isEmpty());
    }

    @Test void horizontalRejectionDoesNotReadLaterHitboxBytes() {
        var h = new Harness(withBoxes(Arrays.copyOf(BOXES, 21))); h.main(2);
        h.primary = player(0x1100, 0x200, 0, 2, 0, 9, 19);
        assertDoesNotThrow(() -> { h.run(); }); assertEquals(0, h.slot().flags() & 8);
        var h2 = new Harness(withBoxes(Arrays.copyOf(BOXES, 21))); h2.main(2);
        var e = assertThrows(CdSpringboard.UnsupportedSpringboard.class, h2::run);
        assertEquals(21, e.value()); assertTrue(h2.publications.isEmpty());
    }

    @Test void allFourStrictEdgesAndSignedRadiusBytesUseNativeBplWordDifferences() {
        // Normal idle box [X,X+24] / [Y-4,Y+4]; equality rejects each separating edge.
        for (int[] p : new int[][]{{0x1021,0x200,9,19},{0x0FF7,0x200,9,19},
                {0x1000,0x217,9,19},{0x1000,0x1E9,9,19},{0x1000,0x200,0xF7,19}}) {
            var h = new Harness(); h.main(2); h.primary = player(p[0],p[1],0,2,0,p[2],p[3]);
            h.run(); assertTrue(h.primaryCalls.isEmpty(), Arrays.toString(p)); assertEquals(0,h.slot().flags() & 8);
        }
        var h = new Harness(); h.main(2); h.primary = player(0x1020,0x216,0,2,0,9,19);
        h.run(); assertEquals(1,h.primaryCalls.size());
    }

    @Test void wordWrapCollisionUsesBplSignFlagRatherThanOverflowCorrectedComparison() {
        var h = new Harness(); h.main(2); h.edit(s -> s.moveWord(X, 0xFFF8).moveWord(Y, 0xFFFE));
        h.primary = player(0, 0, 0, 2, 0, 9, 19); h.run(); assertEquals(1, h.primaryCalls.size());
        assertEquals(0xFFEB0000, h.primary.motion().centreY16_16());
    }

    @Test void main2CollisionKeepsRoutineAndNoPrimaryStoreWhileMissChoosesAirBranch() {
        for (int r : new int[]{6,8}) {
            var h = new Harness(); h.main(r); h.run(); assertEquals(r,h.slot().routine()); assertTrue(h.primaryCalls.isEmpty());
            for (int flags : new int[]{0,2}) {
                var miss = new Harness(); miss.main(r);
                miss.primary=player(0x1100,0x200,0,2,flags,9,19); miss.run();
                assertEquals(flags==0 ? (r==6?2:4) : (r==6?10:12),miss.slot().routine());
                assertEquals(flags==0?0:0x40,miss.slot().unsignedByte(SPRINGBOARD_FLING));
            }
        }
    }

    @Test void freshBouncePublishesEveryPrimaryInstructionInOrderAndPreservesFullUntouchedState() {
        var h = new Harness(); h.main(10); h.primary=player(0x1000,0x200,0x200,2,0x20,9,19);
        var before=h.primary; var env=h.environment; h.run();
        assertEquals(-0x300,h.primary.motion().yVelocity()); assertEquals(0x020555AA,h.primary.motion().centreY16_16());
        assertEquals(0x06,h.primary.motion().flags()); assertEquals(14,h.primary.motion().yRadius());
        assertEquals(7,h.primary.motion().xRadius()); assertEquals(2,h.primary.motion().animation());
        assertEquals(0x7B,h.primary.motion().playerJump()); assertEquals(0,h.primary.motion().stick());
        assertSame(env,h.environment);
        assertEquals(List.of(CdSpringboard.PrimaryField.Y_VELOCITY,CdSpringboard.PrimaryField.FLAGS,
                CdSpringboard.PrimaryField.FLAGS,CdSpringboard.PrimaryField.STICK,CdSpringboard.PrimaryField.Y_RADIUS,
                CdSpringboard.PrimaryField.X_RADIUS,CdSpringboard.PrimaryField.Y_RADIUS,CdSpringboard.PrimaryField.X_RADIUS,
                CdSpringboard.PrimaryField.CENTRE_Y_WORD,CdSpringboard.PrimaryField.FLAGS,
                CdSpringboard.PrimaryField.ANIMATION),h.primaryCalls.stream().map(CdSpringboard.PrimaryCall::field).toList());
        assertSame(before.header(),h.primary.header()); assertSame(before.animation(),h.primary.animation());
        assertSame(before.timers(),h.primary.timers()); assertSame(before.history(),h.primary.history());
        assertSame(before.objectLinks(),h.primary.objectLinks()); assertEquals(before.rotationAngle(),h.primary.rotationAngle());
        assertEquals(before.rotationDistance(),h.primary.rotationDistance()); assertEquals(0x3F,h.slot().unsignedByte(SPRINGBOARD_FLING));
    }

    @Test void alreadyAirRollingBounceClearsJumpAndSetsRollJumpWithNormalRadii() {
        var h=new Harness();h.main(12);h.primary=player(0x1000,0x200,0,2,0x26,7,14);h.run();
        assertEquals(0,h.primary.motion().playerJump()); assertEquals(0x16,h.primary.motion().flags());
        assertEquals(19,h.primary.motion().yRadius());assertEquals(9,h.primary.motion().xRadius());
        assertEquals(0x020055AA,h.primary.motion().centreY16_16());
        assertEquals(List.of(CdSpringboard.PrimaryField.Y_VELOCITY,CdSpringboard.PrimaryField.FLAGS,
                CdSpringboard.PrimaryField.PLAYER_JUMP,CdSpringboard.PrimaryField.FLAGS,CdSpringboard.PrimaryField.STICK,
                CdSpringboard.PrimaryField.Y_RADIUS,CdSpringboard.PrimaryField.X_RADIUS,CdSpringboard.PrimaryField.FLAGS),
                h.primaryCalls.stream().map(CdSpringboard.PrimaryCall::field).toList());
    }

    @Test void bounceSpeedAddsThenCapsUsingTheNativeCmpWordNFlag() {
        for(int[] c:new int[][]{{0,-0x100},{0x8FF,-0x9FF},{0x900,-0xA00},{0x7FFF,-0xA00}}){
            var h=new Harness();h.main(10);h.primary=player(0x1000,0x200,c[0],2,4,9,19);h.run();
            assertEquals(c[1],h.primary.motion().yVelocity());
        }
    }

    @Test void flingNoCollisionDecrementsRawByteAndZeroWrapsWithoutReset() {
        for(int r:new int[]{10,12})for(int t:new int[]{0,2,0x80,0xFF}){
            var h=new Harness();h.main(r);h.edit(s->s.moveByte(SPRINGBOARD_FLING,t));
            h.primary=player(0x1100,0x200,0,2,0,9,19);h.run();
            assertEquals((t-1)&255,h.slot().unsignedByte(SPRINGBOARD_FLING));assertEquals(r,h.slot().routine());
            assertTrue(h.primaryCalls.isEmpty());
        }
    }

    @Test void flingExpiryUsesDifferentNativeNormalAndFlipStoreOrder() {
        for(int r:new int[]{10,12}){
            var h=new Harness();h.main(r);h.edit(s->s.moveByte(SPRINGBOARD_FLING,1));
            h.primary=player(0x1100,0x200,0,2,0,9,19);h.run();
            assertEquals(r==10?2:4,h.slot().routine());assertEquals(r==10?3:4,h.slot().unsignedByte(ANIMATION));
            assertEquals(0x40,h.slot().unsignedByte(SPRINGBOARD_FLING));
            var stores=h.publications; // animation; collision clear; timer0; then exactly ordered reset instructions.
            assertEquals(0,stores.get(2).output().unsignedByte(SPRINGBOARD_FLING));
            assertEquals(r==10?2:12,stores.get(3).output().routine());
            assertEquals(r==10?0:0x40,stores.get(3).output().unsignedByte(SPRINGBOARD_FLING));
        }
    }

    @Test void allFiveAnimationScriptsAdvanceThroughEveryRealFrameAndFfLoop() {
        int[][] nativeFrames={{0,1,0,2,0,1,0,3,4,3,5,3,4,3},{0,1,0,2,0,1,0},{3,4,3,5,3,4,3},{0},{3}};
        for(int anim=0;anim<5;anim++){
            var h=new Harness();h.main(6);final int id=anim;
            h.edit(s->s.moveByte(ANIMATION,id).moveByte(PREVIOUS_ANIMATION,id).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0));
            var frames=new ArrayList<Integer>();
            for(int i=0;i<nativeFrames[anim].length+1;i++){
                h.edit(s->s.moveByte(ANIMATION_TIME,0));h.run();frames.add(h.slot().unsignedByte(MAP_FRAME));
            }
            var expected=new ArrayList<Integer>();for(int frame:nativeFrames[anim])expected.add(frame);expected.add(nativeFrames[anim][0]);
            assertEquals(expected,frames);
        }
    }

    @Test void drawMutationsAreReloadedBeforeDespawnAndNeverOverwritten() {
        var h=new Harness();h.main(6);h.onDraw=s->s.moveByte(ID,0x7F).moveByte(SPRITE_FLAGS,0)
                .moveWord(X,0x3000).moveByte(SAVED_FLAGS_ID,0);
        var result=h.run();assertTrue(result.deleted());assertArrayEquals(new byte[64],h.slot().copyBytes());
    }

    @Test void drawnOnscreenBitKeepsDistanceButCannotSuppressRawWarpDeletion() {
        for(int period:new int[]{0,0x80}){
            var h=new Harness();h.main(6);h.onDraw=s->s.moveByte(SPRITE_FLAGS,0x80).moveWord(X,0x4000);
            var result=h.run(new CdSpringboard.Globals(period,0,0x1080));assertEquals(period!=0,result.deleted());
        }
    }

    @Test void despawnDistanceEqualityKeepsAndOneBucketBeyondDeletes() {
        for(int[] c:new int[][]{{0x1280,0},{0x1300,1},{0x0F80,1}}){
            var h=new Harness();h.main(6);h.onDraw=s->s.moveByte(SPRITE_FLAGS,0).moveWord(X,c[0]);
            assertEquals(c[1]!=0,h.run(new CdSpringboard.Globals(0,0,0x1080)).deleted());
        }
    }

    @Test void savedClearUsesRawPeriodAliasAndPreservesEveryOtherBankBit() {
        var h=new Harness();h.main(6);h.edit(s->s.moveByte(SAVED_FLAGS_ID,1));
        mark(h.placement,1,2,0xE5);byte[] before=h.placement.capture().savedFlags().copy();
        h.run(new CdSpringboard.Globals(0x85,0,0x1080)); // warp clamps period5 to2, saved1=>index7.
        byte[] expected=before.clone();expected[7]&=0x7F;assertArrayEquals(expected,h.placement.capture().savedFlags().copy());
        // No-warp raw period5 must address saved1*3+5 = saved2/period2, not clamp.
        var alias=new Harness();alias.main(6);alias.edit(s->s.moveByte(SAVED_FLAGS_ID,1));mark(alias.placement,2,2,0xE5);
        alias.onDraw=s->s.moveWord(X,0x3000).moveByte(SPRITE_FLAGS,0);alias.run(new CdSpringboard.Globals(5,0,0x1080));
        assertEquals(0x65,alias.placement.capture().savedFlags().unsignedByte(10));
    }

    @Test void savedBankEndFaultOccursAfterDrawAndBeforeAnyClearOrDelete() {
        var h=new Harness();h.main(6);h.edit(s->s.moveByte(SAVED_FLAGS_ID,255));
        h.onDraw=s->s.moveWord(X,0x3000).moveByte(SPRITE_FLAGS,0);
        var bank=h.placement.capture().savedFlags().copy();
        var e=assertThrows(CdSpringboard.UnsupportedSpringboard.class,()->h.run(new CdSpringboard.Globals(127,0,0x1080)));
        assertEquals(CdSpringboard.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,e.boundary());assertEquals(894,e.value());
        assertEquals(1,h.draws.size());assertEquals(0x28,h.slot().id());assertArrayEquals(bank,h.placement.capture().savedFlags().copy());
    }

    @Test void invalidRoutineAndWrongIdFailBeforeAnyMutationOrPlayerRead() {
        for(int routine:new int[]{1,3,5,7,9,11,13,14,255}){
            var h=new Harness();h.main(routine);var before=h.slot();var primary=h.primary;
            var e=assertThrows(CdSpringboard.UnsupportedSpringboard.class,h::run);
            assertEquals(CdSpringboard.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());
            assertArrayEquals(before.copyBytes(),h.slot().copyBytes());assertSame(primary,h.primary);assertTrue(h.events.isEmpty());
        }
        var h=new Harness();h.edit(s->s.moveByte(ID,0x7F));assertThrows(IllegalArgumentException.class,h::run);
    }

    @Test void perStorePrimaryFailureKeepsOriginalExceptionAndExactObjectPlayerPrefix() {
        var h=new Harness();h.main(10);var failure=new IllegalStateException("primary-store");
        h.afterPrimary=call->{if(call.field()==CdSpringboard.PrimaryField.STICK)throw failure;};
        assertSame(failure,assertThrows(IllegalStateException.class,h::run));
        assertEquals(-0x100,h.primary.motion().yVelocity());assertEquals(0,h.primary.motion().stick());
        assertEquals(19,h.primary.motion().yRadius());assertEquals(0x40,h.slot().unsignedByte(SPRINGBOARD_FLING));
        assertEquals(10,h.slot().routine());assertTrue(h.draws.isEmpty());
        assertFalse(h.body.isCurrentCall(h.primaryCalls.getLast())); // failed callback receipt expires.
    }

    @Test void drawFailureKeepsAnimationAndItsOwnWritesWithoutReplayingOrDespawning() {
        var h=new Harness();h.main(6);var failure=new IllegalStateException("draw");
        h.drawFailure=failure;h.onDraw=s->s.moveWord(X,0x3000);
        assertSame(failure,assertThrows(IllegalStateException.class,h::run));
        assertEquals(0x3000,h.slot().centreX());assertEquals(0x28,h.slot().id());assertEquals(1,h.draws.size());
        assertFalse(h.body.isCurrentCall(h.lastDraw));
    }

    @Test void originalPendingReceiptsRejectEqualClonesBeforeDuringAndAfterEachCallback() {
        var h=new Harness();h.main(10);h.checkReceipts=true;h.run();
        assertTrue(h.primaryCalls.size()>1);assertFalse(h.body.isCurrentCall(h.primaryCalls.getFirst()));
        assertFalse(h.body.isCurrentCall(h.publications.getFirst()));assertFalse(h.body.isCurrentCall(h.lastDraw));
    }

    @Test void caughtReentryFromPublicationPrimaryAndDrawDoesNotPoisonOuterOrNextRun() {
        var h=new Harness();h.main(10);h.reenter=true;h.run();
        assertEquals(1,h.draws.size());assertEquals(0x3F,h.slot().unsignedByte(SPRINGBOARD_FLING));
        h.reenter=false;assertDoesNotThrow(() -> { h.run(); });assertEquals(2,h.draws.size());
    }

    @Test void publicationFailureRetainsCompletedEarlierStoresAndReleasesTransientScope() {
        var h=new Harness();var failure=new IllegalStateException("after-store");h.failAfterPublication=4;h.publicationFailure=failure;
        assertSame(failure,assertThrows(IllegalStateException.class,h::run));
        assertEquals(0x10,h.slot().unsignedByte(WIDTH));assertEquals(0,h.slot().unsignedByte(X_RADIUS));
        h.failAfterPublication=0;assertDoesNotThrow(() -> { h.run(); });assertEquals(2,h.slot().routine());
    }

    @Test void resultEffectsAreImmutableObservationsWithoutPlayerOrPoolAuthority() {
        var h=new Harness();h.main(10);var out=h.run();var image=h.slot().copyBytes();var primary=h.primary;
        assertThrows(UnsupportedOperationException.class,()->out.effects().clear());
        var forged=new CdSpringboard.Result(out.state(),out.primary(),out.environment(),List.of(new CdSpringboard.DespawnChecked(true)));
        assertTrue(forged.deleted());assertArrayEquals(image,h.slot().copyBytes());assertSame(primary,h.primary);
    }

    @Test void widthsAndAssetShapeRejectOnlyTheirOwnContractWithoutCurrentArtWork() {
        for(int bad:new int[]{-1,256})assertThrows(IllegalArgumentException.class,()->new CdSpringboard.Globals(bad,0,0));
        assertThrows(IllegalArgumentException.class,()->new CdSpringboard.Globals(0,256,0));
        assertThrows(IllegalArgumentException.class,()->new CdSpringboard.Globals(0,0,65536));
        assertDoesNotThrow(()->new CdSpringboard.Assets(CdAssetBytes.copyOf(new byte[0]),CdAssetBytes.copyOf(new byte[0]),visual(),0xF120BE7C));
        assertThrows(IllegalArgumentException.class,()->new CdSpringboard.Assets(CdAssetBytes.copyOf(TILES),CdAssetBytes.copyOf(BOXES),
                CdObjectVisualAssets.decodeStatic(hex("0002010000000000"),1,21),MAP));
    }

    @Test void everyReachedHitboxByteFaultRetainsOnlyTheEarlierFlingAnimationStore() {
        for(int length=24;length<28;length++){
            var h=new Harness(withBoxes(Arrays.copyOf(BOXES,length)));h.main(10);
            var before=h.slot();
            var e=assertThrows(CdSpringboard.UnsupportedSpringboard.class,h::run);
            assertEquals(length,e.value());assertEquals(CdSpringboard.UnsupportedBoundary.HITBOX_ADDRESS,e.boundary());
            byte[] expected=before.copyBytes();expected[0x1C]=1;
            assertArrayEquals(expected,h.slot().copyBytes());assertEquals(1,h.publications.size());
            assertTrue(h.primaryCalls.isEmpty());assertTrue(h.draws.isEmpty());
        }
    }

    @Test void dirtyFlingFull64ImageChangesOnlyNativeStoresAndTheActualAnimationTimer() {
        var h=new Harness();h.edit(s->dirty(s).moveByte(ID,0x28).moveByte(ROUTINE,10)
                .moveByte(SPRITE_FLAGS,0xE6).moveByte(FLAGS,0xD5).moveByte(SAVED_FLAGS_ID,0)
                .moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_TIME,2)
                .moveByte(SPRINGBOARD_FLING,7).moveWord(X,0x1000).moveWord(Y,0x200));
        h.environment=environment(0x100);var before=h.slot();var primary=h.primary;
        h.run();byte[] expected=before.copyBytes();expected[0x22]&=(byte)0xF7;
        expected[0x2A]=6;expected[0x1E]=1;expected[1]&=0x7F;
        assertArrayEquals(expected,h.slot().copyBytes());assertSame(primary,h.primary);
    }

    @Test void exactPrimaryStoreSitesCarryNativeWidthsAndEveryUnwrittenMotionField() {
        var h=new Harness();h.main(10);var m=h.primary.motion();h.run();var n=h.primary.motion();
        assertEquals(List.of(0x0C,0x0C,0x12,0x22,0x3C,0x38,0x16,0x17,0x1C),
                Arrays.stream(CdSpringboard.PrimaryField.values()).map(CdSpringboard.PrimaryField::byteOffset).toList());
        assertEquals(List.of(4,2,2,1,1,1,1,1,1),
                Arrays.stream(CdSpringboard.PrimaryField.values()).map(CdSpringboard.PrimaryField::byteWidth).toList());
        assertEquals(m.centreX16_16(),n.centreX16_16());assertEquals(m.xVelocity(),n.xVelocity());
        assertEquals(m.groundVelocity(),n.groundVelocity());assertEquals(m.routine(),n.routine());
        assertEquals(m.angle(),n.angle());assertEquals(m.spriteFlags(),n.spriteFlags());
        assertEquals(m.previousAnimation(),n.previousAnimation());assertEquals(m.charge(),n.charge());
        assertEquals(m.moveLock(),n.moveLock());assertEquals(m.playerControl(),n.playerControl());
        assertEquals(m.previousPrimaryAngle(),n.previousPrimaryAngle());assertEquals(m.previousSecondaryAngle(),n.previousSecondaryAngle());
    }

    @Test void everyOtherPhysicalSlotAndTheWholeSavedBankRemainUntouchedDuringBounce() {
        var h=new Harness();h.main(10);
        for(int whole=32;whole<128;whole++)if(whole!=WHOLE){
            var s=h.placement.pool().state(whole);h.placement.pool().write(dirty(s).moveByte(ID,0x7F));
        }
        mark(h.placement,1,1,0xE7);var slots=h.placement.pool().capture().slots();var bank=h.placement.capture().savedFlags().copy();
        h.run();for(int i=0;i<96;i++)if(i!=WHOLE-32)assertSame(slots.get(i),h.placement.pool().capture().slots().get(i));
        assertArrayEquals(bank,h.placement.capture().savedFlags().copy());
    }

    @Test void restoringExistingPlacementAndCallerPairReplaysWithoutAnyBodyCapturePartition() {
        var h=new Harness();h.main(10);var cut=h.placement.capture();var primary=h.primary;var environment=h.environment;
        var first=h.run();h.placement.restore(cut);h.primary=primary;h.environment=environment;
        assertFalse(h.body.isCurrentCall(null));var second=h.run();
        assertEquals(first,second);assertEquals(2,h.draws.size());
    }

    @Test void lastOwnedSavedByteAndZeroSavedIdUseTheirActualConditionalPaths() {
        var h=new Harness();h.main(6);h.edit(s->s.moveByte(SAVED_FLAGS_ID,254));mark(h.placement,254,1,0xE5);
        h.run(new CdSpringboard.Globals(0x81,0,0x1080));
        assertEquals(0x65,h.placement.capture().savedFlags().unsignedByte(765));assertArrayEquals(new byte[64],h.slot().copyBytes());
        var zero=new Harness();zero.main(6);var bank=zero.placement.capture().savedFlags().copy();
        assertDoesNotThrow(()->zero.run(new CdSpringboard.Globals(0xFF,0x80,0x1080)));
        assertArrayEquals(bank,zero.placement.capture().savedFlags().copy());assertArrayEquals(new byte[64],zero.slot().copyBytes());
    }

    @Test void warpDirectionIsSignedByteThenWordClampedBeforeClearingOnlyLoadedBit() {
        for(int[] c:new int[][]{{0x80,0xFF,1},{0x80,0x80,2},{0x82,0x7F,0}}){
            var h=new Harness();h.main(6);h.edit(s->s.moveByte(SAVED_FLAGS_ID,1));
            for(int period=0;period<3;period++)mark(h.placement,1,period,0xE5);
            h.run(new CdSpringboard.Globals(c[0],c[1],0x1080));
            for(int period=0;period<3;period++)assertEquals(period==c[2]?0x65:0xE5,
                    h.placement.capture().savedFlags().unsignedByte(5+period));
        }
    }

    private static CdSpringboard.Assets assets(){return new CdSpringboard.Assets(CdAssetBytes.copyOf(TILES),CdAssetBytes.copyOf(BOXES),visual(),MAP);}
    private static CdSpringboard.Assets withTiles(byte[] b){return new CdSpringboard.Assets(CdAssetBytes.copyOf(b),CdAssetBytes.copyOf(BOXES),visual(),MAP);}
    private static CdSpringboard.Assets withBoxes(byte[] b){return new CdSpringboard.Assets(CdAssetBytes.copyOf(TILES),CdAssetBytes.copyOf(b),visual(),MAP);}
    private static CdObjectVisualAssets visual(){return CdObjectVisualAssets.decode(MAPS,6,SCRIPTS,5,21);}
    private static byte[] hex(String s){return HexFormat.of().parseHex(s);}
    private static void putWord(byte[] b,int at,int n){b[at]=(byte)(n>>>8);b[at+1]=(byte)n;}
    private static void putLong(byte[] b,int at,int n){putWord(b,at,n>>>16);putWord(b,at+2,n);}
    private static CdObjectState dirty(CdObjectState s){
        for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0xA5C37E19^f.byteOffset()*0x01010101);
        for(var f:CdObjectState.WordField.values())s=s.moveWord(f,0xD15B^f.byteOffset()*0x0101);
        for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,0x51+f.byteOffset()*7);
        return s;
    }
    private static CdObjectPlacements layout(){
        var b=ByteBuffer.allocate(34);b.putShort((short)12).putShort((short)28).putShort((short)-1).putInt(0).putShort((short)0);
        b.putShort((short)0x1000).putShort((short)0x200).put((byte)0x28).put((byte)0).put((byte)0xE0).put((byte)0);
        b.putShort((short)-1).putInt(0).putShort((short)0);b.putShort((short)-1).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }
    private static void mark(CdObjectPlacementRuntime p,int id,int period,int value){
        for(int bit=0;bit<8;bit++)if((value&(1<<bit))!=0)p.bsetSavedFlag(id,period,CdObjectPlacementRuntime.SavedBit.values()[bit]);
    }
    private static CdPlayerBody.State player(int x,int y,int yv,int routine,int flags,int xr,int yr){
        var m=new CdPlayerMotionState((x<<16)|0x2468,(y<<16)|0x55AA,0x123,yv,-0x456,routine,0x67,flags,0xA5,xr,yr,
                0x2A,0x29,0x7B,0x77,-0x1234,0x5A,0x81,0x34,0x56);
        return new CdPlayerBody.State(m,new CdPlayerBody.AnimationCursor(4,5,6),new CdPlayerBody.Header(1,0x204000,0x2345,2,16),
                new CdPlayerBody.Timers(0x1234,0x5678,0x9ABC,0xDEF0),new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0x46),
                0x89,new CdPlayerBody.ObjectLinks(0xFF,0xFE),0xAB);
    }
    private static CdPlayerBody.Environment environment(int debug){
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,12,0x80),
                new CdPlayerMovement.Bounds(0x12,0x3456,0x789,0xAB),new CdPlayerMovement.Gates(1,0,0,0,0,0,0),
                new CdPlayerMovement.Globals(1,0x234,3,4,0x5678,0x9ABC,5,6,7,8)),
                new CdPlayerBody.Controls(1,2,3,4,5),new CdPlayerBody.Globals(0x1234,0x1080,0,debug,0xABCD,0x2345,6,7),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }

    private static final class Harness implements CdSpringboard.DispatchObserver {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(layout());
        final List<CdSpringboard.Publication> publications=new ArrayList<>();
        final List<CdSpringboard.PrimaryCall> primaryCalls=new ArrayList<>();
        final List<CdObjectState> draws=new ArrayList<>();final List<String> events=new ArrayList<>();
        CdPlayerBody.State primary=player(0x1000,0x200,0,2,0,9,19);
        CdPlayerBody.Environment environment=environment(0);
        final CdSpringboard body;
        UnaryOperator<CdObjectState> onDraw=s->s.moveByte(SPRITE_FLAGS,s.spriteFlags()&0x7F);
        Consumer<CdSpringboard.PrimaryCall> afterPrimary=c->{};
        CdSpringboard.DrawCall lastDraw;
        boolean checkReceipts,reenter;int failAfterPublication;RuntimeException publicationFailure,drawFailure;
        Harness(){this(assets());}
        Harness(CdSpringboard.Assets a){
            write(slot().moveByte(ID,0x28).moveWord(X,0x1000).moveWord(Y,0x200));
            body=new CdSpringboard(placement,a,call->{
                assertSame(call.expectedState(),primary);assertSame(call.expectedEnvironment(),environment);
                assertTrue(bodyCurrent(call));primaryCalls.add(call);events.add("primary:"+call.field());
                if(checkReceipts){var clone=new CdSpringboard.PrimaryCall(call.ordinal(),call.cut(),call.source(),
                        call.expectedState(),call.expectedEnvironment(),call.outputState(),call.field(),call.expectedValue(),call.value());
                    assertEquals(call,clone);assertFalse(bodyCurrent(clone));}
                if(reenter)assertThrows(IllegalStateException.class,this::run);
                primary=call.outputState();afterPrimary.accept(call);
            },call->{
                lastDraw=call;assertTrue(bodyCurrent(call));draws.add(slot());events.add("draw");
                if(checkReceipts){var clone=new CdSpringboard.DrawCall(call.cut(),call.source());assertEquals(call,clone);assertFalse(bodyCurrent(clone));}
                if(reenter)assertThrows(IllegalStateException.class,this::run);
                write(onDraw.apply(slot()));if(drawFailure!=null)throw drawFailure;
            },this);
        }
        boolean bodyCurrent(Object call){return body.isCurrentCall(call);}
        CdObjectState slot(){return placement.pool().state(WHOLE);}
        void write(CdObjectState s){placement.pool().write(s);}
        void edit(UnaryOperator<CdObjectState> f){write(f.apply(slot()));}
        void main(int r){edit(s->s.moveByte(ROUTINE,r).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0));}
        CdSpringboard.Result run(){return run(new CdSpringboard.Globals(0,0,0x1080));}
        CdSpringboard.Result run(CdSpringboard.Globals g){return body.run(WHOLE,g,new CdSpringboard.PrimaryInputs(primary,environment));}
        public void beforePublication(CdSpringboard.Publication c){
            assertTrue(bodyCurrent(c));assertEquals(slot(),c.source());
            if(checkReceipts){var clone=new CdSpringboard.Publication(c.ordinal(),c.cut(),c.source(),c.output(),c.savedIndex());
                assertEquals(c,clone);assertFalse(bodyCurrent(clone));}
            if(reenter)assertThrows(IllegalStateException.class,this::run);
        }
        public void afterPublication(CdSpringboard.Publication c){
            assertTrue(bodyCurrent(c));publications.add(c);events.add("object");
            if(failAfterPublication==publications.size())throw publicationFailure;
        }
        public void beforeCall(Object c){assertTrue(bodyCurrent(c));}
        public void afterCall(Object c){assertTrue(bodyCurrent(c));}
    }
}
