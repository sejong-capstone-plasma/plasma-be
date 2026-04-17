package com.plasma.be.chat.exception;

public class SessionAccessDeniedException extends RuntimeException {

    public SessionAccessDeniedException() {
        super("Session is not accessible from the current browser.");
    }
}
