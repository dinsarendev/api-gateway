package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.AdminRole;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface AdminRoleRepository extends R2dbcRepository<AdminRole, Long> {

    @Query("SELECT * FROM public.admin_role WHERE status = 'ACT' ORDER BY id")
    Flux<AdminRole> findAllActive();

    @Query("SELECT * FROM public.admin_role WHERE name = :name AND status = 'ACT'")
    Mono<AdminRole> findActiveByName(String name);

    @Query("""
        SELECT r.* FROM public.admin_role r
        JOIN public.user_role ur ON ur.role_id = r.id
        WHERE ur.user_id = :userId AND r.status = 'ACT'
        """)
    Flux<AdminRole> findByUserId(Long userId);
}