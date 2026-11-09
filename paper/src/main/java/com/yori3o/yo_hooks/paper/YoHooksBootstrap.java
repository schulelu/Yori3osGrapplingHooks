package com.yori3o.yo_hooks.paper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;

/** Runs before the server loads its registries: the mod's enchantments have to exist by then. */
public final class YoHooksBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        Enchants.bootstrap(context);
    }
}
