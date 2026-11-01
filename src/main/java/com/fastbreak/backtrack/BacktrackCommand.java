package com.fastbreak.backtrack;

import com.fastbreak.FastBreakMod;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collections;
import java.util.List;

public class BacktrackCommand extends CommandBase {

    private static final String P = EnumChatFormatting.DARK_AQUA + "[BT] " + EnumChatFormatting.RESET;

    @Override
    public String getCommandName() {
        return "backtrack";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("bt");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/bt [toggle|on|off|mode|delay|range|maxhold|cooldown|window|hud]";
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
        BacktrackConfig c = FastBreakMod.backtrackConfig;

        if (args.length == 0) {
            status(sender, c);
            return;
        }

        String sub = args[0].toLowerCase();
        String val = args.length > 1 ? args[1] : null;

        if (sub.equals("toggle") || sub.equals("on") || sub.equals("off")) {
            c.enabled = sub.equals("toggle") ? !c.enabled : sub.equals("on");
            msg(sender, "Backtrack " + onOff(c.enabled));
        } else if (sub.equals("mode")) {
            BacktrackConfig.Mode m = BacktrackConfig.Mode.parse(val);
            if (m == null) {
                msg(sender, EnumChatFormatting.RED + "Режимы: polar, entity");
                return;
            }
            c.mode = m;
            msg(sender, "Режим: " + EnumChatFormatting.AQUA + m.display);
        } else if (sub.equals("delay")) {
            c.delay = (int) number(val, 0, 1000);
            msg(sender, "Delay: " + c.delay + " мс");
        } else if (sub.equals("range")) {
            if (args.length < 3) throw new CommandException("/bt range <мин> <макс>, например /bt range 2.5 6");
            float min = number(args[1], 0f, 6f);
            float max = number(args[2], 1f, 10f);
            if (max < min) { float t = min; min = max; max = t; }
            c.minRange = min;
            c.maxRange = max;
            msg(sender, "Дистанция: " + c.minRange + " – " + c.maxRange + " блоков");
        } else if (sub.equals("maxhold")) {
            c.maxHold = (int) number(val, 50, 5000);
            msg(sender, "Max hold: " + c.maxHold + " мс");
        } else if (sub.equals("cooldown")) {
            c.cooldown = (int) number(val, 0, 5000);
            msg(sender, "Cooldown: " + c.cooldown + " мс");
        } else if (sub.equals("window")) {
            c.combatWindow = (int) number(val, 100, 10000);
            msg(sender, "Окно боя: " + c.combatWindow + " мс после удара");
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
            return getListOfStringsMatchingLastWord(args,
                    "toggle", "on", "off", "mode", "delay", "range", "maxhold", "cooldown", "window", "hud");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return getListOfStringsMatchingLastWord(args, "polar", "entity");
        }
        return null;
    }

    private void status(ICommandSender s, BacktrackConfig c) {
        msg(s, "Backtrack: " + onOff(c.enabled) + EnumChatFormatting.GRAY + "  режим: "
                + EnumChatFormatting.AQUA + c.mode.display);
        msg(s, EnumChatFormatting.GRAY + "delay=" + c.delay + "ms range=" + c.minRange + "-" + c.maxRange
                + " maxhold=" + c.maxHold + "ms cooldown=" + c.cooldown + "ms window=" + c.combatWindow + "ms");
        msg(s, EnumChatFormatting.GRAY + "Команды: toggle, mode <polar|entity>, delay <мс>, range <мин> <макс>, "
                + "maxhold <мс>, cooldown <мс>, window <мс>, hud");
    }

    private static float number(String v, float min, float max) throws CommandException {
        if (v == null) throw new CommandException("Укажи число от " + (int) min + " до " + (int) max);
        float f;
        try {
            f = Float.parseFloat(v.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new CommandException("Не число: " + v);
        }
        return BacktrackConfig.clamp(f, min, max);
    }

    private static String onOff(boolean b) {
        return b ? EnumChatFormatting.GREEN + "ON" + EnumChatFormatting.RESET
                : EnumChatFormatting.RED + "OFF" + EnumChatFormatting.RESET;
    }

    private static void msg(ICommandSender s, String text) {
        s.addChatMessage(new ChatComponentText(P + text));
    }
}
