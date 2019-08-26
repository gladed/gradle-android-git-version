package com.gladed.androidgitversion

class TagTest extends AndroidGitVersionTest {
    void testNoTags() {
        addCommit()
        assert plugin.name().startsWith('untagged-1-')
        assertFalse plugin.name().contains("master") // Due to hideBranches
        assertEquals(0, plugin.code())
    }

    void testTag() {
        addCommit()
        addTag('1.0')
        assertEquals('1.0', plugin.name())
    }

    void testNonVersionTag() {
        addCommit()
        addTag('checkpoint-1')
        assert plugin.name().startsWith('untagged-1-')
        assertEquals(0, plugin.code())
    }

    void testMultiTag() {
        addCommit()
        addTag("1.0")
        addCommit()
        addTag("1.1")
        assertEquals('1.1', plugin.name())
        assertEquals(1001000, plugin.code())
    }

    void testTagsOnSameCommit() {
        addCommit()
        addTag("1.10")
        addTag("1.7")
        addTag("1.90")
        addTag("1.8")
        assertEquals('1.90', plugin.name())
        assertEquals(1090000, plugin.code())
    }

    void testLongTagsOnSameCommit() {
        addCommit()
        addTag("2.0.4-beta3")
        addTag("2.0.5-beta2")
        assertEquals("2.0.5-beta2", plugin.name())
    }

    void testLateParts() {
        addCommit()
        addTag("1.2-rc3")
        assertEquals("1.2-rc3", plugin.name())
        assertEquals(1002003, plugin.code())
    }

    void testMultiTagOnSameCommit3() {
        addCommit()
        addTag("2.0.2")
        addTag("2.0.2-rc")
        addTag("2.0.2b")
        assertEquals("2.0.2", plugin.name())
    }

    void testCommitsAfterTag() {
        addCommit()
        addTag("1.0")
        addCommit()
        assert plugin.name().startsWith("1.0-1-")
        assertEquals(1000000, plugin.code())
    }

    void testLightWeightTag() {
        addCommit()
        addLightweightTag("1.0")
        assertEquals('1.0', plugin.name())
    }

    void testOnlyInTagOutside() {
        // Add a tag outside the onlyIn folder
        addCommit()
        addTag("1.0")

        // Add a commit inside the onlyIn folder
        File subDir = new File(projectFolder.root, "sub")
        subDir.mkdirs()
        File subFile = new File(subDir, "file")
        subFile.createNewFile()
        subFile.append("// addition")
        git.add().addFilepattern("sub/file").call();
        git.commit().setMessage("new subfolder").call();

        plugin.onlyIn = "sub"
        assert plugin.name().startsWith("1.0-1-")
    }

    void testTagWithSuffix() {
        addCommit()
        addTag("1.1-release")
        assertEquals(1001000, plugin.code())
        assertEquals("1.1-release", plugin.name())
    }

    void testNearestTag() {
        addCommit()
        addTag("1.0-dev")
        addBranch("release")
        addCommit()
        def releaseCommit = addCommit()
        addTag("1.1-final")
        checkout("master")
        addCommit()
        addCommit()
        merge(releaseCommit)
        // This shows we are picking the "wrong" tag when merged, issue #34
        assertEquals("1.1-final", plugin.name())
    }
}
