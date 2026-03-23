package com.mindscan.repository;

import com.mindscan.model.StressHistory;
import com.mindscan.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface StressHistoryRepository extends JpaRepository<StressHistory, Long> {

    List<StressHistory> findTop30ByUserOrderByCreatedAtDesc(User user);

    List<StressHistory> findByUserOrderByCreatedAtDesc(User user);

    long countByUser(User user);

    @Query("SELECT AVG(s.stressScore) FROM StressHistory s WHERE s.user = :user")
    Double findAverageStressScoreByUser(@Param("user") User user);

    @Query("SELECT s.stressLevel, COUNT(s) FROM StressHistory s WHERE s.user = :user GROUP BY s.stressLevel")
    List<Object[]> countByStressLevelForUser(@Param("user") User user);

    @Query("SELECT s FROM StressHistory s WHERE s.user = :user AND s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<StressHistory> findRecentByUser(@Param("user") User user, @Param("since") LocalDateTime since);
}
