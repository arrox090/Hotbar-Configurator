package net.arrox090.hotbarconfigurator.mixin;

import net.arrox090.hotbarconfigurator.HotbarConfigurator;
import net.arrox090.hotbarconfigurator.manager.HotbarAssignmentManager;
import net.arrox090.hotbarconfigurator.util.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// For picking up items
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

  @Unique private boolean newItem;

  /**
   * Inject into method called when a slot updates for screen player has
   * open/inventory (as its techincally always open). ex durability changes or
   * item gets picked up or even furnance finishes smelling (head - before
   * anything gets executed)
   */
  @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
  private void beforeSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet,
                                CallbackInfo ci) {

    // check if mod is enabled for pickingupitem scenario
    if (!ConfigManager.getCachedConfig().enabledForScenarios.pickingUpItem)
      return;

    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player == null)
      return;

    // We dont care if the update is of some background screen, only the current
    // (so either open one, or if nothing open - player inventory)
    if (packet.getSyncId() != client.player.currentScreenHandler.syncId) {
      return;
    }

    ScreenHandler handler = client.player.currentScreenHandler;

    // Prevent out-of-bounds crashes if packet slot doesn't match handler
    if (packet.getSlot() < 0 || packet.getSlot() >= handler.slots.size())
      return;

    // Get the slot that got updated
    Slot slot = handler.getSlot(packet.getSlot());

    // Only proceed if the slot belongs to the player's inventory
    if (slot.inventory != client.player.getInventory())
      return;

    // Check if the slot is empty
    newItem = slot.getStack().isEmpty();
  }

  /**
   * Not inject to the end of that function after we gathered the needed info
   * from head
   */
  @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
  private void afterSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet,
                               CallbackInfo ci) {

    // again check config settings. and additionally
    // check if the slot was empty before the update (only care about new items)
    if (!newItem ||
        !ConfigManager.getCachedConfig().enabledForScenarios.pickingUpItem)
      return;

    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player == null)
      return;

    // We dont care if the update is of some background screen, only the current
    // (so either open one, or if nothing open - player inventory)
    if (packet.getSyncId() != client.player.currentScreenHandler.syncId) {
      return;
    }

    ScreenHandler handler = client.player.currentScreenHandler;

    // Prevent out-of-bounds crashes if packet slot doesn't match handler
    if (packet.getSlot() < 0 || packet.getSlot() >= handler.slots.size())
      return;

    Slot slot = handler.getSlot(packet.getSlot());

    // Only proceed if the slot belongs to the player's inventory
    if (slot.inventory != client.player.getInventory())
      return;

    // Check if this packet isnt just a 'new' item in the crafting result
    // (meaning ex. we put two planks to create sticks, it would show in the
    // crafting results the sticks, and would send it as this packet)
    if (slot instanceof CraftingResultSlot)
      return;

    // If the new item is just air, ignore it
    if (slot.getStack().isEmpty()) {
      return;
    }

    // Might be that this packet was just crafting result from player inventory,
    // in that case skip it
    if (HotbarAssignmentManager.expectedCraftingItem != null &&
        slot.getStack().isOf(HotbarAssignmentManager.expectedCraftingItem)) {
      HotbarConfigurator.LOGGER.debug("Found crafted item stack " +
                                      slot.getStack());

      HotbarAssignmentManager.expectedCraftingItemCount--;

      if (HotbarAssignmentManager.expectedCraftingItemCount <= 0) {
        HotbarAssignmentManager.resetDoingInventoryCraftingCalculations();
      }
      return;
    }

    // Assign the new item into its slot
    new HotbarAssignmentManager(
        client.player.getInventory(), slot,
        HotbarAssignmentManager.ActionType.ITEM_PICKING_UP,
        client.player.currentScreenHandler, true, false)
        .assignItem();
  }
}
