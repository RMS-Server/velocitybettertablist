package net.rms.velocitytablist;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.rms.velocitytablist.config.ConfigManager;
import net.rms.velocitytablist.handler.TabListPacketHandler;
import net.rms.velocitytablist.manager.CrossServerInfoManager;
import net.rms.velocitytablist.manager.UpdateManager;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "velocitytablist",
    name = "VelocityTabList",
    version = "1.0.0",
    description = "Cross-server tab list enhancement for Velocity",
    authors = {"XRain"},
    url = "https://github.com/RMS-Server/velocitybettertablist"
)
public class VelocityTabListPlugin {
    
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    
    private ConfigManager configManager;
    private CrossServerInfoManager crossServerManager;
    private TabListPacketHandler packetHandler;
    private UpdateManager updateManager;
    
    @Inject
    public VelocityTabListPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }
    
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("正在初始化 VelocityTabList 插件...");
        
        try {
            // 初始化配置管理器
            configManager = new ConfigManager(dataDirectory, logger);
            
            // 初始化自动更新管理器
            String currentVersion = getCurrentVersion();
            updateManager = new UpdateManager(
                server, logger, currentVersion,
                configManager.getVersionUrl(),
                configManager.getGithubRepo(),
                configManager.isAutoUpdateEnabled(),
                configManager.isAutoDownload(),
                configManager.isCheckOnStartup(),
                configManager.getCheckIntervalHours()
            );
            
            // 启动自动更新管理器
            updateManager.start();
            
            // 初始化跨服务器信息管理器
            crossServerManager = new CrossServerInfoManager(server, logger);
            
            // 初始化数据包处理器
            packetHandler = new TabListPacketHandler(this, server, crossServerManager);
            
            // 注册事件监听器
            server.getEventManager().register(this, crossServerManager);
            server.getEventManager().register(this, packetHandler);
            
            // 启动跨服务器信息收集
            crossServerManager.start();
            
            // 启动定期更新任务（使用配置文件中的间隔）
            server.getScheduler().buildTask(this, () -> {
                packetHandler.updateAllTabLists();
            }).repeat(java.time.Duration.ofSeconds(configManager.getUpdateIntervalSeconds())).schedule();
            
            logger.info("VelocityTabList 插件初始化完成!");
            
        } catch (Exception e) {
            logger.error("初始化插件时发生错误", e);
        }
    }
    
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("正在关闭 VelocityTabList 插件...");
        
        if (updateManager != null) {
            updateManager.shutdown();
        }
        
        if (crossServerManager != null) {
            crossServerManager.shutdown();
        }
        
        if (packetHandler != null) {
            packetHandler.shutdown();
        }
        
        logger.info("VelocityTabList 插件已关闭");
    }
    
    public ProxyServer getServer() {
        return server;
    }
    
    public Logger getLogger() {
        return logger;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public UpdateManager getUpdateManager() {
        return updateManager;
    }
    
    private String getCurrentVersion() {
        try {
            Path versionFile = dataDirectory.getParent().resolve("plugin.version");
            if (!java.nio.file.Files.exists(versionFile)) {
                return "1.0.0";
            }
            
            String version = java.nio.file.Files.readString(versionFile).trim();
            if (version.startsWith("V ")) {
                version = version.substring(2);
            }
            return version;
        } catch (Exception e) {
            logger.warn("无法读取版本文件，使用默认版本", e);
            return "1.0.0";
        }
    }
    
}