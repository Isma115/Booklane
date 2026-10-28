package com.booklane.booking.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "resources", schema = "booking")
public class ResourceEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 20)
    private String type;
    private Integer capacity;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private long version;

    protected ResourceEntity() {}
}
