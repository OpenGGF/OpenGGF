package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.NativePositionOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kDezConveyorBeltObjectInstance {
    @Test
    void unsignedHalfOpenRectangleAndCentrelineSelectContactAndDirection() {
        // subtype 4 means [-32,+32) in X; vertical window is [-48,+48).
        int[][] cases = {
                {-33, -1, 0}, {-32, -1, 2}, {31, -1, 2}, {32, -1, 0},
                {0, -49, 0}, {0, -48, 2}, {0, -1, 2}, {0, 0, -2}, {0, 47, -2}, {0, 48, 0}
        };
        for (int flags = 0; flags < 4; flags++) {
            for (int[] c : cases) {
                var p = player(0x100 + c[0], 0x180 + c[1]);
                var belt = belt(4, flags, p, List.of());
                belt.update(0, p);
                assertEquals(0x100 + c[0] + ((flags & 1) == 0 ? c[2] : -c[2]), p.getCentreX(),
                        "flags=" + flags + " dx=" + c[0] + " dy=" + c[1]);
            }
        }
    }

    @Test
    void subtypeHighBitIsIgnoredAndZeroWidthNeverCarries() {
        for (int subtype : new int[] {0, 0x80, 4, 0x84, 0x7F, 0xFF}) {
            var p = player(0x100, 0x17F);
            belt(subtype, 0, p, List.of()).update(0, p);
            assertEquals(subtype == 0 || subtype == 0x80 ? 0x100 : 0x102, p.getCentreX());
        }
    }

    @Test
    void onlyNativeTwoPlayersAreCarriedAndAirborneSlotIsRejectedIndependently() {
        var main = player(0x100, 0x17F);
        var second = player(0x100, 0x180);
        var extra = player(0x100, 0x17F);
        var belt = belt(4, 0, main, List.of(second, extra));
        belt.update(0, main);
        assertEquals(0x102, main.getCentreX());
        assertEquals(0xFE, second.getCentreX());
        assertEquals(0x100, extra.getCentreX(), "ROM explicitly calls Player_1 and Player_2");
        main.setAir(true);
        belt.update(1, main);
        assertEquals(0x102, main.getCentreX());
        assertEquals(0xFC, second.getCentreX());
    }

    @Test
    void integerCarryPreservesFractionsAndVelocity() {
        var p = player(0x100, 0x17F);
        p.setSubpixelRaw(0xA123, 0xBCDE);
        p.setXSpeed((short) -0x123);
        p.setYSpeed((short) 0x234);
        p.setGSpeed((short) 0x345);
        belt(4, 0, p, List.of()).update(0, p);
        assertEquals(0x102, p.getCentreX());
        assertEquals(0xA123, p.getXSubpixelRaw());
        assertEquals(0xBCDE, p.getYSubpixelRaw());
        assertEquals(-0x123, p.getXSpeed());
        assertEquals(0x234, p.getYSpeed());
        assertEquals(0x345, p.getGSpeed());
    }

    @Test
    void wrappedWorldCoordinatesUseWordArithmetic() {
        var p = player(1, 0);
        var belt = new S3kDezConveyorBeltObjectInstance(new ObjectSpawn(0xFFFE, 1, 0x50, 4, 0, false, 0));
        belt.setServices(new TestObjectServices());
        belt.update(0, p);
        assertEquals(3, p.getCentreX());
    }

    private TestPlayableSprite player(int x, int y) {
        var player = new TestPlayableSprite();
        NativePositionOps.writeXPosResetSubpixel(player, x);
        NativePositionOps.writeYPosResetSubpixel(player, y);
        player.setAir(false);
        return player;
    }

    private S3kDezConveyorBeltObjectInstance belt(int subtype, int flags, TestPlayableSprite main,
                                                List<TestPlayableSprite> sidekicks) {
        var belt = new S3kDezConveyorBeltObjectInstance(new ObjectSpawn(0x100, 0x180, 0x50, subtype, flags, false, 0));
        belt.setServices(new TestObjectServices() {
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> main, () -> sidekicks);
            }
        });
        return belt;
    }
}
