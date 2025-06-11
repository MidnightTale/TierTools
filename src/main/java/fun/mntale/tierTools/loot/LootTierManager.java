package fun.mntale.tierTools.loot;

import fun.mntale.tierTools.TierTools;
import fun.mntale.tierTools.tier.TierData;
import fun.mntale.tierTools.tier.TierManager;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;

import java.util.Set;

public class LootTierManager implements Listener {
    private final TierTools plugin;
    private final TierManager tierManager;
    private final Set<Material> tierableItems;

    public LootTierManager(TierTools plugin) {
        this.plugin = plugin;
        this.tierManager = plugin.getTierManager();
        this.tierableItems = tierManager.getTierableItems();
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        FoliaScheduler.getRegionScheduler().run(plugin, event.getPlayer().getLocation(), (task) -> {
            // Check if the inventory holder is a Lootable container
            if (!(event.getInventory().getHolder() instanceof Lootable)) {
                return;
            }

            Player player = (Player) event.getPlayer();
            Inventory inventory = event.getInventory();
            TierData tierData = new TierData(plugin.getTierKey());

            // Check each item in the inventory
            for (ItemStack item : inventory.getContents()) {
                if (item == null || !tierableItems.contains(item.getType())) {
                    continue;
                }

                // Skip if item already has a tier
                if (tierData.hasTier(item)) {
                    continue;
                }

                // Apply tier to the item
                tierManager.applyTierToItem(item);
                plugin.getLogger().info("Applied tier to " + item.getType() + " in " +
                        inventory.getType() + " opened by " + player.getName());
            }
        });
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        FoliaScheduler.getEntityScheduler().run(event.getEntity(), plugin, (task) -> {
            ItemStack item = event.getEntity().getItemStack();
            if (!tierableItems.contains(item.getType())) {
                return;
            }

            TierData tierData = new TierData(plugin.getTierKey());
            if (tierData.hasTier(item)) {
                return;
            }

            // Apply tier to the spawned item
            tierManager.applyTierToItem(item);
            plugin.getLogger().info("Applied tier to spawned " + item.getType());
        },null);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        FoliaScheduler.getEntityScheduler().run(event.getEntity(), plugin, (task) -> {
            TierData tierData = new TierData(plugin.getTierKey());

            // Check each dropped item
            for (ItemStack item : event.getDrops()) {
                if (item == null || !tierableItems.contains(item.getType())) {
                    continue;
                }

                // Skip if item already has a tier
                if (tierData.hasTier(item)) {
                    continue;
                }

                // Apply tier to the item
                tierManager.applyTierToItem(item);
                plugin.getLogger().info("Applied tier to " + item.getType() + " dropped by " + event.getEntityType());
            }
        },null);
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {

        FoliaScheduler.getEntityScheduler().run(event.getWhoClicked(), plugin, (task) -> {
            ItemStack result = event.getRecipe().getResult();
            if (!tierableItems.contains(result.getType())) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            TierData tierData = new TierData(plugin.getTierKey());

            // Apply tier to the crafted result after it's created
            ItemStack modifiedResult = tierManager.applyTierToItem(result.clone());
            event.getInventory().setResult(modifiedResult);
            plugin.getLogger().info("Applied tier to crafted result " + result.getType() + " for " + player.getName());

            // Then scan inventory for any other tierable items that might have been crafted
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || !tierableItems.contains(item.getType()) || tierData.hasTier(item)) {
                    continue;
                }

                // Apply tier to the item
                tierManager.applyTierToItem(item);
                plugin.getLogger().info("Applied tier to crafted " + item.getType() + " for " + player.getName());
            }
        },null);
    }
} 