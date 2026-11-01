package com.safewalk;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;

@Mod(modid = SafeWalkMod.MODID, name = "SafeWalk", version = "1.1.0",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class SafeWalkMod {

    public static final String MODID = "safewalk";

    public static SafeWalkConfig config;
    public static KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new SafeWalkConfig(new File(event.getModConfigurationDirectory(), "safewalk.properties"));
        config.load();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        toggleKey = new KeyBinding("Toggle SafeWalk", Keyboard.KEY_K, "SafeWalk");
        ClientRegistry.registerKeyBinding(toggleKey);

        MinecraftForge.EVENT_BUS.register(new SafeWalkHandler());
        ClientCommandHandler.instance.registerCommand(new SafeWalkCommand());
    }
}
