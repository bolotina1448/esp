package com.blockhit;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collections;
import java.util.List;

public class BlockHitCommand extends CommandBase {

    private static final String P = EnumChatFormatting.DARK_AQUA + "[BHM] " + EnumChatFormatting.RESET;

    @Override
    public String getCommandName() {
        return "blockhitmining";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("bhm");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/bhm [toggle|on|off|autoblock]";
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
        BlockHitConfig c = BlockHitMod.config;
        if (args.length == 0) {
            msg(sender, "BlockHit Mining: " + onOff(c.enabled) + EnumChatFormatting.GRAY
                    + "  autoblock: " + onOff(c.autoBlock));
            msg(sender, EnumChatFormatting.GRAY + "Зажми ЛКМ + ПКМ с мечом, глядя на блок. Команды: /bhm toggle | autoblock");
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("toggle") || sub.equals("on") || sub.equals("off")) {
            c.enabled = sub.equals("toggle") ? !c.enabled : sub.equals("on");
            msg(sender, "BlockHit Mining " + onOff(c.enabled));
        } else if (sub.equals("autoblock")) {
            c.autoBlock = !c.autoBlock;
            msg(sender, "Начинать блок во время копания: " + onOff(c.autoBlock));
        } else {
            msg(sender, EnumChatFormatting.RED + getCommandUsage(sender));
            return;
        }
        c.save();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "toggle", "on", "off", "autoblock");
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
