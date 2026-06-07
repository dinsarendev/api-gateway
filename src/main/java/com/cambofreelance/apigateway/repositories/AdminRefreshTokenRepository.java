package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.AdminRefreshToken;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AdminRefreshTokenRepository extends R2dbcRepository<AdminRefreshToken, Long> {

    @Query("""
        SELECT * FROM public.admin_refresh_token
        WHERE token_hash = :hash AND revoked = false AND expires_at > NOW()
        """)
    Mono<AdminRefreshToken> findValid(String hash);

    @Modifying
    @Query("UPDATE public.admin_refresh_token SET revoked = true WHERE id = :id")
    Mono<Integer> revokeById(Long id);

    @Modifying
    @Query("UPDATE public.admin_refresh_token SET revoked = true WHERE token_hash = :hash")
    Mono<Integer> revokeByHash(String hash);

    @Modifying
    @Query("UPDATE public.admin_refresh_token SET revoked = true WHERE user_id = :userId")
    Mono<Integer> revokeAllForUser(Long userId);
}