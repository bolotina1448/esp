package com.safewalk;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collections;
import java.util.List;

public class SafeWalkCommand extends CommandBase {

    private static final String P = EnumChatFormatting.DARK_AQUA + "[SW] " + EnumChatFormatting.RESET;

    @Override
    public String getCommandName() {
        return "safewalk";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("sw");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/sw [toggle|on|off|mode|ticks|blocks|look|hud]";
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
    public void processCommand(ICommandSender sender, String[] args) {
        SafeWalkConfig c = SafeWalkMod.config;
        if (args.length == 0) {
            msg(sender, "SafeWalk: " + onOff(c.enabled) + EnumChatFormatting.GRAY + "  режим: "
                    + EnumChatFormatting.AQUA + c.mode.display + EnumChatFormatting.GRAY
                    + "  шифт: " + c.minTicks + "-" + c.maxTicks + " тик.");
            msg(sender, EnumChatFormatting.GRAY + "только с блоком: " + onOff(c.onlyBlocks)
                    + EnumChatFormatting.GRAY + "  только взгляд на блок под ногами: " + onOff(c.onlyLooking)
                    + EnumChatFormatting.GRAY + "  hud: " + onOff(c.hud));
            msg(sender, EnumChatFormatting.GRAY + "Команды: /sw toggle | mode <sneak|silent> | ticks <мин> <макс> | blocks | look | hud");
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("toggle") || sub.equals("on") || sub.equals("off")) {
            c.enabled = sub.equals("toggle") ? !c.enabled : sub.equals("on");
            msg(sender, "SafeWalk " + onOff(c.enabled));
        } else if (sub.equals("mode")) {
            SafeWalkConfig.Mode m = SafeWalkConfig.Mode.parse(args.length > 1 ? args[1] : null);
            if (m == null) {
                msg(sender, EnumChatFormatting.RED + "Режимы: sneak (жмёт шифт у края), silent (без приседа)");
                return;
            }
            c.mode = m;
            msg(sender, "Режим: " + EnumChatFormatting.AQUA + m.display);
        } else if (sub.equals("ticks")) {
            int a, b;
            try {
                a = Integer.parseInt(args[1]);
                b = args.length > 2 ? Integer.parseInt(args[2]) : a;
            } catch (Exception e) {
                msg(sender, EnumChatFormatting.RED + "/sw ticks <мин> <макс>, например /sw ticks 2 3 (1 тик = 0.05 с)");
                return;
            }
            a = Math.max(0, Math.min(20, a));
            b = Math.max(0, Math.min(20, b));
            c.minTicks = Math.min(a, b);
            c.maxTicks = Math.max(a, b);
            msg(sender, "Шифт у края минимум " + c.minTicks + "-" + c.maxTicks + " тик(ов)");
        } else if (sub.equals("blocks")) {
            c.onlyBlocks = !c.onlyBlocks;
            msg(sender, "Только с блоком в руке: " + onOff(c.onlyBlocks));
        } else if (sub.equals("look")) {
            c.onlyLooking = !c.onlyLooking;
            msg(sender, "Только когда смотришь на блок, на котором стоишь: " + onOff(c.onlyLooking));
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
            return getListOfStringsMatchingLastWord(args, "toggle", "on", "off", "mode", "ticks", "blocks", "look", "hud");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return getListOfStringsMatchingLastWord(args, "sneak", "silent");
        }
        return null;
    }

    private static String onOff(boolean b) {
        return b ? EnumChatFormatting.GREEN + "ON" + EnumChatFormatting.RESET
                : EnumChatFormatting.RED + "OFF" + EnumChatFormatting.RESET;
    }

    private static void msg(ICommandSender s, String text) {
        s.addChatMessage(new ChatComponentText(P + text));
    }
}
