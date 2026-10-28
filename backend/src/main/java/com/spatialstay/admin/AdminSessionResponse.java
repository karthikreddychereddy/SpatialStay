package com.spatialstay.admin; import com.spatialstay.user.UserRole; import java.util.UUID;
public record AdminSessionResponse(UUID userPublicId,String email,String firstName,String lastName,UserRole role){}
