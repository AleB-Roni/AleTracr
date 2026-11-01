package com.aleb.aletracr.food

/** Units a spoken amount can have. */
enum class FoodUnit(val defaultGrams: Double, vararg val words: String) {
    GRAM(1.0, "g", "gramm", "gr", "grams"),
    KILO(1000.0, "kg", "kilo", "kilogramm"),
    ML(1.0, "ml", "milliliter"),
    LITER(1000.0, "l", "liter"),
    PIECE(100.0, "stück", "stk", "stücke", "stückchen"),
    SLICE(30.0, "scheibe", "scheiben", "scheibchen"),
    PORTION(150.0, "portion", "portionen"),
    CUP(200.0, "tasse", "tassen", "kaffeetasse"),
    GLASS(200.0, "glas", "gläser", "glaser"),
    POT(150.0, "becher"),
    CAN(330.0, "dose", "dosen"),
    BOTTLE(500.0, "flasche", "flaschen"),
    TBSP(15.0, "el", "esslöffel", "löffel"),
    TSP(5.0, "tl", "teelöffel"),
    HANDFUL(30.0, "handvoll", "hand"),
    PLATE(300.0, "teller"),
    BOWL(250.0, "schüssel", "schale", "schälchen"),
    BAR(50.0, "riegel"),
    SCOOP(50.0, "kugel", "kugeln"),
    PACK(100.0, "packung", "packungen", "tüte", "tüten"),
    CUBE(4.0, "würfel"),
}

/**
 * A common food with typical nutrition values per 100 g (rounded values from
 * common German/European nutrition tables) and typical unit weights.
 */
data class CommonFood(
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugar: Double = 0.0,
    val fiber: Double = 0.0,
    /** Grams for e.g. PIECE/SLICE for this food; otherwise [FoodUnit.defaultGrams] is used. */
    val units: Map<FoodUnit, Double> = emptyMap(),
    val aliases: List<String> = emptyList(),
    /** Unit assumed when only a count is said ("2 Äpfel") or nothing at all ("Apfel"). */
    val defaultUnit: FoodUnit = FoodUnit.PIECE,
    val isWater: Boolean = false,
)

private fun f(
    name: String, kcal: Number, p: Number, c: Number, fat: Number, sugar: Number = 0, fiber: Number = 0,
    piece: Number? = null, slice: Number? = null, default: FoodUnit = FoodUnit.PIECE,
    aliases: List<String> = emptyList(), extra: Map<FoodUnit, Double> = emptyMap(),
): CommonFood {
    val units = buildMap {
        piece?.let { put(FoodUnit.PIECE, it.toDouble()) }
        slice?.let { put(FoodUnit.SLICE, it.toDouble()) }
        putAll(extra)
    }
    return CommonFood(name, kcal.toDouble(), p.toDouble(), c.toDouble(), fat.toDouble(), sugar.toDouble(), fiber.toDouble(), units, aliases, default)
}

