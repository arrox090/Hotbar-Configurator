package net.arrox090.hotbarconfigurator.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.arrox090.hotbarconfigurator.HotbarConfigurator;
import net.arrox090.hotbarconfigurator.config.HotbarConfiguratorConfig;
import net.arrox090.hotbarconfigurator.util.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.tuple.Pair;

public class Helper {

  protected enum ItemType {
    TOOL, ARMOR, FOOD
  }

  private static final Map<String, TagKey<Item>> TAG_CACHE = new HashMap<>();
  private static final Map<String, Set<String>> CUSTOM_TAG_CACHE = new HashMap<>();

  /**
   * Loads custom client-side tags defined in this mod's resources (e.g.
   * /data/hotbarconfigurator/tags/item/travel_blocks.json). Needed because
   * client-only mods do not have their data packs loaded by servers.
   */
  public static Set<String> getCustomTagItems(String tagString) {
    // If that tag was already checked...
    if (CUSTOM_TAG_CACHE.containsKey(tagString)) {
      // ...returned the cached value
      return CUSTOM_TAG_CACHE.get(tagString);
    }

    // if the tag starts with #, cut it off
    String cleanId = tagString.startsWith("#") ? tagString.substring(1) : tagString;

    String[] parts = cleanId.split(":");

    // If there aren't exactly two parts...
    if (parts.length != 2) {
      // ...its invalid syntax, cache as empty
      CUSTOM_TAG_CACHE.put(tagString, Collections.emptySet());
      return Collections.emptySet();
    }

    // Its in the standard directory where server side tags live in, find it
    // there
    String resourcePath = "/data/" + parts[0] + "/tags/item/" + parts[1] + ".json";
    Set<String> items = new HashSet<>();

    // Read and parse the tag JSON file bundled inside the mod's JAR
    try (InputStream in = Helper.class.getResourceAsStream(resourcePath)) {

      // If the file exists...
      if (in != null) {
        // ...get the json contents of the file
        JsonObject root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();

        // and the values (item IDs)
        JsonArray values = root.getAsJsonArray("values");

        // If there are any values...
        if (values != null) {
          // ...loop through each of them and save to items
          for (JsonElement e : values) {
            items.add(e.getAsString());
          }
        }
      } else {
        HotbarConfigurator.LOGGER.warn("Custom tag file not found: {}",
            resourcePath);
      }
    } catch (Exception ex) {
      HotbarConfigurator.LOGGER.warn("Failed to load custom tag {}: {}",
          tagString, ex.getMessage());
    }

    // Save the cache and return the items found
    CUSTOM_TAG_CACHE.put(tagString, items);
    return items;
  }

  /**
   * Retrieves a TagKey<Item> for the given tag string (e.g.,
   * "#minecraft:swords"). Caches the result so Identifier.tryParse and
   * TagKey.of only run once per tag.
   */
  public static TagKey<Item> getItemTag(String tagString) {

    // If that tag was already checked...
    if (TAG_CACHE.containsKey(tagString)) {
      // ...returned the cached value
      return TAG_CACHE.get(tagString);
    }

    // if the tag starts with #, cut it off
    String cleanId = tagString.startsWith("#") ? tagString.substring(1) : tagString;

    // Try to parse the tag (it'll return null if there is no such tag).
    Identifier tagId = Identifier.tryParse(cleanId);

    // If it matched a tag, retrieve it
    TagKey<Item> tag = (tagId != null) ? TagKey.of(RegistryKeys.ITEM, tagId) : null;

    // Cache the result
    TAG_CACHE.put(tagString, tag);

    // And return it
    return tag;
  }

  /**
   * Checks if a stack matches an itemId (e.g. "minecraft:iron_sword") or a tag
   * (e.g.
   * "#minecraft:swords").
   */
  public static boolean stackMatchesIdOrTag(String idToCheck, ItemStack stack) {

    // if it is empty, it means the syntax was smth like
    // '|minecraft:fire_resistance' meaning that it should match any item with
    // that effect. And here only the id is checked
    if (idToCheck.isBlank()) {
      return true;
    }

    String stackId = Registries.ITEM.getId(stack.getItem()).toString();

    // If its a tag, not id...
    if (idToCheck.startsWith("#")) {

      // 1. Food: Any item with a FoodComponent is automatically food (including
      // modded food)
      if (idToCheck.equals("#hotbarconfigurator:food") &&
          stack.contains(DataComponentTypes.FOOD)) {
        return true;
      }

      // 2. Bundled mod tags: Check client-side resource JSONs
      if (idToCheck.startsWith("#hotbarconfigurator:")) {
        Set<String> customTag = getCustomTagItems(idToCheck);
        if (customTag != null && customTag.contains(stackId)) {
          return true;
        }
      }

      // 3. Server/vanilla tags: Check Minecraft synchronized tags (e.g.
      // #minecraft:swords)
      TagKey<Item> tag = getItemTag(idToCheck);

      // Return if matched a tag and the stack is in that tag
      return tag != null && stack.isIn(tag);
    }

    // If instead its an itemId, return if the idToCheck is that exact id
    return idToCheck.equals(stackId);
  }

