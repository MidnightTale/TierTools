package fun.mntale.tierTools;

import com.destroystokyo.paper.loottable.LootableInventory;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.Gson;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;

import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.attribute.Attribute.*;

public class TierTools extends JavaPlugin implements Listener {

    private final List<String> TIERS = Arrays.asList("Common", "Uncommon", "Rare", "Epic", "Legendary");
    private final NamespacedKey tierKey = new NamespacedKey(this, "tool_tier");
    private final NamespacedKey qualityKey = new NamespacedKey(this, "tool_quality");
    private final NamespacedKey baseValueKey = new NamespacedKey(this, "tool_base_value");
    private final NamespacedKey tierMultiplierKey = new NamespacedKey(this, "tool_tier_multiplier");
    private final NamespacedKey qualityMultiplierKey = new NamespacedKey(this, "tool_quality_multiplier");
    private final NamespacedKey attributesKey = new NamespacedKey(this, "tool_attributes");
    private final Random random = new Random();

    private static final List<Attribute> ALL_ATTRIBUTES = List.of(
            ATTACK_DAMAGE, ATTACK_SPEED, ATTACK_KNOCKBACK, SWEEPING_DAMAGE_RATIO,
            ENTITY_INTERACTION_RANGE, BLOCK_INTERACTION_RANGE, MINING_EFFICIENCY,
            BLOCK_BREAK_SPEED, SUBMERGED_MINING_SPEED, MOVEMENT_EFFICIENCY,
            ARMOR, ARMOR_TOUGHNESS, OXYGEN_BONUS, MAX_ABSORPTION,
            KNOCKBACK_RESISTANCE, LUCK, MAX_HEALTH, SPAWN_REINFORCEMENTS,
            SNEAKING_SPEED, SAFE_FALL_DISTANCE, MOVEMENT_SPEED, STEP_HEIGHT,
            FALL_DAMAGE_MULTIPLIER, GRAVITY, JUMP_STRENGTH,
            EXPLOSION_KNOCKBACK_RESISTANCE, BURNING_TIME, SCALE, WATER_MOVEMENT_EFFICIENCY
    );

    // Prettified names lookup map
    private static final Map<Attribute, String> ATTR_NAMES = new HashMap<>();
    static {
        ATTR_NAMES.put(ATTACK_DAMAGE, "Attack Damage");
        ATTR_NAMES.put(ATTACK_SPEED, "Attack Speed");
        ATTR_NAMES.put(ATTACK_KNOCKBACK, "Attack Knockback");
        ATTR_NAMES.put(SWEEPING_DAMAGE_RATIO, "Sweeping Damage Ratio");
        ATTR_NAMES.put(ENTITY_INTERACTION_RANGE, "Entity Interaction Range");
        ATTR_NAMES.put(BLOCK_INTERACTION_RANGE, "Block Interaction Range");
        ATTR_NAMES.put(MINING_EFFICIENCY, "Mining Efficiency");
        ATTR_NAMES.put(BLOCK_BREAK_SPEED, "Block Break Speed");
        ATTR_NAMES.put(SUBMERGED_MINING_SPEED, "Submerged Mining Speed");
        ATTR_NAMES.put(MOVEMENT_EFFICIENCY, "Movement Efficiency");
        ATTR_NAMES.put(ARMOR, "Armor");
        ATTR_NAMES.put(ARMOR_TOUGHNESS, "Armor Toughness");
        ATTR_NAMES.put(OXYGEN_BONUS, "Oxygen Bonus");
        ATTR_NAMES.put(MAX_ABSORPTION, "Max Absorption");
        ATTR_NAMES.put(KNOCKBACK_RESISTANCE, "Knockback Resistance");
        ATTR_NAMES.put(LUCK, "Luck");
        ATTR_NAMES.put(MAX_HEALTH, "Max Health");
        ATTR_NAMES.put(SPAWN_REINFORCEMENTS, "Spawn Reinforcements");
        ATTR_NAMES.put(SNEAKING_SPEED, "Sneaking Speed");
        ATTR_NAMES.put(SAFE_FALL_DISTANCE, "Safe Fall Distance");
        ATTR_NAMES.put(MOVEMENT_SPEED, "Movement Speed");
        ATTR_NAMES.put(STEP_HEIGHT, "Step Height");
        ATTR_NAMES.put(FALL_DAMAGE_MULTIPLIER, "Fall Damage Multiplier");
        ATTR_NAMES.put(GRAVITY, "Gravity");
        ATTR_NAMES.put(JUMP_STRENGTH, "Jump Strength");
        ATTR_NAMES.put(EXPLOSION_KNOCKBACK_RESISTANCE, "Explosion Knockback Resistance");
        ATTR_NAMES.put(BURNING_TIME, "Burning Time");
        ATTR_NAMES.put(SCALE, "Scale");
        ATTR_NAMES.put(WATER_MOVEMENT_EFFICIENCY, "Water Movement Efficiency");
    }

