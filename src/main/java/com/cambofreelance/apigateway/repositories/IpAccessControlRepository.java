package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.IpAccessControl;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface IpAccessControlRepository extends R2dbcRepository<IpAccessControl, Long> {

    @Query("SELECT * FROM public.ip_access_control WHERE status = :status ORDER BY type, scope, id")
    Flux<IpAccessControl> findAllByStatus(String status);
}