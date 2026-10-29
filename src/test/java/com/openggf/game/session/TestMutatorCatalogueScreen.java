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
            assertTrue(((String)notice.get(screen)).toLowerCase(java.util.Locale.ROOT).contains("unavailable"));
        }
        press(screen,input,GLFW_KEY_ESCAPE,false);
        assertEquals(TitleScreenProvider.State.ACTIVE,screen.getState());
        assertEquals(LevelInputOverlay.Command.NONE,screen.consumeCommand());
        var rows=MutatorConfigurationScreen.class.getDeclaredMethod("rows");rows.setAccessible(true);
        var first=((List<?>)rows.invoke(screen)).getFirst();var label=first.getClass().getDeclaredMethod("label");label.setAccessible(true);
        assertEquals("Start "+game,label.invoke(first));
    }

    @Test void backingOutKeepsTheOpeningRowAndUnavailableRowsNameTheirReason() throws Exception {
        var world=open("s2");var state=MutatorWorldAccess.state(world);
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,c->{});screen.initialize();
        var input=new InputHandler();
        var row=MutatorConfigurationScreen.class.getDeclaredField("row");row.setAccessible(true);
        for(int i=0;i<2;i++) press(screen,input,GLFW_KEY_DOWN,false);
        press(screen,input,GLFW_KEY_ENTER,false);
        press(screen,input,GLFW_KEY_ESCAPE,false);
        assertEquals(2,row.get(screen),"How to play keeps focus after Back");
        press(screen,input,GLFW_KEY_UP,false);press(screen,input,GLFW_KEY_ENTER,false);
        for(int i=0;i<3;i++) press(screen,input,GLFW_KEY_DOWN,false);
        press(screen,input,GLFW_KEY_ENTER,false);
        press(screen,input,GLFW_KEY_ESCAPE,false);
        assertEquals(3,row.get(screen),"an options page returns to the mutator that opened it");
        press(screen,input,GLFW_KEY_ESCAPE,false);
        assertEquals(1,row.get(screen),"the catalogue returns to Configure");

        var rows=MutatorConfigurationScreen.class.getDeclaredMethod("rows");rows.setAccessible(true);
        var rowType=Class.forName(MutatorConfigurationScreen.class.getName()+"$Row");
        var scope=MutatorConfigurationScreen.class.getDeclaredMethod("scope",rowType);scope.setAccessible(true);
        var value=MutatorConfigurationScreen.class.getDeclaredMethod("value",rowType);value.setAccessible(true);
        var editable=MutatorConfigurationScreen.class.getDeclaredMethod("editable",rowType);editable.setAccessible(true);
        var tag=MutatorConfigurationScreen.class.getDeclaredMethod("statusTag",rowType);tag.setAccessible(true);
        press(screen,input,GLFW_KEY_ENTER,false);
        var list=(List<?>)rows.invoke(screen);
        assertEquals("Applies at Start",scope.invoke(screen,list.getFirst()),
                "a LOAD edit before play is applied by Start");
        var bonus=list.get(10);
        assertEquals("Unavailable",value.invoke(screen,bonus));
        assertEquals("UNAVAILABLE",tag.invoke(screen,bonus));
        assertEquals(false,editable.invoke(screen,bonus),"an unavailable Off switch must not advertise change controls");
        assertEquals("Unavailable for this game or art profile.",scope.invoke(screen,bonus));
        state.requestEnabled("catalogue:bonus",true);
        assertEquals("On",value.invoke(screen,bonus),"a saved unavailable toggle still shows that it is on");
        assertEquals(true,editable.invoke(screen,bonus),"an unavailable saved On switch can still be cleared");
        assertEquals("Unavailable here. Change to switch off.",scope.invoke(screen,bonus));
        for(int i=0;i<10;i++) press(screen,input,GLFW_KEY_DOWN,false);
        press(screen,input,GLFW_KEY_RIGHT,false);
        assertFalse(state.requested().get("catalogue:bonus").enabled());
    }

    @Test void anOptionsPageNamesItsSharedBoundaryOnceAndItsSwitchRowReadsEnabled() throws Exception {
        var world=open("s2");
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,c->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GLFW_KEY_DOWN,false);press(screen,input,GLFW_KEY_ENTER,false); // Configure mutators
        for(int i=0;i<3;i++) press(screen,input,GLFW_KEY_DOWN,false);
        press(screen,input,GLFW_KEY_ENTER,false);
        var rows=MutatorConfigurationScreen.class.getDeclaredMethod("rows");rows.setAccessible(true);
        var shared=MutatorConfigurationScreen.class.getDeclaredMethod("sharedBoundary");shared.setAccessible(true);
        var height=MutatorConfigurationScreen.class.getDeclaredMethod("rowHeight");height.setAccessible(true);
        var list=(List<?>)rows.invoke(screen);
        var label=list.getFirst().getClass().getDeclaredMethod("label");label.setAccessible(true);
        assertEquals("Enabled",label.invoke(list.getFirst()),"the caption names the mutator; the switch row does not repeat it");
        assertEquals("Applies at Start",shared.invoke(screen),"every row applies at Start before play");
        assertEquals(15,height.invoke(screen),"with nothing row-specific to say, each option is one line");
    }

    @Test void theMutatorListNamesItsSharedTimingOnceAndHeldPlayHasNoOpaqueBackCap() throws Exception {
        var world=open("s2");
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,c->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GLFW_KEY_DOWN,false);press(screen,input,GLFW_KEY_ENTER,false); // Configure mutators
        var shared=MutatorConfigurationScreen.class.getDeclaredMethod("sharedBoundary");shared.setAccessible(true);
        assertEquals("Applies at Start",shared.invoke(screen),"the list names the timing its mutators share");
        var playing=MutatorConfigurationScreen.class.getDeclaredField("inGameplay");playing.setAccessible(true);playing.set(screen,true);
        var font=org.mockito.Mockito.mock(com.openggf.graphics.MenuPixelFont.class);
        var field=MutatorConfigurationScreen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
        var render=MutatorConfigurationScreen.class.getDeclaredMethod("renderPage",boolean.class);render.setAccessible(true);
        int oldWidth=GameServices.graphics().getProjectionWidth();
        try { for(int width:new int[]{320,400}) {
            GameServices.graphics().setProjectionWidth(width);org.mockito.Mockito.clearInvocations(font);render.invoke(screen,true);
            var texts=org.mockito.ArgumentCaptor.forClass(String.class);
            var ys=org.mockito.ArgumentCaptor.forClass(Integer.class);
            org.mockito.Mockito.verify(font,org.mockito.Mockito.atLeastOnce()).drawText(texts.capture(),org.mockito.ArgumentMatchers.anyInt(),ys.capture(),
                    org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat(),
                    org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat());
            String timingText=shared.invoke(screen).toString();
            assertTrue(timingText.startsWith("Applies at"),timingText);
            int band=0;
            for(int i=0;i<texts.getAllValues().size();i++) {
                String text=texts.getAllValues().get(i);
                if(!text.startsWith("Applies at")) continue;
                if(ys.getAllValues().get(i)==47) { assertEquals(timingText,text); band++; }
                // Rows speak only for an exception, such as a LOAD mutator's Restart or death.
                else assertNotEquals(timingText,text,"a row repeats the shared timing at "+width+": "+texts.getAllValues());
            }
            assertEquals(1,band,"the band names the shared timing once at "+width);
            assertFalse(texts.getAllValues().contains("HOLD"),"no opaque hold cap while play is held");
            var rowsMethod=MutatorConfigurationScreen.class.getDeclaredMethod("rows");rowsMethod.setAccessible(true);
            var labels=new ArrayList<Object>();
            for(Object row:(List<?>)rowsMethod.invoke(screen)) { var label=row.getClass().getDeclaredMethod("label");label.setAccessible(true);labels.add(label.invoke(row)); }
            assertTrue(labels.contains("Resume play"),"Resume is the way back to play: "+labels);
            // A row is two lines only when it has its own exception; the rest are one line.
            var perRow=java.util.Arrays.stream(MutatorConfigurationScreen.class.getDeclaredMethods())
                    .filter(m->m.getName().equals("rowHeight")&&m.getParameterCount()==1).findFirst().orElseThrow();
            perRow.setAccessible(true);
            var secondLine=java.util.Arrays.stream(MutatorConfigurationScreen.class.getDeclaredMethods())
                    .filter(m->m.getName().equals("secondLine")).findFirst().orElseThrow();
            secondLine.setAccessible(true);
            for(Object row:(List<?>)rowsMethod.invoke(screen)) {
                boolean two=(boolean)secondLine.invoke(screen,row);
                assertEquals(two?24:15,perRow.invoke(screen,row),"row height follows its own second line");
            }
        } } finally { GameServices.graphics().setProjectionWidth(oldWidth); }
    }

    @Test void longOptionsCaptionEndsBeforeTheSharedTiming() throws Exception {
        var definition=new OwnedMutator("catalogue",new MutatorDefinition("long","Violent Explosions Around You","",
                MutatorScope.LOAD,MutatorScope.LOAD,List.of(),Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                o->List.of(new MutatorPolicy.DrySonicGravity(100))));
        var world=open("s2",new Sonic2GameModule(),List.of(definition));
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,c->{});screen.initialize();
        var input=new InputHandler();press(screen,input,GLFW_KEY_DOWN,false);press(screen,input,GLFW_KEY_ENTER,false);
        press(screen,input,GLFW_KEY_ENTER,false);
        var playing=MutatorConfigurationScreen.class.getDeclaredField("inGameplay");playing.setAccessible(true);playing.set(screen,true);
        var font=org.mockito.Mockito.mock(com.openggf.graphics.MenuPixelFont.class);
        var field=MutatorConfigurationScreen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
        var render=MutatorConfigurationScreen.class.getDeclaredMethod("renderPage",boolean.class);render.setAccessible(true);
        int oldWidth=GameServices.graphics().getProjectionWidth();
        try { for(int width:new int[]{320,400}) {
            GameServices.graphics().setProjectionWidth(width);org.mockito.Mockito.clearInvocations(font);render.invoke(screen,false);
            var texts=org.mockito.ArgumentCaptor.forClass(String.class);
            var xs=org.mockito.ArgumentCaptor.forClass(Integer.class);
            var ys=org.mockito.ArgumentCaptor.forClass(Integer.class);
            org.mockito.Mockito.verify(font,org.mockito.Mockito.atLeastOnce()).drawText(texts.capture(),xs.capture(),ys.capture(),
                    org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat(),
                    org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat());
            String caption=null;int captionX=0,timingX=0;
            for(int i=0;i<texts.getAllValues().size();i++) {
                if(ys.getAllValues().get(i)!=47) continue;
                String text=texts.getAllValues().get(i);
                if(text.startsWith("VIOLENT")) {caption=text;captionX=xs.getAllValues().get(i);}
                if(text.startsWith("Applies at ")) timingX=xs.getAllValues().get(i);
            }
            assertNotNull(caption);assertTrue(timingX>0,"timing is drawn");
            assertTrue(captionX+MenuChrome.compactWidth(caption)<=timingX-8,"heading stays clear at "+width);
            if(width==320) assertTrue(caption.endsWith("..."),"a long heading has an honest fit cue");
        } } finally { GameServices.graphics().setProjectionWidth(oldWidth); }
    }

    @Test void hostFactoryValidatesNativeCuesAndPinsPreparedWorldLifetime() {
        assertThrows(IllegalStateException.class, () -> MutatorConfigurationScreen.forCurrentWorld(
                "Catalogue",null,0xCD,0xB5,0xBC,0xED));
        var first=open("s1");
        assertThrows(IllegalArgumentException.class, () -> MutatorConfigurationScreen.forCurrentWorld(
                "Catalogue",null,0,0xB5,0xCC,0xC1));
        assertThrows(IllegalArgumentException.class, () -> MutatorConfigurationScreen.forCurrentWorld(
                "Catalogue",null,0xCD,0xB5,256,0xC1));
        var old=MutatorConfigurationScreen.forCurrentWorld("Catalogue",null,0xCD,0xB5,0xCC,0xC1);
        old.initialize();
        var draft=MutatorWorldAccess.state(first).requested();
        open("s2");
        var input=new InputHandler();
        press(old,input,GLFW_KEY_DOWN,false);
        press(old,input,GLFW_KEY_ENTER,false);
        assertEquals(draft,MutatorWorldAccess.state(first).requested());
        assertEquals(LevelInputOverlay.Command.NONE,old.consumeCommand(),"Retired world never publishes into the new one");
        assertFalse(old.handleInput(input));
    }

    @Test void resumeKeepsRemovalPendingAndOnlyFullRestartPublishesIt() throws Exception {
        var world=open("s2");MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var state=MutatorWorldAccess.state(world);
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,c->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GameServices.configuration().getInt(com.openggf.configuration.SonicConfiguration.PAUSE_KEY),true);
        press(screen,input,GLFW_KEY_RIGHT,true);
        var rows=MutatorConfigurationScreen.class.getDeclaredMethod("rows");rows.setAccessible(true);
        var first=((List<?>)rows.invoke(screen)).getFirst();
        var rowType=first.getClass();
        var tag=MutatorConfigurationScreen.class.getDeclaredMethod("statusTag",rowType);tag.setAccessible(true);
        var guidance=MutatorConfigurationScreen.class.getDeclaredMethod("rowGuidance",rowType);guidance.setAccessible(true);
        assertEquals("RESTART NEEDED",tag.invoke(screen,first),"the draft switch must not imply immediate activation");
        assertEquals("Now Off",guidance.invoke(screen,first),"the row distinguishes the admitted value from its requested On switch");
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
        assertEquals("",tag.invoke(screen,first),"successful publication removes the pending tag");
        assertEquals("Applies at Restart or death",guidance.invoke(screen,first),"the next edit still names its LOAD boundary");
    }

    @Test void currentLeaderHeadGuidanceExplainsNativeFallbackWithoutChangingDraftOrEligibility() throws Exception {
        var world=open("s2");
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,c->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GameServices.configuration().getInt(com.openggf.configuration.SonicConfiguration.PAUSE_KEY),true);
        var reason=MutatorConfigurationScreen.class.getDeclaredMethod("leaderHeadPresentationReason");reason.setAccessible(true);
        var draft=MutatorWorldAccess.state(world).requested();
        var sprites=GameServices.sprites();
        var sonic=new com.openggf.sprites.playable.Sonic("leader",(short)32,(short)48);
        sprites.addSprite(sonic);
        sonic.setSuperSonic(true);
        assertEquals("",reason.invoke(screen),"a powered flag alone does not reject reviewed art");
        sonic.setSuperSonic(false);
        var art=new com.openggf.sprites.art.SpriteArtSet(new com.openggf.level.Pattern[0],List.of(),List.of(),
                0,com.openggf.graphics.PatternAtlasRange.SIDEKICK_BANKS.base(),0,1,null,null);
        sonic.setSpriteRenderer(new com.openggf.sprites.render.PlayerSpriteRenderer(art,GameServices.graphics()));
        assertTrue(((String)reason.invoke(screen)).contains("no reviewed head mask"));
        sprites.addSprite(new com.openggf.sprites.playable.Tails("leader",(short)32,(short)48));
        assertTrue(((String)reason.invoke(screen)).contains("keeps native head size"));
        assertEquals(draft,MutatorWorldAccess.state(world).requested());
        assertTrue(world.resolvedGameModule().getGameService(MutatorSupportProfile.class)
                .capabilities(0,0).contains(MutatorCapability.BIG_HEAD));
    }

    @Test void titleHeadGuidanceDoesNotBorrowAnExistingGameplayRoster() throws Exception {
        var world=open("s2");
        GameServices.sprites().addSprite(new com.openggf.sprites.playable.Tails("leader",(short)32,(short)48));
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,c->{});screen.initialize();
        var reason=MutatorConfigurationScreen.class.getDeclaredMethod("leaderHeadPresentationReason");reason.setAccessible(true);
        assertEquals("",reason.invoke(screen));
    }

    @Test void allAfflictsCheckboxesFitAndCanBeChangedIndependently() throws Exception {
        var options = new ArrayList<MutatorOption>();
        options.add(new MutatorOption.IntegerSlider("percent", "Head size", "", MutatorScope.LIVE, 150, 100, 200, 1, "%"));
        options.add(new MutatorOption.Checkbox("rings", "Scale with rings", "", MutatorScope.LIVE, false));
        for (String target : List.of("player", "sidekicks", "badniks", "bosses"))
            options.add(new MutatorOption.Checkbox(target, Character.toUpperCase(target.charAt(0))+target.substring(1),
                    "Choose the affected characters.", MutatorScope.LIVE, target.equals("player"), "Afflicts:"));
        var definition = new MutatorDefinition("head", "Big Head Mode", "", MutatorScope.LIVE, MutatorScope.LIVE,
                options, Set.of(MutatorCapability.BIG_HEAD), values -> List.of(new MutatorPolicy.BigHead(150, Set.of())));
        var world = open("s2", new Sonic2GameModule(), List.of(new OwnedMutator("catalogue", definition)));
        var screen = new MutatorConfigurationScreen(world, "Catalogue", null, cue -> {}); screen.initialize();
        var input = new InputHandler();
        press(screen, input, GLFW_KEY_DOWN, false); press(screen, input, GLFW_KEY_ENTER, false);
        press(screen, input, GLFW_KEY_ENTER, false);
        var rows = MutatorConfigurationScreen.class.getDeclaredMethod("rows"); rows.setAccessible(true);
        var list = (List<?>) rows.invoke(screen);
        assertEquals(8, list.size());
        var capacity = MutatorConfigurationScreen.class.getDeclaredMethod("capacity"); capacity.setAccessible(true);
        assertEquals(7, capacity.invoke(screen), "enable, two settings and all four target checkboxes fit together");
        var rowY = MutatorConfigurationScreen.class.getDeclaredMethod("rowY", List.class, int.class, int.class, int.class);
        rowY.setAccessible(true);
        assertTrue((Integer) rowY.invoke(screen, list, 0, 6, 58) + 15 < 179, "Bosses stays above the details panel");
        for (int i=0;i<3;i++) press(screen, input, GLFW_KEY_DOWN, false);
        press(screen, input, GLFW_KEY_ENTER, false); // Player off.
        for (int i=0;i<3;i++) { press(screen, input, GLFW_KEY_DOWN, false); press(screen, input, GLFW_KEY_ENTER, false); }
        assertEquals(Map.of("percent",150,"rings",false,"player",false,"sidekicks",true,"badniks",true,"bosses",true),
                MutatorWorldAccess.state(world).requested().get("catalogue:head").options());
        press(screen,input,GLFW_KEY_DOWN,false);press(screen,input,GLFW_KEY_ENTER,false);
        assertEquals(TitleScreenProvider.State.ACTIVE,screen.getState(),"Back returns to the catalogue");
    }

    @Test void multipleCheckboxGroupsKeepFocusAndPointerRowsAboveDetails() throws Exception {
        var options=new ArrayList<MutatorOption>();
        for(int i=0;i<9;i++) options.add(new MutatorOption.Checkbox("target-"+i,"Target "+i,"Help",
                MutatorScope.LIVE,false,"Group "+i/2));
        var definition=new MutatorDefinition("head","Grouped targets","",MutatorScope.LIVE,MutatorScope.LIVE,
                options,Set.of(MutatorCapability.BIG_HEAD),values->List.of(new MutatorPolicy.BigHead(150,Set.of())));
        var world=open("s2",new Sonic2GameModule(),List.of(new OwnedMutator("catalogue",definition)));
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,cue->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GLFW_KEY_DOWN,false);press(screen,input,GLFW_KEY_ENTER,false);
        press(screen,input,GLFW_KEY_ENTER,false);
        var rows=MutatorConfigurationScreen.class.getDeclaredMethod("rows");rows.setAccessible(true);
        var first=MutatorConfigurationScreen.class.getDeclaredMethod("firstVisibleRow");first.setAccessible(true);
        var count=MutatorConfigurationScreen.class.getDeclaredMethod("visibleCount",List.class,int.class);count.setAccessible(true);
        var rowY=MutatorConfigurationScreen.class.getDeclaredMethod("rowY",List.class,int.class,int.class,int.class);rowY.setAccessible(true);
        var row=MutatorConfigurationScreen.class.getDeclaredField("row");row.setAccessible(true);
        for(int index=0;index<11;index++) {
            var visible=(List<?>)rows.invoke(screen);int from=(Integer)first.invoke(screen),size=(Integer)count.invoke(screen,visible,from);
            int focused=(Integer)row.get(screen);
            assertTrue(focused>=from&&focused<from+size,"selected row must be visible");
            assertTrue((Integer)rowY.invoke(screen,visible,from,from+size-1,58)+15<=176,
                    "group labels cannot push rows into the details panel");
            press(screen,input,GLFW_KEY_DOWN,false);
        }
    }

    @Test void detailsRecoverCreatorOptionAndErrorTailsWithoutEditingOrApplying() throws Exception {
        String name="Creator supplied mutator name "+"long ".repeat(6)+"NAMETAIL";
        String description="The complete creator explanation must remain reachable. ".repeat(12)+"DESCRIPTIONTAIL";
        String label="An option with a deliberately long caption "+"wide ".repeat(4)+"LABELTAIL";
        String help="Read this option before changing it. ".repeat(10)+"OPTIONHELPTAIL";
        String token="a".repeat(95)+"choicetail";
        var option=new MutatorOption.Choice("choice",label,help,MutatorScope.LIVE,token,List.of(token,"other"));
        var definition=new MutatorDefinition("reader",name,description,MutatorScope.LIVE,MutatorScope.LIVE,
                List.of(option),Set.of(MutatorCapability.DRY_SONIC_GRAVITY),o->List.of(new MutatorPolicy.DrySonicGravity(100)));
        var world=open("s2",new Sonic2GameModule(),List.of(new OwnedMutator("creator-identity-tail",definition)));
        var settings=MutatorWorldAccess.state(world);
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,cue->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GLFW_KEY_DOWN,false);press(screen,input,GLFW_KEY_ENTER,false);
        press(screen,input,GLFW_KEY_ENTER,false);press(screen,input,GLFW_KEY_DOWN,false);
        String error="The save or admission failure requires all recovery instructions. ".repeat(10)+"ERRORTAIL";
        var status=MutatorConfigurationScreen.class.getDeclaredField("status");status.setAccessible(true);status.set(screen,error);
        var control=MutatorConfigurationScreen.class.getDeclaredMethod("detailsControl");control.setAccessible(true);
        assertTrue(((String)control.invoke(screen)).contains("F1"),"the advertised reader uses the live Details binding");
        var draft=settings.requested();var admitted=settings.admitted();
        var detailField=MutatorConfigurationScreen.class.getDeclaredField("details");detailField.setAccessible(true);
        for(int width:List.of(320,640)) {
            // F1 owns the entire frame, including a simultaneous edit and confirm.
            input.handleKeyEvent(GLFW_KEY_F1,GLFW_PRESS);input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_ENTER,GLFW_PRESS);screen.update(input);
            input.update();
            for(int key:List.of(GLFW_KEY_F1,GLFW_KEY_RIGHT,GLFW_KEY_ENTER))input.handleKeyEvent(key,GLFW_RELEASE);
            input.update();
            var reader=(MenuDetailsScreen)detailField.get(screen);assertNotNull(reader);
            assertReaderReaches(reader,width,List.of(name,description,label,help,"A"+token.substring(1),error,
                    "creator-identity-tail","Applies at Start"));
            for(int i=0;i<20;i++)press(screen,input,GLFW_KEY_RIGHT,false);
            assertEquals(draft,settings.requested());assertEquals(admitted,settings.admitted());
            press(screen,input,GLFW_KEY_ENTER,false);
            assertNull(detailField.get(screen),"reader confirm only closes Back");
            assertEquals(draft,settings.requested());assertEquals(admitted,settings.admitted());
            assertEquals(LevelInputOverlay.Command.NONE,screen.consumeCommand());
            assertEquals(TitleScreenProvider.State.ACTIVE,screen.getState());
        }
    }

    @Test void readingResumeCannotPublishPendingChangesOrResumePlay() throws Exception {
        var world=open("s2");MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var settings=MutatorWorldAccess.state(world);
        var screen=new MutatorConfigurationScreen(world,"Catalogue",null,cue->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GameServices.configuration().getInt(com.openggf.configuration.SonicConfiguration.PAUSE_KEY),true);
        press(screen,input,GLFW_KEY_RIGHT,true);
        for(int i=0;i<11;i++)press(screen,input,GLFW_KEY_DOWN,true);
        var requested=settings.requested();var admitted=settings.admitted();
        press(screen,input,GLFW_KEY_F1,true);
        for(int key:List.of(GLFW_KEY_DOWN,GLFW_KEY_RIGHT,GLFW_KEY_LEFT,GLFW_KEY_UP,GLFW_KEY_ENTER))press(screen,input,key,true);
        assertEquals(LevelInputOverlay.Command.NONE,screen.consumeCommand());
        assertTrue(screen.pausesGameplay());assertEquals(requested,settings.requested());assertEquals(admitted,settings.admitted());
        assertFalse(settings.pending().isEmpty(),"reading leaves the pending LOAD edit intact");
    }

    @Test void helpWrapsWholeSentencesBeforePagingAndEveryPageCanBeRead() throws Exception {
        var screen=new MutatorConfigurationScreen(open("s2"),"Catalogue",null,cue->{});screen.initialize();
        var input=new InputHandler();
        press(screen,input,GLFW_KEY_DOWN,false);press(screen,input,GLFW_KEY_DOWN,false);press(screen,input,GLFW_KEY_ENTER,false);
        var old=com.openggf.graphics.text.HostTextContext.current();
        var oldLocale=com.openggf.i18n.UiMessages.locale().toLanguageTag();
        try {
            for(String language:List.of("en","bn")) {
                com.openggf.i18n.UiMessages.selectLanguage(language);
                var sections=MutatorConfigurationScreen.class.getDeclaredMethod("helpSections");sections.setAccessible(true);
                var source=new ArrayList<String>();for(var section:(String[][])sections.invoke(screen))source.addAll(List.of(section));
                var linesMethod=MutatorConfigurationScreen.class.getDeclaredMethod("helpLines",int.class);linesMethod.setAccessible(true);
                var panel=MutatorConfigurationScreen.class.getDeclaredMethod("panelWidth");panel.setAccessible(true);
                int width=(Integer)panel.invoke(screen);
                var lines=(List<?>)linesMethod.invoke(screen,width);
                var expected=new ArrayList<String>();
                for(var line:lines) {var text=line.getClass().getDeclaredMethod("text");text.setAccessible(true);expected.add((String)text.invoke(line));}
                assertEquals(String.join("",source).replaceAll("\\s+",""),String.join("",expected).replaceAll("\\s+",""));
                assertTrue(lines.size()>=source.size(),"Help keeps every original section line");
                var draws=new HashSet<String>();
                var font=org.mockito.Mockito.mock(com.openggf.graphics.MenuPixelFont.class);
                org.mockito.Mockito.doAnswer(call->{draws.add(call.getArgument(0,String.class));return null;}).when(font)
                        .drawText(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),
                                org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat(),
                                org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat());
                var fontField=MutatorConfigurationScreen.class.getDeclaredField("font");fontField.setAccessible(true);fontField.set(screen,font);
                var render=MutatorConfigurationScreen.class.getDeclaredMethod("renderHelp",int.class,int.class);render.setAccessible(true);
                var pageField=MutatorConfigurationScreen.class.getDeclaredField("helpPage");pageField.setAccessible(true);pageField.set(screen,0);
                var pages=MutatorConfigurationScreen.class.getDeclaredMethod("helpPageCount");pages.setAccessible(true);
                int count=(Integer)pages.invoke(screen);assertTrue(count>=1);
                for(int page=0;page<count;page++) {render.invoke(screen,10,width);press(screen,input,GLFW_KEY_RIGHT,false);}
                assertEquals(0,pageField.get(screen),"page count is available before rendering the next page");
                assertTrue(draws.containsAll(expected),"every wrapped Help line must actually be rendered without an ellipsis");
            }
        } finally {com.openggf.i18n.UiMessages.selectLanguage(oldLocale);com.openggf.graphics.text.HostTextContext.activate(old);}
        assertEquals(LevelInputOverlay.Command.NONE,screen.consumeCommand());
    }

    @Test void actionRowsUseTheirEntireWidthWithoutReservingAnAbsentValue() throws Exception {
        var screen=new MutatorConfigurationScreen(open("s2"),"Catalogue",null,cue->{});screen.initialize();
        var rowType=Class.forName(MutatorConfigurationScreen.class.getName()+"$Row");
        var actionType=Class.forName(MutatorConfigurationScreen.class.getName()+"$Action");
        var constructor=rowType.getDeclaredConstructor(String.class,String.class,String.class,actionType);constructor.setAccessible(true);
        Object back=Arrays.stream(actionType.getEnumConstants()).filter(a->a.toString().equals("BACK")).findFirst().orElseThrow();
        String label="A complete action label uses the row width";
        var item=constructor.newInstance(null,null,label,back);
        var font=org.mockito.Mockito.mock(com.openggf.graphics.MenuPixelFont.class);
        var seen=new HashSet<String>();
        org.mockito.Mockito.doAnswer(call->{seen.add(call.getArgument(0,String.class));return null;}).when(font)
                .drawText(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat(),
                        org.mockito.ArgumentMatchers.anyFloat(),org.mockito.ArgumentMatchers.anyFloat());
        var fontField=MutatorConfigurationScreen.class.getDeclaredField("font");fontField.setAccessible(true);fontField.set(screen,font);
        var age=MutatorConfigurationScreen.class.getDeclaredField("pageAge");age.setAccessible(true);age.set(screen,30);
        var render=MutatorConfigurationScreen.class.getDeclaredMethod("renderRows",List.class,int.class,int.class,int.class,int.class,boolean.class);
        render.setAccessible(true);render.invoke(screen,List.of(item),10,160,286,13,false);
        assertTrue(seen.contains(label),"the complete action fits its row; a nonexistent value must not force paging");
    }

    private static void assertReaderReaches(MenuDetailsScreen reader,int width,List<String> source) throws Exception {
        var seen=new HashSet<String>();
        var font=new com.openggf.graphics.PixelFont(){
            @Override public void drawText(String text,int x,int y,float scale,float r,float g,float b,float a){seen.add(text);}
        };
        reader.render(font,width);
        var field=MenuDetailsScreen.class.getDeclaredField("lines");field.setAccessible(true);
        var lines=(List<?>)field.get(reader);var expected=new ArrayList<String>();
        for(var line:lines) {var text=line.getClass().getDeclaredMethod("text");text.setAccessible(true);expected.add((String)text.invoke(line));}
        String complete=String.join("",expected).replaceAll("\\s+","");
        for(String text:source)assertTrue(complete.contains(text.replaceAll("\\s+","")),"reader lost source tail: "+text);
        // Single-line scrolling witnesses every layout row, rather than jumping over short tails.
        for(int i=0;i<lines.size()+20;i++) {
            var down=new InputHandler();down.handleKeyEvent(GLFW_KEY_DOWN,GLFW_PRESS);down.refreshLogicalSnapshot();reader.update(down);
            reader.render(font,width);
        }
        // Blank paragraphs reserve spacing; MenuChrome deliberately issues no draw call for them.
        var missing=expected.stream().filter(text->!text.isEmpty()).filter(text->!seen.contains(text)).toList();
        assertTrue(missing.isEmpty(),"reader rows never drawn in full: "+missing);
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
        return open(game, root, definitions);
    }
    private WorldSession open(String game, GameModule root, List<OwnedMutator> definitions) {
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
                    public boolean supportsPlayer(MutatorCapability capability,String key,boolean leader) {
                        return capability!=MutatorCapability.BIG_HEAD || key.equals("sonic");
                    }
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
