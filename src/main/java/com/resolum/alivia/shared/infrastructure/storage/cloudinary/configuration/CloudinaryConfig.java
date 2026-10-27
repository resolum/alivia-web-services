package com.resolum.alivia.shared.infrastructure.storage.cloudinary.configuration;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class CloudinaryConfig {
    @Value("${cloudinary.cloud-name:CLOUDINARY_CLOUD_NAME}")
    private String cloudName;

    @Value("${cloudinary.api-key:CLOUDINARY_API_KEY}")
    private String apiKey;

    @Value("${cloudinary.api-secret:CLOUDINARY_API_SECRET}")
    private String apiSecret;

    @Bean
    Cloudinary cloudinary() {

        return new Cloudinary(
                ObjectUtils.asMap(
                        "cloud_name",cloudName,
                        "api_key",apiKey,
                        "api_secret",apiSecret,
                        "secure", true
                ));
    }
}
