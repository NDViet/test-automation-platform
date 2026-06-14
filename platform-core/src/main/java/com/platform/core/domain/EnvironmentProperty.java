package com.platform.core.domain;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/** A key/value property attached to an {@link Environment}. */
@Entity
@Table(name = "environment_properties")
public class EnvironmentProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "value", nullable = false, length = 500)
    private String value;

    protected EnvironmentProperty() {}

    public EnvironmentProperty(UUID environmentId, String name, String value) {
        this.environmentId = environmentId;
        this.name          = name;
        this.value         = value;
    }

    public UUID getId()            { return id; }
    public UUID getEnvironmentId() { return environmentId; }
    public String getName()        { return name; }
    public String getValue()       { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnvironmentProperty p)) return false;
        return Objects.equals(id, p.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
