package net.rms.velocitytablist.util;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.PlayerListItem;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.rms.velocitytablist.config.TabListConfig;
import net.rms.velocitytablist.manager.CrossServerInfoManager;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PacketInterceptor {
    
    private final Player player;
    private final CrossServerInfoManager crossServerManager;
    private final TabListConfig config;
    private final Logger logger;
    
    private TabListPacketEnhancer enhancer;
    private PlayerListInterceptHandler interceptHandler;
    
    public PacketInterceptor(Player player, CrossServerInfoManager crossServerManager,
                           TabListConfig config, Logger logger) {
        this.player = player;
        this.crossServerManager = crossServerManager;
        this.config = config;
        this.logger = logger;
        
        this.enhancer = new TabListPacketEnhancer(crossServerManager, config, logger);
    }
    
    public void register() {
        try {
            ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
            MinecraftConnection connection = connectedPlayer.getConnection();
            
            if (connection != null && connection.getChannel() != null) {
                interceptHandler = new PlayerListInterceptHandler();
                connection.getChannel().pipeline().addBefore("handler", "tablist-interceptor", interceptHandler);
                logger.debug("已为玩家 {} 注册数据包拦截器", player.getUsername());
            }
            
        } catch (Exception e) {
            logger.error("注册数据包拦截器时发生错误", e);
        }
    }
    
    public void unregister() {
        try {
            ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
            MinecraftConnection connection = connectedPlayer.getConnection();
            
            if (connection != null && connection.getChannel() != null && interceptHandler != null) {
                if (connection.getChannel().pipeline().get("tablist-interceptor") != null) {
                    connection.getChannel().pipeline().remove("tablist-interceptor");
                }
                logger.debug("已为玩家 {} 注销数据包拦截器", player.getUsername());
            }
            
        } catch (Exception e) {
            logger.error("注销数据包拦截器时发生错误", e);
        }
    }
    
    public void refreshTabList() {
        try {
            // 强制刷新跨服务器信息
            crossServerManager.updateServerInfo();
            
            // 发送完整的tab列表更新
            CompletableFuture.runAsync(() -> {
                try {
                    enhancer.sendFullTabListUpdate(player);
                } catch (Exception e) {
                    logger.error("刷新玩家 {} 的tab列表时发生错误", player.getUsername(), e);
                }
            });
            
        } catch (Exception e) {
            logger.error("刷新tab列表时发生错误", e);
        }
    }
    
    private class PlayerListInterceptHandler extends ChannelDuplexHandler {
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // 拦截从服务器发来的PlayerListItem数据包
            if (msg instanceof PlayerListItem && config.isCrossServerEnabled()) {
                PlayerListItem packet = (PlayerListItem) msg;
                
                try {
                    // 获取当前服务器连接
                    Optional<ServerConnection> serverConnection = player.getCurrentServer();
                    if (serverConnection.isPresent()) {
                        VelocityServerConnection connection = (VelocityServerConnection) serverConnection.get();
                        
                        // 增强数据包
                        PlayerListItem enhancedPacket = enhancer.enhancePacket(packet, connection, player);
                        
                        if (enhancedPacket != null) {
                            // 发送增强后的数据包
                            super.channelRead(ctx, enhancedPacket);
                            return;
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理PlayerListItem数据包时发生错误", e);
                    // 发生错误时，发送原始数据包
                }
            }
            
            // 对于其他数据包或处理失败的情况，直接传递
            super.channelRead(ctx, msg);
        }
        
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            // 监控发送到客户端的数据包（如果需要的话）
            super.write(ctx, msg, promise);
        }
    }
}