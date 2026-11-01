package com.fastbreak.backtrack;

import com.fastbreak.FastBreakMod;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Backtrack: пока ты в бою с игроком (недавно его ударил и он в заданной дистанции),
 * входящие пакеты придерживаются на delay мс — ты видишь и бьёшь цель в её чуть более старой позиции.
 */
public class BacktrackHandler {

    private final Minecraft mc = Minecraft.getMinecraft();

    private Channel channel;
    private PacketDelayer delayer;

    private EntityPlayer target;
    private long lastAttack;
    private long activeSince = -1;
    private long cooldownUntil;

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (event.entityPlayer == mc.thePlayer && event.target instanceof EntityPlayer) {
            target = (EntityPlayer) event.target;
            lastAttack = System.currentTimeMillis();
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (FastBreakMod.backtrackKey != null && FastBreakMod.backtrackKey.isPressed()) {
            BacktrackConfig c = FastBreakMod.backtrackConfig;
            c.enabled = !c.enabled;
            c.save();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "[BT] "
                        + EnumChatFormatting.RESET + "Backtrack "
                        + (c.enabled ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")
                        + EnumChatFormatting.GRAY + " [" + c.mode.display + ", " + c.delay + "ms]"));
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        ensureInjected();
        PacketDelayer d = delayer;
        if (d == null) return;

        BacktrackConfig c = FastBreakMod.backtrackConfig;
        d.allPackets = c.mode == BacktrackConfig.Mode.POLAR;
        d.delayMs = c.delay;

        boolean want = shouldBeActive(c);
        long now = System.currentTimeMillis();

        if (now < cooldownUntil) want = false;
        if (want) {
            if (activeSince < 0) {
                activeSince = now;
            } else if (now - activeSince > c.maxHold) {
                // держали слишком долго — отпускаем и ждём, чтобы не было большого скачка пинга
                want = false;
                activeSince = -1;
                cooldownUntil = now + c.cooldown;
            }
        } else {
            activeSince = -1;
        }

        boolean wasActive = d.active;
        d.active = want;
        if (wasActive && !want) d.flushAsync();
    }

    private boolean shouldBeActive(BacktrackConfig c) {
        if (!c.enabled || c.delay <= 0 || mc.thePlayer == null || mc.theWorld == null) return false;
        if (!mc.thePlayer.isEntityAlive()) return false;
        if (target == null) return false;
        if (target.isDead || !target.isEntityAlive() || target.worldObj != mc.theWorld) {
            target = null;
            return false;
        }
        if (System.currentTimeMillis() - lastAttack > c.combatWindow) return false;
        float dist = mc.thePlayer.getDistanceToEntity(target);
        return dist >= c.minRange && dist <= c.maxRange;
    }

    private void ensureInjected() {
        NetHandlerPlayClient nh = mc.getNetHandler();
        if (nh == null || nh.getNetworkManager() == null) {
            channel = null;
            delayer = null;
            target = null;
            activeSince = -1;
            return;
        }
        Channel ch = nh.getNetworkManager().channel();
        if (ch == null || !ch.isOpen()) return;
        if (ch == channel && delayer != null) return;

        try {
            ChannelHandler existing = ch.pipeline().get(PacketDelayer.NAME);
            if (existing instanceof PacketDelayer) {
                delayer = (PacketDelayer) existing;
            } else {
                PacketDelayer d = new PacketDelayer();
                ch.pipeline().addBefore("packet_handler", PacketDelayer.NAME, d);
                delayer = d;
            }
            channel = ch;
        } catch (Throwable t) {
            System.err.println("[Backtrack] Failed to inject into pipeline: " + t);
            channel = ch;
            delayer = null;
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        BacktrackConfig c = FastBreakMod.backtrackConfig;
        if (!c.enabled || !c.hud) return;
        PacketDelayer d = delayer;
        boolean on = d != null && d.active;
        event.right.add(EnumChatFormatting.AQUA + "Backtrack " + EnumChatFormatting.GRAY + c.mode.display
                + " " + c.delay + "ms"
                + (on ? EnumChatFormatting.GREEN + " ● " + d.queued : ""));
    }
}
