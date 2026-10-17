# BlockMaps Exporter

An automated Fabric mod tool for Minecraft that extracts the map color palette of blocks, resolves their respective textures, and generates the block database along with metadata such as physical support requirements and introduction versions.

Target: **Minecraft Java 26.3 / Java 25**. For IntelliJ IDEA setup, commands,
validation and promotion of the generated palette, see [the 26.3 migration guide](UPDATE_26_3.md).

---

## 🚀 Features

1. **Client Automation**: Boots the Minecraft client and directly enters the world without requiring manual player interaction.
2. **Automatic World Generation**: If the target world is missing in the saved games directory (`saves/`), the mod creates NBT files for a **superflat air-only world**. Always use a disposable export world; world loading must be checked when upgrading Minecraft.
3. **Color Palette Extraction**:
   - Groups blocks by their `MapColor`.
   - Calculates the 4 brightness levels (`lowest`, `low`, `normal`, `high`) in RGB format.
   - Evaluates if the block requires a support block beneath it (using `canSurvive` in air coordinates, detecting gravity-affected blocks, and brushable blocks).
4. **Texture Resolution**: Recursively resolves the `blockstate` and model JSON files for each block to extract its primary PNG texture.
5. **Historical Database**: Preserves `introducedIn` from the existing `palette.json`; export fails if the historical database cannot be loaded. IDs absent from that database provisionally receive the current game version and must be reviewed before promotion.
6. **Auto-Exit**: Automatically stops the integrated server and shuts down the Minecraft client gracefully once the export is complete.

---

## 💻 Running the Exporter

To run the automated export, open your terminal in the root of the project and execute:

```powershell
.\gradlew.bat runAutoExportClient -Pworld="WorldName"
```

> 💡 **Note**: The `-Pworld` parameter is optional (defaults to `"BlockMaps-26.3"` for the current target). If the world folder does not exist in `run/saves/`, the tool will automatically create it as an empty superflat air world.

Once execution finishes, the log prints the exact output directory:
📁 `run/blockmaps_export/26_3/<timestamp>/`

Inside this folder, you will find:
- **`palette_<minecraft_version>.json`**: The complete palette JSON with colors, brightness values, support flags, and blocks grouped/sorted by material.
- **`block_map_colors.json`**: A simplified mapping of block identifiers to their map color IDs.
- **`export_report.json`**: Registry/export counts, DataVersion, additions relative to the historical palette, removals, missing textures and excluded blocks with reasons.
- **`textures/`**: A folder containing PNG textures for all processed blocks.

---

## 🔄 Updating `palette.json`

The file `palette.json` acts as the mod's base database to remember the introduction version of each block. When a new Minecraft version is released and you want to lock it in:

1. Generate the palette of the new version using the command above.
2. Follow the validation steps in [UPDATE_26_3.md](UPDATE_26_3.md), including checking additions' introduction versions, exclusions and texture appearance. Then copy `palette_26_3.json` from the successful run directory.
3. Paste it in the mod's resource path:
   📍 `src/main/resources/assets/blockmaps/`
4. Rename it exactly to **`palette.json`** (overwriting the existing file).

By doing this, in future runs all existing blocks will retain their original introduction version, and only new blocks added in subsequent updates will be tagged with that version.

The palette intentionally retains the existing map-art shape filter and evaluates
only default block states. It is not a complete inventory of every block or state.
For example, stairs remain excluded by the current filter; consult `excludedBlocks`
in the report when checking newly added Minecraft content.
