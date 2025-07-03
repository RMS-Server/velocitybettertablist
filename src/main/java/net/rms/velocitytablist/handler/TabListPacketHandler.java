package net.rms.velocitytablist.handler;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import net.rms.velocitytablist.config.TabListConfig;
import net.rms.velocitytablist.manager.CrossServerInfoManager;
import net.rms.velocitytablist.util.PacketInterceptor;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TabListPacketHandler {
    
    private final ProxyServer server;
    private final CrossServerInfoManager crossServerManager;
    private final TabListConfig config;
    private final Logger logger;
    
    private final ConcurrentMap<Player, PacketInterceptor> playerInterceptors = new ConcurrentHashMap<>();
    
    public TabListPacketHandler(ProxyServer server, CrossServerInfoManager crossServerManager,
                               TabListConfig config, Logger logger) {
        this.server = server;
        this.crossServerManager = crossServerManager;
        this.config = config;
        this.logger = logger;
    }
    
    @Subscribe
    public void onPlayerServerConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        
        if (!config.isCrossServerEnabled()) {
            return;
        }
        
        try {
            // 为每个玩家创建数据包拦截器
            PacketInterceptor interceptor = new PacketInterceptor(
                    player, 
                    crossServerManager, 
                    config, 
                    logger
            );
            
            playerInterceptors.put(player, interceptor);
            
            // 注册数据包拦截
            interceptor.register();
            
            logger.debug("为玩家 {} 创建了数据包拦截器", player.getUsername());
            
        } catch (Exception e) {
            logger.error("为玩家 {} 创建数据包拦截器时发生错误", player.getUsername(), e);
        }
    }
    
    @Subscribe
    public void onPlayerDisconnect(com.velocitypowered.api.event.connection.DisconnectEvent event) {
        Player player = event.getPlayer();
        
        // 清理数据包拦截器
        PacketInterceptor interceptor = playerInterceptors.remove(player);
        if (interceptor != null) {
            interceptor.unregister();
            logger.debug("为玩家 {} 清理了数据包拦截器", player.getUsername());
        }
    }
    
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // 处理插件消息通道（如果需要的话）
        if (event.getIdentifier().getId().equals("velocitytablist:update")) {
            // 处理来自后端服务器的更新请求
            Optional<ServerConnection> connection = event.getSource().as(ServerConnection.class);
            if (connection.isPresent()) {
                crossServerManager.requestUpdate(connection.get().getServerInfo());
            }
        }
    }
    
    public void refreshPlayerTabList(Player player) {
        PacketInterceptor interceptor = playerInterceptors.get(player);
        if (interceptor != null) {
            interceptor.refreshTabList();
        }
    }
    
    public void refreshAllPlayers() {
        playerInterceptors.values().forEach(PacketInterceptor::refreshTabList);
    }
}