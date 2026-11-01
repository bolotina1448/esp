package com.scaffold;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

/**
 * Clutch / Scaffold в духе Augustus:
 *  - тихие (серверные) повороты с ограничением скорости и GCD-фиксом под чувствительность мыши;
 *  - MoveFix: движение пересчитывается под серверный yaw, чтобы оно совпадало с поворотом;
 *  - блок ставится только когда реальный рейтрейс с серверного поворота попадает в нужную грань;
 *  - ExtraBlockSearch: если прямо под ногами не за что зацепиться — ищет блок рядом и строит к себе.
 *
 * Тик:
 *  START  — запоминаем камеру, ищем куда ставить, считаем следующий серверный поворот;
 *  (ванила) updatePlayerMoveState -> onMoveInput: MoveFix + ставим rotationYaw/Pitch = серверные,
 *           ванила двигает игрока и шлёт C03 уже с ними;
 *  END    — ставим блок (C08 уходит после C03 этого тика) и возвращаем камеру как была.
 */
public class ScaffoldHandler {

    private static final class Plan {
        final BlockPos target;
        final BlockPos neighbor;
        final EnumFacing face;
        final Vec3 hit;

        Plan(BlockPos target, BlockPos neighbor, EnumFacing face, Vec3 hit) {
            this.target = target;
            this.neighbor = neighbor;
            this.face = face;
            this.hit = hit;
        }
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    // Повороты
    private boolean overriding;
    private boolean appliedThisTick;
    private float serverYaw, serverPitch;
    private float cameraYaw, cameraPitch;

    // Состояние
    private Plan plan;
    private BlockPos lastTarget;
    private double hitJitterA, hitJitterB;
    private boolean clutching;
    private int keep;
    private int placeCooldown;
    private int prevSlot = -1;
    private int sameY = Integer.MIN_VALUE;

    // ------------------------------------------------------------------ события

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (ScaffoldMod.toggleKey != null && ScaffoldMod.toggleKey.isPressed()) {
            ScaffoldConfig c = ScaffoldMod.config;
            c.enabled = !c.enabled;
            c.save();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "[SC] "
                        + EnumChatFormatting.RESET + "Scaffold "
                        + (c.enabled ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")
                        + EnumChatFormatting.GRAY + " [" + c.mode.display + "]"));
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            preTick();
        } else {
            postTick();
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        ScaffoldConfig c = ScaffoldMod.config;
        if (!c.enabled || !c.hud || mc.thePlayer == null) return;
        int blocks = countBlocks(mc.thePlayer);
        event.right.add(EnumChatFormatting.AQUA + "Scaffold " + EnumChatFormatting.GRAY + c.mode.display
                + " " + (blocks > 0 ? EnumChatFormatting.WHITE : EnumChatFormatting.RED) + blocks
                + (plan != null ? EnumChatFormatting.GREEN + " ●" : ""));
    }

    // ------------------------------------------------------------------ START

    private void preTick() {
        appliedThisTick = false;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            overriding = false;
            plan = null;
            prevSlot = -1;
            return;
        }
        if (!ScaffoldMovementInput.chainContains(p.movementInput, ScaffoldMovementInput.class)) {
            p.movementInput = new ScaffoldMovementInput(p.movementInput, this);
        }

        cameraYaw = p.rotationYaw;
        cameraPitch = p.rotationPitch;
        if (!overriding) {
            serverYaw = cameraYaw;
            serverPitch = cameraPitch;
        }

        ScaffoldConfig c = ScaffoldMod.config;
        if (p.onGround) sameY = MathHelper.floor_double(p.posY) - 1;

        boolean want = c.enabled && mc.currentScreen == null && !p.capabilities.isFlying && !p.isSpectator()
                && !p.isInWater() && !p.isInLava() && !p.isOnLadder();

        clutching = want && isFalling(p, c);
        boolean bridging = want && c.mode == ScaffoldConfig.Mode.SCAFFOLD && !clutching && needsBridge(p);
        want = clutching || bridging;

        if (want && !selectBlocks(p, c)) want = false;

        plan = want ? findPlan(p, c) : null;

