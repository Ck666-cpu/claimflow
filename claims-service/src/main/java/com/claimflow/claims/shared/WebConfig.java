package com.claimflow.claims.shared;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.List;

@Configuration
class WebConfig implements WebMvcConfigurer {

    private static final String USER_SCHEME = "userId";

    static {
        // CurrentUser comes from a header, not the request body or query: keep it out of the OpenAPI spec
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    private final AppUserRepository users;

    WebConfig(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver(users));
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Adds an Authorize button to Swagger UI that sends X-User-Id on every request. */
    @Bean
    OpenAPI claimFlowOpenApi() {
        return new OpenAPI()
                .info(new Info().title("ClaimFlow claims API").version("v1")
                        .description("Stub authentication: send a seeded user id in the X-User-Id header."))
                .components(new Components().addSecuritySchemes(USER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(CurrentUserArgumentResolver.HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(USER_SCHEME));
    }
}
