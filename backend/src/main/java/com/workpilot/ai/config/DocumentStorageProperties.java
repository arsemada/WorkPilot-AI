package com.workpilot.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "workpilot.documents")
public class DocumentStorageProperties {

    public static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;

    private String directory = "data/documents";
    private long maxBytes = DEFAULT_MAX_BYTES;
    private boolean processingSync = false;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public boolean isProcessingSync() {
        return processingSync;
    }

    public void setProcessingSync(boolean processingSync) {
        this.processingSync = processingSync;
    }
}
