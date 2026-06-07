package com.cambofreelance.apigateway.models;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = false)
@Table("api_group_route")
public class ApiGroupRoute extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 7013891582242164719L;

    @Id
    private Long id;
    private String code;
    private String uri;
    private String status;

    @Column("blue_uri")
    private String blueUri;

    @Column("green_uri")
    private String greenUri;

    @Column("active_slot")
    private String activeSlot;

    public String resolvedUri() {
        if ("GREEN".equalsIgnoreCase(activeSlot) && greenUri != null && !greenUri.isBlank()) {
            return greenUri;
        }
        return blueUri != null && !blueUri.isBlank() ? blueUri : uri;
    }
}
