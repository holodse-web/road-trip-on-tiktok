package rttt.client.hud;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Measures the final outlined/shadowed text before sizing the victory panel. */
final class VictoryCard {
    private VictoryCard() {}

    static BufferedImage create(int screenWidth, float unit, long wins, boolean defeat) {
        int outerMargin = Math.max(4, Math.round(unit));
        int maxWidth = Math.max(1, screenWidth - outerMargin * 2);
        int paddingX = Math.min(Math.max(3, Math.round(2f * unit)), maxWidth / 8);
        int paddingY = Math.max(3, Math.round(1.6f * unit));
        int textWidth = Math.max(1, maxWidth - paddingX * 2);

        BufferedImage title = defeat
                ? line("АЛЕКС ПОТЕРЯЛСЯ!", Math.round(8.5f * unit), 0xFFFF4A3D, 0xFFFFB0A8, 0.06f, textWidth)
                : line("АЛЕКС ДОМА!", Math.round(8.5f * unit), 0xFFFFB800, 0xFFFFF4B0, 0.06f, textWidth);
        BufferedImage subtitle = line(defeat ? "ALEX GOT LOST!" : "ALEX IS HOME!", Math.round(3.2f * unit), 0xFFFFFFFF, 0, 0.05f, textWidth);
        BufferedImage number = defeat
                ? line("−1 победа · Победы: " + wins + " · Wins: " + wins, Math.round(2.4f * unit), 0xFFFF9A90, 0, 0.05f, textWidth)
                : line("Победа №" + wins + " · Win #" + wins, Math.round(2.4f * unit), 0xFFFFE27A, 0, 0.05f, textWidth);
        int gap1 = Math.max(2, Math.round(1.6f * unit));
        int gap2 = Math.max(2, Math.round(1.8f * unit));
        int width = Math.max(title.getWidth(), Math.max(subtitle.getWidth(), number.getWidth())) + paddingX * 2;
        int height = paddingY * 2 + title.getHeight() + gap1 + subtitle.getHeight() + gap2 + number.getHeight();
        BufferedImage card = Sprites.panel(width, height, Math.round(2 * unit), 0xC0101622, 0xD00A0E16, defeat ? 0x50FF5A4A : 0x40FFD86A);
        Graphics2D graphics = Sprites.gfx(card);
        int y = paddingY;
        graphics.drawImage(title, (width - title.getWidth()) / 2, y, null);
        y += title.getHeight() + gap1;
        graphics.drawImage(subtitle, (width - subtitle.getWidth()) / 2, y, null);
        y += subtitle.getHeight() + gap2;
        graphics.drawImage(number, (width - number.getWidth()) / 2, y, null);
        graphics.dispose();
        return card;
    }

    private static BufferedImage line(String value, int requestedSize, int color, int top, float outlineRatio, int maxWidth) {
        int size = Math.max(1, requestedSize);
        // Match Sprites.text's complete texture width, including outline and shadow padding.
        while (size > 1) {
            float advance = Sprites.measure(value, size);
            int padding = (int) Math.ceil(size * (outlineRatio + 0.12f) + 2);
            int width = (int) Math.ceil(advance + padding * 2);
            if (width <= maxWidth) break;
            size = Math.max(1, Math.min(size - 1, (int) Math.floor(size * maxWidth / (double) width)));
        }
        BufferedImage image = Sprites.text(value, size, color, top, size * outlineRatio, true, null);
        // Verify the generated texture too: font rounding must never eat the inner padding.
        while (image.getWidth() > maxWidth && size > 1) {
            size--;
            image = Sprites.text(value, size, color, top, size * outlineRatio, true, null);
        }
        return image;
    }
}
