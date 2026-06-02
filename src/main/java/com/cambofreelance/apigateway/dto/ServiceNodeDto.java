package com.cambofreelance.apigateway.dto;

import com.cambofreelance.apigateway.models.ServiceNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ServiceNodeDto {

    private Long id;
    private String serviceId;
    private String host;
    private Integer port;
    private boolean secure;
    private Integer weight;
    private String healthStatus;
    private String healthPath;
    private String baseUrl;
    private LocalDateTime lastHealthCheck;
    private String status;

    public static ServiceNodeDto from(ServiceNode node) {
        ServiceNodeDto dto = new ServiceNodeDto();
        dto.id              = node.getId();
        dto.serviceId       = node.getServiceId();
        dto.host            = node.getHost();
        dto.port            = node.getPort();
        dto.secure          = node.isSecure();
        dto.weight          = node.getWeight();
        dto.healthStatus    = node.getHealthStatus();
        dto.healthPath      = node.getHealthPath();
        dto.lastHealthCheck = node.getLastHealthCheck();
        dto.status          = node.getStatus();
        dto.baseUrl         = (node.isSecure() ? "https" : "http") + "://" + node.getHost() + ":" + node.getPort();
        return dto;
    }
}