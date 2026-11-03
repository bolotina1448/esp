package com.fastbreak;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;

@Mod(modid = FastBreakMod.MODID, name = "FastBreak", version = "1.4.0",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class FastBreakMod {

    public static final String MODID = "fastbreak";

    public static FastBreakConfig config;
    public static KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new FastBreakConfig(new File(event.getModConfigurationDirectory(), "fastbreak.properties"));
        config.load();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        toggleKey = new KeyBinding("Toggle FastBreak", Keyboard.KEY_G, "FastBreak");
        ClientRegistry.registerKeyBinding(toggleKey);

        MinecraftForge.EVENT_BUS.register(new FastBreakHandler());
        ClientCommandHandler.instance.registerCommand(new FastBreakCommand());
    }
}
