package de.emrion.kaleidoscopeCookery.recipe;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers the crafting/furnace/smithing recipes of the mods with the server (namespace kaleidoscope).
 */
public class VanillaRecipes implements Listener {

    private final Plugin plugin;
    private final List<NamespacedKey> registered = new ArrayList<>();
    private final java.util.Map<NamespacedKey, Ingredient> smithingBases = new java.util.HashMap<>();
    /**
     * Placeholder recipes "1 to 9 arbitrary mod items" (shapeless, registered last): only apply if no more specific
     * recipe matches, e.g. for mod items with quality or stored data that no ExactChoice matches.
     * The resolvers determine the result (rice bowls, resetting written recipes, addons).
     */
    private static final String ANY_PREFIX = "any_items_";
    private final List<CraftingResolver> resolvers = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Result for mod items that only match the placeholder recipes.
     */
    @FunctionalInterface
    public interface CraftingResolver {
        /**
         * @param inputs occupied slots of the crafting table (mod items only)
         * @return result, or null if the resolver is not responsible
         */
        @Nullable ItemStack resolve(List<ItemStack> inputs);
    }

    /**
     * Register a resolver (addons, e.g. the skewer recipe book in KaleidoscopeGrilling).
     */
    public void addResolver(CraftingResolver resolver) {
        resolvers.add(resolver);
    }

    public static boolean isAnyItemsRecipe(@Nullable Recipe recipe) {
        return recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals(Keys.NAMESPACE) && keyed.getKey().getKey().startsWith(ANY_PREFIX);
    }
    private @Nullable Recipes recipes;

