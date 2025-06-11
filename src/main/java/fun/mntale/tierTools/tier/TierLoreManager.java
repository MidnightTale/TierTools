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

        // Get quality color settings
        ConfigurationSection qualityColors = loreSection.getConfigurationSection("quality-colors");
        if (qualityColors == null) {
            TierTools.getInstance().getLogger().warning("No quality color settings found in config!");
            return;
        }

        // Get color thresholds and their corresponding colors
        TreeMap<Float, String> colorThresholds = new TreeMap<>();
        for (String threshold : qualityColors.getKeys(false)) {
            try {
                float value = Float.parseFloat(threshold);
                String color = qualityColors.getString(threshold);
                if (color != null && color.startsWith("<color:#")) {
                    colorThresholds.put(value, color);
                    TierTools.getInstance().getLogger().info("Loaded quality color threshold: " + value + " -> " + color);
                } else {
                    TierTools.getInstance().getLogger().warning("Invalid color format for threshold " + threshold + ": " + color);
                }
            } catch (NumberFormatException e) {
                TierTools.getInstance().getLogger().warning("Invalid quality threshold: " + threshold);
            }
        }

        if (colorThresholds.isEmpty()) {
            TierTools.getInstance().getLogger().warning("No valid quality color thresholds found!");
            return;
        }

        // Calculate interpolated color based on quality
        String qualityColor = calculateQualityColor(quality, colorThresholds);
        TierTools.getInstance().getLogger().info("Quality: " + quality + ", Color: " + qualityColor);

        // Add tier and quality information
        String tierFormat = loreSection.getString("tier-format", "<tier> (<quality>%)");
        String qualityText = String.format("%.2f", quality * 100);
        String tierText = tierFormat
            .replace("<tier>", tier.getName())
            .replace("<quality>", qualityColor + qualityText + "</color>");
        
        // Use tier color directly from config (should be in MiniMessage format)
        String tierColor = tier.getColor();
        lore.add(MINI_MESSAGE.deserialize(tierColor + tierText));
        
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
        double minDisplayValue = displaySection.getDouble("min-display-value", 0.01);

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
        
        for (NamespacedKey key : container.getKeys()) {
            if (key.getNamespace().equals(ATTRIBUTE_PREFIX.getNamespace()) && 
                key.getKey().startsWith("attribute_")) {
                
                Double value = container.get(key, PersistentDataType.DOUBLE);
                if (value != null && (showZero || value != 0) && Math.abs(value) >= minDisplayValue) {
                    String attrKey = key.getKey().substring("attribute_".length());
                    String displayName = namesSection.getString(attrKey, TierUtils.formatAttributeName(attrKey));
                    attributes.add(new AttributeEntry(attrKey, displayName, value));
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
        }
        
        meta.lore(lore);
    }

    private static String calculateQualityColor(float quality, TreeMap<Float, String> colorThresholds) {
        if (colorThresholds.isEmpty()) {
            TierTools.getInstance().getLogger().warning("No color thresholds available for quality: " + quality);
            return "<color:#FFFFFF>"; // Default white if no colors configured
        }

        // Find the closest threshold
        Float closestThreshold = null;
        String closestColor = null;
        float minDifference = Float.MAX_VALUE;

        for (Map.Entry<Float, String> entry : colorThresholds.entrySet()) {
            float difference = Math.abs(entry.getKey() - quality);
            if (difference < minDifference) {
                minDifference = difference;
                closestThreshold = entry.getKey();
                closestColor = entry.getValue();
            }
        }

        if (closestColor != null) {
            TierTools.getInstance().getLogger().info("Using color for quality " + quality + ": " + closestColor + " (threshold: " + closestThreshold + ")");
            return closestColor;
        }

        // Fallback to white if something goes wrong
        TierTools.getInstance().getLogger().warning("No color found for quality: " + quality);
        return "<color:#FFFFFF>";
    }

    private record AttributeEntry(String key, String displayName, double value) {}
} 