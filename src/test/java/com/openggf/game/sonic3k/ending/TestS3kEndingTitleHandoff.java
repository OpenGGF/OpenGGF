package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The post-credits terminal ends with loc_5F314 (044fa46 sonic3k.asm:125904): Game_mode 0. Sega_Screen
 * (5419) writes Game_mode 4 and Title_Screen (5424) opens with Pal_FadeToBlack (5074): 22 passes of
 * Wait_VSync then Pal_ToBlack over all 64 colours, before any title initialization. Native Tails/+2
 * NTSC (declared D01 product): Game_mode is 4 at the end of poll 15531, the pass the terminal ends;
 * the palette steps red, green, then blue on polls 15532-15552 and is black on 15553.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingTitleHandoff {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void terminalEndEntersTitleModeAndFadesTheRetainedPaletteLikeTitleScreen(boolean holdStart) throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"tails","","off",null,null,null,"3333333",false,false);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            boolean seen=false;int input=0;
            for(;input<40_000;input++) {
                session.step(neutral(input));
                var post=GameServices.level().getObjectManager().activeObjectsOfType(S3kPostCreditsObjectInstance.class)
                        .stream().findFirst().orElse(null);
                if(post!=null&&post.phase()!=8){seen=true;continue;}
                if(seen)break; // the terminal's own last pass ran loc_5F314
            }
            assertTrue(seen,"declared Tails/+2 ending reached its terminal");
            assertEquals(GameMode.TITLE_SCREEN,session.loop().getCurrentGameMode(),
                    "Sega_Screen writes Game_mode 4 in the pass loc_5F314 ends the level loop");
            assertTrue(session.loop().isTitleEntryShowingLevel(),"the last level picture stays on screen");
            var level=GameServices.level();var manager=level.getObjectManager();
            int levelFrame=level.getFrameCounter(),objectFrame=manager.getFrameCounter();
            int[] palette=palette();int[][] expected=new int[23][];
            var registry=com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
            com.openggf.game.rewind.CompositeSnapshot mid=null;
            for(int k=1;k<=22;k++) {
                if(k==9)mid=registry.capture();
                // Title_Screen reads no pad before its initialization; a held Start changes nothing.
                session.step(holdStart?start(input+k):neutral(input+k));
                assertEquals(GameMode.TITLE_SCREEN,session.loop().getCurrentGameMode(),"fade pass "+k);
                for(int i=0;i<64;i++)palette[i]=toBlack(palette[i]); // Pal_ToBlack / Pal_DecColor
                expected[k]=palette.clone();
                if(k<22) {
                    assertArrayEquals(palette,palette(),"Pal_FadeToBlack pass "+k);
                    assertTrue(session.loop().isTitleEntryShowingLevel(),"retained picture at pass "+k);
                    assertEquals(levelFrame,level.getFrameCounter(),"no level frame or timer advance, pass "+k);
                    assertEquals(objectFrame,manager.getFrameCounter(),"no object pass, pass "+k);
                }
                if(k==14) {
                    // Mid-fade restore to the pass-8 capture, then forward replay of the same passes.
                    registry.restore(mid);
                    for(int j=9;j<=14;j++){
                        session.step(neutral(input+j));
                        assertArrayEquals(expected[j],palette(),"replayed fade pass "+j);
                        assertEquals(GameMode.TITLE_SCREEN,session.loop().getCurrentGameMode());
                    }
                }
            }
            assertArrayEquals(new int[64],expected[22],"22 passes leave all 64 colours black");
            assertFalse(session.loop().isTitleEntryShowingLevel(),"the last pass hands over to the title");
        }
    }

    /** Every other way into TITLE_SCREEN draws the title provider: the startup title of a fresh S3K boot. */
    @org.junit.jupiter.api.Test
    void startupTitleIsNotATitleEntryFade() throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"sonic","","off",null,null,null,null,false,false);
        try(var session=new GameplayCaptureSession(settings)) {
            session.startAtTitle();session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            var title=session.loop().getTitleScreenProvider();
            for(int frame=0;frame<3000&&title.getState()!=com.openggf.game.TitleScreenProvider.State.ACTIVE;frame++) {
                session.step(neutral(frame));
                assertEquals(GameMode.TITLE_SCREEN,session.loop().getCurrentGameMode());
                assertFalse(session.loop().isTitleEntryShowingLevel(),"startup title frame "+frame);
            }
            assertEquals(com.openggf.game.TitleScreenProvider.State.ACTIVE,title.getState());
        }
    }

    private static int toBlack(int w){
        return (w&0xE)!=0?w-2:(w&0xE0)!=0?w-0x20:(w&0xE00)!=0?w-0x200:w;
    }
    private static int[] palette(){
        var level=GameServices.level().getCurrentLevel();int[] words=new int[64];
        for(int i=0;i<64;i++)words[i]=com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(level.getPalette(i/16).getColor(i%16));
        return words;
    }
    private static Bk2FrameInput neutral(int frame){return new Bk2FrameInput(frame,0,0,false,0,0,false,"neutral");}
    private static Bk2FrameInput start(int frame){return new Bk2FrameInput(frame,0,0,true,0,0,false,"Start");}
}