    public VanillaRecipes(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Always the current items (addons add their own).
     */
    private CookeryItems items() {
        return de.emrion.kaleidoscopeCookery.KaleidoscopeCookery.getInstance().getItems();
    }

    public static boolean isOwn(@Nullable Recipe recipe) {
        return recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals(Keys.NAMESPACE);
    }

    public void register(Recipes recipes) {
        unregister();
        this.recipes = recipes;
        for (VanillaRecipe recipe : recipes.vanilla()) {
            try {
                Recipe bukkit = toBukkit(recipe);
                if (bukkit != null && Bukkit.addRecipe(bukkit, false)) {
                    registered.add(((Keyed) bukkit).getKey());
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Rezept " + recipe.id() + " konnte nicht registriert werden: " + e.getMessage());
            }
        }
        // Register last so that more specific recipes match first
        ItemStack icon = ItemStack.of(CookeryItems.BASE_MATERIAL);
        for (int count = 1; count <= 9; count++) {
            NamespacedKey key = Keys.key(ANY_PREFIX + count);
            ShapelessRecipe any = new ShapelessRecipe(key, icon);
            for (int i = 0; i < count; i++) {
                any.addIngredient(new RecipeChoice.MaterialChoice(CookeryItems.BASE_MATERIAL));
            }
            if (Bukkit.addRecipe(any, false)) {
                registered.add(key);
            }
        }
        Bukkit.updateRecipes();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.discoverRecipes(discoverable());
        }
        plugin.getLogger().info(registered.size() + " Werkbank-/Ofen-Rezepte registriert");
    }

    /**
     * Plugin recipes only show up in the recipe book once the player knows them.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(discoverable());
    }

    private List<NamespacedKey> discoverable() {
        return registered.stream().filter(key -> !key.getKey().startsWith(ANY_PREFIX)).toList();
    }

    /**
     * Placeholder recipe: result from the resolvers, otherwise none.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnyItems(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (!isAnyItemsRecipe(event.getRecipe())) {
            return;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (stack != null && !stack.isEmpty()) {
                inputs.add(stack);
            }
        }
        ItemStack result = riceBowl(inputs);
        if (result == null) {
            result = resetRecipe(inputs);
        }
        for (CraftingResolver resolver : resolvers) {
            if (result != null) {
                break;
            }
            result = resolver.resolve(inputs);
        }
        event.getInventory().setResult(result);
    }

    /**
     * A written recipe alone in the crafting table: empty again (reset_recipe_item; no ExactChoice matches items with data).
     */
    private @Nullable ItemStack resetRecipe(List<ItemStack> inputs) {
        if (inputs.size() != 1 || !de.emrion.kaleidoscopeCookery.item.RecipeItems.ITEM.equals(CookeryItems.idOf(inputs.getFirst()))) {
            return null;
        }
        return items().create(de.emrion.kaleidoscopeCookery.item.RecipeItems.ITEM, 1);
    }

    /**
     * RiceBowlRecipe#matches/assemble: exactly one ingredient and exactly one cooked rice, best quality onto the result.
     */
    private @Nullable ItemStack riceBowl(List<ItemStack> inputs) {
        if (recipes == null) {
            return null;
        }
        ItemStack result = null;
        if (inputs.size() == 2) {
            for (RiceBowlRecipe recipe : recipes.riceBowl()) {
                boolean firstRice = recipes.cookedRice().test(inputs.get(0)) && recipe.ingredient().test(inputs.get(1));
                boolean secondRice = recipes.cookedRice().test(inputs.get(1)) && recipe.ingredient().test(inputs.get(0));
                if (firstRice || secondRice) {
                    result = recipe.result().create(items());
                    break;
                }
            }
        }
        if (result != null) {
            de.emrion.kaleidoscopeCookery.food.Quality best = null;
            for (ItemStack input : inputs) {
                de.emrion.kaleidoscopeCookery.food.Quality quality = de.emrion.kaleidoscopeCookery.food.FoodService.quality(input);
                if (quality != null && (best == null || quality.score() > best.score())) {
                    best = quality;
                }
            }
            if (best != null && plugin instanceof de.emrion.kaleidoscopeCookery.KaleidoscopeCookery cookery) {
                cookery.getFoodService().setQuality(result, best);
            }
        }
        return result;
    }

    public void unregister() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key, false);
        }
        registered.clear();
        smithingBases.clear();
    }

    private @Nullable Recipe toBukkit(VanillaRecipe recipe) {
        NamespacedKey key = Keys.key(recipe.id().replace(':', '/'));
        ItemStack result = recipe.result().create(items());
        if (result == null) {
            throw new IllegalArgumentException("unbekanntes Ergebnis " + recipe.result().item());
        }
        List<Ingredient> ingredients = recipe.ingredients();
        return switch (recipe.type()) {
            case CRAFTING_SHAPED -> {
                ShapedRecipe shaped = new ShapedRecipe(key, result);
                shaped.shape(recipe.pattern().toArray(String[]::new));
                for (Map.Entry<Character, Ingredient> entry : recipe.key().entrySet()) {
                    shaped.setIngredient(entry.getKey(), choice(entry.getValue()));
                }
                yield shaped;
            }
            case CRAFTING_SHAPELESS -> {
                ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
                for (Ingredient ingredient : ingredients) {
                    shapeless.addIngredient(choice(ingredient));
                }
                yield shapeless;
            }
            case SMELTING -> new FurnaceRecipe(key, result, choice(ingredients.getFirst()), recipe.experience(), recipe.time());
            case SMOKING -> new SmokingRecipe(key, result, choice(ingredients.getFirst()), recipe.experience(), recipe.time());
            case BLASTING -> new BlastingRecipe(key, result, choice(ingredients.getFirst()), recipe.experience(), recipe.time());
            case CAMPFIRE_COOKING -> new CampfireRecipe(key, result, choice(ingredients.getFirst()), recipe.experience(), recipe.time());
            case STONECUTTING -> new StonecuttingRecipe(key, result, choice(ingredients.getFirst()));
            case SMITHING_TRANSFORM -> {
                // Base by material (enchanted/damaged knives do not match exactly), checked in onPrepareSmithing
                smithingBases.put(key, ingredients.get(1));
                yield new SmithingTransformRecipe(key, result, choice(ingredients.get(0)), materialChoice(ingredients.get(1)), choice(ingredients.get(2)), false);
            }
        };
    }

    private RecipeChoice materialChoice(Ingredient ingredient) {
        List<Material> materials = new ArrayList<>();
        for (Key id : ingredient.items()) {
            ItemStack stack = items().create(id, 1);
            if (stack != null && !materials.contains(stack.getType())) {
                materials.add(stack.getType());
            }
        }
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("keine gültigen Items in " + ingredient.items());
        }
        return new RecipeChoice.MaterialChoice(materials);
    }

    /**
     * Vanilla items only: MaterialChoice (any variant allowed), otherwise ExactChoice with the mod items.
     */
    private RecipeChoice choice(Ingredient ingredient) {
        List<Material> materials = new ArrayList<>();
        boolean onlyVanilla = true;
        for (Key id : ingredient.items()) {
            if (!id.namespace().equals(NamespacedKey.MINECRAFT)) {
                onlyVanilla = false;
                break;
            }
            Material material = Material.matchMaterial(id.asString());
            if (material != null && material.isItem()) {
                materials.add(material);
            }
        }
        if (onlyVanilla) {
            if (materials.isEmpty()) {
                throw new IllegalArgumentException("keine gültigen Items in " + ingredient.items());
            }
            return new RecipeChoice.MaterialChoice(materials);
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Key id : ingredient.items()) {
            ItemStack stack = items().create(id, 1);
            if (stack != null) {
                stacks.add(stack);
            }
        }
        if (stacks.isEmpty()) {
            throw new IllegalArgumentException("keine gültigen Items in " + ingredient.items());
        }
        return new RecipeChoice.ExactChoice(stacks);
    }

    /**
     * Smithing (netherite kitchen knife): create the result anew and copy only enchantments, name and damage -
     * otherwise the result would inherit the diamond knife's model and id.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        Recipe recipe = event.getInventory().getRecipe();
        ItemStack base = event.getInventory().getInputEquipment();
        if (!isOwn(recipe) || base == null || event.getResult() == null) {
            return;
        }
        Ingredient expected = smithingBases.get(((Keyed) recipe).getKey());
        if (expected != null && !expected.test(base)) {
            event.setResult(null);
            return;
        }
        ItemStack result = items().create(CookeryItems.typeOf(event.getResult()), 1);
        if (result == null) {
            return;
        }
        if (base.hasData(DataComponentTypes.ENCHANTMENTS)) {
            result.setData(DataComponentTypes.ENCHANTMENTS, base.getData(DataComponentTypes.ENCHANTMENTS));
        }
        if (base.hasData(DataComponentTypes.CUSTOM_NAME)) {
            result.setData(DataComponentTypes.CUSTOM_NAME, base.getData(DataComponentTypes.CUSTOM_NAME));
        }
        Integer damage = base.getData(DataComponentTypes.DAMAGE);
        if (damage != null && damage > 0) {
            result.setData(DataComponentTypes.DAMAGE, damage);
        }
        event.setResult(result);
    }
}
