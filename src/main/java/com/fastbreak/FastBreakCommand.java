package com.fastbreak;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FastBreakCommand extends CommandBase {

    private static final String P = EnumChatFormatting.DARK_AQUA + "[FB] " + EnumChatFormatting.RESET;

    @Override
    public String getCommandName() {
        return "fastbreak";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("fb");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/fastbreak [toggle|mode|threshold|multiplier|delay|jitter|ground|hud]";
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
        FastBreakConfig c = FastBreakMod.config;

        if (args.length == 0) {
            status(sender, c);
            return;
        }

        String sub = args[0].toLowerCase();
        String val = args.length > 1 ? args[1] : null;

        if (sub.equals("toggle") || sub.equals("on") || sub.equals("off")) {
            c.enabled = sub.equals("toggle") ? !c.enabled : sub.equals("on");
            msg(sender, "FastBreak " + (c.enabled ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF"));
        } else if (sub.equals("mode")) {
            FastBreakConfig.Mode m = FastBreakConfig.Mode.parse(val);
            if (m == null) {
                msg(sender, EnumChatFormatting.RED + "Режимы: polar, threshold, multiplier, max, instant");
                return;
            }
            c.mode = m;
            msg(sender, "Режим: " + EnumChatFormatting.AQUA + m.display);
        } else if (sub.equals("threshold")) {
            c.threshold = number(sender, val, c.threshold, 0.05f, 1f);
            msg(sender, "Threshold: " + FastBreakConfig.fmt(c.threshold) + EnumChatFormatting.GRAY + " (режим threshold)");
        } else if (sub.equals("multiplier") || sub.equals("speed")) {
            c.multiplier = number(sender, val, c.multiplier, 1f, 5f);
            msg(sender, "Multiplier: x" + FastBreakConfig.fmt(c.multiplier) + EnumChatFormatting.GRAY + " (режим multiplier)");
        } else if (sub.equals("delay")) {
            c.hitDelay = (int) number(sender, val, c.hitDelay, 0f, 5f);
            msg(sender, "Hit delay: " + c.hitDelay + " тик(ов)" + EnumChatFormatting.GRAY + " (threshold/multiplier)");
        } else if (sub.equals("jitter")) {
            c.jitter = number(sender, val, c.jitter, 0f, 0.2f);
            msg(sender, "Jitter: ±" + FastBreakConfig.fmt(c.jitter));
        } else if (sub.equals("ground")) {
            c.onlyOnGround = !c.onlyOnGround;
            msg(sender, "Только на земле: " + onOff(c.onlyOnGround));
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
                    "toggle", "on", "off", "mode", "threshold", "multiplier", "delay", "jitter", "ground", "hud");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return getListOfStringsMatchingLastWord(args, "polar", "threshold", "multiplier", "max", "instant");
        }
        return null;
    }

    private void status(ICommandSender s, FastBreakConfig c) {
        msg(s, "FastBreak: " + onOff(c.enabled) + EnumChatFormatting.GRAY + "  режим: " + EnumChatFormatting.AQUA + c.mode.display);
        msg(s, EnumChatFormatting.GRAY + "threshold=" + FastBreakConfig.fmt(c.threshold)
                + " multiplier=x" + FastBreakConfig.fmt(c.multiplier)
                + " delay=" + c.hitDelay
                + " jitter=±" + FastBreakConfig.fmt(c.jitter)
                + " ground=" + c.onlyOnGround);
        msg(s, EnumChatFormatting.GRAY + "Polar-пресет: threshold " + FastBreakConfig.POLAR_THRESHOLD
                + "±" + FastBreakConfig.POLAR_JITTER + ", delay " + FastBreakConfig.POLAR_HIT_DELAY);
        msg(s, EnumChatFormatting.GRAY + "Команды: " + Arrays.toString(new String[]{
                "toggle", "mode <polar|threshold|multiplier|max|instant>", "threshold <0.05-1>",
                "multiplier <1-5>", "delay <0-5>", "jitter <0-0.2>", "ground", "hud"}));
    }

    private float number(ICommandSender s, String v, float def, float min, float max) throws CommandException {
        if (v == null) throw new CommandException("Укажи число от " + min + " до " + max);
        float f = FastBreakConfig.parseFloat(v, Float.NaN);
        if (Float.isNaN(f)) throw new CommandException("Не число: " + v);
        return FastBreakConfig.clamp(f, min, max);
    }

    private static String onOff(boolean b) {
        return b ? EnumChatFormatting.GREEN + "ON" + EnumChatFormatting.RESET : EnumChatFormatting.RED + "OFF" + EnumChatFormatting.RESET;
    }

    private static void msg(ICommandSender s, String text) {
        s.addChatMessage(new ChatComponentText(P + text));
    }
}
