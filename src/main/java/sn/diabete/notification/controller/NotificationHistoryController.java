package sn.diabete.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.diabete.notification.entity.NotificationHistory;
import sn.diabete.notification.enums.StatutNotification;
import sn.diabete.notification.repository.NotificationHistoryRepository;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notification-history")
@RequiredArgsConstructor
public class NotificationHistoryController {

    private final NotificationHistoryRepository historyRepository;

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<NotificationHistory>> getHistoryByPatient(
            @PathVariable Long patientId) {
        List<NotificationHistory> history = historyRepository.findByPatientId(patientId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/medecin/{medecinId}")
    public ResponseEntity<List<NotificationHistory>> getHistoryByMedecin(
            @PathVariable Long medecinId) {
        List<NotificationHistory> history = historyRepository.findByMedecinId(medecinId);
        return ResponseEntity.ok(history);
    }

    @GetMapping
    public ResponseEntity<List<NotificationHistory>> getAllHistory() {
        List<NotificationHistory> history = historyRepository.findAll();
        return ResponseEntity.ok(history);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        NotificationHistory notification = historyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification non trouvée"));

        notification.setStatut(StatutNotification.LU);
        notification.setDateLecture(LocalDateTime.now());
        historyRepository.save(notification);

        return ResponseEntity.ok().build();
    }
}