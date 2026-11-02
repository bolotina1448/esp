package com.fastbreak;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Какие блоки каким инструментом можно ломать быстро.
 * FastBreak ускоряет только если в руке инструмент нужного типа, блок есть в его списке
 * и инструмент реально эффективен против блока (getStrVsBlock > 1). Остальное ломается как в ваниле.
 */
public class ToolRules {

    public enum Tool {
        SWORD("sword", "меч"),
        PICKAXE("pickaxe", "кирка"),
        AXE("axe", "топор"),
        SHOVEL("shovel", "лопата"),
        SHEARS("shears", "ножницы");

        public final String id;
        public final String ru;

        Tool(String id, String ru) {
            this.id = id;
            this.ru = ru;
        }

        public static Tool of(ItemStack s) {
            if (s == null) return null;
            Item i = s.getItem();
            if (i instanceof ItemSword) return SWORD;
            if (i instanceof ItemPickaxe) return PICKAXE;
            if (i instanceof ItemAxe) return AXE;
            if (i instanceof ItemSpade) return SHOVEL;
            if (i instanceof ItemShears) return SHEARS;
            return null;
        }

        public static Tool parse(String s) {
            if (s == null) return null;
            String t = s.trim().toLowerCase();
            for (Tool tool : values()) {
                if (tool.id.equals(t) || tool.ru.equals(t) || tool.name().equalsIgnoreCase(t)) return tool;
            }
            if (t.equals("pick") || t.equals("кирку")) return PICKAXE;
            if (t.equals("spade") || t.equals("лопату")) return SHOVEL;
            return null;
        }
    }

    private static final Map<Tool, String[]> DEFAULTS = new EnumMap<Tool, String[]>(Tool.class);

    static {
        DEFAULTS.put(Tool.SWORD, new String[]{"web", "leaves", "leaves2"});
        DEFAULTS.put(Tool.PICKAXE, new String[]{
                "stone", "cobblestone", "mossy_cobblestone", "sandstone", "red_sandstone", "end_stone",
                "stonebrick", "netherrack", "brick_block", "nether_brick", "hardened_clay", "stained_hardened_clay",
                "quartz_block", "coal_ore", "iron_ore", "gold_ore", "diamond_ore", "emerald_ore", "lapis_ore",
                "redstone_ore", "lit_redstone_ore", "quartz_ore"});
        DEFAULTS.put(Tool.AXE, new String[]{"planks", "log", "log2", "bookshelf"});
        DEFAULTS.put(Tool.SHOVEL, new String[]{"dirt", "grass", "sand", "gravel", "clay", "snow", "soul_sand", "mycelium"});
        DEFAULTS.put(Tool.SHEARS, new String[]{"wool", "web", "leaves", "leaves2"});
    }

    private final Map<Tool, Set<String>> rules = new EnumMap<Tool, Set<String>>(Tool.class);

    public ToolRules() {
        for (Tool t : Tool.values()) reset(t);
    }

    public void reset(Tool t) {
        Set<String> set = new LinkedHashSet<String>();
        for (String n : DEFAULTS.get(t)) set.add(normalize(n));
        rules.put(t, set);
    }

    public Set<String> get(Tool t) {
        return rules.get(t);
    }

    /** Можно ли ускорять ломание этого блока этим предметом. */
    public boolean allows(ItemStack held, Block block) {
        Tool t = Tool.of(held);
        if (t == null || block == null) return false;
        String name = nameOf(block);
        if (name == null || !rules.get(t).contains(name)) return false;
        return held.getStrVsBlock(block) > 1.0F;
    }

    /** @return нормализованное имя блока или null, если такого блока нет. */
    public String add(Tool t, String blockName) {
        Block b = Block.getBlockFromName(blockName.trim());
        if (b == null) return null;
        String n = nameOf(b);
        if (n != null) rules.get(t).add(n);
        return n;
    }

    public String addBlock(Tool t, Block b) {
        String n = nameOf(b);
        if (n != null) rules.get(t).add(n);
        return n;
    }

    public boolean remove(Tool t, String blockName) {
        Block b = Block.getBlockFromName(blockName.trim());
        String n = b != null ? nameOf(b) : normalize(blockName);
        return rules.get(t).remove(n);
    }

    public static String nameOf(Block b) {
        Object rl = Block.blockRegistry.getNameForObject(b);
        return rl == null ? null : rl.toString();
    }

    public static String shortName(String full) {
        return full.startsWith("minecraft:") ? full.substring("minecraft:".length()) : full;
    }

    private static String normalize(String n) {
        String t = n.trim().toLowerCase();
        return new ResourceLocation(t).toString();
    }

    public void load(Properties p) {
        for (Tool t : Tool.values()) {
            String v = p.getProperty("tools." + t.id);
            if (v == null) continue;
            Set<String> set = new LinkedHashSet<String>();
            for (String n : Arrays.asList(v.split(","))) {
                if (!n.trim().isEmpty()) set.add(normalize(n));
            }
            rules.put(t, set);
        }
    }

    public void save(Properties p) {
        for (Tool t : Tool.values()) {
            StringBuilder sb = new StringBuilder();
            for (String n : rules.get(t)) {
                if (sb.length() > 0) sb.append(',');
                sb.append(shortName(n));
            }
            p.setProperty("tools." + t.id, sb.toString());
        }
    }
}
