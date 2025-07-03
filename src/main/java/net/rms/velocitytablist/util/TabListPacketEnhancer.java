package net.rms.velocitytablist.util;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.protocol.packet.PlayerListItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.rms.velocitytablist.config.TabListConfig;
import net.rms.velocitytablist.manager.CrossServerInfoManager;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TabListPacketEnhancer {
    
    private final CrossServerInfoManager crossServerManager;
    private final TabListConfig config;
    private final Logger logger;
    
    private final Map<UUID, Set<UUID>> lastSentCrossServerEntries = new ConcurrentHashMap<>();
    private final PacketActionAnalyzer actionAnalyzer;
    private final VirtualEntryGenerator entryGenerator;
    
    public TabListPacketEnhancer(CrossServerInfoManager crossServerManager, 
                                TabListConfig config, Logger logger) {
        this.crossServerManager = crossServerManager;
        this.config = config;
        this.logger = logger;
        
        this.actionAnalyzer = new PacketActionAnalyzer(config);
        this.entryGenerator = new VirtualEntryGenerator(config, logger);
    }
    
    public PlayerListItem enhancePacket(PlayerListItem originalPacket, 
                                       VelocityServerConnection currentConnection,
                                       Player targetPlayer) {
        
        if (!config.isCrossServerEnabled()) {
            return originalPacket;
        }
        
        try {
            // 检查是否应该添加跨服务器信息
            if (!actionAnalyzer.shouldAddCrossServerInfo(originalPacket.getAction())) {
                return originalPacket;
            }
            
            // 检查数据包大小是否合理
            if (!actionAnalyzer.isSafeToModify(originalPacket)) {
                logger.debug("跳过修改 PlayerListItem 数据包 - 大小不安全或存在未知修改");
                return originalPacket;
            }
            
            // 生成跨服务器条目
            List<PlayerListItem.Item> crossServerItems = entryGenerator.generateCrossServerEntries(
                    currentConnection, crossServerManager.getServerPlayerMap()
            );
            
            if (crossServerItems.isEmpty()) {
                return originalPacket;
            }
            
            // 合并原始数据包和跨服务器条目
            return mergePackets(originalPacket, crossServerItems, targetPlayer);
            
        } catch (Exception e) {
            logger.error("增强 PlayerListItem 数据包时发生错误", e);
            return originalPacket; // 发生错误时返回原始数据包
        }
    }
    
    private PlayerListItem mergePackets(PlayerListItem originalPacket, 
                                       List<PlayerListItem.Item> crossServerItems,
                                       Player targetPlayer) {
        
        List<PlayerListItem.Item> mergedItems = new ArrayList<>();
        
        // 首先添加所有原始条目（包括carpet等模组信息）
        mergedItems.addAll(originalPacket.getItems());
        
        // 如果是ADD_PLAYER操作，添加跨服务器信息
        if (originalPacket.getAction() == PlayerListItem.ADD_PLAYER) {
            
            if (config.isEnableIncrementalUpdates()) {
                // 增量更新模式
                Set<UUID> currentCrossServerUUIDs = extractUUIDs(crossServerItems);
                Set<UUID> lastSentUUIDs = lastSentCrossServerEntries.getOrDefault(
                        targetPlayer.getUniqueId(), new HashSet<>()
                );
                
                // 只添加新的条目
                Set<UUID> toAdd = new HashSet<>(currentCrossServerUUIDs);
                toAdd.removeAll(lastSentUUIDs);
                
                crossServerItems = crossServerItems.stream()
                        .filter(item -> toAdd.contains(item.getProfile().getId()))
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
                
                // 更新缓存
                lastSentCrossServerEntries.put(targetPlayer.getUniqueId(), currentCrossServerUUIDs);
            }
            
            // 在合适的位置插入跨服务器信息
            insertCrossServerItems(mergedItems, crossServerItems);
        }
        
        // 检查最终大小
        if (mergedItems.size() > config.getMaxTabListSize()) {
            logger.warn("合并后的tab列表过大 ({}), 限制为 {}", 
                       mergedItems.size(), config.getMaxTabListSize());
            mergedItems = mergedItems.subList(0, config.getMaxTabListSize());
        }
        
        return new PlayerListItem(originalPacket.getAction(), mergedItems);
    }
    
    private void insertCrossServerItems(List<PlayerListItem.Item> mergedItems, 
                                       List<PlayerListItem.Item> crossServerItems) {
        if (crossServerItems.isEmpty()) {
            return;
        }
        
        // 寻找插入位置（在真实玩家和模组信息之间）
        int insertPosition = findInsertPosition(mergedItems);
        mergedItems.addAll(insertPosition, crossServerItems);
    }
    
    private int findInsertPosition(List<PlayerListItem.Item> items) {
        for (int i = 0; i < items.size(); i++) {
            PlayerListItem.Item item = items.get(i);
            if (actionAnalyzer.isCarpetOrModInfo(item)) {
                return i; // 在模组信息之前插入
            }
        }
        return items.size(); // 默认在末尾插入
    }
    
    private Set<UUID> extractUUIDs(List<PlayerListItem.Item> items) {
        Set<UUID> uuids = new HashSet<>();
        for (PlayerListItem.Item item : items) {
            uuids.add(item.getProfile().getId());
        }
        return uuids;
    }
    
    public void sendFullTabListUpdate(Player player) {
        try {
            // 实现完整的tab列表更新逻辑
            Optional<ServerConnection> serverConnection = player.getCurrentServer();
            if (serverConnection.isPresent()) {
                VelocityServerConnection connection = (VelocityServerConnection) serverConnection.get();
                
                // 生成完整的跨服务器条目列表
                List<PlayerListItem.Item> crossServerItems = entryGenerator.generateCrossServerEntries(
                        connection, crossServerManager.getServerPlayerMap()
                );
                
                if (!crossServerItems.isEmpty()) {
                    // 发送ADD_PLAYER数据包来添加跨服务器条目
                    PlayerListItem packet = new PlayerListItem(PlayerListItem.ADD_PLAYER, crossServerItems);
                    player.getConnection().write(packet);
                    
                    // 更新缓存
                    lastSentCrossServerEntries.put(player.getUniqueId(), extractUUIDs(crossServerItems));
                }
            }
            
        } catch (Exception e) {
            logger.error("发送完整tab列表更新时发生错误", e);
        }
    }
    
    public void clearPlayerCache(Player player) {
        lastSentCrossServerEntries.remove(player.getUniqueId());
    }
}