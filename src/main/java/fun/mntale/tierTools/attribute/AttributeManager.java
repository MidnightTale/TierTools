package fun.mntale.tierTools.attribute;

import fun.mntale.tierTools.TierTools;
import fun.mntale.tierTools.tier.TierData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class AttributeManager {
    private final TierTools plugin;
    private final Set<Material> tierableItems;
    private final Map<String, List<ItemAttribute>> tierAttributes;
    private final Map<Material, List<ItemAttribute>> itemAttributes;
    private final NamespacedKey ATTRIBUTE_PREFIX;
    private final Random random;

    public AttributeManager(TierTools plugin) {
        this.plugin = plugin;
        this.tierableItems = new HashSet<>();
        this.tierAttributes = new HashMap<>();
        this.itemAttributes = new HashMap<>();
        this.ATTRIBUTE_PREFIX = new NamespacedKey(plugin, "attribute_");
        this.random = new Random();
        loadConfig();
    }

    public void reloadConfig() {
        // Clear existing data
        tierableItems.clear();
        tierAttributes.clear();
        itemAttributes.clear();
        
        // Reload configuration
        loadConfig();
        
        plugin.getLogger().info("AttributeManager configuration reloaded");
    }

    private void loadConfig() {
        // Load tierable items
        ConfigurationSection tierableSection = plugin.getConfig().getConfigurationSection("tierable-items");
        if (tierableSection != null) {
            for (String category : tierableSection.getKeys(false)) {
                List<String> items = tierableSection.getStringList(category);
                for (String item : items) {
                    try {
                        tierableItems.add(Material.valueOf(item.toUpperCase()));
                        plugin.getLogger().info("Added tierable item: " + item);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid material: " + item);
                    }
                }
            }
        }
        plugin.getLogger().info("Loaded " + tierableItems.size() + " tierable items");

        // Load tier attributes
        ConfigurationSection tiersSection = plugin.getConfig().getConfigurationSection("attributes.tiers");
        if (tiersSection != null) {
            for (String tier : tiersSection.getKeys(false)) {
                List<ItemAttribute> attributes = new ArrayList<>();
                ConfigurationSection tierSection = tiersSection.getConfigurationSection(tier);
                if (tierSection != null) {
                    // Get tier multiplier from tier config
                    ConfigurationSection tierConfig = plugin.getConfig().getConfigurationSection("tiers." + tier);
                    double tierMultiplier = tierConfig != null ? tierConfig.getDouble("multiple", 1.0) : 1.0;
                    plugin.getLogger().info("Loaded multiplier for tier " + tier + ": " + tierMultiplier);
                    
                    for (String attrKey : tierSection.getKeys(false)) {
                        ConfigurationSection attrSection = tierSection.getConfigurationSection(attrKey);
                        if (attrSection != null) {
                            String type = attrSection.getString("type");
                            double minValue = attrSection.getDouble("min_value");
                            double maxValue = attrSection.getDouble("max_value");
                            if (type != null) {
                                Attribute attribute = getAttributeByKey(type);
                                if (attribute != null) {
                                    // Apply tier multiplier to min and max values
                                    attributes.add(new ItemAttribute(attribute, minValue * tierMultiplier, maxValue * tierMultiplier, attrKey));
                                    plugin.getLogger().info("Loaded attribute for tier " + tier + ": " + type + " = " + (minValue * tierMultiplier) + "-" + (maxValue * tierMultiplier));
                                } else {
                                    plugin.getLogger().warning("Invalid attribute type in config: " + type);
                                }
                            }
                        }
                    }
                }
                tierAttributes.put(tier, attributes);
                plugin.getLogger().info("Loaded " + attributes.size() + " attributes for tier " + tier);
            }
        } else {
            plugin.getLogger().warning("No tier attributes found in config!");
        }

        // Load item-specific attributes
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("attributes.items");
        if (itemsSection != null) {
            for (String itemKey : itemsSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(itemKey.toUpperCase());
                    List<ItemAttribute> attributes = new ArrayList<>();
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                    if (itemSection != null) {
                        for (String attrKey : itemSection.getKeys(false)) {
                            ConfigurationSection attrSection = itemSection.getConfigurationSection(attrKey);
                            if (attrSection != null) {
                                String type = attrSection.getString("type");
                                double minValue = attrSection.getDouble("min_value");
                                double maxValue = attrSection.getDouble("max_value");
                                if (type != null) {
                                    Attribute attribute = getAttributeByKey(type);
                                    if (attribute != null) {
                                        attributes.add(new ItemAttribute(attribute, minValue, maxValue, attrKey));
                                        plugin.getLogger().info("Loaded attribute for item " + itemKey + ": " + type + " = " + minValue + "-" + maxValue);
                                    } else {
                                        plugin.getLogger().warning("Invalid attribute type in config: " + type);
                                    }
                                }
                            }
                        }
                    }
                    itemAttributes.put(material, attributes);
                    plugin.getLogger().info("Loaded " + attributes.size() + " attributes for item " + itemKey);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in item attributes: " + itemKey);
                }
            }
        } else {
            plugin.getLogger().warning("No item-specific attributes found in config!");
        }
    }

    private Attribute getAttributeByKey(String key) {
        key = key.toLowerCase();
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        return org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.fromString(key));
    }

    private EquipmentSlotGroup getSlotGroupForItem(Material material) {
        String name = material.name().toLowerCase();
        if (name.contains("helmet")) return EquipmentSlotGroup.HEAD;
        if (name.contains("chestplate")) return EquipmentSlotGroup.CHEST;
        if (name.contains("leggings")) return EquipmentSlotGroup.LEGS;
        if (name.contains("boots")) return EquipmentSlotGroup.FEET;
        if (name.contains("sword") || name.contains("axe") || name.contains("pickaxe") || 
            name.contains("shovel") || name.contains("hoe") || name.contains("bow") || 
            name.contains("crossbow") || name.contains("trident")) return EquipmentSlotGroup.HAND;
        return EquipmentSlotGroup.HAND; // Default to hand for other items
    }

    private AttributeModifier.Operation getOperationForAttribute(Attribute attribute) {
        return AttributeModifier.Operation.ADD_NUMBER;
    }

    private double applyQualityEffect(double baseValue, float quality) {
        // Quality affects the value in an exponential way
        // At quality 0.3 or higher: value scales exponentially from 0 to baseValue
        // At quality below 0.3: value can go negative with exponential scaling
        if (quality >= 0.3f) {
            // Exponential scale from 0 to baseValue between 0.3 and 1.0
            // Using power of 2 for exponential growth
            double normalizedQuality = (quality - 0.3f) / 0.7f;
            return baseValue * Math.pow(normalizedQuality, 2);
        } else {
            // Exponential scale from -baseValue to 0 between 0.0 and 0.3
            // Using power of 2 for exponential growth
            double normalizedQuality = (quality - 0.3f) / 0.3f;
            return baseValue * Math.pow(normalizedQuality, 2);
        }
    }

    private List<ItemAttribute> selectRandomAttributes(List<ItemAttribute> attributes, float quality, String tierName) {
        if (attributes == null || attributes.isEmpty()) {
            plugin.getLogger().info("No attributes available to select from");
            return Collections.emptyList();
        }

        // Get tier level from config to determine minimum attributes
        ConfigurationSection tierSection = plugin.getConfig().getConfigurationSection("tiers." + tierName);
        int tierLevel = tierSection != null ? tierSection.getInt("level", 1) : 1;
        plugin.getLogger().info("Tier level for " + tierName + ": " + tierLevel);
        
        // Minimum attributes based on tier level (1 attribute per level, max 5)
        int minAttributes = Math.min(tierLevel, 5);
        
        // Maximum attributes based on quality and tier level
        // Higher quality and tier level means more possible attributes
        int maxAttributes = Math.max(minAttributes, 
            (int) Math.round(attributes.size() * quality * (1 + (tierLevel - 1) * 0.2)));

        plugin.getLogger().info("Attribute selection for " + tierName + ": min=" + minAttributes + ", max=" + maxAttributes + ", available=" + attributes.size());

        // Always select at least minAttributes, but no more than maxAttributes
        int numAttributes = Math.min(maxAttributes, 
            Math.max(minAttributes, random.nextInt(maxAttributes - minAttributes + 1) + minAttributes));

        plugin.getLogger().info("Selected " + numAttributes + " attributes out of " + attributes.size() + " available");

        // Shuffle and select random attributes
        List<ItemAttribute> shuffled = new ArrayList<>(attributes);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, Math.min(numAttributes, shuffled.size()));
    }

    public void applyAttributes(ItemStack item, String tierName, float quality) {
        // Step 1: Check if item is tierable
        if (item == null || item.getItemMeta() == null || !tierableItems.contains(item.getType())) {
            return;
        }

        // Check if item already has a tier
        TierData tierData = new TierData(plugin.getTierKey());
        if (tierData.getTierName(item) != null) {
            return; // Skip if item already has a tier
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Map to store combined attributes and their counts for averaging
        Map<Attribute, Double> combinedAttributes = new HashMap<>();
        Map<Attribute, Integer> attributeCounts = new HashMap<>();

        // Step 2: Apply tier-based attributes
        List<ItemAttribute> tierAttrs = tierAttributes.get(tierName);
        if (tierAttrs != null) {
            plugin.getLogger().info("Applying tier-based attributes for " + item.getType() + " with tier " + tierName);
            List<ItemAttribute> selectedAttrs = selectRandomAttributes(tierAttrs, quality, tierName);
            for (ItemAttribute attr : selectedAttrs) {
                double baseValue = attr.getRandomValue();
                double value = applyQualityEffect(baseValue, quality);
                combinedAttributes.merge(attr.getAttribute(), value, Double::sum);
                attributeCounts.merge(attr.getAttribute(), 1, Integer::sum);
                plugin.getLogger().info("  - Tier attribute: " + attr.getAttribute().getKey().getKey() + ": " + value);
            }
        }

        // Step 3: Apply item-specific attributes
        List<ItemAttribute> itemAttrs = itemAttributes.get(item.getType());
        if (itemAttrs != null) {
            plugin.getLogger().info("Applying item-specific attributes for " + item.getType());
            List<ItemAttribute> selectedAttrs = selectRandomAttributes(itemAttrs, quality, tierName);
            for (ItemAttribute attr : selectedAttrs) {
                double baseValue = attr.getRandomValue();
                double value = applyQualityEffect(baseValue, quality);
                combinedAttributes.merge(attr.getAttribute(), value, Double::sum);
                attributeCounts.merge(attr.getAttribute(), 1, Integer::sum);
                plugin.getLogger().info("  - Item attribute: " + attr.getAttribute().getKey().getKey() + ": " + value);
            }
        }

        // Calculate averages for attributes that appear in both tier and item
        for (Map.Entry<Attribute, Double> entry : combinedAttributes.entrySet()) {
            Attribute attribute = entry.getKey();
            double totalValue = entry.getValue();
            int count = attributeCounts.get(attribute);
            double averageValue = totalValue / count;
            combinedAttributes.put(attribute, averageValue);
            plugin.getLogger().info("Final average for " + attribute.getKey().getKey() + ": " + averageValue + " (from " + count + " sources)");
        }

        // Apply combined attributes
        for (Map.Entry<Attribute, Double> entry : combinedAttributes.entrySet()) {
            Attribute attribute = entry.getKey();
            double value = entry.getValue();

            // Store in PersistentDataContainer
            String attrKey = attribute.getKey().getKey();
            NamespacedKey key = new NamespacedKey(ATTRIBUTE_PREFIX.getNamespace(), 
                "attribute_" + attrKey);
            container.set(key, PersistentDataType.DOUBLE, value);
            plugin.getLogger().info("Storing attribute in PDC: " + key.getNamespace() + ":" + key.getKey() + " = " + value);

            // Determine slot group and operation based on item type and attribute
            EquipmentSlotGroup slotGroup = getSlotGroupForItem(item.getType());
            AttributeModifier.Operation operation = getOperationForAttribute(attribute);

            // Apply attribute modifier
            NamespacedKey modifierKey = new NamespacedKey(ATTRIBUTE_PREFIX.getNamespace(), 
                "attribute_" + attrKey);
            AttributeModifier modifier = new AttributeModifier(
                modifierKey,
                value,
                operation,
                slotGroup
            );
            meta.addAttributeModifier(attribute, modifier);
            plugin.getLogger().info("Added attribute modifier: " + attribute.getKey().getKey() + " = " + value);
        }

        item.setItemMeta(meta);
        plugin.getLogger().info("Applied " + combinedAttributes.size() + " attributes to " + item.getType());
    }

    public boolean hasAttributes(ItemStack item) {
        if (item == null || !tierableItems.contains(item.getType())) {
            return false;
        }

        TierData tierData = new TierData(plugin.getTierKey());
        String tierName = tierData.getTierName(item);
        if (tierName == null) {
            return false;
        }

        return (tierAttributes.containsKey(tierName) && !tierAttributes.get(tierName).isEmpty()) ||
               (itemAttributes.containsKey(item.getType()) && !itemAttributes.get(item.getType()).isEmpty());
    }
} 