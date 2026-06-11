package com.hris.identity.authserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the custom-branded login page used by the authorization server's
 * form login. Error/logout states arrive as query parameters set by Spring
 * Security and are interpreted in the template.
 */
@Controller
public class LoginPageController {

    @Value("${app.auth.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("frontendUrl", frontendUrl);
        return "login";
    }
}
