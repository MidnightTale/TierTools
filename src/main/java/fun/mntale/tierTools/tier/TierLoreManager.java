package fun.mntale.tierTools.tier;

import fun.mntale.tierTools.TierTools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class TierLoreManager {
    private static final NamespacedKey ATTRIBUTE_PREFIX = new NamespacedKey("tiertools", "attribute_");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static void applyTierLore(ItemMeta meta, Tier tier, float quality) {
        List<Component> lore = new ArrayList<>();
        
        // Get lore format settings from config
        ConfigurationSection loreSection = TierTools.getInstance().getConfig()
            .getConfigurationSection("lore");
        if (loreSection == null) {
            TierTools.getInstance().getLogger().warning("No lore settings found in config!");
            return;
        }

        // Add tier and quality information
        String tierFormat = loreSection.getString("tier-format", "<color:#FFD700><tier> (<quality>%)</color>");
        String tierText = tierFormat
            .replace("<tier>", tier.getName())
            .replace("<quality>", String.format("%.2f", quality * 100));
        lore.add(MINI_MESSAGE.deserialize(tierText));
        
        // Get attribute display settings from config
        ConfigurationSection displaySection = TierTools.getInstance().getConfig()
            .getConfigurationSection("attributes.display");
        if (displaySection == null) {
            TierTools.getInstance().getLogger().warning("No display settings found in config!");
            return;
        }

        String positiveColor = displaySection.getString("positive-color", "<color:#00FF00>");
        String negativeColor = displaySection.getString("negative-color", "<color:#FF0000>");
        String format = displaySection.getString("format", "<sign><value> <name>");
        String sortBy = displaySection.getString("sort-by", "value");
        boolean showZero = displaySection.getBoolean("show-zero", false);

        // Get attribute name mappings
        ConfigurationSection namesSection = TierTools.getInstance().getConfig()
            .getConfigurationSection("attributes.names");
        if (namesSection == null) {
            TierTools.getInstance().getLogger().warning("No attribute names found in config!");
            return;
        }

        // Collect and sort attributes
        List<AttributeEntry> attributes = new ArrayList<>();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        TierTools.getInstance().getLogger().info("Reading attributes from " + meta.displayName());
        TierTools.getInstance().getLogger().info("Container has " + container.getKeys().size() + " keys");
        TierTools.getInstance().getLogger().info("Looking for attributes with prefix: " + ATTRIBUTE_PREFIX.getNamespace() + ":attribute_");
        
        for (NamespacedKey key : container.getKeys()) {
            TierTools.getInstance().getLogger().info("Found key: " + key.getNamespace() + ":" + key.getKey());
            // Check if the key is in our namespace and starts with "attribute_"
            if (key.getNamespace().equals(ATTRIBUTE_PREFIX.getNamespace()) && 
                key.getKey().startsWith("attribute_")) {
                
                TierTools.getInstance().getLogger().info("Found matching attribute key: " + key.getKey());
                Double value = container.get(key, PersistentDataType.DOUBLE);
                if (value != null && (showZero || value != 0)) {
                    String attrKey = key.getKey().substring("attribute_".length());
                    String displayName = namesSection.getString(attrKey, TierUtils.formatAttributeName(attrKey));
                    attributes.add(new AttributeEntry(attrKey, displayName, value));
                    TierTools.getInstance().getLogger().info("Found attribute: " + attrKey + " = " + value);
                } else {
                    TierTools.getInstance().getLogger().info("Attribute value is null or zero: " + value);
                }
            }
        }

        // Sort attributes
        if ("value".equals(sortBy)) {
            attributes.sort(Comparator.comparingDouble(AttributeEntry::value).reversed());
        } else {
            attributes.sort(Comparator.comparing(AttributeEntry::displayName));
        }

        // Add attributes to lore
        for (AttributeEntry entry : attributes) {
            String sign = entry.value() >= 0 ? "+" : "";
            String color = entry.value() >= 0 ? positiveColor : negativeColor;
            String formattedText = format
                .replace("<sign>", sign)
                .replace("<value>", String.format("%.2f", Math.abs(entry.value())))
                .replace("<name>", entry.displayName());
            lore.add(MINI_MESSAGE.deserialize(color + formattedText));
            TierTools.getInstance().getLogger().info("Added to lore: " + formattedText);
        }
        
        meta.lore(lore);
        TierTools.getInstance().getLogger().info("Applied lore with " + attributes.size() + " attributes");
    }

    private record AttributeEntry(String key, String displayName, double value) {}
} 