package rttt.client.hud;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Renders the actual AWT victory artwork without starting Minecraft or a GPU. */
public final class VictoryCardTest {
    public static void main(String[] args) throws Exception {
        int[][] screens = {{441, 784}, {549, 976}, {1080, 1920}, {1920, 1080}, {320, 240}, {3840, 2160}};
        long[] wins = {2, 999_999, Long.MAX_VALUE};
        BufferedImage preview = new BufferedImage(960, 660, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = preview.createGraphics();
        graphics.setColor(new Color(0x202735));
        graphics.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        int sample = 0;
        for (int[] screen : screens) {
            float unit = Math.min(screen[1], screen[0] * 16f / 9f) / 100f;
            for (long number : wins) {
                BufferedImage card = VictoryCard.create(screen[0], unit, number, false);
                BufferedImage lost = VictoryCard.create(screen[0], unit, -number, true);
                check(lost.getWidth() <= screen[0] - 2 * Math.max(4, Math.round(unit)), "defeat card exceeds screen width");
                check(lost.getHeight() < screen[1], "defeat card exceeds screen height");
                check(card.getWidth() <= screen[0] - 2 * Math.max(4, Math.round(unit)), "card exceeds screen width");
                check(card.getHeight() < screen[1], "card exceeds screen height");
                verifyTextHasPadding(card, screen[0] + "x" + screen[1] + " win " + number);
                if (number == 2) {
                    int cellX = (sample % 2) * 480, cellY = (sample / 2) * 220;
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(screen[0] + " × " + screen[1], cellX + 14, cellY + 20);
                    double scale = Math.min(450.0 / card.getWidth(), 180.0 / card.getHeight());
                    graphics.drawImage(card, cellX + 14, cellY + 30,
                            (int) Math.round(card.getWidth() * scale), (int) Math.round(card.getHeight() * scale), null);
                    sample++;
                    graphics.drawImage(lost, cellX + 14, cellY + 30 + (int) Math.round(card.getHeight() * scale) + 4,
                            (int) Math.round(lost.getWidth() * scale * 0.5), (int) Math.round(lost.getHeight() * scale * 0.5), null);
                }
            }
        }
        graphics.dispose();
        if (args.length > 0) {
            Path output = Path.of(args[0]);
            Files.createDirectories(output.toAbsolutePath().getParent());
            ImageIO.write(preview, "png", output.toFile());
        }
        System.out.println("PASS: victory text fits with padding across 6 portrait/landscape sizes and 3 win counts, including Long.MAX_VALUE.");
    }

    private static void verifyTextHasPadding(BufferedImage card, String sample) {
        int left = card.getWidth(), top = card.getHeight(), right = -1, bottom = -1;
        int white = 0, gold = 0;
        for (int y = 0; y < card.getHeight(); y++) {
            for (int x = 0; x < card.getWidth(); x++) {
                int pixel = card.getRGB(x, y);
                if ((pixel >>> 24) < 180) continue; // The decorative gold border is translucent.
                int red = pixel >> 16 & 255, green = pixel >> 8 & 255, blue = pixel & 255;
                if (red < 180 || green < 100) continue;
                if (green > 180 && blue > 180) white++;
                else if (blue < 180) gold++;
                else continue;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
            }
        }
        check(white > 5 && gold > 5, "a text line vanished: " + sample);
        check(left >= 3 && top >= 3 && right < card.getWidth() - 3 && bottom < card.getHeight() - 3,
                "text touches/clips the panel edge: " + sample);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
