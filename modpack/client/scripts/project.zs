// ObsidianGate RPG modpack recipe overrides.
// Keep this file as the single CraftTweaker entry point for future progression tweaks.

import loottweaker.vanilla.loot.LootTables;

val villageBlacksmith = LootTables.getTable("minecraft:chests/village_blacksmith");
val tinkersStarter = villageBlacksmith.addPool("obsidiangate_tinkers_starter", 1, 2, 0, 0);

val woodPickHead = <tconstruct:pick_head>.withTag({Material: "wood"});
val woodAxeHead = <tconstruct:axe_head>.withTag({Material: "wood"});
val woodShovelHead = <tconstruct:shovel_head>.withTag({Material: "wood"});
val woodBinding = <tconstruct:binding>.withTag({Material: "wood"});
val woodToolRod = <tconstruct:tool_rod>.withTag({Material: "wood"});

val stonePickHead = <tconstruct:pick_head>.withTag({Material: "stone"});
val stoneAxeHead = <tconstruct:axe_head>.withTag({Material: "stone"});
val stoneShovelHead = <tconstruct:shovel_head>.withTag({Material: "stone"});
val stoneBinding = <tconstruct:binding>.withTag({Material: "stone"});

val flintPickHead = <tconstruct:pick_head>.withTag({Material: "flint"});
val flintAxeHead = <tconstruct:axe_head>.withTag({Material: "flint"});
val flintShovelHead = <tconstruct:shovel_head>.withTag({Material: "flint"});
val flintBinding = <tconstruct:binding>.withTag({Material: "flint"});

tinkersStarter.addEmptyEntry(10, "obsidiangate_tinkers_empty");
tinkersStarter.addItemEntry(<tconstruct:pattern> * 4, 20, 0, "obsidiangate_tinkers_blank_patterns");
tinkersStarter.addItemEntry(<tconstruct:tooltables:1>, 4, 0, "obsidiangate_tinkers_stencil_table");
tinkersStarter.addItemEntry(<tconstruct:tooltables:2>, 4, 0, "obsidiangate_tinkers_part_builder");
tinkersStarter.addItemEntry(<tconstruct:tooltables:3>, 3, 0, "obsidiangate_tinkers_tool_station");

tinkersStarter.addItemEntry(woodPickHead, 10, 0, "obsidiangate_tinkers_wood_pick_head");
tinkersStarter.addItemEntry(woodAxeHead, 8, 0, "obsidiangate_tinkers_wood_axe_head");
tinkersStarter.addItemEntry(woodShovelHead, 8, 0, "obsidiangate_tinkers_wood_shovel_head");
tinkersStarter.addItemEntry(woodBinding, 10, 0, "obsidiangate_tinkers_wood_binding");
tinkersStarter.addItemEntry(woodToolRod, 12, 0, "obsidiangate_tinkers_wood_tool_rod");

tinkersStarter.addItemEntry(stonePickHead, 8, 0, "obsidiangate_tinkers_stone_pick_head");
tinkersStarter.addItemEntry(stoneAxeHead, 6, 0, "obsidiangate_tinkers_stone_axe_head");
tinkersStarter.addItemEntry(stoneShovelHead, 6, 0, "obsidiangate_tinkers_stone_shovel_head");
tinkersStarter.addItemEntry(stoneBinding, 8, 0, "obsidiangate_tinkers_stone_binding");

tinkersStarter.addItemEntry(flintPickHead, 6, 0, "obsidiangate_tinkers_flint_pick_head");
tinkersStarter.addItemEntry(flintAxeHead, 5, 0, "obsidiangate_tinkers_flint_axe_head");
tinkersStarter.addItemEntry(flintShovelHead, 5, 0, "obsidiangate_tinkers_flint_shovel_head");
tinkersStarter.addItemEntry(flintBinding, 6, 0, "obsidiangate_tinkers_flint_binding");
