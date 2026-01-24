package dfarium.blockmaps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.jspecify.annotations.NonNull;
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
        for (Field field : MapColor.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() != MapColor.class) continue;
            try {
                MapColor color = (MapColor) field.get(null);
                MAP_COLOR_NAMES.put(color, field.getName().toLowerCase());
            } catch (IllegalAccessException ignored) {}
        }
    }

    @Override
    public void onInitialize() {
        // Necesitamos el ResourceManager, por lo que ejecutamos esto cuando el servidor (o el cliente integrado) inicia
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
    }
    private void onServerStarted(MinecraftServer server) {
        try {
            Path outputDir = Path.of("blockmaps_export");
            Path texturesDir = outputDir.resolve("textures");
            Files.createDirectories(texturesDir);

            ResourceManager resourceManager = server.getResourceManager();
            // Si estamos en un cliente, intentar usar el Client ResourceManager que contiene los assets
            try {
                Class<?> clientClass = Class.forName("net.minecraft.client.MinecraftClient");
                Object client = clientClass.getMethod("getInstance").invoke(null);
                resourceManager = (ResourceManager) clientClass.getMethod("getResourceManager").invoke(client);
                LOGGER.info("Using Client ResourceManager for assets");
            } catch (Exception ignored) {
                LOGGER.info("Using Server ResourceManager (assets might not be available)");
            }

            export(outputDir, texturesDir, resourceManager);
            LOGGER.info("Export completed in: {}", outputDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Error exporting data", e);
        }
    }

    public static void export(Path outputJson, Path texturesDir, ResourceManager resourceManager) throws IOException {
        Map<MapColor, Set<Identifier>> blocksByColor = new HashMap<>();
        int blocksProcessed = 0;
        int texturesExtracted = 0;

        for (Block block : Registries.BLOCK) {
            var state = block.getDefaultState();
            MapColor color = state.getMapColor(null, null);

            if (color == MapColor.CLEAR) continue;

            Identifier id = Registries.BLOCK.getId(block);
            boolean isFullBlock = state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            boolean isSlab = block instanceof SlabBlock || id.getPath().contains("slab");
            boolean isGlass = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) == VoxelShapes.fullCube();

            if (isFullBlock || isSlab || isGlass) {
                blocksProcessed++;
                blocksByColor.computeIfAbsent(color, k -> new HashSet<>()).add(id);
                // Intentar extraer la textura para este bloque
                if (extractTexture(id, texturesDir, resourceManager)) {
                    texturesExtracted++;
                }
            }
        }
        LOGGER.info("Processed {} blocks, successfully extracted {} textures", blocksProcessed, texturesExtracted);

        // --- Lógica de guardado del JSON (Igual que antes) ---
        List<ColorEntry> colors = new ArrayList<>();
        for (Map.Entry<MapColor, Set<Identifier>> entry : blocksByColor.entrySet()) {
            ColorEntry colorEntry = new ColorEntry();
            colorEntry.colorID = entry.getKey().id;
            colorEntry.colorName = MAP_COLOR_NAMES.getOrDefault(entry.getKey(), "unknown_" + entry.getKey().id);
            colorEntry.brightnessValues = BrightnessValues.from(entry.getKey());
            colorEntry.blocks = entry.getValue().stream().map(Identifier::toString).sorted().toList();
            colors.add(colorEntry);
        }
        colors.sort(Comparator.comparingInt(c -> c.colorID));
        Root root = new Root();
        root.colors = colors;
        Files.writeString(outputJson.resolve("block_map_colors.json"), GSON.toJson(root));
    }

    private static boolean extractTexture(Identifier blockId, Path texturesDir, ResourceManager resourceManager) {
        try {
            Identifier modelId = null;

            // 1. Intentar buscar en el blockstate para resolver el modelo
            Identifier blockstateId = Identifier.of(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
            Optional<Resource> blockstateRes = resourceManager.getResource(blockstateId);
            if (blockstateRes.isPresent()) {
                try (BufferedReader reader = blockstateRes.get().getReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("variants")) {
                        JsonObject variants = json.getAsJsonObject("variants");
                        if (!variants.keySet().isEmpty()) {
                            String firstKey = variants.keySet().iterator().next();
                            var variant = variants.get(firstKey);
                            String modelPath = null;
                            if (variant.isJsonObject() && variant.getAsJsonObject().has("model")) {
                                modelPath = variant.getAsJsonObject().get("model").getAsString();
                            } else if (variant.isJsonArray() && variant.getAsJsonArray().size() > 0) {
                                var firstVariant = variant.getAsJsonArray().get(0).getAsJsonObject();
                                if (firstVariant.has("model")) modelPath = firstVariant.get("model").getAsString();
                            }
                            if (modelPath != null) modelId = Identifier.of(modelPath);
                        }
                    } else if (json.has("multipart")) {
                        var multipart = json.getAsJsonArray("multipart");
                        if (multipart.size() > 0) {
                            var first = multipart.get(0).getAsJsonObject();
                            if (first.has("apply")) {
                                var apply = first.get("apply");
                                String modelPath = null;
                                if (apply.isJsonObject() && apply.getAsJsonObject().has("model")) {
                                    modelPath = apply.getAsJsonObject().get("model").getAsString();
                                } else if (apply.isJsonArray() && apply.getAsJsonArray().size() > 0) {
                                    var firstApply = apply.getAsJsonArray().get(0).getAsJsonObject();
                                    if (firstApply.has("model")) modelPath = firstApply.get("model").getAsString();
                                }
                                if (modelPath != null) modelId = Identifier.of(modelPath);
                            }
                        }
                    }
                }
            }

            // 2. Si no hay modelo de blockstate, fallback al modelo directo del bloque
            if (modelId == null) {
                modelId = Identifier.of(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json");
            } else {
                // El ID del model que viene del blockstate usualmente es "namespace:block/model"
                // Necesitamos convertirlo a "namespace:models/block/model.json" para cargarlo
                String ns = modelId.getNamespace();
                String path = modelId.getPath();
                if (!path.startsWith("models/")) path = "models/" + path;
                if (!path.endsWith(".json")) path = path + ".json";
                modelId = Identifier.of(ns, path);
            }

            Optional<Resource> modelRes = resourceManager.getResource(modelId);
            Identifier textureId = null;

            if (modelRes.isPresent()) {
                textureId = findTextureInModel(modelRes.get(), resourceManager);
            } else {
                LOGGER.debug("Model file not found: {}", modelId);
            }

            // 3. Si no hay textura aún, intentar modelo de item (para bloques especiales)
            if (textureId == null) {
                Identifier itemModelId = Identifier.of(blockId.getNamespace(), "models/item/" + blockId.getPath() + ".json");
                Optional<Resource> itemRes = resourceManager.getResource(itemModelId);
                if (itemRes.isPresent()) {
                    textureId = findTextureInModel(itemRes.get(), resourceManager);
                }
            }

            // 4. Si encontramos un ID de textura, copiar el .png
            if (textureId != null) {
                Identifier pngId = Identifier.of(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
                Optional<Resource> pngRes = resourceManager.getResource(pngId);

                if (pngRes.isPresent()) {
                    try (InputStream is = pngRes.get().getInputStream()) {
                        Path dest = texturesDir.resolve(blockId.getPath() + ".png");
                        Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
                        return true;
                    }
                } else {
                    LOGGER.warn("Texture PNG not found for {}: {}", blockId, pngId);
                }
            } else {
                LOGGER.warn("Could not find texture ID for {}", blockId);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to extract texture for {}", blockId, e);
        }
        return false;
    }

    private static Identifier findTextureInModel(Resource resource, ResourceManager rm) throws IOException {
        try (BufferedReader reader = resource.getReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            
            Identifier textureId = null;
            if (json.has("textures")) {
                JsonObject textures = json.getAsJsonObject("textures");
                // Prioridad: side -> all -> top -> layer0 -> texture
                String[] keys = {"side", "all", "top", "layer0", "texture"};
                for (String key : keys) {
                    if (textures.has(key)) {
                        String texPath = textures.get(key).getAsString();
                        if (texPath.startsWith("#")) continue; // Referencia, resolver en el parent
                        textureId = Identifier.of(texPath);
                        break;
                    }
                }
                
                // Si no encontramos con las keys prioritarias, tomar la primera que no sea referencia
                if (textureId == null && !textures.keySet().isEmpty()) {
                    for (String key : textures.keySet()) {
                        String texPath = textures.get(key).getAsString();
                        if (!texPath.startsWith("#")) {
                            textureId = Identifier.of(texPath);
                            break;
                        }
                    }
                }
            }

            // Si aún no hay textura o es una referencia, buscar en el padre
            if (textureId == null && json.has("parent")) {
                String parentStr = json.get("parent").getAsString();
                Identifier parentId = Identifier.of(parentStr);
                // Corregir ruta del parent (los models pueden estar en models/block o models/item o solo models)
                String path = parentId.getPath();
                if (!path.startsWith("models/")) path = "models/" + path;
                if (!path.endsWith(".json")) path = path + ".json";
                
                Identifier finalParentId = Identifier.of(parentId.getNamespace(), path);
                Optional<Resource> parentRes = rm.getResource(finalParentId);
                if (parentRes.isPresent()) return findTextureInModel(parentRes.get(), rm);
            }

            return textureId;
        }
    }

    // --- Clases POJO (Root, ColorEntry, etc.) permanecen igual ---
    private static final class Root { List<ColorEntry> colors; }
    private static final class ColorEntry { int colorID; String colorName; BrightnessValues brightnessValues; List<String> blocks; }
    private static final class BrightnessValues {
        RGB lowest, low, normal, high;
        static BrightnessValues from(MapColor color) {
            BrightnessValues v = new BrightnessValues();
            v.lowest = RGB.from(color.getRenderColor(MapColor.Brightness.LOWEST));
            v.low = RGB.from(color.getRenderColor(MapColor.Brightness.LOW));
            v.normal = RGB.from(color.getRenderColor(MapColor.Brightness.NORMAL));
            v.high = RGB.from(color.getRenderColor(MapColor.Brightness.HIGH));
            return v;
        }
    }
    private static final class RGB {
        int r, g, b;
        static @NonNull RGB from(int rgb) {
            RGB c = new RGB();
            c.r = (rgb >> 16) & 0xFF; c.g = (rgb >> 8) & 0xFF; c.b = rgb & 0xFF;
            return c;
        }
    }
}