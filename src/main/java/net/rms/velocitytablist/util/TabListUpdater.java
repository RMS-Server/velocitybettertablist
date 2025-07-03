package net.rms.velocitytablist.util;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rms.velocitytablist.VelocityTabListPlugin;
import net.rms.velocitytablist.config.TabListConfig;
import net.rms.velocitytablist.manager.CrossServerInfoManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TabListUpdater {
    
    private final Player player;
    private final VelocityTabListPlugin plugin;
    private final CrossServerInfoManager infoManager;
    private final TabListConfig config;
    private final UUIDGenerator uuidGenerator;
    
    private final ConcurrentMap<UUID, TabListEntry> virtualEntries = new ConcurrentHashMap<>();
    private volatile boolean isUpdating = false;
    
    public TabListUpdater(Player player, VelocityTabListPlugin plugin, CrossServerInfoManager infoManager) {
        this.player = player;
        this.plugin = plugin;
        this.infoManager = infoManager;
        this.config = plugin.getConfig();
        this.uuidGenerator = new UUIDGenerator();
    }
    
    public void updateTabList() {
        if (isUpdating || !config.isCrossServerEnabled()) {
            return;
        }
        
        isUpdating = true;
        try {
            TabList tabList = player.getTabList();
            
            // 清理旧的虚拟条目
            cleanupVirtualEntries(tabList);
            
            // 添加跨服务器信息
            addCrossServerEntries(tabList);
            
        } catch (Exception e) {
            plugin.getLogger().error("更新玩家 {} 的Tab列表时发生错误", player.getUsername(), e);
        } finally {
            isUpdating = false;
        }
    }
    
    private void cleanupVirtualEntries(TabList tabList) {
        // 移除所有虚拟条目
        for (UUID uuid : virtualEntries.keySet()) {
            try {
                tabList.removeEntry(uuid);
            } catch (Exception e) {
                // 忽略移除不存在条目时的异常
            }
        }
        virtualEntries.clear();
    }
    
    private void addCrossServerEntries(TabList tabList) {
        if (!config.isCrossServerEnabled()) {
            return;
        }
        
        List<TabListEntry> entriesToAdd = new ArrayList<>();
        
        // 添加分隔符（仅当配置了非空文本时）
        if (config.getSeparatorText() != null && !config.getSeparatorText().trim().isEmpty()) {
            entriesToAdd.add(createSeparatorEntry(tabList));
        }
        
        // 获取所有服务器信息
        Collection<RegisteredServer> servers = plugin.getServer().getAllServers();
        
        for (RegisteredServer server : servers) {
            // 跳过当前服务器（如果配置要求）
            if (player.getCurrentServer().isPresent() && 
                player.getCurrentServer().get().getServerInfo().equals(server.getServerInfo())) {
                continue;
            }
            
            Collection<Player> serverPlayers = server.getPlayersConnected();
            if (serverPlayers.isEmpty()) {
                continue;
            }
            
            // 直接添加服务器玩家（限制数量）不显示服务器标题
            int maxPlayers = config.getMaxPlayersPerServer();
            int count = 0;
            
            for (Player serverPlayer : serverPlayers) {
                if (count >= maxPlayers) {
                    // 添加"更多玩家"条目
                    entriesToAdd.add(createMorePlayersEntry(tabList, serverPlayers.size() - maxPlayers));
                    break;
                }
                
                entriesToAdd.add(createCrossServerPlayerEntry(tabList, serverPlayer, server));
                count++;
            }
        }
        
        // 批量添加条目
        for (TabListEntry entry : entriesToAdd) {
            tabList.addEntry(entry);
            virtualEntries.put(entry.getProfile().getId(), entry);
        }
    }
    
    private TabListEntry createSeparatorEntry(TabList tabList) {
        UUID uuid = uuidGenerator.generateSeparatorUUID("main");
        GameProfile profile = new GameProfile(uuid, "separator", Collections.emptyList());
        
        Component displayName = Component.text(config.getSeparatorText())
            .color(NamedTextColor.GOLD);
        
        return TabListEntry.builder()
            .tabList(tabList)
            .profile(profile)
            .displayName(displayName)
            .latency(0)
            .gameMode(0)
            .build();
    }
    
    private TabListEntry createServerHeaderEntry(TabList tabList, RegisteredServer server, int playerCount) {
        UUID uuid = uuidGenerator.generateServerHeaderUUID(server.getServerInfo().getName());
        GameProfile profile = new GameProfile(uuid, server.getServerInfo().getName(), Collections.emptyList());
        
        String format = config.getServerHeaderFormat();
        String displayText = String.format(format, server.getServerInfo().getName(), playerCount);
        
        Component displayName = Component.text(displayText)
            .color(NamedTextColor.YELLOW);
        
        return TabListEntry.builder()
            .tabList(tabList)
            .profile(profile)
            .displayName(displayName)
            .latency(0)
            .gameMode(0)
            .build();
    }
    
    private TabListEntry createCrossServerPlayerEntry(TabList tabList, Player serverPlayer, RegisteredServer server) {
        UUID uuid = uuidGenerator.generatePlayerVirtualUUID(
            serverPlayer.getUniqueId(), 
            server.getServerInfo().getName()
        );
        GameProfile profile = new GameProfile(uuid, serverPlayer.getUsername(), Collections.emptyList());
        
        String format = config.getCrossServerPlayerFormat();
        String displayText = String.format(format, 
            serverPlayer.getUsername(), 
            server.getServerInfo().getName()
        );
        
        Component displayName = Component.text(displayText)
            .color(NamedTextColor.GRAY);
        
        return TabListEntry.builder()
            .tabList(tabList)
            .profile(profile)
            .displayName(displayName)
            .latency((int) serverPlayer.getPing())
            .gameMode(0)
            .build();
    }
    
    private TabListEntry createMorePlayersEntry(TabList tabList, int remainingCount) {
        UUID uuid = uuidGenerator.generateSeparatorUUID("more_players");
        GameProfile profile = new GameProfile(uuid, "more_players", Collections.emptyList());
        
        Component displayName = Component.text("§7... 还有 " + remainingCount + " 名玩家")
            .color(NamedTextColor.GRAY);
        
        return TabListEntry.builder()
            .tabList(tabList)
            .profile(profile)
            .displayName(displayName)
            .latency(0)
            .gameMode(0)
            .build();
    }
    
    public void cleanup() {
        if (player.isActive()) {
            TabList tabList = player.getTabList();
            cleanupVirtualEntries(tabList);
        }
    }
}