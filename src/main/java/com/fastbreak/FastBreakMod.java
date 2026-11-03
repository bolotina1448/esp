package com.fastbreak;

import com.fastbreak.backtrack.BacktrackCommand;
import com.fastbreak.backtrack.BacktrackConfig;
import com.fastbreak.backtrack.BacktrackHandler;
import com.fastbreak.clutch.ClutchCommand;
import com.fastbreak.clutch.ClutchConfig;
import com.fastbreak.clutch.ClutchHandler;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;

@Mod(modid = FastBreakMod.MODID, name = "FastBreak", version = "1.3.1",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class FastBreakMod {

    public static final String MODID = "fastbreak";

    public static FastBreakConfig config;
    public static BacktrackConfig backtrackConfig;

    public static KeyBinding toggleKey;
    public static KeyBinding backtrackKey;
    public static KeyBinding clutchKey;
    public static ClutchConfig clutchConfig;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File dir = event.getModConfigurationDirectory();
        config = new FastBreakConfig(new File(dir, "fastbreak.properties"));
        config.load();
        backtrackConfig = new BacktrackConfig(new File(dir, "backtrack.properties"));
        backtrackConfig.load();
        clutchConfig = new ClutchConfig(new File(dir, "clutch.properties"));
        clutchConfig.load();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        toggleKey = new KeyBinding("Toggle FastBreak", Keyboard.KEY_G, "FastBreak");
        backtrackKey = new KeyBinding("Toggle Backtrack", Keyboard.KEY_H, "FastBreak");
        ClientRegistry.registerKeyBinding(toggleKey);
        clutchKey = new KeyBinding("Toggle Clutch", Keyboard.KEY_J, "FastBreak");
        ClientRegistry.registerKeyBinding(backtrackKey);
        ClientRegistry.registerKeyBinding(clutchKey);

        MinecraftForge.EVENT_BUS.register(new FastBreakHandler());
        MinecraftForge.EVENT_BUS.register(new BacktrackHandler());
        MinecraftForge.EVENT_BUS.register(new ClutchHandler());
        ClientCommandHandler.instance.registerCommand(new FastBreakCommand());
        ClientCommandHandler.instance.registerCommand(new BacktrackCommand());
        ClientCommandHandler.instance.registerCommand(new ClutchCommand());
    }
}
