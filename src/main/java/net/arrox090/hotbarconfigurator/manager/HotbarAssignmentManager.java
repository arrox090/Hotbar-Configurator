package net.arrox090.hotbarconfigurator.manager;

import static net.arrox090.hotbarconfigurator.manager.Helper.*;

import java.util.*;
import net.arrox090.hotbarconfigurator.HotbarConfigurator;
import net.arrox090.hotbarconfigurator.config.HotbarConfiguratorConfig;
import net.arrox090.hotbarconfigurator.util.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class HotbarAssignmentManager {
  private final HotbarConfiguratorConfig config;
  private final Set<Integer> assignedSlots = new HashSet<>();
  private final HashSet<Integer> forbiddenSlots = new HashSet<>();
  private final PlayerInventory playerInventory;
  private Slot slotToMove;
  private ItemStack currentStack;
  private final ActionType actionType;
  private boolean stop = false;
  private final boolean bypassAutoEquipConfig;
  private boolean equipArmor;
  private int replacementDepth = 0;
  private final ScreenHandler screenHandler;

  // For crafting double assign issue
  public static int expectedCraftingItemCount = 0;
  public static Item expectedCraftingItem = null;
  private static long lastInventoryFlagTime = 0L;

  public enum ActionType {
    PLAYER_INVENTORY,
    ITEM_PICKING_UP,
    OTHER,
  }

  /** Reset the crafting items flag */
  public static void resetDoingInventoryCraftingCalculations() {
    expectedCraftingItemCount = 0;
    expectedCraftingItem = null;
    lastInventoryFlagTime = 0L;
  }

  /**
   * every tick, check if the crafting item flag was set more than 1s ago. Acts
   * as a failsafe if the flag wasnt reset by server packets for whatever reason
   */
  public static void tick() {
    if (expectedCraftingItem != null) {
      long now = System.currentTimeMillis();
      if (now - lastInventoryFlagTime >= 1000L) {
        HotbarConfigurator.LOGGER.debug("resetting expected crafting item");
        resetDoingInventoryCraftingCalculations();
      }
    }
  }

  /**
   * HotbarAssignmentManager Constructur
   *
   * @param playerInventory       inventory of the player to do operations on
   * @param slot                  the slot which to do operations on
   * @param actionType            the action type to perform
   * @param screenHandler         the screen handler where the operation is being
   *                              made
   * @param equipArmor            if should allow to equip armor
   * @param bypassAutoEquipConfig if should bypass config for auto equip
   */
  public HotbarAssignmentManager(PlayerInventory playerInventory, Slot slot,
      ActionType actionType,
      ScreenHandler screenHandler,
      boolean equipArmor,
      boolean bypassAutoEquipConfig) {
    this.config = ConfigManager.getCachedConfig();
    this.playerInventory = playerInventory;
    this.slotToMove = slot;
    this.actionType = actionType;
    this.screenHandler = screenHandler;
    this.equipArmor = equipArmor;
    this.bypassAutoEquipConfig = bypassAutoEquipConfig;
    computeAssignedSlots();
  }

  /**
   * HotbarAssignmentManager Other Constructur
   *
   * @param playerInventory inventory of the player to do operations on
   * @param slot            the slot which to do operations on
   * @param actionType      the action type to perform
   * @param screenHandler   the screen handler where the operation is being made
   * @param equipArmor      if should allow to equip armor
   */
  public HotbarAssignmentManager(PlayerInventory playerInventory, Slot slot,
      ActionType actionType,
      ScreenHandler screenHandler,
      boolean equipArmor) {
    this.config = ConfigManager.getCachedConfig();
    this.playerInventory = playerInventory;
    this.slotToMove = slot;
    this.actionType = actionType;
    this.screenHandler = screenHandler;
    this.equipArmor = equipArmor;
    this.bypassAutoEquipConfig = false;
    computeAssignedSlots();
  }

  /**
   * Checks what hotbar slots does the player have configured, and marks that
   * information in assignedSlots set
   */
  private void computeAssignedSlots() {
    if (!config.getProfiles().get(config.activeProfile).slot1.isEmpty())
      assignedSlots.add(0);
    if (!config.getProfiles().get(config.activeProfile).slot2.isEmpty())
      assignedSlots.add(1);
    if (!config.getProfiles().get(config.activeProfile).slot3.isEmpty())
      assignedSlots.add(2);
    if (!config.getProfiles().get(config.activeProfile).slot4.isEmpty())
      assignedSlots.add(3);
    if (!config.getProfiles().get(config.activeProfile).slot5.isEmpty())
      assignedSlots.add(4);
    if (!config.getProfiles().get(config.activeProfile).slot6.isEmpty())
      assignedSlots.add(5);
    if (!config.getProfiles().get(config.activeProfile).slot7.isEmpty())
      assignedSlots.add(6);
    if (!config.getProfiles().get(config.activeProfile).slot8.isEmpty())
      assignedSlots.add(7);
    if (!config.getProfiles().get(config.activeProfile).slot9.isEmpty())
      assignedSlots.add(8);
  }

  /** Assigns a stack to the correct hotbar slot if any */
  public void assignItem() {
    // If a player moves an item from crafting result (quick move from crafting
    // result), then the code needs to call vanilla quick move action to
    // correctly craft the items, and track where the item went, update the slot
    // index and only then proceed
    if ((actionType == ActionType.PLAYER_INVENTORY ||
        screenHandler instanceof CraftingScreenHandler) &&
        slotToMove.getIndex() == 0) {
      List<Integer> emptySlots = new ArrayList<>();

      // Loop through all slots on the screen
      for (Slot slot : screenHandler.slots) {
        // If the slot is part of the player's inventory/hotbar, and it's
        // empty...
        if (slot.inventory == playerInventory && slot.getStack().isEmpty()) {
          // ...save the absolute id
          emptySlots.add(slot.id);
        }
      }

      // Calculate how many stacks will be created. crafts - how many times the
      // recipe will be created
      int crafts = Integer.MAX_VALUE;

      // The crafting grid index aleways starts at 0 so set to 4 for 2x2 grid
      // and 9 for 3x3 grid so it just loops through the crafting grid slots
      int maxGrid = (screenHandler instanceof CraftingScreenHandler) ? 9 : 4;

      for (int i = 1; i <= maxGrid; i++) {
        if (screenHandler.getSlot(i).hasStack()) {
          // Set crafts to count of the stack that has the lowest count out of
          // all that are inside the crafting grid
          crafts = Math.min(crafts, screenHandler.getSlot(i).getStack().getCount());
        }
      }

      // If crafts did not change, set it to 0 (empty grid)
      if (crafts == Integer.MAX_VALUE) {
        HotbarConfigurator.LOGGER.warn(
            "Crafting grid appeared empty during a shift-click on crafting "
                + "result slot! Defaulting crafts to 0.");
        crafts = 0;
      }

      // Calculate the number of items that can be crafted, and how many stacks
      // that equals to
      int totalItems = crafts * slotToMove.getStack().getCount();
      int expectedStacks = (int) Math.ceil((double) totalItems /
          slotToMove.getStack().getMaxCount());

      // The amount of stacks that will be crafted is either the expectedStacks,
      // or size of emtpySlots if that is smaller, because crafting does not
      // allow to craft more than you have empty slots available
      expectedCraftingItemCount = Math.min(expectedStacks, emptySlots.size());

      // Shift click (craft, move) the slotToMove
      doQuickMove();

      // Scan the previously empty slots to find the newly crafted items
      // (Minecraft is synchronous, guaranteeing these items came from the quick
      // move)
      boolean found = false;
      for (int slotId : emptySlots) {
        Slot slot = screenHandler.getSlot(slotId);
        ItemStack stack = slot.getStack();

        // If found a stack with na item it'll be the one with highest count if
        // multiple stacks, because client only calculates first craft, the
        // server does the rest. so ex. if we craft 64 logs into planks, the
        // client will only show one slot filled up with 4 planks
        if (!stack.isEmpty()) {
          slotToMove = slot;
          found = true;
          break;
        }
      }

      if (!found) {
        HotbarConfigurator.LOGGER.warn(
            "did not find the crafted item, skipping");
        return;
      }

      // When crafting, a packet will arrive which is
      // the same as if player picked up an item, so we need to tell it to skip
      // the crafted items packets
      expectedCraftingItem = slotToMove.getStack().getItem();
      lastInventoryFlagTime = System.currentTimeMillis();

      HotbarConfigurator.LOGGER.debug("Setting expectedCraftingItem to " +
          expectedCraftingItem + "and count " +
          expectedCraftingItemCount);
    }

    // Assign currentStack to the stack of slotToMove
    currentStack = slotToMove.getStack();

    // If item comes from a container and isn't assigned to any hotbar slot,
    // don't pull items from inventory. Let vanilla quick-move merge it natively
    if (actionType == ActionType.OTHER &&
        !ProfileManager.isAssignedToHotbar(currentStack)) {
      if (!(screenHandler instanceof CraftingScreenHandler)) {
        doQuickMove();
      }
      return;
    }

    // Try to fill up this stack with stacks from inventory (ex. moving 30 dirt
    // from chest, and have 20 dirt in inventory. it would just merge those, as
    // standard vanilla minecraft does it on quickMove)
    if (actionType != ActionType.PLAYER_INVENTORY &&
        currentStack.getMaxCount() > 1) {
      Set<Integer> checkedSlots = new HashSet<>();

      // We dont allow stacks that are full, because we cant merge with them
      int sameItemIndex = indexOf(playerInventory, currentStack, checkedSlots, false);

      // Merge until there is no more stacks to merge with
      while (sameItemIndex >= 0) {
        checkedSlots.add(sameItemIndex);

        int absoluteInvSlot = Helper.getAbsoluteId(screenHandler, sameItemIndex);

        if (absoluteInvSlot != -1) {
          // Swap the slots, so basically just fill the slotToMove with the
          // other one. Important, pull from inventory to the container!
          swapItems(screenHandler, absoluteInvSlot, slotToMove.id);
        }

        // If we filled the stack to its max, break
        if (slotToMove.getStack().getCount() == slotToMove.getStack().getMaxCount()) {
          break;
        }

        sameItemIndex = indexOf(playerInventory, slotToMove.getStack(),
            checkedSlots, false);
      }

      // Update the currentStack, as the count might have changed
      currentStack = slotToMove.getStack();
    }

    // Move the currentStack to its dedicated slot until we run out of items on
    // that slot or should stop
    while (!currentStack.isEmpty() && !stop) {

      // Move the stack to a slot it has assigned if any
      swapWithDedicatedSlot();

      // If player se chain substitutions to false, break after the first move
      if (!config.allowChainSubstitutions)
        break;

      // Moved the old stack, so now there is a new one in the slot
      currentStack = slotToMove.getStack();

      replacementDepth += 1;
      if (replacementDepth >= 100) {
        HotbarConfigurator.LOGGER.warn(
            "Exceeded max replacement depth (100) while sorting: {}",
            currentStack);
        break;
      }
    }
  }

  /** Finds which slot the item should swap with, and swaps it */
  private void swapWithDedicatedSlot() {
    String currentStackId = Registries.ITEM.getId(currentStack.getItem()).toString();

    // Currently it has to be picked up when player is not in a different screen
    // handler (client side limitation)
    if (equipArmor && screenHandler instanceof PlayerScreenHandler) {
      // Check if its an offhand item and if should equip it
      boolean isShield = currentStackId.equals("minecraft:shield");
      boolean isTotem = currentStackId.equals("minecraft:totem_of_undying");
      boolean shouldEquipShield = isShield && (bypassAutoEquipConfig || config.autoEquip.shield);
      boolean shouldEquipTotem = isTotem && (bypassAutoEquipConfig || config.autoEquip.totemOfUndying);

      if (shouldEquipShield || shouldEquipTotem) {

        // Get the offhand stack and stack id
        ItemStack offhandStack = playerInventory.getStack(PlayerInventory.OFF_HAND_SLOT);
        String offhandStackId = Registries.ITEM.getId(offhandStack.getItem()).toString();

        // If there is no stack in offhand, equip the shield/totem
        if (offhandStack.isEmpty()) {
          swapWithSlot(false, PlayerInventory.OFF_HAND_SLOT);
          return;
        }

        boolean isOffhandShield = offhandStackId.equals("minecraft:shield");
        boolean isOffhandTotem = offhandStackId.equals("minecraft:totem_of_undying");

        // If should replace items which are not configured to auto equip
        boolean shouldReplace = false;

        if (config.replaceNotMatchingItems.offhand) {

          // If its some random item not shield or totem -> replace
          if (!isOffhandShield && !isOffhandTotem) {
            shouldReplace = true;
          }

          // If its a Totem, but we are holding a Shield and Totem auto-equip is
          // OFF -> replace it
          else if (shouldEquipShield && isOffhandTotem &&
              !config.autoEquip.totemOfUndying) {
            shouldReplace = true;
          }

          // If its a Shield, but we are holding a Totem and Shield auto-equip
          // is OFF -> replace it
          else if (shouldEquipTotem && isOffhandShield &&
              !config.autoEquip.shield) {
            shouldReplace = true;
          }
        }

        // If current offhand item is worse and shold replace (in this case a
        // shield with worse enchantments)
        if (!shouldReplace && shouldEquipShield &&
            config.replaceWeakerItems.tools && isOffhandShield &&
            isBetterItem(currentStack, offhandStack, ItemType.TOOL, 0,
                new ArrayList<>(), new ArrayList<>())) {
          shouldReplace = true;
        }

        // If should replace...
        if (shouldReplace) {

          // ...swap with off hand slot
          swapWithSlot(false, PlayerInventory.OFF_HAND_SLOT);
          return;
        }
      }

      // If its an armor part (the stackArmorItem after instanceof ArmorItem
      // automatically creates a variable with type ArmorItem)
      else if (currentStack.getItem() instanceof ArmorItem stackArmorItem &&
          (bypassAutoEquipConfig || config.autoEquip.armor)) {

        // Get index of armor slot this type of armor belongs to
        EquipmentSlot slotType = stackArmorItem.getSlotType();
        int armorSlotIndex = getArmorSlotIndex(slotType);

        if (armorSlotIndex != -1) {
          ItemStack equippedArmor = playerInventory.armor.get(armorSlotIndex);

          // If no armor on the slot, equip
          if (equippedArmor.isEmpty()) {
            swapWithSlot(false, playerInventory.main.size() + armorSlotIndex);
            return;
          }

          // Check if the new armor is better than equipped one (make sure its
          // not ex. elytra)...
          if (config.replaceWeakerItems.armor &&
              equippedArmor.getItem() instanceof ArmorItem &&
              isBetterArmor(currentStack, equippedArmor)) {

            // ...then swap with it and set equipArmor to false as we just did
            swapWithSlot(false, playerInventory.main.size() + armorSlotIndex);
            equipArmor = false;
            return;
          }
        }
      }

      // If elytra instead
      else if (currentStackId.equals("minecraft:elytra") &&
          (bypassAutoEquipConfig || config.autoEquip.armor)) {

        int chestplateSlotIndex = 2;
        ItemStack equippedArmor = playerInventory.armor.get(chestplateSlotIndex);

        // No armor, equip elytra
        if (equippedArmor.isEmpty()) {
          swapWithSlot(false,
              playerInventory.main.size() + chestplateSlotIndex);
          return;
        }

        String equippedArmorId = Registries.ITEM.getId(equippedArmor.getItem()).toString();
        boolean isEquippedElytra = equippedArmorId.equals("minecraft:elytra");

        // Only compare if elytra is equipped
        if (isEquippedElytra) {

          boolean newElytraHasCurseOfBinding = getEnchantmentLevels(currentStack.getEnchantments(),
              List.of(Enchantments.BINDING_CURSE))[0] != 0;
          boolean equippedElytraHasCurseOfBinding = getEnchantmentLevels(equippedArmor.getEnchantments(),
              List.of(Enchantments.BINDING_CURSE))[0] != 0;
          boolean isBetterElytra = isBetterItem(currentStack, equippedArmor, ItemType.ARMOR, 0,
              new ArrayList<>(), new ArrayList<>());

          // If new elytra is better and none have curse of binding...
          if (isBetterElytra && !newElytraHasCurseOfBinding &&
              !equippedElytraHasCurseOfBinding) {

            // ...equip and set equipArmor to false
            swapWithSlot(false,
                playerInventory.main.size() + chestplateSlotIndex);
            equipArmor = false;
            return;
          }
        }
      }
    }

    for (int slotIndex = 0; slotIndex < PlayerInventory.getHotbarSize(); slotIndex++) {

      // Get the list of items/tags that are assigned to this hotbar slot
      List<String> allowedItems = config.getProfiles()
          .get(config.activeProfile)
          .getSlotEntries()
          .get(slotIndex);

      // Find out whats currently in that hotbar slot
      ItemStack targetStack = playerInventory.getStack(slotIndex);
      String targetStackId = Registries.ITEM.getId(targetStack.getItem()).toString();

      // Start shouldReplace true if stack is empty or if should replace not
      // matching items (important)
      boolean shouldReplace = targetStack.isEmpty() || config.replaceNotMatchingItems.hotbar;
      boolean found = false;

      // Check every entry in the allowedItems
      for (String entry : allowedItems) {

        // Split entry into tag/id and enchantments/potion effects
        String[] parsedEntry = Helper.processSlotEntry(entry);
        if (parsedEntry == null)
          continue;

        String idToCheck = parsedEntry[0];
        String extraInputString = parsedEntry[1];

        // If there are enchantments/potion effects...
        if (!extraInputString.isEmpty()) {

          // ...split enchantments/potion effects string into a list
          List<String> extraInputEntries = Arrays.asList(extraInputString.strip().split(","));

          boolean newStackExtraInputMatching;
          boolean targetStackExtraInputMatching;

          // If there is no specified item...
          if (idToCheck.isEmpty()) {

            // ...check potion effects on potions
            if (Set.of("minecraft:potion", "minecraft:splash_potion",
                "minecraft:lingering_potion")
                .contains(currentStackId)) {
              newStackExtraInputMatching = potionHasEffect(currentStack, extraInputEntries);
              targetStackExtraInputMatching = potionHasEffect(targetStack, extraInputEntries);

              // or enchantments on any item
            } else {
              newStackExtraInputMatching = stackHasEnchantments(currentStack, extraInputEntries);
              targetStackExtraInputMatching = stackHasEnchantments(targetStack, extraInputEntries);
            }

            // If new stack has the enchantments/potion effect set found to true
            found = found || newStackExtraInputMatching;

            // if shouldReplace (so did not find anything matching on target
            // slot) and target doesnt have required enchantments/potion effect
            // -> keep true
            shouldReplace = shouldReplace && !targetStackExtraInputMatching;

            // if there is an item specified...
          } else {

            // ...check potion effects on potions (here check idToCheck isntead
            // of currentStackId, as we have it specified)
            if (Set.of("minecraft:potion", "minecraft:splash_potion",
                "minecraft:lingering_potion")
                .contains(idToCheck.strip())) {
              newStackExtraInputMatching = potionHasEffect(currentStack, extraInputEntries);
              targetStackExtraInputMatching = potionHasEffect(targetStack, extraInputEntries);

              // or enchantments on any item
            } else {
              newStackExtraInputMatching = stackHasEnchantments(currentStack, extraInputEntries);
              targetStackExtraInputMatching = stackHasEnchantments(targetStack, extraInputEntries);
            }

            // Check which stacks match the id/tag, and if currentStack is
            // better (in case they are same item/type) found, shouldReplace,
            // betterItem
            boolean[] results = stackMatchesId(idToCheck.strip(), currentStack, targetStack,
                currentStackId, targetStackId);

            // If current stack is 'better', means both are same type, and new
            // stack matches the enchantments/potion effect...
            if (results[2] && newStackExtraInputMatching) {

              // ...it should replace
              found = true;
              shouldReplace = true;
              break;
            }

            // set found true if both id/tag and enchantments/effect matches
            found = found || (results[0] && newStackExtraInputMatching);

            // set shouldReplace true if has been true, and target does not
            // match either id/tag or enchantments/effect
            shouldReplace = shouldReplace && (results[1] || !targetStackExtraInputMatching);
          }

          // If no enchantments/potion effects...
        } else {

          // Get the id/tag matching info of current and target stacks
          boolean[] results = stackMatchesId(idToCheck.strip(), currentStack, targetStack,
              currentStackId, targetStackId);

          // If current is 'better' item...
          if (results[2]) {
            // ...replace
            found = true;
            shouldReplace = true;
            break;
          }

          // set found to true if matching id/tag
          found = found || results[0];

          // set shouldReplace to true if true, and target matches id/tag
          shouldReplace = shouldReplace && results[1];
        }

        // if found matching entry, and found a target matching OR should
        // replace items lower on the list (meaning there could be a target
        // matching from another entry, but we should replace it either way)...
        if (found && (!shouldReplace || config.getProfiles()
            .get(config.activeProfile)
            .getReplaceItemsLowerOnTheList()
            .get(slotIndex))) {
          // ...break, as we have all the info needed
          break;
        }
      }

      // If we found a matching entry...
      if (found) {
        // If the item is already sitting in this matching hotbar slot,
        // it shouldnt get moved, so stop
        if (slotToMove.inventory == playerInventory &&
            slotToMove.getIndex() == slotIndex) {
          stop = true;
          return;
        }

        // if shouldReplace is false, means that either replacelower is true,
        // so target stack has higher priority (was found faster or same than
        // new, otherwise shouldReplace would be true), or if replacelower is
        // false then we check which is better as order does not matter
        if (!shouldReplace && !config.getProfiles()
            .get(config.activeProfile)
            .getReplaceItemsLowerOnTheList()
            .get(slotIndex)) {
          shouldReplace = checkIfBetterItem(
              currentStack, targetStack, currentStackId, targetStackId, false);
        }

        // only replace if shouldReplace is true
        if (shouldReplace) {
          swapWithSlot(true, slotIndex);
          return;
        }
      }
    }

    // finally, if nothing matches, clean up, and stop
    handleUnassigned();
    stop = true;
  }

  /**
   * Compares two stacks to the id/tag provided and sets: found - if newStack
   * matches the id/tag shouldReplace - if targetStack doesnt match the id/tag
   * betterItem - if newStack is a better item (default is false if they arent
   * compared)
   */
  private boolean[] stackMatchesId(String idToCheck, ItemStack newStack,
      ItemStack targetStack, String newStackId,
      String targetStackId) {

    // Values that will be returned
    boolean found = false;
    boolean shouldReplace = true;
    boolean betterItem = false;

    boolean stackMatches = Helper.stackMatchesIdOrTag(idToCheck, newStack);
    boolean targetMatches = Helper.stackMatchesIdOrTag(idToCheck, targetStack);

    // If both match the tag/id...
    if (stackMatches && targetMatches) {
      // ...set values
      found = true;
      shouldReplace = false;

      // and compare the items to see which one is better
      betterItem = checkIfBetterItem(newStack, targetStack, newStackId,
          targetStackId, false);

      // just stack -> found=true
    } else if (stackMatches) {
      found = true;

      // just target -> shouldReplace=false
    } else if (targetMatches) {
      shouldReplace = false;
    }

    return new boolean[] { found, shouldReplace, betterItem };
  }

  /**
   * Swap slots using the appropriate helper function
   *
   * @param hotbar          tells if the target slot is in hotbar
   * @param targetSlotIndex the local index of the slot to move to in player
   *                        inventory
   * @return true if succed, false if failed
   */
  private boolean swapWithSlot(boolean hotbar, int targetSlotIndex) {
    if (hotbar) {
      swapToHotbar(screenHandler, slotToMove.id, targetSlotIndex);
    } else {

      // Get the absolute slot ID (since target is always in inventory can use
      // it)
      int absoluteTargetSlot = Helper.getAbsoluteId(screenHandler, targetSlotIndex);

      // If found the slot...
      if (absoluteTargetSlot != -1) {

        // ...swap the items
        swapItems(screenHandler, slotToMove.id, absoluteTargetSlot);
      } else {

        // Otherwise, if this was first swap and its not item picking up...
        if (actionType != ActionType.ITEM_PICKING_UP && replacementDepth == 0) {
          // just doQuickMove
          doQuickMove();
        }
        // tell the calling function it failed
        return false;
      }
    }

    HotbarConfigurator.LOGGER.debug(
        "Assigned {} to {} slot {}", currentStack.getItem(),
        hotbar ? "hotbar" : "inventory/equipment", targetSlotIndex);

    return true;
  }

  /** Perform a quickMove on the slotToMove */
  private void doQuickMove() {
    MinecraftClient client = MinecraftClient.getInstance();

    if (client.player == null || client.interactionManager == null) {
      HotbarConfigurator.LOGGER.warn(
          "Tried quick move for null player or interactionManager");
      return;
    }

    // Do a quick move from the slotToMove (0 means left click, 1 would be right
    // click)
    client.interactionManager.clickSlot(screenHandler.syncId, slotToMove.id, 0,
        SlotActionType.QUICK_MOVE,
        client.player);
  }

  /**
   * @return an index assigned to armor slot type
   */
  private static int getArmorSlotIndex(EquipmentSlot slotType) {
    return switch (slotType) {
      case FEET -> 0;
      case LEGS -> 1;
      case CHEST -> 2;
      case HEAD -> 3;
      default -> -1;
    };
  }

  /** Handle items that are not assigned to any slots in hotbar */
  private void handleUnassigned() {

    // Its a quick move, not item picking up action
    if (actionType != ActionType.ITEM_PICKING_UP) {

      // Skip quickmove in crafting screen so it does not move the item back to
      // the crafting grid
      if (!(screenHandler instanceof CraftingScreenHandler)) {
        doQuickMove();
      }

      // Its a picking up action
    } else {

      // Either move to any of available slots based on the config
      switch (config.unassignedItemBehavior) {
        case INVENTORY -> {
          Collections.addAll(forbiddenSlots, 0, 1, 2, 3, 4, 5, 6, 7, 8);
          moveToFirstEmptySlot();
        }
        case UNASSIGNED_SLOT -> {
          forbiddenSlots.addAll(assignedSlots);
          moveToFirstEmptySlot();
        }
        // or just do nothing if player does not care
        default -> {
        }
      }
    }
  }

  /**
   * Attempts to place the given stack into the first slot, which is empty and
   * not in forbidden (either unassigned or non-hotbar. Based on config)
   */
  private void moveToFirstEmptySlot() {

    // If the item is already in a legal slot for this behavior, do nothing
    if (slotToMove.inventory == playerInventory &&
        !forbiddenSlots.contains(slotToMove.getIndex())) {
      return;
    }

    for (int slotIndex = 0; slotIndex < playerInventory.main.size(); slotIndex++) {

      // Skip slots that are forbidden
      if (forbiddenSlots.contains(slotIndex)) {
        continue;
      }

      // See if the slot is empty, and if so swap and return
      ItemStack targetStack = playerInventory.getStack(slotIndex);
      if (targetStack.isEmpty()) {
        swapWithSlot(false, slotIndex);
        return;
      }
    }
  }
}
