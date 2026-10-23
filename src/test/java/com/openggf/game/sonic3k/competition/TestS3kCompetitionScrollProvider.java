package com.openggf.game.sonic3k.competition;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.internal.ExplicitForegroundVerticalScroll;
import com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character;
import com.openggf.game.sonic3k.competition.S3kCompetitionScroll.CameraPair;
import com.openggf.game.sonic3k.competition.S3kCompetitionScroll.CameraPosition;
import com.openggf.game.sonic3k.competition.S3kCompetitionScroll.View;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import com.openggf.level.scroll.CameraDrivenScrollHandler;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the actual ROM owner at the world boundary without a render/CPU boot. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionScrollProvider {
    private Rom rom;
    private final ScrollHandlerProvider.ZoneConstants constants=new ScrollHandlerProvider.ZoneConstants() {
        @Override public int getZoneCount(){return 0x18;}
        @Override public String getZoneName(int index){return Integer.toHexString(index);}
    };
    @BeforeEach void rom() throws Exception {rom=RomManager.getInstance().getRom();}
    private static S3kCompetitionPlayer player(int slot,int x,int y) {
        return new S3kCompetitionPlayer(slot,slot==0?Character.SONIC:Character.TAILS,x,y);
    }
    private static CameraPair cameras(int x1,int y1,int x2,int y2) {
        return new CameraPair(new CameraPosition(x1,y1),new CameraPosition(x2,y2));
    }
    private final class Fixture {
        final AtomicReference<S3kCompetitionScroll> scroll;
        final AtomicReference<List<S3kCompetitionPlayer>> players;
        final List<View> mirrors=new ArrayList<>();
        final S3kCompetitionScrollProvider provider;
        Fixture(Arena arena) throws Exception {
            var owner=new S3kCompetitionScroll(rom,arena,5);owner.initialize(cameras(0x500,0x150,0x500,0x150));
            scroll=new AtomicReference<>(owner);
            players=new AtomicReference<>(List.of(player(0,0x5B0,0x180),player(1,0x580,0x180)));
            provider=new S3kCompetitionScrollProvider(constants,scroll::get,players::get,mirrors::add);
        }
        ZoneScrollHandler handler(){return provider.getHandler(scroll.get().arena().zoneId());}
        boolean advance(Camera camera){return ((CameraDrivenScrollHandler)handler()).advanceCameraForFrame(camera,0);}
    }

    @Test void dispatchUsesOnlyTheCurrentNativeArenaAndInjectedConstants() throws Exception {
        var f=new Fixture(Arena.AZURE_LAKE);assertSame(constants,f.provider.getZoneConstants());
        for(int zone=-1;zone<0x19;zone++) {
            if(zone==0xE) assertInstanceOf(CameraDrivenScrollHandler.class,f.provider.getHandler(zone));
            else assertNull(f.provider.getHandler(zone),"no ordinary/default handler for zone"+zone);
        }
        var bpz=new S3kCompetitionScroll(rom,Arena.BALLOON_PARK,5);f.scroll.set(bpz);
        assertNull(f.provider.getHandler(0xE));assertNotNull(f.provider.getHandler(0xF));
        f.scroll.set(null);assertNull(f.provider.getHandler(0xF));
    }
    @Test void genericLoadInitAndResetPreserveTheHostInitializedNativeOwner() throws Exception {
        var f=new Fixture(Arena.AZURE_LAKE);f.advance(null);var before=f.scroll.get().capture();
        f.provider.load(rom);f.provider.initForZone(0xE,0,0,0);f.handler().init(1,0x777,0x888);
        f.provider.resetZoneState();f.provider.load(rom);
        assertEquals(before,f.scroll.get().capture());assertEquals(2,f.mirrors.size());
    }
    @Test void cameraBoundaryUsesPostObjectBodiesWithoutOverwritingActorHistoryAndMirrorsBoth() throws Exception {
        var f=new Fixture(Arena.AZURE_LAKE);var owner=f.scroll.get();
        var p1=f.players.get().get(0);var p2=f.players.get().get(1);
        // Host samples each actor before later object solidity moves P1.
        p1.setCentre(0x5A5,0x180);owner.recordPositions(p1,p2);
        var history=owner.capture().views().stream().map(S3kCompetitionScroll.ViewState::historyX).toList();
        p1.setCentre(0x700,0x180);
        var genericCamera=mock(Camera.class);assertTrue(f.advance(genericCamera));
        assertEquals(0x510,owner.view(0).camera().x());assertEquals(0x4F0,owner.view(1).camera().x());
        assertEquals(0x1000,owner.view(0).horizontalDelta());assertEquals(-0x1000,owner.view(1).horizontalDelta());
        assertEquals(6,owner.capture().waveCounter());assertEquals(0x2000,owner.capture().cloudDrift());
        assertEquals(List.of(0,1),f.mirrors.stream().map(View::slot).toList());
        assertEquals(List.of(owner.view(0),owner.view(1)),f.mirrors);
        assertEquals(history,owner.capture().views().stream().map(S3kCompetitionScroll.ViewState::historyX).toList());
        assertEquals(4,owner.capture().views().get(0).historyIndex());assertEquals(0x5A5,history.get(0).get(0));
        verifyNoInteractions(genericCamera);
    }
    @Test void repeatedViewProjectionCopiesNativeRasterAndNeverAdvancesLogicalState() throws Exception {
        var f=new Fixture(Arena.DESERT_PALACE);
        f.scroll.get().initialize(cameras(0x500,0x148,0x640,0x168));var handler=f.handler();
        var before=f.scroll.get().capture();int[] expected=f.scroll.get().horizontalScrollBuffer();
        var sharedComposer=new ScrollEffectComposer();
        for(int line=0;line<expected.length;line++) sharedComposer.writePackedScrollWord(line,expected[line]);
        for(int render=0;render<4;render++) {
            int[] output=new int[240];Arrays.fill(output,0x12345678);
            handler.update(output,render*123,-render*456,render*999,render&1);
            assertArrayEquals(expected,Arrays.copyOf(output,224));assertEquals(0x12345678,output[224]);
            assertEquals(0x280,handler.getMinScrollOffset());assertEquals(0x320,handler.getMaxScrollOffset());
            assertEquals(sharedComposer.getMinScrollOffset(),handler.getMinScrollOffset(),"shared API tracks negated VDP words");
            assertEquals(sharedComposer.getMaxScrollOffset(),handler.getMaxScrollOffset());
            assertEquals(0x280,handler.getBgCameraX());assertEquals(8,handler.getVscrollFactorBG());
            assertEquals(0x148,handler.getVscrollFactorFG());assertEquals(before,handler.captureRewindState());
        }
        assertEquals(before,f.scroll.get().capture());assertTrue(f.mirrors.isEmpty());
        assertThrows(IllegalArgumentException.class,()->handler.update(new int[223],0,0,0,0));
    }
    @Test void nativeForegroundZeroRemainsAuthoritativeForChromeGadget() throws Exception {
        var f=new Fixture(Arena.CHROME_GADGET);f.scroll.get().initialize(cameras(0x500,0,0x500,0x10));
        assertEquals(0,f.handler().getVscrollFactorFG());
        assertTrue(((ExplicitForegroundVerticalScroll)f.handler()).foregroundVerticalScrollExplicit());
    }
    @Test void rewindRestoresTheCurrentRecreatedOwnerAndFreshActorsThenReplaysForward() throws Exception {
        var f=new Fixture(Arena.AZURE_LAKE);f.advance(null);
        var handler=f.handler();Object before=handler.captureRewindState();
        var actorStates=f.players.get().stream().map(S3kCompetitionPlayer::capture).toList();
        f.players.get().get(0).setCentre(0x700,0x1C0);f.players.get().get(1).setCentre(0x520,0x100);
        f.advance(null);var expected=f.scroll.get().capture();
        var expectedViews=List.of(f.scroll.get().view(0),f.scroll.get().view(1));
        var oldOwner=f.scroll.get();var oldActors=f.players.get();
        f.scroll.set(new S3kCompetitionScroll(rom,Arena.AZURE_LAKE,5));
        f.players.set(actorStates.stream().map(S3kCompetitionPlayer::recreate).toList());
        f.mirrors.clear();handler.restoreRewindState(before);
        assertEquals(before,handler.captureRewindState());assertEquals(2,f.mirrors.size());
        oldActors.get(0).setCentre(0x400,0x100);oldActors.get(1).setCentre(0x7FF,0x300);
        oldOwner.advance(cameras(0x400,0x100,0x400,0x100));
        f.players.get().get(0).setCentre(0x700,0x1C0);f.players.get().get(1).setCentre(0x520,0x100);
        f.mirrors.clear();assertTrue(((CameraDrivenScrollHandler)handler).advanceCameraForFrame(null,0));
        assertEquals(expected,f.scroll.get().capture());assertEquals(expectedViews,f.mirrors);
    }
    @Test void timeAttackRetainsSecondCameraWhileAdvancingSharedBackgroundOnlyOnce() throws Exception {
        var f=new Fixture(Arena.AZURE_LAKE);f.players.set(List.of(f.players.get().getFirst()));
        var second=f.scroll.get().view(1).camera();f.advance(null);
        assertEquals(0x510,f.scroll.get().view(0).camera().x());assertEquals(second,f.scroll.get().view(1).camera());
        assertEquals(6,f.scroll.get().capture().waveCounter());assertEquals(2,f.mirrors.size());
        assertEquals(0,f.scroll.get().capture().views().get(0).historyIndex());
        assertEquals(0,f.scroll.get().capture().views().get(1).historyIndex());
    }
    @Test void nativeDeathLocksOnlyItsSlotAndRespawnReleasesThatCamera() throws Exception {
        var f=new Fixture(Arena.AZURE_LAKE);var p1=f.players.get().get(0);var p2=f.players.get().get(1);
        p1.kill();p1.setCentre(0x700,0x300);p2.setCentre(0x700,0x180);f.advance(null);
        assertEquals(new CameraPosition(0x500,0x150),f.scroll.get().view(0).camera());
        assertEquals(0,f.scroll.get().view(0).horizontalDelta());assertEquals(0x510,f.scroll.get().view(1).camera().x());
        p1.respawn(0x700,0x180,0xC,0xD);f.advance(null);
        assertEquals(0x510,f.scroll.get().view(0).camera().x());
    }
    @Test void invalidOrMissingNativeRosterFailsBeforeAdvancingTheOwner() throws Exception {
        var f=new Fixture(Arena.AZURE_LAKE);var before=f.scroll.get().capture();
        f.players.set(List.of());assertThrows(IllegalStateException.class,()->f.advance(null));
        f.players.set(List.of(player(1,0x500,0x180),player(0,0x500,0x180)));
        assertThrows(IllegalStateException.class,()->f.advance(null));
        assertEquals(before,f.scroll.get().capture());assertTrue(f.mirrors.isEmpty());
    }
    @Test void allFiveRomArenaOwnersProduceOneNativeFrameAndBothViewsAtTheSameBoundary() throws Exception {
        for(Arena arena:Arena.values()) {
            var f=new Fixture(arena);int y=arena==Arena.BALLOON_PARK?0x280:arena==Arena.CHROME_GADGET?0x40:0x150;
            f.scroll.get().initialize(cameras(0x500,y,0x500,y));
            f.players.get().get(0).setCentre(0x5B0,y+0x30);f.players.get().get(1).setCentre(0x580,y+0x30);
            assertTrue(f.advance(null));assertEquals(0x510,f.mirrors.get(0).camera().x(),arena.name());
            assertEquals(0x4F0,f.mirrors.get(1).camera().x(),arena.name());
            assertEquals(y,f.mirrors.get(0).camera().y());assertEquals(y,f.mirrors.get(1).camera().y());
            assertEquals(108,f.mirrors.get(0).horizontalScroll().size());assertEquals(116,f.mirrors.get(1).horizontalScroll().size());
            int[] raster=new int[224];var before=f.scroll.get().capture();f.handler().update(raster,0,0,12345,0);
            assertArrayEquals(f.scroll.get().horizontalScrollBuffer(),raster);assertEquals(before,f.scroll.get().capture());
        }
    }
}
