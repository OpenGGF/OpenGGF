package com.openggf.game.sonic3k.events;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.*;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.difficulty.MarbleGardenBossRecipe;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled native optional-intake witness; real packed-owner fault composition belongs to root. */
class TestWestMgzRecipeBirth {
    @AfterEach void cleanup() { SessionManager.clear(); }

    @ParameterizedTest @ValueSource(strings={"sonic","knuckles"})
    void firstAbortPropagatesBeforeAnyCharacterBossAllocation(String character) throws Exception {
        Witness witness = new Witness(character);
        witness.module.abort = true;
        assertThrows(Declined.class, witness::enter);
        assertEquals(1, witness.module.samples);
        assertTrue(witness.manager.getActiveObjects().isEmpty());
        // Partial event flags belong to the discarded frame and host teardown.
        // Never tick the failed encounter again or synthesize native rollback.
        assertEquals(true, value(witness.events,"bossSpawned"));
    }

    @ParameterizedTest @ValueSource(strings={"sonic","knuckles"})
    void alreadyAbsentProviderKeepsTheStockCharacterEncounter(String character) throws Exception {
        Witness witness=new Witness(character); witness.module.recipe=null; witness.enter();
        assertEquals(1,witness.module.samples); assertEquals(1,witness.manager.getActiveObjects().size());
        assertEquals(false,value(witness.manager.getActiveObjects().iterator().next(),"counterCadence"));
        witness.module.recipe=MarbleGardenBossRecipe.COUNTER_CADENCE;
        witness.events.update(1,2);
        assertEquals(1,witness.module.samples); assertEquals(1,witness.manager.getActiveObjects().size());
    }

    @ParameterizedTest @ValueSource(strings={"sonic","knuckles"})
    void fullNativeSstDoesNotSwallowTheFirstProviderAbort(String character) throws Exception {
        Witness witness = new Witness(character); witness.module.abort = true;
        witness.manager.reserveAllButNFreeSlots(0);
        assertThrows(Declined.class, witness::enter);
        assertEquals(1, witness.module.samples);
        assertTrue(witness.manager.getActiveObjects().isEmpty());
    }

    private static final class Declined extends ObjectCallbackAbortException { Declined(){super("controlled optional first fault",null);} }
    private static final class Module extends Sonic3kGameModule {
        int samples; boolean abort; MarbleGardenBossRecipe recipe=MarbleGardenBossRecipe.COUNTER_CADENCE;
        @Override public <T>T getGameService(Class<T> type) {
            if(type==MarbleGardenBossRecipe.class){samples++;if(abort){abort=false;throw new Declined();}return type.cast(recipe);}
            return super.getGameService(type);
        }
    }
    private static final class Witness {
        final Module module=new Module(); final Sonic3kMGZEvents events=new Sonic3kMGZEvents(); final ObjectManager manager;
        Witness(String character) throws Exception {
            EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
            GameServices.configuration().resetToDefaults(); GameModuleRegistry.setCurrent(module); TestEnvironment.activeGameplayMode();
            GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,character);
            var main=new TestablePlayableSprite(character,(short)0x3A10,(short)0x680);
            GameServices.sprites().addSprite(main); GameServices.camera().setFocusedSprite(main);
            manager=new ObjectManager(new ArrayList<>(),new Sonic3kObjectRegistry(),module.getPlaneSwitcherObjectId(),module.getPlaneSwitcherConfig(),null);
            Field f=GameServices.level().getClass().getDeclaredField("objectManager");f.setAccessible(true);f.set(GameServices.level(),manager);
            events.init(1); GameServices.camera().setX((short)0x3A00);GameServices.camera().setY((short)(character.equals("knuckles")?0:0x600));
        }
        void enter(){events.update(1,0);GameServices.camera().setX((short)0x3C80);events.update(1,1);}
    }
    private static Object value(Object o,String name) throws Exception {for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
}
