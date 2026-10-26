package com.openggf.tools;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.*;
import com.openggf.game.dataselect.AbstractDataSelectProvider;
import com.openggf.game.dataselect.DataSelectActionType;
import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 8501S/6240S representative NG+: declared legitimate completed save, actual
 * title/data-select load. No emulator-state import or publication movie claim. */
@Isolated @RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kNewGamePlus {
    @TempDir Path saves;
    private final String oldRoot=System.getProperty(SavePaths.ROOT_PROPERTY);
    @AfterEach void cleanup() {
        SessionSaveRequests.flushPendingSaves();
        if(oldRoot==null)System.clearProperty(SavePaths.ROOT_PROPERTY);else System.setProperty(SavePaths.ROOT_PROPERTY,oldRoot);
        SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void titleLoadsSelectedAizRestartWithOwnedEmeraldProgress(boolean completed) throws Exception {
        System.setProperty(SavePaths.ROOT_PROPERTY,saves.toString());
        var all=List.of(0,1,2,3,4,5,6);
        var payload=new LinkedHashMap<String,Object>();
        payload.put("zone",0);payload.put("act",0);payload.put("mainCharacter","sonic");payload.put("sidekicks",List.of());
        payload.put("lives",5);payload.put("continues",0);payload.put("chaosEmeralds",completed?all:List.of());
        payload.put("superEmeralds",completed?all:List.of());payload.put("emeraldsConverted",completed);
        payload.put("emeraldStates",Collections.nCopies(7,completed?3:0));
        payload.put("clear",completed);payload.put("progressCode",completed?14:0);payload.put("clearState",completed?2:0);
        assertTrue(new S3kDataSelectProfile().isPayloadValid(payload));
        new SaveManager(saves).writeSlot("s3k",1,payload);
        var settings=new GameplayCaptureSession.Settings(320,"sonic","","off",null,null,null,null,false,false);
        try(var session=new GameplayCaptureSession(settings)) {
            session.startAtTitle();session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),0,0,settings);
            try(var input=MutatorGameplayCaptureTool.PresentationInput.install(session.loop())) {
                for(int i=0;i<1800&&session.loop().getCurrentGameMode()!=GameMode.DATA_SELECT;i++)
                    session.stepPresentation(i%20==10?new Bk2FrameInput(0,0,0,true,"title Start"):null,input);
                assertEquals(GameMode.DATA_SELECT,session.loop().getCurrentGameMode());
                for(int i=0;i<60;i++)session.stepPresentation(null,input);
                session.stepPresentation(new Bk2FrameInput(0,AbstractPlayableSprite.INPUT_RIGHT,0,false,"slot 1"),input);
                session.stepPresentation(null,input);
                var menu=assertInstanceOf(AbstractDataSelectProvider.class,session.loop().getDataSelectProvider());
                if(completed) {
                    // Cleared files default to the terminal icon. Cycle to AIZ
                    // through the production controller, not a destination write.
                    for(int i=0;i<20&&menu.getSessionController().currentClearRestartIndex()!=0;i++)
                        menu.getSessionController().cycleClearRestart(-1);
                }
                var choice=menu.getSessionController().confirmSelection();
                assertEquals(completed?DataSelectActionType.CLEAR_RESTART:DataSelectActionType.LOAD_SLOT,choice.type());assertEquals(0,choice.zone());assertEquals(0,choice.act());
                session.stepPresentation(new Bk2FrameInput(0,0,InputActionMasks.ACTION_A,false,"load AIZ"),input);
                for(int i=0;i<700&&session.loop().getCurrentGameMode()!=GameMode.LEVEL;i++)session.stepPresentation(null,input);
                assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
            }
            assertEquals(0,GameServices.level().getCurrentZone());assertEquals(0,GameServices.level().getCurrentAct());
            assertEquals(5,GameServices.gameState().getLives());
            assertEquals(Collections.nCopies(7,completed?3:0),GameServices.gameState().getS3kEmeraldStates());
            assertEquals(completed,GameServices.gameState().hasAllSuperEmeralds());
            assertEquals(1,SessionManager.getCurrentWorldSession().getSaveSessionContext().activeSlot().orElseThrow());
        }
    }
}
