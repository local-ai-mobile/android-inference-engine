package io.github.localai.inference

import io.github.localai.inference.hardware.HardwareProfiler
import org.junit.Assert.*
import org.junit.Test

class SiliconAccelerationTest {

    @Test
    fun testArmKleidiAiWithDotProdAndI8mm() {
        val cpuInfo = """
            Processor	: AArch64 Processor rev 4 (aarch64)
            Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm jscvt fcma lrcpc dcpop sha3 sm3 sm4 asimddp sha512 sve asimdfhm dit uscat ilrcpc flagm ssbs sb paca pacg dcpodp sva i8mm
            CPU architecture: 8
            Hardware	: Qualcomm Technologies, Inc SM8650
        """.trimIndent()

        val capabilities = HardwareProfiler.detectSiliconAcceleration(cpuInfo)

        assertTrue("Should detect Arm64", capabilities.isArm64)
        assertTrue("Should detect NEON", capabilities.hasNeon)
        assertTrue("Should detect DotProd", capabilities.hasDotProd)
        assertTrue("Should detect I8MM", capabilities.hasI8mm)
        assertTrue("Should detect KleidiAI support", capabilities.hasKleidiAi)
        assertEquals("KleidiAI (NEON+I8MM)", capabilities.engineLabel)
        assertEquals("ARM64-v8.2A+ (KleidiAI)", capabilities.architectureLabel)
    }

    @Test
    fun testArmDotProdOnly() {
        val cpuInfo = """
            Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp asimddp
            CPU architecture: 8
        """.trimIndent()

        val capabilities = HardwareProfiler.detectSiliconAcceleration(cpuInfo)

        assertTrue(capabilities.hasDotProd)
        assertFalse(capabilities.hasI8mm)
        assertTrue(capabilities.hasKleidiAi)
        assertEquals("KleidiAI (Arm DotProd)", capabilities.engineLabel)
        assertEquals("ARM64-v8.2A+ (KleidiAI)", capabilities.architectureLabel)
    }

    @Test
    fun testArmI8mmOnly() {
        val cpuInfo = """
            Features	: fp asimd evtstrm crc32 i8mm
            CPU architecture: 8
        """.trimIndent()

        val capabilities = HardwareProfiler.detectSiliconAcceleration(cpuInfo)

        assertFalse(capabilities.hasDotProd)
        assertTrue(capabilities.hasI8mm)
        assertTrue(capabilities.hasKleidiAi)
        assertEquals("KleidiAI (Arm I8MM)", capabilities.engineLabel)
        assertEquals("ARM64-v8.2A+ (KleidiAI)", capabilities.architectureLabel)
    }

    @Test
    fun testArmNeonFallback() {
        val cpuInfo = """
            Features	: fp asimd evtstrm crc32
            CPU architecture: 8
        """.trimIndent()

        val capabilities = HardwareProfiler.detectSiliconAcceleration(cpuInfo)

        assertTrue(capabilities.hasNeon)
        assertFalse(capabilities.hasDotProd)
        assertFalse(capabilities.hasI8mm)
        assertTrue(capabilities.hasKleidiAi)
        assertEquals("Arm NEON", capabilities.engineLabel)
        assertEquals("ARM64-v8A (NEON)", capabilities.architectureLabel)
    }

    @Test
    fun testNonArmSimd() {
        val cpuInfo = """
            flags		: fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ht syscall nx mmxext fxsr_opt pdpe1gb rdtscp lm constant_tsc rep_good nopl nonstop_tsc cpuid extd_apicid tsc_known_freq pni pclmulqdq ssse3 fma cx16 sse4_1 sse4_2 movbe popcnt aes xsave avx f16c rdrand hypervisor lahf_lm cmp_legacy abm sse4a misalignsse 3dnowprefetch osvw topoext ssbd ibpb vmmcall fsgsbase bmi1 avx2 smep bmi2 erms invpcid rdseed adx smap clflushopt sha_ni
        """.trimIndent()

        val capabilities = HardwareProfiler.detectSiliconAcceleration(cpuInfo)

        assertFalse("Should not detect Arm64", capabilities.isArm64)
        assertFalse(capabilities.hasKleidiAi)
        assertEquals("x86_64 SIMD", capabilities.engineLabel)
    }
}