  /**
   * @param bypassConfig if true does not check config if it should compare
   *                     specific parts, just compares everythin
   * @return boolean of if the new item is better than the equipped one
   */
  protected static boolean checkIfBetterItem(ItemStack newItem, ItemStack equippedItem, String newItemId,
      String equippedItemId, boolean bypassConfig) {
    boolean betterItem = false;
    HotbarConfiguratorConfig config = ConfigManager.getCachedConfig();

    // if they are both the same tools/armor run the is better tool/armor
    // methods accordingly
    if (areTheSameTool(newItem, equippedItem) &&
        (bypassConfig || config.replaceWeakerItems.tools)) {
      betterItem = isBetterTool(newItem, equippedItem);
    } else if (areTheSameArmor(newItem, equippedItem) &&
        (bypassConfig || config.replaceWeakerItems.armor)) {
      betterItem = isBetterArmor(newItem, equippedItem);
    }

    // If they are just the same item do checks based on what item is that
    else if (newItemId.equals(equippedItemId)) {

      // here basically just the itemtype will change from tool to armor, and
      // enchantments
      if (newItemId.equals("minecraft:elytra") &&
          (bypassConfig || config.replaceWeakerItems.armor)) {
        betterItem = isBetterItem(newItem, equippedItem, ItemType.ARMOR, 0,
            new ArrayList<>(), new ArrayList<>());
      } else if ((newItemId.equals("minecraft:shield") ||
          newItemId.equals("minecraft:flint_and_steel")) &&
          (bypassConfig || config.replaceWeakerItems.tools)) {
        betterItem = isBetterItem(newItem, equippedItem, ItemType.TOOL, 0,
            new ArrayList<>(), new ArrayList<>());
      } else if (newItemId.equals("minecraft:shears") &&
          (bypassConfig || config.replaceWeakerItems.tools)) {
        betterItem = isBetterItem(newItem, equippedItem, ItemType.TOOL, 0,
            List.of(Enchantments.EFFICIENCY), new ArrayList<>());
      } else if (newItemId.equals("minecraft:bow") &&
          (bypassConfig || config.replaceWeakerItems.tools)) {
        betterItem = isBetterItem(newItem, equippedItem, ItemType.TOOL, 0,
            List.of(Enchantments.POWER, Enchantments.INFINITY,
                Enchantments.FLAME),
            List.of(Enchantments.PUNCH));
      } else if (newItemId.equals("minecraft:crossbow") &&
          (bypassConfig || config.replaceWeakerItems.tools)) {
        betterItem = isBetterItem(newItem, equippedItem, ItemType.TOOL, 0,
            List.of(Enchantments.QUICK_CHARGE,
                Enchantments.MULTISHOT, Enchantments.PIERCING),
            new ArrayList<>());
      } else if (newItemId.equals("minecraft:trident") &&
          (bypassConfig || config.replaceWeakerItems.tools)) {
        betterItem = isBetterItem(
            newItem, equippedItem, ItemType.TOOL, 0,
            List.of(Enchantments.LOYALTY, Enchantments.RIPTIDE,
                Enchantments.IMPALING, Enchantments.CHANNELING),
            new ArrayList<>());
      } else if (newItemId.equals("minecraft:mace") &&
          (bypassConfig || config.replaceWeakerItems.tools)) {
        betterItem = isBetterItem(
            newItem, equippedItem, ItemType.TOOL, 0,
            List.of(Enchantments.DENSITY, Enchantments.WIND_BURST,
                Enchantments.BREACH, Enchantments.SMITE,
                Enchantments.BANE_OF_ARTHROPODS, Enchantments.FIRE_ASPECT),
            new ArrayList<>());
      } else if (newItemId.equals("minecraft:fishing_rod") &&
          (bypassConfig || config.replaceWeakerItems.tools)) {
        betterItem = isBetterItem(
            newItem, equippedItem, ItemType.TOOL, 0,
            List.of(Enchantments.LUCK_OF_THE_SEA, Enchantments.LURE),
            new ArrayList<>());

        // Only here check for food comparison as its one food that can be the
        // same but actually different (effects)
      } else if (newItemId.equals("minecraft:suspicious_stew") &&
          (bypassConfig || config.replaceWeakerItems.food)) {
        betterItem = isBetterFood(
            newItem.getItem().getComponents().get(DataComponentTypes.FOOD),
            equippedItem.getItem().getComponents().get(
                DataComponentTypes.FOOD));
      }

      // If the items are not the same tool/armor or just same id...
    } else {

      // ...try to get food component from the items (will return null if its
      // not a food)
      FoodComponent newFood = newItem.getItem().getComponents().get(DataComponentTypes.FOOD);
      FoodComponent equippedFood = equippedItem.getItem().getComponents().get(DataComponentTypes.FOOD);

      // If both are foods, compare them
      if ((bypassConfig || config.replaceWeakerItems.food) && newFood != null &&
          equippedFood != null) {

        // If golden apples shouldnt replace other food, check if there thats
        // the case
        // this one is not bypassed by bypassConfig as an exception probably
        // what player would want either way
        if (!config.replaceWeakerItems.allowGoldenApplesToReplaceFood) {
          boolean isNewApple = newItemId.equals("minecraft:golden_apple") ||
              newItemId.equals("minecraft:enchanted_golden_apple");
          boolean isOldApple = equippedItemId.equals("minecraft:golden_apple") ||
              equippedItemId.equals("minecraft:enchanted_golden_apple");

          // If the new item is a golden apple, but we are currently holding
          // normal food, dont swap
          if (isNewApple && !isOldApple) {
            return false;
          }
        }

        betterItem = isBetterFood(newFood, equippedFood);
      }
    }

    return betterItem;
  }

