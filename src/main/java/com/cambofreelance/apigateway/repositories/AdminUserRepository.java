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

    @Query("""
        SELECT COUNT(*) FROM public.admin_user
        WHERE (:status IS NULL OR status = :status)
          AND (:search IS NULL OR username ILIKE :search OR email ILIKE :search OR full_name ILIKE :search)
        """)
    Mono<Long> countByFilter(String status, String search);

    @Query("""
        SELECT * FROM public.admin_user
        WHERE (:status IS NULL OR status = :status)
          AND (:search IS NULL OR username ILIKE :search OR email ILIKE :search OR full_name ILIKE :search)
        ORDER BY id DESC
        LIMIT :size OFFSET :offset
        """)
    Flux<AdminUser> findByFilter(String status, String search, int size, long offset);

    @Query("SELECT * FROM public.admin_user WHERE username = :username")
    Mono<AdminUser> findByUsername(String username);

    @Modifying
    @Query("UPDATE public.admin_user SET status = :status, updated_at = :now, updated_by = :updatedBy WHERE id = :id")
    Mono<Integer> updateStatus(Long id, String status, LocalDateTime now, String updatedBy);

    @Modifying
    @Query("UPDATE public.admin_user SET full_name = :fullName, email = :email, updated_at = :now, updated_by = :updatedBy WHERE id = :id")
    Mono<Integer> updateProfile(Long id, String fullName, String email, LocalDateTime now, String updatedBy);

    @Modifying
    @Query("UPDATE public.admin_user SET password_hash = :passwordHash, updated_at = :now, updated_by = :updatedBy WHERE id = :id")
    Mono<Integer> updatePassword(Long id, String passwordHash, LocalDateTime now, String updatedBy);
}