object CommonFoods {
    val all: List<CommonFood> = listOf(
        // Obst
        f("Apfel", 52, 0.3, 14, 0.2, 10, 2.4, piece = 180, slice = 15),
        f("Banane", 89, 1.1, 23, 0.3, 12, 2.6, piece = 120),
        f("Birne", 57, 0.4, 15, 0.1, 10, 3.1, piece = 180),
        f("Orange", 47, 0.9, 12, 0.1, 9, 2.4, piece = 200, aliases = listOf("apfelsine")),
        f("Mandarine", 53, 0.8, 13, 0.3, 11, 1.8, piece = 70, aliases = listOf("clementine", "klementine")),
        f("Kiwi", 61, 1.1, 15, 0.5, 9, 3, piece = 75),
        f("Erdbeeren", 32, 0.7, 7.7, 0.3, 4.9, 2, piece = 12, default = FoodUnit.PORTION, aliases = listOf("erdbeere")),
        f("Weintrauben", 69, 0.7, 18, 0.2, 16, 0.9, piece = 5, default = FoodUnit.PORTION, aliases = listOf("trauben", "traube", "weintraube")),
        f("Heidelbeeren", 57, 0.7, 14, 0.3, 10, 2.4, default = FoodUnit.PORTION, aliases = listOf("blaubeeren", "blaubeere", "heidelbeere")),
        f("Himbeeren", 52, 1.2, 12, 0.7, 4.4, 6.5, default = FoodUnit.PORTION, aliases = listOf("himbeere")),
        f("Ananas", 50, 0.5, 13, 0.1, 10, 1.4, slice = 80, default = FoodUnit.PORTION),
        f("Mango", 60, 0.8, 15, 0.4, 14, 1.6, piece = 300),
        f("Pfirsich", 39, 0.9, 10, 0.3, 8, 1.5, piece = 150),
        f("Nektarine", 44, 1.1, 11, 0.3, 8, 1.7, piece = 150),
        f("Pflaume", 46, 0.7, 11, 0.3, 10, 1.4, piece = 35, aliases = listOf("zwetschge", "zwetschke")),
        f("Kirschen", 63, 1.1, 16, 0.2, 13, 2.1, piece = 8, default = FoodUnit.PORTION, aliases = listOf("kirsche")),
        f("Wassermelone", 30, 0.6, 8, 0.2, 6, 0.4, slice = 300, aliases = listOf("melone")),
        f("Avocado", 160, 2, 8.5, 15, 0.7, 6.7, piece = 150),
        f("Datteln", 282, 2.5, 75, 0.4, 63, 8, piece = 8, aliases = listOf("dattel")),
        f("Rosinen", 299, 3.1, 79, 0.5, 59, 3.7, default = FoodUnit.HANDFUL),
        // Gemüse
        f("Gurke", 15, 0.7, 3.6, 0.1, 1.7, 0.5, piece = 400, slice = 8, aliases = listOf("salatgurke")),
        f("Tomate", 18, 0.9, 3.9, 0.2, 2.6, 1.2, piece = 100, slice = 15),
        f("Cherrytomaten", 18, 0.9, 3.9, 0.2, 2.6, 1.2, piece = 15, aliases = listOf("cherrytomate", "kirschtomaten", "kirschtomate")),
        f("Paprika", 31, 1, 6, 0.3, 4.2, 2.1, piece = 160),
        f("Karotte", 41, 0.9, 10, 0.2, 4.7, 2.8, piece = 80, aliases = listOf("möhre", "mohre", "karotten", "möhren", "rüebli")),
        f("Brokkoli", 34, 2.8, 7, 0.4, 1.7, 2.6, default = FoodUnit.PORTION, aliases = listOf("broccoli")),
        f("Blumenkohl", 25, 1.9, 5, 0.3, 1.9, 2, default = FoodUnit.PORTION),
        f("Zucchini", 17, 1.2, 3.1, 0.3, 2.5, 1, piece = 250),
        f("Spinat", 23, 2.9, 3.6, 0.4, 0.4, 2.2, default = FoodUnit.PORTION),
        f("Salat", 15, 1.4, 2.9, 0.2, 0.8, 1.3, default = FoodUnit.PORTION, aliases = listOf("kopfsalat", "eisbergsalat", "blattsalat", "rucola")),
        f("Zwiebel", 40, 1.1, 9, 0.1, 4.2, 1.7, piece = 80),
        f("Champignons", 22, 3.1, 3.3, 0.3, 2, 1, piece = 15, default = FoodUnit.PORTION, aliases = listOf("pilze", "champignon")),
        f("Mais", 86, 3.3, 19, 1.4, 6, 2.7, default = FoodUnit.PORTION),
        f("Erbsen", 81, 5.4, 14, 0.4, 5.7, 5.1, default = FoodUnit.PORTION),
        f("Kartoffeln", 86, 1.9, 20, 0.1, 0.9, 1.8, piece = 100, aliases = listOf("kartoffel", "salzkartoffeln", "pellkartoffeln")),
        f("Pommes", 312, 3.4, 41, 15, 0.3, 3.8, default = FoodUnit.PORTION, aliases = listOf("pommes frites", "fritten")),
        f("Kartoffelsalat", 150, 2, 14, 9.5, 1.5, 1.5, default = FoodUnit.PORTION),
        // Brot & Getreide
        f("Brötchen", 270, 9, 52, 2.5, 3, 3, piece = 55, aliases = listOf("semmel", "schrippe", "weckle", "weck", "weizenbrötchen")),
        f("Vollkornbrötchen", 245, 10, 43, 3, 3, 6.5, piece = 60, aliases = listOf("körnerbrötchen", "dinkelbrötchen")),
        f("Brot", 240, 7.6, 46, 1.5, 2.5, 5, slice = 45, default = FoodUnit.SLICE, aliases = listOf("mischbrot", "graubrot", "bauernbrot")),
        f("Vollkornbrot", 220, 7.5, 39, 1.6, 3, 8, slice = 50, default = FoodUnit.SLICE, aliases = listOf("schwarzbrot", "roggenbrot", "körnerbrot", "dinkelbrot")),
        f("Toast", 265, 8, 48, 4, 5, 3, slice = 25, default = FoodUnit.SLICE, aliases = listOf("toastbrot", "sandwich")),
        f("Brezel", 276, 7.7, 56, 1.8, 1, 2.4, piece = 80, aliases = listOf("breze", "brezn", "laugenbrezel")),
        f("Croissant", 406, 8, 45, 21, 11, 2.5, piece = 60),
        f("Haferflocken", 372, 13.5, 59, 7, 1.2, 10, default = FoodUnit.PORTION, extra = mapOf(FoodUnit.PORTION to 50.0), aliases = listOf("hafer", "porridge", "haferbrei")),
        f("Müsli", 370, 9, 64, 7, 16, 8, default = FoodUnit.PORTION, extra = mapOf(FoodUnit.PORTION to 60.0), aliases = listOf("musli", "granola")),
        f("Cornflakes", 378, 7, 84, 0.9, 8, 3, default = FoodUnit.PORTION, extra = mapOf(FoodUnit.PORTION to 40.0)),
        f("Nudeln", 150, 5.3, 29, 0.9, 0.6, 1.8, default = FoodUnit.PORTION, extra = mapOf(FoodUnit.PORTION to 250.0), aliases = listOf("pasta", "spaghetti", "penne", "fusilli", "tagliatelle")),
        f("Reis", 130, 2.7, 28, 0.3, 0.1, 0.4, default = FoodUnit.PORTION, extra = mapOf(FoodUnit.PORTION to 200.0)),
        f("Pizza", 266, 11, 33, 10, 3.6, 2.3, piece = 350, slice = 110),
        f("Döner", 215, 12, 20, 10, 2, 1.5, piece = 400, aliases = listOf("döner kebab", "kebab", "doner")),
        f("Burger", 254, 13, 30, 9, 5, 1.5, piece = 220, aliases = listOf("hamburger", "cheeseburger")),
        f("Wrap", 240, 10, 28, 9, 2.5, 2, piece = 220),
        // Milchprodukte & Eier
        f("Milch", 64, 3.4, 4.8, 3.5, 4.8, 0, default = FoodUnit.GLASS, aliases = listOf("vollmilch")),
        f("Joghurt", 61, 3.5, 4.7, 3.3, 4.7, 0, default = FoodUnit.POT, aliases = listOf("naturjoghurt", "jogurt", "yoghurt")),
        f("Skyr", 63, 11, 4, 0.2, 4, 0, default = FoodUnit.POT),
        f("Quark", 67, 12, 4, 0.3, 4, 0, default = FoodUnit.POT, extra = mapOf(FoodUnit.POT to 250.0), aliases = listOf("magerquark")),
        f("Käse", 356, 25, 0, 27.7, 0, 0, slice = 20, default = FoodUnit.SLICE, aliases = listOf("gouda", "edamer", "emmentaler", "kase")),
        f("Mozzarella", 254, 18, 1, 19, 1, 0, piece = 125),
        f("Feta", 264, 14, 4, 21, 4, 0, default = FoodUnit.PORTION, extra = mapOf(FoodUnit.PORTION to 50.0), aliases = listOf("schafskäse")),
        f("Frischkäse", 250, 6, 4, 23, 3.5, 0, default = FoodUnit.TBSP, extra = mapOf(FoodUnit.TBSP to 20.0)),
        f("Butter", 741, 0.7, 0.6, 83, 0.6, 0, default = FoodUnit.TSP, extra = mapOf(FoodUnit.TSP to 5.0, FoodUnit.TBSP to 10.0, FoodUnit.PORTION to 10.0)),
        f("Ei", 155, 13, 1.1, 11, 1.1, 0, piece = 60, aliases = listOf("eier", "gekochtes ei", "frühstücksei")),
        f("Spiegelei", 196, 13.6, 0.8, 15, 0.8, 0, piece = 60, aliases = listOf("spiegeleier")),
        f("Rührei", 149, 10, 1.6, 11, 1.6, 0, default = FoodUnit.PORTION, extra = mapOf(FoodUnit.PORTION to 120.0)),
        f("Sahne", 292, 2.4, 3.2, 30, 3.2, 0, default = FoodUnit.TBSP),
        // Fleisch & Fisch
        f("Hähnchenbrust", 110, 23, 0, 1.5, piece = 150, aliases = listOf("hähnchen", "hühnchen", "huhn", "hühnerbrust", "chicken", "hahnchen")),
        f("Schnitzel", 230, 18, 12, 12, piece = 180, aliases = listOf("wiener schnitzel")),
        f("Bratwurst", 297, 14, 1, 26, piece = 100, aliases = listOf("rostbratwurst", "currywurst")),
        f("Würstchen", 270, 13, 1, 24, piece = 50, aliases = listOf("wiener", "wienerle", "frankfurter", "wurstchen")),
        f("Salami", 400, 22, 1, 34, slice = 8, default = FoodUnit.SLICE),
        f("Schinken", 115, 19, 1, 4, slice = 25, default = FoodUnit.SLICE, aliases = listOf("kochschinken")),
        f("Hackfleisch", 250, 18, 0, 20, default = FoodUnit.PORTION, aliases = listOf("hack")),
        f("Frikadelle", 250, 15, 9, 17, piece = 90, aliases = listOf("bulette", "fleischpflanzerl", "frikadellen")),
        f("Lachs", 208, 20, 0, 13.4, piece = 125),
        f("Thunfisch", 116, 26, 0, 1, default = FoodUnit.CAN, extra = mapOf(FoodUnit.CAN to 140.0)),
        // Süßes & Snacks
        f("Schokolade", 535, 7.6, 57, 30, 50, 3.4, piece = 6, aliases = listOf("schoki", "schokolode")),
        f("Schokoriegel", 480, 5, 62, 23, 50, 1.5, piece = 50, aliases = listOf("snickers", "mars", "twix", "kitkat")),
        f("Gummibärchen", 343, 6.9, 77, 0.1, 46, 0, piece = 2, default = FoodUnit.HANDFUL, aliases = listOf("gummibären", "haribo", "fruchtgummi")),
        f("Chips", 536, 6.5, 50, 34, 1, 4, default = FoodUnit.HANDFUL, aliases = listOf("kartoffelchips")),
        f("Kekse", 480, 6, 66, 21, 30, 2, piece = 10, aliases = listOf("keks", "plätzchen", "cookies", "cookie")),
        f("Kuchen", 350, 5, 45, 16, 28, 1.5, slice = 100, piece = 100, default = FoodUnit.SLICE, aliases = listOf("torte", "käsekuchen", "apfelkuchen")),
        f("Eis", 207, 3.5, 24, 11, 21, 0.5, default = FoodUnit.SCOOP, aliases = listOf("eiscreme", "speiseeis", "eiskugel")),
        f("Nüsse", 607, 20, 21, 54, 4.5, 7, default = FoodUnit.HANDFUL, aliases = listOf("nussmischung", "studentenfutter", "walnüsse", "cashews")),
        f("Mandeln", 579, 21, 22, 50, 4.4, 12.5, piece = 1.2, default = FoodUnit.HANDFUL, aliases = listOf("mandel")),
        f("Erdnüsse", 567, 26, 16, 49, 4, 8.5, default = FoodUnit.HANDFUL, aliases = listOf("erdnuss")),
        f("Erdnussbutter", 588, 25, 20, 50, 9, 6, default = FoodUnit.TBSP),
        f("Nutella", 539, 6.3, 57.5, 30.9, 56, 3.4, default = FoodUnit.TSP, extra = mapOf(FoodUnit.TSP to 10.0, FoodUnit.TBSP to 20.0), aliases = listOf("nusscreme", "nussnougatcreme")),
        f("Marmelade", 250, 0.4, 60, 0.1, 55, 1, default = FoodUnit.TSP, extra = mapOf(FoodUnit.TSP to 10.0, FoodUnit.TBSP to 20.0), aliases = listOf("konfitüre", "gelee")),
        f("Honig", 304, 0.3, 82, 0, 82, 0, default = FoodUnit.TSP, extra = mapOf(FoodUnit.TSP to 10.0, FoodUnit.TBSP to 20.0)),
        f("Zucker", 400, 0, 100, 0, 100, 0, default = FoodUnit.TSP, extra = mapOf(FoodUnit.TSP to 4.0, FoodUnit.CUBE to 3.0)),
        f("Müsliriegel", 400, 6, 65, 13, 25, 5, piece = 25, aliases = listOf("proteinriegel", "energieriegel")),
        f("Popcorn", 375, 11, 74, 4, 1, 15, default = FoodUnit.HANDFUL),
        // Getränke
        f("Kaffee", 2, 0.1, 0, 0, default = FoodUnit.CUP, aliases = listOf("espresso", "americano", "filterkaffee")),
        f("Cappuccino", 40, 2.3, 3.4, 2, 3.4, 0, default = FoodUnit.CUP),
        f("Latte Macchiato", 50, 2.6, 4, 2.6, 4, 0, default = FoodUnit.GLASS, extra = mapOf(FoodUnit.GLASS to 300.0), aliases = listOf("latte", "milchkaffee", "flat white")),
        f("Tee", 1, 0, 0.2, 0, default = FoodUnit.CUP, extra = mapOf(FoodUnit.CUP to 250.0)),
        f("Cola", 42, 0, 10.6, 0, 10.6, 0, default = FoodUnit.GLASS, extra = mapOf(FoodUnit.GLASS to 300.0), aliases = listOf("coca cola", "pepsi", "limo", "limonade", "fanta", "sprite")),
        f("Cola Zero", 0.3, 0, 0, 0, default = FoodUnit.GLASS, extra = mapOf(FoodUnit.GLASS to 300.0), aliases = listOf("cola light", "coke zero")),
        f("Orangensaft", 45, 0.7, 10, 0.2, 8.4, 0.2, default = FoodUnit.GLASS, aliases = listOf("o-saft", "osaft", "saft")),
        f("Apfelschorle", 24, 0, 5.5, 0, 5.5, 0, default = FoodUnit.GLASS, extra = mapOf(FoodUnit.GLASS to 300.0), aliases = listOf("schorle", "apfelsaftschorle")),
        f("Apfelsaft", 46, 0.1, 11, 0.1, 10, 0.2, default = FoodUnit.GLASS),
        f("Bier", 43, 0.5, 3.6, 0, 0, 0, default = FoodUnit.GLASS, extra = mapOf(FoodUnit.GLASS to 500.0), aliases = listOf("pils", "weizen", "radler")),
        f("Wein", 83, 0.1, 2.6, 0, 1, 0, default = FoodUnit.GLASS, aliases = listOf("rotwein", "weißwein", "weisswein", "sekt")),
        f("Smoothie", 55, 0.6, 12, 0.3, 11, 1.5, default = FoodUnit.BOTTLE, extra = mapOf(FoodUnit.BOTTLE to 250.0)),
        f("Energy Drink", 45, 0, 11, 0, 11, 0, default = FoodUnit.CAN, extra = mapOf(FoodUnit.CAN to 250.0), aliases = listOf("red bull", "energydrink", "monster")),
        CommonFood("Wasser", 0.0, 0.0, 0.0, 0.0, defaultUnit = FoodUnit.GLASS, aliases = listOf("mineralwasser", "sprudel", "leitungswasser"), isWater = true),
        // Sonstiges
        f("Olivenöl", 884, 0, 0, 100, default = FoodUnit.TBSP, extra = mapOf(FoodUnit.TBSP to 10.0, FoodUnit.TSP to 4.0), aliases = listOf("öl", "rapsöl", "speiseöl")),
        f("Ketchup", 112, 1.2, 26, 0.1, 22, 0.3, default = FoodUnit.TBSP),
        f("Mayonnaise", 680, 1, 1.5, 75, 1, 0, default = FoodUnit.TBSP, aliases = listOf("mayo")),
        f("Hummus", 166, 8, 14, 9.6, 0.3, 6, default = FoodUnit.TBSP, extra = mapOf(FoodUnit.TBSP to 20.0)),
        f("Tofu", 144, 15, 2.8, 8.7, 0.6, 2.3, default = FoodUnit.PORTION, extra = mapOf(FoodUnit.PORTION to 100.0)),
        f("Linsen", 116, 9, 20, 0.4, 1.8, 8, default = FoodUnit.PORTION, extra = mapOf(FoodUnit.PORTION to 200.0)),
    )
}
