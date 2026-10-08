package com.openggf.game.session;

import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.*;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.mutators.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Shared screen must keep long catalogues, native availability and LOAD edits honest. */
@Isolated
class TestMutatorCatalogueScreen {
    @TempDir Path temp;
    String oldRoot;
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        oldRoot=System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot",temp.resolve("player").toString());
    }
    @AfterEach void cleanup() {
        SessionManager.clear(); GameModuleRegistry.setCurrent(new Sonic2GameModule());
        if(oldRoot==null) System.clearProperty("openggf.saveRoot"); else System.setProperty("openggf.saveRoot",oldRoot);
    }

    @ParameterizedTest @ValueSource(strings={"s1","s2","s3k"})
    void longCatalogueRejectsUnavailableStageToggleAndRetainsGameSpecificTitle(String game) throws Exception {
        var world=open(game);var state=MutatorWorldAccess.state(world);
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,c->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GLFW_KEY_DOWN,false);press(screen,input,GLFW_KEY_ENTER,false);
        for(int i=0;i<10;i++) press(screen,input,GLFW_KEY_DOWN,false);
        press(screen,input,GLFW_KEY_RIGHT,false);
        assertEquals(game.equals("s3k"),state.requested().get("catalogue:bonus").enabled());
        assertEquals(11,state.definitions().size());
        if(!game.equals("s3k")) {
            var notice=MutatorConfigurationScreen.class.getDeclaredField("notice");notice.setAccessible(true);
            assertTrue(((String)notice.get(screen)).contains("unavailable"));
        }
        press(screen,input,GLFW_KEY_ESCAPE,false);
        assertEquals(TitleScreenProvider.State.ACTIVE,screen.getState());
        assertEquals(LevelInputOverlay.Command.NONE,screen.consumeCommand());
        var rows=MutatorConfigurationScreen.class.getDeclaredMethod("rows");rows.setAccessible(true);
        var first=((List<?>)rows.invoke(screen)).getFirst();var label=first.getClass().getDeclaredMethod("label");label.setAccessible(true);
        assertEquals("Start "+game,label.invoke(first));
    }

    @Test void resumeKeepsRemovalPendingAndOnlyFullRestartPublishesIt() {
        var world=open("s2");MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var state=MutatorWorldAccess.state(world);
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,c->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GameServices.configuration().getInt(com.openggf.configuration.SonicConfiguration.PAUSE_KEY),true);
        press(screen,input,GLFW_KEY_RIGHT,true);
        for(int i=0;i<11;i++) press(screen,input,GLFW_KEY_DOWN,true);
        press(screen,input,GLFW_KEY_ENTER,true);
        assertEquals(LevelInputOverlay.Command.RESUME,screen.consumeCommand());
        assertFalse(state.admitted().get("catalogue:monitors").enabled());
        assertTrue(state.pending().stream().anyMatch(p->p.key().equals("catalogue:monitors") && p.scope()==MutatorScope.LOAD));
        screen.commandQueued(false);
        press(screen,input,GameServices.configuration().getInt(com.openggf.configuration.SonicConfiguration.PAUSE_KEY),true);
        for(int i=0;i<12;i++) press(screen,input,GLFW_KEY_DOWN,true);
        press(screen,input,GLFW_KEY_ENTER,true);
        assertEquals(LevelInputOverlay.Command.FULL_RESTART,screen.consumeCommand());
        assertFalse(state.admitted().get("catalogue:monitors").enabled(),"Preview never publishes a LOAD edit");
        MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_RESTART);
        assertTrue(state.admitted().get("catalogue:monitors").enabled());
        assertTrue(state.pending().isEmpty());
    }

    private WorldSession open(String game) {
        GameModule root=switch(game){case "s1"->new Sonic1GameModule();case "s2"->new Sonic2GameModule();default->new Sonic3kGameModule();};
        var definitions=new ArrayList<OwnedMutator>();
        definitions.add(new OwnedMutator("catalogue",new MutatorDefinition("monitors","No Powerups","",MutatorScope.LOAD,MutatorScope.LOAD,
                List.of(),Set.of(MutatorCapability.MONITOR_FILTER),o->List.of(new MutatorPolicy.MonitorFilter(Set.of(com.openggf.game.mutators.MonitorContent.RINGS))))));
        for(int i=0;i<9;i++) definitions.add(new OwnedMutator("catalogue",new MutatorDefinition("row-"+i,"Row "+i,"",MutatorScope.LIVE,MutatorScope.LIVE,
                List.of(),Set.of(MutatorCapability.DRY_SONIC_GRAVITY),o->List.of(new MutatorPolicy.DrySonicGravity(100)))));
        definitions.add(new OwnedMutator("catalogue",new MutatorDefinition("bonus","No Bonus Stages","",MutatorScope.LIVE,MutatorScope.LIVE,
                List.of(),Set.of(MutatorCapability.NO_BONUS_STAGES),o->List.of(new MutatorPolicy.NoBonusStages()))));
        var faults=new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),owners->new ModStateSaveResult.Saved(),owners->{});
        var catalogue=new MutatorCatalog(definitions,faults);
        var resolved=new DelegatingGameModule(root,"catalogue:lab") {
            @Override public <T> T getGameService(Class<T> type) {
                if(type==WorldSessionPolicyProvider.class) return type.cast(new MutatorWorldProvider(catalogue));
                if(type==MutatorSupportProfile.class) return type.cast(new MutatorSupportProfile() {
                    public Set<MutatorCapability> capabilities(int z,int a) {
                        var all=EnumSet.allOf(MutatorCapability.class);if(!game.equals("s3k")) all.remove(MutatorCapability.NO_BONUS_STAGES);return Set.copyOf(all);
                    }
                    public boolean supportsPlayer(String key,boolean leader){return true;}
                    public String startLabel(){return "Start "+game;}
                });
                return super.getGameService(type);
            }
        };
        GameModuleRegistry.setCurrent(resolved);SessionManager.openGameplaySession(root,resolved,null);TestEnvironment.activeGameplayMode();
        return SessionManager.getCurrentWorldSession();
    }
    private static void press(MutatorConfigurationScreen screen,InputHandler input,int key,boolean overlay) {
        input.handleKeyEvent(key,GLFW_PRESS);if(overlay) screen.handleInput(input);else screen.update(input);
        input.update();input.handleKeyEvent(key,GLFW_RELEASE);input.update();
    }
}
