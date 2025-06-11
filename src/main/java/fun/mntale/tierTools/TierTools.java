package fun.mntale.tierTools;

import fun.mntale.tierTools.loot.LootTierManager;
import fun.mntale.tierTools.tier.TierManager;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class TierTools extends JavaPlugin implements Listener {
    private static TierTools instance;
    private TierManager tierManager;
    private final NamespacedKey tierKey;

    public TierTools() {
        this.tierKey = new NamespacedKey(this, "tier");
    }

    @Override
    public void onEnable() {
        instance = this;
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
        instance = null;
        getLogger().info("TierTools has been disabled!");
    }

    public static TierTools getInstance() {
        return instance;
    }

    public TierManager getTierManager() {
        return tierManager;
    }

    public NamespacedKey getTierKey() {
        return tierKey;
    }
}
