package com.flowpay.monitoring.info;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ApplicationInfoContributor implements InfoContributor {

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @Autowired(required = false)
    private GitProperties gitProperties;

    @Autowired
    private Environment environment;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("application", buildApplicationDetails());
        builder.withDetail("build", buildBuildDetails());
        builder.withDetail("git", buildGitDetails());
        builder.withDetail("runtime", buildRuntimeDetails());
    }

    private Map<String, Object> buildApplicationDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", "FlowPay Core");
        details.put("description", "Real-Time Payment Processing Platform");
        details.put("profiles", environment.getActiveProfiles());
        return details;
    }

    private Map<String, Object> buildBuildDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        if (buildProperties != null) {
            details.put("artifact", buildProperties.getArtifact());
            details.put("group", buildProperties.getGroup());
            details.put("version", buildProperties.getVersion());
            details.put("buildTime", buildProperties.getTime() != null ? buildProperties.getTime().toString() : null);
        } else {
            details.put("version", "1.0.0-SNAPSHOT");
            details.put("note", "Build info not available - run 'mvn package' to generate");
        }
        return details;
    }

    private Map<String, Object> buildGitDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        if (gitProperties != null) {
            details.put("branch", gitProperties.getBranch());
            details.put("commitId", gitProperties.getShortCommitId());
            details.put("commitTime", gitProperties.getCommitTime() != null ? gitProperties.getCommitTime().toString() : null);
        } else {
            details.put("note", "Git info not available");
        }
        return details;
    }

    private Map<String, Object> buildRuntimeDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("java.version", System.getProperty("java.version"));
        details.put("java.vendor", System.getProperty("java.vendor"));
        details.put("os.name", System.getProperty("os.name"));
        details.put("os.arch", System.getProperty("os.arch"));
        details.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        details.put("maxMemoryMB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        return details;
    }
}
