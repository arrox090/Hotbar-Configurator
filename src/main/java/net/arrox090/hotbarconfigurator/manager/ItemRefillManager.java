package net.arrox090.hotbarconfigurator.manager;

import static net.arrox090.hotbarconfigurator.manager.Helper.swapItems;

import java.util.HashSet;
import java.util.Set;
import net.arrox090.hotbarconfigurator.HotbarConfigurator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

public class ItemRefillManager {

  private static final Set<Integer> HOTBAR_SLOTS = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8);

  /**
   * Refills every stack in player hotbar that can be refilled from the
   * inventory
   */
  public static void refillAllHotbarSlots(PlayerInventory playerInventory) {

    // Loop through all player hotbar slots
    for (int slotIndex = 0; slotIndex < PlayerInventory.getHotbarSize(); slotIndex++) {
      ItemStack stack = playerInventory.getStack(slotIndex);

      // If there is no stack in the slot or the stack is already full, skip it
      if (stack.isEmpty() || stack.getCount() == stack.getMaxCount()) {
        continue;
      }

      // Create a checkedSlots set, so if for whatever reason the server desyncs
      // and thinks that there are still items in the slot we moved from, it
      // won't get stuck in an infinite loop
      Set<Integer> checkedSlots = new HashSet<>(HOTBAR_SLOTS);

      // Get a stack that is the same but not in hotbar
      int sameStackIndex = Helper.indexOf(playerInventory, stack, checkedSlots, true);

      // while there is another stack to fill from...
      while (sameStackIndex >= 0) {
        // ...update checkedSlots and refill that slot from the found stack
        checkedSlots.add(sameStackIndex);
        refillItem(slotIndex, sameStackIndex);

        // Check if the stack is already full and if so break out of the loop
        if (stack.getCount() == stack.getMaxCount()) {
          break;
        }

        // And find another such stack
        sameStackIndex = Helper.indexOf(playerInventory, stack, checkedSlots, true);
      }
    }
  }

  /** Refills the toBeRefilled slot with toRefill slot */
  private static void refillItem(int toBeRefilled, int toRefill) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player == null) {
      HotbarConfigurator.LOGGER.warn(
          "Tried refilling a stack for null player or interactionManager");
      return;
    }

    int absoluteToBeRefilled = Helper.getAbsoluteId(client.player.playerScreenHandler, toBeRefilled);
    int absoluteToRefill = Helper.getAbsoluteId(client.player.playerScreenHandler, toRefill);

    if (absoluteToBeRefilled != -1 && absoluteToRefill != -1) {
      // Since swap items makes three clicks (pickup toRefill, click on the
      // toBeRefilled with toRefill in cursor, click the toRefill slot with the
      // rest of toRefill) then we can just use it as a refill func
      swapItems(client.player.playerScreenHandler, absoluteToRefill,
          absoluteToBeRefilled);
    }
  }
}