  /**
   * @return if both tools are either both swords, both axes, ... or not
   */
  private static boolean areTheSameTool(ItemStack tool1, ItemStack tool2) {
    return (tool1.isIn(ItemTags.SWORDS) && tool2.isIn(ItemTags.SWORDS)) ||
        (tool1.isIn(ItemTags.AXES) && tool2.isIn(ItemTags.AXES)) ||
        (tool1.isIn(ItemTags.PICKAXES) && tool2.isIn(ItemTags.PICKAXES)) ||
        (tool1.isIn(ItemTags.SHOVELS) && tool2.isIn(ItemTags.SHOVELS)) ||
        (tool1.isIn(ItemTags.HOES) && tool2.isIn(ItemTags.HOES));
  }

  /**
   * @return if both armor types are either both head_armor, both chest_armor,
   *         ... or not
   */
  private static boolean areTheSameArmor(ItemStack armor1, ItemStack armor2) {
    return (armor1.isIn(ItemTags.HEAD_ARMOR) &&
        armor2.isIn(ItemTags.HEAD_ARMOR)) ||
        (armor1.isIn(ItemTags.CHEST_ARMOR) &&
            armor2.isIn(ItemTags.CHEST_ARMOR))
        ||
        (armor1.isIn(ItemTags.LEG_ARMOR) && armor2.isIn(ItemTags.LEG_ARMOR)) ||
        (armor1.isIn(ItemTags.FOOT_ARMOR) && armor2.isIn(ItemTags.FOOT_ARMOR));
  }

