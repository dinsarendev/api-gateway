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

    private final AdminUserRepository       userRepository;
    private final AdminRoleRepository       roleRepository;
    private final AdminPermissionRepository permissionRepository;
    private final UserRoleRepository        userRoleRepository;
    private final RolePermissionRepository  rolePermissionRepository;

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
        new String[]{"USER_WRITE",       "Create / update / delete admin users"}
    );

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            Long userCount = userRepository.count().block();
            if (userCount != null && userCount > 0) {
                log.info("Admin users already exist — skipping seed");
                return;
            }

            log.info("Seeding default admin user and roles...");

            // ── Permissions ───────────────────────────────────────────────────
            List<AdminPermission> savedPerms = (List<AdminPermission>) DEFAULT_PERMISSIONS.stream()
                .map(p -> permissionRepository.save(AdminPermission.builder()
                    .name(p[0]).description(p[1])
                    .createdAt(LocalDateTime.now()).createdBy("SYS")
                    .build()).block())
                .toList();

            // ── Role: SUPER_ADMIN ─────────────────────────────────────────────
            AdminRole superAdmin = roleRepository.save(AdminRole.builder()
                .name("SUPER_ADMIN").description("Full access to all admin functions")
                .createdAt(LocalDateTime.now()).createdBy("SYS")
                .build()).block();

            savedPerms.forEach(perm ->
                rolePermissionRepository.save(RolePermission.builder()
                    .roleId(superAdmin.getId())
                    .permissionId(perm.getId())
                    .build()).block()
            );

            // ── Role: OPERATOR ────────────────────────────────────────────────
            AdminRole operator = roleRepository.save(AdminRole.builder()
                .name("OPERATOR").description("Manage routes and groups, read-only on security")
                .createdAt(LocalDateTime.now()).createdBy("SYS")
                .build()).block();

            List.of("ROUTE_READ", "ROUTE_WRITE", "GROUP_READ", "GROUP_WRITE",
                    "REGISTRY_READ", "HEALTH_READ", "SECURITY_READ").forEach(permName ->
                savedPerms.stream()
                    .filter(p -> p.getName().equals(permName))
                    .findFirst()
                    .ifPresent(p -> rolePermissionRepository.save(RolePermission.builder()
                        .roleId(operator.getId()).permissionId(p.getId())
                        .build()).block())
            );

            // ── Role: VIEWER ──────────────────────────────────────────────────
            AdminRole viewer = roleRepository.save(AdminRole.builder()
                .name("VIEWER").description("Read-only access")
                .createdAt(LocalDateTime.now()).createdBy("SYS")
                .build()).block();

            List.of("ROUTE_READ", "GROUP_READ", "REGISTRY_READ", "HEALTH_READ", "SECURITY_READ")
                .forEach(permName ->
                    savedPerms.stream()
                        .filter(p -> p.getName().equals(permName))
                        .findFirst()
                        .ifPresent(p -> rolePermissionRepository.save(RolePermission.builder()
                            .roleId(viewer.getId()).permissionId(p.getId())
                            .build()).block())
                );

            // ── Default admin user ────────────────────────────────────────────
            AdminUser admin = userRepository.save(AdminUser.builder()
                .username("admin")
                .passwordHash(BCrypt.hashpw("admin123", BCrypt.gensalt(12)))
                .fullName("Super Admin")
                .email("admin@localhost")
                .createdAt(LocalDateTime.now()).createdBy("SYS")
                .build()).block();

            userRoleRepository.save(UserRole.builder()
                .userId(admin.getId()).roleId(superAdmin.getId())
                .build()).block();

            log.info("Admin seeder complete — login with admin / admin123");

        } catch (Exception e) {
            log.error("Admin seeder failed: {}", e.getMessage(), e);
        }
    }
}