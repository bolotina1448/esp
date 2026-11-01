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
        return "/sw [toggle|on|off|blocks|hud]";
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
            msg(sender, "SafeWalk: " + onOff(c.enabled) + EnumChatFormatting.GRAY
                    + "  только с блоком: " + onOff(c.onlyBlocks) + EnumChatFormatting.GRAY + "  hud: " + onOff(c.hud));
            msg(sender, EnumChatFormatting.GRAY + "Команды: /sw toggle | on | off | blocks | hud");
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("toggle") || sub.equals("on") || sub.equals("off")) {
            c.enabled = sub.equals("toggle") ? !c.enabled : sub.equals("on");
            msg(sender, "SafeWalk " + onOff(c.enabled));
        } else if (sub.equals("blocks")) {
            c.onlyBlocks = !c.onlyBlocks;
            msg(sender, "Только с блоком в руке: " + onOff(c.onlyBlocks));
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
            return getListOfStringsMatchingLastWord(args, "toggle", "on", "off", "blocks", "hud");
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
