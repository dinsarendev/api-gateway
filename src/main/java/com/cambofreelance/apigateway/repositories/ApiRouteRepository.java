package com.cambofreelance.apigateway.repositories;

import com.cambofreelance.apigateway.models.ApiRoute;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ApiRouteRepository extends R2dbcRepository<ApiRoute, Long> {

    @Query("""
        select a.*, g.uri as uri
                from public.api_route a
                inner join public.api_group_route g on g.code = a.group_code
                        WHERE path = :path AND method = :method and a.status = 'ACT'
        """)
    Mono<ApiRoute> findFirstByPathAndMethod(String path, String method);

    @Query("""
              
        SELECT a.*, g.uri AS uri
                                FROM public.api_route a
                                LEFT JOIN public.api_group_route g ON g.code = a.group_code
                                where a.status=:status
         """)
    Flux<ApiRoute> findAllByStatus(String status);
}
