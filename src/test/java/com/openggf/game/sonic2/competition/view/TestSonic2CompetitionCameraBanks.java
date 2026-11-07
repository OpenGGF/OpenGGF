package com.openggf.game.sonic2.competition.view;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionCameraBanks {
    @Test void missingObjectFieldsRejectBeforeAnyTransactionMutation() {
        var state = new com.openggf.game.sonic2.competition.state.Sonic2CompetitionNativeState(
                new com.openggf.game.sonic2.competition.state.Sonic2TeleportPorts.BackgroundBanks() {
                    public void swapPlayers() { fail("unbound transaction must not mutate BG banks"); }
                    public com.openggf.game.sonic2.competition.state.Sonic2TeleportPorts.BackgroundPosition primaryPosition() {
                        throw new AssertionError("unbound transaction must not read gameplay");
                    }
                }, () -> fail("unbound transaction must not mutate plane scroll"),
                com.openggf.game.sonic2.competition.state.Sonic2TeleportPorts.UNBOUND_OBJECT_FIELDS);
        assertFalse(state.operational());
        assertFalse(state.swapActorWorldState(null,null));
    }
    @Test void teleportMovesTableBanksWithoutMovingScratchOrPublishedScroll() throws Exception {
        var owner = new Sonic2CompetitionScroll(GameServices.rom().getRom());
        var a = new Sonic2CompetitionScroll.BackgroundBank(0x12345678,0x23456789,3,4,5,6);
        var b = new Sonic2CompetitionScroll.BackgroundBank(0x67891234,0x78912345,30,40,50,60);
        var first = new Sonic2CompetitionScroll.CameraWords(0x12345678,0x22334455,b,0x1200,1,2,3);
        var second = new Sonic2CompetitionScroll.CameraWords(0x55667788,0x66778899,a,0x3400,4,5,6);
        short[] scratch = new short[24]; scratch[10] = 0x1234;
        int[] horizontal = new int[224]; horizontal[17] = 0x56789;
        var before = new Sonic2CompetitionScroll.State(a,b,scratch,horizontal,10,20,first,second);
        owner.restore(before); owner.swapPlayers();
        var after = owner.capture();
        assertEquals(b, after.p1()); assertEquals(a, after.p2());
        assertEquals(second, after.firstWords()); assertEquals(first, after.secondWords());
        assertArrayEquals(scratch, after.scratch()); assertArrayEquals(horizontal, after.horizontal());
        assertEquals(10, after.v1()); assertEquals(20, after.v2());
        owner.copyBackgroundsAtVInt(true);
        assertEquals(b, owner.capture().firstWords().backgroundCopy(), "title copies the complete P1 camera bank");
        assertEquals(second.foregroundFractions(), owner.capture().firstWords().copiedForegroundFractions());
        assertEquals(first, owner.capture().secondWords(), "title leaves the entire P2 copy bank untouched");
        owner.copyBackgroundsAtVInt(false);
        assertEquals(b, owner.capture().firstWords().backgroundCopy());
        owner.restore(before);
        assertEquals(first, owner.capture().firstWords());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 11, 12})
    void levelInitializersPreserveWordFractionsAndOnlyClearTheirNativeLongs(int zone) throws Exception {
        var rom=GameServices.rom().getRom();
        var adapter=new Sonic2CompetitionScrollSourceAdapter(rom,
                new com.openggf.game.sonic2.Sonic2GameModule().getScrollHandlerProvider().getZoneConstants());
        var session=new com.openggf.game.sonic2.competition.Sonic2CompetitionSession();
        var first=new com.openggf.camera.Camera(com.openggf.game.session.EngineServices.current().configuration());
        var second=new com.openggf.camera.Camera(com.openggf.game.session.EngineServices.current().configuration());
        first.setX((short)0x1200);first.setY((short)0x600);second.setX((short)0x2400);second.setY((short)0x700);
        session.bindCameras(first,second);
        var bank=new Sonic2CompetitionScroll.BackgroundBank(0x11223344,0x22334455,0x33445566,0x44556677,0x55667788,0x66778899);
        var words=new Sonic2CompetitionScroll.CameraWords(1,2,bank,0x1200,3,4,5);
            adapter.owner().restore(new Sonic2CompetitionScroll.State(bank,bank,new short[24],new int[224],0,0,words,words));
            adapter.initialize(com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile.load(rom,zone,0),session);
            var state=adapter.owner().capture();
        assertAll(
            () -> assertEquals(0x12005566,state.p1().x2()),
            () -> assertEquals(0x12007788,state.p1().x3()),
            () -> assertEquals(zone==0?0:0x06006677,state.p1().y2()),
            () -> assertEquals(zone==0?0:bank.y3(),state.p1().y3()),
            () -> assertEquals(zone==11?0x00C04455:0,state.p1().y()),
            () -> assertEquals(zone==12?0x12003344:0,state.p2().x()),
            () -> assertEquals(0,state.firstWords().coarseX()),
            () -> assertEquals(0,state.firstWords().backgroundFlags()),
            () -> assertEquals(0,state.firstWords().backgroundFlagsCopy()),
            () -> assertEquals(3,state.firstWords().backgroundBlocks()),
            () -> assertEquals(bank,state.firstWords().backgroundCopy()));
    }

    @Test void initialDeformReadsVintThenExplicitClearPreservesBackgroundAndScratch() throws Exception {
        var owner = new Sonic2CompetitionScroll(GameServices.rom().getRom());
        owner.initialize(Sonic2CompetitionScroll.BackgroundBank.ZERO,Sonic2CompetitionScroll.BackgroundBank.ZERO);
        owner.update(Sonic2CompetitionScroll.Zone.EHZ,0,new Sonic2CompetitionScroll.Camera(100,128),
                new Sonic2CompetitionScroll.Camera(200,256),8);
        assertEquals(-1, owner.capture().scratch()[0], "initial Vint divisible by eight decrements retained EHZ word");
        var before = owner.capture(); owner.clearInitialDisplayScroll();
        assertArrayEquals(new int[224],owner.capture().horizontal());
        assertArrayEquals(before.scratch(),owner.capture().scratch());
        assertEquals(0,owner.view(0).foregroundVScroll());
        assertEquals(-224,owner.view(1).foregroundVScroll());
        owner.menuClear(); assertArrayEquals(before.scratch(),owner.capture().scratch());
        assertEquals(Sonic2CompetitionScroll.CameraWords.ZERO,owner.capture().firstWords());
    }
}
