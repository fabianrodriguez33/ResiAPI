package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "asistencia_culto")
@Getter
@Setter
@NoArgsConstructor
public class AsistenciaCulto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sesion_id")
    private Long sesionId;

    @Column(name = "residente_id")
    private Long residenteId;

    @Column(name = "registrado_en")
    private Instant registradoEn;

    @Column(name = "registrado_por")
    private Long registradoPor;

    @Column(name = "despues_del_corte")
    private boolean despuesDelCorte;

    @Column(name = "anulada")
    private boolean anulada;

    @Column(name = "anulada_por")
    private Long anuladaPor;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;
}
