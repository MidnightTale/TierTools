package fun.mntale.tierTools.tier;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class TierData {
    private final NamespacedKey tierKey;
    private final NamespacedKey qualityKey;
    private static final String TIER_PREFIX = "tier_";

    public TierData(NamespacedKey tierKey) {
        this.tierKey = tierKey;
        this.qualityKey = new NamespacedKey(tierKey.getNamespace(), "quality");
    }

    public void applyTierData(ItemStack item, Tier tier) {
        if (item == null || tier == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(tierKey, PersistentDataType.STRING, TIER_PREFIX + tier.getName().toLowerCase());
        
        // Generate and store quality
        float quality = tier.generateQuality();
        container.set(qualityKey, PersistentDataType.FLOAT, quality);

        // Apply lore using TierLoreManager
        TierLoreManager.applyTierLore(meta, tier, quality);

        item.setItemMeta(meta);
    }

    public boolean hasTier(ItemStack item) {
        if (item == null) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(tierKey, PersistentDataType.STRING);
    }

    public String getTierName(ItemStack item) {
        if (!hasTier(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        String tierData = container.get(tierKey, PersistentDataType.STRING);
        if (tierData == null || !tierData.startsWith(TIER_PREFIX)) {
            return null;
        }

        return tierData.substring(TIER_PREFIX.length());
    }

    public float getQuality(ItemStack item) {
        if (!hasTier(item)) {
            return 0.0f;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0.0f;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(qualityKey, PersistentDataType.FLOAT, 0.0f);
    }
} 