    private String prettifyAttrName(Attribute attr) {
        return ATTR_NAMES.getOrDefault(attr, attr.toString());
    }

    // Define tier-specific attributes and multipliers
    private static final Map<String, TierProperties> TIER_PROPERTIES = new HashMap<>();
    static {
        // Common tier - Basic attributes only
        TIER_PROPERTIES.put("Common", new TierProperties(1.0, Arrays.asList(
            ATTACK_DAMAGE, ATTACK_SPEED, MINING_EFFICIENCY, BLOCK_BREAK_SPEED,
            ARMOR, ARMOR_TOUGHNESS, MOVEMENT_SPEED
        )));

        // Uncommon tier - Adds utility attributes
        TIER_PROPERTIES.put("Uncommon", new TierProperties(1.3, Arrays.asList(
            ATTACK_DAMAGE, ATTACK_SPEED, MINING_EFFICIENCY, BLOCK_BREAK_SPEED,
            ARMOR, ARMOR_TOUGHNESS, MOVEMENT_SPEED, ATTACK_KNOCKBACK,
            KNOCKBACK_RESISTANCE, OXYGEN_BONUS, SAFE_FALL_DISTANCE
        )));

        // Rare tier - Adds specialized attributes
        TIER_PROPERTIES.put("Rare", new TierProperties(1.7, Arrays.asList(
            ATTACK_DAMAGE, ATTACK_SPEED, MINING_EFFICIENCY, BLOCK_BREAK_SPEED,
            ARMOR, ARMOR_TOUGHNESS, MOVEMENT_SPEED, ATTACK_KNOCKBACK,
            KNOCKBACK_RESISTANCE, OXYGEN_BONUS, SAFE_FALL_DISTANCE,
            SWEEPING_DAMAGE_RATIO, SUBMERGED_MINING_SPEED, MAX_ABSORPTION,
            SNEAKING_SPEED, LUCK
        )));

        // Epic tier - Adds powerful attributes
        TIER_PROPERTIES.put("Epic", new TierProperties(2.2, Arrays.asList(
            ATTACK_DAMAGE, ATTACK_SPEED, MINING_EFFICIENCY, BLOCK_BREAK_SPEED,
            ARMOR, ARMOR_TOUGHNESS, MOVEMENT_SPEED, ATTACK_KNOCKBACK,
            KNOCKBACK_RESISTANCE, OXYGEN_BONUS, SAFE_FALL_DISTANCE,
            SWEEPING_DAMAGE_RATIO, SUBMERGED_MINING_SPEED, MAX_ABSORPTION,
            SNEAKING_SPEED, LUCK, MAX_HEALTH, WATER_MOVEMENT_EFFICIENCY,
            EXPLOSION_KNOCKBACK_RESISTANCE
        )));

        // Legendary tier - All attributes available
        TIER_PROPERTIES.put("Legendary", new TierProperties(3.0, new ArrayList<>(ALL_ATTRIBUTES)));
    }

