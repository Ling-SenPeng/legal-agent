package com.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for Agent endpoint
 */
@RestController
public class AgentController {

    @GetMapping("/")
    public String agent() {
        return "Agent, World!";
    }

    @GetMapping("/api/greeting")
    public Greeting greeting() {
        return new Greeting("Greetings from Legal Agent Spring Boot Application!");
    }

    /**
     * Simple greeting model
     */
    public static class Greeting {
        private String message;

        public Greeting(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
