package fun.mntale.tierTools.tier;

import fun.mntale.tierTools.TierTools;
import fun.mntale.tierTools.attribute.AttributeManager;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.ConfigurationSection;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TierData {
    private final NamespacedKey tierKey;
    private final NamespacedKey qualityKey;
    private static final String TIER_PREFIX = "tier_";
    private final AttributeManager attributeManager;
    private final String name;
    private final int level;
    private final float chance;
    private final String color;
    private final float minQuality;
    private final float maxQuality;
    private final List<String> permissions;
    private static final Random random = new Random();

    public TierData(NamespacedKey tierKey) {
        this.tierKey = tierKey;
        this.qualityKey = new NamespacedKey(tierKey.getNamespace(), "quality");
        this.attributeManager = new AttributeManager(TierTools.getInstance());
        ConfigurationSection section = TierTools.getInstance().getConfig().getConfigurationSection("tiers." + tierKey.getKey());
        if (section != null) {
            this.name = section.getString("name", "Unknown");
            this.level = section.getInt("level", 1);
            this.chance = (float) section.getDouble("chance", 0.0);
            this.color = section.getString("color", "&7");
            this.minQuality = (float) section.getDouble("min-quality", 0.0);
            this.maxQuality = (float) section.getDouble("max-quality", 1.0);
            this.permissions = section.getStringList("permissions");
        } else {
            this.name = "Unknown";
            this.level = 1;
            this.chance = 0.0f;
            this.color = "&7";
            this.minQuality = 0.0f;
            this.maxQuality = 1.0f;
            this.permissions = new ArrayList<>();
        }
    }

    public void applyTierData(ItemStack item, Tier tier) {
        if (item == null || tier == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Store tier and quality first
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(tierKey, PersistentDataType.STRING, TIER_PREFIX + tier.getName().toLowerCase());
        
        // Generate and store quality
        float quality = tier.generateQuality();
        container.set(qualityKey, PersistentDataType.FLOAT, quality);

        // Set display name with tier color
        String itemName = item.getType().name().toLowerCase().replace("_", " ");
        String displayName = tier.getColor() + itemName;
        meta.displayName(MiniMessage.miniMessage().deserialize(displayName));

        // Apply attributes based on tier and quality
        attributeManager.applyAttributes(item, tier.getName().toLowerCase(), quality);

        // Get the updated meta after attributes are applied
        meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Copy the PersistentDataContainer data to the new meta
        PersistentDataContainer newContainer = meta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            if (container.has(key, PersistentDataType.DOUBLE)) {
                newContainer.set(key, PersistentDataType.DOUBLE, container.get(key, PersistentDataType.DOUBLE));
            } else if (container.has(key, PersistentDataType.FLOAT)) {
                newContainer.set(key, PersistentDataType.FLOAT, container.get(key, PersistentDataType.FLOAT));
            } else if (container.has(key, PersistentDataType.STRING)) {
                newContainer.set(key, PersistentDataType.STRING, container.get(key, PersistentDataType.STRING));
            }
        }

        // Apply lore using TierLoreManager after attributes are applied
        TierLoreManager.applyTierLore(meta, tier, quality);

        // Set the final meta with both attributes and lore
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

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public float getChance() {
        return chance;
    }

    public String getColor() {
        return color;
    }

    public float getMinQuality() {
        return minQuality;
    }

    public float getMaxQuality() {
        return maxQuality;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public float generateQuality() {
        return minQuality + (random.nextFloat() * (maxQuality - minQuality));
    }

    public static TierData getRandomTier() {
        List<TierData> tiers = new ArrayList<>();
        float totalChance = 0;

        // First pass: collect all tiers and calculate total chance
        for (String tierKey : TierTools.getInstance().getConfig().getConfigurationSection("tiers").getKeys(false)) {
            ConfigurationSection tierSection = TierTools.getInstance().getConfig().getConfigurationSection("tiers." + tierKey);
            if (tierSection != null) {
                TierData tier = new TierData(new NamespacedKey(TierTools.getInstance().getName(), tierKey));
                tiers.add(tier);
                totalChance += tier.getChance();
            }
        }

        // Second pass: select random tier based on chances
        float randomValue = random.nextFloat() * totalChance;
        float currentSum = 0;

        for (TierData tier : tiers) {
            currentSum += tier.getChance();
            if (randomValue <= currentSum) {
                return tier;
            }
        }

        // Fallback to first tier if something goes wrong
        return tiers.get(0);
    }
} 