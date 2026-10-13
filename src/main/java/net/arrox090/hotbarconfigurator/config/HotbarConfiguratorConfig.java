package net.arrox090.hotbarconfigurator.config;

import static net.arrox090.hotbarconfigurator.HotbarConfigurator.MOD_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = MOD_ID)
public class HotbarConfiguratorConfig implements ConfigData {
  // =========================================================================
  // ENUMS
  // =========================================================================

  public enum UnassignedItemBehavior {
    INVENTORY, UNASSIGNED_SLOT, DEFAULT
  }

  public enum ActiveProfile {
    PROFILE1,
    PROFILE2,
    PROFILE3;

    // Return the next profile in the enum, wrapping back to the first
    public ActiveProfile next() {
      // values() is a method every enum has that returns its values in order
      ActiveProfile[] vals = values();

      // ordinal() returns the index of the current profile
      return vals[(this.ordinal() + 1) % vals.length];
    }
  }

  // =========================================================================
  // GENERAL TAB (Variables with no category default to the main tab)
  // =========================================================================

  @Comment("Which hotbar profile is currently active.")
  public ActiveProfile activeProfile = ActiveProfile.PROFILE1;

  @Comment("Automatically switch to a profile configured for a dimension "
      + "when you enter it.")
  public boolean profileChangeOnDimensionEnter = true;

  @Comment("If true, Axes will prioritize combat enchantments (like "
      + "Sharpness) over utility enchantments (like Efficiency).")
  public boolean treatAxeAsAWeapon = false;

  @Comment("Configure which in-game actions trigger the mod to automatically "
      + "sort items.")
  @ConfigEntry.Gui.CollapsibleObject
  public EnabledScenariosSettings enabledForScenarios = new EnabledScenariosSettings();

  @Comment("Configure which items should be automatically equipped when picked up.")
  @ConfigEntry.Gui.CollapsibleObject
  public AutoEquipSettings autoEquip = new AutoEquipSettings();

  @Comment("Configure the rules for when a stronger item is allowed to "
      + "overwrite a weaker one.")
  @ConfigEntry.Gui.CollapsibleObject
  public ReplaceWeakerItemsSettings replaceWeakerItems = new ReplaceWeakerItemsSettings();

  // =========================================================================
  // ADVANCED TAB
  // =========================================================================

  @ConfigEntry.Category("advanced")
  @Comment("If true, when a sorted item replaces another item, the mod will "
      + "attempt to sort the replaced item as well.")
  public boolean allowChainSubstitutions = true;

  @ConfigEntry.Category("advanced")
  @Comment("What to do with items in the hotbar that don't match any rules.\n"
      + "DEFAULT: Normal vanilla behavior (leave the item in that slot).\n"
      + "INVENTORY: Moves the item into your main inventory.\n"
      + "UNASSIGNED_SLOT: Moves the item to a hotbar slot that has no "
      + "assigned rules (or inventory if hotbar is full).")
  public UnassignedItemBehavior unassignedItemBehavior = UnassignedItemBehavior.DEFAULT;

  @ConfigEntry.Category("advanced")
  @Comment("Rules for forcibly pushing out items that are currently taking "
      + "up the wrong slots.")
  @ConfigEntry.Gui.CollapsibleObject
  public ReplaceNotMatchingItemsSettings replaceNotMatchingItems = new ReplaceNotMatchingItemsSettings();

  // =========================================================================
  // PROFILE SETTINGS
  // =========================================================================

  // --- Profile One (Overworld Defaults) ---
  @ConfigEntry.Category("profileOne")
  @ConfigEntry.Gui.TransitiveObject
  public ProfileSettings profileOne = new ProfileSettings();

