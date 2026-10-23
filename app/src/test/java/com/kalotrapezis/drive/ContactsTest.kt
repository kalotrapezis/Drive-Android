package com.kalotrapezis.drive

import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contacts' files (Plan.md § Contacts): the three exports' forms read (Samsung's 2.1, Google's 3.0 and CSV, Apple's and
 * vCard 4), written as 3.0, an edit changing only its lines, imports without doubles, sync's copies shown as one.
 * The fixtures are made-up people in the shapes of the user's exports.
 */
class ContactsTest {
    private fun fixture(name: String) = File(javaClass.classLoader!!.getResource("contacts/$name")!!.toURI()).readBytes()
    private fun cards(name: String) = ContactsImport.read(fixture(name), name)
    private val t0 = Instant.parse("2026-10-23T18:00:00Z")

    @Test fun readsSamsungsVCard21() {
        val (eleni, plumber) = cards("samsung-21.vcf").map { VCard.info(it) }
        assertEquals("Ελένη Δοκιμαστή", eleni.name)
        assertEquals("Ελένη", eleni.given); assertEquals("Δοκιμαστή", eleni.family)
        assertEquals(listOf(VField("Mobile", "+306900000001"), VField("Home", "2310000001")), eleni.phones)
        assertEquals(listOf(VField("Home", "eleni@example.com")), eleni.emails)
        assertEquals("Οδός Παραδείγματος 12, 66100 Δράμα, Ελλάδα", eleni.addresses.single().text()) // quoted-printable broken over lines
        assertEquals(VDate(1985, 3, 14), eleni.birthday)
        assertEquals(512, java.util.Base64.getDecoder().decode(eleni.photo).size) // the inline photo, its folded lines joined
        assertEquals(listOf(VField("Work", "+30 25210 00000")), plumber.phones)
        // Samsung's own line kept, in 3.0's form (decoded, no CHARSET)
        val samsung = cards("samsung-21.vcf").first().single { it.name == "X-SAMSUNGADR" }
        assertEquals(";;Οδός Παραδείγματος 12;;;;", samsung.value)
        assertEquals("", samsung.params)
    }

    @Test fun readsGooglesVCard30() {
        val anna = VCard.info(cards("google-30.vcf").first())
        assertEquals("Άννα Κωνσταντίνου", anna.name)
        assertEquals("Αννούλα", anna.nickname)
        assertEquals(listOf(VField("Mobile", "+30 690 000 0002"), VField("Γιαγιά", "+30 2310 000002")), anna.phones) // a custom label
        assertEquals(VDate(1990, 5, 23), anna.birthday) // YYYYMMDD
        assertEquals(listOf(VField("Anniversary", "2015-06-12")), anna.dates)
        assertEquals(listOf(VField("Spouse", "Γιώργος")), anna.related)
        assertEquals("Σχολείο Δοκιμής", anna.org); assertEquals("Τμήμα Α", anna.department); assertEquals("Δασκάλα · Σχολείο Δοκιμής, Τμήμα Α", anna.job)
        assertEquals("Σημείωση, με κόμμα", anna.note)
        assertTrue(anna.favourite)
        assertEquals(listOf("Family"), anna.labels)
        assertEquals("https://lh3.googleusercontent.com/contacts/not-a-real-photo", anna.photoUrl)
        assertNull(anna.photo)
    }

    @Test fun readsApplesNoYearAndVCard4() {
        val (petros, dora) = cards("apple-40.vcf").map { VCard.info(it) }
        assertEquals(VDate(null, 10, 23), petros.birthday) // Apple's 1604 is no year
        assertEquals("Mobile", petros.phones.single().label)
        assertEquals(VDate(null, 10, 23), dora.birthday) // vCard 4's --MMDD
        assertEquals("not really a jpeg", String(java.util.Base64.getDecoder().decode(dora.photo))) // a data: link
        assertEquals("--10-23", dora.birthday!!.value())
        assertEquals(java.time.LocalDate.of(2027, 2, 28), VDate(null, 2, 29).inYear(2027))
    }

    @Test fun readsGooglesCsv() {
        val (sofia, bakery) = cards("google.csv").map { VCard.info(it) }
        assertEquals("Σοφία Υποθετική", sofia.name)
        assertEquals(listOf(VField("Mobile", "+30 690 000 0006"), VField("Mobile", "+30 690 000 0007"), VField("Work", "2521000006")), sofia.phones)
        assertEquals(VDate(null, 8, 29), sofia.birthday)
        assertEquals("Δύο\r\nγραμμές".replace("\r\n", "\n"), sofia.note.replace("\r\n", "\n"))
        assertEquals(listOf("Friends"), sofia.labels)
        assertEquals("Οδός 1, 65403 Καβάλα, Ελλάδα", sofia.addresses.single().text())
        assertEquals(listOf(VField("Anniversary", "2018-09-01")), sofia.dates)
        assertEquals("Tetra Bakery", bakery.name) // a company only
    }

