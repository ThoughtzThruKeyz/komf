// CI-only Gradle init script.
//
// komf-client and komf-api-models call signAllPublications() and configure
// signing { useGpgCmd() }, which is correct for publishing to Maven Central but
// fails in CI, where no GPG key exists. We only publish these to the local
// Maven repository so the Komelia build can consume the patched client, and a
// local artifact does not need a signature.
//
// Marking signing as not required makes Gradle skip the Sign tasks when no
// signatory is configured, rather than failing the build. Disabling the tasks
// outright would instead break publication, because signAllPublications()
// registers the signature files as publication artifacts that would then never
// be produced.
allprojects {
    plugins.withType<org.gradle.plugins.signing.SigningPlugin> {
        extensions.configure<org.gradle.plugins.signing.SigningExtension> {
            setRequired({ false })
        }
    }
}
