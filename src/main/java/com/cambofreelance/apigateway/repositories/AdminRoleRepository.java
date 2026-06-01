package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.AdminRole;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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

    @Query("""
        SELECT COUNT(*) FROM public.admin_role
        WHERE (:status IS NULL OR status = :status)
          AND (:search IS NULL OR name ILIKE :search OR description ILIKE :search)
        """)
    Mono<Long> countByFilter(String status, String search);

    @Query("""
        SELECT * FROM public.admin_role
        WHERE (:status IS NULL OR status = :status)
          AND (:search IS NULL OR name ILIKE :search OR description ILIKE :search)
        ORDER BY id DESC
        LIMIT :size OFFSET :offset
        """)
    Flux<AdminRole> findByFilter(String status, String search, int size, long offset);

    @Modifying
    @Query("UPDATE public.admin_role SET status = :status, updated_at = :now, updated_by = :updatedBy WHERE id = :id")
    Mono<Integer> updateStatus(Long id, String status, LocalDateTime now, String updatedBy);
}