package com.mirawuse.sportspot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {
    @Test
    fun manifestUsesBothBackupRules() {
        val application = readXml("AndroidManifest.xml").element("application")
        val android = "http://schemas.android.com/apk/res/android"

        assertEquals("true", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(android, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))
    }

    @Test
    fun legacyBackupExcludesSearchDatabase() {
        val rules = readXml("res/xml/backup_rules.xml")

        assertEquals("full-backup-content", rules.tagName)
        assertSearchDatabaseExcluded(rules)
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludeSearchDatabase() {
        val rules = readXml("res/xml/data_extraction_rules.xml")

        assertEquals("data-extraction-rules", rules.tagName)
        assertSearchDatabaseExcluded(rules.element("cloud-backup"))
        assertSearchDatabaseExcluded(rules.element("device-transfer"))
    }

    private fun readXml(path: String): Element =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main", path))
            .documentElement

    private fun Element.element(name: String): Element {
        val children = getElementsByTagName(name)
        assertEquals("Expected one <$name> in <$tagName>", 1, children.length)
        return children.item(0) as Element
    }

    private fun assertSearchDatabaseExcluded(section: Element) {
        val children = section.childNodes
        val excludedDatabases =
            (0 until children.length)
                .mapNotNull { children.item(it) as? Element }
                .filter { it.tagName == "exclude" && it.getAttribute("domain") == "database" }
                .map { it.getAttribute("path") }

        for (name in listOf("sportspot.db", "sportspot.db-journal", "sportspot.db-shm", "sportspot.db-wal")) {
            assertTrue("$name must be excluded from <${section.tagName}>", name in excludedDatabases)
        }
    }
}
