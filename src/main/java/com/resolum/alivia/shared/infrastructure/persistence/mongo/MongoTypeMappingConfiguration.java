package com.resolum.alivia.shared.infrastructure.persistence.mongo;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

/**
 * Stops Spring Data from writing the {@code _class} field (the full name of the Java class) into every document.
 *
 * <p>Measured on the telemetry readings it was about a third of each document (about 90 of 241 bytes), in a database whose
 * free cluster holds 512 MB. Nothing reads it: every collection is read as one known class, and the documents do not use
 * inheritance. Documents already written with it are read the same, so nothing has to be migrated.</p>
 *
 * <p>It applies to the one converter of the application. If a document with a hierarchy of classes is ever kept in
 * MongoDB, it needs the field back: remove this configuration or give that document its own converter.</p>
 */
@Configuration
public class MongoTypeMappingConfiguration {

    @Bean
    static BeanPostProcessor mongoDocumentsWithoutTypeHint() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof MappingMongoConverter converter) {
                    converter.setTypeMapper(new DefaultMongoTypeMapper(null));
                }
                return bean;
            }
        };
    }
}
