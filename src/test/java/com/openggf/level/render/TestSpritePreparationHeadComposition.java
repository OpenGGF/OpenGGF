package com.openggf.level.render;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpritePresentation.*;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Fresh native frames preserve identity-sensitive draw occurrences across equal rebuilt masks. */
class TestSpritePreparationHeadComposition {
    private GraphicsManager graphics;

    @BeforeEach void setup() {
        GraphicsManager.destroyForReinit();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        graphics = GraphicsManager.getInstance(); graphics.initHeadless();
    }

    @AfterEach void cleanup() { SessionManager.clear(); GraphicsManager.destroyForReinit(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void freshPreparationPreservesDrawOccurrenceWhenEqualMasksAreRebuilt(boolean rebuildMask) {
        HeadMask oldMask = mask();
        HeadMask currentMask = rebuildMask ? mask() : oldMask;
        assertEquals(oldMask, currentMask);
        if (rebuildMask) assertNotSame(oldMask, currentMask);
        Frame previous = raw(oldMask, 18);
        Frame retained = new Frame(previous.tiles(), previous.primitives(), previous.patternVersions());
        Frame fresh = raw(currentMask, 19);
        Frame next = raw(currentMask, 19);

        Frame expected = PlayerHeadPresentation.compose(fresh, 0, 0);
        int base = PatternAtlasRange.PLAYER_PRESENTATION.base();
        assertEquals(List.of(base, base + 2, base + 1, base + 3),
                expected.tiles().stream().map(Tile::patternId).toList(), "both bodies precede both enlarged heads");
        assertEquals(expected, PlayerHeadPresentation.compose(next, 0, 0),
                "fresh native frames must keep body/head occurrence ordering");
        assertSame(currentMask, next.tiles().get(0).subject().head().mask());
        assertSame(currentMask, next.tiles().get(1).subject().head().mask());
        assertNotSame(previous.tiles().getFirst(), next.tiles().getFirst());
        assertNotSame(previous.tiles().get(1), next.tiles().get(1), "changed geometry always replaces the tile");
        assertEquals(retained, previous);
    }

    private static HeadMask mask() {
        return new HeadMask(0, 4, Set.of(0), List.of(new MaskPoint(0, 0),
                new MaskPoint(32, 0), new MaskPoint(32, 4), new MaskPoint(0, 4)));
    }

    private Frame raw(HeadMask mask, int secondX) {
        Pattern art = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) art.setPixel(x, y, (byte) 3);
        var head = new HeadTransform(mask, 0, 10, 20, false, false, 150,
                PatternAtlasRange.PLAYER_PRESENTATION.base(), Map.of(), SpritePresentationRenderer.headSatReplay());
        return SpritePresentation.prepare(graphics, 0, 0, () -> {
            SpritePresentationRenderer.bindPatternBank(graphics, 9000, new Pattern[]{art, art});
            SpritePresentation.withSubject(graphics, new Subject("sonic", Part.BODY, false, 150, head), () -> {
                graphics.renderPatternWithId(9000, new PatternDesc(), 10, 20);
                graphics.renderPatternWithId(9001, new PatternDesc(), secondX, 20);
            });
        }, descriptor -> {
            PatternDesc desc = (PatternDesc) descriptor;
            return new Attributes(desc.getPaletteIndex(), desc.getHFlip(), desc.getVFlip(), desc.getPriority());
        });
    }
}
