package rttt.world;

public final class Track {
    private Track() {}

    public static final int ROAD_Z0 = -1;
    public static final int ROAD_Z1 = 2;
    public static final double CAR_Z = 0.5;
    public static final int BASE_Y = 90;
    public static final int BAND = 448;
    public static final int START_X = 0;
    public static final int FRONT_Z = 5;
    public static final int BACK_Z = -104;
    public static final int RAMP_PERIOD = 613;

    public static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    public static long hash(int a, int b, int seed) {
        return mix(a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + seed * 0x165667B19E3779F9L);
    }

    public static double rand(int a, int b, int seed) {
        return (hash(a, b, seed) >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    public static double noise1(double x, int seed) {
        int i = (int) Math.floor(x);
        double f = x - i;
        double a = rand(i, 0, seed) * 2 - 1;
        double b = rand(i + 1, 0, seed) * 2 - 1;
        return a + (b - a) * smooth(f);
    }

    public static double noise2(double x, double z, int seed) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = smooth(x - ix), fz = smooth(z - iz);
        double a = rand(ix, iz, seed), b = rand(ix + 1, iz, seed);
        double c = rand(ix, iz + 1, seed), d = rand(ix + 1, iz + 1, seed);
        double v = (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
        return v * 2 - 1;
    }

    public static double fbm2(double x, double z, int seed, int oct) {
        double sum = 0, amp = 1, norm = 0;
        for (int o = 0; o < oct; o++) {
            sum += noise2(x, z, seed + o * 31) * amp;
            norm += amp;
            amp *= 0.5;
            x *= 2.03;
            z *= 2.03;
        }
        return sum / norm;
    }

    public static double smoothHeight(double x) {
        double h = 7.0 * Math.sin(x / 97.0 + 1.3 * Math.sin(x / 331.0))
                + 3.0 * Math.sin(x / 41.3 + 0.7);
        double fade = clamp((x - START_X + 40) / 120.0, 0, 1);
        return h * fade;
    }

    private static double rampOffset(int x) {
        int p = Math.floorMod(x - 300, RAMP_PERIOD);
        final double[] prof = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.0, 2.5, 2.0, 1.5, 1.0, 0.5};
        if (p < prof.length) return prof[p];
        return 0;
    }

    public static boolean isRamp(int x) {
        return x > START_X + 200 && Math.floorMod(x - 300, RAMP_PERIOD) < 12;
    }

    public static int roadHalf(int x) {
        double h = smoothHeight(x + 0.5);
        double bumps = 0.95 * noise1(x / 10.0, 11) + 0.3 * noise1(x / 4.3, 12);
        double fade = clamp((x - START_X + 20) / 80.0, 0, 1);
        h += bumps * fade;
        if (x > START_X + 200) h += rampOffset(x);
        return (int) Math.round((BASE_Y + h) * 2.0);
    }

    public static double top(int x) {
        return roadHalf(x) * 0.5;
    }

    public static double topAt(double x) {
        return top((int) Math.floor(x));
    }

    public static double groundAt(double x) {
        double u = x - 0.5;
        int c = (int) Math.floor(u);
        double f = u - c;
        double a = top(c), b = top(c + 1);
        return a + (b - a) * f;
    }

    public static double wheelSupport(double wx, double r) {
        double best = -1e9;
        for (int i = -6; i <= 6; i++) {
            double dx = r * i / 6.0;
            double y = groundAt(wx + dx) + Math.sqrt(Math.max(0, r * r - dx * dx));
            if (y > best) best = y;
        }
        return best;
    }

    public static final String[] BIOME_ORDER = {
            "minecraft:plains",
            "minecraft:flower_forest",
            "minecraft:birch_forest",
            "minecraft:dappled_forest",
            "minecraft:taiga",
            "minecraft:old_growth_spruce_taiga",
            "minecraft:snowy_taiga",
            "minecraft:ice_spikes",
            "minecraft:snowy_plains",
            "minecraft:windswept_hills",
            "minecraft:sunflower_plains",
            "minecraft:savanna",
            "minecraft:desert",
            "minecraft:badlands",
            "minecraft:wooded_badlands",
            "minecraft:beach",
            "minecraft:sparse_jungle",
            "minecraft:jungle",
            "minecraft:bamboo_jungle",
            "minecraft:swamp",
            "minecraft:cherry_grove",
            "minecraft:meadow",
            "minecraft:dark_forest",
            "minecraft:mushroom_fields",
            "minecraft:pale_garden",
            "minecraft:forest"
    };

    public static int bandIndex(double x) {
        return Math.floorMod((int) Math.floor((x - START_X + 160) / BAND), BIOME_ORDER.length);
    }

    public static int decorBiome(int x, int z) {
        double far = Math.max(0, -z - 4);
        double j = noise1(z / 6.0 + x / 23.0, 77) * (7.0 + far * 0.35)
                + noise1(x / 41.0 + z / 13.0, 79) * far * 0.25
                + (rand(x, z, 78) - 0.5) * 4;
        return bandIndex(x + j);
    }

    public static double clamp(double v, double a, double b) {
        return v < a ? a : (v > b ? b : v);
    }
}
