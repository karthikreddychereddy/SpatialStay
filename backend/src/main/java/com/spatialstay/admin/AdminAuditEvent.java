package com.spatialstay.admin;
import com.spatialstay.user.User;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="admin_audit_events",indexes=@Index(name="idx_admin_audit_created",columnList="created_at"))
public class AdminAuditEvent{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="admin_user_id",nullable=false) private User admin;
 @Column(nullable=false,length=80) private String action;
 @Column(nullable=false,length=80) private String targetType;
 @Column(nullable=false,length=120) private String targetId;
 @Column(columnDefinition="TEXT") private String detail;
 @Column(nullable=false,updatable=false) private Instant createdAt;
 protected AdminAuditEvent(){} public AdminAuditEvent(User admin,String action,String targetType,String targetId,String detail){this.admin=admin;this.action=action;this.targetType=targetType;this.targetId=targetId;this.detail=detail;}
 @PrePersist void create(){createdAt=Instant.now();}
 public Long getId(){return id;} public User getAdmin(){return admin;} public String getAction(){return action;} public String getTargetType(){return targetType;} public String getTargetId(){return targetId;} public String getDetail(){return detail;} public Instant getCreatedAt(){return createdAt;}
}