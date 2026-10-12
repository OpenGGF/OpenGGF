package starfall;

/** Stable save IDs and creator combat profiles; native ROM art supplies each badnik's appearance. */
public enum EnemyType {
    RHINOBOT("Rhinobot",24,9,EnemyType.CHARGE,.9,0),
    MONKEY_DUDE("Monkey Dude",32,9,EnemyType.HOVER,1.1,170),
    BLOOMINATOR("Bloominator",45,14,EnemyType.TURRET,EnemyType.WALK,125),
    SENTINEL("Shrine sentinel",240,20,EnemyType.HOVER,1.35,0),
    SPIKER("Spiker",38,12,EnemyType.WALK,.7,0),
    DRAGONFLY("Dragonfly",28,9,EnemyType.HOVER,1.6,0),
    BATBOT("Batbot",32,10,EnemyType.HOVER,1.4,0),
    PENGUINATOR("Penguinator",42,12,EnemyType.CHARGE,1.7,0),
    SKORP("Skorp",48,14,EnemyType.TURRET,EnemyType.WALK,115),
    RIBOT("Ribot",40,12,EnemyType.HOP,1.1,0),
    JAWZ("Jawz",36,11,EnemyType.HOVER,1.8,0),
    TOXOMISTER("Toxomister",50,15,EnemyType.HOVER,.75,135),
    ORBINAUT("Crystal Orbinaut",60,16,EnemyType.HOVER,.65,160),
    EGG_ROBO("Egg Robo",55,14,EnemyType.HOVER,EnemyType.HOP,110);

    static final int WALK=0,HOP=1,CHARGE=2,HOVER=3,TURRET=4;
    public final String label;
    public final int health,damage,shotPeriod;
    final int motion;
    final double speed;
    EnemyType(String label,int health,int damage,int motion,double speed,int shotPeriod) {
        this.label=label;this.health=health;this.damage=damage;this.motion=motion;this.speed=speed;this.shotPeriod=shotPeriod;
    }
    public boolean flying(){return motion==HOVER;}
    static EnemyType forBiome(Biome biome,int variant) {
        return switch(biome) {
            case ANGEL_ISLAND -> switch(Math.floorMod(variant,3)){case 0->RHINOBOT;case 1->MONKEY_DUDE;default->BLOOMINATOR;};
            case MARBLE_GARDEN -> SPIKER;
            case MUSHROOM_HILL -> DRAGONFLY;
            case CARNIVAL_NIGHT -> BATBOT;
            case ICECAP -> PENGUINATOR;
            case SANDOPOLIS -> SKORP;
            case LAUNCH_BASE -> RIBOT;
            case HYDROCITY -> JAWZ;
            case LAVA_REEF -> TOXOMISTER;
            case HIDDEN_PALACE -> ORBINAUT;
            case SKY_SANCTUARY -> EGG_ROBO;
        };
    }
}
