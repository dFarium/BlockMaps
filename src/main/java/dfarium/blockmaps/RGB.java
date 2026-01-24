package dfarium.blockmaps;

import org.jspecify.annotations.NonNull;

public class RGB {
    public int r, g, b;

    public static @NonNull RGB from(int rgb) {
        RGB c = new RGB();
        c.r = (rgb >> 16) & 0xFF;
        c.g = (rgb >> 8) & 0xFF;
        c.b = rgb & 0xFF;
        return c;
    }
}
