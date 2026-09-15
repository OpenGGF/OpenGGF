package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen locked-on placement inventory. Missing families remain explicit. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozObjectInventory {
    @Test void romPlacementsAndRemainingFactoryGapsMatchInventory() throws Exception {
        try (var rom=new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            verify(rom,0x1F4866,3600,599,"72f954524c0d68b213be273d6b5317621c60c1a4aab8949ffc2cc72482dc66db",214);
            verify(rom,0x1F5676,2946,490,"3cca36d2d4db8db480f36c67fafa46833f4f7a63ff1cab6d6835d2380bb225c0",201);
        }
    }
    private void verify(Rom rom,int address,int size,int count,String sha,int missing) throws Exception {
        var bytes=rom.readBytes(address,size);
        assertEquals(sha,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var placements=CommonPlacementParser.parseObjectRecords(new RomByteReader(bytes),0);
        assertEquals(count,placements.size());
        var registry=new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 8; }
        };
        assertEquals(missing,placements.stream().map(registry::create)
                .filter(PlaceholderObjectInstance.class::isInstance).count(),
                "update the explicit inventory as families are implemented; zero is not yet expected");
        placements.stream().filter(p -> p.objectId()==0x38).forEach(p ->
                assertInstanceOf(SozQuicksandObjectInstance.class,registry.create(p)));
    }
}
