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
import org.bukkit.event.inventory.PrepareSmithingEvent;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registriert die Werkbank-/Ofen-/Schmiede-Rezepte der Mods beim Server (Namespace kaleidoscope).
 */
public class VanillaRecipes implements Listener {

    private final Plugin plugin;
    private final CookeryItems items;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public VanillaRecipes(Plugin plugin, CookeryItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public static boolean isOwn(@Nullable Recipe recipe) {
        return recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals(Keys.NAMESPACE);
    }

    public void register(Recipes recipes) {
        unregister();
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
        Bukkit.updateRecipes();
        plugin.getLogger().info(registered.size() + " Werkbank-/Ofen-Rezepte registriert");
    }

    public void unregister() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key, false);
        }
        registered.clear();
    }

    private @Nullable Recipe toBukkit(VanillaRecipe recipe) {
        NamespacedKey key = Keys.key(recipe.id().replace(':', '/'));
        ItemStack result = recipe.result().create(items);
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
            case SMITHING_TRANSFORM -> new SmithingTransformRecipe(key, result, choice(ingredients.get(0)), choice(ingredients.get(1)), choice(ingredients.get(2)), false);
        };
    }

    /**
     * Nur Vanilla-Items: MaterialChoice (beliebige Varianten erlaubt), sonst ExactChoice mit den Mod-Items.
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
            ItemStack stack = items.create(id, 1);
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
     * Schmieden (Netherit-Küchenmesser): Ergebnis neu erzeugen und nur Verzauberungen, Name und Abnutzung übernehmen –
     * sonst würde das Ergebnis Modell und ID des Diamant-Messers erben.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        Recipe recipe = event.getInventory().getRecipe();
        ItemStack base = event.getInventory().getInputEquipment();
        if (!isOwn(recipe) || base == null || event.getResult() == null) {
            return;
        }
        ItemStack result = items.create(CookeryItems.typeOf(event.getResult()), 1);
        if (result == null) {
            return;
        }
        if (base.hasData(DataComponentTypes.ENCHANTMENTS)) {
            result.setData(DataComponentTypes.ENCHANTMENTS, base.getData(DataComponentTypes.ENCHANTMENTS));
        }
        if (base.hasData(DataComponentTypes.CUSTOM_NAME)) {
            result.setData(DataComponentTypes.CUSTOM_NAME, base.getData(DataComponentTypes.CUSTOM_NAME));
        }
        if (base.getItemMeta() instanceof Damageable damaged && damaged.hasDamage()) {
            result.editMeta(Damageable.class, meta -> meta.setDamage(damaged.getDamage()));
        }
        event.setResult(result);
    }
}
