package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDdzRomPalettes {
    private void boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(12,0).build().sprite().setDebugMode(true);
    }
    @Test void bothFlashRowsUseTheTwelveNativeDestinationsAndRomColorWords() {
        boot(); var services=spy(TestEnvironment.objectServices()); var registry=mock(PaletteOwnershipRegistry.class);
        doReturn(registry).when(services).paletteOwnershipRegistryOrNull(); List<PaletteWrite> writes=new ArrayList<>();
        doAnswer(call->{writes.add(call.getArgument(0));return null;}).when(registry).submit(any());
        int[] destinations={3,4,6,7,8,9,10,11,12,13,14,15};
        int[][] expected={{0xA,6,0xCAA,0xA88,0x866,0x444,0xE42,0xE00,0xC00,0x600,0x200,0},
                {0x888,0xAAA,0xCCC,0xAAA,0x888,0x666,0xECC,0xECA,0xAAA,0xAAA,0xCCC,0xEEE}};
        for(int row=0;row<2;row++) {
            DdzPalette.applyFlashRow(services,row);
            for(int i=0;i<12;i++) {
                var write=writes.get(row*12+i); assertEquals(2,write.lineIndex()); assertEquals(destinations[i],write.startColor());
                assertArrayEquals(new byte[]{(byte)(expected[row][i]>>>8),(byte)expected[row][i]},write.segaData());
            }
        }
        assertEquals(24,writes.size());
    }
    @Test void liveEmeraldUsesSharedRomCursorAndReplaysAfterZoneRestore() {
        boot(); GameServices.gameState().restoreS3kEmeraldProgress(List.of(3,3,3,3,3,3,3),true);
        var manager=GameServices.level().getObjectManager();
        var boss=new DdzEndBossObjectInstance(new ObjectSpawn(0x500,0x80,0,0,0,false,0)); manager.addDynamicObject(boss);
        var part=new DdzEndBossShipPartObjectInstance(boss,0); manager.addDynamicObject(part);
        var emerald=new DdzBossMasterEmeraldObjectInstance(part); manager.addDynamicObject(emerald);
        var services=spy(TestEnvironment.objectServices()); var registry=mock(PaletteOwnershipRegistry.class);
        doReturn(registry).when(services).paletteOwnershipRegistryOrNull(); List<PaletteWrite> writes=new ArrayList<>();
        doAnswer(call->{writes.add(call.getArgument(0));return null;}).when(registry).submit(any()); emerald.setServices(services);
        emerald.update(0,null); assertTrue(writes.isEmpty()); emerald.update(1,null); assertEquals(2,writes.size());
        assertEquals(3,writes.getFirst().lineIndex()); assertArrayEquals(new byte[]{6,0x60},writes.getFirst().segaData());
        var state=(DdzZoneRuntimeState)GameServices.zoneRuntimeState(); var saved=state.captureBytes();
        for(int i=0;i<40;i++) emerald.update(i,null); var advanced=state.captureBytes();
        state.restoreBytes(saved); for(int i=0;i<40;i++) emerald.update(i,null); assertArrayEquals(advanced,state.captureBytes());
        // A new emerald executes the native $10-byte copy into shared palette RAM.
        var replacement=new DdzBossMasterEmeraldObjectInstance(part); manager.addDynamicObject(replacement); replacement.setServices(services);
        replacement.update(0,null); emerald.update(1,null); assertArrayEquals(saved,state.captureBytes());
    }
}
