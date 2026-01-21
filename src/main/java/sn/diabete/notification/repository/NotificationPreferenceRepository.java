package sn.diabete.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.diabete.notification.entity.NotificationPreference;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByPatientId(Long patientId);

    List<NotificationPreference> findByMedecinId(Long medecinId);

    @Query("SELECT np FROM NotificationPreference np WHERE " +
            "(np.rappelMatin BETWEEN :startTime AND :endTime) OR " +
            "(np.rappelMidi BETWEEN :startTime AND :endTime) OR " +
            "(np.rappelSoir BETWEEN :startTime AND :endTime)")
    List<NotificationPreference> findPatientsWithReminderBetween(
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    List<NotificationPreference> findAll();
}
