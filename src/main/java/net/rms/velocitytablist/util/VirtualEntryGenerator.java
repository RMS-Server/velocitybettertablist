package net.rms.velocitytablist.util;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.protocol.packet.PlayerListItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.rms.velocitytablist.config.TabListConfig;
import org.slf4j.Logger;

import java.util.*;

public class VirtualEntryGenerator {
    
    private final TabListConfig config;
    private final Logger logger;
    private final UUIDGenerator uuidGenerator;
    
    public VirtualEntryGenerator(TabListConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.uuidGenerator = new UUIDGenerator();
    }
    
    public List<PlayerListItem.Item> generateCrossServerEntries(
            VelocityServerConnection currentConnection,
            Map<RegisteredServer, List<Player>> serverPlayerMap) {
        
        List<PlayerListItem.Item> items = new ArrayList<>();
        String currentServerName = currentConnection.getServerInfo().getName();
        
        try {
            // 1. 添加分隔符
            items.add(createSeparatorItem());
            
            // 2. 遍历所有服务器
            for (Map.Entry<RegisteredServer, List<Player>> entry : serverPlayerMap.entrySet()) {
                RegisteredServer server = entry.getKey();
                List<Player> players = entry.getValue();
                
                // 跳过当前服务器（由后端服务器自己管理）
                if (server.getServerInfo().getName().equals(currentServerName)) {
                    continue;
                }
                
                // 添加服务器标题
                items.add(createServerHeaderItem(server, players.size()));
                
                // 添加该服务器的玩家（限制数量）
                int playerCount = 0;
                for (Player player : players) {
                    if (playerCount >= config.getMaxPlayersPerServer()) {
                        // 如果玩家数量超过限制，添加省略号条目
                        items.add(createMorePlayersItem(players.size() - playerCount));
                        break;
                    }
                    items.add(createCrossServerPlayerItem(player, server));
                    playerCount++;
                }
                
                // 如果服务器没有玩家，添加空服务器提示
                if (players.isEmpty()) {
                    items.add(createEmptyServerItem());
                }
            }
            
            // 3. 添加结束分隔符
            items.add(createEndSeparatorItem());
            
        } catch (Exception e) {
            logger.error("生成跨服务器条目时发生错误", e);
            return new ArrayList<>(); // 返回空列表避免崩溃
        }
        
        return items;
    }
    
    private PlayerListItem.Item createSeparatorItem() {
        UUID virtualUUID = uuidGenerator.generateVirtualUUID("separator_start");
        Component displayName = LegacyComponentSerializer.legacySection().deserialize(config.getSeparatorText());
        
        return new PlayerListItem.Item(
                GameProfile.forOfflinePlayer("", virtualUUID),
                3, // 旁观者模式
                0, // 延迟
                displayName,
                null
        );
    }
    
    private PlayerListItem.Item createEndSeparatorItem() {
        UUID virtualUUID = uuidGenerator.generateVirtualUUID("separator_end");
        Component displayName = LegacyComponentSerializer.legacySection().deserialize("§6§l====================");
        
        return new PlayerListItem.Item(
                GameProfile.forOfflinePlayer("", virtualUUID),
                3, // 旁观者模式
                0, // 延迟
                displayName,
                null
        );
    }
    
    private PlayerListItem.Item createServerHeaderItem(RegisteredServer server, int playerCount) {
        UUID virtualUUID = uuidGenerator.generateVirtualUUID("server_" + server.getServerInfo().getName());
        
        String displayText = String.format(
                config.getServerHeaderFormat(),
                server.getServerInfo().getName(),
                playerCount
        );
        
        Component displayName = LegacyComponentSerializer.legacySection().deserialize(displayText);
        
        return new PlayerListItem.Item(
                GameProfile.forOfflinePlayer("", virtualUUID),
                3, // 旁观者模式
                0, // 延迟
                displayName,
                null
        );
    }
    
    private PlayerListItem.Item createCrossServerPlayerItem(Player player, RegisteredServer server) {
        UUID virtualUUID = uuidGenerator.generateVirtualUUID("cross_" + player.getUniqueId().toString());
        
        String displayText = String.format(
                config.getCrossServerPlayerFormat(),
                player.getUsername(),
                server.getServerInfo().getName()
        );
        
        Component displayName = LegacyComponentSerializer.legacySection().deserialize(displayText);
        
        return new PlayerListItem.Item(
                GameProfile.forOfflinePlayer("cross_" + player.getUsername(), virtualUUID),
                player.getGameModeId(),
                (int) player.getPing(),
                displayName,
                null
        );
    }
    
    private PlayerListItem.Item createEmptyServerItem() {
        UUID virtualUUID = uuidGenerator.generateVirtualUUID("empty_server");
        Component displayName = LegacyComponentSerializer.legacySection().deserialize("§8├ (无玩家在线)");
        
        return new PlayerListItem.Item(
                GameProfile.forOfflinePlayer("", virtualUUID),
                3, // 旁观者模式
                0, // 延迟
                displayName,
                null
        );
    }
    
    private PlayerListItem.Item createMorePlayersItem(int remainingCount) {
        UUID virtualUUID = uuidGenerator.generateVirtualUUID("more_players");
        String displayText = String.format("§8├ ... 还有 %d 名玩家", remainingCount);
        Component displayName = LegacyComponentSerializer.legacySection().deserialize(displayText);
        
        return new PlayerListItem.Item(
                GameProfile.forOfflinePlayer("", virtualUUID),
                3, // 旁观者模式
                0, // 延迟
                displayName,
                null
        );
    }
    
    public PlayerListItem.Item createStatusItem(String serverName, String status) {
        UUID virtualUUID = uuidGenerator.generateVirtualUUID("status_" + serverName);
        String displayText = String.format("§7├ 状态: %s", status);
        Component displayName = LegacyComponentSerializer.legacySection().deserialize(displayText);
        
        return new PlayerListItem.Item(
                GameProfile.forOfflinePlayer("", virtualUUID),
                3, // 旁观者模式
                0, // 延迟
                displayName,
                null
        );
    }
}