package de.emrion.kaleidoscopeCookery.cookbook;

import de.emrion.kaleidoscopeCookery.recipe.BambooTrayRecipe;
import de.emrion.kaleidoscopeCookery.recipe.ChoppingBoardRecipe;
import de.emrion.kaleidoscopeCookery.recipe.Ingredient;
import de.emrion.kaleidoscopeCookery.recipe.MillstoneRecipe;
import de.emrion.kaleidoscopeCookery.recipe.PotRecipe;
import de.emrion.kaleidoscopeCookery.recipe.RecipeResult;
import de.emrion.kaleidoscopeCookery.recipe.Recipes;
import de.emrion.kaleidoscopeCookery.recipe.RiceBowlRecipe;
import de.emrion.kaleidoscopeCookery.recipe.SteamerRecipe;
import de.emrion.kaleidoscopeCookery.recipe.StockpotRecipe;
import de.emrion.kaleidoscopeCookery.recipe.TeapotRecipe;
import de.emrion.kaleidoscopeCookery.recipe.VanillaRecipe;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alle Rezepte aus recipes.yml (und die Lagerfeuer-Rezepte für den Dönerspieß) als Kochbuch-Einträge.
 */
public final class CookbookEntries {

    private final Map<Category, List<CookbookEntry>> byCategory = new EnumMap<>(Category.class);

    public CookbookEntries(Recipes recipes) {
        for (Category category : Category.values()) {
            byCategory.put(category, new ArrayList<>());
        }
        for (PotRecipe recipe : recipes.pot()) {
            List<Component> info = new ArrayList<>();
            info.add(Lang.get("cookbook.info.oil"));
            info.add(Lang.get("cookbook.info.time", seconds(recipe.time())));
            if (recipe.stirFryCount() > 0) {
                info.add(Lang.get("cookbook.info.stir_fry", recipe.stirFryCount()));
            }
            carrier(info, recipe.carrier());
            flex(info, recipe.flex());
            add(Category.POT, recipe.id(), inputs(recipe.ingredients()), List.of(output(recipe.result())), info);
        }
        for (StockpotRecipe recipe : recipes.stockpot()) {
            List<Component> info = new ArrayList<>();
            info.add(Lang.get("cookbook.info.soup_base", soupBase(recipe.soupBase())));
            info.add(Lang.get("cookbook.info.lid"));
            info.add(Lang.get("cookbook.info.time", seconds(recipe.time())));
            carrier(info, recipe.carrier());
            flex(info, recipe.flex());
            add(Category.STOCKPOT, recipe.id(), inputs(recipe.ingredients()), List.of(output(recipe.result())), info);
        }
        for (SteamerRecipe recipe : recipes.steamer()) {
            add(Category.STEAMER, recipe.id(), List.of(new CookbookEntry.Input(recipe.ingredient(), 1)), List.of(output(recipe.result())),
                    List.of(Lang.get("cookbook.info.steamer"), Lang.get("cookbook.info.time", seconds(recipe.time()))));
        }
        for (ChoppingBoardRecipe recipe : recipes.choppingBoard()) {
            List<CookbookEntry.Output> outputs = new ArrayList<>();
            for (RecipeResult result : recipe.results()) {
                outputs.add(output(result));
            }
            add(Category.CHOPPING_BOARD, recipe.id(), List.of(new CookbookEntry.Input(recipe.ingredient(), 1)), outputs,
                    List.of(Lang.get("cookbook.info.cuts", recipe.cuts())));
        }
        for (TeapotRecipe recipe : recipes.teapot()) {
            add(Category.TEAPOT, recipe.id(), List.of(new CookbookEntry.Input(recipe.ingredient(), recipe.ingredientCount())), List.of(output(recipe.result())),
                    List.of(Lang.get("cookbook.info.tea_fluid", soupBase(recipe.fluid())), Lang.get("cookbook.info.heat"),
                            Lang.get("cookbook.info.time", seconds(recipe.time()))));
        }
        for (MillstoneRecipe recipe : recipes.millstone()) {
            List<CookbookEntry.Output> outputs = new ArrayList<>();
            for (MillstoneRecipe.Output output : recipe.results()) {
                outputs.add(new CookbookEntry.Output(output.result().item(), output.result().count(), output.chance()));
            }
            add(Category.MILLSTONE, recipe.id(), List.of(new CookbookEntry.Input(recipe.ingredient(), 1)), outputs,
                    List.of(Lang.get("cookbook.info.millstone")));
        }
        for (BambooTrayRecipe recipe : recipes.bambooTray()) {
            add(Category.BAMBOO_TRAY, recipe.id(), List.of(new CookbookEntry.Input(recipe.ingredient(), 1)), List.of(output(recipe.result())),
                    List.of(Lang.get(recipe.type() == BambooTrayRecipe.Type.DRYING ? "cookbook.info.drying" : "cookbook.info.wetting"),
                            Lang.get("cookbook.info.time", seconds(recipe.time()))));
        }
        for (RiceBowlRecipe recipe : recipes.riceBowl()) {
            add(Category.CRAFTING, recipe.id(), List.of(new CookbookEntry.Input(recipe.ingredient(), 1), new CookbookEntry.Input(recipes.cookedRice(), 1)),
                    List.of(output(recipe.result())), List.of(Lang.get("cookbook.info.shapeless"), Lang.get("cookbook.info.best_quality")),
                    shapelessGrid(List.of(recipe.ingredient(), recipes.cookedRice())));
        }
        for (VanillaRecipe recipe : recipes.vanilla()) {
            addVanilla(recipe);
        }
        addCampfire();
        for (List<CookbookEntry> entries : byCategory.values()) {
            entries.sort(Comparator.comparing(CookbookEntry::id));
        }
    }

