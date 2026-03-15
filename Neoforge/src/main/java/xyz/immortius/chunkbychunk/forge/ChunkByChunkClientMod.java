package xyz.immortius.chunkbychunk.forge;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import xyz.immortius.chunkbychunk.client.screens.ChunkByChunkConfigScreen;

public final class ChunkByChunkClientMod {

    private ChunkByChunkClientMod() {
    }

    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (minecraft, screen) -> new ChunkByChunkConfigScreen(screen));
    }
}
