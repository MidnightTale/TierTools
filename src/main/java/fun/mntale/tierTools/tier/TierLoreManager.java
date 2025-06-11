package fun.mntale.tierTools.tier;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class TierLoreManager {
    public static void applyTierLore(ItemMeta meta, Tier tier, float quality) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format("%s (%.1f%%)", tier.getName(), quality)));
        meta.lore(lore);
    }
} 