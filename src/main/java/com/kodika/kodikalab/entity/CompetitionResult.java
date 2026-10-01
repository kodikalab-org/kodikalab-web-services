package com.kodika.kodikalab.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "competition_results")
public class CompetitionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "score")
    private Double score;

    @Column(name = "solved_problems")
    private Integer solvedProblems;

    @Column(name = "status")
    private String status;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "competition_id")
    private Long competitionId;

    public CompetitionResult() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(Integer rankPosition) {
        this.rankPosition = rankPosition;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Integer getSolvedProblems() {
        return solvedProblems;
    }

    public void setSolvedProblems(Integer solvedProblems) {
        this.solvedProblems = solvedProblems;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public Long getCompetitionId() {
        return competitionId;
    }

    public void setCompetitionId(Long competitionId) {
        this.competitionId = competitionId;
    }

}
