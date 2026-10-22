package tw.kuies.link.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkVerifierTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun verifier(
        archive: ApkMetadata?,
        installed: ApkMetadata?,
    ): ApkVerifier = ApkVerifier(object : ApkMetadataSource {
        override fun readArchive(apk: File): ApkMetadata? = archive
        override fun readInstalled(packageName: String): ApkMetadata? = installed
    })

    private val installed = ApkMetadata("tw.kuies.link", 9, setOf("signer-a"))

    @Test
    fun matchingArchivePasses() {
        val apk = temporaryFolder.newFile("update.apk")
        val failure = verifier(
            ApkMetadata("tw.kuies.link", 11, setOf("signer-a")),
            installed,
        ).verify(apk, "tw.kuies.link", expectedVersionCode = 11, currentVersionCode = 9)
        assertNull(failure)
    }

    @Test
    fun packageNameMismatchIsRejected() {
        val apk = temporaryFolder.newFile("update.apk")
        val failure = verifier(
            ApkMetadata("com.evil.app", 11, setOf("signer-a")),
            installed,
        ).verify(apk, "tw.kuies.link", expectedVersionCode = 11, currentVersionCode = 9)
        assertEquals(UpdateFailure.PACKAGE_MISMATCH, failure)
    }

    @Test
    fun archiveVersionMustMatchManifest() {
        val apk = temporaryFolder.newFile("update.apk")
        val failure = verifier(
            ApkMetadata("tw.kuies.link", 12, setOf("signer-a")),
            installed,
        ).verify(apk, "tw.kuies.link", expectedVersionCode = 11, currentVersionCode = 9)
        assertEquals(UpdateFailure.VERSION_MISMATCH, failure)
    }

    @Test
    fun downgradeIsRejected() {
        val apk = temporaryFolder.newFile("update.apk")
        val failure = verifier(
            ApkMetadata("tw.kuies.link", 9, setOf("signer-a")),
            installed,
        ).verify(apk, "tw.kuies.link", expectedVersionCode = 9, currentVersionCode = 9)
        assertEquals(UpdateFailure.VERSION_MISMATCH, failure)
    }

    @Test
    fun differentSignerIsRejected() {
        val apk = temporaryFolder.newFile("update.apk")
        val failure = verifier(
            ApkMetadata("tw.kuies.link", 11, setOf("signer-b")),
            installed,
        ).verify(apk, "tw.kuies.link", expectedVersionCode = 11, currentVersionCode = 9)
        assertEquals(UpdateFailure.SIGNATURE_MISMATCH, failure)
    }

    @Test
    fun unsignedArchiveIsRejected() {
        val apk = temporaryFolder.newFile("update.apk")
        val failure = verifier(
            ApkMetadata("tw.kuies.link", 11, emptySet()),
            installed,
        ).verify(apk, "tw.kuies.link", expectedVersionCode = 11, currentVersionCode = 9)
        assertEquals(UpdateFailure.SIGNATURE_MISMATCH, failure)
    }

    @Test
    fun unreadableArchiveIsRejected() {
        val apk = temporaryFolder.newFile("update.apk")
        val failure = verifier(null, installed)
            .verify(apk, "tw.kuies.link", expectedVersionCode = 11, currentVersionCode = 9)
        assertEquals(UpdateFailure.PACKAGE_MISMATCH, failure)
    }
}
