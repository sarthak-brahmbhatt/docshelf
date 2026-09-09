// ApiTokenFilter: 401 without / with wrong token, pass-through with Bearer or X-Api-Token, actuator health open
package com.docshelf.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docshelf.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ApiTokenFilterTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void missingTokenIs401ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/_test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value("https://docshelf.local/errors/unauthorized"));
    }

    @Test
    void wrongTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/_test/ping").header("Authorization", "Bearer nope"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/_test/ping").header("X-Api-Token", "test-token-but-longer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTokenPasses() throws Exception {
        mvc.perform(get("/api/v1/_test/ping").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pong").value("ok"));
    }

    @Test
    void apiTokenHeaderPasses() throws Exception {
        mvc.perform(get("/api/v1/_test/ping").header("X-Api-Token", "test-token"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownApiPathWithTokenIs404Problem() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist").header("X-Api-Token", "test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://docshelf.local/errors/not-found"));
    }

    @Test
    void validationAndDomainErrorsAreProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/_test/echo").header("X-Api-Token", "test-token")
                        .contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://docshelf.local/errors/validation"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/_test/echo"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
        mvc.perform(post("/api/v1/_test/echo").header("X-Api-Token", "test-token")
                        .contentType("application/json").content("{\"name\":\"conflict\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://docshelf.local/errors/conflict"))
                .andExpect(jsonPath("$.detail").value("name already taken"));
        mvc.perform(post("/api/v1/_test/echo").header("X-Api-Token", "test-token")
                        .contentType("application/json").content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://docshelf.local/errors/bad-request"));
    }

    @Test
    void preflightAndHealthAreOpen() throws Exception {
        mvc.perform(options("/api/v1/_test/ping").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
