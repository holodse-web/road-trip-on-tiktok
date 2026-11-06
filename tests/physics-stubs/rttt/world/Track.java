package rttt.world;

/** Flat road fixture for standalone CarPhysics regression checks. */
public final class Track {
    public static double groundAt(double x) { return 0; }
    public static double smoothHeight(double x) { return 0; }
    public static double wheelSupport(double x, double radius) { return radius; }
}
