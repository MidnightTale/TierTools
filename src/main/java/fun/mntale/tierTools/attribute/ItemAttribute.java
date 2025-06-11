package fun.mntale.tierTools.attribute;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Registry;
import java.util.Random;

public class ItemAttribute {
    private final Attribute attribute;
    private final double minValue;
    private final double maxValue;
    private final String key;
    private static final Random random = new Random();

    public ItemAttribute(Attribute attribute, double minValue, double maxValue, String key) {
        this.attribute = attribute;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.key = key;
    }

    public void applyAttribute(ItemStack item, float quality) {
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        double scaledValue = getRandomValue() * quality;
        
        AttributeModifier modifier = new AttributeModifier(
            new NamespacedKey("tiertools", "attribute_" + attribute.getKey().getKey()),
            scaledValue,
            AttributeModifier.Operation.ADD_NUMBER
        );
        
        meta.addAttributeModifier(attribute, modifier);
        
        item.setItemMeta(meta);
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public String getKey() {
        return key;
    }

    public double getRandomValue() {
        return minValue + (random.nextDouble() * (maxValue - minValue));
    }

    /**
     * Get an attribute by its key name.
     * @param key The key name of the attribute (e.g., "attack_damage")
     * @return The attribute, or null if not found
     */
    public static Attribute getAttributeByKey(String key) {
        try {
            // Convert to lowercase and add minecraft: namespace if needed
            if (!key.contains(".")) {
                key = key.toLowerCase();
            }
            if (!key.contains(":")) {
                key = "minecraft:" + key;
            }
            return Registry.ATTRIBUTE.get(NamespacedKey.fromString(key));
        } catch (Exception e) {
            return null;
        }
    }
} 