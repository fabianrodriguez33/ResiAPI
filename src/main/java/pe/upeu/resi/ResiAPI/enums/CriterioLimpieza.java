package pe.upeu.resi.ResiAPI.enums;

/** Criterios de la evaluación de limpieza (propuesta: total 20 puntos). */
public enum CriterioLimpieza {
    ROPERO(5), CAMA(3), ESCRITORIO(3), BASURA(3), OLORES(3), PISO(3);

    private final int maximo;

    CriterioLimpieza(int maximo) {
        this.maximo = maximo;
    }

    public int getMaximo() {
        return maximo;
    }
}
