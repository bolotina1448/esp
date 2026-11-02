package com.blockhit;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;

@Mod(modid = BlockHitMod.MODID, name = "BlockHit Mining", version = "1.0.0",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class BlockHitMod {

    public static final String MODID = "blockhitmining";

    public static BlockHitConfig config;
    public static KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new BlockHitConfig(new File(event.getModConfigurationDirectory(), "blockhitmining.properties"));
        config.load();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // по умолчанию без клавиши — назначается в «Управление → BlockHit Mining»
        toggleKey = new KeyBinding("Toggle BlockHit Mining", Keyboard.KEY_NONE, "BlockHit Mining");
        ClientRegistry.registerKeyBinding(toggleKey);

        MinecraftForge.EVENT_BUS.register(new BlockHitHandler());
        ClientCommandHandler.instance.registerCommand(new BlockHitCommand());
    }
}
