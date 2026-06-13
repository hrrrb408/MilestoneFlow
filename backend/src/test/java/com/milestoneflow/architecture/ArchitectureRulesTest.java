package com.milestoneflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArchUnit tests enforcing the architecture boundaries defined in ADR-BE-001.
 *
 * <p>These rules verify that the modular monolith package structure is respected,
 * preventing architectural drift at compile time. Rules run as part of the
 * Surefire unit test suite.
 *
 * <p>Rules targeting packages or annotations that do not yet exist
 * (e.g., domain layers, RestControllers) use {@code allowEmptyShould(true)}
 * so they act as future gatekeepers without failing on the current codebase.
 * Once the relevant classes are introduced, the rules will automatically
 * start enforcing boundaries.
 *
 * <p>Rule summary:
 * <ol>
 *   <li>ARCH-001: shared must not depend on business modules</li>
 *   <li>ARCH-002: domain must not depend on api layer</li>
 *   <li>ARCH-003: domain must not depend on Spring Web</li>
 *   <li>ARCH-004: api must not directly access persistence/repository</li>
 *   <li>ARCH-005: modules must not access other modules' infrastructure</li>
 *   <li>ARCH-006: controllers must not return JPA entities</li>
 *   <li>ARCH-007: repositories must not reside in api layer</li>
 *   <li>ARCH-008: @Configuration classes must have Config/Configuration suffix</li>
 *   <li>ARCH-009: @RestController classes must have Controller suffix</li>
 *   <li>ARCH-010: Spring Data Repository interfaces must be package-private</li>
 * </ol>
 *
 * @see <a href="backend/docs/architecture/ARCHITECTURE_TESTING.md">ARCHITECTURE_TESTING.md</a>
 */
class ArchitectureRulesTest {

    private static final String BASE_PKG = "com.milestoneflow";

