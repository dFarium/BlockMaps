package dfarium.blockmaps;

import net.minecraft.world.level.material.MapColor;

public class BrightnessValues {
    public RGB lowest, low, normal, high;

    public static BrightnessValues from(MapColor color) {
        BrightnessValues v = new BrightnessValues();
        v.lowest = RGB.from(color.calculateARGBColor(MapColor.Brightness.LOWEST));
        v.low = RGB.from(color.calculateARGBColor(MapColor.Brightness.LOW));
        v.normal = RGB.from(color.calculateARGBColor(MapColor.Brightness.NORMAL));
        v.high = RGB.from(color.calculateARGBColor(MapColor.Brightness.HIGH));
        return v;
    }
}