  /**
   * @return if newItem is better than equippedItem
   */
  protected static boolean isBetterItem(ItemStack newItem, ItemStack equippedItem, ItemType itemType,
      int strongerMaterial,
      List<RegistryKey<Enchantment>> beforeMendingAndUnbreakingEnchantmentsCheck,
      List<RegistryKey<Enchantment>> afterMendingAndUnbreakingEnchantmentsCheck) {

    // Get durability by substracting how much the tool/armor was damaged from
    // max durability
    int newItemDurability = newItem.getMaxDamage() - newItem.getDamage();
    int equippedItemDurability = equippedItem.getMaxDamage() - equippedItem.getDamage();

    // get the minimum durability set by the user in the settings
    int minDurability = switch (itemType) {
      case TOOL ->
        ConfigManager.getCachedConfig().replaceWeakerItems.minToolDurability;
      case ARMOR ->
        ConfigManager.getCachedConfig().replaceWeakerItems.minArmorDurability;
      default -> 0;
    };

    // If new durability is more than minimum and equipped is lower then
    // immediately return new is a better item (true)
    if (newItemDurability > minDurability &&
        equippedItemDurability <= minDurability) {
      return true;
    }

    // Reverse check if new item is below minimum durability then return that
    // equipped item is better (false)
    if (newItemDurability <= minDurability &&
        equippedItemDurability > minDurability) {
      return false;
    }

    // If there is a stronger material just return that the item with better
    // material is the better one
    if (strongerMaterial != 0) {
      return strongerMaterial == 1;
    }

    ItemEnchantmentsComponent newItemEnchantments = newItem.getEnchantments();
    ItemEnchantmentsComponent equippedItemEnchantments = equippedItem.getEnchantments();

    // make allEnchantments list, which holds all enchantments in the correct
    // order (with highest priority down to the ones with lowest)
    List<RegistryKey<Enchantment>> allEnchantments = new ArrayList<>(
        beforeMendingAndUnbreakingEnchantmentsCheck.size() + 2 +
            afterMendingAndUnbreakingEnchantmentsCheck.size());
    allEnchantments.addAll(beforeMendingAndUnbreakingEnchantmentsCheck);
    allEnchantments.add(Enchantments.MENDING);
    allEnchantments.add(Enchantments.UNBREAKING);
    allEnchantments.addAll(afterMendingAndUnbreakingEnchantmentsCheck);

    // If enchantments are not equal return the one with better enchantments
    int newItemHasBetterEnchantments = hasBetterEnchantments(
        newItemEnchantments, equippedItemEnchantments, allEnchantments);
    if (newItemHasBetterEnchantments != 0) {
      return newItemHasBetterEnchantments == 1;
    }

    // If they are equal in all the above, check which one has more enchantments
    // and return one with higher amount of them
    if (newItemEnchantments.getSize() > equippedItemEnchantments.getSize()) {
      return true;
    }
    if (newItemEnchantments.getSize() < equippedItemEnchantments.getSize()) {
      return false;
    }

    // If they are equal in general just return false (new one is not better)
    return false;
    // return newItemDurability > equippedItemDurability;
  }

  /**
   * @return if the newTool is better than the equippedTool
   */
  private static boolean isBetterTool(ItemStack newToolItemStack,
      ItemStack equippedToolItemStack) {
    // Convert ItemStack variables to ToolItem
    ToolItem newTool = (ToolItem) newToolItemStack.getItem();
    ToolItem equippedTool = (ToolItem) equippedToolItemStack.getItem();

    // Get the material from which the tools are made
    ToolMaterial newToolMaterial = newTool.getMaterial();
    ToolMaterial equippedToolMaterial = equippedTool.getMaterial();

    // Check stronger material by damage or miningspeed
    int strongerMaterial;
    if (newToolMaterial.getAttackDamage() > equippedToolMaterial.getAttackDamage() ||
        newToolMaterial.getMiningSpeedMultiplier() > equippedToolMaterial.getMiningSpeedMultiplier()) {
      strongerMaterial = 1;
    } else if (newToolMaterial.getAttackDamage() < equippedToolMaterial.getAttackDamage() ||
        newToolMaterial.getMiningSpeedMultiplier() < equippedToolMaterial.getMiningSpeedMultiplier()) {
      strongerMaterial = -1;
    } else {
      strongerMaterial = 0;
    }

    List<RegistryKey<Enchantment>> beforeMendingAndUnbreakingEnchantmentsCheck;
    List<RegistryKey<Enchantment>> afterMendingAndUnbreakingEnchantmentsCheck;

    // Add valid enchantments to compare based on what type of tool it is
    // ranking better (beforeMending...) and worse (afterMending...)
    if (newTool instanceof SwordItem) {
      beforeMendingAndUnbreakingEnchantmentsCheck = List.of(Enchantments.SHARPNESS, Enchantments.LOOTING,
          Enchantments.FIRE_ASPECT, Enchantments.SWEEPING_EDGE);
      afterMendingAndUnbreakingEnchantmentsCheck = List.of(Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
          Enchantments.KNOCKBACK);
    } else if (newTool instanceof AxeItem) {
      if (ConfigManager.getCachedConfig().treatAxeAsAWeapon) {
        beforeMendingAndUnbreakingEnchantmentsCheck = List.of(Enchantments.SHARPNESS, Enchantments.EFFICIENCY,
            Enchantments.FORTUNE, Enchantments.SILK_TOUCH);
        afterMendingAndUnbreakingEnchantmentsCheck = new ArrayList<>();
      } else {
        beforeMendingAndUnbreakingEnchantmentsCheck = List.of(Enchantments.EFFICIENCY, Enchantments.FORTUNE,
            Enchantments.SILK_TOUCH);
        afterMendingAndUnbreakingEnchantmentsCheck = List.of(Enchantments.SHARPNESS);
      }
    } else {
      beforeMendingAndUnbreakingEnchantmentsCheck = List.of(Enchantments.EFFICIENCY, Enchantments.FORTUNE,
          Enchantments.SILK_TOUCH);
      afterMendingAndUnbreakingEnchantmentsCheck = new ArrayList<>();
    }

    // With the information gathered run and return the isBetterItem to finalize
    // the comparison
    return isBetterItem(newToolItemStack, equippedToolItemStack, ItemType.TOOL,
        strongerMaterial,
        beforeMendingAndUnbreakingEnchantmentsCheck,
        afterMendingAndUnbreakingEnchantmentsCheck);
  }

