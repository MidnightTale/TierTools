package fun.mntale.tierTools;

import com.destroystokyo.paper.loottable.LootableInventory;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Registry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.attribute.Attribute.*;

public class TierTools extends JavaPlugin implements Listener, CommandExecutor {

    private final Random random = new Random();

    private List<String> TIERS;
    private final NamespacedKey tierKey = new NamespacedKey(this, "tool_tier");
    private final NamespacedKey qualityKey = new NamespacedKey(this, "tool_quality");
    private final NamespacedKey baseValueKey = new NamespacedKey(this, "tool_base_value");
    private final NamespacedKey tierMultiplierKey = new NamespacedKey(this, "tool_tier_multiplier");
    private final NamespacedKey qualityMultiplierKey = new NamespacedKey(this, "tool_quality_multiplier");
    private final NamespacedKey attributesKey = new NamespacedKey(this, "tool_attributes");

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
    private Map<Attribute, String> ATTR_NAMES = new HashMap<>();
    private boolean tierAssignmentEnabled = true;
    private List<String> eligibleItemTypes = new ArrayList<>();
    private boolean qualityColorsEnabled = true;

    // Define tier-specific attributes and multipliers
    private static final Map<String, TierProperties> TIER_PROPERTIES = new HashMap<>();

    // Define item type specific attributes
    private static final Map<String, ItemTypeProperties> ITEM_TYPE_PROPERTIES = new HashMap<>();

    // Quality ranges for each tier
    private static final Map<String, QualityRange> TIER_QUALITY_RANGES = new HashMap<>();

    // Tier-specific bonuses
    private static final Map<String, Map<String, List<SpecialBonus>>> TIER_SPECIFIC_BONUSES = new HashMap<>();

    private record SpecialBonus(Attribute attribute, double baseValue) {
    }

    // Lore template keys
    private static final NamespacedKey LORE_TEMPLATE_KEY = new NamespacedKey("tier_tools", "lore_template");
    private static final NamespacedKey LORE_VERSION_KEY = new NamespacedKey("tier_tools", "lore_version");

    // Lore templates from config
    private String tierLineTemplate;
    private String attributeLineTemplate;
    private String specialAttributeLineTemplate;
    private Map<Integer, String> qualityColors;

    // Line symbols configuration
    private boolean lineSymbolsEnabled = true;
    private String lineSymbolStart = "┝";
    private String lineSymbolMiddle = "┝";
    private String lineSymbolEnd = "┖";
    private String lineSymbolIndent = "  ";

    private boolean usePlaceholderAPI = false;
    private MiniMessage miniMessage;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Load tier list
        loadTierList();
        logTierList();
        
        // Load tier properties from config
        loadTierProperties();
        logTierProperties();
        
        // Load item type properties from config
        loadItemTypeProperties();
        logItemTypeProperties();

        // Load quality ranges from config
        loadQualityRanges();
        logQualityRanges();

        // Load tier-specific bonuses from config
        loadTierSpecificBonuses();
        logTierSpecificBonuses();
        
        // Load lore templates
        loadLoreTemplates();
        
        // Initialize MiniMessage
        miniMessage = MiniMessage.builder()
            .tags(StandardTags.defaults())
            .build();

