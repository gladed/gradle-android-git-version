package com.gladed.androidgitversion

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.rules.TemporaryFolder

class MainTest extends AndroidGitVersionTest {
    void testNoGitRepo() {
        assertEquals('unknown', plugin.name())
        assertEquals(0, plugin.code())
    }

    void testNoCommits() {
        assertEquals('unknown', plugin.name())
        assertEquals(0, plugin.code())
    }

    void testAddDirty() {
        addCommit()
        addTag("1.0")
        new File(projectFolder.root, "build.gradle").append("// addition")
        assertEquals('1.0-dirty', plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testAddNotDirty() {
        addCommit()
        addTag("1.0")
        File otherFile = new File(projectFolder.root, "build.gradle2")
        otherFile.createNewFile()
        otherFile.append("// addition")
        assertEquals('1.0', plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testNothingInOnlyIn() {
        addCommit()
        File subDir = new File(projectFolder.root, "sub")
        plugin.onlyIn = "sub"
        assert plugin.name().startsWith("untagged")
    }

    void testOnlyInChangeOutside() {
        File subDir = new File(projectFolder.root, "sub")
        subDir.mkdirs()
        File subFile = new File(subDir, "file")
        subFile.createNewFile()
        subFile.append("// addition")
        git.add().addFilepattern("sub/file").call();
        git.commit().setMessage("new subfolder").call();
        addTag("1.0")

        // Add another commit after the tag, but outside the onlyIn subfolder
        addCommit()

        plugin.onlyIn = "sub"
        assertEquals('1.0', plugin.name())
    }

    void testWeirdBranchName() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        addCommit()
        assert plugin.name().startsWith("1.0-1-")
        assert plugin.name().endsWith('-release_1.x')
    }

    void testHideBranch() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        addCommit()
        assert plugin.name().startsWith("1.0-1-")
        assert plugin.name().endsWith('-release_1.x')
    }

    void testHideBranchPattern() {
        plugin.hideBranches = [ 'master', 'feature/.*' ]
        addCommit()
        addTag('1.0')
        addBranch('feature/xyz')
        addCommit()
        assert plugin.name() ==~ '1.0-1-[a-f0-9]{7}'
    }

    void testHideOnlyFullBranchPattern() {
        plugin.hideBranches = [ 'master', 'feat' ]
        addCommit()
        addTag('1.0')
        addBranch('feature')
        addCommit()
        assert plugin.name() ==~ '1.0-1-[a-f0-9]{7}-feature'
    }

    void testBaseCode() {
        addCommit()
        addTag("1.1")
        plugin.baseCode = 55555
        assertEquals(1056555, plugin.code())
    }


    void testSubmodule() {
        // Set up a base repo
        addCommit()
        addTag("1.0")
        assertEquals("1.0", plugin.name())

        TemporaryFolder libraryFolder = new TemporaryFolder()
        libraryFolder.create()
        libraryFolder.newFile("build.gradle")

        try {
            // Create a library repo with its own label
            Git libraryGit = Git.init().setDirectory(libraryFolder.root).call()
            libraryGit.add().addFilepattern("build.gradle").call()
            libraryGit.commit().setMessage("addition").call()
            libraryGit.tag().setName("2.0").call()

            // Add the library repo to the base repo as a submodule
            Repository libraryRepo = git.submoduleAdd()
                    .setPath("library")
                    .setURI(libraryGit.getRepository().getDirectory().getCanonicalPath())
                    .call()
            libraryRepo.close()

            // Add the submodule as a subproject to the base project
            Project libraryProject = ProjectBuilder.builder()
                    .withProjectDir(new File(projectFolder.root, "library"))
                    .withParent(project)
                    .build()
            libraryProject.pluginManager.apply 'com.gladed.androidgitversion'
            AndroidGitVersionExtension libraryPlugin = (AndroidGitVersionExtension) libraryProject.getExtensions().getByName('androidGitVersion')

            // Make sure the subproject gets its version number from the library repo
            // and NOT the base repo
            assertEquals("2.0", libraryPlugin.name())
        } finally {
            libraryFolder.delete()
        }
    }

    void testUntrackedIsNotDirty() {
        addCommit()
        addTag("1.0")
        File file = new File(projectFolder.root, "untracked.file");
        file.append("content");
        assertFalse("untracked is not dirty", plugin.name().contains("dirty"))
    }

    void testUntrackedIsDirty() {
        addCommit()
        addTag("1.0")
        plugin.untrackedIsDirty = true
        File file = new File(projectFolder.root, "untracked.file");
        file.append("content");
        assertTrue("untracked is dirty", plugin.name().contains("dirty"))
    }

    void testMatchGitDescribeOffByDefault() {
        addCommit()
        addTag("1.0.0")
        def currentCommit = addCommit()
        def currentHash = ObjectId.toString(currentCommit.toObjectId())
        def shortHash = currentHash.substring(0, 7)
        def expectedVersionName = "1.0.0-1-" + shortHash
        def versionName = plugin.name()
        assert versionName.startsWith("1.0.0-1-")
        assert versionName.endsWith(shortHash)
        assertEquals (expectedVersionName, versionName)
    }

    void testMatchGitDescribeUsesCorrectCommit() {
        plugin.matchGitDescribe = true
        addCommit()
        addTag("1.0.0")
        def currentCommit = addCommit()
        def currentHash = ObjectId.toString(currentCommit.toObjectId())
        def shortHash = currentHash.substring(0, 7)
        def expectedVersionName = "1.0.0-1-g" + shortHash
        def versionName = plugin.name()
        assert versionName.startsWith("1.0.0-1-g")
        assert versionName.endsWith(shortHash)
        assertEquals (expectedVersionName, versionName)
    }

    void testFlush() {
        addCommit()
        addTag("1.2.3")
        assertEquals(1002003, plugin.code())
        addTag("1.2.4")
        plugin.flush()
        assertEquals(1002004, plugin.code())
    }

}
