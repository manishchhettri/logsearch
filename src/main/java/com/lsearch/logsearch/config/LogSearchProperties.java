package com.lsearch.logsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "log-search")
public class LogSearchProperties {

    private String logsDir;
    private String indexDir;
    private String filePattern;
    private String filenameDatePattern;
    private String logDatetimeFormat;
    private String timezone;
    private int retentionDays;
    private boolean autoWatch;
    private int watchInterval;

    public String getLogsDir() {
        return logsDir;
    }

    public void setLogsDir(String logsDir) {
        this.logsDir = logsDir;
    }

    public String getIndexDir() {
        return indexDir;
    }

    public void setIndexDir(String indexDir) {
        this.indexDir = indexDir;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    public String getFilenameDatePattern() {
        return filenameDatePattern;
    }

    public void setFilenameDatePattern(String filenameDatePattern) {
        this.filenameDatePattern = filenameDatePattern;
    }

    public String getLogDatetimeFormat() {
        return logDatetimeFormat;
    }

    public void setLogDatetimeFormat(String logDatetimeFormat) {
        this.logDatetimeFormat = logDatetimeFormat;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public boolean isAutoWatch() {
        return autoWatch;
    }

    public void setAutoWatch(boolean autoWatch) {
        this.autoWatch = autoWatch;
    }

    public int getWatchInterval() {
        return watchInterval;
    }

    public void setWatchInterval(int watchInterval) {
        this.watchInterval = watchInterval;
    }
}
