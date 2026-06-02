package com.cambofreelance.apigateway.startup;

import com.cambofreelance.apigateway.models.*;
import com.cambofreelance.apigateway.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder {

    private final AdminUserRepository        userRepository;
    private final AdminRoleRepository        roleRepository;
    private final AdminPermissionRepository  permissionRepository;
    private final UserRoleRepository         userRoleRepository;
    private final RolePermissionRepository   rolePermissionRepository;

    private static final List<String[]> DEFAULT_PERMISSIONS = List.of(
        new String[]{"ROUTE_READ",       "View routes"},
        new String[]{"ROUTE_WRITE",      "Create / update / delete routes"},
        new String[]{"GROUP_READ",       "View service groups"},
        new String[]{"GROUP_WRITE",      "Create / update / delete groups"},
        new String[]{"REGISTRY_READ",    "View service registry"},
        new String[]{"REGISTRY_WRITE",   "Manage service instances"},
        new String[]{"HEALTH_READ",      "View health monitor"},
        new String[]{"SECURITY_READ",    "View security settings"},
        new String[]{"SECURITY_WRITE",   "Manage API keys, IP rules, OAuth2 providers"},
        new String[]{"USER_READ",        "View admin users"},
        new String[]{"USER_WRITE",       "Create / update / delete admin users"},
        new String[]{"ROLE_READ",        "View roles and permissions"},
        new String[]{"ROLE_WRITE",       "Create / update / delete roles and assign permissions"}
    );

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            // ── 1. Upsert all permissions ─────────────────────────────────────
            List<AdminPermission> allPerms = DEFAULT_PERMISSIONS.stream()
                .map(p -> upsertPermission(p[0], p[1]))
                .toList();

            // ── 2. Upsert SUPER_ADMIN role ────────────────────────────────────
            AdminRole superAdmin = upsertRole("SUPER_ADMIN", "Full access to all admin functions");

            // ── 3. Ensure SUPER_ADMIN has every permission ────────────────────
            for (AdminPermission perm : allPerms) {
                rolePermissionRepository
                    .findByRoleIdAndPermissionId(superAdmin.getId(), perm.getId())
                    .switchIfEmpty(rolePermissionRepository.save(
                        RolePermission.builder()
                            .roleId(superAdmin.getId())
                            .permissionId(perm.getId())
                            .build()))
                    .block();
            }

            // ── 4. Upsert OPERATOR role + permissions ─────────────────────────
            AdminRole operator = upsertRole("OPERATOR", "Manage routes and groups, read-only on security");
            List<String> operatorPerms = List.of(
                "ROUTE_READ", "ROUTE_WRITE", "GROUP_READ", "GROUP_WRITE",
                "REGISTRY_READ", "HEALTH_READ", "SECURITY_READ",
                "ROLE_READ"
            );
            for (AdminPermission perm : allPerms) {
                if (operatorPerms.contains(perm.getName())) {
                    rolePermissionRepository
                        .findByRoleIdAndPermissionId(operator.getId(), perm.getId())
                        .switchIfEmpty(rolePermissionRepository.save(
                            RolePermission.builder()
                                .roleId(operator.getId())
                                .permissionId(perm.getId())
                                .build()))
                        .block();
                }
            }

            // ── 5. Upsert VIEWER role + permissions ───────────────────────────
            AdminRole viewer = upsertRole("VIEWER", "Read-only access");
            List<String> viewerPerms = List.of(
                "ROUTE_READ", "GROUP_READ", "REGISTRY_READ", "HEALTH_READ", "SECURITY_READ",
                "ROLE_READ", "USER_READ"
            );
            for (AdminPermission perm : allPerms) {
                if (viewerPerms.contains(perm.getName())) {
                    rolePermissionRepository
                        .findByRoleIdAndPermissionId(viewer.getId(), perm.getId())
                        .switchIfEmpty(rolePermissionRepository.save(
                            RolePermission.builder()
                                .roleId(viewer.getId())
                                .permissionId(perm.getId())
                                .build()))
                        .block();
                }
            }

            // ── 6. Create default admin user (first boot only) ────────────────
            Long userCount = userRepository.count().block();
            if (userCount == null || userCount == 0) {
                AdminUser admin = userRepository.save(AdminUser.builder()
                    .username("admin")
                    .passwordHash(BCrypt.hashpw("admin123", BCrypt.gensalt(12)))
                    .fullName("Super Admin")
                    .email("admin@localhost")
                    .createdAt(LocalDateTime.now())
                    .createdBy("SYS")
                    .build()).block();

                userRoleRepository.save(
                    UserRole.builder().userId(admin.getId()).roleId(superAdmin.getId()).build()
                ).block();

                log.info("Admin seeder — created default user: admin / admin123");
            } else {
                log.info("Admin seeder — permissions/roles synced ({} permissions, users already exist)",
                    allPerms.size());
            }

        } catch (Exception e) {
            log.error("Admin seeder failed: {}", e.getMessage(), e);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AdminPermission upsertPermission(String name, String description) {
        return permissionRepository.findActiveByName(name)
            .switchIfEmpty(permissionRepository.save(AdminPermission.builder()
                .name(name)
                .description(description)
                .createdAt(LocalDateTime.now())
                .createdBy("SYS")
                .build()))
            .block();
    }

    private AdminRole upsertRole(String name, String description) {
        return roleRepository.findActiveByName(name)
            .switchIfEmpty(roleRepository.save(AdminRole.builder()
                .name(name)
                .description(description)
                .createdAt(LocalDateTime.now())
                .createdBy("SYS")
                .build()))
            .block();
    }
}