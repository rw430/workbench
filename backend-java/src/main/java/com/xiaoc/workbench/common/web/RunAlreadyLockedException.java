package com.xiaoc.workbench.common.web;

public class RunAlreadyLockedException extends RuntimeException {
    public RunAlreadyLockedException(String message) {
        super(message);
    }
}
