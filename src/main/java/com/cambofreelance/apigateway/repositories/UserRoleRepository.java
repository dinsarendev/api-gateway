package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.UserRole;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserRoleRepository extends R2dbcRepository<UserRole, Long> {

    @Modifying
    @Query("DELETE FROM public.user_role WHERE user_id = :userId")
    Mono<Integer> deleteByUserId(Long userId);
}