package com.gladed.androidgitversion

class VariantTest extends AndroidGitVersionTest {
    enum AbiType { ONE, TWO, THREE }

    class SimOutput {
        int versionCodeOverride = 0
        AbiType abi
        AbiType getFilter(f) {
            if (f == "ABI") return abi
            return null
        }
    }

    class SimVariants {
        def variants = [ new SimVariant() ]

        void all(Closure c) {
            variants.each {
                c it
            }
        }
    }

    class SimVariant {
        def outputs = [ new SimOutput() ]
    }

    void testVariants() {
        plugin.codeFormat = "AMNNPP"
        addCommit()
        addTag("1.2.3")

        // Configure our own abi map
        plugin.abis = [ (AbiType.ONE) : 1, (AbiType.TWO) : 2, (AbiType.THREE) : 3 ]

        // Simulate some variants each with their own set of abi outputs
        def simVariants = new SimVariants(variants : [
                new SimVariant(outputs : [new SimOutput()]),
                new SimVariant(outputs : [new SimOutput(abi: AbiType.TWO),
                                          new SimOutput(abi: AbiType.THREE)] )
        ])

        plugin.variants(simVariants)
        assertEquals(10203, plugin.code())
        assertEquals(10203, simVariants.variants[0].outputs[0].versionCodeOverride)
        assertEquals(210203, simVariants.variants[1].outputs[0].versionCodeOverride)
        assertEquals(310203, simVariants.variants[1].outputs[1].versionCodeOverride)
    }
}
