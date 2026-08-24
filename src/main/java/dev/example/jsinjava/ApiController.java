package dev.example.jsinjava;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    record Message(String message, Instant serverTime) {
    }

    @GetMapping("/api/message")
    Message message() {
        return new Message("Hello from Spring after hydration!", Instant.now());
    }
}
