package dfarium.blockmaps;

import dfarium.blockmaps.exporter.PaletteExporter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class Blockmaps implements ModInitializer {
    public static final String MOD_ID = "blockmaps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        checkAndCreateTemplateWorld();
        ServerLifecycleEvents.SERVER_STARTED.register(PaletteExporter::run);
    }

    private void checkAndCreateTemplateWorld() {
        String worldName = System.getProperty("blockmaps.world_name");
        if (worldName == null) {
            try {
                String[] args = net.fabricmc.loader.api.FabricLoader.getInstance().getLaunchArguments(true);
                for (int i = 0; i < args.length - 1; i++) {
                    if ("--quickPlaySingleplayer".equals(args[i])) {
                        worldName = args[i + 1];
                        break;
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("Could not check launch arguments for world name: {}", t.getMessage());
            }
        }
        if (worldName == null) return;
        
        Path savesDir = Path.of("saves");
        Path worldDir = savesDir.resolve(worldName);
        if (Files.exists(worldDir.resolve("level.dat")) && Files.exists(worldDir.resolve("data/minecraft/world_gen_settings.dat"))) {
            return;
        }
        
        try {
            LOGGER.info("World '{}' not found in saves. Creating template superflat air world...", worldName);
            Files.createDirectories(worldDir);
            
            // Core level and configuration files to extract
            String[] filesToCopy = {
                "level.dat",
                "level.dat_old",
                "data/minecraft/custom_boss_events.dat",
                "data/minecraft/game_rules.dat",
                "data/minecraft/random_sequences.dat",
                "data/minecraft/scheduled_events.dat",
                "data/minecraft/scoreboard.dat",
                "data/minecraft/stopwatches.dat",
                "data/minecraft/weather.dat",
                "data/minecraft/world_clocks.dat",
                "data/minecraft/world_gen_settings.dat"
            };
            
            for (String file : filesToCopy) {
                try (var is = getClass().getResourceAsStream("/assets/blockmaps/empty_world/" + file)) {
                    if (is != null) {
                        Path dest = worldDir.resolve(file);
                        Files.createDirectories(dest.getParent());
                        Files.copy(is, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        LOGGER.warn("Template file not found in resources: {}", file);
                    }
                }
            }
            
            // Recreate template directory skeleton
            String[] dirsToCreate = {
                "datapacks",
                "dimensions/minecraft/overworld",
                "dimensions/minecraft/the_nether",
                "dimensions/minecraft/the_end",
                "players/advancements",
                "players/data",
                "players/stats"
            };
            for (String dir : dirsToCreate) {
                Files.createDirectories(worldDir.resolve(dir));
            }
            
            LOGGER.info("Template world created successfully.");
        } catch (Exception e) {
            LOGGER.error("Failed to create template world", e);
        }
    }
}