    private void addVanilla(VanillaRecipe recipe) {
        List<Component> info = new ArrayList<>();
        Category category = Category.CRAFTING;
        List<CookbookEntry.Input> inputs;
        List<Ingredient> grid = null;
        switch (recipe.type()) {
            case CRAFTING_SHAPED -> {
                Map<Character, Integer> counts = new LinkedHashMap<>();
                for (String row : recipe.pattern()) {
                    for (char symbol : row.toCharArray()) {
                        if (symbol != ' ') {
                            counts.merge(symbol, 1, Integer::sum);
                        }
                    }
                }
                inputs = new ArrayList<>();
                for (Map.Entry<Character, Integer> entry : counts.entrySet()) {
                    Ingredient ingredient = recipe.key().get(entry.getKey());
                    if (ingredient != null) {
                        inputs.add(new CookbookEntry.Input(ingredient, entry.getValue()));
                    }
                }
                info.add(Lang.get("cookbook.info.shaped"));
                grid = new ArrayList<>(java.util.Collections.nCopies(9, null));
                for (int row = 0; row < Math.min(3, recipe.pattern().size()); row++) {
                    String line = recipe.pattern().get(row);
                    for (int column = 0; column < Math.min(3, line.length()); column++) {
                        grid.set(row * 3 + column, recipe.key().get(line.charAt(column)));
                    }
                }
            }
            case CRAFTING_SHAPELESS -> {
                inputs = inputs(recipe.ingredients());
                info.add(Lang.get("cookbook.info.shapeless"));
                grid = shapelessGrid(recipe.ingredients());
            }
            case SMITHING_TRANSFORM -> {
                inputs = inputs(recipe.ingredients());
                info.add(Lang.raw("block.minecraft.smithing_table"));
            }
            case STONECUTTING -> {
                inputs = inputs(recipe.ingredients());
                info.add(Lang.raw("block.minecraft.stonecutter"));
            }
            case CAMPFIRE_COOKING -> {
                category = Category.SHAWARMA_SPIT;
                inputs = inputs(recipe.ingredients());
                info.add(Lang.get("cookbook.info.campfire"));
                info.add(Lang.get("cookbook.info.time", seconds(recipe.time())));
            }
            default -> {
                category = Category.FURNACE;
                inputs = inputs(recipe.ingredients());
                info.add(Lang.raw(switch (recipe.type()) {
                    case SMOKING -> "block.minecraft.smoker";
                    case BLASTING -> "block.minecraft.blast_furnace";
                    default -> "block.minecraft.furnace";
                }));
                info.add(Lang.get("cookbook.info.time", seconds(recipe.time())));
            }
        }
        add(category, recipe.id(), inputs, List.of(output(recipe.result())), info, grid);
    }

