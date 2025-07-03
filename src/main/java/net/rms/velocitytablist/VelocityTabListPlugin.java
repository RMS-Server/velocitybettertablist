package net.rms.velocitytablist;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.rms.velocitytablist.config.TabListConfig;
import net.rms.velocitytablist.handler.TabListPacketHandler;
import net.rms.velocitytablist.manager.CrossServerInfoManager;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "velocitytablist",
    name = "VelocityTabList",
    version = "1.0.0",
    description = "Cross-server tab list enhancement for Velocity",
    authors = {"XRain"},
    url = "https://github.com/RMS-Server/velocitytab"
)
public class VelocityTabListPlugin {
    
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    
    private TabListConfig config;
    private CrossServerInfoManager crossServerManager;
    private TabListPacketHandler packetHandler;
    
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
            // 加载配置
            config = new TabListConfig(dataDirectory);
            config.load();
            
            // 初始化跨服务器信息管理器
            crossServerManager = new CrossServerInfoManager(server, config, logger);
            
            // 初始化数据包处理器
            packetHandler = new TabListPacketHandler(server, crossServerManager, config, logger);
            
            // 注册事件监听器
            server.getEventManager().register(this, crossServerManager);
            server.getEventManager().register(this, packetHandler);
            
            // 启动跨服务器信息收集
            crossServerManager.start();
            
            logger.info("VelocityTabList 插件初始化完成!");
            
        } catch (Exception e) {
            logger.error("初始化插件时发生错误", e);
        }
    }
    
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("正在关闭 VelocityTabList 插件...");
        
        if (crossServerManager != null) {
            crossServerManager.shutdown();
        }
        
        logger.info("VelocityTabList 插件已关闭");
    }
    
    public ProxyServer getServer() {
        return server;
    }
    
    public Logger getLogger() {
        return logger;
    }
    
    public TabListConfig getConfig() {
        return config;
    }
}