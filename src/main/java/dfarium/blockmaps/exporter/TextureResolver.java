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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
        return findTextureInModel(resource, rm, new HashMap<>());
    }

    private static Identifier findTextureInModel(Resource resource, ResourceManager rm, Map<String, String> textures) throws IOException {
        try (BufferedReader reader = resource.openAsReader()) {
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
                            if (textures.containsKey(ref)) return Identifier.parse(textures.get(ref));
                        } else {
                            return Identifier.parse(val);
                        }
                    }
                    if (textures.containsKey(key)) {
                        return Identifier.parse(textures.get(key));
                    }
                }
            }

            if (json.has("parent")) {
                Identifier parentId = Identifier.parse(json.get("parent").getAsString());
                String path = parentId.getPath();
                if (!path.startsWith("models/")) path = "models/" + path;
                if (!path.endsWith(".json")) path = path + ".json";

                Optional<Resource> parentRes = rm.getResource(Identifier.fromNamespaceAndPath(parentId.getNamespace(), path));
                if (parentRes.isPresent()) return findTextureInModel(parentRes.get(), rm, textures);
            }

            for (String val : textures.values()) {
                if (!val.startsWith("#")) return Identifier.parse(val);
            }

            return null;
        }
    }
}