    // Define item type specific attributes
    private static final Map<String, ItemTypeProperties> ITEM_TYPE_PROPERTIES = new HashMap<>();
    static {
        // Weapons
        ITEM_TYPE_PROPERTIES.put("SWORD", new ItemTypeProperties(Arrays.asList(
            new AttributeMod(ATTACK_DAMAGE, 6.0),
            new AttributeMod(ATTACK_SPEED, 1.6),
            new AttributeMod(ATTACK_KNOCKBACK, 0.5),
            new AttributeMod(SWEEPING_DAMAGE_RATIO, 0.25),
            new AttributeMod(ENTITY_INTERACTION_RANGE, 1.0)
        )));

        ITEM_TYPE_PROPERTIES.put("AXE", new ItemTypeProperties(Arrays.asList(
            new AttributeMod(ATTACK_DAMAGE, 5.0),
            new AttributeMod(ATTACK_SPEED, 1.0),
            new AttributeMod(ATTACK_KNOCKBACK, 0.8),
            new AttributeMod(BLOCK_BREAK_SPEED, 1.5),
            new AttributeMod(ENTITY_INTERACTION_RANGE, 1.0)
        )));

        ITEM_TYPE_PROPERTIES.put("PICKAXE", new ItemTypeProperties(Arrays.asList(
            new AttributeMod(MINING_EFFICIENCY, 2.0),
            new AttributeMod(BLOCK_BREAK_SPEED, 2.0),
            new AttributeMod(SUBMERGED_MINING_SPEED, 1.0),
            new AttributeMod(MOVEMENT_EFFICIENCY, 0.5),
            new AttributeMod(ENTITY_INTERACTION_RANGE, 1.0)
        )));

        // Armor pieces
        ITEM_TYPE_PROPERTIES.put("HELMET", new ItemTypeProperties(Arrays.asList(
            new AttributeMod(ARMOR, 2.0),
            new AttributeMod(ARMOR_TOUGHNESS, 1.0),
            new AttributeMod(OXYGEN_BONUS, 15.0),
            new AttributeMod(MAX_ABSORPTION, 2.0),
            new AttributeMod(KNOCKBACK_RESISTANCE, 0.05),
            new AttributeMod(LUCK, 0.1)
        )));

        ITEM_TYPE_PROPERTIES.put("CHESTPLATE", new ItemTypeProperties(Arrays.asList(
            new AttributeMod(ARMOR, 6.0),
            new AttributeMod(ARMOR_TOUGHNESS, 2.0),
            new AttributeMod(MAX_ABSORPTION, 4.0),
            new AttributeMod(KNOCKBACK_RESISTANCE, 0.1),
            new AttributeMod(MAX_HEALTH, 2.0)
        )));

        ITEM_TYPE_PROPERTIES.put("LEGGINGS", new ItemTypeProperties(Arrays.asList(
            new AttributeMod(ARMOR, 5.0),
            new AttributeMod(ARMOR_TOUGHNESS, 2.0),
            new AttributeMod(MAX_ABSORPTION, 3.0),
            new AttributeMod(KNOCKBACK_RESISTANCE, 0.05),
            new AttributeMod(MOVEMENT_SPEED, 0.1)
        )));

        ITEM_TYPE_PROPERTIES.put("BOOTS", new ItemTypeProperties(Arrays.asList(
            new AttributeMod(ARMOR, 2.0),
            new AttributeMod(ARMOR_TOUGHNESS, 1.0),
            new AttributeMod(MAX_ABSORPTION, 2.0),
            new AttributeMod(KNOCKBACK_RESISTANCE, 0.05),
            new AttributeMod(SNEAKING_SPEED, 0.1),
            new AttributeMod(SAFE_FALL_DISTANCE, 1.0),
            new AttributeMod(MOVEMENT_SPEED, 0.1)
        )));
    }

    // Helper classes for tier and item properties
    private static class TierProperties {
        final double multiplier;
        final List<Attribute> availableAttributes;

        TierProperties(double multiplier, List<Attribute> availableAttributes) {
            this.multiplier = multiplier;
            this.availableAttributes = availableAttributes;
        }
    }

    private static class ItemTypeProperties {
        final List<AttributeMod> baseAttributes;

        ItemTypeProperties(List<AttributeMod> baseAttributes) {
            this.baseAttributes = baseAttributes;
        }
    }

    private static class AttributeMod {
        final Attribute attribute;
        final double baseValue;

        AttributeMod(Attribute attribute, double baseValue) {
            this.attribute = attribute;
            this.baseValue = baseValue;
        }
    }

