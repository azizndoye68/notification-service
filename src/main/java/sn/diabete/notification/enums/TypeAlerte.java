package sn.diabete.notification.enums;

public enum TypeAlerte {
    HYPOGLYCEMIE_SEVERE("Hypoglycémie sévère"),
    HYPOGLYCEMIE("Hypoglycémie"),
    HYPERGLYCEMIE("Hyperglycémie"),
    HYPERGLYCEMIE_SEVERE("Hyperglycémie sévère"),
    INACTIVITE_PATIENT("Patient inactif"),
    RAPPEL_MESURE("Rappel de mesure");

    private final String libelle;

    TypeAlerte(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() { return libelle; }
}