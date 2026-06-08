package com.demo.bwgateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class WebSocketGatewayLogFilter implements GlobalFilter, Ordered {

    private static final String WS_UPGRADE_HEADER = "Upgrade";
    private static final String WS_UPGRADE_VALUE = "websocket";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        List<String> upgrade = headers.get(WS_UPGRADE_HEADER);

        if (upgrade != null && upgrade.stream().anyMatch(WS_UPGRADE_VALUE::equalsIgnoreCase)) {
            String requestId = exchange.getRequest().getId();
            String path = exchange.getRequest().getURI().getPath();
            String remoteAddress = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().toString() : "unknown";
            
            log.info("[GW-WS-FORWARD] requestId={}, path={}, remote={}, upgrading to WebSocket",
                    requestId, path, remoteAddress);

            return chain.filter(exchange).doOnSuccess(aVoid -> {
                log.info("[GW-WS-COMPLETE] requestId={}, path={}, WebSocket forwarding completed", requestId, path);
            }).doOnError(error -> {
                log.error("[GW-WS-ERROR] requestId={}, path={}, error={}", requestId, path, error.getMessage(), error);
            });
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }
}
