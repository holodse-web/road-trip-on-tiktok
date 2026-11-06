package rttt.client.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MobCatalog {
    private MobCatalog() {}

    public enum Kind { WALK, RUN, HOP, CRAWL, FLY, SWIM, SHOOT, EXPLODE, TELEPORT, GIANT, WITHER, DRAGON, WARDEN, ELDER }

    public enum Size {
        TINY(30, 0.3), SMALL(80, 0.45), MEDIUM(200, 0.65), LARGE(500, 0.9), HUGE(1200, 1.15), BOSS(3500, 1.6);
        public final long meters;
        public final double power;

        Size(long meters, double power) {
            this.meters = meters;
            this.power = power;
        }
    }

    public enum Shot { NONE, ARROW, SNOWBALL, SPIT, SMALL_FIREBALL, FIREBALL, POTION, TRIDENT, WIND, BULLET, FANGS, SKULL, BEAM }

    public static final class Entry {
        public final String id;
        public final String ru;
        public final Kind kind;
        public final Size size;
        public Shot shot = Shot.NONE;
        public byte attackEvent = -1;
        public String hand;
        public float scale = 1f;
        public boolean baby = false;
        public double speed = 3.5;
        public final List<String> names = new ArrayList<>();
        public boolean helper = false;

        Entry(String id, String ru, Kind kind, Size size, String... aliases) {
            this.id = id;
            this.ru = ru;
            this.kind = kind;
            this.size = size;
            names.add(ru);
            names.add(id);
            names.add(id.replace('_', ' '));
            Collections.addAll(names, aliases);
        }

        Entry shot(Shot s) { this.shot = s; return this; }
        Entry event(int e) { this.attackEvent = (byte) e; return this; }
        Entry hand(String h) { this.hand = h; return this; }
        Entry scale(float s) { this.scale = s; return this; }
        Entry speed(double s) { this.speed = s; return this; }
    }

    public static final Map<String, Entry> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_NAME = new HashMap<>();
    private static int maxNameWords = 1;

    private static Entry add(Entry e) {
        BY_ID.put(e.id, e);
        return e;
    }

    static {
        Kind W = Kind.WALK, R = Kind.RUN, H = Kind.HOP, C = Kind.CRAWL, F = Kind.FLY, S = Kind.SWIM, SH = Kind.SHOOT;
        Size T = Size.TINY, SM = Size.SMALL, M = Size.MEDIUM, L = Size.LARGE, HU = Size.HUGE, B = Size.BOSS;

        add(new Entry("chicken", "Курица", W, T, "курочка", "кура", "цыпа", "петух", "chick"));
        add(new Entry("cow", "Корова", W, M, "коровка", "бык", "му", "moo"));
        add(new Entry("pig", "Свинья", W, SM, "свин", "хрюша", "свинка", "поросёнок", "поросенок", "хряк"));
        add(new Entry("sheep", "Овца", W, SM, "овечка", "баран", "барашек"));
        add(new Entry("rabbit", "Кролик", H, T, "зайка", "заяц", "кроль", "bunny"));
        add(new Entry("mooshroom", "Грибная корова", W, M, "муушрум", "грибная", "mushroom cow"));
        add(new Entry("horse", "Лошадь", R, L, "конь", "лошадка", "коняшка").speed(7));
        add(new Entry("donkey", "Осёл", R, M, "осел", "ослик").speed(6));
        add(new Entry("mule", "Мул", R, M, "мулик").speed(6));
        add(new Entry("skeleton_horse", "Лошадь-скелет", R, L, "скелет лошадь", "конь скелет", "skelly horse").speed(7));
        add(new Entry("zombie_horse", "Лошадь-зомби", R, L, "зомби лошадь", "конь зомби").speed(7));
        add(new Entry("camel", "Верблюд", R, L, "верблюжонок").speed(5));
        add(new Entry("camel_husk", "Верблюд-кадавр", R, L, "кадавр верблюд", "верблюд кадавр", "camelhusk").speed(5));
        add(new Entry("llama", "Лама", W, M, "ламочка").shot(Shot.SPIT));
        add(new Entry("trader_llama", "Лама торговца", W, M, "торговая лама").shot(Shot.SPIT));
        add(new Entry("cat", "Кошка", R, T, "кот", "котик", "котёнок", "котенок", "кис", "киса", "kitty").speed(6));
        add(new Entry("ocelot", "Оцелот", R, T, "оцелотик").speed(7));
        add(new Entry("fox", "Лиса", R, SM, "лис", "лисичка", "лисёнок").speed(7));
        add(new Entry("wolf", "Волк", R, SM, "собака", "пёс", "пес", "песик", "собачка", "волчонок", "dog", "doggo").speed(7));
        add(new Entry("parrot", "Попугай", F, T, "попугайчик", "попка"));
        add(new Entry("bat", "Летучая мышь", F, T, "мышь", "летучка", "нетопырь"));
        add(new Entry("bee", "Пчела", F, T, "пчёлка", "пчелка", "пчёлы", "пчелы"));
        add(new Entry("allay", "Тихоня", F, T, "аллей", "эллей"));
        add(new Entry("frog", "Лягушка", H, T, "жаба", "лягуха", "квак"));
        add(new Entry("tadpole", "Головастик", S, T, "головастики"));
        add(new Entry("axolotl", "Аксолотль", S, T, "аксолотл", "аксолот", "axo"));
        add(new Entry("turtle", "Черепаха", W, SM, "черепашка").speed(1.5));
        add(new Entry("armadillo", "Броненосец", W, SM, "броник").speed(2.5));
        add(new Entry("sniffer", "Нюхач", W, L, "сниффер", "нюхастик").speed(2));
        add(new Entry("panda", "Панда", W, M, "пандочка"));
        add(new Entry("polar_bear", "Белый медведь", R, L, "медведь", "мишка", "белый мишка", "умка", "bear").speed(5));
        add(new Entry("goat", "Коза", R, M, "козёл", "козел", "козлик").event(58).speed(8));
        add(new Entry("strider", "Лавомерка", W, M, "страйдер", "лавомер").speed(2.5));
        add(new Entry("villager", "Житель", W, M, "деревенский житель", "селянин", "крестьянин", "хмм", "hmm"));
        add(new Entry("wandering_trader", "Странствующий торговец", W, M, "торговец", "трейдер", "trader"));
        add(new Entry("snow_golem", "Снежный голем", W, SM, "снеговик", "снеговичок", "снежный", "snowman").shot(Shot.SNOWBALL));
        add(new Entry("iron_golem", "Железный голем", W, L, "голем", "жг", "железный", "ig", "golem").event(4).speed(2.5));
        add(new Entry("copper_golem", "Медный голем", W, SM, "медный", "медяшка").speed(3));
        add(new Entry("happy_ghast", "Счастливый гаст", F, HU, "весёлый гаст", "веселый гаст", "добрый гаст", "гастик"));
        add(new Entry("cod", "Треска", S, T, "рыба", "рыбка", "fish"));
        add(new Entry("salmon", "Лосось", S, T, "сёмга", "семга"));
        add(new Entry("tropical_fish", "Тропическая рыба", S, T, "тропическая рыбка", "клоун", "рыба клоун"));
        add(new Entry("pufferfish", "Иглобрюх", S, T, "рыба фугу", "фугу", "иглобрюх", "puffer"));
        add(new Entry("squid", "Спрут", S, SM, "кальмар", "осьминог"));
        add(new Entry("glow_squid", "Светящийся спрут", S, SM, "светящийся кальмар", "светоспрут"));
        add(new Entry("dolphin", "Дельфин", S, M, "дельфинчик"));
        add(new Entry("nautilus", "Наутилус", S, SM, "наутилус"));
        add(new Entry("zombie_nautilus", "Зомби-наутилус", S, M, "зомби наутилус"));
        add(new Entry("guardian", "Страж", S, M, "гвардиан", "гардиан").shot(Shot.BEAM));
        add(new Entry("zombie", "Зомби", W, SM, "зомбак", "зомбарь", "зомбик", "зомбя"));
        add(new Entry("husk", "Кадавр", W, SM, "хаск", "пустынный зомби"));
        add(new Entry("drowned", "Утопленник", W, SM, "утопец", "дровнед").shot(Shot.TRIDENT).hand("trident"));
        add(new Entry("zombie_villager", "Зомби-житель", W, SM, "зомби житель", "зомби деревенский"));
        add(new Entry("skeleton", "Скелет", SH, SM, "скелетон", "скелли", "кость", "skelly").shot(Shot.ARROW).hand("bow"));
        add(new Entry("stray", "Зимогор", SH, SM, "страй", "ледяной скелет").shot(Shot.ARROW).hand("bow"));
        add(new Entry("bogged", "Болотник", SH, SM, "богед", "болотный скелет").shot(Shot.ARROW).hand("bow"));
        add(new Entry("parched", "Иссохший", SH, SM, "засохший", "высохший", "пустынный скелет").shot(Shot.ARROW).hand("bow"));
        add(new Entry("wither_skeleton", "Скелет-иссушитель", W, M, "визер скелет", "иссушенный скелет", "чёрный скелет", "черный скелет", "wither skelly").hand("stone_sword"));
        add(new Entry("creeper", "Крипер", Kind.EXPLODE, L, "крипак", "криппер", "крипи", "крипочек", "ссс", "creep").speed(3));
        add(new Entry("spider", "Паук", H, SM, "паучок", "паучара"));
        add(new Entry("cave_spider", "Пещерный паук", H, T, "пещерник", "синий паук"));
        add(new Entry("enderman", "Эндермен", Kind.TELEPORT, M, "эндер", "эндермэн", "эндермен", "эндерман", "странник края", "ender"));
        add(new Entry("endermite", "Эндермит", C, T, "эндермитик"));
        add(new Entry("silverfish", "Чешуйница", C, T, "серебрянка", "чешуйка", "сильверфиш"));
        add(new Entry("slime", "Слизень", H, SM, "слайм", "слизь", "слизняк"));
        add(new Entry("magma_cube", "Магмовый куб", H, M, "магма куб", "лавовый куб", "магмакуб", "магма"));
        add(new Entry("sulfur_cube", "Серный куб", H, M, "сера", "серный", "сульфур"));
        add(new Entry("witch", "Ведьма", SH, SM, "ведьмочка", "колдунья").shot(Shot.POTION));
        add(new Entry("pillager", "Разбойник", SH, SM, "пилладжер", "пилигрим", "грабитель").shot(Shot.ARROW).hand("crossbow"));
        add(new Entry("vindicator", "Поборник", W, M, "виндикатор", "топорщик").hand("iron_axe").speed(5));
        add(new Entry("evoker", "Вызыватель", SH, M, "эвокер", "заклинатель", "колдун").shot(Shot.FANGS));
        add(new Entry("illusioner", "Иллюзионист", SH, M, "иллюзионер", "фокусник").shot(Shot.ARROW).hand("bow"));
        add(new Entry("vex", "Досаждатель", F, T, "векс", "призрак", "вредина").hand("iron_sword"));
        add(new Entry("ravager", "Разоритель", W, L, "равагер", "опустошитель").event(4).speed(4));
        add(new Entry("phantom", "Фантом", F, SM, "фантомчик", "ночной"));
        add(new Entry("blaze", "Ифрит", SH, M, "блейз", "блэйз", "огненный").shot(Shot.SMALL_FIREBALL));
        add(new Entry("ghast", "Гаст", SH, L, "гасть", "гастище").shot(Shot.FIREBALL));
        add(new Entry("breeze", "Вихрь", SH, M, "бриз", "ветерок").shot(Shot.WIND));
        add(new Entry("shulker", "Шалкер", SH, M, "шулкер", "шалк").shot(Shot.BULLET));
        add(new Entry("piglin", "Пиглин", W, SM, "свиночеловек").hand("golden_sword"));
        add(new Entry("piglin_brute", "Пиглин-громила", W, M, "громила", "брут", "пиглин громила").hand("golden_axe").speed(5));
        add(new Entry("zombified_piglin", "Зомбифицированный пиглин", W, SM, "зомби пиглин", "свинозомби", "зомби свин", "пигмен").hand("golden_sword"));
        add(new Entry("hoglin", "Хоглин", R, L, "хоглинчик", "кабан").event(4).speed(6));
        add(new Entry("zoglin", "Зоглин", R, L, "зомби хоглин").event(4).speed(6));
        add(new Entry("creaking", "Скрипун", W, M, "крикинг", "скрипач").speed(4));
        add(new Entry("giant", "Великан", Kind.GIANT, HU, "гигант", "гигантский зомби", "огромный зомби").scale(1f).speed(1.6));
        add(new Entry("elder_guardian", "Древний страж", Kind.ELDER, B, "элдер гардиан", "древний", "старый страж", "elder"));
        add(new Entry("warden", "Хранитель", Kind.WARDEN, B, "варден", "вардэн", "надзиратель", "страж глубин"));
        add(new Entry("wither", "Иссушитель", Kind.WITHER, B, "визер", "висер", "вызер", "босс визер"));
        add(new Entry("ender_dragon", "Эндер-дракон", Kind.DRAGON, B, "дракон", "эндер дракон", "эндердракон", "дракоша", "дракончик", "ед", "dragon"));

        for (String h : new String[]{"chicken", "cow", "pig", "sheep", "rabbit", "mooshroom", "horse", "donkey", "mule", "camel", "llama", "trader_llama", "cat", "ocelot", "fox", "wolf", "parrot", "bee", "allay", "frog", "axolotl", "turtle", "armadillo", "sniffer", "panda", "strider", "villager", "wandering_trader", "snow_golem", "iron_golem", "copper_golem", "happy_ghast", "dolphin", "glow_squid", "tadpole"}) {
            Entry e = BY_ID.get(h);
            if (e != null) {
                e.helper = true;
                e.shot = Shot.NONE;
                e.attackEvent = h.equals("iron_golem") ? (byte) 4 : (byte) -1;
            }
        }
        for (Entry e : BY_ID.values()) {
            for (String n : e.names) {
                String k = normalize(n);
                if (k.isEmpty()) continue;
                BY_NAME.putIfAbsent(k, e);
                String compact = k.replace(" ", "");
                BY_NAME.putIfAbsent(compact, e);
                maxNameWords = Math.max(maxNameWords, k.split(" ").length);
            }
        }
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).replace('ё', 'е').replace('_', ' ').replace('-', ' ');
        t = cyrillicLookalikes(t);
        StringBuilder b = new StringBuilder(t.length());
        boolean space = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            int type = Character.getType(c);
            if (type == Character.FORMAT || type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) continue;
            if (Character.isLetterOrDigit(c)) {
                b.append(c);
                space = false;
            } else if (!space && b.length() > 0) {
                b.append(' ');
                space = true;
            }
        }
        return b.toString().trim();
    }

    private static final String LATIN = "aceopxykmthbu";
    private static final String CYR = "асеорхукмтнви";

    private static String cyrillicLookalikes(String t) {
        StringBuilder out = new StringBuilder(t.length());
        int start = 0;
        for (int i = 0; i <= t.length(); i++) {
            if (i < t.length() && Character.isLetter(t.charAt(i))) continue;
            String word = t.substring(start, i);
            boolean cyr = false, lat = false;
            for (int k = 0; k < word.length(); k++) {
                Character.UnicodeScript sc = Character.UnicodeScript.of(word.charAt(k));
                if (sc == Character.UnicodeScript.CYRILLIC) cyr = true;
                else if (sc == Character.UnicodeScript.LATIN) lat = true;
            }
            if (cyr && lat) {
                for (int k = 0; k < word.length(); k++) {
                    int idx = LATIN.indexOf(word.charAt(k));
                    out.append(idx >= 0 ? CYR.charAt(idx) : word.charAt(k));
                }
            } else {
                out.append(word);
            }
            if (i < t.length()) out.append(t.charAt(i));
            start = i + 1;
        }
        return out.toString();
    }

    public static Entry match(String message, boolean inside) {
        String n = normalize(message);
        if (n.isEmpty()) return null;
        Entry e = BY_NAME.get(n);
        if (e == null) e = BY_NAME.get(n.replace(" ", ""));
        if (e != null || !inside) return e;
        String[] words = n.split(" ");
        if (words.length > 12) return null;
        for (int len = Math.min(maxNameWords, words.length); len >= 1; len--) {
            for (int i = 0; i + len <= words.length; i++) {
                StringBuilder b = new StringBuilder();
                for (int k = i; k < i + len; k++) {
                    if (k > i) b.append(' ');
                    b.append(words[k]);
                }
                String key = b.toString();
                if (len == 1 && key.length() < 3 && !key.equals("жг") && !key.equals("ед") && !key.equals("ig")) continue;
                Entry m = BY_NAME.get(key);
                if (m != null) return m;
                if (key.length() >= 5) {
                    String[] stems = {key.substring(0, key.length() - 1), key.substring(0, key.length() - 2)};
                    for (String st : stems) {
                        for (String end : new String[]{"", "а", "ь", "я", "й"}) {
                            Entry mm = BY_NAME.get(st + end);
                            if (mm != null && (st + end).length() >= 4) return mm;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static Config.MobSetting defaults(Entry e, boolean soft) {
        Config.MobSetting s = new Config.MobSetting();
        s.meters = e.size.meters;
        s.power = e.size.power;
        if (e.helper) { s.meters = e.size.meters + e.size.meters / 4; s.power = 0.6 + e.size.power * 0.6; }
        if (e.id.equals("ender_dragon")) { s.meters = 5000; s.power = 1.9; }
        if (e.id.equals("warden")) { s.meters = 4000; s.power = 1.8; }
        if (e.id.equals("wither")) { s.meters = 4000; s.power = 1.8; }
        if (e.id.equals("elder_guardian")) { s.meters = 3000; s.power = 1.6; }
        if (e.id.equals("creeper")) { s.meters = 600; s.power = 1.1; }
        if (e.id.equals("iron_golem")) { s.meters = 1500; s.power = 1.5; }
        if (e.id.equals("happy_ghast")) { s.meters = 2000; s.power = 1.5; }
        if (soft && !e.helper) {
            s.meters = Math.max(10, Math.round(s.meters * 0.6 / 10.0) * 10);
            s.power = Math.round(s.power * 0.8 * 20) / 20.0;
        }
        return s;
    }

    public static void fillDefaults(Config c) {
        for (Entry e : BY_ID.values()) {
            if (!c.mobs.containsKey(e.id)) c.mobs.put(e.id, defaults(e, true));
        }
    }

    public static void softenHarmful(Config c) {
        for (Entry e : BY_ID.values()) {
            if (e.helper) continue;
            Config.MobSetting s = c.mobs.get(e.id);
            if (s == null) continue;
            Config.MobSetting old = defaults(e, false);
            if (s.meters == old.meters && Math.abs(s.power - old.power) < 1e-6) {
                Config.MobSetting n = defaults(e, true);
                s.meters = n.meters;
                s.power = n.power;
            }
        }
    }
}