        if (plan != null) {
            if (!plan.target.equals(lastTarget)) {
                lastTarget = plan.target;
                hitJitterA = (random.nextDouble() * 2 - 1) * 0.25;
                hitJitterB = (random.nextDouble() * 2 - 1) * 0.25;
            }
            float[] rot = rotationsTo(p, plan.hit);
            float speed = clutching ? rand(c.clutchMinSpeed, c.clutchMaxSpeed) : rand(c.minSpeed, c.maxSpeed);
            stepTowards(rot[0], rot[1], speed);
            overriding = true;
            keep = c.keepTicks;
        } else if (overriding && keep > 0) {
            keep--;
        } else if (overriding) {
            // плавно возвращаемся к камере
            stepTowards(cameraYaw, cameraPitch, rand(c.minSpeed, c.maxSpeed));
            if (Math.abs(MathHelper.wrapAngleTo180_float(cameraYaw - serverYaw)) < 1f
                    && Math.abs(cameraPitch - serverPitch) < 1f) {
                overriding = false;
                // чтобы на сервер не улетел скачок yaw на 360°
                float k = Math.round((serverYaw - cameraYaw) / 360f);
                if (k != 0) {
                    p.rotationYaw += 360f * k;
                    p.prevRotationYaw += 360f * k;
                    cameraYaw = p.rotationYaw;
                }
            }
        }

