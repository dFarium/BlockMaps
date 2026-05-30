package dfarium.blockmaps.model;

import java.util.List;

public record ColorEntry(int colorID, String colorName, BrightnessValues brightnessValues, List<BlockEntry> blocks) {}