        // Check for PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            usePlaceholderAPI = true;
            getLogger().info("PlaceholderAPI found! PlaceholderAPI placeholders are available.");
        }
        
        // Load attribute names
        loadAttributeNames();
        logAttributeNames();
        
        // Load eligible items
        loadEligibleItems();
        
        // Load quality color settings
        loadQualityColorSettings();

        // Register commands
        getCommand("tiertools").setExecutor(this);
        
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void logTierList() {
        getLogger().info("=== Tier List Configuration ===");
        getLogger().info("Tiers (in order):");
        for (int i = 0; i < TIERS.size(); i++) {
            getLogger().info(String.format("  %d. %s", i + 1, TIERS.get(i)));
        }
        getLogger().info("============================");
    }

    private void logTierProperties() {
        getLogger().info("=== Tier Properties Configuration ===");
        for (Map.Entry<String, TierProperties> entry : TIER_PROPERTIES.entrySet()) {
            String tier = entry.getKey();
            TierProperties props = entry.getValue();
            getLogger().info(String.format("Tier: %s", tier));
            getLogger().info(String.format("  Multiplier: %.2f", props.multiplier()));
            getLogger().info("  Available Attributes:");
            for (Attribute attr : props.availableAttributes()) {
                getLogger().info(String.format("    - %s", attr.toString()));
            }
        }
        getLogger().info("===================================");
    }

    private void logItemTypeProperties() {
        getLogger().info("=== Item Type Properties Configuration ===");
        for (Map.Entry<String, ItemTypeProperties> entry : ITEM_TYPE_PROPERTIES.entrySet()) {
            String type = entry.getKey();
            ItemTypeProperties props = entry.getValue();
            getLogger().info(String.format("Item Type: %s", type));
            getLogger().info("  Base Attributes:");
            for (AttributeMod mod : props.baseAttributes()) {
                getLogger().info(String.format("    - %s: %.2f", 
                    mod.attribute().toString(), mod.baseValue()));
            }
        }
        getLogger().info("=======================================");
    }

    private void logQualityRanges() {
        getLogger().info("=== Quality Ranges Configuration ===");
        for (Map.Entry<String, QualityRange> entry : TIER_QUALITY_RANGES.entrySet()) {
            String tier = entry.getKey();
            QualityRange range = entry.getValue();
            getLogger().info(String.format("Tier: %s", tier));
            getLogger().info(String.format("  Min Quality: %d", range.min()));
            getLogger().info(String.format("  Max Quality: %d", range.max()));
            getLogger().info(String.format("  Rarity Factor: %.2f", range.rarityFactor()));
        }
        getLogger().info("=================================");
    }

    private void logTierSpecificBonuses() {
        getLogger().info("=== Tier-Specific Bonuses Configuration ===");
        for (Map.Entry<String, Map<String, List<SpecialBonus>>> tierEntry : TIER_SPECIFIC_BONUSES.entrySet()) {
            String tier = tierEntry.getKey();
            getLogger().info(String.format("Tier: %s", tier));
            
            for (Map.Entry<String, List<SpecialBonus>> itemEntry : tierEntry.getValue().entrySet()) {
                String itemType = itemEntry.getKey();
                getLogger().info(String.format("  Item Type: %s", itemType));
                
                for (SpecialBonus bonus : itemEntry.getValue()) {
                    getLogger().info(String.format("    - %s: %.2f", 
                        bonus.attribute().toString(), bonus.baseValue()));
                }
            }
        }
        getLogger().info("========================================");
    }

    private void logAttributeNames() {
        getLogger().info("=== Attribute Names Configuration ===");
        for (Map.Entry<Attribute, String> entry : ATTR_NAMES.entrySet()) {
            getLogger().info(String.format("%s: %s", 
                entry.getKey().toString(), entry.getValue()));
        }
        getLogger().info("===================================");
    }

    private void loadTierList() {
        List<String> tiers = getConfig().getStringList("tier_list");
        if (tiers.isEmpty()) {
            getLogger().warning("No tier list found in config.yml!");
            TIERS = Arrays.asList("Common", "Uncommon", "Rare", "Epic", "Legendary");
        } else {
            TIERS = tiers;
        }
    }

    private void loadQualityRanges() {
        TIER_QUALITY_RANGES.clear();
        
        ConfigurationSection qualityRangesSection = getConfig().getConfigurationSection("quality_ranges");
        if (qualityRangesSection == null) {
            getLogger().warning("No quality ranges configuration found in config.yml!");
            return;
        }

        for (String tier : qualityRangesSection.getKeys(false)) {
            ConfigurationSection tierSection = qualityRangesSection.getConfigurationSection(tier);
            if (tierSection == null) continue;

            int min = tierSection.getInt("min", 1);
            int max = tierSection.getInt("max", 100);
            double rarityFactor = tierSection.getDouble("rarity_factor", 2.0);

            TIER_QUALITY_RANGES.put(tier, new QualityRange(min, max, rarityFactor));
        }

        // Validate that we have quality ranges for all tiers
        for (String tier : TIERS) {
            if (!TIER_QUALITY_RANGES.containsKey(tier)) {
                getLogger().warning("Missing quality range for tier: " + tier + "!");
                return;
            }
        }
    }

    private void loadTierSpecificBonuses() {
        TIER_SPECIFIC_BONUSES.clear();
        
        ConfigurationSection bonusesSection = getConfig().getConfigurationSection("tier_specific_bonuses");
        if (bonusesSection == null) {
            getLogger().warning("No tier-specific bonuses configuration found in config.yml!");
            return;
        }

        for (String tier : bonusesSection.getKeys(false)) {
            ConfigurationSection tierSection = bonusesSection.getConfigurationSection(tier);
            if (tierSection == null) continue;

            Map<String, List<SpecialBonus>> itemTypeBonuses = new HashMap<>();
            
            for (String itemType : tierSection.getKeys(false)) {
                List<Map<?, ?>> bonusList = tierSection.getMapList(itemType);
                List<SpecialBonus> bonuses = new ArrayList<>();

                for (Map<?, ?> bonusMap : bonusList) {
                    try {
                        String attrName = (String) bonusMap.get("attribute");
                        double value = ((Number) bonusMap.get("value")).doubleValue();
                        
                        NamespacedKey key = NamespacedKey.minecraft(attrName.toLowerCase(Locale.ROOT));
                        Attribute attr = Registry.ATTRIBUTE.get(key);
                        
                        if (attr != null) {
                            bonuses.add(new SpecialBonus(attr, value));
                        } else {
                            getLogger().warning("Invalid attribute name in tier-specific bonus for " + tier + " " + itemType + ": " + attrName);
                        }
                    } catch (Exception e) {
                        getLogger().warning("Invalid tier-specific bonus configuration for " + tier + " " + itemType);
                    }
                }

                if (!bonuses.isEmpty()) {
                    itemTypeBonuses.put(itemType, bonuses);
                }
            }

            if (!itemTypeBonuses.isEmpty()) {
                TIER_SPECIFIC_BONUSES.put(tier, itemTypeBonuses);
            }
        }
    }
  
    private void loadTierProperties() {
        TIER_PROPERTIES.clear();
        
        // Get the tiers section from config
        ConfigurationSection tiersSection = getConfig().getConfigurationSection("tiers");
        if (tiersSection == null) {
            getLogger().warning("No tiers configuration found in config.yml!");
            return;
        }

        // Load each tier from config
        for (String tierName : tiersSection.getKeys(false)) {
            ConfigurationSection tierSection = tiersSection.getConfigurationSection(tierName);
            if (tierSection == null) continue;

            double multiplier = tierSection.getDouble("multiplier", 1.0);
            List<Attribute> attributes = new ArrayList<>();

            // Handle special case for Legendary tier with "ALL" attributes
            if (tierSection.isString("available_attributes") && 
                tierSection.getString("available_attributes").equals("ALL")) {
                attributes.addAll(ALL_ATTRIBUTES);
            } else {
                // Load individual attributes
                List<String> attrNames = tierSection.getStringList("available_attributes");
                for (String attrName : attrNames) {
                    try {
                        // Convert attribute name to lowercase and create NamespacedKey
                        NamespacedKey key = NamespacedKey.minecraft(attrName.toLowerCase(Locale.ROOT));
                        Attribute attr = Registry.ATTRIBUTE.get(key);
                        if (attr != null) {
                            attributes.add(attr);
                        } else {
                            getLogger().warning("Invalid attribute name in config: " + attrName);
                        }
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid attribute name in config: " + attrName);
                    }
                }
            }

            TIER_PROPERTIES.put(tierName, new TierProperties(multiplier, attributes));
        }

        // Validate that we have at least the basic tiers
        if (!TIER_PROPERTIES.containsKey("Common") || !TIER_PROPERTIES.containsKey("Legendary")) {
            getLogger().warning("Missing required tiers in config.yml");
        }
    }



    private void loadItemTypeProperties() {
        ITEM_TYPE_PROPERTIES.clear();
        
        // Get the item_types section from config
        ConfigurationSection itemTypesSection = getConfig().getConfigurationSection("item_types");
        if (itemTypesSection == null) {
            getLogger().warning("No item types configuration found in config.yml!");
            return;
        }

        // Load each item type from config
        for (String itemType : itemTypesSection.getKeys(false)) {
            ConfigurationSection itemTypeSection = itemTypesSection.getConfigurationSection(itemType);
            if (itemTypeSection == null) continue;

            ConfigurationSection baseAttrsSection = itemTypeSection.getConfigurationSection("base_attributes");
            if (baseAttrsSection == null) {
                getLogger().warning("No base attributes found for item type: " + itemType);
                continue;
            }

            List<AttributeMod> baseAttributes = new ArrayList<>();
            for (String attrName : baseAttrsSection.getKeys(false)) {
                try {
                    // Convert attribute name to lowercase and create NamespacedKey
                    NamespacedKey key = NamespacedKey.minecraft(attrName.toLowerCase(Locale.ROOT));
                    Attribute attr = Registry.ATTRIBUTE.get(key);
                    if (attr != null) {
                        double value = baseAttrsSection.getDouble(attrName);
                        baseAttributes.add(new AttributeMod(attr, value));
                    } else {
                        getLogger().warning("Invalid attribute name in config for item type " + itemType + ": " + attrName);
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid attribute name in config for item type " + itemType + ": " + attrName);
                }
            }

            if (!baseAttributes.isEmpty()) {
                ITEM_TYPE_PROPERTIES.put(itemType, new ItemTypeProperties(baseAttributes));
            } else {
                getLogger().warning("No valid attributes found for item type: " + itemType);
            }
        }

        // Validate that we have at least the basic item types
        if (!ITEM_TYPE_PROPERTIES.containsKey("SWORD") || !ITEM_TYPE_PROPERTIES.containsKey("HELMET")) {
            getLogger().warning("Missing required item types in config.yml!");
        }
    }

    // Helper classes for tier and item properties
        private record TierProperties(double multiplier, List<Attribute> availableAttributes) {
    }

    private record ItemTypeProperties(List<AttributeMod> baseAttributes) {
    }

    private record AttributeMod(Attribute attribute, double baseValue) {
    }

    /**
     * @param rarityFactor Higher means rarer high percentages
     */
    private record QualityRange(int min, int max, double rarityFactor) {

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

        // Update clicked item if it has a tier
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && hasTier(clickedItem)) {
            updateItemLore(clickedItem.getItemMeta());
            event.setCurrentItem(clickedItem);
        }

        // Update cursor item if it has a tier
        ItemStack cursorItem = event.getCursor();
        if (cursorItem != null && hasTier(cursorItem)) {
            updateItemLore(cursorItem.getItemMeta());
            event.setCursor(cursorItem);
        }

        // Handle crafting/crafting table clicks
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
        if (!tierAssignmentEnabled || item == null) return false;
        
        String name = item.getType().name();
        return eligibleItemTypes.stream().anyMatch(type -> name.contains(type));
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

    private String getQualityColor(int quality) {
        if (!qualityColorsEnabled) return "";
        
        for (Map.Entry<Integer, String> entry : qualityColors.entrySet()) {
            if (quality >= entry.getKey()) {
                return entry.getValue();
            }
        }
        return qualityColors.get(0); // Default color
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
        }

        // Add special tier-specific bonuses
        addTierSpecificBonuses(meta, tier, type, tierProps.multiplier * qualityMultiplier);

        // Update the lore
        updateItemLore(meta);

        item.setItemMeta(meta);
    }

    private void updateItemLore(ItemMeta meta) {
        if (!meta.getPersistentDataContainer().has(tierKey, PersistentDataType.STRING)) return;

        String tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        int quality = meta.getPersistentDataContainer().get(qualityKey, PersistentDataType.INTEGER);
        String qualityColor = getQualityColor(quality);

        List<Component> lore = new ArrayList<>();

        if (usePlaceholderAPI) {
            // Use PlaceholderAPI format
            String tierLine = tierLineTemplate
                .replace("%tier_tools_tier%", tier)
                .replace("%tier_tools_quality%", String.valueOf(quality))
                .replace("%tier_tools_quality_color%", qualityColor);
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(tierLine));

            // Collect all attribute lines first
            List<String> allAttributeLines = new ArrayList<>();
            int totalAttrCount = 0;

            // Add regular attributes
            if (meta.getPersistentDataContainer().has(baseValueKey, PersistentDataType.STRING)) {
                Map<String, Double> baseValues = new Gson().fromJson(
                    meta.getPersistentDataContainer().get(baseValueKey, PersistentDataType.STRING),
                    new TypeToken<Map<String, Double>>(){}.getType()
                );

                double tierMultiplier = meta.getPersistentDataContainer().get(tierMultiplierKey, PersistentDataType.DOUBLE);
                double qualityMultiplier = meta.getPersistentDataContainer().get(qualityMultiplierKey, PersistentDataType.DOUBLE);

                for (Map.Entry<String, Double> entry : baseValues.entrySet()) {
                    try {
                        NamespacedKey key = NamespacedKey.minecraft(entry.getKey().toLowerCase(Locale.ROOT));
                        Attribute attr = Registry.ATTRIBUTE.get(key);
                        if (attr != null) {
                            double value = entry.getValue() * tierMultiplier * qualityMultiplier;
                            String attrLine = attributeLineTemplate
                                .replace("%tier_tools_attribute_name%", prettifyAttrName(attr))
                                .replace("%tier_tools_attribute_value%", String.format("%+,.2f", value));
                            allAttributeLines.add(attrLine);
                            totalAttrCount++;
                        }
                    } catch (Exception e) {
                        getLogger().warning("Error processing attribute for lore: " + entry.getKey());
                    }
                }
            }

            // Add special attributes
            for (AttributeModifier mod : meta.getAttributeModifiers().values()) {
                if (mod.getName().startsWith("tier_special_")) {
                    String attrName = mod.getName().substring("tier_special_".length());
                    try {
                        NamespacedKey key = NamespacedKey.minecraft(attrName);
                        Attribute attr = Registry.ATTRIBUTE.get(key);
                        if (attr != null) {
                            String specialLine = specialAttributeLineTemplate
                                .replace("%tier_tools_attribute_name%", prettifyAttrName(attr))
                                .replace("%tier_tools_attribute_value%", String.format("%+,.2f", mod.getAmount()))
                                .replace("%tier_tools_special_prefix%", "Special");
                            allAttributeLines.add(specialLine);
                            totalAttrCount++;
                        }
                    } catch (Exception e) {
                        getLogger().warning("Error processing special attribute for lore: " + attrName);
                    }
                }
            }

            // Apply line symbols to all attributes
            for (int i = 0; i < allAttributeLines.size(); i++) {
                String line = allAttributeLines.get(i);
                if (lineSymbolsEnabled) {
                    String symbol = i == 0 ? lineSymbolStart :
                                 i == allAttributeLines.size() - 1 ? lineSymbolEnd :
                                 lineSymbolMiddle;
                    line = lineSymbolIndent + symbol + " " + line;
                }
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
            }
        } else {
            // Use MiniMessage format
            TagResolver tierResolver = TagResolver.resolver(
                Placeholder.parsed("tier", tier),
                Placeholder.parsed("quality", String.valueOf(quality)),
                Placeholder.parsed("quality_color", qualityColor)
            );

            lore.add(miniMessage.deserialize(tierLineTemplate, tierResolver));

            // Collect all attribute lines first
            List<String> allAttributeLines = new ArrayList<>();
            int totalAttrCount = 0;

            // Add regular attributes
            if (meta.getPersistentDataContainer().has(baseValueKey, PersistentDataType.STRING)) {
                Map<String, Double> baseValues = new Gson().fromJson(
                    meta.getPersistentDataContainer().get(baseValueKey, PersistentDataType.STRING),
                    new TypeToken<Map<String, Double>>(){}.getType()
                );

                double tierMultiplier = meta.getPersistentDataContainer().get(tierMultiplierKey, PersistentDataType.DOUBLE);
                double qualityMultiplier = meta.getPersistentDataContainer().get(qualityMultiplierKey, PersistentDataType.DOUBLE);

                for (Map.Entry<String, Double> entry : baseValues.entrySet()) {
                    try {
                        NamespacedKey key = NamespacedKey.minecraft(entry.getKey().toLowerCase(Locale.ROOT));
                        Attribute attr = Registry.ATTRIBUTE.get(key);
                        if (attr != null) {
                            double value = entry.getValue() * tierMultiplier * qualityMultiplier;
                            TagResolver attrResolver = TagResolver.resolver(
                                Placeholder.parsed("attribute_name", prettifyAttrName(attr)),
                                Placeholder.parsed("attribute_value", String.format("%+,.2f", value))
                            );
                            String attrLine = miniMessage.serialize(miniMessage.deserialize(attributeLineTemplate, attrResolver));
                            allAttributeLines.add(attrLine);
                            totalAttrCount++;
                        }
                    } catch (Exception e) {
                        getLogger().warning("Error processing attribute for lore: " + entry.getKey());
                    }
                }
            }

            // Add special attributes
            for (AttributeModifier mod : Objects.requireNonNull(Objects.requireNonNull(meta.getAttributeModifiers())).values()) {
                if (mod.getName().startsWith("tier_special_")) {
                    String attrName = mod.getName().substring("tier_special_".length());
                    try {
                        NamespacedKey key = NamespacedKey.minecraft(attrName);
                        Attribute attr = Registry.ATTRIBUTE.get(key);
                        if (attr != null) {
                            TagResolver specialResolver = TagResolver.resolver(
                                Placeholder.parsed("attribute_name", prettifyAttrName(attr)),
                                Placeholder.parsed("attribute_value", String.format("%+,.2f", mod.getAmount())),
                                Placeholder.parsed("special_prefix", "Special")
                            );
                            String specialLine = miniMessage.serialize(miniMessage.deserialize(specialAttributeLineTemplate, specialResolver));
                            allAttributeLines.add(specialLine);
                            totalAttrCount++;
                        }
                    } catch (Exception e) {
                        getLogger().warning("Error processing special attribute for lore: " + attrName);
                    }
                }
            }

            // Apply line symbols to all attributes
            for (int i = 0; i < allAttributeLines.size(); i++) {
                String line = allAttributeLines.get(i);
                if (lineSymbolsEnabled) {
                    String symbol = i == 0 ? lineSymbolStart :
                                 i == allAttributeLines.size() - 1 ? lineSymbolEnd :
                                 lineSymbolMiddle;
                    line = lineSymbolIndent + symbol + " " + line;
                }
                lore.add(miniMessage.deserialize(line));
            }
        }

        meta.lore(lore);
    }

    private void addTierSpecificBonuses(ItemMeta meta, String tier, String type, double multiplier) {
        Map<String, List<SpecialBonus>> tierBonuses = TIER_SPECIFIC_BONUSES.get(tier);
        if (tierBonuses == null) return;

        String itemType = getItemType(type);
        List<SpecialBonus> bonuses = tierBonuses.get(itemType);
        if (bonuses == null) return;

        for (SpecialBonus bonus : bonuses) {
            EquipmentSlotGroup slotGroup = getEquipmentSlotForAttr(bonus.attribute, type).getGroup();
            NamespacedKey key = new NamespacedKey(this, "tier_special_" + bonus.attribute.toString().toLowerCase());

            AttributeModifier mod = new AttributeModifier(
                key,
                bonus.baseValue * multiplier,
                AttributeModifier.Operation.ADD_NUMBER,
                slotGroup
            );

            meta.addAttributeModifier(bonus.attribute, mod);
        }
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

    private EquipmentSlot getEquipmentSlotForAttr(Attribute attr, String itemType) {
        // Try to guess slot based on item type
        if (itemType.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (itemType.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (itemType.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (itemType.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        return EquipmentSlot.HAND; // default
    }

    private void loadLoreTemplates() {
        ConfigurationSection loreSection = getConfig().getConfigurationSection("lore_templates");
        if (loreSection == null) {
            getLogger().warning("No lore templates found in config.yml!");
            return;
        }

        // Load line symbols configuration
        ConfigurationSection symbolsSection = loreSection.getConfigurationSection("line_symbols");
        if (symbolsSection != null) {
            lineSymbolsEnabled = symbolsSection.getBoolean("enabled", true);
            ConfigurationSection symbols = symbolsSection.getConfigurationSection("symbols");
            if (symbols != null) {
                lineSymbolStart = symbols.getString("start", "┝");
                lineSymbolMiddle = symbols.getString("middle", "┝");
                lineSymbolEnd = symbols.getString("end", "┖");
                lineSymbolIndent = symbols.getString("indent", "  ");
            }
            getLogger().info("Line symbols are " + (lineSymbolsEnabled ? "enabled" : "disabled"));
            if (lineSymbolsEnabled) {
                getLogger().info(String.format("Line symbols: start='%s', middle='%s', end='%s', indent='%s'",
                    lineSymbolStart, lineSymbolMiddle, lineSymbolEnd, lineSymbolIndent));
            }
        }

        // Check if templates use PlaceholderAPI format
        String tierLine = loreSection.getString("tier_line", "");
        usePlaceholderAPI = tierLine.contains("%tier_tools_");

        tierLineTemplate = tierLine;
        attributeLineTemplate = loreSection.getString("attribute_line", "");
        specialAttributeLineTemplate = loreSection.getString("special_attribute_line", "");
    }

    private void loadQualityColorSettings() {
        ConfigurationSection qualitySection = getConfig().getConfigurationSection("quality_colors");
        if (qualitySection == null) {
            getLogger().warning("No quality colors configuration found in config.yml!");
            return;
        }

        qualityColorsEnabled = qualitySection.getBoolean("enabled", true);
        getLogger().info("Quality colors are " + (qualityColorsEnabled ? "enabled" : "disabled"));
        
        if (!qualityColorsEnabled) return;

        ConfigurationSection colorsSection = qualitySection.getConfigurationSection("colors");
        if (colorsSection == null) {
            getLogger().warning("No quality color thresholds found in config.yml!");
            return;
        }

        qualityColors = new TreeMap<>(Collections.reverseOrder());
        for (String threshold : colorsSection.getKeys(false)) {
            try {
                int value = Integer.parseInt(threshold);
                String color = colorsSection.getString(threshold);
                qualityColors.put(value, color);
                getLogger().info("Loaded quality color for threshold " + value + ": " + color);
            } catch (NumberFormatException e) {
                getLogger().warning("Invalid quality color threshold: " + threshold);
            }
        }

        String defaultColor = qualitySection.getString("default", "<dark_gray>");
        if (!qualityColors.containsKey(0)) {
            qualityColors.put(0, defaultColor);
            getLogger().info("Using default quality color: " + defaultColor);
        }
    }

    private void loadAttributeNames() {
        ATTR_NAMES.clear();
        ConfigurationSection attrNamesSection = getConfig().getConfigurationSection("attribute_names");
        if (attrNamesSection == null) {
            getLogger().warning("No attribute names found in config.yml!");
            return;
        }

        for (String attrKey : attrNamesSection.getKeys(false)) {
            try {
                NamespacedKey key = NamespacedKey.minecraft(attrKey.toLowerCase(Locale.ROOT));
                Attribute attr = Registry.ATTRIBUTE.get(key);
                if (attr != null) {
                    ATTR_NAMES.put(attr, attrNamesSection.getString(attrKey));
                } else {
                    getLogger().warning("Invalid attribute name in config: " + attrKey);
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid attribute name in config: " + attrKey);
            }
        }
    }

    private void loadEligibleItems() {
        ConfigurationSection eligibleSection = getConfig().getConfigurationSection("eligible_items");
        if (eligibleSection == null) {
            getLogger().warning("No eligible items configuration found in config.yml!");
            return;
        }

        tierAssignmentEnabled = eligibleSection.getBoolean("enabled", true);
        eligibleItemTypes = eligibleSection.getStringList("types");
        
        if (eligibleItemTypes.isEmpty()) {
            getLogger().warning("No eligible item types found in config.yml!");
        }
    }

    private String prettifyAttrName(Attribute attr) {
        return ATTR_NAMES.getOrDefault(attr, attr.toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("tiertools")) return false;
        
        if (args.length == 0) {
            sender.sendMessage(Component.text("TierTools commands:")
                .color(NamedTextColor.GOLD));
            sender.sendMessage(Component.text("/tiertools reload - Reload the configuration")
                .color(NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("tiertools.reload")) {
                sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
                return true;
            }

            // Reload config
            reloadConfig();
            
            // Reload all settings
            loadTierList();
            logTierList();
            loadTierProperties();
            logTierProperties();
            loadItemTypeProperties();
            logItemTypeProperties();
            loadQualityRanges();
            logQualityRanges();
            loadTierSpecificBonuses();
            logTierSpecificBonuses();
            loadLoreTemplates();
            loadAttributeNames();
            logAttributeNames();
            loadEligibleItems();
            loadQualityColorSettings();

            // Update all online players' items
            updateAllPlayersItems();

            sender.sendMessage(Component.text("TierTools configuration reloaded and all items updated!")
                .color(NamedTextColor.GREEN));
            return true;
        }

        return false;
    }

    private void updateAllPlayersItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerItems(player);
        }
    }

    private void updatePlayerItems(Player player) {
        // Update inventory items
        updateInventoryItems(player.getInventory());
        
        // Update armor items
        updateInventoryItems(player.getInventory().getArmorContents());
        
        // Update offhand item
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (hasTier(offhand)) {
            updateItemLore(offhand.getItemMeta());
            player.getInventory().setItemInOffHand(offhand);
        }
    }

    private void updateInventoryItems(Inventory inventory) {
        if (inventory == null) return;
        
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && hasTier(item)) {
                updateItemLore(item.getItemMeta());
                inventory.setItem(i, item);
            }
        }
    }

    private void updateInventoryItems(ItemStack[] items) {
        if (items == null) return;
        
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item != null && hasTier(item)) {
                updateItemLore(item.getItemMeta());
                items[i] = item;
            }
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item != null && hasTier(item)) {
            updateItemLore(item.getItemMeta());
            player.getInventory().setItem(event.getNewSlot(), item);
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        if (mainHand != null && hasTier(mainHand)) {
            updateItemLore(mainHand.getItemMeta());
            event.setMainHandItem(mainHand);
        }

        if (offHand != null && hasTier(offHand)) {
            updateItemLore(offHand.getItemMeta());
            event.setOffHandItem(offHand);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Update player's items when they join
        updatePlayerItems(event.getPlayer());
    }

}
