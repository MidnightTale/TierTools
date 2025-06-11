package fun.mntale.tierTools.tier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

public class Tier {
    private final String name;
    private final int level;
    private final float chance;
    private final String color;
    private final float minQuality;
    private final float maxQuality;
    private final List<String> permissions;
    private final Random random;

    public Tier(ConfigurationSection config) {
        this.name = config.getString("name", "Unknown");
        this.level = config.getInt("level", 1);
        this.chance = (float) config.getDouble("chance", 100.0);
        this.color = config.getString("color", "&f");
        this.minQuality = (float) config.getDouble("min-quality", 0.0);
        this.maxQuality = (float) config.getDouble("max-quality", 100.0);
        this.permissions = config.getStringList("permissions");
        this.random = new Random();
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public float getChance() {
        return chance;
    }

    public String getColor() {
        return color;
    }

    public float getMinQuality() {
        return minQuality;
    }

    public float getMaxQuality() {
        return maxQuality;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public float generateQuality() {
        return minQuality + (random.nextFloat() * (maxQuality - minQuality));
    }

    public Component getDisplayName() {
        return MiniMessage.miniMessage().deserialize(color + name);
    }

    @Override
    public @NotNull String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tier tier = (Tier) o;
        return level == tier.level &&
                Float.compare(tier.chance, chance) == 0 &&
                name.equals(tier.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + level;
        result = 31 * result + Float.floatToIntBits(chance);
        return result;
    }
} 