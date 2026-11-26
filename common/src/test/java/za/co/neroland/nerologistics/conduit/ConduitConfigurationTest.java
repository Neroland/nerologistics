package za.co.neroland.nerologistics.conduit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class ConduitConfigurationTest {
    @Test
    void universalDuctAndItemCellHaveDifferentCraftingGrids() throws IOException {
        JsonObject duct = recipe("universal_duct");
        JsonObject cell = recipe("item_cell_1k");
        List<List<String>> ductGrid = ingredients(duct);
        List<List<String>> cellGrid = ingredients(cell);
        //? if >=1.20.5 {
        String resultKey = "id";
        //?} else {
        /*String resultKey = "item";
        *///?}
        assertEquals("nerologistics:universal_duct", duct.getAsJsonObject("result").get(resultKey).getAsString());
        assertEquals(8, duct.getAsJsonObject("result").get("count").getAsInt());
        assertEquals("nerologistics:item_cell_1k", cell.getAsJsonObject("result").get(resultKey).getAsString());
        assertNotEquals(ductGrid, cellGrid);
        cellGrid.forEach(Collections::reverse);
        assertNotEquals(ductGrid, cellGrid);
    }

    private static JsonObject recipe(String name) throws IOException {
        String path = "/data/nerologistics/recipe";
        var current = ConduitConfigurationTest.class.getResourceAsStream(path + "/" + name + ".json");
        try (var stream = current != null ? current
                : ConduitConfigurationTest.class.getResourceAsStream(path + "s/" + name + ".json")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<List<String>> ingredients(JsonObject recipe) {
        JsonObject key = recipe.getAsJsonObject("key");
        List<List<String>> grid = new ArrayList<>();
        for (var row : recipe.getAsJsonArray("pattern")) {
            List<String> ingredients = new ArrayList<>();
            for (char symbol : row.getAsString().toCharArray()) {
                ingredients.add(key.get(String.valueOf(symbol)).toString());
            }
            grid.add(ingredients);
        }
        return grid;
    }
}
