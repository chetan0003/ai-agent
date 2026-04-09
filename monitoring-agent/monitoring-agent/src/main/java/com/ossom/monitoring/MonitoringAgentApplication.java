package com.ossom.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.ossom.monitoring.config.AgentProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AgentProperties.class)
public class MonitoringAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringAgentApplication.class, args);
    }
}