    private static List<Ingredient> shapelessGrid(List<Ingredient> ingredients) {
        List<Ingredient> grid = new ArrayList<>(java.util.Collections.nCopies(9, null));
        for (int i = 0; i < Math.min(9, ingredients.size()); i++) {
            grid.set(i, ingredients.get(i));
        }
        return grid;
    }

    /**
     * Dönerspieß: alle Lagerfeuer-Rezepte des Servers (Vanilla und eigene).
     */
    private void addCampfire() {
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            if (!(iterator.next() instanceof CampfireRecipe campfire) || ((Keyed) campfire).getKey().getNamespace().equals("kaleidoscope")) {
                continue;
            }
            Set<Key> items = new java.util.LinkedHashSet<>();
            if (campfire.getInputChoice() instanceof RecipeChoice.MaterialChoice materials) {
                materials.getChoices().forEach(material -> items.add(material.key()));
            } else if (campfire.getInputChoice() instanceof RecipeChoice.ExactChoice exact) {
                exact.getChoices().forEach(stack -> items.add(stack.getType().key()));
            }
            if (items.isEmpty()) {
                continue;
            }
            add(Category.SHAWARMA_SPIT, campfire.getKey().asString(), List.of(new CookbookEntry.Input(new Ingredient(Set.copyOf(items)), 1)),
                    List.of(new CookbookEntry.Output(campfire.getResult().getType().key(), campfire.getResult().getAmount(), 1)),
                    List.of(Lang.get("cookbook.info.campfire"), Lang.get("cookbook.info.time", seconds(campfire.getCookingTime()))));
        }
    }

    private void add(Category category, String id, List<CookbookEntry.Input> inputs, List<CookbookEntry.Output> outputs, List<Component> info) {
        add(category, id, inputs, outputs, info, null);
    }

    private void add(Category category, String id, List<CookbookEntry.Input> inputs, List<CookbookEntry.Output> outputs, List<Component> info,
                     @org.jspecify.annotations.Nullable List<Ingredient> grid) {
        if (outputs.isEmpty()) {
            return;
        }
        byCategory.get(category).add(new CookbookEntry(category, id, inputs, outputs, List.copyOf(info),
                grid == null ? null : java.util.Collections.unmodifiableList(grid)));
    }

    /**
     * Gleiche Zutaten zusammenfassen (3× Tomate statt dreimal Tomate).
     */
    private static List<CookbookEntry.Input> inputs(List<Ingredient> ingredients) {
        Map<Ingredient, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            counts.merge(ingredient, 1, Integer::sum);
        }
        List<CookbookEntry.Input> result = new ArrayList<>();
        counts.forEach((ingredient, count) -> result.add(new CookbookEntry.Input(ingredient, count)));
        return result;
    }

    private static CookbookEntry.Output output(RecipeResult result) {
        return new CookbookEntry.Output(result.item(), result.count(), 1);
    }

    private static void carrier(List<Component> info, @org.jspecify.annotations.Nullable Ingredient carrier) {
        if (carrier != null && !carrier.items().isEmpty()) {
            info.add(Lang.get("cookbook.info.carrier", Cookbook.name(carrier.items().iterator().next())));
        }
    }

    private static void flex(List<Component> info, boolean flex) {
        if (flex) {
            info.add(Lang.get("cookbook.info.flex"));
        }
    }

    /**
     * Brühe bzw. Tee-Flüssigkeit als Eimer-Name (Wasser, Lava, Milch, Fischeimer ...).
     */
    private static Component soupBase(Key base) {
        String value = base.value();
        String bucket = value.endsWith("_bucket") ? value : value + "_bucket";
        return Lang.raw("item.minecraft." + bucket);
    }

    private static String seconds(int ticks) {
        double seconds = ticks / 20.0;
        return seconds == Math.floor(seconds) ? String.valueOf((int) seconds) : String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    public List<CookbookEntry> of(Category category) {
        return byCategory.get(category);
    }

    public List<CookbookEntry> all() {
        List<CookbookEntry> all = new ArrayList<>();
        byCategory.values().forEach(all::addAll);
        return all;
    }
}
