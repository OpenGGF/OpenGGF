package com.openggf.game.sonic2.competition;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.*;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.graphics.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Production entry from its ROM start; native callbacks corroborate the cleared P2 scroll read. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestS2CompetitionInitialCameraOrder {
    @Test void bothBanksStartFromSonicBeforeTheClearedSidekickSlotIsScrolled() throws Exception {
        GameServices.audio().setBackend(new NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        var services=EngineServices.current();var root=SessionManager.getCurrentWorldSession().rootGameModule();
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        // LevelSizeLoad's consecutive MOVE.W D0,Camera_Y_pos / Camera_Y_pos_P2.
        assertEquals(0x31C0,rom.readU16BE(0xC1C2));assertEquals(0xEE04,rom.readU16BE(0xC1C4));
        assertEquals(0x31C0,rom.readU16BE(0xC1C6));assertEquals(0xEE24,rom.readU16BE(0xC1C8));
        boolean[] seen=new boolean[3];
        try(var gl=PresentationOffscreenTestContext.openConfigured(320,224,PresentationOffscreenTestContext.LegacyProfile.CORE_41);
            AutoCloseable cleanup=()->{try{SessionManager.closeGameplaySession();}finally{services.graphics().cleanup();}};
            var views=new Sonic2CompetitionViews(services.graphics())) {
            var graphics=services.graphics();graphics.setHeadlessMode(false);graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            var title=(com.openggf.game.sonic2.titlecard.TitleCardManager)root.getTitleCardProvider();
            var observed=new Sonic2NativeCompetitionHost.Views() {
                @Override public void prepare(GameplayModeContext context,Sonic2CompetitionSession session,
                        Sonic2CompetitionLevelProfile profile,Sonic2CompetitionNativeArt art) {
                    views.prepare(context,session,profile,art);
                    int pass=title.capture().leavePass();
                    // Native completed samples1300/1310 and1325; only comparisons, no state import.
                    if(pass==1 || pass==7 || pass==22) {
                        int index=pass==1?0:pass==7?1:2;seen[index]=true;
                        assertEquals(pass==22?560:559,session.camera(0).getY());
                        assertEquals(pass==22?559:553,session.camera(1).getY());
                        if(pass==1) assertThrows(AssertionError.class,
                                ()->assertEquals(563,session.camera(1).getY()),"negative stale-initialization control");
                    }
                }
                @Override public void draw(GameplayModeContext context,Sonic2CompetitionSession session) { views.draw(context,session); }
            };
            var host=Sonic2NativeCompetitionHost.production(root,services,ignored->{},observed,false,true,
                    new com.openggf.game.sonic2.objects.Sonic2TeleportObjectFieldsAccess());
            host.clearMenuCameraRam();var input=new InputHandler();input.setLogicalOverride(LogicalInputSnapshot.neutral());
            host.bindNativeWaitInput(input::logical,()->false);
            var session=new Sonic2CompetitionSession();session.updateMenu(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(0,0,0,0,true,true),PlayerInputState.neutral()),true);
            host.loadAct(session,0,0);
            assertArrayEquals(new boolean[]{true,true,true},seen);assertEquals(559,session.camera(1).getY());
            var context=host.context();LevelFrameStep.updateTimers(LevelFrameContext.from(context));
            context.plcFrameLifecycle().runLogicalIteration(context::updateFade,frame->{
                NativeCompetitionIterationAccess.runAdmitted(frame,()->host.step(input,frame));return null;
            });
            assertEquals(565,session.camera(1).getY(),"first ordinary ScrollVerti catches up without a title offset");
        } finally {TestEnvironment.resetAll();}
    }
}
