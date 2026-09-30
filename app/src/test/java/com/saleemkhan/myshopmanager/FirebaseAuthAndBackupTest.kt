package com.saleemkhan.myshopmanager

import com.saleemkhan.myshopmanager.data.backup.*
import com.saleemkhan.myshopmanager.data.firebase.FirebaseUserSession
import com.saleemkhan.myshopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class FirebaseAuthAndBackupTest {

    private val serializer = BackupSerializer()
    private val validator = BackupValidator(serializer)
    private val restoreManager = RestoreManager()

    @Test
    fun testAnonymousSessionDefaultState() {
        val anonSession = FirebaseUserSession(
            uid = "anon_1234567890ab",
            isAnonymous = true,
            displayName = null,
            email = null
        )

        assertTrue(anonSession.isAnonymous)
        assertFalse(anonSession.isLoggedInGoogle)
        assertEquals("anon_1234567890ab", anonSession.uid)
        assertNull(anonSession.email)
    }

    @Test
    fun testGoogleCredentialLinkingPreservesUid() {
        val anonSession = FirebaseUserSession(
            uid = "user_uid_persistent_999",
            isAnonymous = true,
            displayName = null,
            email = null
        )

        // Link with Google
        val linkedSession = anonSession.copy(
            isAnonymous = false,
            displayName = "Saleem Khan",
            email = "saleem@example.com",
            isLinkedWithGoogle = true
        )

        // Verify UID is preserved and account is now verified Google account
        assertEquals("user_uid_persistent_999", linkedSession.uid)
        assertFalse(linkedSession.isAnonymous)
        assertTrue(linkedSession.isLoggedInGoogle)
        assertEquals("Saleem Khan", linkedSession.displayName)
        assertEquals("saleem@example.com", linkedSession.email)
    }

    @Test
    fun testBackupPayloadSerializationAndChecksumIntegrity() {
        val testState = AppState(
            profile = ShopProfile(
                shopName = "Bismillah Super Store",
                ownerName = "Saleem Khan",
                phone = "03001234567"
            ),
            inventory = listOf(
                InventoryItem(id = "item_1", name = "Atta 10kg", quantity = 20.0, purchasePrice = 1200.0, salePrice = 1350.0),
                InventoryItem(id = "item_2", name = "Sugar 1kg", quantity = 50.0, purchasePrice = 140.0, salePrice = 160.0)
            ),
            customers = listOf(
                CustomerParty(id = "cust_1", name = "Ahmad", totalCredit = 4500.0)
            ),
            suppliers = listOf(
                SupplierParty(id = "supp_1", name = "Lahore Flour Mills", totalPayable = 24000.0)
            ),
            transactions = listOf(
                Transaction(id = "tx_1", type = TransactionType.SALE, amount = 1350.0, date = "2026-09-29T10:00:00Z")
            )
        )

        val serialized = serializer.serialize(testState, pretty = false)
        assertNotNull(serialized)
        assertTrue(serialized.isNotBlank())

        val validation = validator.validate(serialized)
        assertTrue("Validation should succeed for valid backup", validation.isValid)
        assertNotNull(validation.payloadState)
        assertEquals("Bismillah Super Store", validation.payloadState?.profile?.shopName)
        assertEquals(2, validation.payloadState?.inventory?.size)
        assertEquals(1, validation.payloadState?.customers?.size)
        assertEquals(1, validation.payloadState?.transactions?.size)
    }

    @Test
    fun testCorruptBackupRejection() {
        val emptyValidation = validator.validate("")
        assertFalse("Empty JSON must be rejected", emptyValidation.isValid)

        val malformedJson = "{ \"header\": { \"app\": \"MyShopManager\" }, \"data\": "
        val malformedValidation = validator.validate(malformedJson)
        assertFalse("Malformed JSON must be rejected", malformedValidation.isValid)

        val emptyObjValidation = validator.validate("{}")
        assertFalse("Empty object with no records must be rejected", emptyObjValidation.isValid)
    }

    @Test
    fun testTamperedChecksumRejection() {
        val testState = AppState(
            profile = ShopProfile(shopName = "Authentic Shop"),
            customers = listOf(CustomerParty(id = "c_1", name = "Zaid", totalCredit = 500.0))
        )
        val validJson = serializer.serialize(testState, pretty = false)
        assertTrue(validator.validate(validJson).isValid)

        // Simulate tampering: change customer name or amount in payload while retaining original header checksum
        val tamperedJson = validJson.replace("500.0", "999999.0")
        val tamperedValidation = validator.validate(tamperedJson)
        assertFalse("Tampered backup with invalid SHA-256 checksum MUST be rejected", tamperedValidation.isValid)
        assertTrue(tamperedValidation.errorReason?.contains("Checksum", ignoreCase = true) == true)
    }

    @Test
    fun testGoogleConflictSessionSwitching() {
        // Current anonymous session on device (UID B)
        val anonSession = FirebaseUserSession(
            uid = "anon_device_user_b",
            isAnonymous = true,
            displayName = null,
            email = null
        )

        // When signing in with existing Google account (UID A)
        val existingGoogleSession = FirebaseUserSession(
            uid = "google_user_uid_a",
            isAnonymous = false,
            displayName = "Pre-existing Google Account",
            email = "existing@gmail.com",
            isLinkedWithGoogle = true
        )

        // Verify clean transition to existing account identity without destructive state loss
        assertNotEquals(anonSession.uid, existingGoogleSession.uid)
        assertFalse(existingGoogleSession.isAnonymous)
        assertTrue(existingGoogleSession.isLoggedInGoogle)
        assertEquals("google_user_uid_a", existingGoogleSession.uid)
    }

    @Test
    fun testMergeRestoreAccountingReconciliation() {
        val currentLocalState = AppState(
            profile = ShopProfile(shopName = "Local Shop"),
            customers = listOf(
                CustomerParty(id = "c_1", name = "Bilal", totalCredit = 1000.0)
            ),
            transactions = listOf(
                Transaction(id = "tx_local_1", type = TransactionType.SALE, amount = 1000.0, paymentMethod = PaymentMethod.CREDIT, partyName = "Bilal", date = "2026-09-01T10:00:00Z")
            )
        )

        val cloudIncomingState = AppState(
            profile = ShopProfile(shopName = "Local Shop"),
            customers = listOf(
                CustomerParty(id = "c_2", name = "Hamza", totalCredit = 2500.0)
            ),
            transactions = listOf(
                Transaction(id = "tx_cloud_1", type = TransactionType.SALE, amount = 2500.0, paymentMethod = PaymentMethod.CREDIT, partyName = "Hamza", date = "2026-09-02T10:00:00Z")
            )
        )

        val merged = restoreManager.mergeStates(currentLocalState, cloudIncomingState)
        assertEquals(2, merged.customers.size)
        assertEquals(2, merged.transactions.size)

        val bilal = merged.customers.first { it.id == "c_1" }
        val hamza = merged.customers.first { it.id == "c_2" }
        assertEquals(1000.0, bilal.totalCredit, 0.001)
        assertEquals(2500.0, hamza.totalCredit, 0.001)
    }
}
