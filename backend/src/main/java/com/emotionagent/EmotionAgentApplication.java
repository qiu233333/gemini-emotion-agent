package com.emotionagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.emotionagent.config.AppProperties;

@SpringBootApplication
@MapperScan("com.emotionagent.**.mapper")
@EnableConfigurationProperties(AppProperties.class)
public class EmotionAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmotionAgentApplication.class, args);
    }
}