    // Quality ranges for each tier
    private static final Map<String, QualityRange> TIER_QUALITY_RANGES = new HashMap<>();
    static {
        // All tiers can get 1-100%, but higher percentages are exponentially rarer
        TIER_QUALITY_RANGES.put("Common", new QualityRange(1, 100, 2.3));
        TIER_QUALITY_RANGES.put("Uncommon", new QualityRange(1, 100, 2.1));
        TIER_QUALITY_RANGES.put("Rare", new QualityRange(1, 100, 1.7));
        TIER_QUALITY_RANGES.put("Epic", new QualityRange(1, 100, 1.2));
        TIER_QUALITY_RANGES.put("Legendary", new QualityRange(1, 100, 1.1));
    }

    private static class QualityRange {
        final int min;
        final int max;
        final double rarityFactor; // Higher means rarer high percentages

        QualityRange(int min, int max, double rarityFactor) {
            this.min = min;
            this.max = max;
            this.rarityFactor = rarityFactor;
        }

        int getRandomQuality(Random random) {
            // Use exponential distribution to make higher numbers rarer
            double u = random.nextDouble();
            // Transform uniform random to exponential distribution
            double x = -Math.log(1 - u) / rarityFactor;
            // Scale to our range and clamp
            int quality = (int) Math.round(min + (max - min) * (1 - Math.exp(-x)));
            return Math.min(Math.max(quality, min), max);
        }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();

        if (!(inv.getHolder() instanceof LootableInventory lootable)) return;
        if (lootable.hasBeenFilled()) return;

        FoliaScheduler.getRegionScheduler().runDelayed(this, event.getPlayer().getLocation(), (task) -> {
            boolean changed = false;

            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || !isEligibleItem(item) || hasTier(item)) continue;

                String tier = getRandomTier();
                assignTier(item, tier);
                inv.setItem(i, item);
                changed = true;
            }

