package net.arrox090.hotbarconfigurator.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Integrates the mod with the ModMenu API. This class provides the entry point
 * for ModMenu to generate and display the mod's configuration screen directly
 * from the in-game mod list.
 */
@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

  /**
   * Provides the factory used to create the configuration screen. Delegates the
   * screen generation to Cloth Config's AutoConfig API.
   *
   * @return A factory that generates the Hotbar Configurator screen.
   */
  @Override
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    return parent -> AutoConfig.getConfigScreen(HotbarConfiguratorConfig.class, parent)
        .get();
  }
}
