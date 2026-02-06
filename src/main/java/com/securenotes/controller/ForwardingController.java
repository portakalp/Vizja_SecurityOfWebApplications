package com.securenotes.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to forward client-side routes to index.html
 * This enables SPA-style routing for the frontend.
 */
@Controller
public class ForwardingController {

    @GetMapping("/notes/{id}")
    public String forwardNoteDetail() {
        return "forward:/index.html";
    }
}
