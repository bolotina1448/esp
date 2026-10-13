package com.playeresp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@Mod(modid = PlayerESP.MODID, name = "PlayerESP", version = "1.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class PlayerESP {

    public static final String MODID = "playeresp";

    private KeyBinding toggleKey;
    private boolean enabled = true;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Rebindable in Options > Controls > "PlayerESP"
        toggleKey = new KeyBinding("Toggle ESP", Keyboard.KEY_J, "PlayerESP");
        ClientRegistry.registerKeyBinding(toggleKey);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            enabled = !enabled;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GRAY + "[PlayerESP] " +
                        (enabled ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")));
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc.theWorld == null || mc.thePlayer == null) return;

        float pt = event.partialTicks;
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;

        // Interpolated camera position
        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();   // draw through walls
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0F);

        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || p.getHealth() <= 0) continue;

            double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt - camX;
            double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - camY;
            double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt - camZ;

            AxisAlignedBB bb = p.getEntityBoundingBox();
            double e = 0.05;
            AxisAlignedBB box = new AxisAlignedBB(
                    bb.minX - p.posX + x - e, bb.minY - p.posY + y,     bb.minZ - p.posZ + z - e,
                    bb.maxX - p.posX + x + e, bb.maxY - p.posY + y + e, bb.maxZ - p.posZ + z + e);

            // Green for teammates, red for everyone else
            if (p.isOnSameTeam(mc.thePlayer)) {
                GlStateManager.color(0.2F, 1.0F, 0.2F, 0.9F);
            } else {
                GlStateManager.color(1.0F, 0.25F, 0.25F, 0.9F);
            }
            drawBoxOutline(box);
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static void drawBoxOutline(AxisAlignedBB b) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);

        // bottom
        line(wr, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ);
        line(wr, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ);
        line(wr, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ);
        line(wr, b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ);
        // top
        line(wr, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ);
        line(wr, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ);
        line(wr, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
        line(wr, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ);
        // verticals
        line(wr, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ);
        line(wr, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ);
        line(wr, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ);
        line(wr, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ);

        tess.draw();
    }

    private static void line(WorldRenderer wr, double x1, double y1, double z1,
                             double x2, double y2, double z2) {
        wr.pos(x1, y1, z1).endVertex();
        wr.pos(x2, y2, z2).endVertex();
    }
}
