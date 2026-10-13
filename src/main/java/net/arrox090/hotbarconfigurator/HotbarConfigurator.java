package net.arrox090.hotbarconfigurator;

import static net.arrox090.hotbarconfigurator.manager.ItemRefillManager.refillAllHotbarSlots;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.arrox090.hotbarconfigurator.config.HotbarConfiguratorConfig;
import net.arrox090.hotbarconfigurator.manager.HotbarAssignmentManager;
import net.arrox090.hotbarconfigurator.manager.ProfileManager;
import net.arrox090.hotbarconfigurator.util.ConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public class HotbarConfigurator implements ClientModInitializer {
  public static final String MOD_ID = "hotbarconfigurator";

  // Create a logger
  public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

  private String lastDimension = null;

  private static KeyBinding sortHotbarKey;
  private static KeyBinding cycleProfilesKey;
  private static KeyBinding setProfileOneKey;
  private static KeyBinding setProfileTwoKey;
  private static KeyBinding setProfileThreeKey;
  private static KeyBinding refillAllHotbarSlots;

  @Override
  public void onInitializeClient() {

    // run the tick HotbarAssignmentManager.tick() command at the end of every
    // client tick
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          HotbarAssignmentManager.tick();
        });

    AutoConfig.register(HotbarConfiguratorConfig.class,
        GsonConfigSerializer::new);

    // Initialize config
    ConfigManager.refreshCachedConfig();
    ConfigManager.registerSaveListener();

    // listen for world (dimension) changes on the client:
    ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
      String newDim = world.getRegistryKey().getValue().toString();

      // If actually dimension was changed...
      if (!newDim.equals(lastDimension) && client.player != null) {

        // ...change to new dimension's profile if any
        ProfileManager.onDimensionChange(client.player.getInventory(), newDim);

        // and update lastDimension
        lastDimension = newDim;

        LOGGER.debug("Player changed dimension to " + newDim);
      }
    });

    // Register listener for world disconnect to reset the lastDimension, so if
    // the player disconnects in a dimension, changes it, and joins another
    // world it doesnt carry the last dimension from the old world
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          lastDimension = null;
        });

    // Create key bindings
    sortHotbarKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.hotbarconfigurator.sortHotbar", InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_PERIOD, "category.hotbarconfigurator"));
    cycleProfilesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.hotbarconfigurator.cycleProfiles", InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN, "category.hotbarconfigurator"));
    setProfileOneKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.hotbarconfigurator.activateProfileOne", InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN, "category.hotbarconfigurator"));
    setProfileTwoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.hotbarconfigurator.activateProfileTwo", InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN, "category.hotbarconfigurator"));
    setProfileThreeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.hotbarconfigurator.activateProfileThree", InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN, "category.hotbarconfigurator"));
    refillAllHotbarSlots = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.hotbarconfigurator.refillAllHotbarSlots", InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_COMMA, "category.hotbarconfigurator"));

    // Listen for key presses at the end of each client tick
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      if (client.currentScreen == null) {

        // And run the appropriate function
        while (sortHotbarKey.wasPressed()) {
          if (client.player != null) {
            ProfileManager.sortHotbar(client.player.getInventory());
          }
        }
        while (cycleProfilesKey.wasPressed()) {
          if (client.player != null) {
            ProfileManager.cycleProfiles(client.player.getInventory());
          }
        }
        while (setProfileOneKey.wasPressed()) {
          if (client.player != null) {
            ProfileManager.activateProfile(client.player.getInventory(),
                "PROFILE1");
          }
        }
        while (setProfileTwoKey.wasPressed()) {
          if (client.player != null) {
            ProfileManager.activateProfile(client.player.getInventory(),
                "PROFILE2");
          }
        }
        while (setProfileThreeKey.wasPressed()) {
          if (client.player != null) {
            ProfileManager.activateProfile(client.player.getInventory(),
                "PROFILE3");
          }
        }
        while (refillAllHotbarSlots.wasPressed()) {
          if (client.player != null) {
            refillAllHotbarSlots(client.player.getInventory());
          }
        }
      }
    });

    LOGGER.info("Hotbar Configurator initialized successfully!");
  }
}
