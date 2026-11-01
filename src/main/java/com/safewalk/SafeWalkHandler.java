package com.safewalk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

/**
 * SafeWalk без приседания и только с блоком в руке.
 *
 * Предсказываем, куда ванила сдвинет игрока в этом тике (тот же расчёт, что в moveFlying),
 * режем сдвиг ванильным алгоритмом приседа у края (Entity.moveEntity) и подгоняем motion так,
 * чтобы итоговый сдвиг получился обрезанным. Пакет приседа не уходит, скорость не падает.
 */
public class SafeWalkHandler {

    private final Minecraft mc = Minecraft.getMinecraft();

    private SafeWalkInput mine;
    private EntityPlayerSP minePlayer;

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (SafeWalkMod.toggleKey != null && SafeWalkMod.toggleKey.isPressed()) {
            SafeWalkConfig c = SafeWalkMod.config;
            c.enabled = !c.enabled;
            c.save();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "[SW] "
                        + EnumChatFormatting.RESET + "SafeWalk "
                        + (c.enabled ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")));
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) {
            mine = null;
            minePlayer = null;
            autoSneak = false;
            return;
        }
        ensureOutermost(p);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        SafeWalkConfig c = SafeWalkMod.config;
        if (!c.enabled || !c.hud || mc.thePlayer == null) return;
        boolean on = !c.onlyBlocks || holdingBlock(mc.thePlayer);
        event.right.add((on ? EnumChatFormatting.AQUA : EnumChatFormatting.DARK_GRAY) + "SafeWalk "
                + EnumChatFormatting.GRAY + c.mode.display + (autoSneak ? EnumChatFormatting.GREEN + " ●" : ""));
    }

    // ------------------------------------------------------------------ обёртка MovementInput

    /**
     * Наша обёртка должна быть самой внешней: тогда Scaffold (или другой мод) уже подменил
     * поворот и ввод под MoveFix, и мы предсказываем движение с теми значениями, которые реально пойдут в ход.
     */
    private void ensureOutermost(EntityPlayerSP p) {
        MovementInput top = p.movementInput;
        if (top instanceof SafeWalkInput) {
            mine = (SafeWalkInput) top;
            minePlayer = p;
            return;
        }
        if (minePlayer != p) {
            mine = null;
        }

        // ищем свою обёртку внутри цепочки и вынимаем её
        MovementInput prev = null;
        MovementInput cur = top;
        boolean found = false;
        int guard = 0;
        while (cur != null && guard++ < 16) {
            if (cur == mine) {
                found = true;
                break;
            }
            prev = cur;
            cur = parentOf(cur);
        }

        if (found && prev != null) {
            if (!setParent(prev, mine.parent)) return; // не смогли переставить — оставляем как есть
            mine.parent = top;
            p.movementInput = mine;
            return;
        }
        if (!found && mine != null) {
            // нас обернул мод без поля parent — цепочку не видно, но мы в ней; не трогаем
            return;
        }

        mine = new SafeWalkInput(top, this);
        minePlayer = p;
        p.movementInput = mine;
    }

