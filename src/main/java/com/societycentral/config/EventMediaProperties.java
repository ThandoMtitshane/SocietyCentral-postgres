package com.societycentral.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.nio.file.Path;

/**
 * Configurable local-storage and public-URL settings for event media.
 *
 * @param uploadDir root directory containing only event media
 * @param mediaBaseUrl externally reachable backend base URL
 */
@Validated
@ConfigurationProperties(prefix = "societycentral")
public record EventMediaProperties(
        @NotNull Path uploadDir,
        @NotNull URI mediaBaseUrl) {
}