  /**
   * @return if the newArmor is better than the equippedArmor
   */
  protected static boolean isBetterArmor(ItemStack newArmorItemStack,
      ItemStack equippedArmorItemStack) {
    // Check if armors have curse of binding. if the level is not set, the value
    // will be 0, as the getEnchantmentLevels returns int array
    boolean newArmorHasCurseOfBinding = getEnchantmentLevels(newArmorItemStack.getEnchantments(),
        List.of(Enchantments.BINDING_CURSE))[0] != 0;
    boolean equippedArmorHasCurseOfBinding = getEnchantmentLevels(equippedArmorItemStack.getEnchantments(),
        List.of(Enchantments.BINDING_CURSE))[0] != 0;

    // if any have curse of binding, then either you cant swap them or dont want
    // to as then itll get stuck on you, so return false
    if (newArmorHasCurseOfBinding || equippedArmorHasCurseOfBinding)
      return false;

    ArmorItem newArmor = (ArmorItem) newArmorItemStack.getItem();
    ArmorItem equippedArmor = (ArmorItem) equippedArmorItemStack.getItem();

    // Check stronger material by comparing protection / toughness
    int strongerMaterial;
    if (newArmor.getProtection() > equippedArmor.getProtection() ||
        newArmor.getToughness() > equippedArmor.getToughness()) {
      strongerMaterial = 1;
    } else if (newArmor.getProtection() < equippedArmor.getProtection() ||
        newArmor.getToughness() < equippedArmor.getToughness()) {
      strongerMaterial = -1;
    } else {
      strongerMaterial = 0;
    }

    // Just need to check newArmor as both armors are same type
    EquipmentSlot slotType = newArmor.getSlotType();
    Pair<List<RegistryKey<Enchantment>>, List<RegistryKey<Enchantment>>> enchantmentsToCompare = getArmorEnchantmentsForSlot(
        slotType);

    // finish in isBetterItem with gathered info to know which is better
    return isBetterItem(newArmorItemStack, equippedArmorItemStack,
        ItemType.ARMOR, strongerMaterial,
        enchantmentsToCompare.getLeft(),
        enchantmentsToCompare.getRight());
  }

  /**
   * @return boolean of if newfood is better than the equippedfood
   */
  protected static boolean isBetterFood(FoodComponent newFood,
      FoodComponent equippedFood) {
    // Get the scores for new and equipped food by combining scores from two
    // functions
    double newScore = baseFoodScore(newFood) + totalEffectScore(newFood);
    double equippedScore = baseFoodScore(equippedFood) + totalEffectScore(equippedFood);

    return Double.compare(newScore, equippedScore) == 1;
  }

  /**
   * @return double representation of a 'baseFoodScore' which is bacially a
   *         small calculation of combining its nutrition, saturation, and if it
   *         can
   *         always be eaten stats
   */
  private static double baseFoodScore(FoodComponent food) {
    return food.nutrition() + food.saturation() * 1.5 +
        (food.canAlwaysEat() ? 1.0 : 0.0);
  }

