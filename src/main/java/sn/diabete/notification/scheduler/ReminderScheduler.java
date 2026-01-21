package sn.diabete.notification.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sn.diabete.notification.client.AuthClient;
import sn.diabete.notification.client.PatientClient;
import sn.diabete.notification.dto.PatientDTO;
import sn.diabete.notification.entity.NotificationPreference;
import sn.diabete.notification.enums.TypeAlerte;
import sn.diabete.notification.repository.NotificationPreferenceRepository;
import sn.diabete.notification.service.NotificationService;

import java.time.LocalTime;
import java.util.List;

/**
 * Scheduler responsable de l'envoi des rappels de mesure de glycémie
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationService notificationService;
    private final PatientClient patientClient;
    private final AuthClient authClient;  // 🆕 Ajouté

    /**
     * Vérifie toutes les heures si des rappels doivent être envoyés
     * S'exécute à chaque heure pile (00 minutes)
     */
    @Scheduled(cron = "${scheduler.rappel.cron}")
    public void checkAndSendReminders() {
        log.info("🔔 Démarrage de la vérification des rappels programmés");

        LocalTime now = LocalTime.now();
        LocalTime start = now.minusMinutes(30);
        LocalTime end = now.plusMinutes(30);

        // Récupérer tous les patients ayant un rappel dans cette fenêtre
        List<NotificationPreference> preferences =
                preferenceRepository.findPatientsWithReminderBetween(start, end);

        log.info("Nombre de patients avec rappels à vérifier: {}", preferences.size());

        for (NotificationPreference pref : preferences) {
            try {
                // Vérifier chaque moment de rappel
                checkAndSendReminderForMoment(pref, pref.getRappelMatin(), "matin", now);
                checkAndSendReminderForMoment(pref, pref.getRappelMidi(), "midi", now);
                checkAndSendReminderForMoment(pref, pref.getRappelSoir(), "soir", now);

            } catch (Exception e) {
                log.error("Erreur lors du traitement du rappel pour patient {}: {}",
                        pref.getPatientId(), e.getMessage());
            }
        }

        log.info("✅ Vérification des rappels terminée");
    }

    private void checkAndSendReminderForMoment(NotificationPreference pref,
                                               LocalTime rappelTime,
                                               String moment,
                                               LocalTime now) {
        if (rappelTime == null) {
            return;
        }

        // Vérifier si l'heure de rappel correspond (avec une marge de 30 minutes)
        if (Math.abs(rappelTime.toSecondOfDay() - now.toSecondOfDay()) > 1800) {
            return;
        }

        // TODO: Vérifier si le patient a déjà mesuré aujourd'hui à ce moment
        // Cela nécessiterait un appel à suivi-medical-service

        // Envoyer le rappel
        sendReminder(pref, moment);
    }

    private void sendReminder(NotificationPreference pref, String moment) {
        try {
            // 1. Récupérer les informations du patient
            PatientDTO patient = patientClient.getPatientById(pref.getPatientId());

            // 2. 🆕 Récupérer l'email du patient depuis auth-service
            String emailPatient = null;
            try {
                emailPatient = authClient.getUserEmail(patient.getUtilisateurId());
            } catch (Exception e) {
                log.error("❌ Impossible de récupérer l'email du patient {} : {}",
                        pref.getPatientId(), e.getMessage());
                return;
            }

            // 3. Construire le message de rappel
            String message = String.format(
                    "Bonjour %s,\n\n" +
                            "🔔 C'est l'heure de mesurer votre glycémie (%s).\n\n" +
                            "N'oubliez pas d'enregistrer votre mesure dans l'application pour un meilleur suivi.\n\n" +
                            "Prenez soin de vous,\n" +
                            "Votre équipe de suivi médical - SUIVIDIABETE SN",
                    patient.getPrenom(),
                    moment
            );

            // 4. Envoyer par email si activé
            if (Boolean.TRUE.equals(pref.getAlerteEmailActif()) && emailPatient != null) {
                notificationService.sendEmail(
                        emailPatient,
                        "🔔 Rappel de mesure de glycémie - " + moment,
                        message,
                        pref.getPatientId(),
                        null,
                        TypeAlerte.RAPPEL_MESURE,
                        null
                );
                log.info("✅ Email de rappel {} envoyé au patient {} ({})",
                        moment, pref.getPatientId(), emailPatient);
            }

            // 5. Envoyer par SMS si activé
            if (Boolean.TRUE.equals(pref.getAlerteSmsActif()) && patient.getTelephone() != null) {
                String smsMessage = String.format(
                        "Rappel SUIVIDIABETE: Mesurez votre glycémie (%s). Enregistrez dans l'app.",
                        moment
                );

                notificationService.sendSms(
                        patient.getTelephone(),
                        smsMessage,
                        pref.getPatientId(),
                        null,
                        TypeAlerte.RAPPEL_MESURE,
                        null
                );
                log.info("✅ SMS de rappel {} envoyé au patient {} ({})",
                        moment, pref.getPatientId(), patient.getTelephone());
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du rappel au patient {}: {}",
                    pref.getPatientId(), e.getMessage(), e);
        }
    }
}