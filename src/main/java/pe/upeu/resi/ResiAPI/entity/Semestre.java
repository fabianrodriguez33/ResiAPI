package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Semestre de la residencia (2026-2). Nunca se borra: al crear el siguiente, este se cierra y conserva su historial. */
@Entity
@Table(name = "semestre")
@Getter
@Setter
@NoArgsConstructor
public class Semestre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "inicio")
    private LocalDate inicio;

    @Column(name = "fin")
    private LocalDate fin;

    @Column(name = "activo")
    private boolean activo;

    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "creado_en")
    private Instant creadoEn;

    @Column(name = "cerrado_en")
    private Instant cerradoEn;
}
