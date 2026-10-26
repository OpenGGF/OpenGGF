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
        HeadlessTestFixture.builder().withZoneAndAct(12,0)
                // Sonic_Init returns without a floor probe; loc_81554 owns the next movement.
                .withFreshLevelStartLifecycle().build().sprite().setDebugMode(true);
    }
    @Test void freshEntryDoesNotFabricateDefinitionsForAnUndeclaredTerrainProbe() throws Exception {
        boot();
        var level = GameServices.level().getCurrentLevel();
        var rom = com.openggf.tests.TestEnvironment.currentRom();
        int layout = rom.read32BitAddr(com.openggf.game.sonic3k.constants.Sonic3kConstants.LEVEL_PTRS_ADDR + 24 * 4);
        assertEquals(20, rom.read16BitAddr(layout));
        assertEquals(6, rom.read16BitAddr(layout + 4));
        // Declared unsigned-word far-left probe, outside the flight owner's terrain contract.
        int row = 2;
        int address = (rom.read16BitAddr(layout + 8 + row * 4) + (0xFFFF >>> 7)) & 0xFFFF;
        int selected = rom.readBytes(layout + address - 0x8000, 1)[0] & 0xFF;
        assertEquals(0x82AF, address);
        assertEquals(20, selected);
        assertTrue(selected >= level.getBlockCount(), "this load has no producer for that definition");
        var unsupported = assertThrows(IllegalStateException.class,
                () -> GameServices.level().getChunkDescAt((byte) 0, -128, row * 128));
        assertTrue(unsupported.getMessage().contains("$FF82AF: 128x128 definition 20"));
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
    @Test void exitSignalAppliesWrapPlayerClampAndCameraDeltaImmediatelyAndReleasesParent() throws Exception {
        for(int wrap:new int[]{0,0x2000}) {
            boot(); var manager=GameServices.level().getObjectManager();
            var boss=new DdzEndBossObjectInstance(new ObjectSpawn(0x2500,0x80,0,0,0,false,0)); manager.addDynamicObject(boss);
            var part=new DdzEndBossShipPartObjectInstance(boss,0); manager.addDynamicObject(part);
            var emerald=new DdzBossMasterEmeraldObjectInstance(part); manager.addDynamicObject(emerald);
            emerald.update(0,null); emerald.update(1,null);
            int anchoredX=emerald.getX(), anchoredY=emerald.getY();
            var state=(DdzZoneRuntimeState)GameServices.zoneRuntimeState(); state.setWrapOffset(wrap); state.setCameraDelta(7);
            // Isolate the ROM publication consumed at loc_81D32; the full boss route
            // owns the preceding defeat. No gameplay trace data supplies this state.
            var flag=DdzEndBossObjectInstance.class.getDeclaredField("flags"); flag.setAccessible(true); flag.setInt(boss,0x10);
            var player=TestEnvironment.objectServices().playerQuery().mainPlayerOrNull();
            int playerX=(anchoredX-wrap+20)&0xFFFF; player.setCentreX((short)playerX);
            emerald.update(2,null);
            assertEquals((playerX+7)&0xFFFF,emerald.getX(),"loc_81D44 falls directly into loc_81D4A");
            assertEquals(anchoredY,emerald.getY());
            part.setDestroyed(true); boss.setDestroyed(true);
            var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
            emerald.update(3,null); int expected=emerald.getX(); registry.restore(saved);
            var restored=manager.activeObjectsOfType(DdzBossMasterEmeraldObjectInstance.class).getFirst();
            restored.update(3,null); assertEquals(expected,restored.getX());
        }
    }

}
