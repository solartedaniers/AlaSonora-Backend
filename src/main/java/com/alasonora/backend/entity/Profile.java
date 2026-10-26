package com.alasonora.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/** El id es el de auth.users, asignado y no generado; agrego la FK por SQL porque ddl-auto no maneja el esquema de Supabase. */
@Entity
@Table(name = "profiles")
@Getter
@Setter
public class Profile {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String displayName;

    // Único sin distinguir mayúsculas: lo garantiza el índice sobre lower(username) de la migración SQL.
    private String username;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ObserverRole role;

    private String institution;

    private String orcidId;

    private String stationName;

    // ColumnDefault evita que ddl-auto=update falle al agregar esta columna
    // NOT NULL sobre una tabla con filas existentes.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'USER'")
    private SystemRole systemRole = SystemRole.USER;

    // Copia local del baneo de Supabase Auth: solo la uso para pintar el panel, no para autorizar.
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean suspended;

    // Hasta cuándo vio la campana: las detecciones públicas de otros más nuevas son avisos sin leer.
    private Instant notificationsSeenAt;
}
