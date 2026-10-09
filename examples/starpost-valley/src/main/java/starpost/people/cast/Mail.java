package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;

import starpost.people.Cast;

/**
 * The Flicky post's letters: welcomes, Robotnik's offers and adverts, news, and notes after heart
 * events. Thank-you notes and birthday reminders are written by {@code People} from each villager's
 * own lines.
 */
public final class Mail {
    private Mail() {
    }

    public static void define(Cast cast) {
        welcomes(cast);
        robotnik(cast);
        news(cast);
        afterEvents(cast);
    }

    private static void welcomes(Cast cast) {
        cast.letter("welcome_tails", "tails").firstMorning().notFarmer("tails")
                .text("{FARMER}! WELCOME TO THE VALLEY! I'M IN TOWN, IN THE WORKSHOP UNDER THE WRECKED TORNADO. "
                        + "COME SAY HI! P.S. EAT THIS FIRST. - TAILS")
                .enclose("chili_dog", 1);
        cast.letter("welcome_sonic", "sonic").firstMorning().farmer("tails")
                .text("HEY BUDDY! A FARMER, HUH? PROUD OF YOU. I'M AROUND. YOU KNOW. EVERYWHERE. "
                        + "P.S. I SAVED YOU A CHILI DOG. MOSTLY. - S")
                .enclose("chili_dog", 1);
        cast.letter("welcome_pip", "pip").firstMorning()
                .text("HELLO NEW NEIGHBOUR! I'M PIP! I LIVE IN YOUR SIGNPOST NOW! I DELIVER THE POST! WELCOME HOME!")
                .pic("flicky", "house", "heart", "farmer", "!");
        cast.letter("welcome_totem", "elder_totem").on(SPRING, 2).yearOne()
                .text("THE HILL REMEMBERS EVERY FOOTSTEP. YOURS ARE FAST. PLANT SLOWLY. "
                        + "(I FOUND THIS ON THE MEADOW. I THINK THE TOTEM WROTE IT. - PIP)");
        cast.letter("welcome_clementine", "clementine").on(SPRING, 4).yearOne()
                .text("YOU HAVEN'T BEEN TO THE INN YET. COME AND EAT. THAT'S NOT AN INVITATION. IT'S AN INSTRUCTION.")
                .pic("house", "food", "farmer", "!");
    }

    private static void robotnik(Cast cast) {
        cast.letter("robotnik_offer_1", "robotnik").on(SPRING, 3).yearOne()
                .text("DEAR NEIGHBOUR. CONGRATULATIONS ON YOUR NEW FARM. I WILL BUY IT FROM YOU FOR 500 RINGS. "
                        + "THAT IS A FAIR PRICE FOR DIRT. YOURS IN BUSINESS, DR. IVO ROBOTNIK, PROPRIETOR, EGG.");
        cast.letter("robotnik_ad_spring", "robotnik").on(SPRING, 10)
                .text("EGG MEMBERSHIP! CHEAP SEEDS! LOW PRICES! NO QUESTIONS! ANSWERS EXTRA. "
                        + "VISIT EGG TODAY. EGG: YOU'LL HAVE NO CHOICE.");
        cast.letter("robotnik_offer_2", "robotnik").on(SUMMER, 2).yearOne()
                .text("MY OFFER STANDS. 1,000 RINGS. I HAVE NOTICED YOUR RADISHES. THEY ARE ADEQUATE. "
                        + "THE OFFER WILL NOT GO UP AGAIN. IT WILL, BUT NOT SOON. - DR. I. R.");
        cast.letter("robotnik_race", "robotnik").on(SUMMER, 8)
                .text("NOTICE: THE EGG MOBILE WILL BE ENTERED IN THE GREAT VALLEY RACE. "
                        + "IT IS A VEHICLE. THE RULES DO NOT FORBID VEHICLES. I HAVE READ THE RULES. I WROTE THEM.");
        cast.letter("robotnik_fair", "robotnik").on(FALL, 10)
                .text("AS JUDGE OF THE VALLEY FAIR I MUST REMIND ENTRANTS THAT BRIBES ARE STRICTLY FORBIDDEN. "
                        + "PLEASE DELIVER THEM TO THE CARAVAN AFTER DARK.");
        cast.letter("robotnik_winter", "robotnik").on(WINTER, 3)
                .text("THE VALLEY IS COLD. MY CARAVAN IS WARM. COINCIDENCE? NO. CENTRAL HEATING. "
                        + "AVAILABLE NOW TO EGG MEMBERS. AND TO NOBODY ELSE. HO HO.");
        cast.letter("robotnik_order", "robotnik").on(FALL, 22).whenHearts("robotnik", 3)
                .text("SPECIAL ORDER: 500 EGG-PLANTS. NO QUESTIONS ASKED. NO ANSWERS GIVEN. "
                        + "PAYMENT ON DELIVERY, OR SHORTLY BEFORE THE HEAT DEATH OF THE UNIVERSE. - DR. I. R.");
    }

