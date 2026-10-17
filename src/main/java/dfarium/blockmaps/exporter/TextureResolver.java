package dfarium.blockmaps.exporter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TextureResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextureResolver.class);

    public static ResourceManager getBestResourceManager(MinecraftServer server) {
        try {
            Class<?> clientClass = Class.forName("net.minecraft.client.Minecraft");
            Object client = clientClass.getMethod("getInstance").invoke(null);
            ResourceManager rm = (ResourceManager) clientClass.getMethod("getResourceManager").invoke(client);
            LOGGER.info("Using Client ResourceManager for assets");
            return rm;
        } catch (Exception ignored) {
            LOGGER.info("Using Server ResourceManager (assets might not be available)");
            return server.getResourceManager();
        }
    }

    public static boolean extractTexture(Identifier blockId, Path texturesDir, ResourceManager resourceManager) {
        try {
            Identifier textureId = null;
            Block block = BuiltInRegistries.BLOCK.get(blockId).map(Holder::value).orElse(null);

            // Special case for candles: use item texture directly
            if (block instanceof AbstractCandleBlock) {
                Identifier itemModelId = Identifier.fromNamespaceAndPath(blockId.getNamespace(), "models/item/" + blockId.getPath() + ".json");
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

                // Fallback to item model
                if (textureId == null) {
                    Identifier itemModelId = Identifier.fromNamespaceAndPath(blockId.getNamespace(), "models/item/" + blockId.getPath() + ".json");
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
        Identifier blockstateId = Identifier.fromNamespaceAndPath(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        Optional<Resource> blockstateRes = rm.getResource(blockstateId);
        
        if (blockstateRes.isPresent()) {
            try (BufferedReader reader = blockstateRes.get().openAsReader()) {
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
                    Identifier id = Identifier.parse(modelPath);
                    String path = id.getPath();
                    if (!path.startsWith("models/")) path = "models/" + path;
                    if (!path.endsWith(".json")) path = path + ".json";
                    return Identifier.fromNamespaceAndPath(id.getNamespace(), path);
                }
            }
        }
        return Identifier.fromNamespaceAndPath(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json");
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
        Identifier pngId = Identifier.fromNamespaceAndPath(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
        Optional<Resource> pngRes = rm.getResource(pngId);

        if (pngRes.isPresent()) {
            try (InputStream is = pngRes.get().open()) {
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
        Map<String, String> textures = new LinkedHashMap<>();
        collectTextures(resource, rm, textures, new HashSet<>(), 0);
        // Keep the established icon preference, resolving aliases after inheritance.
        String[] priorityKeys = {"side", "top", "all", "texture", "candle", "carpet", "layer0"};
        for (String key : priorityKeys) {
            Identifier texture = resolveTexture(key, textures);
            if (texture != null) return texture;
        }
        for (String key : textures.keySet()) {
            Identifier texture = resolveTexture(key, textures);
            if (texture != null) return texture;
        }
        return null;
    }

    private static void collectTextures(Resource resource, ResourceManager rm, Map<String, String> textures,
                                        Set<Identifier> parents, int depth) throws IOException {
        if (depth >= 64) throw new IOException("Model parent chain exceeds 64 levels");
        try (BufferedReader reader = resource.openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("textures")) {
                JsonObject texJson = json.getAsJsonObject("textures");
                for (String key : texJson.keySet()) {
                    // Child definitions, including aliases, take precedence over parents.
                    var texture = texJson.get(key);
                    // 26.3 also allows sprite objects, e.g. stained glass with force_translucent.
                    if (texture.isJsonObject()) {
                        texture = texture.getAsJsonObject().get("sprite");
                    }
                    if (texture == null || !texture.isJsonPrimitive()
                            || !texture.getAsJsonPrimitive().isString()) {
                        throw new IOException("Invalid texture definition for model key: " + key);
                    }
                    textures.putIfAbsent(key, texture.getAsString());
                }
            }
            if (json.has("parent")) {
                Identifier parentId = Identifier.parse(json.get("parent").getAsString());
                if (!parents.add(parentId)) throw new IOException("Cyclic model parent: " + parentId);
                String path = parentId.getPath();
                if (!path.startsWith("models/")) path = "models/" + path;
                if (!path.endsWith(".json")) path = path + ".json";
                Optional<Resource> parentRes = rm.getResource(Identifier.fromNamespaceAndPath(parentId.getNamespace(), path));
                if (parentRes.isPresent()) collectTextures(parentRes.get(), rm, textures, parents, depth + 1);
            }
        }
    }

    private static Identifier resolveTexture(String key, Map<String, String> textures) {
        Set<String> visited = new HashSet<>();
        while (visited.add(key)) {
            String value = textures.get(key);
            if (value == null) return null;
            if (!value.startsWith("#")) return Identifier.parse(value);
            key = value.substring(1);
        }
        return null;
    }
}
