package net.rms.velocitytablist.handler;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.rms.velocitytablist.VelocityTabListPlugin;
import net.rms.velocitytablist.config.TabListConfig;
import net.rms.velocitytablist.manager.CrossServerInfoManager;
import net.rms.velocitytablist.util.TabListUpdater;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TabListPacketHandler {
    
    private final VelocityTabListPlugin plugin;
    private final ProxyServer server;
    private final TabListConfig config;
    private final CrossServerInfoManager infoManager;
    
    private final ConcurrentMap<Player, TabListUpdater> playerUpdaters = new ConcurrentHashMap<>();
    
    public TabListPacketHandler(VelocityTabListPlugin plugin, ProxyServer server, 
                              TabListConfig config, CrossServerInfoManager infoManager) {
        this.plugin = plugin;
        this.server = server;
        this.config = config;
        this.infoManager = infoManager;
    }
    
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        
        if (config.isCrossServerEnabled()) {
            // 为玩家创建Tab列表更新器
            TabListUpdater updater = new TabListUpdater(player, plugin, infoManager);
            playerUpdaters.put(player, updater);
            
            // 延迟初始化Tab列表
            server.getScheduler().buildTask(plugin, () -> updater.updateTabList())
                .delay(java.time.Duration.ofSeconds(1))
                .schedule();
        }
    }
    
    @Subscribe
    public void onServerConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        
        if (config.isCrossServerEnabled()) {
            TabListUpdater updater = playerUpdaters.get(player);
            if (updater != null) {
                // 服务器切换时更新Tab列表
                server.getScheduler().buildTask(plugin, () -> updater.updateTabList())
                    .delay(java.time.Duration.ofMillis(500))
                    .schedule();
            }
        }
    }
    
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        
        // 清理更新器
        TabListUpdater updater = playerUpdaters.remove(player);
        if (updater != null) {
            updater.cleanup();
        }
    }
    
    public void updateAllTabLists() {
        // 更新所有玩家的Tab列表
        playerUpdaters.values().forEach(TabListUpdater::updateTabList);
    }
    
    public void handlePluginMessage(Player player, String channel, byte[] data) {
        // 处理插件消息，用于与后端服务器通信
        if ("velocitytablist:sync".equals(channel)) {
            // 处理同步请求
            infoManager.requestSync();
        }
    }
    
    public void shutdown() {
        // 清理所有更新器
        playerUpdaters.values().forEach(TabListUpdater::cleanup);
        playerUpdaters.clear();
    }
}