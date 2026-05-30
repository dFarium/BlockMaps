# BlockMaps Exporter

An automated Fabric mod tool for Minecraft that extracts the map color palette of blocks, resolves their respective textures, and generates the block database along with metadata such as physical support requirements and introduction versions.

---

## 🚀 Features

1. **Client Automation**: Boots the Minecraft client and directly enters the world without requiring manual player interaction.
2. **Automatic World Generation**: If the target world is missing in the saved games directory (`saves/`), the mod programmatically generates all the necessary NBT data (`level.dat` and `world_gen_settings.dat`) to instantly create a **superflat air-only world** and load the game without any version-upgrade warnings.
3. **Color Palette Extraction**:
   - Groups blocks by their `MapColor`.
   - Calculates the 4 brightness levels (`lowest`, `low`, `normal`, `high`) in RGB format.
   - Evaluates if the block requires a support block beneath it (using `canSurvive` in air coordinates, detecting gravity-affected blocks, and brushable blocks).
4. **Texture Resolution**: Recursively resolves the `blockstate` and model JSON files for each block to extract its primary PNG texture.
5. **Historical Database**: Compares blocks against the existing database (`palette.json`) to preserve the `introducedIn` versions from past releases. If new blocks are found, they are tagged with the current Minecraft game version.
6. **Auto-Exit**: Automatically stops the integrated server and shuts down the Minecraft client gracefully once the export is complete.

---

## 💻 Running the Exporter

To run the automated export, open your terminal in the root of the project and execute:

```powershell
.\gradlew.bat runAutoExportClient -Pworld="WorldName"
```

> 💡 **Note**: The `-Pworld` parameter is optional (defaults to `"New World"`). If the world folder does not exist in `run/saves/`, the tool will automatically create it as an empty superflat air world.

Once the execution finishes (usually taking less than 20 seconds), you will find the results in:
📁 `run/blockmaps_export/`

Inside this folder, you will find:
- **`palette_<minecraft_version>.json`**: The complete palette JSON with colors, brightness values, support flags, and blocks grouped/sorted by material.
- **`block_map_colors.json`**: A simplified mapping of block identifiers to their map color IDs.
- **`textures/`**: A folder containing PNG textures for all processed blocks.

---

## 🔄 Updating `palette.json`

The file `palette.json` acts as the mod's base database to remember the introduction version of each block. When a new Minecraft version is released and you want to lock it in:

1. Generate the palette of the new version using the command above.
2. Go to the `run/blockmaps_export/` folder and copy the newly generated file (e.g., `palette_26_2 Pre-Release 2.json`).
3. Paste it in the mod's resource path:
   📍 `src/main/resources/assets/blockmaps/`
4. Rename it exactly to **`palette.json`** (overwriting the existing file).

By doing this, in future runs all existing blocks will retain their original introduction version, and only new blocks added in subsequent updates will be tagged with that version.
