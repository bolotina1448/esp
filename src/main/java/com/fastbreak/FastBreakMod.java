package com.fastbreak;

import com.fastbreak.backtrack.BacktrackCommand;
import com.fastbreak.backtrack.BacktrackConfig;
import com.fastbreak.backtrack.BacktrackHandler;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;

@Mod(modid = FastBreakMod.MODID, name = "FastBreak", version = "1.1.0",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class FastBreakMod {

    public static final String MODID = "fastbreak";

    public static FastBreakConfig config;
    public static BacktrackConfig backtrackConfig;

    public static KeyBinding toggleKey;
    public static KeyBinding backtrackKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File dir = event.getModConfigurationDirectory();
        config = new FastBreakConfig(new File(dir, "fastbreak.properties"));
        config.load();
        backtrackConfig = new BacktrackConfig(new File(dir, "backtrack.properties"));
        backtrackConfig.load();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        toggleKey = new KeyBinding("Toggle FastBreak", Keyboard.KEY_G, "FastBreak");
        backtrackKey = new KeyBinding("Toggle Backtrack", Keyboard.KEY_H, "FastBreak");
        ClientRegistry.registerKeyBinding(toggleKey);
        ClientRegistry.registerKeyBinding(backtrackKey);

        MinecraftForge.EVENT_BUS.register(new FastBreakHandler());
        MinecraftForge.EVENT_BUS.register(new BacktrackHandler());
        ClientCommandHandler.instance.registerCommand(new FastBreakCommand());
        ClientCommandHandler.instance.registerCommand(new BacktrackCommand());
    }
}
