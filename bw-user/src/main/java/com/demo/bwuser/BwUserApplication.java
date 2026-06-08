package com.demo.bwuser;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;


@MapperScan("com.demo.bwuser.mapper")
@SpringBootApplication
@EnableScheduling
@EnableDubbo(scanBasePackages = "com.demo.bwuser.rpc")
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan("com.demo")
@EnableDiscoveryClient
public class BwUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(BwUserApplication.class, args);
    }

}
