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
        
        // Add tier and quality information
        lore.add(Component.text(String.format("%s (%.1f%%)", tier.getName(), quality * 100)));
        
        // Get attribute display settings from config
        ConfigurationSection displaySection = TierTools.getInstance().getConfig()
            .getConfigurationSection("attributes.display");
        if (displaySection == null) {
            TierTools.getInstance().getLogger().warning("No display settings found in config!");
            return;
        }

        String color = displaySection.getString("color", "&a");
        String format = displaySection.getString("format", "+%.1f %s");
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
        
        for (NamespacedKey key : container.getKeys()) {
            TierTools.getInstance().getLogger().info("Found key: " + key.getNamespace() + ":" + key.getKey());
            if (key.getNamespace().equals(ATTRIBUTE_PREFIX.getNamespace()) && 
                key.getKey().startsWith(ATTRIBUTE_PREFIX.getKey())) {
                
                Double value = container.get(key, PersistentDataType.DOUBLE);
                if (value != null && (showZero || value != 0)) {
                    String attrKey = key.getKey().substring(ATTRIBUTE_PREFIX.getKey().length());
                    String displayName = namesSection.getString(attrKey, TierUtils.formatAttributeName(attrKey));
                    attributes.add(new AttributeEntry(attrKey, displayName, value));
                    TierTools.getInstance().getLogger().info("Found attribute: " + attrKey + " = " + value);
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
            String formattedText = String.format(format, entry.value(), entry.displayName());
            lore.add(MINI_MESSAGE.deserialize(color + formattedText));
            TierTools.getInstance().getLogger().info("Added to lore: " + formattedText);
        }
        
        meta.lore(lore);
        TierTools.getInstance().getLogger().info("Applied lore with " + attributes.size() + " attributes");
    }

    private record AttributeEntry(String key, String displayName, double value) {}
} 