package dfarium.blockmaps.exporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dfarium.blockmaps.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PaletteExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaletteExporter.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MapColor, String> MAP_COLOR_NAMES = new HashMap<>();

    static {
        initializeColorNames();
    }

    private static void initializeColorNames() {
        for (Field field : MapColor.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != MapColor.class) continue;
            try {
                MapColor color = (MapColor) field.get(null);
                MAP_COLOR_NAMES.put(color, field.getName().toLowerCase());
            } catch (IllegalAccessException ignored) {}
        }
    }

    public static void run(MinecraftServer server) {
        try {
            Path outputDir = Path.of("blockmaps_export");
            Path texturesDir = outputDir.resolve("textures");
            Files.createDirectories(texturesDir);

            ResourceManager resourceManager = TextureResolver.getBestResourceManager(server);
            export(outputDir, texturesDir, resourceManager, server.getServerVersion(), server.overworld());
            
            LOGGER.info("Export completed in: {}", outputDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Error exporting data", e);
        }
    }

    public static void export(Path outputJson, Path texturesDir, ResourceManager resourceManager, String gameVersion, LevelReader world) throws IOException {
        Map<MapColor, Set<Identifier>> blocksByColor = new HashMap<>();
        int blocksProcessed = 0;
        int texturesExtracted = 0;

        for (Block block : BuiltInRegistries.BLOCK) {
            if (processBlock(block, blocksByColor, texturesDir, resourceManager)) {
                blocksProcessed++;
                texturesExtracted++;
            } else if (shouldExport(block)) {
                blocksProcessed++;
            }
        }
        
        LOGGER.info("Processed {} blocks, successfully extracted {} textures", blocksProcessed, texturesExtracted);
        Map<String, String> introducedVersions = VersionDatabase.load(resourceManager);
        saveJson(outputJson, blocksByColor, gameVersion, world, introducedVersions);
    }

    private static boolean shouldExport(Block block) {
        var state = block.defaultBlockState();
        if (state.getMapColor(null, null) == MapColor.NONE) return false;
        
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        boolean isFullBlock = state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        boolean isSlab = block instanceof SlabBlock || id.getPath().contains("slab");
        boolean isGlass = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()) == Shapes.block();
        boolean isCarpet = block instanceof CarpetBlock;
        boolean isCandle = block instanceof AbstractCandleBlock;
        boolean isPressurePlate = block instanceof PressurePlateBlock || block instanceof WeightedPressurePlateBlock;
        
        return isFullBlock || isSlab || isGlass || isCarpet || isCandle || isPressurePlate;
    }

    private static boolean processBlock(Block block, Map<MapColor, Set<Identifier>> blocksByColor, Path texturesDir, ResourceManager rm) {
        if (!shouldExport(block)) return false;
        
        var state = block.defaultBlockState();
        MapColor color = state.getMapColor(null, null);
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        
        blocksByColor.computeIfAbsent(color, k -> new HashSet<>()).add(id);
        return TextureResolver.extractTexture(id, texturesDir, rm);
    }

    private static void saveJson(Path outputDir, Map<MapColor, Set<Identifier>> blocksByColor, String gameVersion, LevelReader world, Map<String, String> introducedVersions) throws IOException {
        List<ColorEntry> colors = new ArrayList<>();
        for (Map.Entry<MapColor, Set<Identifier>> entry : blocksByColor.entrySet()) {
            MapColor mapColor = entry.getKey();
            int colorID = mapColor.id;
            String colorName = MAP_COLOR_NAMES.getOrDefault(mapColor, "unknown_" + colorID);
            BrightnessValues brightnessValues = BrightnessValues.from(mapColor);
            
            // Sort blocks by "nature" (grouping materials)
            List<BlockEntry> blocks = entry.getValue().stream()
                .sorted((idA, idB) -> {
                    String a = idA.toString();
                    String b = idB.toString();
                    String materialA = getMaterial(a);
                    String materialB = getMaterial(b);
                    int comp = materialA.compareTo(materialB);
                    if (comp != 0) return comp;
                    return a.compareTo(b); // Secondary alphabetical sort
                })
                .map(id -> {
                    Block block = BuiltInRegistries.BLOCK.get(id).map(Holder::value).orElse(null);
                    String introducedIn = introducedVersions.getOrDefault(id.toString(), gameVersion);
                    return new BlockEntry(id.toString(), needsSupport(block, world), introducedIn);
                })
                .toList();
            
            colors.add(new ColorEntry(colorID, colorName, brightnessValues, blocks));
        }
        colors.sort(Comparator.comparingInt(ColorEntry::colorID));
        
        Root root = new Root(gameVersion, colors);
        String fileName = String.format("palette_%s.json", gameVersion.replace(".", "_"));
        Files.writeString(outputDir.resolve(fileName), GSON.toJson(root));
    }

    private static boolean needsSupport(Block block, LevelReader world) {
        // Evaluate canPlaceAt at a high altitude overworld position (likely air)
        return !block.defaultBlockState().canSurvive(world, new BlockPos(0, 320, 0)) 
                || block instanceof FallingBlock 
                || block instanceof BrushableBlock;
    }

    private static String getMaterial(String blockId) {
        String path = blockId.split(":")[1];
        // Common materials/prefixes
        String[] materials = {
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", 
            "crimson", "warped", "iron", "gold", "copper", "stone", "cobblestone", "andesite", "diorite", 
            "granite", "sandstone", "quartz", "prismarine", "nether_brick", "blackstone", "deepslate", "mud", 
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", 
            "cyan", "purple", "blue", "brown", "green", "red", "black"
        };
        
        for (String m : materials) {
            if (path.startsWith(m + "_") || path.equals(m)) {
                return m;
            }
        }
        return "zzzzz_" + path; // Fallback to end of list
    }
}
