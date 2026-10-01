package com.kodika.kodikalab.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "external_accounts")
public class ExternalHandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform")
    private String platform;

    @Column(name = "handle")
    private String handle;

    @Column(name = "profile_url")
    private String profileUrl;

    @Column(name = "verified")
    private Boolean verified;

    @Column(name = "competitive_profile_id")
    private Long competitiveProfileId;

    public ExternalHandle() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Long getCompetitiveProfileId() {
        return competitiveProfileId;
    }

    public void setCompetitiveProfileId(Long competitiveProfileId) {
        this.competitiveProfileId = competitiveProfileId;
    }

}
