package towerdefense.core;

/** Replays an ordinary buying strategy for scene debug/capture jumps. No state cheats. */
public final class CampaignDemo {
    private CampaignDemo() { }

    public static void prepare(Battlefield battle) {
        int[] kinds = {Catalog.SNALE, Catalog.MORTAR, Catalog.SPIKER, Catalog.ORBINAUT, Catalog.EGG_ROBO,
                Catalog.BUGGERNAUT, Catalog.SNALE, Catalog.SPIKER, Catalog.MORTAR, Catalog.EGG_ROBO};
        for (int site = 0; site < Battlefield.SITES; site++) {
            if (battle.tower(site) == null) battle.build(site, kinds[site]);
        }
        for (int level = 2; level <= 3; level++) {
            for (int site = 0; site < Battlefield.SITES; site++) {
                if (battle.tower(site) != null && battle.tower(site).level() < level) battle.upgrade(site);
            }
        }
        if (battle.doorHp() < 160) battle.repair();
    }

    public static Battlefield toWave(int number) {
        if (number < 1 || number > Catalog.WAVES + 1) throw new IllegalArgumentException("Wave " + number);
        Battlefield battle = new Battlefield();
        while (!battle.finished() && battle.wave() < number) {
            prepare(battle);
            battle.startWave();
            if (battle.wave() == number) break;
            int limit = 0;
            while (battle.phase().equals(Battlefield.WAVE) && limit++ < 20000) {
                battle.update();
                if (battle.birds().stream().anyMatch(f -> f.x() > 325)) battle.bomb();
            }
            if (limit >= 20000) throw new IllegalStateException("Demonstration wave did not terminate");
        }
        return battle;
    }
}
