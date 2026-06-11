package com.hris.identity.authserver;

import com.hris.tenancy.Tenant;
import com.hris.tenancy.TenantContext;
import com.hris.tenancy.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the custom-branded login page used by the authorization server's
 * form login. Error/logout states arrive as query parameters set by Spring
 * Security and are interpreted in the template. The resolved tenant (set by
 * LoginTenantFilter) travels as a hidden form field so the credential POST
 * authenticates against the same tenant the visitor saw.
 */
@Controller
@RequiredArgsConstructor
public class LoginPageController {

    private final TenantRepository tenantRepository;

    @Value("${app.auth.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        model.addAttribute("frontendUrl", frontendUrl);

        Tenant tenant = tenantRepository.findById(
                TenantContext.get() != null ? TenantContext.get() : TenantContext.DEFAULT_TENANT_ID)
            .orElse(null);
        model.addAttribute("tenantSlug", tenant != null ? tenant.getSlug() : "default");
        model.addAttribute("tenantName", tenant != null ? tenant.getName() : null);
        return "login";
    }
}
