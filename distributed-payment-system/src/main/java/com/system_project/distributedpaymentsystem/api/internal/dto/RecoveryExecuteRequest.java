package com.system_project.distributedpaymentsystem.api.internal.dto;

public class RecoveryExecuteRequest {
    private String action;
    private String targetProvider;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetProvider() { return targetProvider; }
    public void setTargetProvider(String targetProvider) { this.targetProvider = targetProvider; }
}
