package com.gladed.androidgitversion

import org.eclipse.jgit.lib.ObjectId

class FormatTest extends AndroidGitVersionTest {
    void testFormat() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        addCommit()
        makeChange() // Uncommitted

        plugin.format = '%tag%%!count%%_commit%%.branch%%+dirty%'
        // Should be something like '1.0!1_10a984.release_1.x+dirty'
        assert plugin.name().startsWith('1.0!1_')
        assert plugin.name().endsWith('.release_1.x+dirty')
    }

    void testCleanFormat() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        addCommit()

        plugin.format = '%tag%%!count%%(commit)%%.branch%%+dirty%'
        // Should be something like '1.0!1_10a984.release_1.x'
        assert plugin.name().startsWith('1.0!1(')
        assert plugin.name().endsWith(').release_1.x')
    }

    void testBranchNameOnGitDescribe() {
        addCommit()
        addTag("1.0")
        addBranch("feature-foo")
        addCommit()
        plugin.format = "%describe%%-branch%%-dirty%"

        assert plugin.name().startsWith('1.0-1-g')
        assert plugin.name().endsWith('-feature-foo')
    }

    void testDirtyOnGitDescribe() {
        addCommit()
        addTag("1.0")
        makeChange()
        plugin.format = "%describe%%-branch%%-dirty%"

        assertEquals(plugin.name(), "1.0-dirty")
    }

    void testDirtyBranchOnGitDescribe() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        addCommit()
        makeChange()
        plugin.format = "%describe%%-branch%%-dirty%"

        assert plugin.name().startsWith('1.0-1-g')
        assert plugin.name().endsWith('release_1.x-dirty')
    }

    void testLongCommitHash() {
        addCommit()
        addTag("1.4")
        addCommit()
        addCommit()
        plugin.format = "%tag%%-count%%-branch%%-dirty%"
        assertEquals("1.4-2", plugin.name())
    }

    void testHashLength() {
        plugin.commitHashLength = 8
        addCommit()
        addTag("1.0")
        ObjectId commitId = addCommit().toObjectId();
        String hash = ObjectId.toString(commitId)
        assert plugin.name().contains(hash[0..7])
    }
}
