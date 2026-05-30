package dfarium.blockmaps;

import dfarium.blockmaps.exporter.PaletteExporter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Blockmaps implements ModInitializer {
    public static final String MOD_ID = "blockmaps";

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(PaletteExporter::run);
    }
}