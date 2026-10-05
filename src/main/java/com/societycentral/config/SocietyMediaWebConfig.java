package com.societycentral.config; // Places this configuration class in the existing Spring configuration package.

import com.societycentral.model.SocietyImageType; // Imports the existing enum that defines society media folders such as logos and highlights.
import org.springframework.context.annotation.Configuration; // Marks this class as Spring configuration so it is discovered automatically.
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry; // Provides the registry used to expose stored files through HTTP URLs.
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // Allows this class to add custom Spring MVC resource mappings.

import java.nio.file.Path; // Represents the physical filesystem directories where society images are stored.

/**
 * Exposes uploaded society media through /media/societies/** URLs.
 */
@Configuration // Registers this configuration automatically when Spring Boot starts.
public class SocietyMediaWebConfig implements WebMvcConfigurer { // Defines the MVC configuration responsible only for society media.

    private final EventMediaProperties properties; // Reuses the same configured upload root used by LocalSocietyMediaStorageService.

    /**
     * Creates the society-media web configuration.
     *
     * @param properties existing configured media root
     */
    public SocietyMediaWebConfig(EventMediaProperties properties) { // Receives EventMediaProperties through Spring dependency injection.
        this.properties = properties; // Stores the configuration so the physical upload directory can be resolved.
    }

    /**
     * Registers every supported society image directory.
     *
     * @param registry Spring MVC resource handler registry
     */
    @Override // Implements Spring MVC's resource-handler registration hook.
    public void addResourceHandlers(ResourceHandlerRegistry registry) { // Registers browser-accessible mappings for society media.
        registerUploadedSocietyAssets(registry);
        registerSocietyImageType(registry, SocietyImageType.LOGO); // Makes uploaded society logos accessible through HTTP.
        registerSocietyImageType(registry, SocietyImageType.BANNER); // Makes uploaded society banners accessible through HTTP.
        registerSocietyImageType(registry, SocietyImageType.GALLERY_IMAGE); // Makes society gallery images accessible through HTTP.
        registerSocietyImageType(registry, SocietyImageType.HIGHLIGHT_COVER); // Makes Society Highlight article cover images accessible through HTTP.
    }

    private void registerUploadedSocietyAssets(ResourceHandlerRegistry registry) {
        Path uploadRoot = properties.uploadDir()
                .toAbsolutePath()
                .normalize()
                .getParent();

        if (uploadRoot == null) {
            throw new IllegalStateException(
                    "Configured upload directory must have a parent directory.");
        }

        String resourceLocation = uploadRoot.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resourceLocation);
    }

    /**
     * Connects one public society-media URL to its physical storage folder.
     *
     * @param registry Spring MVC resource handler registry
     * @param imageType society image type being exposed
     */
    private void registerSocietyImageType( // Defines reusable mapping logic for each SocietyImageType.
                                           ResourceHandlerRegistry registry, // Receives Spring's registry for static-resource mappings.
                                           SocietyImageType imageType) { // Identifies whether this mapping is for logos, banners, gallery images, or highlight covers.

        Path root = properties.uploadDir() // Reads the exact same upload root used by LocalSocietyMediaStorageService.
                .toAbsolutePath() // Converts the configured root to an absolute filesystem path.
                .normalize(); // Removes unnecessary path segments and creates a consistent root path.

        Path directory = root // Starts from the configured upload root.
                .resolve("societies") // Matches LocalSocietyMediaStorageService.directoryFor(), which creates a societies subdirectory.
                .resolve(imageType.getFolderName()) // Adds the folder defined by SocietyImageType, such as logos or highlights.
                .normalize(); // Normalises the final physical directory before exposing it.

        if (!directory.startsWith(root)) { // Ensures the calculated media directory cannot escape the configured upload root.
            throw new IllegalStateException( // Stops application startup if an unsafe media path somehow occurs.
                    "Configured society media folder escaped its upload root."); // Explains why the resource mapping was rejected.
        }

        String resourceLocation = directory // Starts with the physical media directory.
                .toUri() // Converts the filesystem path into a URI that Spring ResourceHandler can understand.
                .toString(); // Converts the URI into the string expected by addResourceLocations().

        if (!resourceLocation.endsWith("/")) { // Checks whether the resource URI ends as a directory path.
            resourceLocation += "/"; // Adds the trailing slash required for reliable directory-based resource lookup.
        }

        String publicPath = "/media/societies/" // Starts the same URL prefix produced by buildPublicUrl().
                + imageType.getFolderName() // Adds logos, banners, gallery, or highlights.
                + "/**"; // Allows Spring to resolve any generated filename underneath that folder.

        registry.addResourceHandler(publicPath) // Registers the browser-facing society-media URL pattern.
                .addResourceLocations(resourceLocation); // Connects that HTTP pattern to the actual physical directory.
    }
}
