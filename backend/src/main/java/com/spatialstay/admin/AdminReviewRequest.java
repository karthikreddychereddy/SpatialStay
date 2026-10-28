package com.spatialstay.admin;
import jakarta.validation.constraints.Size;
public record AdminReviewRequest(@Size(max=5000) String note) {}
