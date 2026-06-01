package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.AdminUser;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface AdminUserRepository extends R2dbcRepository<AdminUser, Long> {

    @Query("SELECT * FROM public.admin_user WHERE username = :username AND status = 'ACT'")
    Mono<AdminUser> findActiveByUsername(String username);

    @Query("SELECT * FROM public.admin_user WHERE status = 'ACT' ORDER BY id")
    Flux<AdminUser> findAllActive();

    @Modifying
    @Query("UPDATE public.admin_user SET last_login_at = :now WHERE id = :id")
    Mono<Integer> touchLastLogin(Long id, LocalDateTime now);
}