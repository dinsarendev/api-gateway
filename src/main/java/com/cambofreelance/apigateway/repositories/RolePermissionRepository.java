package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.RolePermission;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface RolePermissionRepository extends R2dbcRepository<RolePermission, Long> {

    @Modifying
    @Query("DELETE FROM public.role_permission WHERE role_id = :roleId")
    Mono<Integer> deleteByRoleId(Long roleId);
}