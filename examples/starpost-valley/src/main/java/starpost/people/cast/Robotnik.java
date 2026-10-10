package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;
import static starpost.people.cast.Tunes.SUN;
import static starpost.people.cast.Tunes.WED;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Dr. Robotnik, on foot (Sonic 1's Scrap Brain cutscene sprite), living in the Egg Mobile
 * caravan behind ROBOMART. A grandiloquent schemer whose schemes are genuine and doomed. Never a
 * Partner.
 */
public final class Robotnik {
    private Robotnik() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("robotnik", "DR. ROBOTNIK")
                .about("PROPRIETOR OF ROBOMART. LIVES IN A CARAVAN. SCHEMING.")
                .body("robotnik").home("caravan").birthday(FALL, 20)
                .loves("egg_plant", "eggman_pumpkin", "egg")
                .likes("scrap", "scrap_amaranth", "chili_dog")
                .kind(Kind.MINERAL, Taste.LIKE).kind(Kind.CROP, Taste.DISLIKE)
                .hates("ring_radish", "spring_tulip", "hill_daffodil", "sunflower");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().inside(600, "robomart").at(900, "caravan").at(1100, "plaza", 40).at(1400, "seed_stall", 46)
                .at(1600, "caravan").inside(1900, "robomart");
        v.plan().weekdays(SUN).inside(600, "robomart").at(1000, "inn_porch", 30).at(1300, "capsule", -50)
                .at(1600, "caravan").inside(1900, "robomart");
        v.plan().weekdays(WED).inside(600, "robomart").farm(1000, "signpost", 30, 14).at(1300, "plaza", 40)
                .at(1600, "caravan").inside(1900, "robomart");
        v.plan().rain().inside(600, "robomart").at(1300, "caravan").inside(1500, "robomart");
    }

    private static void lines(VillagerDef v) {
        v.line("AH! THE NEW NEIGHBOUR. DOCTOR IVO ROBOTNIK, PROPRIETOR OF ROBOMART. WE ARE GOING TO BE SUCH GOOD FRIENDS.")
                .first();
        v.line("YOU! OF ALL THE VALLEYS IN ALL THE ZONES... NO MATTER. BYGONES. BUY SOMETHING.").first().farmer("sonic");
        v.line("ROBOMART. THE ROBO IS FOR ROBOTNIK. THE MART IS FOR... MARTIAL GENIUS. ALSO ME.");
        v.line("I AM A SIMPLE SHOPKEEPER. A SIMPLE SHOPKEEPER WITH AN IQ OF 300 AND A CARAVAN FULL OF LASERS.");
        v.line("MY BADNIKS NEVER COMPLAINED, NEVER SLEPT AND NEVER ASKED FOR A DAY OFF. AND THEN ONE DID. RUSTY.");
        v.line("THAT CAPSULE ON THE HILL WAS A MASTERPIECE. NOBODY APPRECIATES A GOOD PRISON ANY MORE.");
        v.line("I DO NOT FARM. FARMING IS WAITING. I HAVE BUILT MACHINES SO THAT I NEVER HAVE TO WAIT.");
        v.line("HO HO! YOU AGAIN. YOU'RE LIKE A PEST. A PEST WITH A WATERING CAN.").below(3);
        v.line("SPRING. EVERYTHING IS GREEN AND HOPEFUL. DISGUSTING. I'M ORDERING GREY PAINT.").spring();
        v.line("THE SUNFLOWERS ARE STARING AT ME. I KNOW WHAT THEY'RE DOING. I INVENTED STARING.").spring();
        v.line("THE HEAT! MY MOUSTACHE IS WILTING. A WILTED MOUSTACHE IS A SIGN OF WEAKNESS.").summer();
        v.line("THE GREAT VALLEY RACE. I WILL ENTER THE EGG MOBILE. THERE IS NO RULE AGAINST IT. I CHECKED. I WROTE THEM.")
                .summer();
        v.line("FALL! THE SEASON OF THE EGGMAN PUMPKIN. FINALLY, A VEGETABLE WITH TASTE.").fall();
        v.line("AS THIS YEAR'S FAIR JUDGE I AM COMPLETELY IMPARTIAL. ALSO, I LOVE A BRIBE.").fall();
        v.line("WINTER. A TIME FOR REFLECTION. I REFLECT ON MY SUCCESSES. IT TAKES ALL WINTER.").winter();
        v.line("SNOW IS SIMPLY RAIN THAT HAS GIVEN UP. I DESPISE QUITTERS.").winter();
        v.line("RAIN ON THE CARAVAN ROOF. PLINK. PLINK. PLINK. I WILL BUILD A ROBOT THAT CATCHES EVERY DROP.").rain();
        v.line("SONIC. HOW IS THE LITTLE FARM? I WOULD HATE FOR ANYTHING TO... GROW ON IT.").farmer("sonic");
        v.line("TAILS, MY BOY. A MIND LIKE YOURS, WASTED ON TURNIPS. COME WORK FOR ROBOMART. DENTAL PLAN.")
                .farmer("tails");
        v.line("THE ECHIDNA! STILL GULLIBLE? I HAVE AN EMERALD TO SELL YOU. IT'S GREEN. MOSTLY.")
                .farmer("knuckles");
        v.line("YOU KNOW, YOU ARE NOT ENTIRELY INTOLERABLE. ENTIRELY IS A STRONG WORD.").hearts(3);
        v.line("SOMETIMES I WONDER WHAT I WOULD HAVE BUILT IF I HAD HAD A FARM. NOTHING. I'D HAVE BUILT NOTHING.")
                .hearts(5);
        v.line("THE MOTOBUG WAS MEANT TO HAVE A SEAT. THEN THE ACCOUNTANTS GOT INVOLVED. THE ACCOUNTANT WAS ME.")
                .hearts(6);
        v.line("BETWEEN US, I HAVE A NEW SCHEME. IT IS VERY EVIL. YOU WILL HATE IT. I WILL TELL YOU ALL ABOUT IT.")
                .hearts(7);
        v.line("SO. THE ANIMALS CAN TALK NOW. I SUPPOSE THEY HAVE ALL BEEN SAYING VERY UNKIND THINGS ABOUT ME.")
                .flag(People.TRANSLATOR);
        v.line("TOMORROW I JUDGE THE FAIR. I HAVE ALREADY CHOSEN THE WINNER. IT IS A PUMPKIN WITH MY FACE.")
                .on(FALL, 15);
        v.line("YOU KNOW IT'S MY BIRTHDAY? I DID NOT TELL ANYONE. I HAVE BEEN WAITING ALL DAY FOR SOMEONE TO NOTICE.")
                .birthday();
        v.line("THE BLUEPRINT STAYS BETWEEN US. IF ANYONE ASKS, IT WAS A DEATH RAY.").after("robotnik_8", 5);

        v.again("YES, YES, I'M STILL HERE. ROBOMART NEVER CLOSES. EXCEPT AT SEVEN.");
        v.again("YOU HAVE THE LOOK OF A CUSTOMER. NO? THE LOOK OF A NUISANCE, THEN.");

        v.gift(Taste.LOVE, "HO HO HO! NOW THIS IS A GIFT FIT FOR A GENIUS! YOU MAY LIVE. FOR NOW.");
        v.gift(Taste.LIKE, "HM. ACCEPTABLE. I SHALL ADD IT TO MY COLLECTION OF ACCEPTABLE THINGS.");
        v.gift(Taste.NEUTRAL, "A GIFT? FOR ME? WHAT'S THE CATCH? THERE'S NO CATCH? HOW SUSPICIOUS.");
        v.gift(Taste.DISLIKE, "VEGETABLES. HOW... RUSTIC. I SHALL FEED IT TO A BADNIK.");
        v.gift(Taste.HATE, "A FLOWER?! DO I LOOK LIKE A MAN WHO OWNS A VASE? GET IT AWAY FROM ME!");
        v.birthdayGift("A PRESENT. ON MY BIRTHDAY. ...I WILL NOT CRY IN FRONT OF A HEDGEHOG. OR WHOEVER YOU ARE.");
        v.thanks("TO THE FARMER. YOUR GIFT WAS ADEQUATE. I HAVE PUT IT IN THE CARAVAN, NEXT TO THE LASERS. - DR. I. R.");
    }

    private static void events(Cast cast) {
        cast.event("robotnik_2", "robotnik", 2).near("plaza", 180).between(1000, 1700).dry()
                .summary("The salesman: a free sample of Egg-plant seeds and the Egg Membership pitch.")
                .enter("robotnik", 1, 220, 46)
                .say("robotnik", "STEP RIGHT UP! STEP RIGHT UP! YOU, FARMER! YOU LOOK LIKE A PERSON OF TASTE.")
                .pose("robotnik", "laugh").pause(30).pose("robotnik", "idle")
                .say("robotnik", "INTRODUCING THE ROBOMART MEMBERSHIP! CHEAPER SEEDS! FASTER CROPS! SOME LASERS!")
                .emote("farmer", "?")
                .say("robotnik", "AND FOR YOU, A FREE SAMPLE. EGG-PLANT SEEDS. THEY GROW A LITTLE MOUSTACHE. ONE PERCENT OF THE TIME.")
                .give("egg_plant_seeds", 5)
                .say("robotnik", "NO OBLIGATION! NONE AT ALL! ...THE OBLIGATION IS IN THE SMALL PRINT.")
                .pose("robotnik", "laugh").pause(40)
                .leave("robotnik", 280);

        cast.event("robotnik_4", "robotnik", 4).onFarm().between(900, 1600).dry().requires("robotnik_2")
                .summary("The offer: Robotnik tries to buy the farm, and is refused.")
                .music(Tunes.S1, Tunes.BOSS)
                .enter("robotnik", 1, 240, 44)
                .say("robotnik", "WHAT A CHARMING LITTLE FARM. SO MUCH DIRT. SO MUCH POTENTIAL. FOR A CAR PARK.")
                .say("robotnik", "I'LL BE BRIEF. I WILL BUY IT. FIVE THOUSAND RINGS. CASH. WELL, RINGS.")
                .emote("farmer", "no")
                .say("robotnik", "SIX THOUSAND!")
                .emote("farmer", "anger")
                .pose("robotnik", "surprise").pause(30)
                .say("robotnik", "...EVERYONE HAS A PRICE, {FARMER}. YOURS IS SIMPLY HIGHER. I CAN WAIT.")
                .pose("robotnik", "idle")
                .say("robotnik", "I CANNOT WAIT. I HAVE NEVER BEEN ABLE TO WAIT. GOOD DAY!")
                .flag("robotnik_offer_refused")
                .leave("robotnik", 280);

        cast.event("robotnik_6", "robotnik", 6).near("caravan", 180).between(1000, 1800).dry()
                .requires("robotnik_4")
                .summary("The judge: Robotnik appoints himself Fair judge and wants an Eggman Pumpkin grown.")
                .enter("robotnik", 1, 220, 44)
                .say("robotnik", "GOOD NEWS! THE VALLEY FAIR HAS A NEW JUDGE. ME. I WAS THE ONLY APPLICANT.")
                .say("robotnik", "I WAS ALSO THE ONLY ONE ON THE INTERVIEW PANEL. IT WENT VERY WELL.")
                .say("robotnik", "I WANT TO SEE AN EGGMAN PUMPKIN AT THE FAIR. A GIANT ONE. WITH MY FACE. FOR SCIENCE.")
                .give("eggman_pumpkin_seeds", 3)
                .emote("farmer", "sweat")
                .say("robotnik", "THEY WILL ONLY GROW MY FACE IF YOU PLANT THEM IN THREES. I DESIGNED THEM THAT WAY. OBVIOUSLY.")
                .pose("robotnik", "laugh").pause(40)
                .flag("robotnik_fair_judge")
                .leave("robotnik", 260);

        cast.event("robotnik_8", "robotnik", 8).near("caravan", 200).between(2000, 2500).dry()
                .requires("robotnik_6")
                .summary("The blueprint: badniks were meant to be rides.")
                .music(Tunes.S1, Tunes.SLZ)
                .place("robotnik", 50).face("robotnik", 1)
                .pause(40)
                .say("robotnik", "OH. IT'S YOU. COME TO GLOAT? I'M NOT DOING ANYTHING EVIL. I'M LOOKING AT A PIECE OF PAPER.")
                .face("robotnik", 0)
                .say("robotnik", "MY FIRST BLUEPRINT. THE MOTOBUG. LOOK. THERE. A SEAT. AND A LITTLE HANDLE.")
                .say("robotnik", "THEY WERE MEANT TO BE RIDES. FOR THE ANIMALS. A FUNFAIR THAT RAN ITSELF.")
                .emote("farmer", "?")
                .say("robotnik", "THEN I NEEDED A POWER SOURCE. AND THE ANIMALS WERE RIGHT THERE. IT WAS... EFFICIENT.")
                .emote("robotnik", "...")
                .say("robotnik", "KEEP IT. I CAN'T BUILD IT. NOBODY WOULD RIDE IT. NOT AFTER WHAT I DID.")
                .flag("blueprint_motobug_ride").sfx(Sfx.GRAB)
                .narrate("YOU GOT ROBOTNIK'S MOTOBUG RIDE BLUEPRINT.")
                .say("robotnik", "NOW GO AWAY. AND IF YOU TELL ANYONE I WAS NICE, I WILL DENY IT WITH A LASER.")
                .leave("robotnik", 220);
    }
}
