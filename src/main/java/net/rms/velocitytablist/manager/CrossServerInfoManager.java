package net.rms.velocitytablist.manager;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CrossServerInfoManager {
    
    private final ProxyServer server;
    private final Logger logger;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ServerInfo> serverInfoCache = new ConcurrentHashMap<>();
    private final Map<String, List<Player>> serverPlayerCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateTime = new ConcurrentHashMap<>();
    
    private ScheduledFuture<?> updateTask;
    private volatile boolean isRunning = false;
    
    public CrossServerInfoManager(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }
    
    public void start() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        logger.info("启动跨服务器信息收集器...");
        
        // 初始化服务器信息缓存
        updateServerInfo();
        
        // 启动定期更新任务（每30秒）
        updateTask = scheduler.scheduleAtFixedRate(
                this::updateServerInfo,
                0,
                30,
                TimeUnit.SECONDS
        );
        
        logger.info("跨服务器信息收集器已启动，更新间隔: 30秒");
    }
    
    public void shutdown() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        logger.info("正在关闭跨服务器信息收集器...");
        
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel(false);
        }
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("跨服务器信息收集器已关闭");
    }
    
    @Subscribe
    public void onPlayerConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        String serverName = player.getCurrentServer()
            .map(conn -> conn.getServerInfo().getName())
            .orElse("unknown");
        
        // 更新玩家服务器缓存
        updatePlayerServerCache(player, serverName);
        
        logger.debug("玩家 {} 连接到服务器 {}", player.getUsername(), serverName);
    }
    
    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        
        // 从所有服务器缓存中移除玩家
        serverPlayerCache.values().forEach(players -> players.remove(player));
        
        logger.debug("玩家 {} 断开连接", player.getUsername());
    }
    
    public void updateServerInfo() {
        try {
            // 更新所有服务器信息
            for (RegisteredServer registeredServer : server.getAllServers()) {
                ServerInfo serverInfo = registeredServer.getServerInfo();
                serverInfoCache.put(serverInfo.getName(), serverInfo);
                
                // 更新玩家列表
                List<Player> players = new ArrayList<>(registeredServer.getPlayersConnected());
                serverPlayerCache.put(serverInfo.getName(), players);
                
                lastUpdateTime.put(serverInfo.getName(), System.currentTimeMillis());
            }
            
            logger.debug("已更新 {} 个服务器的信息", serverInfoCache.size());
            
        } catch (Exception e) {
            logger.error("更新服务器信息时发生错误", e);
        }
    }
    
    private void updatePlayerServerCache(Player player, String serverName) {
        // 从旧服务器移除玩家
        serverPlayerCache.values().forEach(players -> players.remove(player));
        
        // 添加到新服务器
        serverPlayerCache.computeIfAbsent(serverName, k -> new ArrayList<>()).add(player);
    }
    
    public Map<RegisteredServer, List<Player>> getServerPlayerMap() {
        Map<RegisteredServer, List<Player>> result = new HashMap<>();
        
        for (RegisteredServer registeredServer : server.getAllServers()) {
            String serverName = registeredServer.getServerInfo().getName();
            List<Player> players = serverPlayerCache.getOrDefault(serverName, new ArrayList<>());
            
            // 限制每个服务器显示的玩家数量
            if (players.size() > 10) {
                players = players.subList(0, 10);
            }
            
            result.put(registeredServer, new ArrayList<>(players));
        }
        
        return result;
    }
    
    public List<Player> getPlayersOnServer(String serverName) {
        return new ArrayList<>(serverPlayerCache.getOrDefault(serverName, new ArrayList<>()));
    }
    
    public int getTotalPlayerCount() {
        return server.getPlayerCount();
    }
    
    public int getServerPlayerCount(String serverName) {
        return serverPlayerCache.getOrDefault(serverName, new ArrayList<>()).size();
    }
    
    public Set<String> getServerNames() {
        return new HashSet<>(serverInfoCache.keySet());
    }
    
    public ServerInfo getServerInfo(String serverName) {
        return serverInfoCache.get(serverName);
    }
    
    public boolean isServerOnline(String serverName) {
        RegisteredServer server = this.server.getServer(serverName).orElse(null);
        if (server == null) {
            return false;
        }
        
        // 检查服务器是否在最近的更新时间内
        Long lastUpdate = lastUpdateTime.get(serverName);
        if (lastUpdate == null) {
            return false;
        }
        
        long timeSinceUpdate = System.currentTimeMillis() - lastUpdate;
        return timeSinceUpdate < (30 * 2000); // 2倍更新间隔作为超时
    }
    
    public void requestUpdate(ServerInfo serverInfo) {
        CompletableFuture.runAsync(() -> {
            try {
                // 立即更新指定服务器的信息
                RegisteredServer server = this.server.getServer(serverInfo.getName()).orElse(null);
                if (server != null) {
                    List<Player> players = new ArrayList<>(server.getPlayersConnected());
                    serverPlayerCache.put(serverInfo.getName(), players);
                    lastUpdateTime.put(serverInfo.getName(), System.currentTimeMillis());
                    
                    logger.debug("已更新服务器 {} 的信息", serverInfo.getName());
                }
            } catch (Exception e) {
                logger.error("更新服务器 {} 信息时发生错误", serverInfo.getName(), e);
            }
        }, scheduler);
    }
    
    public void requestSync() {
        CompletableFuture.runAsync(() -> {
            try {
                updateServerInfo();
                logger.debug("已同步所有服务器信息");
            } catch (Exception e) {
                logger.error("同步服务器信息时发生错误", e);
            }
        }, scheduler);
    }
    
    public long getLastUpdateTime(String serverName) {
        return lastUpdateTime.getOrDefault(serverName, 0L);
    }
    
    public boolean isRunning() {
        return isRunning;
    }
}