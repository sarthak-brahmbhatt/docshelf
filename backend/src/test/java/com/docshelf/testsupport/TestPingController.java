// Test-only endpoints under /api so the token filter and the ProblemDetail error contract can be exercised without feature controllers
package com.docshelf.testsupport;

import com.docshelf.common.ConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestPingController {

    public record EchoRequest(@NotBlank String name) {
    }

    @GetMapping("/api/v1/_test/ping")
    public Map<String, String> ping() {
        return Map.of("pong", "ok");
    }

    @PostMapping("/api/v1/_test/echo")
    public Map<String, String> echo(@Valid @RequestBody EchoRequest req) {
        if ("conflict".equals(req.name())) {
            throw new ConflictException("name already taken");
        }
        return Map.of("name", req.name());
    }
}