  /**
   * @return the sum of all status‐effect scores for one FoodComponent
   */
  private static double totalEffectScore(FoodComponent food) {
    double score = 0.0;
    for (FoodComponent.StatusEffectEntry entry : food.effects()) {
      StatusEffectInstance effect = entry.effect();
      StatusEffect statusEffect = entry.effect().getEffectType().value();
      StatusEffectCategory effectCategory = statusEffect.getCategory();

      double durationSec = effect.getDuration() / 20.0; // ticks → seconds

      // weight positive vs negative
      double weight = (effectCategory == StatusEffectCategory.BENEFICIAL ? 1.0
          : effect.equals(StatusEffects.POISON) ? -3.5
              : effectCategory == StatusEffectCategory.HARMFUL
                  ? -1.5
                  : 0.0); // neutral effects ignored

      // each 5 seconds counts as one “unit”
      score += weight * (durationSec / 5.0) * (effect.getAmplifier() + 1) *
          entry.probability();
    }
    return score;
  }

  /**
   * @return 1 - item1 has better enchantments -1 - item2 has better
   *         enchantments 0 - both are equal
   */
  private static int hasBetterEnchantments(ItemEnchantmentsComponent item1,
      ItemEnchantmentsComponent item2,
      List<RegistryKey<Enchantment>> enchantments) {
    int[] newItemEnchantments = getEnchantmentLevels(item1, enchantments);
    int[] equippedItemEnchantments = getEnchantmentLevels(item2, enchantments);

    // Loop through all enchantments
    // the order is very important as the list goes from enchantments with
    // highest priority to lowest
    for (int i = 0; i < newItemEnchantments.length; i++) {
      // if any item has better enchantment levels instantly return
      // (this will also work if
      // one item does not have that enchantment thus then it would be 0 for
      // it)
      if (newItemEnchantments[i] > equippedItemEnchantments[i]) {
        return 1;
      } else if (newItemEnchantments[i] < equippedItemEnchantments[i]) {
        return -1;
      }
    }

    return 0;
  }

  /**
   * @return Array of enchantment levels for each of the enchantments to check
   *         for
   */
  protected static int[] getEnchantmentLevels(ItemEnchantmentsComponent itemEnchantments,
      List<RegistryKey<Enchantment>> enchantmentsToCheck) {
    int[] enchantmentLevels = new int[enchantmentsToCheck.size()];

    // need to call getEnchantments() again to get a set of the actual
    // enchantments
    for (RegistryEntry<Enchantment> entry : itemEnchantments.getEnchantments()) {
      int level = itemEnchantments.getLevel(entry);

      // The enchantment id ex. minecraft:sharpness
      Optional<RegistryKey<Enchantment>> enchantment = entry.getKey();

      // Check if the enchantment id is correct and exists
      if (enchantment.isPresent()) {
        for (int i = 0; i < enchantmentsToCheck.size(); i++) {
          // If we matched the enchantments...
          if (enchantment.get().equals(enchantmentsToCheck.get(i))) {
            // ...set the level for that enchantment in the enchantmentLevels
            enchantmentLevels[i] = level;
          }
        }
      }
    }
    return enchantmentLevels;
  }

  /**
   * @return a pair of lists of enchantments with higher priority and lower
   *         priority based on what armor type is the armor
   */
  private static Pair<List<RegistryKey<Enchantment>>, List<RegistryKey<Enchantment>>> getArmorEnchantmentsForSlot(
      EquipmentSlot slotType) {
    switch (slotType) {
      case HEAD -> {
        return Pair.of(
            List.of(Enchantments.PROTECTION, Enchantments.BLAST_PROTECTION,
                Enchantments.PROJECTILE_PROTECTION,
                Enchantments.FIRE_PROTECTION, Enchantments.RESPIRATION,
                Enchantments.AQUA_AFFINITY),
            List.of(Enchantments.THORNS));
      }
      case CHEST -> {
        return Pair.of(List.of(Enchantments.PROTECTION,
            Enchantments.BLAST_PROTECTION,
            Enchantments.PROJECTILE_PROTECTION,
            Enchantments.FIRE_PROTECTION),
            List.of(Enchantments.THORNS));
      }
      case LEGS -> {
        return Pair.of(
            List.of(Enchantments.PROTECTION, Enchantments.BLAST_PROTECTION,
                Enchantments.PROJECTILE_PROTECTION,
                Enchantments.FIRE_PROTECTION, Enchantments.SWIFT_SNEAK),
            List.of(Enchantments.THORNS));
      }
      case FEET -> {
        return Pair.of(
            List.of(Enchantments.PROTECTION, Enchantments.BLAST_PROTECTION,
                Enchantments.PROJECTILE_PROTECTION,
                Enchantments.FIRE_PROTECTION, Enchantments.FEATHER_FALLING,
                Enchantments.DEPTH_STRIDER),
            List.of(Enchantments.THORNS, Enchantments.SOUL_SPEED,
                Enchantments.FROST_WALKER));
      }
      default -> {
        return Pair.of(new ArrayList<>(), new ArrayList<>());
      }
    }
  }

