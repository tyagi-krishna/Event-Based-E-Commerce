package com.e_commerce.gateway_service.filter;

import com.e_commerce.gateway_service.security.JwtParser;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtParser jwtParser;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();

        if (isPublic(path, method)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return reject(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        Optional<JwtParser.JwtClaims> claimsOpt = jwtParser.parse(token);
        if (claimsOpt.isEmpty()) {
            return reject(exchange);
        }

        // Check blacklist in Redis — fail open if Redis is unavailable
        return redisTemplate.hasKey("blacklist:" + token)
                .defaultIfEmpty(false)
                .onErrorReturn(false)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return reject(exchange);
                    }
                    JwtParser.JwtClaims claims = claimsOpt.get();
                    // Propagate user context as trusted internal headers
                    // Authorization header is kept so user-service logout can still read the raw token
                    ServerHttpRequest modified = request.mutate()
                            .header("X-User-Id", claims.userId())
                            .header("X-User-Email", claims.email())
                            .header("X-User-Role", claims.role())
                            .build();
                    return chain.filter(exchange.mutate().request(modified).build());
                });
    }

    private boolean isPublic(String path, String method) {
        return ("POST".equals(method) && "/api/v1/users".equals(path))
                || ("POST".equals(method) && "/api/v1/users/login".equals(path));
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
