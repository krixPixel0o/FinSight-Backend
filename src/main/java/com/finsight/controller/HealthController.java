package com.finsight.controller;

import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/API")
public class HealthController {

    @GetMapping("/healthio")
    public String health() {
        return "health checked !";
    }
    
}
