package com.spatialstay.config;
import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.Configuration; import org.springframework.web.servlet.config.annotation.*; import java.nio.file.*;
@Configuration public class UploadedMediaConfig implements WebMvcConfigurer{
 private final String uploadDir; public UploadedMediaConfig(@Value("${app.upload-dir:uploads}")String uploadDir){this.uploadDir=uploadDir;}
 @Override public void addResourceHandlers(ResourceHandlerRegistry registry){String location=Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();registry.addResourceHandler("/uploads/**").addResourceLocations(location);}
}
