package com.kodika.kodikalab.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "submissions")
public class ProblemSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "status")
    private String status;

    @Column(name = "score")
    private Double score;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "team_membership_id")
    private Long teamMembershipId;

    @Column(name = "assignment_detail_id")
    private Long assignmentDetailId;

    @Column(name = "evidence_platform")
    private String evidencePlatform;

    @Column(name = "external_submission_id")
    private String externalSubmissionId;

    @Column(name = "validated_at")
    private OffsetDateTime validatedAt;

    public ProblemSubmission() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public void setEvidenceUrl(String evidenceUrl) {
        this.evidenceUrl = evidenceUrl;
    }

    public Long getTeamMembershipId() {
        return teamMembershipId;
    }

    public void setTeamMembershipId(Long teamMembershipId) {
        this.teamMembershipId = teamMembershipId;
    }

    public Long getAssignmentDetailId() {
        return assignmentDetailId;
    }

    public void setAssignmentDetailId(Long assignmentDetailId) {
        this.assignmentDetailId = assignmentDetailId;
    }

    public String getEvidencePlatform() {
        return evidencePlatform;
    }

    public void setEvidencePlatform(String evidencePlatform) {
        this.evidencePlatform = evidencePlatform;
    }

    public String getExternalSubmissionId() {
        return externalSubmissionId;
    }

    public void setExternalSubmissionId(String externalSubmissionId) {
        this.externalSubmissionId = externalSubmissionId;
    }

    public OffsetDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(OffsetDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

}
