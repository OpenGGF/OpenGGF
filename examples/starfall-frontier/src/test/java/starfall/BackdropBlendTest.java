package starfall;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackdropBlendTest {
    @Test void firstRegionIsOpaqueAndAChangeFadesToCompletion() {
        var blend=new BackdropBlend();blend.update(0);
        assertEquals(1,blend.weight(0));blend.update(2);
        assertTrue(blend.weight(0)>.99);assertTrue(blend.weight(2)>0);
        for(int n=1;n<BackdropBlend.DURATION;n++)blend.update(2);
        assertEquals(0,blend.weight(0));assertEquals(1,blend.weight(2));
        for(int n=0;n<100;n++)blend.update(2);
        assertEquals(1,blend.weight(2));
    }
    @Test void turningBackOrEnteringAThirdRegionRetainsTheVisibleMixture() {
        var blend=new BackdropBlend();blend.snap(0);
        for(int n=0;n<22;n++)blend.update(2);
        double old=blend.weight(0),incoming=blend.weight(2);
        blend.update(4);
        assertEquals(old/incoming,blend.weight(0)/blend.weight(2),1e-10);
        assertTrue(blend.weight(4)>0);assertTrue(blend.weight(4)<.01);
        assertEquals(1,blend.weight(0)+blend.weight(2)+blend.weight(4),1e-10);
        blend.update(0);assertTrue(blend.weight(0)>old*.99);
        for(int n=1;n<BackdropBlend.DURATION;n++)blend.update(0);
        assertEquals(1,blend.weight(0));assertEquals(0,blend.weight(2));assertEquals(0,blend.weight(4));
    }
    @Test void depthAndActChangesSelectDifferentBackgroundsButReadingNeverAdvancesTheFade() {
        var world=new World(123,true,256,96);world.x=80*World.T+6;
        world.y=(world.surface(80)+10)*World.T;
        int shallow=BackdropBlend.key(world);world.y=(world.surface(80)+22)*World.T;
        int deep=BackdropBlend.key(world);assertNotEquals(shallow,deep);
        var blend=new BackdropBlend();blend.snap(shallow);blend.update(deep);
        double before=blend.weight(deep);
        for(int n=0;n<100;n++)assertEquals(before,blend.weight(deep));
        world.y=(world.surface(80)+32)*World.T;assertNotEquals(deep,BackdropBlend.key(world));
        blend.snap(world);assertEquals(1,blend.weight(BackdropBlend.key(world)));assertEquals(0,blend.weight(deep));
    }
}
