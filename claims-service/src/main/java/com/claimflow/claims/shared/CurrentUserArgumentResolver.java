package com.claimflow.claims.shared;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/** Resolves a {@link CurrentUser} controller parameter from the {@code X-User-Id} header. */
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    static final String HEADER = "X-User-Id";

    private final AppUserRepository users;

    CurrentUserArgumentResolver(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            throw new UnauthenticatedException("Missing " + HEADER + " header");
        }
        UUID id;
        try {
            id = UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException(HEADER + " is not a valid UUID");
        }
        AppUser user = users.findById(id)
                .orElseThrow(() -> new UnauthenticatedException("Unknown user " + id));
        return new CurrentUser(user.getId(), user.getName(), user.getRole());
    }
}
