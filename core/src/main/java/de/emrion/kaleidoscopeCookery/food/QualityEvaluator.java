package de.emrion.kaleidoscopeCookery.food;

import de.emrion.kaleidoscopeCookery.recipe.Ingredient;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Quality rating of the "flex" recipes, 1:1 following QualityEvaluator in the mod: per recipe and world seed there is a
 * hidden ratio of the ingredient amounts. The closer the added amounts are to it (cosine similarity),
 * the better the dish.
 */
public final class QualityEvaluator {

    private static final int MAX_CAPACITY = 9;

    private QualityEvaluator() {
    }

    /**
     * @param inputs      all slots of the station (also empty ones), as in the mod
     * @param ingredients ingredients of the recipe in the order from recipes.yml
     * @param recipeId    recipe id as in the mod (e.g. kaleidoscope_cookery:flex_pot/braised_beef)
     */
    public static Quality evaluate(List<ItemStack> inputs, List<Ingredient> ingredients, String recipeId, long worldSeed) {
        if (inputs.isEmpty() || ingredients.isEmpty()) {
            return Quality.POOR;
        }
        if (ingredients.size() == 1) {
            return oneInputQuality(inputs, recipeId, worldSeed);
        }
        int[] recipeVector = randomVector(ingredients.size(), recipeId, worldSeed);
        int[] inputVector = new int[ingredients.size()];
        for (int i = 0; i < ingredients.size(); i++) {
            for (ItemStack stack : inputs) {
                if (ingredients.get(i).test(stack)) {
                    inputVector[i]++;
                }
            }
        }
        double score = finalScore(ingredients.size(), inputs.size(), inputVector, recipeVector);
        for (Quality quality : Quality.values()) {
            if (score >= quality.score()) {
                return quality;
            }
        }
        return Quality.STANDARD;
    }

    private static Quality oneInputQuality(List<ItemStack> inputs, String recipeId, long worldSeed) {
        Random random = new Random(recipeSeed(recipeId, worldSeed));
        int count = inputs.size();
        if (count <= 1) {
            return Quality.POOR;
        }
        int standard = 1 + random.nextInt(2);
        if (count <= standard) {
            return Quality.STANDARD;
        }
        int excellent = 2 + random.nextInt(2);
        if (count <= excellent) {
            return Quality.EXCELLENT;
        }
        return Quality.SUPERB;
    }

    private static int[] randomVector(int size, String recipeId, long worldSeed) {
        Random random = new Random(recipeSeed(recipeId, worldSeed));
        List<Integer> pool = new ArrayList<>(size);
        switch (size) {
            case 2 -> {
                pool.add(2 + random.nextInt(3));
                pool.add(1 + random.nextInt(2));
            }
            case 3 -> {
                pool.add(2 + random.nextInt(3));
                pool.add(1 + random.nextInt(3));
                pool.add(1 + random.nextInt(2));
            }
            default -> {
                for (int i = 0; i < size; i++) {
                    pool.add(1 + random.nextInt(2));
                }
            }
        }
        Collections.shuffle(pool, random);
        int[] vector = new int[size];
        for (int i = 0; i < size; i++) {
            vector[i] = pool.get(i);
        }
        return vector;
    }

    /**
     * worldSeed * 31 + ResourceLocation#hashCode() as in the mod.
     */
    private static long recipeSeed(String recipeId, long worldSeed) {
        int colon = recipeId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : recipeId.substring(0, colon);
        String path = recipeId.substring(colon + 1);
        int hash = 31 * namespace.hashCode() + path.hashCode();
        return worldSeed * 31 + hash;
    }

    private static double finalScore(int size, int inputSize, int[] inputs, int[] recipe) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < size; i++) {
            dot += (double) inputs[i] * recipe[i];
            normA += (double) inputs[i] * inputs[i];
            normB += (double) recipe[i] * recipe[i];
        }
        double cosine = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        double similarity = Math.pow(cosine, 4);
        double quantityFactor = 0.8 + 0.2 * (inputSize / (double) MAX_CAPACITY);
        return Math.clamp(similarity * quantityFactor, 0.0, 1.0);
    }
}
