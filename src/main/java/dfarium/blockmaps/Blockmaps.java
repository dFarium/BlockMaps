package dfarium.blockmaps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.WorldView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class Blockmaps implements ModInitializer {

    public static final String MOD_ID = "blockmaps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
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

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
    }

    private void onServerStarted(MinecraftServer server) {
        try {
            Path outputDir = Path.of("blockmaps_export");
            Path texturesDir = outputDir.resolve("textures");
            Files.createDirectories(texturesDir);

            ResourceManager resourceManager = getBestResourceManager(server);
            export(outputDir, texturesDir, resourceManager, server.getVersion(), server.getOverworld());
            
            LOGGER.info("Export completed in: {}", outputDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Error exporting data", e);
        }
    }

    private ResourceManager getBestResourceManager(MinecraftServer server) {
        try {
            Class<?> clientClass = Class.forName("net.minecraft.client.MinecraftClient");
            Object client = clientClass.getMethod("getInstance").invoke(null);
            ResourceManager rm = (ResourceManager) clientClass.getMethod("getResourceManager").invoke(client);
            LOGGER.info("Using Client ResourceManager for assets");
            return rm;
        } catch (Exception ignored) {
            LOGGER.info("Using Server ResourceManager (assets might not be available)");
            return server.getResourceManager();
        }
    }

    public static void export(Path outputJson, Path texturesDir, ResourceManager resourceManager, String gameVersion, WorldView world) throws IOException {
        Map<MapColor, Set<Identifier>> blocksByColor = new HashMap<>();
        int blocksProcessed = 0;
        int texturesExtracted = 0;

        for (Block block : Registries.BLOCK) {
            if (processBlock(block, blocksByColor, texturesDir, resourceManager)) {
                blocksProcessed++;
                texturesExtracted++;
            } else if (shouldExport(block)) {
                blocksProcessed++;
            }
        }
        
        LOGGER.info("Processed {} blocks, successfully extracted {} textures", blocksProcessed, texturesExtracted);
        saveJson(outputJson, blocksByColor, gameVersion, world);
    }

    private static boolean shouldExport(Block block) {
        var state = block.getDefaultState();
        if (state.getMapColor(null, null) == MapColor.CLEAR) return false;
        
        Identifier id = Registries.BLOCK.getId(block);
        boolean isFullBlock = state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        boolean isSlab = block instanceof SlabBlock || id.getPath().contains("slab");
        boolean isGlass = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) == VoxelShapes.fullCube();
        boolean isCarpet = block instanceof CarpetBlock;
        boolean isCandle = block instanceof AbstractCandleBlock;
        boolean isPressurePlate = block instanceof PressurePlateBlock || block instanceof WeightedPressurePlateBlock;
        
        return isFullBlock || isSlab || isGlass || isCarpet || isCandle || isPressurePlate;
    }

    private static boolean processBlock(Block block, Map<MapColor, Set<Identifier>> blocksByColor, Path texturesDir, ResourceManager rm) {
        if (!shouldExport(block)) return false;
        
        var state = block.getDefaultState();
        MapColor color = state.getMapColor(null, null);
        Identifier id = Registries.BLOCK.getId(block);
        
        blocksByColor.computeIfAbsent(color, k -> new HashSet<>()).add(id);
        return extractTexture(id, texturesDir, rm);
    }

    private static void saveJson(Path outputDir, Map<MapColor, Set<Identifier>> blocksByColor, String gameVersion, WorldView world) throws IOException {
        List<ColorEntry> colors = new ArrayList<>();
        for (Map.Entry<MapColor, Set<Identifier>> entry : blocksByColor.entrySet()) {
            ColorEntry colorEntry = new ColorEntry();
            colorEntry.colorID = entry.getKey().id;
            colorEntry.colorName = MAP_COLOR_NAMES.getOrDefault(entry.getKey(), "unknown_" + entry.getKey().id);
            colorEntry.brightnessValues = BrightnessValues.from(entry.getKey());
            
            // Sort blocks by "nature" (grouping materials)
            colorEntry.blocks = entry.getValue().stream()
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
                    Block block = Registries.BLOCK.get(id);
                    return new BlockEntry(id.toString(), needsSupport(block, world));
                })
                .toList();
            
            colors.add(colorEntry);
        }
        colors.sort(Comparator.comparingInt(c -> c.colorID));
        
        Root root = new Root();
        root.version = gameVersion;
        root.colors = colors;
        String fileName = String.format("palette_%s.json", gameVersion.replace(".", "_"));
        Files.writeString(outputDir.resolve(fileName), GSON.toJson(root));
    }

    private static boolean needsSupport(Block block, WorldView world) {
        // Evaluate canPlaceAt at a high altitude overworld position (likely air)
        return !block.getDefaultState().canPlaceAt(world, new BlockPos(0, 320, 0));
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

    private static boolean extractTexture(Identifier blockId, Path texturesDir, ResourceManager resourceManager) {
        try {
            Identifier textureId = null;
            Block block = Registries.BLOCK.get(blockId);

            // Special case for candles: use item texture directly
            if (block instanceof AbstractCandleBlock) {
                Identifier itemModelId = Identifier.of(blockId.getNamespace(), "models/item/" + blockId.getPath() + ".json");
                Optional<Resource> itemRes = resourceManager.getResource(itemModelId);
                if (itemRes.isPresent()) {
                    textureId = findTextureInModel(itemRes.get(), resourceManager);
                }
            }

            if (textureId == null) {
                Identifier modelId = resolveModelId(blockId, resourceManager);
                Optional<Resource> modelRes = resourceManager.getResource(modelId);

                if (modelRes.isPresent()) {
                    textureId = findTextureInModel(modelRes.get(), resourceManager);
                }

                // Fallback al modelo de item
                if (textureId == null) {
                    Identifier itemModelId = Identifier.of(blockId.getNamespace(), "models/item/" + blockId.getPath() + ".json");
                    Optional<Resource> itemRes = resourceManager.getResource(itemModelId);
                    if (itemRes.isPresent()) {
                        textureId = findTextureInModel(itemRes.get(), resourceManager);
                    }
                }
            }

            if (textureId != null) {
                return copyTexture(blockId, textureId, texturesDir, resourceManager);
            } else {
                LOGGER.warn("Could not find texture ID for {}", blockId);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to extract texture for {}", blockId, e);
        }
        return false;
    }

    private static Identifier resolveModelId(Identifier blockId, ResourceManager rm) throws IOException {
        Identifier blockstateId = Identifier.of(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        Optional<Resource> blockstateRes = rm.getResource(blockstateId);
        
        if (blockstateRes.isPresent()) {
            try (BufferedReader reader = blockstateRes.get().getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String modelPath = null;
                
                if (json.has("variants")) {
                    JsonObject variants = json.getAsJsonObject("variants");
                    if (!variants.keySet().isEmpty()) {
                        var variant = variants.get(variants.keySet().iterator().next());
                        modelPath = getModelPathFromJson(variant);
                    }
                } else if (json.has("multipart")) {
                    var multipart = json.getAsJsonArray("multipart");
                    if (multipart.size() > 0) {
                        var apply = multipart.get(0).getAsJsonObject().get("apply");
                        modelPath = getModelPathFromJson(apply);
                    }
                }
                
                if (modelPath != null) {
                    Identifier id = Identifier.of(modelPath);
                    String path = id.getPath();
                    if (!path.startsWith("models/")) path = "models/" + path;
                    if (!path.endsWith(".json")) path = path + ".json";
                    return Identifier.of(id.getNamespace(), path);
                }
            }
        }
        return Identifier.of(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json");
    }

    private static String getModelPathFromJson(com.google.gson.JsonElement element) {
        if (element.isJsonObject() && element.getAsJsonObject().has("model")) {
            return element.getAsJsonObject().get("model").getAsString();
        } else if (element.isJsonArray() && element.getAsJsonArray().size() > 0) {
            var first = element.getAsJsonArray().get(0).getAsJsonObject();
            if (first.has("model")) return first.get("model").getAsString();
        }
        return null;
    }

    private static boolean copyTexture(Identifier blockId, Identifier textureId, Path texturesDir, ResourceManager rm) throws IOException {
        Identifier pngId = Identifier.of(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
        Optional<Resource> pngRes = rm.getResource(pngId);

        if (pngRes.isPresent()) {
            try (InputStream is = pngRes.get().getInputStream()) {
                Path dest = texturesDir.resolve(blockId.getPath() + ".png");
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } else {
            LOGGER.warn("Texture PNG not found for {}: {}", blockId, pngId);
            return false;
        }
    }

    private static Identifier findTextureInModel(Resource resource, ResourceManager rm) throws IOException {
        return findTextureInModel(resource, rm, new HashMap<>());
    }

    private static Identifier findTextureInModel(Resource resource, ResourceManager rm, Map<String, String> textures) throws IOException {
        try (BufferedReader reader = resource.getReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if (json.has("textures")) {
                JsonObject texJson = json.getAsJsonObject("textures");
                for (String key : texJson.keySet()) {
                    String val = texJson.get(key).getAsString();
                    if (!val.startsWith("#")) {
                        textures.putIfAbsent(key, val);
                    }
                }

                // Rule: side > top > normal (all/texture)
                String[] priorityKeys = {"side", "top", "all", "texture", "candle", "carpet", "layer0"};
                for (String key : priorityKeys) {
                    if (texJson.has(key)) {
                        String val = texJson.get(key).getAsString();
                        if (val.startsWith("#")) {
                            String ref = val.substring(1);
                            if (textures.containsKey(ref)) return Identifier.of(textures.get(ref));
                        } else {
                            return Identifier.of(val);
                        }
                    }
                    if (textures.containsKey(key)) {
                        return Identifier.of(textures.get(key));
                    }
                }
            }

            if (json.has("parent")) {
                Identifier parentId = Identifier.of(json.get("parent").getAsString());
                String path = parentId.getPath();
                if (!path.startsWith("models/")) path = "models/" + path;
                if (!path.endsWith(".json")) path = path + ".json";

                Optional<Resource> parentRes = rm.getResource(Identifier.of(parentId.getNamespace(), path));
                if (parentRes.isPresent()) return findTextureInModel(parentRes.get(), rm, textures);
            }

            for (String val : textures.values()) {
                if (!val.startsWith("#")) return Identifier.of(val);
            }

            return null;
        }
    }
}