package com.mindscan.repository;

import com.mindscan.model.User;
import com.mindscan.model.UserBehaviorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserBehaviorDataRepository extends JpaRepository<UserBehaviorData, Long> {

    List<UserBehaviorData> findByUserOrderByRecordDateDesc(User user);

    Optional<UserBehaviorData> findByUserAndRecordDate(User user, LocalDate date);

    @Query("SELECT b FROM UserBehaviorData b WHERE b.user = :user AND b.recordDate >= :since ORDER BY b.recordDate DESC")
    List<UserBehaviorData> findRecentByUser(@Param("user") User user, @Param("since") LocalDate since);

    @Query("SELECT b FROM UserBehaviorData b WHERE b.user = :user ORDER BY b.recordDate DESC")
    List<UserBehaviorData> findLast30ByUser(@Param("user") User user,
        org.springframework.data.domain.Pageable pageable);
}
