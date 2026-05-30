package dfarium.blockmaps.model;

import net.minecraft.world.level.material.MapColor;

public record BrightnessValues(RGB lowest, RGB low, RGB normal, RGB high) {
    public static BrightnessValues from(MapColor color) {
        return new BrightnessValues(
            RGB.from(color.calculateARGBColor(MapColor.Brightness.LOWEST)),
            RGB.from(color.calculateARGBColor(MapColor.Brightness.LOW)),
            RGB.from(color.calculateARGBColor(MapColor.Brightness.NORMAL)),
            RGB.from(color.calculateARGBColor(MapColor.Brightness.HIGH))
        );
    }
}