  // --- Profile Two (Nether Defaults) ---
  @ConfigEntry.Category("profileTwo")
  @ConfigEntry.Gui.TransitiveObject
  public ProfileSettings profileTwo = new ProfileSettings(
      Map.of(7,
          List.of("|minecraft:fire_resistance", "minecraft:ender_pearl",
              "minecraft:crafting_table", "minecraft:firework_rocket"),
          8, List.of("minecraft:lava_bucket", "minecraft:bucket")),
      List.of("minecraft:the_nether"));

  // --- Profile Three (End Defaults) ---
  @ConfigEntry.Category("profileThree")
  @ConfigEntry.Gui.TransitiveObject
  public ProfileSettings profileThree = new ProfileSettings(List.of("minecraft:the_end"));

  // Helper to return all profiles' settings
  public Map<ActiveProfile, ProfileSettings> getProfiles() {
    return Map.of(ActiveProfile.PROFILE1, profileOne, ActiveProfile.PROFILE2,
        profileTwo, ActiveProfile.PROFILE3, profileThree);
  }

  // =========================================================================
  // SCENARIO TOGGLES
  // =========================================================================
  public static class EnabledScenariosSettings {
    @Comment("Sort items when picking them up from the ground.")
    public boolean pickingUpItem = true;

    @Comment("Sort items when shift-clicking from your inventory.")
    public boolean quickMovePlayerInventory = true;

    @Comment("Sort items when shift-clicking out of a Crafting Table.")
    public boolean quickMoveCraftingTable = true;

    @Comment("Sort items when shift-clicking out of Chests and Barrels.")
    public boolean quickMoveGenericContainers = true;

    @Comment("Sort items when shift-clicking out of Shulker Boxes.")
    public boolean quickMoveShulkerBoxes = true;

    @Comment("Sort items when shift-clicking out of Furnaces/Smokers/Blast "
        + "Furnaces.")
    public boolean quickMoveFurnaces = true;

    @Comment("Sort items when shift-clicking out of unlisted and modded containers.")
    public boolean quickMoveOther = false;
  }

  // =========================================================================
  // REPLACEMENT RULES SETTINGS
  // =========================================================================
  public static class ReplaceNotMatchingItemsSettings {
    @Comment("If true, incoming sorted items will forcibly replace items "
        + "taking up hotbar slots if they"
        + " don't belong there.\n"
        + "If false, items will only sort into empty slots.")
    public boolean hotbar = true;

    @Comment("If true, incoming sorted items will forcibly replace the item "
        + "in your offhand if it"
        + " doesn't belong there.\n"
        + "If false, items will go to offhand if its empty.")
    public boolean offhand = true;
  }

  public static class ReplaceWeakerItemsSettings {
    @Comment("Allow stronger armor to push weaker armor out of its designated slot.")
    public boolean armor = true;

    @ConfigEntry.BoundedDiscrete(max = 1500)
    @Comment("If your equipped armor falls below this durability, any armor piece "
        + "above this durability"
        + " will instantly replace it, regardless of material or enchantments.")
    public int minArmorDurability = 10;

    @Comment("Allow stronger tools to push weaker tools out of their "
        + "designated slot.")
    public boolean tools = true;

    @ConfigEntry.BoundedDiscrete(max = 5000)
    @Comment("If your equipped tool falls below this durability, any tool "
        + "above this durability will"
        + " instantly replace it, regardless of material or enchantments.")
    public int minToolDurability = 10;

    @Comment("Allow better food (calculated via nutrition, saturation, and "
        + "status effects) to push"
        + " weaker food out of its slot.")
    public boolean food = true;

    @Comment("Allow Golden Apples to overwrite standard food items during "
        + "sorts. If false, they will"
        + " leave standard food alone.")
    public boolean allowGoldenApplesToReplaceFood = false;
  }

  // =========================================================================
  // AUTO-EQUIP SETTINGS
  // =========================================================================
  public static class AutoEquipSettings {
    @Comment("When picking up an item, try to automatically equip it.")
    public boolean armor = true;

    @Comment("When picking up a shield, try to automatically equip it to "
        + "your offhand.")
    public boolean shield = true;

