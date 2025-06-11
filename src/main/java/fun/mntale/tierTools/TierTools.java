package fun.mntale.tierTools;

import fun.mntale.tierTools.loot.LootTierManager;
import fun.mntale.tierTools.tier.TierManager;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class TierTools extends JavaPlugin implements Listener {
    private TierManager tierManager;
    private final NamespacedKey tierKey;

    public TierTools() {
        this.tierKey = new NamespacedKey(this, "tier");
    }

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Initialize tier system
        tierManager = new TierManager(this);
        
        // Initialize loot tier system
        LootTierManager lootTierManager = new LootTierManager(this);
        getServer().getPluginManager().registerEvents(lootTierManager, this);
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("TierTools has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TierTools has been disabled!");
    }

    public TierManager getTierManager() {
        return tierManager;
    }

    public NamespacedKey getTierKey() {
        return tierKey;
    }
}
