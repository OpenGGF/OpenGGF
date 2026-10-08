package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.*;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated
class TestOwnedCharacterRegistry {
    @TempDir Path temp;
    private static final String OWNER="verified-owner";
    private static final CharacterKey MOD=CharacterKey.mod(OWNER,"hero");
    @BeforeEach void setup() {
        SessionManager.clear(); GameModuleRegistry.reset(); TestEnvironment.resetAll(); SessionManager.clear();
        GameModuleRegistry.setCurrent(new com.openggf.game.sonic2.Sonic2GameModule());
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void teardown() { SessionManager.clear(); GameModuleRegistry.reset(); TestEnvironment.resetAll(); }

    private static final class ProbeSprite extends AbstractPlayableSprite {
        final CharacterKey key;
        String failingHook;
        ProbeSprite(CharacterKey key) { super("probe",(short)0,(short)0); this.key=key; }
        @Override public CharacterKey characterKey() { return key; }
        @Override public void draw() { }
        @Override protected void createSensorLines() { }
        @Override public void defineSpeeds() {
            runAccel=12;runDecel=128;friction=12;max=1536;jump=1664;
            slopeRunning=32;slopeRollingDown=80;slopeRollingUp=20;
            rollDecel=32;minStartRollSpeed=128;minRollSpeed=128;maxRoll=4096;rollHeight=28;runHeight=38;
        }
        @Override protected boolean onAbilityActivate(boolean up,boolean down,boolean left,boolean right) {
            failHook("ability"); return false;
        }
        @Override protected void onLanded() { failHook("landing"); }
        @Override protected void onLevelReset() { failHook("reset"); }
        private void failHook(String hook) { if (hook.equals(failingHook)) throw new IllegalStateException(hook); }
    }
    private static CharacterDefinition definition(CharacterKey key,CharacterDefinition.PlayableFactory factory) {
        return new CharacterDefinition(key,"Hero",factory,null,PlayerCharacter.SONIC_ALONE,
                SecondaryAbility.NONE,false,code->SpriteArtSet.EMPTY);
    }
    private static Fixture fixture() {
        var disabled=new LinkedHashSet<String>();
        var boundary=new ModFaultBoundary(Map.of("dependent",Set.of(OWNER)),new ModRuntimeFindingStore(),
                owners->new ModStateSaveResult.Saved(),disabled::addAll);
        return new Fixture(boundary,disabled);
    }
    private record Fixture(ModFaultBoundary boundary,Set<String> disabled) { }
    private CharacterDefinition mapped(CharacterDefinition definition,Fixture fixture) {
        return OwnedCharacterRegistry.bind(OWNER,PlayableCharacterRegistry.empty().register(definition.key(),definition),
                null,fixture.boundary()).find(definition.key()).orElseThrow();
    }
    private static void assertOwner(Fixture fixture,ModFaultBoundary.CallbackAborted failure,String cause) {
        assertEquals(OWNER,failure.owner()); assertEquals(cause,failure.getCause().getMessage());
        assertEquals(Set.of(OWNER,"dependent"),fixture.disabled());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void freshModAndSemanticBuiltinReplacementFactoriesUseVerifiedOwner(boolean builtin) {
        CharacterKey key=builtin ? CharacterKey.SONIC : MOD;
        var fixture=fixture();
        var definition=mapped(definition(key,(code,x,y)-> { throw new IllegalStateException("factory"); }),fixture);
        assertEquals(key,definition.key());
        assertOwner(fixture,assertThrows(ModFaultBoundary.CallbackAborted.class,
                ()->definition.spriteFactory().create(key.persisted(),0,0)),"factory");
    }

    @ParameterizedTest @ValueSource(strings={"ability","landing","reset"})
    void returnedSpritesKeepOwnerThroughActualRuntimeHooks(String hook) {
        var fixture=fixture();
        var definition=mapped(definition(CharacterKey.SONIC,(code,x,y)->new ProbeSprite(CharacterKey.SONIC)),fixture);
        var sprite=(ProbeSprite)definition.spriteFactory().create("sonic",0,0);
        sprite.failingHook=hook;
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()-> {
            switch (hook) {
                case "ability" -> CharacterRuntimeHooks.activateAbility(sprite);
                case "landing" -> { sprite.setAir(true);sprite.setAir(false); }
                case "reset" -> sprite.resetState();
            }
        });
        assertOwner(fixture,failure,hook);
    }

    @Test void cachedSpriteFromOutsideTheCurrentConstructionScopeCannotEscapeRuntimeOwnership() {
        var cached=new ProbeSprite(CharacterKey.SONIC); var fixture=fixture();
        var definition=mapped(definition(CharacterKey.SONIC,(code,x,y)->cached),fixture);
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->definition.spriteFactory().create("sonic",0,0));
        assertEquals(OWNER,failure.owner());
        assertTrue(failure.getCause().getMessage().contains("current owner scope"));
    }

