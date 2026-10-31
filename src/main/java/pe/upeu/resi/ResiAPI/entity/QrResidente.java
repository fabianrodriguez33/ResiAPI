package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "qr_residente")
@Getter
@Setter
@NoArgsConstructor
public class QrResidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "residente_id")
    private Long residenteId;

    @Column(name = "codigo")
    private UUID codigo;

    @Column(name = "activo")
    private boolean activo;

    @Column(name = "creado_en")
    private Instant creadoEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;
}
