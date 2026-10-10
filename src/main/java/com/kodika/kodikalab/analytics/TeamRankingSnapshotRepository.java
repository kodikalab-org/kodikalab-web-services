package com.kodika.kodikalab.analytics;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRankingSnapshotRepository extends JpaRepository<TeamRankingSnapshot, Integer> {
    @Modifying
    @Query(value = """
            insert into ranking_equipo_actual(grupo_id, calculado_en, resultado)
            values (:teamId, :calculatedAt, cast(:result as jsonb))
            on conflict (grupo_id) do update
            set calculado_en = excluded.calculado_en, resultado = excluded.resultado
            where ranking_equipo_actual.calculado_en < excluded.calculado_en
            """, nativeQuery = true)
    int replaceIfNewer(@Param("teamId") Integer teamId, @Param("calculatedAt") OffsetDateTime calculatedAt,
                       @Param("result") String result);
}
