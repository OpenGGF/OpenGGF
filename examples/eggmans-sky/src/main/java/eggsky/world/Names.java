package eggsky.world;

import eggsky.core.Rng;

/**
 * Procedural names in the spirit of No Man's Sky's alien syllables, with Dr. Eggman's habit of
 * naming everything after himself mixed in.
 */
public final class Names {
    private Names() {
    }

    private static String pick(Rng rng, String options) {
        String[] parts = options.split(",");
        return parts[rng.nextInt(parts.length)];
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** An alien word of two to four syllables. */
    public static String word(Rng rng) {
        String onset = "b,br,ch,d,dr,f,g,gl,h,j,k,kr,l,m,n,p,pl,qu,r,s,sh,st,t,th,tr,v,vr,w,x,y,z,zh";
        String vowel = "a,e,i,o,u,ae,ai,au,ee,io,ou,y,oa";
        String coda = ",,,,n,r,s,x,th,k,l,m,nd,rk,st";
        int syllables = rng.range(2, 3) + (rng.chance(0.15) ? 1 : 0);
        StringBuilder sb = new StringBuilder();
        if (rng.chance(0.25)) {
            sb.append(pick(rng, vowel));
        }
        for (int i = 0; i < syllables; i++) {
            sb.append(pick(rng, onset)).append(pick(rng, vowel));
            if (i == syllables - 1 || rng.chance(0.3)) {
                sb.append(pick(rng, coda));
            }
        }
        String s = sb.toString();
        return cap(s.length() > 11 ? s.substring(0, 11) : s);
    }

    /** A star system's name. */
    public static String system(long seed) {
        Rng rng = new Rng(seed);
        if (rng.chance(0.12)) {
            return pick(rng, "Robotnik,Eggman,Ivo,Moustachio,Egg,Badnik,Mecha,Metal,Death Egg") + " "
                    + pick(rng, "Prime,Major,Minor,Nebula,Cluster,Expanse,Reach,Belt,Drift") ;
        }
        String base = word(rng);
        int style = rng.nextInt(4);
        return switch (style) {
            case 0 -> base + "-" + pick(rng, "Ix,Pol,Kor,Zan,Yt,Ul,Vex") + rng.range(1, 99);
            case 1 -> base + " " + pick(rng, "Prime,Major,Minor,Alpha,Beta,Gamma,Delta,Omega");
            default -> base;
        };
    }

    /** A planet's name, given its system's name and index. */
    public static String planet(long seed, String systemName, int index) {
        Rng rng = new Rng(seed);
        if (rng.chance(0.18)) {
            String[] roman = {"I", "II", "III", "IV", "V", "VI"};
            return pick(rng, "Eggmanland,Robotnikia,New Eggopolis,Ivotopia,Egg Station,Fort Moustache,"
                    + "Planet Egg,Mount Ivo,Eggberg,Robo Haven") + " " + roman[Math.min(5, index)];
        }
        String w = word(rng);
        return switch (rng.nextInt(5)) {
            case 0 -> w + " " + pick(rng, "IV,VII,XI,Prime,Minor,Secundus,Tertius");
            case 1 -> pick(rng, "New ,Old ,Lost ,") + w;
            default -> w;
        };
    }

    /** A creature species: an alien genus plus a Badnik-flavoured epithet. */
    public static String species(long seed, String body) {
        Rng rng = new Rng(seed);
        String genus = word(rng);
        String epithet = switch (rng.nextInt(5)) {
            case 0 -> body.toLowerCase() + "us";
            case 1 -> pick(rng, "robotica,mechanis,eggmani,feralis,ivoensis,ferrum,rotorus,chromis");
            default -> word(rng).toLowerCase();
        };
        return genus + " " + epithet;
    }

    /** A plant species. */
    public static String plant(long seed) {
        Rng rng = new Rng(seed);
        return word(rng) + " " + pick(rng, "Bulb,Frond,Shroom,Spire,Bloom,Thorn,Stalk,Cap,Fern,Root,Pod,Coral");
    }

    /** A mineral. */
    public static String mineral(long seed) {
        Rng rng = new Rng(seed);
        return word(rng) + pick(rng, "ite,ium,ar,ese,ine,ox,olite,erite");
    }
}
