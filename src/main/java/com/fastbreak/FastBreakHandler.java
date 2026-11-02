package com.fastbreak;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.BlockPos;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * Логика FastBreak.
 *
 * Ванильный PlayerControllerMP каждый тик, пока зажата ЛКМ, прибавляет к curBlockDamageMP
 * "силу удара" (getPlayerRelativeBlockHardness). Когда значение доходит до 1.0 — клиент
 * шлёт C07 STOP_DESTROY_BLOCK и ставит blockHitDelay = 5 тиков до следующего блока.
 *
 * Мы в конце каждого тика:
 *  - INSTANT: STOP_DESTROY_BLOCK в тот же тик, что START (1 тик);
 *  - THRESHOLD / POLAR / MAX: если на следующем тике прогресс достигнет порога —
 *    поднимаем его до 1.0, и ванила сама сломает блок обычным пакетом (время ломания ≈ порог × ванила);
 *  - MULTIPLIER: докидываем прогресс, чтобы итоговая скорость была ×multiplier;
 *  - режем blockHitDelay до заданного значения.
 */
public class FastBreakHandler {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    private static Field fDamage;
    private static Field fHitDelay;
    private static Field fHitting;
    private static Field fCurrentBlock;
    private static boolean reflectionOk;

    static {
        try {
            fDamage = ReflectionHelper.findField(PlayerControllerMP.class, "curBlockDamageMP", "field_78770_f");
            fHitDelay = ReflectionHelper.findField(PlayerControllerMP.class, "blockHitDelay", "field_78781_i");
            fHitting = ReflectionHelper.findField(PlayerControllerMP.class, "isHittingBlock", "field_78778_j");
            fCurrentBlock = ReflectionHelper.findField(PlayerControllerMP.class, "currentBlock", "field_178895_c");
            reflectionOk = true;
        } catch (Throwable t) {
            reflectionOk = false;
            System.err.println("[FastBreak] Reflection failed, module disabled: " + t);
        }
    }

    private BlockPos lastPos;
    private float rolledThreshold = 1f;
    private BlockPos instantPos;
    private int instantTicks;

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (FastBreakMod.toggleKey != null && FastBreakMod.toggleKey.isPressed()) {
            FastBreakConfig c = FastBreakMod.config;
            c.enabled = !c.enabled;
            c.save();
            chat("FastBreak " + (c.enabled ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")
                    + EnumChatFormatting.GRAY + " [" + c.mode.display + "]");
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !reflectionOk) return;

        FastBreakConfig c = FastBreakMod.config;
        PlayerControllerMP pc = mc.playerController;
        if (!c.enabled || mc.thePlayer == null || mc.theWorld == null || pc == null) return;
        if (pc.isInCreativeMode()) return;

        try {
            // 0) Только подходящий инструмент и блок из его списка — иначе всё как в ваниле
            if (c.toolsOnly) {
                BlockPos target = fHitting.getBoolean(pc) ? (BlockPos) fCurrentBlock.get(pc) : null;
                MovingObjectPosition look = mc.objectMouseOver;
                if (target == null && look != null && look.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    target = look.getBlockPos();
                }
                Block tb = target == null ? null : mc.theWorld.getBlockState(target).getBlock();
                if (tb == null || !c.tools.allows(mc.thePlayer.getHeldItem(), tb)) {
                    lastPos = null;
                    instantPos = null;
                    return;
                }
            }

            // 1) Задержка между блоками (ванила = 5 тиков)
            int maxDelay = c.effectiveHitDelay();
            if (fHitDelay.getInt(pc) > maxDelay) {
                fHitDelay.setInt(pc, maxDelay);
            }

            // 2) Ускорение ломания текущего блока
            if (!fHitting.getBoolean(pc)) {
                lastPos = null;
                instantPos = null;
                return;
            }
            BlockPos pos = (BlockPos) fCurrentBlock.get(pc);
            if (pos == null) return;

            boolean fastMode = c.mode == FastBreakConfig.Mode.MAX || c.mode == FastBreakConfig.Mode.INSTANT;
            if (c.onlyOnGround && !fastMode && !mc.thePlayer.onGround) return;

            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block.getMaterial() == Material.air) return;

            float step = block.getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, pos);
            if (step <= 0f || step >= 1f) return; // неломаемый или и так ломается мгновенно

            // INSTANT: шлём STOP через instantDelay тиков после START (0 = в тот же тик) и ломаем блок на клиенте
            if (c.mode == FastBreakConfig.Mode.INSTANT) {
                if (!pos.equals(instantPos)) {
                    instantPos = pos;
                    instantTicks = 0;
                }
                if (instantTicks < c.instantDelay) {
                    instantTicks++;
                    return;
                }
                instantPos = null;
                EnumFacing face = EnumFacing.UP;
                MovingObjectPosition mop = mc.objectMouseOver;
                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && pos.equals(mop.getBlockPos()) && mop.sideHit != null) {
                    face = mop.sideHit;
                }
                mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, face));
                pc.onPlayerDestroyBlock(pos, face);
                fHitting.setBoolean(pc, false);
                fDamage.setFloat(pc, 0f);
                fHitDelay.setInt(pc, 0);
                lastPos = null;
                return;
            }

            // Новый блок — бросаем новый порог с разбросом
            if (!pos.equals(lastPos)) {
                lastPos = pos;
                float jitter = c.effectiveJitter();
                float t = c.effectiveThreshold() + (random.nextFloat() * 2f - 1f) * jitter;
                rolledThreshold = FastBreakConfig.clamp(t, 0f, 1f);
            }

            float damage = fDamage.getFloat(pc);

            if (c.mode == FastBreakConfig.Mode.MULTIPLIER) {
                if (damage <= 0f) return;
                float boosted = damage + step * (c.multiplier - 1f);
                fDamage.setFloat(pc, Math.min(boosted, 1f));
                return;
            }

            // Хотя бы один ванильный тик прогресса
            if (damage <= 0f) return;

            // Если на следующем тике прогресс дойдёт до порога — ломаем на следующем тике
            if (damage + step >= rolledThreshold) {
                fDamage.setFloat(pc, 1f);
            }
        } catch (IllegalAccessException e) {
            reflectionOk = false;
            System.err.println("[FastBreak] " + e);
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        FastBreakConfig c = FastBreakMod.config;
        if (c.enabled && c.hud) {
            String extra = "";
            if (c.mode == FastBreakConfig.Mode.INSTANT) extra = " " + c.instantDelay + "t";
            if (c.toolsOnly && mc.thePlayer != null) {
                MovingObjectPosition look = mc.objectMouseOver;
                boolean ok = look != null && look.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && c.tools.allows(mc.thePlayer.getHeldItem(), mc.theWorld.getBlockState(look.getBlockPos()).getBlock());
                extra += ok ? EnumChatFormatting.GREEN + " \u25CF" : EnumChatFormatting.DARK_GRAY + " \u25CF";
            }
            event.right.add(EnumChatFormatting.AQUA + "FastBreak " + EnumChatFormatting.GRAY + c.mode.display + extra);
        }
    }

    private void chat(String msg) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.DARK_AQUA + "[FB] " + EnumChatFormatting.RESET + msg));
        }
    }
}
