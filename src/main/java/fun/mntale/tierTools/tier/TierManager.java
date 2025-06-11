package fun.mntale.tierTools.tier;

import fun.mntale.tierTools.TierTools;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TierManager {
    private final TierTools plugin;
    private final Map<String, Tier> tiers;
    private final Set<Material> tierableItems;

    public TierManager(TierTools plugin) {
        this.plugin = plugin;
        this.tiers = new HashMap<>();
        this.tierableItems = new HashSet<>();
        loadTiers();
        loadTierableItems();
    }

    private void loadTiers() {
        ConfigurationSection tiersSection = plugin.getConfig().getConfigurationSection("tiers");
        if (tiersSection == null) {
            plugin.getLogger().warning("No tiers found in config!");
            return;
        }

        for (String tierKey : tiersSection.getKeys(false)) {
            ConfigurationSection tierConfig = tiersSection.getConfigurationSection(tierKey);
            if (tierConfig != null) {
                tiers.put(tierKey, new Tier(tierConfig));
                plugin.getLogger().info("Loaded tier: " + tierKey);
            }
        }
    }

    private void loadTierableItems() {
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("tierable-items");
        if (itemsSection == null) {
            plugin.getLogger().warning("No tierable items found in config!");
            return;
        }

        for (String category : itemsSection.getKeys(false)) {
            List<String> items = itemsSection.getStringList(category);
            for (String itemName : items) {
                try {
                    Material material = Material.valueOf(itemName);
                    tierableItems.add(material);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material name in config: " + itemName);
                }
            }
        }
    }

    public ItemStack applyTierToItem(ItemStack item) {
        if (item == null || !tierableItems.contains(item.getType())) {
            return item;
        }

        TierData tierData = new TierData(plugin.getTierKey());
        if (tierData.hasTier(item)) {
            return item;
        }

        Tier tier = getRandomTier();
        if (tier != null) {
            tierData.applyTierData(item, tier);
        }
        return item;
    }

    private Tier getRandomTier() {
        if (tiers.isEmpty()) {
            return null;
        }

        float totalChance = (float) tiers.values().stream()
                .mapToDouble(Tier::getChance)
                .sum();

        float random = new Random().nextFloat() * totalChance;
        float currentSum = 0;

        for (Tier tier : tiers.values()) {
            currentSum += tier.getChance();
            if (random < currentSum) {
                return tier;
            }
        }

        return tiers.values().iterator().next();
    }

    public Set<Material> getTierableItems() {
        return Collections.unmodifiableSet(tierableItems);
    }
} 