    @Comment("When picking up a Totem of Undying, try to automatically equip "
        + "it to your offhand.")
    public boolean totemOfUndying = true;
  }

  // =========================================================================
  // PROFILE BLUEPRINT
  // =========================================================================
  public static class ProfileSettings {

    // -------------------- CONSTANTS (Define defaults) --------------------
    private static List<List<String>> getDefaultSlotSettings() {
      return List.of(
          List.of("#minecraft:swords"),
          List.of("#minecraft:axes", "#minecraft:shovels", "#minecraft:hoes"),
          List.of("minecraft:bow", "minecraft:crossbow"),
          List.of("#hotbarconfigurator:travel_blocks"),
          List.of("#hotbarconfigurator:food", "minecraft:golden_apple",
              "minecraft:enchanted_golden_apple"),
          List.of("minecraft:elytra"), List.of("#minecraft:pickaxes"),
          List.of("minecraft:ender_pearl", "minecraft:crafting_table",
              "minecraft:firework_rocket"),
          List.of("minecraft:water_bucket", "minecraft:bucket"));
    }

    // -------------------- HELPER METHODS --------------------
    public Map<Integer, List<String>> getSlotEntries() {
      return Map.of(0, slot1, 1, slot2, 2, slot3, 3, slot4, 4, slot5, 5, slot6,
          6, slot7, 7, slot8, 8, slot9);
    }

    public Map<Integer, Boolean> getReplaceItemsLowerOnTheList() {
      return Map.of(
          0, replaceItemsLower1, 1, replaceItemsLower2, 2, replaceItemsLower3,
          3, replaceItemsLower4, 4, replaceItemsLower5, 5, replaceItemsLower6,
          6, replaceItemsLower7, 7, replaceItemsLower8, 8, replaceItemsLower9);
    }

    private void replaceSlotSetting(int index, List<String> settings) {
      switch (index) {
        case 0:
          slot1 = settings;
          break;
        case 1:
          slot2 = settings;
          break;
        case 2:
          slot3 = settings;
          break;
        case 3:
          slot4 = settings;
          break;
        case 4:
          slot5 = settings;
          break;
        case 5:
          slot6 = settings;
          break;
        case 6:
          slot7 = settings;
          break;
        case 7:
          slot8 = settings;
          break;
        case 8:
          slot9 = settings;
          break;
      }
    }

    // -------------------- CONSTRUCTORS --------------------
    public ProfileSettings() {
      initialize(true, true, true, true, true);
    }

    public ProfileSettings(List<String> dimensions) {
      this.dimensions = new ArrayList<>(dimensions);

      initialize(true, true, false, true, true);
    }

    public ProfileSettings(Map<Integer, List<String>> slotSettings,
        List<String> dimensions) {

      for (int i = 0; i < getDefaultSlotSettings().size(); i++) {
        List<String> settings = slotSettings.getOrDefault(i, getDefaultSlotSettings().get(i));
        replaceSlotSetting(i, settings);
      }

      this.dimensions = new ArrayList<>(dimensions);

      initialize(false, true, false, true, true);
    }

