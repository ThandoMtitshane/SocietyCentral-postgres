package com.societycentral.config; // Places this Spring MVC configuration in the existing configuration package.

import com.societycentral.model.EventImageType; // Imports the existing event-media enum used by the current event upload system.
import org.springframework.boot.context.properties.EnableConfigurationProperties; // Enables the project's existing media configuration properties.
import org.springframework.context.annotation.Configuration; // Marks the class as Spring configuration.
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry; // Provides the registry used to expose uploaded files over HTTP.
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // Allows this class to customise Spring MVC resource handling.

import java.nio.file.Path; // Represents the physical event-media directory safely.

/**
 * Exposes only the controlled event poster and banner directories.
 */
@Configuration // Registers this class with Spring.
@EnableConfigurationProperties(EventMediaProperties.class) // Enables the existing EventMediaProperties record.
public class EventMediaWebConfig implements WebMvcConfigurer { // Defines the existing event-media web configuration.

    private final EventMediaProperties properties; // Stores the configured upload location.

    /**
     * Creates the event-media resource mapping configuration.
     *
     * @param properties configured event-media paths
     */
    public EventMediaWebConfig(EventMediaProperties properties) { // Receives media configuration through dependency injection.
        this.properties = properties; // Stores the injected configuration.
    }

    /**
     * Registers separate resource mappings for posters and banners.
     *
     * @param registry Spring MVC resource handler registry
     */
    @Override // Overrides Spring MVC's resource-handler registration method.
    public void addResourceHandlers(ResourceHandlerRegistry registry) { // Registers the currently supported event media folders.
        registerImageType(registry, EventImageType.POSTER); // Exposes event poster files.
        registerImageType(registry, EventImageType.BANNER); // Exposes event banner files.
    }

    private void registerImageType( // Defines the shared mapping logic for one event image type.
                                    ResourceHandlerRegistry registry, // Receives the Spring resource registry.
                                    EventImageType imageType) { // Receives the event image category being mapped.

        Path directory = properties.uploadDir() // Starts with the configured upload directory.
                .toAbsolutePath() // Converts it to an absolute filesystem path.
                .normalize() // Removes unnecessary path segments.
                .resolve(imageType.getFolderName()) // Enters the specific event image folder.
                .normalize(); // Normalises the final physical path.

        String resourceLocation = directory.toUri().toString(); // Converts the physical folder to a URI Spring can serve.

        if (!resourceLocation.endsWith("/")) { // Checks whether Spring will treat the location as a directory.
            resourceLocation += "/"; // Adds the required trailing separator.
        }

        registry.addResourceHandler( // Creates the browser-accessible media mapping.
                        "/media/events/" + imageType.getFolderName() + "/**") // Defines the public event-media URL.
                .addResourceLocations(resourceLocation); // Connects the URL to the physical folder.
    }
}