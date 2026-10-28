package com.spatialstay.admin;
import jakarta.validation.constraints.Size;
public record AdminDecisionRequest(@Size(max=4000) String note) {}
