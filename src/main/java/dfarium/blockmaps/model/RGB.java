package dfarium.blockmaps.model;

import org.jspecify.annotations.NonNull;

public record RGB(int r, int g, int b) {
    public static @NonNull RGB from(int rgb) {
        return new RGB(
            (rgb >> 16) & 0xFF,
            (rgb >> 8) & 0xFF,
            rgb & 0xFF
        );
    }
}
