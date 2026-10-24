package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aProfile;
import com.openggf.game.soniccd.level.CdShieldArt;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-literal LoadShieldArt service tests, authored before production; no native execution oracle. */
class TestCdPowerupArtRuntime {
    @ParameterizedTest
    @CsvSource({"130,0,2", "132,1,4", "134,2,6", "136,3,8", "138,4,10"})
    void requestCopiesTheActualRawSlotThenUploadsTheCompleteRetainedTail(int request, int slot, int cleared) throws Exception {
        var assets = assets(); var owner = new CdPowerupArtRuntime(assets);
        byte[] cpu = filled(0xA5), resident = filled(0xCC);
        var ingress = new CdPowerupArtRuntime.State(cpu, resident);
        var result = owner.service(ingress, request);
        assertEquals(cleared, result.powerup());
        byte[] expected = cpu.clone();
        assets.copy(slot).bytes().buffer().get(expected, 0, 0x400);
        assertArrayEquals(expected, result.state().cpuBuffer().copy());
        assertArrayEquals(expected, result.state().residentBank().copy());
        var dma = result.dma().orElseThrow();
        assertEquals(0xA880, dma.vramByteAddress());
        assertEquals(0x480, dma.bytes().length());
        assertArrayEquals(expected, dma.bytes().copy());
        for (int i = 0x400; i < 0x480; i++) assertEquals(0xA5, dma.bytes().unsignedByte(i), "RAM tail is not source padding");
        assertArrayEquals(cpu, ingress.cpuBuffer().copy());
        assertArrayEquals(resident, ingress.residentBank().copy());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 10, 127})
    void clearedRequestBitSkipsTheTableReadEvenForAnUnboundLowSelector(int powerup) throws Exception {
        var owner = new CdPowerupArtRuntime(assets());
        var state = new CdPowerupArtRuntime.State(filled(0xA5), filled(0xCC));
        var result = owner.service(state, powerup);
        assertEquals(powerup, result.powerup()); assertTrue(result.dma().isEmpty());
        assertEquals(state, result.state());
    }

    @ParameterizedTest
    @ValueSource(ints = {128, 129, 131, 140, 254, 255})
    void activeUnboundPointerReadsStopWithoutPublishingOrInventingArt(int powerup) throws Exception {
        var owner = new CdPowerupArtRuntime(assets());
        var state = new CdPowerupArtRuntime.State(filled(0xA5), filled(0xCC));
        var error = assertThrows(CdPowerupArtRuntime.UnsupportedArt.class, () -> owner.service(state, powerup));
        assertEquals(((powerup & 0x7F) - 2 & 0xFF) * 2, error.pointerOffset());
        assertArrayEquals(filled(0xA5), state.cpuBuffer().copy());
        assertArrayEquals(filled(0xCC), state.residentBank().copy());
    }

    @Test void replayUsesOnlyTheSuppliedStateAndClearedRequestDoesNotUploadAgain() throws Exception {
        var owner = new CdPowerupArtRuntime(assets());
        var cut = new CdPowerupArtRuntime.State(filled(0x39), filled(0x87));
        var forward = owner.service(cut, 0x88); var replay = owner.service(cut, 0x88);
        assertEquals(forward.state(), replay.state()); assertEquals(8, replay.powerup());
        assertArrayEquals(forward.dma().orElseThrow().bytes().copy(), replay.dma().orElseThrow().bytes().copy());
        var idle = owner.service(forward.state(), forward.powerup());
        assertTrue(idle.dma().isEmpty()); assertEquals(forward.state(), idle.state());
    }

    @Test void completeStateOwnsItsBytesAndComparesByNativeContent() {
        byte[] a = filled(0xA5), b = filled(0xCC);
        var state = new CdPowerupArtRuntime.State(a, b);
        var equal = new CdPowerupArtRuntime.State(a.clone(), b.clone());
        Arrays.fill(a, (byte) 0); Arrays.fill(b, (byte) 0);
        assertEquals(0xA5, state.cpuBuffer().unsignedByte(0));
        assertEquals(0xCC, state.residentBank().unsignedByte(0x47F));
        assertEquals(equal, state); assertEquals(equal.hashCode(), state.hashCode());
        assertNotEquals(state, new CdPowerupArtRuntime.State(filled(0xA4), filled(0xCC)));
    }

    @Test void missingOwnersInvalidExtentsAndNonByteRequestsReject() throws Exception {
        var owner = new CdPowerupArtRuntime(assets());
        var state = new CdPowerupArtRuntime.State(filled(0), filled(0));
        assertThrows(NullPointerException.class, () -> new CdPowerupArtRuntime(null));
        assertThrows(NullPointerException.class, () -> owner.service(null, 0));
        assertThrows(IllegalArgumentException.class, () -> owner.service(state, -1));
        assertThrows(IllegalArgumentException.class, () -> owner.service(state, 256));
        assertThrows(IllegalArgumentException.class, () -> new CdPowerupArtRuntime.State(new byte[0x47F], filled(0)));
        assertThrows(IllegalArgumentException.class, () -> new CdPowerupArtRuntime.State(filled(0), new byte[0x481]));
    }

    private static byte[] filled(int value) { byte[] b = new byte[0x480]; Arrays.fill(b, (byte) value); return b; }

    private static CdShieldArt assets() throws Exception {
        // Component-only input, not a forged authenticated program. Existing decoder validates
        // all five native pointer slots and the two real adjacent raw-copy continuations.
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        byte[] file = new byte[0x40000];
        CdR11aAsset[] order = {ART_SHIELD, ART_INV_STARS, ART_TIME_STARS, ART_GAME_OVER, ART_TIME_OVER};
        for (int slot = 0; slot < order.length; slot++) {
            var range = profile.range(order[slot]);
            for (int i = 0; i < range.length(); i++) file[range.fileOffset() + i] = (byte) (17 * slot + i);
        }
        var bytes = ByteBuffer.wrap(file); int table = profile.range(SHIELD_ART_INDEX).fileOffset();
        for (int slot = 0; slot < order.length; slot++) bytes.putInt(table + 4 * slot, profile.range(order[slot]).nativeAddress());
        return CdShieldArt.decode(profile, CdMmdImage.parse(file));
    }
}
