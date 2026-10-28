package com.booklane.booking;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMigrationIT {
    @Test
    void upgradesExistingAuditWithoutLosingReasonsOrConflictIdentity() throws Exception {
        var image = DockerImageName.parse("postgres:18.6-bookworm@sha256:bbf83594798e144bff365a9c191c5d779f181fb3135902064ab8ff11918020fc")
                .asCompatibleSubstituteFor("postgres");
        try (var postgres = new PostgreSQLContainer(image).withDatabaseName("booklane")
                .withUsername("test_owner").withPassword("test_owner_password")) {
            postgres.start();
            var initial = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas("booking").defaultSchema("booking").target("4").load();
            initial.migrate();
            try (var db = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = db.createStatement()) {
                sql.execute("""
                        INSERT INTO booking.command_results (actor_id,command_id,request_hash,command_type,resource_id,
                            outcome,reason_code,canonical_request,processed_at)
                        VALUES ('20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001',
                            repeat('a',64),'SetResourceStatus','10000000-0000-4000-8000-000000000001',
                            'SUCCEEDED','STATUS_CHANGED','{}',now())
                        """);
                sql.execute("""
                        INSERT INTO booking.audit_entries (id,actor_id,command_id,request_hash,action,resource_id,outcome,reason,occurred_at)
                        VALUES ('40000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001',
                            '30000000-0000-4000-8000-000000000001',repeat('a',64),'SetResourceStatus',
                            '10000000-0000-4000-8000-000000000001','SUCCEEDED','Mantenimiento',now()),
                           ('40000000-0000-4000-8000-000000000002','20000000-0000-4000-8000-000000000001',
                            '30000000-0000-4000-8000-000000000001',repeat('b',64),'IDEMPOTENCY_CONFLICT',
                            '10000000-0000-4000-8000-000000000002','REJECTED','IDEMPOTENCY_CONFLICT',now())
                        """);
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .schemas("booking").defaultSchema("booking").load().migrate();
                try (var rows = sql.executeQuery("SELECT action, reason_code, reason FROM booking.audit_entries ORDER BY id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("reason_code")).isEqualTo("STATUS_CHANGED");
                    assertThat(rows.getString("reason")).isEqualTo("Mantenimiento");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("action")).isEqualTo("IDEMPOTENCY_CONFLICT");
                    assertThat(rows.getString("reason_code")).isEqualTo("IDEMPOTENCY_CONFLICT");
                    assertThat(rows.getString("reason")).isEqualTo("IDEMPOTENCY_CONFLICT");
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }
}
