package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.FrameMapping;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.RawPiece;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Actual disc-backed mapper admission; this establishes no gameplay or pixel outcome. */
@Isolated
class TestCdOriginalR11WaterfallMappingAdmission {
    @Test void actualStartupFactoryAdmitsThreeFramesWithoutChangingOpeningAdmission() throws Exception {
        var assets = TestCdOriginalBootPrefix.source().assets();
        var objects = assets.openingObjects();
        var startup = CdObjectMappings.fromOriginalR11Startup(assets, objects, assets.startupObjects());
        // Native selected waterfall include: three counted bodies, one piece each.
        var expected = List.of(new RawPiece(-16, 0x0F, 0, -16),
                new RawPiece(-12, 0x0A, 0x10, -12), new RawPiece(-8, 0x05, 0x19, -8));
        for (int i = 0; i < expected.size(); i++) {
            final int selected = i;
            var body = assertDoesNotThrow(() -> startup.resolve(0x23F05C, false, selected),
                    "Actual original startup must register the selected waterfall root");
            var frame = assertInstanceOf(FrameMapping.class, body);
            assertEquals(1, frame.countByte());
            assertEquals(List.of(expected.get(i)), frame.pieces());
        }
        assertThrows(UnsupportedOperationException.class, () -> startup.resolve(0x23F05C, false, 3));
        var opening = CdObjectMappings.fromOpeningAssets(assets, objects);
        assertThrows(UnsupportedOperationException.class, () -> opening.resolve(0x23F05C, false, 0));
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
