package dfarium.blockmaps;

import dfarium.blockmaps.exporter.PaletteExporter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
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
            LOGGER.info("World '{}' not found or incomplete in saves. Generating superflat air world programmatically...", worldName);
            Files.createDirectories(worldDir);
            
            int dataVersion = net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
            
            // 1. Generate level.dat
            CompoundTag level = new CompoundTag();
            CompoundTag data = new CompoundTag();
            data.putString("LevelName", worldName);
            data.putInt("GameType", 1); // Creative Mode
            data.putByte("allowCommands", (byte) 1);
            data.putByte("initialized", (byte) 1);
            data.putInt("DataVersion", dataVersion);
            data.putInt("version", 19133); // Anvil world version
            data.putByte("WasModded", (byte) 1);
            
            // Version NBT block matching the current game version
            CompoundTag versionTag = new CompoundTag();
            versionTag.putInt("Id", dataVersion);
            versionTag.putString("Name", net.minecraft.SharedConstants.getCurrentVersion().name());
            versionTag.putString("Series", "main");
            versionTag.putByte("Snapshot", (byte) (net.minecraft.SharedConstants.getCurrentVersion().stable() ? 0 : 1));
            data.put("Version", versionTag);
            
            // difficulty_settings NBT block
            CompoundTag difficultySettings = new CompoundTag();
            difficultySettings.putString("difficulty", "normal");
            difficultySettings.putByte("hardcore", (byte) 0);
            difficultySettings.putByte("locked", (byte) 0);
            data.put("difficulty_settings", difficultySettings);
            
            CompoundTag spawn = new CompoundTag();
            spawn.putString("dimension", "minecraft:overworld");
            spawn.putIntArray("pos", new int[]{0, 320, 0});
            data.put("spawn", spawn);
            
            CompoundTag dataPacks = new CompoundTag();
            net.minecraft.nbt.ListTag enabled = new net.minecraft.nbt.ListTag();
            enabled.add(net.minecraft.nbt.StringTag.valueOf("vanilla"));
            dataPacks.put("Enabled", enabled);
            data.put("DataPacks", dataPacks);
            
            level.put("Data", data);
            
            try (var os = Files.newOutputStream(worldDir.resolve("level.dat"))) {
                NbtIo.writeCompressed(level, os);
            }
            
            // 2. Generate data/minecraft/world_gen_settings.dat
            Path wgsDir = worldDir.resolve("data/minecraft");
            Files.createDirectories(wgsDir);
            
            CompoundTag wgs = new CompoundTag();
            wgs.putInt("DataVersion", dataVersion);
            
            CompoundTag wgsData = new CompoundTag();
            wgsData.putByte("bonus_chest", (byte) 0);
            wgsData.putByte("generate_structures", (byte) 0);
            wgsData.putLong("seed", 0L);
            
            CompoundTag dimensions = new CompoundTag();
            
            // Overworld (Flat World generator setting only air)
            CompoundTag overworld = new CompoundTag();
            overworld.putString("type", "minecraft:overworld");
            CompoundTag generator = new CompoundTag();
            generator.putString("type", "minecraft:flat");
            CompoundTag settings = new CompoundTag();
            settings.putString("biome", "minecraft:the_void");
            settings.putByte("features", (byte) 0);
            settings.putByte("lakes", (byte) 0);
            net.minecraft.nbt.ListTag layers = new net.minecraft.nbt.ListTag();
            CompoundTag layer1 = new CompoundTag();
            layer1.putString("block", "minecraft:air");
            layer1.putInt("height", 1);
            layers.add(layer1);
            settings.put("layers", layers);
            generator.put("settings", settings);
            overworld.put("generator", generator);
            dimensions.put("minecraft:overworld", overworld);
            
            // Nether
            CompoundTag nether = new CompoundTag();
            nether.putString("type", "minecraft:the_nether");
            CompoundTag netherGen = new CompoundTag();
            netherGen.putString("type", "minecraft:noise");
            netherGen.putString("settings", "minecraft:nether");
            CompoundTag netherBiome = new CompoundTag();
            netherBiome.putString("type", "minecraft:multi_noise");
            netherBiome.putString("preset", "minecraft:nether");
            netherGen.put("biome_source", netherBiome);
            nether.put("generator", netherGen);
            dimensions.put("minecraft:the_nether", nether);
            
            // End
            CompoundTag end = new CompoundTag();
            end.putString("type", "minecraft:the_end");
            CompoundTag endGen = new CompoundTag();
            endGen.putString("type", "minecraft:noise");
            endGen.putString("settings", "minecraft:end");
            CompoundTag endBiome = new CompoundTag();
            endBiome.putString("type", "minecraft:the_end");
            endGen.put("biome_source", endBiome);
            end.put("generator", endGen);
            dimensions.put("minecraft:the_end", end);
            
            wgsData.put("dimensions", dimensions);
            wgs.put("data", wgsData);
            
            try (var os = Files.newOutputStream(wgsDir.resolve("world_gen_settings.dat"))) {
                NbtIo.writeCompressed(wgs, os);
            }
            
            LOGGER.info("Programmatic superflat air world created successfully.");
        } catch (Exception e) {
            LOGGER.error("Failed to generate superflat world programmatically", e);
        }
    }
}