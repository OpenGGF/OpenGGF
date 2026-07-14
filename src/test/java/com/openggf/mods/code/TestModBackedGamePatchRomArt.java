package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.patch.PatchContext;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestModBackedGamePatchRomArt {

    private static ModRegistrationPlan planWithRomArt() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerRomObjectArt("bird",
                new RomArtRequest(0x50000, RomArtCompression.NEMESIS, 0, 0x60000, 0, 0, 1));
        return context.freeze();
    }

    @Test
    void applyMaterializesRomArtAndServesItThroughTheOverlay() {
        ModRegistrationPlan plan = planWithRomArt();
        ObjectSpriteSheet fakeSheet = fakeSheet();
        ModBackedGamePatch patch = new ModBackedGamePatch(plan, faultBoundary(), findingSink(),
                (owner, requests) -> {
                    assertEquals(Map.of("owner:bird", plan.romObjectArt().get("owner:bird")), requests);
                    return Map.of("owner:bird", fakeSheet);
                });
        GameModule module = patch.apply(baseModule(), patchContext());
        ObjectArtProvider provider = module.getObjectArtProvider();
        assertNotNull(provider.getRenderer("owner:bird"));
    }

    @Test
    void materializationFailurePropagatesOutOfApply() {
        ModRegistrationPlan plan = planWithRomArt();
        ModBackedGamePatch patch = new ModBackedGamePatch(plan, faultBoundary(), findingSink(),
                (owner, requests) -> {
                    throw new ModRegistrationException("owner", "MOD_ROM_ART_INVALID", "boom",
                            "owner:bird", null);
                });
        assertThrows(ModRegistrationException.class,
                () -> patch.apply(baseModule(), patchContext()));
    }

    @Test
    void planWithoutRomArtNeverInvokesTheSource() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerObject("thing", (spawn, registry) -> null);
        ModRegistrationPlan plan = context.freeze();
        assertTrue(plan.romObjectArt().isEmpty());
        ModBackedGamePatch patch = new ModBackedGamePatch(plan, faultBoundary(), findingSink(),
                (owner, requests) -> { throw new AssertionError("must not materialize"); });
        assertNotNull(patch.apply(baseModule(), patchContext()));
    }

    private static GameModule baseModule() {
        GameModule base = mock(GameModule.class);
        when(base.getObjectArtProvider()).thenReturn(mock(ObjectArtProvider.class));
        return base;
    }

    private static PatchContext patchContext() {
        return new PatchContext(ignored -> null, SonicConfigurationService.createStandalone());
    }

    private static ModFaultBoundary faultBoundary() {
        return new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> {});
    }

    private static java.util.function.BiConsumer<String,
            com.openggf.game.sonic2.dataselect.S2SaveFinding> findingSink() {
        return (owner, finding) -> {};
    }

    private static ObjectSpriteSheet fakeSheet() {
        Pattern pattern = new Pattern();
        SpriteMappingFrame frame = new SpriteMappingFrame(
                List.of(new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0)));
        return new ObjectSpriteSheet(new Pattern[] {pattern}, List.of(frame), 0, 1);
    }
}
