package com.scaffold;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;

@Mod(modid = ScaffoldMod.MODID, name = "Scaffold", version = "1.0.0",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class ScaffoldMod {

    public static final String MODID = "scaffold";

    public static ScaffoldConfig config;
    public static KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new ScaffoldConfig(new File(event.getModConfigurationDirectory(), "scaffold.properties"));
        config.load();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        toggleKey = new KeyBinding("Toggle Scaffold", Keyboard.KEY_J, "Scaffold");
        ClientRegistry.registerKeyBinding(toggleKey);

        MinecraftForge.EVENT_BUS.register(new ScaffoldHandler());
        ClientCommandHandler.instance.registerCommand(new ScaffoldCommand());
    }
}
