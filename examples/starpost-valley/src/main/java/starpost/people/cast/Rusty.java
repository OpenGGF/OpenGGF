package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;
import static starpost.people.cast.Tunes.FRI;
import static starpost.people.cast.Tunes.SUN;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Rusty, an Egg Robo who left Robotnik after a firmware fault and tends the Lamppost Inn. Literal
 * and gentle; learning what it means to want things.
 */
public final class Rusty {
    private Rusty() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("rusty", "RUSTY")
                .about("EGG ROBO. TENDS THE INN. LEFT ROBOTNIK AFTER A FAULT.")
                .body("eggrobo").home("inn").birthday(SPRING, 26)
                .loves("oil_can", "battery", "scrap")
                .likes("marble_chip", "palm_wood")
                .kind(Kind.MATERIAL, Taste.LIKE).kind(Kind.MINERAL, Taste.LIKE).kind(Kind.FOOD, Taste.DISLIKE)
                .hates("frost_ring");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().inside(600, "inn").at(800, "inn_porch").inside(1000, "inn").at(1500, "plaza", -30)
                .inside(1700, "inn");
        v.plan().weekdays(SUN).inside(600, "inn").at(1200, "inn_porch").inside(1500, "inn");
        v.plan().weekdays(SUN).when("rusty_day_off").inside(600, "inn").at(900, "waterfall", 50)
                .at(1300, "palms_east").at(1600, "plaza", 60).inside(1800, "inn");
        v.plan().rain().inside(600, "inn");
    }

    private static void lines(VillagerDef v) {
        v.line("GREETINGS. I AM UNIT E-R 0451. THE VALLEY CALLS ME RUSTY. I DO NOT KNOW WHY. I AM SEVENTY PERCENT RUST.")
                .first();
        v.line("WELCOME TO THE LAMPPOST INN. PLEASE WIPE YOUR FEET. I HAVE ALREADY WIPED THEM FOR YOU. IN MY MIND.");
        v.line("MY OLD ORDERS WERE: PATROL, CAPTURE, OBEY. MY NEW ORDERS ARE: SWEEP, POUR, SMILE. I AM BAD AT SMILE.");
        v.line("I HAD A FIRMWARE FAULT. NOW WHEN I SEE A RABBIT, I DO NOT CAPTURE IT. I SAY GOOD MORNING.");
        v.line("CLEMENTINE SAYS I MAY HAVE A HOBBY. I HAVE CHOSEN COUNTING. I HAVE COUNTED THE INN'S SPOONS. 41.");
        v.line("THE LAMP ON THE INN SPINS WHEN IT IS OPEN. I OIL IT EVERY MORNING. IT IS MY FAVOURITE THING TO OIL.");
        v.line("ROBOTNIK VISITS ON SUNDAYS. HE ORDERS TEA AND ASKS IF I WOULD LIKE MY OLD JOB BACK. I SAY NO, THANK YOU.");
        v.line("SPRING. MY SENSORS DETECT POLLEN. I HAVE SNEEZED. I DID NOT KNOW I COULD SNEEZE.").spring();
        v.line("THE BIRDS ARE BUILDING NESTS IN THE INN'S GUTTER. I HAVE MOVED THE GUTTER.").spring();
        v.line("SUMMER. MY CHASSIS IS HOT ENOUGH TO COOK EGGS. CLEMENTINE ASKED ME NOT TO SAY THAT AGAIN.").summer();
        v.line("THE INN IS BUSY IN SUMMER. I HAVE POURED 212 DRINKS. I HAVE SPILLED 3. I APOLOGISED 212 TIMES.")
                .summer();
        v.line("THE LEAVES FALL. I SWEEP THEM. THEY FALL AGAIN. IT IS A GOOD JOB. IT NEVER ENDS.").fall();
        v.line("WINTER. MY JOINTS CREAK. I AM TOLD THIS IS CALLED GETTING OLD. I AM FOUR.").winter();
        v.line("I SAW SNOW FOR THE FIRST TIME. IT WAS COLD. IT WAS BEAUTIFUL. I DO NOT HAVE A WORD FOR BOTH.").winter();
        v.line("RAIN. I AM STAYING INSIDE. MY NAME IS A WARNING.").rain();
        v.line("THE INN SMELLS OF SOUP WHEN IT RAINS. I CANNOT SMELL. I AM TOLD IT IS NICE.").rain();
        v.line("JUKEBOX NIGHT. I DO NOT DANCE. I OSCILLATE.").weekdays(FRI);
        v.line("YOU VISIT OFTEN. MY LOG SAYS THIS IS CALLED A FRIEND. I HAVE FLAGGED THE ENTRY AS IMPORTANT.")
                .hearts(3);
        v.line("I HAVE A QUESTION. WHAT DO YOU WANT? NOT TODAY. IN GENERAL. I AM TRYING TO FIND OUT HOW WANTING WORKS.")
                .hearts(4);
        v.line("I REPAIRED THE STAR POST AT THE GATE. NOW IT CHIMES WHEN YOU PASS. I LISTEN FOR IT.")
                .flag("starposts_repaired");
        v.line("I TOOK MY DAY OFF. I LOOKED AT THE WATERFALL FOR FOUR HOURS. I THINK I ENJOYED IT. I WILL CHECK.")
                .flag("rusty_day_off");
        v.line("NOW I CAN UNDERSTAND THE ANIMALS. THEY WERE SAYING THANK YOU THIS WHOLE TIME. I DID NOT KNOW.")
                .flag(People.TRANSLATOR);
        v.line("YOU, SONIC, DESTROYED 2,847 OF MY BROTHERS. I HAVE DECIDED THIS WAS CORRECT. PLEASE PASS THE SALT.")
                .farmer("sonic").hearts(2);
        v.line("TAILS. YOU TAKE BADNIKS APART AND MAKE THEM HELPFUL. I WOULD LIKE TO THANK YOU. ON BEHALF OF MY KIND.")
                .farmer("tails").hearts(2);
        v.line("KNUCKLES. YOU PUNCHED ME ONCE. IN 1994. I HAVE FORGIVEN YOU. I HAVE NOT FORGOTTEN. I AM A ROBOT.")
                .farmer("knuckles").hearts(2);
        v.line("TODAY IS MY ACTIVATION DAY. CLEMENTINE CALLS IT A BIRTHDAY. I WILL CALL IT THAT TOO.").birthday();

        v.again("I AM STILL HERE. I AM ALWAYS HERE. EXCEPT SUNDAYS, NOW.");
        v.again("HOW MAY I HELP? I CAN SWEEP SOMETHING. I CAN SWEEP ANYTHING.");

        v.gift(Taste.LOVE, "OH. OH. MY SENSORS ARE DOING SOMETHING. I THINK THIS IS WHAT DELIGHT FEELS LIKE.");
        v.gift(Taste.LIKE, "THANK YOU. I WILL STORE IT CAREFULLY. ALPHABETICALLY.");
        v.gift(Taste.NEUTRAL, "A GIFT. I DO NOT KNOW WHAT IT IS FOR. I WILL HOLD IT UNTIL I DO.");
        v.gift(Taste.DISLIKE, "I CANNOT EAT. BUT I WILL HOLD IT. HOLDING IS ALSO NICE.");
        v.gift(Taste.HATE, "MOISTURE. MY ONE WEAKNESS. AND MY NAME. PLEASE TAKE IT AWAY QUICKLY.");
        v.birthdayGift("A PRESENT ON MY ACTIVATION DAY. I AM SAVING THIS MOMENT TO PERMANENT MEMORY. DONE. AND AGAIN.");
        v.thanks("DEAR FRIEND. THANK YOU FOR THE GIFT. I HAVE LOOKED AT IT 600 TIMES. IT IS STILL GOOD. - RUSTY");
    }

    private static void events(Cast cast) {
        cast.event("rusty_2", "rusty", 2).near("inn", 160).between(800, 1500).dry()
                .summary("What is a day off? Rusty decides to try one (his Sundays change).")
                .enter("rusty", 1, 200, 40)
                .say("rusty", "EXCUSE ME. I HAVE A QUESTION THAT IS NOT IN MY MANUAL.")
                .say("rusty", "CLEMENTINE TOLD ME TO TAKE A DAY OFF. I SEARCHED MY MEMORY. I HAVE NEVER HAD A DAY OFF.")
                .say("rusty", "WHAT IS A DAY OFF? IS IT A DAY THAT IS SWITCHED OFF? WILL I BE SWITCHED OFF?")
                .emote("farmer", "sweat")
                .narrate("YOU EXPLAIN THAT A DAY OFF IS A DAY TO DO WHATEVER YOU LIKE.")
                .emote("rusty", "?")
                .say("rusty", "WHATEVER I LIKE. I DO NOT KNOW WHAT I LIKE. I WILL TAKE THE DAY OFF TO FIND OUT.")
                .say("rusty", "SUNDAY. I WILL BEGIN SUNDAY. I HAVE SCHEDULED SPONTANEITY FOR NINE O'CLOCK.")
                .flag("rusty_day_off")
                .leave("rusty", 240);

        cast.event("rusty_4", "rusty", 4).near("farm_gate", 160).between(800, 1700).dry().requires("rusty_2")
                .summary("Rusty repairs the gate's Star Post: it chimes again.")
                .enter("rusty", 1, 200, 40)
                .say("rusty", "I HAVE BEEN REPAIRING THINGS. ON MY DAYS OFF. I FIND I LIKE REPAIRING THINGS.")
                .say("rusty", "THIS STAR POST WAS BROKEN. IT HAS NOT CHIMED SINCE THE BADNIKS CAME. WATCH.")
                .sfx(Sfx.STARPOST).pause(30)
                .emote("rusty", "note")
                .say("rusty", "IT CHIMES WHEN YOU COME HOME. I THOUGHT YOU SHOULD HAVE SOMETHING THAT SAYS WELCOME HOME.")
                .say("rusty", "I HAVE NEVER BEEN WELCOMED HOME. I AM TOLD IT IS NICE.")
                .emote("farmer", "heart")
                .say("rusty", "...WELCOME HOME, RUSTY. THERE. I HAVE TRIED IT. IT IS NICE.")
                .flag("starposts_repaired")
                .leave("rusty", 260);
    }
}
