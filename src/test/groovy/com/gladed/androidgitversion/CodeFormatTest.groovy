package com.gladed.androidgitversion

class CodeFormatTest extends AndroidGitVersionTest {
    void testEmpties() {
        plugin.codeFormat = "MXNNXPP"
        addCommit()
        addTag("1.2.3")
        assertEquals(1002003, plugin.code())
    }

    void testMajorMinorPatch() {
        addCommit()
        addTag("1.2")
        plugin.codeFormat = "MMNNPPP"
        assertEquals(102000, plugin.code())
    }

    void testMajorMinorPatchBuild() {
        addCommit()
        addTag("1.2")
        plugin.codeFormat = "MMNNPPBBB"
        assertEquals(10200000, plugin.code())
    }

    void testMajorMinorPatchBuildNonZero() {
        addCommit()
        addTag("42.88.33")
        addCommit()
        plugin.codeFormat = "MMNNPPBBB"
        assertEquals(428833001, plugin.code())
    }

    void testCodeFormatBaseCode() {
        plugin.codeFormat = "MNNPP"
        plugin.baseCode = 200000
        addCommit()
        addTag("1.2.3")
        assertEquals(210203, plugin.code())
    }

    void testCodeFormatExtraParts() {
        plugin.codeFormat = "MNNPP"
        addCommit()
        addTag("1.2.3.4.5.6")
        assertEquals(10203, plugin.code())
    }

    void testRevision() {
        plugin.codeFormat = "MNPR"
        addCommit()
        addTag("1.2.3-alpha1")
        assertEquals(1231, plugin.code())
    }
}
