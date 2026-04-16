package com.epam.ratelimitergateway.showcase;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ShowcaseController {

    @GetMapping({"/showcase", "/showcase/"})
    public String showcase() {
        return "forward:/showcase/index.html";
    }
}
