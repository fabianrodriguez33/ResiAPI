package pe.upeu.resi.ResiAPI.enums;

/** Tipos de reserva de alimentación. Las dos cenas son excluyentes entre sí el mismo día. */
public enum TipoReserva {
    CENA_TAPER, CENA_SEGUNDO_TURNO, ALMUERZO, DESAYUNO_FRIO;

    public boolean esCena() {
        return this == CENA_TAPER || this == CENA_SEGUNDO_TURNO;
    }

    /** Cenas y almuerzo piden «curso o actividad» (opcional); el desayuno frío no. */
    public boolean admiteCurso() {
        return this != DESAYUNO_FRIO;
    }
}
