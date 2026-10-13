package net.arrox090.hotbarconfigurator.mixin;

import net.arrox090.hotbarconfigurator.manager.HotbarAssignmentManager;
import net.arrox090.hotbarconfigurator.util.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// For quick move
@Mixin(HandledScreen.class)
public class HandledScreenMixin {

  /**
   * Inject into method called when a player clicks a mouse inside a screen
   * (head - before anything gets executed)
   */
  @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/"
                   + "minecraft/screen/slot/SlotActionType;)V",
          at = @At("HEAD"), cancellable = true)
  private void
  beforeOnMouseClick(Slot slot, int slotId, int button,
                     SlotActionType actionType, CallbackInfo ci) {

    // If clicked a slot which is not empty, and with quick move action only
    // then proceed
    if (slot != null && actionType == SlotActionType.QUICK_MOVE &&
        slot.hasStack()) {

      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null || client.interactionManager == null)
        return;

      HandledScreen<?> self = (HandledScreen<?>)(Object)this;

      // Check which screen did the player click in.
      // if inventory (only counting the backpack and crafting result slots)...
      if (self instanceof InventoryScreen &&
          ConfigManager.getCachedConfig()
              .enabledForScenarios.quickMovePlayerInventory &&
          ((slotId >= PlayerScreenHandler.EQUIPMENT_END &&
            slotId < PlayerInventory.MAIN_SIZE) ||
           slotId == PlayerScreenHandler.CRAFTING_RESULT_ID)) {

        // ...assign the item with player inventory action type (bypass config
        // for auto equip as we are moving from player inventory)
        new HotbarAssignmentManager(
            client.player.getInventory(), slot,
            HotbarAssignmentManager.ActionType.PLAYER_INVENTORY,
            self.getScreenHandler(), true, true)
            .assignItem();

        // and cancel the vanilla quick move action
        ci.cancel();

        // If not player inventory screen, and clicked out side of the player
        // inventory
      } else if (!(self instanceof InventoryScreen) &&
                 slot.inventory != client.player.getInventory() &&
                 checkConfig(self, slotId)) {

        // Assign the item with player inventory action type (no armor equip)
        new HotbarAssignmentManager(client.player.getInventory(), slot,
                                    HotbarAssignmentManager.ActionType.OTHER,
                                    self.getScreenHandler(), false)
            .assignItem();

        // Cancel the vanilla quick move action
        ci.cancel();
      }
    }
  }

  /** check if given screen is enabled for quickmove action */
  @Unique
  private boolean checkConfig(HandledScreen<?> screen, int slotIndex) {
    if (screen instanceof GenericContainerScreen) {
      return ConfigManager.getCachedConfig()
          .enabledForScenarios.quickMoveGenericContainers;
    }

    if (screen instanceof FurnaceScreen || screen instanceof SmokerScreen ||
        screen instanceof BlastFurnaceScreen) {
      return ConfigManager.getCachedConfig()
          .enabledForScenarios.quickMoveFurnaces;
    }

    if (screen instanceof ShulkerBoxScreen) {
      return ConfigManager.getCachedConfig()
          .enabledForScenarios.quickMoveShulkerBoxes;
    }

    if (screen instanceof CraftingScreen) {
      // Only for crafting check the slot index is 0, so dont use it for the
      // crafting grid
      return slotIndex == 0 && ConfigManager.getCachedConfig()
                                   .enabledForScenarios.quickMoveCraftingTable;
    }

    // For modded screens or just anything else
    return ConfigManager.getCachedConfig().enabledForScenarios.quickMoveOther;
  }
}
