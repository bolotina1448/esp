package com.blockhit;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Копание блока во время блока мечом, как в 1.7.10.
 *
 * В 1.8 Minecraft.sendClickBlockToController() не копает, пока игрок «использует предмет»
 * (держит блок мечом), а rightClickMouse() не даёт начать блок, пока копаешь.
 * В 1.7.10 этих проверок не было. Мод в конце тика делает то же, что 1.7.10:
 *  - зажаты ЛКМ + ПКМ, в руке меч, смотришь на блок → если ещё не блокируешь, начинает блок;
 *  - пока блокируешь, сам продолжает копать блок (onPlayerDamageBlock + частицы + взмах);
 *  - отпустил ЛКМ или отвёл взгляд → сбрасывает копание (как ванила).
 */
public class BlockHitHandler {

    private final Minecraft mc = Minecraft.getMinecraft();

    private boolean usingAtStart;
    private boolean mining;

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (BlockHitMod.toggleKey != null && BlockHitMod.toggleKey.isPressed()) {
            BlockHitConfig c = BlockHitMod.config;
            c.enabled = !c.enabled;
            c.save();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "[BHM] "
                        + EnumChatFormatting.RESET + "BlockHit Mining "
                        + (c.enabled ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")));
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null || mc.playerController == null) {
            mining = false;
            usingAtStart = false;
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            // если в начале тика уже блокируем — ванила в этом тике копать не будет, копаем мы
            usingAtStart = p.isUsingItem();
            return;
        }

        BlockHitConfig c = BlockHitMod.config;
        boolean lmb = mc.gameSettings.keyBindAttack.isKeyDown();
        boolean rmb = mc.gameSettings.keyBindUseItem.isKeyDown();
        boolean focus = mc.currentScreen == null && mc.inGameHasFocus;

        ItemStack held = p.getHeldItem();
        boolean sword = held != null && held.getItemUseAction() == EnumAction.BLOCK;

        MovingObjectPosition mop = mc.objectMouseOver;
        boolean onBlock = mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos() != null
                && mc.theWorld.getBlockState(mop.getBlockPos()).getBlock().getMaterial() != Material.air;

        if (!c.enabled || !focus || !sword || !lmb || !rmb || !onBlock || p.isSpectator()) {
            if (mining) {
                mining = false;
                // ванила во время блока сама не сбрасывает копание — делаем как 1.7.10
                if (p.isUsingItem() || !lmb || !onBlock) {
                    mc.playerController.resetBlockRemoving();
                }
            }
            return;
        }

        // 1) Блок мечом: в 1.8 нельзя начать блок, пока копаешь — начинаем сами
        if (!p.isUsingItem()) {
            if (c.autoBlock) {
                mc.playerController.sendUseItem(p, mc.theWorld, held);
            }
            // в этом тике ванила уже копала сама (мы ещё не блокировали) — второй раз не бьём
            return;
        }

        // 2) Копание во время блока
        if (!usingAtStart) return; // блок начался в этом тике — ванила уже ударила по блоку
        BlockPos pos = mop.getBlockPos();
        if (mc.playerController.onPlayerDamageBlock(pos, mop.sideHit)) {
            mc.effectRenderer.addBlockHitEffects(pos, mop.sideHit);
            p.swingItem();
        }
        mining = true;
    }
}