    /**
     * Business modules that shared must not depend on.
     * Matches the module list from ADR-BE-001 and architecture doc §02.
     */
    private static final String[] BUSINESS_MODULES = {
            "identity", "workspace", "client", "project", "milestone", "task",
            "progress",
            "quotation", "baseline", "delivery", "changeorder", "receivable",
            "publicaccess", "fileasset", "notification", "audit",
            "activity",
            "actioncenter", "pilotfeedback", "scheduler"
    };

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages(BASE_PKG);
    }

    // ── ARCH-001: shared must not depend on business modules ──────────────

    @Test
    @DisplayName("ARCH-001: shared must not depend on business modules")
    void sharedMustNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..shared..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(businessModulePackages());

        rule.check(productionClasses);
    }

    // ── ARCH-002: domain must not depend on api layer ─────────────────────

    @Test
    @DisplayName("ARCH-002: domain must not depend on api layer")
    void domainMustNotDependOnApiLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..api..")
                .allowEmptyShould(true);

        rule.check(productionClasses);
    }

    // ── ARCH-003: domain must not depend on Spring Web ────────────────────

    @Test
    @DisplayName("ARCH-003: domain must not depend on Spring Web or Servlet API")
    void domainMustNotDependOnSpringWeb() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.web..",
                        "jakarta.servlet.."
                )
                .allowEmptyShould(true);

        rule.check(productionClasses);
    }

    // ── ARCH-004: api must not directly access persistence/repository ─────

    @Test
    @DisplayName("ARCH-004: api layer must not directly access persistence or repository")
    void apiMustNotDirectlyAccessPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..repository..", "..persistence..")
                .allowEmptyShould(true);

        rule.check(productionClasses);
    }

    // ── ARCH-005: modules must not access other modules' infrastructure ───

    @Test
    @DisplayName("ARCH-005: modules must not access other modules' infrastructure")
    void modulesMustNotAccessOtherModulesInfrastructure() {
        // Check each module against only OTHER modules' infrastructure packages.
        // A module's own infrastructure classes may depend on each other — that
        // is the expected adapter pattern. Cross-module infrastructure coupling
        // is what this rule prevents.
        for (int i = 0; i < BUSINESS_MODULES.length; i++) {
            String[] otherInfraPackages = otherModuleInfrastructurePackagesExcluding(i);

            ArchRule rule = noClasses()
                    .that().resideInAPackage(".." + BUSINESS_MODULES[i] + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(otherInfraPackages)
                    .allowEmptyShould(true);

            rule.check(productionClasses);
        }
    }

    // ── ARCH-006: controllers must not return JPA entities ────────────────

    @Test
    @DisplayName("ARCH-006: controllers must not return JPA entities")
    void controllersMustNotReturnJpaEntities() {
        // Uses annotation check as proxy: @RestController classes must not
        // depend on domain/entity packages. This catches returning entities
        // in method signatures or using them in request/response DTOs.
        ArchRule rule = noClasses()
                .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..domain..")
                .allowEmptyShould(true);

        rule.check(productionClasses);
    }

    // ── ARCH-007: repositories must not reside in api layer ───────────────

    @Test
    @DisplayName("ARCH-007: repository types must not reside in api layer")
    void repositoriesMustNotResideInApiLayer() {
        // Repository interfaces and implementations must be outside the api layer.
        // Uses classes() (not noClasses()) so that the rule asserts repositories
        // should reside outside of the api package.
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Repository")
                .should().resideOutsideOfPackage("..api..")
                .allowEmptyShould(true);

        rule.check(productionClasses);
    }

    // ── ARCH-008: @Configuration class naming ─────────────────────────────

    @Test
    @DisplayName("ARCH-008: @Configuration classes must end with Configuration or Config")
    void configurationClassNaming() {
        ArchRule rule = classes()
                .that().areAnnotatedWith("org.springframework.context.annotation.Configuration")
                .should().haveSimpleNameEndingWith("Configuration")
                .orShould().haveSimpleNameEndingWith("Config");

        rule.check(productionClasses);
    }

    // ── ARCH-009: @RestController class naming ────────────────────────────

    @Test
    @DisplayName("ARCH-009: @RestController classes must end with Controller")
    void restControllerNaming() {
        ArchRule rule = classes()
                .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().haveSimpleNameEndingWith("Controller")
                .allowEmptyShould(true);

        rule.check(productionClasses);
    }

    // ── ARCH-010: Spring Data repositories must be package-private ──────────

    @Test
    @DisplayName("ARCH-010: Spring Data Repository interfaces must be package-private")
    void springDataRepositoriesMustBePackagePrivate() {
        ArchRule rule = classes()
                .that().implement("org.springframework.data.jpa.repository.JpaRepository")
                .should().notBePublic()
                .allowEmptyShould(true);

        rule.check(productionClasses);
    }

    // ── Verification: production classes are loaded ───────────────────────

    @Test
    @DisplayName("Production classes are loaded for architecture scanning")
    void productionClassesAreLoaded() {
        assertThat(productionClasses).isNotEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String[] businessModulePackages() {
        String[] packages = new String[BUSINESS_MODULES.length];
        for (int i = 0; i < BUSINESS_MODULES.length; i++) {
            packages[i] = ".." + BUSINESS_MODULES[i] + "..";
        }
        return packages;
    }

    /**
     * Returns infrastructure packages for all modules — used to verify
     * no module accesses another module's infrastructure.
     */
    private static String[] otherModuleInfrastructurePackages() {
        String[] packages = new String[BUSINESS_MODULES.length];
        for (int i = 0; i < BUSINESS_MODULES.length; i++) {
            packages[i] = ".." + BUSINESS_MODULES[i] + ".infrastructure..";
        }
        return packages;
    }

    /**
     * Returns infrastructure packages for all modules EXCEPT the one at the
     * given index — used by ARCH-005 to check cross-module dependencies only.
     */
    private static String[] otherModuleInfrastructurePackagesExcluding(int excludeIndex) {
        String[] packages = new String[BUSINESS_MODULES.length - 1];
        int idx = 0;
        for (int i = 0; i < BUSINESS_MODULES.length; i++) {
            if (i != excludeIndex) {
                packages[idx++] = ".." + BUSINESS_MODULES[i] + ".infrastructure..";
            }
        }
        return packages;
    }
}
