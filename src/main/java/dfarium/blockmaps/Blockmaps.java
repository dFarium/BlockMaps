package dfarium.blockmaps;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Blockmaps implements ModInitializer {

    public static final String MOD_ID = "blockmaps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    // Map color names
    private static final Map<MapColor, String> MAP_COLOR_NAMES = new HashMap<>();

    static {
        for (Field field : MapColor.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() != MapColor.class) continue;

            try {
                MapColor color = (MapColor) field.get(null);
                MAP_COLOR_NAMES.put(color, field.getName().toLowerCase());
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing BlockMaps");

        try {
            Path output = Path.of("block_map_colors.json");
            export(output);
            LOGGER.info("Export completed: {}", output.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Error exporting JSON", e);
        }
    }

    // Export JSON
    public static void export(Path output) throws IOException {
        Map<MapColor, Set<Identifier>> blocksByColor = new HashMap<>();

        for (Block block : Registries.BLOCK) {
            MapColor color = block.getDefaultState().getMapColor(null, null);
            Identifier id = Registries.BLOCK.getId(block);

            blocksByColor
                    .computeIfAbsent(color, k -> new HashSet<>())
                    .add(id);
        }

        List<ColorEntry> colors = new ArrayList<>();

        //Add color entry
        for (Map.Entry<MapColor, Set<Identifier>> entry : blocksByColor.entrySet()) {
            MapColor color = entry.getKey();

            ColorEntry colorEntry = new ColorEntry();
            colorEntry.colorID = color.id;
            colorEntry.colorName = resolveColorName(color);
            colorEntry.brightnessValues = BrightnessValues.from(color);

            colorEntry.blocks = entry.getValue()
                    .stream()
                    .map(Identifier::toString)
                    .sorted()
                    .toList();

            colors.add(colorEntry);
        }

        colors.sort(Comparator.comparingInt(c -> c.colorID));

        Root root = new Root();
        root.colors = colors;

        Files.writeString(output, GSON.toJson(root));
    }

    private static String resolveColorName(MapColor color) {
        return MAP_COLOR_NAMES.getOrDefault(color, "unknown_" + color.id);
    }

    private static final class Root {
        List<ColorEntry> colors;
    }

    private static final class ColorEntry {
        int colorID;
        String colorName;
        BrightnessValues brightnessValues;
        List<String> blocks;
    }

    private static final class BrightnessValues {
        RGB lowest;
        RGB low;
        RGB normal;
        RGB high;

        static BrightnessValues from(MapColor color) {
            BrightnessValues values = new BrightnessValues();
            values.lowest = RGB.from(color.getRenderColor(MapColor.Brightness.LOWEST));
            values.low = RGB.from(color.getRenderColor(MapColor.Brightness.LOW));
            values.normal = RGB.from(color.getRenderColor(MapColor.Brightness.NORMAL));
            values.high = RGB.from(color.getRenderColor(MapColor.Brightness.HIGH));
            return values;
        }
    }

    private static final class RGB {
        int r, g, b;

        static @NonNull RGB from(int rgb) {
            RGB c = new RGB();
            c.r = (rgb >> 16) & 0xFF;
            c.g = (rgb >> 8) & 0xFF;
            c.b = rgb & 0xFF;
            return c;
        }
    }
}
