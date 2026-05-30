package dfarium.blockmaps.exporter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class VersionDatabase {
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionDatabase.class);

    public static Map<String, String> load(ResourceManager rm) {
        Map<String, String> versions = new HashMap<>();
        try {
            Identifier id = Identifier.fromNamespaceAndPath("blockmaps", "palette.json");
            Optional<Resource> res = rm.getResource(id);
            if (res.isPresent()) {
                try (BufferedReader reader = res.get().openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("colors")) {
                        var colors = json.getAsJsonArray("colors");
                        for (var colorEl : colors) {
                            var color = colorEl.getAsJsonObject();
                            if (color.has("blocks")) {
                                var blocks = color.getAsJsonArray("blocks");
                                for (var blockEl : blocks) {
                                    var block = blockEl.getAsJsonObject();
                                    if (block.has("id") && block.has("introducedIn")) {
                                        versions.put(block.get("id").getAsString(), block.get("introducedIn").getAsString());
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LOGGER.warn("palette.json not found in mod resources.");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load existing introducedIn versions", e);
        }
        return versions;
    }
}