        if (!want && !overriding && prevSlot >= 0) {
            if (c.switchBack) p.inventory.currentItem = prevSlot;
            prevSlot = -1;
        }
    }

    // ------------------------------------------------------------------ hook из MovementInput

    void onMoveInput(MovementInput in) {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || !overriding || appliedThisTick) return;
        moveFix(in);
        p.rotationYaw = serverYaw;
        p.rotationPitch = serverPitch;
        appliedThisTick = true;
    }

    // ------------------------------------------------------------------ END

    private void postTick() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) return;

        if (placeCooldown > 0) placeCooldown--;
        if (appliedThisTick && plan != null && placeCooldown <= 0) {
            tryPlace(p, ScaffoldMod.config);
        }
        if (appliedThisTick) {
            p.rotationYaw = cameraYaw;
            p.rotationPitch = cameraPitch;
        }
    }

    private void tryPlace(EntityPlayerSP p, ScaffoldConfig c) {
        ItemStack stack = p.getHeldItem();
        if (!isGoodBlock(stack)) return;

        MovingObjectPosition mop = rayTrace(p, serverYaw, serverPitch, c.reach);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mop.sideHit == null) return;

        BlockPos placed = mop.getBlockPos().offset(mop.sideHit);
        if (!placed.equals(plan.target)) return;
        if (!isClickable(mop.getBlockPos())) return;
        // блок не должен оказаться внутри игрока
        if (intersectsPlayer(p, placed)) return;

        if (mc.playerController.onPlayerRightClick(p, mc.theWorld, stack, mop.getBlockPos(), mop.sideHit, mop.hitVec)) {
            if (c.noSwing) {
                mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
            } else {
                p.swingItem();
            }
            if (stack.stackSize <= 0) {
                p.inventory.mainInventory[p.inventory.currentItem] = null;
            }
            placeCooldown = c.placeDelay;
        }
    }

    // ------------------------------------------------------------------ когда срабатывать

    private boolean isFalling(EntityPlayerSP p, ScaffoldConfig c) {
        if (p.onGround || p.motionY >= 0) return false;
        AxisAlignedBB bb = p.getEntityBoundingBox();
        int startY = MathHelper.floor_double(bb.minY) - 1;
        int depth = c.voidOnly ? startY + 1 : c.fallBlocks;
        double[][] pts = {
                {bb.minX + 0.001, bb.minZ + 0.001}, {bb.maxX - 0.001, bb.minZ + 0.001},
                {bb.minX + 0.001, bb.maxZ - 0.001}, {bb.maxX - 0.001, bb.maxZ - 0.001},
                {p.posX, p.posZ}};
        for (double[] pt : pts) {
            int x = MathHelper.floor_double(pt[0]);
            int z = MathHelper.floor_double(pt[1]);
            for (int y = startY; y >= Math.max(0, startY - depth + 1); y--) {
                Material m = mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock().getMaterial();
                if (m.blocksMovement() || m.isLiquid()) return false; // есть на что упасть
            }
        }
        return true;
    }

    private boolean needsBridge(EntityPlayerSP p) {
        int y = p.onGround ? MathHelper.floor_double(p.posY) - 1 : Math.min(sameY, MathHelper.floor_double(p.posY) - 1);
        BlockPos under = new BlockPos(MathHelper.floor_double(p.posX), y, MathHelper.floor_double(p.posZ));
        return isReplaceable(under);
    }

    // ------------------------------------------------------------------ поиск места

    private Plan findPlan(EntityPlayerSP p, ScaffoldConfig c) {
        Vec3 eye = p.getPositionEyes(1.0F);
        AxisAlignedBB bb = p.getEntityBoundingBox();

        // где игрок будет через пару тиков
        double px = p.posX + p.motionX * 1.5;
        double pz = p.posZ + p.motionZ * 1.5;
        double py = bb.minY + (clutching ? p.motionY * 1.5 : 0);

        int topY;
        if (clutching) {
            topY = MathHelper.floor_double(py) - 1;
        } else {
            topY = p.onGround ? MathHelper.floor_double(p.posY) - 1 : Math.min(sameY, MathHelper.floor_double(p.posY) - 1);
        }
        int levels = clutching ? 3 : 1;
        int r = c.search;

        Plan best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dy = 0; dy < levels; dy++) {
            int y = topY - dy;
            if (y < 0) break;
            if (y + 1 > bb.minY + 1e-3) continue; // должно быть ниже ног
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = new BlockPos(MathHelper.floor_double(px) + dx, y, MathHelper.floor_double(pz) + dz);
                    if (!isReplaceable(pos)) continue;

                    boolean supports = overlaps(pos, px, pz, 0.3);
                    if (!supports && !nextToSupport(pos, px, pz)) continue; // ExtraBlockSearch: только шаг к себе

                    Plan cand = bestFace(p, eye, pos, c.reach);
                    if (cand == null) continue;

                    double cx = pos.getX() + 0.5 - px;
                    double cz = pos.getZ() + 0.5 - pz;
                    double score = Math.sqrt(cx * cx + cz * cz) + dy * 0.6 + (supports ? 0 : 2.0);
                    float[] rot = rotationsTo(p, cand.hit);
                    score += (Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - serverYaw))
                            + Math.abs(rot[1] - serverPitch)) / 360.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = cand;
                    }
                }
            }
            if (best != null && clutching) break; // самый высокий уровень — раньше спасёт
        }
        return best;
    }

    private Plan bestFace(EntityPlayerSP p, Vec3 eye, BlockPos target, float reach) {
        Plan best = null;
        double bestDelta = Double.MAX_VALUE;
        for (EnumFacing d : EnumFacing.values()) {
            BlockPos nb = target.offset(d);
            if (!isClickable(nb)) continue;
            EnumFacing face = d.getOpposite(); // грань соседа, смотрящая на target
            double fx = face.getFrontOffsetX(), fy = face.getFrontOffsetY(), fz = face.getFrontOffsetZ();
            Vec3 center = new Vec3(nb.getX() + 0.5 + fx * 0.5, nb.getY() + 0.5 + fy * 0.5, nb.getZ() + 0.5 + fz * 0.5);

            // грань должна смотреть на игрока
            double vis = (eye.xCoord - center.xCoord) * fx + (eye.yCoord - center.yCoord) * fy + (eye.zCoord - center.zCoord) * fz;
            if (vis <= 0.01) continue;
            if (eye.distanceTo(center) > reach) continue;

            Vec3 hit = jitter(center, face);
            float[] rot = rotationsTo(p, hit);
            double delta = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - serverYaw)) + Math.abs(rot[1] - serverPitch);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = new Plan(target, nb, face, hit);
            }
        }
        return best;
    }

    private Vec3 jitter(Vec3 c, EnumFacing face) {
        double a = hitJitterA, b = hitJitterB;
        switch (face.getAxis()) {
            case X: return new Vec3(c.xCoord, c.yCoord + a, c.zCoord + b);
            case Y: return new Vec3(c.xCoord + a, c.yCoord, c.zCoord + b);
            default: return new Vec3(c.xCoord + a, c.yCoord + b, c.zCoord);
        }
    }

    private static boolean overlaps(BlockPos pos, double px, double pz, double half) {
        return pos.getX() < px + half && pos.getX() + 1 > px - half
                && pos.getZ() < pz + half && pos.getZ() + 1 > pz - half;
    }

    private static boolean nextToSupport(BlockPos pos, double px, double pz) {
        for (EnumFacing d : EnumFacing.Plane.HORIZONTAL) {
            if (overlaps(pos.offset(d), px, pz, 0.3)) return true;
        }
        return false;
    }

    private boolean intersectsPlayer(EntityPlayerSP p, BlockPos pos) {
        AxisAlignedBB bb = p.getEntityBoundingBox();
        return bb.maxX > pos.getX() && bb.minX < pos.getX() + 1
                && bb.maxY > pos.getY() && bb.minY < pos.getY() + 1
                && bb.maxZ > pos.getZ() && bb.minZ < pos.getZ() + 1;
    }

    private boolean isReplaceable(BlockPos pos) {
        Block b = mc.theWorld.getBlockState(pos).getBlock();
        return b.isReplaceable(mc.theWorld, pos);
    }

    private boolean isClickable(BlockPos pos) {
        Block b = mc.theWorld.getBlockState(pos).getBlock();
        if (b.getMaterial() == Material.air || b.getMaterial().isLiquid()) return false;
        if (!b.getMaterial().blocksMovement()) return false;
        if (b instanceof BlockContainer || b == Blocks.crafting_table || b == Blocks.anvil
                || b == Blocks.bed || b == Blocks.enchanting_table) return false; // откроют GUI
        return true;
    }

    // ------------------------------------------------------------------ блоки в руке

    private boolean selectBlocks(EntityPlayerSP p, ScaffoldConfig c) {
        if (isGoodBlock(p.getHeldItem())) return true;
        if (!c.autoSwitch) return false;
        int bestSlot = -1, bestCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (isGoodBlock(s) && s.stackSize > bestCount) {
                bestCount = s.stackSize;
                bestSlot = i;
            }
        }
        if (bestSlot < 0) return false;
        if (prevSlot < 0) prevSlot = p.inventory.currentItem;
        p.inventory.currentItem = bestSlot;
        return true;
    }

    private static boolean isGoodBlock(ItemStack s) {
        if (s == null || s.stackSize <= 0 || !(s.getItem() instanceof ItemBlock)) return false;
        Block b = ((ItemBlock) s.getItem()).getBlock();
        if (b instanceof BlockFalling || b instanceof BlockContainer) return false;
        if (b == Blocks.tnt || b == Blocks.web || b == Blocks.crafting_table) return false;
        return b.isFullCube();
    }

    private int countBlocks(EntityPlayerSP p) {
        int n = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (isGoodBlock(s)) n += s.stackSize;
        }
        return n;
    }

    // ------------------------------------------------------------------ повороты

    private void stepTowards(float targetYaw, float targetPitch, float maxStep) {
        float dYaw = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float dPitch = targetPitch - serverPitch;
        // делим скорость между осями пропорционально, чтобы движение было по прямой
        float len = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
        if (len > maxStep) {
            dYaw = dYaw / len * maxStep;
            dPitch = dPitch / len * maxStep;
        }
        float gcd = gcd();
        dYaw = Math.round(dYaw / gcd) * gcd;
        dPitch = Math.round(dPitch / gcd) * gcd;
        serverYaw += dYaw;
        serverPitch = MathHelper.clamp_float(serverPitch + dPitch, -90f, 90f);
    }

    /** Минимальный шаг поворота мышью при текущей чувствительности (как в ваниле). */
    private float gcd() {
        float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return f * f * f * 8.0F * 0.15F;
    }

    private static float[] rotationsTo(EntityPlayerSP p, Vec3 v) {
        Vec3 eye = p.getPositionEyes(1.0F);
        double dx = v.xCoord - eye.xCoord;
        double dy = v.yCoord - eye.yCoord;
        double dz = v.zCoord - eye.zCoord;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, MathHelper.clamp_float(pitch, -90f, 90f)};
    }

    private MovingObjectPosition rayTrace(EntityPlayerSP p, float yaw, float pitch, double reach) {
        Vec3 eye = p.getPositionEyes(1.0F);
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        Vec3 look = new Vec3(f1 * f2, f3, f * f2);
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        return mc.theWorld.rayTraceBlocks(eye, end, false, false, true);
    }

    // ------------------------------------------------------------------ MoveFix

    private void moveFix(MovementInput in) {
        float f = in.moveForward, s = in.moveStrafe;
        if (f == 0 && s == 0) return;
        float mult = Math.max(Math.abs(f), Math.abs(s));

        double cam = Math.toRadians(cameraYaw);
        double ix = s * Math.cos(cam) - f * Math.sin(cam);
        double iz = f * Math.cos(cam) + s * Math.sin(cam);

        double srv = Math.toRadians(serverYaw);
        int bestF = 0, bestS = 0;
        double bestDot = -Double.MAX_VALUE;
        for (int fi = -1; fi <= 1; fi++) {
            for (int si = -1; si <= 1; si++) {
                if (fi == 0 && si == 0) continue;
                double vx = si * Math.cos(srv) - fi * Math.sin(srv);
                double vz = fi * Math.cos(srv) + si * Math.sin(srv);
                double dot = (vx * ix + vz * iz) / Math.sqrt(fi * fi + si * si);
                if (dot > bestDot) {
                    bestDot = dot;
                    bestF = fi;
                    bestS = si;
                }
            }
        }
        in.moveForward = bestF * mult;
        in.moveStrafe = bestS * mult;
    }

    private float rand(float min, float max) {
        return max <= min ? min : min + random.nextFloat() * (max - min);
    }
}
