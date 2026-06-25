package com.xiaoc.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XiaocWorkbenchApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaocWorkbenchApplication.class, args);
    }
}
