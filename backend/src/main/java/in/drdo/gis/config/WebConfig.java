package in.drdo.gis.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Prefix every REST endpoint with "/api" WITHOUT giving the whole app a context-path.
     * This lets the Angular UI live at the root ("/") while the API stays under "/api/v1/...".
     * The prefix is applied only to @RestController classes, so it never touches the static
     * UI files or the SPA fallback below.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
                HandlerTypePredicate.forAnnotation(RestController.class));
    }

    /**
     * Serve the bundled Angular build (src/main/resources/static) and make it behave like a
     * single-page app: any path that is not a real file and is not an API/actuator/docs path
     * falls back to index.html so Angular's router can handle it (e.g. a refreshed deep link).
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Never swallow API / actuator / docs calls with the SPA page.
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("actuator/")
                                || resourcePath.startsWith("webjars/")) {
                            return null;
                        }
                        // Everything else -> Angular's index.html (client-side routing).
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:4200", "http://localhost:4201")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "Content-Range", "Accept-Ranges")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