    private static void news(Cast cast) {
        cast.letter("knuckles_arrives", "pip").on(SUMMER, 1).yearOne().notFarmer("knuckles")
                .text("BIG NEWS! A RED ECHIDNA GLIDED IN LAST NIGHT AND SAT ON THE TOTEM LEDGE. "
                        + "HE GLARED AT ME. I GLARED BACK. HE WON.")
                .pic("who:knuckles", "moon", "house", "anger", "!");
        cast.letter("sonic_knuckles_farm", "sonic").on(SPRING, 2).yearOne().farmer("knuckles")
                .text("KNUX! FARMING! I HEARD AND I HAD TO RUN OVER AND CHECK. IT'S TRUE. IT'S THE BEST THING "
                        + "THAT'S EVER HAPPENED. SEE YOU AROUND, FARMER. - S");
        cast.letter("frost_winter", "frost").on(WINTER, 1)
                .text("IT'S SNOWING! THIS IS THE BEST DAY OF THE YEAR! COME TO MY HUT BY THE WATERFALL. "
                        + "BRING NOTHING WARM.")
                .pic("snow", "snow", "house", "heart", "!");
        cast.letter("barnaby_summer", "barnaby").on(SUMMER, 5)
                .text("THE CHOPPERS ARE BITING. NOT THE FISH. THE CHOPPERS. BRING A LONG ROD AND SHORT TROUSERS.")
                .pic("badnik", "fish", "sweat", "!");
        cast.letter("tails_fall", "tails").on(FALL, 1).notFarmer("tails")
                .text("THE LEAVES ARE TURNING! SO IS MY PROPELLER! (A LITTLE.) COME SEE! - TAILS");
        cast.letter("dandel_summer_seeds", "dandel").on(SUMMER, 1)
                .text("SUMMER SEEDS ARE IN! PLEASE COME. PLEASE. I ORDERED TOO MANY. - DANDEL")
                .pic("sun", "item:emerald_melon_seeds", "house", "sweat", "!");
        cast.letter("hazel_spring", "hazel").on(SPRING, 20).yearOne()
                .text("I RAN TO YOUR FARM AND BACK TO GIVE YOU THIS LETTER BUT THEN I FORGOT TO GIVE IT TO YOU. "
                        + "SO PIP IS BRINGING IT. IT SAYS HI. - HAZEL")
                .pic("who:hazel", "arrow", "house", "heart");
        cast.letter("rusty_after_2", "rusty").afterEvent("rusty_2")
                .text("DEAR FRIEND. I HAVE CALCULATED THAT A DAY OFF REQUIRES A HOBBY. I HAVE CHOSEN LOOKING AT "
                        + "WATER. IF YOU HAVE OTHER SUGGESTIONS, PLEASE SUBMIT THEM IN WRITING. - RUSTY");
        cast.letter("totem_winter", "elder_totem").on(WINTER, 15)
                .text("THE VALLEY SLEEPS. ITS DREAMS ARE OF YOU. (PIP SAYS THE TOTEM SAID THIS. PIP WAS VERY COLD.)");
        cast.letter("tails_hearts_6", "tails").whenHearts("tails", 6).notFarmer("tails")
                .text("{FARMER}, I JUST WANTED TO SAY YOU'RE MY BEST FRIEND. THAT'S ALL. I WROTE IT DOWN SO I "
                        + "COULDN'T CHICKEN OUT. SORRY CLEMENTINE. - TAILS");
    }

    private static void afterEvents(Cast cast) {
        cast.letter("dandel_after_2", "dandel").afterEvent("dandel_2")
                .text("THANK YOU FOR STOPPING AT MY STALL. YOU MADE MY WEEK. MY WHOLE WEEK. I'M NOT CRYING, "
                        + "IT'S POLLEN. - DANDEL")
                .pic("house", "farmer", "heart", "sad");
        cast.letter("clementine_after_4", "clementine").afterEvent("clementine_4")
                .text("PAGE TWO: CHILI. INGREDIENTS: MORE. A PEPPER. ANOTHER PEPPER. METHOD: STAND BACK. "
                        + "I'VE ENCLOSED A SAMPLE. EAT IT ALL. - CLEMENTINE")
                .pic("food", "item:fire_pepper", "item:fire_pepper", "!")
                .enclose("chili_dog", 1);
        cast.letter("tails_after_4", "tails").afterEvent("tails_4")
                .text("ENCLOSED: ONE DIAGRAM OF THE DRIZZLER. DON'T LOSE IT. I HAVE TWELVE MORE COPIES. "
                        + "ALSO SOME SCRAP, FOR LUCK. - TAILS")
                .enclose("scrap", 3);
        cast.letter("robotnik_after_4", "robotnik").afterEvent("robotnik_4")
                .text("YOU MAY HAVE REFUSED MY GENEROUS OFFER. BUT REMEMBER: EGG IS PATIENT. "
                        + "EGG IS NOT PATIENT. EGG IS WAITING FOR YOU TO CHANGE YOUR MIND. QUICKLY. - DR. I. R.");
        cast.letter("pip_after_4", "pip").afterEvent("pip_4")
                .text("I READ MUM'S LETTER AGAIN. AND AGAIN. I'M KEEPING IT IN YOUR SIGNPOST. IT'S THE SAFEST "
                        + "PLACE I KNOW. - PIP")
                .pic("flicky", "flicky", "heart", "house");
        cast.letter("knuckles_after_6", "knuckles").afterEvent("knuckles_6")
                .text("THE GRAPES NEED A TRELLIS. AND SUN. AND TO BE LEFT ALONE. LIKE ME. - K");
        cast.letter("barnaby_after_4", "barnaby").afterEvent("barnaby_4")
                .text("TOLD IT AT THE INN LAST NIGHT TOO. THE HAT GOT BIGGER. IT HAD TWO FEATHERS. - BARNABY")
                .pic("house", "note", "badnik", "!");
    }
}