    private static MovementInput parentOf(MovementInput in) {
        try {
            Object o = in.getClass().getField("parent").get(in);
            return o instanceof MovementInput ? (MovementInput) o : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean setParent(MovementInput in, MovementInput parent) {
        try {
            Field f = in.getClass().getField("parent");
            f.set(in, parent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ SafeWalk

    // состояние режима SNEAK
    private boolean autoSneak;
    private int sneakHeld;
    private int sneakTarget;
    private final java.util.Random random = new java.util.Random();

    void onMoveInput(MovementInput in) {
        EntityPlayerSP p = mc.thePlayer;
        SafeWalkConfig c = SafeWalkMod.config;
        boolean eligible = p != null && mc.theWorld != null && c.enabled
                && (!c.onlyBlocks || holdingBlock(p))
                && p.onGround && !in.jump && !in.sneak // в прыжке не держим; если приседаешь сам — держит ванила
                && !p.capabilities.isFlying && !p.isInWater() && !p.isInLava() && !p.isOnLadder();
        if (!eligible) {
            autoSneak = false;
            return;
        }

        AxisAlignedBB bb = p.getEntityBoundingBox();
        double[] a = accel(p, bb, in.moveStrafe, in.moveForward);
        double dx = p.motionX + a[0];
        double dz = p.motionZ + a[1];
        double[] clipped = clipToEdge(p, bb, dx, dz);
        boolean atEdge = clipped[0] != dx || clipped[1] != dz;

        if (c.mode == SafeWalkConfig.Mode.SILENT) {
            autoSneak = false;
            if (atEdge) {
                p.motionX = clipped[0] - a[0];
                p.motionZ = clipped[1] - a[1];
            }
            return;
        }

        // SNEAK: у края жмём шифт на короткое время; отпускаем, как только край ушёл (поставил блок)
        if (!autoSneak && atEdge) {
            autoSneak = true;
            sneakHeld = 0;
            int min = c.minTicks, max = Math.max(c.minTicks, c.maxTicks);
            sneakTarget = min + random.nextInt(max - min + 1);
        }
        if (autoSneak) {
            sneakHeld++;
            if (sneakHeld > sneakTarget && !atEdge) autoSneak = false;
        }
        if (autoSneak) {
            // как настоящий шифт: пакет приседа уходит, скорость ×0.3, ванила держит у края
            in.sneak = true;
            in.moveStrafe *= 0.3F;
            in.moveForward *= 0.3F;
        }
    }

    /** Ускорение, которое ванила добавит в moveFlying (moveStrafing/moveForward умножаются на 0.98). */
    private double[] accel(EntityPlayerSP p, AxisAlignedBB bb, float moveStrafe, float moveForward) {
        float strafe = moveStrafe * 0.98F;
        float forward = moveForward * 0.98F;
        float f = strafe * strafe + forward * forward;
        if (f < 1.0E-4F) return new double[]{0, 0};
        BlockPos below = new BlockPos(MathHelper.floor_double(p.posX),
                MathHelper.floor_double(bb.minY) - 1, MathHelper.floor_double(p.posZ));
        float slip = mc.theWorld.getBlockState(below).getBlock().slipperiness * 0.91F;
        float friction = p.getAIMoveSpeed() * (0.16277136F / (slip * slip * slip));
        f = MathHelper.sqrt_float(f);
        if (f < 1.0F) f = 1.0F;
        f = friction / f;
        strafe *= f;
        forward *= f;
        float sin = MathHelper.sin(p.rotationYaw * (float) Math.PI / 180.0F);
        float cos = MathHelper.cos(p.rotationYaw * (float) Math.PI / 180.0F);
        return new double[]{strafe * cos - forward * sin, forward * cos + strafe * sin};
    }

    boolean isAutoSneaking() {
        return autoSneak;
    }

    /** Ванильный алгоритм приседа у края + небольшой запас на погрешность предсказания. */
    private double[] clipToEdge(EntityPlayerSP p, AxisAlignedBB bb, double x, double z) {
        final double step = 0.05D;
        final double m = 0.05D;

        while (x != 0.0D && noGround(p, bb.offset(x + Math.signum(x) * m, -1.0D, 0.0D))) {
            x = shrink(x, step);
        }
        while (z != 0.0D && noGround(p, bb.offset(0.0D, -1.0D, z + Math.signum(z) * m))) {
            z = shrink(z, step);
        }
        while (x != 0.0D && z != 0.0D
                && noGround(p, bb.offset(x + Math.signum(x) * m, -1.0D, z + Math.signum(z) * m))) {
            x = shrink(x, step);
            z = shrink(z, step);
        }
        return new double[]{x, z};
    }

    private static double shrink(double v, double step) {
        if (v < step && v >= -step) return 0.0D;
        return v > 0.0D ? v - step : v + step;
    }

    private boolean noGround(EntityPlayerSP p, AxisAlignedBB box) {
        return mc.theWorld.getCollidingBoundingBoxes(p, box).isEmpty();
    }

    static boolean holdingBlock(EntityPlayerSP p) {
        ItemStack s = p.getHeldItem();
        return s != null && s.stackSize > 0 && s.getItem() instanceof ItemBlock;
    }
}
