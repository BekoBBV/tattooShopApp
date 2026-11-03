package com.tattoostudio.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React site (client/dist, copied to static/ by Maven) next to the API, so the
 * whole app runs on one address. Page addresses such as /book/time or /admin/artists have no file
 * of their own: they get index.html and React Router shows the right page. Missing files and
 * unknown /api addresses still answer 404.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        // Spring's own lookup, including its check that the file is inside static/
                        Resource requested = super.getResource(path, location);
                        if (requested != null) {
                            return requested;
                        }
                        if (path.startsWith("api/") || path.contains(".")) {
                            return null;
                        }
                        return super.getResource("index.html", location);
                    }
                });
    }
}
