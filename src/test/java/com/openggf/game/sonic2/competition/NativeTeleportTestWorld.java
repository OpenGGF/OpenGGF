package com.openggf.game.sonic2.competition;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.control.*;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.competition.state.*;
import com.openggf.tests.RomTestUtils;
import com.openggf.tools.HeadlessGameBoot;
import java.util.EnumMap;

/** Positioned ROM-world probe; views and BG-bank ports are test fakes, never a parity activation. */
final class NativeTeleportTestWorld {
    interface Check { void run(NativeTeleportTestWorld world) throws Exception; }
    static final class Banks implements Sonic2TeleportPorts.BackgroundBanks {
        Sonic2TeleportPorts.BackgroundPosition first = new Sonic2TeleportPorts.BackgroundPosition(1,2,3,4,5,6);
        Sonic2TeleportPorts.BackgroundPosition second = new Sonic2TeleportPorts.BackgroundPosition(11,12,13,14,15,16);
        int swaps;
        @Override public void swapPlayers() { var old=first; first=second; second=old; swaps++; }
        @Override public Sonic2TeleportPorts.BackgroundPosition primaryPosition() { return first; }
    }
    final Banks backgrounds = new Banks();
    final Sonic2CompetitionSession session = new Sonic2CompetitionSession();
    final InputHandler input = new InputHandler();
    final Sonic2NativeControlState controls = new Sonic2NativeControlState();
    final Sonic2NativeCompetitionHost host;
    final Sonic2CompetitionNativeState nativeState;
    int planeSwaps;
    private NativeTeleportTestWorld(com.openggf.GameLoop loop, EngineContext services, Sonic2TeleportPorts.ObjectFields fields) {
        nativeState = fields == null ? new Sonic2CompetitionNativeState(backgrounds, () -> planeSwaps++)
                : new Sonic2CompetitionNativeState(backgrounds, () -> planeSwaps++, fields);
        host = new Sonic2NativeCompetitionHost(SessionManager.getCurrentWorldSession().rootGameModule(), services,
                loop::setGameplayMode, new Sonic2NativeCompetitionHost.Views() {
                    public void prepare(GameplayModeContext c, Sonic2CompetitionSession s,
                                        Sonic2CompetitionLevelProfile p, Sonic2CompetitionNativeArt a) { }
                    public void draw(GameplayModeContext c, Sonic2CompetitionSession s) { }
                }, nativeState);
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        host.bindNativeWaitInput(NativeWaitInput.from(input), controls);
        session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0,0,0,0,true,true), PlayerInputState.neutral()), true);
        host.loadAct(session, 0, 0);
    }
    /** Qualify the transaction before its production operational gate is enabled. */
    void qualifyTeleport() {
        var transaction=org.mockito.Mockito.mock(Sonic2CompetitionHost.class);
        org.mockito.Mockito.when(transaction.swapNativeActorWorldState())
                .thenAnswer(call -> nativeState.swapActorWorldState(host.context(),session));
        org.mockito.Mockito.doAnswer(call -> { nativeState.freezeObjects(host.context(),call.getArgument(0));return null; })
                .when(transaction).freezeObjects(0x40);
        org.junit.jupiter.api.Assertions.assertTrue(session.teleportInitializedMonitor(transaction));
    }
    void tick() {
        var c=host.context();
        LevelFrameStep.updateTimers(LevelFrameContext.from(c));
        c.plcFrameLifecycle().runLogicalIteration(c::updateFade, frame -> {
            NativeCompetitionIterationAccess.runAdmitted(frame, () -> host.step(input, frame));
            return null;
        });
    }
    static Object value(Object source) throws Exception {
        if(source==null)return null;
        var values=new java.util.ArrayList<Object>();
        if(source.getClass().isArray()) {
            for(int i=0;i<java.lang.reflect.Array.getLength(source);i++)
                values.add(value(java.lang.reflect.Array.get(source,i)));
            return java.util.Collections.unmodifiableList(values);
        }
        if(source.getClass().isRecord()) {
            for(var component:source.getClass().getRecordComponents()) {
                var accessor=component.getAccessor();accessor.setAccessible(true);
                values.add(value(accessor.invoke(source)));
            }
            return java.util.Collections.unmodifiableList(values);
        }
        return source;
    }
    static void run(Check check) throws Exception { run(null, check); }
    static void run(Sonic2TeleportPorts.ObjectFields fields, Check check) throws Exception {
        var services=EngineServices.current(); var config=services.configuration();
        var before=new EnumMap<SonicConfiguration,Object>(SonicConfiguration.class);
        for (var key:SonicConfiguration.values()) before.put(key,config.getConfigValue(key));
        String gl=System.getProperty("openggf.headless.gl");
        try {
            System.setProperty("openggf.headless.gl","surfaceless-egl");
            config.setConfigValue(SonicConfiguration.AUDIO_ENABLED,false);
            config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED,false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,"NATIVE_4_3");
            config.resolveDisplayAspect();
            try (var boot=new HeadlessGameBoot(320,224,services)) {
                var loop=boot.boot(RomTestUtils.ensureSonic2RomAvailable().toPath().toAbsolutePath(),0,0);
                check.run(new NativeTeleportTestWorld(loop,services,fields));
            }
        } finally {
            com.openggf.tests.TestEnvironment.resetAll();
            before.forEach(config::setConfigValue); config.resolveDisplayAspect();
            if(gl==null)System.clearProperty("openggf.headless.gl");else System.setProperty("openggf.headless.gl",gl);
        }
    }
}
