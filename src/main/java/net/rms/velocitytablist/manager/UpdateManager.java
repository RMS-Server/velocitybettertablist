package net.rms.velocitytablist.manager;

import com.velocitypowered.api.proxy.ProxyServer;
import net.rms.velocitytablist.VelocityTabListPlugin;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UpdateManager {
    
    private final VelocityTabListPlugin plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final String currentVersion;
    private final String versionUrl;
    private final String githubRepo;
    private final boolean autoUpdateEnabled;
    private final boolean autoDownload;
    private final boolean checkOnStartup;
    private final int checkIntervalHours;
    
    private final HttpClient httpClient;
    private ScheduledExecutorService scheduler;
    
    public UpdateManager(VelocityTabListPlugin plugin, ProxyServer server, Logger logger, 
                        String currentVersion, String versionUrl, String githubRepo,
                        boolean autoUpdateEnabled, boolean autoDownload, 
                        boolean checkOnStartup, int checkIntervalHours) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.versionUrl = versionUrl;
        this.githubRepo = githubRepo;
        this.autoUpdateEnabled = autoUpdateEnabled;
        this.autoDownload = autoDownload;
        this.checkOnStartup = checkOnStartup;
        this.checkIntervalHours = checkIntervalHours;
        
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    public void start() {
        if (!autoUpdateEnabled) {
            logger.info("自动更新已禁用");
            return;
        }
        
        logger.info("启动自动更新管理器...");
        
        scheduler = server.getScheduler().createScheduledExecutorService(plugin);
        
        if (checkOnStartup) {
            checkForUpdates();
        }
        
        if (checkIntervalHours > 0) {
            scheduler.scheduleAtFixedRate(
                this::checkForUpdates,
                checkIntervalHours,
                checkIntervalHours,
                TimeUnit.HOURS
            );
            logger.info("已设置定期检查更新，间隔: {} 小时", checkIntervalHours);
        }
    }
    
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void checkForUpdates() {
        logger.info("检查插件更新...");
        
        fetchLatestVersion()
            .thenAccept(latestVersion -> {
                if (latestVersion != null) {
                    if (isNewerVersion(latestVersion)) {
                        logger.info("发现新版本: {} (当前版本: {})", latestVersion, currentVersion);
                        
                        if (autoDownload) {
                            downloadUpdate(latestVersion);
                        } else {
                            logger.info("请手动下载最新版本: https://github.com/{}/releases", githubRepo);
                        }
                    } else {
                        logger.info("当前版本已是最新版本: {}", currentVersion);
                    }
                } else {
                    logger.warn("无法获取最新版本信息");
                }
            })
            .exceptionally(throwable -> {
                logger.error("检查更新时发生错误", throwable);
                return null;
            });
    }
    
    private CompletableFuture<String> fetchLatestVersion() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(versionUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    String version = response.body().trim();
                    if (version.startsWith("V ")) {
                        version = version.substring(2);
                    }
                    return version;
                } else {
                    logger.warn("获取版本信息失败，HTTP状态码: {}", response.statusCode());
                    return null;
                }
            } catch (Exception e) {
                logger.error("获取最新版本时发生错误", e);
                return null;
            }
        });
    }
    
    private boolean isNewerVersion(String latestVersion) {
        try {
            return compareVersions(currentVersion, latestVersion) < 0;
        } catch (Exception e) {
            logger.warn("比较版本时发生错误: {} vs {}", currentVersion, latestVersion, e);
            return false;
        }
    }
    
    private int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");
        
        int maxLength = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        
        return 0;
    }
    
    private void downloadUpdate(String latestVersion) {
        logger.info("正在下载新版本: {}", latestVersion);
        
        String downloadUrl = String.format(
            "https://github.com/%s/releases/download/v%s/velocitytablist-%s.jar",
            githubRepo, latestVersion, latestVersion
        );
        
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
                
                HttpResponse<byte[]> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofByteArray());
                
                if (response.statusCode() == 200) {
                    Path updateFile = Paths.get("plugins", String.format("velocitytablist-%s.jar", latestVersion));
                    Files.createDirectories(updateFile.getParent());
                    Files.write(updateFile, response.body());
                    
                    logger.info("新版本已下载到: {}", updateFile.toAbsolutePath());
                    logger.info("请重启服务器以应用更新");
                    return true;
                } else {
                    logger.warn("下载更新失败，HTTP状态码: {}", response.statusCode());
                    return false;
                }
            } catch (Exception e) {
                logger.error("下载更新时发生错误", e);
                return false;
            }
        }).thenAccept(success -> {
            if (success) {
                logger.info("更新下载完成");
            } else {
                logger.warn("更新下载失败，请手动下载: https://github.com/{}/releases", githubRepo);
            }
        });
    }
    
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    public boolean isAutoUpdateEnabled() {
        return autoUpdateEnabled;
    }
}