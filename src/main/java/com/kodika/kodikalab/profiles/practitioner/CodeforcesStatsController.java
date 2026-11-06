package com.kodika.kodikalab.profiles.practitioner;

import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.dto.CodeforcesStatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/codeforces/stats")
public class CodeforcesStatsController {
    private final CurrentUserResolver currentUser;
    private final CodeforcesStatsService stats;

    public CodeforcesStatsController(CurrentUserResolver currentUser, CodeforcesStatsService stats) {
        this.currentUser = currentUser;
        this.stats = stats;
    }

    @GetMapping
    public CodeforcesStatsResponse getStats() {
        return stats.getStats(currentUser.currentUser());
    }
}
