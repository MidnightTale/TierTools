package fun.mntale.tierTools.commands;

import fun.mntale.tierTools.TierTools;
import fun.mntale.tierTools.attribute.AttributeManager;
import fun.mntale.tierTools.tier.Tier;
import fun.mntale.tierTools.tier.TierData;
import fun.mntale.tierTools.tier.TierLoreManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ReloadCommand implements CommandExecutor, TabCompleter {
    private final TierTools plugin;
    private final AttributeManager attributeManager;

    public ReloadCommand(TierTools plugin, AttributeManager attributeManager) {
        this.plugin = plugin;
        this.attributeManager = attributeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // Check permission
        if (!sender.hasPermission("tiertools.reload")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        try {
            // Reload config
            plugin.reloadConfig();
            
            // Reload attribute manager
            attributeManager.reloadConfig();
            
            // Update all online players' inventory items
            updateAllPlayersItems();
            
            // Send success message
            sender.sendMessage("§aConfiguration reloaded successfully! All items have been updated.");
            
            // Log reload
            plugin.getLogger().info("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            // Send error message
            sender.sendMessage("§cError reloading configuration: " + e.getMessage());
            
            // Log error
            plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private void updateAllPlayersItems() {
        int updatedItems = 0;
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Update inventory items
            updatedItems += updateInventoryItems(player.getInventory().getContents());
            
            // Update armor items
            updatedItems += updateInventoryItems(player.getInventory().getArmorContents());
            
            // Update off hand item
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && updateItem(offHand)) {
                updatedItems++;
            }
        }
        
        plugin.getLogger().info("Updated " + updatedItems + " items across all online players");
    }

    private int updateInventoryItems(ItemStack[] items) {
        int updated = 0;
        
        for (ItemStack item : items) {
            if (item != null && updateItem(item)) {
                updated++;
            }
        }
        
        return updated;
    }

    private boolean updateItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        TierData tierData = new TierData(plugin.getTierKey());
        String tierName = tierData.getTierName(item);
        
        if (tierName != null) {
            float quality = tierData.getQuality(item);
            Tier tier = new Tier(plugin.getConfig().getConfigurationSection("tiers." + tierName));
            
            // Update the lore
            TierLoreManager.applyTierLore(meta, tier, quality);
            item.setItemMeta(meta);
            return true;
        }
        
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        // No tab completion needed for this command
        return new ArrayList<>();
    }
} 