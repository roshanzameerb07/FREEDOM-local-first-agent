package com.example.freedom

import com.example.freedom.data.local.entity.FarmerEntity
import com.example.freedom.domain.query.EntityResolver
import com.example.freedom.domain.query.NameNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EntityResolverTest {

    private lateinit var resolver: EntityResolver
    private lateinit var knownFarmers: List<FarmerEntity>

    @Before
    fun setUp() {
        resolver = EntityResolver()
        knownFarmers = listOf(
            FarmerEntity(
                farmerId = "farmer-1",
                farmerName = "Suresh Kumar",
                normalizedName = NameNormalizer.normalize("Suresh Kumar")
            ),
            FarmerEntity(
                farmerId = "farmer-2",
                farmerName = "Suresh Gowda",
                normalizedName = NameNormalizer.normalize("Suresh Gowda")
            ),
            FarmerEntity(
                farmerId = "farmer-3",
                farmerName = "Ramesh Naik",
                normalizedName = NameNormalizer.normalize("Ramesh Naik")
            ),
            FarmerEntity(
                farmerId = "farmer-4",
                farmerName = "Mahesh",
                normalizedName = NameNormalizer.normalize("Mahesh")
            )
        )
    }

    @Test
    fun `exact canonical name resolves to correct FarmerEntity`() {
        val res = resolver.resolve("Ramesh Naik", knownFarmers)
        assertTrue(res is EntityResolver.Resolution.Resolved)
        val resolved = res as EntityResolver.Resolution.Resolved
        assertEquals("farmer-3", resolved.farmer.farmerId)
        assertEquals("Ramesh Naik", resolved.farmer.farmerName)
    }

    @Test
    fun `normalized exact name resolves regardless of case or repeated whitespace`() {
        val res = resolver.resolve("  ramesh   naik  ", knownFarmers)
        assertTrue(res is EntityResolver.Resolution.Resolved)
        val resolved = res as EntityResolver.Resolution.Resolved
        assertEquals("Ramesh Naik", resolved.farmer.farmerName)
    }

    @Test
    fun `possessive suffix is normalized and resolved`() {
        val res = resolver.resolve("Ramesh Naik's", knownFarmers)
        assertTrue(res is EntityResolver.Resolution.Resolved)
        val resolved = res as EntityResolver.Resolution.Resolved
        assertEquals("Ramesh Naik", resolved.farmer.farmerName)
    }

    @Test
    fun `safe unique token match resolves when only one farmer has that token`() {
        // "Ramesh" is unique to "Ramesh Naik"
        val res = resolver.resolve("Ramesh", knownFarmers)
        assertTrue(res is EntityResolver.Resolution.Resolved)
        val resolved = res as EntityResolver.Resolution.Resolved
        assertEquals("Ramesh Naik", resolved.farmer.farmerName)

        // "Mahesh" is unique to "Mahesh"
        val resMahesh = resolver.resolve("Mahesh", knownFarmers)
        assertTrue(resMahesh is EntityResolver.Resolution.Resolved)
        assertEquals("Mahesh", (resMahesh as EntityResolver.Resolution.Resolved).farmer.farmerName)
    }

    @Test
    fun `ambiguous mention returns Ambiguous resolution when multiple farmers match token`() {
        // "Suresh" matches both "Suresh Kumar" and "Suresh Gowda"
        val res = resolver.resolve("Suresh", knownFarmers)
        assertTrue(res is EntityResolver.Resolution.Ambiguous)
        val ambiguous = res as EntityResolver.Resolution.Ambiguous
        assertEquals("Suresh", ambiguous.mention)
        assertEquals(2, ambiguous.candidates.size)
        val candidateNames = ambiguous.candidates.map { it.farmerName }.toSet()
        assertTrue(candidateNames.contains("Suresh Kumar"))
        assertTrue(candidateNames.contains("Suresh Gowda"))
    }

    @Test
    fun `longest valid match is preferred when both short and long names exist`() {
        val customFarmers = listOf(
            FarmerEntity(
                farmerId = "s-1",
                farmerName = "Suresh",
                normalizedName = NameNormalizer.normalize("Suresh")
            ),
            FarmerEntity(
                farmerId = "s-2",
                farmerName = "Suresh Kumar",
                normalizedName = NameNormalizer.normalize("Suresh Kumar")
            )
        )

        val res = resolver.resolve("Suresh Kumar", customFarmers)
        assertTrue(res is EntityResolver.Resolution.Resolved)
        assertEquals("Suresh Kumar", (res as EntityResolver.Resolution.Resolved).farmer.farmerName)
    }

    @Test
    fun `nonexistent farmer returns NotFound`() {
        val res = resolver.resolve("NonexistentFarmer", knownFarmers)
        assertTrue(res is EntityResolver.Resolution.NotFound)
        assertEquals("NonexistentFarmer", (res as EntityResolver.Resolution.NotFound).mention)
    }

    @Test
    fun `blank or empty mention returns NotFound`() {
        val emptyRes = resolver.resolve("", knownFarmers)
        assertTrue(emptyRes is EntityResolver.Resolution.NotFound)

        val blankRes = resolver.resolve("   ", knownFarmers)
        assertTrue(blankRes is EntityResolver.Resolution.NotFound)
    }
}