    @Test fun anEditChangesOnlyItsLinesAndTheFileRoundTrips() {
        val dir = scratchDir("contacts")
        val imported = ContactsFiles.import(dir, cards("google-30.vcf"), t0)
        assertEquals(2, imported.added)
        val anna = ContactsFiles.all(dir).single { it.info.given == "Άννα" }
        val text = anna.file!!.readText()
        assertTrue(text.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(text.lines().all { it.toByteArray().size <= 75 })
        assertTrue(text.contains("X-UNKNOWN-THING;X-PARAM=1:κάτι που δεν ξέρει το Tetra\r\n"))
        assertTrue(anna.info.uid.isNotEmpty())
        // written again with no change: byte for byte the same
        assertEquals(text, VCard.write(ContactsFiles.read(anna.file).lines))
        // a phone changed: the TEL lines and their label only; every other line where it was
        val was = anna.info
        val now = was.copy(phones = listOf(VField("Mobile", "+30 690 999 9999")))
        ContactsFiles.save(dir, anna, now, t0.plusSeconds(60))
        val after = ContactsFiles.read(anna.file).lines
        val before = anna.lines
        assertEquals(before.filterNot { it.name.endsWith("TEL") || it.name == "item1.X-ABLabel" || it.name == "REV" }.map { it.toString() },
            after.filterNot { it.name.endsWith("TEL") || it.name == "REV" }.map { it.toString() })
        assertEquals(now.copy(rev = "2026-10-23T18:01:00Z"), ContactsFiles.read(anna.file).info)
        // the star and a label: CATEGORIES only
        val starred = ContactsFiles.save(dir, ContactsFiles.read(anna.file), now.favourite(false).copy(categories = now.favourite(false).categories + "School"), t0)
        assertFalse(starred.info.favourite)
        assertEquals(listOf("Family", "School"), starred.info.labels)
    }

    @Test fun aNewContactIsAPlainVCard30() {
        val dir = scratchDir("contacts")
        val info = ContactInfo(given = "Νέα", family = "Επαφή", phones = listOf(VField("Mobile", "6900000009"), VField("Κουμπάρος", "6900000010")),
            emails = listOf(VField("Work", "nea@example.com")), birthday = VDate(null, 1, 2), photo = "AAEC", person = "f3b1-person",
            addresses = listOf(VAddress("Home", listOf("", "", "Οδός 5", "Δράμα", "", "66100", ""))))
        val c = ContactsFiles.save(dir, null, info, t0)
        val text = c.file!!.readText()
        for (l in listOf("N:Επαφή;Νέα;;;", "FN:Νέα Επαφή", "TEL;TYPE=CELL:6900000009", "item1.TEL:6900000010", "item1.X-ABLabel:Κουμπάρος",
            "EMAIL;TYPE=INTERNET,WORK:nea@example.com", "BDAY:--01-02", "PHOTO;ENCODING=b;TYPE=JPEG:AAEC", "X-TETRA-PERSON:f3b1-person",
            "ADR;TYPE=HOME:;;Οδός 5;Δράμα;;66100;", "REV:2026-10-23T18:00:00Z")) assertTrue(l, text.contains(l + "\r\n"))
        assertTrue(c.file.name.matches(Regex("νέα-επαφή-[0-9a-f]{6}\\.vcf")))
        assertEquals(info.copy(uid = c.info.uid, formatted = "Νέα Επαφή", rev = "2026-10-23T18:00:00Z"), ContactsFiles.read(c.file).info)
    }

    @Test fun importingTwiceMakesNoDoubles() {
        val dir = scratchDir("contacts")
        assertEquals(2, ContactsFiles.import(dir, cards("samsung-21.vcf")).added)
        val again = ContactsFiles.import(dir, cards("samsung-21.vcf"))
        assertEquals(0, again.added); assertEquals(2, again.same)
        // the same person from Google's export, the phone written another way: still the same
        val eleni = VCard.build(ContactInfo(formatted = "Ελένη Δοκιμαστή", phones = listOf(VField("Mobile", "0030 690 000 0001"))))
        assertEquals(1, ContactsFiles.import(dir, listOf(eleni)).same)
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test fun takeoutsFoldersBecomeLabelsAndEachPersonComesOnce() {
        val zip = ByteArrayOutputStream().also { b -> ZipOutputStream(b).use { z ->
            z.putNextEntry(ZipEntry("Takeout/Contacts/All Contacts/All Contacts.vcf")); z.write(fixture("google-30.vcf"))
            z.putNextEntry(ZipEntry("Takeout/Contacts/School/School.vcf")); z.write(fixture("google-30.vcf"))
            z.putNextEntry(ZipEntry("Takeout/Contacts/Starred/Starred.vcf"))
            z.write("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Kostas Example\r\nN:Example;Kostas;;;\r\nTEL;TYPE=CELL:6900000003\r\nEND:VCARD\r\n".toByteArray())
        } }.toByteArray()
        val got = ContactsImport.read(zip).map { VCard.info(it) }
        assertEquals(2, got.size)
        assertEquals(listOf("Family", "School"), got.single { it.given == "Άννα" }.labels)
        assertTrue(got.single { it.given == "Kostas" }.favourite)
    }

    @Test fun syncsCopiesShowAsOneAndTheTrashKeepsTheFile() {
        val dir = File(scratchDir("tetra"), ContactsFiles.FOLDER)
        val c = ContactsFiles.save(dir, null, ContactInfo(given = "Copy", phones = listOf(VField("Mobile", "1"))), t0)
        val older = File(dir, c.file!!.nameWithoutExtension + " (2).vcf").also { c.file.copyTo(it) }
        ContactsFiles.save(dir, c, c.info.copy(note = "newer"), t0.plusSeconds(5))
        val shown = ContactsFiles.all(dir).single()
        assertEquals("newer", shown.info.note)
        assertEquals(2, shown.versions)
        assertEquals(listOf(older), ContactsFiles.copies(dir, shown))
        ContactsFiles.trash(dir, shown)
        assertTrue(ContactsFiles.all(dir).isEmpty())
        assertEquals(2, File(dir.parentFile, "Trash").listFiles()!!.size) // moved, never deleted
    }

    @Test fun searchAndLetters() {
        val i = ContactInfo(given = "Άννα", family = "Κωνσταντίνου", formatted = "Άννα Κωνσταντίνου", phones = listOf(VField("Mobile", "+30 690 000 0002")))
        assertTrue(ContactsFiles.matches(i, "αννα"))
        assertTrue(ContactsFiles.matches(i, "0000002"))
        assertFalse(ContactsFiles.matches(i, "Μαρία"))
        assertEquals("Α", ContactsFiles.letter(i, false))
        assertEquals("Κ", ContactsFiles.letter(i, true))
        assertNotNull(VDate.parse("1985-03-14T00:00:00Z"))
    }

    @Test fun theEditorsDates() {
        assertEquals(VDate(1985, 10, 23), dateFromText("23/10/1985"))
        assertEquals(VDate(null, 10, 23), dateFromText("23/10"))
        assertEquals(VDate(1985, 10, 23), dateFromText("23.10.85"))
        assertEquals(VDate(2010, 1, 2), dateFromText("2-1-10"))
        assertNull(dateFromText("32/1"))
        assertNull(dateFromText("tomorrow"))
        assertEquals("23/10", dateToText(VDate(null, 10, 23)))
        assertEquals("14 Mar · turns 42", dateText(VDate(1985, 3, 14), true, java.time.LocalDate.of(2026, 10, 23)))
        assertEquals("23 Oct · 41 today", dateText(VDate(1985, 10, 23), true, java.time.LocalDate.of(2026, 10, 23)))
    }

    @Test fun photosPeopleMatchContactsByName() {
        val people = listOf("Ελένη Δοκιμαστή", "Άννα", "Γιώργος Α", "Γιώργος Β")
        val contacts = listOf(ContactInfo(formatted = "Ελένη Δοκιμαστή"), ContactInfo(formatted = "Αννα Κωνσταντίνου"), ContactInfo(formatted = "Γιώργος Τρίτος"),
            ContactInfo(formatted = "Has Photo", photo = "AAEC"))
        // the same name; the same first name when only one person and one contact have it; two Γιώργος: no one
        assertEquals(listOf("Ελένη Δοκιμαστή" to "Ελένη Δοκιμαστή", "Αννα Κωνσταντίνου" to "Άννα"),
            ContactsFaces.matches(contacts, people) { it }.map { it.first.name to it.second })
        assertEquals(2, ContactsFaces.score("Μαρία Π.", "μαρια π."))
    }
}
