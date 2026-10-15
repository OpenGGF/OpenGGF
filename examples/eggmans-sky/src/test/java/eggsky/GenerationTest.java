package eggsky;

import eggsky.core.Recolor;
import eggsky.space.Body;
import eggsky.space.SpaceMode;
import eggsky.world.Galaxy;
import eggsky.world.Planet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenerationTest {
    @Test void newGalaxyAtTheSameCoordinateReplacesPlanetAndOrbitalTexture() throws Exception {
        ArtFixture fixture=new ArtFixture();Game retained=fixture.game();
        retained.newExpedition(42,1,null);retained.player.systemId=0;
        Planet first=retained.buildPlanet(0);Object firstTexture=orbit(retained);
        retained.newExpedition(73,2,null);retained.player.systemId=0;
        Planet second=retained.buildPlanet(0);Object secondTexture=orbit(retained);
        assertNotSame(first,second,"same coordinate in another generation builds its own planet");
        assertNotSame(firstTexture,secondTexture,"orbit cache includes the generation");
        Game fresh=fixture.game();fresh.newExpedition(73,2,null);fresh.player.systemId=0;
        Planet expected=fresh.buildPlanet(0);
        assertEquals(expected.spec.seed,second.spec.seed);
        assertArrayEquals(expected.backdrop.pixels(),second.backdrop.pixels());
        for(int i=0;i<expected.minerals.getFirst().sprites.length;i++)
            assertArrayEquals(expected.minerals.getFirst().sprites[i].image().pixels(),second.minerals.getFirst().sprites[i].image().pixels());
        assertTrue(retained.save());assertTrue(retained.load());
        assertNotSame(second,retained.buildPlanet(0),"Continue replaces generation-owned data");
    }
    @Test void carvingLivesWithTheCurrentPlanetAndIsNotInTheExpeditionSave() {
        Game game=new ArtFixture().game();game.newExpedition(42,1,null);
        Planet active=game.buildPlanet(0);int x=0,y=-1;
        for(;x<active.terrain.width();x+=16){y=active.terrain.topFloor(x);if(y>=0)break;}
        assertTrue(y>=0);String saved=game.player.encode();
        assertTrue(active.terrain.carve(x,y+3,6)>0);assertTrue(active.terrain.carvedCells()>0);
        assertSame(active,game.buildPlanet(0),"orbit/relanding on the retained planet preserves edits");
        assertEquals(saved,game.player.encode(),"carving does not alter the v1 stream");
        assertTrue(game.system().planetCount>1);assertNotNull(game.buildPlanet(1));
        Planet rebuilt=game.buildPlanet(0);assertNotSame(active,rebuilt);assertEquals(0,rebuilt.terrain.carvedCells());
        assertTrue(game.save());assertTrue(game.load());assertEquals(0,game.buildPlanet(0).terrain.carvedCells());
    }
    private Object orbit(Game game) throws Exception {
        SpaceMode mode=new SpaceMode(SpaceMode.ARRIVE_LAUNCH,0);mode.enter(game);
        var field=SpaceMode.class.getDeclaredField("bodies");field.setAccessible(true);
        return ((Body)((List<?>)field.get(mode)).getFirst()).texture;
    }
    @Test void rejectedLoadPreservesActiveStateAndTheOriginalStoredBytes() {
        ArtFixture fixture=new ArtFixture();Game game=fixture.game();
        game.newExpedition(42,1,null);game.player.systemId=0;
        Planet activePlanet=game.buildPlanet(0);var active=game.player;String before=active.encode();
        for(String text:List.of("arbitrary prose",before.replace("version=1","version=999"),
                before.replace("bonusSlots=0","bonusSlots=1000000"),"x".repeat(1048577))) {
            fixture.storage.files.put(Game.SAVE_FILE,text);
            assertFalse(game.hasSave());assertFalse(game.load());
            assertSame(active,game.player);assertSame(activePlanet,game.planet());assertEquals(before,active.encode());
            assertFalse(game.save());assertEquals(text,fixture.storage.files.get(Game.SAVE_FILE));
        }
    }
    @Test void recolourLookupIsBoundedAndEvictedImagesStayValid() throws Exception {
        ArtFixture fixture=new ArtFixture();var art=fixture.game().art;
        var scheme=new Recolor(90,1,1,0,0);var active=art.recolored(fixture.sprite,scheme,"active");
        int[] pixels=active.image().pixels();
        for(int i=0;i<600;i++)art.recolored(fixture.sprite,scheme,"explored:"+i);
        var field=art.getClass().getDeclaredField("tinted");field.setAccessible(true);
        assertTrue(((Map<?,?>)field.get(art)).size()<=256,"mod-owned recolours are bounded");
        assertArrayEquals(pixels,active.image().pixels());
    }
    @Test void longExplorationBoundsLookupsAndPreservesHomeWhenEvicted() throws Exception {
        Galaxy galaxy=new Galaxy(42,1);var home=galaxy.start();
        for(int i=100;i<10100;i++)galaxy.system(i,120);
        for(var entry:Map.of("cache",512,"empty",2048).entrySet()) {
            var field=Galaxy.class.getDeclaredField(entry.getKey());field.setAccessible(true);
            assertTrue(((Map<?,?>)field.get(galaxy)).size()<=entry.getValue(),entry.getKey()+" remains bounded");
        }
        var returned=galaxy.system(home.id);assertEquals(home.seed,returned.seed);assertTrue(returned.home);
    }
}
