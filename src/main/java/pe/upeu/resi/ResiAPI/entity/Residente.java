package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "residente")
@Getter
@Setter
@NoArgsConstructor
public class Residente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "codigo")
    private String codigo;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "activo")
    private boolean activo;

    @Column(name = "creado_en")
    private Instant creadoEn;
}
