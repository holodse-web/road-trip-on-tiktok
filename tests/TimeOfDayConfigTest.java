import rttt.client.game.Config;

/** Run against the compiled mod plus its Gson dependency. No world is opened. */
public final class TimeOfDayConfigTest {
    public static void main(String[] args) {
        Config defaults = Config.defaults();
        check("AUTO".equals(defaults.timeOfDay), "existing configurations default to automatic time");
        check("DAY".equals(Config.normalizeTimeOfDay(" day ")), "day normalization");
        check("NIGHT".equals(Config.normalizeTimeOfDay("night")), "night normalization");
        check("AUTO".equals(Config.normalizeTimeOfDay(null)), "null fallback");
        check("AUTO".equals(Config.normalizeTimeOfDay("invalid")), "invalid fallback");
        for (String mode : new String[]{"DAY", "NIGHT", "AUTO"}) {
            defaults.timeOfDay = mode;
            defaults.dayLengthMinutes = 32;
            Config restored = Config.fromJson(defaults.toJson().toString(), Config.defaults());
            check(mode.equals(restored.timeOfDay), "saved time mode survives round trip: " + mode);
            check(restored.dayLengthMinutes == 32, "manual mode retains automatic day length");
        }
        Config invalid = Config.fromJson("{\"timeOfDay\":\"invalid\",\"dayLengthMinutes\":0}", defaults);
        check("AUTO".equals(invalid.timeOfDay) && invalid.dayLengthMinutes >= 1, "safe config fallback");
        System.out.println("PASS time-of-day defaults, normalization and saved configuration round trips");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
