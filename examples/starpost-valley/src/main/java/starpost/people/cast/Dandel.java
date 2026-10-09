package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;
import static starpost.people.cast.Tunes.WED;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Dandel, a Pocky rabbit: runs the seed stall, worries about everything, and is being undercut
 * by ROBOMART. Speaks in pictures until the Chirp Translator.
 */
public final class Dandel {
    private Dandel() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("dandel", "DANDEL")
                .about("POCKY. RUNS THE SEED STALL. WORRIES A LOT.")
                .body("animal:pocky").home("seed_stall").birthday(SPRING, 15).animal()
                .loves("ring_radish", "spring_tulip", "checker_cauliflower")
                .likes("hill_daffodil", "spin_spud", "totem_leek")
                .kind(Kind.CROP, Taste.LIKE).kind(Kind.SEED, Taste.LIKE)
                .dislikes("egg_plant", "fire_pepper")
                .hates("scrap");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().inside(600, "seed_stall").at(800, "seed_stall", -22).at(1700, "plaza", -20).inside(1900, "seed_stall");
        v.plan().weekdays(WED).inside(600, "seed_stall").at(900, "path").at(1300, "palms_west")
                .at(1600, "plaza", -20).inside(1800, "seed_stall");
        v.plan().rain().inside(600, "seed_stall").at(900, "seed_stall", -22).inside(1600, "seed_stall");
    }

    private static void lines(VillagerDef v) {
        v.line("OH! A CUSTOMER! A REAL ONE! WELCOME TO MY STALL! SEEDS! FRESH! PLEASE BUY SOMETHING!")
                .first().pic("item:ring_radish_seeds", "house", "heart", "!");
        v.line("BUSINESS IS FINE. FINE! IT'S FINE. IS IT FINE? I THINK IT'S FINE.").pic("house", "sweat", "?");
        v.line("I COUNT MY SEEDS EVERY MORNING. AND EVERY NIGHT. AND SOMETIMES AT LUNCH. JUST IN CASE.")
                .pic("item:ring_radish_seeds", "clock", "sweat");
        v.line("ROBOMART SELLS SEEDS FOR HALF MY PRICE. THEY'RE HALF SEEDS. I CHECKED. THEY'RE MOSTLY GRAVEL.")
                .pic("robotnik", "item:ring_radish_seeds", "no", "anger");
        v.line("RING RADISHES ARE THE BEST THING THAT GROWS. NOT BECAUSE OF THE RING. ALSO BECAUSE OF THE RING.")
                .pic("item:ring_radish", "ring", "heart");
        v.line("MY EARS TWITCH WHEN I'M NERVOUS. THEY'RE TWITCHING NOW. THEY'RE ALWAYS TWITCHING.")
                .pic("sweat", "sweat", "...");
        v.line("I WAS INSIDE A MOTOBUG. FOR A LONG TIME. NOW I SELL SEEDS. IT'S BETTER. IT'S MUCH BETTER.")
                .hearts(3).pic("badnik", "sad", "item:ring_radish_seeds", "heart");
        v.line("SPRING IS THE BUSIEST SEASON. BUSY IS GOOD. BUSY IS SCARY. BUSY IS GOOD.").spring()
                .pic("sun", "house", "sweat", "heart");
        v.line("TULIPS FOR THE SUNFLOWER PARADE! GET THEM EARLY! OR LATE. BUT PLEASE GET THEM.").spring()
                .pic("item:spring_tulip", "note", "!");
        v.line("SUMMER SEEDS ARE IN! MELONS! TOMATOES! PEPPERS, WHICH ARE TERRIFYING.").summer()
                .pic("item:emerald_melon", "item:motobug_tomato", "item:fire_pepper", "sweat");
        v.line("IT'S SO HOT MY STALL'S AWNING IS SWEATING. CAN AWNINGS SWEAT? MINE CAN.").summer()
                .pic("sun", "house", "sweat");
        v.line("FALL SEEDS! PUMPKINS! GRAPES! EGG-PLANTS... WHICH I DON'T SELL. ON PRINCIPLE.").fall()
                .pic("item:eggman_pumpkin", "item:marble_grape", "item:egg_plant", "no");
        v.line("NOTHING GROWS IN WINTER. NOBODY BUYS SEEDS. I SIT AND COUNT THEM. IT'S VERY CALMING.").winter()
                .pic("snow", "item:ring_radish_seeds", "zzz");
        v.line("RAIN! GOOD FOR SEEDS. BAD FOR SEED PACKETS. I'VE BROUGHT THEM ALL UNDER THE AWNING.").rain()
                .pic("rain", "item:ring_radish_seeds", "house", "sweat");
        v.line("WEDNESDAY IS MY DAY OFF. I SPEND IT WORRYING ABOUT THE STALL. IT'S RESTFUL.").weekdays(WED)
                .pic("clock", "house", "sweat", "zzz");
        v.line("YOU BUY SEEDS EVERY WEEK. YOU'RE MY BEST CUSTOMER. YOU'RE MY ONLY CUSTOMER. THANK YOU.")
                .hearts(2).pic("farmer", "item:ring_radish_seeds", "heart");
        v.line("I SLEPT ALL NIGHT. ALL OF IT. I HAVEN'T DONE THAT SINCE THE CAPSULE.").hearts(5)
                .pic("zzz", "moon", "heart");
        v.line("THE CO-OP SIGN LOOKS SO NICE. EVERY MORNING I CHECK IT'S STILL THERE. IT IS.")
                .flag("seed_coop").pic("house", "heart", "!");
        v.line("I CAN SAY THINGS NOW! THINGS LIKE: PLEASE BUY SEEDS. AND: THANK YOU FOR BUYING SEEDS.")
                .flag(People.TRANSLATOR).after("tails_2", 6);
        v.line("THE TRANSLATOR MAKES ME SOUND NERVOUS. I'M NOT NERVOUS. I'M... ALERT.").flag(People.TRANSLATOR);
        v.line("TOMORROW IS THE SUNFLOWER PARADE! EVERY TULIP I'VE GOT IS SPOKEN FOR! WELL. TWO ARE.")
                .on(SPRING, 23).pic("item:spring_tulip", "note", "!");
        v.line("IT'S MY BIRTHDAY. I WASN'T GOING TO SAY. I SAID. SORRY. THANK YOU.").birthday()
                .pic("gift", "sweat", "heart");
        v.line("I SAID NO TO ROBOTNIK. ME! NO! I'VE BEEN SHAKING FOR TWO DAYS. GOOD SHAKING.")
                .after("dandel_6", 5).pic("robotnik", "no", "heart", "!");

        v.line("YOU BOUGHT SEEDS AND LEFT BEFORE I FINISHED SAYING THANK YOU. SO: THANK YOU.").farmer("sonic")
                .pic("farmer", "arrow", "heart");
        v.line("YOU PUNCHED A HOLE IN MY COUNTER. BY ACCIDENT. I PUT A FLOWER IN IT. IT'S A FEATURE NOW.")
                .farmer("knuckles").pic("farmer", "house", "item:hill_daffodil", "heart");

        v.again("STILL OPEN! STILL HERE! STILL SEEDS!").pic("house", "item:ring_radish_seeds", "!");
        v.again("SORRY, I WAS COUNTING. WHERE WAS I? 412.").pic("clock", "...");

        v.gift(Taste.LOVE, "OH! OH MY. YOU GREW THIS? IT'S PERFECT. IT'S THE MOST PERFECT ONE I'VE EVER SEEN!")
                .pic("heart", "heart", "!");
        v.gift(Taste.LIKE, "FOR ME? THANK YOU! I'LL PUT IT ON THE STALL. NOT FOR SALE. FOR LOOKING AT.")
                .pic("house", "heart");
        v.gift(Taste.NEUTRAL, "OH! A GIFT! THANK YOU. I DON'T KNOW WHAT IT IS, BUT THANK YOU.").pic("?", "heart");
        v.gift(Taste.DISLIKE, "OH. UM. IT'S VERY... THANK YOU. I'LL KEEP IT. OVER THERE.").pic("sweat", "...");
        v.gift(Taste.HATE, "THAT'S BADNIK. THAT'S A PIECE OF BADNIK. PLEASE TAKE IT AWAY. PLEASE.")
                .pic("badnik", "sad", "no", "!");
        v.birthdayGift("A BIRTHDAY PRESENT? NOBODY'S EVER... I'M NOT CRYING. IT'S POLLEN.").pic("gift", "heart", "sad");
        v.thanks("DEAR {FARMER}. THANK YOU FOR THE LOVELY GIFT. I'VE PUT IT ON THE STALL. NOT FOR SALE. - DANDEL")
                .pic("house", "heart", "gift");
    }

    private static void events(Cast cast) {
        cast.event("dandel_2", "dandel", 2).near("seed_stall", 140).between(900, 1600).dry()
                .summary("A slow day at the stall: Dandel gives you seeds for your first harvest.")
                .place("dandel", 36).face("dandel", 0)
                .say("dandel", "NOBODY'S BOUGHT ANYTHING ALL WEEK. EVERYONE GOES TO ROBOMART. IT'S CHEAPER. IT'S AWFUL.",
                        "house", "no", "robotnik", "sad")
                .emote("dandel", "sad")
                .say("dandel", "BUT YOU ALWAYS STOP AND SAY HELLO. SO... HERE. FOR YOUR NEXT PLANTING.",
                        "farmer", "heart", "item:ring_radish_seeds")
                .give("ring_radish_seeds", 6)
                .say("dandel", "DON'T PAY ME! IF YOU PAY ME I'LL HAVE TO COUNT IT AND I'M VERY TIRED.",
                        "ring", "no", "zzz")
                .emote("farmer", "heart")
                .flag("dandel_seeds_gift");

        cast.event("dandel_4", "dandel", 4).near("seed_stall", 140).between(900, 1600).dry().requires("dandel_2")
                .summary("Dandel asks to sell the farm's crops at her stall.")
                .place("dandel", 36).face("dandel", 0)
                .say("dandel", "I HAD AN IDEA. AN IDEA! I DON'T HAVE MANY. THIS IS ONE.", "!", "sparkle", "?")
                .say("dandel", "WHAT IF I SOLD YOUR CROPS HERE? REAL VALLEY FOOD. ROBOMART CAN'T GROW ANYTHING. IT'S ALL GRAVEL.",
                        "item:ring_radish", "house", "robotnik", "no")
                .hop("dandel")
                .say("dandel", "YOU BRING ME CROPS, I SELL THEM, AND THE VALLEY GETS FED. IS THAT A GOOD IDEA? IT'S A GOOD IDEA.",
                        "farmer", "item:ring_radish", "house", "heart", "?")
                .emote("farmer", "yes")
                .say("dandel", "IT'S A GOOD IDEA!", "heart", "!")
                .flag("dandel_stocks_farm");

        cast.event("dandel_6", "dandel", 6).near("seed_stall", 140).between(1000, 1700).dry().requires("dandel_4")
                .summary("Robotnik tries to buy the stall; Dandel refuses.")
                .music(Tunes.S1, Tunes.BOSS)
                .place("dandel", 30).face("dandel", 1)
                .enter("robotnik", 1, 220, 80)
                .say("robotnik", "LITTLE RABBIT. I HAVE A GENEROUS OFFER. I BUY YOUR STALL. YOU NEVER WORRY AGAIN.")
                .emote("dandel", "sweat")
                .say("robotnik", "THINK OF IT. NO COUNTING. NO CUSTOMERS. JUST A LOVELY ROBOMART-SHAPED SIGN WHERE YOURS USED TO BE.")
                .say("dandel", "...I'D NEVER WORRY AGAIN?", "sweat", "?")
                .emote("farmer", "!")
                .turnTo("dandel")
                .pause(30)
                .say("dandel", "NO. NO! I LIKE WORRYING! IT'S MY STALL AND I'LL WORRY IN IT IF I WANT TO!",
                        "no", "house", "heart", "anger", "!")
                .pose("robotnik", "surprise").pause(30)
                .say("robotnik", "YOU'LL REGRET THIS! PROBABLY! I'LL CHECK BACK!")
                .leave("robotnik", 300)
                .emote("dandel", "heart")
                .say("dandel", "DID I DO THAT? I DID THAT. I NEED TO SIT DOWN. I'M ALREADY SITTING. I'M A RABBIT.",
                        "!", "sweat", "zzz")
                .flag("dandel_refused_egg");

        cast.event("dandel_8", "dandel", 8).near("seed_stall", 160).between(900, 1600).dry().requires("dandel_6")
                .summary("The stall becomes the valley's seed co-op.")
                .music(Tunes.S1, Tunes.SYZ)
                .place("dandel", 36).face("dandel", 0)
                .enter("clementine", -1, 220, 60)
                .say("dandel", "EVERYONE CAME TO A MEETING. A MEETING! ABOUT MY STALL! I THOUGHT I WAS IN TROUBLE.",
                        "who:clementine", "who:pip", "house", "sweat")
                .say("clementine", "SHE WASN'T IN TROUBLE. WE VOTED. THE STALL IS EVERYONE'S NOW. A CO-OP.",
                        "house", "heart", "heart", "heart")
                .say("dandel", "AND THE SIGN SAYS YOUR FARM'S NAME. NEXT TO MINE. BECAUSE YOU STARTED IT.",
                        "house", "farmer", "heart", "!")
                .flag("seed_coop")
                .narrate("THE SEED STALL IS NOW THE DANDEL & {FARM} SEED CO-OP.")
                .emote("dandel", "heart")
                .say("dandel", "I'M STILL GOING TO COUNT THE SEEDS. THAT'S NOT A CO-OP THING. THAT'S A ME THING.",
                        "item:ring_radish_seeds", "clock", "heart")
                .leave("clementine", -260);
    }
}
