package com.openggf.game.sonic1;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.internal.NativeLevelLoadInputs;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission.Phase;
import com.openggf.game.internal.NativeLevelLoadPlan;
import com.openggf.game.sonic1.credits.Sonic1EndingProvider;
import com.openggf.level.NativeLevelLoadAccess;
import com.openggf.sprites.NativePositionOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import static com.openggf.game.sonic1.Sonic1RomProfile.resolve;

/** Actual credits GM_Level setup and uncounted initial ExecuteObjects. */
final class Sonic1CreditsDemoLoad {
    private final LevelLoadContext context;
    private final NativeLevelLoadInputs inputs;
    private final Sonic1EndingProvider provider;
    private final Runnable finished;
    private Sonic1EndingAssembly assembly;

    Sonic1CreditsDemoLoad(LevelLoadContext context, NativeLevelLoadInputs inputs,
            Sonic1EndingProvider provider, Runnable finished) {
        this.context = context; this.inputs = inputs; this.provider = provider; this.finished = finished;
    }

    static NativeLevelLoadInputs inputs(RomByteReader rom, int page) {
        if (page < 0 || page >= 8) throw new IllegalArgumentException("invalid credits demo selection");
        int demoLevel = resolve(rom, 0x5906) + page * 2;
        int zoneAct = rom.readU16BE(demoLevel), zone = zoneAct >>> 8, act = zoneAct & 0xff;
        int index = zone * 4 + act;
        int layouts = resolve(rom, 0x68B96), layout = layouts + index * 6;
        int collision = resolve(rom, 0x4102) + zone * 4;
        int demoInput = resolve(rom, 0x40A2) + page * 4;
        var regions = new ArrayList<NativeLevelLoadInputs.RegionSelection>();
        regions.add(new NativeLevelLoadInputs.RegionSelection(demoLevel, 2));
        regions.add(new NativeLevelLoadInputs.RegionSelection(resolve(rom, 0x607A) + page * 4, 4));
        regions.add(new NativeLevelLoadInputs.RegionSelection(resolve(rom, 0x1DD16) + zone * 16, 16));
        regions.add(new NativeLevelLoadInputs.RegionSelection(layout, 6));
        regions.add(new NativeLevelLoadInputs.RegionSelection(resolve(rom, 0x6B000) + index * 4, 4));
        regions.add(new NativeLevelLoadInputs.RegionSelection(resolve(rom, 0x5F2A) + index * 12, 12));
        regions.add(new NativeLevelLoadInputs.RegionSelection(collision, 4));
        regions.add(new NativeLevelLoadInputs.RegionSelection(demoInput, 4));
        // EndingDemoLoad selects its saved-lamppost branch on credits page 4.
        if (page == 3) regions.add(new NativeLevelLoadInputs.RegionSelection(resolve(rom, 0x5916), 36));
        return NativeLevelLoadInputs.fromRom(rom, regions, List.of(
                layouts + rom.readU16BE(layout), layouts + rom.readU16BE(layout + 2),
                rom.readU32BE(collision), rom.readU32BE(demoInput)));
    }

