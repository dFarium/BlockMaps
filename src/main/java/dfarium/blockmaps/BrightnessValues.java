package dfarium.blockmaps;

import net.minecraft.block.MapColor;

public class BrightnessValues {
    public RGB lowest, low, normal, high;

    public static BrightnessValues from(MapColor color) {
        BrightnessValues v = new BrightnessValues();
        v.lowest = RGB.from(color.getRenderColor(MapColor.Brightness.LOWEST));
        v.low = RGB.from(color.getRenderColor(MapColor.Brightness.LOW));
        v.normal = RGB.from(color.getRenderColor(MapColor.Brightness.NORMAL));
        v.high = RGB.from(color.getRenderColor(MapColor.Brightness.HIGH));
        return v;
    }
}
