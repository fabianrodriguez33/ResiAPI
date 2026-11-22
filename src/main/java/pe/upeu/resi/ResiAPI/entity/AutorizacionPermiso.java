package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;

/** Autorización previa (hoja firmada) para que un estudiante envíe un tipo de permiso sin pasar por su apoderado. */
@Entity
@Table(name = "autorizacion_permiso")
@Getter
@Setter
@NoArgsConstructor
public class AutorizacionPermiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "residente_id")
    private Long residenteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoPermiso tipo;

    @Column(name = "inicio")
    private LocalDate inicio;

    @Column(name = "fin")
    private LocalDate fin;

    @Column(name = "referencia")
    private String referencia;

    @Column(name = "registrada_por")
    private Long registradaPor;

    @Column(name = "registrada_en")
    private Instant registradaEn;

    @Column(name = "revocada")
    private boolean revocada;

    @Column(name = "revocada_por")
    private Long revocadaPor;

    @Column(name = "revocada_en")
    private Instant revocadaEn;

    @Column(name = "motivo_revocacion")
    private String motivoRevocacion;
}
