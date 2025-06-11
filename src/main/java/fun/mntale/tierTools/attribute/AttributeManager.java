package fun.mntale.tierTools.attribute;

import fun.mntale.tierTools.TierTools;
import fun.mntale.tierTools.tier.TierData;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;

public class AttributeManager {
    private final TierTools plugin;
    private final Map<String, List<ItemAttribute>> tierAttributes;
    private final Map<Material, List<ItemAttribute>> itemAttributes;
    private final Set<Material> tierableItems;
    private static final NamespacedKey ATTRIBUTE_PREFIX = new NamespacedKey("tiertools", "attribute_");

    public AttributeManager(TierTools plugin) {
        this.plugin = plugin;
        this.tierAttributes = new HashMap<>();
        this.itemAttributes = new HashMap<>();
        this.tierableItems = plugin.getTierManager().getTierableItems();
        loadAttributes();
    }

    private void loadAttributes() {
        ConfigurationSection attributesSection = plugin.getConfig().getConfigurationSection("attributes");
        if (attributesSection == null) {
            plugin.getLogger().warning("No attributes found in config!");
            return;
        }

        // Load tier-based attributes
        ConfigurationSection tierSection = attributesSection.getConfigurationSection("tiers");
        if (tierSection != null) {
            for (String tierKey : tierSection.getKeys(false)) {
                List<ItemAttribute> attributes = loadAttributeList(tierSection.getConfigurationSection(tierKey));
                if (!attributes.isEmpty()) {
                    tierAttributes.put(tierKey, attributes);
                }
            }
        }

        // Load item-specific attributes
        ConfigurationSection itemsSection = attributesSection.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemKey : itemsSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(itemKey);
                    // Only load attributes for tierable items
                    if (tierableItems.contains(material)) {
                        List<ItemAttribute> attributes = loadAttributeList(itemsSection.getConfigurationSection(itemKey));
                        if (!attributes.isEmpty()) {
                            itemAttributes.put(material, attributes);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material name in attributes config: " + itemKey);
                }
            }
        }
    }

    private List<ItemAttribute> loadAttributeList(ConfigurationSection section) {
        List<ItemAttribute> attributes = new ArrayList<>();
        if (section == null) return attributes;

        for (String attrKey : section.getKeys(false)) {
            ConfigurationSection attrSection = section.getConfigurationSection(attrKey);
            if (attrSection == null) continue;

            try {
                String attributeKey = attrSection.getString("type");
                if (attributeKey == null) {
                    plugin.getLogger().warning("Missing attribute type in config: " + attrKey);
                    continue;
                }

                // Convert to lowercase with underscores if needed
                if (!attributeKey.contains(".")) {
                    attributeKey = attributeKey.toLowerCase();
                }

                Attribute attribute = ItemAttribute.getAttributeByKey(attributeKey);
                if (attribute == null) {
                    plugin.getLogger().warning("Invalid attribute type in config: " + attributeKey);
                    continue;
                }

                double value = attrSection.getDouble("value");
                String name = attrSection.getString("name", attribute.getKey().getKey());

                attributes.add(new ItemAttribute(attribute, value, name));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid attribute configuration: " + attrKey);
            }
        }

        return attributes;
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
        // For most attributes, we want to add to the base value
        return AttributeModifier.Operation.ADD_NUMBER;
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

        // Map to store combined attributes
        Map<Attribute, Double> combinedAttributes = new HashMap<>();

        // Step 2: Apply tier-based attributes
        List<ItemAttribute> tierAttrs = tierAttributes.get(tierName);
        if (tierAttrs != null) {
            plugin.getLogger().info("Applying tier-based attributes for " + item.getType() + " with tier " + tierName);
            for (ItemAttribute attr : tierAttrs) {
                double value = attr.getValue() * quality;
                combinedAttributes.put(attr.getAttribute(), value);
                plugin.getLogger().info("  - " + attr.getAttribute().getKey().getKey() + ": " + value);
            }
        }

        // Step 3: Apply item-specific attributes
        List<ItemAttribute> itemAttrs = itemAttributes.get(item.getType());
        if (itemAttrs != null) {
            plugin.getLogger().info("Applying item-specific attributes for " + item.getType());
            for (ItemAttribute attr : itemAttrs) {
                double value = attr.getValue() * quality;
                // If attribute already exists, take the higher value
                combinedAttributes.merge(attr.getAttribute(), value, Math::max);
                plugin.getLogger().info("  - " + attr.getAttribute().getKey().getKey() + ": " + value);
            }
        }

        // Apply combined attributes
        for (Map.Entry<Attribute, Double> entry : combinedAttributes.entrySet()) {
            Attribute attribute = entry.getKey();
            double value = entry.getValue();

            // Store in PersistentDataContainer
            String attrKey = attribute.getKey().getKey();
            NamespacedKey key = new NamespacedKey(ATTRIBUTE_PREFIX.getNamespace(), 
                ATTRIBUTE_PREFIX.getKey() + attrKey);
            container.set(key, PersistentDataType.DOUBLE, value);
            plugin.getLogger().info("Stored attribute in PDC: " + attrKey + " = " + value);

            // Determine slot group and operation based on item type and attribute
            EquipmentSlotGroup slotGroup = getSlotGroupForItem(item.getType());
            AttributeModifier.Operation operation = getOperationForAttribute(attribute);

            // Apply attribute modifier
            AttributeModifier modifier = new AttributeModifier(
                key,
                value,
                operation,
                slotGroup
            );

            meta.addAttributeModifier(attribute, modifier);
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