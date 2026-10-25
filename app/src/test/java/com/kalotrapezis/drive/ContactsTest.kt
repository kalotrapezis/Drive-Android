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
        assertEquals("14 Mar · turns 42", dateText(VDate(1985, 3, 14), true, java.time.LocalDate.of(2026, 10, 23), locale = java.util.Locale.ENGLISH) { a, now -> if (now) "$a today" else "turns $a" })
        assertEquals("23 Oct · 41 today", dateText(VDate(1985, 10, 23), true, java.time.LocalDate.of(2026, 10, 23), locale = java.util.Locale.ENGLISH) { a, now -> if (now) "$a today" else "turns $a" })
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

    @Test fun otherAppsRowsForAContactsNumbers() {
        val rows = listOf(
            AppRow(1, "vnd.android.cursor.item/vnd.com.whatsapp.profile", "306970000002@s.whatsapp.net", "WhatsApp"),
            AppRow(2, "vnd.android.cursor.item/vnd.com.whatsapp.video.call", "306970000002@s.whatsapp.net", "WhatsApp"),
            AppRow(3, "vnd.android.cursor.item/vnd.com.viber.voip.viber_number_call", "+306970000002", "Viber"),
            AppRow(4, "vnd.android.cursor.item/vnd.com.viber.voip.viber_number_call", "+306970000002", "Viber"), // Google's copy of Viber's row
            AppRow(5, "vnd.android.cursor.item/vnd.com.viber.voip.viber_out_call_viber", "+306970000002", "Viber"), // Viber Out: paid, not offered
            AppRow(6, "vnd.android.cursor.item/vnd.com.whatsapp.profile", "306999999999@s.whatsapp.net", "WhatsApp"), // someone else
        )
        assertEquals(listOf(Triple("WhatsApp", Way.Message, 1L), Triple("WhatsApp", Way.Video, 2L), Triple("Viber", Way.Call, 3L)),
            ContactsWays.ways(rows, listOf("+30 697 000 0002")).map { Triple(it.app, it.way, it.id) })
    }

    @Test fun mergeKeepsEveryValueAndNotTheSameIsRemembered() {
        val dir = File(scratchDir("tetra"), ContactsFiles.FOLDER)
        val a = ContactsFiles.save(dir, null, ContactInfo(given = "Μαρία", family = "Δοκιμή", formatted = "Μαρία Δοκιμή", phones = listOf(VField("Mobile", "+30 690 000 0011")), categories = listOf("Family")))
        val b = ContactsFiles.save(dir, null, ContactInfo(formatted = "Μαρία Δοκιμή", phones = listOf(VField("Mobile", "6900000011"), VField("Work", "2521000011")),
            emails = listOf(VField("Home", "maria@example.com")), birthday = VDate(1980, 2, 3), categories = listOf("School")))
        val c = ContactsFiles.save(dir, null, ContactInfo(formatted = "Άλλος", emails = listOf(VField("Home", "MARIA@example.com"))))
        ContactsFiles.save(dir, null, ContactInfo(formatted = "Κανείς"))
        val pairs = ContactsMerge.pairs(ContactsFiles.all(dir))
        assertEquals(setOf(setOf(a.info.uid, b.info.uid), setOf(b.info.uid, c.info.uid)), pairs.map { setOf(it.first.info.uid, it.second.info.uid) }.toSet())
        // not the same: in both files, and not offered again
        val (x, y) = pairs.single { it.first.info.uid == c.info.uid || it.second.info.uid == c.info.uid }
        ContactsMerge.notTheSame(x, y)
        assertTrue(c.file!!.readText().contains("X-TETRA-NOT-DUPLICATE:${b.info.uid}"))
        val left = ContactsMerge.pairs(ContactsFiles.all(dir)).single()
        val kept = ContactsMerge.merge(dir, left.first, left.second)
        val m = kept.info
        assertEquals(setOf("6900000011", "2521000011"), m.phones.map { it.value }.toSet()) // the fuller one kept: its form of the mobile
        assertEquals(2, m.phones.size) // the same mobile written two ways is one
        assertEquals(listOf("maria@example.com"), m.emails.map { it.value })
        assertEquals(VDate(1980, 2, 3), m.birthday)
        assertEquals(setOf("Family", "School"), m.labels.toSet())
        assertEquals(3, ContactsFiles.all(dir).size)
        assertTrue(File(dir.parentFile, "Trash").listFiles()!!.isNotEmpty())
        // export: one file of cards, a QR's without the photo
        val text = ContactsMerge.export(ContactsFiles.all(dir))
        assertEquals(3, VCard.cards(text).size)
    }

    @Test fun contactsPartOfSync() {
        assertTrue(DriveScope.contains("contacts", "Contacts/maria-3f9a2c.vcf"))
        assertTrue(DriveScope.contains("contacts", "Trash/maria-3f9a2c.vcf")) // a deleted contact follows into the Trash
        assertFalse(DriveScope.contains("contacts", "Trash/event.ics"))
        assertFalse(DriveScope.contains("contacts", "Documents/maria.vcf"))
        assertFalse(DriveScope.contains("calendar", "Contacts/maria-3f9a2c.vcf"))
    }

    /** The computer's contacts.js wrote desktop-written.vcf from the same contact: the phone writes the very same bytes. */
    @Test fun theDesktopWritesWhatThePhoneWrites() {
        val desktop = String(fixture("desktop-written.vcf"))
        val i = ContactInfo(uid = "desktop-1", given = "Νέα", family = "Επαφή", phones = listOf(VField("Mobile", "6900000009"), VField("Κουμπάρος", "6900000010")),
            emails = listOf(VField("Work", "nea@example.com")), birthday = VDate(null, 1, 2), addresses = listOf(VAddress("Home", listOf("", "", "Οδός 5", "Δράμα", "", "66100", ""))),
            dates = listOf(VField("Anniversary", "2015-06-12")), related = listOf(VField("Spouse", "Γιώργος")), categories = listOf("starred", "Family"), note = "Δύο\nγραμμές, και κόμμα")
        assertEquals(desktop, VCard.write(VCard.build(i, Instant.parse("2026-10-23T18:00:00Z"))))
        assertEquals(desktop, VCard.write(VCard.cards(desktop).single()))
    }

    @Test fun aPortraitsFaceIsKeptInTheFile() {
        val dir = scratchDir("contacts")
        val face = "${"a".repeat(64)};0.2,0.1,0.6,0.6"
        val c = ContactsFiles.save(dir, null, ContactInfo(given = "Νέα", person = "p-new", face = face))
        assertTrue(c.file!!.readText().replace("\r\n ", "").contains("X-TETRA-FACE:$face\r\n")) // folded at 75, as every long line
        assertEquals(face, ContactsFiles.read(c.file).info.face)
        assertEquals(face, ContactsFaces.faceValue("a".repeat(64), listOf(0.2f, 0.1f, 0.6f, 0.6f)))
    }

    /** A default number (TYPE=PREF): the computer's file read and written again by the phone, the same bytes. */
    @Test fun theDefaultNumberIsTypePref() {
        val desktop = String(fixture("desktop-default.vcf"))
        val i = VCard.info(VCard.cards(desktop).single())
        assertEquals("2310000021", i.defaultPhone)
        assertEquals(listOf("Mobile", "Home", "Κουμπάρος"), i.phones.map { it.label }) // PREF is not a label
        assertEquals(desktop, VCard.write(VCard.build(i.copy(rev = ""), Instant.parse("2026-10-24T09:00:00Z"))))
        // starring another: only the TEL lines change
        val lines = VCard.apply(VCard.cards(desktop).single(), i, i.copy(defaultPhone = "6900000022"), Instant.parse("2026-10-24T09:00:00Z"))
        assertEquals("6900000022", VCard.info(lines).defaultPhone)
        assertTrue(Regex("item\\d+\\.TEL;TYPE=PREF:6900000022\r\n").containsMatchIn(VCard.write(lines))) // a custom label keeps its group form
    }

    @Test fun aContactsRecordingsByNameOrNumber() {
        val i = ContactInfo(given = "Άννα", family = "Πέτρου", phones = listOf(VField("Mobile", "+30 690 000 0011")))
        val files = listOf("2026-10-24 09.15 Άννα Πέτρου.ogg", "2026-10-25 12.04 6900000011.ogg", "2026-10-25 13.00 Άννα Πετρίδου.ogg",
            "2026-10-25 14.00 6900000099.ogg", "2026-10-25 15.00 Άννα Πέτρου.txt").map { File("/r", it) }
        assertEquals(listOf("2026-10-25 12.04 6900000011.ogg", "2026-10-24 09.15 Άννα Πέτρου.ogg"), ContactsRecordings.of(files, i).map { it.name })
    }
}
