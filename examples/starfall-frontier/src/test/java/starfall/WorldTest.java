package starfall;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static starfall.Content.Item.*;

class WorldTest {
    private World blank() {
        World w=new World(73,false);w.x=40*12+6;w.y=29*12-10;
        for(int x=1;x<World.W-1;x++)w.set(x,29,World.STONE);
        w.add(PICK,1);w.add(AXE,1);w.add(SWORD,1);return w;
    }
    private int recipe(World w,Content.Item i) {for(int n=0;n<w.content.recipes.size();n++)if(w.content.recipes.get(n).item()==i)return n;throw new AssertionError();}
    private void mine(World w,int tx,int ty,Content.Item tool) {for(int i=0;i<60&&w.tile(tx,ty)!=World.AIR;i++){w.actionCooldown=0;w.mine(tx,ty,tool);}}
    @Test void seedsProduceIdenticalWorldsAndAlwaysProvideStarterOreAndThreeShrines() {
        for(long seed:new long[]{0,1,73,Long.MIN_VALUE,Long.MAX_VALUE}) {
            World a=new World(seed),b=new World(seed);assertArrayEquals(a.tiles,b.tiles);
            assertArrayEquals(a.inventory,b.inventory);assertFalse(a.blocked(a.x,a.y,4,10));
            assertEquals(World.COPPER,a.tile(44,37));assertEquals(World.IRON,a.tile(55,46));
            for(int i=0;i<3;i++)assertEquals(World.SHRINE,a.tile(a.shrineX[i],a.shrineY[i]-1));
        }
        assertFalse(Arrays.equals(new World(1).tiles,new World(2).tiles));
    }
    @Test void collisionPreventsWallPenetrationAndJumpReachesItsTop() {
        World w=blank();w.set(42,28,World.STONE);
        for(int i=0;i<20;i++)w.step(new World.Input(1,false,false));
        assertTrue(w.x<=42*12-4);assertTrue(w.grounded);
        w.step(new World.Input(1,true,false));
        for(int i=0;i<24;i++)w.step(new World.Input(1,false,false));
        assertTrue(w.x>43*12);assertTrue(w.y<29*12-10);
    }
    @Test void sonicMomentumHasFirmFrictionAndAirControlWithoutAirDrag() {
        World w=blank();w.grounded=true;
        w.step(new World.Input(1,false,false));assertEquals(24/256.0,w.vx,1e-9);
        for(int i=0;i<70;i++)w.step(new World.Input(1,false,false));assertEquals(6,w.vx,1e-9);
        w.step(new World.Input(-1,false,false));assertEquals(5.5,w.vx,1e-9);
        for(int i=0;i<24;i++)w.step(new World.Input(0,false,false));assertEquals(0,w.vx,1e-9);
        w.step(new World.Input(1,true,false,true));double momentum=w.vx;
        w.step(new World.Input(0,false,false,true));assertEquals(momentum,w.vx,1e-9);
        w.step(new World.Input(-1,false,false,true));assertEquals(momentum-24/256.0,w.vx,1e-9);
    }
    @Test void holdingJumpGoesHigherAndDescendingSpinAttackBouncesOffBadnik() {
        World shortJump=blank(),highJump=blank();shortJump.grounded=highJump.grounded=true;
        shortJump.step(new World.Input(0,true,false,true));highJump.step(new World.Input(0,true,false,true));
        assertEquals(-6.5+56/256.0,highJump.vy,1e-9);
        double shortTop=shortJump.y,highTop=highJump.y;
        for(int i=0;i<32;i++) {
            shortJump.step(new World.Input(0,false,false,false));highJump.step(new World.Input(0,false,false,true));
            shortTop=Math.min(shortTop,shortJump.y);highTop=Math.min(highTop,highJump.y);
        }
        assertTrue(highTop<shortTop-30);
        World w=blank();w.y-=20;w.vy=2;World.Enemy e=new World.Enemy(w.x,w.y+12,0);w.enemies.add(e);
        int hp=w.hp;w.step(new World.Input(0,false,false));assertEquals(1,w.kills);assertEquals(hp,w.hp);assertEquals(-4,w.vy);
    }
    @Test void newWorldIsAnAngelIslandBiomeAndLegacyTerrainTagsStillDecode() {
        World w=new World(73);
        assertTrue(w.biome().startsWith("ANGEL ISLAND"));
        assertEquals(World.SNOW,w.tile(176,w.surface(176)));
        assertEquals(World.EMBER,w.tile(208,w.surface(208)));
        w.set(100,30,World.SNOW);w.set(180,30,World.EMBER);
        World restored=SaveCodec.decode(SaveCodec.encode(w)).orElseThrow();
        assertEquals(World.SNOW,restored.tile(100,30));assertEquals(World.EMBER,restored.tile(180,30));
    }
    @Test void oneWayPlatformsCatchFallingPlayerAndAllowDownwardDrop() {
        World w=blank();w.set(40,27,World.PLATFORM);w.x=40*12+6;w.y=25*12;w.vy=3;
        for(int i=0;i<20;i++)w.step(new World.Input(0,false,false));
        assertTrue(w.grounded);assertTrue(w.y+10<=27*12+.01);
        for(int i=0;i<20;i++)w.step(new World.Input(0,false,true));
        assertTrue(w.y>27*12);
    }
    @Test void treeFallsAsOneAndReturnsWoodAndReplantableAcorns() {
        World w=blank();for(int y=23;y<29;y++)w.set(43,y,World.LOG);w.set(42,23,World.LEAVES);
        mine(w,43,27,AXE);assertEquals(18,w.count(WOOD));assertEquals(2,w.count(ACORN));
        assertEquals(World.AIR,w.tile(43,23));assertEquals(World.AIR,w.tile(42,23));assertEquals(1,w.woodChopped);
    }
    @Test void miningNeedsReachCorrectToolAndProgressionTier() {
        World w=blank();w.set(43,28,World.IRON);mine(w,43,28,PICK);assertEquals(World.IRON,w.tile(43,28));
        w.add(COPPER_PICK,1);mine(w,43,28,AXE);assertEquals(World.IRON,w.tile(43,28));
        mine(w,43,28,COPPER_PICK);assertEquals(1,w.count(IRON));
        w.set(43,28,World.CRYSTAL);mine(w,43,28,COPPER_PICK);assertEquals(World.CRYSTAL,w.tile(43,28));
        w.add(IRON_PICK,1);mine(w,43,28,IRON_PICK);assertEquals(1,w.count(CRYSTAL));
        w.set(60,28,World.COPPER);mine(w,60,28,IRON_PICK);assertEquals(World.COPPER,w.tile(60,28));
    }
    @Test void changingMiningTargetLosesPartialProgress() {
        World w=blank();w.set(43,28,World.STONE);w.set(44,28,World.STONE);
        for(int i=0;i<20;i++)w.mine(43,28,PICK);w.mine(44,28,PICK);w.mine(43,28,PICK);
        assertEquals(1,w.mining);assertEquals(World.STONE,w.tile(43,28));
    }
    @Test void craftingHasAtomicCostsRequiresNearbyStationAndCannotDuplicateUniqueTools() {
        World w=blank();w.add(WOOD,15);int n=recipe(w,FURNACE);int wood=w.count(WOOD);
        assertFalse(w.craft(n));assertEquals(wood,w.count(WOOD));
        w.add(STONE,20);assertFalse(w.craft(n));assertEquals(20,w.count(STONE));
        w.set(43,28,World.BENCH);assertTrue(w.craft(n));assertEquals(0,w.count(STONE));assertEquals(11,w.count(WOOD));
        w.add(COPPER_BAR,8);assertTrue(w.craft(recipe(w,COPPER_PICK)));assertEquals(COPPER_PICK,w.hotbar[0]);
        int bars=w.count(COPPER_BAR);assertFalse(w.craft(recipe(w,COPPER_PICK)));assertEquals(bars,w.count(COPPER_BAR));
    }
    @Test void placementConsumesExactlyOnceAndCannotEntombPlayerOrFloat() {
        World w=blank();w.add(WOOD,10);
        assertFalse(w.place(40,28,WOOD));assertEquals(10,w.count(WOOD));
        assertFalse(w.place(44,26,WOOD));assertTrue(w.place(43,28,WOOD));assertEquals(9,w.count(WOOD));
        w.actionCooldown=0;assertFalse(w.place(43,28,WOOD));assertEquals(9,w.count(WOOD));
    }
    @Test void wallsCanBeSalvagedAndSupportLanternsAndShelter() {
        World w=blank();w.add(WALL,30);w.add(TORCH,1);
        for(int a=-2;a<=2;a++)for(int b=-2;b<=0;b++){w.actionCooldown=0;assertTrue(w.place(40+a,28+b,WALL));}
        w.actionCooldown=0;assertTrue(w.place(42,26,TORCH));w.set(40,24,World.PLANK);assertTrue(w.sheltered());
        w.actionCooldown=0;assertTrue(w.mine(39,27,PICK));assertEquals(0,w.walls[27*World.W+39]);assertEquals(16,w.count(WALL));
    }
    @Test void rangedWeaponsRespectAmmunitionManaAndCooldown() {
        World w=blank();w.add(BOW,1);assertFalse(w.attack(w.x+40,w.y,BOW));assertEquals(0,w.shots.size());
        w.add(ARROW,2);assertTrue(w.attack(w.x+40,w.y,BOW));assertEquals(1,w.count(ARROW));
        assertFalse(w.attack(w.x+40,w.y,BOW));assertEquals(1,w.count(ARROW));
        w.actionCooldown=0;w.add(STAFF,1);w.mana=11;assertFalse(w.attack(w.x+30,w.y,STAFF));
        w.mana=12;assertTrue(w.attack(w.x+30,w.y,STAFF));assertEquals(0,w.mana);
    }
    @Test void foodAndHeartstonesDoNotWasteAtCapsAndArmorReducesDamage() {
        World w=blank();w.add(POTION,2);assertFalse(w.heal());assertEquals(2,w.count(POTION));
        w.hp=10;w.add(IRON,3);assertFalse(w.consume(IRON));assertEquals(3,w.count(IRON));assertEquals(10,w.hp);
        assertTrue(w.heal());assertEquals(60,w.hp);assertFalse(w.heal());
        w.add(HEART,8);for(int i=0;i<5;i++)assertTrue(w.consume(HEART));assertEquals(200,w.maxHp);
        assertFalse(w.consume(HEART));assertEquals(3,w.count(HEART));
        w.add(ARMOR,1);w.invulnerable=0;w.enemies.add(new World.Enemy(w.x,w.y,0));int hp=w.hp;
        w.step(new World.Input(0,false,false));assertEquals(hp-4,w.hp);
    }
    @Test void deathDuringAnEnemyPassDoesNotModifyItsIteratorOrLoseInventory() {
        World w=blank();w.hp=1;w.add(CRYSTAL,13);w.set(40,28,World.PLANK);
        w.enemies.add(new World.Enemy(w.x,w.y,0));World.Enemy boss=new World.Enemy(w.x,w.y,3);boss.shrine=0;w.enemies.add(boss);
        assertDoesNotThrow(()->w.step(new World.Input(0,false,false)));assertEquals(w.maxHp,w.hp);
        assertEquals(13,w.count(CRYSTAL));assertEquals(1,w.respawns);assertFalse(w.blocked(w.x,w.y,4,10));
        assertTrue(w.enemies.stream().noneMatch(e->e.kind==3));
    }
    @Test void shrineRequiresSigilCannotDuplicateBossAndAwardsEachFragmentOnce() {
        World w=new World(73);int sx=w.shrineX[0],sy=w.shrineY[0]-1;w.x=sx*12+6;w.y=sy*12;
        assertFalse(w.interact(sx,sy));w.add(SIGIL,2);assertTrue(w.interact(sx,sy));assertFalse(w.interact(sx,sy));
        World.Enemy boss=w.enemies.getFirst();boss.hp=1;w.add(IRON_SWORD,1);w.x=boss.x-15;w.y=boss.y;
        assertTrue(w.attack(boss.x,boss.y,IRON_SWORD));assertEquals(1,w.count(RELIC));assertEquals(1,w.wardens);
        w.x=sx*12+6;w.y=sy*12;assertFalse(w.interact(sx,sy));assertEquals(1,w.count(SIGIL));
    }
    @Test void allQuestStagesAndFinalCoreCanCompleteThroughProductionRules() {
        World w=new World(73);w.add(WOOD,40);w.advanceQuests();assertEquals(1,w.quest);
        assertTrue(w.craft(recipe(w,BENCH)));assertTrue(w.place(43,28,BENCH));assertEquals(2,w.quest);
        w.add(STONE,40);assertTrue(w.craft(recipe(w,FURNACE)));w.actionCooldown=0;assertTrue(w.place(44,28,FURNACE));assertEquals(3,w.quest);
        w.add(COPPER,12);for(int i=0;i<4;i++)assertTrue(w.craft(recipe(w,COPPER_BAR)));
        assertTrue(w.craft(recipe(w,COPPER_PICK)));assertEquals(4,w.quest);
        w.add(IRON,27);for(int i=0;i<9;i++)assertTrue(w.craft(recipe(w,IRON_BAR)));
        assertTrue(w.craft(recipe(w,ANVIL)));w.actionCooldown=0;assertTrue(w.place(45,28,ANVIL));
        assertTrue(w.craft(recipe(w,IRON_PICK)));assertEquals(5,w.quest);
        w.add(CRYSTAL,21);w.add(GEL,15);w.add(IRON_SWORD,1);
        for(int i=0;i<3;i++) {
            w.x=44*12;w.y=28*12;assertTrue(w.craft(recipe(w,SIGIL)));
            w.x=w.shrineX[i]*12;w.y=(w.shrineY[i]-1)*12;assertTrue(w.interact(w.shrineX[i],w.shrineY[i]-1));
            World.Enemy boss=w.enemies.stream().filter(e->e.hp>0&&e.kind==3).findFirst().orElseThrow();
            w.x=boss.x-16;w.y=boss.y;boss.hp=1;w.actionCooldown=0;w.attack(boss.x,boss.y,IRON_SWORD);
            w.step(new World.Input(0,false,false));
        }
        w.advanceQuests();assertEquals(6,w.quest);assertEquals(3,w.count(RELIC));
        w.x=44*12;w.y=28*12;w.add(IRON_BAR,6);assertTrue(w.craft(recipe(w,BEACON)));
        w.x=40*12+6;w.y=28*12;assertTrue(w.interact(40,27));assertTrue(w.won);assertEquals(7,w.quest);
        assertEquals(0,w.count(BEACON));assertEquals(0,w.count(RELIC));
    }
    @Test void saveRestoresTilesInventoryCombatAndDeterministicForwardReplay() {
        World a=new World(123);a.add(WOOD,30);a.set(43,28,World.BENCH);a.quest=2;
        a.enemies.add(new World.Enemy(a.x+70,a.y-10,1));a.shots.add(new World.Shot(a.x,a.y,2,-1,12,false,true));
        for(int i=0;i<40;i++)a.step(new World.Input(i<15?1:0,i==2,false));
        World b=SaveCodec.decode(SaveCodec.encode(a)).orElseThrow();
        assertEquals(SaveCodec.encode(a),SaveCodec.encode(b));
        for(int i=0;i<600;i++) {
            World.Input input=new World.Input(i%70<30?1:-1,i%50==0,i%85<8);a.step(input);b.step(input);
            assertEquals(SaveCodec.encode(a),SaveCodec.encode(b),"replay step "+i);
        }
    }
    @Test void malformedOversizedAndTruncatedSavesAreRejectedWithoutMutatingWorld() {
        World w=new World(73);String original=SaveCodec.encode(w);
        assertTrue(SaveCodec.decode(original).isPresent());assertTrue(SaveCodec.decode("not a save").isEmpty());
        assertTrue(SaveCodec.decode("A".repeat(500001)).isEmpty());
        for(int length:new int[]{0,4,original.length()/2,original.length()-12})assertTrue(SaveCodec.decode(original.substring(0,length)).isEmpty());
        w.x=Double.NaN;assertTrue(SaveCodec.decode(SaveCodec.encode(w)).isEmpty());
    }
    @Test void longSimulationKeepsSavesAndTransientPopulationsBounded() {
        World w=new World(73);
        for(int i=0;i<22000;i++)w.step(new World.Input(i%500<250?1:-1,i%50==0,false));
        assertTrue(w.enemies.size()<=18);assertTrue(w.particles.size()<=300);assertTrue(SaveCodec.encode(w).length()<500000);
        assertTrue(SaveCodec.decode(SaveCodec.encode(w)).isPresent());
    }
}
