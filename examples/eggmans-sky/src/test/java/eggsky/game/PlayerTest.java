package eggsky.game;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Production expedition codec regressions runnable in an exported creator fork. */
class PlayerTest {
    private final Catalog catalog = new Catalog();

    @TestFactory Stream<DynamicTest> rejectedSavesPreserveTheActivePlayerWithoutAllocatingCargo() {
        String valid = new Player(catalog).encode();
        List<String> rejected = List.of("arbitrary prose", valid.replace("version=1", "version=999"),
                valid.replace("bonusSlots=0", "bonusSlots=1000000"), valid.replace("bonusSlots=0", "bonusSlots=-1"),
                valid.replace("bonusSlots=0", "bonusSlots=2147483647"), valid.replace("spaceX=0.0", "spaceX=NaN"),
                valid + "rings=1\n", "version=1\nrings=42\n", valid.replace("cargo=", "cargo=999:1,"),
                valid.replace("cargo=0:0", "cargo=1:-1"), valid.replace("statDeaths=0", "statDeaths=-1"),
                valid.replace("rings=250", "rings=9223372036854775808"), valid.replace("tech=0,", "tech=7,"),
                valid.replace("tech=0,", "tech=-1,"), valid.replace("tech=", "tech=0,"),
                valid.replace("reserves=", "reserves=1:-1;"), valid.replace("names=", "names=place>one;place>two"),
                valid.replace("visitedSystems=", "visitedSystems=0;0"), "x".repeat(1048577));
        return java.util.stream.IntStream.range(0,rejected.size()).mapToObj(index -> DynamicTest.dynamicTest(
                "rejected candidate " + index, () -> {
                    Player active = new Player(catalog);active.cargo.add(Catalog.OXYGEN,25);
                    String before = active.encode();
                    Object result = Player.class.getMethod("decode",String.class).invoke(active,rejected.get(index));
                    assertEquals(16,active.cargo.slots(),"invalid save cannot drive allocation");
                    assertEquals(before,active.encode(),"all active state survives rejection");
                    assertEquals(false,result,"decoder returns a checked rejection");
                }));
    }

    @Test void supportedLegacyOmitsOnlyOptionalProgressFields() throws Exception {
        Player active = new Player(catalog);active.pinned=Catalog.WARP_CELL;active.reserves.put(Catalog.OXYGEN,25);
        String legacy = new Player(catalog).encode().lines().filter(line -> !line.startsWith("pinned=")
                && !line.startsWith("reserves=")).collect(java.util.stream.Collectors.joining("\n"));
        assertEquals(true,Player.class.getMethod("decode",String.class).invoke(active,legacy));
        assertEquals(0,active.pinned);assertEquals(Map.of(),active.reserves);
    }

    @Test void earnedMaximumCargoAndInventoryRoundTrip() throws Exception {
        Player original = new Player(catalog);
        original.tech[Catalog.T_CARGO]=catalog.tech(Catalog.T_CARGO).max();original.bonusSlots=24;
        original.cargo.resize(original.slots());original.cargo.add(Catalog.OXYGEN,500);
        original.reserves.put(Catalog.OXYGEN,25);original.pinned=Catalog.WARP_CELL;
        Player loaded = new Player(catalog);
        assertEquals(true,Player.class.getMethod("decode",String.class).invoke(loaded,original.encode()));
        assertEquals(64,loaded.cargo.slots());assertEquals(original.encode(),loaded.encode());
        original.tech[Catalog.T_CARGO]++;
        String before = loaded.encode();
        assertEquals(false,Player.class.getMethod("decode",String.class).invoke(loaded,original.encode()));
        assertEquals(before,loaded.encode());
    }
}
