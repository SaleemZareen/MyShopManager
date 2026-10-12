package com.saleemkhan.myshopmanager

import com.saleemkhan.myshopmanager.model.AppState
import com.saleemkhan.myshopmanager.model.TaxRecord
import com.saleemkhan.myshopmanager.utils.TaxEngine
import org.junit.Assert.*
import org.junit.Test

class FbrTaxEngineTest {

    @Test
    fun testFbrProgressiveTaxSlabBoundaries() {
        // Slab 1: Up to Rs. 600,000 -> 0% tax
        assertEquals(0.0, TaxEngine.calculateFbrNonSalariedTax(0.0), 0.001)
        assertEquals(0.0, TaxEngine.calculateFbrNonSalariedTax(600000.0), 0.001)

        // Slab 2: Rs. 600,001 to Rs. 1,200,000 -> 15% of amount exceeding 600k
        // Income = 1,000,000 -> Tax = 400,000 * 15% = 60,000.0
        assertEquals(60000.0, TaxEngine.calculateFbrNonSalariedTax(1000000.0), 0.001)
        // Income = 1,200,000 -> Tax = 600,000 * 15% = 90,000.0
        assertEquals(90000.0, TaxEngine.calculateFbrNonSalariedTax(1200000.0), 0.001)

        // Slab 3: Rs. 1,200,001 to Rs. 1,600,000 -> Rs. 90,000 + 20% of amount exceeding 1.2M
        // Income = 1,500,000 -> Tax = 90,000 + (300,000 * 20%) = 150,000.0
        assertEquals(150000.0, TaxEngine.calculateFbrNonSalariedTax(1500000.0), 0.001)
        // Income = 1,600,000 -> Tax = 90,000 + (400,000 * 20%) = 170,000.0
        assertEquals(170000.0, TaxEngine.calculateFbrNonSalariedTax(1600000.0), 0.001)

        // Slab 4: Rs. 1,600,001 to Rs. 3,200,000 -> Rs. 170,000 + 30% of amount exceeding 1.6M
        // Income = 2,000,000 -> Tax = 170,000 + (400,000 * 30%) = 290,000.0
        assertEquals(290000.0, TaxEngine.calculateFbrNonSalariedTax(2000000.0), 0.001)

        // Slab 5: Rs. 3,200,001 to Rs. 5,600,000 -> Rs. 650,000 + 40% of amount exceeding 3.2M
        // Income = 4,000,000 -> Tax = 650,000 + (800,000 * 40%) = 970,000.0
        assertEquals(970000.0, TaxEngine.calculateFbrNonSalariedTax(4000000.0), 0.001)

        // Slab 6: Exceeding Rs. 5,600,000 -> Rs. 1,610,000 + 45% of amount exceeding 5.6M
        // Income = 6,000,000 -> Tax = 1,610,000 + (400,000 * 45%) = 1,790,000.0
        assertEquals(1790000.0, TaxEngine.calculateFbrNonSalariedTax(6000000.0), 0.001)
    }

    @Test
    fun testWithholdingTaxAdjustment() {
        val state = AppState(
            taxRecord = TaxRecord(
                utilityWithholding = 5000.0,
                mobileWithholding = 3000.0,
                bankWithholding = 2000.0,
                advanceTax = 10000.0
            )
        )

        val summary = TaxEngine.calculateFbrSummary(state)
        val whtData = TaxEngine.getWithholdingTaxData(summary, state)

        // Total adjustable tax = 5000 + 3000 + 2000 + 10000 = 20,000.0
        assertEquals(20000.0, whtData.totalAdjustableTax, 0.001)
    }

    @Test
    fun testIrisMappingCodesExhaustiveCoverage() {
        val state = AppState()
        val summary = TaxEngine.calculateFbrSummary(state)
        val mappings = TaxEngine.getIrisMappings(summary, state)

        // Verify key FBR Iris Section codes exist
        val codes = mappings.map { it.code }
        assertTrue(codes.contains("Code 7000")) // Gross Turnover / Total Sales
        assertTrue(codes.contains("Code 7011")) // COGS
        assertTrue(codes.contains("Code 7020")) // Gross Profit
        assertTrue(codes.contains("Code 7040")) // Total Operating Deductions
        assertTrue(codes.contains("Code 7050")) // Net Taxable Business Profit
    }
}