  /**
   * @return true if stack has required enchantments else false
   */
  protected static boolean stackHasEnchantments(ItemStack stack, List<String> requiredEnchantments) {
    // Grab all enchantments from the stack
    ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);

    if (enchantments.isEmpty() && !requiredEnchantments.isEmpty()) {
      return false;
    }

    for (String requiredEnchantment : requiredEnchantments) {
      Pair<String, Integer> enchantmentLevelPair = getEnchantmentLevelPair(requiredEnchantment);

      boolean found = false;

      // If its blank, then assume its a typo
      if (enchantmentLevelPair.getLeft().isBlank()) {
        found = true;
        continue;
      }

      for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {

        // If id of the enchantment can be found and exists
        if (entry.getKey().isPresent()) {
          String enchantment = entry.getKey().get().getValue().toString();
          int level = enchantments.getLevel(entry);

          if (enchantment.equals(enchantmentLevelPair.getLeft()) &&
              level >= enchantmentLevelPair.getRight()) {
            found = true;
          }
        }
      }
      // If the enchantment was not found iterating through all item
      // enchantments, return false
      if (!found) {
        return false;
      }
    }

    // If did no return false means it found every enchantment so return true
    return true;
  }

  /**
   * Parses an entry like "minecraft:sharpness:5" into
   * [id="minecraft:sharpness", level=5], or if there’s no trailing number,
   * returns level=1.
   *
   * @return pair of enchantment id and its level
   */
  private static Pair<String, Integer> getEnchantmentLevelPair(String entry) {
    // No enchantment, assume a typo, return blank
    if (entry == null || entry.isBlank()) {
      return Pair.of("", 1);
    }

    // Strip the entry from any leading or trailing spaces
    entry = entry.strip();

    char last = entry.charAt(entry.length() - 1);

    // If last char is a digit, assume there’s a ":<level>" suffix
    if (Character.isDigit(last)) {
      int colon = entry.lastIndexOf(':');

      // Ensure there really was a colon before that digit
      if (colon > 0) {
        String idPart = entry.substring(0, colon);
        String lvlPart = entry.substring(colon + 1);

        // Try parsing the integer level and return it
        try {
          int lvl = Integer.parseInt(lvlPart);
          return Pair.of(idPart, lvl);
        } catch (NumberFormatException e) {
          HotbarConfigurator.LOGGER.warn("Invalid enchantment level: {}",
              lvlPart);
        }
      }
    }
    // No trailing number → default level 1
    return Pair.of(entry, 1);
  }

  /**
   * @return true if potion has any effect from effects list else false
   */
  protected static boolean potionHasEffect(ItemStack stack,
      List<String> effects) {
    // If its not a potion return false
    if (!(stack.getItem() instanceof PotionItem)) {
      return false;
    }

    // Get the list of StatusEffectInstances on this potion
    PotionContentsComponent contents = stack.getComponents().get(DataComponentTypes.POTION_CONTENTS);

    // If no effects, return false
    if (contents == null) {
      return false;
    }

    for (StatusEffectInstance instance : contents.getEffects()) {
      String current_effect = instance.getEffectType().getIdAsString();
      for (String effect : effects) {
        String effect_to_check = current_effect;

        // If found a matching effect, return true
        if (effect != null && effect_to_check.equals(effect.strip())) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Searches the player's inventory for a stack that is identical to the
   * provided item
   *
   * @param playerInventory The inventory to search through
   * @param newStack        The exact item stack you are trying to find a match
   *                        for
   * @param forbiddenSlots  A set of local slot indices (0-35) to ignore (ex.
   *                        skipping the hotbar)
   * @param maxCountAllowed If false, ignores stacks that are already at their
   *                        maximum size
   * @return The local index (0-35) of the matching stack, or -1 if no match was
   *         found
   */
  protected static int indexOf(PlayerInventory playerInventory,
      ItemStack newStack, Set<Integer> forbiddenSlots,
      boolean maxCountAllowed) {
    for (int slotIndex = 0; slotIndex < playerInventory.main.size(); slotIndex++) {

      // Skip if in forbiddenSlots
      if (forbiddenSlots.contains(slotIndex)) {
        continue;
      }

      // Check if the item exists, is an exact match (including components ex.
      // enchantments), is not damaged (cant stack damaged items either way),
      // and is not the exact same physical object
      ItemStack targetStack = playerInventory.main.get(slotIndex);
      if (!targetStack.isEmpty() &&
          ItemStack.areItemsAndComponentsEqual(newStack, targetStack) &&
          !targetStack.isDamaged() && newStack != targetStack) {

        // Skip the stack if maxCountAllowed is false and the stack is full
        if (!maxCountAllowed &&
            targetStack.getCount() == targetStack.getMaxCount())
          continue;

        return slotIndex;
      }
    }

    return -1;
  }

  /**
   * Swap items in a ScreenHandler from specificed slot id to another
   * specificed slot id
   */
  protected static void swapItems(ScreenHandler screenHandler, int from,
      int to) {
    MinecraftClient client = MinecraftClient.getInstance();

    if (client.player == null || client.interactionManager == null) {
      HotbarConfigurator.LOGGER.warn(
          "Tried swapping items for null player or interactionManager");
      return;
    }

    // 3 step PICKUP and the 0 = left mouse click.
    // so first pick up item to move
    client.interactionManager.clickSlot(screenHandler.syncId, from, 0,
        SlotActionType.PICKUP, client.player);
    // then left click the slot to change to, to swap them
    client.interactionManager.clickSlot(screenHandler.syncId, to, 0,
        SlotActionType.PICKUP, client.player);
    // and finally put the item we swapped with to the old slot of item we moved
    client.interactionManager.clickSlot(screenHandler.syncId, from, 0,
        SlotActionType.PICKUP, client.player);
    // displaySwapInfo(from, to);
  }

  protected static void swapToHotbar(ScreenHandler screenHandler, int from,
      int to) {
    MinecraftClient client = MinecraftClient.getInstance();

    if (client.player == null || client.interactionManager == null) {
      HotbarConfigurator.LOGGER.warn(
          "Tried swapping items for null player or interactionManager");
      return;
    }

    // Use swap action to swap from the slot we want to move from to a hotbar
    // slot using 'to' as the index of hotbar (since swap simulates clicking ex.
    // number 3 to swap to third slot on hotbar)
    client.interactionManager.clickSlot(screenHandler.syncId, from, to,
        SlotActionType.SWAP, client.player);
    // displaySwapInfo(from, to);
  }

  /**
   * Show the user a message saying what swapped from what (more just for
   * debugging)
   */
  public static void displaySwapInfo(int from, int to) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player != null) {
      client.player.sendMessage(
          Text.literal("Swapped from " + from + " to " + to),
          /* actionBar= */ true);
    }
  }

  /**
   * @return converted local index (0-35) of player inventory into an Absolute
   *         Screen syncId
   */
  public static int getAbsoluteId(ScreenHandler screenHandler, int localIndex) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player == null)
      return -1;

    // Map through every slot in the screen handler
    for (Slot slot : screenHandler.slots) {
      // if its the player inventory not ex. the crafting 2x2 grid, then check
      // if the local index is the current slot index (0-35 where 0-8 are
      // hotbar)...
      if (slot.inventory == client.player.getInventory() &&
          slot.getIndex() == localIndex) {
        // ...and set the absolute 'global' id of that slot
        return slot.id;
      }
    }
    return -1;
  }

  /**
   * Processes string entry from slot settings
   *
   * @param entry A string of itemId/tag followed by optional enchantment/potion
   *              separated with '|'
   * @return Array with parsed itemId, and enchantments/potion effects if there
   *         are any
   */
  protected static String[] processSlotEntry(String entry) {

    // if entry is empty return null, else it would match everything
    if (entry == null || entry.isBlank()) {
      return null;
    }

    // Split the entry by '|' (the separator for itemId and enchantments/potion
    // effects)
    String[] parts = entry.split("\\|");
    String itemId;
    String enchantments;

    // If there was '|' in the entry, there are two parts: 1. itemId, 2.
    // enchantments/potion effects
    if (parts.length == 2) {
      itemId = parts[0].strip();
      enchantments = parts[1].strip();

      // if no '|' then only one part so no enchantments/potion effects
    } else if (parts.length == 1) {
      itemId = parts[0].strip();
      enchantments = "";
    } else {
      HotbarConfigurator.LOGGER.warn(
          "Malformed slot entry input: '{}'. Expected 'id|enchantments'",
          entry);
      return null;
    }

    return new String[] { itemId, enchantments };
  }
}
