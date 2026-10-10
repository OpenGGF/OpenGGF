package starpost.realvalley;

import static org.junit.jupiter.api.Assertions.*;
import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.level.Palette;
import java.util.List;
import org.junit.jupiter.api.Test;
import starpost.art.Tone;
import starpost.core.Game;

/** Palette writes are exactly the scene's Tone rules, with stable ROM inputs and bounded ownership. */
class TestActSeasons {
    private Game game() { return new Game(new starpost.core.Catalog(),1234); }
    private int word(int rgb) {
        return Math.round((rgb >>> 16 & 255) * 7 / 255f) << 1
            | Math.round((rgb >>> 8 & 255) * 7 / 255f) << 5 | Math.round((rgb & 255) * 7 / 255f) << 9;
    }
    @Test void everySeasonAndLightMatchesToneForAllGenesisColours() {
        for (int season=0; season<4; season++) for (int light=0; light<3; light++) {
            Tone tone=new Tone(season);
            for (int r=0;r<8;r++) for (int g=0;g<8;g++) for(int b=0;b<8;b++) {
                int source=r<<1|g<<5|b<<9;
                Palette.Color c=new Palette.Color(); c.fromSegaFormat(source);
                int rgb=0xFF000000|(c.r&255)<<16|(c.g&255)<<8|c.b&255;
                assertEquals(word(Tone.sky(tone.apply(rgb),light)),ActSeasons.colour(source,tone,light,false));
            }
        }
    }
    @Test void seasonAndClockChangesRecomputeFromRomClaimsWithoutTouchingHostCells() {
        var game=game(); var registry=new PaletteOwnershipRegistry();
        var claims=List.of(new ModPaletteClaim(2,3,0x0E0),new ModPaletteClaim(3,2,0xE44));
        Palette[] palettes={new Palette(),new Palette(),new Palette(),new Palette()};
        palettes[0].getColor(1).fromSegaFormat(0xE00);
        palettes[1].getColor(5).fromSegaFormat(0xEEE);
        for (int season : new int[]{0,1,2,3,0}) for (int minutes : new int[]{720,1080,1200,720}) {
            game.calendar.set(1,season,10,minutes);
            registry.beginFrame(); ActSeasons.submit(registry,claims,game);
            registry.resolveInto(palettes,null,null,palettes[0]);
            for(var claim:claims) {
                assertEquals(ActSeasons.colour(claim.segaColor(),new Tone(season),game.calendar.light(),false),
                    word(0xFF000000|(palettes[claim.line()].red(claim.color())&255)<<16
                        |(palettes[claim.line()].green(claim.color())&255)<<8|palettes[claim.line()].blue(claim.color())&255));
                assertEquals("starpost-valley:seasons",registry.ownerAt(PaletteSurface.NORMAL,claim.line(),claim.color()));
            }
            assertEquals((byte)255,palettes[0].blue(1));
            assertEquals((byte)255,palettes[1].green(5));
            assertEquals("none",registry.ownerAt(PaletteSurface.NORMAL,1,5));
        }
        assertEquals(0xE0,claims.getFirst().segaColor());
    }
    @Test void rainCoolsDaylightButDoesNotReplaceNightOrDusk() {
        assertNotEquals(ActSeasons.colour(0xEEE,new Tone(0),0,false),ActSeasons.colour(0xEEE,new Tone(0),0,true));
        for(int light:new int[]{1,2}) assertEquals(ActSeasons.colour(0xEEE,new Tone(3),light,false),ActSeasons.colour(0xEEE,new Tone(3),light,true));
    }
}
