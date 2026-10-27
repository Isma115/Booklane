package com.booklane.api;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test
    void apiCannotImportPersistenceServletOrBooking() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.booklane.api");
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "com.booklane.booking..", "java.sql..", "javax.sql..", "javax.persistence..",
                "jakarta.persistence..", "jakarta.servlet..",
                "org.hibernate..", "org.springframework.jdbc..", "org.springframework.orm..",
                "org.springframework.web.servlet..").check(classes);
    }
}
