// Spring Boot entry point for the DocShelf backend
package com.docshelf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocShelfApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocShelfApplication.class, args);
    }
}