    @Test void anEarlierFactoryResultCannotBeReusedForAnotherPlayerConstruction() {
        ProbeSprite[] cached=new ProbeSprite[1];var fixture=fixture();
        var definition=mapped(definition(CharacterKey.SONIC,(code,x,y)-> {
            if (cached[0]==null) cached[0]=new ProbeSprite(CharacterKey.SONIC);
            return cached[0];
        }),fixture);
        var first=definition.spriteFactory().create("sonic",0,0);
        assertSame(cached[0],first);
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,
                ()->definition.spriteFactory().create("sonic_p2",20,0));
        assertEquals(OWNER,failure.owner());
        assertTrue(failure.getCause().getMessage().contains("current owner scope"));
    }

    @ParameterizedTest @ValueSource(strings={"art","palette","respawn-factory","respawn-runtime"})
    void latentArtAndRespawnCallbacksRemainOwnedAfterRegistryPublication(String callback) {
        var fixture=fixture();
        var raw=new CharacterDefinition(CharacterKey.SONIC,"Hero",(code,x,y)->new ProbeSprite(CharacterKey.SONIC),
                controller-> {
                    if (callback.equals("respawn-factory")) throw new IllegalStateException(callback);
                    return new SidekickRespawnStrategy() {
                        public boolean beginApproach(AbstractPlayableSprite sidekick,AbstractPlayableSprite leader) {
                            throw new IllegalStateException(callback);
                        }
                        public boolean updateApproaching(AbstractPlayableSprite sidekick,AbstractPlayableSprite leader,int frame) { return false; }
                    };
                },PlayerCharacter.SONIC_ALONE,SecondaryAbility.NONE,false,
                code->{ if(callback.equals("art"))throw new IllegalStateException(callback);return SpriteArtSet.EMPTY; },
                code->{ throw new IllegalStateException(callback); });
        var definition=mapped(raw,fixture);
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()-> {
            switch(callback) {
                case "art" -> definition.artSupplier().load("sonic");
                case "palette" -> definition.paletteSupplier().load("sonic");
                case "respawn-factory" -> definition.respawnStrategyFactory().create(null);
                case "respawn-runtime" -> definition.respawnStrategyFactory().create(null).beginApproach(null,null);
            }
        });
        assertOwner(fixture,failure,callback);
    }

    @ParameterizedTest @ValueSource(strings={"sonic","tails","knuckles"})
    void exactNativeRespawnStrategiesKeepTheirConcreteRuntimeContracts(String nativeCharacter) {
        var controller=mock(SidekickCpuController.class);
        SidekickRespawnStrategy nativeStrategy=switch(nativeCharacter) {
            case "tails" -> new TailsRespawnStrategy(controller);
            case "knuckles" -> new KnucklesRespawnStrategy(controller);
            default -> new SonicRespawnStrategy(controller);
        };
        var raw=new CharacterDefinition(CharacterKey.SONIC,"Hero",(code,x,y)->new ProbeSprite(CharacterKey.SONIC),
                ignored->nativeStrategy,PlayerCharacter.SONIC_ALONE,SecondaryAbility.NONE,false,code->SpriteArtSet.EMPTY);
        assertSame(nativeStrategy,mapped(raw,fixture()).respawnStrategyFactory().create(controller));
    }

    @Test void unchangedAndCopiedInheritedDefinitionsRetainEarlierIdentityAndFaultAttribution() {
        var earlier=fixture();
        var key=CharacterKey.mod("earlier","hero");
        var raw=PlayableCharacterRegistry.empty().register(key,definition(key,(code,x,y)-> { throw new IllegalStateException("earlier factory"); }));
        var inherited=OwnedCharacterRegistry.bind("earlier",raw,null,earlier.boundary());
        var current=fixture();
        assertSame(inherited,OwnedCharacterRegistry.bind(OWNER,inherited,inherited,current.boundary()));
        var copied=PlayableCharacterRegistry.empty().register(key,inherited.find(key).orElseThrow())
                .register(MOD,definition(MOD,(code,x,y)->new ProbeSprite(MOD)));
        var result=OwnedCharacterRegistry.bind(OWNER,copied,inherited,current.boundary());
        assertSame(inherited.find(key).orElseThrow(),result.find(key).orElseThrow());
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,
                ()->result.find(key).orElseThrow().spriteFactory().create(key.persisted(),0,0));
        assertEquals("earlier",failure.owner());assertEquals(Set.of("earlier"),earlier.disabled());
        assertTrue(current.disabled().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void capturedEarlierBuiltinDefinitionOrCopiedBoundCallbacksCannotBeRetagged(boolean copied) {
        var raw=PlayableCharacterRegistry.empty().register(CharacterKey.SONIC,
                definition(CharacterKey.SONIC,(code,x,y)->new ProbeSprite(CharacterKey.SONIC)));
        var earlier=OwnedCharacterRegistry.bind("earlier",raw,null,fixture().boundary()).find(CharacterKey.SONIC).orElseThrow();
        var captured=copied ? new CharacterDefinition(earlier.key(),earlier.displayName(),earlier.spriteFactory(),
                earlier.respawnStrategyFactory(),earlier.behavesLike(),earlier.secondaryAbility(),earlier.supportsSuperForm(),
                earlier.artSupplier(),earlier.paletteSupplier()) : earlier;
        var current=fixture();
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->mapped(captured,current));
        assertEquals(OWNER,failure.owner());
        assertTrue(failure.getCause().getMessage().contains("already owned"));
    }

    @Test void freshForeignKeyAndOversizedPublicationFailBeforeAnyFactoryRuns() {
        var fixture=fixture();
        var foreign=CharacterKey.mod("foreign","hero");
        assertThrows(ModFaultBoundary.CallbackAborted.class,()->mapped(definition(foreign,(code,x,y)->fail("factory ran")),fixture));
        var oversized=mock(PlayableCharacterRegistry.class);
        @SuppressWarnings("unchecked") Map<CharacterKey,CharacterDefinition> definitions=mock(Map.class);
        when(definitions.size()).thenReturn(com.openggf.io.ModInputLimits.production().maxCollectionEntries()+1);
        when(oversized.definitions()).thenReturn(definitions);
        assertThrows(ModFaultBoundary.CallbackAborted.class,()->OwnedCharacterRegistry.bind(OWNER,oversized,null,fixture().boundary()));
    }

    @Test void standaloneCachesRegistryProjectionAndRealTeamBootstrapOwnsBuiltinReplacementRuntime() {
        var fixture=fixture();
        var raw=PlayableCharacterRegistry.empty().register(CharacterKey.SONIC,
                definition(CharacterKey.SONIC,(code,x,y)->new ProbeSprite(CharacterKey.SONIC)));
        GameModule delegate=mock(GameModule.class);
        when(delegate.getPlayableCharacterRegistry()).thenReturn(raw);
        when(delegate.getPhysicsProvider()).thenReturn(new com.openggf.game.sonic2.Sonic2GameModule().getPhysicsProvider());
        var owned=OwnerAwareStandaloneModule.wrap(OWNER,delegate,fixture.boundary(),Map.of());
        var second=OwnerAwareStandaloneModule.wrap(OWNER,delegate,fixture().boundary(),Map.of());
        assertSame(owned.getPlayableCharacterRegistry(),owned.getPlayableCharacterRegistry());
        assertNotSame(owned.getPlayableCharacterRegistry().find(CharacterKey.SONIC).orElseThrow(),
                second.getPlayableCharacterRegistry().find(CharacterKey.SONIC).orElseThrow());
        var config=SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        var sprites=new SpriteManager(config);
        var team=GameplayTeamBootstrap.registerActiveTeam(owned,sprites,config);
        var sprite=(ProbeSprite)team.mainSprite();sprite.failingHook="ability";
        assertOwner(fixture,assertThrows(ModFaultBoundary.CallbackAborted.class,
                ()->CharacterRuntimeHooks.activateAbility(sprite)),"ability");
    }

    public interface RegistryBinder {
        PlayableCharacterRegistry bind(String owner,PlayableCharacterRegistry registry,
                PlayableCharacterRegistry inherited,ModFaultBoundary boundary);
    }
    public interface ScopeCaller {
        AbstractPlayableSprite call(CharacterKey key,CharacterConstructionScope.CallbackInvoker invoker,
                java.util.function.Supplier<AbstractPlayableSprite> factory);
    }
    public static final class CreatorProbe {
        public static PlayableCharacterRegistry bind(PlayableCharacterRegistry registry,ModFaultBoundary boundary) {
            return OwnedCharacterRegistry.bind("forged-owner",registry,null,boundary);
        }
        public static RegistryBinder binder() { return OwnedCharacterRegistry::bind; }
        public static AbstractPlayableSprite scopedFactory(CharacterDefinition definition) {
            return CharacterConstructionScope.call(CharacterKey.SONIC,callback->callback.get(),
                    ()->definition.spriteFactory().create("sonic",0,0));
        }
        public static ScopeCaller scopeCaller() { return CharacterConstructionScope::call; }
        public static CharacterDefinition definition() {
            return new CharacterDefinition(CharacterKey.SONIC,"Creator template",
                    (code,x,y)-> { throw new IllegalStateException("creator factory"); },null,
                    PlayerCharacter.SONIC_ALONE,SecondaryAbility.NONE,false,code->SpriteArtSet.EMPTY);
        }
    }
    private Path creatorProbeJar() throws Exception {
        String name=CreatorProbe.class.getName();Path jar=temp.resolve("registry-probe.jar");
        try(var input=getClass().getResourceAsStream("/"+name.replace('.','/')+".class");
            var output=new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.jar.JarEntry(name.replace('.','/')+".class"));output.write(input.readAllBytes());output.closeEntry();
        }
        return jar;
    }
    private ClassLoader creatorProbeParent() {
        String name=CreatorProbe.class.getName();
        return new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String requested,boolean resolve) throws ClassNotFoundException {
                if(requested.equals(name))throw new ClassNotFoundException(requested);return super.loadClass(requested,resolve);
            }
        };
    }
    @Test void copiedRawCreatorCallbacksCannotRetagAnEarlierPublication() throws Exception {
        try(var loader=new ModDependencyClassLoader("probe",new java.net.URL[]{creatorProbeJar().toUri().toURL()},
                creatorProbeParent(),List.of())) {
            var raw=(CharacterDefinition)loader.loadClass(CreatorProbe.class.getName()).getMethod("definition").invoke(null);
            OwnedCharacterRegistry.bind("earlier",PlayableCharacterRegistry.empty().register(raw.key(),raw),null,fixture().boundary());
            var copied=new CharacterDefinition(raw.key(),raw.displayName(),raw.spriteFactory(),raw.respawnStrategyFactory(),
                    raw.behavesLike(),raw.secondaryAbility(),raw.supportsSuperForm(),raw.artSupplier(),raw.paletteSupplier());
            var current=fixture();
            var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->mapped(copied,current));
            assertEquals(OWNER,failure.owner());
            assertTrue(failure.getCause().getMessage().contains("already owned"));
            assertEquals(Set.of(OWNER,"dependent"),current.disabled());
        }
    }
    @Test void creatorCannotForgeMapperOwnerThroughDirectCallsOrHiddenMethodReferences() throws Exception {
        String name=CreatorProbe.class.getName();
        try(var loader=new ModDependencyClassLoader("probe",new java.net.URL[]{creatorProbeJar().toUri().toURL()},creatorProbeParent(),List.of())) {
            var creator=loader.loadClass(name);var boundary=fixture().boundary();var empty=PlayableCharacterRegistry.empty();
            var direct=assertThrows(java.lang.reflect.InvocationTargetException.class,
                    ()->creator.getMethod("bind",PlayableCharacterRegistry.class,ModFaultBoundary.class).invoke(null,empty,boundary));
            assertInstanceOf(SecurityException.class,direct.getCause());
            var binder=(RegistryBinder)creator.getMethod("binder").invoke(null);
            assertThrows(SecurityException.class,()->binder.bind("forged-owner",empty,null,boundary));
            RegistryBinder host=OwnedCharacterRegistry::bind;
            assertSame(empty,host.bind(OWNER,empty,null,boundary));
        }
    }
    @Test void creatorCannotReplaceFactoryRuntimeBoundaryThroughAnOuterConstructionScope() throws Exception {
        try(var loader=new ModDependencyClassLoader("probe",new java.net.URL[]{creatorProbeJar().toUri().toURL()},
                creatorProbeParent(),List.of())) {
            var creator=loader.loadClass(CreatorProbe.class.getName());var fixture=fixture();
            var owned=mapped(definition(CharacterKey.SONIC,(code,x,y)->new ProbeSprite(CharacterKey.SONIC)),fixture);
            var direct=assertThrows(java.lang.reflect.InvocationTargetException.class,
                    ()->creator.getMethod("scopedFactory",CharacterDefinition.class).invoke(null,owned));
            assertInstanceOf(SecurityException.class,direct.getCause());
            assertTrue(fixture.disabled().isEmpty(),"rejected intake never invokes the owned factory");
            var caller=(ScopeCaller)creator.getMethod("scopeCaller").invoke(null);
            assertThrows(SecurityException.class,()->caller.call(CharacterKey.SONIC,callback->callback.get(),
                    ()->new ProbeSprite(CharacterKey.SONIC)));
            ScopeCaller host=CharacterConstructionScope::call;
            assertEquals(CharacterKey.SONIC,host.call(CharacterKey.SONIC,callback->callback.get(),
                    ()->new ProbeSprite(CharacterKey.SONIC)).characterKey());
        }
    }
}
