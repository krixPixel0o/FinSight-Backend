package com.finsight.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class TestController {

    @GetMapping("/API/protected")
    public String protectedEndpoint() {
        return "This is a protected endpoint.";
    }
}
