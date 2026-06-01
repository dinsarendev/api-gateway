package com.cambofreelance.apigateway.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.cambofreelance.apigateway.models.ApiRoute;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ApiRouteDto implements Serializable {

    private Long id;
    private String uri;
    private String path;
    private String method;
    private String groupCode;
    private String description;
    private String applicationId;
    private Integer rateLimit;
    private Integer rateLimitDuration;
    private String isPublic;
    private String isEncrypt;
    private String enableCircuitBreaker;
    private String status;
    private Integer priority;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String authType;
    private String requiredRoles;
    private String requiredPermissions;

    public void setData(ApiRoute data) {
        this.id = data.getId();
        this.uri = data.getUri();
        this.path = data.getPath();
        this.method = data.getMethod();
        this.groupCode = data.getGroupCode();
        this.description = data.getDescription();
        this.applicationId = data.getApplicationId();
        this.rateLimit = data.getRateLimit();
        this.rateLimitDuration = data.getRateLimitDuration();
        this.isPublic = data.getIsPublic();
        this.isEncrypt = data.getIsEncrypt();
        this.enableCircuitBreaker = data.getEnableCircuitBreaker();
        this.status = data.getStatus();
        this.priority = data.getPriority();
        this.startTime = data.getStartTime();
        this.endTime = data.getEndTime();
        this.authType = data.getAuthType();
        this.requiredRoles = data.getRequiredRoles();
        this.requiredPermissions = data.getRequiredPermissions();
    }
}
