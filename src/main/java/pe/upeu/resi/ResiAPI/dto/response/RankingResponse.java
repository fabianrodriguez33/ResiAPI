package pe.upeu.resi.ResiAPI.dto.response;

import java.time.LocalDate;

/** Ranking del residente en el ciclo en curso: cultos asistidos y promedio de limpieza (sobre 20). */
public record RankingResponse(LocalDate desde, LocalDate hasta, Puesto cultos, Puesto limpieza) {

    /** {@code posicion} y {@code valor} vienen vacíos si el residente aún no tiene datos; {@code total} son los comparados. */
    public record Puesto(Integer posicion, int total, Double valor) {
    }
}
