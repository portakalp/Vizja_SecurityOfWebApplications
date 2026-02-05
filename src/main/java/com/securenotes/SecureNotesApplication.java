package com.securenotes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the SecureNotes Manager application.
 * A secure REST API for managing private notes with strict data isolation.
 */
@SpringBootApplication
public class SecureNotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureNotesApplication.class, args);
    }
}