    // -------------------- DEFAULT SETTINGS INITIALIZATION --------------------
    /**
     * Initializes the default configuration values for a profile. Each
     * parameter acts as a flag to determine whether its corresponding category
     * of settings should be populated with default values.
     *
     * @param slotSettings                    if true, populates slots 1-9 with
     *                                        default item
     *                                        tags/IDs.
     * @param replaceItemsLowerSettings       if true, sets the default behavior for
     *                                        whether lower list items replace
     *                                        higher ones.
     * @param dimensionsSettings              if true, sets the default
     *                                        auto-selected
     *                                        dimension (e.g., Overworld).
     * @param refillOnSortSetting             if true, enables automatic refilling
     *                                        from
     *                                        inventory during sorts.
     * @param replaceWeakerItemsOnSortSetting if true, initializes the option to
     *                                        replace weaker items during sorting.
     */
    private void initialize(boolean slotSettings,
        boolean replaceItemsLowerSettings,
        boolean dimensionsSettings,
        boolean refillOnSortSetting,
        boolean replaceWeakerItemsOnSortSetting) {
      if (slotSettings) {
        slot1 = getDefaultSlotSettings().get(0);
        slot2 = getDefaultSlotSettings().get(1);
        slot3 = getDefaultSlotSettings().get(2);
        slot4 = getDefaultSlotSettings().get(3);
        slot5 = getDefaultSlotSettings().get(4);
        slot6 = getDefaultSlotSettings().get(5);
        slot7 = getDefaultSlotSettings().get(6);
        slot8 = getDefaultSlotSettings().get(7);
        slot9 = getDefaultSlotSettings().get(8);
      }
      if (replaceItemsLowerSettings) {
        replaceItemsLower1 = false;
        replaceItemsLower2 = false;
        replaceItemsLower3 = false;
        replaceItemsLower4 = false;
        replaceItemsLower5 = false;
        replaceItemsLower6 = false;
        replaceItemsLower7 = false;
        replaceItemsLower8 = false;
        replaceItemsLower9 = true;
      }
      if (dimensionsSettings) {
        dimensions = new ArrayList<>(List.of("minecraft:overworld"));
      }

      if (refillOnSortSetting) {
        refillOnSort = true;
      }

      if (replaceWeakerItemsOnSortSetting) {
        replaceWeakerItemsOnSort = false;
      }
    }

    // -------------------- SLOT CONFIGURATIONS --------------------
    @Comment("Rules for Slot 1")
    public List<String> slot1;

    @Comment("Allow items higher in this list to replace items lower on the " +
        "list in Slot 1")
    public boolean replaceItemsLower1;

    @Comment("Rules for Slot 2")
    public List<String> slot2;

    @Comment("Allow items higher in this list to replace items lower on the " +
        "list in Slot 2")
    public boolean replaceItemsLower2;

    @Comment("Rules for Slot 3")
    public List<String> slot3;

    @Comment("Allow items higher in this list to replace items lower on the " +
        "list in Slot 3")
    public boolean replaceItemsLower3;

    @Comment("Rules for Slot 4")
    public List<String> slot4;

    @Comment("Allow items higher in this list to replace items lower on the " +
        "list in Slot 4")
    public boolean replaceItemsLower4;

    @Comment("Rules for Slot 5")
    public List<String> slot5;

    @Comment("Allow items higher in this list to replace items lower on the " +
        "list in Slot 5")
    public boolean replaceItemsLower5;

    @Comment("Rules for Slot 6")
    public List<String> slot6;

    @Comment("Allow items higher in this list to replace items lower on the " +
        "list in Slot 6")
    public boolean replaceItemsLower6;

    @Comment("Rules for Slot 7")
    public List<String> slot7;

    @Comment("Allow items higher in this list to replace items lower on the " +
        "list in Slot 7")
    public boolean replaceItemsLower7;

    @Comment("Rules for Slot 8")
    public List<String> slot8;

    @Comment("Allow items higher in this list to replace items lower on the " +
        "list in Slot 8")
    public boolean replaceItemsLower8;

    @Comment("Rules for Slot 9")
    public List<String> slot9;

    @Comment("Allow items higher in this list to replace items lower on the " +
        "list in Slot 9")
    public boolean replaceItemsLower9;

    // -------------------- PROFILE BEHAVIORS --------------------
    @Comment("List of dimensions for which this profile should be auto-selected. "
        +
        "E.g. [\"minecraft:the_nether\",\"minecraft:the_end\"]. Empty = none.")
    public List<String> dimensions;

    @Comment("Automatically pull identical items from your inventory to "
        + "refill the hotbar when sorting.")
    public boolean refillOnSort;

    @Comment("Allow superior items to replace other assigned items when sorting.")
    public boolean replaceWeakerItemsOnSort;
  }
}
