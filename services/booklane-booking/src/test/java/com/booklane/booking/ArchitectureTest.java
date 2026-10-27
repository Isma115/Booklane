package com.booklane.booking;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test
    void bookingCannotImportApi() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.booklane.booking");
        noClasses().should().dependOnClassesThat().resideInAPackage("com.booklane.api..").check(classes);
        // Domain/application packages do not exist until their backlog tasks; empty matches are intentional here.
        noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.apache.kafka..",
                        "com.booklane.contracts..", "..adapters..")
                .allowEmptyShould(true).check(classes);
        noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat()
                .resideInAPackage("..adapters..").allowEmptyShould(true).check(classes);
        noClasses().that().resideInAnyPackage("..adapters.http..", "..query..")
                .should().dependOnClassesThat().resideInAPackage("..persistence.entity..")
                .allowEmptyShould(true).check(classes);
    }
}