            if (changed) {
                event.getPlayer().sendMessage(Component.text("Loot items have been assigned tiers!")
                    .color(NamedTextColor.GREEN));
            }
        }, 1);
    }

    @EventHandler
    public void onCreativeItemGive(InventoryCreativeEvent event) {
        ItemStack item = event.getCursor();
        if (item == null || !isEligibleItem(item) || hasTier(item)) return;

        String tier = getRandomTier();
        assignTier(item, tier);
        event.setCursor(item);
    }

    @EventHandler
    public void onCreativeClick(InventoryClickEvent event) {
        if (event.getWhoClicked().getGameMode() != GameMode.CREATIVE) return;
        if (event.getClick() != ClickType.CREATIVE) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || !isEligibleItem(item) || hasTier(item)) return;

        String tier = getRandomTier();
        assignTier(item, tier);
        event.setCurrentItem(item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getInventory().getType() != InventoryType.WORKBENCH
                && event.getInventory().getType() != InventoryType.CRAFTING) return;

        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        ItemStack result = event.getCurrentItem();
        if (result != null && isEligibleItem(result) && !hasTier(result)) {
            String tier = getRandomTier();
            assignTier(result, tier);
            event.setCurrentItem(result);

            FoliaScheduler.getRegionScheduler().runDelayed(this, player.getLocation(), (task) -> {
                scanAndAssignTiers(player.getInventory());
            }, 1);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        List<ItemStack> drops = event.getDrops();
        boolean changed = false;

        for (ItemStack item : drops) {
            if (item == null || !isEligibleItem(item) || hasTier(item)) continue;

            String tier = getRandomTier();
            assignTier(item, tier);
            changed = true;
        }

        if (changed && entity.getKiller() instanceof Player player) {
            player.sendMessage(Component.text("You got a tiered item drop!")
                .color(NamedTextColor.YELLOW));
        }
    }

    private void scanAndAssignTiers(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && isEligibleItem(item) && !hasTier(item)) {
                String tier = getRandomTier();
                assignTier(item, tier);
                inventory.setItem(i, item);
            }
        }
    }

    private boolean isEligibleItem(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        String name = mat.name();

        return name.contains("SWORD")
                || name.contains("AXE")
                || name.contains("PICKAXE")
                || name.contains("SHOVEL")
                || name.contains("HOE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    private boolean hasTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(tierKey, PersistentDataType.STRING) &&
               item.getItemMeta().getPersistentDataContainer().has(qualityKey, PersistentDataType.INTEGER) &&
               item.getItemMeta().getPersistentDataContainer().has(tierMultiplierKey, PersistentDataType.DOUBLE) &&
               item.getItemMeta().getPersistentDataContainer().has(qualityMultiplierKey, PersistentDataType.DOUBLE) &&
               item.getItemMeta().getPersistentDataContainer().has(baseValueKey, PersistentDataType.STRING) &&
               item.getItemMeta().getPersistentDataContainer().has(attributesKey, PersistentDataType.STRING);
    }

    private String getRandomTier() {
        return TIERS.get(random.nextInt(TIERS.size()));
    }

    private int getRandomQuality(String tier) {
        QualityRange range = TIER_QUALITY_RANGES.get(tier);
        return range != null ? range.getRandomQuality(random) : 50;
    }

    private Component getQualityColor(int quality) {
        if (quality >= 95) return Component.text("").color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD);
        if (quality >= 90) return Component.text("").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD);
        if (quality >= 80) return Component.text("").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
        if (quality >= 70) return Component.text("").color(NamedTextColor.YELLOW);
        if (quality >= 50) return Component.text("").color(NamedTextColor.GREEN);
        if (quality >= 30) return Component.text("").color(NamedTextColor.GRAY);
        return Component.text("").color(NamedTextColor.DARK_GRAY);
    }

    private void assignTier(ItemStack item, String tier) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Generate and store quality
        int quality = getRandomQuality(tier);
        double qualityMultiplier = quality / 100.0;
        
        // Get tier properties
        TierProperties tierProps = TIER_PROPERTIES.get(tier);
        if (tierProps == null) return;

        String type = item.getType().name();
        String itemType = getItemType(type);
        ItemTypeProperties itemProps = ITEM_TYPE_PROPERTIES.get(itemType);
        if (itemProps == null) return;

        // Store all data in item's persistent data
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier);
        meta.getPersistentDataContainer().set(qualityKey, PersistentDataType.INTEGER, quality);
        meta.getPersistentDataContainer().set(tierMultiplierKey, PersistentDataType.DOUBLE, tierProps.multiplier);
        meta.getPersistentDataContainer().set(qualityMultiplierKey, PersistentDataType.DOUBLE, qualityMultiplier);

        // Store base values for each attribute
        Map<String, Double> baseValues = new HashMap<>();
        for (AttributeMod attrMod : itemProps.baseAttributes) {
            baseValues.put(attrMod.attribute.toString(), attrMod.baseValue);
        }
        meta.getPersistentDataContainer().set(baseValueKey, PersistentDataType.STRING, 
            new Gson().toJson(baseValues));

        // Store available attributes for this tier
        List<String> availableAttrs = tierProps.availableAttributes.stream()
            .map(Attribute::toString)
            .collect(Collectors.toList());
        meta.getPersistentDataContainer().set(attributesKey, PersistentDataType.STRING,
            new Gson().toJson(availableAttrs));

        List<Component> lore = new ArrayList<>();
        
        // Add tier with quality percentage in one line using TextComponent
        Component tierComponent = Component.text("Tier: ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(tier)
                .color(NamedTextColor.GOLD))
            .append(Component.text(" ")
                .color(NamedTextColor.GRAY))
            .append(getQualityColor(quality))
            .append(Component.text("(" + quality + "%)"));
        
        lore.add(tierComponent);

        // Remove all existing attribute modifiers first
        for (Attribute attr : ALL_ATTRIBUTES) {
            meta.removeAttributeModifier(attr);
        }

        // Apply attributes based on stored data
        for (AttributeMod attrMod : itemProps.baseAttributes) {
            if (!tierProps.availableAttributes.contains(attrMod.attribute)) continue;

            double value = attrMod.baseValue * tierProps.multiplier * qualityMultiplier;
            EquipmentSlotGroup slotGroup = getEquipmentSlotForAttr(attrMod.attribute, type).getGroup();
            NamespacedKey key = new NamespacedKey(this, "tier_" + attrMod.attribute.toString().toLowerCase());

            AttributeModifier mod = new AttributeModifier(
                key,
                value,
                AttributeModifier.Operation.ADD_NUMBER,
                slotGroup
            );

            meta.addAttributeModifier(attrMod.attribute, mod);
            
            // Create attribute line using TextComponent
            Component attrComponent = Component.text(prettifyAttrName(attrMod.attribute))
                .color(NamedTextColor.GREEN)
                .append(Component.text(String.format(" %+,.2f", value))
                    .color(NamedTextColor.GREEN));
            
            lore.add(attrComponent);
        }

        // Add special tier-specific bonuses, also affected by quality
        addTierSpecificBonuses(meta, lore, tier, type, tierProps.multiplier * qualityMultiplier);

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private String getItemType(String materialName) {
        if (materialName.contains("SWORD")) return "SWORD";
        if (materialName.contains("AXE")) return "AXE";
        if (materialName.contains("PICKAXE")) return "PICKAXE";
        if (materialName.contains("SHOVEL")) return "SHOVEL";
        if (materialName.contains("HOE")) return "HOE";
        if (materialName.endsWith("_HELMET")) return "HELMET";
        if (materialName.endsWith("_CHESTPLATE")) return "CHESTPLATE";
        if (materialName.endsWith("_LEGGINGS")) return "LEGGINGS";
        if (materialName.endsWith("_BOOTS")) return "BOOTS";
        return "UNKNOWN";
    }

    private void addTierSpecificBonuses(ItemMeta meta, List<Component> lore, String tier, String type, double multiplier) {
        // Add special tier-specific bonuses that aren't part of the base attributes
        switch (tier) {
            case "Rare":
                if (type.contains("SWORD") || type.contains("AXE")) {
                    addSpecialAttribute(meta, lore, LUCK, 0.2 * multiplier, type);
                }
                if (type.endsWith("_HELMET")) {
                    addSpecialAttribute(meta, lore, WATER_MOVEMENT_EFFICIENCY, 0.1 * multiplier, type);
                }
                break;
            case "Epic":
                if (type.contains("PICKAXE")) {
                    addSpecialAttribute(meta, lore, EXPLOSION_KNOCKBACK_RESISTANCE, 0.2 * multiplier, type);
                }
                if (type.endsWith("_CHESTPLATE")) {
                    addSpecialAttribute(meta, lore, MAX_HEALTH, 2.0 * multiplier, type);
                }
                break;
            case "Legendary":
                if (type.contains("SWORD")) {
                    addSpecialAttribute(meta, lore, SWEEPING_DAMAGE_RATIO, 0.5 * multiplier, type);
                    addSpecialAttribute(meta, lore, EXPLOSION_KNOCKBACK_RESISTANCE, 0.3 * multiplier, type);
                }
                if (type.endsWith("_BOOTS")) {
                    addSpecialAttribute(meta, lore, STEP_HEIGHT, 0.5 * multiplier, type);
                    addSpecialAttribute(meta, lore, JUMP_STRENGTH, 0.2 * multiplier, type);
                }
                break;
        }
    }

    private void addSpecialAttribute(ItemMeta meta, List<Component> lore, Attribute attr, double value, String type) {
        EquipmentSlotGroup slotGroup = getEquipmentSlotForAttr(attr, type).getGroup();
        NamespacedKey key = new NamespacedKey(this, "tier_special_" + attr.toString().toLowerCase());

        AttributeModifier mod = new AttributeModifier(
            key,
            value,
            AttributeModifier.Operation.ADD_NUMBER,
            slotGroup
        );

        meta.addAttributeModifier(attr, mod);
        
        // Create special attribute line using TextComponent
        Component specialComponent = Component.text(prettifyAttrName(attr))
            .color(NamedTextColor.AQUA)
            .append(Component.text(String.format(" %+,.2f", value))
                .color(NamedTextColor.AQUA))
            .append(Component.text(" (Special)")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.ITALIC));
        
        lore.add(specialComponent);
    }

    private EquipmentSlot getEquipmentSlotForAttr(Attribute attr, String itemType) {
        // Try to guess slot based on item type
        if (itemType.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (itemType.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (itemType.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (itemType.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        if (itemType.contains("SWORD") || itemType.contains("AXE") || itemType.contains("PICKAXE")
                || itemType.contains("SHOVEL") || itemType.contains("HOE"))
            return EquipmentSlot.HAND;
        return EquipmentSlot.HAND; // default
    }

}
