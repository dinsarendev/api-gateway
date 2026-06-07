package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.AdminPermission;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface AdminPermissionRepository extends R2dbcRepository<AdminPermission, Long> {

    @Query("SELECT * FROM public.admin_permission WHERE status = 'ACT' ORDER BY name")
    Flux<AdminPermission> findAllActive();

    @Query("SELECT * FROM public.admin_permission WHERE name = :name AND status = 'ACT'")
    Mono<AdminPermission> findActiveByName(String name);

    @Query("""
        SELECT p.* FROM public.admin_permission p
        JOIN public.role_permission rp ON rp.permission_id = p.id
        WHERE rp.role_id = :roleId AND p.status = 'ACT'
        """)
    Flux<AdminPermission> findByRoleId(Long roleId);

    @Query("""
        SELECT DISTINCT p.* FROM public.admin_permission p
        JOIN public.role_permission rp ON rp.permission_id = p.id
        JOIN public.user_role ur ON ur.role_id = rp.role_id
        WHERE ur.user_id = :userId AND p.status = 'ACT'
        ORDER BY p.name
        """)
    Flux<AdminPermission> findByUserId(Long userId);
}