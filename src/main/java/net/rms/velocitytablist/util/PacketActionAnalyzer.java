package net.rms.velocitytablist.util;

import com.velocitypowered.proxy.protocol.packet.PlayerListItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.rms.velocitytablist.config.TabListConfig;

public class PacketActionAnalyzer {
    
    private final TabListConfig config;
    
    public PacketActionAnalyzer(TabListConfig config) {
        this.config = config;
    }
    
    public boolean shouldAddCrossServerInfo(int action) {
        switch (action) {
            case PlayerListItem.ADD_PLAYER:
                // 玩家加入时，重建完整列表
                return true;
            case PlayerListItem.REMOVE_PLAYER:
                // 玩家离开时，可能需要清理跨服务器条目
                return false; // 暂时不在移除时添加，避免混乱
            case PlayerListItem.UPDATE_DISPLAY_NAME:
            case PlayerListItem.UPDATE_LATENCY:
            case PlayerListItem.UPDATE_GAMEMODE:
                // 更新操作时，不添加跨服务器信息（避免干扰）
                return false;
            default:
                return false;
        }
    }
    
    public boolean isCarpetOrModInfo(PlayerListItem.Item item) {
        if (!config.isPreserveModInfo()) {
            return false;
        }
        
        try {
            // 检查名称
            String name = item.getProfile().getName();
            if (name != null && !name.isEmpty()) {
                String lowerName = name.toLowerCase();
                if (lowerName.contains("tps") || lowerName.contains("mspt") || 
                    lowerName.contains("carpet") || lowerName.contains("entity") ||
                    lowerName.contains("tile")) {
                    return true;
                }
            }
            
            // 检查显示名称
            Component displayName = item.getDisplayName();
            if (displayName != null) {
                String displayText = PlainTextComponentSerializer.plainText().serialize(displayName);
                if (displayText != null && !displayText.isEmpty()) {
                    String lowerDisplay = displayText.toLowerCase();
                    return lowerDisplay.contains("tps") || lowerDisplay.contains("mspt") || 
                           lowerDisplay.contains("实体") || lowerDisplay.contains("方块") ||
                           lowerDisplay.contains("carpet") || lowerDisplay.contains("entity") ||
                           lowerDisplay.contains("tile") || lowerDisplay.contains("chunk") ||
                           lowerDisplay.contains("mob") || lowerDisplay.contains("redstone");
                }
            }
            
            // 检查特殊的UUID模式（一些模组使用特定的UUID格式）
            String uuid = item.getProfile().getId().toString();
            if (uuid.startsWith("00000000-") || uuid.endsWith("-0000-0000-000000000000")) {
                return true;
            }
            
        } catch (Exception e) {
            // 如果检查过程中出现异常，保守地认为是模组信息
            return true;
        }
        
        return false;
    }
    
    public boolean isSafeToModify(PlayerListItem packet) {
        try {
            // 检查数据包大小是否合理
            if (packet.getItems().size() > config.getMaxTabListSize() / 2) {
                return false;
            }
            
            // 检查是否存在过多的未知条目
            int unknownItems = 0;
            for (PlayerListItem.Item item : packet.getItems()) {
                if (hasUnknownModifications(item)) {
                    unknownItems++;
                }
            }
            
            // 如果超过50%的条目都是未知的，可能存在兼容性问题
            return unknownItems < packet.getItems().size() / 2;
            
        } catch (Exception e) {
            // 发生异常时保守处理
            return false;
        }
    }
    
    private boolean hasUnknownModifications(PlayerListItem.Item item) {
        try {
            // 检查是否有异常的属性组合
            
            // 1. 检查UUID是否为null或无效
            if (item.getProfile().getId() == null) {
                return true;
            }
            
            // 2. 检查名称是否异常长
            String name = item.getProfile().getName();
            if (name != null && name.length() > 16) { // Minecraft玩家名最长16字符
                return true;
            }
            
            // 3. 检查显示名称是否异常长
            Component displayName = item.getDisplayName();
            if (displayName != null) {
                String displayText = PlainTextComponentSerializer.plainText().serialize(displayName);
                if (displayText != null && displayText.length() > 256) { // 合理的显示名称长度限制
                    return true;
                }
            }
            
            // 4. 检查延迟值是否合理
            int latency = item.getLatency();
            if (latency < 0 || latency > 30000) { // 30秒延迟已经很异常了
                return true;
            }
            
            // 5. 检查游戏模式是否有效
            int gameMode = item.getGameMode();
            if (gameMode < 0 || gameMode > 3) { // 0-3是有效的游戏模式
                return true;
            }
            
        } catch (Exception e) {
            return true;
        }
        
        return false;
    }
    
    public boolean isVirtualEntry(PlayerListItem.Item item) {
        try {
            String name = item.getProfile().getName();
            String uuid = item.getProfile().getId().toString();
            
            // 检查是否是我们生成的虚拟条目
            return (name != null && name.startsWith("cross_")) ||
                   uuid.contains("velocitytablist") ||
                   (name != null && name.isEmpty() && item.getDisplayName() != null);
            
        } catch (Exception e) {
            return false;
        }
    }
}