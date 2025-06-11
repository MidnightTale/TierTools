package fun.mntale.tierTools.tier;

import fun.mntale.tierTools.TierTools;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TierUtils {
    private static final Random random = new Random();

    public static String formatAttributeName(String attrName) {
        // Convert from snake_case to Title Case
        String[] words = attrName.split("_");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
            }
        }
        
        return result.toString().trim();
    }

    public static Tier getRandomTier() {
        List<Tier> tiers = new ArrayList<>();
        float totalChance = 0;

        // First pass: collect all tiers and calculate total chance
        for (String tierKey : TierTools.getInstance().getConfig().getConfigurationSection("tiers").getKeys(false)) {
            ConfigurationSection tierSection = TierTools.getInstance().getConfig().getConfigurationSection("tiers." + tierKey);
            if (tierSection != null) {
                Tier tier = new Tier(tierSection);
                tiers.add(tier);
                totalChance += tier.getChance();
            }
        }

        // Second pass: select random tier based on chances
        float randomValue = random.nextFloat() * totalChance;
        float currentSum = 0;

        for (Tier tier : tiers) {
            currentSum += tier.getChance();
            if (randomValue <= currentSum) {
                return tier;
            }
        }

        // Fallback to first tier if something goes wrong
        return tiers.get(0);
    }

    public static float generateQuality(float minQuality, float maxQuality) {
        return minQuality + (random.nextFloat() * (maxQuality - minQuality));
    }
} 