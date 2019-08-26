package com.gladed.androidgitversion;

public class DeprecatedTest extends AndroidGitVersionTest {
    void testFourPartCode() {
        addCommit()
        addTag("1.2.3.4")
        plugin.parts = 4
        assertEquals(1002003004, plugin.code())
    }
}
