package net.rms.velocitytablist.config;

import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    
    private final Path configPath;
    private final Logger logger;
    private ConfigurationNode config;
    
    public ConfigManager(Path dataDirectory, Logger logger) {
        this.configPath = dataDirectory.resolve("config.yml");
        this.logger = logger;
        
        try {
            loadConfig();
        } catch (IOException e) {
            logger.error("加载配置文件时发生错误", e);
        }
    }
    
    private void loadConfig() throws IOException {
        if (!Files.exists(configPath)) {
            createDefaultConfig();
        }
        
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
            .path(configPath)
            .build();
        
        config = loader.load();
        logger.info("配置文件加载完成: {}", configPath);
    }
    
    private void createDefaultConfig() throws IOException {
        Files.createDirectories(configPath.getParent());
        
        try (InputStream inputStream = getClass().getResourceAsStream("/config.yml")) {
            if (inputStream != null) {
                Files.copy(inputStream, configPath);
                logger.info("创建默认配置文件: {}", configPath);
            } else {
                logger.error("无法找到默认配置文件");
            }
        }
    }
    
    // 使用预设值，不从配置文件读取
    public boolean isCrossServerEnabled() {
        return true;
    }
    
    public boolean isShowPlayerCount() {
        return true;
    }
    
    public boolean isShowServerStatus() {
        return true;
    }
    
    public int getUpdateIntervalSeconds() {
        return 30;
    }
    
    public int getMaxPlayersPerServer() {
        return 10;
    }
    
    public String getCurrentServerPrefix() {
        return "§a➤ ";
    }
    
    public String getOtherServerPrefix() {
        return "§7├ ";
    }
    
    public String getServerHeaderFormat() {
        return "§e§l%s §7(%d人在线)";
    }
    
    public String getCrossServerPlayerFormat() {
        return "§7%s §8[%s]";
    }
    
    public String getSeparatorText() {
        return "";
    }
    
    public boolean isEnableIncrementalUpdates() {
        return true;
    }
    
    public int getMaxTabListSize() {
        return 100;
    }
    
    public boolean isPreserveModInfo() {
        return true;
    }
    
    public boolean isAutoUpdateEnabled() {
        return config.node("auto-update", "enabled").getBoolean(true);
    }
    
    public int getCheckIntervalHours() {
        return config.node("auto-update", "check-interval-hours").getInt(24);
    }
    
    public boolean isAutoDownload() {
        return config.node("auto-update", "auto-download").getBoolean(true);
    }
    
    public boolean isCheckOnStartup() {
        return config.node("auto-update", "check-on-startup").getBoolean(true);
    }
    
    public String getGithubRepo() {
        return config.node("auto-update", "github-repo").getString("RMS-Server/velocitybettertablist");
    }
    
    public String getVersionUrl() {
        return config.node("auto-update", "version-url").getString("https://raw.githubusercontent.com/RMS-Server/velocitybettertablist/main/plugin.version");
    }
    
    public void reloadConfig() {
        try {
            loadConfig();
            logger.info("配置文件重新加载完成");
        } catch (IOException e) {
            logger.error("重新加载配置文件时发生错误", e);
        }
    }
}