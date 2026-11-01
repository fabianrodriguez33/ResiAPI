package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.upeu.resi.ResiAPI.enums.Rol;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username")
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "nombre_completo")
    private String nombreCompleto;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol")
    private Rol rol;

    @Column(name = "dni")
    private String dni;

    @Column(name = "fecha_nacimiento")
    private java.time.LocalDate fechaNacimiento;

    @Column(name = "email")
    private String email;

    @Column(name = "email_verificado")
    private boolean emailVerificado;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "activo")
    private boolean activo;

    @Column(name = "creado_en")
    private Instant creadoEn;
}
