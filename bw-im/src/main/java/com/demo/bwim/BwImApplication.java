package com.demo.bwim;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@MapperScan("com.demo.bwim.mapper")
@SpringBootApplication
@EnableDubbo(scanBasePackages = "com.demo.bwim")
@ComponentScan("com.demo")
@EnableDiscoveryClient
public class BwImApplication {

    public static void main(String[] args) {
        SpringApplication.run(BwImApplication.class, args);
    }

}
