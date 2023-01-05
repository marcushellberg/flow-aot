package com.vaadin.spring.aot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.event.EventListener;

@AutoConfiguration
@ImportRuntimeHints({AtmosphereHintsRegistrar.class})
class AotAutoConfiguration {

    @Bean
    static FlowBeanFactoryInitializationAotProcessor flowBeanFactoryInitializationAotProcessor() {
        return new FlowBeanFactoryInitializationAotProcessor();
    }

}
