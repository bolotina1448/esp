package com.fastbreak.clutch;

import com.fastbreak.FastBreakMod;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collections;
import java.util.List;

public class ClutchCommand extends CommandBase {

    private static final String P = EnumChatFormatting.DARK_AQUA + "[CL] " + EnumChatFormatting.RESET;

    @Override
    public String getCommandName() {
        return "clutch";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("cl");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/cl [toggle|mode|speed|clutchspeed|fall|void|search|reach|switch|switchback|noswing|delay|keep|hud]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        ClutchConfig c = FastBreakMod.clutchConfig;
        if (args.length == 0) {
            status(sender, c);
            return;
        }
        String sub = args[0].toLowerCase();
        String val = args.length > 1 ? args[1] : null;

        if (sub.equals("toggle") || sub.equals("on") || sub.equals("off")) {
            c.enabled = sub.equals("toggle") ? !c.enabled : sub.equals("on");
            msg(sender, "Clutch " + onOff(c.enabled));
        } else if (sub.equals("mode")) {
            ClutchConfig.Mode m = ClutchConfig.Mode.parse(val);
            if (m == null) {
                msg(sender, EnumChatFormatting.RED + "Режимы: clutch, scaffold");
                return;
            }
            c.mode = m;
            msg(sender, "Режим: " + EnumChatFormatting.AQUA + m.display);
        } else if (sub.equals("speed") || sub.equals("clutchspeed")) {
            if (args.length < 3) throw new CommandException("/cl " + sub + " <мин> <макс>, °/тик, например 40 70");
            float a = number(args[1], 1, 180), b = number(args[2], 1, 180);
            float min = Math.min(a, b), max = Math.max(a, b);
            if (sub.equals("speed")) {
                c.minSpeed = min;
                c.maxSpeed = max;
                msg(sender, "Скорость поворота: " + min + "–" + max + " °/тик");
            } else {
                c.clutchMinSpeed = min;
                c.clutchMaxSpeed = max;
                msg(sender, "Скорость поворота при клатче: " + min + "–" + max + " °/тик");
            }
        } else if (sub.equals("fall")) {
            c.fallBlocks = (int) number(val, 1, 30);
            msg(sender, "Клатч, если под тобой пусто на " + c.fallBlocks + "+ блоков");
        } else if (sub.equals("void")) {
            c.voidOnly = !c.voidOnly;
            msg(sender, "Только над войдом: " + onOff(c.voidOnly));
        } else if (sub.equals("search")) {
            c.search = (int) number(val, 0, 3);
            msg(sender, "Радиус поиска: " + c.search);
        } else if (sub.equals("reach")) {
            c.reach = number(val, 3, 6);
            msg(sender, "Дистанция: " + c.reach);
        } else if (sub.equals("switch")) {
            c.autoSwitch = !c.autoSwitch;
            msg(sender, "Авто-выбор блоков: " + onOff(c.autoSwitch));
        } else if (sub.equals("switchback")) {
            c.switchBack = !c.switchBack;
            msg(sender, "Возврат слота: " + onOff(c.switchBack));
        } else if (sub.equals("noswing")) {
            c.noSwing = !c.noSwing;
            msg(sender, "NoSwing: " + onOff(c.noSwing));
        } else if (sub.equals("delay")) {
            c.placeDelay = (int) number(val, 0, 10);
            msg(sender, "Задержка между блоками: " + c.placeDelay + " тик(ов)");
        } else if (sub.equals("keep")) {
            c.keepTicks = (int) number(val, 0, 20);
            msg(sender, "Держать поворот: " + c.keepTicks + " тик(ов)");
        } else if (sub.equals("hud")) {
            c.hud = !c.hud;
            msg(sender, "HUD: " + onOff(c.hud));
        } else {
            msg(sender, EnumChatFormatting.RED + getCommandUsage(sender));
            return;
        }
        c.save();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "toggle", "on", "off", "mode", "speed", "clutchspeed",
                    "fall", "void", "search", "reach", "switch", "switchback", "noswing", "delay", "keep", "hud");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return getListOfStringsMatchingLastWord(args, "clutch", "scaffold");
        }
        return null;
    }

    private void status(ICommandSender s, ClutchConfig c) {
        msg(s, "Clutch: " + onOff(c.enabled) + EnumChatFormatting.GRAY + "  режим: "
                + EnumChatFormatting.AQUA + c.mode.display);
        msg(s, EnumChatFormatting.GRAY + "speed=" + c.minSpeed + "-" + c.maxSpeed
                + " clutchspeed=" + c.clutchMinSpeed + "-" + c.clutchMaxSpeed
                + " fall=" + c.fallBlocks + " void=" + c.voidOnly + " search=" + c.search + " reach=" + c.reach);
        msg(s, EnumChatFormatting.GRAY + "switch=" + c.autoSwitch + " switchback=" + c.switchBack
                + " noswing=" + c.noSwing + " delay=" + c.placeDelay + " keep=" + c.keepTicks);
    }

    private static float number(String v, float min, float max) throws CommandException {
        if (v == null) throw new CommandException("Укажи число от " + (int) min + " до " + (int) max);
        float f;
        try {
            f = Float.parseFloat(v.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new CommandException("Не число: " + v);
        }
        return ClutchConfig.clamp(f, min, max);
    }

    private static String onOff(boolean b) {
        return b ? EnumChatFormatting.GREEN + "ON" + EnumChatFormatting.RESET
                : EnumChatFormatting.RED + "OFF" + EnumChatFormatting.RESET;
    }

    private static void msg(ICommandSender s, String text) {
        s.addChatMessage(new ChatComponentText(P + text));
    }
}
