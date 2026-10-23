package com.openggf.game.sonic3k.competition;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance.Kind;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance.NativeContext;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance.RockDebrisPiece;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Independent literal native cases, with the shipped ROM's animation scripts and placement lists. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionArenaObjects {
    private RomByteReader rom;
    private Map<String, SpriteAnimationSet> animations;
    private List<RockDebrisPiece> rockDebris;

    @BeforeEach
    void readNativeAssets() throws Exception {
        rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        animations = Map.of(
                "competition_balloon", S3kSpriteDataLoader.loadAnimationSet(rom, 0x35E74, 4),
                "competition_sand_bar", S3kSpriteDataLoader.loadAnimationSet(rom, 0x36046, 4),
                "competition_bumper", S3kSpriteDataLoader.loadAnimationSet(rom, 0x33094, 2),
                "competition_spring", S3kSpriteDataLoader.loadAnimationSet(rom, 0x23722, 6),
                "competition_retracting_spring", S3kSpriteDataLoader.loadAnimationSet(rom, 0x23722, 6));
        rockDebris = new ArrayList<>();
        assertEquals(5, rom.readU16BE(0x202A6));
        for (int i = 0; i < 6; i++) rockDebris.add(new RockDebrisPiece(
                (byte) rom.readU8(0x202A8 + i * 2), (byte) rom.readU8(0x202A9 + i * 2),
                rom.readS16BE(0x20358 + i * 4), rom.readS16BE(0x2035A + i * 4)));
    }

    @Test
    void lockedOnRomContainsEveryArenaAndTheActualSpecializedPlacements() throws Exception {
        CRC32 crc = new CRC32();
        crc.update(RomManager.getInstance().getRom().readBytes(0, rom.size()));
        assertEquals(0x63522553L, crc.getValue());
        assertArrayEquals(new int[]{0x65, 2, 0x5F, 2, 0x64, 0x64, 0x4F, 0x33}, ids(0x1FC560, 8));
        assertArrayEquals(new int[]{0x61, 0x65, 2, 0x4A, 2, 0x60, 2, 0x64, 0x64, 0x4A, 0x4A, 0x60, 0x4A, 0x61, 2}, ids(0x1FC5A2, 15));
        assertEquals(0x5F, rom.readU8(0x1FC60E + 7 * 6 + 4));
        assertEquals(0x62, rom.readU8(0x1FC60E + 12 * 6 + 4));
        assertEquals(0x63, rom.readU8(0x1FC60E + 15 * 6 + 4));
        assertEquals(0x5E, rom.readU8(0x1FC686 + 5 * 6 + 4));
        assertEquals(0x38, rom.readU8(0x1FC686 + 6 * 6 + 4));
        assertEquals(0x5D, rom.readU8(0x1FC686 + 13 * 6 + 4));
        assertEquals(0x05, rom.readU8(0x1FC6F2 + 5 * 6 + 4));
        assertEquals(0x66, rom.readU8(0x1FC6F2 + 8 * 6 + 4));
        // sub_32D20 immediate x=-$800,y=$400, and loc_35B16 one/two rider increments.
        assertEquals(0x33_7C_F8_00L, Integer.toUnsignedLong(rom.readU32BE(0x32D20)));
        assertEquals(0x400, rom.readU16BE(0x32D28));
        assertEquals(0x80, rom.readU16BE(0x35B22));
        assertEquals(0x100, rom.readU16BE(0x35B2C));
    }

    @Test
    void triangleUsesTheCorrectSideBothNativeSlotsAndTheSubtypeLayerFlip() {
        Fixture f = fixture();
        var triangle = f.place(Kind.TRIANGLE, 0x100, 0x100, 5, 0);
        var p1 = f.players.get(0); var p2 = f.players.get(1);
        p1.setCentre(0x10F, 0x100); p1.setAirborne(true);
        p2.setCentre(0xF1, 0x100); p2.setAirborne(true);
        f.tick();
        assertEquals(0x800, p1.xVelocity());
        assertEquals(0x400, p1.yVelocity());
        assertEquals(15, p1.moveLock());
        assertEquals(1, p1.groundVelocity());
        assertEquals(3, p1.flipsRemaining());
        assertEquals(0xC, p1.topSolidBit());
        assertNotEquals(0x800, p2.xVelocity(), "back face is solid but does not launch");
        var reflected = f.place(Kind.TRIANGLE, 0x200, 0x100, 0x89, 1);
        p2.setCentre(0x1F1, 0x100); p2.setAirborne(true);
        reflected.tickNative(f);
        assertEquals(-0x800, p2.xVelocity());
        assertEquals(0, p2.yVelocity(), "subtype bit7 suppresses Y velocity");
        assertEquals(-1, p2.groundVelocity());
        assertEquals(0xE, p2.topSolidBit());
        assertTrue(p2.facingLeft());
        assertEquals(0, triangle.nativeStandingMask());
    }

    @Test
    void bladeUsesBothStandingBitsAndPreservesTheNativeLowByteYAddition() {
        Fixture f = fixture();
        var blade = f.place(Kind.BLADE, 0x100, 0xFF, 0, 0);
        f.players.get(0).setCentre(0x100, 0xEB);
        f.players.get(1).setCentre(0x100, 0xEB);
        f.tick();
        assertEquals(0, blade.nativeStandingMask(), "new SolidObjectFull objects have no carried render bit");
        f.tick();
        assertEquals(3, blade.nativeStandingMask());
        assertEquals(2, f.objects.size() - 1);
        f.tick();
        assertEquals(0x100, blade.extensionWord());
        assertEquals(0, blade.getY(), "ADD.B wraps FF to00 without carrying into the Y high byte");
        f.players.get(1).setAirborne(true);
        f.players.get(1).setYVelocity(-0x100);
        f.tick();
        assertEquals(0x180, blade.extensionWord(), "only one remaining rider contributes $80");
        f.players.get(0).setAirborne(true);
        f.players.get(0).setYVelocity(-0x100);
        f.tick();
        assertEquals(0x80, blade.extensionWord());
    }

    @Test
    void fullObjectRamStopsBladeChildrenWithoutInventingHazards() {
        Fixture f = fixture(); f.capacity = 1;
        var blade = f.place(Kind.BLADE, 0x100, 0x100, 0, 0);
        f.tick();
        assertEquals(1, f.objects.size());
        assertEquals(1, f.allocationFailures);
        assertEquals(5, blade.getPriorityBucket());
        Fixture oneChild = fixture(); oneChild.capacity = 2;
        oneChild.place(Kind.BLADE, 0x100, 0x100, 0, 0); oneChild.tick();
        assertEquals(Kind.BLADE_LEFT, oneChild.objects.get(1).kind());
        assertEquals(2, oneChild.objects.size());
        assertEquals(1, oneChild.allocationFailures);
        var p2 = oneChild.players.get(1); p2.setCentre(0xF0, 0x108);
        oneChild.touch();
        assertEquals(List.of(1), oneChild.hurtSlots);
    }

    @Test
    void elephantStartsFromTheReflectedEndpointAndMovesInHalfPixelSteps() {
        Fixture f = fixture();
        var extending = f.place(Kind.ELEPHANT, 0x559, 0x320, 0x50, 0);
        extending.tickNative(f);
        assertEquals(0x80, extending.extensionWord());
        assertEquals(0x559, extending.getX());
        for (int i = 1; i < 160; i++) extending.tickNative(f);
        assertEquals(0x5000, extending.extensionWord());
        assertEquals(0x531, extending.getX());
        assertEquals(0x509, extending.nativeRenderX(), "sub2 is drawn at the tip, not the solid centre");
        assertEquals(0x320, extending.nativeRenderY());
        assertFalse(extending.nativeRenderXFlip()); assertFalse(extending.nativeRenderYFlip());
        var reflected = f.place(Kind.ELEPHANT, 0x4E7, 0x320, 0x50, 1);
        reflected.tickNative(f);
        assertEquals(0x4F80, reflected.extensionWord());
        assertEquals(0x50E, reflected.getX());
        assertEquals(0x536, reflected.nativeRenderX());
        assertTrue(reflected.nativeRenderXFlip()); assertFalse(reflected.nativeRenderYFlip());
        assertEquals(1, reflected.mappingFrame());
    }

    @Test
    void elephantRenderDescriptorRestoresAndForwardsIndependentlyOfItsVisibilityCentre() {
        Fixture f = fixture();
        f.place(Kind.ELEPHANT, 0x4E7, 0x320, 0x50, 1);
        for (int tick=0;tick<21;tick++) f.tick();
        var before=f.capture();
        for (int tick=0;tick<17;tick++) f.tick();
        var forward=f.capture();
        int renderX=f.objects.getFirst().nativeRenderX();
        int solidX=f.objects.getFirst().getX();
        assertNotEquals(renderX,solidX);
        f.restore(before);
        for (int tick=0;tick<17;tick++) f.tick();
        assertEquals(renderX,f.objects.getFirst().nativeRenderX());
        assertEquals(solidX,f.objects.getFirst().getX());
        assertTrue(f.objects.getFirst().nativeRenderXFlip());
        assertEquals(forward.objectStates,f.capture().objectStates);
    }

    @Test
    void balloonUsesRomAnimationGatingAndDistinctTopVersusSideLaunchesForBothPlayers() {
        Fixture f = fixture();
        var balloon = f.place(Kind.BALLOON, 0x100, 0x100, 0, 0);
        f.players.get(0).setCentre(0x100, 0xF4);
        f.players.get(0).setXVelocity(0x123);
        f.players.get(1).setCentre(0x10C, 0x100);
        f.touch(); f.tick();
        assertEquals(0, f.players.get(0).yVelocity(), "init is absent from the previous collision list");
        for (int i = 0; i < 60 && balloon.mappingFrame() != 6; i++) f.tick();
        assertEquals(6, balloon.mappingFrame());
        f.touch(); f.tick();
        assertEquals(-0x900, f.players.get(0).yVelocity());
        assertEquals(0x123, f.players.get(0).xVelocity(), "upward sector preserves x_vel");
        assertEquals(0x480, f.players.get(1).xVelocity());
        assertEquals(0, f.players.get(1).yVelocity());
        assertTrue(f.players.get(0).airborne());
        assertTrue(f.players.get(1).airborne());
    }

    @Test
    void sandBarWaitsSixteenTicksAndReleasesBothPlayersWhenItsNativeSurfaceDisappears() {
        Fixture f = fixture();
        var sand = f.place(Kind.SAND_BAR, 0x100, 0x200, 1, 0);
        f.players.forEach(p -> p.setCentre(0x100, 0x1E3));
        f.tick();
        assertEquals(3, sand.nativeStandingMask());
        for (int i = 0; i < 15; i++) f.tick();
        assertTrue(sand.mappingFrame() < 4);
        f.tick();
        assertEquals(4, sand.mappingFrame());
        for (int i = 0; i < 60 && sand.mappingFrame() < 20; i++) f.tick();
        assertEquals(20, sand.mappingFrame());
        assertEquals(0, sand.nativeStandingMask());
        for (var p : f.players) {
            assertTrue(p.airborne()); assertEquals(0, p.status() & 8);
            assertEquals(sand.nativeIdentity(), p.standingObject(), "native clears OnObj, not its interact word");
        }
    }

    @Test
    void dpzButtonOwnsItsTriggerBitAndNativeFourTickPaletteBlink() {
        Fixture f = fixture();
        f.palette[2][14] = 0x2E;
        var button = f.place(Kind.BUTTON, 0x100, 0x200, 3, 0);
        f.players.get(1).setCentre(0x100, 0x1F2);
        f.tick();
        assertEquals(1, f.trigger(3));
        assertEquals(1, button.mappingFrame());
        assertEquals(0xC2E, f.paletteColor(2, 14));
        assertEquals(0xA0E, f.paletteColor(2, 15));
        for (int i = 0; i < 4; i++) f.tick();
        assertEquals(0x2E, f.paletteColor(2, 14));
        f.players.get(1).setCentre(0x200, 0x100); f.players.get(1).setAirborne(true);
        f.tick();
        assertEquals(0, f.trigger(3));
        assertEquals(0xA, f.paletteColor(2, 15));
    }

    @Test
    void floorButtonCanLatchTheRetractingSpringTrigger() {
        Fixture f = fixture();
        var button = f.place(Kind.FLOOR_BUTTON, 0x100, 0x200, 0x10, 0);
        f.tick();
        f.players.get(0).setCentre(0x100, 0x1F4);
        f.tick();
        assertEquals(1, f.trigger(0));
        f.players.get(0).setCentre(0x300, 0x100); f.players.get(0).setAirborne(true); f.tick();
        assertEquals(1, f.trigger(0));
        assertEquals(0, button.mappingFrame());
    }

    @Test
    void triggeredRetractingSpringUsesTheAllocatedBaseAndTheBorrowEdge() {
        Fixture f = fixture();
        var spring = f.place(Kind.RETRACTING_SPRING, 0x51D, 0x1D4, 0xB0, 1);
        f.tick(); f.tick(); f.tick();
        assertEquals(0x1800, spring.extensionWord());
        assertEquals(0x1EC, spring.getY());
        assertEquals(Kind.SPRING_BASE, f.objects.get(1).kind());
        f.setTrigger(0, 1); f.tick(); f.setTrigger(0, 0);
        f.tick(); f.tick(); f.tick();
        assertEquals(0, spring.extensionWord());
        assertEquals(0, spring.delayTicks(), "SUBI reaching zero has no borrow; hold is entered one pass later");
        f.tick();
        assertEquals(180, spring.delayTicks());
    }

    @Test
    void e0RetractingSpringSwitchesItsNativeDirectionAtExtension() {
        Fixture f = fixture();
        var spring = f.place(Kind.RETRACTING_SPRING, 0x6F4, 0xC8, 0xE0, 0);
        f.tick(); f.tick(); f.tick();
        assertEquals(0xF1, spring.nativeSubtype());
        assertEquals(0x70C, spring.getX());
        assertEquals(0xDE, spring.getY());
    }

    @Test
    void competitionSpringsUseMiniatureSolidWidthAndTheNativeStrengthAndFlipWrites() {
        Fixture f = fixture();
        var horizontal = f.place(Kind.SPRING, 0x100, 0x100, 0x10, 0);
        horizontal.tickNative(f);
        var p1 = f.players.get(0); p1.setCentre(0x10E, 0x100); p1.setXVelocity(-0x200); p1.setYVelocity(0x123);
        horizontal.tickNative(f);
        assertEquals(0x1000, p1.xVelocity());
        assertEquals(0x123, p1.yVelocity());
        assertFalse(p1.airborne());
        assertEquals(15, p1.moveLock());
        var vertical = f.place(Kind.SPRING, 0x200, 0x200, 3, 0);
        vertical.tickNative(f);
        var p2 = f.players.get(1); p2.setCentre(0x200, 0x1EC); p2.setAirborne(true); p2.setYVelocity(0x100);
        vertical.tickNative(f);
        assertEquals(-0xA00, p2.yVelocity());
        assertTrue(p2.airborne());
        assertEquals(0x1F4, p2.centreY());
        assertEquals(1, p2.flipAngle());
        assertEquals(0, p2.flipsRemaining());
        assertEquals(4, p2.flipSpeed());
    }

    @Test
    void competitionFanLiftsBothSlotsAndRejectsItsExclusiveRightEdge() {
        Fixture f = fixture();
        var fan = f.place(Kind.FAN, 0x100, 0x100, 0, 0);
        var p1 = f.players.get(0); var p2 = f.players.get(1);
        p1.setCentre(0xEC, 0xC0); p2.setCentre(0x113, 0xC0);
        f.tick();
        assertEquals(0xBF, p1.centreY()); assertEquals(0xBF, p2.centreY());
        assertTrue(p1.airborne()); assertTrue(p2.airborne());
        assertEquals(0x7F, p2.flipsRemaining());
        p2.setCentre(0x114, 0xC0); fan.tickNative(f);
        assertEquals(0xC0, p2.centreY());
    }

    @Test
    void bumperUsesTheHighFrameCounterByteForItsNativeAnglePerturbation() {
        Fixture f = fixture();
        f.place(Kind.BUMPER, 0x100, 0x100, 0x80, 0);
        f.players.get(1).setCentre(0x10C, 0x100);
        f.tick(); f.frame = 0x100; f.touch(); f.tick();
        assertEquals(0x6F9, f.players.get(1).xVelocity());
        assertEquals(42, f.players.get(1).yVelocity());
        assertEquals(0x100, f.objects.getFirst().getX(), "competition bumper never takes the campaign orbit branch");
    }

    @Test
    void mudKeepsSeparateNativeDepthAndKillsOnlyTheSunkPlayerOnTheFollowingPass() {
        Fixture f = fixture();
        var mud = f.place(Kind.MUD, 0x100, 0x200, 4, 0);
        f.players.get(0).setCentre(0x100, 0x1DC);
        f.tick();
        for (int i = 0; i < 48; i++) f.tick();
        assertEquals(0, mud.mudDepth(0));
        assertEquals(0x30, mud.mudDepth(1));
        assertTrue(f.players.get(0).routine() < 6);
        f.tick();
        assertEquals(6, f.players.get(0).routine());
        assertEquals(0x30, mud.mudDepth(0));
        assertTrue(f.players.get(1).routine() < 6);
    }

    @Test
    void pathSwapTracksNativeSlotsSeparatelyAndRestoresAcrossCrossings() {
        Fixture f = fixture();
        var swap = f.place(Kind.PATH_SWAP, 0x100, 0x100, 0x29, 0);
        var p1 = f.players.get(0); var p2 = f.players.get(1);
        p1.setCentre(0xFF, 0x100); p2.setCentre(0x101, 0x100); f.tick();
        var before = f.capture();
        p1.setCentre(0x101, 0x100); p2.setCentre(0xFF, 0x100); f.tick();
        assertEquals(0xE, p1.topSolidBit()); assertEquals(0xC, p2.topSolidBit());
        assertTrue(p1.highPriority()); assertFalse(p2.highPriority());
        var after = f.capture();
        f.restore(before);
        p1.setCentre(0x101, 0x100); p2.setCentre(0xFF, 0x100); f.tick();
        assertEquals(after.playerStates, f.capture().playerStates);
        assertEquals(after.objectStates, f.capture().objectStates);
        assertEquals(5, swap.getPriorityBucket());
    }

    @Test
    void dripperSpawnsOnNativePhaseAndAllocationFailureDoesNotConsumeRng() {
        Fixture f = fixture();
        f.place(Kind.DRIPPER, 0x647, 0x1A4, 1, 0);
        f.tick(); assertEquals(1, f.objects.size());
        f.frame = 0x7F; f.tick(); assertEquals(2, f.objects.size());
        var drop = f.objects.get(1); f.floorDistance = -1; f.capacity = 2;
        f.tick();
        assertEquals(Kind.SPLASH, drop.kind());
        assertEquals(0, f.randomCalls);
        assertEquals(-4, drop.nativeYVelocity());
        assertEquals(0, drop.lifetimeByte());
        f.tick(); assertEquals(255, drop.lifetimeByte());
    }

    @Test
    void dripperCopiesSplashVelocityBeforeReducingParentAndRestoresItsEntireChildGraph() {
        Fixture f = fixture(); f.floorDistance = -1;
        f.place(Kind.DRIPPER, 0x647, 0x1A4, 0, 0);
        f.randomWords = new int[]{0x1234, 0x5678, 0x9ABC};
        f.tick();
        assertEquals(5, f.objects.size());
        assertEquals(3, f.randomCalls);
        assertEquals(-0x32, f.objects.get(1).nativeYVelocity());
        // Children run later in the same SST pass: MoveSprite2 precedes y_vel+=8.
        assertEquals(6, f.objects.get(2).nativeYVelocity());
        assertEquals(-10, f.objects.get(3).nativeYVelocity());
        assertEquals(-26, f.objects.get(4).nativeYVelocity());
        assertEquals(-0xCC, f.objects.get(2).nativeXVelocity());
        assertEquals(26, f.objects.get(2).lifetimeByte());
        var before = f.capture();
        for (int i = 0; i < 8; i++) f.tick();
        var after = f.capture();
        f.restore(before);
        for (int i = 0; i < 8; i++) f.tick();
        assertEquals(after.playerStates, f.capture().playerStates);
        assertEquals(after.objectStates, f.capture().objectStates);
    }

    @Test
    void bladeRecreationRelinksBothCollisionChildrenToTheFreshParent() {
        Fixture f = fixture();
        var original = f.place(Kind.BLADE, 0x100, 0x100, 0, 0);
        f.players.forEach(p -> p.setCentre(0x100, 0xEC));
        f.tick(); f.tick(); f.tick();
        var state = f.capture();
        for (int i = 0; i < 8; i++) f.tick();
        f.restore(state);
        assertNotSame(original, f.objects.getFirst());
        assertNotSame(state.objects.get(1), f.objects.get(1));
        assertEquals(Kind.BLADE, f.objects.get(0).kind());
        assertEquals(Kind.BLADE_LEFT, f.objects.get(1).kind());
        assertEquals(Kind.BLADE_RIGHT, f.objects.get(2).kind());
        assertNotNull(state.objectStates.get(1).compactGenericState(), "Native child links use compact identities");
        f.tick();
        assertFalse(f.objects.get(1).isDestroyed());
        assertFalse(f.objects.get(2).isDestroyed());
        assertEquals(0x102, f.objects.get(0).getY());
        assertEquals(0x10A, f.objects.get(1).getY());
        assertEquals(0x112, f.objects.get(2).getY());
    }

    @Test
    void invisibleEmzPlatformUsesActualSubtype12GeometryAndNeverDraws() {
        Fixture f = fixture();
        var platform = f.place(Kind.INVISIBLE_PLATFORM, 0x570, 0x1D0, 0x12, 0);
        var p1 = f.players.get(0); var p2 = f.players.get(1);
        p1.setCentre(0x570, 0x1AC); p1.setAirborne(true); p1.setYVelocity(0x100);
        p2.setCentre(0x58A, 0x1D0); p2.setXVelocity(-0x200);
        f.tick();
        assertEquals(0x1AC, p1.centreY());
        assertFalse(p1.airborne());
        assertEquals(platform.nativeIdentity(), p1.standingObject());
        assertEquals(0x58B, p2.centreX(), "full2 d1 retains width+$B, not the miniature width+7");
        assertTrue(p2.pushing()); assertEquals(0, p2.xVelocity());
        assertEquals(0x10, platform.getOnScreenHalfWidth());
        assertEquals(0x18, platform.getOnScreenHalfHeight());
        assertNull(platform.artKey()); assertFalse(platform.nativeDrawPublished());
        assertFalse(platform.nativeVisible());
    }

    @Test
    void emzRockUsesSavedPreSolidRollingAnimationAndEjectsTheOtherNativePlayer() {
        Fixture f = fixture();
        var rock = f.place(Kind.ROCK, 0x200, 0x200, 0x31, 0); f.tick();
        var p1 = f.players.get(0); var p2 = f.players.get(1);
        p1.setCentre(0x200, 0x1E5);
        p2.setCentre(0x200, 0x1E9); p2.setAirborne(true); p2.setRolling(true);
        p2.setRadii(3, 7); p2.setAnimation(2); p2.setYVelocity(0x200);
        p2.setFractions(0x1234, 0x5678);
        f.tick();
        assertEquals(Kind.ROCK_DEBRIS, rock.kind());
        assertEquals(7, f.objects.size(), "one shard plus six debris pieces, including the parent");
        assertEquals(Kind.ROCK_SHARD, f.objects.get(1).kind());
        assertEquals(7, f.objects.get(1).mappingFrame());
        assertTrue(p1.airborne()); assertEquals(0, p1.status() & 8);
        assertEquals(rock.nativeIdentity(), p1.standingObject());
        assertEquals(0, p1.yVelocity());
        assertTrue(p2.airborne()); assertEquals(-0x300, p2.yVelocity());
        assertEquals(7, p2.xRadius()); assertEquals(0xE, p2.yRadius());
        assertEquals(0x1E5, p2.centreY(), "native radius write adds no centre adjustment");
        assertEquals(0x1234, p2.xFraction()); assertEquals(0x5678, p2.yFraction());
        assertEquals(0x1F7, rock.getX()); assertEquals(0x1F6, rock.getY());
        assertEquals(-0xB0, rock.nativeXVelocity()); assertEquals(-0x1C8, rock.nativeYVelocity());
        assertEquals(1, rock.getPriorityBucket()); assertTrue(rock.isHighPriority());
        assertEquals(0xF, rock.getOnScreenHalfHeight());
        assertEquals(0, f.objects.get(2).getOnScreenHalfHeight(), "FixBugs0 leaves fresh debris height zero");
    }

    @Test
    void emzRockDoesNotBreakForNonRollingStandingContacts() {
        Fixture f = fixture();
        var rock = f.place(Kind.ROCK, 0x200, 0x200, 0x31, 0); f.tick();
        f.players.forEach(p -> p.setCentre(0x200, 0x1E5)); f.tick();
        assertEquals(Kind.ROCK, rock.kind()); assertEquals(3, rock.nativeStandingMask());
        assertEquals(1, f.objects.size()); assertEquals(4, rock.getPriorityBucket());
    }

    @Test
    void emzRockFullRamKeepsOnlyTheParentDebrisAndNoInventedRespawnOwner() {
        Fixture f = fixture(); f.capacity = 1;
        var rock = f.place(Kind.ROCK, 0x200, 0x200, 0x31, 0); f.tick();
        var p2 = f.players.get(1);
        p2.setCentre(0x200, 0x1E5); p2.setRolling(true); p2.setAnimation(2);
        f.tick();
        assertEquals(Kind.ROCK_DEBRIS, rock.kind()); assertEquals(1, f.objects.size());
        assertEquals(2, f.allocationFailures, "shard allocation fails, then first extra debris allocation fails");
        assertEquals(0, f.randomCalls);
        rock.refreshNativeVisibility(false); f.tick();
        assertTrue(rock.isDestroyed());
        assertTrue(f.objects.stream().noneMatch(o -> o.kind() == Kind.ROCK_SHARD));
    }

    @Test
    void allocatedRockShardReformsOnlyAfterItsCarriedRenderBitClears() {
        Fixture f = fixture(); f.capacity = 2;
        var rock = f.place(Kind.ROCK, 0x200, 0x200, 0x31, 0); f.tick();
        var p1 = f.players.get(0);
        p1.setCentre(0x200, 0x1E5); p1.setRolling(true); p1.setAnimation(2);
        f.tick();
        var shard = f.objects.get(1);
        assertEquals(Kind.ROCK_SHARD, shard.kind()); f.tick();
        assertEquals(Kind.ROCK_SHARD, shard.kind());
        shard.refreshNativeVisibility(false); f.tick();
        assertEquals(Kind.ROCK, shard.kind()); assertEquals(0, shard.mappingFrame());
        assertEquals(0x200, shard.getX()); assertEquals(0x200, shard.getY());
        assertEquals(0xE, shard.getOnScreenHalfWidth()); assertEquals(0xF, shard.getOnScreenHalfHeight());
        assertEquals(1, f.allocationFailures);
        assertEquals(Kind.ROCK_DEBRIS, rock.kind());
    }

    @Test
    void rockBreakRestoreRecreatesTheSstGraphAndForwardReplaysBothPlayers() {
        Fixture f = fixture();
        f.place(Kind.ROCK, 0x200, 0x200, 0x31, 0); f.tick();
        var p1 = f.players.get(0); var p2 = f.players.get(1);
        p1.setCentre(0x200, 0x1E5); p2.setCentre(0x200, 0x1E5);
        p2.setRolling(true); p2.setAnimation(2);
        var before = f.capture(); f.tick(); f.tick(); var after = f.capture();
        f.restore(before); f.tick(); f.tick(); var replay = f.capture();
        assertEquals(after.playerStates, replay.playerStates);
        assertEquals(after.objectStates, replay.objectStates);
        assertEquals(7, f.objects.size());
        assertNotSame(before.objects.getFirst(), f.objects.getFirst());
    }

    @Test
    void staleInteractWordAfterFalseStartAndTerrainLandingCannotRestoreAnOldRide() {
        Fixture f = fixture();
        var blade = f.place(Kind.BLADE, 0x100, 0x100, 0, 0); f.tick();
        var p1 = f.players.getFirst(); p1.setCentre(0x100, 0xEC); f.tick();
        assertEquals(blade.nativeIdentity(), p1.standingObject());
        p1.setStatus(2); // Native false-start reset leaves interact untouched.
        p1.setCentreNative(0x100, 0x300);
        p1.setStatus(0); // Subsequent terrain landing clears InAir, without acquiring an object.
        assertEquals(blade.nativeIdentity(), p1.standingObject());
        f.tick();
        assertEquals(0x300, p1.centreY()); assertEquals(0, p1.status() & 8);
        assertEquals(0, blade.nativeStandingMask());
    }

    @Test
    void automaticRetractingSpringSoundUsesCapturedPreviousVisibilityWhenItsHoldExpires() {
        for (boolean previousVisible : List.of(false,true)) {
            Fixture f=fixture(); f.visible=previousVisible;
            var spring=f.place(Kind.RETRACTING_SPRING,0x100,0x100,0,0);
            // Three extension steps, then 59 hold steps: the next native pass expires $3A.
            for (int tick=0;tick<62;tick++) f.tick();
            assertEquals(1,spring.delayTicks());
            assertEquals(previousVisible,spring.nativeVisible());
            assertTrue(f.sounds.isEmpty());
            var before=f.capture();
            f.visible=!previousVisible; // This pass's later BuildSprites result must not decide its sound.
            f.tick();
            List<Integer> expected=previousVisible?List.of(Sonic3kSfx.SPIKE_MOVE.id):List.of();
            assertEquals(expected,f.sounds);
            assertEquals(!previousVisible,spring.nativeVisible());
            var forward=f.capture();
            f.restore(before); f.sounds.clear();
            assertEquals(previousVisible,f.objects.getFirst().nativeVisible());
            f.tick();
            assertEquals(expected,f.sounds);
            assertEquals(forward.objectStates,f.capture().objectStates);
            assertEquals(forward.playerStates,f.capture().playerStates);
        }
    }

    @Test
    void canonicalCollisionListRetainsOnlyPublishedArenaEntriesInSstOrderAcrossRecreation() {
        Fixture f=fixture();
        var triangle=f.place(Kind.TRIANGLE,0x100,0x100,0,0);
        var bumper=f.place(Kind.BUMPER,0x200,0x100,0,0);
        var blade=f.place(Kind.BLADE,0x300,0x100,0,0);
        var balloon=f.place(Kind.BALLOON,0x400,0x100,0,0);
        f.tick();
        var left=f.objects.get(4); var right=f.objects.get(5);
        assertEquals(0,triangle.getCollisionFlags()); assertEquals(0,blade.getCollisionFlags());
        assertEquals(0xD7,bumper.getCollisionFlags()); assertEquals(0xD7,balloon.getCollisionFlags());
        assertEquals(0xA6,left.getCollisionFlags()); assertEquals(0xA6,right.getCollisionFlags());
        assertFalse(bumper.requiresRenderFlagForTouch());
        var manager=new StubObjectServices().withIsolatedObjectManager().objectManager();
        // Reverse insertion proves the canonical list follows the SST, not registration order.
        for (int i=f.objects.size()-1;i>=0;i--) manager.addDynamicObjectAtSlot(f.objects.get(i),32+i);
        var bumperId=manager.objectQuery().identityOf(bumper).orElseThrow();
        var balloonId=manager.objectQuery().identityOf(balloon).orElseThrow();
        var leftId=manager.objectQuery().identityOf(left).orElseThrow();
        var rightId=manager.objectQuery().identityOf(right).orElseThrow();
        var adapter=manager.rewindSnapshottable();
        manager.publishRepresentedInitialCollisionResponseList();
        assertEquals(List.of(bumperId,leftId,rightId),
                adapter.capture().collisionResponseState().previousCollisionObjectIds());
        for (int tick=0;tick<60 && !balloon.publishesTouchResponseListEntryThisFrame();tick++) f.tick();
        assertTrue(balloon.publishesTouchResponseListEntryThisFrame());
        for (var player:f.players) {
            player.setCentre(bumper.getX(),bumper.getY());
            assertTrue(bumper.touchNativePlayer(f,player));
        }
        assertEquals(3,bumper.getCollisionProperty());
        manager.publishRepresentedInitialCollisionResponseList();
        var before=adapter.capture(); var playerBefore=f.players.stream().map(S3kCompetitionPlayer::capture).toList();
        int frameBefore=f.frame;
        assertEquals(List.of(bumperId,balloonId,leftId,rightId),
                before.collisionResponseState().previousCollisionObjectIds());
        f.tick(); manager.publishRepresentedInitialCollisionResponseList();
        var forward=adapter.capture(); var playerForward=f.players.stream().map(S3kCompetitionPlayer::capture).toList();
        assertEquals(0,bumper.getCollisionProperty());
        adapter.restore(before);
        f.objects.clear();
        for (var object:manager.getActiveObjects()) f.objects.add((CompetitionArenaObjectInstance)object);
        for (int i=0;i<f.players.size();i++) f.players.get(i).restore(playerBefore.get(i));
        f.frame=frameBefore;
        var restored=(CompetitionArenaObjectInstance)manager.objectQuery().resolve(bumperId).orElseThrow();
        assertNotSame(bumper,restored); assertEquals(3,restored.getCollisionProperty());
        assertEquals(before.collisionResponseState(),adapter.capture().collisionResponseState());
        f.tick(); manager.publishRepresentedInitialCollisionResponseList();
        assertEquals(forward.dynamicObjects(),adapter.capture().dynamicObjects());
        assertEquals(forward.collisionResponseState(),adapter.capture().collisionResponseState());
        assertEquals(playerForward,f.players.stream().map(S3kCompetitionPlayer::capture).toList());
    }

    private int[] ids(int address, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = rom.readU8(address + i * 6 + 4);
        return result;
    }

    private Fixture fixture() { return new Fixture(animations, rockDebris); }

    private record Snapshot(List<CompetitionArenaObjectInstance> objects, List<ObjectSpawn> spawns,
            List<PerObjectRewindSnapshot> objectStates, List<S3kCompetitionPlayer.State> playerStates,
            int frame, int[] triggers, int[][] palette) { }

    private static final class Fixture implements NativeContext {
        final List<S3kCompetitionPlayer> players = List.of(
                new S3kCompetitionPlayer(0, S3kCompetitionPlayer.Character.SONIC, 0x800, 0x800),
                new S3kCompetitionPlayer(1, S3kCompetitionPlayer.Character.TAILS, 0x900, 0x800));
        final List<CompetitionArenaObjectInstance> objects = new ArrayList<>();
        final Map<String, SpriteAnimationSet> animationSets;
        final List<RockDebrisPiece> rockRows;
        final List<Integer> hurtSlots = new ArrayList<>();
        final List<Integer> sounds = new ArrayList<>();
        final int[] triggers = new int[16];
        final int[][] palette = new int[4][16];
        int frame;
        boolean visible = true;
        int capacity = 128;
        int allocationFailures;
        int floorDistance;
        int randomCalls;
        int[] randomWords = {0x1234, 0x5678, 0x9ABC};

        Fixture(Map<String, SpriteAnimationSet> animationSets, List<RockDebrisPiece> rockRows) {
            this.animationSets = animationSets;
            this.rockRows = rockRows;
        }

        CompetitionArenaObjectInstance place(Kind kind, int x, int y, int subtype, int flags) {
            var object = new CompetitionArenaObjectInstance(new ObjectSpawn(x, y, 0, subtype, flags,
                    false, y, objects.size()), kind);
            object.setSlotIndex(objects.size() + 2);
            objects.add(object);
            return object;
        }

        void tick() {
            for (int i = 0; i < objects.size(); i++) objects.get(i).tickNative(this);
            for (var object : objects) if (object.nativeDrawPublished()) object.refreshNativeVisibility(visible);
            frame++;
        }

        void touch() {
            for (var player : players) for (var object : objects)
                if (object.touchNativePlayer(this, player)) break;
        }

        Snapshot capture() {
            RewindCaptureContext context = captureContext();
            return new Snapshot(List.copyOf(objects), objects.stream().map(AbstractObjectInstance::getSpawn).toList(),
                    objects.stream().map(object -> object.captureRewindState(context)).toList(),
                    players.stream().map(S3kCompetitionPlayer::capture).toList(), frame,
                    triggers.clone(), Arrays.stream(palette).map(int[]::clone).toArray(int[][]::new));
        }

        void restore(Snapshot state) {
            objects.clear();
            for (int i = 0; i < state.objects.size(); i++) {
                var object = (CompetitionArenaObjectInstance) ObjectRewindDynamicCodecs.genericRecreate(
                        new DynamicObjectEntry(CompetitionArenaObjectInstance.class.getName(),
                                state.spawns.get(i), state.objectStates.get(i).slotIndex(), state.objectStates.get(i)),
                        new DynamicObjectRecreateContext(mock(ObjectManager.class)));
                assertNotNull(object, "Production genericRecreate must construct every native SST routine");
                object.setSlotIndex(state.objectStates.get(i).slotIndex());
                objects.add(object);
            }
            RewindCaptureContext context = captureContext();
            for (int i = 0; i < objects.size(); i++) objects.get(i).restoreRewindState(state.objectStates.get(i), context);
            for (int i = 0; i < players.size(); i++) players.get(i).restore(state.playerStates.get(i));
            frame = state.frame;
            System.arraycopy(state.triggers, 0, triggers, 0, 16);
            for (int i = 0; i < 4; i++) System.arraycopy(state.palette[i], 0, palette[i], 0, 16);
        }

        private RewindCaptureContext captureContext() {
            var identities = new RewindIdentityTable();
            for (int i = 0; i < objects.size(); i++) identities.registerObject(objects.get(i),
                    ObjectRefId.dynamic(objects.get(i).getSlotIndex(), 0, i));
            return RewindCaptureContext.withIdentityTable(identities);
        }

        @Override public List<S3kCompetitionPlayer> players() { return players; }
        @Override public int zoneId() { return 0xE; }
        @Override public int levelFrameCounter() { return frame; }
        @Override public int trigger(int index) { return triggers[index & 15]; }
        @Override public void setTrigger(int index, int value) { triggers[index & 15] = value & 0xFF; }
        @Override public AbstractObjectInstance allocateAfter(AbstractObjectInstance parent,
                Supplier<? extends AbstractObjectInstance> factory) {
            if (objects.size() >= capacity) { allocationFailures++; return null; }
            var object = (CompetitionArenaObjectInstance) factory.get();
            object.setSlotIndex(objects.size() + 2);
            objects.add(object);
            return object;
        }
        @Override public SpriteAnimationSet animations(String key) { return animationSets.get(key); }
        @Override public int oscillationByte(int nativeOffset) { return 0; }
        @Override public int floorDistance(int x, int y) { return floorDistance; }
        @Override public int randomWord() { return randomWords[randomCalls++ % randomWords.length]; }
        @Override public int bottomY() { return 0x700; }
        @Override public int wrapYMask() { return -1; }
        @Override public void playSfx(int id) { sounds.add(id); }
        @Override public void hurt(S3kCompetitionPlayer player, int sourceX) { hurtSlots.add(player.slot()); }
        @Override public int paletteColor(int line, int index) { return palette[line][index]; }
        @Override public void setPaletteColor(int line, int index, int nativeColor) { palette[line][index] = nativeColor; }
        @Override public List<RockDebrisPiece> rockDebris() { return rockRows; }
    }
}
