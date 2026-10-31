-- Esquema inicial de ResiUP. Fechas/horas absolutas en timestamptz (UTC); la zona de la residencia es America/Lima.

CREATE TABLE usuario (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    nombre_completo VARCHAR(150) NOT NULL,
    rol             VARCHAR(20)  NOT NULL CHECK (rol IN ('RESIDENTE', 'PRECEPTOR', 'GARITA', 'ADMINISTRADOR')),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE habitacion (
    id        BIGSERIAL PRIMARY KEY,
    numero    VARCHAR(10) NOT NULL UNIQUE,
    capacidad SMALLINT    NOT NULL CHECK (capacidad > 0),
    activa    BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE residente (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT       NOT NULL UNIQUE REFERENCES usuario (id),
    codigo     VARCHAR(20)  NOT NULL UNIQUE,
    telefono   VARCHAR(20),
    activo     BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE asignacion_habitacion (
    id            BIGSERIAL PRIMARY KEY,
    residente_id  BIGINT      NOT NULL REFERENCES residente (id),
    habitacion_id BIGINT      NOT NULL REFERENCES habitacion (id),
    desde         TIMESTAMPTZ NOT NULL DEFAULT now(),
    hasta         TIMESTAMPTZ,
    CHECK (hasta IS NULL OR hasta >= desde)
);
-- Un residente solo puede tener una asignación vigente.
CREATE UNIQUE INDEX uq_asignacion_vigente ON asignacion_habitacion (residente_id) WHERE hasta IS NULL;
CREATE INDEX ix_asignacion_habitacion ON asignacion_habitacion (habitacion_id) WHERE hasta IS NULL;

CREATE TABLE apoderado (
    id       BIGSERIAL PRIMARY KEY,
    nombre   VARCHAR(150) NOT NULL,
    telefono VARCHAR(20),
    email    VARCHAR(150)
);

CREATE TABLE residente_apoderado (
    residente_id BIGINT      NOT NULL REFERENCES residente (id),
    apoderado_id BIGINT      NOT NULL REFERENCES apoderado (id),
    parentesco   VARCHAR(50),
    principal    BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (residente_id, apoderado_id)
);

-- Identificador opaco y revocable del QR. El contenido del QR se firma en el servidor (HMAC).
CREATE TABLE qr_residente (
    id           BIGSERIAL PRIMARY KEY,
    residente_id BIGINT      NOT NULL REFERENCES residente (id),
    codigo       UUID        NOT NULL UNIQUE,
    activo       BOOLEAN     NOT NULL DEFAULT TRUE,
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    revocado_en  TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_qr_activo ON qr_residente (residente_id) WHERE activo;

CREATE TABLE sesion_culto (
    id          BIGSERIAL PRIMARY KEY,
    fecha       DATE        NOT NULL,
    tipo        VARCHAR(12) NOT NULL CHECK (tipo IN ('MATUTINO', 'VESPERTINO')),
    inicio      TIMESTAMPTZ NOT NULL,
    corte       TIMESTAMPTZ NOT NULL,
    estado      VARCHAR(10) NOT NULL DEFAULT 'ABIERTA' CHECK (estado IN ('ABIERTA', 'CERRADA')),
    abierta_por BIGINT      NOT NULL REFERENCES usuario (id),
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    cerrada_en  TIMESTAMPTZ,
    UNIQUE (fecha, tipo)
);

CREATE TABLE asistencia_culto (
    id                 BIGSERIAL PRIMARY KEY,
    sesion_id          BIGINT      NOT NULL REFERENCES sesion_culto (id),
    residente_id       BIGINT      NOT NULL REFERENCES residente (id),
    registrado_en      TIMESTAMPTZ NOT NULL,
    registrado_por     BIGINT      NOT NULL REFERENCES usuario (id),
    despues_del_corte  BOOLEAN     NOT NULL,
    anulada            BOOLEAN     NOT NULL DEFAULT FALSE,
    anulada_por        BIGINT REFERENCES usuario (id),
    motivo_anulacion   VARCHAR(300),
    UNIQUE (sesion_id, residente_id)
);
CREATE INDEX ix_asistencia_residente ON asistencia_culto (residente_id);

CREATE TABLE evaluacion_limpieza (
    id           BIGSERIAL PRIMARY KEY,
    residente_id BIGINT      NOT NULL REFERENCES residente (id),
    fecha        DATE        NOT NULL,
    evaluador_id BIGINT      NOT NULL REFERENCES usuario (id),
    total        SMALLINT    NOT NULL CHECK (total BETWEEN 0 AND 20),
    observacion  VARCHAR(500),
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (residente_id, fecha)
);

CREATE TABLE detalle_evaluacion_limpieza (
    id            BIGSERIAL PRIMARY KEY,
    evaluacion_id BIGINT      NOT NULL REFERENCES evaluacion_limpieza (id) ON DELETE CASCADE,
    criterio      VARCHAR(20) NOT NULL CHECK (criterio IN ('ROPERO', 'CAMA', 'ESCRITORIO', 'BASURA', 'OLORES', 'PISO')),
    puntaje       SMALLINT    NOT NULL CHECK (puntaje >= 0),
    UNIQUE (evaluacion_id, criterio)
);

CREATE TABLE evidencia (
    id               BIGSERIAL PRIMARY KEY,
    nombre_original  VARCHAR(200) NOT NULL,
    content_type     VARCHAR(50)  NOT NULL,
    ruta             VARCHAR(300) NOT NULL,
    tamanio          BIGINT       NOT NULL,
    subida_por       BIGINT       NOT NULL REFERENCES usuario (id),
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE solicitud_permiso (
    id                  BIGSERIAL PRIMARY KEY,
    residente_id        BIGINT       NOT NULL REFERENCES residente (id),
    tipo                VARCHAR(10)  NOT NULL CHECK (tipo IN ('CORTO', 'LARGO', 'ESPECIAL')),
    motivo              VARCHAR(300) NOT NULL,
    destino             VARCHAR(200) NOT NULL,
    salida_solicitada   TIMESTAMPTZ  NOT NULL,
    retorno_solicitado  TIMESTAMPTZ  NOT NULL,
    retorno_autorizado  TIMESTAMPTZ  NOT NULL,
    estado              VARCHAR(25)  NOT NULL CHECK (estado IN
        ('BORRADOR', 'PENDIENTE_APODERADO', 'PENDIENTE_PRECEPTOR', 'APROBADO', 'RECHAZADO', 'CANCELADO', 'VENCIDO')),
    requiere_apoderado  BOOLEAN      NOT NULL,
    decidido_por        BIGINT REFERENCES usuario (id),
    decidido_en         TIMESTAMPTZ,
    comentario_decision VARCHAR(300),
    creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (retorno_solicitado > salida_solicitada)
);
CREATE INDEX ix_solicitud_residente ON solicitud_permiso (residente_id, creado_en DESC);
CREATE INDEX ix_solicitud_estado ON solicitud_permiso (estado);

CREATE TABLE aprobacion_apoderado (
    id           BIGSERIAL PRIMARY KEY,
    solicitud_id BIGINT      NOT NULL REFERENCES solicitud_permiso (id),
    apoderado_id BIGINT      NOT NULL REFERENCES apoderado (id),
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expira_en    TIMESTAMPTZ NOT NULL,
    usado_en     TIMESTAMPTZ,
    decision     VARCHAR(10) CHECK (decision IN ('APROBADO', 'RECHAZADO')),
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_aprobacion_solicitud ON aprobacion_apoderado (solicitud_id);

CREATE TABLE ampliacion_permiso (
    id               BIGSERIAL PRIMARY KEY,
    solicitud_id     BIGINT       NOT NULL REFERENCES solicitud_permiso (id),
    motivo           VARCHAR(300) NOT NULL,
    evidencia_id     BIGINT REFERENCES evidencia (id),
    retorno_original TIMESTAMPTZ  NOT NULL,
    retorno_nuevo    TIMESTAMPTZ  NOT NULL,
    estado           VARCHAR(10)  NOT NULL CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    revisada_por     BIGINT REFERENCES usuario (id),
    revisada_en      TIMESTAMPTZ,
    comentario       VARCHAR(300),
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (retorno_nuevo > retorno_original)
);
CREATE INDEX ix_ampliacion_solicitud ON ampliacion_permiso (solicitud_id);

CREATE TABLE justificacion_posterior (
    id           BIGSERIAL PRIMARY KEY,
    solicitud_id BIGINT       NOT NULL REFERENCES solicitud_permiso (id),
    motivo       VARCHAR(300) NOT NULL,
    evidencia_id BIGINT REFERENCES evidencia (id),
    estado       VARCHAR(10)  NOT NULL CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    revisada_por BIGINT REFERENCES usuario (id),
    revisada_en  TIMESTAMPTZ,
    comentario   VARCHAR(300),
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_justificacion_solicitud ON justificacion_posterior (solicitud_id);

CREATE TABLE movimiento_garita (
    id               BIGSERIAL PRIMARY KEY,
    residente_id     BIGINT      NOT NULL REFERENCES residente (id),
    solicitud_id     BIGINT REFERENCES solicitud_permiso (id),
    tipo             VARCHAR(10) NOT NULL CHECK (tipo IN ('SALIDA', 'RETORNO')),
    registrado_en    TIMESTAMPTZ NOT NULL,
    registrado_por   BIGINT      NOT NULL REFERENCES usuario (id),
    minutos_tarde    INTEGER,
    anulado          BOOLEAN     NOT NULL DEFAULT FALSE,
    anulado_por      BIGINT REFERENCES usuario (id),
    motivo_anulacion VARCHAR(300)
);
CREATE INDEX ix_movimiento_residente ON movimiento_garita (residente_id, registrado_en DESC);

CREATE TABLE registro_auditoria (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuario (id),
    accion     VARCHAR(60)  NOT NULL,
    entidad    VARCHAR(60)  NOT NULL,
    entidad_id BIGINT,
    detalle    VARCHAR(500),
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_auditoria_fecha ON registro_auditoria (creado_en DESC);

CREATE TABLE configuracion (
    clave       VARCHAR(80) PRIMARY KEY,
    valor       VARCHAR(200),
    descripcion VARCHAR(300) NOT NULL
);

-- Valores iniciales. Los marcados «pendiente» NO están confirmados institucionalmente.
INSERT INTO configuracion (clave, valor, descripcion) VALUES
    ('culto.matutino.inicio', '06:00', 'Hora de inicio del culto matutino (lunes a viernes)'),
    ('culto.matutino.corte', '06:05', 'Corte de asistencia matutina'),
    ('culto.vespertino.inicio', '20:00', 'Hora de inicio del culto vespertino (lunes a viernes)'),
    ('culto.vespertino.corte', '20:05', 'Corte de asistencia vespertina'),
    ('permiso.corto.max_horas', NULL, 'Pendiente de confirmar: duración máxima de permiso corto (vacío = sin límite)'),
    ('permiso.largo.max_horas', NULL, 'Pendiente de confirmar: duración máxima de permiso largo (vacío = sin límite)'),
    ('permiso.anticipacion_horas', NULL, 'Pendiente de confirmar: anticipación mínima para fines de semana/feriados/pernoctación (vacío = sin exigencia)'),
    ('permiso.apoderado.requerido', 'true', 'Pendiente de confirmar: si toda solicitud exige aprobación del apoderado'),
    ('permiso.apoderado.vigencia_horas', '48', 'Vigencia del enlace de aprobación del apoderado'),
    ('ampliacion.evidencia.obligatoria', 'true', 'Pendiente de confirmar: evidencia fotográfica obligatoria en ampliaciones'),
    ('justificacion.evidencia.obligatoria', 'false', 'Pendiente de confirmar: evidencia obligatoria en justificaciones posteriores');
