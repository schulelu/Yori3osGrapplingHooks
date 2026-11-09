package com.yori3o.yo_hooks.paper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The optional resource pack: offered on join (players may decline), and remembered per player once it loaded, so
 * sounds can use the pack's sounds for them. The build bakes the release URL and sha1 into yohooks-pack.properties.
 */
final class Pack implements Listener {

    private final YoHooks plugin;
    private final Set<UUID> loaded = new HashSet<>();
    private @Nullable ResourcePackInfo info;

    Pack(YoHooks plugin) {
        this.plugin = plugin;
    }

    void load() {
        info = null;
        Settings cfg = plugin.settings();
        if (!cfg.packEnabled) {
            return;
        }
        String url = cfg.packUrl;
        String sha1 = cfg.packSha1;
        if (url.isEmpty()) {
            Properties p = new Properties();
            try (InputStream in = plugin.getResource("yohooks-pack.properties")) {
                if (in != null) {
                    p.load(in);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("could not read the bundled pack info: " + e);
            }
            url = p.getProperty("url", "");
            sha1 = p.getProperty("sha1", "");
        }
        if (url.isEmpty() || !sha1.matches("[0-9a-f]{40}")) {
            plugin.getLogger().warning("resource pack disabled: no URL or sha1 (set resource-pack.url and .sha1)");
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(("yo_hooks:" + sha1).getBytes(StandardCharsets.UTF_8));
        info = ResourcePackInfo.resourcePackInfo(id, URI.create(url), sha1);
    }

    boolean loaded(Player p) {
        return loaded.contains(p.getUniqueId());
    }

    void offer(Player p) {
        if (info == null) {
            return;
        }
        String prompt = plugin.settings().packPrompt;
        p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
            .packs(info).required(false).replace(false)
            .prompt(prompt.isEmpty() ? null : Component.text(prompt)));
    }

    @EventHandler
    void onJoin(PlayerJoinEvent e) {
        offer(e.getPlayer());
    }

    @EventHandler
    void onStatus(PlayerResourcePackStatusEvent e) {
        if (info == null || !info.id().equals(e.getID())) {
            return;
        }
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(e.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> loaded.remove(e.getPlayer().getUniqueId());
            default -> { }
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
    }
}
