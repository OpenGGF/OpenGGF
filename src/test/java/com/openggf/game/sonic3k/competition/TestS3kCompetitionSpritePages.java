package com.openggf.game.sonic3k.competition;

import com.openggf.camera.Camera;
import com.openggf.data.RomManager;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CPU native SAT construction. Real GPU composition remains the shared view consumer's check. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionSpritePages {
    @Test void nativePhysicalPagesStartWithExactP1MaskPrefixAndFlipOnlyAtOwningVblank() throws Exception {
        var f=new Fixture();
        var initial=f.pages.front();
        assertEquals(2,initial.player1().pieces().size());assertTrue(initial.player2().pieces().isEmpty());
        var first=initial.player1().pieces().getFirst();var second=initial.player1().pieces().getLast();
        assertEquals(-127,first.screenX());assertEquals(-128,second.screenX());
        assertEquals(107,first.screenY());assertEquals(1,first.mapping().widthTiles());
        assertEquals(4,first.mapping().heightTiles());assertEquals(0,first.mapping().tileIndex());
        assertTrue(first.maskPrefix());assertTrue(second.maskPrefix());
        f.submit(0,2);var built=f.prepare();
        assertSame(initial,f.pages.uploaded(),"building the back page does not DMA it");
        assertSame(initial,f.pages.publish(PlcLifecyclePhase.LAG));
        assertEquals(0,f.pages.currentPageWord());assertTrue(f.pages.flipPending());
        assertSame(built,f.pages.publish(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertEquals(0xFFFF,f.pages.currentPageWord());assertFalse(f.pages.flipPending());
        assertSame(built,f.pages.publish(PlcLifecyclePhase.NORMAL_PAUSE));
        f.newPass();f.player(0).setCentreNative(110,50);f.submit(0,2);var next=f.prepare();
        assertSame(built,f.pages.publish(PlcLifecyclePhase.LAG));
        var fade=f.pages.publish(PlcLifecyclePhase.PALETTE_FADE);
        assertSame(built.player1(),fade.player1());assertSame(built.player2(),fade.player2());
        assertSame(fade,f.pages.uploaded());
        assertTrue(f.pages.flipPending());
        assertSame(next,f.pages.publish(PlcLifecyclePhase.LEVEL_TITLE_CARD));
        assertEquals(0,f.pages.currentPageWord());
    }

    @Test void lockedOnVintFadeUsesFixedTopDmaAndIndependentHintBottomPageBranch() throws Exception {
        var rom=RomManager.getInstance().getRom();
        var hex=java.util.HexFormat.of();
        assertArrayEquals(hex.parseHex("610001ba"),rom.readBytes(0xA94,4),"VInt_12 calls Do_ControllerPal");
        assertArrayEquals(hex.parseHex("2abc96fc95003abc977f"),rom.readBytes(0xCC2,10),
                "Do_ControllerPal fixed top DMA source is $FFF800");
        assertArrayEquals(hex.parseHex("4a78ef3c6726"),rom.readBytes(0xD60,6),
                "HInt tests Current_sprite_table_page and branches on zero");
        assertEquals(0xD8C,0xD66+(rom.readByte(0xD65)&0xFF));
        assertArrayEquals(hex.parseHex("2abc96bd95803abc977f"),rom.readBytes(0xD72,10),
                "nonzero front bottom DMA source is $FF7B00");
        assertArrayEquals(hex.parseHex("2abc96be95c03abc977f"),rom.readBytes(0xD98,10),
                "zero front bottom DMA source is $FF7D80");
    }

    @Test void initialFadePublishesNewPhysicalTopButRetainsBottomFrontWithoutFlipping() throws Exception {
        var f=new Fixture();var retained=f.pages.front();
        f.submit(0,2);f.submit(1,2);var prepared=f.prepare();
        var before=f.pages.capture();
        assertEquals(2,slots(prepared.player1()).size());assertEquals(2,slots(prepared.player2()).size());
        var fade=f.pages.publish(PlcLifecyclePhase.PALETTE_FADE);
        assertSame(prepared.player1(),fade.player1(),"Do_ControllerPal DMA reads physical Sprite_table");
        assertSame(retained.player2(),fade.player2(),"HInt reads the front P2 alternate page independently");
        assertTrue(fade.player2().pieces().isEmpty());
        assertSame(fade,f.pages.uploaded());assertEquals(0,f.pages.currentPageWord());assertTrue(f.pages.flipPending());
        assertEquals(before.physicalPages(),f.pages.capture().physicalPages());
        assertEquals(before.inputPointers(),f.pages.capture().inputPointers());
        assertSame(retained,f.pages.publish(PlcLifecyclePhase.LAG));
        var repeated=f.pages.publish(PlcLifecyclePhase.PALETTE_FADE);assertEquals(fade,repeated);
        assertSame(prepared,f.pages.publish(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertEquals(0xFFFF,f.pages.currentPageWord());assertFalse(f.pages.flipPending());
    }

    @Test void fadeRetainsTheFrontBottomAcrossBothPageWordsAndUploadedRestoreForward() throws Exception {
        var f=new Fixture();f.submit(0,2);f.submit(1,2);var first=f.prepare();
        f.pages.publish(PlcLifecyclePhase.ORDINARY_LEVEL);
        f.newPass();f.player(0).setCentreNative(140,60);f.player(1).setCentreNative(150,60);
        f.submit(0,2);f.submit(1,2);var second=f.prepare();
        var nonzeroFade=f.pages.publish(PlcLifecyclePhase.PALETTE_FADE);
        assertSame(first.player1(),nonzeroFade.player1());assertSame(first.player2(),nonzeroFade.player2());
        assertEquals(0xFFFF,f.pages.currentPageWord());assertTrue(f.pages.flipPending());
        f.pages.publish(PlcLifecyclePhase.ORDINARY_LEVEL);assertEquals(0,f.pages.currentPageWord());
        f.newPass();f.player(0).setCentreNative(180,70);f.player(1).setCentreNative(190,70);
        f.submit(0,2);f.submit(1,2);var third=f.prepare();
        var fade=f.pages.publish(PlcLifecyclePhase.PALETTE_FADE);
        assertSame(third.player1(),fade.player1());assertSame(second.player2(),fade.player2());
        assertNotEquals(third.player2(),fade.player2(),"bottom is not the newest physical page0");
        assertEquals(0,f.pages.currentPageWord());assertTrue(f.pages.flipPending());
        var checkpoint=f.pages.capture();
        f.pages.publish(PlcLifecyclePhase.NORMAL_PAUSE);f.pages.publish(PlcLifecyclePhase.LAG);
        f.pages.publish(PlcLifecyclePhase.PALETTE_FADE);var expected=f.pages.capture();
        f.pages.restore(checkpoint);
        assertEquals(fade,f.pages.uploaded(),"rewind restores the actually uploaded mixed page pair");
        assertEquals(checkpoint,f.pages.capture());
        f.pages.publish(PlcLifecyclePhase.NORMAL_PAUSE);f.pages.publish(PlcLifecyclePhase.LAG);
        f.pages.publish(PlcLifecyclePhase.PALETTE_FADE);assertEquals(expected,f.pages.capture());
        assertEquals(0xFFFF,f.pages.currentPageWord());assertFalse(f.pages.flipPending());
    }

    @Test void queueKeepsFifoDuplicatesPerBucketAndSwapsOnlyTheExactRawPlayerPrefix() throws Exception {
        var f=new Fixture();f.submit(0,2);f.submit(1,2);f.submit(0,2);f.submit(1,1);
        var admitted=NativeSpriteSubmissionAccess.admissions(f.objects);
        var built=f.prepare();
        assertEquals(List.of(1,0,1,0),slots(built.player1()));
        assertEquals(List.of(1,1,0,0),slots(built.player2()));
        assertEquals(1,f.pages.inputPointer(2,0));assertEquals(0,f.pages.inputPointer(2,1));
        assertEquals(admitted,NativeSpriteSubmissionAccess.admissions(f.objects));
        f.newPass();f.submit(1,2);f.submit(0,2);var reversed=f.prepare();
        assertEquals(List.of(1,0),slots(reversed.player1()));assertEquals(List.of(1,0),slots(reversed.player2()));
    }

    @Test void untouchedStalePointerWordsAffectOneEntryFramesAndSurviveRestoreForward() throws Exception {
        var f=new Fixture();f.submit(50,2);f.submit(1,2);f.prepare();
        assertEquals(1,f.pages.inputPointer(2,1));
        var before=f.pages.capture();
        f.newPass();f.submit(0,2);var next=f.prepare();
        assertEquals(List.of(0),slots(next.player1()));
        assertEquals(List.of(1),slots(next.player2()),"no count guard protects the raw pair comparison");
        var forward=f.pages.capture();
        f.pages.restore(before);f.newPass();f.submit(0,2);
        assertEquals(next,f.prepare());assertEquals(forward,f.pages.capture());
        f.newPass();f.submit(0,2);var only=f.prepare();
        assertEquals(List.of(0),slots(only.player2()),"the previous swap left stale Player_1, not Player_2");
    }

    @Test void p1ClearsBeforeSkipP2OnlyOrsAndUnqueuedPublishersRetainBit7() throws Exception {
        var f=new Fixture();
        f.player(0).setNativeRenderFlags(0x94); // World + retained bit7 + skip P1.
        f.player(1).setNativeRenderFlags(0x84);
        f.submit(0,2);f.prepare();
        assertEquals(0x94,f.player(0).nativeRenderFlags(),"P2 range restores bit7 after P1 clears it");
        assertEquals(0x84,f.player(1).nativeRenderFlags(),"no inferred clearing of an unqueued body");
        f.newPass();f.player(0).setNativeRenderFlags(0x9C);f.submit(0,2);f.prepare();
        assertEquals(0x1C,f.player(0).nativeRenderFlags(),"both view skip flags still receive P1's clear");
        f.newPass();f.player(0).setNativeRenderFlags(0x84);f.player(1).setNativeRenderFlags(0x84);
        f.submit(50,2);f.submit(1,2);f.prepare();
        f.newPass();f.player(1).setCentreNative(600,300);f.submit(0,2);f.prepare();
        assertEquals(0x84,f.player(1).nativeRenderFlags(),"stale P2-only out-of-range reference never clears bit7");
    }

    @Test void satFullStopsPiecesButStillPublishesLaterQueuedRangeAndKeepsNative78Plus2And80Caps() throws Exception {
        var f=new Fixture();
        var blade=new CompetitionArenaObjectInstance(new ObjectSpawn(120,60,0x5E,0,0,false,0),
                CompetitionArenaObjectInstance.Kind.BLADE);
        f.objects.addDynamicObjectAtSlot(blade,4);
        assertFalse(blade.nativeDrawPublished());
        for(int i=0;i<63;i++)f.submit(0,0);
        for(int i=0;i<20;i++)f.submit(0,1);
        f.submit(4,5);var built=f.prepare();
        assertEquals(80,built.player1().pieces().size());assertEquals(80,built.player2().pieces().size());
        assertEquals(78,slots(built.player1()).size());assertEquals(80,slots(built.player2()).size());
        assertTrue(blade.nativeVisible(),"visibility is range admission, even after both SAT tables filled");
    }

    @Test void normalProjectionUsesNativeWordWrapExtentsFlipsAndZeroXCorrection() throws Exception {
        var mapping=new SpriteMappingPiece(-3,-4,2,1,7,false,false,2,false);
        var f=new Fixture(mapping);f.player(0).setNativeRenderFlags(7);f.submit(0,2);
        var piece=f.prepare().player1().pieces().getLast();
        assertEquals(87,piece.screenX());assertEquals(46,piece.screenY());
        assertTrue(piece.mapping().hFlip());assertTrue(piece.mapping().vFlip());assertEquals(2,piece.mapping().paletteIndex());
        f.newPass();f.player(0).setNativeRenderFlags(4);f.player(0).setCentreNative(0x400,50);
        f.scroll.initialize(new S3kCompetitionScroll.CameraPair(new S3kCompetitionScroll.CameraPosition(1020,0),
                new S3kCompetitionScroll.CameraPosition(1020,0)));f.submit(0,2);
        assertEquals(1,f.prepare().player1().pieces().getLast().screenX());
        var edge=new Fixture(new SpriteMappingPiece(-228,0,1,1,0,false,false,0,false));
        edge.submit(0,2);assertEquals(-127,edge.prepare().player1().pieces().getLast().screenX(),"native raw X zero becomes one");
    }

    @Test void elephantUsesMainRangeAndChildMaskAfterBiasWithStrictPhysicalPieceClipping() throws Exception {
        var f=new Fixture(new SpriteMappingPiece(-32,0,1,1,1,false,false,0,false));
        var elephant=new CompetitionArenaObjectInstance(new ObjectSpawn(0,20,0x60,1,0,false,0),
                CompetitionArenaObjectInstance.Kind.ELEPHANT);
        f.objects.addDynamicObjectAtSlot(elephant,4);f.submit(4,5);
        var built=f.prepare();
        assertTrue(elephant.nativeVisible());
        assertTrue(slots(built.player1()).isEmpty(),"multi anchor X=96 is rejected, unlike a normal piece");
        assertTrue(slots(built.player2()).isEmpty());
        var normal=new Fixture(new SpriteMappingPiece(-132,0,1,1,1,false,false,0,false));
        normal.submit(0,2);assertEquals(List.of(0),slots(normal.prepare().player1()),"normal mappings have no per-piece anchor clip");
    }

    private static List<Integer> slots(S3kCompetitionSpritePages.Frame frame) {
        return frame.pieces().stream().filter(piece->!piece.maskPrefix()).map(S3kCompetitionSpritePages.Piece::nativeSlot).toList();
    }
    private static final class Fixture {
        final ObjectManager objects;
        final List<S3kCompetitionPlayer> players=List.of(new S3kCompetitionPlayer(0,S3kCompetitionPlayer.Character.SONIC,100,50),
                new S3kCompetitionPlayer(1,S3kCompetitionPlayer.Character.TAILS,110,50));
        final S3kCompetitionScroll scroll;
        final S3kCompetitionSpritePages pages;
        Fixture() throws Exception { this(new SpriteMappingPiece(0,0,1,1,1,false,false,0,false)); }
        Fixture(SpriteMappingPiece piece) throws Exception {
            var camera=mock(Camera.class);when(camera.getWidth()).thenReturn((short)320);when(camera.getHeight()).thenReturn((short)224);
            var registry=mock(ObjectRegistry.class,CALLS_REAL_METHODS);when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_3K);
            objects=new ObjectManager(List.of(),registry,0,null,null,mock(GraphicsManager.class),camera,new StubObjectServices());
            players.forEach(player->player.setNativeRenderFlags(4));
            scroll=new S3kCompetitionScroll(RomManager.getInstance().getRom(),S3kCompetitionSession.Arena.AZURE_LAKE,5);
            scroll.initialize(new S3kCompetitionScroll.CameraPair(new S3kCompetitionScroll.CameraPosition(0,0),new S3kCompetitionScroll.CameraPosition(0,0)));
            var sheet=new ObjectSpriteSheet(new Pattern[]{new Pattern(),new Pattern()},List.of(new SpriteMappingFrame(List.of(piece))),0,1);
            pages=new S3kCompetitionSpritePages(key->sheet);
        }
        S3kCompetitionPlayer player(int slot) { return players.get(slot); }
        void submit(int slot,int bucket) { assertTrue(NativeSpriteSubmissionAccess.submitSpilling(objects,slot,bucket)); }
        void newPass() { objects.update(0,null,List.of(),1,false); }
        S3kCompetitionSpritePages.Pair prepare() { return pages.prepare(objects,players,scroll); }
    }
}
