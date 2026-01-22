package sn.diabete.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sn.diabete.notification.client.AuthClient;
import sn.diabete.notification.client.MedecinClient;
import sn.diabete.notification.client.PatientClient;
import sn.diabete.notification.dto.MedecinDTO;
import sn.diabete.notification.dto.PatientDTO;
import sn.diabete.notification.entity.NotificationPreference;
import sn.diabete.notification.enums.TypeAlerte;
import sn.diabete.notification.event.GlycemieEvent;
import sn.diabete.notification.repository.NotificationPreferenceRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final NotificationService notificationService;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PatientClient patientClient;
    private final MedecinClient medecinClient;
    private final AuthClient authClient;

    public void handleGlycemieEvent(GlycemieEvent event) {
        log.info("📨 Traitement événement glycémie pour patient {}", event.getPatientId());

        try {
            sendPatientAlert(event);

            if (Boolean.TRUE.equals(event.getAlerterMedecin())) {
                sendDoctorAlert(event);
            }

        } catch (Exception e) {
            log.error("❌ Erreur lors du traitement de l'événement : {}", e.getMessage(), e);
        }
    }

    private void sendPatientAlert(GlycemieEvent event) {
        Long patientId = event.getPatientId();

        try {
            NotificationPreference preferences = preferenceRepository
                    .findByPatientId(patientId)
                    .orElseGet(() -> createDefaultPreferences(patientId));

            PatientDTO patient = patientClient.getPatientById(patientId);

            String emailPatient = null;
            try {
                emailPatient = authClient.getUserEmail(patient.getUtilisateurId());
                log.info("📧 Email patient récupéré : {}", emailPatient);
            } catch (Exception e) {
                log.error("❌ Impossible de récupérer l'email du patient {} : {}",
                        patientId, e.getMessage());
                return;
            }

            String message = buildPatientMessage(patient, event);

            if (Boolean.TRUE.equals(preferences.getAlerteEmailActif()) && emailPatient != null) {
                notificationService.sendEmail(
                        emailPatient,
                        "⚠️ Alerte Glycémie - " + event.getTypeAlerte().getLibelle(),
                        message,
                        patientId,
                        null,
                        event.getTypeAlerte(),
                        event.getGlycemieId()
                );
                log.info("✅ Email envoyé au patient {} ({})", patientId, emailPatient);
            }

            if (Boolean.TRUE.equals(preferences.getAlerteSmsActif()) && patient.getTelephone() != null) {
                String smsMessage = buildShortMessage(event);
                notificationService.sendSms(
                        patient.getTelephone(),
                        smsMessage,
                        patientId,
                        null,
                        event.getTypeAlerte(),
                        event.getGlycemieId()
                );
                log.info("✅ SMS envoyé au patient {} ({})", patientId, patient.getTelephone());
            }

            log.info("✅ Alerte patient {} traitée avec succès", patientId);

        } catch (Exception e) {
            log.error("❌ Erreur lors de l'envoi de l'alerte patient {}: {}",
                    patientId, e.getMessage(), e);
        }
    }

    // 🆕 MÉTHODE CORRIGÉE - Récupère medecinId DEPUIS LE PATIENT
    private void sendDoctorAlert(GlycemieEvent event) {
        Long patientId = event.getPatientId();

        try {
            // 1. Récupérer les informations du patient
            PatientDTO patient = patientClient.getPatientById(patientId);

            // 2. 🆕 CORRECTION : Récupérer medecinId DEPUIS le patient
            Long medecinId = patient.getMedecinId();

            if (medecinId == null) {
                log.warn("⚠️ Pas de médecin assigné au patient {}", patientId);
                return;
            }

            log.info("👨‍⚕️ Médecin référent du patient {} : ID {}", patientId, medecinId);

            // 3. Récupérer les informations du médecin
            MedecinDTO medecin = null;
            try {
                medecin = medecinClient.getMedecinById(medecinId);
                log.info("👨‍⚕️ Médecin récupéré : Dr {} {}", medecin.getPrenom(), medecin.getNom());
            } catch (Exception e) {
                log.error("❌ Impossible de récupérer le médecin {} : {}",
                        medecinId, e.getMessage());
                return;
            }

            // 4. Récupérer l'email du médecin depuis auth-service
            String emailMedecin = null;
            try {
                emailMedecin = authClient.getUserEmail(medecin.getUtilisateurId());
                log.info("📧 Email médecin récupéré : {}", emailMedecin);
            } catch (Exception e) {
                log.error("❌ Impossible de récupérer l'email du médecin {} : {}",
                        medecin.getId(), e.getMessage());
                return;
            }

            // 5. Construire le message pour le médecin
            String message = buildDoctorMessage(patient, medecin, event);

            // 6. Envoyer l'email
            notificationService.sendEmail(
                    emailMedecin,
                    "🚨 ALERTE PATIENT - " + patient.getPrenom() + " " + patient.getNom(),
                    message,
                    patientId,
                    medecinId,  // 🆕 Utiliser le medecinId du patient
                    event.getTypeAlerte(),
                    event.getGlycemieId()
            );

            log.info("✅ Alerte médecin {} envoyée pour patient {} ({})",
                    medecinId, patientId, emailMedecin);

        } catch (Exception e) {
            log.error("❌ Erreur lors de l'envoi de l'alerte médecin : {}",
                    e.getMessage(), e);
        }
    }

    // 🆕 MÉTHODE CORRIGÉE - Récupère medecinId DEPUIS LE PATIENT
    public void sendInactivityAlert(Long patientId, int joursInactivite) {
        try {
            // 1. Récupérer les informations du patient
            PatientDTO patient = patientClient.getPatientById(patientId);

            // 2. 🆕 CORRECTION : Récupérer medecinId DEPUIS le patient
            Long medecinId = patient.getMedecinId();

            if (medecinId == null) {
                log.warn("⚠️ Pas de médecin assigné au patient inactif {}", patientId);
                return;
            }

            // 3. Récupérer les informations du médecin
            MedecinDTO medecin = medecinClient.getMedecinById(medecinId);

            // 4. Récupérer l'email du médecin
            String emailMedecin = authClient.getUserEmail(medecin.getUtilisateurId());

            // 5. Construire le message
            String message = String.format(
                    "Bonjour Dr,\n\n" +
                            "Le patient %s %s n'a pas enregistré de mesure de glycémie depuis %d jours.\n\n" +
                            "- Téléphone : %s\n\n" +
                            "Il est recommandé de contacter le patient pour vérifier son état.\n\n" +
                            "Cordialement,\n" +
                            "Système de Suivi Diabète - SUIVIDIABETE SN",
                    patient.getPrenom(),
                    patient.getNom(),
                    joursInactivite,
                    patient.getTelephone()
            );

            // 6. Envoyer l'email
            notificationService.sendEmail(
                    emailMedecin,
                    "⚠️ Patient inactif - " + patient.getPrenom() + " " + patient.getNom(),
                    message,
                    patientId,
                    medecinId,  // 🆕 Utiliser le medecinId du patient
                    TypeAlerte.INACTIVITE_PATIENT,
                    null
            );

            log.info("✅ Alerte d'inactivité envoyée pour patient {} ({} jours)",
                    patientId, joursInactivite);

        } catch (Exception e) {
            log.error("❌ Erreur lors de l'alerte d'inactivité pour patient {}: {}",
                    patientId, e.getMessage(), e);
        }
    }

    private NotificationPreference createDefaultPreferences(Long patientId) {
        log.info("Création des préférences par défaut pour patient {}", patientId);
        NotificationPreference pref = new NotificationPreference();
        pref.setPatientId(patientId);
        pref.setAlerteEmailActif(true);
        pref.setAlerteSmsActif(false);
        return preferenceRepository.save(pref);
    }

    private String buildPatientMessage(PatientDTO patient, GlycemieEvent event) {
        return String.format(
                "Bonjour %s,\n\n" +
                        "%s\n\n" +
                        "📊 Détails de la mesure :\n" +
                        "- Valeur : %.2f g/L\n" +
                        "- Date : %s\n" +
                        "- Moment de la prise : %s\n" +
                        "- Type de repas : %s\n\n" +
                        "💡 Recommandation :\n" +
                        "%s\n\n" +
                        "Prenez soin de vous,\n" +
                        "Votre équipe de suivi médical - SUIVIDIABETE SN",
                patient.getPrenom(),
                event.getMessage(),
                event.getValeurGlycemie(),
                event.getDateEnregistrement(),
                event.getMoment() != null ? event.getMoment() : "Non spécifié",
                event.getRepas() != null ? event.getRepas() : "Non spécifié",
                event.getRecommandation()
        );
    }

    private String buildDoctorMessage(PatientDTO patient, MedecinDTO medecin, GlycemieEvent event) {
        return String.format(
                "Bonjour Dr,\n\n" +
                        "🚨 ALERTE CRITIQUE pour le patient %s %s\n\n" +
                        "📊 Mesure de glycémie :\n" +
                        "- Type d'alerte : %s\n" +
                        "- Valeur mesurée : %.2f g/L\n" +
                        "- Date de mesure : %s\n" +
                        "- Moment de la prise : %s\n" +
                        "- Type de repas : %s\n\n" +
                        "- Téléphone : %s\n" +
                        "⚠️ Action recommandée :\n" +
                        "Contacter le patient rapidement pour évaluer sa situation.\n\n" +
                        "Cordialement,\n" +
                        "Système de Suivi Diabète - SUIVIDIABETE SN",
                patient.getPrenom(),
                patient.getNom(),
                event.getTypeAlerte().getLibelle(),
                event.getValeurGlycemie(),
                event.getDateEnregistrement(),
                event.getMoment() != null ? event.getMoment() : "Non spécifié",
                event.getRepas() != null ? event.getRepas() : "Non spécifié",
                patient.getTelephone()
        );
    }

    private String buildShortMessage(GlycemieEvent event) {
        return String.format(
                "ALERTE: %s détectée (%.2f g/L). %s",
                event.getTypeAlerte().getLibelle(),
                event.getValeurGlycemie(),
                event.getRecommandation().substring(0, Math.min(100, event.getRecommandation().length()))
        );
    }
}