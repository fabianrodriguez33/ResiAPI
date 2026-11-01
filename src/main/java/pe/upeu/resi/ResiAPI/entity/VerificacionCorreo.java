package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Código pendiente para confirmar el correo de un usuario. Solo existe uno por usuario. */
@Entity
@Table(name = "verificacion_correo")
@Getter
@Setter
@NoArgsConstructor
public class VerificacionCorreo {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "correo")
    private String correo;

    @Column(name = "codigo_hash")
    private String codigoHash;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    @Column(name = "expira_en")
    private Instant expiraEn;

    @Column(name = "intentos")
    private short intentos;
}
