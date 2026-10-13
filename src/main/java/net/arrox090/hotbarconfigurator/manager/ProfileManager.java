package net.arrox090.hotbarconfigurator.manager;

import static net.arrox090.hotbarconfigurator.manager.Helper.*;
import static net.arrox090.hotbarconfigurator.manager.ItemRefillManager.refillAllHotbarSlots;

import com.llamalad7.mixinextras.lib.apache.commons.tuple.Pair;
import java.util.*;
import me.shedaniel.autoconfig.AutoConfig;
import net.arrox090.hotbarconfigurator.HotbarConfigurator;
import net.arrox090.hotbarconfigurator.config.HotbarConfiguratorConfig;
import net.arrox090.hotbarconfigurator.util.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public class ProfileManager {

  public record SlotToHotbarMappings(int preferredSlot, int priority,
      ItemStack stack, int currentSlot) {
  }

  /**
   * Compares two candidates for a given hotbar slot to sort from best to worst.
   * Returns positive if a is better than b, negative if b is better than a.
   */
  private static int compareCandidates(Pair<Integer, Integer> candidateA,
      Pair<Integer, Integer> candidateB,
      int hotbarSlot,
      PlayerInventory playerInventory,
      HotbarConfiguratorConfig config) {
    if (candidateA.equals(candidateB))
      return 0;

    int priorityA = candidateA.getRight();
    int priorityB = candidateB.getRight();

    boolean replaceLower = config.getProfiles()
        .get(config.activeProfile)
        .getReplaceItemsLowerOnTheList()
        .get(hotbarSlot);

    // If list order matters...
    if (replaceLower && priorityA != priorityB) {
      // ...compare the priority and higher one wins
      return Integer.compare(priorityA, priorityB);
    }

    ItemStack stackA = playerInventory.getStack(candidateA.getLeft());
    ItemStack stackB = playerInventory.getStack(candidateB.getLeft());
    String idA = Registries.ITEM.getId(stackA.getItem()).toString();
    String idB = Registries.ITEM.getId(stackB.getItem()).toString();

    // Check item quality (tier, enchantments etc.)
    // (using bypassConfig true here, as this function just gets the absolute
    // best stack, the settings are checked later)
    if (checkIfBetterItem(stackA, stackB, idA, idB, true)) {
      // A is better
      return 1;
    }
    if (checkIfBetterItem(stackB, stackA, idB, idA, true)) {
      // B is better
      return -1;
    }

    // // Same item type: higher stack count wins
    // if (idA.equals(idB) && stackA.getCount() != stackB.getCount()) {
    // return Integer.compare(stackA.getCount(), stackB.getCount());
    // }

    return 0;
  }

  /**
   * Determines if an item already sitting in its slot should be protected from
   * being stolen.
   */
  private static boolean isIncumbentProtected(int hotbarSlot, Pair<Integer, Integer> incumbent,
      List<Pair<Integer, Integer>> sortedCandidates,
      PlayerInventory playerInventory,
      HotbarConfiguratorConfig config) {
    // If the profile disables replacing weaker items on sort, incumbent always
    // keeps its spot
    if (!config.getProfiles()
        .get(config.activeProfile).replaceWeakerItemsOnSort) {
      return true;
    }

    // Get the best candidate for this slot
    Pair<Integer, Integer> topCandidate = sortedCandidates.get(0);

    // If the incumbent is the best candidate, it should stay, so is protected
    if (topCandidate.getLeft() == hotbarSlot) {
      return true;
    }

    // If something ranked higher, check if replacement settings allow it to
    // displace the incumbent:
    int topPriority = topCandidate.getRight();
    int incumbentPriority = incumbent.getRight();

    boolean replaceLower = config.getProfiles()
        .get(config.activeProfile)
        .getReplaceItemsLowerOnTheList()
        .get(hotbarSlot);

    // If top candidate has higher list priority and replaceLower is enabled ->
    // it should be replaced
    if (replaceLower && topPriority > incumbentPriority) {
      return false;
    }

    ItemStack topStack = playerInventory.getStack(topCandidate.getLeft());
    ItemStack incStack = playerInventory.getStack(incumbent.getLeft());
    String topId = Registries.ITEM.getId(topStack.getItem()).toString();
    String incId = Registries.ITEM.getId(incStack.getItem()).toString();

    // If top candidate is a better item -> it should be replaced
    // (using bypassConfig false here, because there already is an incumbent in
    // this slot)
    if (checkIfBetterItem(topStack, incStack, topId, incId, false)) {
      return false;
    }

    // // Same item with higher stack count -> evicts incumbent
    // if (topId.equals(incId) && topStack.getCount() > incStack.getCount()) {
    // return false;
    // }

    // Otherwise, nothing is allowed to kick it out -> incumbent should stay
    return true;
  }

  /**
   * Scans the player's inventory to build a mapping of which stacks should be
   * moved to which hotbar slots.
   *
   * @return A Pair containing the list of stacks to move, and a Set of hotbar
   *         slots that are already finalized.
   */
  private static Pair<List<SlotToHotbarMappings>, Set<Integer>> cacheInventory(PlayerInventory playerInventory) {

    // Map of hotbar slot -> list of slots and their priorities that are
    // candidates to be assigned to that hotbar slot
    Map<Integer, List<Pair<Integer, Integer>>> slotCandidates = new HashMap<>();

    for (int inventorySlot = 0; inventorySlot < playerInventory.main.size(); inventorySlot++) {

      // Get stacks in that slot, and skip if no stacks
      ItemStack stack = playerInventory.getStack(inventorySlot);
      if (stack.isEmpty())
        continue;

      // Get the list of slots and their priorities which this stack is assigned
      // to in slot settings of current profile
      List<Pair<Integer, Integer>> stackPlacements = getStackAssignedPlacements(stack);

      for (Pair<Integer, Integer> placement : stackPlacements) {

        // Add the candidate to the existing list for this slot/creating a new
        // one if its the first one
        slotCandidates
            .computeIfAbsent(placement.getLeft(), k -> new ArrayList<>())
            .add(Pair.of(inventorySlot, placement.getRight()));
      }
    }

    HotbarConfiguratorConfig config = ConfigManager.getCachedConfig();

    // Sort each slot's candidates from best to worst
    for (int hotbarSlot = 0; hotbarSlot < PlayerInventory.getHotbarSize(); hotbarSlot++) {
      // need this because in java, any local variable used inside lambda
      // expression must be "final or effectively final"
      final int slot = hotbarSlot;

      List<Pair<Integer, Integer>> candidates = slotCandidates.get(hotbarSlot);

      // Only sort needed if more than one candidate
      if (candidates != null && candidates.size() > 1) {

        // Use sort with the custom compareCandidates function to correctly sort
        // from best to worst
        Comparator<Pair<Integer, Integer>> comparator = (a, b) -> compareCandidates(a, b, slot, playerInventory,
            config);

        // It needs to be reversed, as it puts smaller number first
        candidates.sort(comparator.reversed());
      }
    }

    // Find protected incumbents (items already in place that won't be replaced)
    Set<Integer> protectedIncumbents = new HashSet<>();
    for (int hotbarSlot = 0; hotbarSlot < PlayerInventory.getHotbarSize(); hotbarSlot++) {

      // Get all candidates
      List<Pair<Integer, Integer>> candidates = slotCandidates.get(hotbarSlot);
      if (candidates == null || candidates.isEmpty())
        continue;

      for (Pair<Integer, Integer> candidate : candidates) {

        // If this candidate is an incumbent...
        if (candidate.getLeft() == hotbarSlot) {

          // ...check if its protected (shouldnt get moved)
          if (isIncumbentProtected(hotbarSlot, candidate, candidates,
              playerInventory, config)) {
            protectedIncumbents.add(hotbarSlot);
          }
          break;
        }
      }
    }

    // Set of slots already assigned to a hotbar slot, so there are no stacks
    // assigned to two different hotbar slots
    Set<Integer> claimedInventorySlots = new HashSet<>();

    // Slots with already the correct slot in place
    Set<Integer> assignedHotbarSlots = new HashSet<>();

    // List containing slots and where each of them should be moved
    List<SlotToHotbarMappings> slotToHotbar = new ArrayList<>();

    // Assign best available candidate to each slot
    for (int hotbarSlot = 0; hotbarSlot < PlayerInventory.getHotbarSize(); hotbarSlot++) {

      // If the incumbent is protected...
      if (protectedIncumbents.contains(hotbarSlot)) {
        // ...keep it where it is
        assignedHotbarSlots.add(hotbarSlot);
        claimedInventorySlots.add(hotbarSlot);
        continue;
      }

      List<Pair<Integer, Integer>> candidates = slotCandidates.get(hotbarSlot);
      if (candidates == null || candidates.isEmpty())
        continue;

      // Pick the first candidate in sorted order that is free
      for (Pair<Integer, Integer> candidate : candidates) {
        int inventorySlot = candidate.getLeft();

        // If its either claimed or protected, skip and check the next best
        // candidate
        if (claimedInventorySlots.contains(inventorySlot))
          continue;
        if (protectedIncumbents.contains(inventorySlot))
          continue;

        // Else, add it to slotToHotbar
        ItemStack stack = playerInventory.getStack(inventorySlot);
        slotToHotbar.add(new SlotToHotbarMappings(
            hotbarSlot, candidate.getRight(), stack, inventorySlot));
        claimedInventorySlots.add(inventorySlot);
        break;
      }
    }

    return Pair.of(slotToHotbar, assignedHotbarSlots);
  }

  /**
   * Determines if a stack belongs in any of the configured hotbar slots for the
   * active profile.
   *
   * @return A list of valid target slots and their priority (higher priority
   *         number = better match).
   */
  private static List<Pair<Integer, Integer>> getStackAssignedPlacements(ItemStack stack) {
    List<Pair<Integer, Integer>> result = new ArrayList<>();

    // Get slot settings (what items in which hotbar slots)
    HotbarConfiguratorConfig.ProfileSettings slotSettings = ConfigManager.getCachedConfig().getProfiles().get(
        ConfigManager.getCachedConfig().activeProfile);
    List<List<String>> allSlots = List.of(slotSettings.slot1, slotSettings.slot2, slotSettings.slot3,
        slotSettings.slot4, slotSettings.slot5, slotSettings.slot6,
        slotSettings.slot7, slotSettings.slot8, slotSettings.slot9);

    int slotIndex = 0;
    for (List<String> assignedItems : allSlots) {

      // Count priorityIndex down so higher priority is 'better' item
      int priorityIndex = assignedItems.size() - 1;

      for (String assignedItem : assignedItems) {

        // Array containing itemid and enchantments/potion effects if there are
        // any
        String[] ids = Helper.processSlotEntry(assignedItem);

        if (ids != null) {

          // Check if the stack matches the itemId, and if enchantments/potion
          // effects are empty (match everything) or they match
          if (Helper.stackMatchesIdOrTag(ids[0], stack) &&
              (ids[1].isBlank() ||
                  stackHasEnchantments(stack, Arrays.asList(ids[1].split(","))) ||
                  potionHasEffect(stack, Arrays.asList(ids[1].split(","))))) {
            result.add(Pair.of(slotIndex, priorityIndex));
          }
        }
        priorityIndex--;
      }
      slotIndex++;
    }
    return result;
  }

  /** Helper to check if a stack is assigned to a hotbar slot */
  public static boolean isAssignedToHotbar(ItemStack stack) {
    return !getStackAssignedPlacements(stack).isEmpty();
  }

  public static void sortHotbar(PlayerInventory playerInventory) {
    Pair<List<SlotToHotbarMappings>, Set<Integer>> cachedInventory = cacheInventory(playerInventory);

    List<SlotToHotbarMappings> entries = cachedInventory.getLeft();

    Set<Integer> assignedSlots = cachedInventory.getRight();
    List<Pair<Integer, Integer>> swappedSlots = new ArrayList<>();

    for (var entry : entries) {
      int preferredSlot = entry.preferredSlot;

      if (!assignedSlots.contains(preferredSlot)) {

        // Get the current position of the stack that was in this slot (it might
        // have been already swapped)
        int currentSlot = getCurrentSlot(swappedSlots, entry.currentSlot);

        // a safety check to not swap slot with the same slot
        if (currentSlot != preferredSlot) {
          swapItems(currentSlot, preferredSlot);
          swappedSlots.add(Pair.of(currentSlot, preferredSlot));
        }
        assignedSlots.add(preferredSlot);
      }
      if (assignedSlots.size() == 9)
        break;
    }

    if (ConfigManager.getCachedConfig()
        .getProfiles()
        .get(ConfigManager.getCachedConfig().activeProfile).refillOnSort) {
      refillAllHotbarSlots(playerInventory);
    }

    HotbarConfigurator.LOGGER.debug("Sorted hotbar. Performed {} slot swaps.",
        swappedSlots.size());
  }

  /**
   * Traces the chronological history of swaps to find where an originally
   * displaced item currently sits. Since items are always displaced from the
   * 'right' (preferredSlot) to the 'left' (currentSlot), we only need to track
   * right-to-left movements, cause when a stack moves to the preferredSlot its
   * never moved again.
   *
   * @param swappedSlots Chronological list of past swaps Pair(currentSlot,
   *                     preferredSlot)
   * @param slot         The original slot the item was in before any sorting
   *                     started
   * @return The actual current slot of the item
   */
  private static int getCurrentSlot(List<Pair<Integer, Integer>> swappedSlots,
      int slot) {
    int currentSlot = slot;
    for (var entry : swappedSlots) {
      if (currentSlot == entry.getRight()) {
        currentSlot = entry.getLeft();
      }
    }
    return currentSlot;
  }

  private static void swapItems(int currentSlot, int targetHotbarSlot) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player == null) {
      HotbarConfigurator.LOGGER.warn("Tried swapping items for null player");
      return;
    }

    int absoluteCurrentSlot = Helper.getAbsoluteId(client.player.playerScreenHandler, currentSlot);

    if (absoluteCurrentSlot != -1) {
      swapToHotbar(client.player.playerScreenHandler, absoluteCurrentSlot,
          targetHotbarSlot);
    }
  }

  public static void cycleProfiles(PlayerInventory playerInventory) {
    ConfigManager.getCachedConfig().activeProfile = ConfigManager.getCachedConfig().activeProfile.next();
    AutoConfig.getConfigHolder(HotbarConfiguratorConfig.class).save();
    displayProfileChangeMessage();

    sortHotbar(playerInventory);
  }

  public static void activateProfile(PlayerInventory playerInventory,
      String profile) {
    if (ConfigManager.getCachedConfig().activeProfile.equals(
        HotbarConfiguratorConfig.ActiveProfile.valueOf(profile))) {
      return;
    }

    ConfigManager.getCachedConfig().activeProfile = HotbarConfiguratorConfig.ActiveProfile.valueOf(profile);
    AutoConfig.getConfigHolder(HotbarConfiguratorConfig.class).save();
    displayProfileChangeMessage();

    sortHotbar(playerInventory);
  }

  private static void displayProfileChangeMessage() {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player != null) {

      // Get the current profile
      HotbarConfiguratorConfig.ActiveProfile current = ConfigManager.getCachedConfig().activeProfile;

      // Format it nicely
      String profileName = switch (current) {
        case PROFILE1 -> "Profile 1";
        case PROFILE2 -> "Profile 2";
        case PROFILE3 -> "Profile 3";
      };

      // Send the message
      client.player.sendMessage(
          Text.literal("Activated profile: " + profileName),
          /* actionBar= */ true);

      HotbarConfigurator.LOGGER.info("Switched to profile: {}", profileName);
    }
  }

  public static void onDimensionChange(PlayerInventory playerInventory,
      String newDimension) {
    if (!ConfigManager.getCachedConfig().profileChangeOnDimensionEnter)
      return;

    for (var entry : ConfigManager.getCachedConfig().getProfiles().entrySet()) {
      String name = entry.getKey().toString();
      HotbarConfiguratorConfig.ProfileSettings profileSettings = entry.getValue();
      if (profileSettings.dimensions.contains(newDimension)) {

        // Dont swap if alrady the active profile
        if (!name.equals(
            ConfigManager.getCachedConfig().activeProfile.toString())) {
          activateProfile(playerInventory, name);
        }
        break;
      }
    }
  }
}
