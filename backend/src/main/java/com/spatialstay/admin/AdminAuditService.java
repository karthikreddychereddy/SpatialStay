package com.spatialstay.admin;
import com.spatialstay.user.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class AdminAuditService {private final CurrentUser current;private final UserRepository users;private final AdminAuditEventRepository repo;
 public AdminAuditService(CurrentUser c,UserRepository u,AdminAuditEventRepository r){current=c;users=u;repo=r;}
 @Transactional public void record(String action,String targetType,String targetId,String details){User admin=users.findByPublicId(current.publicId()).orElseThrow();repo.save(new AdminAuditEvent(admin,action,targetType,targetId,details));}}
