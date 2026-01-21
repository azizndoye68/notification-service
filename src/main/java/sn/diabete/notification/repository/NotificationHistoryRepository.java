package sn.diabete.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.diabete.notification.entity.NotificationHistory;
import sn.diabete.notification.enums.StatutNotification;
import sn.diabete.notification.enums.TypeAlerte;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    List<NotificationHistory> findByPatientId(Long patientId);

    List<NotificationHistory> findByMedecinId(Long medecinId);

    List<NotificationHistory> findByStatut(StatutNotification statut);

    List<NotificationHistory> findByTypeAlerte(TypeAlerte typeAlerte);

    List<NotificationHistory> findByPatientIdAndDateEnvoiAfter(Long patientId, LocalDateTime date);

    Long countByPatientIdAndStatut(Long patientId, StatutNotification statut);
}