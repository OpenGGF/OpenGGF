package com.openggf.game.sonic2.competition;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Native Level/InitPlayers assembly; the supplied humans never enter campaign team setup. */
public final class Sonic2CompetitionLevelInitProfile extends AbstractLevelInitProfile
        implements com.openggf.game.internal.QueuedPatternDmaPublication {
    private final Sonic2NativeCompetitionHost host;

    public Sonic2CompetitionLevelInitProfile(Sonic2NativeCompetitionHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
        var steps = new ArrayList<InitStep>();
        steps.add(ioStep("PrepareS2CompetitionLoad", "Level_ClrRam / LoadLevelBlocks_2P", () -> {
            context.setShowTitleCard(false);
            context.setTitleCardRequiredInHeadlessMode(false);
            context.setQueueFreshLevelRuntimeArt(false);
            host.prepareLoad(context);
        }));
        for (var core : buildCoreSteps(context)) {
            switch (core.name()) {
                case "InitGameModule" -> {
                    steps.add(core);
                    steps.add(ioStep("QueueInitialCompetitionPlcs", "Level / Level_TtlCard: native initial PLCs",
                            host::completeInitialPlcs));
                }
                case "LoadLevelData" -> {
                    steps.add(core);
                    steps.add(ioStep("QueueSecondaryCompetitionPlc", "loadZoneBlockMaps / title leave loop",
                            host::completeSecondaryPlc));
                }
                case "InitObjectSystem" -> steps.add(ioStep("InitNativeCompetitionObjects",
                        "ObjectsManager_2P_Init: six shared banks before first placement reset", host::initializeObjects));
                case "InitGameplayState" -> steps.add(new InitStep("InitCompetitionRuntimeState",
                        "Level_ClrRam: reset runtime globals, retaining the session ledger", host::initializeRuntimeState));
                case "InitRings" -> steps.add(new InitStep("InitNativeCompetitionRings",
                        "RingsManager: independent P1/P2 address pairs", host::initializeRings));
                case "InitArt" -> steps.add(new InitStep("InitCompetitionObjectArt",
                        "LoadPLC: object art only; native playable art has its own VRAM producer", host::initializeArt));
                case "InitPlayerAndCheckpoint" -> steps.add(new InitStep("BindCompetitionPlayers",
                        "InitPlayers: human Sonic and human Tails", host::initializeParticipants));
                default -> steps.add(core);
            }
        }
        steps.add(new InitStep("InitCompetitionCameras", "Get_LevelSizeStart / DeformBgLayerAfterScrollVert",
                host::initializeCameras));
        // S2's leading RunObjects and 25 title leave waits belong to the host
        // after load completion. The S3K slot-3 prelude cannot dispatch this SST.
        return List.copyOf(steps);
    }

    @Override public void serviceQueuedPatternDma(com.openggf.game.resources.PlcLifecyclePhase phase,
            boolean explicitDmaService, com.openggf.game.RuntimeArtCoordinator runtimeArtCoordinator,
            com.openggf.level.LevelManager levelManager) {
        if (!explicitDmaService) return;
        var context = host.context();
        if (runtimeArtCoordinator != context.runtimeArtCoordinator() || levelManager != context.getLevelManager())
            throw new IllegalArgumentException("Native DMA publication requires its admitted world");
        // Vint_CtrlDMA services only the actual queued DMA, including a LAG
        // classification. The claimed lifecycle already owns normal PLC/tail.
        var owner = context.getWorldSession().getGameModule().getGameService(
                com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVramService.class);
        Objects.requireNonNull(owner, "native VRAM owner").serviceDmaQueueVBlank();
    }

    @Override protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetNativeCompetitionLoad", "Retire level-local native state", host::resetLoadState);
    }
    @Override protected InitStep perTestLeadStep() { return levelEventTeardownStep(); }
}
