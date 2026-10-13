package net.arrox090.hotbarconfigurator.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.shedaniel.autoconfig.AutoConfig;
import net.arrox090.hotbarconfigurator.HotbarConfigurator;
import net.arrox090.hotbarconfigurator.config.HotbarConfiguratorConfig;
import net.minecraft.util.ActionResult;

public class ConfigManager {
  private static HotbarConfiguratorConfig cachedConfig;

  /** Refresh config */
  public static void refreshCachedConfig() {
    cachedConfig =
        AutoConfig.getConfigHolder(HotbarConfiguratorConfig.class).getConfig();
    preventDuplicatedDimensions();
  }

  /** Listener for updates made to the config */
  public static void registerSaveListener() {
    AutoConfig.getConfigHolder(HotbarConfiguratorConfig.class)
        .registerSaveListener((holder, config) -> {
          refreshCachedConfig();
          HotbarConfigurator.LOGGER.info(
              "HotbarConfiguratorConfig reloaded from disk");
          return ActionResult.SUCCESS;
        });
  }

  /** Getter for config */
  public static HotbarConfiguratorConfig getCachedConfig() {
    return cachedConfig;
  }

  /** Make sure no dimension is assigned to more than one profile */
  private static void preventDuplicatedDimensions() {

    // Set of seen dimensions
    Set<String> seen = new HashSet<>();

    // Go through every profile
    for (var entry : ConfigManager.getCachedConfig().getProfiles().entrySet()) {

      // Get profile's settings
      HotbarConfiguratorConfig.ProfileSettings profileSettings =
          entry.getValue();

      // List of dimensions to remove
      List<String> toRemove = new ArrayList<>();

      // Check all profile's dimensions
      for (String dimension : profileSettings.dimensions) {

        // Add to seen, and if its already in seen...
        if (!seen.add(dimension)) {

          // ...log a warning
          String name = entry.getKey().toString();
          HotbarConfigurator.LOGGER.warn(
              "Dimension {} assigned to multiple "
                  + "profiles; removing from profile {}",
              dimension, name);

          // and add dimension to remove
          toRemove.add(dimension);
        }
      }

      // Remove all dimensions that were duplicates leaving only one of each
      profileSettings.dimensions.removeAll(toRemove);
    }
  }
}