    NativeLevelLoadPlan plan(List<InitStep> steps) {
        var named = new LinkedHashMap<String, InitStep>();
        for (var step : steps) named.put(step.name(), step);
        var setup = new ArrayList<InitStep>();
        for (String name : List.of("InitGameModule", "ResetRng", "LoadLevelData")) setup.add(named.get(name));
        setup.add(new InitStep("InstallCreditsDemoPrefix", "Level_ClrRam before setup", () ->
                NativeLevelLoadAccess.installPrefix(GameServices.level(), context.getLevel())));
        for (String name : List.of("InitAnimatedContent", "InitObjectSystem", "InitZoneFeatures", "InitWater", "InitBackgroundRenderer"))
            setup.add(named.get(name));
        setup.add(new InitStep("QueueCreditsSecondaryPlc", "LevelDataLoad secondary AddPLC (LevelLayoutLoad.asm:50-57)",
                this::queueSecondaryPlc));
        setup.add(new InitStep("PrepareCreditsObjectArt", "LevelDataLoad ROM-backed art", () ->
                NativeLevelLoadAccess.initializeObjectArt(GameServices.level())));
        var placement = new ArrayList<InitStep>();
        placement.add(new InitStep("PrepareCreditsPlayableArt", "Level_LoadObj native Sonic resources", () ->
                NativeLevelLoadAccess.initializePlayableArt(GameServices.level())));
        for (String name : List.of("InitPlayerAndCheckpoint", "RestoreCheckpoint", "SpawnPlayer", "ResetPlayerState", "InitCamera"))
            placement.add(named.get(name));
        placement.add(new InitStep("SelectCreditsStart", "LevelSizeLoad ending start/lamppost branch", this::preparePlayer));
        for (String name : List.of("InitBackgroundScroll", "InitLevelEvents", "InitRings")) placement.add(named.get(name));
        placement.add(new InitStep("PlaceCreditsObjects", "Level_LoadObj ObjPosLoad", () ->
                NativeLevelLoadAccess.prepareInitialObjects(GameServices.level())));
        var presentation = List.of(new InitStep("BuildCreditsDemoSprites", "BuildSprites before Level_SkipClr", () -> assembly.buildSprites()),
                named.get("InitGameplayState"), new InitStep("ClearCreditsPostPass", "Level_SkipClr (sonic.asm:2901-2923)", this::clearPostPass));
        return new NativeLevelLoadPlan(List.of(section(Phase.LEVEL_SETUP, setup, List.of()),
                section(Phase.INITIAL_PLACEMENT, placement, List.of()),
                section(Phase.INITIAL_OBJECTS, List.of(), List.of(new InitStep("ExecuteCreditsObjects",
                        "Level_LoadObj initial ExecuteObjects (sonic.asm:2894-2897)", () -> {
                            assembly = Sonic1EndingAssembly.executeObjects(); provider.nativeDemoAssemblyPrepared();
                        }))),
                section(Phase.INITIAL_PRESENTATION, presentation, List.of()),
                NativeLevelLoadPlan.Section.steps(List.of(new InitStep("CompleteCreditsDemoSetup", "Level_Delay", finished)))));
    }

    private NativeLevelLoadPlan.Section section(Phase phase, List<InitStep> prepare, List<InitStep> commit) {
        return new NativeLevelLoadPlan.Section(phase, () -> inputs, () -> true, prepare, commit);
    }

    private void queueSecondaryPlc() {
        if (!com.openggf.game.resources.NativeLevelPlcPolicy.allows(context)) return;
        var plc = GameServices.module().getGameService(com.openggf.game.sonic1.resources.Sonic1PlcService.class);
        if (plc == null) return;
        // Only zero-routine interrupts occur inside this prepared setup, and
        // they cannot service the queue. Submission remains actual ROM work.
        try {
            var rom = GameServices.rom().getRom();
            int header = resolve(rom, com.openggf.game.sonic1.constants.Sonic1Constants.LEVEL_HEADERS_ADDR)
                    + context.getLevel().getZoneIndex() * 16;
            int secondary = (rom.read32BitAddr(header + 4) >>> 24) & 0xff;
            if (secondary != 0) plc.append(secondary);
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private void preparePlayer() {
        var player = GameServices.sprites().getMainPlayable();
        var lamp = provider.getDemoLamppostState();
        NativePositionOps.writeXPosResetSubpixel(player, lamp == null ? provider.getDemoStartX() : lamp.playerX());
        NativePositionOps.writeYPosResetSubpixel(player, lamp == null ? provider.getDemoStartY() : lamp.playerY());
        player.setRingCount(lamp == null ? 0 : lamp.rings());
        player.setXSpeed((short)0); player.setYSpeed((short)0); player.setGSpeed((short)0);
        player.setAir(false); player.setAngle((byte)0); player.setGroundMode(com.openggf.game.GroundMode.GROUND);
        player.setOnObject(false); player.setPushing(false); player.setControlLocked(false); player.setForcedInputMask(0);
        player.setNativeSlotPresent(true); player.setHidden(false); player.forceAnimationRestart();
        var camera = GameServices.camera();
        if (lamp == null) camera.updatePosition(true);
        else {
            camera.setX((short)lamp.cameraX()); camera.setY((short)lamp.cameraY()); camera.setMaxY((short)lamp.cameraMaxY());
            int zone = GameServices.level().getFeatureZoneId(), act = GameServices.level().getFeatureActId();
            GameServices.water().setWaterLevelDirect(zone, act, lamp.waterHeight());
            GameServices.water().setWaterLevelTarget(zone, act, lamp.waterHeight());
            GameServices.level().getZoneFeatureProvider().setWaterRoutine(lamp.waterRoutine());
        }
    }

    private void clearPostPass() {
        var player = GameServices.sprites().getMainPlayable();
        var lamp = provider.getDemoLamppostState();
        player.setRingCount(lamp == null ? 0 : lamp.rings());
        GameServices.level().setFrameCounter(0); GameServices.sprites().setFrameCounter(0);
    }
}
